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
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.core.pathfind.TraversalLink;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DeterministicRng;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Monster;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Piece;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Prop;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.map.StaticMap;
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
 * snow; Yalmog'iz kampir's hut stands in a clearing deep in the forest. Dirt roads are found over the ground — round the cliffs, preferring
 * the gentle way — from the city to every site, so each is walked to by construction. Monster camps stand in the three
 * lands to free and on the way to the hut and the castle; their levels rise with the walk from the city, as every
 * stage's do. Trees in the forest and in groves on the meadow, rocks in the highlands, a little of each elsewhere.
 *
 * <p>The places are built of the castle kits (see castles): the city in its town wall, its two roads leaving by gates
 * of their own, the royal castle inside; the queen's castle of the fortress drawn dark; a desert fortress; a small fort
 * and a ruined one in the lands to free; a watchtower by the roads. Their walls stand in the way, and wherever a road
 * comes through a wall there is a gate.
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
    /** A KayKit broadleaf's trunk at its scenery scale of 3.1, as a footprint in cells: 0.45 of its own units, measured. */
    static final float TRUNK = 0.14f;

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
            new Site("Poytaxt", 0.22, 0.90, 18),
            new Site("Yashil_dala", 0.40, 0.62, 16),
            new Site("Qumli_dasht", 0.76, 0.80, 16),
            new Site("Toshli_adir", 0.54, 0.42, 14),
            new Site("Kampir_kulbasi", 0.09, 0.17, 9),
            new Site("Malika_qasri", 0.64, 0.19, 12));
    private static final int CITY = 0;
    /** Yalmog'iz kampir's hut, by its place in {@link #SITES}. */
    static final int HUT = 4;
    private static final int CASTLE = SITES.size() - 1;

    /** The roads, by the sites they join; each also a corridor between their rooms. */
    private static final int[][] ROADS = {{0, 1}, {0, 2}, {1, 3}, {1, 4}, {3, 5}, {2, 5}, {3, 4}};

    /** The low basin where a lake would be, as a share of the map, and its reach in cells. */
    private static final double[] BASIN = {0.22, 0.50};
    private static final int BASIN_REACH = 24;

    /** How far a road may rise or fall in one cell, in sixteenths of a cell: half what a cliff is. */
    private static final int GRADE = 8;

    /** How steep a ramp up to a shelf may be: a stair cut in the hillside, still short of a cliff. */
    private static final int RAMP_GRADE = 13;

    /** The lake's surface, in sixteenths of a cell, and its name and the river's. */
    private static final int LAKE = 44;
    private static final String LAKE_NAME = "Ko'l";
    private static final String RIVER_NAME = "Daryo";

    /** Where the river rises, as a share of the map: high in the snow. */
    private static final double[] SPRING = {0.88, 0.34};

    /** The bends it is drawn through on its way to the lake: round the south of the castle, then west. */
    private static final double[][] BENDS = {{0.72, 0.40}, {0.46, 0.27}};

    /** How many cells of ground a piece cut off from the rest may have before it is given a ramp. */
    static final int SHELF = 32;

    /** How many rounds of ramps are laid before what is still cut off is sealed. */
    private static final int RAMP_ROUNDS = 24;

    /** The forest itself, named so a quest can send a hero into it. */
    private static final String FOREST_ZONE = "Qorong'i_o'rmon";

    static final Map<String, String> PALETTE = palette();
    // Photographed ground (Poly Haven, OpenGameArt; CC0): a copy every few cells, about what one picture shows.
    static final Map<String, Float> COVERAGE = Map.of("g", 4f, "d", 3f, "s", 4f, "a", 5f, "n", 5f, "r", 4f, "f", 4f,
            "m", 4f);

    private static Map<String, String> palette() {
        var palette = new LinkedHashMap<String, String>();
        // Eight, the most the ground's shader blends. Each file is named for the sound a foot makes on it.
        palette.put("g", "ground/world/grass.png");   // meadow, and the grass between highland rocks
        palette.put("d", "ground/world/dirt.png");    // the roads (E52 costs read this letter), bare patches
        palette.put("s", "ground/world/steppe.png");  // the steppe's withered grass
        palette.put("a", "ground/world/sand.png");    // the sandy steppe round Qumli dasht
        palette.put("n", "ground/world/snow.png");
        palette.put("r", "ground/world/rock.png");    // highland rock, steep ground
        palette.put("f", "ground/world/forest.png");  // the dark wood's fallen leaves
        palette.put("m", "ground/world/mud.png");     // the marsh, the lake bed, the river's banks
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

    /**
     * Small groups about each region, beside the camps: the kinds, by region. The steppe, the forest and the
     * highlands are walked by their own families' (see {@link Wild}).
     */
    private static final String[][] ROAMERS = {
            {"Skeleton", "Runner"}, {"Cactoro", "Jinn"}, {"Wolf", "Sporeling"},
            {"Goleling", "Shaman"}, {"Revenant", "Skeleton"},
    };

    /**
     * The wilds' camps, one a land (data/units/wilds_*.duke): where to look for its ground, as a share of the map,
     * the region it has to stand in ({@code null}: anywhere round the lake), its boss in the middle, and the family
     * round him -- the common ones, the stronger, the ones that shoot, and an elite.
     */
    enum Wild {
        FOREST(0.17, 0.36, Biome.FOREST, "GoblinChief",
                "Wolf", "Wolf", "Wolf", "Spider", "Spider", "Sporeling", "Sporeling", "BloodWolf"),
        DESERT(0.88, 0.64, Biome.STEPPE, "Dragon",
                "Cactoro", "Cactoro", "Cactoro", "Lizard", "Lizard", "Jinn", "Jinn", "FireLizard"),
        HIGHLANDS(0.38, 0.28, Biome.HIGHLANDS, "GiantKing",
                "Goleling", "Goleling", "Goleling", "Ogre", "Ogre", "Shaman", "Shaman", "StoneGoleling"),
        MARSH(BASIN[0] + 0.08, BASIN[1] - 0.04, null, "BogHorror",
                "Slime", "Slime", "Slime", "Frog", "Frog", "Wisp", "Wisp", "VenomFrog"),
        // The bestiary's (data/units/bestiary_*.duke), after the four so their places are as they were.
        ROADS(0.32, 0.76, Biome.MEADOW, "BanditChief",
                "Bandit", "Bandit", "Bandit", "Brigand", "Brigand", "BanditArcher", "BanditArcher", "BanditVeteran"),
        GROVE(0.07, 0.42, Biome.FOREST, "MushroomKing",
                "Armabee", "Armabee", "Armabee", "Stag", "Stag", "Wasp", "Wasp", "ArmabeeGuard", "GiantArmabee"),
        ORCS(0.50, 0.24, Biome.HIGHLANDS, "Troll",
                "OrcGrunt", "OrcGrunt", "OrcGrunt", "OrcWarrior", "OrcWarrior", "OrcHunter", "OrcHunter",
                "OrcWarlord"),
        CRYPT(0.36, 0.53, Biome.MEADOW, "FallenKing",
                "Zombie", "Zombie", "Zombie", "ZombieGhoul", "ZombieGhoul", "GhostSkull", "GhostSkull", "ZombieBrute",
                "ZombieGiant"),
        COAST(BASIN[0] - 0.07, BASIN[1] + 0.06, null, "Kraken",
                "Crab", "Crab", "Crab", "Sharkman", "Sharkman", "PirateAnne", "PirateAnne", "BloodShark"),
        PEAKS(0.86, 0.28, Biome.SNOW, "Wyvern",
                "YetiCub", "YetiCub", "YetiCub", "Yeti", "Yeti", "Hywirl", "Hywirl", "IceGolem", "FrostYeti"),
        DEMONS(0.74, 0.12, Biome.SNOW, "DemonBrute",
                "Shayton", "Shayton", "Shayton", "BlueDemon", "BlueDemon", "CultMage", "CultMage", "FireGolem",
                "HellDemon"),
        RUINS(0.62, 0.62, Biome.STEPPE, "Minotaur",
                "RuinRat", "RuinRat", "RuinRat", "EarthGolem", "EarthGolem", "CrownedGolem", "CrownedGolem",
                "LightGolem", "ShadowGolem");

        final double u;
        final double v;
        final Biome region;
        final String boss;
        final String[] kinds;

        Wild(double u, double v, Biome region, String boss, String... kinds) {
            this.u = u;
            this.v = v;
            this.region = region;
            this.boss = boss;
            this.kinds = kinds;
        }

        /** The one that is the family's elite: the last. */
        String elite() {
            return kinds[kinds.length - 1];
        }
    }

    /**
     * The bestiary's small groups, walking the land beside its camps: where to look, as a share of the map, how many
     * groups, and the kinds in each. Placed after every camp, so nothing placed before moves.
     */
    static final Object[][] BEASTS = {
            {0.32, 0.62, 3, new String[] {"GreyWolf", "GreyWolf", "GreyWolf"}},
            {0.46, 0.80, 2, new String[] {"Bull"}},
            {0.12, 0.30, 3, new String[] {"Fox", "Fox"}},
            {0.16, 0.45, 2, new String[] {"Stag"}},
            {0.36, 0.53, 3, new String[] {"ZombieCrawler", "SkullCrawler", "Bat", "Bat"}},
            {0.36, 0.53, 2, new String[] {"Witch", "Rat", "Rat"}},
            {0.36, 0.53, 1, new String[] {"CaveSpider", "CaveSpider", "BroodMother"}},
            {0.16, 0.56, 2, new String[] {"PirateMako", "PirateMako", "SkeletonPirate"}},
            {0.16, 0.56, 1, new String[] {"PirateCaptain", "PirateMako"}},
            {0.56, 0.30, 2, new String[] {"OrcGrunt", "OrcGrunt", "OrcHunter"}},
            {0.80, 0.15, 2, new String[] {"YetiCub", "YetiCub"}},
            {0.62, 0.62, 2, new String[] {"RuinRat", "RuinRat", "CrownedGolem"}},
            {0.72, 0.24, 1, new String[] {"Shayton", "Shayton", "CultMage"}},
    };
    /** How far the bestiary's groups keep from the city -- its gate, the opening field and the corridor -- in cells. */
    static final int BEASTS_KEEP_FROM_CITY = 70;

    /** How far a wild camp keeps from a village, a tower, a landmark or a site's edge, in cells. */
    static final int WILD_QUIET = 30;
    private static final int ROAMING_GROUPS = 4;

    /** The villages, one by each road but the forest's, in the order of those roads. */
    static final List<String> VILLAGES = List.of("Olmazor", "Bog'iston", "Qoratepa", "Tolzor", "Yangiobod");

    /** The watchtowers' zones, numbered from 1 in the order of the roads: see data/world/travel.duke. */
    static final String TOWER = "Minora_";
    static final String TEMPLE = "Eski_ibodatxona";
    static final String STONES = "Tosh_doira";
    static final String BATTLEFIELD = "Jang_maydoni";
    /** Act 1's places on the world: the opening battle's field, where he lands, and the old graves he trains at. */
    static final String OPENING = "Darvoza_oldi";
    static final String LANDING = "Qonish";
    static final String GRAVES = "Eski_qabrlar";
    /** The cave he sees from the old graves (open-world Act 1), and the wilds' caves and graveyards, numbered from 1. */
    static final String CAVE_SEEN = "Gor_kirish";
    static final String CAVE = "Gor_";
    static final String GRAVEYARD = "Qabriston_";
    /** The Black Knight's camp, in the north: stepping into it is going into his castle (open-world Act 1). */
    static final String CAMP = "Qora_qarorgoh";
    /** Where the wilds' graveyards and caves are sought, in shares of the world: off the sites, one or two a region. */
    static final double[][] WILD_GRAVES = {{0.12, 0.72}, {0.33, 0.45}, {0.62, 0.62}, {0.88, 0.62}};
    static final double[][] WILD_CAVES = {{0.30, 0.76}, {0.48, 0.80}, {0.70, 0.45}, {0.30, 0.28}, {0.85, 0.40}};


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

    /** The middle of the lake, in cells. */
    static double[] lakeMiddle() {
        return new double[] {BASIN[0] * SIZE, BASIN[1] * SIZE};
    }

    /** How many cells of shelves no ramp reached were sealed. */
    static int sealedCells(long seed) {
        return new World(seed).sealedCells;
    }

    /** The cells the roads run over, row by row. */
    static boolean[] roads(long seed) {
        return new World(seed).road;
    }

    /** The ways across what no walk crosses, read from {@code data/world/traversal.duke}, in cells. */
    static List<TraversalLink> traversalLinks() {
        var text = Content.read("data/world/traversal.duke");
        var links = new ArrayList<TraversalLink>();
        var inBlock = false;
        for (var line : text.split("\n", -1)) {
            var words = line.strip().split("\\s+");
            if (words.length == 0 || words[0].isEmpty() || words[0].startsWith(";")) {
                continue;
            }
            if (words[0].equalsIgnoreCase("TraversalLinks")) {
                inBlock = true;
                continue;
            }
            if (inBlock && words[0].equalsIgnoreCase("End")) {
                inBlock = false;
                continue;
            }
            if (!inBlock) {
                continue;
            }
            if (words.length < 10) {
                throw new IllegalStateException("a traversal link needs kind ax ay bx by frames arcHeight clip bothWays low: "
                        + line);
            }
            links.add(new TraversalLink(
                    new Coord3D(Float.parseFloat(words[1]), Float.parseFloat(words[2]), 0f),
                    new Coord3D(Float.parseFloat(words[3]), Float.parseFloat(words[4]), 0f),
                    words[0], Integer.parseInt(words[5]), Float.parseFloat(words[6]),
                    words[7], Boolean.parseBoolean(words[8]), Boolean.parseBoolean(words[9])));
        }
        return List.copyOf(links);
    }

    /** One world being drawn: every field of it, in the order it is drawn. */
    private static final class World {

        private final long seed;
        private final Biome[] region = new Biome[SIZE * SIZE];
        private final int[] steps;
        private final boolean[] road = new boolean[SIZE * SIZE];
        private final boolean[] kept = new boolean[SIZE * SIZE];
        private final boolean[] walked = new boolean[SIZE * SIZE];
        /** Roads and the ramps up to the shelves: ground graded to be walked, which later grading leaves be. */
        private final boolean[] graded = new boolean[SIZE * SIZE];
        /** The river's channel, a cell either side of its course; and how high the water stands over each. */
        private final boolean[] channel = new boolean[SIZE * SIZE];
        private final float[] surface = new float[SIZE * SIZE];
        /** Under water: cells nobody walks, written {@code #}. */
        private boolean[] wet = new boolean[SIZE * SIZE];
        /** Shelves no ramp reached, sealed: see {@link #reachTheShelves}. */
        private final boolean[] sealed = new boolean[SIZE * SIZE];
        private int sealedCells;
        private final List<StaticMap.Water> waters = new ArrayList<>();
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
            carveRiver();
            waters.add(lake());
            for (var join : ROADS) {
                // A road out of the city leaves by a gate of its own, on the side of the town wall facing where it
                // goes, and is joined to the plaza within: see door.
                var paths = join[0] == CITY
                        ? List.of(route(door(SITES.get(join[1])), SITES.get(join[1])),
                                route(SITES.get(CITY), door(SITES.get(join[1]))))
                        : List.of(route(SITES.get(join[0]), SITES.get(join[1])));
                for (var path : paths) {
                    grade(path);
                    for (var cell : path) {
                        road[cell[1] * SIZE + cell[0]] = true;
                        graded[cell[1] * SIZE + cell[0]] = true;
                    }
                }
            }
            smoothRoads();
            soak();
            reachTheShelves();
        }

        // ---- the water ----

        /** Whether a cell is in the lake's basin, where no road goes. */
        private boolean inTheLake(int x, int y) {
            return Math.hypot(x + 0.5 - BASIN[0] * SIZE, y + 0.5 - BASIN[1] * SIZE) < BASIN_REACH - 4;
        }

        /** The lake: standing water at {@link #LAKE} over the basin, an outline of a circle round it. */
        private StaticMap.Water lake() {
            var points = new ArrayList<Float>();
            int corners = 32;
            double reach = BASIN_REACH - 2;
            for (int i = 0; i < corners; i++) {
                double angle = 2 * Math.PI * i / corners;
                points.add(tenth(BASIN[0] * SIZE + reach * Math.cos(angle)));
                points.add(tenth(BASIN[1] * SIZE + reach * Math.sin(angle)));
            }
            return new StaticMap.Water(LAKE_NAME, false, LAKE, points);
        }

        /**
         * The river, from a spring high in the snow down to the lake: its course the cheapest way over the ground,
         * keeping low and away from the sites; its bed never rising on the way down; a channel cut along it; and its
         * water drawn as a chain of reaches, each level and a little lower than the one above, so it comes down a
         * mountain in steps, as a river with rapids does, and never stands above its banks.
         */
        private void carveRiver() {
            var course = riverCourse();
            int n = course.size();
            // The bed never rises downstream, and never sinks below what keeps it above the lake it runs into.
            var bed = new int[n];
            for (int i = 0; i < n; i++) {
                var cell = course.get(i);
                int ground = Math.min(Math.min(relief.at(cell[0], cell[1]), relief.at(cell[0] + 1, cell[1])),
                        Math.min(relief.at(cell[0], cell[1] + 1), relief.at(cell[0] + 1, cell[1] + 1)));
                bed[i] = Math.max(LAKE + 10, i == 0 ? ground : Math.min(bed[i - 1], ground));
            }
            // The reaches: a new one wherever the bed has fallen more than a little, or the last has run long.
            var starts = new ArrayList<Integer>();
            starts.add(0);
            for (int i = 1; i < n - 1; i++) {
                int from = starts.getLast();
                if (bed[from] - bed[i] > 8 || i - from >= 24) {
                    starts.add(i);
                }
            }
            starts.add(n - 1);
            var level = new float[n];
            for (int s = 0; s + 1 < starts.size(); s++) {
                int from = starts.get(s);
                int to = starts.get(s + 1);
                for (int i = from; i <= to; i++) {
                    level[i] = bed[from] - 10;
                }
                waters.add(reach(course, from, to, bed[from] - 10, s));
            }
            // The channel: cut along the course, deepest in the middle, its banks the land as it was.
            int corners = SIZE + 1;
            for (int i = 0; i < n; i++) {
                double mx = course.get(i)[0] + 0.5;
                double my = course.get(i)[1] + 0.5;
                for (int cy = (int) my - 3; cy <= (int) my + 3; cy++) {
                    for (int cx = (int) mx - 3; cx <= (int) mx + 3; cx++) {
                        if (cx < 0 || cy < 0 || cx > SIZE || cy > SIZE) {
                            continue;
                        }
                        double away = Math.hypot(cx - mx, cy - my);
                        int cut = (int) Math.round(bed[i] - 24 * smooth((2.6 - away) / 1.6));
                        if (away < 2.6 && cut < steps[cy * corners + cx]) {
                            steps[cy * corners + cx] = cut;
                        }
                    }
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int at = (course.get(i)[1] + dy) * SIZE + course.get(i)[0] + dx;
                        channel[at] = true;
                        surface[at] = Math.max(surface[at], level[i]);
                    }
                }
            }
            relief = new HeightMap(corners, corners, steps);
        }

        /**
         * The river's course: from its spring by its bends to the lake, each stretch the cheapest way over the ground
         * -- a step costing more uphill, up high, near a site, and more or less as a field of noise says, so it winds.
         * A diagonal step goes through the cell beside it, so the channel is whole.
         */
        private List<int[]> riverCourse() {
            var course = new ArrayList<int[]>();
            int[] from = {(int) (SPRING[0] * SIZE), (int) (SPRING[1] * SIZE)};
            course.add(from);
            for (int leg = 0; leg <= BENDS.length; leg++) {
                boolean last = leg == BENDS.length;
                int[] to = last ? null : new int[] {(int) (BENDS[leg][0] * SIZE), (int) (BENDS[leg][1] * SIZE)};
                var stretch = riverStretch(course.getLast(), to);
                course.addAll(stretch.subList(1, stretch.size()));
            }
            return course;
        }

        /** One stretch of the river, from {@code from} to {@code to}, or to the lake where {@code to} is null. */
        private List<int[]> riverStretch(int[] from, int[] to) {
            int start = from[1] * SIZE + from[0];
            var cost = new long[SIZE * SIZE];
            var came = new int[SIZE * SIZE];
            Arrays.fill(cost, Long.MAX_VALUE);
            cost[start] = 0;
            var open = new PriorityQueue<long[]>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0])
                    : Long.compare(a[1], b[1]));
            open.add(new long[] {0, start});
            int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
            int end = -1;
            while (!open.isEmpty()) {
                var head = open.poll();
                int here = (int) head[1];
                if (head[0] > cost[here]) {
                    continue;
                }
                int hx = here % SIZE;
                int hy = here / SIZE;
                if (to == null ? inTheLake(hx, hy) : hx == to[0] && hy == to[1]) {
                    end = here;
                    break;
                }
                for (var way : ways) {
                    int x = hx + way[0];
                    int y = hy + way[1];
                    if (x < 14 || y < 14 || x >= SIZE - 14 || y >= SIZE - 14) {
                        continue;
                    }
                    int next = y * SIZE + x;
                    double flat = 10 + relief.at(x, y) / 24.0
                            + 160 * GroundPictures.fbm((x + 0.5) / SIZE, (y + 0.5) / SIZE, 24, 3, seed + 41)
                            + (nearASite(x, y, 8) ? 5000 : 0);
                    long step = Math.round(flat * (way[0] != 0 && way[1] != 0 ? 1.42 : 1))
                            + Math.max(0, relief.at(x, y) - relief.at(hx, hy)) * 6L;
                    if (cost[here] + step < cost[next]) {
                        cost[next] = cost[here] + step;
                        came[next] = here;
                        open.add(new long[] {cost[next], next});
                    }
                }
            }
            if (end < 0) {
                throw new IllegalStateException("the river finds no way to the lake");
            }
            var stretch = new ArrayList<int[]>();
            for (int at = end; at != start; at = came[at]) {
                int x = at % SIZE;
                int y = at / SIZE;
                stretch.add(new int[] {x, y});
                if (came[at] % SIZE != x && came[at] / SIZE != y) {
                    stretch.add(new int[] {came[at] % SIZE, y}); // the cell a diagonal step goes through
                }
            }
            stretch.add(from);
            return stretch.reversed();
        }

        private boolean nearASite(int x, int y, int margin) {
            for (var site : SITES) {
                if (Math.hypot(x - site.x(), y - site.y()) < site.radius() + margin) {
                    return true;
                }
            }
            return false;
        }

        /**
         * One reach of the river, {@code from} to {@code to} along its course: two banks a cell and a half either side
         * of the course smoothed, its first two corners its start, level at {@code height}.
         */
        private StaticMap.Water reach(List<int[]> course, int from, int to, float height, int number) {
            var left = new ArrayList<double[]>();
            var right = new ArrayList<double[]>();
            var along = new ArrayList<Integer>();
            along.add(from);
            if (to - from >= 2) {
                for (int i = from + 2; i < to; i += 2) {
                    along.add(i);
                }
            } else {
                along.add(from); // at least three pairs: a reach of one step is drawn a little short of nothing
            }
            along.add(to);
            for (int i : along) {
                var here = smoothed(course, i);
                var before = smoothed(course, Math.max(0, i - 2));
                var after = smoothed(course, Math.min(course.size() - 1, i + 2));
                double dx = after[0] - before[0];
                double dy = after[1] - before[1];
                double length = Math.max(1e-6, Math.hypot(dx, dy));
                double nx = -dy / length * 1.6;
                double ny = dx / length * 1.6;
                left.add(new double[] {here[0] + nx, here[1] + ny});
                right.add(new double[] {here[0] - nx, here[1] - ny});
            }
            var points = new ArrayList<Float>();
            // Its start, left bank then right; on down the right bank; back up the left.
            add(points, left.getFirst());
            for (var corner : right) {
                add(points, corner);
            }
            for (int i = left.size() - 1; i >= 1; i--) {
                add(points, left.get(i));
            }
            return new StaticMap.Water(RIVER_NAME + "_" + (number + 1), true, height, points);
        }

        /** The middle of the course at {@code i}, averaged with its neighbours so the banks do not zigzag. */
        private static double[] smoothed(List<int[]> course, int i) {
            double x = 0;
            double y = 0;
            int count = 0;
            for (int k = Math.max(0, i - 2); k <= Math.min(course.size() - 1, i + 2); k++) {
                x += course.get(k)[0] + 0.5;
                y += course.get(k)[1] + 0.5;
                count++;
            }
            return new double[] {x / count, y / count};
        }

        private static void add(List<Float> points, double[] corner) {
            points.add(tenth(corner[0]));
            points.add(tenth(corner[1]));
        }

        /**
         * Which cells are under water, and so nobody's to walk: the lake's where all its ground lies under the
         * surface, the river's where its channel is cut below its reach's -- but a road over the river is a ford.
         */
        private void soak() {
            wet = new boolean[SIZE * SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int at = y * SIZE + x;
                    int top = highest(x, y);
                    boolean lake = Math.hypot(x + 0.5 - BASIN[0] * SIZE, y + 0.5 - BASIN[1] * SIZE) < BASIN_REACH
                            && top < LAKE - 2;
                    boolean river = channel[at] && top < surface[at] - 2;
                    wet[at] = sealed[at] || !graded[at] && (lake || river);
                }
            }
        }

        private int highest(int x, int y) {
            int corners = SIZE + 1;
            return Math.max(Math.max(steps[y * corners + x], steps[y * corners + x + 1]),
                    Math.max(steps[(y + 1) * corners + x], steps[(y + 1) * corners + x + 1]));
        }

        // ---- the shelves ----

        /**
         * Every stretch of ground a hero could stand about on -- more than {@link #SHELF} cells of it -- reached from
         * the city: a terrace the risers cut off, or a hollow among cliffs, is given a ramp graded up or down to the
         * nearest road, as a road is graded. Pieces no larger are left as they are: a ledge on a cliff, not a place.
         *
         * <p>A ramp can cut a little of its own hillside off, so the rounds are counted: what is still cut off after
         * {@link #RAMP_ROUNDS} of them is sealed -- written {@code #}, as water is, ground nobody walks on purpose --
         * rather than left a place a hero sees and cannot reach. How much is sealed: {@link WorldMapWriter#sealedCells}.
         */
        private void reachTheShelves() {
            for (int round = 0; ; round++) {
                var piece = pieces();
                int main = piece[SITES.get(CITY).y() * SIZE + SITES.get(CITY).x()];
                var sizes = new int[SIZE * SIZE + 1];
                for (int p : piece) {
                    if (p >= 0) {
                        sizes[p]++;
                    }
                }
                if (round == RAMP_ROUNDS) {
                    for (int at = 0; at < piece.length; at++) {
                        if (piece[at] >= 0 && piece[at] != main && sizes[piece[at]] > SHELF) {
                            sealed[at] = true;
                            sealedCells++;
                        }
                    }
                    soak();
                    return;
                }
                boolean cut = false;
                var done = new boolean[SIZE * SIZE + 1];
                for (int at = 0; at < piece.length; at++) {
                    int p = piece[at];
                    if (p < 0 || p == main || sizes[p] <= SHELF || done[p]) {
                        continue;
                    }
                    done[p] = true;
                    cut = true;
                    // From the shelf to the nearest road, the way a road would go, then graded to meet both.
                    var nearest = rampFrom(piece, p, main);
                    var shelf = nearest.getLast();
                    var road = nearest.getFirst();
                    var ramp = route(new Site("", (double) shelf[0] / SIZE, (double) shelf[1] / SIZE, 0),
                            new Site("", (double) road[0] / SIZE, (double) road[1] / SIZE, 0));
                    grade(ramp, true);
                    for (var cell : ramp) {
                        graded[cell[1] * SIZE + cell[0]] = true;
                    }
                }
                if (!cut) {
                    return;
                }
                smoothRoads();
                soak();
            }
        }

        /** Which piece of walkable ground every cell is in, by the walk's own rule; -1 for a cliff or water. */
        private int[] pieces() {
            var piece = new int[SIZE * SIZE];
            Arrays.fill(piece, -1);
            int next = 0;
            var queue = new java.util.ArrayDeque<Integer>();
            for (int first = 0; first < piece.length; first++) {
                if (piece[first] >= 0 || !standable(first % SIZE, first / SIZE)) {
                    continue;
                }
                piece[first] = next;
                queue.add(first);
                while (!queue.isEmpty()) {
                    int here = queue.poll();
                    for (int[] way : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int x = here % SIZE + way[0];
                        int y = here / SIZE + way[1];
                        if (x >= 0 && y >= 0 && x < SIZE && y < SIZE && piece[y * SIZE + x] < 0 && standable(x, y)) {
                            piece[y * SIZE + x] = next;
                            queue.add(y * SIZE + x);
                        }
                    }
                }
                next++;
            }
            return piece;
        }

        private boolean standable(int x, int y) {
            return !wet[y * SIZE + x] && lean(x, y) < HeightMap.CLIFF_STEPS;
        }

        /** The shortest way from piece {@code p} to a road, over cliffs but never through water; the road end first. */
        private List<int[]> rampFrom(int[] piece, int p, int main) {
            var came = new int[SIZE * SIZE];
            Arrays.fill(came, -2);
            var queue = new java.util.ArrayDeque<Integer>();
            for (int at = 0; at < piece.length; at++) {
                if (piece[at] == p) {
                    came[at] = -1;
                    queue.add(at);
                }
            }
            while (!queue.isEmpty()) {
                int here = queue.poll();
                if (piece[here] == main && graded[here]) {
                    var ramp = new ArrayList<int[]>();
                    for (int at = here; at >= 0; at = came[at]) {
                        ramp.add(new int[] {at % SIZE, at / SIZE});
                    }
                    return ramp;
                }
                for (int[] way : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int x = here % SIZE + way[0];
                    int y = here / SIZE + way[1];
                    if (x >= 1 && y >= 1 && x < SIZE - 1 && y < SIZE - 1 && came[y * SIZE + x] == -2
                            && !wet[y * SIZE + x]) {
                        came[y * SIZE + x] = here;
                        queue.add(y * SIZE + x);
                    }
                }
            }
            throw new IllegalStateException("a shelf has no way to the rest of the world but through water");
        }

        /**
         * Where two roads meet, or one folds back beside itself, a cell of one can be left leaning as far as a cliff.
         * Each such cell's corners are laid to their average, again until no cell of any road is a cliff: each pass
         * only narrows what a cell's corners span, so it settles in a few. A ramp, steeper by right, is eased only
         * where it is a cliff, and as far as the passes go: a shelf it fails to reach is tried again.
         */
        private void smoothRoads() {
            int corners = SIZE + 1;
            for (int pass = 0; pass < 64; pass++) {
                boolean steep = false;
                for (int at = 0; at < graded.length; at++) {
                    int x = at % SIZE;
                    int y = at / SIZE;
                    if (!graded[at] || lean(x, y) < (road[at] ? HeightMap.CLIFF_STEPS - 4 : HeightMap.CLIFF_STEPS)) {
                        continue;
                    }
                    steep |= road[at];
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
            grade(path, false);
        }

        /**
         * The same, {@code pinned} keeping both ends at their own heights where the way is long enough to climb
         * between them at the grade: a ramp that must meet a shelf and a road where each stands.
         */
        private void grade(List<int[]> path, boolean pinned) {
            int rise = pinned ? RAMP_GRADE : GRADE;
            int n = path.size();
            var along = new double[n];
            for (int i = 0; i < n; i++) {
                along[i] = relief.at(path.get(i)[0], path.get(i)[1]);
            }
            if (pinned) {
                double first = along[0];
                double last = along[n - 1];
                for (int i = 0; i < n; i++) {
                    double lowest = Math.max(first - rise * i, last - rise * (n - 1 - i));
                    double highest = Math.min(first + rise * i, last + rise * (n - 1 - i));
                    along[i] = lowest <= highest ? Math.clamp(along[i], lowest, highest) : (lowest + highest) / 2;
                }
            }
            var low = along.clone();
            var high = along.clone();
            for (int i = 1; i < n; i++) {
                low[i] = Math.min(low[i], low[i - 1] + rise);
                high[i] = Math.max(high[i], high[i - 1] - rise);
            }
            for (int i = n - 2; i >= 0; i--) {
                low[i] = Math.min(low[i], low[i + 1] + rise);
                high[i] = Math.max(high[i], high[i + 1] - rise);
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
                if (graded[y * SIZE + x]) {
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
                    if (x >= 0 && y >= 0 && x < SIZE && y < SIZE && graded[y * SIZE + x]) {
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
            return lerp(height, 20, smooth((BASIN_REACH + wobble - away) / 18));
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
                            + (lean >= HeightMap.CLIFF_STEPS ? 400 : 0)
                            // Over the river where it must, by a ford, and round the lake always.
                            + (channel[next] ? 300 : 0) + (inTheLake(x, y) ? 5000 : 0);
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

        /**
         * Where the road from the city to {@code to} leaves it: in the middle of a length of the town wall (see
         * castles) on the side facing {@code to}, a cell outside it.
         */
        private Site door(Site to) {
            var city = SITES.get(CITY);
            int dx = to.x() - city.x();
            int dy = to.y() - city.y();
            int x = Math.abs(dx) >= Math.abs(dy) ? city.x() + Integer.signum(dx) * 13 : city.x() + 3;
            int y = Math.abs(dx) >= Math.abs(dy) ? city.y() + 3 : city.y() + Integer.signum(dy) * 13;
            return new Site(city.zone() + "_door", (double) x / SIZE, (double) y / SIZE, 0);
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
                var row = new StringBuilder(SIZE);
                for (int x = 0; x < SIZE; x++) {
                    row.append(wet[y * SIZE + x] ? '#' : '0');
                }
                levels.add(row.toString());
                walls.add(row.toString().replace('0', '.'));
            }
            var paint = paint();
            var taken = new HashSet<Long>();
            walk(SITES.get(CITY));
            // The castles first, so nothing is placed inside a wall.
            var castles = castles();
            closeUnder(castles, taken);
            var city = SITES.get(CITY);
            var hero = free(taken, city.x(), city.y());
            var castle = SITES.get(CASTLE);
            var boss = new Monster("Warden", free(taken, castle.x(), castle.y()));
            var props = new ArrayList<Prop>();
            var npcs = new ArrayList<StaticMap.Placed>();
            var yard = yard(taken, props, npcs);
            var places = new ArrayList<Zone>();
            var dressed = dressing(taken, props, npcs, places);
            dressed.addAll(campaign(taken, places));
            var rng = new DeterministicRng(seed ^ 0x776F726C64L);
            var monsters = new ArrayList<Monster>();
            for (var camp : CAMPS) {
                var site = SITES.get((int) camp[0]);
                // Short of the site itself on its road from the city, so the camp is met on the way in.
                for (var kind : (String[]) camp[1]) {
                    double angle = rng.nextInt(360) * Math.PI / 180;
                    int reach = site.radius() / 2 + rng.nextInt(site.radius() / 2 + 1)
                            + ((int) camp[0] == HUT ? site.radius() : 0); // not in her yard: on the way to it
                    monsters.add(new Monster(kind, free(taken, site.x() + (int) Math.round(reach * Math.cos(angle)),
                            site.y() + (int) Math.round(reach * Math.sin(angle)))));
                }
            }
            for (int b = 0; b < HEARTS.length; b++) {
                for (int g = 0; g < ROAMING_GROUPS; g++) {
                    int x = (int) (HEARTS[b][0] * SIZE) + rng.nextInt(-60, 60);
                    int y = (int) (HEARTS[b][1] * SIZE) + rng.nextInt(-60, 60);
                    int gx = x;
                    int gy = y;
                    if (Math.hypot(x - city.x(), y - city.y()) < 50
                            || quiet.stream().anyMatch(q -> Math.hypot(q[0] - gx, q[1] - gy) < 16)) {
                        continue; // the city's own ground is kept quiet
                    }
                    for (var kind : ROAMERS[b]) {
                        monsters.add(new Monster(kind, free(taken, x, y)));
                    }
                }
            }
            for (var wild : Wild.values()) {
                var heart = wildCamp(wild);
                monsters.add(new Monster(wild.boss, free(taken, heart[0], heart[1])));
                // The rest in a ring round him, each a few cells out, met as one fight.
                for (int i = 0; i < wild.kinds.length; i++) {
                    double angle = 2 * Math.PI * i / wild.kinds.length;
                    int reach = 4 + i % 3;
                    monsters.add(new Monster(wild.kinds[i], free(taken,
                            heart[0] + (int) Math.round(reach * Math.cos(angle)),
                            heart[1] + (int) Math.round(reach * Math.sin(angle)))));
                }
            }
            // The bestiary's groups: off the city's ground and the campaign's, and clear of every quiet place.
            for (var beasts : BEASTS) {
                for (int g = 0; g < (int) beasts[2]; g++) {
                    int x = (int) Math.round((double) beasts[0] * SIZE) + rng.nextInt(-30, 30);
                    int y = (int) Math.round((double) beasts[1] * SIZE) + rng.nextInt(-30, 30);
                    int gx = x;
                    int gy = y;
                    if (Math.hypot(x - city.x(), y - city.y()) < BEASTS_KEEP_FROM_CITY
                            || quiet.stream().anyMatch(q -> Math.hypot(q[0] - gx, q[1] - gy) < 20)) {
                        continue;
                    }
                    for (var kind : (String[]) beasts[3]) {
                        monsters.add(new Monster(kind, free(taken, x, y)));
                    }
                }
            }
            for (int[] corner : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
                props.add(new Prop("Pillar", free(taken, castle.x() + corner[0] / 2, castle.y() + corner[1] / 2)));
                props.add(new Prop("Pillar", free(taken, city.x() + corner[0] + (corner[0] > 0 ? 1 : -1),
                        city.y() + corner[1] / 2)));
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
            zones.addAll(places); // the villages, the towers and the landmarks, after the sites
            for (var monster : monsters) {
                keep(monster.at().cellX(), monster.at().cellY(), 1);
            }
            for (var prop : props) {
                keep(prop.at().cellX(), prop.at().cellY(), 1);
            }
            for (var npc : npcs) {
                keep(npc.x(), npc.y(), 1);
            }
            var floor = new GeneratedDungeon(String.join("\n", walls) + "\n", String.join("\n", levels) + "\n", hero,
                    monsters, boss, CASTLE, rooms, links, storeys, props, relief, 0f);
            return new Stage(NAME, "Ochiq dunyo",
                    "Butun qirollik bir xaritada: shahar, qorong'i o'rmon, ozod qilinadigan yerlar va malika qasri.",
                    1, 1, seed, floor, new StagePaint(NAME, paint, PALETTE, COVERAGE, scenery(yard, castles, dressed),
                            waters, 1, traversalLinks()),
                    new MapStory(npcs, zones, List.of(), List.of()));
        }


        /**
         * Yalmog'iz kampir's yard, round the middle of {@link #HUT}: her hut on its chicken legs at the north of the
         * clearing, its door to the south; her cauldron on its fire with her beside it; the girl's cage on its gibbet
         * to the west, a torch by it; her imp; a well, a bench and a stool, a scarecrow; a fence round the west and
         * north; and the small things -- a bucket, a broom, candles, a rat, toadstools, a stump, fallen leaves. The
         * road comes in from the east, which is left open. The solid things and the people go into {@code props}
         * and {@code npcs}; what is only looked at is returned, as scenery.
         */
        private List<Piece> yard(Set<Long> taken, List<Prop> props, List<StaticMap.Placed> npcs) {
            var site = SITES.get(HUT);
            int x = site.x();
            int y = site.y();
            for (var prop : new Object[][] {{"Cauldron", -2, 0}, {"Well", 4, -5}, {"Scarecrow", -6, -5},
                    {"Bench", -5, 1}, {"Stool", -1, 2}, {"Torch", -6, 4}}) {
                props.add(new Prop((String) prop[0], free(taken, x + (int) prop[1], y + (int) prop[2])));
            }
            for (var person : new Object[][] {{"BabaYaga", -2, 1}, {"Damsel", -7, 2}, {"Imp", 1, -2}}) {
                var at = free(taken, x + (int) person[1], y + (int) person[2]);
                npcs.add(new StaticMap.Placed((String) person[0], at.cellX(), at.cellY()));
            }
            var pieces = new ArrayList<Piece>();
            // The hut, walked under between its legs, its door (the model's +Z) facing south, down the map.
            pieces.add(yardPiece("hut", x + 0.5f, y - 4.5f, 0, 0.7f));
            // A fence on an arc round the west and the north, length by length along it (a length lies along the
            // model's X, which a facing of -(a + 90) turns along the arc at a), a gap where a post has fallen.
            for (int angle = 125; angle <= 305; angle += 9) {
                if (angle == 179 || angle == 251) {
                    continue;
                }
                double a = Math.toRadians(angle);
                pieces.add(yardPiece(angle == 188 ? "props/fence_gate" : "props/fence",
                        (float) (x + 0.5 + 7.5 * Math.cos(a)), (float) (y + 0.5 + 7.5 * Math.sin(a)),
                        Math.floorMod(-(angle + 90), 360), 0.2f));
            }
            pieces.add(yardPiece("props/bucket", x + 5.6f, y - 3.6f, 30, 0f));
            pieces.add(yardPiece("props/broom", x + 2.4f, y - 3.2f, 200, 0f));
            pieces.add(yardPiece("props/candles", x - 4.2f, y + 1.8f, 0, 0f));
            pieces.add(yardPiece("props/rat", x - 0.4f, y + 3.6f, 120, 0f));
            pieces.add(yardPiece("nature/leaves", x + 1.5f, y + 2.5f, 40, 0f));
            pieces.add(yardPiece("nature/stump", x + 3.5f, y + 4.5f, 70, 0.2f));
            float[][] toadstools = {{-3.4f, 3.3f}, {-3.0f, 3.7f}, {2.7f, 3.9f}, {4.1f, 4.3f}, {-7.8f, -1.5f}};
            for (int i = 0; i < toadstools.length; i++) {
                pieces.add(yardPiece(i % 2 == 0 ? "nature/mushroom_red" : "nature/mushroom_brown",
                        x + 0.5f + toadstools[i][0], y + 0.5f + toadstools[i][1], i * 73, 0f));
            }
            return pieces;
        }

        private static Piece yardPiece(String model, float x, float y, int facing, float footprint) {
            return new Piece(INUCIIAN + model + ".glb", Math.round(x * 100) / 100f, Math.round(y * 100) / 100f,
                    facing, 1f, 0xFFFFFF, footprint);
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
                    if (x >= 0 && y >= 0 && x < SIZE && y < SIZE && !walked[y * SIZE + x] && !relief.isCliff(x, y)
                            && !wet[y * SIZE + x]) {
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

        // ---- the castles ----

        /** The castle kits (models/castles, CC BY 4.0, see CREDITS.md and data/world/themes/castles.duke). */
        private static final String KITS = "models/castles/";

        /** How each piece the world builds with stands in the way, as the Castles theme offers it. */
        private static final Map<String, String> FOOT = Map.ofEntries(
                Map.entry("town/town_wall_long", "6x1.1"), Map.entry("town/town_wall_short", "3.5x1.1"),
                Map.entry("town/town_wall_tower", "2x1.4"), Map.entry("town/town_gatehouse", "0"),
                Map.entry("town/town_gate", "0"), Map.entry("town/town_tower_round", "0.98"),
                Map.entry("town/town_tower_round_low", "0.79"), Map.entry("town/town_tower_slim", "0.49"),
                Map.entry("town/town_house", "1.2"), Map.entry("town/town_palace_tower", "1.2"),
                Map.entry("fort/fort_wall", "3x1.1"), Map.entry("fort/fort_wall_gable", "1x1.1"),
                Map.entry("fort/fort_wall_ruined", "3x1.1"), Map.entry("fort/fort_gatehouse", "0"),
                Map.entry("fort/fort_house_front", "1x1.1"), Map.entry("fort/fort_house_front_narrow", "1x1.1"),
                Map.entry("fort/fort_keep", "0.57"), Map.entry("fort/fort_tower_cone", "0.39"),
                Map.entry("fort/fort_tower_stepped", "0.49"), Map.entry("fort/fort_tower_open", "0.41"),
                Map.entry("fort/fort_turret", "0.5"),
                Map.entry("desert/desert_block_long", "4x1.3"), Map.entry("desert/desert_block_long_b", "4x1.1"),
                Map.entry("desert/desert_pillar_tall", "0.49"),
                Map.entry("rampart/rampart_wall", "3x1.4"), Map.entry("rampart/rampart_wall_arcade", "3x1.4"),
                Map.entry("rampart/rampart_wall_gate", "0"), Map.entry("rampart/rampart_tower", "1.17"),
                Map.entry("rampart/rampart_tower_tall", "1.17"));

        /** The fort's pictures, by piece, that its dark copy is drawn with: the queen's castle. */
        private static final Map<String, String> DARK = Map.of("fort_wall", "fort_9", "fort_wall_gable", "fort_7",
                "fort_gatehouse", "fort_14", "fort_keep", "fort_15", "fort_tower_stepped", "fort_5",
                "fort_tower_cone", "fort_3", "fort_turret", "fort_12");

        /**
         * How a ring of walls is built: each side whole {@code segment}s; at the start of each a tower (the
         * {@code corner} one at a corner, the {@code joint} one between, either may be none); then, {@code wallAt}
         * cells on, a wall from {@code walls} in turn ("" a breach) -- or, where a road comes through, the
         * {@code gate} (none: an open gap), what stands in it at the same point ({@code inGate}), and {@code fill}
         * {@code gateLength} cells on to close the rest. Pieces named {@code kit/piece}, dark where {@code dark}.
         */
        private record Style(int segment, float wallAt, String[] walls, String gate, String inGate, String fill,
                float gateLength, String corner, String joint, boolean dark) {
        }

        private static final Style TOWN = new Style(6, 0, new String[] {"town/town_wall_long"}, "town/town_gatehouse",
                "town/town_gate", "town/town_wall_tower", 4, "town/town_tower_round", "town/town_tower_round_low",
                false);
        private static final Style ROYAL = new Style(3, 0, new String[] {"fort/fort_wall"}, "fort/fort_gatehouse",
                null, "fort/fort_wall_gable", 2f, "fort/fort_tower_cone", "fort/fort_turret", false);
        private static final Style QUEEN = new Style(3, 0, new String[] {"fort/fort_wall"}, "fort/fort_gatehouse",
                null, "fort/fort_wall_gable", 2f, "fort/fort_tower_stepped", "fort/fort_turret", true);
        private static final Style DESERT = new Style(4, 0, new String[] {"desert/desert_block_long",
                "desert/desert_block_long_b"}, null, null, null, 0, "desert/desert_pillar_tall",
                "desert/desert_pillar_tall", false);
        private static final Style RAMPART = new Style(5, 1, new String[] {"rampart/rampart_wall",
                "rampart/rampart_wall_arcade"}, "rampart/rampart_wall_gate", null, null, 3,
                "rampart/rampart_tower_tall", "rampart/rampart_tower", false);
        private static final Style RUIN = new Style(3, 0, new String[] {"fort/fort_wall_ruined", "",
                "fort/fort_wall_ruined", "fort/fort_wall_ruined"}, null, null, null, 0, "fort/fort_tower_open", null,
                false);

        /**
         * The castles at the sites, from the castle kits: the city ringed by its town wall with the royal castle in
         * its west and a street of house fronts by the plaza; the queen's castle of the fort's pieces drawn dark;
         * a desert fortress in the steppe; a small fort on the meadow and a ruined one in the highlands, their
         * camps about them; and a watchtower by each road between. Every wall stands across a grid line and in the
         * way along its length (a {@code WxD} box); wherever a road comes through a ring there is a gate, and a gate
         * stands in nobody's way. Kampir_kulbasi keeps her fence and nothing more.
         */
        private List<Piece> castles() {
            var pieces = new ArrayList<Piece>();
            var city = SITES.get(CITY);
            // The town wall: 24 cells a side round the city, its roads leaving by gates of their own (door); the
            // royal castle, 9 a side, against its west wall, its gate to the east onto the plaza.
            ring(pieces, city.x() - 12, city.y() - 12, 24, TOWN, Set.of());
            ring(pieces, city.x() - 11, city.y() - 4, 9, ROYAL, Set.of(1 * 100 + 1));
            pieces.add(piece("fort/fort_keep", city.x() - 6.5f, city.y() + 0.5f, 0, false));
            pieces.add(piece("town/town_palace_tower", city.x() - 9f, city.y() - 1.5f, 0, false));
            // A street of house fronts west and east of the plaza, broken where a road runs, a house at its end.
            String[] fronts = {"fort/fort_house_front", "fort/fort_house_front_narrow", "fort/fort_house_front"};
            for (int side : new int[] {-1, 1}) {
                for (int i = 0; i < 6; i++) {
                    int x = side < 0 ? city.x() - 10 + i : city.x() + 4 + i;
                    int y = city.y() + 6;
                    if (!roadNear(x, y, 1)) {
                        pieces.add(piece(fronts[i % fronts.length], x, y, 0, false));
                    }
                }
                if (!roadNear(city.x() + side * 8, city.y() + 9, 2)) {
                    pieces.add(piece("town/town_house", city.x() + side * 8 + 0.5f, city.y() + 9.5f, 0, false));
                }
            }
            var castle = SITES.get(CASTLE);
            ring(pieces, castle.x() - 6, castle.y() - 6, 12, QUEEN, Set.of());
            pieces.add(piece("fort/fort_keep", castle.x() - 6, castle.y() - 6, 0, true));
            var steppe = SITES.get(2);
            ring(pieces, steppe.x() - 8, steppe.y() - 8, 16, DESERT, Set.of());
            var meadow = SITES.get(1);
            ring(pieces, meadow.x() - 7, meadow.y() - 7, 15, RAMPART, Set.of());
            var hill = SITES.get(3);
            ring(pieces, hill.x() - 6, hill.y() - 6, 12, RUIN, Set.of());
            // A watchtower beside each road, about halfway, but on the forest's.
            for (var join : ROADS) {
                if (join[0] == HUT || join[1] == HUT) {
                    continue;
                }
                var a = SITES.get(join[0]);
                var b = SITES.get(join[1]);
                var spot = besideTheRoad((a.x() + b.x()) / 2, (a.y() + b.y()) / 2);
                if (spot != null) {
                    pieces.add(piece("town/town_tower_slim", spot[0] + 0.5f, spot[1] + 0.5f, 0, false));
                    towers.add(spot);
                }
            }
            return pieces;
        }

        /** A ring of walls from (x0, y0), {@code side} cells a side, run clockwise; {@code gates} forced, side*100+i. */
        private void ring(List<Piece> out, int x0, int y0, int side, Style style, Set<Integer> gates) {
            int[][] corners = {{x0, y0}, {x0 + side, y0}, {x0 + side, y0 + side}, {x0, y0 + side}};
            int[][] ways = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
            int[] facings = {0, 270, 180, 90};
            int n = side / style.segment();
            for (int k = 0; k < 4; k++) {
                int[] way = ways[k];
                for (int i = 0; i < n; i++) {
                    int sx = corners[k][0] + way[0] * i * style.segment();
                    int sy = corners[k][1] + way[1] * i * style.segment();
                    var tower = i == 0 ? style.corner() : style.joint();
                    if (tower != null) {
                        out.add(piece(tower, sx, sy, 0, style.dark()));
                    }
                    float ox = sx + way[0] * style.wallAt();
                    float oy = sy + way[1] * style.wallAt();
                    if (gates.contains(k * 100 + i) || roadAcross(sx, sy, way, style.segment())) {
                        if (style.gate() != null) {
                            out.add(piece(style.gate(), ox, oy, facings[k], style.dark()));
                        }
                        if (style.inGate() != null) {
                            out.add(piece(style.inGate(), ox, oy, facings[k], style.dark()));
                        }
                        if (style.fill() != null) {
                            out.add(piece(style.fill(), ox + way[0] * style.gateLength(),
                                    oy + way[1] * style.gateLength(), facings[k], style.dark()));
                        }
                    } else {
                        var wall = style.walls()[(k * n + i) % style.walls().length];
                        if (!wall.isEmpty()) {
                            out.add(piece(wall, ox, oy, facings[k], style.dark()));
                        }
                    }
                }
            }
        }

        /** Whether a road crosses the grid line from (x, y) along {@code way} for {@code length} cells. */
        private boolean roadAcross(int x, int y, int[] way, int length) {
            for (int t = 0; t < length; t++) {
                int cx = way[0] != 0 ? x + way[0] * t + (way[0] < 0 ? -1 : 0) : x;
                int cy = way[1] != 0 ? y + way[1] * t + (way[1] < 0 ? -1 : 0) : y;
                if (way[0] != 0 ? onRoad(cx, cy - 1) || onRoad(cx, cy) : onRoad(cx - 1, cy) || onRoad(cx, cy)) {
                    return true;
                }
            }
            return false;
        }

        private boolean onRoad(int x, int y) {
            return x >= 0 && y >= 0 && x < SIZE && y < SIZE && road[y * SIZE + x];
        }

        private boolean roadNear(int x, int y, int reach) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    if (onRoad(x + dx, y + dy)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** A cell three from a road, near (x, y): flat, walked to, off the road's shoulder; null for none. */
        private int[] besideTheRoad(int x, int y) {
            for (int reach = 0; reach < 40; reach++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int cx = x + dx;
                        int cy = y + dy;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) == reach && cx > 12 && cy > 12 && cx < SIZE - 12
                                && cy < SIZE - 12 && walked[cy * SIZE + cx] && lean(cx, cy) < 6
                                && !roadNear(cx, cy, 2) && roadNear(cx, cy, 3) && !nearASite(cx, cy, 4)) {
                            return new int[] {cx, cy};
                        }
                    }
                }
            }
            return null;
        }

        /** One piece of a kit at (x, y) turned {@code facing}, standing in the way as its kit says; dark if asked. */
        private static Piece piece(String kitPiece, float x, float y, int facing, boolean dark) {
            var name = kitPiece.substring(kitPiece.indexOf('/') + 1);
            var model = KITS + kitPiece + ".gltf"
                    + (dark ? "@" + KITS + "fort/textures/" + DARK.get(name) + "_dark.jpg" : "");
            return Piece.of(model + " " + x + " " + y + " " + facing + " 1 " + FOOT.get(kitPiece));
        }

        /** The cells the castles close, made taken so nothing is placed in a wall, and kept clear of trees. */
        private void closeUnder(List<Piece> castles, Set<Long> taken) {
            for (var piece : castles) {
                var box = piece.boxLength() > 0
                        ? new uz.dukeengine.core.pathfind.SceneryBox(piece.x(), piece.y(), piece.boxLength(),
                                piece.boxDepth(), piece.facing())
                        : null;
                int reach = (int) Math.ceil(Math.max(piece.boxLength(), piece.footprint())) + 1;
                for (int cy = (int) piece.y() - reach; cy <= (int) piece.y() + reach; cy++) {
                    for (int cx = (int) piece.x() - reach; cx <= (int) piece.x() + reach; cx++) {
                        if (cx < 0 || cy < 0 || cx >= SIZE || cy >= SIZE) {
                            continue;
                        }
                        boolean under = box != null ? box.covers(cx + 0.5f, cy + 0.5f)
                                : Math.hypot(cx + 0.5 - piece.x(), cy + 0.5 - piece.y()) < piece.footprint() + 0.5;
                        if (under) {
                            taken.add((long) cy * SIZE + cx);
                            kept[cy * SIZE + cx] = true;
                        }
                    }
                }
            }
        }

        // ---- the dressing: villages, travellers, towers to travel between, landmarks, bridges ----

        /** Where each watchtower stands, in the order the roads are, as castles laid them. */
        private final List<int[]> towers = new ArrayList<>();
        /** The middles of the villages, landmarks and towers: no roaming band is set down near one. */
        private final List<int[]> quiet = new ArrayList<>();

        /**
         * The world's ordinary places, after its castles and before its monsters: a village a third of the way along
         * each road but the forest's (houses, house fronts, a fence, a well, a scarecrow, its villagers), a keeper and
         * a clear zone to land in by each watchtower, a traveller and a pedlar on their rounds, a ruined temple in the
         * highlands, a stone circle on the meadow, an old battlefield in the steppe, and a bridge wherever a road
         * fords the river. Solid things go into {@code props}, people into {@code npcs}, the named places into
         * {@code zones}; what is only looked at is returned.
         */
        private List<Piece> dressing(Set<Long> taken, List<Prop> props, List<StaticMap.Placed> npcs,
                List<Zone> zones) {
            var pieces = new ArrayList<Piece>();
            int village = 0;
            for (var join : ROADS) {
                if (join[0] == HUT || join[1] == HUT) {
                    continue;
                }
                var a = SITES.get(join[0]);
                var b = SITES.get(join[1]);
                int[] at = null; // a third of the way along, or further on where the ground there will not do
                for (double t = 0.3; at == null && t < 0.8; t += 0.15) {
                    at = nearTheRoad((int) Math.round(a.x() + t * (b.x() - a.x())),
                            (int) Math.round(a.y() + t * (b.y() - a.y())), 6);
                }
                if (at != null) {
                    pieces.addAll(village(VILLAGES.get(village), at[0], at[1], village, taken, props, npcs, zones));
                }
                village++;
            }
            for (int t = 0; t < towers.size(); t++) {
                var tower = towers.get(t);
                // The keeper by the tower; the ground he lands on, kept clear, the tower's zone.
                var keeper = free(taken, tower[0] + 2, tower[1]);
                npcs.add(new StaticMap.Placed("TowerKeeper", keeper.cellX(), keeper.cellY()));
                var landing = free(taken, tower[0], tower[1] + 2);
                zones.add(new Zone(TOWER + (t + 1), landing.cellX(), landing.cellY(), 0, 0, 3));
                quiet.add(tower);
            }
            // Who walks the roads: their rounds are their Npc blocks' (data/story/villages.duke).
            for (var walker : new Object[][] {{"Traveller", 0}, {"Pedlar", 2}}) {
                var home = zones.stream().filter(z -> z.name().equals(VILLAGES.get((int) walker[1]))).findFirst();
                if (home.isPresent()) {
                    var at = free(taken, home.get().x() + 2, home.get().y() - 1);
                    npcs.add(new StaticMap.Placed((String) walker[0], at.cellX(), at.cellY()));
                }
            }
            // The stable keeper, inside the city's east wall: he sells the horse's whistle (data/world/mounts.duke).
            var city = SITES.get(CITY);
            var stable = free(taken, city.x() + 9, city.y() + 3);
            npcs.add(new StaticMap.Placed("Otboqar", stable.cellX(), stable.cellY()));
            pieces.addAll(temple(flatNear((int) (0.34 * SIZE), (int) (0.36 * SIZE)), zones));
            pieces.addAll(stones(flatNear((int) (0.52 * SIZE), (int) (0.67 * SIZE)), zones));
            pieces.addAll(battlefield(flatNear((int) (0.63 * SIZE), (int) (0.62 * SIZE)), zones));
            pieces.addAll(bridges());
            closeUnder(pieces, taken);
            return pieces;
        }

        /**
         * Act 1's places, folded into the world (docs/plan/2026-10-15-seamless-campaign.md): the field before the
         * city's north gate where the opening battle is fought -- its two lines, the landing in its middle, the corridor
         * down the road into the gate -- the old graves west of it where he trains, and inside the city the palace's
         * court (the royal castle's ring) with the queen's throne, the plaza and the blacksmith's cellar. Found from the
         * city's middle and the north road, so a reseeded world still has them. Only looked at: nothing new stands in
         * the way.
         */
        private List<Piece> campaign(Set<Long> taken, List<Zone> zones) {
            var pieces = new ArrayList<Piece>();
            var city = SITES.get(CITY);
            int[] field = roomNear(city.x() + 3, city.y() - 34, 8, false);
            int fx = field[0];
            int fy = field[1];
            zones.add(new Zone(OPENING, fx, fy, 0, 0, 13));
            zones.add(new Zone("Saf", fx - 4, fy + 6, 9, 2, 0));
            zones.add(new Zone("Dushman_safi", fx, fy - 8, 0, 0, 3));
            // The landing is the middle of the field, not beside it: the summoning is over the battle itself, the three
            // ritualists ringed round both lines (Battle.Ring), so the beams sweep in over the armies and meet here.
            zones.add(new Zone(LANDING, fx, fy, 0, 0, 1));
            int gate = city.y() - 12 + 3;
            zones.add(new Zone("Yo'lak", fx - 4, fy, 9, Math.max(1, gate - fy), 0));
            int[] graves = roomNear(fx - 16, fy + 2, 6, true);
            zones.add(new Zone(GRAVES, graves[0], graves[1], 0, 0, 6));
            // The palace: the royal castle's court (castles: its ring 9 a side from C + (-11, -4)), the throne by its
            // west wall facing the gate in its east one.
            int px = city.x() - 10;
            int py = city.y() - 3;
            zones.add(new Zone("Saroy", px, py, 8, 8, 0));
            zones.add(new Zone("Taxt", px + 1, py + 5, 0, 0, 1));
            zones.add(new Zone("Bozor", city.x(), city.y(), 0, 0, 3));
            zones.add(new Zone("Yerto'la", city.x() + 8, city.y() - 8, 0, 0, 2));
            // The throne: a dais of floor tiles, the seat, banners on the wall behind, a column either side.
            pieces.add(Piece.of(KITS + "room/room_floor.gltf " + (px + 0.6f) + " " + (py + 5.5f) + " 0 0.5 0"));
            pieces.add(new Piece(KITS + "room/room_pedestal.gltf", px + 0.6f, py + 5.5f, 90, 1.2f, 0xD8B060, 0f));
            pieces.add(new Piece(KITS + "room/room_banners.gltf", px + 0.15f, py + 5.5f, 90, 1f, 0xFFFFFF, 0f));
            pieces.add(new Piece(KITS + "room/room_column.gltf", px + 0.6f, py + 3.8f, 0, 0.8f, 0xFFFFFF, 0f));
            pieces.add(new Piece(KITS + "room/room_column.gltf", px + 0.6f, py + 7.2f, 0, 0.8f, 0xFFFFFF, 0f));
            // The cellar corner: crates, barrels and a shelf against the walls.
            float cx = city.x() + 8.5f;
            float cy = city.y() - 8.5f;
            pieces.add(new Piece(KITS + "room/room_crate.gltf", cx + 1.8f, cy - 1.6f, 15, 1f, 0xFFFFFF, 0f));
            pieces.add(new Piece(KITS + "room/room_barrel.gltf", cx - 1.7f, cy - 1.8f, 0, 1f, 0xFFFFFF, 0f));
            pieces.add(new Piece(KITS + "room/room_shelf.gltf", cx + 2.2f, cy + 0.6f, 270, 1f, 0xFFFFFF, 0f));
            // The field: banner posts by the line.
            pieces.add(new Piece(KITS + "town/town_banner_post.gltf", fx - 5.5f, fy + 8.5f, 0, 1f, 0xFFFFFF, 0f));
            pieces.add(new Piece(KITS + "town/town_banner_post.gltf", fx + 6.5f, fy + 8.5f, 0, 1f, 0xFFFFFF, 0f));
            // The old graves: three rows of mounds, a sword leaning at each head, a dead tree, candles, stumps.
            float gx = graves[0] + 0.5f;
            float gy = graves[1] + 0.5f;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    float mx = gx - 3f + col * 3f;
                    float my = gy - 2.5f + row * 2.5f;
                    pieces.add(new Piece(INUCIIAN + "nature/mound.glb", mx, my, (row * 3 + col) * 23 % 30, 1f,
                            0xFFFFFF, 0f));
                    pieces.add(new Piece(KITS + "room/room_sword_leaning.gltf", mx, my - 0.8f, 90, 1f, 0xB0A898,
                            0f));
                }
            }
            pieces.add(yardPiece("nature/dead_tree_bent", gx + 5.2f, gy - 3.6f, 200, 0f));
            pieces.add(yardPiece("props/candles", gx - 4.6f, gy + 0.4f, 0, 0f));
            pieces.add(Piece.of(KITS + "room/room_column_stump.gltf " + (gx + 4.4f) + " " + (gy + 3.4f) + " 0 1 0"));
            pieces.add(Piece.of(KITS + "room/room_column_stump.gltf " + (gx - 4.4f) + " " + (gy - 3.6f) + " 40 1 0"));
            // The cave he sees from the old graves: a mouth in the rocks north of them (open-world Act 1).
            int[] mouth = roomNear(graves[0] + 3, graves[1] - 16, 3, true);
            caveMouth(pieces, zones, CAVE_SEEN, mouth);
            // The Black Knight's camp, far in the north: dark towers round a yard, its gate to the south.
            int[] camp = roomNear((int) (0.40 * SIZE), (int) (0.10 * SIZE), 6, true);
            for (int[] at : new int[][] {{-5, -5}, {5, -5}, {-5, 3}, {5, 3}}) {
                pieces.add(piece("fort/fort_tower_stepped", camp[0] + at[0], camp[1] + at[1], 0, true));
            }
            pieces.add(piece("fort/fort_keep", camp[0], camp[1] - 5, 0, true));
            zones.add(new Zone(CAMP, camp[0], camp[1], 0, 0, 3));
            keep(camp[0], camp[1], 8);
            quiet.add(camp);
            // The wilds' graveyards and caves, the Black Knight's strength: each a place of its own, off the roads.
            for (int i = 0; i < WILD_GRAVES.length; i++) {
                int[] at = roomNear((int) (WILD_GRAVES[i][0] * SIZE), (int) (WILD_GRAVES[i][1] * SIZE), 6, true);
                graveyard(pieces, zones, GRAVEYARD + (i + 1), at);
            }
            for (int i = 0; i < WILD_CAVES.length; i++) {
                int[] at = roomNear((int) (WILD_CAVES[i][0] * SIZE), (int) (WILD_CAVES[i][1] * SIZE), 3, true);
                caveMouth(pieces, zones, CAVE + (i + 1), at);
            }
            keep(fx, fy, 14);
            keep(graves[0], graves[1], 7);
            quiet.add(field);
            quiet.add(graves);
            closeUnder(pieces, taken);
            return pieces;
        }

        /**
         * A cave's mouth at {@code at}: three tall rocks round it, open to the south, and a zone of one cell in it that
         * takes a hero in (Caves). Kept clear of trees and quiet of the wilds' monsters.
         */
        private void caveMouth(List<Piece> pieces, List<Zone> zones, String name, int[] at) {
            float x = at[0] + 0.5f;
            float y = at[1] + 0.5f;
            pieces.add(new Piece(KENNEY_ROCK + "rock_tall_a.glb", x - 1.6f, y - 0.4f, 20, 9f, 0x6A645C, 0.9f));
            pieces.add(new Piece(KENNEY_ROCK + "rock_tall_c.glb", x + 1.6f, y - 0.4f, 200, 7f, 0x6A645C, 0.6f));
            pieces.add(new Piece(KENNEY_ROCK + "rock_tall_g.glb", x, y - 1.7f, 90, 8f, 0x5A544C, 0.7f));
            zones.add(new Zone(name, at[0], at[1], 0, 0, 1));
            keep(at[0], at[1], 4);
            quiet.add(at);
        }

        /** A graveyard of the wilds at {@code at}: rows of mounds with a sword at each head, a dead tree, and its zone. */
        private void graveyard(List<Piece> pieces, List<Zone> zones, String name, int[] at) {
            float gx = at[0] + 0.5f;
            float gy = at[1] + 0.5f;
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 3; col++) {
                    float mx = gx - 3f + col * 3f;
                    float my = gy - 1.2f + row * 2.5f;
                    pieces.add(new Piece(INUCIIAN + "nature/mound.glb", mx, my, (row * 3 + col) * 23 % 30, 1f,
                            0xFFFFFF, 0f));
                    pieces.add(new Piece(KITS + "room/room_sword_leaning.gltf", mx, my - 0.8f, 90, 1f, 0xB0A898,
                            0f));
                }
            }
            pieces.add(yardPiece("nature/dead_tree_bent", gx + 5.2f, gy - 3.6f, 200, 0f));
            zones.add(new Zone(name, at[0], at[1], 0, 0, 6));
            keep(at[0], at[1], 7);
            quiet.add(at);
        }

        private static final String KENNEY_ROCK = "models/nature/kenney/";

        /** The walked, gentle cell nearest (x, y) with {@code reach} of such ground round it; off the roads if asked. */
        private int[] roomNear(int x, int y, int reach, boolean offRoad) {
            for (int far = 0; far < 60; far++) {
                for (int dy = -far; dy <= far; dy++) {
                    for (int dx = -far; dx <= far; dx++) {
                        int cx = x + dx;
                        int cy = y + dy;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) == far && cx > 14 && cy > 14 && cx < SIZE - 14
                                && cy < SIZE - 14 && roomAt(cx, cy, reach) && (!offRoad || !roadNear(cx, cy, reach))) {
                            return new int[] {cx, cy};
                        }
                    }
                }
            }
            throw new IllegalStateException("no room for the campaign near " + x + "," + y);
        }

        /** A village round (x, y): houses, a row of house fronts, a fence, a well and a scarecrow, its people. */
        private List<Piece> village(String name, int x, int y, int number, Set<Long> taken, List<Prop> props,
                List<StaticMap.Placed> npcs, List<Zone> zones) {
            var pieces = new ArrayList<Piece>();
            pieces.add(piece("town/town_house", x - 3 + 0.5f, y - 3 + 0.5f, 0, false));
            pieces.add(piece("town/town_house", x + 3 + 0.5f, y - 3 + 0.5f, 0, false));
            String[] fronts = {"fort/fort_house_front", "fort/fort_house_front_narrow"};
            for (int i = 0; i < 4; i++) {
                if (!roadNear(x - 2 + i, y + 3, 1)) {
                    pieces.add(piece(fronts[(i + number) % 2], x - 2 + i, y + 3, 0, false));
                }
            }
            for (int i = 0; i < 5; i++) {
                float fx = x - 2.4f + i * 1.2f;
                if (!roadNear((int) fx, y + 5, 1)) {
                    pieces.add(new Piece(INUCIIAN + "props/fence.glb", fx, y + 5.5f, 0, 1f, 0xFFFFFF, 0.2f));
                }
            }
            closeUnder(pieces, taken);
            props.add(new Prop("Well", free(taken, x + 1, y - 1))); // beside the middle, which is kept walkable
            props.add(new Prop("Scarecrow", free(taken, x + 5, y + 1)));
            for (int i = 0; i <= number % 3; i++) {
                var at = free(taken, x - 1 + 2 * i, y + 1);
                npcs.add(new StaticMap.Placed(i % 2 == 0 ? "Villager" : "Farmer", at.cellX(), at.cellY()));
            }
            zones.add(new Zone(name, x, y, 0, 0, 6));
            keep(x, y, 7);
            quiet.add(new int[] {x, y});
            return pieces;
        }

        /** A ruined temple at {@code at}: three rows of columns, some fallen, two broken walls, its old floor. */
        private List<Piece> temple(int[] at, List<Zone> zones) {
            var pieces = new ArrayList<Piece>();
            int x = at[0];
            int y = at[1];
            pieces.add(Piece.of(KITS + "room/room_floor.gltf " + (x - 3) + " " + (y - 2) + " 0 1 0"));
            String[] columns = {"room/room_column", "room/room_column_half", "room/room_column", "room/room_column_stump"};
            int n = 0;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 2; col++) {
                    float cx = x - 2 + col * 3 + 0.5f;
                    float cy = y - 1 + row * 1.5f + 0.5f;
                    pieces.add(Piece.of(KITS + columns[n++ % columns.length] + ".gltf " + cx + " " + cy + " 0 1 0.25"));
                }
            }
            pieces.add(piece("fort/fort_wall_ruined", x - 3, y - 2, 0, false));
            pieces.add(piece("fort/fort_wall_ruined", x - 3, y + 3, 90, false));
            zones.add(new Zone(TEMPLE, x, y, 0, 0, 6));
            keep(x, y, 7);
            quiet.add(at);
            return pieces;
        }

        /** A circle of standing stones at {@code at}, grey with age. */
        private List<Piece> stones(int[] at, List<Zone> zones) {
            var pieces = new ArrayList<Piece>();
            for (int i = 0; i < 10; i++) {
                double a = 2 * Math.PI * i / 10;
                float sx = (float) (at[0] + 0.5 + 4 * Math.cos(a));
                float sy = (float) (at[1] + 0.5 + 4 * Math.sin(a));
                pieces.add(Piece.of(KITS + "room/room_column_half.gltf " + tenth(sx) + " " + tenth(sy) + " "
                        + (i * 37 % 360) + " 1.2 0.3 0xA8A89C"));
            }
            zones.add(new Zone(STONES, at[0], at[1], 0, 0, 6));
            keep(at[0], at[1], 6);
            quiet.add(at);
            return pieces;
        }

        /** An old battlefield at {@code at}: axes and hammers stuck in the ground, shields half sunk, banners, a ruin. */
        private List<Piece> battlefield(int[] at, List<Zone> zones) {
            var pieces = new ArrayList<Piece>();
            var rng = new DeterministicRng(seed ^ 0x62617474L);
            String[] arms = {"weapons/medieval/axe.glb", "weapons/medieval/warhammer.glb",
                "weapons/medieval/shield_round.glb", "weapons/medieval/shield_heater.glb",
                KITS + "room/room_sword.gltf", "weapons/medieval/axe.glb"};
            for (int i = 0; i < 18; i++) {
                double a = rng.nextInt(360) * Math.PI / 180;
                double r = 1 + rng.nextInt(60) / 10.0;
                float wx = (float) (at[0] + 0.5 + r * Math.cos(a));
                float wy = (float) (at[1] + 0.5 + r * Math.sin(a));
                var model = arms[i % arms.length];
                var path = model.startsWith(KITS) ? model : "models/" + model;
                float scale = model.startsWith(KITS) ? 1f : 4f;
                pieces.add(new Piece(path, tenth(wx), tenth(wy), rng.nextInt(360), scale, 0xFFFFFF, 0f));
            }
            pieces.add(piece("town/town_tower_slim", at[0] - 4 + 0.5f, at[1] - 3 + 0.5f, 0, false));
            pieces.add(new Piece(KITS + "town/town_banner_post.gltf", at[0] + 4.5f, at[1] - 2.5f, 30, 0.8f, 0xC8B8B8,
                    0f));
            pieces.add(new Piece(KITS + "town/town_banner_post.gltf", at[0] - 2.5f, at[1] + 4.5f, 200, 0.8f, 0xC8B8B8,
                    0f));
            pieces.add(piece("fort/fort_wall_ruined", at[0] + 2, at[1] + 4, 180, false));
            zones.add(new Zone(BATTLEFIELD, at[0], at[1], 0, 0, 7));
            keep(at[0], at[1], 8);
            quiet.add(at);
            return pieces;
        }

        /** A bridge wherever a road fords the river: over the middle of the ford, along the road's way across. */
        private List<Piece> bridges() {
            var pieces = new ArrayList<Piece>();
            var seen = new boolean[SIZE * SIZE];
            for (int start = 0; start < SIZE * SIZE; start++) {
                if (seen[start] || !road[start] || !channel[start]) {
                    continue;
                }
                // The ford: road over the channel, cell by cell.
                var queue = new java.util.ArrayDeque<Integer>();
                queue.add(start);
                seen[start] = true;
                int minX = SIZE;
                int minY = SIZE;
                int maxX = 0;
                int maxY = 0;
                while (!queue.isEmpty()) {
                    int here = queue.poll();
                    int hx = here % SIZE;
                    int hy = here / SIZE;
                    minX = Math.min(minX, hx);
                    maxX = Math.max(maxX, hx);
                    minY = Math.min(minY, hy);
                    maxY = Math.max(maxY, hy);
                    for (int[] way : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = hx + way[0];
                        int ny = hy + way[1];
                        int next = ny * SIZE + nx;
                        if (nx >= 0 && ny >= 0 && nx < SIZE && ny < SIZE && !seen[next] && road[next]
                                && channel[next]) {
                            seen[next] = true;
                            queue.add(next);
                        }
                    }
                }
                // The bridge runs along the map's y as made: turned a quarter where the road crosses east to west.
                int facing = maxX - minX > maxY - minY ? 90 : 0;
                float bx = tenth((minX + maxX + 1) / 2.0);
                float by = tenth((minY + maxY + 1) / 2.0);
                if (pieces.stream().noneMatch(p -> Math.hypot(p.x() - bx, p.y() - by) < 5)) { // one bridge a ford
                    pieces.add(Piece.of(KITS + "town/town_bridge.gltf " + bx + " " + by + " " + facing + " 1 0"));
                }
            }
            return pieces;
        }

        /** The cell {@code off} from a road near (x, y): flat, walked to, off the road and away from the sites. */
        private int[] nearTheRoad(int x, int y, int off) {
            for (int reach = 0; reach < 60; reach++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int cx = x + dx;
                        int cy = y + dy;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) == reach && cx > 14 && cy > 14 && cx < SIZE - 14
                                && cy < SIZE - 14 && walked[cy * SIZE + cx] && lean(cx, cy) < 10
                                && !roadNear(cx, cy, off - 3) && roadNear(cx, cy, off + 1) && !nearASite(cx, cy, 8)
                                && quiet.stream().noneMatch(q -> Math.hypot(q[0] - cx, q[1] - cy) < 14)) {
                            return new int[] {cx, cy};
                        }
                    }
                }
            }
            return null;
        }

        /** The flat, walked cell nearest (x, y) with room round it: off the roads, away from the sites. */
        private int[] flatNear(int x, int y) {
            for (int reach = 0; reach < 80; reach++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int cx = x + dx;
                        int cy = y + dy;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) == reach && cx > 14 && cy > 14 && cx < SIZE - 14
                                && cy < SIZE - 14 && roomAt(cx, cy, 5) && !roadNear(cx, cy, 6)
                                && !nearASite(cx, cy, 10)) {
                            return new int[] {cx, cy};
                        }
                    }
                }
            }
            throw new IllegalStateException("no flat ground near " + x + "," + y);
        }

        /** Whether every cell within {@code reach} of (x, y) is walked to and gentle. */
        private boolean roomAt(int x, int y, int reach) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int at = (y + dy) * SIZE + x + dx;
                    if (!walked[at] || wet[at] || lean(x + dx, y + dy) >= 6) {
                        return false;
                    }
                }
            }
            return true;
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
            if (wet[y * SIZE + x] && !sealed[y * SIZE + x] || channel[y * SIZE + x] && !road[y * SIZE + x]) {
                return 'm'; // a bed of mud under the water, and the river's wet banks
            }
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
                case STEPPE -> patch > 0.7 ? 'd' : patch < 0.28 ? 'g' : sandy(x, y, patch) ? 'a' : 's';
                case FOREST -> patch > 0.7 || away < BASIN_REACH + 16 && patch > 0.55 ? 'm' : 'f';
                case HIGHLANDS -> lean >= 10 || patch > 0.56 ? 'r' : 'g';
                case SNOW -> lean >= 10 ? 'r' : 'n';
            };
        }

        /** The steppe round Qumli dasht gone to sand: most of it near the desert fort, thinning out to patches. */
        private boolean sandy(int x, int y, double patch) {
            var desert = SITES.get(2);
            return Math.hypot(x - desert.x(), y - desert.y()) < 40 + 50 * (0.6 - patch);
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
        /** Inuciian's pieces ("Baba Yaga's hut", CC BY 4.0): see art/inuciian/export_models.py and darkwood.duke. */
        private static final String INUCIIAN = "models/inuciian/";

        /** One kind of piece a region scatters: its model, how many in ten thousand cells, its size, its trunk. */
        private record Scatter(String model, int perTenThousand, float scale, float footprint) {
        }

        /** The newer nature packs (CC0), each model re-centred on its trunk: Ultimate Nature, MegaKit, Nature Kit. */
        private static final String ULT = "models/nature/ultimate/";
        private static final String MEGA = "models/nature/megakit/";
        private static final String KENNEY = "models/nature/kenney/";

        /**
         * The dark wood, thick: Inuciian's dark pines and dead crooked trees among the KayKit pines and broadleaves,
         * Quaternius' pines, a birch, now and then a great MegaKit tree; mossy stumps and rocks, dark and berried
         * bushes between them, toadstools. What grows under them is the ground's cover (GroundCover). A model with a
         * folder in its name is Inuciian's, at scale 1; one under models/ is named whole. Footprints are the trunks,
         * measured, at the scale given.
         */
        private static final Scatter[] FOREST_TREES = {
                new Scatter("nature/pine_dark", 1300, 1f, 0.09f), new Scatter("pine_tall", 700, 1.67f, 0.05f),
                new Scatter("pine", 900, 1.67f, 0.05f), new Scatter(ULT + "pine_tree_1", 150, 6f, 0.14f),
                new Scatter(ULT + "pine_tree_2", 150, 6f, 0.14f), new Scatter(ULT + "pine_tree_3", 100, 6f, 0.14f),
                new Scatter("tree", 400, 3.1f, TRUNK), new Scatter("tree_round", 300, 3.1f, TRUNK),
                new Scatter("nature/dead_tree", 200, 1f, 0.25f), new Scatter("nature/dead_tree_bent", 150, 1f, 0.22f),
                new Scatter("tree_twisted", 250, 3.1f, 0.17f), new Scatter("nature/dead_tree_tall", 120, 1f, 0.1f),
                new Scatter(ULT + "birch_tree_2", 120, 6f, 0.07f), new Scatter(MEGA + "common_tree_5", 25, 6f, 0.13f),
                new Scatter(MEGA + "common_tree_1", 15, 6f, 0.13f),
                new Scatter("nature/bush_large_dark", 300, 1f, 0f), new Scatter("nature/bush_bare", 200, 1f, 0f),
                new Scatter(ULT + "bush_berries_1", 120, 6f, 0f), new Scatter("nature/grass_tall_dark", 150, 1f, 0f),
                new Scatter("nature/mushroom_red", 60, 1f, 0f), new Scatter("nature/mushroom_brown", 60, 1f, 0f),
                new Scatter(ULT + "tree_stump_moss", 80, 6f, 0.32f), new Scatter(ULT + "rock_moss_4", 80, 8f, 0.3f),
                new Scatter("nature/stump", 40, 1f, 0.1f), new Scatter("nature/rock", 50, 1f, 0.18f)};
        /** The meadow's groves: KayKit's broadleaves with birches and MegaKit's common trees among them. */
        private static final Scatter[] GROVE = {
                new Scatter("tree", 1200, 3.1f, TRUNK), new Scatter("tree_round", 1200, 3.1f, TRUNK),
                new Scatter(ULT + "birch_tree_1", 250, 6f, 0.07f), new Scatter(ULT + "birch_tree_3", 200, 6f, 0.07f),
                new Scatter(MEGA + "common_tree_1", 50, 6f, 0.13f), new Scatter(MEGA + "common_tree_2", 50, 6f, 0.13f),
                new Scatter("bush_small", 300, 3f, 0f), new Scatter(ULT + "bush_1", 150, 6f, 0f)};
        /** The open meadow: its grass is the ground's cover; bushes, and a lone tree. */
        private static final Scatter[] MEADOW = {
                new Scatter("bush_small", 80, 3f, 0f), new Scatter(ULT + "bush_1", 60, 6f, 0f),
                new Scatter(ULT + "bush_2", 60, 6f, 0f), new Scatter(ULT + "bush_berries_1", 20, 6f, 0f),
                new Scatter(ULT + "birch_tree_1", 30, 6f, 0.07f), new Scatter("tree_round", 40, 3.1f, TRUNK)};
        private static final Scatter[] STEPPE = {
                new Scatter("bush_small", 120, 3f, 0f), new Scatter("tree_stunted", 30, 3.1f, TRUNK),
                new Scatter(ULT + "common_tree_dead_2", 25, 6f, 0.09f), new Scatter("pebble", 100, 3f, 0f),
                new Scatter("rock_cluster_low", 40, 3f, 0.42f), new Scatter(ULT + "rock_1", 60, 8f, 0.18f)};
        /** The sandy steppe round Qumli dasht: cacti, palms, dead trees, and Kenney's tall rocks as mesas. */
        private static final Scatter[] SANDS = {
                new Scatter(ULT + "cactus_1", 80, 6f, 0.04f), new Scatter(ULT + "cactus_2", 80, 6f, 0.04f),
                new Scatter(ULT + "cactus_3", 80, 6f, 0.04f), new Scatter(ULT + "cactus_flowers_2", 40, 6f, 0.05f),
                new Scatter(KENNEY + "cactus_tall", 60, 6f, 0.18f), new Scatter(KENNEY + "cactus_short", 60, 6f, 0.15f),
                new Scatter(KENNEY + "tree_palm_bend", 30, 6f, 0.13f),
                new Scatter(KENNEY + "tree_palm_detailed_short", 30, 6f, 0.19f),
                new Scatter(ULT + "palm_tree_1", 20, 6f, 0.05f), new Scatter(ULT + "common_tree_dead_1", 30, 6f, 0.07f),
                new Scatter(KENNEY + "rock_tall_a", 8, 10f, 1.0f), new Scatter(KENNEY + "rock_tall_c", 15, 6f, 0.42f),
                new Scatter(KENNEY + "rock_tall_g", 15, 6f, 0.44f)};
        /** The highlands: rock, MegaKit's and Quaternius' pines, and Kenney's standing stones. */
        private static final Scatter[] HIGHLANDS = {
                new Scatter("rock_cluster", 200, 3f, 0.42f), new Scatter("stone", 150, 3f, 0.16f),
                new Scatter(MEGA + "rock_medium_1", 150, 8f, 0.44f), new Scatter(ULT + "rock_4", 150, 8f, 0.3f),
                new Scatter(KENNEY + "stone_tall_b", 25, 6f, 0.67f), new Scatter("pebble", 200, 3f, 0f),
                new Scatter("pine", 250, 1.67f, 0.05f), new Scatter(ULT + "pine_tree_2", 200, 6f, 0.14f),
                new Scatter(MEGA + "pine_5", 100, 6f, 0.09f)};
        /** The snow: Quaternius' snowy pines and birch, snowy rocks, bushes and stumps. */
        private static final Scatter[] SNOW = {
                new Scatter(ULT + "pine_tree_snow_1", 200, 6f, 0.14f), new Scatter(ULT + "pine_tree_snow_2", 200, 6f, 0.14f),
                new Scatter(ULT + "pine_tree_snow_3", 200, 6f, 0.14f), new Scatter(ULT + "pine_tree_snow_4", 150, 6f, 0.14f),
                new Scatter(ULT + "birch_tree_snow_1", 60, 6f, 0.07f), new Scatter("pine_tall", 100, 1.67f, 0.05f),
                new Scatter(ULT + "rock_snow_4", 150, 8f, 0.3f), new Scatter(ULT + "rock_snow_1", 100, 8f, 0.18f),
                new Scatter(ULT + "bush_snow_1", 100, 6f, 0f), new Scatter(ULT + "tree_stump_snow", 50, 6f, 0.32f),
                new Scatter("rock_cluster", 80, 3f, 0.42f)};
        /** The marsh by the lake: willows and dead willows over reeds; its grass is the ground's cover. */
        private static final Scatter[] MARSH = {
                new Scatter(ULT + "willow_1", 150, 6f, 0.08f), new Scatter(ULT + "willow_3", 150, 6f, 0.09f),
                new Scatter(ULT + "willow_5", 150, 6f, 0.09f), new Scatter(ULT + "willow_dead_1", 100, 6f, 0.07f),
                new Scatter(ULT + "willow_dead_2", 80, 6f, 0.08f), new Scatter(MEGA + "dead_tree_1", 30, 6f, 0.13f),
                new Scatter("nature/cattails", 300, 1f, 0f), new Scatter("bush_small", 100, 3f, 0f),
                new Scatter("tree_twisted", 40, 3.1f, 0.17f)};

        /**
         * What stands about, cell by cell in a fixed order: never on a road or a step from one, on a site, by a
         * monster or a prop, or on ground too steep to stand on.
         */
        private List<Piece> scenery(List<Piece> yard, List<Piece> castles, List<Piece> dressed) {
            for (var site : SITES) {
                // The kampir's yard is walled in by the wood up to its fence; every other site has open ground round it.
                keep(site.x(), site.y(), site == SITES.get(HUT) ? site.radius() - 1 : site.radius() + 2);
            }
            for (var piece : yard) {
                keep((int) piece.x(), (int) piece.y(), piece.model().endsWith("hut.glb") ? 2 : 0);
            }
            var rng = new DeterministicRng(seed ^ 0x7363656E65L);
            var pieces = new ArrayList<Piece>();
            for (int y = 2; y < SIZE - 2; y++) {
                for (int x = 2; x < SIZE - 2; x++) {
                    if (kept[y * SIZE + x] || nearRoad(x, y, 2) || lean(x, y) >= 12 || wet[y * SIZE + x]
                            || channel[y * SIZE + x]) {
                        continue;
                    }
                    double thick = thickness(x, y);
                    for (var scatter : scattersAt(x, y)) {
                        if (rng.nextInt(10_000) >= scatter.perTenThousand() * thick) {
                            continue;
                        }
                        float across = x + (15 + rng.nextInt(71)) / 100f;
                        float down = y + (15 + rng.nextInt(71)) / 100f;
                        float size = scatter.scale() * (85 + rng.nextInt(31)) / 100f;
                        pieces.add(new Piece(modelOf(scatter), across, down, rng.nextInt(360),
                                Math.round(size * 100) / 100f, 0xFFFFFF,
                                Math.round(scatter.footprint() * size / scatter.scale() * 100) / 100f));
                        break; // one piece a cell
                    }
                }
            }
            pieces.addAll(yard);
            pieces.addAll(castles);
            pieces.addAll(dressed);
            // A line a row of cells, as the file writes them: the yard's pieces go into their rows.
            pieces.sort(java.util.Comparator.comparingInt(piece -> (int) Math.floor(piece.y())));
            return pieces;
        }

        private static String modelOf(Scatter scatter) {
            return scatter.model().startsWith("models/") ? scatter.model() + ".glb"
                    : scatter.model().contains("/") ? INUCIIAN + scatter.model() + ".glb"
                    : MODELS + scatter.model() + ".gltf";
        }

        /**
         * How thick the dark wood stands at a cell: thinned a third along a road's shoulders, so the camera above
         * sees the hero walking it, and half again as thick in a ring round the kampir's clearing, so her yard is
         * walled in trees. Elsewhere a third again as thick as it was, broken by glades. Outside the forest, as its scatter says.
         */
        private double thickness(int x, int y) {
            if (region[y * SIZE + x] != Biome.FOREST) {
                return 1;
            }
            var hut = SITES.get(HUT);
            double fromHut = Math.hypot(x - hut.x(), y - hut.y());
            if (fromHut > hut.radius() - 1 && fromHut < hut.radius() + 7 && !nearRoad(x, y, 4)) {
                return 1.6;
            }
            // Glades: open ground in the wood, where its cover grows and the light comes down.
            double u = (x + 0.5) / SIZE;
            double v = (y + 0.5) / SIZE;
            if (GroundPictures.fbm(u, v, 20, 2, seed + 31) < 0.3) {
                return 0.12;
            }
            return nearRoad(x, y, 5) ? 0.65 : 1.3;
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
                case STEPPE -> surfaceAt(x, y) == 'a' ? SANDS : STEPPE;
                case HIGHLANDS -> HIGHLANDS;
                case SNOW -> SNOW;
            };
        }

        /** The nearest cell to ({@code x}, {@code y}) that can be stood on and is not yet taken. */
        /**
         * The middle of a wild camp: the nearest cell to where it is looked for, in rings outward, that a hero walks
         * to from the city, dry and level, in its region -- or, for the marsh, by the lake -- and at least
         * {@link #WILD_QUIET} cells from every village, tower and landmark and past every site's own ground.
         */
        private int[] wildCamp(Wild wild) {
            int x0 = (int) Math.round(wild.u * SIZE);
            int y0 = (int) Math.round(wild.v * SIZE);
            for (int reach = 0; reach < 160; reach++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int x = x0 + dx;
                        int y = y0 + dy;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) == reach && wildGround(wild, x, y)) {
                            return new int[] {x, y};
                        }
                    }
                }
            }
            throw new IllegalStateException("no ground for the " + wild + " camp");
        }

        private boolean wildGround(Wild wild, int x, int y) {
            if (x < 20 || y < 20 || x >= SIZE - 20 || y >= SIZE - 20) {
                return false;
            }
            int at = y * SIZE + x;
            if (!walked[at] || wet[at] || lean(x, y) >= 10) {
                return false;
            }
            if (wild.region != null ? region[at] != wild.region
                    : Math.hypot(x - BASIN[0] * SIZE, y - BASIN[1] * SIZE) > BASIN_REACH + 24) {
                return false;
            }
            if (quiet.stream().anyMatch(q -> Math.hypot(q[0] - x, q[1] - y) < WILD_QUIET)) {
                return false;
            }
            return SITES.stream().noneMatch(site -> Math.hypot(site.x() - x, site.y() - y) < site.radius() + WILD_QUIET);
        }

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

    /** A coordinate to a tenth of a cell, as the file writes it. */
    private static float tenth(double value) {
        return Math.round(value * 10) / 10f;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
