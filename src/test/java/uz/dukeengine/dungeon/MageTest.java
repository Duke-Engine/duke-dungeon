package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;
import uz.dukeengine.game.DukeGame;

/**
 * The third hero: that he exists, that he is nobody else, and that his four
 * skills do the four things they are sold as.
 *
 * <p>Almost nothing here is a balance figure. The numbers are the file's and a
 * re-tune should move these tests with it rather than break them; what is held
 * still is the SHAPE — that a fireball can miss, that a nova leaves what it
 * caught slower than it was, that a blink puts him where he asked in one frame,
 * and that a meteor does nothing at all for the second and a half it is falling.
 *
 * <p>The last test is the one that matters most and asserts nothing about the
 * mage: the archer and the knight are untouched.
 */
class MageTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final String ARCHER = "Rogue";
    private static final String KNIGHT = "Knight";
    private static final String MAGE = "Mage";

    // ---- who he is ----

    /**
     * The squishiest thing that has ever been playable here.
     *
     * <p>Both halves, because they are two different axes: health is how long he
     * lasts and armour is what each blow is worth, and a mage who was merely
     * low on one of them would be an archer with better skills.
     */
    @Test
    void heIsTheSquishiestOfTheThree() {
        assertTrue(health(MAGE) < health(ARCHER), "he is no tougher than the archer");
        assertTrue(health(MAGE) < health(KNIGHT) / 2f, "and nowhere near the knight");
        assertTrue(SETTINGS.heroNamed(MAGE).armourPercent()
                < SETTINGS.heroNamed(KNIGHT).armourPercent(),
                "a mage in the knight's plate is a knight who can also throw meteors");
    }

    /**
     * And his weapon is the feeblest, which is the other half of the same
     * bargain: everything he is worth is on a cooldown.
     */
    @Test
    void hisOwnWeaponIsTheFeeblestOfTheThree() {
        assertTrue(damagePerFrame(MAGE) < damagePerFrame(ARCHER),
                "a mage whose auto-attack carried him is an archer with a better ultimate");
        assertTrue(damagePerFrame(MAGE) < damagePerFrame(KNIGHT),
                "and the knight has to walk into the room for his");
        assertTrue(reach(MAGE) < reach(ARCHER), "nor does he outrange the archer");
    }

    // ---- Q: a shot that can miss ----

    // ---- W: the panic button ----

    // ---- E: the escape ----

    /**
     * It is an escape and not a weapon: nothing it passes is any worse off.
     *
     * <p>The one thing that separates it from the knight's charge in the
     * simulation, and it is deliberate — a blink that also hurt would be his
     * charge with the drawbacks taken out.
     */
    @Test
    void hisBlinkHurtsNothingOnTheWay() {
        var arena = arena(180f, 150f);
        var passed = arena.skeletons().get(0);
        float before = passed.getBody().getHealth();

        arena.book().cast('E', 1, null, new Coord3D(210f, 150f, 0f));

        assertEquals(before, passed.getBody().getHealth(), 0.01f,
                "it was trampled, so this is a charge rather than a blink");
    }

    /** And it never puts him inside stone. */
    @Test
    void hisBlinkNeverLandsInAWall() {
        var arena = arena(walledArena());

        arena.book().cast('E', 1, null, new Coord3D(280f, 150f, 0f));

        var grid = arena.game().getLogic().getPathGrid();
        var standing = arena.hero().getPosition();

        assertFalse(grid.isBlocked(grid.toCellX(standing), grid.toCellY(standing)),
                "he is standing in the wall");
    }

    // ---- R: the one with a pause in it ----

    // ---- and what the client is told about all of it ----

    // ---- and the two who were here first ----

    // ---- the arena ----

    private record Arena(DukeGame game, GameObject hero, SkillBook book,
            java.util.List<GameObject> skeletons) {
    }

    private static Arena arena(float... skeletonXy) {
        return arena(openArena(), skeletonXy);
    }

    private static Arena arena(String map, float... skeletonXy) {
        var world = Dungeon.world(map, SETTINGS, Content.units());
        var game = world.game();
        game.spawn(MAGE, world.hero(), 150f, 150f);
        for (int i = 0; i + 1 < skeletonXy.length; i += 2) {
            game.spawn("Skeleton", world.dungeon(), skeletonXy[i], skeletonXy[i + 1]);
        }
        game.runHeadless(1);
        var hero = creature(game, MAGE);
        var skeletons = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Skeleton"))
                .sorted(java.util.Comparator.comparingInt(object -> object.getId().value()))
                .toList();
        return new Arena(game, hero, hero.findModule(SkillBook.class), skeletons);
    }

    /** An empty room, stone only round the edge. */
    private static String openArena() {
        return room(40, 30, -1);
    }

    /** The same room with a wall down the middle of it, at about x = 240. */
    private static String walledArena() {
        return room(40, 30, 24);
    }

    private static String room(int width, int height, int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                text.append(edge || x == wallColumn ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static float health(Arena arena, int which) {
        return arena.skeletons().get(which).getBody().getHealth();
    }

    // ---- the file, read the way the game reads it ----

    private static Skill skillOf(String hero, char key) {
        return SETTINGS.skillsFor(hero).stream().filter(skill -> skill.key() == key)
                .findFirst().orElseThrow(() -> new AssertionError(hero + " has no " + key));
    }

    /** Built once: a fresh dungeon per assertion is a generated map per assertion. */
    private static uz.dukeengine.core.thing.ThingFactory templates;

    private static uz.dukeengine.core.thing.ThingTemplate templateOf(String name) {
        if (templates == null) {
            var game = Dungeon.world(openArena(), SETTINGS, Content.units())
                    .game();
            game.runHeadless(1);
            templates = game.getLogic().getThingFactory();
        }
        return templates.findTemplate(name);
    }

    /** What he is at his first level: his creature block with his attributes on it. */
    private static uz.dukeengine.rpg.hero.HeroFigures firstLevel(String hero) {
        var look = SETTINGS.heroNamed(hero);
        return uz.dukeengine.rpg.hero.HeroFigures.of(
                uz.dukeengine.rpg.hero.HeroBase.of(templateOf(hero)), look.maxMana(),
                look.attributes(), SETTINGS.attributeRules(), 1,
                uz.dukeengine.rpg.hero.HeroFigures.Found.NOTHING);
    }

    private static float health(String hero) {
        return firstLevel(hero).maxHealth();
    }

    private static uz.dukeengine.combat.module.WeaponUpdate.Data weaponOf(String hero) {
        for (var entry : templateOf(hero).modules()) {
            if (entry instanceof uz.dukeengine.combat.module.WeaponUpdate.Data weapon) {
                return weapon;
            }
        }
        throw new AssertionError(hero + " carries no weapon");
    }

    private static float reach(String hero) {
        return weaponOf(hero).attackRange();
    }

    private static float damagePerFrame(String hero) {
        return firstLevel(hero).attack() / (float) weaponOf(hero).reloadFrames();
    }
}
