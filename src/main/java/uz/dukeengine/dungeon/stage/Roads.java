package uz.dukeengine.dungeon.stage;

import uz.dukeengine.core.pathfind.PathGrid;

/**
 * What a step costs on a painted map (engine E52): its dirt road, painted {@code d}, at the plain cost and every
 * other cell dearer, so a walker keeps to the road unless it runs far out of the way. A map with no road costs nothing
 * differently, and routes as ever.
 */
public final class Roads {

    /** The paint character of a road. */
    static final char ROAD = 'd';
    /** A step on a road, and on anything else, as a share of its plain cost in percent (never under 100: A* stays optimal). */
    public static final int ROAD_PERCENT = 100;
    public static final int OPEN_PERCENT = 250;

    private Roads() {
    }

    /** Lay the paint's step costs on {@code grid}, which may be walked finer than the paint is drawn; clears them where there is no road. */
    public static void lay(PathGrid grid, Object map) {
        grid.setStepCosts(map instanceof StagePaint paint ? costs(paint, grid.getWidth(), grid.getHeight()) : null);
    }

    /** A share for each of the grid's cells, or null where the paint has no road. */
    static int[] costs(StagePaint paint, int width, int height) {
        var rows = paint.rows();
        if (rows.isEmpty() || rows.stream().noneMatch(row -> row.indexOf(ROAD) >= 0)) {
            return null;
        }
        int paintHeight = rows.size();
        var shares = new int[width * height];
        for (int cy = 0; cy < height; cy++) {
            var row = rows.get(cy * paintHeight / height);
            for (int cx = 0; cx < width; cx++) {
                int column = cx * row.length() / width;
                shares[cy * width + cx] = row.charAt(column) == ROAD ? ROAD_PERCENT : OPEN_PERCENT;
            }
        }
        return shares;
    }
}
