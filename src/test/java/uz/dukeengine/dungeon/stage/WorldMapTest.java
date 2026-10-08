package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.Pathfinder;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap.Zone;

/**
 * The campaign's open world: what its seed writes, every region a real share of it, every named place on ground a
 * hero stands on and walks to — by road, and in a game — and how much it costs to load and to run.
 */
class WorldMapTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static Stage shipped;

    private static synchronized Stage shipped() {
        if (shipped == null) {
            shipped = Stages.load("world", SETTINGS);
        }
        return shipped;
    }

    /** What is shipped is what its seed writes, and the same seed writes it again, character for character. */
    @Test
    void theShippedWorldIsWhatItsSeedWritesEveryTime() {
        var text = Stages.found("world").text().replace("\r\n", "\n");

        assertEquals(StageFile.write(WorldMapWriter.stage(SETTINGS)), text);
        assertEquals(StageFile.write(WorldMapWriter.stage(SETTINGS, 7)),
                StageFile.write(WorldMapWriter.stage(SETTINGS, 7)));
        assertTrue(text.length() < 10_000_000, "the world's file is " + text.length() + " characters");
    }

    @Test
    void theStageScreenOffersItAsTheOpenWorld() {
        assertTrue(Stages.all().stream().anyMatch(listed -> listed.name().equals("world")
                && listed.title().equals("Ochiq dunyo")), "the stage screen does not offer it");
        assertEquals(WorldMapWriter.SIZE, shipped().paint().rows().size());
    }

    /** Five regions, each at least the share the biome generator keeps a region to (MinBiomeShare, 15). */
    @Test
    void everyRegionIsAtLeastItsShare() {
        var regions = WorldMapWriter.regions(WorldMapWriter.SEED);
        var counts = new int[WorldMapWriter.Biome.values().length];
        for (var region : regions) {
            counts[region.ordinal()]++;
        }
        for (var biome : WorldMapWriter.Biome.values()) {
            double share = 100.0 * counts[biome.ordinal()] / regions.length;
            assertTrue(share >= 15, biome + " is " + share + "% of the world");
        }
    }

    /** From the city along roads alone, every site: the roads join them all. */
    @Test
    void theRoadsJoinEverySite() {
        var road = WorldMapWriter.roads(WorldMapWriter.SEED);
        var grid = groundOf(shipped());
        int size = WorldMapWriter.SIZE;
        var reached = new boolean[size * size];
        var city = WorldMapWriter.SITES.getFirst();
        var queue = new ArrayDeque<int[]>();
        reached[city.y() * size + city.x()] = true;
        queue.add(new int[] {city.x(), city.y()});
        int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            var here = queue.poll();
            for (var way : ways) {
                int x = here[0] + way[0];
                int y = here[1] + way[1];
                if (grid.inBounds(x, y) && road[y * size + x] && !reached[y * size + x]
                        && grid.canStep(here[0], here[1], x, y)) {
                    reached[y * size + x] = true;
                    queue.add(new int[] {x, y});
                }
            }
        }
        for (var site : WorldMapWriter.SITES) {
            assertTrue(reached[site.y() * size + site.x()], "no road reaches " + site.zone());
        }
    }

    /** Every zone inside the map, its middle on ground that can be stood on and walked to from the way in. */
    @Test
    void everyZoneIsInsideTheMapOnGroundWalkedTo() {
        var stage = shipped();
        var walk = StageCheck.walk(stage.floor());
        var relief = stage.floor().relief();
        assertEquals(WorldMapWriter.SITES.size() + 1, stage.story().zones().size());
        for (var zone : stage.story().zones()) {
            int reach = zone.radius() > 0 ? zone.radius() : 0;
            int right = zone.radius() > 0 ? zone.x() + reach : zone.x() + zone.width() - 1;
            int bottom = zone.radius() > 0 ? zone.y() + reach : zone.y() + zone.height() - 1;
            assertTrue(zone.x() - reach >= 0 && zone.y() - reach >= 0 && right < WorldMapWriter.SIZE
                    && bottom < WorldMapWriter.SIZE, zone.name() + " runs off the map");
            int[] middle = middleOf(zone);
            assertFalse(relief.isCliff(middle[0], middle[1]), zone.name() + "'s middle is a cliff");
            assertTrue(walk.to(middle[0], middle[1]) >= 0, zone.name() + " cannot be walked to");
        }
    }

    @Test
    void itsPaletteNamesOnlyPicturesTheGameHasAndItsSceneryOnlyModelsItHas() {
        var paint = shipped().paint();
        for (var picture : paint.palette().values()) {
            assertNotNull(getClass().getClassLoader().getResource(picture), picture + " is not among the resources");
        }
        assertTrue(paint.scenery().size() > 1000, "a world with " + paint.scenery().size() + " pieces is bare");
        paint.scenery().stream().map(piece -> piece.model()).distinct().forEach(model ->
                assertNotNull(getClass().getClassLoader().getResource(model), model + " is not among the resources"));
    }

    /**
     * Played headless, the hero is in the city and the engine's own pathfinder finds him a way to every site. The
     * times — reading and checking the file, laying the world, a frame — are printed for the record.
     */
    @Test
    void playedTheHeroWalksFromTheCityToEverySite() {
        long started = System.nanoTime();
        var stage = Stages.load("world", SETTINGS);
        long loaded = System.nanoTime();
        var session = uz.dukeengine.dungeon.Dungeon.newStageSession(stage, SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        long laid = System.nanoTime();
        int frames = 120;
        game.runHeadless(frames);
        long ran = System.nanoTime();
        System.out.printf("world: read and checked %d ms, laid %d ms, %.2f ms a frame over %d frames%n",
                (loaded - started) / 1_000_000, (laid - loaded) / 1_000_000,
                (ran - laid) / 1e6 / frames, frames);

        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(SETTINGS.run().defaultHero())).findFirst().orElse(null);
        assertNotNull(hero, "no hero in the world");
        var grid = game.getTerrain();
        float cell = PathGrid.DEFAULT_CELL_SIZE;
        for (var site : WorldMapWriter.SITES.subList(1, WorldMapWriter.SITES.size())) {
            var to = new Coord3D((site.x() + 0.5f) * cell, (site.y() + 0.5f) * cell, 0f);
            var path = Pathfinder.findPath(grid, hero.getPosition(), to);
            assertFalse(path.isEmpty(), "the hero finds no way to " + site.zone());
            var end = path.getWaypoints().getLast();
            assertTrue(Math.hypot(end.x() - to.x(), end.y() - to.y()) < 2 * cell,
                    "the way to " + site.zone() + " stops at " + end + ", short of " + to);
        }
    }

    private static int[] middleOf(Zone zone) {
        return zone.radius() > 0 ? new int[] {zone.x(), zone.y()}
                : new int[] {zone.x() + zone.width() / 2, zone.y() + zone.height() / 2};
    }

    private static PathGrid groundOf(Stage stage) {
        var grid = uz.dukeengine.core.pathfind.MapLoader.fromText(stage.floor().asciiMap());
        uz.dukeengine.core.pathfind.MapLoader.levels(grid, stage.floor().levelMap());
        grid.setRelief(stage.floor().relief());
        return grid;
    }
}
