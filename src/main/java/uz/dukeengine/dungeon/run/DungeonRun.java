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
        /** The frame his belt is ready again from, and the bag's count of changes when its potions were last stacked. */
        volatile int beltReady;
        int stackedAt = -1;
        /** His purse: the gold his kills have paid him and his trading left him. */
        int gold;
        /** The souls his book has drunk (SOUL_DRINK), carried from body to body and saved. See {@link #medallion}. */
        int souls;
        /** Whether the medallion has drunk him whole: his look is darkened. See the CURSE act. */
        boolean cursed;
        /** The body whose skill book his souls were last put into: a new body (a floor laid) is given them again. */
        ObjectId soulsIn;
        /** What his souls add to his blows, as his book last said: for the card, from any thread. */
        volatile int soulPercent;
        /** How many of the conversations said this run his machine has shown to the end (or had skipped). */
        int read;
        /** Where his hero last stood inside the gate kept now, and the frame he was last told it is shut. */
        uz.dukeengine.core.math.Coord3D lastInside;
        int toldShutAt = Integer.MIN_VALUE / 2;

        Seat(GamePlayer player, HeroProgress progress, SkillRanks learnt, String hero) {
            this.player = player;
            this.progress = progress;
            this.learnt = learnt;
            this.hero = hero;
        }
    }

    private final GamePlayer dungeonPlayer;
    private final DungeonSettings settings;
    /** Food and fire: the world's trees, bushes, wells and fires, and each hero's work at them. See {@link Camp}. */
    private final Camp camp;
    /** The map's scenery a loaded save says was felled, taken away again as its floor is laid. */
    private List<Integer> felled = List.of();
    /** Each hero's horse, and whether he rides it. See {@link Stable}. */
    private final Stable stable;
    /** Whether a loaded save's hero rode: he is put back on his horse as its map is laid. */
    private boolean ridingOnLoad;
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
    /**
     * Where the floor's key lies and the frame it dropped, while the step is to take it; null otherwise. Written on the
     * simulation's thread as it drops, read by the window for its arrow and its "Kalit tushdi!".
     */
    private volatile KeyLying keyLying;

    /** The floor's key lying at {@code (x, y, z)}, dropped at {@code frame}. */
    public record KeyLying(float x, float y, float z, int frame) {
    }

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
        this.script = new uz.dukeengine.dungeon.story.Script(settings.story());
        this.fight = new BossFight(settings.story());
        script.judgedBy(this::holds);
        this.orders = orders;
        this.dungeonPlayer = dungeonPlayer;
        this.floors = floors;
        this.settings = settings;
        this.drops = drops;
        this.themes = settings.themes();
        this.look = lookOf(null);
        this.depth = floors.firstDepth();
        this.camp = new Camp(settings);
        this.stable = new Stable(settings);
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
        this.lastCheckpoint = null; // a game chosen afresh goes back to nothing of the last one's
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

    /** Where a story's SAVE writes the run, into the slot it names — the act's end. Nothing until somebody asks. */
    public void onSaveInto(java.util.function.BiConsumer<String, SaveGame> told) {
        this.keeper = told;
    }

    private java.util.function.BiConsumer<String, SaveGame> keeper = (slot, save) -> { };
    /** The last checkpoint written or loaded: where a campaign's death goes back to. Null before there is one. */
    private SaveGame lastCheckpoint;

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
        seat.gold = save.gold();
        seat.souls = save.souls();
        seat.cursed = !save.givenTo().isBlank();
        givenTo = save.givenTo();
        lastCheckpoint = save;
        // And its quests, before the floor is laid: the map's starting quests are then going or finished already,
        // and are not started again. A save from before quests were saved starts them, as a new run does.
        visited.clear();
        visited.addAll(save.places()); // the places found, as the save left them
        felled = save.felled(); // and the trees felled, taken away again as the floor is laid
        ridingOnLoad = save.mounted(); // and on his horse, if he rode
        visitedNow = List.copyOf(visited);
        if (save.quests() == null) {
            quests.reset();
        } else {
            quests.restore(save.quests());
        }
        // A save on a map the act left (the seamless campaign folded jang, poytaxt and mashq into the world) loads
        // as the joint that took its place: its flags added, its quests started, put in its At.
        var moved = settings.replacing(save.stage());
        var kept = save.script();
        if (moved != null) {
            var flags = new ArrayList<String>(kept == null ? List.of() : kept.flags());
            moved.flags().stream().filter(flag -> !flags.contains(flag)).forEach(flags::add);
            kept = new uz.dukeengine.dungeon.story.Script.State(kept == null ? List.of() : kept.fired(), flags,
                    List.of(), List.of());
        }
        if (kept == null) {
            script.reset();
        } else {
            script.restore(kept);
        }
        if (moved != null) {
            moved.quests().forEach(quests::start);
            skippedTo = moved.at();
        }
        loading = true;
    }

    /**
     * The campaign from its joint {@code n}, 1 its first — the {@code --act1=N} debug arg: the joint's map, the
     * campaign's hero with the experience, gold, flags and things (worn where their slot is free) the joints before
     * it would have given. Recorded as {@link #resume} is: the floor is laid when the game starts.
     */
    public void skipTo(int n) {
        skipTo(settings.campaign(), n, null);
    }

    /**
     * An act of the campaign from its joint {@code n}, 1 its first: a new game's act (n 1), or the {@code --actN=}
     * debug arg's joint. An act that follows another carries on from {@code carried}, that act's end save — its
     * flags, and its hero's level as the flag {@code <Follows>Level=N} — or, with none, from its DefaultLevel: its own
     * hero is new (see {@code docs/plan/2026-10-15-act2.md}).
     */
    public void skipTo(uz.dukeengine.dungeon.story.Campaign campaign, int n, SaveGame carried) {
        var joint = campaign.joints().get(Math.clamp(n, 1, campaign.joints().size()) - 1);
        var chosen = Floors.named(joint.map(), floors.seed(), settings, false);
        var items = new ArrayList<SaveGame.Item>();
        for (var id : joint.items()) {
            settings.loot().stream().filter(item -> item.id().equals(id)).findFirst()
                    .ifPresent(item -> items.add(new SaveGame.Item(-1, item.id(), 1, item.value(), 0)));
        }
        var flags = new ArrayList<String>();
        if (!campaign.follows().isBlank()) {
            if (carried != null && carried.script() != null) {
                // What happened, but not that it was the act before: its own flag (Act1 of act1) would wake its story.
                var own = Character.toUpperCase(campaign.follows().charAt(0)) + campaign.follows().substring(1);
                carried.script().flags().stream().filter(flag -> !flag.equals(own)).forEach(flags::add);
            }
            flags.add(campaign.levelFlag(carried == null ? campaign.defaultLevel()
                    : settings.levelling().levelFor(carried.experience())));
        }
        joint.flags().stream().filter(flag -> !flags.contains(flag)).forEach(flags::add);
        resume(new SaveGame(chosen.seed(), joint.map(), chosen.firstDepth(), campaign.hero(), joint.experience(),
                List.of(), items, null, new uz.dukeengine.dungeon.story.Script.State(List.of(), flags,
                        List.of(), List.of()), joint.gold()), chosen);
        joint.quests().forEach(quests::start); // the quests that joint has going
        skippedTo = joint.at();
    }

    /** The zone a skipped-to joint puts the heroes in once its floor is laid; blank for none. */
    private String skippedTo = "";

    /** Told when the story ends the game (MENU): the window goes back to the front menu. Nothing until asked. */
    public void onTheEnd(Runnable told) {
        this.theEnd = told;
    }

    private Runnable theEnd = () -> { };

    /** Told the music cue a story wants played (its credits'): the window plays it. Nothing until asked. */
    public void onMusic(java.util.function.Consumer<String> told) {
        this.music = told;
    }

    private java.util.function.Consumer<String> music = cue -> { };

    /** The map's own zones, and the places the story adds to it (Place blocks). */
    private List<uz.dukeengine.dungeon.map.StaticMap.Zone> zones() {
        var placed = settings.placesOn(floors.stageName());
        if (placed.isEmpty()) {
            return floors.story().zones();
        }
        var all = new ArrayList<>(floors.story().zones());
        all.addAll(placed);
        return all;
    }

    /**
     * The run as a save this moment, mid-floor or not: the hero as he is now, on the floor he entered — Saqlash, and
     * leaving the game. {@code null} for a party, and while the run is over or a party gathers: the save from the
     * floor's entry stands. Asked with the simulation paused.
     */
    public SaveGame saveNow() {
        // Nor mid-fight: a boss's phase or a beaten foe is not saved, and the save from the hall's entry stands —
        // see docs/plan/2026-10-09-final-fight.md.
        return state == State.RUNNING && gathering == null && !fight.on() ? save() : null;
    }

    /** The run as a save, the first seat's hero in it; {@code null} for a party or a seat with no hero. */
    private SaveGame save() {
        var seat = seats.getFirst();
        if (seats.size() != 1 || seat.hero == null) {
            return null;
        }
        return SaveGame.of(floors.seed(), floors.stageName(), depth, seat.hero, seat.progress.getExperience(),
                seat.learnt, seat.progress.getLoot(), quests.state(), script.state(), seat.gold)
                .withMedallion(seat.souls, givenTo).withPlaces(visited)
                .withFelled(lastLogic == null ? List.of() : lastLogic.getRemovedScenery())
                .withMounted(stable.mounted(seat.player.getIndex()));
    }

    /** The first seat's hero on the floor now, by its id, or null: the menu's backdrop keeps him out of sight. */
    public ObjectId heroId() {
        return seats.getFirst().heroId;
    }

    /** The dungeon's side, which every monster the floors place is on. */
    public int dungeonSide() {
        return dungeonPlayer.getIndex();
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

    /** A painted map's roads become the logic grid's step costs, so walkers keep to them (E52). */
    private static void roadsOf(DukeGame game, Object map) {
        if (game.getLogic() != null && game.getLogic().getPathGrid() != null) {
            uz.dukeengine.dungeon.stage.Roads.lay(game.getLogic().getPathGrid(), map);
        }
    }

    /** The floor the world was built around, with everyone on it. */
    private void lay(DukeGame game, GeneratedDungeon floor) {
        look = lookOf(floor);
        var looks = looksOf(floor);
        if (looks != null) {
            // The world was built around this floor's grid before the run knew its seed and depth; the grid stays
            // as it was, and the client is told what each of its cells wears.
            game.applyMapTerrain(game.getTerrain(), looks);
            roadsOf(game, looks);
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
        fight.reset();
        for (int i = 0; i < seats.size(); i++) {
            var bag = seats.get(i).progress.getLoot();
            bag.removeAll(ItemUse.UNLOCK);
            var hero = placed.heroes().get(i);
            if (hero != null) {
                hero.addModule(new KeyDrop(hero, bag, settings.lootDrops().template(), dungeonPlayer.getIndex()));
            }
        }
        mission = floorMission() ? missionOf(game.getLogic(), floor, placed, settings, dungeonPlayer.getIndex()) : null;
        // Every monster the floor placed pays the hero who kills it, by its level. See Bounty.
        var paying = new ArrayList<GameObject>(placed.monsters());
        if (placed.boss() != null) {
            paying.add(placed.boss());
        }
        for (var monster : paying) {
            monster.addModule(new Bounty(monster, uz.dukeengine.rpg.hero.LevelBonus.levelOf(monster), this::pay));
        }
        peopleOn(game);
        var paint = floors.paint();
        camp.lay(game.getLogic(), floors.stageName(), floors.seed(), zones(), dungeonPlayer.getIndex(), heroLevel(),
                paint == null ? List.of() : paint.scenery().stream().map(piece -> piece.model()).toList(),
                loading ? felled : List.of());
        // Whoever rode as the last map ended rides on, where he may; a loaded save's rider is back on his horse.
        stable.lay(floors.stageName(), zones(), !loading ? stable.riding()
                : ridingOnLoad ? List.of(seats.getFirst().player.getIndex()) : List.of());
        sky = null;
        holding = null;
        trails.clear();
        beams.clear();
        seats.forEach(seat -> seat.lastInside = null);
        var gate = gate();
        if (loading && skippedTo.isBlank() && gate != null && !gate.start().isBlank()) {
            skippedTo = gate.start(); // back from a save or a death under a gate: where the story has him
        }
        if (loading && skippedTo.isBlank() && lastTowerFound() != null) {
            skippedTo = lastTowerFound(); // back from a save or a death: at the last tower he found, not the way in
        }
        if (!skippedTo.isBlank()) {
            // A joint skipped to on a great map: he starts where that joint's story is, not at the map's way in.
            act(game, new uz.dukeengine.dungeon.story.Trigger.Action(uz.dukeengine.dungeon.story.Trigger.Act.PLACE,
                    "", 1, skippedTo, 1, 0f, ""));
            var there = zoneNamed(skippedTo);
            if (there != null) {
                float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
                game.moveView(there.middleX(cell), there.middleY(cell));
            }
            skippedTo = "";
        }
        finished.clear();
        said = null;
        track(game);
        var save = loading ? null : save();
        loading = false;
        if (save != null) {
            lastCheckpoint = save;
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
            keyLying = step == Mission.Step.TAKE ? keyDropped(game, said != null) : null;
            said = step;
            saidSince = frame;
        }
        finished.removeIf(done -> frame - done.since() >= run.doneFrames());
        var words = mission == null ? run.killBossWord() : words(mission, run, step);
        var steps = new ArrayList<>(finished);
        steps.add(new Tracker.Step(words, false, saidSince));
        tracker = descendAtFrame > 0 ? Tracker.NONE
                : new Tracker(floorMission() ? listed(new Tracker.Entry(Tracker.Kind.MAIN, run.missionTitle(), steps))
                        : quests.entries());
        questsNow = quests.state();
        storyNow = floors.story();
    }

    /**
     * The floor's key, just come to lie on the floor — dropped early by the floor's own, by a hero who fell with it, or
     * laid at the way in on a floor with nobody outside — or null where none lies. A drop ({@code heard}) is heard: the
     * run's KeyDroppedSound, an event out of the checksum; the key laid with the floor is not news.
     */
    private KeyLying keyDropped(DukeGame game, boolean heard) {
        var logic = game.getLogic();
        for (var thing : logic.getObjects()) {
            var lying = thing.findModule(uz.dukeengine.rpg.item.GroundItem.class);
            if (lying != null && lying.getHolding() != null && lying.getHolding().use() == ItemUse.UNLOCK) {
                if (heard && !settings.run().keyDroppedSound().isBlank()) {
                    logic.sound(settings.run().keyDroppedSound(), thing, 0);
                }
                var at = thing.getPosition();
                return new KeyLying(at.x(), at.y(), at.z(), heard ? logic.getFrame() : Integer.MIN_VALUE / 2);
            }
        }
        return null;
    }

    /** Where the floor's key lies while the step is to take it, or null: for the window's arrow, from any thread. */
    public KeyLying keyLying() {
        return keyLying;
    }

    /**
     * Whether this map runs the floor mission — key, gate, boss — as its tracker's main entry: every floor of the
     * descent does, and a stage only when its file says {@code FloorMission = Yes}. A story map lists its own quests.
     */
    private boolean floorMission() {
        return floors.descends() || floors.story().floorMission();
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
        // The key drops with a few still standing (the owner, 2026-10-15): the run's leeway, the engine's count (E61).
        return Mission.begin(world, key, settings.lootDrops().template(), floorOwner, placed.gate(), outside,
                wayIn(floor, placed), settings.run().keyLeeway(outside.size()));
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
        lastLogic = game.getLogic();
        if (gathering != null) {
            return; // nobody is on the floor until everybody is
        }
        switch (state) {
            case RUNNING -> whileRunning(game);
            case DEAD -> whileDead(game);
            case WON -> whileWon(game);
        }
        if (state != State.RUNNING) {
            target = null; // the banner has the screen, as the tracker gives it up
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
        if (bossId != null && logic.findObject(bossId) == null && descendAtFrame == 0 && storied()) {
            // ★ A map with a story is not won by its boss falling: the story says what follows — a trigger on her
            // death, its WIN, a MAP — and no banner says "won" for it (the owner, 2026-10-09).
            bossId = null;
        }
        if (bossId != null && logic.findObject(bossId) == null && descendAtFrame == 0) {
            // Nothing below this one: the last boss is the end of the game rather
            // than the door to the next floor. It ends with no banner (the owner, 2026-10-09): the run holds for
            // its VictoryFrames and starts again.
            if (floors.lastDepth() > 0 && depth >= floors.lastDepth()) {
                state = State.WON;
                endedFrame = logic.getFrame();
                tracker = Tracker.NONE;
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
        scripted(game);
        intro(game);
        gated(game);
        if (state != State.RUNNING) {
            return; // a trigger won it: the banner has the screen
        }
        track(game);
        showStatus(game);
    }

    /**
     * Whether a trigger's condition holds this moment: a flag, what a hero carries or wears (any hero of a party), a
     * quest going or done, a creature standing on the floor or none.
     */
    private boolean holds(uz.dukeengine.dungeon.story.Trigger.Condition condition) {
        var what = condition.target();
        return switch (condition.is()) {
            case FLAG_SET, FLAG_UNSET -> script.flagsAlone(condition);
            case LACKS -> !holds(new uz.dukeengine.dungeon.story.Trigger.Condition(
                    uz.dukeengine.dungeon.story.Trigger.Is.HOLDS, what));
            case HOLDS, WEARS -> bags().stream().anyMatch(bag -> {
                for (int place = 0; place < bag.slots().size(); place++) {
                    var item = bag.at(place);
                    boolean worn = !bag.wears() || place >= bag.backpackSize();
                    if (item != null && item.id().equals(what)
                            && (condition.is() == uz.dukeengine.dungeon.story.Trigger.Is.HOLDS || worn)) {
                        return true;
                    }
                }
                return false;
            });
            case QUEST_ACTIVE -> quests.active().stream().anyMatch(each -> each.quest().equals(what));
            case QUEST_DONE -> quests.finished().contains(what);
            case ALIVE, DEAD -> {
                boolean alive = lastLogic != null && lastLogic.getObjects().stream().anyMatch(thing ->
                        thing.getTemplate().name().equals(what) && !thing.isEffectivelyDead());
                yield alive == (condition.is() == uz.dukeengine.dungeon.story.Trigger.Is.ALIVE);
            }
        };
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
        var picked = orders.watchedBy(mine.player.getIndex());
        var creature = picked == null ? null : game.getLogic().findObject(picked);
        var fellow = creature == null ? null : seatOf(creature.getPlayerIndex());
        target = Target.of(creature, Skills.heroOf(game.getLogic(), mine.player.getIndex()),
                fellow != null && Skills.heroOf(game.getLogic(), fellow.player.getIndex()) == creature
                        ? fellow.progress : null, settings);
        sheet = Sheet.of(Skills.heroOf(game.getLogic(), mine.player.getIndex()), mine.progress, mine.gold, mine.souls,
                settings);
    }

    /** What the target frame shows: this machine's player's pick, worked out each frame, or null for nothing. */
    private volatile Target target;

    /** What the character sheet shows of this machine's hero now, or null: read by the window. */
    private volatile Sheet sheet;

    /** The run's quests as they stood at the end of the last frame: read by the quest log, from any thread. */
    private volatile QuestLog.State questsNow = new QuestLog.State(List.of(), List.of(), List.of());

    /** The story of the map being played -- its zones and people -- as the map screen reads it. */
    private volatile uz.dukeengine.dungeon.story.MapStory storyNow = uz.dukeengine.dungeon.story.MapStory.NONE;

    /**
     * Every zone a hero has stood in this run, in the order they were first come to: the map's places he has found.
     * The simulation's own list, saved with the run ({@code SaveGame.places}), and a copy of it for the window.
     */
    private final List<String> visited = new ArrayList<>();
    private volatile List<String> visitedNow = List.of();

    /** What the target frame shows now, or null: read by the window. */
    public Target getTarget() {
        return target;
    }

    /** What the character sheet shows of this machine's hero now, or null for no hero: read by the window. */
    public Sheet getSheet() {
        return sheet;
    }

    /** The run's quests as the last frame left them -- going, finished, heard -- from any thread. */
    public QuestLog.State questsNow() {
        return questsNow;
    }

    /** The story of the map being played, its zones and people: from any thread. */
    public uz.dukeengine.dungeon.story.MapStory storyNow() {
        return storyNow;
    }

    /** Every zone a hero has stood in this run, in the order first come to: from any thread. */
    public List<String> visited() {
        return visitedNow;
    }

    /**
     * The card itself — the panel's half of the line, and always about his own hero, whatever he has picked out to
     * look at: that is the target frame's ({@link Target}). A single-hero game's panel is the hero's, as WoW's and
     * Diablo's are.
     *
     * <p>With no hero of his on the floor — a party's player whose hero has gone — the bar keeps the floor and
     * loses the creature, and its buttons go dim. A dying hero is still the hero the bar is about.
     */
    private String card(DukeGame game, Seat seat) {
        int player = seat.player.getIndex();
        var hero = Skills.heroOf(game.getLogic(), player);
        if (hero != null) {
            return HeroStatus.of(hero, seat.progress, depth, floors.lastDepth(), settings,
                    game.getLogic().getFrame(), look, orders.isHolding(player), seat.learnt);
        }
        return HeroStatus.nothing(depth, floors.lastDepth(), settings,
                seat.progress.getLoot().noteAt(game.getLogic().getFrame()), look, false);
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
        if (lastCheckpoint != null && seats.size() == 1 && settings.inCampaign(floors.stageName())) {
            // ★ A campaign is not a roguelike: a death goes back to the last floor entered, as he entered it — the
            // checkpoint the autosave holds — not to the opening battle with nothing. See docs/plan/2026-10-15-act1.md.
            var back = lastCheckpoint;
            resume(back, Floors.resumed(back, settings));
            openingIsStale = false;
            cued.clear();
            endCutscene(game);
            descend(game);
            state = State.RUNNING;
            game.setBanner("");
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
        if (storied()) {
            // ★ A map with a story, won by its WIN, stays won: nothing starts again by itself — the
            // owner's playtest of the talk map, where a won run cleared away its own story five seconds later. The
            // story goes on meanwhile, so a trigger may still say what follows (the next map), and the
            // player leaves by the menu.
            story(game);
            scripted(game);
            return;
        }
        if (game.getLogic().getFrame() - endedFrame < settings.run().victoryFrames()) {
            return;
        }
        begin(game);
    }

    /**
     * Whether the map being played has a story: people, zones, things laid or quests of its own, or a trigger that
     * names it. The descent and a plain stage have none — a stage is learnt and won again — and the open world has one.
     */
    private boolean storied() {
        if (floors.descends()) {
            return false; // a campaign's descent too: its bosses open the next floor, and its triggers say the rest
        }
        var story = floors.story();
        var name = floors.stageName();
        return !story.npcs().isEmpty() || !story.zones().isEmpty() || !story.items().isEmpty()
                || !story.quests().isEmpty()
                || name != null && settings.story().triggers().stream().anyMatch(each -> each.map().equals(name));
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
        for (var seat : seats) {
            seat.gold = 0; // and his purse: a run that ends keeps nothing
            seat.souls = 0; // and his souls, and his curse
            seat.cursed = false;
        }
        givenTo = "";
        quests.reset(); // and every quest: the map that starts them starts them again
        visited.clear(); // and the places found
        visitedNow = List.of();
        script.reset(); // and every trigger: the story begins again with the run
        cued.clear();
        endCutscene(game);
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
        var looks = looksOf(floor);
        game.applyMapTerrain(terrainOf(floor), looks);
        roadsOf(game, looks);

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
    /** The map being played, by its name, or null for the descent. */
    public String getStageName() {
        return floors.stageName();
    }

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
    private record Person(ObjectId id, String npc, float homeX, float homeY) {
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
        lastLogic = game.getLogic();
        var story = floors.story();
        var logic = game.getLogic();
        quests.frame(logic.getFrame());
        for (var placed : story.npcs()) {
            var template = logic.getThingFactory().findTemplate(placed.kind());
            if (template != null) {
                var person = logic.spawn(template, ground(logic, placed),
                        uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX);
                people.add(new Person(person.getId(), placed.kind(), person.getPosition().x(),
                        person.getPosition().y()));
            }
        }
        var items = settings.items();
        for (var placed : story.items()) {
            // One of the data's own, a base, a unique or a potion by name — or a treasure, rolled at the hero's level
            // as a chest's drop (docs/plan/2026-10-15-loot.md).
            var loot = placed.kind().equals(items.rules().treasure()) && items.active()
                    ? items.drop(logic.random().nextInt(0, (1 << 30) - 1), heroLevel(), uz.dukeengine.dungeon.loot.Items.Source.CHEST)
                    : items.resolve(placed.kind());
            if (loot != null) {
                uz.dukeengine.rpg.item.GroundItem.lay(logic, loot.liesAs(settings.lootDrops().template()), loot,
                        ground(logic, placed), dungeonPlayer.getIndex());
            }
        }
        for (var quest : story.quests()) {
            quests.start(quest);
        }
        restock(); // every merchant's stock is the data's again
        script.mapStarted(floors.stageName());
        // A campaign's descent says how deep it has come: DEPTH, the floor's depth. See docs/plan/2026-10-15-act1.md.
        script.heard(uz.dukeengine.dungeon.story.Trigger.When.DEPTH, Integer.toString(depth), 0);
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
        drunk(death);
        camp.died(lastLogic, death);
        script.heard(uz.dukeengine.dungeon.story.Trigger.When.DIED, death.templateName(), 0);
        // A camp cleared: every zone with none of the dungeon's creatures standing in it any more is heard CLEARED
        // (a once-only trigger hears it once). See docs/plan/2026-10-15-act2.md.
        if (lastLogic != null) {
            float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
            for (var zone : zones()) {
                boolean held = lastLogic.getObjects().stream().anyMatch(thing -> thing.getBody() != null
                        && !thing.isEffectivelyDead() && thing.getPlayerIndex() == dungeonPlayer.getIndex()
                        && !camp.isAnimal(thing) && zone.holds(thing.getPosition().x(), thing.getPosition().y(), cell));
                if (!held) {
                    script.heard(uz.dukeengine.dungeon.story.Trigger.When.CLEARED, zone.name(), 0);
                }
            }
        }
    }

    // ---- triggers and cutscenes: see docs/plan/2026-10-08-story-scripting.md ----

    /** The run's triggers: what has fired, the flags, what waits. */
    private final uz.dukeengine.dungeon.story.Script script;
    /** The floor's bosses: who is beaten, and which phase each is in. See docs/plan/2026-10-09-final-fight.md. */
    private final BossFight fight;
    /** The cutscene playing, or null; the shot it is on, and that shot's frames left (-1 before it begins). */
    private uz.dukeengine.dungeon.story.Cutscene cutscene;
    private int shotAt;
    private int shotLeft;
    private volatile boolean inCutscene;
    /** A map a trigger sent the heroes on to, laid once the frame's triggers are done; null for none. */
    private String nextMap;

    /**
     * Every frame the floor is played, after the quests: what the quests told, heard; every trigger due, done; the
     * cutscene a frame on; and a map a trigger asked for, laid.
     */
    private void scripted(DukeGame game) {
        for (var event : quests.takeEvents()) {
            script.heard(event.when(), event.target(), event.step());
        }
        fight.tick(game.getLogic(), script, (action, caller) -> act(game, action, caller),
                uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX);
        for (var trigger : script.due()) {
            for (var action : trigger.actions()) {
                act(game, action);
            }
        }
        for (var event : quests.takeEvents()) {
            script.heard(event.when(), event.target(), event.step()); // what the actions did: due next frame
        }
        cue(game);
        shoot(game);
        marks();
        if (nextMap != null) {
            var name = nextMap;
            nextMap = null;
            try {
                floors = Floors.named(name, floors.seed(), settings, false);
            } catch (RuntimeException e) {
                java.util.logging.Logger.getLogger(DungeonRun.class.getName())
                        .warning("a trigger asked for the map " + name + ", which cannot be played: " + e.getMessage());
                return;
            }
            depth = floors.firstDepth();
            endCutscene(game);
            descend(game);
            game.setBanner(""); // a floor's "the next depth" said as its boss fell is not this map's
            if (state == State.WON) {
                // A won map's story sent the heroes on: the next chapter is played, not watched.
                state = State.RUNNING;
            }
        }
    }

    private void act(DukeGame game, uz.dukeengine.dungeon.story.Trigger.Action action) {
        act(game, action, null);
    }

    /** One action done: a trigger's, or — {@code caller} the boss — a phase's. */
    private void act(DukeGame game, uz.dukeengine.dungeon.story.Trigger.Action action, GameObject caller) {
        var logic = game.getLogic();
        var what = action.target();
        switch (action.act()) {
            case CALL -> {
                // Every one of it on the floor, on the dungeon's side — a beaten one is nobody's — walks to her side.
                var zone = zoneNamed(action.zone());
                float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
                var to = zone != null ? new uz.dukeengine.core.math.Coord3D(zone.middleX(cell), zone.middleY(cell), 0f)
                        : caller != null ? caller.getPosition() : null;
                for (var thing : logic.getObjects()) {
                    var legs = thing.getLocomotor();
                    if (to != null && legs != null && thing.getTemplate().name().equals(what)
                            && thing.getPlayerIndex() == dungeonPlayer.getIndex() && !thing.isEffectivelyDead()) {
                        legs.moveTo(to);
                    }
                }
            }
            case EFFECT -> {
                var zone = zoneNamed(action.zone());
                if (zone != null) {
                    float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
                    var flat = new uz.dukeengine.core.math.Coord3D(zone.middleX(cell), zone.middleY(cell), 0f);
                    logic.effect(what, new uz.dukeengine.core.math.Coord3D(flat.x(), flat.y(), logic.groundHeight(flat)),
                            0f);
                } else if (caller != null) {
                    logic.effect(what, caller);
                }
            }
            case WIN -> {
                if (state == State.RUNNING) {
                    state = State.WON;
                    endedFrame = logic.getFrame();
                    tracker = Tracker.NONE;
                    // The story's own words, or nothing: there is no generic "won" (the owner, 2026-10-09).
                    game.setBanner(what.isBlank() ? "" : "won|" + what);
                } else if (state == State.WON) {
                    // A story won already (the turning) says its last words — or, saying none, clears the banner for
                    // its title card: "1-qism tugadi".
                    game.setBanner(what.isBlank() ? "" : "won|" + what);
                }
            }
            case ALLY -> {
                // On the first hero's side, fighting as any monster does — for him. Grown to the floor's depth.
                var zone = zoneNamed(action.zone());
                var template = logic.getThingFactory().findTemplate(what);
                if (zone == null || template == null) {
                    return;
                }
                var cells = zone.cells();
                for (int i = 0; i < action.count(); i++) {
                    var cell = cells.get(i % cells.size());
                    var spawned = logic.spawn(template, ground(logic,
                            new uz.dukeengine.dungeon.map.StaticMap.Placed(what, cell[0], cell[1])),
                            seats.getFirst().player.getIndex());
                    Spawner.scale(spawned, settings.beforeBossLevel(depth), settings);
                }
            }
            case PLACE, KNOCK -> {
                var zone = zoneNamed(action.zone());
                if (zone == null) {
                    return;
                }
                float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
                var flat = new uz.dukeengine.core.math.Coord3D(zone.middleX(cell), zone.middleY(cell), 0f);
                var to = new uz.dukeengine.core.math.Coord3D(flat.x(), flat.y(), logic.groundHeight(flat));
                for (var thing : List.copyOf(logic.getObjects())) {
                    boolean moved = action.act() == uz.dukeengine.dungeon.story.Trigger.Act.PLACE
                            ? seats.stream().anyMatch(seat -> thing.getId().equals(seat.heroId))
                            : thing.getTemplate().name().equals(what) && !thing.isEffectivelyDead();
                    if (moved) {
                        uz.dukeengine.combat.module.Errand.giveUpAll(thing);
                        var legs = thing.getLocomotor();
                        if (legs != null && legs.isMoving()) {
                            legs.stop();
                        }
                        thing.setPosition(to);
                    }
                }
            }
            case MOVE, LEAP, FALL -> {
                // Carried over frames by the engine's ScriptedMove (E40), not legs: a slide, a hop, a fall from the
                // sky. Coming down, the Effect it names plays on the thing — its dust and its knock of the camera.
                var zone = zoneNamed(action.zone());
                if (zone == null) {
                    return;
                }
                float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
                var flat = new uz.dukeengine.core.math.Coord3D(zone.middleX(cell), zone.middleY(cell), 0f);
                var to = new uz.dukeengine.core.math.Coord3D(flat.x(), flat.y(), logic.groundHeight(flat));
                boolean heroes = uz.dukeengine.dungeon.story.Trigger.HEROES.equals(what);
                for (var thing : List.copyOf(logic.getObjects())) {
                    boolean moved = heroes ? seats.stream().anyMatch(seat -> thing.getId().equals(seat.heroId))
                            : thing.getTemplate().name().equals(what) && !thing.isEffectivelyDead();
                    if (!moved) {
                        continue;
                    }
                    uz.dukeengine.combat.module.Errand.giveUpAll(thing);
                    var move = action.act() == uz.dukeengine.dungeon.story.Trigger.Act.FALL
                            ? uz.dukeengine.core.module.ScriptedMove.fall(thing, to, action.height(), action.frames())
                            : uz.dukeengine.core.module.ScriptedMove.along(thing, to, action.frames(),
                                    action.act() == uz.dukeengine.dungeon.story.Trigger.Act.LEAP
                                            ? action.height() : 0f);
                    if (!action.landed().isBlank()) {
                        move.onLanded(() -> logic.effect(action.landed(), thing));
                    }
                }
            }
            case TITLE -> {
                var card = settings.title(what);
                if (card != null) {
                    // Drawn only, as every look is: the client shows it and says when it is gone (E42).
                    game.titleCard(card.text(), card.subtext(), card.fadeIn(), card.hold(), card.fadeOut());
                }
            }
            case CLEAR -> {
                // Taken away, not killed: no death is heard, nothing pays and nothing is drunk.
                var zone = zoneNamed(action.zone());
                float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
                for (var thing : List.copyOf(logic.getObjects())) {
                    if (zone != null && thing.getBody() != null && thing.getPlayerIndex() == dungeonPlayer.getIndex()
                            && zone.holds(thing.getPosition().x(), thing.getPosition().y(), cell)) {
                        logic.destroyObject(thing);
                    }
                }
            }
            case CHECKPOINT -> {
                var save = save();
                if (save != null) {
                    lastCheckpoint = save;
                    checkpoint.accept(save);
                }
            }
            case GROW -> {
                int level = action.count();
                for (var flag : script.state().flags()) {
                    if (flag.matches("[a-z0-9]+Level=[0-9]+")) {
                        level = Integer.parseInt(flag.substring(flag.indexOf('=') + 1));
                    }
                }
                for (var thing : List.copyOf(logic.getObjects())) {
                    if (thing.getTemplate().name().equals(what) && !thing.isEffectivelyDead()) {
                        // ponytail: the floor's own level bonus is swapped out, but its body's growth stays and this
                        // one's is added to it; grow from the template's health if the cursed hero ends up too stout.
                        var had = thing.findModule(uz.dukeengine.rpg.hero.LevelBonus.class);
                        if (had != null) {
                            thing.removeModule(had);
                        }
                        Spawner.scale(thing, level, settings);
                    }
                }
            }
            case LAY -> {
                var zone = zoneNamed(action.zone());
                var loot = zone == null ? null : settings.items().resolve(what);
                if (loot != null) {
                    uz.dukeengine.rpg.item.GroundItem.lay(logic, loot.liesAs(settings.lootDrops().template()), loot,
                            ground(logic, new uz.dukeengine.dungeon.map.StaticMap.Placed(what, zone.x(), zone.y())),
                            dungeonPlayer.getIndex());
                }
            }
            case BATTLE -> {
                var battle = settings.battle(what);
                if (battle != null) {
                    Battles.lay(logic, battle, this::zoneNamed, seats.getFirst().player.getIndex(),
                            dungeonPlayer.getIndex(), settings, depth, this::pay);
                }
            }
            case POSE -> {
                boolean heroes = uz.dukeengine.dungeon.story.Trigger.HEROES.equals(what);
                for (var thing : List.copyOf(logic.getObjects())) {
                    if (heroes ? seats.stream().anyMatch(seat -> thing.getId().equals(seat.heroId))
                            : thing.getTemplate().name().equals(what) && !thing.isEffectivelyDead()) {
                        var legs = thing.getLocomotor();
                        if (legs != null && legs.isMoving()) {
                            legs.stop();
                        }
                        thing.busy(Battles.clipFor(settings, thing.getTemplate().name(), action.clip(),
                                action.orElse()), action.frames());
                    }
                }
            }
            case BEAM -> {
                var zone = zoneNamed(action.zone());
                if (zone == null) {
                    return;
                }
                var foot = groundAt(logic, zone);
                var top = new uz.dukeengine.core.math.Coord3D(foot.x(), foot.y(), foot.z() + action.height());
                if (what.isBlank()) {
                    beams.add(new Lit(logic.beam(action.drawn(), foot, top, 1f), logic.getFrame() + action.frames()));
                } else {
                    for (var thing : logic.getObjects()) {
                        if (thing.getTemplate().name().equals(what) && !thing.isEffectivelyDead()) {
                            var at = thing.getPosition();
                            beams.add(new Lit(logic.beam(action.drawn(), new uz.dukeengine.core.math.Coord3D(at.x(), at.y(),
                                    at.z() + Battles.HANDS), top, 1f), logic.getFrame() + action.frames()));
                        }
                    }
                }
            }
            case SKY -> {
                var zone = zoneNamed(action.zone());
                if (zone != null) {
                    var foot = groundAt(logic, zone);
                    sky = new uz.dukeengine.core.math.Coord3D(foot.x(), foot.y(), foot.z() + action.height());
                }
            }
            case STREAK -> {
                var zone = zoneNamed(action.zone());
                if (zone == null) {
                    return;
                }
                sky = null; // let go of: the streak carries him from here
                var to = groundAt(logic, zone);
                double bearing = Math.toRadians(action.bearing());
                var from = new uz.dukeengine.core.math.Coord3D(to.x() + action.lean() * (float) Math.cos(bearing),
                        to.y() + action.lean() * (float) Math.sin(bearing), to.z() + action.height());
                for (var seat : seats) {
                    var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
                    if (hero == null) {
                        continue;
                    }
                    uz.dukeengine.combat.module.Errand.giveUpAll(hero);
                    hero.setPosition(from);
                    var move = uz.dukeengine.core.module.ScriptedMove.along(hero, to, action.frames(), 0f);
                    if (!action.landed().isBlank()) {
                        move.onLanded(() -> logic.effect(action.landed(), hero));
                    }
                    if (!action.drawn().isBlank()) {
                        trails.add(new Trail(hero.getId(), action.drawn(), logic.getFrame() + action.frames()));
                    }
                }
            }
            case SHOCKWAVE -> {
                var wave = settings.shockwave(what);
                var zone = zoneNamed(action.zone());
                if (wave != null && zone != null) {
                    Battles.shockwave(logic, wave, groundAt(logic, zone), dungeonPlayer.getIndex(),
                            settings.combat().stunLook());
                }
            }
            case HOLD -> holding = action.zone();
            case SOUND -> {
                var zone = zoneNamed(action.zone());
                if (zone != null) {
                    logic.sound(what, groundAt(logic, zone), 0); // heard, not decided: an event, out of the checksum
                }
            }
            case RELEASE -> holding = null;
            case MENU -> theEnd.run();
            case CREDITS -> {
                // Drawn only (E49): the window rolls them, Esc or a click skips, and once they are gone it goes to the
                // front menu on its own thread.
                var credits = settings.credits();
                if (!credits.music().isBlank()) {
                    music.accept(credits.music());
                }
                game.creditsRoll(credits.rolled(), credits.seconds(), theEnd);
            }
            case REMOVE -> {
                for (var thing : List.copyOf(logic.getObjects())) {
                    if (thing.getTemplate().name().equals(what)) {
                        if (!action.drawn().isBlank()) {
                            logic.effect(action.drawn(), thing.getPosition(), 0f); // gone in a flash
                        }
                        people.removeIf(who -> who.id().equals(thing.getId()));
                        logic.destroyObject(thing);
                    }
                }
            }
            case KILL -> {
                for (var thing : List.copyOf(logic.getObjects())) {
                    var body = thing.getBody();
                    if (thing.getTemplate().name().equals(what) && body != null && !thing.isEffectivelyDead()) {
                        body.setLeastHealth(0f); // an UNYIELDING's floor, and a beaten one's guard, let go
                        body.setDamageScale(1f);
                        body.damage(body.getHealth() + 1f);
                    }
                }
            }
            case GOLD -> seats.getFirst().gold += action.count();
            case SAVE -> {
                var save = save(); // won or not: the turning has ended the act, and this is what Act 2 reads
                if (save != null) {
                    keeper.accept(what, save);
                }
            }
            case SAY -> quests.play(what);
            case START_QUEST -> quests.start(what);
            case FINISH_QUEST -> quests.finishNow(what);
            case SPAWN -> {
                var zone = zoneNamed(action.zone());
                var template = logic.getThingFactory().findTemplate(what);
                if (zone == null || template == null) {
                    return;
                }
                boolean person = settings.story().npc(what) != null;
                var cells = zone.cells();
                for (int i = 0; i < action.count(); i++) {
                    var cell = cells.get(i % cells.size());
                    var placed = new uz.dukeengine.dungeon.map.StaticMap.Placed(what, cell[0], cell[1]);
                    if (person) {
                        var spawned = logic.spawn(template, ground(logic, placed),
                                uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX);
                        people.add(new Person(spawned.getId(), what, spawned.getPosition().x(),
                                spawned.getPosition().y()));
                    } else {
                        var spawned = logic.spawn(template, ground(logic, placed), dungeonPlayer.getIndex());
                        Spawner.scale(spawned, settings.beforeBossLevel(depth), settings);
                        spawned.addModule(new Bounty(spawned, settings.beforeBossLevel(depth), this::pay));
                    }
                }
            }
            case WALK -> {
                var zone = zoneNamed(action.zone());
                for (int at = 0; at < people.size(); at++) {
                    var who = people.get(at);
                    var there = logic.findObject(who.id());
                    var legs = there == null ? null : there.getLocomotor();
                    if (zone != null && who.npc().equals(what) && legs != null) {
                        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
                        float x = zone.middleX(cell);
                        float y = zone.middleY(cell);
                        legs.moveTo(new uz.dukeengine.core.math.Coord3D(x, y, 0f));
                        people.set(at, new Person(who.id(), who.npc(), x, y)); // home is where she was sent
                    }
                }
            }
            case OPEN_GATE -> {
                for (var thing : logic.getObjects()) {
                    var gate = thing.findModule(uz.dukeengine.rpg.mission.GateUpdate.class);
                    if (gate != null) {
                        gate.open();
                    }
                }
            }
            case GIVE -> {
                var given = settings.items().resolve(what);
                if (given != null) {
                    seats.getFirst().progress.getLoot().take(given, logic.getFrame(), 0);
                }
            }
            case TAKE -> {
                for (var bag : bags()) {
                    for (int slot = 0; slot < bag.slots().size(); slot++) {
                        if (bag.at(slot) != null && bag.at(slot).id().equals(what)) {
                            bag.remove(slot);
                            return;
                        }
                    }
                }
            }
            case FLAG -> script.flag(what);
            case MAP -> nextMap = what;
            case CUTSCENE -> {
                // Queued behind every conversation said before it: a cutscene never starts while a line is on a
                // screen. See cue.
                var asked = settings.story().cutscene(what);
                if (asked != null) {
                    cued.add(new Cued(asked, quests.said().size()));
                }
            }
            case CURSE -> curse(game, what);
        }
    }

    /** The middle of {@code zone}, on the ground. */
    private static uz.dukeengine.core.math.Coord3D groundAt(uz.dukeengine.core.GameLogic logic, uz.dukeengine.dungeon.map.StaticMap.Zone zone) {
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        var flat = new uz.dukeengine.core.math.Coord3D(zone.middleX(cell), zone.middleY(cell), 0f);
        return new uz.dukeengine.core.math.Coord3D(flat.x(), flat.y(), logic.groundHeight(flat));
    }

    // ---- the seamless campaign's intro: see docs/plan/2026-10-15-seamless-campaign.md ----

    /** A beam the story lit, and the frame it goes out. Drawing only: not saved. */
    private record Lit(int beam, int until) {
    }

    private final List<Lit> beams = new ArrayList<>();

    /** An Effect played on what a STREAK carries, every frame until {@code until}: its burning trail. */
    private record Trail(ObjectId on, String look, int until) {
    }

    private final List<Trail> trails = new ArrayList<>();
    /** Where SKY holds the heroes, out of sight, until a STREAK; null while it holds none. */
    private uz.dukeengine.core.math.Coord3D sky;
    /** The zone a HOLD keeps still, until a RELEASE; null while nothing is held. */
    private String holding;

    // ---- the follow mission and the gated world: docs/plan/2026-10-15-seamless-campaign.md, sections 5 and 6 ----

    /**
     * Every frame: whoever a FOLLOW step leads him after walks to its Where while a hero keeps within FollowNear, stops
     * and turns to wait for him past FollowFar, and the step is done when they stand in it with a hero near.
     */
    private void follow(DukeGame game) {
        var dialogue = settings.story().dialogue();
        var logic = game.getLogic();
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        for (var each : List.copyOf(quests.active())) {
            var step = settings.story().quest(each.quest()).steps().get(each.step());
            var where = step.goal() == uz.dukeengine.dungeon.story.Quest.Goal.FOLLOW ? zoneNamed(step.where()) : null;
            if (where == null) {
                continue;
            }
            for (var who : people) {
                var there = who.npc().equals(step.target()) ? logic.findObject(who.id()) : null;
                var legs = there == null ? null : there.getLocomotor();
                if (legs == null) {
                    continue;
                }
                GameObject nearest = null;
                float best = Float.MAX_VALUE;
                for (var seat : seats) {
                    var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
                    if (hero != null && !hero.isEffectivelyDead()) {
                        float d = (float) Math.hypot(hero.getPosition().x() - there.getPosition().x(),
                                hero.getPosition().y() - there.getPosition().y());
                        if (d < best) {
                            best = d;
                            nearest = hero;
                        }
                    }
                }
                if (nearest == null) {
                    continue;
                }
                if (where.holds(there.getPosition().x(), there.getPosition().y(), cell)
                        && best <= dialogue.followNear()) {
                    quests.followed(who.npc(), bags().getFirst());
                } else if (best > dialogue.followFar()) {
                    if (legs.isMoving()) {
                        legs.stop(); // he has strayed: she waits for him
                    }
                    face(there, nearest);
                } else if (best <= dialogue.followNear() && !legs.isMoving()
                        && !where.holds(there.getPosition().x(), there.getPosition().y(), cell)) {
                    legs.moveTo(new uz.dukeengine.core.math.Coord3D(where.middleX(cell), where.middleY(cell), 0f));
                }
            }
        }
    }

    /** The gate kept now: the first of the files' on this map whose If holds; null where none does. */
    private uz.dukeengine.dungeon.story.Gate gate() {
        var map = floors.stageName();
        for (var gate : settings.gates()) {
            if (gate.map().equals(map) && gate.If().stream().allMatch(this::holds)) {
                return gate;
            }
        }
        return null;
    }

    /** The gate kept now, by its name, or null: for a test and the window. */
    public String gateName() {
        var gate = gate();
        return gate == null ? null : gate.name();
    }

    /**
     * Every frame: a hero outside every Open zone of the gate kept now is put back where he last stood inside it, his
     * walk given up, and told so (its Word, once every three seconds). One carried by the story (a fall, a streak) or
     * held in the sky is let be until he comes down. The barrier is this leash, not the path: E84 asks for path regions
     * the game shuts, so a route never plans through closed land.
     */
    private void gated(DukeGame game) {
        var gate = gate();
        if (gate == null || sky != null) {
            return;
        }
        var logic = game.getLogic();
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        for (var seat : seats) {
            var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
            if (hero == null || hero.isEffectivelyDead()
                    || hero.findModule(uz.dukeengine.core.module.ScriptedMove.class) != null
                    && !hero.findModule(uz.dukeengine.core.module.ScriptedMove.class).isDone()) {
                continue;
            }
            var at = hero.getPosition();
            boolean inside = false;
            for (var open : gate.open()) {
                var zone = zoneNamed(open);
                inside |= zone != null && zone.holds(at.x(), at.y(), cell);
            }
            if (inside) {
                seat.lastInside = at;
                continue;
            }
            var back = seat.lastInside;
            if (back == null) {
                var start = zoneNamed(gate.start());
                if (start == null) {
                    continue; // nowhere inside known yet, and nowhere to start: let be
                }
                back = Battles.middleOf(logic, start);
            }
            uz.dukeengine.combat.module.Errand.giveUpAll(hero);
            var legs = hero.getLocomotor();
            if (legs != null && legs.isMoving()) {
                legs.stop();
            }
            hero.setPosition(back);
            seat.lastInside = back;
            int frame = logic.getFrame();
            if (!gate.word().isBlank() && frame - seat.toldShutAt >= 3 * uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND) {
                seat.toldShutAt = frame;
                seat.progress.getLoot().say(gate.word(), frame, settings.lootDrops().noteFrames());
            }
        }
    }

    /** Whether the story holds the battle now: every fighter in its zone kept still. */
    public boolean holdsTheBattle() {
        return holding != null;
    }

    /**
     * Every frame the floor is played: the heroes kept in the sky, everything a HOLD holds kept still, the trails
     * played, the beams put out on time. Every fighter held is the engine's stun ({@code DISABLED}) renewed for two
     * frames, so a RELEASE lets them go within one.
     */
    private void intro(DukeGame game) {
        var logic = game.getLogic();
        int frame = logic.getFrame();
        if (sky != null) {
            for (var seat : seats) {
                var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
                if (hero != null) {
                    hero.setPosition(sky);
                    Battles.daze(hero, 2);
                }
            }
        }
        if (holding != null) {
            var zone = zoneNamed(holding);
            float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
            for (var thing : logic.getObjects()) {
                if (zone != null && thing.getBody() != null && !thing.isEffectivelyDead()
                        && thing.getPlayerIndex() != uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX
                        && zone.holds(thing.getPosition().x(), thing.getPosition().y(), cell)) {
                    Battles.daze(thing, 2);
                }
            }
        }
        for (var each = trails.iterator(); each.hasNext(); ) {
            var trail = each.next();
            var on = logic.findObject(trail.on());
            if (on == null || frame >= trail.until()) {
                each.remove();
            } else {
                logic.effect(trail.look(), on);
            }
        }
        for (var each = beams.iterator(); each.hasNext(); ) {
            var lit = each.next();
            if (frame >= lit.until()) {
                logic.endBeam(lit.beam());
                each.remove();
            }
        }
    }

    private uz.dukeengine.dungeon.map.StaticMap.Zone zoneNamed(String name) {
        return zones().stream().filter(zone -> zone.name().equals(name)).findFirst().orElse(null);
    }

    /** A cutscene asked for, and how many conversations had been said when it was: it waits until they are read. */
    private record Cued(uz.dukeengine.dungeon.story.Cutscene cutscene, int after) {
    }

    private final List<Cued> cued = new ArrayList<>();
    /** Frames the first cutscene in the queue has waited, the world going on. */
    private int cueWaited;

    /**
     * The first cutscene queued begins once none plays and every player's machine has shown to the end every
     * conversation said before it was asked for — told by the {@code Read} order each window sends as it closes one —
     * or, at the latest, once it has waited the Dialogue block's ReadWaitFrames: a machine with no window, or a player
     * gone from his keys. Alone, the world is paused while a line is on his screen, so the wait does not run then.
     */
    private void cue(DukeGame game) {
        if (cutscene != null || cued.isEmpty()) {
            return;
        }
        var next = cued.getFirst();
        boolean read = seats.stream().allMatch(seat -> seat.read >= next.after());
        if (!read && ++cueWaited < settings.story().dialogue().readWaitFrames()) {
            return;
        }
        cued.removeFirst();
        cueWaited = 0;
        cutscene = next.cutscene();
        shotAt = 0;
        shotLeft = -1;
        inCutscene = true;
    }

    /** A player's machine has shown to the end the first {@code count} conversations said. */
    public void read(int playerIndex, int count) {
        var seat = seatOf(playerIndex);
        if (seat != null) {
            seat.read = Math.max(seat.read, count);
        }
    }

    /** The cutscene a frame on: a shot begun — the camera sent flying, its line said — or counted down. */
    private void shoot(DukeGame game) {
        if (cutscene == null) {
            return;
        }
        var shot = cutscene.shots().get(shotAt);
        if (shotLeft < 0) {
            var zone = shot.zone().isBlank() ? null : zoneNamed(shot.zone());
            var camera = game.camera();
            if (zone != null) {
                float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
                camera.moveTo(zone.middleX(cell), zone.middleY(cell), shot.frames(), shot.ease());
            }
            // Turned, tilted and drawn in or back over the same frames, eased (E41); after the move, which stops a turn.
            if (!Float.isNaN(shot.turn())) {
                camera.turnTo((float) Math.toRadians(shot.turn()), shot.frames(), shot.ease());
            }
            if (!Float.isNaN(shot.pitch())) {
                camera.pitchTo((float) Math.toRadians(shot.pitch()), shot.frames(), shot.ease());
            }
            if (!Float.isNaN(shot.zoom())) {
                camera.zoomTo(shot.zoom(), shot.frames(), shot.ease());
            }
            if (!shot.says().isBlank()) {
                quests.play(shot.says());
            }
            shotLeft = shot.length();
        }
        if (--shotLeft <= 0) {
            shotAt++;
            shotLeft = -1;
            if (shotAt >= cutscene.shots().size()) {
                seen(game);
            }
        }
    }

    /** The cutscene over, or skipped: the camera given back, over the first hero standing. */
    private void endCutscene(DukeGame game) {
        if (cutscene == null) {
            return;
        }
        cutscene = null;
        inCutscene = false;
        game.camera().release();
        for (var seat : seats) {
            var hero = seat.heroId == null ? null : game.getLogic().findObject(seat.heroId);
            if (hero != null) {
                game.moveView(hero.getPosition().x(), hero.getPosition().y());
                return;
            }
        }
    }

    /** A player skipped the cutscene: for everybody, as every order is heard. */
    public void skipCutscene(DukeGame game) {
        seen(game);
    }

    /** The cutscene played to its end, or skipped: over, and heard as SEEN. */
    private void seen(DukeGame game) {
        if (cutscene != null) {
            script.heard(uz.dukeengine.dungeon.story.Trigger.When.SEEN, cutscene.name(), 0);
        }
        endCutscene(game);
    }

    /** Whether a cutscene is playing — from any thread: the window letterboxes and takes the player's input. */
    public boolean inCutscene() {
        return inCutscene;
    }

    /** The cutscene playing, by its name, or null — from any thread: the music may be its own. */
    public String cutsceneName() {
        var playing = inCutscene ? cutscene : null;
        return playing == null ? null : playing.name();
    }

    /** The map he plays by its name, or {@code descent} for the generated floors — as the Audio block names places. */
    public String placeName() {
        var name = getStageName();
        return name == null ? "descent" : name;
    }

    /** The run's triggers: what has fired, the flags, what waits. */
    public uz.dukeengine.dungeon.story.Script getScript() {
        return script;
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
            spoke(game, seat, who, hero, there, true);
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
     * The player right-clicked a thing to work -- a tree, a bush, a well, a fire -- and his hero goes to work it; or,
     * with no thing, pressed the build key: a fire at his feet from his wood. See {@link Camp}.
     */
    public void work(DukeGame game, int playerIndex, ObjectId thing, uz.dukeengine.core.math.Coord3D place) {
        var seat = seatOf(playerIndex);
        var hero = seat == null || seat.heroId == null ? null : game.getLogic().findObject(seat.heroId);
        if (hero == null || hero.isEffectivelyDead()) {
            return;
        }
        if (thing == null && place == null) {
            camp.build(game.getLogic(), playerIndex, hero, seat.progress.getLoot());
            return;
        }
        seat.talkingTo = null;
        orders.attackMove(playerIndex, null);
        uz.dukeengine.combat.module.Errand.giveUpAll(hero);
        stable.dismount(game.getLogic(), playerIndex, hero, seat.progress); // work is done on foot
        camp.order(game.getLogic(), playerIndex, hero, thing, place);
    }

    /** His mount key, or a right-click on his horse: called, got onto, or got off. See {@link Stable#press}. */
    public void ride(DukeGame game, int playerIndex, ObjectId horse) {
        var seat = seatOf(playerIndex);
        var hero = seat == null || seat.heroId == null ? null : game.getLogic().findObject(seat.heroId);
        if (hero != null) {
            stable.press(game.getLogic(), playerIndex, hero, seat.progress, horse);
        }
    }

    /** Whether this player's hero is on his horse. */
    public boolean mounted(int playerIndex) {
        return stable.mounted(playerIndex);
    }

    /** This player's hero off his horse, at once: a skill pressed on horseback. See {@link Stable#dismount}. */
    public void dismount(DukeGame game, int playerIndex) {
        var seat = seatOf(playerIndex);
        var hero = seat == null || seat.heroId == null ? null : game.getLogic().findObject(seat.heroId);
        stable.dismount(game.getLogic(), playerIndex, hero, seat == null ? null : seat.progress);
    }

    /** What a click on the ground at {@code place} gives: the chop's word over a tree that may be felled (E66). */
    public String groundWord(DukeGame game, uz.dukeengine.core.math.Coord3D place) {
        return game.getLogic() == null ? null : camp.wordAt(game.getLogic(), place);
    }

    /** Whether this player's hero is at work: chopping, picking, filling or cooking. */
    public boolean working(int playerIndex) {
        return camp.working(playerIndex);
    }

    /**
     * Every BeamFrames, every magic, rare and epic thing lying on the floor stands in a column of its rarity's light
     * (ItemRules' Beams): an event the client plays riding the thing, as a quest's glow is; nothing in the world reads it.
     * The floor's key stands in the run's KeyBeam, the tallest, and the place every quest going sends him to on
     * this map -- a REACH's zone, or the zone a step names as its Where -- in its QuestBeam, a colour of its own.
     */
    private void beams(DukeGame game) {
        var items = settings.items();
        var logic = game.getLogic();
        if (items.rules().beams().isEmpty() || logic.getFrame() % items.rules().beamFrames() != 0) {
            return;
        }
        var keyBeam = settings.run().keyBeam();
        for (var thing : logic.getObjects()) {
            var lying = thing.findModule(uz.dukeengine.rpg.item.GroundItem.class);
            var holding = lying == null ? null : lying.getHolding();
            var beam = holding == null ? "" : holding.use() == ItemUse.UNLOCK ? keyBeam : items.beamOf(holding);
            if (!beam.isBlank()) {
                logic.effect(beam, thing);
            }
        }
        var questBeam = settings.run().questBeam();
        if (questBeam.isBlank()) {
            return;
        }
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        for (var each : quests.active()) {
            var place = zoneNamed(placeOf(settings.story(), each.quest(), each.step()));
            if (place != null) {
                logic.effect(questBeam, new uz.dukeengine.core.math.Coord3D(place.middleX(cell), place.middleY(cell),
                        0f), 0f);
            }
        }
    }

    /**
     * The zone the step {@code at} of {@code quest} sends him to, for its pillar of light: its Where, or a REACH's own
     * zone; blank for a step with none (a person, a thing, a creature: they glow where they stand).
     */
    public static String placeOf(uz.dukeengine.dungeon.story.Story story, String quest, int at) {
        var known = story.quest(quest);
        if (known == null || at >= known.steps().size()) {
            return "";
        }
        var step = known.steps().get(at);
        return !step.where().isBlank() ? step.where()
                : step.goal() == uz.dukeengine.dungeon.story.Quest.Goal.REACH ? step.target() : "";
    }

    /** Every hero's potion stacks joined, as things come into his bag. See {@link Belt#stack}. */
    private void stackPotions() {
        var items = settings.items();
        for (var seat : seats) {
            var bag = seat.progress.getLoot();
            if (bag.version() != seat.stackedAt) {
                Belt.stack(bag, items);
                seat.stackedAt = bag.version();
            }
        }
    }

    /**
     * A player's hero drinks from his belt's {@code belt}th stack, if the belt is ready: the Drink order, keys 1-4.
     * See {@link Belt}.
     */
    public void drink(DukeGame game, int playerIndex, int belt) {
        var seat = seatOf(playerIndex);
        int frame = game.getLogic().getFrame();
        if (seat == null || seat.heroId == null || frame < seat.beltReady) {
            return;
        }
        var bag = seat.progress.getLoot();
        var items = settings.items();
        Belt.stack(bag, items);
        var hero = game.getLogic().findObject(seat.heroId);
        var eaten = Belt.drink(hero, bag, items, belt);
        if (eaten != null) {
            seat.beltReady = frame + items.rules().beltFrames();
            // heard, not decided: an event, out of the checksum. A meal is eaten, a draught or a drink drunk.
            game.getLogic().sound(eaten.heal() > 0 && eaten.mana() <= 0 && eaten.frames() > 0 ? "eat" : "drink",
                    hero, 0);
        }
    }

    /** The frame this player's belt is ready again from: for the belt on the screen, from any thread. */
    public int beltReadyOf(int playerIndex) {
        var seat = seatOf(playerIndex);
        return seat == null ? 0 : seat.beltReady;
    }

    /** The level things are rolled at for the party: its first hero's. */
    private int heroLevel() {
        return Math.max(1, seats.getFirst().progress.getLevel());
    }

    /**
     * Every GlowFrames, the Dialogue block's GlowLook played on the target of every quest step that says Glow, for as
     * long as the step wants it: the thing lying that it asks for, the creature it asks to kill, the person it sends
     * him to. An event, which the client plays riding the thing; nothing in the world reads it.
     */
    private void glow(DukeGame game) {
        var dialogue = settings.story().dialogue();
        var logic = game.getLogic();
        if (dialogue.glowLook().isBlank() || logic.getFrame() % dialogue.glowFrames() != 0) {
            return;
        }
        for (var each : quests.active()) {
            var step = settings.story().quest(each.quest()).steps().get(each.step());
            // Whoever a FOLLOW leads him after wears the follow mark over their head, whatever its Glow says.
            boolean follow = step.goal() == uz.dukeengine.dungeon.story.Quest.Goal.FOLLOW
                    && !dialogue.followLook().isBlank();
            if (!step.glow() && !follow) {
                continue;
            }
            for (var thing : logic.getObjects()) {
                if (glows(step, thing)) {
                    logic.effect(follow ? dialogue.followLook() : dialogue.glowLook(), thing);
                }
            }
        }
    }

    /** Whether {@code thing} is what {@code step} asks for. */
    private static boolean glows(uz.dukeengine.dungeon.story.Quest.Step step, GameObject thing) {
        return switch (step.goal()) {
            case COLLECT -> {
                var lying = thing.findModule(uz.dukeengine.rpg.item.GroundItem.class);
                yield lying != null && lying.getHolding() != null && lying.getHolding().id().equals(step.target());
            }
            case KILL -> thing.getTemplate().name().equals(step.target()) && !thing.isEffectivelyDead();
            case TALK, GIVE, FOLLOW -> thing.getTemplate().name().equals(step.target())
                    && thing.getPlayerIndex() == uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX;
            case REACH -> false;
        };
    }

    /**
     * Every frame the floor is played: each hero's book told the rank of every passive he has put points into — the
     * knight's dragon blood — as his {@link SkillRanks} say, for a passive is never cast with one. Every frame rather
     * than on a raise, so a point spent, a save loaded and a new body (a floor laid, a book made fresh) all agree.
     */
    private void tellTheRanks(DukeGame game) {
        var logic = game.getLogic();
        for (var seat : seats) {
            var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
            var book = hero == null ? null : hero.findModule(uz.dukeengine.rpg.skill.SkillBook.class);
            if (book == null) {
                continue;
            }
            for (var skill : seat.learnt.getSkills()) {
                if (skill.effect().isPassive()) {
                    book.setRank(skill.key(), seat.learnt.rankOf(skill.key()));
                }
            }
        }
    }

    /**
     * Every frame the floor is played: experience earned paid to every hero standing, and every {@code CheckFrames}
     * the bags, the zones and the people looked at.
     */
    private void story(DukeGame game) {
        stroll(game);
        follow(game);
        tellTheRanks(game);
        medallion(game);
        glow(game);
        beams(game);
        stackPotions();
        var heroes = new ArrayList<GameObject>();
        for (var seat : seats) {
            var hero = seat.heroId == null ? null : game.getLogic().findObject(seat.heroId);
            camp.tick(game.getLogic(), seat.player.getIndex(), hero, seat.progress.getLoot());
            stable.tick(game.getLogic(), seat.player.getIndex(), hero, seat.progress);
            heroes.add(hero);
        }
        camp.grow(game.getLogic(), heroes);
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
        var standing = new ArrayList<String>();
        for (var seat : seats) {
            var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
            if (hero == null || hero.isEffectivelyDead()) {
                continue;
            }
            var his = new ArrayList<String>();
            for (var zone : zones()) {
                if (zone.holds(hero.getPosition().x(), hero.getPosition().y(), cell)) {
                    quests.reached(zone.name(), seat.progress.getLoot());
                    his.add(zone.name());
                    if (!standing.contains(zone.name())) {
                        standing.add(zone.name());
                    }
                }
            }
        }
        script.standing(standing);
        // A land freed opens the tower nearest it (the Travel block's Opens): found as if he had stood in it.
        for (var opened : settings.travel().opened(script::flagged)) {
            var tower = nearestTower(opened);
            if (tower != null && !standing.contains(tower) && !visited.contains(tower)) {
                standing.add(tower);
            }
        }
        if (!visited.containsAll(standing)) {
            standing.stream().filter(zone -> !visited.contains(zone)).forEach(visited::add);
            visitedNow = List.copyOf(visited);
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
                if (spoke(game, seat, who, hero, there, asked)) {
                    break; // one talk to a person at a time
                }
            }
        }
        marks();
    }

    // ---- people who walk, gold and trade: see docs/plan/2026-10-08-people-and-trade.md ----

    /**
     * A hero talks to a person: what the quests make of it, the person turned to face him, and — asked, of a merchant —
     * the trade window opened on his player's machine.
     */
    private boolean spoke(DukeGame game, Seat seat, Person who, GameObject hero, GameObject there, boolean asked) {
        boolean said = quests.talk(who.npc(), seat.progress.getLoot(), asked);
        if (said || asked) {
            face(there, hero);
        }
        if (asked && !settings.travel().keeper().isBlank() && settings.travel().keeper().equals(who.npc())) {
            var more = new ArrayList<>(travelAsks); // a tower's keeper: the world map opened to travel from
            more.add(new Opened(seat.player.getIndex(), who.id().value()));
            travelAsks = List.copyOf(more);
        }
        if (asked && settings.story().merchant(who.npc()) != null) {
            var more = new ArrayList<>(trades);
            more.add(new Opened(seat.player.getIndex(), who.id().value()));
            trades = List.copyOf(more);
        }
        return said;
    }

    private static void face(GameObject person, GameObject hero) {
        float dx = hero.getPosition().x() - person.getPosition().x();
        float dy = hero.getPosition().y() - person.getPosition().y();
        if (dx != 0f || dy != 0f) {
            person.setOrientation((float) StrictMath.atan2(dy, dx));
        }
    }

    /**
     * A wandering person strolls: every WanderFrames — each person on a beat of their own — to a point within Wander of
     * home, worked out from the frame and their id so no dice of the world's are drawn; and stands still while a hero
     * is near.
     */
    private void stroll(DukeGame game) {
        var logic = game.getLogic();
        int every = settings.story().trade().wanderFrames();
        for (var who : people) {
            var npc = settings.story().npc(who.npc());
            var there = logic.findObject(who.id());
            var legs = there == null ? null : there.getLocomotor();
            if (npc == null || legs == null || npc.wander() <= 0f && npc.route().isEmpty()) {
                continue;
            }
            boolean heroNear = false;
            for (var seat : seats) {
                var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
                heroNear |= hero != null && !hero.isEffectivelyDead() && near(hero, there, who);
            }
            if (heroNear) {
                if (legs.isMoving()) {
                    legs.stop();
                }
                continue;
            }
            int id = who.id().value();
            if ((logic.getFrame() + id * 37) % every != 0) {
                continue;
            }
            if (!npc.route().isEmpty()) {
                // A traveller: on to the next place of his round once he has come to a stop.
                var next = legs.isMoving() ? null : nextOnRoute(npc.route(), there);
                if (next != null) {
                    legs.moveTo(next);
                }
                continue;
            }
            long hash = (logic.getFrame() * 0x9E3779B97F4A7C15L) ^ (id * 0xC2B2AE3D27D4EB4FL);
            hash ^= hash >>> 29;
            double angle = (hash & 0xFFFF) / 65536.0 * 2 * StrictMath.PI;
            float reach = npc.wander() * (((hash >>> 16) & 0xFF) / 255f);
            legs.moveTo(new uz.dukeengine.core.math.Coord3D(who.homeX() + (float) StrictMath.cos(angle) * reach,
                    who.homeY() + (float) StrictMath.sin(angle) * reach, 0f));
        }
    }

    /**
     * Where a traveller on {@code route} walks next: the middle of the place after the one he stands in -- or after the
     * nearest, if he is in none -- round and round; null where the map has none of its places.
     */
    private uz.dukeengine.core.math.Coord3D nextOnRoute(List<String> route, GameObject traveller) {
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        float x = traveller.getPosition().x();
        float y = traveller.getPosition().y();
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            var zone = zoneNamed(route.get(i));
            if (zone == null) {
                continue;
            }
            double far = zone.holds(x, y, cell) ? -1 : Math.hypot(zone.middleX(cell) - x, zone.middleY(cell) - y);
            if (far < best) {
                best = far;
                nearest = i;
            }
        }
        if (nearest < 0) {
            return null;
        }
        for (int step = 1; step <= route.size(); step++) {
            var zone = zoneNamed(route.get((nearest + (best < 0 ? step : 0)) % route.size()));
            if (zone != null) {
                return new uz.dukeengine.core.math.Coord3D(zone.middleX(cell), zone.middleY(cell), 0f);
            }
        }
        return null;
    }

    /** A trade window asked for: whose player, and the merchant, by the id of the thing. */
    public record Opened(int player, int merchant) {
    }

    /** Every trade window asked for this session, in order — the window opens the last of its player's. */
    private volatile List<Opened> trades = List.of();

    public List<Opened> getTrades() {
        return trades;
    }

    // ---- fast travel between the watchtowers: see uz.dukeengine.dungeon.world.Travel ----

    /** Every time a tower's keeper was asked to send a hero on: the world map opens to travel from, on his machine. */
    private volatile List<Opened> travelAsks = List.of();

    public List<Opened> getTravelAsks() {
        return travelAsks;
    }

    /** The towers of {@code story}: its zones named as the Travel block's towers are, in the map's order. */
    public static List<uz.dukeengine.dungeon.map.StaticMap.Zone> towers(uz.dukeengine.dungeon.story.MapStory story,
            uz.dukeengine.dungeon.world.Travel travel) {
        if (travel.towers().isBlank()) {
            return List.of();
        }
        return story.zones().stream().filter(zone -> zone.name().startsWith(travel.towers())).toList();
    }
    /** The tower nearest the zone {@code near} of the map, by its zone's name; null for none or no such zone. */
    private String nearestTower(String near) {
        var zone = zoneNamed(near);
        if (zone == null) {
            return null;
        }
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        String best = null;
        double far = Double.MAX_VALUE;
        for (var tower : towers(floors.story(), settings.travel())) {
            double d = Math.hypot(tower.middleX(cell) - zone.middleX(cell), tower.middleY(cell) - zone.middleY(cell));
            if (d < far) {
                far = d;
                best = tower.name();
            }
        }
        return best;
    }

    /** The last tower of the map he found, by its zone's name, or null: where he comes back from a save or a death. */
    private String lastTowerFound() {
        var towers = towers(floors.story(), settings.travel()).stream().map(z -> z.name()).toList();
        for (int i = visited.size() - 1; i >= 0; i--) {
            if (towers.contains(visited.get(i))) {
                return visited.get(i);
            }
        }
        return null;
    }

    /**
     * A player's hero goes to the map's {@code tower}th tower: one he has found (stood in, as every place is found, and
     * saved with them), for the Travel block's gold, shown under its title card; set down in the middle of the tower's
     * zone, which the map keeps clear for him. Refused -- said in the bag -- for a tower not found or too little gold.
     */
    public void travel(DukeGame game, int playerIndex, int tower) {
        var seat = seatOf(playerIndex);
        var logic = game.getLogic();
        var hero = seat == null || seat.heroId == null ? null : logic.findObject(seat.heroId);
        var towers = towers(floors.story(), settings.travel());
        if (hero == null || hero.isEffectivelyDead() || tower < 0 || tower >= towers.size()) {
            return;
        }
        var travel = settings.travel();
        var bag = seat.progress.getLoot();
        int frame = logic.getFrame();
        int notes = settings.lootDrops().noteFrames();
        var zone = towers.get(tower);
        if (!visited.contains(zone.name())) {
            bag.say(travel.unknownWord(), frame, notes);
            return;
        }
        if (seat.gold < travel.cost()) {
            bag.say(travel.noGoldWord(), frame, notes);
            return;
        }
        seat.gold -= travel.cost();
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        var flat = new uz.dukeengine.core.math.Coord3D(zone.middleX(cell), zone.middleY(cell), 0f);
        uz.dukeengine.combat.module.Errand.giveUpAll(hero);
        var legs = hero.getLocomotor();
        if (legs != null && legs.isMoving()) {
            legs.stop();
        }
        hero.setPosition(new uz.dukeengine.core.math.Coord3D(flat.x(), flat.y(), logic.groundHeight(flat)));
        var card = travel.card().isBlank() ? null : settings.title(travel.card());
        if (card != null) {
            game.titleCard(card.text(), card.subtext(), card.fadeIn(), card.hold(), card.fadeOut());
        }
        published();
    }

    /** A merchant's stock on this floor: the person, and how many of each of his wares are left. */
    private record Shop(ObjectId id, List<uz.dukeengine.dungeon.story.Merchant.Ware> wares, int[] left) {
    }

    private final List<Shop> shops = new ArrayList<>();
    /** The world the run ticks, for a death heard outside a tick: what the dead creature's level was. */
    private uz.dukeengine.core.GameLogic lastLogic;
    /** What each merchant has left, for the window, by the id of the person: written whole as it changes. */
    private volatile java.util.Map<Integer, List<uz.dukeengine.dungeon.story.Merchant.Ware>> stock =
            java.util.Map.of();

    /**
     * Every merchant on the floor stocked again: the data's wares, and his {@code Gear} things rolled at the hero's
     * level off the world's draw (docs/plan/2026-10-15-loot.md).
     */
    private void restock() {
        shops.clear();
        var items = settings.items();
        for (var who : people) {
            var merchant = settings.story().merchant(who.npc());
            if (merchant == null) {
                continue;
            }
            var wares = new ArrayList<>(merchant.stock());
            if (merchant.gear() > 0 && items.active() && lastLogic != null) {
                for (var thing : items.stock(lastLogic.random().nextInt(0, (1 << 30) - 1), heroLevel(),
                        merchant.gear())) {
                    wares.add(new uz.dukeengine.dungeon.story.Merchant.Ware(thing.id(), items.price(thing), 1));
                }
            }
            shops.add(new Shop(who.id(), List.copyOf(wares),
                    wares.stream().mapToInt(uz.dukeengine.dungeon.story.Merchant.Ware::count).toArray()));
        }
        published();
    }

    private void published() {
        var now = new java.util.TreeMap<Integer, List<uz.dukeengine.dungeon.story.Merchant.Ware>>();
        for (var shop : shops) {
            var wares = new ArrayList<uz.dukeengine.dungeon.story.Merchant.Ware>();
            for (int i = 0; i < shop.left().length; i++) {
                var ware = shop.wares().get(i);
                wares.add(new uz.dukeengine.dungeon.story.Merchant.Ware(ware.item(), ware.price(), shop.left()[i]));
            }
            now.put(shop.id().value(), List.copyOf(wares));
        }
        stock = java.util.Collections.unmodifiableMap(now);
    }

    /** What the merchant {@code id} has left, each ware with its count now; empty for nobody who trades. */
    public List<uz.dukeengine.dungeon.story.Merchant.Ware> stockOf(int id) {
        return stock.getOrDefault(id, List.of());
    }

    /**
     * A monster the floor placed died: the hero whose blow it was is paid, at once, and it is shown -- the GoldBurst's
     * coins played at the corpse, an event and so nothing the simulation reads, and the payment kept for the painter
     * to float the gold over it. A blow that names its killer but not his side is his killer's side.
     */
    private void pay(uz.dukeengine.core.module.Death death, int level, GameObject monster) {
        int player = death.killerPlayerIndex();
        if (player < 0 && death.killer() != null && lastLogic != null) {
            var killer = lastLogic.findObject(death.killer());
            player = killer == null ? -1 : killer.getPlayerIndex();
        }
        var seat = seatOf(player);
        if (seat == null) {
            return;
        }
        int paid = settings.story().trade().goldFor(level);
        seat.gold += paid;
        // The world the corpse lies in, which is the one drawn: a run outlives the worlds its floors are laid in.
        var world = monster.getWorld();
        if (world == null || paid <= 0) {
            return;
        }
        var at = monster.getPosition();
        int frame = world.getFrame();
        payments.removeIf(old -> frame - old.frame() > PAYMENTS_KEPT_FRAMES);
        payments.add(new Payment(frame, player, paid, at.x(), at.y(), at.z()));
        var burst = settings.goldBurst();
        if (!burst.coinLook().isBlank()) {
            // Toward the one whose blow it was, or his hero: the coins that home fly into him.
            var killer = death.killer() == null ? null : world.findObject(death.killer());
            if (killer == null && seat.heroId != null) {
                killer = world.findObject(seat.heroId);
            }
            for (int i = burst.burstsFor(paid); i > 0; i--) {
                world.effect(burst.coinLook(), at, 0f, killer);
            }
        }
    }

    /** A kill paid for, as it is shown: the frame, whose gold, how much, and where the corpse lay. */
    public record Payment(int frame, int player, int gold, float x, float y, float z) {
    }

    /** How long a payment is kept for the painter: longer than any float lasts. */
    private static final int PAYMENTS_KEPT_FRAMES = 10 * uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;

    /** Drawing only: never read by the simulation, never saved. */
    private final List<Payment> payments = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** The kills paid for in the last few seconds, oldest first: what the painter floats the gold of. */
    public List<Payment> payments() {
        return List.copyOf(payments);
    }

    /** How much gold this player's hero has; 0 for a player with no seat. */
    public int goldOf(int playerIndex) {
        var seat = seatOf(playerIndex);
        return seat == null ? 0 : seat.gold;
    }

    /** The shop of the merchant {@code id} whose hero of {@code seat} is near enough to trade, or null. */
    private Shop shopNear(DukeGame game, Seat seat, ObjectId merchant) {
        var shop = shops.stream().filter(each -> each.id().equals(merchant)).findFirst().orElse(null);
        var who = people.stream().filter(each -> each.id().equals(merchant)).findFirst().orElse(null);
        var hero = seat == null || seat.heroId == null ? null : game.getLogic().findObject(seat.heroId);
        var there = game.getLogic().findObject(merchant);
        if (shop == null || who == null || hero == null || there == null || hero.isEffectivelyDead()) {
            return null;
        }
        var npc = settings.story().npc(who.npc());
        float reach = npc == null ? 0f : npc.radius() * 2f;
        float dx = hero.getPosition().x() - there.getPosition().x();
        float dy = hero.getPosition().y() - there.getPosition().y();
        return dx * dx + dy * dy <= reach * reach ? shop : null;
    }

    /** A player buys one of a merchant's wares: if the hero is near, the ware not sold out, the gold and the room there. */
    public void buy(DukeGame game, int playerIndex, ObjectId merchant, int ware) {
        var seat = seatOf(playerIndex);
        var shop = shopNear(game, seat, merchant);
        if (shop == null || ware < 0 || ware >= shop.left().length) {
            return;
        }
        var trade = settings.story().trade();
        var bag = seat.progress.getLoot();
        int frame = game.getLogic().getFrame();
        int notes = settings.lootDrops().noteFrames();
        var sold = shop.wares().get(ware);
        var items = settings.items();
        var item = items.resolve(sold.item());
        var potion = items.potionOf(item);
        if (potion != null) {
            // A stack of one of its own, joined into his first of its kind as it comes in.
            item = items.potion(potion, 1, game.getLogic().random().nextInt(0, (1 << 30) - 1));
        }
        if (item == null || shop.left()[ware] <= 0) {
            bag.say(trade.soldOutWord(), frame, notes);
        } else if (seat.gold < sold.price()) {
            bag.say(trade.noGoldWord(), frame, notes);
        } else if (!bag.take(item, frame, notes)) {
            bag.say(trade.fullWord(), frame, notes);
        } else {
            Belt.stack(bag, items);
            seat.gold -= sold.price();
            shop.left()[ware]--;
            published();
        }
    }

    /**
     * Whether the medallion keeps this player from what he asked of his bag's {@code place}: in the campaign it cannot
     * leave him (the owner, 2026-10-15) -- not dropped from anywhere ({@code shifting} false), not taken off, and not
     * swapped out by putting another thing on in its slot ({@code shifting} true). When it does, he says why (the
     * Medallion block's LockedWord) and the order is not obeyed. Outside the campaign, never.
     */
    public boolean keepsMedallion(DukeGame game, int playerIndex, int place, boolean shifting) {
        var named = settings.medallion().item();
        var seat = seatOf(playerIndex);
        if (named.isBlank() || seat == null || !settings.inCampaign(floors.stageName())) {
            return false;
        }
        var bag = seat.progress.getLoot();
        var item = place >= 0 && place < bag.slots().size() ? bag.at(place) : null;
        if (item == null) {
            return false;
        }
        boolean worn = place >= bag.backpackSize();
        boolean kept = item.id().equals(named) && (!shifting || worn);
        if (!kept && shifting && !worn) {
            int slot = bag.wornPlace(item.slot());
            var wearing = slot < 0 ? null : bag.at(slot);
            kept = wearing != null && wearing.id().equals(named); // putting this on would take it off
        }
        if (kept) {
            bag.say(settings.medallion().lockedWord(), game.getLogic().getFrame(), settings.lootDrops().noteFrames());
        }
        return kept;
    }

    /**
     * A player sells what is in a place of his backpack to a merchant he is near: a quest thing or a key he cannot,
     * and nothing he wears — he takes it off first.
     */
    public void sell(DukeGame game, int playerIndex, ObjectId merchant, int slot) {
        var seat = seatOf(playerIndex);
        if (shopNear(game, seat, merchant) == null) {
            return;
        }
        var bag = seat.progress.getLoot();
        var item = slot < bag.backpackSize() ? bag.at(slot) : null;
        if (item == null) {
            return;
        }
        int worth = worth(item);
        if (worth < 0) {
            bag.say(settings.story().trade().cantSellWord(), game.getLogic().getFrame(),
                    settings.lootDrops().noteFrames());
            return;
        }
        bag.remove(slot);
        seat.gold += worth;
    }

    /** What a thing sells for, or -1 for one that does not sell: a quest thing, or a key. */
    public int worth(uz.dukeengine.rpg.item.Loot item) {
        if ("QUEST".equals(item.kind()) || item.use() == ItemUse.UNLOCK) {
            return -1;
        }
        var story = settings.story();
        // A generated thing by its base, rarity and level, a potion stack by its potions (docs/plan/2026-10-15-loot.md).
        int priced = settings.items().price(item);
        if (priced >= 0) {
            return priced * story.trade().sellPercent() / 100;
        }
        int joined = (int) Math.pow(Math.max(1, settings.lootDrops().joinCount()), item.level() - 1);
        return story.priceOf(item.id()) * joined * story.trade().sellPercent() / 100;
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

    // ---- the medallion and the turning: see docs/plan/2026-10-09-medallion.md ----

    /** The person the turning gave the medallion to, by Npc name; blank while no hero has lost it. */
    private String givenTo = "";

    /** A soul risen from a corpse, to fly to the hero who drank it: spawned at the next frame's story. */
    private record Soul(uz.dukeengine.core.math.Coord3D from, ObjectId to) {
    }

    private final List<Soul> rising = new ArrayList<>();

    /** A soul in flight, and the hero it flies to. */
    private record Wisp(ObjectId id, ObjectId to) {
    }

    private final List<Wisp> flying = new ArrayList<>();

    /**
     * A death heard as it is reaped: told to the killer's skill book (the engine's {@code SkillBook.reaped}) — a soul
     * for a SOUL_DRINK it holds, its own or lent by the medallion it wears, when the dead was its enemy. A soul drunk
     * rises from the corpse and flies to him next frame. The blow names its dealer, arrows and skills too (E27).
     */
    private void drunk(uz.dukeengine.core.event.ObjectDied death) {
        if (lastLogic == null || death.killer() == null) {
            return;
        }
        var killer = lastLogic.findObject(death.killer());
        var book = killer == null ? null : killer.findModule(uz.dukeengine.rpg.skill.SkillBook.class);
        if (book == null) {
            return;
        }
        int before = book.getSouls();
        uz.dukeengine.rpg.skill.SkillBook.reaped(lastLogic, death);
        if (book.getSouls() > before) {
            rising.add(new Soul(death.position(), killer.getId()));
        }
    }

    /**
     * Every frame the floor is played: each hero's book lent what his bag has acting on him (the medallion's soul
     * drink while he wears it) and, on a new body, given the souls he carries — and read back, for the save and the
     * card; a cursed hero's look kept dark; and the souls risen, flown to their heroes.
     */
    private void medallion(DukeGame game) {
        var medallion = settings.medallion();
        if (medallion.item().isBlank()) {
            return;
        }
        var logic = game.getLogic();
        for (var seat : seats) {
            var hero = seat.heroId == null ? null : logic.findObject(seat.heroId);
            if (hero == null) {
                continue;
            }
            if (seat.cursed) {
                hero.setCondition(medallion.cursedWord());
            }
            var book = hero.findModule(uz.dukeengine.rpg.skill.SkillBook.class);
            if (book == null) {
                continue;
            }
            if (!hero.getId().equals(seat.soulsIn)) {
                book.lendFrom(seat.progress.getLoot());
                book.setSouls(seat.souls);
                seat.soulsIn = hero.getId();
            }
            seat.souls = book.getSouls();
            seat.soulPercent = book.soulPercent();
        }
        var wisp = medallion.wisp().isBlank() ? null : logic.getThingFactory().findTemplate(medallion.wisp());
        for (var soul : rising) {
            if (wisp != null) {
                var spawned = logic.spawn(wisp, soul.from(), uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX);
                flying.add(new Wisp(spawned.getId(), soul.to()));
            }
        }
        rising.clear();
        for (var each = flying.iterator(); each.hasNext(); ) {
            var wisping = each.next();
            var thing = logic.findObject(wisping.id());
            var to = logic.findObject(wisping.to());
            if (thing == null || to == null) {
                if (thing != null) {
                    logic.destroyObject(thing);
                }
                each.remove();
                continue;
            }
            var here = thing.getPosition();
            var there = to.getPosition();
            float dx = there.x() - here.x();
            float dy = there.y() - here.y();
            float far = (float) Math.sqrt(dx * dx + dy * dy);
            if (far <= medallion.wispSpeed()) {
                logic.destroyObject(thing); // into him
                each.remove();
            } else {
                thing.setPosition(new uz.dukeengine.core.math.Coord3D(here.x() + dx / far * medallion.wispSpeed(),
                        here.y() + dy / far * medallion.wispSpeed(), there.z()));
            }
        }
    }

    /**
     * The turning, a story's CURSE: the medallion leaves the bag that holds it and appears in {@code to}'s hand (her
     * GivenLook played on her), the hero who held it darkens and goes over to the monsters' side, out of his player's
     * hands, and the act is over — the banner says so and the map stays won, its story going on (a MAP may follow).
     */
    private void curse(DukeGame game, String to) {
        var logic = game.getLogic();
        var medallion = settings.medallion();
        Seat holder = null;
        for (var seat : seats) {
            var bag = seat.progress.getLoot();
            for (int place = 0; place < bag.slots().size(); place++) {
                var item = bag.at(place);
                if (item != null && item.id().equals(medallion.item())) {
                    bag.remove(place);
                    holder = holder == null ? seat : holder;
                }
            }
        }
        holder = holder == null ? seats.getFirst() : holder;
        givenTo = to;
        holder.cursed = true;
        var hero = holder.heroId == null ? null : logic.findObject(holder.heroId);
        if (hero != null) {
            hero.setCondition(medallion.cursedWord());
            uz.dukeengine.combat.module.Errand.giveUpAll(hero);
            hero.setPlayerIndex(dungeonPlayer.getIndex());
        }
        for (var who : people) {
            var there = logic.findObject(who.id());
            if (there != null && who.npc().equals(to) && !medallion.givenLook().isBlank()) {
                logic.effect(medallion.givenLook(), there);
            }
        }
        state = State.WON;
        endedFrame = logic.getFrame();
        tracker = Tracker.NONE;
        game.setBanner("lost|" + medallion.turnedWord());
    }

    /** The souls this player's hero's medallion has drunk; 0 for a player with no seat. From any thread. */
    public int soulsOf(int playerIndex) {
        var seat = seatOf(playerIndex);
        return seat == null ? 0 : seat.souls;
    }

    /** What this player's hero's souls add to his blows, in percent, as his book last said; 0 for no seat. */
    public int soulPercentOf(int playerIndex) {
        var seat = seatOf(playerIndex);
        return seat == null ? 0 : seat.soulPercent;
    }

    /** Whether this player's hero has been cursed by the turning. */
    public boolean isCursed(int playerIndex) {
        var seat = seatOf(playerIndex);
        return seat != null && seat.cursed;
    }

    /** The person the turning gave the medallion to; blank while it has not been given. */
    public String medallionGivenTo() {
        return givenTo;
    }
}
