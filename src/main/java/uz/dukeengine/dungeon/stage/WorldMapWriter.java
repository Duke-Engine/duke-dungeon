package uz.dukeengine.dungeon.stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DeterministicRng;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Monster;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Piece;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Prop;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.map.StaticMap.Zone;
import uz.dukeengine.dungeon.story.MapStory;
import uz.dukeengine.dungeon.tools.GroundPictures;

/**
 * The campaign's open world, written from a seed: the starting point the Map tab then sculpts by hand.
 *
 * <p>Five large regions laid as the biome generator lays a floor's — hearts, every cell the nearest after two broad
 * fields of noise push it about — but with the hearts set where the story wants them: a green valley in the south-west
 * where the kingdom's city stands by the southern edge, dry steppe in the south-east, a dark forest in the west whose
 * marshy edge runs down to a low basin, rocky highlands in the middle whose terraces break into cliffs, and snowy
 * mountains in the north-east corner. The queen's castle stands on a flattened height between the highlands and the
 * snow; the creature's cave is deep in the forest. Dirt roads are found over the ground — round the cliffs, preferring
 * the gentle way — from the city to every site, so each is walked to by construction. Monster camps stand in the three
 * lands to free and on the way to the cave and the castle; their levels rise with the walk from the city, as every
 * stage's do. Trees in the forest and in groves on the meadow, rocks in the highlands, a little of each elsewhere.
 *
 * <p>No water: the engine draws water from a map's areas ({@code Zoned}, {@code MapArea.water}), and a map file has
 * no areas yet, so the lake is a low basin of mud and marsh. Giving {@code StaticMap} its areas is the game's next step.
 *
 * <p>Never by hand and never over another map: it writes {@code maps/world} and nothing else.
 *
 * <pre>./gradlew writeWorldMap               # its own seed
 * ./gradlew writeWorldMap --args="42"     # another seed</pre>
 */
public final class WorldMapWriter {

    static final Path FOLDER = MapWriter.FOLDER.resolve("world");
    static final String NAME = "world";
    static final int SIZE = 512;
    static final long SEED = 20261008L;

    /** The regions, in the order their hearts are listed. */
    enum Biome { MEADOW, STEPPE, FOREST, HIGHLANDS, SNOW }

    /** Where each region's heart is, as a share of the map across and down. */
    private static final double[][] HEARTS = {{0.24, 0.72}, {0.71, 0.77}, {0.11, 0.25}, {0.46, 0.34}, {0.81, 0.20}};

    /** A named place: where it is as a share of the map, how far round it the ground is flattened, in cells. */
    record Site(String zone, double u, double v, int radius) {

        int x() {
            return (int) Math.round(u * SIZE);
        }

        int y() {
            return (int) Math.round(v * SIZE);
        }
    }

    // In the order of the rooms: the city first, where the hero comes in, and the castle last, where the boss waits.
    static final List<Site> SITES = List.of(
            new Site("Poytaxt", 0.22, 0.90, 14),
            new Site("Yashil_dala", 0.40, 0.62, 16),
            new Site("Qumli_dasht", 0.76, 0.80, 16),
            new Site("Toshli_adir", 0.54, 0.42, 14),
            new Site("Maxluq_g'ori", 0.09, 0.17, 6),
            new Site("Malika_qasri", 0.64, 0.19, 12));
    private static final int CITY = 0;
    private static final int CASTLE = SITES.size() - 1;

    /** The roads, by the sites they join; each also a corridor between their rooms. */
    private static final int[][] ROADS = {{0, 1}, {0, 2}, {1, 3}, {1, 4}, {3, 5}, {2, 5}, {3, 4}};

    /** The low basin where a lake would be, as a share of the map, and its reach in cells. */
    private static final double[] BASIN = {0.22, 0.50};
    private static final int BASIN_REACH = 24;

    /** How far a road may rise or fall in one cell, in sixteenths of a cell: half what a cliff is. */
    private static final int GRADE = 8;

    /** The forest itself, named so a quest can send a hero into it. */
    private static final String FOREST_ZONE = "Qorong'i_o'rmon";

    static final Map<String, String> PALETTE = palette();
    static final Map<String, Float> COVERAGE = Map.of("g", 6f, "d", 4f, "s", 6f, "n", 6f, "r", 4f, "f", 6f,
            "m", 5f);

