package uz.dukeengine.dungeon;

import uz.dukeengine.game.Colour;
import uz.dukeengine.dungeon.ai.HeroBrain;
import uz.dukeengine.dungeon.ai.MonsterBrain;
import uz.dukeengine.rpg.fight.ArrowUpdate;
import uz.dukeengine.rpg.fight.FallingUpdate;
import uz.dukeengine.rpg.fight.Bow;
import uz.dukeengine.rpg.fight.EyesOnly;
import uz.dukeengine.rpg.fight.Swing;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Hero;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.content.Projectile;
import uz.dukeengine.dungeon.content.Prop;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.rpg.hero.GrowableBody;
import uz.dukeengine.rpg.hero.HeroAttributes;
import uz.dukeengine.rpg.hero.HeroBuild;
import uz.dukeengine.rpg.hero.HeroProgress;
import uz.dukeengine.rpg.hero.Recovery;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.item.LootTable;
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.skill.CastSkill;
import uz.dukeengine.rpg.skill.MendingUpdate;
import uz.dukeengine.rpg.skill.SummoningUpdate;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.Skills;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.run.Floors;
import uz.dukeengine.dungeon.run.Seal;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.stage.Stage;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.GamePlayer;
import uz.dukeengine.game.script.ScriptModule;

/**
 * Duke Dungeon — the first game written on this engine.
 *
 * <p>A hero, a dungeon full of skeletons, and no way to save: die and the whole
 * thing is generated again from a new seed. Everything on screen is a coloured
 * shape — no models, no textures, no sound — because the question this game exists
 * to answer is whether the engine can be <em>played</em>, and the quickest way to
 * learn that is to strip away everything that could hide the answer.
 *
 * <p>This class is only assembly. What the creatures are is data
 * ({@code data/units/}), how a dungeon is laid out is data ({@code maps/}),
 * how they behave is {@link uz.dukeengine.dungeon.ai}, and when a run ends is
 * {@link DungeonRun}. Nothing here is added to the engine — the game is definitions
 * and orders the engine already understands, which is the real test: if a game
 * needs new engine features to exist, the engine was not finished.
 */
public final class Dungeon {

    static final Colour HERO_COLOUR = new Colour(0xFF5AAAFF);
    static final Colour SKELETON_COLOUR = new Colour(0xFFE15F50);

    private Dungeon() {
    }

    /**
     * The hand-drawn room — one hero, three fixed skeletons, no run loop and no
     * behaviour scripts. Kept as the plainest proof the engine can be played: a
     * known world with known answers, built from its own frozen data so that
     * tuning the game can never move it. The real game is {@link #create(long)}.
     */
    public static DukeGame create() {
        var game = DukeGame.create("Duke Dungeon")
                .subtitle("one room, one hero, three skeletons")
                .loadUnits(Content.read(Content.FIXTURE_CREATURES))
                .mapFromText(Content.read(Content.FIXTURE_ROOM));

        var hero = game.addPlayer("Hero", HERO_COLOUR);
        var dungeon = game.addPlayer("Dungeon", SKELETON_COLOUR);
        game.enemies(hero, dungeon).localPlayer(hero);

        game.spawn("Rogue", hero, 200f, 180f);

        // Placed by hand and far enough apart that they are fought one at a time.
        // A crowd would be a balance problem, and balance is not what this room
        // is trying to find out.
        game.spawn("Skeleton", dungeon, 90f, 60f)
                .spawn("Skeleton", dungeon, 200f, 50f)
                .spawn("Skeleton", dungeon, 310f, 60f);

        return game;
    }

    /** An empty world of the game's own making: its creatures, behaviour and sides. */
    /**
     * @param seal   the keep's shut gate, as every blow, shot and mending asks it: told of each floor by the run that
     *               lays it, and of none in a world nobody lays one in. See {@link Seal}
     * @param clock  the run's clock, frames of play since it began, saved with it: the hour the night roamers wake by
     *               (see {@link uz.dukeengine.dungeon.world.DayNight}), kept by the run and read by every brain
     */
    public record Arena(DukeGame game, GamePlayer hero, GamePlayer dungeon,
            java.util.List<GamePlayer> heroes, Seal seal, java.util.concurrent.atomic.AtomicInteger clock) {
    }

    /** A game, the run loop that keeps it going, and the hero's progression. */
    public record Session(DukeGame game, DungeonRun run, HeroProgress progress) {
    }

