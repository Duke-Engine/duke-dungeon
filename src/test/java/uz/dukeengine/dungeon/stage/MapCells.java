package uz.dukeengine.dungeon.stage;

import uz.dukeengine.core.pathfind.PathGrid;

/** A map's own cell asked of a grid that may walk it finer: shut if any of the grid's cells in it is. */
final class MapCells {

    private MapCells() {
    }

    static boolean shut(PathGrid grid, int x, int y) {
        int k = grid.cellsPerMapCell();
        for (int sy = 0; sy < k; sy++) {
            for (int sx = 0; sx < k; sx++) {
                if (grid.isBlocked(x * k + sx, y * k + sy)) {
                    return true;
                }
            }
        }
        return false;
    }
}
