package uz.dukeengine.dungeon.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import uz.dukeengine.dungeon.content.Biomes;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.world.Theme;

/**
 * Which biome every cell of a floor is, when the floor mixes them.
 *
 * <p><b>Placed by climate.</b> Two broad fields of weather are drawn across the floor — how wild, how alive — each
 * ranked to cover the whole scale evenly, and drifted by depth as the map says. Every cell grows the biome whose
 * {@code Climate} point is nearest the weather there. So a biome is wherever the weather suits it and two meet
 * where their weathers do, which is what makes a mixed floor read as a landscape rather than a quilt — and ranking
 * is what gives each biome the share of a floor its share of the climate says, and several to nearly every floor.
 *
 * <p><b>Whole in every chamber.</b> A chamber takes the biome at its middle over its whole footprint: a room is
 * one place, and it is cut to that place's terrain.
 *
 * <p><b>Never a speck.</b> A patch of fewer than {@link #SMALLEST_REGION} cells with no chamber in it goes to the
 * biome it borders most, until none is left.
 *
 * <p>Drawn from a stream of its own, so turning biomes on moves no other draw on the floor, and a seed grows one
 * landscape on every machine.
 */
public final class BiomeMap {

    /** The fewest cells a patch of one biome may be, unless a chamber stands in it. */
    static final int SMALLEST_REGION = 24;

    private final int width;
    private final List<Theme> biomes;
    private final int[] cells;
    private final int[] rooms;

    private BiomeMap(int width, List<Theme> biomes, int[] cells, int[] rooms) {
        this.width = width;
        this.biomes = List.copyOf(biomes);
        this.cells = cells;
        this.rooms = rooms;
    }

