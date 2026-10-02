package uz.dukeengine.dungeon.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import uz.dukeengine.dungeon.content.Biomes;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.world.Theme;

/**
 * Which biome every cell of a floor is, when the floor mixes them.
 *
 * <p><b>Places first.</b> A floor is laid out in a few large regions — as many as the map's {@code BiomeCount}
 * draws, three or four as shipped — before any of them is given a biome, so a place is a place and not wherever the
 * weather happened to suit. Each region has a heart, and the hearts are scattered far from one another and clear of
 * the edges. Every cell belongs to the nearest heart after its position has been pushed about by two broad fields of
 * noise, so the borders wander as a coast does. How broad the fields are, and how hard they push, grow with how big
 * a region is — the square root of the floor's cells over their number — so a world nine hundred cells across is laid
 * out in the same few places a floor ninety across is, only larger, and never in more of them.
 *
 * <p><b>One piece each, and none small.</b> Pushing cuts a region in two now and then, and a sliver of the map is no
 * place. Each region keeps only its largest piece; any other piece, and any region under {@code MinBiomeShare} of
 * the cells, is joined whole to the region it borders most, the smallest first, until none is left. Regions are only
 * ever joined, so a floor may come out in fewer places than it was laid out in, and never in more.
 *
 * <p><b>Then a biome for each.</b> Two broad fields of weather are drawn across the floor — how wild, how alive —
 * each ranked to cover the whole scale evenly, and drifted by depth as the map says. The largest region chooses
 * first: it takes the biome whose {@code Climate} point is nearest the weather at its heart, and each region after
 * it the nearest of those not yet taken, so no biome is grown twice while there are biomes enough. When regions
 * outnumber the biomes, the nearest of all.
 *
 * <p><b>Whole in every chamber.</b> A chamber takes the region under its middle over its whole footprint, and does so
 * before regions are joined, so what is joined is joined with its chambers in it: a chamber is never cut in two, and
 * never left behind to be a speck of one biome in the middle of another.
 *
 * <p>Drawn from a stream of its own, so turning biomes on moves no other draw on the floor, and a seed grows one
 * landscape on every machine.
 */
public final class BiomeMap {

    /** How many places are tried for each heart after the first, the farthest from the rest being kept. */
    private static final int CANDIDATES = 8;

    /** The widest a region's span is taken as in warping the borders: see {@link #warp}. */
    private static final int WARP_CEILING = 1000;

    private final int width;
    private final List<Theme> biomes;
    private final int[] cells;
    /** Where each chamber's middle is, as a cell: the biome a chamber is, is the one its middle reads. */
    private final int[] middles;
    /** Which region every cell is in, and which biome each region is: how the cells came to be, kept for a keep. */
    private final int[] region;
    private final int[] biomeOf;

    private BiomeMap(int width, List<Theme> biomes, int[] cells, int[] middles, int[] region, int[] biomeOf) {
        this.width = width;
        this.biomes = List.copyOf(biomes);
        this.cells = cells;
        this.middles = middles;
        this.region = region;
        this.biomeOf = biomeOf;
    }

    /** The biomes of a floor {@code width} by {@code height} at {@code depth}, its chambers' footprints whole. */
    static BiomeMap draw(long seed, int depth, int width, int height, List<Room> rooms, Biomes settings) {
        var rng = new DeterministicRng(seed ^ 0x636C696D617465L);
        int total = width * height;
        // As many as were drawn, and no more than the share lets fit or the floor has cells for.
        int count = Math.min(rng.nextInt(settings.count().fewest(), settings.count().most()),
                Math.min(100 / settings.minShare(), total));
        int span = (int) Math.sqrt((double) total / count);
        var hearts = hearts(rng, count, width, height, span / 2);
        var region = lay(rng, hearts, width, height, span);
        for (var room : rooms) {
            fill(region, width, room);
        }
        join(region, width, count, settings.minShare());

        return of(width, settings.all(), region, choose(rng, region, hearts, depth, width, height, settings), rooms);
    }