    private static Map<String, String> palette() {
        var palette = new LinkedHashMap<String, String>();
        palette.put("g", "ground/grass.png");
        palette.put("d", "ground/dirt.png");
        palette.put("s", "ground/sand.png");
        palette.put("n", "ground/snow.png");
        palette.put("r", "ground/rock.png");
        palette.put("f", "ground/forest.png");
        palette.put("m", "ground/mud.png");
        return palette;
    }

    /** The camps: which site's ground, then the kinds standing in it. */
    private static final Object[][] CAMPS = {
            {1, new String[] {"Skeleton", "Skeleton", "Runner", "Skeleton", "Runner", "Brute"}},
            {2, new String[] {"Runner", "Runner", "Stalker", "Runner", "Stalker", "Brute"}},
            {3, new String[] {"Skeleton", "SkeletonMage", "Skeleton", "SkeletonMage", "Revenant", "Brute"}},
            {4, new String[] {"Stalker", "Stalker", "Runner", "Stalker", "Necromancer"}},
            {5, new String[] {"Revenant", "SkeletonMage", "SkeletonHealer", "Revenant", "Reaper", "Champion"}},
    };

    /** Small groups about each region, beside the camps: the kinds, by region. */
    private static final String[][] ROAMERS = {
            {"Skeleton", "Runner"}, {"Runner", "Stalker"}, {"Stalker", "Skeleton"},
            {"SkeletonMage", "Skeleton"}, {"Revenant", "Skeleton"},
    };
    private static final int ROAMING_GROUPS = 4;

    private WorldMapWriter() {
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : SEED;
        long started = System.nanoTime();
        var stage = stage(DungeonSettings.load(), seed);
        var path = FOLDER.resolve(NAME + uz.dukeengine.core.map.MapPackage.SUFFIX);
        try {
            Files.createDirectories(FOLDER);
            Files.writeString(path, StageFile.write(stage), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + path, e);
        }
        MapPicture.write(stage, FOLDER);
        System.out.printf("wrote %s (seed %d) in %d ms%n", path.toAbsolutePath(), seed,
                (System.nanoTime() - started) / 1_000_000);
    }

    /** The world of its own seed. */
    static Stage stage(DungeonSettings settings) {
        return stage(settings, SEED);
    }

