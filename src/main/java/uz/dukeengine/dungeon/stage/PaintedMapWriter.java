package uz.dukeengine.dungeon.stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Monster;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.tools.GroundPictures;

/**
 * The painted test map, written from a seed: open country rather than a dungeon, so the painted ground the engine
 * draws (blended surfaces, cliffs by slope) can be seen on screen. Rolling hills, a steep ridge across the middle
 * whose faces are cliffs, a snowy upland in the north where the boss waits, sandy lowland in the south where the
 * hero comes in, dirt paths crossing the grass, and rock where the ground is steep or breaks through.
 *
 * <p>Never by hand and never over another map: it writes {@code maps/painted} and nothing else.
 *
 * <pre>./gradlew writePaintedMap</pre>
 */
public final class PaintedMapWriter {

    static final Path FOLDER = MapWriter.FOLDER.resolve("painted");
    static final String NAME = "painted";
    static final int SIZE = 128;
    static final long SEED = 20261008L;

    /** The surfaces, in the order the blend counts them. */
    static final Map<String, String> PALETTE = palette();
    static final Map<String, Float> COVERAGE = Map.of("g", 6f, "d", 4f, "s", 6f, "n", 6f, "r", 4f);

    private static Map<String, String> palette() {
        var palette = new LinkedHashMap<String, String>();
        palette.put("g", "ground/grass.png");
        palette.put("d", "ground/dirt.png");
        palette.put("s", "ground/sand.png");
        palette.put("n", "ground/snow.png");
        palette.put("r", "ground/rock.png");
        return palette;
    }

    // Where things are, in cells: the hero comes in on the southern sand, the boss waits on the northern snow, and
    // the ridge between them is walked round at either end.
    private static final int[] ENTRANCE = {64, 116};
    private static final int[] BOSS = {64, 14};
    private static final int[][] RIDGE = {{30, 66}, {98, 52}};
    private static final int[][][] PATHS = {
            {{64, 116}, {40, 96}, {20, 74}, {30, 44}, {50, 26}, {64, 14}},
            {{4, 88}, {44, 90}, {84, 84}, {124, 92}},
            {{84, 84}, {108, 66}, {100, 36}, {64, 14}},
    };
    private static final Object[][] MONSTERS = {
            {"Runner", 30, 88}, {"Skeleton", 34, 92}, {"Skeleton", 20, 70}, {"Runner", 24, 62},
            {"Skeleton", 86, 82}, {"Skeleton", 90, 86}, {"Runner", 108, 64}, {"Skeleton", 104, 44},
            {"Skeleton", 30, 42}, {"Runner", 46, 28}, {"Skeleton", 58, 20}, {"Skeleton", 72, 20},
    };

    private PaintedMapWriter() {
    }

    public static void main(String[] args) {
        var stage = stage(DungeonSettings.load());
        var path = FOLDER.resolve(NAME + uz.dukeengine.core.map.MapPackage.SUFFIX);
        try {
            Files.createDirectories(FOLDER);
            Files.writeString(path, StageFile.write(stage), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + path, e);
        }
        MapPicture.write(stage, FOLDER);
        System.out.println("wrote " + path.toAbsolutePath());
    }

