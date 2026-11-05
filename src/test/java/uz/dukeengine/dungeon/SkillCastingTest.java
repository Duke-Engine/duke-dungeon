package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.rpg.skill.CastSkill;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.game.DukeGame;

/**
 * Casting: what each key does, what stops it, and how it grows.
 *
 * <p>Fought in the game's own arena with the game's own creatures, so a skill is
 * tested against the hero the player actually has. Nothing here asserts a balance
 * figure — the numbers come from the file, and a re-tune moves the test with them
 * rather than breaking it. What is held still is the mechanism: that a cooldown
 * refuses, that an ultimate waits for its level, that a level makes a skill
 * stronger, that the file decides all three.
 */
class SkillCastingTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /**
     * A floor of the real descent he comes in facing open ground on: the first seed from 11 up.
     *
     * <p>The dash goes the way he faces, and a hero who comes in nose to the rock dashes
     * nowhere -- which the tests that press E through the queue would read as the command
     * never arriving. Which floors those are moves with every change to the generator (11
     * was one until the floors grew, and put him beside the fountain facing a cliff), so
     * rather than a seed that happened to work, the first one that does.
     */
    private static final long FACING_OPEN_GROUND = firstSeedFacingOpenGround();

    private static long firstSeedFacingOpenGround() {
        for (long seed = 11; seed < 211; seed++) {
            var game = Dungeon.newSession(seed, SETTINGS).game();
            game.runHeadless(2);
            if (facesOpenGround(game, creature(game, "Rogue"), 60f)) {
                return seed;
            }
        }
        throw new AssertionError("no floor in two hundred lets the Rogue dash the way he comes in facing");
    }

    /** Open floor, and nothing standing within a body's width of it, for {@code reach} the way he faces. */
    private static boolean facesOpenGround(DukeGame game, GameObject hero, float reach) {
        var grid = game.getTerrain();
        var from = hero.getPosition();
        for (float along = 2f; along <= reach; along += 2f) {
            float x = from.x() + hero.getFacingCos() * along;
            float y = from.y() + hero.getFacingSin() * along;
            int cx = (int) Math.floor(x / grid.getCellSize());
            int cy = (int) Math.floor(y / grid.getCellSize());
            if (!grid.inBounds(cx, cy) || grid.isTerrainBlocked(cx, cy)) {
                return false;
            }
            for (var other : game.getLogic().getObjects()) {
                var at = other.getPosition();
                if (other != hero && Math.hypot(at.x() - x, at.y() - y) < 12f) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String arena() {
        int width = 40;
        int height = 30;
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record Arena(DukeGame game, GameObject hero, SkillBook book) {
    }

    /** The hero alone in a room, plus however many skeletons the test wants. */
    private static Arena arena(DungeonSettings settings, float... skeletonXy) {
        return arena(settings, Content.units(), skeletonXy);
    }

    private static Arena arena(DungeonSettings settings, String creaturesIni, float... skeletonXy) {
        var world = Dungeon.world(arena(), settings, creaturesIni);
        var game = world.game();
        game.spawn("Rogue", world.hero(), 150f, 150f);
        for (int i = 0; i + 1 < skeletonXy.length; i += 2) {
            game.spawn("Skeleton", world.dungeon(), skeletonXy[i], skeletonXy[i + 1]);
        }
        game.runHeadless(1);
        var hero = creature(game, "Rogue");
        return new Arena(game, hero, hero.findModule(SkillBook.class));
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /**
     * The shipped creatures with the hero's bow taken away.
     *
     * <p>His strike is no longer instant — he draws, looses, and the arrow crosses
     * the room — so a test about it has to let the world run, and a world that runs
     * is a world in which his ordinary bow is also shooting. Then "the skeleton
     * lost health" says nothing about the skill.
     *
     * <p>A weapon that reaches nothing acquires nothing, so with this file anything
     * that gets hurt was hurt by a skill. His skills are untouched: their range is
     * their own, in his Skill blocks.
     */
    private static String creaturesWithNoBow() {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text());
    }

    /** An arena where only his skills can hurt anything. */
    private static Arena skillsOnly(float... skeletonXy) {
        return arena(SETTINGS, creaturesWithNoBow(), skeletonXy);
    }

    /**
     * The hero and the boss, which is what to use when the test needs its target
     * to still be there afterwards — a skeleton dies to one heavy shot, and a
     * question about what he does next cannot be asked of an empty room.
     */
    private static Arena bossArena(String creaturesIni, float distance) {
        var world = Dungeon.world(arena(), SETTINGS, creaturesIni);
        var game = world.game();
        game.spawn("Rogue", world.hero(), 150f, 150f);
        game.spawn(SETTINGS.bossKindAt(SETTINGS.finalDepth()), world.dungeon(),
                150f + distance, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Rogue");
        return new Arena(game, hero, hero.findModule(SkillBook.class));
    }

    /**
     * Cast, then run the world on until the shot has been drawn, loosed and landed.
     *
     * <p>What the older tests asked — "cast it, is it hurt?" — is now a question
     * about two moments rather than one.
     */
    private static boolean castAndWait(Arena arena, char key, int level, ObjectId at) {
        boolean cast = arena.book().cast(key, level, at, null);
        arena.game().runHeadless(untilItLands(key));
        return cast;
    }

    /** The skill's own wind-up, plus flight enough to cross its whole range. */
    private static int untilItLands(char key) {
        var skill = SETTINGS.skillsFor("Rogue").stream()
                .filter(s -> s.key() == key).findFirst().orElseThrow();
        return skill.windUpFrames() + 60;
    }

    private static long arrowsInTheAir(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .count();
    }

    private static long livingSkeletons(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Skeleton"))
                .filter(object -> !object.isEffectivelyDead())
                .count();
    }

    /** The hero has a skill book at all, built from the file rather than from Java. */
    @Test
    void theHeroCarriesTheSkillsTheFileGaveHim() {
        var arena = arena(SETTINGS);

        assertNotNull(arena.book(), "his creature block asks for a SkillBook");
        assertEquals(SETTINGS.bookFor("Rogue"), arena.book().getSkills());
    }

    // ---- what each key does ----

    // ---- the drawn shot ----

    /** The skill the file describes, by key. */
    private static uz.dukeengine.rpg.skill.Skill skillNamed(char key) {
        return SETTINGS.skillsFor("Rogue").stream()
                .filter(skill -> skill.key() == key).findFirst().orElseThrow();
    }

    /** Every ordinary arrow in the air right now, by id. */
    private static java.util.Set<Integer> ordinaryArrows(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(SETTINGS.combat().arrowTemplate()))
                .map(o -> o.getId().value())
                .collect(java.util.stream.Collectors.toSet());
    }

    /** {@code ReloadFrames} from the hero's own block, rather than a copy of it. */
    private static int heroReloadFrames() {
        return Integer.parseInt(ShippedBlock.of("Rogue").value("ReloadFrames"));
    }

    // ---- the ultimate ----

    /** What one cast takes off the boss, with or without the ultimate up first. */
    private static float damageFrom(char key, int level, boolean underTheUltimate) {
        var arena = bossArena(creaturesWithNoBow(), 20f);
        var game = arena.game();
        var book = arena.book();
        var boss = creature(game, SETTINGS.bossKindAt(SETTINGS.finalDepth()));

        if (underTheUltimate) {
            assertTrue(book.cast('R', level), "the ultimate should have gone up");
        }
        float before = boss.getBody().getHealth();
        book.cast(key, level);
        game.runHeadless(untilItLands(key));
        return before - boss.getBody().getHealth();
    }

    // ---- aiming ----

    /** And aiming it at himself is not aiming it at an enemy. */
    @Test
    void aStrikeCannotBeAimedAtSomethingThatIsNotAnEnemy() {
        var arena = arena(SETTINGS, 170f, 150f);

        assertFalse(arena.book().cast('Q', 1, arena.hero().getId(), null));
        assertEquals(0, arena.book().cooldownOf('Q'));
    }

    /** The first living skeleton past {@code x}, in creation order. */
    private static GameObject skeletonBeyond(DukeGame game, float x) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Skeleton"))
                .filter(object -> object.getPosition().x() > x)
                .findFirst().orElseThrow();
    }

    /** A room split down the middle, so a dash across it has to clear the wall. */
    private static String walledArena() {
        int width = 40;
        int height = 30;
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                text.append(edge || x == 20 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    // ---- cooldown ----

    // ---- levels ----

    /** How much health one Q takes off a fresh skeleton, cast by a hero of {@code level}. */
    private static float damageDealtByStrike(int level) {
        var arena = skillsOnly(170f, 150f);
        var skeleton = creature(arena.game(), "Skeleton");
        float before = skeleton.getBody().getHealth();
        castAndWait(arena, 'Q', level, null);
        return before - skeleton.getBody().getHealth();
    }

    // ---- the file decides ----

    /**
     * A second hero, described only in data, has his own skills and works.
     *
     * <p>The point of the whole shape: effects are code, heroes are file. Adding a
     * hero must never mean adding a class, or the fifth one never gets written.
     */
    @Test
    void aSecondHeroDescribedOnlyInDataWorks() {
        // ★ A hero this game has never heard of, and the name has to stay that
        // way. He used to be called Rogue, which was fine while the archer was
        // called Hero and stopped being fine the moment the archer BECAME the
        // rogue: the test then handed its two invented skills to a hero who
        // already had four, and asked why he had six.
        var withTwo = DungeonSettings.parse("""
                Hero
                  Name = Sellsword
                  Skills = [
                    Skill
                      Key = A
                      Effect = DASH
                      Distance = 120
                      CooldownFrames = 60
                    End,
                    Skill
                      Key = S
                      Effect = STRIKE
                      Damage = 500
                      Range = 60
                      CooldownFrames = 30
                    End
                  ]
                End
                """);
        var creatures = Content.units()
                .replace("Hero\n  Name = Rogue\n", "Hero\n  Name = Sellsword\n");

        var world = Dungeon.world(arena(), withTwo, creatures);
        var game = world.game();
        game.spawn("Sellsword", world.hero(), 150f, 150f);
        game.spawn("Skeleton", world.dungeon(), 180f, 150f);
        game.runHeadless(1);
        var sellsword = creature(game, "Sellsword");
        var book = sellsword.findModule(SkillBook.class);

        assertNotNull(book, "he asked for a SkillBook in the same way");
        assertEquals(2, book.getSkills().size(), "and got the two the file gave him");
        assertTrue(book.cast('S', 1), "on his own key, which no shipped hero has");
        assertEquals(0, livingSkeletons(game));
        assertFalse(book.cast('Q', 1), "and not on a key that is not his");
    }

    // ---- through the command pipeline ----

    // ---- determinism ----

    // ---- the two shapes no hero uses yet, for the heroes that come next ----

    /**
     * A hero whose Q is the shape being tested, with the rest of him untouched.
     *
     * <p>Written as a file rather than as a fifth skill on the shipped hero,
     * because that is exactly how the next hero will arrive: a block of data and no
     * Java. If these tests need a line of code changed to run, the claim they are
     * making is false.
     */
    private static DungeonSettings heroWhose(String q) {
        return DungeonSettings.parse("Hero\n  Name = Rogue\n  Skills = [\n    Skill\n      Key = Q\n" + q + "    End\n  ]\nEnd\n");
    }

    /**
     * A blast the player puts on a spot hurts what is standing there, not what is
     * standing near him.
     *
     * <p>The whole difference from the area skill he already has, and the reason
     * it is worth being a second kind: one is a panic button and the other is a
     * shot he has to place.
     */
    @Test
    void aBlastAimedAtASpotLandsOnTheSpot() {
        var settings = heroWhose("""
                  Effect = AREA_AT_SPOT
                  Damage = 40
                  Range = 120
                  Radius = 25
                  CooldownFrames = 30
                """);
        // One skeleton out where the blast is aimed, one at his elbow.
        var arena = arena(settings, creaturesWithNoBow(), 250f, 150f, 170f, 150f);
        var far = creature(arena.game(), "Skeleton");
        var near = arena.game().getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Skeleton"))
                .skip(1).findFirst().orElseThrow();
        float wasFar = far.getBody().getHealth();
        float wasNear = near.getBody().getHealth();

        assertTrue(arena.book().cast('Q', 1, null, new Coord3D(250f, 150f, 0f)));
        arena.game().runHeadless(3);

        assertTrue(far.getBody().getHealth() < wasFar, "it should have landed where he put it");
        assertEquals(wasNear, near.getBody().getHealth(), 0.01f,
                "and left the one beside him alone, or it is not aimed at all");
    }

    /** Pointing past its reach throws it as far as it goes, rather than refusing. */
    @Test
    void aBlastAimedTooFarLandsAtTheEdgeOfHisReach() {
        var settings = heroWhose("""
                  Effect = AREA_AT_SPOT
                  Damage = 40
                  Range = 60
                  Radius = 25
                  CooldownFrames = 30
                """);
        // Beyond his reach of 60 from 150, but within it once the click is pulled
        // back to the edge: the picture on screen promises exactly this.
        var arena = arena(settings, creaturesWithNoBow(), 205f, 150f);
        var skeleton = creature(arena.game(), "Skeleton");
        float was = skeleton.getBody().getHealth();

        assertTrue(arena.book().cast('Q', 1, null, new Coord3D(400f, 150f, 0f)));
        arena.game().runHeadless(3);

        assertTrue(skeleton.getBody().getHealth() < was,
                "the ring says 'as far that way as you can', and the cast has to agree");
    }

    /**
     * A shot down a lane hits whoever is standing in it — and misses when nobody
     * is.
     *
     * <p>Both halves matter and the second one is the point of the shape: this is
     * the skill that can be wasted, which is what makes placing it worth anything.
     */
    @Test
    void aShotDownALaneHitsWhatIsInTheWayAndMissesWhatIsNot() {
        var settings = heroWhose("""
                  Effect = SKILLSHOT
                  Damage = 30
                  Range = 200
                  Radius = 6
                  Projectile = HeavyArrow
                  CooldownFrames = 1
                """);
        var arena = arena(settings, creaturesWithNoBow(), 250f, 150f);
        var skeleton = creature(arena.game(), "Skeleton");
        float was = skeleton.getBody().getHealth();

        // Pointed at it.
        assertTrue(arena.book().cast('Q', 1, null, new Coord3D(280f, 150f, 0f)));
        arena.game().runHeadless(40);
        float afterAHit = skeleton.getBody().getHealth();
        assertTrue(afterAHit < was, "a shot down its lane should have hit it");

        // Pointed the other way, at nothing at all.
        assertTrue(arena.book().cast('Q', 1, null, new Coord3D(150f, 250f, 0f)));
        arena.game().runHeadless(40);

        assertEquals(afterAHit, skeleton.getBody().getHealth(), 0.01f,
                "and a shot at nothing should have missed — it does not chase");
    }

    /** It stops at a wall rather than carrying on through the map. */
    @Test
    void aShotDownALaneIsSpentAgainstStone() {
        var settings = heroWhose("""
                  Effect = SKILLSHOT
                  Damage = 30
                  Range = 400
                  Radius = 6
                  Projectile = HeavyArrow
                  CooldownFrames = 1
                """);
        var arena = arena(settings, creaturesWithNoBow());

        assertTrue(arena.book().cast('Q', 1, null, new Coord3D(150f, 10f, 0f)),
                "straight at the north wall");
        arena.game().runHeadless(1);
        assertNotNull(creature(arena.game(), "HeavyArrow"), "it left the bow");

        arena.game().runHeadless(90);

        assertNull(creature(arena.game(), "HeavyArrow"),
                "the lane the client drew stops at the wall, and the shot has to as well");
    }

    /** And the two new shapes keep the world reproducible, like everything else. */
    @Test
    void theNewShapesAreDeterministic() {
        assertEquals(lanesPlayedOut(), lanesPlayedOut());
    }

    private static long lanesPlayedOut() {
        var settings = heroWhose("""
                  Effect = SKILLSHOT
                  Damage = 30
                  Range = 200
                  Radius = 6
                  Projectile = HeavyArrow
                  CooldownFrames = 10
                """);
        var arena = arena(settings, creaturesWithNoBow(), 250f, 150f, 200f, 200f);
        for (int shot = 0; shot < 4; shot++) {
            arena.book().cast('Q', 1, null, new Coord3D(250f + shot, 150f + shot, 0f));
            arena.game().runHeadless(20);
        }
        return arena.game().getLogic().checksum();
    }

    private static long playedOut(boolean cast) {
        var session = Dungeon.newSession(FACING_OPEN_GROUND, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        // Bought before cast, and bought in BOTH runs: what is being compared is
        // the casting, so spending the points has to happen either way or the
        // difference would be the shopping rather than the skills.
        // ★ ONE skill, and his dash. He has exactly one point at his first level,
        // so a run that cast all four would be casting three he does not own --
        // and "nothing happened" is the same world twice, which is this test
        // passing for the wrong reason. The dash is the one whose effect cannot
        // be mistaken for luck: it moves him, every time, with nothing else in
        // the room required.
        learn(game, 'E');
        for (int press = 0; press < 5; press++) {
            if (cast) {
                game.postCommand(new CastSkill(game.getLocalPlayerIndex(), 'E'));
            }
            game.runHeadless(40);
        }
        return game.getLogic().checksum();
    }

    /**
     * Spend a point on a slot, through the queue, the way a click does.
     *
     * <p>Only one is ever available at the first level, so this is "the one skill
     * he starts with" rather than a way to hand him a finished build.
     */
    private static void learn(uz.dukeengine.game.DukeGame game, char key) {
        game.postCommand(new uz.dukeengine.rpg.skill.UpgradeSkill(
                game.getLocalPlayerIndex(), key));
        game.runHeadless(1);
    }
}
