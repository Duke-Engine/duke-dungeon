package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.EffectLayer;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
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

/**
 * The mages' auras: what each lends everyone of its own round it -- the living of its side that carry a book, within
 * its Radius, middle to middle, and in its plain sight, itself among them -- asked by the one it lends to at the moment
 * the figure is used.
 *
 * <p>Most of it is asked of creatures standing in a room with nobody to fight, so nothing moves the figure read; a blow
 * is fought for real, against a Rogue who cannot answer, where the figure is the blow itself.
 *
 * <p>And what is drawn of it: each bearer's look, and the circle under every creature an aura reaches -- laid by that
 * creature's own book from {@code SkillBook.aurasOn}, which asks the same figures -- and what the HUD is handed of
 * the creature the player picks, from the same rule.
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
        return room(settings, units, wallColumn, game -> { }, ones);
    }

    /** The same, {@code before} told of the world before its first frame -- as the HUD's row is, by the bag. */
    private static Room room(DungeonSettings settings, String units, int wallColumn,
            java.util.function.Consumer<DukeGame> before, One... ones) {
        var arena = Dungeon.world(room(wallColumn), settings, units);
        var game = arena.game();
        before.accept(game);
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

    /** This block with its creature noticing nobody: it does only what a test does for it. */
    private static String unseen(ShippedBlock block) {
        return block.with("SenseRadius", 1).with("ChaseRadius", 1).with("AlertRadius", 0).text();
    }

    /** The shipped files with these kinds noticing nobody: they do only what a test does for them. */
    private static DungeonSettings unseeing(String... kinds) {
        var text = new StringBuilder();
        for (var kind : kinds) {
            text.append(unseen(ShippedBlock.of(kind)));
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

    /** What the healer's mending gives a Skeleton left at ten, called down by hand -- beside a summoner, or alone. */
    private static float aMendingFrom(boolean summonerBeside) {
        var room = summonerBeside
                ? room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one("Skeleton", 130f, 150f),
                        one(SUMMONER, 100f, 110f))
                : room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one("Skeleton", 130f, 150f));
        var patient = room.get(1);
        patient.getBody().setHealth(10f);
        var mending = SETTINGS.skillsFor(HEALER).getFirst();

        assertEquals(summonerBeside ? 1.5f : 1f, might(room.get(0)), 0.0001f, "the premise: the might on the healer");
        assertTrue(bookOf(room.get(0)).cast(mending.key(), 1, patient.getId(), null), "the premise: it mended");
        room.game().runHeadless(mending.windUpFrames() + 5);
        return patient.getBody().getHealth() - 10f;
    }

    /** A mending is not damage and the might does not raise it: beside a summoner it heals what it heals alone. */
    @Test
    void aMendingIsNotRaisedByTheMight() {
        float alone = aMendingFrom(false);

        assertEquals(30f, alone, 0.001f, "the premise: its Heal, alone");
        assertEquals(alone, aMendingFrom(true), 0.001f, "beside a summoner");
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
        var books = Arrays.stream(refilling).mapToObj(at -> emptied(room.get(at))).toList();
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

    /**
     * The aura fills pools, it makes none: a Skeleton beside a healer -- its kind names no pool, and the spawner gave
     * it none -- is reached by the aura, and is still given none.
     */
    @Test
    void aSkeletonBesideAHealerIsGivenNoPool() {
        var room = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one("Skeleton", 130f, 150f));
        Spawner.scale(room.get(1), 1, SETTINGS);
        assertEquals(50, SkillBook.auraOn(room.get(1), SkillEffect.MANA_AURA), "the premise: the aura reaches it");
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

    /** Two Rogues of the hero's side, one beside the other at (150, 150), held where they stand. */
    private static List<GameObject> twoRogues(Dungeon.Arena arena) {
        arena.game().spawn("Rogue", arena.hero(), 150f, 150f);
        arena.game().spawn("Rogue", arena.hero(), 150f, 160f);
        arena.game().runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        return arena.game().getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Rogue")).toList();
    }

    /** The health the two of them have between them. */
    private static float together(List<GameObject> two) {
        return two.get(0).getBody().getHealth() + two.get(1).getBody().getHealth();
    }

    /**
     * The meteor's blast drinks, for each it hurts: a level-6 fire mage beside a Revenant, its meteor cast by hand
     * at two Rogues standing in one blast, gets back a tenth of the 112.5 that landed on each. It is given room to
     * drink into by its ceiling raised, its health left where it was: lowered, its brain would take it for a wound
     * and answer it.
     */
    @Test
    void theMeteorsBlastDrinksFromEachItHurts() {
        var arena = Dungeon.world(room(NO_WALL), unseeing(MAGE, REVENANT), unarmedRogue());
        arena.game().spawn(MAGE, arena.dungeon(), 200f, 150f);
        arena.game().spawn(REVENANT, arena.dungeon(), 200f, 100f);
        var heroes = twoRogues(arena);
        var mage = first(arena.game(), MAGE);
        Spawner.scale(mage, 6, SETTINGS);
        ((GrowableBody) mage.getBody()).setMaxHealth(mage.getBody().getMaxHealth() * 2f);
        float his = together(heroes);
        float its = mage.getBody().getHealth();
        var meteor = SETTINGS.skillsFor(MAGE).getFirst();

        assertTrue(bookOf(mage).cast(meteor.key(), 1, null, heroes.get(0).getPosition()),
                "the premise: it called the meteor down");
        arena.game().runHeadless(meteor.windUpFrames() + 5);

        assertEquals(2 * 112.5f, his - together(heroes), 0.01f, "the premise: what landed on the two of them");
        assertEquals(2 * 11.25f, mage.getBody().getHealth() - its, 0.01f, "a tenth of each back");
    }

    /** A damaging skill the book lands for itself: its effect, the figures that make it reach, the Rogues it hurts. */
    private record Blow(String effect, String figures, int hurts) {
    }

    /**
     * A Skeleton given a LIFESTEAL of a quarter and this skill, with a Revenant 30 from it and both noticing nobody,
     * casts the skill by hand at two Rogues who cannot answer, 40 away: what they lost and what it gained.
     */
    private static float[] aSkillsBlow(Blow blow) {
        var skeleton = ShippedBlock.of("Skeleton").text();
        var skills = "  SkillDistance = [0, 60]\n  Skills = [\n"
                + "    Skill\n      Key = Q\n      Effect = " + blow.effect() + "\n      Damage = 20\n"
                + blow.figures() + "      MaxRank = 1\n    End,\n"
                + "    Skill\n      Key = W\n      Effect = LIFESTEAL\n      BoostPercent = 25\n      MaxRank = 1\n"
                + "    End\n  ]\n";
        var armed = skeleton.replace("\nEnd\n", "\n" + skills + "End\n");
        assertNotEquals(skeleton, armed, "the premise: the Skeleton was given its skills");
        var settings = DungeonSettings.parse(unseen(new ShippedBlock(armed)) + unseen(ShippedBlock.of(REVENANT)));
        var arena = Dungeon.world(room(NO_WALL), settings, drinkingUnits());
        arena.game().spawn("Skeleton", arena.dungeon(), 190f, 150f);
        arena.game().spawn(REVENANT, arena.dungeon(), 190f, 120f);
        var heroes = twoRogues(arena);
        var striker = first(arena.game(), "Skeleton");
        striker.getBody().setHealth(striker.getBody().getMaxHealth() / 2f);
        float his = together(heroes);
        float its = striker.getBody().getHealth();

        assertTrue(bookOf(striker).cast('Q', 1, null, heroes.get(0).getPosition()),
                "the premise: it cast its " + blow.effect());
        return new float[] {his - together(heroes), striker.getBody().getHealth() - its};
    }

    /**
     * A skill's own blows drink where they land, as a weapon's do, for each they hurt: an area blow, a blast at a spot,
     * a strike with no shot and a charge, each cast by a Skeleton given a LIFESTEAL beside a Revenant, give it back the
     * quarter and the tenth of every 20 that landed.
     */
    @Test
    void aSkillsOwnBlowsDrinkForEachTheyHurt() {
        for (var blow : List.of(new Blow("AREA_DAMAGE", "      Radius = 50\n", 2),
                new Blow("AREA_AT_SPOT", "      Radius = 15\n      Range = 60\n", 2),
                new Blow("STRIKE", "      Range = 60\n", 1),
                new Blow("DASH", "      Distance = 40\n      Radius = 30\n      Range = 60\n", 2))) {
            var change = aSkillsBlow(blow);

            assertEquals(20f * blow.hurts(), change[0], 0.001f, "the premise: what its " + blow.effect() + " took");
            assertEquals(change[0] * 35f / 100f, change[1], 0.001f,
                    blow.effect() + " took " + change[0] + " and got back " + change[1]);
        }
    }

    // ---- worn ----

    /** One aura's look played: which, on whom, on what frame. */
    private record Worn(String look, ObjectId on, int frame) {
    }

    /**
     * Every one of {@code looks} played over the next {@code frames}, in the order played -- watched through nobody's
     * fog, since a room with no hero in it is a room nobody sees.
     */
    private static List<Worn> played(DukeGame game, int frames, List<String> looks) {
        game.watch();
        var worn = new ArrayList<Worn>();
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var event : game.getSnapshot().events()) {
                if (event instanceof EffectPlayed played && looks.contains(played.name())) {
                    worn.add(new Worn(played.name(), played.riding(), played.frame()));
                }
            }
        }
        return worn;
    }

    /** Every aura's look played over the next {@code frames}, in the order played. */
    private static List<Worn> worn(DukeGame game, int frames) {
        return played(game, frames,
                SETTINGS.skills().stream().filter(skill -> skill.effect().isAura()).map(Skill::look).toList());
    }

    /** Every mark under a creature an aura reaches played over the next {@code frames}, in the order played. */
    private static List<Worn> marked(DukeGame game, int frames) {
        return played(game, frames, SETTINGS.combat().auraMarkLooks());
    }

    /** The looks among these worn by {@code creature}, in the order they were played on it. */
    private static List<String> on(List<Worn> worn, GameObject creature) {
        return worn.stream().filter(one -> creature.getId().equals(one.on())).map(Worn::look).toList();
    }

    /** Its aura, as shipped. */
    private static Skill auraOf(GameObject bearer) {
        return SETTINGS.skillsFor(bearer.getTemplate().name()).stream().filter(skill -> skill.effect().isAura())
                .findFirst().orElseThrow();
    }

    /**
     * Each bearer wears its aura's look every TickFrames, on the frames its object id falls on, as a brain's re-plans
     * are: a summoner its might, a healer its mana, a Revenant its drink -- and nothing on those they reach.
     */
    @Test
    void eachBearerWearsItsLookEveryTickOnTheFramesItsIdFallsOn() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 60f, 150f), one(HEALER, 180f, 150f),
                one(REVENANT, 300f, 150f), one("Skeleton", 90f, 150f));
        var worn = worn(room.game(), 90);

        assertEquals(List.of("MightAura", "ManaAura", "BloodAura"),
                room.ones().subList(0, 3).stream().map(bearer -> auraOf(bearer).look()).toList());
        for (var bearer : room.ones().subList(0, 3)) {
            var aura = auraOf(bearer);
            var its = worn.stream().filter(one -> bearer.getId().equals(one.on())).toList();
            assertEquals(List.of(aura.look(), aura.look(), aura.look()), its.stream().map(Worn::look).toList(),
                    "three beats in three seconds: " + its);
            for (var one : its) {
                assertEquals(Math.floorMod(bearer.getId().value(), aura.tickFrames()),
                        one.frame() % aura.tickFrames(), "on the frames its id falls on: " + its);
            }
        }
        assertEquals(9, worn.size(), "and on nobody else: " + worn);
    }

    /** A bearer fallen wears it no more; and an aura is worn from the level that opens it, never before. */
    @Test
    void aFallenOrUnopenedAuraIsNotWorn() {
        var fallen = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        assertFalse(worn(fallen.game(), 30).isEmpty(), "the premise: standing, it wears its might");
        var body = fallen.get(0).getBody();
        body.damage(body.getHealth() + 1f);
        assertEquals(List.of(), worn(fallen.game(), 60), "fallen");

        var data = Content.data().replace("      Effect = DAMAGE_AURA\n",
                "      Effect = DAMAGE_AURA\n      LevelPerRank = 6\n");
        assertNotEquals(Content.data(), data, "the premise: the summoner's might waits for its sixth level");
        var settings = DungeonSettings.parse(data);
        var waiting = room(settings, NO_WALL, one(SUMMONER, 100f, 150f));
        assertEquals(List.of(), worn(waiting.game(), 60), "at its first level");
        Spawner.scale(waiting.get(0), 6, settings);
        assertFalse(worn(waiting.game(), 30).isEmpty(), "at its sixth");
    }

    /**
     * Each look is measured two of its bearer's ticks, as wide as its Radius, and renews as a state does. Its ring lies
     * on the floor and follows its bearer, fading in over its first half and out over its second -- so the one laid a
     * tick later takes over as it goes, and the two stand as one steady ring. Its rider is fed by a Rate on the body,
     * and each tick carries it on to the next, as a stun's stars are -- with room for every one it lets out to live out
     * its life, so none is overwritten while it still shows.
     */
    @Test
    void eachLookIsMeasuredTwoTicksItsRingAtItsRadiusAndItsRiderRenewed() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        var auras = SETTINGS.skills().stream().filter(skill -> skill.effect().isAura()).toList();
        assertEquals(3, auras.size(), "the premise: three auras");

        for (var aura : auras) {
            assertEquals(2f * aura.tickFrames() / GameConstants.LOGICFRAMES_PER_SECOND,
                    visuals.getEffectSeconds(aura.look()), 0.001f, aura.look() + ": two ticks");
            assertEquals(aura.radius(), visuals.getEffectReach(aura.look()), 0.001f, aura.look() + ": its Radius");
            var layers = SETTINGS.effectLayers().stream().filter(art -> art.effect().equals(aura.look()))
                    .map(art -> Main.layerOf(art, SETTINGS)).toList();
            assertEquals(List.of(EffectLayer.MARK, EffectLayer.AURA), layers.stream().map(EffectLayer::type).toList(),
                    aura.look() + " is a ring on the floor, and under what rides over it, a rider");

            var ring = layers.get(0);
            assertTrue(ring.follows(), aura.look() + " follows its bearer");
            assertEquals(EffectLayer.REACH, ring.measure(), aura.look() + " is measured in its reach");
            assertEquals(0.5f, ring.fadeIn(), 0.001f, aura.look());
            assertEquals(0.5f, ring.fadeOut(), 0.001f, aura.look());
            assertTrue(ring.renews(), aura.look() + " renews, worn as a state");

            var rider = layers.get(1);
            assertTrue(rider.renews(), aura.look() + "'s rider is carried on by each tick, worn as a state");
            assertTrue(rider.continuous(), aura.look() + "'s rider is fed by a Rate");
            assertEquals(EffectLayer.UNITS, rider.measure(),
                    aura.look() + "'s rider is a few units at the body: the ring carries the reach");
            assertTrue(rider.count() >= Math.ceil(rider.rate() * rider.lifeMax()), aura.look() + "'s rider lets out "
                    + rider.rate() + " a second living " + rider.lifeMax() + " s, in room for " + rider.count());
        }
    }

    /** What each kind of aura is drawn in, whoever bears it: red, blue and violet as hues, in degrees from-to. */
    private static final Map<SkillEffect, float[]> HUES = Map.of(
            SkillEffect.LIFESTEAL_AURA, new float[] {340f, 20f},
            SkillEffect.MANA_AURA, new float[] {200f, 240f},
            SkillEffect.DAMAGE_AURA, new float[] {255f, 295f});

    /**
     * Each aura is drawn in the colour of its kind, not of its bearer: the thirst red, the mana blue, the might violet
     * -- its ring and what rides its body alike, from the colour each starts in to the one it ends in -- so one is told
     * from another at a glance, as Dota's are. A hue is red when it lies within 20 degrees of red, blue between 200 and
     * 240, violet between 255 and 295, and coloured when it is at least a quarter saturated: white has no hue to lead.
     */
    @Test
    void eachAurasLookIsInTheColourOfItsKind() {
        var kinds = new java.util.HashSet<SkillEffect>();
        for (var aura : SETTINGS.skills().stream().filter(skill -> skill.effect().isAura()).toList()) {
            var window = HUES.get(aura.effect());
            assertNotNull(window, aura.effect() + " is a kind of aura with no colour of its own");
            var layers = SETTINGS.effectLayers().stream().filter(art -> art.effect().equals(aura.look()))
                    .map(art -> Main.layerOf(art, SETTINGS)).toList();
            assertFalse(layers.isEmpty(), aura.look() + " is drawn in layers");
            for (var layer : layers) {
                for (int colour : new int[] {layer.colourStart(), layer.colourEnd()}) {
                    var hsb = java.awt.Color.RGBtoHSB(colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF, null);
                    float hue = hsb[0] * 360f;
                    boolean led = window[0] <= window[1] ? hue >= window[0] && hue <= window[1]
                            : hue >= window[0] || hue <= window[1];
                    assertTrue(led && hsb[1] >= 0.25f, aura.effect() + "'s " + aura.look() + " is drawn in 0x"
                            + Integer.toHexString(colour) + ": hue " + Math.round(hue) + ", saturation "
                            + Math.round(hsb[1] * 100) + "% -- not hue " + window[0] + " to " + window[1]
                            + " and coloured");
                }
            }
            kinds.add(aura.effect());
        }
        assertEquals(HUES.keySet(), kinds, "an aura of each kind");
    }

    // ---- marked ----

    /** Where the bearers stand round the Skeleton at (130, 150): each within 40 of it, and in plain sight of it. */
    private static final float[][] ROUND_IT = {{100f, 150f}, {100f, 130f}, {100f, 170f}};

    /** These bearers of its side, in this order, standing round a Skeleton at (130, 150): the last of the room. */
    private static Room beside(DungeonSettings settings, String... bearers) {
        return beside(settings, game -> { }, bearers);
    }

    /** The same, {@code before} told of the world before its first frame. */
    private static Room beside(DungeonSettings settings, java.util.function.Consumer<DukeGame> before,
            String... bearers) {
        var ones = new ArrayList<One>();
        for (int at = 0; at < bearers.length; at++) {
            ones.add(one(bearers[at], ROUND_IT[at][0], ROUND_IT[at][1]));
        }
        ones.add(one("Skeleton", 130f, 150f));
        return room(settings, Content.units(), NO_WALL, before, ones.toArray(One[]::new));
    }

    /** The last of a room's creatures: the Skeleton {@link #beside} puts last. */
    private static GameObject lastOf(Room room) {
        return room.get(room.ones().size() - 1);
    }

    /** The shipped files whole, with the Combat block's {@code AuraMarkLooks} naming just these. */
    private static DungeonSettings marksNamed(List<String> looks) {
        var data = Content.data();
        var changed = data.replaceFirst("(?m)^ *AuraMarkLooks = .*$",
                "  AuraMarkLooks = [" + String.join(", ", looks) + "]");
        assertNotEquals(data, changed, "the premise: the Combat block names its marks");
        return DungeonSettings.parse(changed);
    }

    /** The shipped files whole, with these kinds noticing nobody: their Combat block stays, so the marks are drawn. */
    private static DungeonSettings unseeingInWhole(String... kinds) {
        var data = Content.data();
        for (var kind : kinds) {
            var block = ShippedBlock.of(kind);
            data = data.replace(block.text(), unseen(block));
        }
        return DungeonSettings.parse(data);
    }

    /**
     * Every creature an aura reaches wears a light-blue circle underfoot, laid by its own book every
     * {@code AuraMarkTickFrames} on the frames its object id falls on, as the rings are staggered: a Skeleton beside a
     * summoner wears the first mark, and the summoner itself -- among those its aura reaches -- wears one too.
     */
    @Test
    void aSkeletonBesideASummonerWearsTheFirstMarkAtItsBeat() {
        var room = beside(SETTINGS, SUMMONER);
        var marks = marked(room.game(), 90);
        int beat = SETTINGS.combat().auraMarkTickFrames();
        var first = SETTINGS.combat().auraMarkLooks().getFirst();

        for (var creature : room.ones()) {
            var its = marks.stream().filter(one -> creature.getId().equals(one.on())).toList();
            assertEquals(List.of(first, first, first), its.stream().map(Worn::look).toList(),
                    creature.getTemplate().name() + " wears the first mark once a beat, three in three seconds: "
                            + its);
            for (var one : its) {
                assertEquals(Math.floorMod(creature.getId().value(), beat), one.frame() % beat,
                        "on the frames its id falls on: " + its);
            }
        }
        assertEquals(6, marks.size(), "and on nobody else: " + marks);
    }

    /**
     * What it wears is chosen by how many kinds of aura reach it: beside a summoner the first mark, beside a healer
     * too the second -- the Skeleton given a pool, for a mana aura changes nothing on a creature with none -- and
     * beside a Revenant as well the third.
     */
    @Test
    void itWearsTheMarkOfHowManyKindsReachIt() {
        var bearers = new String[] {SUMMONER, HEALER, REVENANT};
        for (int kinds = 1; kinds <= 3; kinds++) {
            var room = beside(SETTINGS, Arrays.copyOf(bearers, kinds));
            bookOf(lastOf(room)).resize(50, 0);

            assertEquals(kinds, SkillBook.aurasOn(lastOf(room)).size(), "the premise: " + kinds + " reach it");
            var wanted = SETTINGS.combat().auraMarkLooks().get(kinds - 1);
            assertEquals(List.of(wanted, wanted), on(marked(room.game(), 60), lastOf(room)),
                    kinds + " kinds reach it: the mark in that place of the list, once a beat");
        }
    }

    /** Counted by kind, not by bearer: two summoners lend one might, so what stands beside them wears the first. */
    @Test
    void twoSummonersMakeOneKindAndTheFirstMark() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one(SUMMONER, 160f, 150f),
                one("Skeleton", 130f, 150f));
        var first = SETTINGS.combat().auraMarkLooks().getFirst();

        assertEquals(List.of(SkillEffect.DAMAGE_AURA), SkillBook.aurasOn(room.get(2)),
                "the premise: one kind reaches it, from two bearers");
        assertEquals(List.of(first, first), on(marked(room.game(), 60), room.get(2)));
    }

    /**
     * Where no aura reaches there is no mark: a Skeleton alone, one behind stone, one a step past the Radius, and one
     * with no pool beside a healer -- whose mana is lent to pools and changes nothing on a creature without.
     */
    @Test
    void whereNoAuraReachesItWearsNothing() {
        var alone = room(SETTINGS, NO_WALL, one("Skeleton", 130f, 150f));
        var walled = room(SETTINGS, 13, one(SUMMONER, 100f, 150f), one("Skeleton", 150f, 150f));
        var apart = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 161f, 150f));
        var poolless = beside(SETTINGS, HEALER);
        assertEquals(50, SkillBook.auraOn(lastOf(poolless), SkillEffect.MANA_AURA), "the premise: the aura reaches it");
        assertEquals(0, bookOf(lastOf(poolless)).getMaxMana(), "the premise: and it has no pool");

        var cases = Map.of("alone", alone, "behind stone", walled, "61 from it", apart,
                "with no pool beside a healer", poolless);
        for (var one : cases.entrySet()) {
            var worn = marked(one.getValue().game(), 90);
            assertEquals(List.of(), on(worn, lastOf(one.getValue())), "a Skeleton " + one.getKey());
        }
    }

    /** A hero's side is never reached -- no aura reaches a hero -- so a hero beside a summoner never wears one. */
    @Test
    void aHeroBesideASummonerNeverWearsOne() {
        var arena = Dungeon.world(room(NO_WALL), unseeingInWhole(SUMMONER), unarmedRogue());
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), 100f, 150f);
        game.spawn("Rogue", arena.hero(), 115f, 150f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = first(game, "Rogue");

        var marks = marked(game, 90);
        assertEquals(3, on(marks, first(game, SUMMONER)).size(), "the premise: the summoner wears its own");
        assertEquals(List.of(), on(marks, hero), "a hero, 15 from it");
        assertEquals(List.of(), SkillBook.aurasOn(hero));
    }

    /**
     * Runs the game on until the next frame it runs is the one {@code creature} lays its next mark on. What is done to
     * the room then is done a frame before the beat, so a beat that took no notice of it would lay its mark at once --
     * where a body reaped meanwhile would have hidden the fault.
     */
    private static void toTheFrameBefore(Room room, GameObject creature) {
        int beat = SETTINGS.combat().auraMarkTickFrames();
        int frame = room.game().getLogic().getFrame();
        int wait = Math.floorMod(Math.floorMod(creature.getId().value(), beat) - frame, beat);
        if (wait > 0) {
            room.game().runHeadless(wait);
        }
        assertEquals(Math.floorMod(creature.getId().value(), beat), room.game().getLogic().getFrame() % beat,
                "the premise: the next frame is its beat");
    }

    /** A creature killed wears none from then, its body still lying there: not the next beat's, nor those after. */
    @Test
    void aCreatureKilledWearsNoMarkFromThen() {
        var room = beside(SETTINGS, SUMMONER);
        var skeleton = lastOf(room);
        assertFalse(on(marked(room.game(), 30), skeleton).isEmpty(), "the premise: standing, it wears the mark");

        toTheFrameBefore(room, skeleton);
        var body = skeleton.getBody();
        body.damage(body.getHealth() + 1f);

        assertTrue(skeleton.isEffectivelyDead(), "the premise: it is dead");
        var after = marked(room.game(), 90);
        assertEquals(List.of(), on(after, skeleton), "killed on the frame before its beat");
        assertEquals(3, on(after, room.get(0)).size(), "and the summoner goes on wearing its own");
        assertEquals(List.of(), SkillBook.aurasOn(skeleton), "no aura is on a creature that has fallen");
    }

    /** The mark follows the aura: its bearer fallen, none is laid on those it lent to -- from the very next beat. */
    @Test
    void whenItsBearerFallsNoMarkIsLaidOnThoseItLentTo() {
        var room = beside(SETTINGS, SUMMONER);
        var skeleton = lastOf(room);
        assertFalse(on(marked(room.game(), 30), skeleton).isEmpty(), "the premise: it wears the mark");

        toTheFrameBefore(room, skeleton);
        var body = room.get(0).getBody();
        body.damage(body.getHealth() + 1f);

        assertEquals(List.of(), on(marked(room.game(), 90), skeleton),
                "its bearer killed on the frame before its beat");
    }

    /**
     * One rule says which auras are on a creature: the kinds that reach it, each at most once, in {@code SkillEffect}
     * order whatever order their bearers stand in -- a mana aura counted only for a creature with a pool.
     */
    @Test
    void aurasOnAnswersTheKindsThatReachItInOrder() {
        var room = beside(SETTINGS, REVENANT, HEALER, SUMMONER);
        var skeleton = lastOf(room);

        assertEquals(50, SkillBook.auraOn(skeleton, SkillEffect.MANA_AURA),
                "the premise: the healer's aura reaches it");
        assertEquals(List.of(SkillEffect.DAMAGE_AURA, SkillEffect.LIFESTEAL_AURA), SkillBook.aurasOn(skeleton),
                "with no pool the mana aura is not on it, though it reaches it");
        bookOf(skeleton).resize(50, 0);
        assertEquals(List.of(SkillEffect.DAMAGE_AURA, SkillEffect.MANA_AURA, SkillEffect.LIFESTEAL_AURA),
                SkillBook.aurasOn(skeleton), "given a pool, all three, in SkillEffect order");
        assertEquals(List.of(), SkillBook.aurasOn(null), "nobody");
        assertThrows(UnsupportedOperationException.class, () -> SkillBook.aurasOn(skeleton).add(SkillEffect.STRIKE),
                "an answer, never a view of anything kept");
    }

    /**
     * A list that ends early is worn to its last -- three kinds reaching a creature where two marks are named: the
     * second -- and an empty one draws nothing, the summoner wearing its might all the same.
     */
    @Test
    void aCountPastTheListsEndWearsItsLastAndAnEmptyListDrawsNothing() {
        var shipped = SETTINGS.combat().auraMarkLooks();
        var two = marksNamed(shipped.subList(0, 2));
        var room = beside(two, SUMMONER, HEALER, REVENANT);
        bookOf(lastOf(room)).resize(50, 0);
        assertEquals(3, SkillBook.aurasOn(lastOf(room)).size(), "the premise: three kinds reach it");
        assertEquals(List.of(shipped.get(1), shipped.get(1)), on(played(room.game(), 60, shipped), lastOf(room)),
                "three kinds, two marks named: the second");

        var empty = beside(marksNamed(List.of()), SUMMONER);
        var looks = new ArrayList<>(shipped);
        looks.add(auraOf(empty.get(0)).look());
        var seen = played(empty.game(), 90, looks);
        assertEquals(3, on(seen, empty.get(0)).size(), "the premise: the summoner wears its might");
        assertTrue(seen.stream().noneMatch(one -> shipped.contains(one.look())),
                "an empty list draws nothing: " + seen);
    }

    /** Each mark lasts two beats: it rides the creature while an aura reaches it, and is gone within two of leaving. */
    @Test
    void eachMarkIsMeasuredTwoBeats() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        var looks = SETTINGS.combat().auraMarkLooks();
        assertEquals(3, looks.size(), "the premise: a mark for one kind, for two and for three");

        for (var look : looks) {
            assertEquals(2f * SETTINGS.combat().auraMarkTickFrames() / GameConstants.LOGICFRAMES_PER_SECOND,
                    visuals.getEffectSeconds(look), 0.001f, look + ": two beats");
        }
    }

    /**
     * The beat is the file's, and no figure the code keeps: at an AuraMarkTickFrames of 20 each creature wears its mark
     * on the frames its id falls on in twenties, three in two seconds, and each mark is measured two of those beats.
     */
    @Test
    void theMarksAreLaidAndMeasuredAtTheBeatTheFileGives() {
        var data = Content.data();
        var twenty = data.replaceFirst("(?m)^ *AuraMarkTickFrames = .*$", "  AuraMarkTickFrames = 20");
        assertNotEquals(data, twenty, "the premise: the Combat block gives the marks' beat");
        var settings = DungeonSettings.parse(twenty);
        var room = beside(settings, SUMMONER);
        var marks = marked(room.game(), 60);

        for (var creature : room.ones()) {
            var its = marks.stream().filter(one -> creature.getId().equals(one.on())).toList();
            assertEquals(3, its.size(), creature.getTemplate().name() + ": three beats in two seconds: " + its);
            for (var one : its) {
                assertEquals(Math.floorMod(creature.getId().value(), 20), one.frame() % 20,
                        "on the frames its id falls on in twenties: " + its);
            }
        }
        var visuals = Visuals.create();
        Main.measureLooks(visuals, settings);
        for (var look : settings.combat().auraMarkLooks()) {
            assertEquals(2f * 20 / GameConstants.LOGICFRAMES_PER_SECOND, visuals.getEffectSeconds(look), 0.001f,
                    look + ": two beats of 20");
        }
    }

    /**
     * As shipped: a small light-blue circle lying just above the floor under the creature, a few units across and not
     * the reach of anything, crossfading from one beat's to the next as the rings do -- and burning brighter the more
     * kinds reach it. Light blue is paler than the mana ring's blue: a hue between 190 and 215 degrees, at most 65%
     * saturated, and at least 90% bright.
     */
    @Test
    void theShippedMarksAreLightBlueAndBrighterTheMoreKindsReachIt() {
        var looks = SETTINGS.combat().auraMarkLooks();
        assertEquals(3, looks.size(), "the premise: a mark for one kind, for two and for three");

        float dimmer = 0f;
        for (var look : looks) {
            var layers = SETTINGS.effectLayers().stream().filter(art -> art.effect().equals(look))
                    .map(art -> Main.layerOf(art, SETTINGS)).toList();
            assertEquals(1, layers.size(), look + " is one circle");
            var mark = layers.getFirst();
            assertEquals(EffectLayer.MARK, mark.type(), look + " lies on the floor");
            assertTrue(mark.follows(), look + " goes where its creature goes");
            assertEquals(EffectLayer.UNITS, mark.measure(), look + " is a few units across, whatever an aura's reach");
            assertEquals(0.5f, mark.fadeIn(), 0.001f, look);
            assertEquals(0.5f, mark.fadeOut(), 0.001f, look);
            assertTrue(mark.height() > 0f && mark.height() <= 0.5f, look + " lies just above the floor: "
                    + mark.height());
            for (int colour : new int[] {mark.colourStart(), mark.colourEnd()}) {
                var hsb = java.awt.Color.RGBtoHSB(colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF, null);
                float hue = hsb[0] * 360f;
                assertTrue(hue >= 190f && hue <= 215f && hsb[1] >= 0.2f && hsb[1] <= 0.65f && hsb[2] >= 0.9f,
                        look + " is drawn in 0x" + Integer.toHexString(colour) + ": hue " + Math.round(hue)
                                + ", saturation " + Math.round(hsb[1] * 100) + "%, brightness "
                                + Math.round(hsb[2] * 100) + "% -- not a light blue");
            }
            assertEquals(mark.alphaStart(), mark.alphaEnd(), 0.0001f, look + " holds its brightness");
            assertTrue(mark.alphaStart() > dimmer, look + " burns at " + mark.alphaStart() + ", no brighter than the "
                    + "mark for one kind fewer, " + dimmer);
            dimmer = mark.alphaStart();
        }
    }

    // ---- shown ----

    /** What the screen last handed over for {@code creature}, each one's kind in the order handed: none for none. */
    private static List<SkillEffect> handedOver(BuffScreen screen, GameObject creature) {
        return screen.buffs().getOrDefault(creature.getId().value(), List.of()).stream()
                .map(buff -> buff.skill().effect()).toList();
    }

    /** The player has picked {@code creature} alone, and a frame has gone by. */
    private static void picked(DukeGame game, GameObject creature) {
        game.setSelection(List.of(creature.getId().value()));
        game.runHeadless(1);
    }

    /**
     * What is on the creature the player has picked alone is handed over whole after each frame, each with its skill and
     * figure: a Skeleton in a summoner's reach holds the might; beside a healer too, given a pool, the might and the mana;
     * and hastened, the haste and the frames it has left first, as SkillEffect has them -- until it burns out.
     */
    @Test
    void whatIsOnTheCreatureThePlayerPicksIsHandedOverWhole() {
        var screen = new BuffScreen(SETTINGS);
        var mighty = beside(SETTINGS, screen::show, SUMMONER);
        picked(mighty.game(), lastOf(mighty));
        var might = auraOf(mighty.get(0));
        assertEquals(List.of(new BuffScreen.Buff(might, 50, 0)), screen.buffs().get(lastOf(mighty).getId().value()),
                "the might, at its 50");

        var shown = new BuffScreen(SETTINGS);
        var both = beside(SETTINGS, shown::show, SUMMONER, HEALER);
        bookOf(lastOf(both)).resize(50, 0);
        picked(both.game(), lastOf(both));
        assertEquals(List.of(new BuffScreen.Buff(might, 50, 0), new BuffScreen.Buff(auraOf(both.get(1)), 50, 0)),
                shown.buffs().get(lastOf(both).getId().value()), "the might, and the mana in tenths a second");

        var its = new BuffScreen(SETTINGS);
        var hastened = beside(SETTINGS, its::show, SUMMONER);
        var skeleton = lastOf(hastened);
        assertTrue(bookOf(hastened.get(0)).cast('W', 1, null, null), "the premise: it cast its haste");
        assertTrue(bookOf(skeleton).getHasteFrames() > 0, "the premise: on the Skeleton, the sturdier");
        picked(hastened.game(), skeleton);
        var held = its.buffs().get(skeleton.getId().value());
        var haste = SETTINGS.skillsFor(SUMMONER).stream().filter(skill -> skill.effect() == SkillEffect.HASTE)
                .findFirst().orElseThrow();
        assertEquals(List.of(new BuffScreen.Buff(haste, 75, bookOf(skeleton).getHasteFrames()),
                new BuffScreen.Buff(might, 50, 0)), held, "the haste with the frames it has left, then the might");

        hastened.game().runHeadless(haste.durationFrames());
        assertEquals(List.of(SkillEffect.DAMAGE_AURA), handedOver(its, skeleton), "burnt out, the might alone");
    }

    /**
     * Nothing is handed over for a creature nothing is on: a hero beside a summoner -- no aura reaches a hero -- a
     * Skeleton with no pool beside a healer alone, and a hastened Skeleton in a might once it has fallen. A body is
     * reaped in the frame it falls, before the screen reads the world; one killed after the reap -- by a word of the
     * game's own, read before the screen's -- is read lying there dead, its haste still counting down in its book.
     */
    @Test
    void aHeroAPoollessSkeletonBesideAHealerAndAFallenOneHoldNone() {
        var arena = Dungeon.world(room(NO_WALL), unseeingInWhole(SUMMONER), unarmedRogue());
        var game = arena.game();
        var beheld = new BuffScreen(SETTINGS);
        beheld.show(game);
        game.spawn(SUMMONER, arena.dungeon(), 100f, 150f);
        game.spawn("Rogue", arena.hero(), 115f, 150f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = first(game, "Rogue");
        assertEquals(1.5f, might(first(game, SUMMONER)), 0.0001f, "the premise: the summoner bears its might");
        picked(game, hero);
        assertEquals(List.of(), handedOver(beheld, hero), "a hero, 15 from a summoner");

        var dry = new BuffScreen(SETTINGS);
        var poolless = beside(SETTINGS, dry::show, HEALER);
        assertEquals(50, SkillBook.auraOn(lastOf(poolless), SkillEffect.MANA_AURA), "the premise: the aura reaches it");
        picked(poolless.game(), lastOf(poolless));
        assertEquals(List.of(), handedOver(dry, lastOf(poolless)), "a Skeleton with no pool beside a healer");

        var fallen = new BuffScreen(SETTINGS);
        var killed = new java.util.concurrent.atomic.AtomicReference<GameObject>();
        var room = beside(SETTINGS, world -> {
            world.onTick(ticked -> {
                var one = killed.getAndSet(null);
                if (one != null) {
                    one.getBody().damage(one.getBody().getHealth() + 1f);
                }
            });
            fallen.show(world);
        }, SUMMONER);
        var skeleton = lastOf(room);
        assertTrue(bookOf(room.get(0)).cast('W', 1, null, null), "the premise: it cast its haste");
        picked(room.game(), skeleton);
        assertEquals(List.of(SkillEffect.HASTE, SkillEffect.DAMAGE_AURA), handedOver(fallen, skeleton),
                "the premise: hastened, in the might");
        killed.set(skeleton);
        room.game().runHeadless(1);
        assertTrue(skeleton.isEffectivelyDead() && room.game().getLogic().findObject(skeleton.getId()) != null
                && bookOf(skeleton).getHasteFrames() > 0, "the premise: it lies there dead, its haste counting down");
        assertEquals(List.of(), handedOver(fallen, skeleton), "fallen");
    }

    /**
     * And the one the pointer rests on, picked or not -- kept once the pointer leaves it, so its row may stay while the
     * pointer is on the row, until the pointer rests on another; two picked together are neither picked alone.
     */
    @Test
    void theOneThePointerRestsOnIsHandedOverTooAndKeptWhenItLeaves() {
        var screen = new BuffScreen(SETTINGS);
        var room = beside(SETTINGS, screen::show, SUMMONER);
        var game = room.game();
        var summoner = room.get(0);
        var skeleton = lastOf(room);

        game.setSelection(List.of(summoner.getId().value(), skeleton.getId().value()));
        game.runHeadless(1);
        assertEquals(Map.of(), screen.buffs(), "two picked, neither alone");

        game.setPointedAt(skeleton.getId().value());
        game.runHeadless(1);
        assertEquals(List.of(SkillEffect.DAMAGE_AURA), handedOver(screen, skeleton), "the pointer on it");
        game.setPointedAt(-1);
        game.runHeadless(1);
        assertEquals(List.of(SkillEffect.DAMAGE_AURA), handedOver(screen, skeleton), "and kept once it leaves it");
        game.setPointedAt(summoner.getId().value());
        game.runHeadless(1);
        assertEquals(List.of(), handedOver(screen, skeleton), "until it rests on another");
        assertEquals(List.of(SkillEffect.DAMAGE_AURA), handedOver(screen, summoner), "which is handed over instead");
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

    /**
     * A summoner lends its own side and no other: a Skeleton of the hero's side -- a monster of another player, whose
     * own book reaches as far as any monster's -- 36 from it and in its plain sight hits as hard as it ever did, where
     * one of the summoner's own, 30 from it, hits half as hard again.
     */
    @Test
    void aMonsterOfAnotherSideIsNotLent() {
        var arena = Dungeon.world(room(NO_WALL), SETTINGS);
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), 100f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 130f, 150f);
        game.spawn("Skeleton", arena.hero(), 130f, 170f);
        game.runHeadless(1);
        var room = new Room(game, game.getLogic().getObjects().stream().filter(object -> object.getBody() != null)
                .toList());
        var own = room.get(1);
        var rival = room.get(2);

        assertEquals(arena.dungeon().getIndex(), own.getPlayerIndex(), "the premise: one is the summoner's own");
        assertEquals(arena.hero().getIndex(), rival.getPlayerIndex(), "the premise: and one is of another side");
        assertEquals(1.5f, might(own), 0.0001f, "the premise: it lends to its own, 30 from it");
        assertEquals(1f, might(rival), 0.0001f, "a rival's, 36 from it");
        assertEquals(0, SkillBook.auraOn(rival, SkillEffect.DAMAGE_AURA));
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

    /**
     * An aura is worn at the beat it names: its Look and its TickFrames come together or not at all, else the file is
     * refused. A lifesteal is not worn, and still may not say what it looks like.
     */
    @Test
    void anAurasLookAndItsBeatComeTogether() {
        var block = ShippedBlock.of(SUMMONER).text();
        for (var line : new String[] {"      Look = MightAura\n", "      TickFrames = 30\n"}) {
            var text = block.replace(line, "");
            assertNotEquals(block, text, "the premise: " + line.strip() + " was taken off its might");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(text), line);
            assertTrue(refused.getMessage().contains("TickFrames"), refused.getMessage());
        }

        var warden = ShippedBlock.of("Warden").text();
        var drawn = warden.replace("      Effect = LIFESTEAL\n",
                "      Effect = LIFESTEAL\n      Look = BloodAura\n      TickFrames = 30\n");
        assertNotEquals(warden, drawn, "the premise: the Warden's lifesteal was given a look");
        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(drawn));
        assertTrue(refused.getMessage().contains("never cast"), refused.getMessage());
    }

    /**
     * An aura holds while its bearer lives, and its look is measured two of its beats: a DurationFrames on one would
     * lay its rings more than two deep, and break the steady ring they stand as -- so the file is refused, saying so,
     * of each of the three.
     */
    @Test
    void anAuraThatSaysHowLongItLastsIsRefused() {
        for (var aura : new String[][] {{SUMMONER, "DAMAGE_AURA"}, {HEALER, "MANA_AURA"},
                {REVENANT, "LIFESTEAL_AURA"}}) {
            var block = ShippedBlock.of(aura[0]).text();
            var lasting = block.replace("      Effect = " + aura[1] + "\n",
                    "      Effect = " + aura[1] + "\n      DurationFrames = 600\n");
            assertNotEquals(block, lasting, "the premise: the " + aura[1] + " was given a DurationFrames");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(lasting), aura[1]);
            assertTrue(refused.getMessage().contains("DurationFrames"), refused.getMessage());
            assertTrue(refused.getMessage().contains(aura[1]), refused.getMessage());
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

    /**
     * Two worlds fought through the same frames, the three mages and their own in them, read the same checksum each
     * second through -- and not the one checksum every time, which would pass for any two worlds.
     */
    @Test
    void twoWorldsFoughtAlikeReadTheSameChecksum() {
        var sums = checksums();

        assertEquals(sums, checksums());
        assertTrue(sums.stream().distinct().count() > 1, "the premise: the fight moved the world on: " + sums);
    }

    private static List<Long> checksums() {
        var arena = Dungeon.world(room(NO_WALL), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        for (var one : new One[] {one(SUMMONER, 210f, 130f), one(HEALER, 210f, 170f), one(REVENANT, 200f, 150f),
                one(MAGE, 220f, 150f), one("Skeleton", 175f, 140f), one("Brute", 175f, 160f)}) {
            game.spawn(one.kind(), arena.dungeon(), one.x(), one.y());
        }
        var sums = new ArrayList<Long>();
        for (int i = 0; i < 12; i++) {
            game.runHeadless(30);
            sums.add(game.getLogic().checksum());
        }
        return sums;
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
