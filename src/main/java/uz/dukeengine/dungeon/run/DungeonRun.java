package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.story.QuestLog;
import uz.dukeengine.rpg.hero.HeroProgress;
import uz.dukeengine.rpg.item.KeyDrop;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.mission.Mission;
import uz.dukeengine.rpg.item.LootTable;
import uz.dukeengine.rpg.skill.SkillRanks;
import uz.dukeengine.rpg.skill.Skills;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.GamePlayer;

/**
 * The run loop: one dungeon at a time, and a fresh one the moment the hero dies.
 *
 * <p>Duke Dungeon is a roguelike in the oldest sense — nothing is carried past a run's
 * ending. (A run itself is saved as each floor is entered, and picked up there: see
 * {@link #onCheckpoint} and {@link #resume}.) A run is the stretch between spawning at full health
 * and its ending, and it can end two ways: on a floor, or at the bottom of the
 * last one. Either way the world is torn down and rebuilt from a new seed and the
 * hero starts over with nothing but full health, because progress is not meant to
 * survive an ending of either sort.
 *
 * <p>That there are two is recent and is the shape of the game rather than a
 * detail of this class. The descent used to have no bottom: floors went down for
 * ever, each a little harder, and the only question one could ask was how much
 * further. It stops at the floor the last boss stands on — see {@code Bosses} in
 * the settings file, which is both who they are and how many floors there are.
 *
 * <p>Where the floors themselves come from is not this class's business — see
 * {@link Floors}. A descent draws each one from the next seed along the chain; a
 * stage hands back the same frozen floor every time, and the boss standing on it
 * is the bottom of a one-floor game. The loop is the same either way, which is
 * the point: a stage is not a second mode with a second run loop in it.
 *
 * <p><b>A party is more seats at the same loop.</b> Every player has a seat — his side, his hero, and everything
 * that hero has earned — and the game alone is a party of one. A hero who falls waits while the others fight on;
 * the boss down, every seat goes to the next floor, the fallen standing again with all they had; and only when
 * every hero has fallen is the run lost, for all of them. A party's heroes are said as orders once the match runs
 * ({@link #choose}), and its first floor waits until every one of them has been.
 *
 * <p>This runs as a per-frame tick on the simulation thread, so it must stay
 * deterministic like everything else there: it reads the frame counter, never the
 * clock, and the seed of each new run is drawn from the last by the same
 * {@link DeterministicRng} chain. Given one starting seed, the entire sequence of
 * dungeons a session plays is fixed. What it tells the panel is the one exception, and only because it is not
 * part of the world: each machine describes its own player's hero.
 *
 * <p>Rebuilding in place reuses the engine's save/load seam — {@code clearWorld}
 * then respawn — which is safe from a tick because the frame's own object updates
 * and reaping have already run by the time the callback fires. The players are
 * left untouched across runs, so ownership stays valid; only the objects and the
 * terrain are replaced.
 */
public final class DungeonRun {

    /**
     * How the run is going right now.
     *
     * <p>{@code WON} is the newest and the one that changes what this game is. A
     * descent with no bottom is a scoreboard: you go down until you stop, and the
     * only question a floor asks is how much further. A descent with a last floor
     * on it can be finished, and then every floor before it is on the way
     * somewhere — which is the difference between a run and a session.
     */
    public enum State {
        RUNNING,
        DEAD,
        WON
    }

    /**
     * One player's place in the run: his side, which hero he plays, and what that hero has earned.
     *
     * <p>Progress and skills are held here rather than on the hero so that they can be wiped when a run ends —
     * which is the whole of why the run owns them: a roguelike keeps nothing, and a build is the most valuable
     * thing there is to keep.
     */
    private static final class Seat {

        final GamePlayer player;
        final HeroProgress progress;
        final SkillRanks learnt;
        /**
         * Which hero he plays: the file's {@code DefaultHero} until somebody chooses, and nobody at all for a party
         * member who has not said yet.
         */
        String hero;
        ObjectId heroId;
        /** The person his player right-clicked, whom he is walking over to talk to; null for nobody. */
        ObjectId talkingTo;
        /** The experience a loaded save gives his next body, or -1 when there is none to give. */
        int loadedExperience = -1;

        Seat(GamePlayer player, HeroProgress progress, SkillRanks learnt, String hero) {
            this.player = player;
            this.progress = progress;
            this.learnt = learnt;
            this.hero = hero;
        }
    }

    private final GamePlayer dungeonPlayer;
    private final DungeonSettings settings;
    private final LootTable drops;

    /** The players' seats, the first player's first: a game alone has that one. */
    private final List<Seat> seats = new ArrayList<>();

    /** Where each floor comes from: the seed chain, or the file one was frozen into. */
    private Floors floors;
    private State state = State.RUNNING;
    private ObjectId bossId;
    private int depth = 1;
    /** When the run ended, whether it was lost or won. */
    private int endedFrame;
    /** When the floor closes behind him, or 0 while the boss is still alive. */
    private int descendAtFrame;

