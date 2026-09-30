package uz.dukeengine.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.combat.LevelBonus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.Summoned;
import uz.dukeengine.game.DukeGame;

/**
 * A monster's mana: the {@code SkillBook}'s own pool, as a hero's is -- grown by its level where it is placed, full
 * then, trickling back, and paid from at its skills' costs. A kind that names no pool casts free.
 *
 * <p>Asked of a caster alone in a room with nobody to notice, and cast by hand, so nothing its brain does moves a
 * figure the test is reading -- and of a floor placed for real and of a rift's rising, the two places besides a
 * direct call that make a monster its level.
 */
class MonsterManaTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String MAGE = "SkeletonMage";
    private static final String SUMMONER = "SkeletonSummoner";

    /** Somewhere on the floor to throw at. */
    private static final Coord3D AWAY = new Coord3D(100f, 150f, 0f);

    /** An open room forty cells by thirty, stone only round its edge. */
    private static String room() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                text.append(x == 0 || y == 0 || x == 39 || y == 29 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record Alone(DukeGame game, GameObject mage) {

        SkillBook book() {
            return mage.findModule(SkillBook.class);
        }
    }

    /** The fire mage alone in a room, as {@code settings} build it, with nobody to notice. */
    private static Alone aloneUnder(DungeonSettings settings) {
        var arena = Dungeon.world(room(), settings);
        var game = arena.game();
        game.spawn(MAGE, arena.dungeon(), 150f, 150f);
        game.runHeadless(1);
        return new Alone(game, game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(MAGE)).findFirst().orElseThrow());
    }

    /** A whole number the fire mage's shipped block writes once. */
    private static int shipped(String key) {
        return Integer.parseInt(ShippedBlock.of(MAGE).value(key));
    }

    /**
     * Placed at level 8: its pool and its trickle grown a tenth a level, in whole points and tenths -- its 60 and 30
     * become 102 and 51 -- and full.
     */
    @Test
    void itsPoolIsGrownByItsLevelAndFullWhereItIsPlaced() {
        var alone = aloneUnder(SETTINGS);
        Spawner.scale(alone.mage(), 8, SETTINGS);

        assertEquals(shipped("MaxMana") + shipped("MaxMana") * 7 * 10 / 100, alone.book().getMaxMana(), "its pool");
        assertEquals(alone.book().getMaxMana(), alone.book().getMana(), "full");
        assertEquals(shipped("ManaRegen") + shipped("ManaRegen") * 7 * 10 / 100, alone.book().getManaRegen(),
                "its trickle, in tenths of a point a second");
        assertEquals(102, alone.book().getMaxMana(), "60 and a tenth of it for each of seven levels");
        assertEquals(51, alone.book().getManaRegen(), "30 and a tenth of it for each of seven levels");
    }

    /** Placed at level 1 nothing is grown: its pool and its trickle are its block's own, 60 and 30, and it is full. */
    @Test
    void aLevelOnePoolIsItsBlocksOwn() {
        var alone = aloneUnder(SETTINGS);
        Spawner.scale(alone.mage(), 1, SETTINGS);

        assertEquals(60, shipped("MaxMana"), "the premise: its block names a pool of 60");
        assertEquals(30, shipped("ManaRegen"), "and a trickle of 30");
        assertEquals(shipped("MaxMana"), alone.book().getMaxMana(), "its pool");
        assertEquals(alone.book().getMaxMana(), alone.book().getMana(), "full");
        assertEquals(shipped("ManaRegen"), alone.book().getManaRegen(), "its trickle");
    }

    /** What a cast spends -- its skill's cost at its first rank -- comes back at the trickle, in whole points. */
    @Test
    void whatItSpendsTricklesBack() {
        var alone = aloneUnder(SETTINGS);
        Spawner.scale(alone.mage(), 8, SETTINGS);
        var book = alone.book();
        int full = book.getMana();

        assertTrue(book.cast('Q', 1, null, AWAY), "the premise: it threw");
        int spent = full - book.getMana();
        assertTrue(spent > 0, "the premise: its fireball costs something");
        assertEquals(book.skillOn('Q').manaAt(1), spent, "it paid the fireball's cost");
        alone.game().runHeadless(30);

        assertEquals(full - spent + book.getManaRegen() / 10, book.getMana(), "a second's trickle back");
    }

    /** A cast it cannot pay for is refused before anything happens: no mana taken, and its cooldown not started. */
    @Test
    void aCastItCannotPayForIsRefusedWithNothingSpent() {
        var alone = aloneUnder(SETTINGS);
        Spawner.scale(alone.mage(), 1, SETTINGS);
        var book = alone.book();
        book.resize(10, 0);

        assertFalse(book.cast('Q', 1, null, AWAY), "it threw what it could not pay for");
        assertEquals(10, book.getMana(), "and something was taken");
        assertTrue(book.isReady('Q'), "and its cooldown was started");
    }

    /** A kind that names no pool casts free, as every monster did: the fire mage re-tuned without one. */
    @Test
    void aKindThatNamesNoPoolCastsFree() {
        var poolless = DungeonSettings.parse("""
                Monster
                  Name = SkeletonMage
                  SkillDistance = [20, 60]
                End
                """);
        var alone = aloneUnder(poolless);
        Spawner.scale(alone.mage(), 8, poolless);

        assertEquals(0, alone.book().getMaxMana(), "it was given a pool");
        assertTrue(alone.book().cast('Q', 1, null, AWAY), "and without one it could not cast");
    }

    /** A monster whose own skills cost mana and that names no pool is refused when the file is read. */
    @Test
    void aCostWithNoPoolIsRefusedWhenTheFileIsRead() {
        var block = ShippedBlock.of(MAGE).text();
        var poolless = block.replace("  MaxMana = " + shipped("MaxMana") + "\n", "");
        assertNotEquals(block, poolless, "the premise: its pool was taken out");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(poolless));
        assertTrue(refused.getMessage().contains("MaxMana"), refused.getMessage());
    }

    /**
     * A pool, a trickle and the growth that grows them cannot be negative: each is refused when the file is read. The
     * skeleton casts nothing, so the check that a cost names a pool has nothing to say of its {@code MaxMana}.
     */
    @Test
    void aNegativePoolTrickleOrGrowthIsRefusedWhenTheFileIsRead() {
        var growth = Content.data().replace("    ManaPercentPerLevel = 10\n", "    ManaPercentPerLevel = -10\n");
        assertNotEquals(Content.data(), growth, "the premise: the shipped files say ManaPercentPerLevel = 10");

        for (var wrong : new String[][] {
                {"MaxMana", "Monster\n  Name = Skeleton\n  MaxMana = -5\nEnd\n"},
                {"ManaRegen", ShippedBlock.dataWith(MAGE, "ManaRegen", -30)},
                {"ManaPercentPerLevel", growth}}) {
            assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(wrong[1]), wrong[0]);
        }
    }

    /**
     * A floor placed for real gives every kind that names a pool its pool, at its own level, and fills it: the boss's
     * guard, two healers and two summoners, stands on every floor to be asked.
     */
    @Test
    void aPlacedFloorGivesEveryCasterItsPoolAtItsOwnLevel() {
        var session = Dungeon.newSession(11L);
        session.game().runHeadless(1);

        int pooled = 0;
        for (var object : session.game().getLogic().getObjects()) {
            var kind = SETTINGS.monster(object.getTemplate().name());
            if (kind == null || kind.maxMana() == 0) {
                continue;
            }
            pooled++;
            var book = object.findModule(SkillBook.class);
            int level = LevelBonus.levelOf(object);
            assertEquals(SETTINGS.manaAtLevel(kind.maxMana(), level), book.getMaxMana(),
                    kind.name() + "'s pool at level " + level);
            assertEquals(SETTINGS.manaAtLevel(kind.manaRegen(), level), book.getManaRegen(),
                    kind.name() + "'s trickle at level " + level);
            assertEquals(book.getMaxMana(), book.getMana(), kind.name() + " placed full");
        }
        assertTrue(pooled >= 4, "the premise: the boss's two healers and two summoners stood on the floor: " + pooled);
    }

    /** What rises from a rift is made as what is placed is: a mage it calls up has its pool, at its caller's level. */
    @Test
    void whatRisesFromARiftHasItsPoolAtItsCallersLevel() {
        var settings = DungeonSettings.parse(ShippedBlock.of(SUMMONER).with("Summons", "[SkeletonMage = 2]").text());
        var arena = Dungeon.world(room(), settings);
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        var summoner = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(SUMMONER)).findFirst().orElseThrow();
        Spawner.scale(summoner, 8, settings);

        assertTrue(summoner.findModule(SkillBook.class).cast('Q', 1, null, AWAY), "the premise: it opened its rifts");
        game.runHeadless(30);

        var risen = game.getLogic().getObjects().stream()
                .filter(object -> object.findModule(Summoned.class) != null).toList();
        assertEquals(2, risen.size(), "the premise: two mages rose");
        for (var one : risen) {
            var book = one.findModule(SkillBook.class);
            assertEquals(102, book.getMaxMana(), "its pool, at its caller's level 8");
            assertEquals(51, book.getManaRegen(), "its trickle");
            assertEquals(book.getMaxMana(), book.getMana(), "and full");
        }
    }

    /**
     * As shipped: the three casting mages' pools, their trickles and what their skills cost -- the fire mage's 60 and
     * 30 for a fireball of 20, the healer's 50 and 25 for a mending of 25, the summoner's 80 and 25 for a calling of
     * 40 -- and everything else down here casts free.
     */
    @Test
    void asShippedTheThreeCastingMagesPayFromPoolsAndNothingElseDoes() {
        var pools = Map.of("SkeletonMage", List.of(60, 30, 20), "SkeletonHealer", List.of(50, 25, 25),
                "SkeletonSummoner", List.of(80, 25, 40));
        for (var kind : SETTINGS.monsters()) {
            var skills = SETTINGS.skillsFor(kind.name());
            var pool = pools.get(kind.name());
            if (pool == null) {
                assertEquals(0, kind.maxMana(), kind.name() + " names a pool");
                assertTrue(skills.stream().allMatch(skill -> skill.manaAt(1) == 0), kind.name() + "'s skills cost mana");
                continue;
            }
            var q = skills.stream().filter(skill -> skill.key() == 'Q').findFirst().orElseThrow();
            assertEquals(pool, List.of(kind.maxMana(), kind.manaRegen(), q.manaAt(1)), kind.name());
        }
    }
}