    /** The biomes of a floor {@code width} by {@code height} at {@code depth}, its chambers' footprints whole. */
    static BiomeMap draw(long seed, int depth, int width, int height, List<Room> rooms, Biomes settings) {
        var rng = new DeterministicRng(seed ^ 0x636C696D617465L);
        var wild = weather(rng, width, height, settings.size());
        var alive = weather(rng, width, height, settings.size());
        int drift = Math.max(0, depth - 1);
        var all = settings.all();
        var cells = new int[width * height];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = nearest(all, Math.clamp(wild[i] + settings.perDepth().wild() * drift, 0, 100),
                    Math.clamp(alive[i] + settings.perDepth().alive() * drift, 0, 100));
        }
        var ofRoom = new int[rooms.size()];
        var chamber = new boolean[cells.length];
        for (int i = 0; i < rooms.size(); i++) {
            var room = rooms.get(i);
            ofRoom[i] = cells[room.centerCellY() * width + room.centerCellX()];
            for (int y = room.y(); y < room.y() + room.h(); y++) {
                for (int x = room.x(); x < room.x() + room.w(); x++) {
                    cells[y * width + x] = ofRoom[i];
                    chamber[y * width + x] = true;
                }
            }
        }
        absorbSpecks(cells, chamber, width, height, all.size());
        return new BiomeMap(width, all, cells, ofRoom);
    }

    /** The biome of the cell at {@code (x, y)}. */
    public Theme at(int x, int y) {
        return biomes.get(indexAt(x, y));
    }

    /** Which of {@link #biomes()} the cell at {@code (x, y)} is. */
    public int indexAt(int x, int y) {
        return cells[y * width + x];
    }

    /** The biome chamber {@code room} is, whole. */
    public Theme ofRoom(int room) {
        return biomes.get(rooms[room]);
    }

    /** Every biome the map could hold, in the file's order — not only the ones this floor grew. */
    public List<Theme> biomes() {
        return biomes;
    }

    /** The same biomes in the same cells: a floor drawn twice from one seed is one floor, biomes and all. */
    @Override
    public boolean equals(Object other) {
        return other instanceof BiomeMap map && width == map.width && biomes.equals(map.biomes)
                && Arrays.equals(cells, map.cells) && Arrays.equals(rooms, map.rooms);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(cells) + Arrays.hashCode(rooms);
    }

    /**
     * The hills the whole floor is drawn with before each region scales them: as tall as the tallest rise any of its
     * biomes has, the size its cells' biomes average, and no steeper than the gentlest of them allows — so every
     * region's hills fit under it, and a border between two is never where a cliff appears.
     */
    Theme.Terrain hills() {
        var count = new int[biomes.size()];
        for (int cell : cells) {
            count[cell]++;
        }
        int rise = 0;
        long size = 0;
        int slope = Integer.MAX_VALUE;
        for (int kind = 0; kind < biomes.size(); kind++) {
            if (count[kind] == 0) {
                continue;
            }
            var terrain = biomes.get(kind).terrain();
            rise = Math.max(rise, terrain.rise());
            size += (long) count[kind] * terrain.hillSize();
            slope = Math.min(slope, terrain.slope());
        }
        return new Theme.Terrain(0, 0, 0, new uz.dukeengine.dungeon.map.ProceduralMap.PerRoom(0, 0), rise,
                (int) Math.max(1, size / cells.length), slope, 0);
    }

    /** How high the ground rises at the corner {@code (x, y)} of the cells: the mean of the cells that meet there. */
    int riseAt(int x, int y) {
        int height = cells.length / width;
        int sum = 0;
        int meeting = 0;
        for (int cy = y - 1; cy <= y; cy++) {
            for (int cx = x - 1; cx <= x; cx++) {
                if (cx >= 0 && cy >= 0 && cx < width && cy < height) {
                    sum += at(cx, cy).terrain().rise();
                    meeting++;
                }
            }
        }
        return sum / Math.max(1, meeting);
    }

    /**
     * One field of weather: broad sweeps {@code size} cells across with smaller ones riding them, then ranked, so
     * every value from 0 to 100 covers as much of the floor as every other.
     *
     * <p>Ranked rather than stretched between the floor's extremes, and that is the balance of the whole thing.
     * Summed noise piles up in the middle of its range, so a stretched field is mostly middling weather, and a
     * biome whose point sits toward a corner of the climate — the green wood, lushest and wildest — grew in scraps
     * while whichever sat nearest the middle took the floor. Ranked, a biome's share of a floor is the share of the
     * climate that is nearer its point than any other's; ties keep the order of the cells, so it is still one map.
     */
    private static int[] weather(DeterministicRng rng, int width, int height, int size) {
        var field = new int[width * height];
        Relief.octave(rng, field, width, height, size, 2000);
        Relief.octave(rng, field, width, height, Math.max(1, size / 2), 1000);
        var order = new Integer[field.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(field[a], field[b]));
        var ranked = new int[field.length];
        for (int rank = 0; rank < order.length; rank++) {
            ranked[order[rank]] = (int) ((long) rank * 100 / Math.max(1, order.length - 1));
        }
        return ranked;
    }

    /** The biome whose point is nearest this weather; a tie goes to the one the file lists first. */
    private static int nearest(List<Theme> biomes, int wild, int alive) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < biomes.size(); i++) {
            var point = biomes.get(i).climate();
            long across = wild - point.wild();
            long down = alive - point.alive();
            long distance = across * across + down * down;
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Every patch too small to be a place, and holding no chamber, becomes the biome it borders most — ties to the
     * one listed first — and again, until there are none. Each pass only ever merges, so it ends.
     */
    private static void absorbSpecks(int[] cells, boolean[] chamber, int width, int height, int kinds) {
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean merged = true;
        while (merged) {
            merged = false;
            var seen = new boolean[cells.length];
            for (int start = 0; start < cells.length; start++) {
                if (seen[start]) {
                    continue;
                }
                int biome = cells[start];
                var patch = new ArrayList<Integer>();
                var border = new int[kinds];
                boolean holdsChamber = false;
                var queue = new ArrayDeque<Integer>();
                queue.add(start);
                seen[start] = true;
                while (!queue.isEmpty()) {
                    int at = queue.poll();
                    patch.add(at);
                    holdsChamber |= chamber[at];
                    for (var step : steps) {
                        int x = at % width + step[0];
                        int y = at / width + step[1];
                        if (x < 0 || y < 0 || x >= width || y >= height) {
                            continue;
                        }
                        int next = y * width + x;
                        if (cells[next] != biome) {
                            border[cells[next]]++;
                        } else if (!seen[next]) {
                            seen[next] = true;
                            queue.add(next);
                        }
                    }
                }
                if (patch.size() < SMALLEST_REGION && !holdsChamber) {
                    int into = 0;
                    for (int kind = 1; kind < kinds; kind++) {
                        if (border[kind] > border[into]) {
                            into = kind;
                        }
                    }
                    if (border[into] > 0) {
                        for (int at : patch) {
                            cells[at] = into;
                        }
                        merged = true;
                    }
                }
            }
        }
    }
}