    /** The map, checked as the game checks a stage: one its checks refuse is never written. */
    static Stage stage(DungeonSettings settings) {
        var relief = relief();
        var levels = new ArrayList<String>();
        var walls = new ArrayList<String>();
        for (int y = 0; y < SIZE; y++) {
            levels.add("0".repeat(SIZE));
            walls.add(".".repeat(SIZE));
        }
        var taken = new java.util.HashSet<Long>();
        var hero = free(relief, taken, ENTRANCE[0], ENTRANCE[1]);
        var boss = new Monster("Warden", free(relief, taken, BOSS[0], BOSS[1]));
        var monsters = new ArrayList<Monster>();
        for (var monster : MONSTERS) {
            monsters.add(new Monster((String) monster[0], free(relief, taken, (int) monster[1], (int) monster[2])));
        }
        // A chamber round each place the paths meet, so the game's checks walk to each of them.
        var rooms = new ArrayList<Room>();
        var storeys = new ArrayList<Integer>();
        for (var at : List.of(hero, monsters.get(2).at(), monsters.get(4).at(), monsters.get(8).at(), boss.at())) {
            rooms.add(new Room(at.cellX() - 2, at.cellY() - 2, 5, 5));
            storeys.add(0);
        }
        var links = List.of(new Link(0, 1), new Link(0, 2), new Link(1, 3), new Link(3, 4), new Link(2, 4));
        var floor = new GeneratedDungeon(String.join("\n", walls) + "\n", String.join("\n", levels) + "\n", hero,
                monsters, boss, rooms.size() - 1, rooms, links, storeys, List.of(), relief, 0f);
        var stage = new Stage(NAME, "Sinov xaritasi",
                "Bo'yalgan yer sinovi: tepaliklar, qoya, qor, qum va yo'llar.", 1, 1, SEED, floor,
                new StagePaint(NAME, paint(relief), PALETTE, COVERAGE));
        var problems = StageCheck.problems(stage, settings);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(NAME + " is broken: " + problems);
        }
        return stage;
    }

    /** Every corner's height, in sixteenths of a cell. */
    static HeightMap relief() {
        int corners = SIZE + 1;
        var steps = new int[corners * corners];
        for (int y = 0; y < corners; y++) {
            for (int x = 0; x < corners; x++) {
                double u = (double) x / SIZE;
                double v = (double) y / SIZE;
                double rolling = 110 * GroundPictures.fbm(u, v, 4, 4, SEED);
                double upland = 80 * smooth((40 - y) / 26.0);
                double lowland = -16 * smooth((y - 92) / 20.0);
                steps[y * corners + x] = (int) Math.max(0, Math.round(rolling + upland + lowland + ridge(x, y)));
            }
        }
        return new HeightMap(corners, corners, steps);
    }

    /** The ridge: a wall of earth 72 steps high across the middle, its faces steeper than anything can climb. */
    private static double ridge(double x, double y) {
        double away = distance(x, y, RIDGE[0], RIDGE[1]);
        return 72 * Math.max(0, 1 - away / 3.5);
    }

    /** One character a cell: rock where it is steep or breaks through, snow high, sand low, dirt on the paths. */
    static List<String> paint(HeightMap relief) {
        var rows = new ArrayList<String>();
        for (int y = 0; y < SIZE; y++) {
            var row = new StringBuilder();
            for (int x = 0; x < SIZE; x++) {
                row.append(surfaceAt(relief, x, y));
            }
            rows.add(row.toString());
        }
        return rows;
    }

    private static char surfaceAt(HeightMap relief, int x, int y) {
        int a = relief.at(x, y);
        int b = relief.at(x + 1, y);
        int c = relief.at(x, y + 1);
        int d = relief.at(x + 1, y + 1);
        int lean = Math.max(Math.max(a, b), Math.max(c, d)) - Math.min(Math.min(a, b), Math.min(c, d));
        double u = (x + 0.5) / SIZE;
        double v = (y + 0.5) / SIZE;
        double jitter = GroundPictures.fbm(u, v, 16, 2, SEED + 1) - 0.5;
        if (lean >= 12 || distance(x + 0.5, y + 0.5, RIDGE[0], RIDGE[1]) < 3.5) {
            return 'r';
        }
        if (onAPath(x + 0.5, y + 0.5, 1.2 + 1.6 * jitter)) {
            return 'd';
        }
        if (GroundPictures.fbm(u, v, 8, 3, SEED + 2) > 0.72) {
            return 'r';
        }
        if (smooth((40 - y) / 26.0) + jitter > 0.7) {
            return 'n';
        }
        if (smooth((y - 92) / 20.0) + jitter > 0.5) {
            return 's';
        }
        return 'g';
    }

    private static boolean onAPath(double x, double y, double width) {
        for (var path : PATHS) {
            for (int i = 1; i < path.length; i++) {
                if (distance(x, y, path[i - 1], path[i]) < width) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The nearest cell to ({@code x}, {@code y}) that can be stood on and is not yet taken. */
    private static Placement free(HeightMap relief, java.util.Set<Long> taken, int x, int y) {
        for (int reach = 0; reach < SIZE; reach++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int cx = x + dx;
                    int cy = y + dy;
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != reach || cx < 1 || cy < 1 || cx >= SIZE - 1
                            || cy >= SIZE - 1 || relief.isCliff(cx, cy) || !taken.add((long) cy * SIZE + cx)) {
                        continue;
                    }
                    return Placement.atCell(cx, cy);
                }
            }
        }
        throw new IllegalStateException("nowhere to stand near " + x + "," + y);
    }

    private static double distance(double x, double y, int[] from, int[] to) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double t = Math.clamp(((x - from[0]) * dx + (y - from[1]) * dy) / (dx * dx + dy * dy), 0, 1);
        return Math.hypot(x - from[0] - t * dx, y - from[1] - t * dy);
    }

    private static double smooth(double t) {
        double c = Math.clamp(t, 0, 1);
        return c * c * (3 - 2 * c);
    }
}
