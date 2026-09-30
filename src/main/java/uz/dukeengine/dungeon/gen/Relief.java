package uz.dukeengine.dungeon.gen;

import java.util.List;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.world.Theme;

/**
 * The ground under a floor: hills and hollows, the chambers laid level or not as the terrain says, the boss's keep
 * always level whatever it says, and nowhere steep enough to be a cliff.
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
        return of(seed, cave, rooms, terrain, (x, y) -> terrain.rise(),
                java.util.Collections.nCopies(rooms.size(), terrain.level()));
    }

    /**
     * The same ground with its hills told region by region, as a floor that mixes biomes is: the hills drawn once
     * at {@code floor}'s size and height and each corner's then scaled to the rise {@code riseAt} gives it, each
     * chamber levelled as much as {@code levelOf} says, and the whole held to {@code floor}'s slope. One terrain
     * everywhere scales every corner by exactly one, and is the call above.
     *
     * <p>Where the cave has a keep it is set level whatever {@code levelOf} says, and the ground round it eased down
     * to meet it by the same slope.
     */
    static HeightMap of(long seed, Cave cave, List<Room> rooms, Theme.Terrain floor,
            java.util.function.IntBinaryOperator riseAt, List<Integer> levelOf) {
        if (floor.rise() <= 0) {
            return null;
        }
        int columns = cave.width() + 1;
        int rows = cave.height() + 1;
        var rng = new DeterministicRng(seed ^ 0x72656C696566L);
        var steps = new int[columns * rows];
        // Two sizes of hill, the broad one twice the height of the one riding on it.
        int broad = floor.rise() * 2 / 3;
        octave(rng, steps, columns, rows, floor.hillSize(), broad);
        octave(rng, steps, columns, rows, Math.max(1, floor.hillSize() / 2), floor.rise() - broad);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                steps[y * columns + x] = (int) ((long) steps[y * columns + x] * riseAt.applyAsInt(x, y)
                        / floor.rise());
            }
        }
        level(steps, columns, cave, rooms, levelOf);
        smooth(steps, columns, rows);
        smooth(steps, columns, rows);
        limit(steps, columns, rows, floor.slope());
        var keep = cave.keep();
        if (keep != null) {
            // Built level: set down at the lowest ground within two cells of it, and the ground round it eased down
            // to meet it by the same slope. Nothing near it is lower, so it is never lowered again.
            flatten(steps, columns, rows, keep.walls());
            limit(steps, columns, rows, floor.slope());
        }
        int lowest = java.util.Arrays.stream(steps).min().orElse(0);
        for (int i = 0; i < steps.length; i++) {
            steps[i] -= lowest;
        }
        return new HeightMap(columns, rows, steps);
    }

    /**
     * Value noise: a height drawn every {@code size} corners and eased between, the slope flat at each drawn one so
     * a hill has a top rather than a point. The biomes' weather is drawn with it too — see {@link BiomeMap}.
     */
    static void octave(DeterministicRng rng, int[] steps, int columns, int rows, int size, int most) {
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

    /** Each chamber's floor drawn its own {@code levelOf} percent of the way toward the height at its middle. */
    private static void level(int[] steps, int columns, Cave cave, List<Room> rooms, List<Integer> levelOf) {
        var before = steps.clone();
        for (int i = 0; i < rooms.size(); i++) {
            var room = rooms.get(i);
            int percent = levelOf.get(i);
            if (percent <= 0) {
                continue;
            }
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

    /**
     * Every corner of {@code square} and of the ring of cells round it — the keep, and its threshold — set to the
     * lowest corner within two cells of that ring.
     */
    private static void flatten(int[] steps, int columns, int rows, Room square) {
        int left = square.x() - 1;
        int top = square.y() - 1;
        int right = square.x() + square.w() + 1;
        int bottom = square.y() + square.h() + 1;
        int lowest = Integer.MAX_VALUE;
        for (int y = Math.max(0, top - 2); y <= Math.min(rows - 1, bottom + 2); y++) {
            for (int x = Math.max(0, left - 2); x <= Math.min(columns - 1, right + 2); x++) {
                lowest = Math.min(lowest, steps[y * columns + x]);
            }
        }
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                steps[y * columns + x] = lowest;
            }
        }
    }
}