    /**
     * The game's world on a caller-supplied map, with nothing placed in it yet.
     *
     * <p>Pulled out so that the generated dungeon and a test's purpose-built arena
     * are the same game — a combat test that wired up its own creatures would be
     * testing a world nobody plays.
     */
    public static Arena world(String asciiMap, DungeonSettings settings) {
        return world(asciiMap, settings, Content.units());
    }

    /** The same, for a caller with no interest in loot: an empty bag. */
    public static Arena world(String asciiMap, DungeonSettings settings, String creaturesIni) {
        return world(asciiMap, settings, creaturesIni, new LootBag());
    }

    /**
     * The same, on a creature file the caller supplies — the seam for asking what
     * a re-tuned creature does, next to {@link #newSession(long, DungeonSettings)}
     * for re-tuned generation.
     */
    public static Arena world(String asciiMap, DungeonSettings settings, String creaturesIni,
            LootBag bag) {
        return world(asciiMap, null, settings, creaturesIni, bag);
    }

    /**
     * The same, on a dungeon that has height in it.
     *
     * <p>{@code levelMap} is the second layer the generator draws — which storey
     * each cell stands on and where the stairs are. A hand-written arena passes
     * {@code null} and gets the flat world it drew.
     */
    public static Arena world(String asciiMap, String levelMap, DungeonSettings settings,
            String creaturesIni, LootBag bag) {
        return world(asciiMap, levelMap, settings, creaturesIni, java.util.List.of(bag));
    }

