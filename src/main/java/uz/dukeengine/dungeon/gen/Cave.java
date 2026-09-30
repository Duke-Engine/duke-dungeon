package uz.dukeengine.dungeon.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.map.ProceduralMap;
import uz.dukeengine.dungeon.world.Theme;

/**
 * The floor, carved out of the rock: a chamber grown in every footprint, a tunnel along every link, the edges worn,
 * and then everything taken away again that a large body could not fit through or the entrance could not reach.
 *
 * <p><b>Connected by construction, still.</b> The links are a spanning tree, every tunnel is stamped from the
 * middle of one chamber to the middle of the other with a brush as wide as the narrowest way is allowed to be, and
 * those cells — with the middle of every chamber — are {@code kept}: nothing that wears the floor afterwards may
 * turn them back to rock. So however ragged the rest comes out, the tree of tunnels is still there underneath it,
 * and every chamber hangs off the first one exactly as every room did when they were rectangles.
 *
 * <p><b>Nowhere narrower than a tunnel.</b> Wearing leaves spurs and slits a cell wide, and a cell-wide slit is
 * where the widest creature wedges — the reason {@code CorridorWidth} exists at all. So after it, every floor cell
 * that does not lie inside a square of floor that wide goes back to rock, and then whatever the entrance can no
 * longer reach goes too. What is left is one place, and a body that fits down a tunnel fits everywhere in it.
 *
 * <p>Every draw comes from the one {@link DeterministicRng}, in a fixed order, and nothing here reads a clock or
 * iterates a hash: a seed carves one cave.
 */
final class Cave {

    static final char STONE = '#';
    static final char FLOOR = '.';
    /**
     * A grove: ground the engine walks, with trees standing on it that are in the way only as far as their trunks —
     * see {@link Scenery}. Floor to the map it hands on; not floor to anything placed here, so nobody is set down
     * inside a tree.
     */
    static final char GROVE = ',';

    private final int width;
    private final int height;
    private final char[][] cells;
    /** Floor by promise: the tunnels and the middle of every chamber. Wearing never takes them. */
    private final boolean[][] kept;
    /** Every cell of every grove, in the order they were raised: where {@link Scenery} plants their trees. */
    private final List<int[]> groves = new ArrayList<>();
    /** The boss's keep once it is built in — see {@link #raise} — and null before, or on a floor with none. */
    private Keep keep;
    /** How wide a tunnel is stamped: the settings' corridor width, and never less than a cell. */
    private final int brush;

    private Cave(int width, int height, int corridorWidth) {
        this.width = width;
        this.height = height;
        this.brush = Math.max(1, corridorWidth);
        this.cells = new char[height][width];
        this.kept = new boolean[height][width];
        for (var row : cells) {
            java.util.Arrays.fill(row, STONE);
        }
    }

    /**
     * The whole floor: chambers, tunnels along {@code links} and a few more for the loops, worn to the terrain's
     * raggedness, cleaned of slits and islands of floor nobody could reach, and given its islands of rock.
     *
     * <p>{@code bossRoom} is the furthest chamber, which gets neither loops nor islands: the boss waits in it where no
     * keep fits, and where one does the boss stands in the keep beyond it, built once all this is carved.
     */
    static Cave carve(DeterministicRng rng, int width, int height, List<Room> rooms, List<Link> links, int bossRoom,
            int corridorWidth, int maxSpacing, Theme.Terrain terrain) {
        return carve(rng, width, height, rooms, links, bossRoom, corridorWidth, maxSpacing,
                java.util.Collections.nCopies(rooms.size(), terrain), (x, y) -> terrain.ragged(), room -> false);
    }

