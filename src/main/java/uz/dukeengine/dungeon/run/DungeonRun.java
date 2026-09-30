package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.level.HeroProgress;
import uz.dukeengine.dungeon.loot.LootTable;
import uz.dukeengine.dungeon.skill.SkillRanks;
import uz.dukeengine.dungeon.skill.Skills;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.GamePlayer;

/**
 * The run loop: one dungeon at a time, and a fresh one the moment the hero dies.
 *
 * <p>Duke Dungeon is a roguelike in the oldest sense — there is no saving and no
 * carrying anything forward. A run is the stretch between spawning at full health
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

    public DungeonRun(GamePlayer heroPlayer, GamePlayer dungeonPlayer, Floors floors,
            DungeonSettings settings, HeroProgress progress,
            LootTable drops, uz.dukeengine.dungeon.ai.Orders orders,
            SkillRanks learnt) {
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
        var chosen = floor != null && floor.biomes() != null
                ? themes.dressedAs(floor.biomes().ofRoom(0), floors.seed(), depth)
                : themes.pick(floors.seed(), depth);
        return chosen == null ? null : chosen.asStatus();
    }

    /**
     * What each cell of {@code floor} is drawn as, for a floor that mixes biomes, or null for one that wears its look
     * whole. Each biome in the tone the depth draws for it — the same draw {@link #lookOf} makes for the biome the
     * heroes come in to, so the cells round them and the status line name one look.
     */
    private FloorLooks looksOf(GeneratedDungeon floor) {
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
        if (his && Skills.heroOf(game.getLogic(), player) == creature) {
            return HeroStatus.of(creature, seat.progress, depth, floors.lastDepth(), settings,
                    game.getLogic().getFrame(), look, orders.isHolding(player), seat.learnt);
        }
        if (creature != null && !creature.isEffectivelyDead()) {
            // Somebody else's creature, or one of his that is not the hero: the
            // card describes it, and the buttons are live only if he could give it
            // an order.
            return HeroStatus.creature(creature, depth, floors.lastDepth(), settings,
                    look, his);
        }
        // Nothing selected, or what was selected has died: the bar keeps the floor
        // and loses the creature. A panel describing a corpse until the player
        // thinks to click somewhere is a panel that looks broken.
        return HeroStatus.nothing(depth, floors.lastDepth(), settings,
                seat.progress.getLoot().noteAt(game.getLogic().getFrame()), look);
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
        }
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
                seat.progress.carryOver(game, hero);
            }
        }
        bossId = placed.boss() == null ? null : placed.boss().getId();
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
}
