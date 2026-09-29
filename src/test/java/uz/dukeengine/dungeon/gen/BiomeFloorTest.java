package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.BiomeFile;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stage;
import uz.dukeengine.dungeon.stage.StageCheck;

/** A floor that mixes biomes: every promise a floor makes, kept, and each region cut to its own ground. */
class BiomeFloorTest {

    private static final DungeonSettings MIXED = BiomeFile.listing("Forest", "Dungeon");
    /** All six, as generation.duke lists them. */
    private static final DungeonSettings ALL =
            BiomeFile.listing("Forest", "Autumn", "PineWood", "DeadLand", "Dungeon", "Mine");

    /** Reachable, stood on, readable — what the stage checker asks of a hand-made floor, asked of these. */
    @Test
    void aMixedFloorKeepsEveryPromiseAFloorMakes() {
        for (long seed = 1; seed <= 30; seed++) {
            var floor = DungeonGenerator.generate(seed, ALL, 1);
            assertNotNull(floor.biomes(), "the map lists biomes, so the floor should carry them");
            var problems = StageCheck.problems(new Stage("mixed", "Mixed", "", 1, 1, seed, floor), ALL);
            assertTrue(problems.isEmpty(), "seed " + seed + ": " + problems);
            int rooms = floor.rooms().size();
            assertTrue(rooms >= ALL.minRooms() && rooms <= ALL.maxRooms(), "seed " + seed + ": " + rooms);
        }
    }

    /** Two biomes' hills meet somewhere on every floor, and where they meet is never a cliff. */
    @Test
    void noBorderBetweenTwoBiomesIsACliff() {
        for (long seed = 1; seed <= 30; seed++) {
            var floor = DungeonGenerator.generate(seed, ALL, 1);
            var relief = floor.relief();
            for (int y = 0; y < relief.rows() - 1; y++) {
                for (int x = 0; x < relief.columns() - 1; x++) {
                    assertTrue(!relief.isCliff(x, y), "seed " + seed + " has a cliff at " + x + "," + y);
                }
            }
        }
    }

    /**
     * Each chamber is cut to its own biome: the wood's (Ragged 45, a grove or three in every glade) come out rougher
     * than the caves' (Ragged 20) on the same floors. Rough is how much of a chamber's floor lies against rock.
     */
    @Test
    void eachChamberIsCutToItsOwnBiome() {
        double woodEdge = 0;
        int woods = 0;
        double caveEdge = 0;
        int caves = 0;
        for (long seed = 1; seed <= 40; seed++) {
            var floor = DungeonGenerator.generate(seed, MIXED, 1);
            var rows = floor.asciiMap().strip().split("\n");
            for (int i = 0; i < floor.rooms().size(); i++) {
                double edge = edgeShare(rows, floor.rooms().get(i));
                if (floor.biomes().ofRoom(i).name().equals("Forest")) {
                    woodEdge += edge;
                    woods++;
                } else {
                    caveEdge += edge;
                    caves++;
                }
            }
        }
        assertTrue(woods > 20 && caves > 20, "both should be common: " + woods + " glades, " + caves + " caverns");
        assertTrue(woodEdge / woods > caveEdge / caves, "glades " + woodEdge / woods + " against rock, caverns "
                + caveEdge / caves);
    }

    /** The share of a chamber's floor cells with rock beside them. */
    private static double edgeShare(String[] rows, GeneratedDungeon.Room room) {
        int floor = 0;
        int edge = 0;
        for (int y = room.y(); y < room.y() + room.h(); y++) {
            for (int x = room.x(); x < room.x() + room.w(); x++) {
                if (rows[y].charAt(x) == '#') {
                    continue;
                }
                floor++;
                if (rows[y - 1].charAt(x) == '#' || rows[y + 1].charAt(x) == '#'
                        || rows[y].charAt(x - 1) == '#' || rows[y].charAt(x + 1) == '#') {
                    edge++;
                }
            }
        }
        return floor == 0 ? 0 : (double) edge / floor;
    }
}
