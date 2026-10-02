package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.BiomeFile;
import uz.dukeengine.dungeon.content.Biomes;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.map.ProceduralMap.Count;
import uz.dukeengine.dungeon.world.Theme;

/**
 * Which biome every cell of a floor is: a few large regions laid out first and each given a biome, whole in every
 * chamber — on a floor, on a map ten times as wide and on one a hundred times as large.
 */
class BiomeMapTest {

    /** All six, as generation.duke lists them, with the three or four regions and the fifteen percent it asks. */
    private static final Biomes SIX =
            BiomeFile.listing("Forest", "Autumn", "PineWood", "DeadLand", "Dungeon", "Mine").biomes();
    private static final Biomes TWO = BiomeFile.listing("Forest", "Dungeon").biomes();
    private static final int WIDTH = 96;
    private static final int HEIGHT = 72;
    private static final List<Room> ROOMS = List.of(new Room(8, 8, 9, 9), new Room(40, 30, 11, 9),
            new Room(70, 50, 13, 11));
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static BiomeMap draw(long seed, int depth, Biomes biomes) {
        return BiomeMap.draw(seed, depth, WIDTH, HEIGHT, ROOMS, biomes);
    }

    private static Biomes asking(Biomes biomes, Count count, int share) {
        return new Biomes(biomes.all(), count, share, biomes.size(), biomes.perDepth());
    }

    private static Biomes drifting(Biomes biomes, Theme.Climate perDepth) {
        return new Biomes(biomes.all(), biomes.count(), biomes.minShare(), biomes.size(), perDepth);
    }

    @Test
    void aSeedGrowsOneMap() {
        assertArrayEquals(cellsOf(draw(7L, 1, TWO)), cellsOf(draw(7L, 1, TWO)));
        assertEquals(draw(7L, 1, SIX), draw(7L, 1, SIX));
        assertNotEquals(draw(7L, 1, SIX), draw(8L, 1, SIX), "two seeds, one landscape");
    }

    /** A chamber is one place: the biome at its middle, over the whole of its footprint. */
    @Test
    void everyChamberIsOneBiomeThroughout() {
        for (long seed = 1; seed <= 40; seed++) {
            assertChambersWhole(seed, draw(seed, 1, TWO), ROOMS);
        }
    }

    /**
     * Crowded as a real floor is, chambers stand across borders, and a region cut off or too small is joined to
     * another whole: a chamber goes with its piece, and is never cut in two.
     */
    @Test
    void aCrowdOfChambersIsWholeToo() {
        var crowd = crowd(WIDTH, HEIGHT, 20);
        for (long seed = 1; seed <= 60; seed++) {
            var map = BiomeMap.draw(seed, 1 + (int) (seed % 4), WIDTH, HEIGHT, crowd, SIX);
            assertChambersWhole(seed, map, crowd);
            assertFewLargeRegions("seed " + seed, map, WIDTH, HEIGHT, SIX);
        }
    }