    /** A map from regions already laid, and the biome each is, with its chambers standing in them. */
    static BiomeMap of(int width, List<Theme> biomes, int[] region, int[] biomeOf, List<Room> rooms) {
        var middles = new int[rooms.size()];
        for (int i = 0; i < middles.length; i++) {
            middles[i] = middleOf(rooms.get(i), width);
        }
        return new BiomeMap(width, biomes, cellsOf(region, biomeOf), middles, region, biomeOf);
    }

    /** The biome of the cell at {@code (x, y)}. */
    public Theme at(int x, int y) {
        return biomes.get(indexAt(x, y));
    }

    /** Which of {@link #biomes()} the cell at {@code (x, y)} is. */
    public int indexAt(int x, int y) {
        return cells[y * width + x];
    }

    /** The biome chamber {@code room} is, whole: the one its middle reads, whatever has happened to the map since. */
    public Theme ofRoom(int room) {
        return biomes.get(cells[middles[room]]);
    }

    /**
     * The same map with one more chamber, whole in the biome under its middle, numbered after the rest: a keep, built
     * once the floor was carved. Standing across a border it may cut the far side off another region; that piece
     * goes where any piece cut off does, and no region is left in two — chamber and all, so a chamber that was in it
     * is the biome of the region it went to, which {@link #ofRoom} reads off the cells and so always says.
     */
    BiomeMap withRoom(Room room) {
        var painted = region.clone();
        fill(painted, width, room);
        join(painted, width, biomeOf.length, 0);
        var more = Arrays.copyOf(middles, middles.length + 1);
        more[middles.length] = middleOf(room, width);
        return new BiomeMap(width, biomes, cellsOf(painted, biomeOf), more, painted, biomeOf);
    }

    /** Every biome the map could hold, in the file's order — not only the ones this floor grew. */
    public List<Theme> biomes() {
        return biomes;
    }

    /** The same biomes in the same cells: a floor drawn twice from one seed is one floor, biomes and all. */
    @Override
    public boolean equals(Object other) {
        return other instanceof BiomeMap map && width == map.width && biomes.equals(map.biomes)
                && Arrays.equals(cells, map.cells) && Arrays.equals(middles, map.middles);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(cells) + Arrays.hashCode(middles);
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

    /** The cell at {@code room}'s middle. */
    private static int middleOf(Room room, int width) {
        return room.centerCellY() * width + room.centerCellX();
    }

    /** {@code room}'s whole footprint given to the region under its middle. */
    private static void fill(int[] region, int width, Room room) {
        int under = region[middleOf(room, width)];
        for (int y = room.y(); y < room.y() + room.h(); y++) {
            for (int x = room.x(); x < room.x() + room.w(); x++) {
                region[y * width + x] = under;
            }
        }
    }

    /** Every cell's biome, given the region it is in and the biome each region is. */
    private static int[] cellsOf(int[] region, int[] biomeOf) {
        var cells = new int[region.length];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = biomeOf[region[i]];
        }
        return cells;
    }

    /**
     * Where each region's heart is, as a cell: the first anywhere {@code margin} cells clear of the edges, and each
     * one after it the farthest from those already placed of {@link #CANDIDATES} places drawn the same way (the best
     * candidate: a few tries rather than one, which is what keeps hearts apart without setting them on a grid). A
     * tie goes to the place drawn first.
     */
    private static int[] hearts(DeterministicRng rng, int count, int width, int height, int margin) {
        int clearX = Math.min(margin, (width - 1) / 2);
        int clearY = Math.min(margin, (height - 1) / 2);
        var hearts = new int[count];
        for (int placed = 0; placed < count; placed++) {
            long farthest = -1;
            for (int candidate = 0; candidate < (placed == 0 ? 1 : CANDIDATES); candidate++) {
                int x = clearX + rng.nextInt(width - 2 * clearX);
                int y = clearY + rng.nextInt(height - 2 * clearY);
                long nearest = Long.MAX_VALUE;
                for (int other = 0; other < placed; other++) {
                    long across = x - hearts[other] % width;
                    long down = y - hearts[other] / width;
                    nearest = Math.min(nearest, across * across + down * down);
                }
                if (nearest > farthest) {
                    farthest = nearest;
                    hearts[placed] = y * width + x;
                }
            }
        }
        return hearts;
    }

