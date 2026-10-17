package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The open world's castles (WorldMapWriter.castles): every walled site is ringed in its kit's walls, the queen's in
 * the fort's drawn dark, and the hero walks from the city through a gate into the court of every one -- never
 * through a wall, which stands in the way.
 */
class WorldCastlesTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void everySiteHasItsCastleAndTheQueensIsDark() {
        var scenery = Stages.load("world", SETTINGS).paint().scenery();
        for (var kit : List.of("town/town_wall_long", "town/town_gatehouse", "fort/fort_wall.gltf",
                "fort/fort_keep", "desert/desert_block_long", "rampart/rampart_wall", "fort/fort_wall_ruined",
                "town/town_tower_slim")) {
            assertTrue(scenery.stream().anyMatch(p -> p.model().contains(kit)), "no " + kit + " in the world");
        }
        var queens = scenery.stream().filter(p -> p.model().contains("/fort/") && !p.picture().isEmpty()).toList();
        assertTrue(queens.size() >= 30, "the queen's castle has " + queens.size() + " dark pieces");
        assertTrue(queens.stream().allMatch(p -> p.picture().endsWith("_dark.jpg")));
        assertTrue(scenery.stream().filter(p -> p.model().contains("_wall")).filter(p -> !p.model().contains("gate"))
                .allMatch(p -> p.boxLength() > 0), "a wall stands in nobody's way");
        long cityGates = scenery.stream().filter(p -> p.model().endsWith("town_gatehouse.gltf")).count();
        assertTrue(cityGates >= 2, "the city has " + cityGates + " gates");
    }

    @Test
    void theHeroWalksFromTheCityIntoEveryCourtByAGate() {
        var game = Dungeon.newStageSession(Stages.load("world", SETTINGS), SETTINGS).game();
        game.runHeadless(1);
        var logic = game.getLogic();
        var grid = logic.getPathGrid();
        var hero = logic.getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(SETTINGS.run().defaultHero())).findFirst().orElseThrow();
        float cell = PathGrid.DEFAULT_CELL_SIZE;
        // Walls are shut: the cells under the town wall's first length, east of its north-west corner.
        var city = WorldMapWriter.SITES.getFirst();
        assertTrue(MapCells.shut(grid, city.x() - 9, city.y() - 12), "the town wall stands in nobody's way");
        for (var site : WorldMapWriter.SITES.subList(1, WorldMapWriter.SITES.size())) { // he starts in the city
            var court = new Coord3D((site.x() + 0.5f) * cell, (site.y() + 0.5f) * cell, 0f);
            assertFalse(MapCells.shut(grid, site.x(), site.y()), site.zone() + "'s court is walled up");
            var path = logic.findPath(hero.getPosition(), court);
            assertFalse(path.isEmpty(), "no way into " + site.zone());
            var end = path.getWaypoints().getLast();
            assertTrue(Math.hypot(end.x() - court.x(), end.y() - court.y()) < 1.5f * cell,
                    "the way into " + site.zone() + " stops at " + end + ", outside");
            for (var waypoint : path.getWaypoints()) {
                assertFalse(grid.isBlocked(grid.toCellX(waypoint), grid.toCellY(waypoint)),
                        "the way into " + site.zone() + " goes through a wall at " + waypoint);
            }
        }
    }
}
