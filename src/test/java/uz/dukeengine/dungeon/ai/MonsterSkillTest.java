package uz.dukeengine.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.combat.LevelBonus;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.game.DukeGame;

/**
 * A monster that casts: when it may, at whom, and how it keeps its distance.
 *
 * <p>Fought in an open room, so every distance is the test's own, and through the seam
 * the dungeon is built on. Where a test needs the caster to stand still it says so in a
 * {@code Monster} block of its own rather than leaning on the shipped numbers, so
 * retuning the Skeleton Mage cannot quietly turn one of these into a test of nothing.
 */
class MonsterSkillTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String MAGE = "SkeletonMage";
    private static final float CELL = 10f;
    private static final float ROW = 205f;
    private static final int NO_WALL = -1;

    /** What leaves it between fireballs: its weapon's shot, named in its creature block. */
    private static final String ORDINARY_FIRE = "Fireball";

    /** An open room 60 cells by 40; with a wall down one column, and a doorway at its far end. */
    private static String room(int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 60; x++) {
                boolean edge = x == 0 || y == 0 || x == 59 || y == 39;
                boolean wall = x == wallColumn && y != 37;
                text.append(edge || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** A dead end one cell wide, running west out of an open room. */
    private static String deadEnd() {
        var text = new StringBuilder();
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 60; x++) {
                boolean room = x >= 20 && x <= 58 && y >= 1 && y <= 38;
                boolean corridor = y == 20 && x >= 1 && x < 20;
                text.append(room || corridor ? '.' : '#');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static float at(int cell) {
        return (cell + 0.5f) * CELL;
    }

    private record Fight(DukeGame game, GameObject hero, GameObject mage) {

        float gap() {
            return World.reachBetween(mage, hero);
        }
    }

    /** A hero told to stand and pick no fights, and one caster, on one row. */
    private static Fight fight(DungeonSettings settings, String map, float heroX, float mageX) {
        var arena = Dungeon.world(map, settings);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), heroX, ROW);
        game.spawn(MAGE, arena.dungeon(), mageX, ROW);
        game.runHeadless(1);
        // A player has an index once the game has started, and not before.
        arena.orders().hold(arena.hero().getIndex(), true);
        return new Fight(game, creature(game, "Rogue"), creature(game, MAGE));
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /**
     * A caster that never walks: it casts from where it was put, or it does not cast. Its
     * block says nothing of its skill, so it keeps the shipped one.
     */
    private static DungeonSettings standingStill() {
        return DungeonSettings.parse("""
                Monster
                  Name = SkeletonMage
                  SenseRadius = 400
                  ChaseRadius = 400
                  CloseDistance = 500
                  SwingFrames = 20
                  SkillDistance = [20, 60]
                End
                """);
    }

    /** A caster that holds the band it is given, and notices him from anywhere in the room. */
    private static DungeonSettings keeping(String band, String distance) {
        return DungeonSettings.parse("""
                Monster
                  Name = SkeletonMage
                  SenseRadius = 300
                  ChaseRadius = 300
                  CloseDistance = 6
                  SwingFrames = 20
                  SkillDistance = [%s]
                  KeepDistance = [%s]
                End
                """.formatted(distance, band));
    }

    private static String fireball() {
        return SETTINGS.skillsFor(MAGE).get(0).projectile();
    }

    /** Every fireball that leaves it over the next frames, each counted once. */
    private static int thrown(DukeGame game, int frames) {
        var seen = new HashSet<Integer>();
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var object : game.getLogic().getObjects()) {
                if (object.getTemplate().name().equals(fireball())) {
                    seen.add(object.getId().value());
                }
            }
        }
        return seen.size();
    }

    /** The frame each new one of these templates appeared on, over the next frames. */
    private static java.util.Map<String, java.util.List<Integer>> leaving(DukeGame game,
            int frames, String... templates) {
        var when = new java.util.HashMap<String, java.util.List<Integer>>();
        for (var template : templates) {
            when.put(template, new java.util.ArrayList<>());
        }
        var seen = new HashSet<Integer>();
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var object : game.getLogic().getObjects()) {
                var list = when.get(object.getTemplate().name());
                if (list != null && seen.add(object.getId().value())) {
                    list.add(game.getLogic().getFrame());
                }
            }
        }
        return when;
    }

    // ---- when it may ----

    @Test
    void itThrowsItsFireballAtAHeroInsideItsDistance() {
        var fight = fight(standingStill(), room(NO_WALL), 240f, 200f);
        float health = fight.hero().getBody().getHealth();

        assertTrue(thrown(fight.game(), 60) >= 1,
                "he is " + fight.gap() + " away and in plain sight, and nothing left it");
        fight.game().runHeadless(90);
        assertTrue(fight.hero().getBody().getHealth() < health, "and what it threw reached him");
    }

    /** Too far, too near, and then the step inside that is all it was waiting for. */
    @Test
    void aHeroTooFarOrTooNearDrawsNoFireUntilHeStepsInside() {
        var far = fight(standingStill(), room(NO_WALL), 320f, 200f);
        assertEquals(0, thrown(far.game(), 150), "he is " + far.gap() + " away, beyond its 60");

        var near = fight(standingStill(), room(NO_WALL), 212f, 200f);
        assertEquals(0, thrown(near.game(), 150), "he is " + near.gap() + " away, inside its 20");

        far.hero().setPosition(new Coord3D(240f, ROW, 0f));
        assertTrue(thrown(far.game(), 60) >= 1, "and the moment he is inside, it throws");
    }

    /** It casts at what it can see. The same two, the same distance, and a wall between. */
    @Test
    void aWallBetweenThemHoldsItsFire() {
        var walled = fight(standingStill(), room(22), 255f, 205f);
        assertEquals(0, thrown(walled.game(), 150),
                "the stone at column 22 stands between them, and it threw anyway");

        var open = fight(standingStill(), room(NO_WALL), 255f, 205f);
        assertTrue(thrown(open.game(), 60) >= 1, "and in the same place without the wall, it throws");
    }

    @Test
    void itWaitsForItsCooldownBetweenThrows() {
        var fight = fight(standingStill(), room(NO_WALL), 240f, 200f);
        int cooldown = SETTINGS.skillsFor(MAGE).get(0).cooldownFrames();
        int frames = cooldown * 3;
        // The one it threw the moment it saw him lands first, and is not counted here.
        fight.game().runHeadless(cooldown / 2);

        int count = thrown(fight.game(), frames);

        assertTrue(count >= 2 && count <= 3, count + " fireballs in " + frames
                + " frames, from a skill that comes back every " + cooldown);
    }

    /** While the fireball comes back it throws its ordinary fire, rather than standing about. */
    @Test
    void betweenItsFireballsItThrowsItsOrdinaryFire() {
        var fight = fight(standingStill(), room(NO_WALL), 240f, 200f);
        int cooldown = SETTINGS.skillsFor(MAGE).get(0).cooldownFrames();

        var thrown = leaving(fight.game(), cooldown * 2, fireball(), ORDINARY_FIRE);

        assertTrue(thrown.get(fireball()).size() >= 2, "two cooldowns went by: " + thrown);
        assertTrue(thrown.get(ORDINARY_FIRE).size() >= 3,
                "and its ordinary fire should fill them: " + thrown);
    }

    /** One throw at a time: neither leaves while the other is still leaving its hands. */
    @Test
    void itsFireballAndItsOrdinaryFireNeverLeaveTogether() {
        var settings = standingStill();
        var fight = fight(settings, room(NO_WALL), 240f, 200f);
        int swing = settings.monster(MAGE).swingFrames();

        var thrown = leaving(fight.game(), 600, fireball(), ORDINARY_FIRE);

        assertTrue(thrown.get(fireball()).size() >= 3 && thrown.get(ORDINARY_FIRE).size() >= 5,
                "too few of either to say anything about: " + thrown);
        for (int cast : thrown.get(fireball())) {
            for (int shot : thrown.get(ORDINARY_FIRE)) {
                assertTrue(Math.abs(shot - cast) >= swing,
                        "a fireball on frame " + cast + " and its ordinary fire on " + shot);
            }
        }
    }

    // ---- at whom ----

    /** Two heroes exactly as near: the tie goes to the one the world made first. */
    @Test
    void ofTwoAsNearItThrowsAtTheOneThatCameFirst() {
        for (boolean leftFirst : new boolean[] {true, false}) {
            var arena = Dungeon.world(room(NO_WALL), standingStill());
            var game = arena.game();
            game.spawn("Rogue", arena.hero(), leftFirst ? 160f : 240f, ROW);
            game.spawn("Rogue", arena.hero(), leftFirst ? 240f : 160f, ROW);
            game.spawn(MAGE, arena.dungeon(), 200f, ROW);
            game.runHeadless(1);
            arena.orders().hold(arena.hero().getIndex(), true);

            GameObject ball = null;
            for (int frame = 0; frame < 60 && ball == null; frame++) {
                game.runHeadless(1);
                ball = creature(game, fireball());
            }
            assertNotNull(ball, "nothing was thrown at either of them");
            game.runHeadless(8);

            float went = ball.getPosition().x() - 200f;
            assertTrue(leftFirst ? went < 0f : went > 0f, (leftFirst ? "left" : "right")
                    + " came first, and the fireball went " + (went < 0f ? "left" : "right"));
        }
    }

    // ---- keeping its distance ----

    @Test
    void itBacksAwayFromAHeroWhoComesTooNear() {
        var fight = fight(SETTINGS, room(NO_WALL), 215f, 200f);
        float before = fight.gap();
        float from = fight.mage().getPosition().x();

        fight.game().runHeadless(150);

        assertTrue(fight.gap() > before + 15f, "it should have opened the gap from " + before
                + ", and it is " + fight.gap());
        assertTrue(fight.mage().getPosition().x() < from, "by going away from him");
    }

    @Test
    void itComesAfterAHeroWhoIsTooFarAndStopsInsideTheBand() {
        var fight = fight(keeping("35, 55", "20, 60"), room(NO_WALL), 350f, 200f);

        fight.game().runHeadless(600);

        assertTrue(fight.gap() <= 61f, "it should have come to within 55, and stands at " + fight.gap());
        assertTrue(fight.gap() >= 29f, "and no nearer than 35, and stands at " + fight.gap());
    }

    /** Straight back when the room allows it; nowhere at all at the end of a dead end. */
    @Test
    void aDeadEndLeavesItNowhereToBackAwayTo() {
        var arena = Dungeon.world(deadEnd(), SETTINGS);
        arena.game().runHeadless(1);
        World world = arena.game().getLogic();

        assertNull(KeepingDistance.stepBack(world, new Coord3D(at(2), at(20), 0f),
                new Coord3D(at(6), at(20), 0f), 63f, SETTINGS.combat().retreatTurnDegrees(),
                SETTINGS.combat().retreatTurns()), "stone behind it and on both sides, and yet it found a way");

        var inTheOpen = new Coord3D(at(40), at(20), 0f);
        var spot = KeepingDistance.stepBack(world, inTheOpen, new Coord3D(at(44), at(20), 0f), 63f,
                SETTINGS.combat().retreatTurnDegrees(), SETTINGS.combat().retreatTurns());
        assertNotNull(spot, "in the open it has somewhere to go");
        assertEquals(inTheOpen.y(), spot.y(), 0.01f, "and it is straight back");
        assertTrue(spot.x() < inTheOpen.x(), "away from him");
    }

    /** The band is the file's: the same fight with a wider band settles further out. */
    @Test
    void aWiderBandInTheFileIsAWiderBandOnTheFloor() {
        float close = settledGap("35, 55");
        float wide = settledGap("80, 100");

        assertTrue(close < 62f, "with 35 to 55 it settled at " + close);
        assertTrue(wide > 72f, "with 80 to 100 it settled at " + wide);
    }

    /** Where it settles holding {@code band}, casting at him from anywhere its fireball reaches. */
    private static float settledGap(String band) {
        var fight = fight(keeping(band, "20, 70"), room(NO_WALL), 215f, 200f);
        fight.game().runHeadless(600);
        return fight.gap();
    }

    // ---- how hard, and how reliably ----

    /** With a level's bonus it hits harder: the bonus reaches its skill as well as its weapon. */
    @Test
    void aCasterWithALevelsBonusHitsHarder() {
        float plain = aBlowFrom(1f);
        float twice = aBlowFrom(2f);

        assertTrue(plain > 0f, "the fireball never reached him");
        assertEquals(plain * 2f, twice, 0.05f, "twice the bonus should be twice the blow");
    }

    /**
     * What one fireball takes off the hero, from a caster carrying this bonus -- at the first
     * level, so nothing a higher one would open is open. The one it throws the moment it sees
     * him leaves before the bonus is put on, so it is the next one, a cooldown later, that is
     * measured.
     */
    private static float aBlowFrom(float bonus) {
        var fight = fight(standingStill(), room(NO_WALL), 240f, 200f);
        if (bonus != 1f) {
            fight.mage().addModule(new LevelBonus(fight.mage(), 1, bonus, 1f));
        }
        int cooldown = SETTINGS.skillsFor(MAGE).get(0).cooldownFrames();
        fight.game().runHeadless(cooldown / 2);
        float health = fight.hero().getBody().getHealth();
        fight.game().runHeadless(cooldown);
        return health - fight.hero().getBody().getHealth();
    }

    /** The same fight twice is the same fight: frame by frame, bit for bit. */
    @Test
    void theSameFightPlaysOutTheSameEveryTime() {
        assertEquals(checksums(), checksums());
    }

    private static String checksums() {
        var fight = fight(SETTINGS, room(NO_WALL), 215f, 200f);
        var line = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            fight.game().runHeadless(30);
            line.append(fight.game().getLogic().checksum()).append('|');
        }
        return line.toString();
    }

    // ---- which of its skills ----

    /** What the fire mage's added skill throws in these fights: a shadow spark, which nothing else in the room does. */
    private static final String SPARK = "ShadowSpark";

    /**
     * The shipped files with the fire mage given more skills, written after its fireball -- where a block that re-tunes
     * a unit puts the skills it adds, behind the ones its shipped block writes.
     */
    private static DungeonSettings withSkillsAfterItsFireball(String... skills) {
        var mage = ShippedBlock.of(MAGE);
        var text = mage.text().replace("    End\n  ]\nEnd\n", "    End,\n" + String.join(",\n", skills) + "\n  ]\nEnd\n");
        assertNotEquals(mage.text(), text, "the premise: the fire mage was given more skills");
        return DungeonSettings.parse(text);
    }

    /** A skill on {@code key} throwing a shadow spark, costing {@code cost} and waiting for level {@code opensAt}. */
    private static String spark(char key, int cost, int opensAt) {
        return """
                    Skill
                      Key = %s
                      Effect = SKILLSHOT
                      Damage = 5
                      Range = 70
                      ProjectileSpeed = 120
                      Projectile = %s
                      CooldownFrames = 300
                      ManaCost = %d
                      LevelPerRank = %d
                      MaxRank = 1
                    End""".formatted(key, SPARK, cost, opensAt);
    }

    /**
     * The fire mage made {@code level} where it stands -- by the spawner's own step, before anybody is there to cast
     * at -- and then a Rogue holding his ground inside its band.
     */
    private static Fight atLevel(DungeonSettings settings, int level) {
        var arena = Dungeon.world(room(NO_WALL), settings);
        var game = arena.game();
        game.spawn(MAGE, arena.dungeon(), 200f, ROW);
        game.runHeadless(1);
        var mage = creature(game, MAGE);
        Spawner.scale(mage, level, settings);
        arena.orders().hold(arena.hero().getIndex(), true);
        game.spawn("Rogue", arena.hero(), 240f, ROW);
        return new Fight(game, creature(game, "Rogue"), mage);
    }

    /**
     * Of its skills it casts the first, in the order its block writes them, that is open, ready and affordable -- and
     * then nothing at all until the gesture is over: the next skill, and its ordinary fire, a swing's frames later.
     */
    @Test
    void itCastsTheFirstOpenReadySkillInFileOrderAndNothingUntilTheGestureEnds() {
        var settings = withSkillsAfterItsFireball(spark('W', 0, 0));
        int swing = settings.monster(MAGE).swingFrames();

        var thrown = leaving(atLevel(settings, 1).game(), 90, SPARK, fireball(), ORDINARY_FIRE);

        assertFalse(thrown.get(fireball()).isEmpty(), "the skill written first was never cast: " + thrown);
        int first = thrown.get(fireball()).getFirst();
        assertFalse(thrown.get(SPARK).isEmpty(), "and once its gesture was over, the next never went: " + thrown);
        assertTrue(thrown.get(SPARK).getFirst() > first, "the one written second went first: " + thrown);
        for (var shot : new String[] {SPARK, ORDINARY_FIRE}) {
            assertTrue(thrown.get(shot).stream().allMatch(frame -> frame < first || frame >= first + swing),
                    shot + " left inside the gesture of the cast on frame " + first + ": " + thrown);
        }
    }

    /** A skill waits for the level its first rank does, as a hero's does: below it never cast, from it cast. */
    @Test
    void aSkillOpensAtTheLevelItsFirstRankWaitsFor() {
        var settings = withSkillsAfterItsFireball(spark('W', 0, 6));

        var below = leaving(atLevel(settings, 5).game(), 400, SPARK, fireball());
        assertTrue(below.get(SPARK).isEmpty(), "cast below the level it waits for: " + below);
        assertFalse(below.get(fireball()).isEmpty(), "the premise: it cast its fireball: " + below);

        var from = leaving(atLevel(settings, 6).game(), 90, SPARK);
        assertFalse(from.get(SPARK).isEmpty(), "never cast at the level it waits for: " + from);
    }

    /**
     * A skill it cannot pay for is refused before anything is spent, and the next one is cast: with less in its pool
     * than its fireball costs, it throws the spark written after it.
     */
    @Test
    void aSkillItCannotPayForPassesToTheNext() {
        var fight = atLevel(withSkillsAfterItsFireball(spark('W', 5, 0)), 1);
        var book = fight.mage().findModule(SkillBook.class);
        book.resize(book.skillOn('Q').manaAt(1) - 1, 0);

        var thrown = leaving(fight.game(), 60, SPARK, fireball());

        assertTrue(thrown.get(fireball()).isEmpty(), "it threw what it could not pay for: " + thrown);
        assertTrue(book.isReady('Q'), "and what it could not pay for spent its cooldown");
        assertFalse(thrown.get(SPARK).isEmpty(), "and nothing else was cast in its place: " + thrown);
    }

    /** A passive is never cast, wherever it is written: the brain passes over it to the next. */
    @Test
    void aPassiveIsNeverCastAndTheNextIs() {
        var fight = atLevel(withSkillsAfterItsFireball(
                "    Skill\n      Key = W\n      Effect = LIFESTEAL\n      BoostPercent = 25\n    End",
                spark('E', 0, 0)), 1);

        var thrown = leaving(fight.game(), 60, fireball(), SPARK);

        assertFalse(thrown.get(fireball()).isEmpty() || thrown.get(SPARK).isEmpty(),
                "the passive between the fireball and the spark stopped its casting: " + thrown);
        assertEquals(0, fight.mage().findModule(SkillBook.class).cooldownOf('W'), "and the passive was cast");
    }

    /**
     * A cast its book declines passes to its next skill, that frame: a W whose shot does not fly -- a mending light
     * -- is refused every time with its cooldown unspent, and the spark written after it is thrown in its place.
     */
    @Test
    void aCastItsBookDeclinesPassesToTheNextSkill() {
        var fight = atLevel(withSkillsAfterItsFireball(
                spark('W', 0, 0).replace("Projectile = " + SPARK, "Projectile = MendingLight"), spark('E', 0, 0)), 1);

        var thrown = leaving(fight.game(), 60, SPARK);

        assertTrue(fight.mage().findModule(SkillBook.class).isReady('W'), "the premise: its book never let the W go");
        assertFalse(thrown.get(SPARK).isEmpty(), "and nothing went in its place: " + thrown);
    }

    /** A skill aimed at him has to reach the far end of its band: a Range short of it is refused when the file is read. */
    @Test
    void anAimedSkillThatFallsShortOfItsBandIsRefused() {
        var data = ShippedBlock.of(MAGE).with("SkillDistance", "[20, 80]").text();

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("Range"), refused.getMessage());
    }
}