    /** The themes the file described, and how this floor is drawn from them. */
    private final uz.dukeengine.dungeon.content.Themes themes;
    private String look;
    private int runCount; // how many times a new dungeon has been generated after a death

    /** The standing orders every player has given; only the panel reads them. */
    private final uz.dukeengine.dungeon.ai.Orders orders;

    /** The first floor, held while a party's heroes are still being said; {@code null} once it is laid. */
    private GeneratedDungeon gathering;

    /** What this floor asks of the party, or {@code null} for a floor with no keep — see {@link Mission}. */
    private Mission mission;
    /** Where it stands, worked out every frame the floor is played: the last step on a floor with none. */
    private Mission.Step step = Mission.Step.KILL;
    /**
     * The missions listed for it, read by the window: written whole, on the simulation's thread, every frame the floor
     * is played — and empty from the frame the run is lost or won, or the boss falls and the floor waits to close,
     * until the next floor is laid.
     */
    private volatile Tracker tracker = Tracker.NONE;
    /** The steps of this floor already finished and still listed, each until its time is up; the simulation's alone. */
    private final List<Tracker.Step> finished = new ArrayList<>();
    /** The step said last frame, and the frame it began at; null before the floor says its first. */
    private Mission.Step said;
    private int saidSince;

    /** The keep's shut gate, as everything that hurts or mends asks it: told of each floor as it is laid. */
    private final Seal seal;

    /** Told of the run as each floor is entered — the autosave; nothing until somebody asks. */
    private java.util.function.Consumer<SaveGame> checkpoint = save -> { };
    /** Whether the floor about to be laid is a loaded save's, which is not written back. */
    private boolean loading;

    public DungeonRun(GamePlayer heroPlayer, GamePlayer dungeonPlayer, Floors floors,
            DungeonSettings settings, HeroProgress progress,
            LootTable drops, uz.dukeengine.dungeon.ai.Orders orders,
            SkillRanks learnt, Seal seal) {
        this.seal = seal;
        this.quests = new QuestLog(settings.story(), settings.loot(), settings.lootDrops().joinCount());
        this.orders = orders;
        this.dungeonPlayer = dungeonPlayer;
        this.floors = floors;
        this.settings = settings;
        this.drops = drops;
        this.themes = settings.themes();
        this.look = lookOf(null);
        this.depth = floors.firstDepth();
        seats.add(new Seat(heroPlayer, progress, learnt, settings.run().defaultHero()));
    }

    /**
     * Another player goes down with the first: a seat of his own, with his own progress and skills, and no hero
     * until he says which — see {@link #choose}.
     */
    public void seat(GamePlayer player, HeroProgress progress, SkillRanks learnt) {
        seats.add(new Seat(player, progress, learnt, null));
    }

    /**
     * A party's heroes are said once the match runs, the first player's with the rest: every seat waits for its
     * pick, and the first floor for all of them.
     */
    public void awaitHeroes() {
        for (var seat : seats) {
            seat.hero = null;
        }
    }

    /**
     * Play with this hero from here on.
     *
     * <p><b>Two moments, and they are not the same.</b> Answered off the menu
     * before anything has started, there is no world yet — the first floor is
     * placed when the engine starts, so recording the choice is the whole of the
     * work and laying a floor here would be laying one into a game with no
     * simulation in it. That is not a theoretical ordering: it threw a null
     * pointer the first time anybody pressed a row.
     *
     * <p>Answered when a world already exists — he abandoned a run, went back to
     * the menu and chose again — the standing floor is his predecessor's and has
     * to go. So it takes the road a death takes, and everything the last hero
     * earned goes with him, because none of it was this one's.
     *
     * <p>Either way it is called from the menu with no simulation thread running,
     * so nothing here has to cross one.
     */
    public void startWith(DukeGame game, String template) {
        pick(seats.getFirst(), template);
        if (game.getLogic() != null) {
            begin(game);
        }
    }

    /**
     * A player of a party says which hero he goes in as — an order, heard on the simulation thread on the same
     * frame on every machine. Once, and only a hero this game has: a second pick would lay the floor again under
     * everyone, and a name no {@code Hero} block has is somebody else's build talking. The last of a party to say
     * lays the first floor.
     */
    public void choose(DukeGame game, int playerIndex, String template) {
        var seat = seatOf(playerIndex);
        if (seat == null || seat.hero != null || template == null
                || !(game.getLogic().getThingFactory().findTemplate(template)
                        instanceof uz.dukeengine.dungeon.content.Hero)) {
            return;
        }
        pick(seat, template);
        if (gathering != null && seats.stream().allMatch(each -> each.hero != null)) {
            var first = gathering;
            gathering = null;
            game.setBanner("");
            lay(game, first);
        }
    }

    /** His hero from here on, with none of his skills learnt: a knight cannot inherit a mage's points. */
    private void pick(Seat seat, String template) {
        seat.hero = template;
        seat.progress.playing(settings.heroNamed(template));
        seat.learnt.startWith(settings.skillsFor(template));
    }

    /** What the first player has learnt, for the panel and for whatever spends a level. */
    public SkillRanks getLearnt() {
        return seats.getFirst().learnt;
    }

