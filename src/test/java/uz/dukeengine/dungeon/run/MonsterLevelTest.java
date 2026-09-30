package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;

/**
 * A monster's level: where it is met, and what the level makes of it.
 *
 * <p>A place has a tier -- today a floor's depth, which is what a stage's {@code Difficulty} already means -- and the
 * level rule is handed the tier and nothing else: the way in at one end, the chamber before the boss's at the other,
 * the boss two above, and nothing above the cap.
 */
class MonsterLevelTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The shipped files with one line of them saying something else. */
    private static String dataWith(String line, String instead) {
        var data = Content.data().replace(line, instead);
        assertNotEquals(Content.data(), data, "the premise: the shipped files say " + line.strip());
        return data;
    }

    // ---- the rule ----

    /** Each tier runs from where the one before closed: 1 to 8, 8 to 13, 13 to 20, 20 to 33, the boss two above. */
    @Test
    void eachTierRunsFromWhereTheOneBeforeClosed() {
        int[][] tiers = {{1, 1, 8, 10}, {2, 8, 13, 15}, {3, 13, 20, 22}, {4, 20, 33, 35}};
        for (var tier : tiers) {
            assertEquals(tier[1], SETTINGS.wayInLevel(tier[0]), "tier " + tier[0] + "'s way in");
            assertEquals(tier[2], SETTINGS.beforeBossLevel(tier[0]), "tier " + tier[0] + " before its boss");
            assertEquals(tier[3], SETTINGS.bossLevel(tier[0]), "tier " + tier[0] + "'s boss");
        }
    }

    /** Asked straight for the fourth tier, it is the fourth tier: worked out in one step, never tier by tier. */
    @Test
    void theFourthTierAskedDirectlyIsTheFourthTier() {
        var fresh = DungeonSettings.load();

        assertEquals(33, fresh.beforeBossLevel(4));
        assertEquals(20, fresh.wayInLevel(4));
        assertEquals(35, fresh.bossLevel(4));
    }

    /** With no growth, every tier after the first stays where the first closed: 8 to 8. */
    @Test
    void withNoGrowthEveryTierAfterTheFirstStaysWhereTheFirstClosed() {
        var flat = DungeonSettings.parse(dataWith("    TierGrowthPercent = 60\n", "    TierGrowthPercent = 0\n"));

        assertEquals(1, flat.wayInLevel(1));
        for (int tier = 2; tier <= 6; tier++) {
            assertEquals(8, flat.wayInLevel(tier), "tier " + tier + "'s way in");
            assertEquals(8, flat.beforeBossLevel(tier), "tier " + tier + " before its boss");
        }
    }

    /** Nothing stands above the cap, the boss included: the deep stage plays at it throughout. */
    @Test
    void nothingStandsAboveTheCap() {
        assertEquals(50, SETTINGS.maxMonsterLevel());
        for (int tier = 1; tier <= 20; tier++) {
            assertTrue(SETTINGS.bossLevel(tier) <= SETTINGS.maxMonsterLevel(),
                    "tier " + tier + "'s boss stands at " + SETTINGS.bossLevel(tier));
        }
        int deep = Stages.load("deep", SETTINGS).difficulty();
        assertEquals(50, SETTINGS.wayInLevel(deep), "the deep stage's way in");
        assertEquals(50, SETTINGS.beforeBossLevel(deep), "and before its boss");
        assertEquals(50, SETTINGS.bossLevel(deep), "and its boss");
    }

    /**
     * Along the way a level is the way in plus its share of the climb, the share the steps walked over the steps to
     * the chamber -- rounded once, and never past that chamber's.
     */
    @Test
    void alongTheWayALevelIsItsShareOfTheClimb() {
        assertEquals(1, SETTINGS.levelAlong(1, 0, 70), "at the way in");
        assertEquals(5, SETTINGS.levelAlong(1, 35, 70), "half way: 1 + 7 x 0.5 is 4.5, rounded once");
        assertEquals(7, SETTINGS.levelAlong(1, 60, 70), "most of the way: 1 + 7 x 6/7 is 7");
        assertEquals(8, SETTINGS.levelAlong(1, 70, 70), "in the middle of the chamber");
        assertEquals(8, SETTINGS.levelAlong(1, 140, 70), "and past it, the chamber's");
        assertEquals(8, SETTINGS.levelAlong(1, -1, 70), "and where no step reaches, the whole way");
        assertEquals(11, SETTINGS.levelAlong(2, 35, 70), "the second tier half way: 8 + 5 x 0.5, rounded once");
    }

    /** A rule that steps back is refused when the file is read: no way in below 1, no chamber below it, no shrinking. */
    @Test
    void aRuleThatStepsBackIsRefused() {
        for (var wrong : new String[][] {
                {"    WayInLevel = 1\n", "    WayInLevel = 0\n"},
                {"    BeforeBossLevel = 8\n", "    BeforeBossLevel = 0\n"},
                {"    TierGrowthPercent = 60\n", "    TierGrowthPercent = -10\n"},
                {"    BossLevelsAbove = 2\n", "    BossLevelsAbove = -1\n"},
                {"    MaxMonsterLevel = 50\n", "    MaxMonsterLevel = 0\n"}}) {
            var data = dataWith(wrong[0], wrong[1]);
            assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data), wrong[1].strip());
        }
    }
}