    /**
     * The same floor with its ground told region by region, as a floor that mixes biomes is: each chamber cut to
     * {@code ofRoom}'s terrain, a tunnel to the mean of its two ends', the loops to the chambers' mean, and every
     * cell worn as ragged as {@code raggedAt} says there. One terrain everywhere is exactly the call above — the same
     * numbers go to the same draws in the same order.
     *
     * <p>A chamber {@code groveIn} names has its islands raised as groves rather than rock: the same blocks in the
     * same places, drawn by the same dice, left walkable for the trees {@link Scenery} plants on them.
     */
    static Cave carve(DeterministicRng rng, int width, int height, List<Room> rooms, List<Link> links, int bossRoom,
            int corridorWidth, int maxSpacing, List<Theme.Terrain> ofRoom,
            java.util.function.IntBinaryOperator raggedAt, java.util.function.IntPredicate groveIn) {
        var cave = new Cave(width, height, corridorWidth);
        for (int i = 0; i < rooms.size(); i++) {
            cave.chamber(rng, rooms.get(i), ofRoom.get(i).ragged(), i == 0 ? WAY_IN : 3);
        }
        for (var link : links) {
            cave.tunnel(rng, rooms, ofRoom, link);
        }
        int loops = ofRoom.stream().mapToInt(Theme.Terrain::loops).sum() / Math.max(1, ofRoom.size());
        for (var loop : loops(rooms, links, bossRoom, maxSpacing, loops)) {
            cave.tunnel(rng, rooms, ofRoom, loop);
        }
        cave.wear(rng, raggedAt);
        cave.dropSlits();
        cave.dropUnreachable(rooms.getFirst().centerCellX(), rooms.getFirst().centerCellY());
        cave.raiseIslands(rng, rooms, bossRoom, ofRoom, groveIn);
        return cave;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    boolean isFloor(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && cells[y][x] == FLOOR;
    }

    /** Whether a cell is rock: not floor, and not a grove's ground either. */
    boolean isStone(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && cells[y][x] == STONE;
    }

    /** The map as {@code MapLoader} reads it: {@code #} is rock, {@code .} is floor — a grove's ground among it. */
    String walls() {
        var text = new StringBuilder(height * (width + 1));
        for (var row : cells) {
            text.append(row).append('\n');
        }
        return text.toString().replace(GROVE, FLOOR);
    }

    /**
     * The same map in storeys: all of the floor on the one, the keep's court among it, because the ground's height is
     * the relief's now.
     */
    String levels() {
        var text = new StringBuilder(height * (width + 1));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                text.append(cells[y][x] == STONE ? STONE : '0');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /**
     * Floor cells inside {@code room}'s footprint with floor all round them to {@code reach} cells, row by row: where
     * something may stand without hugging a wall ({@code reach} 1) or be stood without narrowing a way (2) — and never
     * on a keep's cells for any chamber but the keep: a chamber's footprint may reach into the rock a keep was later
     * built in.
     */
    List<int[]> openAround(Room room, int reach) {
        var open = new ArrayList<int[]>();
        for (int y = room.y(); y < room.y() + room.h(); y++) {
            for (int x = room.x(); x < room.x() + room.w(); x++) {
                if (floorWithin(x - reach, y - reach, x + reach, y + reach)
                        && (keep == null || keep.walls().equals(room) || !keep.holds(x, y))) {
                    open.add(new int[] {x, y});
                }
            }
        }
        return open;
    }

    // ---- carving ----

    /**
     * How much of the first chamber's middle is floor whatever happens to the rest: five cells across, which is the
     * fountain at the way in with a walk round it. A cramped glade with a grove or two in it left no such place, and
     * the heroes came in to no fountain at all.
     */
    private static final int WAY_IN = 5;

    /**
     * A chamber: an ellipse filling most of its footprint, a few lobes bulging out of it, and its middle — {@code core}
     * cells across — floor whatever the two made of it: the hero comes in there, the boss waits there, and the
     * tunnels meet there.
     */
    private void chamber(DeterministicRng rng, Room room, int ragged, int core) {
        double middleX = room.centerCellX() + 0.5;
        double middleY = room.centerCellY() + 0.5;
        int body = 100 - rng.nextInt(ragged / 2 + 1);
        ellipse(room, middleX, middleY, room.w() / 2.0 * body / 100, room.h() / 2.0 * body / 100);
        int lobes = rng.nextInt(ragged / 20 + 1);
        for (int lobe = 0; lobe < lobes; lobe++) {
            int offX = rng.nextInt(-room.w() / 4, room.w() / 4);
            int offY = rng.nextInt(-room.h() / 4, room.h() / 4);
            int size = 45 + rng.nextInt(26);
            ellipse(room, middleX + offX, middleY + offY, room.w() / 2.0 * size / 100, room.h() / 2.0 * size / 100);
        }
        core = Math.max(core, brush);
        for (int y = room.centerCellY() - (core - 1) / 2; y <= room.centerCellY() + core / 2; y++) {
            for (int x = room.centerCellX() - (core - 1) / 2; x <= room.centerCellX() + core / 2; x++) {
                keep(x, y);
            }
        }
    }

    /** Every cell of the footprint whose middle lies inside the ellipse. */
    private void ellipse(Room room, double centreX, double centreY, double radiusX, double radiusY) {
        for (int y = room.y(); y < room.y() + room.h(); y++) {
            for (int x = room.x(); x < room.x() + room.w(); x++) {
                double across = (x + 0.5 - centreX) / radiusX;
                double down = (y + 0.5 - centreY) / radiusY;
                if (across * across + down * down <= 1.0) {
                    open(x, y);
                }
            }
        }
    }

    /**
     * A tunnel from one chamber's middle to the other's: the straight line pushed sideways at its middle, and each
     * half again, so it wanders the way water-cut rock and a deer path do — and a pocket where it turns, now and
     * then, because a cave is not a pipe.
     */
    private void tunnel(DeterministicRng rng, List<Room> rooms, List<Theme.Terrain> ofRoom, Link link) {
        var from = rooms.get(link.from());
        var to = rooms.get(link.to());
        // Between two places, as much like either as the other.
        int winding = (ofRoom.get(link.from()).winding() + ofRoom.get(link.to()).winding()) / 2;
        int ragged = (ofRoom.get(link.from()).ragged() + ofRoom.get(link.to()).ragged()) / 2;
        var bends = new ArrayList<int[]>();
        var start = new int[] {from.centerCellX(), from.centerCellY()};
        bends.add(start);
        bend(rng, start, new int[] {to.centerCellX(), to.centerCellY()}, winding, 3, bends);
        for (int i = 1; i < bends.size(); i++) {
            line(bends.get(i - 1), bends.get(i));
            if (i < bends.size() - 1 && rng.nextInt(100) < ragged) {
                pocket(bends.get(i), 1 + rng.nextInt(2));
            }
        }
    }

    /** The points between {@code a} and {@code b}, each pushed sideways, then {@code b} itself. */
    private void bend(DeterministicRng rng, int[] a, int[] b, int winding, int depth, List<int[]> out) {
        int dx = b[0] - a[0];
        int dy = b[1] - a[1];
        int length = Math.max(Math.abs(dx), Math.abs(dy));
        if (depth == 0 || length < 4) {
            out.add(b);
            return;
        }
        int most = length * winding / 200;
        int push = rng.nextInt(-most, most);
        // Sideways is the line turned a quarter: (-dy, dx), scaled so the push is about that many cells.
        var middle = new int[] {inside((a[0] + b[0]) / 2 - dy * push / length, width),
                inside((a[1] + b[1]) / 2 + dx * push / length, height)};
        bend(rng, a, middle, winding, depth - 1, out);
        bend(rng, middle, b, winding, depth - 1, out);
    }

    /**
     * Bresenham's line with the brush stamped at every cell of it, and a step never taken diagonally: a diagonal
     * step leaves two brushes touching at a corner, which the pathfinder — rightly — will not squeeze through.
     */
    private void line(int[] a, int[] b) {
        int x = a[0];
        int y = a[1];
        int dx = Math.abs(b[0] - x);
        int dy = -Math.abs(b[1] - y);
        int stepX = x < b[0] ? 1 : -1;
        int stepY = y < b[1] ? 1 : -1;
        int error = dx + dy;
        stamp(x, y);
        while (x != b[0] || y != b[1]) {
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x += stepX;
                stamp(x, y);
            }
            if (doubled <= dx) {
                error += dx;
                y += stepY;
                stamp(x, y);
            }
        }
    }

    /** Where along an axis {@code across} cells long a brush may be stamped without cutting into the border. */
    private int inside(int at, int across) {
        int least = 1 + (brush - 1) / 2;
        return Math.clamp(at, least, Math.max(least, across - 2 - brush / 2));
    }

    /** One brush's worth of tunnel, kept. */
    private void stamp(int x, int y) {
        for (int dy = -(brush - 1) / 2; dy <= brush / 2; dy++) {
            for (int dx = -(brush - 1) / 2; dx <= brush / 2; dx++) {
                keep(x + dx, y + dy);
            }
        }
    }

    /** A round widening, not kept: wearing may take some of it back, which is what makes it a hollow in rock. */
    private void pocket(int[] at, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radius * radius + radius) {
                    open(at[0] + dx, at[1] + dy);
                }
            }
        }
    }

    /**
     * The tunnels beyond the tree: a way round rather than only a way through.
     *
     * <p>Only between chambers the same number of tunnels from the entrance, or one apart, and never into the
     * furthest — the boss waits in it where no keep fits, and beyond it, in the keep, where one does — so no loop is
     * a short cut: a walk still crosses as many chambers to reach the end as the tree says, and the floor's end is as
     * far off as it was. The nearest such pairs first, and none longer than a new chamber may sit from an old one;
     * no dice, so a loop cannot move anything else on the floor.
     */
    static List<Link> loops(List<Room> rooms, List<Link> links, int bossRoom, int maxSpacing, int percent) {
        int wanted = rooms.size() * percent / 100;
        if (wanted <= 0) {
            return List.of();
        }
        var depth = new int[rooms.size()];
        var joined = new HashSet<Long>();
        for (var link : links) {
            depth[link.to()] = depth[link.from()] + 1;
            joined.add(pair(link.from(), link.to()));
        }
        var candidates = new ArrayList<int[]>();
        for (int a = 0; a < rooms.size(); a++) {
            for (int b = a + 1; b < rooms.size(); b++) {
                if (a == bossRoom || b == bossRoom || joined.contains(pair(a, b))
                        || Math.abs(depth[a] - depth[b]) > 1) {
                    continue;
                }
                int distance = Math.abs(rooms.get(a).centerCellX() - rooms.get(b).centerCellX())
                        + Math.abs(rooms.get(a).centerCellY() - rooms.get(b).centerCellY());
                if (distance <= maxSpacing) {
                    candidates.add(new int[] {distance, a, b});
                }
            }
        }
        candidates.sort(Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1])
                .thenComparingInt(c -> c[2]));
        var loops = new ArrayList<Link>();
        for (int i = 0; i < Math.min(wanted, candidates.size()); i++) {
            loops.add(new Link(candidates.get(i)[1], candidates.get(i)[2]));
        }
        return loops;
    }

    private static long pair(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    // ---- wearing, and taking back what cannot be used ----

    /**
     * Two passes over the edges: a spur of floor or a notch of rock smoothed away, and the rest of the edge eaten
     * back or grown out, each by a chance the raggedness where it stands sets. Both passes read the floor as it was
     * before the pass, so the order the cells are visited in changes nothing but which dice they get.
     */
    private void wear(DeterministicRng rng, java.util.function.IntBinaryOperator raggedAt) {
        for (int pass = 0; pass < 2; pass++) {
            var before = new char[height][];
            for (int y = 0; y < height; y++) {
                before[y] = cells[y].clone();
            }
            for (int y = 1; y < height - 1; y++) {
                for (int x = 1; x < width - 1; x++) {
                    if (kept[y][x]) {
                        continue;
                    }
                    int around = floorAround(before, x, y);
                    int ragged = raggedAt.applyAsInt(x, y);
                    if (before[y][x] == FLOOR) {
                        if (around <= 2 || around <= 5 && rng.nextInt(100) < ragged / 2) {
                            cells[y][x] = STONE;
                        }
                    } else if (around >= 6 || around >= 3 && rng.nextInt(100) < ragged / 2) {
                        cells[y][x] = FLOOR;
                    }
                }
            }
        }
    }

    /** How many of the eight cells round one are floor. */
    private static int floorAround(char[][] map, int x, int y) {
        int floor = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && map[y + dy][x + dx] == FLOOR) {
                    floor++;
                }
            }
        }
        return floor;
    }

    /**
     * Every floor cell that lies in no square of floor as wide as a tunnel goes back to rock — a spur, a slit, a
     * corner a cell wide. Once is enough: a square that is all floor loses none of its cells, so nothing another
     * cell was relying on is ever taken.
     */
    private void dropSlits() {
        int side = Math.max(2, brush);
        var whole = new boolean[height][width];
        for (int y = 0; y + side <= height; y++) {
            for (int x = 0; x + side <= width; x++) {
                whole[y][x] = floorWithin(x, y, x + side - 1, y + side - 1);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (cells[y][x] == FLOOR && !kept[y][x] && !inWholeSquare(whole, side, x, y)) {
                    cells[y][x] = STONE;
                }
            }
        }
    }

    private static boolean inWholeSquare(boolean[][] whole, int side, int x, int y) {
        for (int dy = 0; dy < side; dy++) {
            for (int dx = 0; dx < side; dx++) {
                if (y - dy >= 0 && x - dx >= 0 && whole[y - dy][x - dx]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whatever floor the entrance cannot walk to — straight steps only, as the engine's own walk — is rock. */
    private void dropUnreachable(int startX, int startY) {
        var reached = new boolean[height][width];
        var queue = new ArrayDeque<int[]>();
        reached[startY][startX] = true;
        queue.add(new int[] {startX, startY});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            var at = queue.poll();
            for (var step : steps) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                if (isFloor(x, y) && !reached[y][x]) {
                    reached[y][x] = true;
                    queue.add(new int[] {x, y});
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!reached[y][x]) {
                    cells[y][x] = STONE;
                }
            }
        }
    }

    /**
     * Rock left standing inside the chambers: a pillar in a cavern, a grove in a glade. A block of one or two cells
     * across, only where floor rings it as wide as a tunnel — so it is always something to walk round and never
     * something that walls anything off — and never on the tunnels or a chamber's middle. None in the furthest
     * chamber, which is an arena and wants to be one: the boss waits in it where no keep fits, and where one does the
     * boss stands in the keep beyond it — sited after the carving, so the chamber is carved the same either way. In a
     * chamber {@code groveIn} names, the block is a grove instead: the same cells, left walkable, for trees to stand
     * on.
     */
    private void raiseIslands(DeterministicRng rng, List<Room> rooms, int bossRoom, List<Theme.Terrain> ofRoom,
            java.util.function.IntPredicate groveIn) {
        int margin = Math.max(2, brush);
        for (int i = 0; i < rooms.size(); i++) {
            ProceduralMap.PerRoom perRoom = ofRoom.get(i).islandsPerRoom();
            if (perRoom.max() <= 0) {
                continue;
            }
            char island = groveIn.test(i) ? GROVE : STONE;
            int count = rng.nextInt(perRoom.min(), perRoom.max());
            for (int n = 0; n < count && i != bossRoom; n++) {
                int size = 1 + rng.nextInt(2);
                var spots = islandSpots(rooms.get(i), size, margin);
                if (spots.isEmpty()) {
                    break;
                }
                var spot = spots.get(rng.nextInt(spots.size()));
                for (int y = spot[1]; y < spot[1] + size; y++) {
                    for (int x = spot[0]; x < spot[0] + size; x++) {
                        cells[y][x] = island;
                        if (island == GROVE) {
                            groves.add(new int[] {x, y});
                        }
                    }
                }
            }
        }
    }

    /** Every cell of every grove, in the order they were raised. */
    List<int[]> groves() {
        return groves;
    }

    /** The keep built into this floor, or null. */
    Keep keep() {
        return keep;
    }

    /**
     * Build {@code keep} into the carved floor: its ring of wall, its court and doorway, the threshold before the
     * doorway, and a straight road from beyond the threshold to the middle of the chamber it hangs off in
     * {@code rooms}. All of it kept, the ring rock: a built thing is not worn. The road never touches the ring — it
     * leaves the gate's side outward, the side having been chosen for that, and a straight line leaving a square
     * outward never meets it again.
     */
    void raise(Keep keep, List<Room> rooms) {
        this.keep = keep;
        var walls = keep.walls();
        for (int y = walls.y() - 1; y <= walls.y() + walls.h(); y++) {
            for (int x = walls.x() - 1; x <= walls.x() + walls.w(); x++) {
                if (keep.isCourt(x, y) || keep.isDoorway(x, y) || keep.isThreshold(x, y)) {
                    keep(x, y);
                } else if (keep.holds(x, y)) {
                    cells[y][x] = STONE;
                }
            }
        }
        var to = rooms.get(keep.chamber());
        line(keep.roadStart(), new int[] {to.centerCellX(), to.centerCellY()});
    }

    /** Where a block {@code size} across may stand in {@code room}: its own cells free, floor all round it. */
    private List<int[]> islandSpots(Room room, int size, int margin) {
        var spots = new ArrayList<int[]>();
        for (int y = room.y(); y + size <= room.y() + room.h(); y++) {
            for (int x = room.x(); x + size <= room.x() + room.w(); x++) {
                if (!keptWithin(x, y, x + size - 1, y + size - 1)
                        && floorWithin(x - margin, y - margin, x + size - 1 + margin, y + size - 1 + margin)) {
                    spots.add(new int[] {x, y});
                }
            }
        }
        return spots;
    }

    // ---- cells ----

    /** Floor, if it is inside the border of rock every map keeps. */
    private void open(int x, int y) {
        if (x >= 1 && y >= 1 && x < width - 1 && y < height - 1) {
            cells[y][x] = FLOOR;
        }
    }

    /** Floor for good. */
    private void keep(int x, int y) {
        if (x >= 1 && y >= 1 && x < width - 1 && y < height - 1) {
            cells[y][x] = FLOOR;
            kept[y][x] = true;
        }
    }

    private boolean floorWithin(int left, int top, int right, int bottom) {
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (!isFloor(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean keptWithin(int left, int top, int right, int bottom) {
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (kept[y][x]) {
                    return true;
                }
            }
        }
        return false;
    }
}