    /** The world of {@code seed}, checked as the game checks a stage: one its checks refuse is never written. */
    static Stage stage(DungeonSettings settings, long seed) {
        var world = new World(seed);
        var stage = world.stage();
        var problems = StageCheck.problems(stage, settings);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(NAME + " is broken: " + problems);
        }
        return stage;
    }

    /** The region every cell is in, row by row: the share each region keeps is read off it. */
    static Biome[] regions(long seed) {
        return new World(seed).region;
    }

    /** The cells the roads run over, row by row. */
    static boolean[] roads(long seed) {
        return new World(seed).road;
    }

    /** One world being drawn: every field of it, in the order it is drawn. */
    private static final class World {

        private final long seed;
        private final Biome[] region = new Biome[SIZE * SIZE];
        private final int[] steps;
        private final boolean[] road = new boolean[SIZE * SIZE];
        private final boolean[] kept = new boolean[SIZE * SIZE];
        private final boolean[] walked = new boolean[SIZE * SIZE];
        private HeightMap relief;

        World(long seed) {
            this.seed = seed;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    region[y * SIZE + x] = Biome.values()[nearest(x + 0.5, y + 0.5)];
                }
            }
            steps = ground();
            flattenSites();
            relief = new HeightMap(SIZE + 1, SIZE + 1, steps);
            for (var join : ROADS) {
                var path = route(SITES.get(join[0]), SITES.get(join[1]));
                grade(path);
                for (var cell : path) {
                    road[cell[1] * SIZE + cell[0]] = true;
                }
            }
            smoothRoads();
        }

        /**
         * Where two roads meet, or one folds back beside itself, a cell of one can be left leaning as far as a cliff.
         * Each such cell's corners are laid to their average, again until no cell of any road is a cliff: each pass
         * only narrows what a cell's corners span, so it settles in a few.
         */
        private void smoothRoads() {
            int corners = SIZE + 1;
            for (int pass = 0; pass < 64; pass++) {
                boolean steep = false;
                for (int at = 0; at < road.length; at++) {
                    int x = at % SIZE;
                    int y = at / SIZE;
                    if (!road[at] || lean(x, y) < HeightMap.CLIFF_STEPS - 4) {
                        continue;
                    }
                    steep = true;
                    int mean = (steps[y * corners + x] + steps[y * corners + x + 1] + steps[(y + 1) * corners + x]
                            + steps[(y + 1) * corners + x + 1]) / 4;
                    for (int dy = 0; dy <= 1; dy++) {
                        for (int dx = 0; dx <= 1; dx++) {
                            int corner = (y + dy) * corners + x + dx;
                            steps[corner] = (steps[corner] + mean) / 2;
                        }
                    }
                }
                if (!steep) {
                    relief = new HeightMap(corners, corners, steps);
                    return;
                }
            }
            throw new IllegalStateException("a road is still too steep to walk after smoothing");
        }

        /**
         * A road cut and filled to a grade a hero walks: along it the ground rises no more than {@link #GRADE} steps
         * a cell -- halfway between the highest and the lowest ground that grade allows -- and each cell's corners
         * are laid level across the road, so no cell of it is a cliff, whatever it crossed. The ground beside it is
         * eased half way down or up to it: a cutting through a ridge, an embankment over a hollow.
         */
        private void grade(List<int[]> path) {
            int n = path.size();
            var along = new double[n];
            for (int i = 0; i < n; i++) {
                along[i] = relief.at(path.get(i)[0], path.get(i)[1]);
            }
            var low = along.clone();
            var high = along.clone();
            for (int i = 1; i < n; i++) {
                low[i] = Math.min(low[i], low[i - 1] + GRADE);
                high[i] = Math.max(high[i], high[i - 1] - GRADE);
            }
            for (int i = n - 2; i >= 0; i--) {
                low[i] = Math.min(low[i], low[i + 1] + GRADE);
                high[i] = Math.max(high[i], high[i + 1] - GRADE);
            }
            int corners = SIZE + 1;
            var level = new int[corners * corners];
            Arrays.fill(level, Integer.MIN_VALUE);
            for (int i = 0; i < n; i++) {
                int height = (int) Math.round((low[i] + high[i]) / 2);
                int x = path.get(i)[0];
                int y = path.get(i)[1];
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dx = -1; dx <= 2; dx++) {
                        int cx = x + dx;
                        int cy = y + dy;
                        if (cx < 0 || cy < 0 || cx > SIZE || cy > SIZE || level[cy * corners + cx] != Integer.MIN_VALUE
                                || onARoad(cx, cy)) {
                            continue;
                        }
                        level[cy * corners + cx] = (steps[cy * corners + cx] + height) / 2;
                    }
                }
            }
            for (int at = 0; at < level.length; at++) {
                if (level[at] != Integer.MIN_VALUE) {
                    steps[at] = level[at];
                }
            }
            // The road's own corners last, so a shoulder of one stretch never lifts the road of another; and a road
            // laid before keeps its own, so where two meet the one already walkable stays so.
            for (int i = 0; i < n; i++) {
                int height = (int) Math.round((low[i] + high[i]) / 2);
                int x = path.get(i)[0];
                int y = path.get(i)[1];
                if (road[y * SIZE + x]) {
                    continue;
                }
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dx = 0; dx <= 1; dx++) {
                        steps[(y + dy) * corners + x + dx] = height;
                    }
                }
            }
            relief = new HeightMap(corners, corners, steps);
        }

        /** Whether the corner ({@code cx}, {@code cy}) is a corner of a road already laid. */
        private boolean onARoad(int cx, int cy) {
            for (int y = cy - 1; y <= cy; y++) {
                for (int x = cx - 1; x <= cx; x++) {
                    if (x >= 0 && y >= 0 && x < SIZE && y < SIZE && road[y * SIZE + x]) {
                        return true;
                    }
                }
            }
            return false;
        }

        // ---- the regions ----

        /** The heart nearest to ({@code x}, {@code y}) once two broad fields of noise have pushed it about. */
        private int nearest(double x, double y) {
            var distances = distances(x, y);
            int best = 0;
            for (int i = 1; i < distances.length; i++) {
                if (distances[i] < distances[best]) {
                    best = i;
                }
            }
            return best;
        }

        /** How far ({@code x}, {@code y}), warped, is from each heart, in cells. */
        private double[] distances(double x, double y) {
            double u = x / SIZE;
            double v = y / SIZE;
            double wx = x + 260 * (GroundPictures.fbm(u, v, 2, 3, seed + 101) - 0.5)
                    + 70 * (GroundPictures.fbm(u, v, 7, 2, seed + 103) - 0.5);
            double wy = y + 260 * (GroundPictures.fbm(u, v, 2, 3, seed + 102) - 0.5)
                    + 70 * (GroundPictures.fbm(u, v, 7, 2, seed + 104) - 0.5);
            var distances = new double[HEARTS.length];
            for (int i = 0; i < HEARTS.length; i++) {
                distances[i] = Math.hypot(wx - HEARTS[i][0] * SIZE, wy - HEARTS[i][1] * SIZE);
            }
            return distances;
        }

        // ---- the relief ----

        /** Every corner's height, in sixteenths of a cell: each region's own ground, blended across its borders. */
        private int[] ground() {
            int corners = SIZE + 1;
            var heights = new int[corners * corners];
            for (int y = 0; y < corners; y++) {
                for (int x = 0; x < corners; x++) {
                    var distances = distances(x, y);
                    double nearest = Arrays.stream(distances).min().orElseThrow();
                    double total = 0;
                    double height = 0;
                    for (int i = 0; i < distances.length; i++) {
                        double weight = Math.exp(-(distances[i] - nearest) / 14);
                        total += weight;
                        height += weight * heightOf(Biome.values()[i], x, y);
                    }
                    height /= total;
                    height = basin(x, y, height);
                    height += rim(x, y);
                    heights[y * corners + x] = (int) Math.max(0, Math.round(height));
                }
            }
            return heights;
        }

        private double heightOf(Biome biome, int x, int y) {
            double u = (double) x / SIZE;
            double v = (double) y / SIZE;
            return switch (biome) {
                case MEADOW -> 64 + 70 * GroundPictures.fbm(u, v, 4, 4, seed + 1);
                case STEPPE -> 48 + 50 * GroundPictures.fbm(u, v, 6, 3, seed + 2);
                case FOREST -> 72 + 90 * GroundPictures.fbm(u, v, 5, 4, seed + 3);
                // Terraces: level shelves a few cells high whose risers are steep enough to be cliffs.
                case HIGHLANDS -> terrace(140 + 460 * ridged(u, v, 5, 4, seed + 4), 64);
                case SNOW -> 280 + 440 * ridged(u, v, 3, 5, seed + 5)
                        + 260 * Math.min(1, Math.hypot(1 - u, v) < 0.35 ? 1 - Math.hypot(1 - u, v) / 0.35 : 0);
            };
        }

        /** The low ground where a lake would be: drawn down to a flat floor across its middle. */
        private double basin(int x, int y, double height) {
            double away = Math.hypot(x - BASIN[0] * SIZE, y - BASIN[1] * SIZE);
            double wobble = 30 * (GroundPictures.fbm((double) x / SIZE, (double) y / SIZE, 5, 2, seed + 7) - 0.5);
            return lerp(height, 30, smooth((BASIN_REACH + wobble - away) / 26));
        }

        /** The edge of the world: hills rising to cliffs, so it ends in something rather than in nothing. */
        private double rim(int x, int y) {
            int edge = Math.min(Math.min(x, y), Math.min(SIZE - x, SIZE - y));
            return 360 * smooth((10 - edge) / 10.0);
        }

        /** Every site's ground drawn level to its own average, its edge eased into the land round it. */
        private void flattenSites() {
            int corners = SIZE + 1;
            for (var site : SITES) {
                int ring = 14;
                long sum = 0;
                int count = 0;
                for (int y = site.y() - site.radius(); y <= site.y() + site.radius(); y++) {
                    for (int x = site.x() - site.radius(); x <= site.x() + site.radius(); x++) {
                        if (Math.hypot(x - site.x(), y - site.y()) <= site.radius()) {
                            sum += steps[y * corners + x];
                            count++;
                        }
                    }
                }
                double level = (double) sum / count;
                int reach = site.radius() + ring;
                for (int y = Math.max(0, site.y() - reach); y <= Math.min(SIZE, site.y() + reach); y++) {
                    for (int x = Math.max(0, site.x() - reach); x <= Math.min(SIZE, site.x() + reach); x++) {
                        double t = smooth((reach - Math.hypot(x - site.x(), y - site.y())) / ring);
                        steps[y * corners + x] = (int) Math.round(lerp(steps[y * corners + x], level, t));
                    }
                }
            }
        }

        // ---- the roads ----

        /**
         * The road from one site to another: the cheapest way over the ground, a step costing more the more the
         * ground leans, a little more or less by a broad field of noise so a road wanders as roads do, and less along
         * a road already laid, so roads share their way where they can. Diagonal steps are taken through the cell
         * beside them, so the road stays a way a hero walks four ways from a cell.
         */
        private List<int[]> route(Site from, Site to) {
            int start = from.y() * SIZE + from.x();
            int goal = to.y() * SIZE + to.x();
            var cost = new int[SIZE * SIZE];
            var came = new int[SIZE * SIZE];
            Arrays.fill(cost, Integer.MAX_VALUE);
            cost[start] = 0;
            var open = new PriorityQueue<long[]>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0])
                    : Long.compare(a[1], b[1]));
            open.add(new long[] {0, start});
            int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
            while (!open.isEmpty()) {
                var head = open.poll();
                int here = (int) head[1];
                if (here == goal) {
                    break;
                }
                int hx = here % SIZE;
                int hy = here / SIZE;
                if (head[0] > cost[here] + 4L * Math.max(Math.abs(hx - to.x()), Math.abs(hy - to.y()))) {
                    continue;
                }
                for (var way : ways) {
                    int x = hx + way[0];
                    int y = hy + way[1];
                    if (x < 1 || y < 1 || x >= SIZE - 1 || y >= SIZE - 1) {
                        continue;
                    }
                    int next = y * SIZE + x;
                    int lean = lean(x, y);
                    double wander = 12 * GroundPictures.fbm((x + 0.5) / SIZE, (y + 0.5) / SIZE, 12, 3, seed + 31);
                    // A cliff is crossed only where nothing else will do: the road is cut through it after.
                    double step = (road[next] ? 4 : 6 + wander) + lean * lean / 2.0
                            + (lean >= HeightMap.CLIFF_STEPS ? 400 : 0);
                    int total = cost[here] + (int) Math.round(way[0] != 0 && way[1] != 0 ? step * 1.42 : step);
                    if (total < cost[next]) {
                        cost[next] = total;
                        came[next] = here;
                        open.add(new long[] {total + 4L * Math.max(Math.abs(x - to.x()), Math.abs(y - to.y())), next});
                    }
                }
            }
            if (cost[goal] == Integer.MAX_VALUE) {
                throw new IllegalStateException("no road can be found from " + from.zone() + " to " + to.zone());
            }
            var path = new ArrayList<int[]>();
            for (int at = goal; at != start; at = came[at]) {
                int x = at % SIZE;
                int y = at / SIZE;
                int px = came[at] % SIZE;
                path.add(new int[] {x, y});
                if (px != x && came[at] / SIZE != y) {
                    path.add(new int[] {px, y}); // the cell a diagonal step goes through
                }
            }
            path.add(new int[] {from.x(), from.y()});
            return path.reversed();
        }

        private int lean(int x, int y) {
            int a = steps[y * (SIZE + 1) + x];
            int b = steps[y * (SIZE + 1) + x + 1];
            int c = steps[(y + 1) * (SIZE + 1) + x];
            int d = steps[(y + 1) * (SIZE + 1) + x + 1];
            return Math.max(Math.max(a, b), Math.max(c, d)) - Math.min(Math.min(a, b), Math.min(c, d));
        }

        // ---- the stage ----

        Stage stage() {
            var levels = new ArrayList<String>();
            var walls = new ArrayList<String>();
            for (int y = 0; y < SIZE; y++) {
                levels.add("0".repeat(SIZE));
                walls.add(".".repeat(SIZE));
            }
            var paint = paint();
            var taken = new HashSet<Long>();
            walk(SITES.get(CITY));
            var city = SITES.get(CITY);
            var hero = free(taken, city.x(), city.y());
            var castle = SITES.get(CASTLE);
            var boss = new Monster("Warden", free(taken, castle.x(), castle.y()));
            var rng = new DeterministicRng(seed ^ 0x776F726C64L);
            var monsters = new ArrayList<Monster>();
            for (var camp : CAMPS) {
                var site = SITES.get((int) camp[0]);
                // Short of the site itself on its road from the city, so the camp is met on the way in.
                for (var kind : (String[]) camp[1]) {
                    double angle = rng.nextInt(360) * Math.PI / 180;
                    int reach = site.radius() / 2 + rng.nextInt(site.radius() / 2 + 1);
                    monsters.add(new Monster(kind, free(taken, site.x() + (int) Math.round(reach * Math.cos(angle)),
                            site.y() + (int) Math.round(reach * Math.sin(angle)))));
                }
            }
            for (int b = 0; b < HEARTS.length; b++) {
                for (int g = 0; g < ROAMING_GROUPS; g++) {
                    int x = (int) (HEARTS[b][0] * SIZE) + rng.nextInt(-60, 60);
                    int y = (int) (HEARTS[b][1] * SIZE) + rng.nextInt(-60, 60);
                    if (Math.hypot(x - city.x(), y - city.y()) < 50) {
                        continue; // the city's own ground is kept quiet
                    }
                    for (var kind : ROAMERS[b]) {
                        monsters.add(new Monster(kind, free(taken, x, y)));
                    }
                }
            }
            var props = new ArrayList<Prop>();
            for (int[] corner : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
                props.add(new Prop("Pillar", free(taken, castle.x() + corner[0], castle.y() + corner[1])));
                props.add(new Prop("Pillar", free(taken, city.x() + corner[0] * 2, city.y() + corner[1])));
            }
            props.add(new Prop("Statue", free(taken, castle.x(), castle.y() + 9)));
            for (int c = 0; c < 3; c++) {
                var site = SITES.get((int) CAMPS[c][0]);
                props.add(new Prop("Barrel", free(taken, site.x() + 3, site.y() - 2)));
                props.add(new Prop("Barrel", free(taken, site.x() - 2, site.y() + 3)));
            }
            var rooms = new ArrayList<Room>();
            var storeys = new ArrayList<Integer>();
            for (var site : SITES) {
                rooms.add(new Room(site.x() - 2, site.y() - 2, 5, 5));
                storeys.add(0);
            }
            var links = new ArrayList<Link>();
            for (var join : ROADS) {
                links.add(new Link(join[0], join[1]));
            }
            var zones = new ArrayList<Zone>();
            zones.add(new Zone(city.zone(), city.x() - 12, city.y() - 9, 24, 18, 0));
            for (int i = 1; i < SITES.size(); i++) {
                var site = SITES.get(i);
                zones.add(new Zone(site.zone(), site.x(), site.y(), 0, 0, site.radius()));
            }
            zones.add(new Zone(FOREST_ZONE, (int) (HEARTS[Biome.FOREST.ordinal()][0] * SIZE),
                    (int) (HEARTS[Biome.FOREST.ordinal()][1] * SIZE), 0, 0, 44));
            for (var monster : monsters) {
                keep(monster.at().cellX(), monster.at().cellY(), 1);
            }
            for (var prop : props) {
                keep(prop.at().cellX(), prop.at().cellY(), 1);
            }
            var floor = new GeneratedDungeon(String.join("\n", walls) + "\n", String.join("\n", levels) + "\n", hero,
                    monsters, boss, CASTLE, rooms, links, storeys, props, relief, 0f);
            return new Stage(NAME, "Ochiq dunyo",
                    "Butun qirollik bir xaritada: shahar, qorong'i o'rmon, ozod qilinadigan yerlar va malika qasri.",
                    1, 1, seed, floor, new StagePaint(NAME, paint, PALETTE, COVERAGE, scenery()),
                    new MapStory(List.of(), zones, List.of(), List.of()));
        }


        /** Every cell a hero can walk to from {@code from}, by the engine's rule on ground of one storey: no cliff. */
        private void walk(Site from) {
            var queue = new java.util.ArrayDeque<Integer>();
            walked[from.y() * SIZE + from.x()] = true;
            queue.add(from.y() * SIZE + from.x());
            int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!queue.isEmpty()) {
                int here = queue.poll();
                for (var way : ways) {
                    int x = here % SIZE + way[0];
                    int y = here / SIZE + way[1];
                    if (x >= 0 && y >= 0 && x < SIZE && y < SIZE && !walked[y * SIZE + x] && !relief.isCliff(x, y)) {
                        walked[y * SIZE + x] = true;
                        queue.add(y * SIZE + x);
                    }
                }
            }
        }
        /** No scenery within {@code reach} cells of ({@code x}, {@code y}). */
        private void keep(int x, int y, int reach) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int cx = x + dx;
                    int cy = y + dy;
                    if (cx >= 0 && cy >= 0 && cx < SIZE && cy < SIZE) {
                        kept[cy * SIZE + cx] = true;
                    }
                }
            }
        }

        // ---- the paint ----

        private List<String> paint() {
            var rows = new ArrayList<String>();
            for (int y = 0; y < SIZE; y++) {
                var row = new StringBuilder(SIZE);
                for (int x = 0; x < SIZE; x++) {
                    row.append(surfaceAt(x, y));
                }
                rows.add(row.toString());
            }
            return rows;
        }

        private char surfaceAt(int x, int y) {
            double u = (x + 0.5) / SIZE;
            double v = (y + 0.5) / SIZE;
            int lean = lean(x, y);
            int height = relief.at(x, y);
            if (lean >= 13) {
                return 'r';
            }
            if (nearRoad(x, y, GroundPictures.fbm(u, v, 32, 2, seed + 11) > 0.6 ? 1 : 0)) {
                return 'd';
            }
            var city = SITES.get(CITY);
            if (Math.abs(x - city.x()) < 10 && Math.abs(y - city.y()) < 7) {
                return 'd';
            }
            var castle = SITES.get(CASTLE);
            if (Math.hypot(x - castle.x(), y - castle.y()) < castle.radius() - 2) {
                return 'r';
            }
            double patch = GroundPictures.fbm(u, v, 12, 3, seed + 12);
            double away = Math.hypot(x - BASIN[0] * SIZE, y - BASIN[1] * SIZE);
            if (away < BASIN_REACH - 6 + 8 * (patch - 0.5)) {
                return 'm';
            }
            if (height > 680 || height > 560 && patch > 0.5) {
                return lean >= 9 ? 'r' : 'n';
            }
            return switch (region[y * SIZE + x]) {
                case MEADOW -> GroundPictures.fbm(u, v, 40, 2, seed + 13) > 0.74 ? 'd' : 'g';
                case STEPPE -> patch > 0.7 ? 'd' : patch < 0.28 ? 'g' : 's';
                case FOREST -> patch > 0.7 || away < BASIN_REACH + 16 && patch > 0.55 ? 'm' : 'f';
                case HIGHLANDS -> lean >= 10 || patch > 0.56 ? 'r' : 'g';
                case SNOW -> lean >= 10 ? 'r' : 'n';
            };
        }

        private boolean nearRoad(int x, int y, int reach) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int cx = x + dx;
                    int cy = y + dy;
                    if (cx >= 0 && cy >= 0 && cx < SIZE && cy < SIZE && road[cy * SIZE + cx]) {
                        return true;
                    }
                }
            }
            return false;
        }

        // ---- the scenery ----

        private static final String MODELS = "models/tiles/forest/";

        /** One kind of piece a region scatters: its model, how many in ten thousand cells, its size, its trunk. */
        private record Scatter(String model, int perTenThousand, float scale, float footprint) {
        }

        private static final Scatter[] FOREST_TREES = {
                new Scatter("pine_tall", 900, 1.67f, 0.15f), new Scatter("pine", 900, 1.67f, 0.15f),
                new Scatter("tree", 700, 3.1f, 0.15f), new Scatter("tree_round", 500, 3.1f, 0.15f),
                new Scatter("tree_twisted", 400, 3.1f, 0.15f), new Scatter("bush_wide", 300, 3f, 0f),
                new Scatter("grass_tall", 300, 3f, 0f)};
        private static final Scatter[] GROVE = {
                new Scatter("tree", 1500, 3.1f, 0.15f), new Scatter("tree_round", 1500, 3.1f, 0.15f),
                new Scatter("bush_small", 400, 3f, 0f)};
        private static final Scatter[] MEADOW = {
                new Scatter("grass_short", 500, 3f, 0f), new Scatter("grass_tall", 250, 3f, 0f),
                new Scatter("bush_small", 80, 3f, 0f), new Scatter("tree_round", 40, 3.1f, 0.15f)};
        private static final Scatter[] STEPPE = {
                new Scatter("bush_small", 120, 3f, 0f), new Scatter("tree_stunted", 30, 3.1f, 0.15f),
                new Scatter("pebble", 100, 3f, 0f), new Scatter("rock_cluster_low", 40, 3f, 0.3f)};
        private static final Scatter[] HIGHLANDS = {
                new Scatter("rock_cluster", 200, 3f, 0.3f), new Scatter("stone", 200, 3f, 0.2f),
                new Scatter("pebble", 300, 3f, 0f), new Scatter("pine", 250, 1.67f, 0.15f)};
        private static final Scatter[] SNOW = {
                new Scatter("pine_tall", 200, 1.67f, 0.15f), new Scatter("rock_cluster", 150, 3f, 0.3f),
                new Scatter("stone", 100, 3f, 0.2f)};
        private static final Scatter[] MARSH = {
                new Scatter("grass_tall", 600, 3f, 0f), new Scatter("bush_small", 150, 3f, 0f),
                new Scatter("tree_twisted", 60, 3.1f, 0.15f)};

        /**
         * What stands about, cell by cell in a fixed order: never on a road or a step from one, on a site, by a
         * monster or a prop, or on ground too steep to stand on.
         */
        private List<Piece> scenery() {
            for (var site : SITES) {
                keep(site.x(), site.y(), site.radius() + 2);
            }
            var rng = new DeterministicRng(seed ^ 0x7363656E65L);
            var pieces = new ArrayList<Piece>();
            for (int y = 2; y < SIZE - 2; y++) {
                for (int x = 2; x < SIZE - 2; x++) {
                    if (kept[y * SIZE + x] || nearRoad(x, y, 2) || lean(x, y) >= 12) {
                        continue;
                    }
                    for (var scatter : scattersAt(x, y)) {
                        if (rng.nextInt(10_000) >= scatter.perTenThousand()) {
                            continue;
                        }
                        float across = x + (15 + rng.nextInt(71)) / 100f;
                        float down = y + (15 + rng.nextInt(71)) / 100f;
                        float size = scatter.scale() * (85 + rng.nextInt(31)) / 100f;
                        pieces.add(new Piece(MODELS + scatter.model() + ".gltf", across, down, rng.nextInt(360),
                                Math.round(size * 100) / 100f, 0xFFFFFF, scatter.footprint()));
                        break; // one piece a cell
                    }
                }
            }
            return pieces;
        }

        private Scatter[] scattersAt(int x, int y) {
            double u = (x + 0.5) / SIZE;
            double v = (y + 0.5) / SIZE;
            if (Math.hypot(x - BASIN[0] * SIZE, y - BASIN[1] * SIZE) < BASIN_REACH + 4) {
                return MARSH;
            }
            return switch (region[y * SIZE + x]) {
                case FOREST -> FOREST_TREES;
                case MEADOW -> GroundPictures.fbm(u, v, 14, 3, seed + 21) > 0.66 ? GROVE : MEADOW;
                case STEPPE -> STEPPE;
                case HIGHLANDS -> HIGHLANDS;
                case SNOW -> SNOW;
            };
        }

        /** The nearest cell to ({@code x}, {@code y}) that can be stood on and is not yet taken. */
        private Placement free(Set<Long> taken, int x, int y) {
            for (int reach = 0; reach < SIZE; reach++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int cx = x + dx;
                        int cy = y + dy;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != reach || cx < 12 || cy < 12 || cx >= SIZE - 12
                                || cy >= SIZE - 12 || !walked[cy * SIZE + cx] || lean(cx, cy) >= 10
                                || !taken.add((long) cy * SIZE + cx)) {
                            continue;
                        }
                        return Placement.atCell(cx, cy);
                    }
                }
            }
            throw new IllegalStateException("nowhere to stand near " + x + "," + y);
        }
    }

    // ---- shapes ----

    /** Folds of noise: sharp crests where the noise crosses its middle, as mountain ridges run. */
    private static double ridged(double u, double v, int base, int octaves, long seed) {
        double crest = 1 - Math.abs(2 * GroundPictures.fbm(u, v, base, octaves, seed) - 1);
        return crest * crest;
    }

    /** {@code height} in shelves {@code rise} high, each rising steeply over its last quarter. */
    private static double terrace(double height, double rise) {
        double shelf = Math.floor(height / rise);
        double within = height / rise - shelf;
        return (shelf + smooth((within - 0.75) / 0.25)) * rise;
    }

    private static double smooth(double t) {
        double c = Math.clamp(t, 0, 1);
        return c * c * (3 - 2 * c);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