    /**
     * The same for a party: a hero's side for every bag, in seat order — the host's first — allies of each other
     * and every one of them the dungeon's enemy, and the dungeon's side last.
     */
    public static Arena world(String asciiMap, String levelMap, DungeonSettings settings,
            String creaturesIni, java.util.List<LootBag> bagsBySeat) {
        var seal = new Seal();
        var clock = new java.util.concurrent.atomic.AtomicInteger();
        var heroes = new java.util.ArrayList<GamePlayer>();
        // No subtitle here: what this world is called depends on why it was built,
        // and only the caller knows — an endless descent, or one named stage. See
        // the two entry points below.
        var game = DukeGame.create(settings.mainMenu().title())
                .customModules(factory -> {
                    // A brain is a module named by its class, like every other: the block word is the
                    // class, so the editor completes it, opens it and flags a typo. Which creature a
                    // MonsterBrain is the brain of is the block it was put on -- see MonsterBrain.onStart.
                    ScriptModule.registerScript(factory, HeroBrain.Data.class,
                            () -> new HeroBrain(settings));
                    ScriptModule.registerScript(factory, MonsterBrain.Data.class,
                            () -> new MonsterBrain(settings, seal, clock));
                    // Hero and monsters alike need a body that can grow: levels
                    // raise his and theirs, and the engine's fixes its maximum
                    // when the unit is built.
                    //
                    // A hero's is built with his first level's strength already in it,
                    // and so are his legs and his weapon below: the creature file holds
                    // what his attributes are added to, and a hero is the same hero
                    // wherever he is spawned. Anything that is not a hero is built
                    // exactly as its block says. See HeroBuild.
                    var rules = settings.attributeRules();
                    factory.register(GrowableBody.Data.class, (owner, data) -> new GrowableBody(owner,
                            HeroBuild.body(data, attributesOf(settings, owner), rules)));
                    factory.register(uz.dukeengine.core.module.MoveUpdate.Data.class,
                            (owner, data) -> new uz.dukeengine.core.module.MoveUpdate(owner,
                                    HeroBuild.legs(data, attributesOf(settings, owner), rules)));
                    // Named after the creature that holds it, so every blow is heard as fired.<Template> --
                    // a knight's sword, a brute's axe, a stalker's crossbow -- see data/sounds/sfx.duke.
                    factory.register(uz.dukeengine.combat.module.WeaponUpdate.Data.class, (owner, data) -> {
                        var weapon = new uz.dukeengine.combat.module.WeaponUpdate(owner, namedFor(owner,
                                HeroBuild.weapon(data, attributesOf(settings, owner), rules)));
                        // A hero swings when told and picks no fight of his own, from his first frame (HeroBrain).
                        if (owner.getTemplate().modules().stream().anyMatch(HeroBrain.Data.class::isInstance)) {
                            weapon.acquiresNothing(true);
                        }
                        return weapon;
                    });
                    // Health coming back on its own, at the rate his block names --
                    // set by HeroProgress, as his mana is.
                    factory.register(Recovery.Data.class, (owner, data) -> new Recovery(owner));
                    // And the water at the way in, mending whoever stands near it -- on its own side of the keep's
                    // shut gate, as every mending is. See FountainUpdate.
                    factory.register(uz.dukeengine.rpg.hero.FountainUpdate.Data.class,
                            (owner, data) -> new uz.dukeengine.rpg.hero.FountainUpdate(owner, data, seal));
                    // The keep's gate, which only the key given to it opens. See GateUpdate.
                    factory.register(uz.dukeengine.rpg.mission.GateUpdate.Data.class,
                            uz.dukeengine.rpg.mission.GateUpdate::new);
                    // Which skills a unit has is the Skill blocks written inside its own:
                    // the SkillBook block says only that it has some.
                    factory.register(SkillBook.Data.class, (owner, data) -> new SkillBook(owner,
                            settings.bookFor(owner.getTemplate().name()), settings.skillRules(), seal));
                    // An archer's shots become things in the world. The engine's
                    // weapon still aims and reloads; these two decide what
                    // happens between letting go and landing.
                    factory.register(Bow.Data.class, (owner, data) -> new Bow(owner, data, settings.bowDefaults()));
                    // Stone stops his shots as well as his eyes.
                    factory.register(EyesOnly.Data.class, (owner, data) -> new EyesOnly(owner, settings.storeyHeight()));
                    // And what a creature a shot stuns wears while it stands dazed -- see ArrowUpdate.stun.
                    factory.register(ArrowUpdate.Data.class,
                            (owner, data) -> new ArrowUpdate(owner, settings.combat().stunLook(), seal));
                    // A blast with a pause in the middle. The mark it leaves is a
                    // thing in the world like the arrow above, so the client draws
                    // the warning without being told anything special.
                    factory.register(FallingUpdate.Data.class, (owner, data) -> new FallingUpdate(owner, seal));
                    // The meteor's mark turned round: holy light lying where it will
                    // land, and mending whoever it came down for when it does.
                    factory.register(MendingUpdate.Data.class, MendingUpdate::new);
                    // And a rift, which something of the dungeon's own climbs out of -- at its
                    // caller's level, which is what the settings say a level is worth.
                    factory.register(SummoningUpdate.Data.class,
                            (owner, data) -> new SummoningUpdate(owner,
                                    (risen, level) -> Spawner.scale(risen, level, settings)));
                    // A monster's blow lands where it stands, as it always did.
                    // This is how the brain finds out that it struck -- and where
                    // the keep's shut gate takes a blow struck through it.
                    factory.register(Swing.Data.class, (owner, data) -> new Swing(owner, seal));
                    // His roll (Space), the action module's: what a skill of Effect MODULE does on a hero that has it.
                    factory.register(uz.dukeengine.action.Dodge.Data.class, uz.dukeengine.action.Dodge::new);
                    // And his sprint (Shift): a faster run while the order says so, the Controls block's share.
                    factory.register(uz.dukeengine.dungeon.run.Sprint.Data.class, (owner, data) ->
                            new uz.dukeengine.dungeon.run.Sprint(owner, settings.controls().sprintPercent() / 100f));
                    // What a dead monster leaves lying about. The chest is a
                    // creature like any other -- it is in the world, so the client
                    // draws it without being told anything special.
                    factory.register(GroundItem.Data.class, (owner, data) -> new GroundItem(owner));
                })
                // A unit is one block, and its record is its word: a Monster block is a Monster.
                .templates(loader -> loader.type(Monster.class).type(Hero.class)
                        .type(Projectile.class).type(Prop.class))
                .loadUnits(creaturesIni)
                // How tall a storey stands is the World block's, and the engine lays
                // every map at it: this one and each floor after it.
                .world(settings.world())
                .mapFromText(asciiMap);

        if (levelMap != null) {
            // The height layer over the map that was just read. The engine owns
            // what a storey means and what may be walked between two of them;
            // this only hands it the picture the generator drew.
            uz.dukeengine.core.pathfind.MapLoader.levels(game.getTerrain(), levelMap);
        }

        // The heroes' sides first, so a party's seats are the engine's players 1 to N — the numbers its network
        // session hands out — and the dungeon's after them, played by nobody's machine.
        for (int seat = 1; seat <= bagsBySeat.size(); seat++) {
            heroes.add(game.addPlayer(seat == 1 ? "Hero" : "Hero " + seat, settings.party().colourOf(seat)));
        }
        var dungeonPlayer = game.addPlayer("Dungeon", SKELETON_COLOUR);
        for (int seat = 0; seat < heroes.size(); seat++) {
            var hero = heroes.get(seat);
            game.enemies(hero, dungeonPlayer);
            for (var other : heroes.subList(0, seat)) {
                game.allies(hero, other);
            }
        }
        game.localPlayer(heroes.getFirst());
        // The hero run along a held direction or swung toward a point: the action module's orders, heard in every world.
        game.onOrder(order -> uz.dukeengine.action.ActionOrders.apply(order, game.getLogic()));
        return new Arena(game, heroes.getFirst(), dungeonPlayer, java.util.List.copyOf(heroes), seal, clock);
    }