    /** What this player's hero has learnt, or {@code null} for a player with no seat. */
    public SkillRanks learntOf(int playerIndex) {
        var seat = seatOf(playerIndex);
        return seat == null ? null : seat.learnt;
    }

    /** How far this player's hero has come, or {@code null} for a player with no seat. */
    public HeroProgress progressOf(int playerIndex) {
        var seat = seatOf(playerIndex);
        return seat == null ? null : seat.progress;
    }

    private Seat seatOf(int playerIndex) {
        for (var seat : seats) {
            if (seat.player.getIndex() == playerIndex) {
                return seat;
            }
        }
        return null;
    }

    /**
     * Play these floors from here on — the endless descent, or one frozen stage.
     *
     * <p>The whole of what choosing a game <em>type</em> comes to. Everything that
     * differs between the two reads {@link Floors} and nothing else: where the
     * next floor comes from, what seed the look is drawn from, and which depth
     * wins. So swapping this swaps the game, and the run loop above does not learn
     * a second shape.
     *
     * <p>Recorded rather than acted on, for the same reason the hero is: this is
     * answered on the menu, where the first floor has not been laid yet. It is
     * {@link #startWith} that lays one, and it comes after.
     */
    public void playing(Floors floors) {
        this.floors = floors;
        // How deep the game he chose starts. A stage's difficulty IS a depth, so
        // this is where being handed a hard stage becomes being in one — and it
        // has to be here rather than only in the constructor, because the menu
        // chooses after the game is built. Without it a stage picked off the menu
        // would be fought at depth one however hard its author made it, and
        // nothing anywhere would say so.
        this.depth = floors.firstDepth();
        this.look = lookOf(null);
        // The world was built around a floor from the floors we no longer have.
        // Nobody has seen it — this is answered on the menu — but it is still
        // standing, and opening on it would put the player in the game he did not
        // choose while everything else said he had.
        this.openingIsStale = true;
    }

    /** Whether the floor the world was built around belongs to a game nobody chose. */
    private boolean openingIsStale;

    /**
     * Where the run is told as each floor is entered, a game alone: the checkpoint the game saves. Called on the
     * simulation thread, after the floor is laid and the last floor's keys are taken from the bag. A party is not
     * saved — see {@code docs/plan/2026-10-08-saving.md}.
     */
    public void onCheckpoint(java.util.function.Consumer<SaveGame> told) {
        this.checkpoint = told;
    }

    /**
     * Pick up a saved run: its floors, its depth, its hero, and what he had learnt and carried — his experience goes
     * into the body the floor gives him. Recorded, as {@link #playing} and {@link #startWith} are: answered on the
     * menu, and the floor is laid when the game starts. A game alone; the first seat is his.
     */
    public void resume(SaveGame save, Floors floors) {
        playing(floors);
        depth = save.depth();
        var seat = seats.getFirst();
        seat.progress.reset();
        pick(seat, save.hero());
        save.restore(seat.progress.getLoot(), seat.learnt, settings);
        seat.loadedExperience = save.experience();
        loading = true;
    }

    /**
     * The run as a save this moment, mid-floor or not: the hero as he is now, on the floor he entered — Saqlash, and
     * leaving the game. {@code null} for a party, and while the run is over or a party gathers: the save from the
     * floor's entry stands. Asked with the simulation paused.
     */
    public SaveGame saveNow() {
        return state == State.RUNNING && gathering == null ? save() : null;
    }

    /** The run as a save, the first seat's hero in it; {@code null} for a party or a seat with no hero. */
    private SaveGame save() {
        var seat = seats.getFirst();
        if (seats.size() != 1 || seat.hero == null) {
            return null;
        }
        return SaveGame.of(floors.seed(), floors.stageName(), depth, seat.hero, seat.progress.getExperience(),
                seat.learnt, seat.progress.getLoot());
    }

    /** Whoever the first player plays — the file's answer until somebody chooses. */
    public String getHeroTemplate() {
        return seats.getFirst().hero;
    }

    /**
     * The floor this game is won on, or 0 for a descent with no bottom.
     *
     * <p>Which of the two games is being played, said as a number: a stage is one
     * floor deep whatever the file's list of bosses says, and the descent is as
     * deep as that list. The panel already shows it; this is the same figure
     * asked of the run rather than of the settings.
     */
    public int getLastDepth() {
        return floors.lastDepth();
    }

    /**
     * How this floor is drawn, as the two names the client is told.
     *
     * <p>Looks and nothing else. It is worked out from the seed and the depth --
     * both of which the run already has -- with a generator of its own, so asking
     * what a floor looks like cannot move the world's dice by a step. Held rather
     * than recomputed because it is asked for every frame and settled once a floor.
     *
     * <p>A floor that mixes biomes is dressed as the biome its first chamber grew, in the tone the depth draws for
     * it: the client shows one look per floor, and the one the heroes come in to is the one that has to be right.
     * Before a floor exists, and for one that mixes none, it is the depth's theme as ever.
     */
    private String lookOf(GeneratedDungeon floor) {
        if (floors.paint() != null) {
            return null; // painted ground: a look's kit would be drawn over it
        }
        var chosen = floor != null && floor.biomes() != null
                ? themes.dressedAs(floor.biomes().ofRoom(0), floors.seed(), depth)
                : themes.pick(floors.seed(), depth);
        return chosen == null ? null : chosen.asStatus();
    }

