package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.dungeon.content.BiomeFile;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** The ground under a floor: hills that rise and fall, and not one cell anywhere too steep to walk. */
class ReliefTest {

    /**
     * Whole floors, each its depth's theme: these tune one theme's ground and watch what it does, and a floor that
     * mixes biomes never asks the depth's theme — BiomeFloorTest holds the same promises for those.
     */
    private static final DungeonSettings SETTINGS = BiomeFile.listing();

    /** The shipped files with {@code from} rewritten as {@code to}, which has to be there to rewrite. */
    private static String rewritten(String data, String from, String to) {
        assertTrue(data.contains(from), "the shipped files no longer say " + from.strip());
        return data.replace(from, to);
    }

    /** How far apart the corners of one cell are: 16 or more is a cliff. */
    private static int steepness(HeightMap relief, int x, int y) {
        int a = relief.at(x, y);
        int b = relief.at(x + 1, y);
        int c = relief.at(x, y + 1);
        int d = relief.at(x + 1, y + 1);
        return Math.max(Math.max(a, b), Math.max(c, d)) - Math.min(Math.min(a, b), Math.min(c, d));
    }

    /**
     * No cell of any floor is steeper than its theme's {@code Slope} — and so none is a cliff, and every chamber the
     * cave joined is still joined on the hills. Over both of the shipped grounds.
     */
    @Test
    void noCellIsSteeperThanItsThemeAllows() {
        for (int depth = 1; depth <= 4; depth++) {
            int slope = SETTINGS.themes().terrainAt(depth).slope();
            for (long seed = 0; seed <= 30; seed++) {
                var relief = DungeonGenerator.generate(seed, SETTINGS, depth).relief();
                assertNotNull(relief, "depth " + depth + " should have ground that rises");
                for (int y = 0; y < relief.rows() - 1; y++) {
                    for (int x = 0; x < relief.columns() - 1; x++) {
                        assertFalse(relief.isCliff(x, y), "seed " + seed + ", depth " + depth + ": a cliff at " + x
                                + "," + y);
                        assertTrue(steepness(relief, x, y) <= slope, "seed " + seed + ", depth " + depth + ": "
                                + x + "," + y + " is " + steepness(relief, x, y) + " steps across");
                    }
                }
            }
        }
    }

    /** And it does rise: hills and hollows a good part of the terrain's Rise apart, not a floor tilted by a step. */
    @Test
    void theGroundRisesAndFalls() {
        for (int depth = 1; depth <= 4; depth++) {
            int rise = SETTINGS.themes().terrainAt(depth).rise();
            for (long seed = 0; seed <= 30; seed++) {
                var relief = DungeonGenerator.generate(seed, SETTINGS, depth).relief();
                int lowest = Integer.MAX_VALUE;
                int highest = Integer.MIN_VALUE;
                for (int y = 0; y < relief.rows(); y++) {
                    for (int x = 0; x < relief.columns(); x++) {
                        lowest = Math.min(lowest, relief.at(x, y));
                        highest = Math.max(highest, relief.at(x, y));
                    }
                }
                assertEquals(0, lowest, "the lowest ground is where heights are counted from");
                assertTrue(highest >= rise / 3, "seed " + seed + ", depth " + depth + ": the ground rose only "
                        + highest + " of " + rise);
            }
        }
    }

    /** A seed raises the same ground every time. */
    @Test
    void aSeedRaisesTheSameGround() {
        assertEquals(DungeonGenerator.generate(12L, SETTINGS, 3).relief(),
                DungeonGenerator.generate(12L, SETTINGS, 3).relief());
    }

    /** The hills are only the ground: the same floor, the same monsters, the same furniture, flat or not. */
    @Test
    void theHillsChangeNothingButTheGround() {
        var flatData = rewritten(BiomeFile.textListing(), "    Rise = 56\n", "    Rise = 0\n");
        var flat = DungeonSettings.parse(rewritten(flatData, "    Rise = 40\n", "    Rise = 0\n"));
        for (int depth = 1; depth <= 4; depth++) {
            for (long seed = 0; seed < 10; seed++) {
                var level = DungeonGenerator.generate(seed, flat, depth);
                var hilly = DungeonGenerator.generate(seed, SETTINGS, depth);
                assertNull(level.relief(), "a Rise of nothing lays the floor flat");
                assertEquals(level.asciiMap(), hilly.asciiMap(), "seed " + seed + ", depth " + depth);
                assertEquals(level.monsters(), hilly.monsters(), "seed " + seed + ", depth " + depth);
                assertEquals(level.props(), hilly.props(), "seed " + seed + ", depth " + depth);
            }
        }
    }

    /** A chamber laid level is flatter than the same chamber left to the hills. */
    @Test
    void aLevelledChamberIsFlatterThanTheHillsItStandsIn() {
        var cellar = rewritten(BiomeFile.textListing(), "  Themes = [Forest, Forest, Dungeon, Dungeon]\n",
                "  Themes = [Dungeon]\n");
        var levelled = DungeonSettings.parse(rewritten(cellar, "    Level = 70\n", "    Level = 100\n"));
        var rolling = DungeonSettings.parse(rewritten(cellar, "    Level = 70\n", "    Level = 0\n"));
        long levelledSteepness = 0;
        long rollingSteepness = 0;
        for (long seed = 0; seed < 20; seed++) {
            levelledSteepness += steepnessOfTheChambers(DungeonGenerator.generate(seed, levelled, 1));
            rollingSteepness += steepnessOfTheChambers(DungeonGenerator.generate(seed, rolling, 1));
        }
        assertTrue(levelledSteepness * 2 < rollingSteepness, "levelled chambers came out " + levelledSteepness
                + " steps steep all told, against " + rollingSteepness + " left to the hills");
    }

    /** Every floor cell's steepness, summed over the chambers' footprints. */
    private static long steepnessOfTheChambers(GeneratedDungeon floor) {
        var rows = floor.asciiMap().strip().split("\n");
        long total = 0;
        for (var room : floor.rooms()) {
            for (int y = room.y(); y < room.y() + room.h(); y++) {
                for (int x = room.x(); x < room.x() + room.w(); x++) {
                    if (rows[y].charAt(x) != '#') {
                        total += steepness(floor.relief(), x, y);
                    }
                }
            }
        }
        return total;
    }
}