    /**
     * The real dungeon: a fresh layout drawn from {@code seed}, and a run loop that
     * generates the next one each time the hero dies. Same seed, same first dungeon
     * and same sequence of dungeons after it.
     */
    public static DukeGame create(long seed) {
        return newSession(seed).game();
    }

    /** The same, on settings already loaded — so a caller can read them once. */
    public static DukeGame create(long seed, DungeonSettings settings) {
        return newSession(seed, settings).game();
    }

    /**
     * A stage: the one floor somebody built, and the boss on it is the end of the
     * game rather than a door down.
     *
     * <p>The same game in every other respect, and deliberately so — the stage was
     * cut from a generated floor, so handing it to the same assembly is what makes
     * "it plays exactly like the dungeon it came from" true by construction rather
     * than by care. What it swaps is where floors come from; see {@link Floors}.
     */
    public static DukeGame createStage(Stage stage, DungeonSettings settings) {
        return newStageSession(stage, settings).game();
    }

    /** Build the game and expose its run loop (the entry point tests build on). */
    public static Session newSession(long seed) {
        return newSession(seed, DungeonSettings.load());
    }

    /** The same, on settings the caller supplies — the seam for testing a re-tuned game. */
    public static Session newSession(long seed, DungeonSettings settings) {
        return open(DungeonGenerator.generate(seed, settings, 1), seed,
                Floors.generated(seed, settings), settings, "a different dungeon every run");
    }

    /** The stage's own session, for a test that wants at its run loop. */
    public static Session newStageSession(Stage stage, DungeonSettings settings) {
        var told = stage.name() == null || stage.name().isBlank() ? stage.id() : stage.name();
        var about = stage.description() == null || stage.description().isBlank()
                ? told : told + " — " + stage.description();
        return open(stage.floor(), stage.seed(), Floors.ofStage(stage), settings, about);
    }

    /**
     * Assemble the game around a first floor and a place for the next one to come
     * from.
     *
     * <p>One method for both kinds because everything below the floors is the same
     * game: the same creatures, the same hero, the same commands, the same loot
     * table drawn the same way. A second copy of this for stages would drift,
     * and what it drifted into would be a stage that no longer played like the
     * dungeon it was frozen from.
     *
     * @param seed what the run's own dice are wound to — the floor's
     *             look. A stage carries the seed it was cut
     *             from so that it is the same run every time, not merely the same
     *             rooms
     */
    private static Session open(uz.dukeengine.dungeon.gen.GeneratedDungeon floor, long seed,
            Floors floors, DungeonSettings settings, String subtitle) {
        return open(floor, seed, floors, settings, subtitle, 1, null, null);
    }

    /** How far round a hero a wide world's things stay awake, in cells, and how often who sleeps is decided. */
    static final int SLEEP_CELLS = 40;
    static final int SLEEP_EVERY_FRAMES = 15;

    /**
     * A party's game: the host's match, played by {@code heroes} players — every machine building the same one
     * from the same line, and each playing the seat its session gave it.
     *
     * <p>Every hero is said once the match runs, this machine's as {@code localHero}, and the first floor waits
     * until they all have been: see {@link DungeonRun#choose}. {@code net} is the session the lobby made, or
     * {@code null} for a party whose players are all on this machine — which is what a test is.
     */
    public static Session newPartySession(uz.dukeengine.dungeon.party.PartyMatch match, int heroes,
            uz.dukeengine.game.MultiplayerSession net, DungeonSettings settings, String localHero) {
        if (match.isStage()) {
            var stage = uz.dukeengine.dungeon.stage.Stages.load(match.stage(), settings);
            return open(stage.floor(), stage.seed(), Floors.ofStage(stage), settings, stage.name(), heroes, net,
                    localHero);
        }
        return open(DungeonGenerator.generate(match.seed(), settings, 1), match.seed(),
                Floors.generated(match.seed(), settings), settings, "a party", heroes, net, localHero);
    }