    /**
     * What each cell of {@code floor} is drawn as, for a floor that mixes biomes, or null for one that wears its look
     * whole. Each biome in the tone the depth draws for it — the same draw {@link #lookOf} makes for the biome the
     * heroes come in to, so the cells round them and the status line name one look — but for the keep's cells, which
     * wear the keep's, whatever biome they stand in. A painted stage is its paint, and wears no look at all.
     */
    private uz.dukeengine.core.map.MapTemplate looksOf(GeneratedDungeon floor) {
        if (floors.paint() != null) {
            return floors.paint();
        }
        if (floor.biomes() == null) {
            return null;
        }
        var looks = new java.util.ArrayList<String>();
        for (var biome : floor.biomes().biomes()) {
            var chosen = themes.dressedAs(biome, floors.seed(), depth);
            looks.add(chosen == null ? null : chosen.asStatus());
        }
        var stone = themes.dressedAs(themes.themeNamed(settings.keep().look()), floors.seed(), depth);
        return new FloorLooks("descent", floor.biomes(), looks, floor.scenery(),
                settings.world().navigationCellsPerCell(), floor.keep(), stone == null ? null : stone.asStatus());
    }

    /**
     * Put the first floor in the world.
     *
     * <p>Goes through the same placement every later floor does, so the one the
     * player always sees and the ones he rarely reaches cannot drift apart. A party still saying its heroes holds
     * it until the last of them has.
     */
    public void openOn(DukeGame game, GeneratedDungeon floor) {
        if (openingIsStale) {
            // He chose a different game on the menu. The floor handed in here was
            // built with the world, before he was asked, and belongs to whichever
            // game the file happened to name — so it is torn out and one from the
            // floors he actually chose is laid instead. Which is the road a new
            // run already takes, and the reason it costs nothing to say so.
            openingIsStale = false;
            descend(game);
            return;
        }
        if (seats.stream().anyMatch(seat -> seat.hero == null)) {
            gathering = floor;
            game.setBanner(settings.party().gatheringWord());
            return;
        }
        lay(game, floor);
    }

    /** The floor the world was built around, with everyone on it. */
    private void lay(DukeGame game, GeneratedDungeon floor) {
        look = lookOf(floor);
        var looks = looksOf(floor);
        if (looks != null) {
            // The world was built around this floor's grid before the run knew its seed and depth; the grid stays
            // as it was, and the client is told what each of its cells wears.
            game.applyMapTerrain(game.getTerrain(), looks);
        }
        var placed = Spawner.place(game, heroPlayers(), heroTemplates(), dungeonPlayer, floor, settings, depth,
                drops);
        for (int i = 0; i < seats.size(); i++) {
            seats.get(i).heroId = placed.heroes().get(i) == null ? null : placed.heroes().get(i).getId();
        }
        bossId = placed.boss() == null ? null : placed.boss().getId();
        onTheFloor(game, floor, placed);
    }

    /**
     * What a floor just laid asks of the party: that no hero bring a key from the floor above, its mission, if it has
     * a keep, and of every hero that he leave the key where he falls, so a party never loses the way on with him. And
     * its keep and gate, or none, for the seal to keep shut.
     *
     * <p>A key belongs to the floor it was found on: one carried down would open this floor's gate unearned, and have
     * the tracker say "give it" of a key that is still lying.
     */
    private void onTheFloor(DukeGame game, GeneratedDungeon floor, Spawner.Placed placed) {
        seal.floor(floor.keep(), placed.gate());
        for (int i = 0; i < seats.size(); i++) {
            var bag = seats.get(i).progress.getLoot();
            bag.removeAll(ItemUse.UNLOCK);
            var hero = placed.heroes().get(i);
            if (hero != null) {
                hero.addModule(new KeyDrop(hero, bag, settings.lootDrops().template(), dungeonPlayer.getIndex()));
            }
        }
        mission = missionOf(game.getLogic(), floor, placed, settings, dungeonPlayer.getIndex());
        peopleOn(game);
        finished.clear();
        said = null;
        track(game);
        var save = loading ? null : save();
        loading = false;
        if (save != null) {
            checkpoint.accept(save);
        }
    }

