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
import uz.dukeengine.dungeon.level.GrowableBody;
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
    private static final String HEALER = "SkeletonHealer";
    private static final String REVENANT = "Revenant";
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

    // ---- mana ----

    /** Its pool at the first level, with nothing in it: what comes back over the next frames is only the trickle. */
    private static SkillBook emptied(GameObject caster) {
        Spawner.scale(caster, 1, SETTINGS);
        var book = bookOf(caster);
        int most = book.getMaxMana();
        int tenths = book.getManaRegen();
        book.resize(0, 0);
        book.resize(most, tenths);
        return book;
    }

    /** What an emptied pool of each of {@code refilling} holds two seconds on: the trickle, in whole points. */
    private static List<Integer> twoSecondsOf(Room room, int... refilling) {
        var books = java.util.Arrays.stream(refilling).mapToObj(at -> emptied(room.get(at))).toList();
        room.game().runHeadless(60);
        return books.stream().map(SkillBook::getMana).toList();
    }

    /** A healer alone refills seven and a half a second: its own two and a half, and its aura's five. */
    @Test
    void aHealerAloneRefillsSevenAndAHalfASecond() {
        var alone = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f));

        assertEquals(List.of(15), twoSecondsOf(alone, 0));
    }

    /**
     * A fire mage beside it refills eight a second, its own three and the aura's five; out of its reach, three; and
     * with no trickle of its own, beside it, the aura's five.
     */
    @Test
    void aFireMageBesideItRefillsEightAndOutOfItsReachThree() {
        var beside = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one(MAGE, 160f, 150f));
        assertEquals(List.of(16), twoSecondsOf(beside, 1), "60 from it");

        var apart = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one(MAGE, 161f, 150f));
        assertEquals(List.of(6), twoSecondsOf(apart, 1), "61 from it");

        var dry = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one(MAGE, 130f, 150f));
        var book = emptied(dry.get(1));
        book.resize(book.getMaxMana(), 0);
        dry.game().runHeadless(60);
        assertEquals(10, book.getMana(), "no trickle of its own, 30 from it");
    }

    /** Two healers, and still five more, not ten: the strongest of a kind, never the sum -- each of them as much. */
    @Test
    void twoHealersStillFiveMore() {
        var two = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one(HEALER, 160f, 150f), one(MAGE, 130f, 150f));

        assertEquals(List.of(15, 15, 16), twoSecondsOf(two, 0, 1, 2));
    }

    /** The aura fills pools, it makes none: a Skeleton beside a healer has no pool, and is given none. */
    @Test
    void aSkeletonBesideAHealerIsGivenNoPool() {
        var room = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one("Skeleton", 130f, 150f));
        room.game().runHeadless(60);

        assertEquals(List.of(0, 0), List.of(bookOf(room.get(1)).getMaxMana(), bookOf(room.get(1)).getMana()));
    }

    // ---- lifesteal ----

    /** The shipped units with the Rogue's bow reaching nothing and the Revenant mending nothing on its own. */
    private static String drinkingUnits() {
        var revenant = ShippedBlock.of(REVENANT);
        return unarmedRogue().replace(revenant.text(), revenant.with("HealPerSecond", 0).text());
    }

    /** What the Rogue lost and what {@code striker}, left at half, gained over {@code frames} of a fight. */
    private static float[] lostAndGained(DukeGame game, GameObject hero, GameObject striker, int frames) {
        striker.getBody().setHealth(striker.getBody().getMaxHealth() / 2f);
        float his = hero.getBody().getHealth();
        float its = striker.getBody().getHealth();
        game.runHeadless(frames);
        return new float[] {his - hero.getBody().getHealth(), striker.getBody().getHealth() - its};
    }

    /**
     * A fight: a Rogue who cannot answer at (150, 150), {@code striker} at {@code (x, 150)} and a Revenant noticing
     * nobody 50 from it -- or none, for {@code REVENANT} itself.
     */
    private static Room fight(String striker, float x) {
        var arena = Dungeon.world(room(NO_WALL), REVENANT.equals(striker) ? SETTINGS : unseeing(REVENANT),
                drinkingUnits());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(striker, arena.dungeon(), x, 150f);
        if (!REVENANT.equals(striker)) {
            game.spawn(REVENANT, arena.dungeon(), x, 100f);
        }
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        return new Room(game, List.of(first(game, "Rogue"), first(game, striker)));
    }

    /** A Skeleton beside a Revenant striking for 7 gets back 0.7, and a tenth of every blow after. */
    @Test
    void aSkeletonBesideARevenantGetsBackATenth() {
        var fight = fight("Skeleton", 162f);
        var skeleton = fight.get(1);
        skeleton.getBody().setHealth(30f);

        assertEquals(7f, untilStruck(fight.game(), fight.get(0), 150), 0.001f, "the premise: its blow");
        assertEquals(30.7f, skeleton.getBody().getHealth(), 0.001f, "0.7 back");
        var change = lostAndGained(fight.game(), fight.get(0), skeleton, 90);
        assertTrue(change[0] > 0f, "the premise: it went on striking");
        assertEquals(change[0] / 10f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** A Stalker's bolt, where it arrives: a tenth of it. */
    @Test
    void aStalkersBoltItsTenth() {
        var fight = fight("Stalker", 190f);
        var change = lostAndGained(fight.game(), fight.get(0), fight.get(1), 150);

        assertTrue(change[0] > 0f, "the premise: its bolts reached him");
        assertEquals(change[0] / 10f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** The Revenant is among those its aura reaches: its own fire, a tenth -- its own mending stilled. */
    @Test
    void theRevenantDrinksFromItsOwnFire() {
        var fight = fight(REVENANT, 190f);
        var change = lostAndGained(fight.game(), fight.get(0), fight.get(1), 150);

        assertTrue(change[0] > 0f, "the premise: its fire reached him");
        assertEquals(change[0] / 10f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** What {@code creature}, left at half, gains from a blow of 20 told to it by hand. */
    private static float aBlowOfTwentyTo(GameObject creature) {
        creature.getBody().setHealth(creature.getBody().getMaxHealth() / 2f);
        float before = creature.getBody().getHealth();
        SkillBook.drink(creature, 20f);
        return creature.getBody().getHealth() - before;
    }

    /** A creature's own lifesteal and the aura add: a Warden beside a Revenant drinks its quarter and the tenth. */
    @Test
    void aBossBesideARevenantDrinksItsOwnAndTheAura() {
        var room = room(SETTINGS, NO_WALL, one(REVENANT, 100f, 150f), one("Warden", 140f, 150f));

        assertEquals(7f, aBlowOfTwentyTo(room.get(1)), 0.001f, "35% of 20");
    }

    /** Two Revenants, and still a tenth; out of reach, nothing. */
    @Test
    void twoRevenantsStillATenthAndOutOfReachNothing() {
        var two = room(SETTINGS, NO_WALL, one(REVENANT, 100f, 150f), one(REVENANT, 160f, 150f),
                one("Skeleton", 130f, 150f));
        assertEquals(2f, aBlowOfTwentyTo(two.get(2)), 0.001f, "two Revenants");

        var apart = room(SETTINGS, NO_WALL, one(REVENANT, 100f, 150f), one("Skeleton", 161f, 150f));
        assertEquals(0f, aBlowOfTwentyTo(apart.get(1)), 0.001f, "61 from it");
    }

    /**
     * The meteor's blast drinks, for each it hurts: a level-6 fire mage beside a Revenant, its meteor cast by hand at a
     * Rogue who cannot answer, gets back a tenth of the 112.5 that landed on him. It is given room to drink into by its
     * ceiling raised, its health left where it was: lowered, its brain would take it for a wound and answer it.
     */
    @Test
    void theMeteorsBlastDrinks() {
        var arena = Dungeon.world(room(NO_WALL), unseeing(MAGE, REVENANT), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(MAGE, arena.dungeon(), 200f, 150f);
        game.spawn(REVENANT, arena.dungeon(), 200f, 100f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = first(game, "Rogue");
        var mage = first(game, MAGE);
        Spawner.scale(mage, 6, SETTINGS);
        ((GrowableBody) mage.getBody()).setMaxHealth(mage.getBody().getMaxHealth() * 2f);
        float his = hero.getBody().getHealth();
        float its = mage.getBody().getHealth();
        var meteor = SETTINGS.skillsFor(MAGE).getFirst();

        assertTrue(bookOf(mage).cast('R', 1, null, hero.getPosition()), "the premise: it called the meteor down");
        game.runHeadless(meteor.windUpFrames() + 5);

        assertEquals(112.5f, his - hero.getBody().getHealth(), 0.01f, "the premise: what landed on him");
        assertEquals(11.25f, mage.getBody().getHealth() - its, 0.01f, "a tenth of it back");
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
     * A mana aura needs a ManaRegen of at least 1; and a ManaRegen on any other skill is read by nothing, and refused,
     * as StunFrames off a skillshot is -- each saying so.
     */
    @Test
    void aManaRegenOnlyAManaAuraReadsIsRefusedElsewhere() {
        var healer = ShippedBlock.of(HEALER).text();
        var none = healer.replace("      ManaRegen = 50\n", "      ManaRegen = 0\n");
        assertNotEquals(healer, none, "the premise: the healer's aura was given no ManaRegen");
        var summoner = ShippedBlock.of(SUMMONER).text();
        var misplaced = summoner.replace("      Effect = HASTE\n", "      Effect = HASTE\n      ManaRegen = 50\n");
        assertNotEquals(summoner, misplaced, "the premise: the summoner's haste was given a ManaRegen");

        for (var text : new String[] {none, misplaced}) {
            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(text));
            assertTrue(refused.getMessage().contains("ManaRegen"), refused.getMessage());
        }
    }

    /** A lifesteal aura's BoostPercent is a share of every blow, from 1 to 100, as a lifesteal's is: else refused. */
    @Test
    void aLifestealAurasShareOutOfRangeIsRefused() {
        for (int share : new int[] {0, 101}) {
            var refused = assertThrows(IllegalArgumentException.class,
                    () -> DungeonSettings.parse(ShippedBlock.dataWith(REVENANT, "BoostPercent", share)));
            assertTrue(refused.getMessage().contains("BoostPercent"), refused.getMessage());
        }
    }

    /**
     * As shipped: the Revenant's Q, the one skill it has -- a tenth of every blow back to everyone of its own within 60
     * of it, never cast -- carried by the SkillBook every monster has.
     */
    @Test
    void asShippedTheRevenantsDrink() {
        var skills = SETTINGS.skillsFor(REVENANT);
        var drink = skills.getFirst();

        assertEquals(List.of(SkillEffect.LIFESTEAL_AURA), skills.stream().map(Skill::effect).toList());
        assertEquals('Q', drink.key());
        assertTrue(drink.effect().isPassive() && drink.effect().isAura());
        assertEquals(10, drink.boostPercent());
        assertEquals(60f, drink.radius(), 0.001f);
        assertEquals(1, drink.maxRank());
        assertEquals("Qon aurasi", drink.name());
    }

    /** Two worlds fought through the same frames, the three mages and their own in them, read the same checksum. */
    @Test
    void twoWorldsFoughtAlikeReadTheSameChecksum() {
        assertEquals(checksums(), checksums());
    }

    private static String checksums() {
        var arena = Dungeon.world(room(NO_WALL), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        for (var one : new One[] {one(SUMMONER, 210f, 130f), one(HEALER, 210f, 170f), one(REVENANT, 200f, 150f),
                one(MAGE, 220f, 150f), one("Skeleton", 175f, 140f), one("Brute", 175f, 160f)}) {
            game.spawn(one.kind(), arena.dungeon(), one.x(), one.y());
        }
        var line = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            game.runHeadless(30);
            line.append(game.getLogic().checksum()).append('|');
        }
        return line.toString();
    }

    /**
     * As shipped: the healer's W, written after its mending -- five points a second more to every pool of its own
     * within 60 of it, never cast.
     */
    @Test
    void asShippedTheHealersMana() {
        var skills = SETTINGS.skillsFor(HEALER);
        var mana = skills.get(1);

        assertEquals(List.of(SkillEffect.HEAL, SkillEffect.MANA_AURA), skills.stream().map(Skill::effect).toList());
        assertEquals('W', mana.key());
        assertTrue(mana.effect().isPassive() && mana.effect().isAura());
        assertEquals(50, mana.manaRegen(), "in tenths of a point a second");
        assertEquals(60f, mana.radius(), 0.001f);
        assertEquals(1, mana.maxRank());
        assertEquals("Sehr buloqi", mana.name());
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