    /**
     * @param heroes    how many players' heroes go down: one alone, more for a party, whose heroes are said as
     *                  orders once the match runs
     * @param net       the party's session, attached before a frame is stepped; {@code null} alone
     * @param localHero the hero this machine's player goes in as, said when the match starts; {@code null} alone,
     *                  where the menu tells the run before it starts instead
     */
    private static Session open(uz.dukeengine.dungeon.gen.GeneratedDungeon floor, long seed,
            Floors floors, DungeonSettings settings, String subtitle, int heroes,
            uz.dukeengine.game.MultiplayerSession net, String localHero) {
        // What each has put his levels into, and what each has picked up: a floor gives a hero a fresh body and a
        // fresh SkillBook, so both have to live somewhere that outlives both.
        var bags = new java.util.ArrayList<LootBag>();
        var learnts = new java.util.ArrayList<uz.dukeengine.rpg.skill.SkillRanks>();
        for (int seat = 0; seat < heroes; seat++) {
            bags.add(settings.lootDrops().bag());
            var learnt = new uz.dukeengine.rpg.skill.SkillRanks(settings.progression().skillSpread());
            learnt.startWith(settings.skillsFor(settings.run().defaultHero()));
            learnts.add(learnt);
        }
        var arena = world(floor.asciiMap(), floor.levelMap(), settings, Content.units(), bags);
        // And the relief the floors lie on, over the levels just laid: the same grid, as the next floor's is.
        arena.game().getTerrain().setRelief(floor.relief());
        if (floor.levelHeight() > 0f) {
            arena.game().getTerrain().setLevelHeight(floor.levelHeight());
        }
        var game = arena.game().subtitle(subtitle);
        // A wide world sleeps far from its heroes: a camp a hero is nowhere near costs a frame nothing, and wakes as
        // he comes within SLEEP_CELLS -- further than he sees, so nothing is caught dozing. Every floor of the
        // descent, and every small stage, is awake as ever.
        if (uz.dukeengine.dungeon.stage.StagePaint.isWide(floors.paint())) {
            // Asked once the match runs, when a player has its index.
            var wakers = java.util.List.copyOf(arena.heroes());
            game.sleep(new uz.dukeengine.core.Sleep(
                    thing -> wakers.stream().anyMatch(hero -> hero.getIndex() == thing.getPlayerIndex()),
                    SLEEP_CELLS, SLEEP_EVERY_FRAMES));
        }
        if (net != null) {
            // Before a frame is stepped: from here this machine plays the seat the session gave it.
            game.multiplayer(net);
        }

        // Told which creature each hero is and everything his block says about him --
        // see DefaultHero and Hero.
        var progresses = new java.util.ArrayList<HeroProgress>();
        for (int seat = 0; seat < heroes; seat++) {
            var player = arena.heroes().get(seat);
            var progress = new HeroProgress(player::getIndex, banner(game, player), settings.levelling(),
                    settings.attributeRules(), settings.progression().levelUpBannerFrames(), bags.get(seat));
            progress.playing(settings.playedHeroLook());
            progress.manaPerKill(settings.progression().manaPerKill());
            progresses.add(progress);
        }
        // Drawn from the world's own chances, as the engine's weapons are: the same
        // orders drop the same things off the same deaths, on every machine.
        var drops = new LootTable(settings.loot(), settings.lootDrops().dropPercent(),
                settings.lootDrops().bossDropPercent(), settings.lootDrops().valuePercentPerDepth());
        var run = new DungeonRun(arena.hero(), arena.dungeon(), floors, settings, progresses.getFirst(),
                drops, learnts.getFirst(), arena.seal(), arena.clock());
        for (int seat = 1; seat < heroes; seat++) {
            run.seat(arena.heroes().get(seat), progresses.get(seat), learnts.get(seat));
        }
        if (localHero != null) {
            run.awaitHeroes();
        }

        // Q, W, E and R arrive as this game's own orders, through the same queue
        // the standard orders use — so a keypress lands on a frame boundary and is
        // recorded, rather than reaching into the simulation from the input thread.
        // Heard as the order that carries them to every machine, or as the command
        // itself where one was posted straight here.
        java.util.function.Consumer<uz.dukeengine.core.message.Command> obey = command -> {
            switch (command) {
                // The rank he has PUT INTO it, not the level he has reached. Four
                // slots that were all as strong as the hero are now four that
                // compete for his levels -- see SkillRanks. Whose ranks is whose
                // hero is casting; a player with no hero casts nothing.
                case CastSkill cast when run.mounted(cast.playerIndex()) ->
                        run.dismount(game, cast.playerIndex()); // a skill pressed on horseback gets him off it
                case CastSkill cast -> {
                    var learnt = run.learntOf(cast.playerIndex());
                    // A skill of his book outside his four is never bought: his at its first rank.
                    int rank = learnt == null ? 0 : learnt.getSkills().stream().anyMatch(s -> s.key() == cast.key())
                            ? learnt.rankOf(cast.key()) : 1;
                    if (learnt != null && Skills.cast(game.getLogic(),
                            toTheEndOfItsLane(game.getLogic(), cast, learnt.getSkills()), rank)) {
                        // Heard as cast.<Template>.<key>: the knight's wave and the huntress's volley are two sounds.
                        var hero = Skills.heroOf(game.getLogic(), cast.playerIndex());
                        if (hero != null) {
                            sound(hero, "cast." + hero.getTemplate().name() + "." + cast.key());
                        }
                    }
                }
                // And spending one of those levels, which is a click on the little
                // button beside a slot.
                case uz.dukeengine.rpg.skill.UpgradeSkill raise -> {
                    var learnt = run.learntOf(raise.playerIndex());
                    if (learnt != null) {
                        Skills.raise(learnt, raise, run.progressOf(raise.playerIndex()).getLevel());
                    }
                }
                // Sent for something on the floor (E on it), or to put something of his down.
                case uz.dukeengine.rpg.item.PickUp pick -> {
                    var progress = run.progressOf(pick.playerIndex());
                    if (progress != null) {
                        uz.dukeengine.rpg.item.ItemErrand.pickUp(
                                Skills.heroOf(game.getLogic(), pick.playerIndex()),
                                game.getLogic().findObject(pick.item()), progress.getLoot(),
                                errandRules(settings, arena));
                    }
                }
                case uz.dukeengine.rpg.item.DropItem drop -> {
                    var progress = run.progressOf(drop.playerIndex());
                    if (progress != null && !run.keepsMedallion(game, drop.playerIndex(), drop.slot(), false)) {
                        uz.dukeengine.rpg.item.ItemErrand.drop(
                                Skills.heroOf(game.getLogic(), drop.playerIndex()), drop.slot(), drop.place(),
                                progress.getLoot(), errandRules(settings, arena));
                    }
                }
                // A thing put on out of the backpack or taken off into it, where he stands and at once; a full
                // backpack keeps it on, and he says so.
                case uz.dukeengine.rpg.item.ShiftItem shift -> {
                    var progress = run.progressOf(shift.playerIndex());
                    var shifted = progress == null || run.keepsMedallion(game, shift.playerIndex(), shift.place(), true)
                            ? null : progress.getLoot().shift(shift.place());
                    if (shifted == uz.dukeengine.rpg.item.LootBag.Shift.FULL) {
                        progress.getLoot().say(settings.lootDrops().fullWord(), game.getLogic().getFrame(),
                                settings.lootDrops().noteFrames());
                    } else if (shifted == uz.dukeengine.rpg.item.LootBag.Shift.WORN
                            || shifted == uz.dukeengine.rpg.item.LootBag.Shift.TAKEN_OFF) {
                        sound(Skills.heroOf(game.getLogic(), shift.playerIndex()), "equip");
                    }
                }
                // Sent up to the keep's gate: he walks there and says whether he has the key. See ItemErrand.
                case uz.dukeengine.dungeon.run.ToTheGate gate -> {
                    var progress = run.progressOf(gate.playerIndex());
                    if (progress != null) {
                        uz.dukeengine.rpg.item.ItemErrand.toTheGate(
                                Skills.heroOf(game.getLogic(), gate.playerIndex()),
                                game.getLogic().findObject(gate.gate()), progress.getLoot(),
                                errandRules(settings, arena));
                    }
                }
                // Sent to use a thing of his on another: the key on the gate. See ItemErrand.
                case uz.dukeengine.rpg.item.UseItem use -> {
                    var progress = run.progressOf(use.playerIndex());
                    if (progress != null) {
                        uz.dukeengine.rpg.item.ItemErrand.use(
                                Skills.heroOf(game.getLogic(), use.playerIndex()), use.slot(),
                                game.getLogic().findObject(use.target()), progress.getLoot(),
                                errandRules(settings, arena));
                    }
                }
                // A party's player saying who he goes in as.
                case uz.dukeengine.dungeon.party.ChooseHero pick ->
                        run.choose(game, pick.playerIndex(), pick.hero());
                // Sent to talk to a person: talked to now if he is near, or walked over to. See DungeonRun.talkTo.
                // A player skipped the cutscene: it is over for everybody. See DungeonRun.skipCutscene.
                case uz.dukeengine.dungeon.run.SkipCutscene skip -> run.skipCutscene(game);
                // A machine has read what was said so far: a cutscene waiting on it may begin. See DungeonRun.cue.
                case uz.dukeengine.dungeon.run.Read read -> run.read(read.playerIndex(), read.count());
                // The answer to an offer he has just been shown: accepted, its quests start. See DungeonRun.answer.
                case uz.dukeengine.dungeon.run.Answer answer ->
                        run.answer(answer.playerIndex(), answer.count(), answer.accept());
                // Trading with a merchant he is near. See DungeonRun.buy and sell.
                case uz.dukeengine.dungeon.run.Buy buy -> run.buy(game, buy.playerIndex(), buy.merchant(), buy.ware());
                case uz.dukeengine.dungeon.run.Sell sell -> run.sell(game, sell.playerIndex(), sell.merchant(), sell.slot());
                // A draught from the belt, keys 1-4. See Belt.
                case uz.dukeengine.dungeon.run.Drink drink -> run.drink(game, drink.playerIndex(), drink.belt());
                // Shift held or let go: his hero runs fast or at his own pace.
                case uz.dukeengine.dungeon.run.Sprinting sprint -> {
                    var hero = Skills.heroOf(game.getLogic(), sprint.playerIndex());
                    var legs = hero == null ? null : hero.findModule(uz.dukeengine.dungeon.run.Sprint.class);
                    if (legs != null) {
                        legs.set(sprint.on());
                    }
                }
                // A journey to a found watchtower, paid for. See DungeonRun.travel.
                case uz.dukeengine.dungeon.run.TravelTo travel ->
                        run.travel(game, travel.playerIndex(), travel.tower());
                case uz.dukeengine.dungeon.run.TalkTo talk ->
                        run.talkTo(game, talk.playerIndex(), talk.person());
                // Sent to chop, pick, fill or cook, or to build a fire at his feet. See DungeonRun.work.
                case uz.dukeengine.dungeon.run.Work work -> run.work(game, work.playerIndex(), work.thing(), work.place());
                // A recipe crafted where he stands. See DungeonRun.craft.
                case uz.dukeengine.dungeon.run.Craft craft -> run.craft(game, craft.playerIndex(), craft.recipe());
                // His horse: called, got onto or got off. See DungeonRun.ride.
                case uz.dukeengine.dungeon.run.Ride ride -> run.ride(game, ride.playerIndex(), ride.horse());
                default -> {
                    // Not one of ours; rts has already said so.
                }
            }
        };
        game.onCommand(obey);
        game.onOrder(order -> {
            var command = uz.dukeengine.dungeon.party.PartyOrders.commandOf(order);
            if (command != null) {
                obey.accept(command);
            }
        });

        // The first floor is laid out the same way every later one is, so the
        // deep floors nobody plays as often cannot drift from the first. A party's
        // waits for its heroes, and this machine says its own as the match starts.
        game.onStart(started -> {
            // Every death, heard as it is reaped: what a quest's KILL counts.
            started.getLogic().onDied(run::died);
            run.openOn(started, floor);
            if (localHero != null && started.getLocalPlayerIndex() > 0) {
                started.postCommand(uz.dukeengine.dungeon.party.PartyOrders.of(
                        new uz.dukeengine.dungeon.party.ChooseHero(started.getLocalPlayerIndex(), localHero)));
            }
        });
        game.onTick(run::tick);
        for (var progress : progresses) {
            game.onTick(ticked -> progress.tick(ticked.getLogic()));
        }

        return new Session(game, run, progresses.getFirst());
    }