    /**
     * Where the mission stands this frame, and the list that says it — empty while the boss has fallen and the floor
     * waits to close: the banner has the screen, and there is nothing left to tell him to do. A step passed stays in
     * it, done, for the run's {@code DoneFrames}; a step that comes back (the key dropped again) is not a step done.
     */
    private void track(DukeGame game) {
        step = mission == null ? Mission.Step.KILL
                : mission.step(game.getLogic(), seats.stream().map(seat -> seat.progress.getLoot()).toList());
        int frame = game.getLogic().getFrame();
        var run = settings.run();
        if (said == null || step != said) {
            if (said != null && step.ordinal() > said.ordinal() && mission != null) {
                finished.add(new Tracker.Step(doneWords(mission, run, said), true, frame));
            }
            said = step;
            saidSince = frame;
        }
        finished.removeIf(done -> frame - done.since() >= run.doneFrames());
        var words = mission == null ? run.killBossWord() : words(mission, run, step);
        var steps = new ArrayList<>(finished);
        steps.add(new Tracker.Step(words, false, saidSince));
        tracker = descendAtFrame > 0 ? Tracker.NONE
                : new Tracker(listed(new Tracker.Entry(Tracker.Kind.MAIN, run.missionTitle(), steps)));
    }

    /** What a step said as it was finished: the count full where it counts. */
    private static String doneWords(Mission mission, uz.dukeengine.dungeon.world.Run run, Mission.Step step) {
        return step == Mission.Step.CLEAR ? run.clearWord(mission.outside(), mission.outside())
                : words(mission, run, step);
    }

    /**
     * The mission of a floor just laid, or null for one with no keep — a stage, or a floor where none fitted — or a
     * game with no key to give: the first item that unlocks, the monsters placed outside the keep counted, and with
     * none there the key laid now where the first hero came in.
     */
    static Mission missionOf(uz.dukeengine.core.thing.World world, GeneratedDungeon floor, Spawner.Placed placed,
            DungeonSettings settings, int floorOwner) {
        var keep = floor.keep();
        var key = settings.loot().stream().filter(item -> item.use() == ItemUse.UNLOCK).findFirst().orElse(null);
        if (keep == null || key == null) {
            return null;
        }
        var outside = new ArrayList<GameObject>();
        for (var monster : placed.monsters()) {
            var at = new GeneratedDungeon.Placement(monster.getPosition().x(), monster.getPosition().y());
            if (!keep.holds(at.cellX(), at.cellY())) {
                outside.add(monster);
            }
        }
        return Mission.begin(world, key, settings.lootDrops().template(), floorOwner, placed.gate(), outside,
                wayIn(floor, placed));
    }

    /** Where the heroes came in: where the first of them stands, or the floor's own way in. */
    private static uz.dukeengine.core.math.Coord3D wayIn(GeneratedDungeon floor, Spawner.Placed placed) {
        for (var hero : placed.heroes()) {
            if (hero != null) {
                return hero.getPosition();
            }
        }
        return new uz.dukeengine.core.math.Coord3D(floor.hero().x(), floor.hero().y(), 0f);
    }

    /** What the tracker says at {@code step}, in the run's words: the count while there is one. */
    static String words(Mission mission, uz.dukeengine.dungeon.world.Run run, Mission.Step step) {
        return switch (step) {
            case CLEAR -> run.clearWord(mission.killed(), mission.outside());
            case TAKE -> run.takeKeyWord();
            case GIVE -> run.giveKeyWord();
            case KILL -> run.killBossWord();
        };
    }

    /** Called every logic frame on the simulation thread. */
    public void tick(DukeGame game) {
        if (gathering != null) {
            return; // nobody is on the floor until everybody is
        }
        switch (state) {
            case RUNNING -> whileRunning(game);
            case DEAD -> whileDead(game);
            case WON -> whileWon(game);
        }
    }

    private void whileRunning(DukeGame game) {
        var logic = game.getLogic();
        // Learn each hero the first time we run, then track him by id. The run is lost only when every one of
        // them is down: a fallen hero waits for the others to take the floor.
        boolean standing = false;
        for (var seat : seats) {
            if (seat.heroId == null) {
                var hero = findHero(game, seat);
                if (hero != null) {
                    seat.heroId = hero.getId();
                }
            }
            var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
            standing |= hero != null && hero.getBody().getHealth() > 0f;
        }
        if (!standing) {
            state = State.DEAD;
            endedFrame = logic.getFrame();
            tracker = Tracker.NONE; // the banner has the screen: the next floor says its step when it is laid
            game.setBanner("lost|" + settings.run().diedWord());
            return;
        }
        if (bossId != null && logic.findObject(bossId) == null && descendAtFrame == 0) {
            // Nothing below this one: the last boss is the end of the game rather
            // than the door to the next floor. The banner is the whole of what
            // says so, and it is the only thing in this run loop that is not a
            // beginning of something.
            if (floors.lastDepth() > 0 && depth >= floors.lastDepth()) {
                state = State.WON;
                endedFrame = logic.getFrame();
                tracker = Tracker.NONE;
                game.setBanner("won|" + settings.run().wonWord());
                return;
            }
            // The floor is finished, but not left yet — see below.
            descendAtFrame = logic.getFrame() + settings.run().descendDelayFrames();
            game.setBanner("depth|" + settings.run().nextDepthWord(depth + 1));
        }
        if (descendAtFrame > 0 && logic.getFrame() >= descendAtFrame) {
            depth++;
            descend(game);
            game.setBanner("");
        }
        story(game);
        track(game);
        showStatus(game);
    }

