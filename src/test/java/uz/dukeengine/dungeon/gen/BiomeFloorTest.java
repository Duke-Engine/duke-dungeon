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
            int rooms = floor.rooms().size() - (floor.keep() == null ? 0 : 1);
            assertTrue(rooms >= ALL.minRooms() && rooms <= ALL.maxRooms(), "seed " + seed + ": " + rooms);
        }
    }

    /**
     * Whatever biome the heroes come in to, the way in has room for the fountain with a walk round it: open floor
     * two cells every way from the first chamber's middle. A cramped glade with a grove or two once left none.
     */
    @Test
    void theWayInAlwaysHasRoomForTheFountain() {
        for (long seed = 0; seed <= 80; seed++) {
            var floor = DungeonGenerator.generate(seed, ALL, 1 + (int) (seed % 4));
            var rows = floor.asciiMap().split("\n");
            var middle = floor.rooms().getFirst();
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    assertTrue(rows[middle.centerCellY() + dy].charAt(middle.centerCellX() + dx) == '.',
                            "seed " + seed + " (" + floor.biomes().ofRoom(0).name() + ") has rock "
                                    + dx + "," + dy + " from the way in");
                }
            }
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
            // A grove stands in its glade as an island stood: walkable between its trunks now, and still part of what
            // makes a glade rougher than a cavern — so its cells count here as what the floor is cut round.
            for (var piece : floor.scenery()) {
                if (piece.footprint() > 0f) {
                    int x = (int) piece.x();
                    int y = (int) piece.y();
                    rows[y] = rows[y].substring(0, x) + '#' + rows[y].substring(x + 1);
                }
            }
            for (int i = 0; i < floor.rooms().size(); i++) {
                if (floor.keep() != null && i == floor.bossRoom()) {
                    continue; // built, not cut to its biome
                }
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
