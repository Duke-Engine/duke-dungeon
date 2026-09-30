package uz.dukeengine.dungeon.gen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;

/**
 * The boss's keep: a square court on the ground, walled round, at the far end of the floor — a gate in the middle of
 * the side it is approached from, a threshold of the keep's stone before the gate, and a straight road from the
 * threshold to the chamber it hangs off.
 *
 * <p>Built in solid rock once the cave is carved (see {@link #site}), so it takes no floor from anything and cuts no
 * tunnel: whatever was connected stays connected, and the only way into the court is over the threshold and through
 * the gate. Whole numbers and a fixed order throughout, and no dice: a seed names one keep.
 *
 * @param walls   the square it stands in, its ring of wall included — odd across, so the court and the doorway each
 *     have a middle cell
 * @param side    which of its sides the gate is in
 * @param chamber the chamber its road runs to, by its place in the floor's rooms
 */
public record Keep(Room walls, Side side, int chamber) {

    /** The four sides of a keep, each with the way out of it. North is toward row 0, as the map is drawn. */
    public enum Side {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        final int dx;
        final int dy;

        Side(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    /** How far the doorway runs along its wall: the gate's cell and one either side, wider than any tunnel. */
    static final int DOORWAY = 3;

    /** Cells across, walls included. */
    public int size() {
        return walls.w();
    }

    /** The doorway's middle cell, in the ring on the gate's side: where the gate stands. */
    public int[] gate() {
        int middle = size() / 2;
        return switch (side) {
            case NORTH -> new int[] {walls.x() + middle, walls.y()};
            case SOUTH -> new int[] {walls.x() + middle, walls.y() + size() - 1};
            case WEST -> new int[] {walls.x(), walls.y() + middle};
            case EAST -> new int[] {walls.x() + size() - 1, walls.y() + middle};
        };
    }

    /**
     * Which way the gate's wall runs, as a facing in radians: none along the map's x, a quarter turn along its y. A
     * gate turned so stands across its doorway.
     */
    public float facing() {
        return side.dx == 0 ? 0f : (float) (StrictMath.PI / 2);
    }

    /** Whether a cell is the doorway: the gate's own and one either side of it, along the wall. */
    public boolean isDoorway(int x, int y) {
        var gate = gate();
        return side.dx == 0
                ? y == gate[1] && Math.abs(x - gate[0]) <= DOORWAY / 2
                : x == gate[0] && Math.abs(y - gate[1]) <= DOORWAY / 2;
    }

    /** Whether a cell is the court: inside the ring. */
    public boolean isCourt(int x, int y) {
        return x > walls.x() && y > walls.y() && x < walls.x() + size() - 1 && y < walls.y() + size() - 1;
    }

    /**
     * Where the boss's first four guards stand: the court's corners, a cell in from each wall — one diagonal and then
     * the other, the order the ring round the boss takes its own corners in, so the first two named share a diagonal.
     */
    public List<int[]> corners() {
        int near = 2;
        int far = size() - 3;
        return List.of(new int[] {walls.x() + near, walls.y() + near}, new int[] {walls.x() + far, walls.y() + far},
                new int[] {walls.x() + far, walls.y() + near}, new int[] {walls.x() + near, walls.y() + far});
    }

    /**
     * Whether a cell is the threshold: floor a step out from the doorway and as wide as it, drawn in the keep's
     * stone — where the stair stood while the court was a storey up.
     */
    public boolean isThreshold(int x, int y) {
        return isDoorway(x - side.dx, y - side.dy);
    }

    /**
     * Whether a cell belongs to the keep — its square or its threshold: what is drawn as the keep, and strewn with
     * nothing.
     */
    public boolean holds(int x, int y) {
        return x >= walls.x() && y >= walls.y() && x < walls.x() + size() && y < walls.y() + size()
                || isThreshold(x, y);
    }

    /** Where the road leaves from: beyond the threshold's middle, two cells out from the gate. */
    int[] roadStart() {
        var gate = gate();
        return new int[] {gate[0] + 2 * side.dx, gate[1] + 2 * side.dy};
    }

    /**
     * Where a keep goes on a carved floor, or null where none fits: in solid rock, beside the deepest chamber that has
     * room for one, as large as it can be there.
     *
     * <p>Every size but the last is tried only beside the deepest chambers — at most one tunnel short of the deepest
     * — largest first; failing that, the deepest chamber any size fits beside, largest first. Beside a chamber the
     * square with the shortest road wins, and of two as short the first in reading order.
     *
     * @param rooms      the chambers, the first where the heroes come in
     * @param links      the tunnels joining them, a tree grown out of the first
     * @param sizes      how many cells across, largest first
     * @param maxSpacing the longest a road may be
     */
    static Keep site(Cave cave, List<Room> rooms, List<Link> links, List<Integer> sizes, int maxSpacing) {
        if (sizes.isEmpty() || rooms.isEmpty()) {
            return null;
        }
        var depth = new int[rooms.size()];
        int deepest = 0;
        for (var link : links) {
            depth[link.to()] = depth[link.from()] + 1;
            deepest = Math.max(deepest, depth[link.to()]);
        }
        var order = new ArrayList<Integer>();
        for (int room = 0; room < rooms.size(); room++) {
            order.add(room);
        }
        order.sort(Comparator.comparingInt((Integer room) -> -depth[room]).thenComparingInt(room -> room));
        var open = openCells(cave);
        for (int s = 0; s < sizes.size() - 1; s++) {
            for (int room : order) {
                if (depth[room] < deepest - 1) {
                    break;
                }
                var keep = beside(cave, open, rooms, room, sizes.get(s), maxSpacing);
                if (keep != null) {
                    return keep;
                }
            }
        }
        for (int room : order) {
            for (int size : sizes) {
                var keep = beside(cave, open, rooms, room, size, maxSpacing);
                if (keep != null) {
                    return keep;
                }
            }
        }
        return null;
    }

    /**
     * The keep {@code size} across beside chamber {@code room} with the shortest road, or null: its square and a cell
     * round it all rock and inside the map's border, its road no longer than {@code maxSpacing} and leaving from
     * inside the border too.
     */
    private static Keep beside(Cave cave, int[] open, List<Room> rooms, int room, int size, int maxSpacing) {
        var chamber = rooms.get(room);
        Keep best = null;
        int shortest = Integer.MAX_VALUE;
        for (int y = 2; y + size + 2 <= cave.height(); y++) {
            for (int x = 2; x + size + 2 <= cave.width(); x++) {
                int road = Math.abs(chamber.centerCellX() - (x + size / 2))
                        + Math.abs(chamber.centerCellY() - (y + size / 2)) - size / 2;
                if (road > maxSpacing || road >= shortest
                        || openWithin(open, cave.width(), x - 1, y - 1, size + 2) > 0) {
                    continue;
                }
                var keep = new Keep(new Room(x, y, size, size), facing(chamber, x + size / 2, y + size / 2), room);
                var start = keep.roadStart();
                if (start[0] < 1 || start[1] < 1 || start[0] > cave.width() - 2 || start[1] > cave.height() - 2) {
                    continue;
                }
                best = keep;
                shortest = road;
            }
        }
        return best;
    }

    /** The side whose way out points most nearly at the chamber's middle; ties go north, east, south, west. */
    private static Side facing(Room chamber, int middleX, int middleY) {
        int dx = chamber.centerCellX() - middleX;
        int dy = chamber.centerCellY() - middleY;
        var best = Side.NORTH;
        int most = Integer.MIN_VALUE;
        for (var side : Side.values()) {
            int toward = side.dx * dx + side.dy * dy;
            if (toward > most) {
                most = toward;
                best = side;
            }
        }
        return best;
    }

    /** How many cells are not rock in every rectangle from the corner: {@code (width + 1)} by {@code (height + 1)}. */
    private static int[] openCells(Cave cave) {
        int across = cave.width() + 1;
        var sums = new int[across * (cave.height() + 1)];
        for (int y = 0; y < cave.height(); y++) {
            for (int x = 0; x < cave.width(); x++) {
                sums[(y + 1) * across + x + 1] = sums[y * across + x + 1] + sums[(y + 1) * across + x]
                        - sums[y * across + x] + (cave.isStone(x, y) ? 0 : 1);
            }
        }
        return sums;
    }

    /** How many cells are not rock in the square {@code side} across from {@code (left, top)}. */
    private static int openWithin(int[] sums, int width, int left, int top, int side) {
        int across = width + 1;
        int right = left + side;
        int bottom = top + side;
        return sums[bottom * across + right] - sums[top * across + right] - sums[bottom * across + left]
                + sums[top * across + left];
    }
}