    /**
     * What the hero's panel shows: where he is and what he has become.
     *
     * <p>Goes through the snapshot's status channel, which the engine carries and
     * never reads — depth and levels are this game's arithmetic and the engine has
     * no name for either. See {@link HeroStatus} for what is in the line.
     *
     * <p>About this machine's own player: the panel is his. It changes nothing in the world, which is the only
     * reason a machine may say something here another does not.
     */
    private void showStatus(DukeGame game) {
        var seat = seatOf(game.getLocalPlayerIndex());
        var mine = seat == null ? seats.getFirst() : seat;
        game.setStatus(card(game, mine) + HeroStatus.world(game.getLogic(),
                Skills.heroOf(game.getLogic(), mine.player.getIndex()), mine.progress, depth,
                bossId, settings));
    }

    /**
     * The card itself — the panel's half of the line, about whatever is selected.
     *
     * <p>Split from the floor's half above because the two answer different
     * questions. This one changes with every click; the bars over everybody's
     * heads are drawn whether anything is selected or not.
     *
     * <p>Three answers and they are tried in this order, which is the order of
     * what is most specific. His own is asked first and asked before the creature
     * is looked at at all: a dying hero is still the hero the bar is about, and
     * the run has a screen of its own for what happens next.
     */
    private String card(DukeGame game, Seat seat) {
        int player = seat.player.getIndex();
        var picked = orders.watchedBy(player);
        var creature = picked == null ? null : game.getLogic().findObject(picked);
        boolean his = creature != null && creature.getPlayerIndex() == player;
        var hero = Skills.heroOf(game.getLogic(), player);
        if (his && hero == creature) {
            return HeroStatus.of(creature, seat.progress, depth, floors.lastDepth(), settings,
                    game.getLogic().getFrame(), look, orders.isHolding(player), seat.learnt);
        }
        // Selecting anything shows it, and his orders always go to his own hero: the client sends them to the hero
        // the bars name whatever is picked out, so the buttons are his to press whenever that hero stands.
        boolean heroStands = hero != null && !hero.isEffectivelyDead();
        if (creature != null && !creature.isEffectivelyDead()) {
            // Somebody else's creature, or one of his that is not the hero: the card describes it, and what it is
            // doing is on the buttons.
            return HeroStatus.creature(creature, depth, floors.lastDepth(), settings,
                    look, his || heroStands);
        }
        // Nothing selected, or what was selected has died: the bar keeps the floor
        // and loses the creature. A panel describing a corpse until the player
        // thinks to click somewhere is a panel that looks broken.
        return HeroStatus.nothing(depth, floors.lastDepth(), settings,
                seat.progress.getLoot().noteAt(game.getLogic().getFrame()), look, heroStands);
    }

    /**
     * Why the floor does not close the instant the boss falls.
     *
     * <p>Two reasons, and the first is a bug the second would have hidden: the
     * boss leaves something behind, and rebuilding the world in the same frame
     * takes it away again before anyone could walk to it. Beyond that, being
     * moved somewhere else the instant a fight ends reads as a glitch — a floor
     * wants a moment to have been finished in.
     */
    private void whileDead(DukeGame game) {
        if (game.getLogic().getFrame() - endedFrame < settings.run().respawnDelayFrames()) {
            return;
        }
        begin(game);
    }

    /**
     * Having finished it: the same clearing away as a death, after a longer look
     * at the word.
     *
     * <p>The two are one act with two names, which is worth saying because it
     * would be easy to think a win deserves machinery of its own. It does not: a
     * run that has ended is a run that has ended, and everything the hero earned
     * belonged to it. What a win gets that a death does not is time — long enough
     * to have been a win rather than an interruption.
     */
    private void whileWon(DukeGame game) {
        if (game.getLogic().getFrame() - endedFrame < settings.run().victoryFrames()) {
            return;
        }
        begin(game);
    }

    /** A fresh run: the first floor, heroes with nothing, and the banner cleared. */
    private void begin(DukeGame game) {
        // An ending is the end of everything, not just of this floor.
        // Back to the top of whatever is being played: the first floor of a
        // descent, and for a stage the one depth it is fought at. Resetting to 1
        // would make the second attempt at a hard stage an easy one, which is the
        // worst way for a level meant to be re-attempted to fail.
        depth = floors.firstDepth();
        descendAtFrame = 0;
        for (var seat : seats) {
            seat.progress.reset();
            // ★ AND WHAT HE HAD LEARNT. The same argument again and it was missed the
            // first time: a hero who died came back at the first level with all four
            // skills still open, so his second run began with twelve points he had
            // not earned and no decisions left to make. A build is the most valuable
            // thing a run has and a roguelike keeps nothing.
            seat.learnt.startWith(settings.skillsFor(seat.hero));
            seat.talkingTo = null;
        }
        quests.reset(); // and every quest: the map that starts them starts them again
        descend(game);
        runCount++;
        state = State.RUNNING;
        game.setBanner("");
    }

