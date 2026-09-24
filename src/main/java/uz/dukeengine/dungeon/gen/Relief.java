package uz.dukeengine.dungeon.gen;

import java.util.List;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.world.Theme;

/**
 * The ground under a floor: hills and hollows, the chambers laid level or not as the terrain says, and nowhere
 * steep enough to be a cliff.
 *
 * <p>This is what the storeys were for, done the way the ground itself does it. A chamber stands higher than the
 * one before it because the land rises between them, and the tunnel that joins them is the slope — there is no
 * stair to cut, no landing to fit, and no climb to give up because a corridor was too short to hold one.
 *
 * <p><b>Walkable everywhere, by construction.</b> The last thing done to the heights is to hold every corner within
 * {@code Slope} steps of each of its eight neighbours, lowering whatever stands too proud. A cell's four corners
 * are all neighbours of one another, so none of them is ever {@code Slope} or more from another, and SAGE's rule
 * for a cliff — sixteen steps between a cell's corners — can never be met. The floor the {@link Cave} guaranteed
 * connected stays connected on any ground this lays under it.
 *
 * <p>Integers throughout, from a stream of its own: the same seed raises the same hills on every machine, and
 * drawing them moves nothing else on the floor.
 */
final class Relief {

    private Relief() {
    }

    /** The heights at every corner of {@code cave}'s cells, or null for ground that lies flat. */
    static HeightMap of(long seed, Cave cave, List<Room> rooms, Theme.Terrain terrain) {
        if (terrain.rise() <= 0) {
            return null;
        }
        int columns = cave.width() + 1;
        int rows = cave.height() + 1;
        var rng = new DeterministicRng(seed ^ 0x72656C696566L);
        var steps = new int[columns * rows];
        // Two sizes of hill, the broad one twice the height of the one riding on it.
        int broad = terrain.rise() * 2 / 3;
        octave(rng, steps, columns, rows, terrain.hillSize(), broad);
        octave(rng, steps, columns, rows, Math.max(1, terrain.hillSize() / 2), terrain.rise() - broad);
        level(steps, columns, cave, rooms, terrain.level());
        smooth(steps, columns, rows);
        smooth(steps, columns, rows);
        limit(steps, columns, rows, terrain.slope());
        int lowest = java.util.Arrays.stream(steps).min().orElse(0);
        for (int i = 0; i < steps.length; i++) {
            steps[i] -= lowest;
        }
        return new HeightMap(columns, rows, steps);
    }

    /**
     * Value noise: a height drawn every {@code size} corners and eased between, the slope flat at each drawn one so
     * a hill has a top rather than a point.
     */
    private static void octave(DeterministicRng rng, int[] steps, int columns, int rows, int size, int most) {
        if (most <= 0) {
            return;
        }
        int across = columns / size + 2;
        int down = rows / size + 2;
        var drawn = new int[across * down];
        for (int i = 0; i < drawn.length; i++) {
            drawn[i] = rng.nextInt(most + 1);
        }
        long whole = (long) size * size * size;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int at = y / size * across + x / size;
                long sx = ease(x % size, size);
                long sy = ease(y % size, size);
                long top = drawn[at] * (whole - sx) + drawn[at + 1] * sx;
                long bottom = drawn[at + across] * (whole - sx) + drawn[at + across + 1] * sx;
                steps[y * columns + x] += (int) ((top * (whole - sy) + bottom * sy) / (whole * whole));
            }
        }
    }

    /** 3t² − 2t³ across a span, in whole numbers: nothing at the start, {@code size³} at the end, flat at both. */
    private static long ease(int t, int size) {
        return (long) t * t * (3L * size - 2L * t);
    }

    /** Each chamber's floor drawn {@code percent} of the way toward the height at its middle. */
    private static void level(int[] steps, int columns, Cave cave, List<Room> rooms, int percent) {
        if (percent <= 0) {
            return;
        }
        var before = steps.clone();
        for (var room : rooms) {
            int target = before[room.centerCellY() * columns + room.centerCellX()];
            for (int y = room.y(); y < room.y() + room.h(); y++) {
                for (int x = room.x(); x < room.x() + room.w(); x++) {
                    if (!cave.isFloor(x, y)) {
                        continue;
                    }
                    for (int corner = 0; corner < 4; corner++) {
                        int at = (y + corner / 2) * columns + x + corner % 2;
                        steps[at] = before[at] + (target - before[at]) * percent / 100;
                    }
                }
            }
        }
    }

    /** Every corner the average of itself and the eight round it: the edges of a levelled floor rounded off. */
    private static void smooth(int[] steps, int columns, int rows) {
        var before = steps.clone();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int sum = 0;
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (x + dx >= 0 && y + dy >= 0 && x + dx < columns && y + dy < rows) {
                            sum += before[(y + dy) * columns + x + dx];
                            count++;
                        }
                    }
                }
                steps[y * columns + x] = sum / count;
            }
        }
    }

    /** The neighbours a pass has already been past, forward and then back. */
    private static final int[][] BEHIND = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}};
    private static final int[][] AHEAD = {{1, 1}, {0, 1}, {-1, 1}, {1, 0}};

    /**
     * No corner more than {@code slope} steps above any of its eight neighbours: the highest ground that is nowhere
     * steeper, found by lowering. A pass forward and a pass back settle it, as they settle a chessboard distance;
     * it is run again until nothing moves, which is once more at most.
     */
    static void limit(int[] steps, int columns, int rows, int slope) {
        boolean lowered = true;
        while (lowered) {
            lowered = false;
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < columns; x++) {
                    lowered |= lower(steps, columns, rows, x, y, slope, BEHIND);
                }
            }
            for (int y = rows - 1; y >= 0; y--) {
                for (int x = columns - 1; x >= 0; x--) {
                    lowered |= lower(steps, columns, rows, x, y, slope, AHEAD);
                }
            }
        }
    }

    private static boolean lower(int[] steps, int columns, int rows, int x, int y, int slope, int[][] neighbours) {
        int at = y * columns + x;
        int least = steps[at];
        for (var step : neighbours) {
            int nx = x + step[0];
            int ny = y + step[1];
            if (nx >= 0 && ny >= 0 && nx < columns && ny < rows) {
                least = Math.min(least, steps[ny * columns + nx] + slope);
            }
        }
        if (least < steps[at]) {
            steps[at] = least;
            return true;
        }
        return false;
    }
}
