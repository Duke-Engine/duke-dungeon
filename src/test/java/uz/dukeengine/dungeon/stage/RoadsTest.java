package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** E52: the open world's dirt roads are cheap to walk and its open ground dear; a map with no road routes as ever. */
class RoadsTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void theOpenWorldLaysItsRoadsAsCheapSteps() {
        var stage = Stages.load("world", SETTINGS);
        var game = Dungeon.newStageSession(stage, SETTINGS).game();
        game.runHeadless(1);
        var grid = game.getLogic().getPathGrid();
        assertTrue(grid.hasStepCosts(), "the world's roads are not costed");
        var rows = stage.paint().rows();
        boolean road = false;
        boolean open = false;
        for (int y = 0; y < rows.size() && !(road && open); y++) {
            for (int x = 0; x < rows.get(y).length(); x++) {
                char c = rows.get(y).charAt(x);
                if (c == 'd' && !road) {
                    road = true;
                    assertEquals(Roads.ROAD_PERCENT, grid.stepPercent(x, y));
                } else if (c != 'd' && !open) {
                    open = true;
                    assertEquals(Roads.OPEN_PERCENT, grid.stepPercent(x, y));
                }
            }
        }
        assertTrue(road && open);
    }

    @Test
    void aMapWithoutRoadsHasNoStepCosts() {
        var grid = new PathGrid(4, 4);
        var bare = new StagePaint("bare", List.of("gggg", "gggg", "gggg", "gggg"), Map.of("g", "ground/grass.png"),
                Map.of());
        Roads.lay(grid, bare);
        assertFalse(grid.hasStepCosts());
        var roaded = new StagePaint("roaded", List.of("gggg", "dddd", "gggg", "gggg"), Map.of("g", "ground/grass.png"),
                Map.of());
        Roads.lay(grid, roaded);
        assertEquals(100, grid.stepPercent(2, 1));
        assertEquals(Roads.OPEN_PERCENT, grid.stepPercent(2, 0));
        Roads.lay(grid, null);
        assertFalse(grid.hasStepCosts());
    }
}
