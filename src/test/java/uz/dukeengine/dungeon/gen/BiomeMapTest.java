package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.BiomeFile;
import uz.dukeengine.dungeon.content.Biomes;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.world.Theme;

/** Which biome every cell of a floor is: placed by climate, whole in every chamber, and never a speck. */
class BiomeMapTest {

    private static final Biomes TWO = BiomeFile.listing("Forest", "Dungeon").biomes();
    private static final int WIDTH = 96;
    private static final int HEIGHT = 72;
    private static final List<Room> ROOMS = List.of(new Room(8, 8, 9, 9), new Room(40, 30, 11, 9),
            new Room(70, 50, 13, 11));

    private static BiomeMap draw(long seed, int depth, Biomes biomes) {
        return BiomeMap.draw(seed, depth, WIDTH, HEIGHT, ROOMS, biomes);
    }

    @Test
    void aSeedGrowsOneMap() {
        assertArrayEquals(cellsOf(draw(7L, 1, TWO)), cellsOf(draw(7L, 1, TWO)));
    }

    /** A chamber is one place: the biome at its middle, over the whole of its footprint. */
    @Test
    void everyChamberIsOneBiomeThroughout() {
        for (long seed = 1; seed <= 40; seed++) {
            var map = draw(seed, 1, TWO);
            for (int i = 0; i < ROOMS.size(); i++) {
                var room = ROOMS.get(i);
                for (int y = room.y(); y < room.y() + room.h(); y++) {
                    for (int x = room.x(); x < room.x() + room.w(); x++) {
                        assertEquals(map.ofRoom(i), map.at(x, y), "seed " + seed + ", chamber " + i);
                    }
                }
            }
        }
    }

    /** The point of it: one floor, more than one place — on nearly every seed, not now and then. */
    @Test
    void aFloorMixesItsBiomes() {
        int mixed = 0;
        for (long seed = 1; seed <= 50; seed++) {
            if (biomesOn(draw(seed, 1, TWO)) > 1) {
                mixed++;
            }
        }
        assertTrue(mixed >= 45, "only " + mixed + " of 50 floors held both biomes");
    }

    /** Regions, not speckle: every patch of one biome is a place you could stand in. */
    @Test
    void noPatchIsSmallerThanAPlace() {
        for (long seed = 1; seed <= 40; seed++) {
            int smallest = smallestRegion(draw(seed, 1, TWO));
            assertTrue(smallest >= BiomeMap.SMALLEST_REGION, "seed " + seed + " left a patch of " + smallest);
        }
    }

    /** The drift the map asks for moves the floor toward the biomes it points at, the deeper the more. */
    @Test
    void depthDriftsTheClimate() {
        var drifting = new Biomes(TWO.all(), TWO.size(), new Theme.Climate(-10, -10));
        int shallow = 0;
        int deep = 0;
        for (long seed = 1; seed <= 30; seed++) {
            shallow += cellsOf(draw(seed, 1, drifting), "Dungeon");
            deep += cellsOf(draw(seed, 6, drifting), "Dungeon");
        }
        assertTrue(deep > shallow, "caves (the less wild, the less alive) should gain with depth: "
                + shallow + " cells at the top, " + deep + " five floors down");
    }

    private static byte[] cellsOf(BiomeMap map) {
        var cells = new byte[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                cells[y * WIDTH + x] = (byte) map.biomes().indexOf(map.at(x, y));
            }
        }
        return cells;
    }

    private static int cellsOf(BiomeMap map, String biome) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (map.at(x, y).name().equals(biome)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static long biomesOn(BiomeMap map) {
        var seen = new java.util.HashSet<String>();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                seen.add(map.at(x, y).name());
            }
        }
        return seen.size();
    }

    /** The fewest cells in any four-connected patch of one biome. */
    private static int smallestRegion(BiomeMap map) {
        var seen = new boolean[WIDTH * HEIGHT];
        int smallest = Integer.MAX_VALUE;
        for (int start = 0; start < seen.length; start++) {
            if (seen[start]) {
                continue;
            }
            var biome = map.at(start % WIDTH, start / WIDTH);
            int size = 0;
            var queue = new ArrayDeque<Integer>(List.of(start));
            seen[start] = true;
            while (!queue.isEmpty()) {
                int at = queue.poll();
                size++;
                int x = at % WIDTH;
                int y = at / WIDTH;
                for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + step[0];
                    int ny = y + step[1];
                    int next = ny * WIDTH + nx;
                    if (nx >= 0 && ny >= 0 && nx < WIDTH && ny < HEIGHT && !seen[next]
                            && map.at(nx, ny) == biome) {
                        seen[next] = true;
                        queue.add(next);
                    }
                }
            }
            smallest = Math.min(smallest, size);
        }
        return smallest;
    }
}