    /**
     * Which region every cell is in: the nearest heart to the cell's position once two fields of noise have pushed it
     * about, one across and one down. A tie goes to the lower heart.
     *
     * <p>The fields sweep half a region's span and push a third of it, so a border is moved about by a fraction of a
     * region either way: enough to wander, too little to carry a region off its own heart. Their size follows the
     * span rather than the floor's, so a larger floor's borders wander more and not merely longer.
     */
    private static int[] lay(DeterministicRng rng, int[] hearts, int width, int height, int span) {
        var pushes = warp(rng, width, height, span);
        var heartX = new int[hearts.length];
        var heartY = new int[hearts.length];
        for (int heart = 0; heart < hearts.length; heart++) {
            heartX[heart] = hearts[heart] % width;
            heartY[heart] = hearts[heart] / width;
        }
        var region = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int at = y * width + x;
                long pushedX = x + pushes[0][at];
                long pushedY = y + pushes[1][at];
                long nearest = Long.MAX_VALUE;
                for (int heart = 0; heart < hearts.length; heart++) {
                    long dx = pushedX - heartX[heart];
                    long dy = pushedY - heartY[heart];
                    long distance = dx * dx + dy * dy;
                    if (distance < nearest) {
                        nearest = distance;
                        region[at] = heart;
                    }
                }
            }
        }
        return region;
    }

    /**
     * How far every cell's position is pushed, across and then down: two fields of noise sweeping half a region's
     * {@code span} and pushing a third of it, with their middle taken for no push at all.
     *
     * <p>ponytail: the span stops growing at {@link #WARP_CEILING}. {@link Relief#octave} works in a long and
     * multiplies a height by its sweep six times over, which runs out at a sweep of about 540, a span of about 1,080;
     * past it the borders went to sawtooth. So a world past about 1,700 across keeps borders that wander on the scale
     * of a span of a thousand and no wider. Wider wants an octave of its own in wider arithmetic: Relief's rounding is
     * every hill's, and is not to be moved for this.
     */
    static int[][] warp(DeterministicRng rng, int width, int height, int span) {
        int reach = Math.min(span, WARP_CEILING);
        int sweep = Math.max(2, reach / 2);
        int push = reach / 3;
        // Each field runs from nothing to this much, and where it is half that nothing is moved.
        int middle = (push + push / 2) / 2;
        var pushes = new int[2][width * height];
        for (var field : pushes) {
            Relief.octave(rng, field, width, height, sweep, push);
            Relief.octave(rng, field, width, height, Math.max(1, sweep / 2), push / 2);
            for (int at = 0; at < field.length; at++) {
                field[at] -= middle;
            }
        }
        return pushes;
    }

    /**
     * Every region one piece of at least {@code minShare} percent of the cells, by joining and nothing else.
     *
     * <p>Pieces are the four-connected runs of one region. Two that touch are two regions' and share a border, whose
     * length in cell edges is what "borders most" counts. The smallest piece that is not its region's largest goes
     * whole to the region it borders most, becoming one piece with whatever of that region it touches; and when
     * every region is one piece, the smallest under the share goes the same way. Ties go to the piece, or the region,
     * the scan meets first. Each step joins at least two pieces into one, so it ends; and it works on the pieces and
     * their borders rather than the cells, so a world's worth of cells costs one scan to cut into pieces and no more.
     */
    static void join(int[] region, int width, int count, int minShare) {
        int total = region.length;
        var piece = new int[total];
        int pieces = cutInto(region, piece, width);
        var size = new int[pieces];
        var owner = new int[pieces];
        for (int i = 0; i < total; i++) {
            size[piece[i]]++;
            owner[piece[i]] = region[i];
        }
        var borders = new ArrayList<TreeMap<Integer, Integer>>(pieces);
        for (int p = 0; p < pieces; p++) {
            borders.add(new TreeMap<>());
        }
        for (int i = 0; i < total; i++) {
            if (i % width + 1 < width) {
                meet(borders, piece[i], piece[i + 1]);
            }
            if (i + width < total) {
                meet(borders, piece[i], piece[i + width]);
            }
        }
        // A piece joined to another is found through the one it was joined to, which is always the lower.
        var joinedTo = new int[pieces];
        for (int p = 0; p < pieces; p++) {
            joinedTo[p] = p;
        }
        for (int goes = nextToGo(size, owner, joinedTo, count, total, minShare); goes >= 0;
                goes = nextToGo(size, owner, joinedTo, count, total, minShare)) {
            var votes = new long[count];
            for (var border : borders.get(goes).entrySet()) {
                votes[owner[border.getKey()]] += border.getValue();
            }
            int into = 0;
            for (int heart = 1; heart < count; heart++) {
                if (votes[heart] > votes[into]) {
                    into = heart;
                }
            }
            owner[goes] = into;
            for (int next : new ArrayList<>(borders.get(goes).keySet())) {
                if (owner[next] == into) {
                    int keep = Math.min(goes, next);
                    fuse(borders, size, joinedTo, keep, Math.max(goes, next));
                    goes = keep;
                }
            }
        }
        var regionOf = new int[pieces];
        for (int p = 0; p < pieces; p++) {
            int root = p;
            while (joinedTo[root] != root) {
                root = joinedTo[root];
            }
            regionOf[p] = owner[root];
        }
        for (int i = 0; i < total; i++) {
            region[i] = regionOf[piece[i]];
        }
    }

    /**
     * The piece that goes next, or -1 when none need: the smallest that is not its region's largest, else — every
     * region being one piece — the smallest region under the share, unless it is the only one left.
     */
    private static int nextToGo(int[] size, int[] owner, int[] joinedTo, int count, int total, int minShare) {
        var largest = new int[count];
        Arrays.fill(largest, -1);
        for (int p = 0; p < size.length; p++) {
            if (joinedTo[p] == p && (largest[owner[p]] < 0 || size[p] > size[largest[owner[p]]])) {
                largest[owner[p]] = p;
            }
        }
        int goes = -1;
        for (int p = 0; p < size.length; p++) {
            if (joinedTo[p] == p && largest[owner[p]] != p && (goes < 0 || size[p] < size[goes])) {
                goes = p;
            }
        }
        if (goes >= 0) {
            return goes;
        }
        int regions = 0;
        for (int piece : largest) {
            regions += piece < 0 ? 0 : 1;
        }
        for (int heart = 0; heart < count && regions > 1; heart++) {
            int whole = largest[heart];
            if (whole >= 0 && size[whole] * 100L < (long) total * minShare && (goes < 0 || size[whole] < size[goes])) {
                goes = whole;
            }
        }
        return goes;
    }

    /** Numbers every four-connected run of one region, in the order the scan first meets them; how many there are. */
    private static int cutInto(int[] region, int[] piece, int width) {
        int total = region.length;
        Arrays.fill(piece, -1);
        var queue = new int[total];
        int pieces = 0;
        for (int start = 0; start < total; start++) {
            if (piece[start] >= 0) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            piece[start] = pieces;
            while (head < tail) {
                int at = queue[head++];
                if (at % width > 0 && piece[at - 1] < 0 && region[at - 1] == region[at]) {
                    piece[at - 1] = pieces;
                    queue[tail++] = at - 1;
                }
                if (at % width + 1 < width && piece[at + 1] < 0 && region[at + 1] == region[at]) {
                    piece[at + 1] = pieces;
                    queue[tail++] = at + 1;
                }
                if (at >= width && piece[at - width] < 0 && region[at - width] == region[at]) {
                    piece[at - width] = pieces;
                    queue[tail++] = at - width;
                }
                if (at + width < total && piece[at + width] < 0 && region[at + width] == region[at]) {
                    piece[at + width] = pieces;
                    queue[tail++] = at + width;
                }
            }
            pieces++;
        }
        return pieces;
    }

    /** Notes one cell edge between pieces {@code a} and {@code b}, if they are two. */
    private static void meet(List<TreeMap<Integer, Integer>> borders, int a, int b) {
        if (a != b) {
            borders.get(a).merge(b, 1, Integer::sum);
            borders.get(b).merge(a, 1, Integer::sum);
        }
    }

    /** Makes piece {@code gone} part of piece {@code keep}: their sizes added, and their borders with the rest too. */
    private static void fuse(List<TreeMap<Integer, Integer>> borders, int[] size, int[] joinedTo, int keep, int gone) {
        joinedTo[gone] = keep;
        size[keep] += size[gone];
        borders.get(keep).remove(gone);
        for (var border : borders.get(gone).entrySet()) {
            int other = border.getKey();
            if (other != keep) {
                borders.get(other).remove(gone);
                borders.get(other).merge(keep, border.getValue(), Integer::sum);
                borders.get(keep).merge(other, border.getValue(), Integer::sum);
            }
        }
        borders.get(gone).clear();
    }

    /**
     * Each region's biome, indexed by its heart: the regions in order of size, the largest first and the lower heart
     * first among equals, each taking the biome whose point is nearest the weather at its heart that no region has
     * taken — or, when every biome has been, the nearest of all.
     */
    private static int[] choose(DeterministicRng rng, int[] region, int[] hearts, int depth, int width, int height,
            Biomes settings) {
        var wild = weather(rng, width, height, settings.size());
        var alive = weather(rng, width, height, settings.size());
        int drift = Math.max(0, depth - 1);
        var area = new int[hearts.length];
        for (int heart : region) {
            area[heart]++;
        }
        var largestFirst = new ArrayList<Integer>();
        for (int heart = 0; heart < hearts.length; heart++) {
            if (area[heart] > 0) {
                largestFirst.add(heart);
            }
        }
        largestFirst.sort(Comparator.comparingInt((Integer heart) -> -area[heart]).thenComparingInt(heart -> heart));
        var taken = new boolean[settings.all().size()];
        var biomeOf = new int[hearts.length];
        for (int heart : largestFirst) {
            biomeOf[heart] = nearest(settings.all(),
                    Math.clamp(rank(wild, hearts[heart]) + settings.perDepth().wild() * drift, 0, 100),
                    Math.clamp(rank(alive, hearts[heart]) + settings.perDepth().alive() * drift, 0, 100), taken);
            taken[biomeOf[heart]] = true;
        }
        return biomeOf;
    }

    /**
     * One field of weather: broad sweeps {@code size} cells across with smaller ones riding them. Ranked where it is
     * read — see {@link #rank}.
     */
    private static int[] weather(DeterministicRng rng, int width, int height, int size) {
        var field = new int[width * height];
        Relief.octave(rng, field, width, height, size, 2000);
        Relief.octave(rng, field, width, height, Math.max(1, size / 2), 1000);
        return field;
    }

    /**
     * Where the weather at cell {@code at} stands among all the floor's, from 0 to 100: the share of the cells it is
     * above. Ties keep the order of the cells, so it is still one map.
     *
     * <p>Ranked rather than stretched between the floor's extremes, and that is the balance of the whole thing.
     * Summed noise piles up in the middle of its range, so a stretched field is mostly middling weather, and a
     * biome whose point sits toward a corner of the climate — the green wood, lushest and wildest — would be given
     * to a region now and then while whichever sat nearest the middle took most. Ranked, the weather at a heart is
     * as likely to be one value from 0 to 100 as any other, and a biome is as likely to be wanted as the share of the
     * climate that is nearer its point than any other's. Counted rather than sorted, since only a few cells are asked.
     */
    private static int rank(int[] field, int at) {
        long below = 0;
        for (int i = 0; i < field.length; i++) {
            if (field[i] < field[at] || field[i] == field[at] && i < at) {
                below++;
            }
        }
        return (int) (below * 100 / Math.max(1, field.length - 1));
    }

    /**
     * The biome whose point is nearest this weather and not yet taken; a tie goes to the one the file lists first.
     * When every biome is taken, the nearest of all.
     */
    private static int nearest(List<Theme> biomes, int wild, int alive, boolean[] taken) {
        int best = -1;
        for (int pass = 0; pass < 2 && best < 0; pass++) {
            long bestDistance = Long.MAX_VALUE;
            for (int i = 0; i < biomes.size(); i++) {
                if (pass == 0 && taken[i]) {
                    continue;
                }
                var point = biomes.get(i).climate();
                long across = wild - point.wild();
                long down = alive - point.alive();
                long distance = across * across + down * down;
                if (distance < bestDistance) {
                    best = i;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
}