    /**
     * The next floor's ground: what is stone, and how high each cell stands. How far
     * apart two storeys are is the world's, and the engine lays the grid at it.
     *
     * <p>Both arrive together because they are one map. A grid built from the walls
     * alone would lay the new floor out flat and leave the hero walking through the
     * storeys of the last one.
     */
    private static uz.dukeengine.core.pathfind.PathGrid terrainOf(GeneratedDungeon floor) {
        var grid = MapLoader.fromText(floor.asciiMap());
        MapLoader.levels(grid, floor.levelMap());
        grid.setRelief(floor.relief());
        if (floor.levelHeight() > 0f) {
            // A map that says how tall its storeys are is laid at its own height rather than the world's.
            grid.setLevelHeight(floor.levelHeight());
        }
        return grid;
    }

    /**
     * Down a floor: a new seed, a new layout, and tougher inhabitants — but the
     * same heroes, still carrying what they have earned, the fallen among them standing again.
     *
     * <p>The distinction from a death is the whole point of depth, and it has to
     * be stated rather than inferred. Both replace the hero objects, so anything
     * watching for a new hero to decide whether to reset would wipe his levels on
     * every floor: {@link HeroProgress} is told which of the two this is.
     */
    private void descend(DukeGame game) {
        descendAtFrame = 0;
        var floor = floors.next(depth);

        var logic = game.getLogic();
        logic.clearWorld();
        game.applyMapTerrain(terrainOf(floor), looksOf(floor));

        var placed = Spawner.place(game, heroPlayers(), heroTemplates(), dungeonPlayer, floor, settings, depth,
                drops);
        for (int i = 0; i < seats.size(); i++) {
            var hero = placed.heroes().get(i);
            var seat = seats.get(i);
            seat.heroId = hero == null ? null : hero.getId();
            if (hero != null) {
                seat.progress.carryOver(game.getLogic(), hero);
                // A loaded save's experience, given the way a kill gives it — on his body, which his progress reads
                // and banks at the next floor. A hero's ranks are all at nothing (ExperienceRequired = [0, 0, 0]),
                // so the body itself is no different for it.
                var earned = hero.findModule(uz.dukeengine.combat.module.ExperienceModule.class);
                if (seat.loadedExperience >= 0 && earned != null) {
                    earned.setExperience(seat.loadedExperience);
                }
                seat.loadedExperience = -1;
            }
        }
        bossId = placed.boss() == null ? null : placed.boss().getId();
        onTheFloor(game, floor, placed);
        look = lookOf(floor);
    }

    private List<GamePlayer> heroPlayers() {
        return seats.stream().map(seat -> seat.player).toList();
    }

    private List<String> heroTemplates() {
        return seats.stream().map(seat -> seat.hero).toList();
    }

    /**
     * His hero, out of everything his player owns.
     *
     * <p>The template check is not redundant beside the player check: his arrows
     * are his too, and one of them is not the hero. Which template that is comes
     * out of his seat, because there is more than one hero now.
     */
    private GameObject findHero(DukeGame game, Seat seat) {
        for (var object : game.getLogic().getObjects()) {
            if (object.getPlayerIndex() == seat.player.getIndex()
                    && object.getTemplate().name().equals(seat.hero)) {
                return object;
            }
        }
        return null;
    }

    // ---- observation ----

    public State getState() {
        return state;
    }

    /** How many new dungeons this session has generated after a death (0 at first). */
    public int getRunCount() {
        return runCount;
    }

    /** Which floor the hero is on. One at the start of every run.  */
    public int getDepth() {
        return depth;
    }

    /** What this floor asks of the party, or {@code null} for a floor with no keep. */
    public Mission getMission() {
        return mission;
    }

    /** Where this floor's mission stands: the last step on a floor with none. */
    public Mission.Step getStep() {
        return step;
    }

    /**
     * The missions as the tracker at the window's right edge lists them — from any thread — or none while a run
     * that was lost or won waits to start again, or the floor waits to close behind its fallen boss.
     */
    public Tracker getTracker() {
        return tracker;
    }

    // ---- people and quests: see docs/plan/2026-10-08-dialogue-and-quests.md ----

    /** The run's quests: its state, and what has been said. */
    private final QuestLog quests;

    /** A person standing on this floor: the thing, and the Npc it is. In the order the map writes them. */
    private record Person(ObjectId id, String npc) {
    }

    private final List<Person> people = new ArrayList<>();

    /** What one person shows over their head, by the id of the thing: read by the window. */
    public record Mark(int id, QuestLog.Mark mark) {
    }

    private volatile List<Mark> marks = List.of();

