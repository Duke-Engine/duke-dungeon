package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.dungeon.skill.Summoned;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.combat.module.StatusUpdate;

/**
 * The mages' auras: what each lends everyone of its own round it -- the living of its side that carry a book, within
 * its Radius, middle to middle, and in its plain sight, itself among them -- asked by the one it lends to at the moment
 * the figure is used.
 *
 * <p>Most of it is asked of creatures standing in a room with nobody to fight, so nothing moves the figure read; a blow
 * is fought for real, against a Rogue who cannot answer, where the figure is the blow itself.
 */
class AuraTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String SUMMONER = "SkeletonSummoner";
    private static final String MAGE = "SkeletonMage";
    private static final int NO_WALL = -1;

    /** An open room forty cells by thirty; with a wall down one column, and a doorway at its far end. */
    private static String room(int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean edge = x == 0 || y == 0 || x == 39 || y == 29;
                boolean wall = x == wallColumn && y != 27;
                text.append(edge || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** One creature to place: its kind and where. */
    private record One(String kind, float x, float y) {
    }

    private static One one(String kind, float x, float y) {
        return new One(kind, x, y);
    }

    /** A room, and the dungeon's own standing in it, each in the order it was made. */
    private record Room(DukeGame game, List<GameObject> ones) {

        GameObject get(int index) {
            return ones.get(index);
        }
    }

    /** A room holding these of the dungeon's own, in this order, and nobody to fight. */
    private static Room room(DungeonSettings settings, int wallColumn, One... ones) {
        return room(settings, Content.units(), wallColumn, ones);
    }

    /** The same, of creatures as {@code units} write them. */
    private static Room room(DungeonSettings settings, String units, int wallColumn, One... ones) {
        var arena = Dungeon.world(room(wallColumn), settings, units);
        var game = arena.game();
        for (var one : ones) {
            game.spawn(one.kind(), arena.dungeon(), one.x(), one.y());
        }
        game.runHeadless(1);
        return new Room(game, game.getLogic().getObjects().stream().filter(object -> object.getBody() != null)
                .toList());
    }

    private static SkillBook bookOf(GameObject creature) {
        return creature.findModule(SkillBook.class);
    }

    /** What its blows and its skills are multiplied by, as its weapon and its book read it. */
    private static float might(GameObject creature) {
        return bookOf(creature).damageMultiplier();
    }

    /** The shipped units with the Rogue's bow reaching nothing: whatever he loses, a monster took. */
    private static String unarmedRogue() {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text());
    }

    /** The shipped files with these kinds noticing nobody: they do only what a test does for them. */
    private static DungeonSettings unseeing(String... kinds) {
        var text = new StringBuilder();
        for (var kind : kinds) {
            text.append(ShippedBlock.of(kind).with("SenseRadius", 1).with("ChaseRadius", 1).with("AlertRadius", 0)
                    .text());
        }
        return DungeonSettings.parse(text.toString());
    }

    private static GameObject first(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    /** Runs a frame at a time until {@code hero} loses health: what he lost, within {@code frames}, or 0. */
    private static float untilStruck(DukeGame game, GameObject hero, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            float health = hero.getBody().getHealth();
            game.runHeadless(1);
            if (hero.getBody().getHealth() < health) {
                return health - hero.getBody().getHealth();
            }
        }
        return 0f;
    }

    // ---- might ----

    /**
     * A Skeleton 60 from a summoner, middle to middle, deals 10.5 for its 7: its first blow at a Rogue who cannot
     * answer, the summoner noticing nobody.
     */
    @Test
    void aSkeletonSixtyFromASummonerDealsTenAndAHalfForItsSeven() {
        var arena = Dungeon.world(room(NO_WALL), unseeing(SUMMONER), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 162f, 150f);
        game.spawn(SUMMONER, arena.dungeon(), 162f, 90f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);

        assertEquals(10.5f, untilStruck(game, first(game, "Rogue"), 150), 0.001f, "its first blow");
    }

    /** What the fire mage's fireball takes off a Rogue, thrown by hand -- with a summoner 50 from it, or without. */
    private static float aFireballFrom(boolean summonerBeside) {
        var arena = Dungeon.world(room(NO_WALL), unseeing(MAGE, SUMMONER), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 240f, 150f);
        game.spawn(MAGE, arena.dungeon(), 200f, 150f);
        if (summonerBeside) {
            game.spawn(SUMMONER, arena.dungeon(), 200f, 100f);
        }
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = first(game, "Rogue");
        float health = hero.getBody().getHealth();

        assertTrue(bookOf(first(game, MAGE)).cast('Q', 1, null, hero.getPosition()), "the premise: it threw");
        game.runHeadless(30);
        return health - hero.getBody().getHealth();
    }

    /** A skill's damage is raised alike: the fire mage's fireball, 45, lands 67.5 beside a summoner. */
    @Test
    void aFireMagesFireballIsRaisedAlike() {
        float plain = aFireballFrom(false);

        assertEquals(45f, plain, 0.001f, "the premise: its fireball, alone");
        assertEquals(plain * 1.5f, aFireballFrom(true), 0.001f, "beside a summoner");
    }

    /** At 60, middle to middle, half as hard again; a step further, or behind stone, its own. */
    @Test
    void atSixtyOneOrBehindStoneItsOwn() {
        var at60 = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 160f, 150f));
        assertEquals(1.5f, might(at60.get(1)), 0.0001f, "60 from it");

        var at61 = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 161f, 150f));
        assertEquals(1f, might(at61.get(1)), 0.0001f, "61 from it");

        var walled = room(SETTINGS, 13, one(SUMMONER, 100f, 150f), one("Skeleton", 150f, 150f));
        assertEquals(1f, might(walled.get(1)), 0.0001f, "50 from it, stone between");
    }

    /** Of several of one kind, the strongest -- never the sum: two summoners, and still half as hard again. */
    @Test
    void twoSummonersLendTheStrongerNotBoth() {
        var two = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one(SUMMONER, 160f, 150f),
                one("Skeleton", 130f, 150f));

        assertEquals(1.5f, might(two.get(2)), 0.0001f);
    }

    /** Its bearer is among those it reaches; a hero beside it never is. */
    @Test
    void itsBearerHitsHarderAndAHeroBesideItDoesNot() {
        var arena = Dungeon.world(room(NO_WALL), SETTINGS);
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), 100f, 150f);
        game.spawn("Rogue", arena.hero(), 115f, 150f);
        game.runHeadless(1);

        assertEquals(1.5f, might(first(game, SUMMONER)), 0.0001f, "the summoner itself");
        assertEquals(1f, might(first(game, "Rogue")), 0.0001f, "a hero, 15 from it");
        assertEquals(0, SkillBook.auraOn(first(game, "Rogue"), SkillEffect.DAMAGE_AURA));
    }

    // ---- reach ----

    /**
     * It holds while its bearer lives: the summoner killed, the Skeleton beside it is its own again -- from the moment
     * it falls, its body still lying there, and after.
     */
    @Test
    void itsBearerKilledItsBlowIsItsOwnAgain() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f));
        assertEquals(1.5f, might(room.get(1)), 0.0001f, "the premise: it lends");

        var body = room.get(0).getBody();
        body.damage(body.getHealth() + 1f);

        assertTrue(room.get(0).isEffectivelyDead(), "the premise: the summoner is dead");
        assertEquals(1f, might(room.get(1)), 0.0001f, "the moment it falls");
        room.game().runHeadless(1);
        assertEquals(1f, might(room.get(1)), 0.0001f, "and after");
    }

    /** It holds while its bearer lives, stunned or not: a summoner standing dazed still lends. */
    @Test
    void aStunnedBearerStillLends() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f));
        room.get(0).findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 60);
        room.game().runHeadless(1);

        assertTrue(room.get(0).hasStatus(ObjectStatus.DISABLED), "the premise: the summoner stands dazed");
        assertEquals(1.5f, might(room.get(1)), 0.0001f);
    }

    /** What a summoner calls up is of its own, and lent to as any of them is: two swordsmen and two archers. */
    @Test
    void whatASummonerCallsUpIsLentTo() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 200f, 150f));
        assertTrue(bookOf(room.get(0)).cast('Q', 1, null, new Coord3D(150f, 150f, 0f)),
                "the premise: it opened its rifts");
        room.game().runHeadless(30);

        var risen = room.game().getLogic().getObjects().stream()
                .filter(object -> object.findModule(Summoned.class) != null).toList();
        assertEquals(4, risen.size(), "the premise: its four rose");
        for (var one : risen) {
            assertEquals(1.5f, might(one), 0.0001f, one.getTemplate().name());
        }
    }

    /** A creature that carries no book is not reached, as one with no StatusUpdate is not stunned. */
    @Test
    void aCreatureWithNoBookIsNotReached() {
        var skeleton = ShippedBlock.of("Skeleton").text();
        var bookless = skeleton.replace(
                "    ; Every monster carries one, casting or not: a summoner's haste is held in it.\n"
                        + "    SkillBook\n    End,\n", "");
        assertNotEquals(skeleton, bookless, "the premise: the Skeleton's book was taken out");
        var room = room(SETTINGS, Content.units().replace(skeleton, bookless), NO_WALL,
                one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f));

        assertNull(bookOf(room.get(1)), "the premise: it carries none");
        assertEquals(0, SkillBook.auraOn(room.get(1), SkillEffect.DAMAGE_AURA));
    }

    /** An aura its bearer's level has not opened lends nothing, and lends from the level that opens it. */
    @Test
    void anAuraItsBearersLevelHasNotOpenedLendsNothing() {
        var data = Content.data().replace("      Effect = DAMAGE_AURA\n",
                "      Effect = DAMAGE_AURA\n      LevelPerRank = 6\n");
        assertNotEquals(Content.data(), data, "the premise: the summoner's might waits for its sixth level");
        var settings = DungeonSettings.parse(data);
        var room = room(settings, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f));

        assertEquals(1f, might(room.get(1)), 0.0001f, "at its first level");
        Spawner.scale(room.get(0), 6, settings);
        assertEquals(1.5f, might(room.get(1)), 0.0001f, "at its sixth");
    }

    // ---- the file ----

    /** An aura needs a Radius, and a might a BoostPercent of at least 1: without either, the file is refused. */
    @Test
    void aMightWithoutItsRadiusOrItsWorthIsRefused() {
        var block = ShippedBlock.of(SUMMONER).text();
        for (var wrong : new String[][] {{"      Radius = 60\n", "", "Radius"},
                {"      BoostPercent = 50\n", "      BoostPercent = 0\n", "BoostPercent"}}) {
            var text = block.replace(wrong[0], wrong[1]);
            assertNotEquals(block, text, "the premise: " + wrong[2] + " was changed");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(text), wrong[2]);
            assertTrue(refused.getMessage().contains(wrong[2]), refused.getMessage());
        }
    }

    /**
     * As shipped: the summoner's E, written after its haste -- half as hard again for everyone of its own within 60 of
     * it, never cast -- and its ring drawn on him alone, as a lifesteal's is.
     */
    @Test
    void asShippedTheSummonersMight() {
        var skills = SETTINGS.skillsFor(SUMMONER);
        var might = skills.get(2);

        assertEquals(List.of(SkillEffect.SUMMON, SkillEffect.HASTE, SkillEffect.DAMAGE_AURA),
                skills.stream().map(Skill::effect).toList());
        assertEquals('E', might.key());
        assertTrue(might.effect().isPassive() && might.effect().isAura());
        assertEquals(50, might.boostPercent());
        assertEquals(60f, might.radius(), 0.001f);
        assertEquals(1, might.maxRank());
        assertEquals("Qudrat", might.name());
        assertEquals(SkillRange.Shape.ON_HIMSELF, Main.rangeOf(might, 5f).shape());
    }
}