    /**
     * A keep is built once the floor is carved, wherever there is rock for it, and may stand across a border. It is
     * whole in the biome under its middle, as a chamber is, and whatever it cuts off the far side of another region
     * goes to a region it borders: every biome is still one piece.
     */
    @Test
    void aKeepBuiltAcrossABorderIsWholeAndLeavesEveryRegionOnePiece() {
        for (long seed = 1; seed <= 40; seed++) {
            var floor = BiomeMap.draw(seed, 1, WIDTH, HEIGHT, List.of(), SIX);
            for (int y = 3; y + 15 <= HEIGHT; y += 7) {
                for (int x = 3; x + 15 <= WIDTH; x += 7) {
                    var keep = new Room(x, y, 15, 15);
                    var built = floor.withRoom(keep);

                    for (int cy = keep.y(); cy < keep.y() + keep.h(); cy++) {
                        for (int cx = keep.x(); cx < keep.x() + keep.w(); cx++) {
                            assertEquals(floor.at(keep.centerCellX(), keep.centerCellY()), built.at(cx, cy),
                                    "seed " + seed + ", keep at " + x + "," + y);
                        }
                    }
                    assertEquals(built.at(keep.centerCellX(), keep.centerCellY()), built.ofRoom(0));
                    var pieces = pieces(built, WIDTH, HEIGHT);
                    assertEquals(pieces.stream().map(Piece::biome).distinct().count(), pieces.size(),
                            "seed " + seed + ", keep at " + x + "," + y + " cut a region in two: " + pieces);
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

    /**
     * A few places, not a quilt: three or four regions as the file says — both drawn, from the floor's seed — each
     * one piece and none under the share, and no biome grown twice while there are biomes enough.
     */
    @Test
    void aFloorIsAFewLargeRegions() {
        var crowd = crowd(WIDTH, HEIGHT, 20);
        var counts = new java.util.TreeSet<Integer>();
        for (long seed = 1; seed <= 60; seed++) {
            var map = BiomeMap.draw(seed, 1 + (int) (seed % 4), WIDTH, HEIGHT, crowd, SIX);
            counts.add(assertFewLargeRegions("seed " + seed, map, WIDTH, HEIGHT, SIX));
        }
        assertTrue(counts.contains(SIX.count().fewest()) && counts.contains(SIX.count().most()),
                "the regions drawn on 60 floors came to " + counts + ", not both the fewest and the most");
    }

    /**
     * Hearts far apart and clear of the edges make regions that need little joining: a floor comes out in fewer
     * places than the fewest only where a merge was needed, which is the exception. Hearts set down anywhere leave
     * one floor in ten with a region too small to keep.
     */
    @Test
    void fewerThanTheFewestIsTheExceptionNotTheRule() {
        int atLeastTheFewest = 0;
        for (long seed = 1; seed <= 300; seed++) {
            if (pieces(BiomeMap.draw(seed, 1, WIDTH, HEIGHT, List.of(), SIX), WIDTH, HEIGHT).size()
                    >= SIX.count().fewest()) {
                atLeastTheFewest++;
            }
        }
        assertTrue(atLeastTheFewest >= 285, "only " + atLeastTheFewest + " of 300 floors kept the fewest regions");
    }

    /** The same on a map ten times as large: the regions grow with it, and there are no more of them. */
    @Test
    void aMapThreeHundredAcrossIsTheSameFewPlacesLarger() {
        var crowd = crowd(300, 300, 40);
        int atLeastTheFewest = 0;
        for (long seed = 1; seed <= 8; seed++) {
            var map = BiomeMap.draw(seed, 1, 300, 300, crowd, SIX);
            if (assertFewLargeRegions("seed " + seed, map, 300, 300, SIX) >= SIX.count().fewest()) {
                atLeastTheFewest++;
            }
        }
        assertTrue(atLeastTheFewest >= 6, "only " + atLeastTheFewest + " of 8 maps kept the fewest regions");
    }

    /** And on a world a hundred times the floor: drawn once, because it is the slow one. */
    @Test
    void aWorldNineHundredAcrossIsTheSameFewPlacesLarger() {
        var crowd = crowd(900, 900, 60);
        var map = BiomeMap.draw(1L, 1, 900, 900, crowd, SIX);

        int regions = assertFewLargeRegions("the world", map, 900, 900, SIX);
        assertTrue(regions >= SIX.count().fewest(), "only " + regions + " regions");
        assertChambersWhole(1L, map, crowd);
    }

    /** More biomes asked for than the file lists is fine: the biome the largest regions leave is given again. */
    @Test
    void whenRegionsOutnumberBiomesEveryBiomeIsStillGrown() {
        var many = asking(TWO, new Count(4, 4), 10);
        for (long seed = 1; seed <= 30; seed++) {
            assertEquals(2, biomesOn(draw(seed, 1, many)), "seed " + seed);
        }
    }

    /** The share bounds the count: at forty percent the floor holds two regions at the most, whatever was drawn. */
    @Test
    void aShareThatFitsFewerRegionsLowersTheCountDrawn() {
        var crowded = asking(SIX, new Count(5, 6), 40);
        for (long seed = 1; seed <= 30; seed++) {
            var pieces = pieces(draw(seed, 1, crowded), WIDTH, HEIGHT);
            assertTrue(pieces.size() <= 2, "seed " + seed + " grew " + pieces);
        }
    }

    /** A count of one is a floor of one place. */
    @Test
    void aCountOfOneIsOneRegion() {
        var one = asking(SIX, new Count(1, 1), 15);
        for (long seed = 1; seed <= 10; seed++) {
            assertEquals(1, pieces(draw(seed, 1, one), WIDTH, HEIGHT).size(), "seed " + seed);
        }
    }

    /**
     * The largest region chooses first. With the weather pushed to the barren corner (and then the lush one) the
     * nearest biome is the cave (and then the wood) whichever region asks, so the one that gets it is the larger.
     */
    @Test
    void theLargestRegionChoosesItsBiomeFirst() {
        var two = asking(TWO, new Count(2, 2), 10);
        for (long seed = 1; seed <= 30; seed++) {
            var barren = draw(seed, 2, drifting(two, new Theme.Climate(-100, -100)));
            assertTrue(cellsOf(barren, "Dungeon") >= cellsOf(barren, "Forest"), "seed " + seed + ": the barren corner");
            var lush = draw(seed, 2, drifting(two, new Theme.Climate(100, 100)));
            assertTrue(cellsOf(lush, "Forest") >= cellsOf(lush, "Dungeon"), "seed " + seed + ": the lush corner");
        }
    }

    /** The drift the map asks for moves the floor toward the biomes it points at, the deeper the more. */
    @Test
    void depthDriftsTheClimate() {
        var drifting = drifting(TWO, new Theme.Climate(-10, -10));
        int shallow = 0;
        int deep = 0;
        for (long seed = 1; seed <= 30; seed++) {
            shallow += cellsOf(draw(seed, 1, drifting), "Dungeon");
            deep += cellsOf(draw(seed, 6, drifting), "Dungeon");
        }
        assertTrue(deep > shallow, "caves (the less wild, the less alive) should gain with depth: "
                + shallow + " cells at the top, " + deep + " five floors down");
    }

    /** As shipped, with all six: seven floors down the places are the worked and barren ones. */
    @Test
    void aDeepFloorFavoursTheDeepBiomes() {
        int shallow = 0;
        int deep = 0;
        for (long seed = 1; seed <= 30; seed++) {
            for (var biome : List.of("Dungeon", "Mine", "DeadLand")) {
                shallow += cellsOf(draw(seed, 1, SIX), biome);
                deep += cellsOf(draw(seed, 8, SIX), biome);
            }
        }
        assertTrue(deep > shallow * 3 / 2, "caves, mines and dead land: " + shallow + " cells at the top, " + deep
                + " seven floors down");
    }

    /** A piece cut off from its region goes whole to the region it borders most: here three edges to one's one. */
    @Test
    void aPieceCutOffGoesToTheRegionItBordersMost() {
        assertEquals(List.of(
                "0000011111",
                "0000011111",
                "0000011111",
                "2222222222",
                "2222222222"), joined(1,
                "0000011111",
                "0000211111",
                "0000011111",
                "2222222222",
                "2222222222"));
    }

    /** And where it borders two alike, the lower: the piece is between a 0 and a 1 twice over. */
    @Test
    void aTieGoesToTheLowerRegion() {
        assertEquals(List.of(
                "0000001111",
                "0000001111",
                "0000111111",
                "2222222222",
                "2222222222"), joined(1,
                "0000001111",
                "0000021111",
                "0000111111",
                "2222222222",
                "2222222222"));
    }

    /** A region under the share goes whole, and to the region it borders most: here four edges to three. */
    @Test
    void aRegionUnderTheShareGoesWholeToTheRegionItBordersMost() {
        assertEquals(List.of(
                "00000000001111111111",
                "00000000001111111111",
                "00000000001111111111",
                "00000000000111111111",
                "00000000000111111111"), joined(10,
                "00000000001111111111",
                "00000000001111111111",
                "00000000001111111111",
                "00000000222111111111",
                "00000000222111111111"));
    }

    /** A region at the share stays: four cells of a hundred are four percent, and not under it. */
    @Test
    void aRegionAtTheShareStays() {
        var rows = new String[] {
                "00000000001111111111",
                "00000000001111111111",
                "00000000001111111111",
                "00000000221111111111",
                "00000000221111111111"};
        assertEquals(List.of(rows), joined(4, rows));
        assertEquals(List.of("0000", "0000"), joined(50, "0000", "0000"), "and a floor of one region is left alone");
    }

    /** Several pieces cut off are each joined whole, the smallest first, and none is left. */
    @Test
    void everyPieceCutOffIsJoinedInTurn() {
        assertEquals(List.of(
                "000111",
                "000111",
                "000111",
                "222222"), joined(1,
                "000111",
                "020121",
                "000111",
                "222222"));
    }

    /** A map of regions drawn in digits, row by row, once joined: the digits that are left. */
    private static List<String> joined(int minShare, String... rows) {
        int width = rows[0].length();
        var region = new int[width * rows.length];
        int count = 0;
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < width; x++) {
                region[y * width + x] = rows[y].charAt(x) - '0';
                count = Math.max(count, region[y * width + x] + 1);
            }
        }
        BiomeMap.join(region, width, count, minShare);
        var joined = new ArrayList<String>();
        for (int y = 0; y < rows.length; y++) {
            var row = new StringBuilder();
            for (int x = 0; x < width; x++) {
                row.append(region[y * width + x]);
            }
            joined.add(row.toString());
        }
        return joined;
    }

    /** Chambers all over a map as a real floor has them, none touching another: one every {@code spacing} cells. */
    private static List<Room> crowd(int width, int height, int spacing) {
        var rooms = new ArrayList<Room>();
        for (int y = spacing / 4; y + 11 < height; y += spacing) {
            for (int x = spacing / 4; x + 13 < width; x += spacing) {
                rooms.add(new Room(x, y, 9 + (x + y) % 5, 7 + (x + 3 * y) % 5));
            }
        }
        return rooms;
    }

    private static void assertChambersWhole(long seed, BiomeMap map, List<Room> rooms) {
        for (int i = 0; i < rooms.size(); i++) {
            var room = rooms.get(i);
            for (int y = room.y(); y < room.y() + room.h(); y++) {
                for (int x = room.x(); x < room.x() + room.w(); x++) {
                    assertEquals(map.ofRoom(i), map.at(x, y), "seed " + seed + ", chamber " + i);
                }
            }
        }
    }

    /**
     * What a map is asked to come out as whatever its size: at most the most regions, each in one piece, none
     * under the share of the cells, and no biome grown twice (there are more biomes than regions here). Returns how
     * many regions there are.
     */
    private static int assertFewLargeRegions(String where, BiomeMap map, int width, int height, Biomes settings) {
        var pieces = pieces(map, width, height);
        assertTrue(pieces.size() <= settings.count().most(), where + " grew " + pieces.size() + " regions: " + pieces);
        assertEquals(pieces.stream().map(Piece::biome).distinct().count(), pieces.size(),
                where + " grew a biome in two pieces: " + pieces);
        for (var piece : pieces) {
            assertTrue(piece.cells() * 100L >= (long) width * height * settings.minShare(),
                    where + " has a region of only " + piece.cells() + " cells: " + pieces);
        }
        return pieces.size();
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

    /** One connected run of a single biome, which is what a region is to the eye. */
    private record Piece(int biome, int cells) {
    }

    /** Every four-connected piece of one biome on a map {@code width} by {@code height}. */
    private static List<Piece> pieces(BiomeMap map, int width, int height) {
        var seen = new boolean[width * height];
        var queue = new int[width * height];
        var pieces = new ArrayList<Piece>();
        for (int start = 0; start < seen.length; start++) {
            if (seen[start]) {
                continue;
            }
            int biome = map.indexAt(start % width, start / width);
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            while (head < tail) {
                int at = queue[head++];
                for (int[] step : STEPS) {
                    int x = at % width + step[0];
                    int y = at / width + step[1];
                    if (x >= 0 && y >= 0 && x < width && y < height && !seen[y * width + x]
                            && map.indexAt(x, y) == biome) {
                        seen[y * width + x] = true;
                        queue[tail++] = y * width + x;
                    }
                }
            }
            pieces.add(new Piece(biome, tail));
        }
        return pieces;
    }
}