    /** The floor's mission first, then every quest going: the MAIN ones first, then the SIDE. */
    private List<Tracker.Entry> listed(Tracker.Entry floor) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(floor), quests.entries().stream()).toList();
    }

    /**
     * The floor's people, on the engine's neutral side, which nobody is the enemy of; the things lying on its floor;
     * and the quests it starts with.
     */
    private void peopleOn(DukeGame game) {
        people.clear();
        var story = floors.story();
        var logic = game.getLogic();
        quests.frame(logic.getFrame());
        for (var placed : story.npcs()) {
            var template = logic.getThingFactory().findTemplate(placed.kind());
            if (template != null) {
                var person = logic.spawn(template, ground(logic, placed),
                        uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX);
                people.add(new Person(person.getId(), placed.kind()));
            }
        }
        for (var placed : story.items()) {
            var item = settings.loot().stream().filter(loot -> loot.id().equals(placed.kind())).findFirst();
            item.ifPresent(loot -> uz.dukeengine.rpg.item.GroundItem.lay(logic,
                    loot.liesAs(settings.lootDrops().template()), loot, ground(logic, placed),
                    dungeonPlayer.getIndex()));
        }
        for (var quest : story.quests()) {
            quests.start(quest);
        }
        marks();
    }

    /** The middle of a placed thing's cell, on the ground. */
    private static uz.dukeengine.core.math.Coord3D ground(uz.dukeengine.core.GameLogic logic,
            uz.dukeengine.dungeon.map.StaticMap.Placed placed) {
        var at = GeneratedDungeon.Placement.atCell(placed.x(), placed.y());
        var flat = new uz.dukeengine.core.math.Coord3D(at.x(), at.y(), 0f);
        return new uz.dukeengine.core.math.Coord3D(at.x(), at.y(), logic.groundHeight(flat));
    }

    /** A death in the world, heard as it is reaped: every KILL of its kind counts it. */
    public void died(uz.dukeengine.core.event.ObjectDied death) {
        quests.died(death.templateName(), bags());
    }

    /**
     * The player right-clicked a person: his hero talks to them now if he is near enough, or walks over and talks
     * when he is — and is told what they say even if he has heard it.
     */
    public void talkTo(DukeGame game, int playerIndex, ObjectId person) {
        var seat = seatOf(playerIndex);
        var who = people.stream().filter(each -> each.id().equals(person)).findFirst().orElse(null);
        var hero = seat == null || seat.heroId == null ? null : game.getLogic().findObject(seat.heroId);
        var there = who == null ? null : game.getLogic().findObject(person);
        if (hero == null || there == null || hero.isEffectivelyDead()) {
            return;
        }
        quests.frame(game.getLogic().getFrame());
        if (near(hero, there, who)) {
            seat.talkingTo = null;
            quests.talk(who.npc(), seat.progress.getLoot(), true);
            marks();
            return;
        }
        seat.talkingTo = person;
        orders.attackMove(playerIndex, null);
        uz.dukeengine.combat.module.Errand.giveUpAll(hero);
        var legs = hero.getLocomotor();
        if (legs != null) {
            legs.moveTo(there.getPosition());
        }
    }

    /**
     * Every frame the floor is played: experience earned paid to every hero standing, and every {@code CheckFrames}
     * the bags, the zones and the people looked at.
     */
    private void story(DukeGame game) {
        var logic = game.getLogic();
        quests.frame(logic.getFrame());
        int paid = quests.takeExperience();
        if (paid > 0) {
            for (var seat : seats) {
                var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
                var worth = hero == null ? null : hero.findModule(uz.dukeengine.combat.module.ExperienceModule.class);
                if (worth != null) {
                    worth.addExperience(paid);
                }
            }
        }
        if (logic.getFrame() % settings.story().dialogue().checkFrames() != 0) {
            marks(); // a death this frame may have sent him back to somebody
            return;
        }
        quests.holding(bags());
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        for (var seat : seats) {
            var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
            if (hero == null || hero.isEffectivelyDead()) {
                continue;
            }
            for (var zone : floors.story().zones()) {
                if (zone.holds(hero.getPosition().x(), hero.getPosition().y(), cell)) {
                    quests.reached(zone.name(), seat.progress.getLoot());
                }
            }
        }
        for (var who : people) {
            var there = logic.findObject(who.id());
            for (var seat : seats) {
                var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
                if (there == null || hero == null || hero.isEffectivelyDead() || !near(hero, there, who)) {
                    continue;
                }
                boolean asked = who.id().equals(seat.talkingTo);
                if (asked) {
                    seat.talkingTo = null;
                }
                if (quests.talk(who.npc(), seat.progress.getLoot(), asked)) {
                    break; // one talk to a person at a time
                }
            }
        }
        marks();
    }

    private boolean near(GameObject hero, GameObject there, Person who) {
        var npc = settings.story().npc(who.npc());
        float reach = npc == null ? 0f : npc.radius();
        float dx = hero.getPosition().x() - there.getPosition().x();
        float dy = hero.getPosition().y() - there.getPosition().y();
        return dx * dx + dy * dy <= reach * reach;
    }

    private void marks() {
        marks = people.stream().map(who -> new Mark(who.id().value(), quests.markOf(who.npc()))).toList();
    }

    private List<uz.dukeengine.rpg.item.LootBag> bags() {
        return seats.stream().map(seat -> seat.progress.getLoot()).toList();
    }

    /** The run's quests: what is going, finished and heard. */
    public QuestLog getQuests() {
        return quests;
    }

    /** What each person on the floor shows over their head — from any thread. */
    public List<Mark> getMarks() {
        return marks;
    }
}