    /**
     * A shield charge (SKEWER) aimed down a lane as the wave is: the click says which way, not how far, so the point
     * it carries is put out at the skill's whole Distance that way -- the length its lane is drawn (Main.rangeOf). The
     * engine drives no further than the point it is given. Any other cast goes as it came.
     */
    static CastSkill toTheEndOfItsLane(uz.dukeengine.core.GameLogic logic, CastSkill cast,
            java.util.List<uz.dukeengine.rpg.skill.Skill> skills) {
        var skill = skills.stream().filter(each -> each.key() == cast.key()).findFirst().orElse(null);
        var hero = cast.point() == null || skill == null
                || skill.effect() != uz.dukeengine.rpg.skill.SkillEffect.SKEWER
                ? null : Skills.heroOf(logic, cast.playerIndex());
        if (hero == null) {
            return cast;
        }
        var from = hero.getPosition();
        float dx = cast.point().x() - from.x();
        float dy = cast.point().y() - from.y();
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) {
            return cast;
        }
        float scale = skill.distance() / length;
        return new CastSkill(cast.playerIndex(), cast.key(), cast.target(), new uz.dukeengine.core.math.Coord3D(
                from.x() + dx * scale, from.y() + dy * scale, cast.point().z()));
    }

    /**
     * Where {@code player}'s hero says he has levelled: across the screen of the machine that plays him, and on no
     * other machine of a party.
     */
    public static java.util.function.Consumer<String> banner(DukeGame game, GamePlayer player) {
        return words -> {
            if (player.getIndex() == game.getLocalPlayerIndex()) {
                game.setBanner(words);
            }
        };
    }

    /**
     * What every errand for a thing on the floor shares. Asked as an order is obeyed rather than at assembly, by
     * which time the dungeon's side has the number the engine gave it: whatever a hero puts down is the dungeon's,
     * so it is nobody's hero's to select.
     */
    private static uz.dukeengine.rpg.item.ItemErrand.Rules errandRules(DungeonSettings settings, Arena arena) {
        var drops = settings.lootDrops();
        return new uz.dukeengine.rpg.item.ItemErrand.Rules(drops.pickupRange(), drops.noteFrames(),
                drops.template(), arena.dungeon().getIndex(), drops.fullWord(), drops.noUseWord(), drops.noWayWord(),
                drops.stuckFrames(), drops.stuckFightingFrames());
    }

    /** The attributes of the hero this creature is, or none for anything that is not one. */
    private static HeroAttributes attributesOf(DungeonSettings settings,
            uz.dukeengine.core.thing.GameObject owner) {
        return settings.heroNamed(owner.getTemplate().name()).attributes();
    }

    /**
     * A weapon with no name of its own named after the creature holding it, so its shot is heard as {@code
     * fired.<Template>} rather than every creature's blow as one sound. A weapon its block names keeps its name; one in
     * sets is the sets'. The name is the sound's and the muzzle's alone: nothing the simulation decides reads it.
     */
    static uz.dukeengine.combat.module.WeaponUpdate.Data namedFor(uz.dukeengine.core.thing.GameObject owner,
            uz.dukeengine.combat.module.WeaponUpdate.Data data) {
        if (data == null || data.name() != null || !data.weaponSets().isEmpty() || owner.getTemplate() == null) {
            return data;
        }
        return new uz.dukeengine.combat.module.WeaponUpdate.Data(data.damage(), data.attackRange(),
                data.reloadFrames(), data.damageType(), data.splashRadius(), data.attackOnTheMove(), data.targets(),
                data.reloadFramesMax(), data.clipSize(), data.clipReloadFrames(), data.autoReload(),
                owner.getTemplate().name(), data.weaponSets(), data.deathType(), data.targetScanFrames(),
                data.preAttackFrames()); // the wind-up too: the blow lands with the swing
    }

    /** A sound the simulation plays riding {@code thing} — see {@code World.sound}: an event, out of the checksum. */
    static void sound(uz.dukeengine.core.thing.GameObject thing, String cue) {
        if (thing != null && thing.getWorld() != null) {
            thing.getWorld().sound(cue, thing, 0);
        }
    }
}
