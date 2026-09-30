package uz.dukeengine.dungeon.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.MonsterKind;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Monster;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Prop;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.world.Theme;

/**
 * Draws a floor from a seed: chambers of rock or glades of wood grown where the rooms are placed, joined by
 * tunnels that wander, laid over hills — with the hero in the first and the monsters scattered through the rest.
 *
 * <p>The floor ends in the boss's {@link Keep}: a walled court on the ground, built in solid rock once the rest is
 * carved, so it cuts nothing off; the last of the rooms, and the boss's.
 *
 * <p>The one property that matters more than any other is that <b>every chamber can be reached</b> — a skeleton
 * walled off in an island chamber is a run the player cannot finish. This is guaranteed by construction rather
 * than hoped for: the chambers are joined along a spanning tree grown out of the first, and every tunnel of it is
 * kept whatever the edges of the floor are worn into afterwards — see {@link Cave}. The ground under it is laid so
 * no cell is steep enough to be a cliff — see {@link Relief} — so the floor that was connected on the flat is
 * connected on the hills.
 *
 * <p>Which earlier chamber a new one joins is chosen to keep the walking short: the <b>nearest</b> pair across the
 * tree, grown one chamber at a time (Prim's). Joining in placement order regularly joined two chambers random
 * placement had thrown to opposite corners, and the result was long empty tunnels that were dull to walk down.
 *
 * <p>Everything is drawn with a {@link DeterministicRng} in a fixed order, so a seed names exactly one floor.
 * Placement is the only part that can fail to progress (a footprint may land on another and be rejected); it is
 * bounded by an attempt count and simply stops with however many fit.
 *
 * <p>How many chambers, how big, how crowded: all of it comes from {@link DungeonSettings}, which is read from a
 * file. What the ground between them is like — ragged or smooth, winding or straight, hilly or flat — is the
 * theme's the depth wears, from the same files. None of it is compiled in.
 */
public final class DungeonGenerator {

    private DungeonGenerator() {
    }

    /**
     * The seed one link along the chain — how a finished run picks the dungeon for
     * the next one. Keeps a whole session's sequence of dungeons a function of the
     * single seed it started with.
     */
    public static long nextSeed(long seed) {
        return DeterministicRng.advance(seed);
    }

    /** The first floor. */
    public static GeneratedDungeon generate(long seed, DungeonSettings settings) {
        return generate(seed, settings, 1);
    }

    /**
     * A floor of the dungeon at {@code depth}.
     *
     * <p>Depth changes who lives here and how many of them, and which theme's ground the floor is carved into;
     * the chambers are placed and joined the same way at every depth.
     */
    public static GeneratedDungeon generate(long seed, DungeonSettings settings, int depth) {
        return generate(seed, settings, depth, Layout.of(settings));
    }

    /**
     * The same, on a dungeon of somebody else's size.
     *
     * <p>Only a map drawn once passes one — see {@code MapWriter}. The descent's floors are the size the
     * settings file says, and handing it {@code Layout.of(settings)} is exactly
     * the call above — so a stage cut at 200 by 150 and a floor of the endless
     * dungeon come out of the same generator, drawn the same way, carrying the
     * same connectivity guarantee.
     */
    public static GeneratedDungeon generate(long seed, DungeonSettings settings, int depth,
            Layout layout) {
        var rng = new DeterministicRng(seed);
        var terrain = settings.themes().terrainAt(depth);

        var rooms = placeRooms(rng, settings, layout);
        var links = spanningTree(rooms);
        int furthest = furthestRoomFromStart(rooms.size(), links);
        // A map that mixes biomes lays them out now, from a stream of its own, so each region can be cut to its
        // own ground; one that does not cuts the whole floor to its depth's theme, exactly as it always has.
        var drawn = settings.biomes().isEmpty() || !layout.mixesBiomes() ? null
                : BiomeMap.draw(seed, depth, layout.width(), layout.height(), rooms, settings.biomes());
        var cave = drawn == null
                ? Cave.carve(rng, layout.width(), layout.height(), rooms, links, furthest,
                        settings.corridorWidth(), settings.maxRoomSpacing(), terrain)
                : Cave.carve(rng, layout.width(), layout.height(), rooms, links, furthest,
                        settings.corridorWidth(), settings.maxRoomSpacing(), terrainOfRooms(drawn, rooms.size()),
                        (x, y) -> drawn.at(x, y).terrain().ragged(),
                        // A wood's islands are groves of trees a body can go between; a cavern's are rock.
                        room -> drawn.ofRoom(room).grove() != null);

        // The boss's keep, in solid rock beside the deepest chamber with room for it — see Keep. A map drawn once
        // gets none: its file keeps no look for one, as it keeps no biome for a cell (see Layout). Where none fits,
        // the boss waits in the furthest chamber as it always did, and the floor is today's.
        var keep = layout.mixesBiomes()
                ? Keep.site(cave, rooms, links, settings.keep().sizes(), settings.maxRoomSpacing()) : null;
        var chambers = new ArrayList<>(rooms);
        var joined = new ArrayList<>(links);
        int bossRoom = furthest;
        if (keep != null) {
            cave.raise(keep, rooms);
            chambers.add(keep.walls());
            joined.add(new Link(keep.chamber(), chambers.size() - 1));
            bossRoom = chambers.size() - 1;
        }
        var biomes = drawn == null || keep == null ? drawn : drawn.withRoom(keep.walls());

        var hero = middleOf(rooms.get(0));
        var monsters = populate(rng, cave, chambers, settings, depth, bossRoom);
        var boss = new Monster(settings.bossKindAt(depth), middleOf(chambers.get(bossRoom)));
        var guarded = chambers.get(bossRoom);
        if (keep != null) {
            // The guard is kept to the court: the square the keep is walled in takes in its ring of wall and its
            // doorway too, and no guard is stood in either. Its middle cell is the same, so the ring round the boss is.
            var w = keep.walls();
            guarded = new Room(w.x() + 1, w.y() + 1, w.w() - 2, w.h() - 2);
        }
        monsters.addAll(guard(cave, guarded, keep, settings));
        var props = new ArrayList<>(scatter(rng, cave, chambers, settings, monsters, hero, boss.at()));
        if (keep != null) {
            // In the doorway until it is opened. Spawned as a prop is, and turned across the doorway: see Spawner.
            var gate = keep.gate();
            props.add(new Prop(settings.keep().gate(), Placement.atCell(gate[0], gate[1])));
        }

        var relief = biomes == null ? Relief.of(seed, cave, chambers, terrain)
                : Relief.of(seed, cave, chambers, biomes.hills(), biomes::riseAt,
                        terrainOfRooms(biomes, chambers.size()).stream().map(Theme.Terrain::level).toList());
        var scenery = biomes == null ? List.<GeneratedDungeon.Piece>of()
                : Scenery.scatter(seed, cave, biomes, rooms.getFirst());
        // Every chamber on the ground, the keep's court among them: how high each stands is the relief's.
        var storeys = Collections.nCopies(chambers.size(), 0);
        return new GeneratedDungeon(cave.walls(), cave.levels(), hero, monsters, boss, bossRoom,
                List.copyOf(chambers), List.copyOf(joined), List.copyOf(storeys), List.copyOf(props),
                relief, 0f, biomes, scenery, keep);
    }

    /** Each chamber's ground: its own biome's. */
    private static List<Theme.Terrain> terrainOfRooms(BiomeMap biomes, int rooms) {
        var terrains = new ArrayList<Theme.Terrain>(rooms);
        for (int room = 0; room < rooms; room++) {
            terrains.add(biomes.ofRoom(room).terrain());
        }
        return terrains;
    }

    /**
     * The room furthest from where the hero starts, counted in corridors rather
     * than in metres.
     *
     * <p>Corridors are what the player actually walks, so the room at the end of
     * the longest chain of them is the one that feels like the end — a room across
     * the map with a direct corridor to the start is next door, whatever the
     * distance says. Ties fall to the lowest room index, so a seed names one room.
     */
    private static int furthestRoomFromStart(int roomCount, List<Link> links) {
        var stepsFromStart = new int[roomCount];
        java.util.Arrays.fill(stepsFromStart, -1);
        stepsFromStart[0] = 0;

        // The links form a tree grown outward from room 0, so one pass in link
        // order reaches every room with its true distance.
        for (var link : links) {
            stepsFromStart[link.to()] = stepsFromStart[link.from()] + 1;
        }

        int furthest = 0;
        for (int room = 1; room < roomCount; room++) {
            if (stepsFromStart[room] > stepsFromStart[furthest]) {
                furthest = room;
            }
        }
        return furthest;
    }

    /**
     * Which chambers a tunnel joins: the cheapest set that reaches all of them — a minimum spanning tree, grown one
     * chamber at a time (Prim's).
     *
     * <p>Connectivity is a property of the construction, not of luck: the tree starts at chamber 0 and every step
     * joins one already in it to one that is not, so after {@code rooms - 1} steps every chamber hangs off the
     * first. What the minimum buys is the walking: no chamber is ever joined by a longer tunnel than it has to be.
     *
     * <p>Ties fall to the lowest index by iteration order, so the result is the same everywhere.
     */
    private static List<Link> spanningTree(List<Room> rooms) {
        var links = new ArrayList<Link>();
        var joined = new boolean[rooms.size()];
        joined[0] = true;

        for (int step = 1; step < rooms.size(); step++) {
            int bestInside = -1;
            int bestOutside = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int inside = 0; inside < rooms.size(); inside++) {
                if (!joined[inside]) {
                    continue;
                }
                for (int outside = 0; outside < rooms.size(); outside++) {
                    if (joined[outside]) {
                        continue;
                    }
                    int distance = tunnelCost(rooms.get(inside), rooms.get(outside));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestInside = inside;
                        bestOutside = outside;
                    }
                }
            }
            joined[bestOutside] = true;
            links.add(new Link(bestInside, bestOutside));
        }
        return links;
    }

    /** Manhattan between the two middles: about what a tunnel between them costs to walk. */
    private static int tunnelCost(Room a, Room b) {
        return Math.abs(a.centerCellX() - b.centerCellX())
                + Math.abs(a.centerCellY() - b.centerCellY());
    }

    private static List<Room> placeRooms(DeterministicRng rng, DungeonSettings settings,
            Layout layout) {
        int target = rng.nextInt(layout.minRooms(), layout.maxRooms());
        var rooms = new ArrayList<Room>();
        for (int attempt = 0;
                attempt < layout.attempts() && rooms.size() < target;
                attempt++) {
            int w = rng.nextInt(settings.minRoomSize(), settings.maxRoomSize());
            int h = rng.nextInt(settings.minRoomSize(), settings.maxRoomSize());
            // Keep a one-cell stone border so a room never touches the map edge.
            int x = rng.nextInt(1, layout.width() - w - 2);
            int y = rng.nextInt(1, layout.height() - h - 2);
            var room = new Room(x, y, w, h);
            if (!overlapsAny(room, rooms, settings.roomGap())
                    && withinReach(room, rooms, settings.maxRoomSpacing())) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    /**
     * Whether this room is close enough to something already placed to belong to
     * the same dungeon.
     *
     * <p>Rejection sampling over the whole map scatters rooms into corners, and two
     * rooms in opposite corners are joined by a corridor across everything — the
     * part of a dungeon a player walks rather than plays. Requiring each new room
     * to sit near an existing one grows the dungeon outward from the first instead
     * of sprinkling it. The first room has nothing to be near, so it goes anywhere.
     */
    private static boolean withinReach(Room room, List<Room> placed, int maxSpacing) {
        if (placed.isEmpty()) {
            return true;
        }
        for (var other : placed) {
            int distance = Math.abs(room.centerCellX() - other.centerCellX())
                    + Math.abs(room.centerCellY() - other.centerCellY());
            if (distance <= maxSpacing) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsAny(Room room, List<Room> placed, int gap) {
        for (var other : placed) {
            // Expand one room by the gap and test plain rectangle intersection.
            boolean apart = room.x() - gap >= other.x() + other.w()
                    || other.x() - gap >= room.x() + room.w()
                    || room.y() - gap >= other.y() + other.h()
                    || other.y() - gap >= room.y() + room.h();
            if (!apart) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fill the chambers the hero does not start in, drawing a kind for each monster
     * from what the data file makes available at this depth.
     *
     * <p>Each stands on floor with floor all round it, never against the rock. The boss's chamber is left to the
     * boss and the guard the file names for it -- see {@link #guard}. It is meant to be the end of the floor, and a
     * crowd drawn at random around it would turn the fight that gates the next depth into a brawl the player
     * stumbles into sideways.
     */
    private static List<Monster> populate(DeterministicRng rng, Cave cave, List<Room> rooms,
            DungeonSettings settings, int depth, int bossRoom) {
        var available = settings.roomFillersAt(depth);
        var monsters = new ArrayList<Monster>();
        if (available.isEmpty()) {
            return monsters;
        }
        int totalWeight = 0;
        for (var kind : available) {
            totalWeight += kind.weight();
        }

        for (int i = 1; i < rooms.size(); i++) {
            if (i == bossRoom) {
                continue;
            }
            var room = rooms.get(i);
            int count = Math.round(rng.nextInt(settings.minSkeletonsPerRoom(),
                    settings.maxSkeletonsPerRoom()) * settings.monsterCountAt(depth));
            var spots = cave.openAround(room, 1);
            var used = new ArrayList<int[]>();
            var inThisRoom = new java.util.HashMap<String, Integer>();
            for (int n = 0; n < count && !spots.isEmpty(); n++) {
                var spot = spots.get(rng.nextInt(spots.size()));
                if (occupied(used, spot[0], spot[1])) {
                    continue; // one per cell, so they never spawn overlapping
                }
                // A kind with as many here as the file allows is drawn no more in this
                // room. Until one has, the draw is exactly the draw it always was.
                var open = roomLeftFor(available, inThisRoom);
                if (open.isEmpty()) {
                    break;
                }
                used.add(spot);
                var kind = open.size() == available.size() ? draw(rng, available, totalWeight)
                        : draw(rng, open, weightOf(open));
                inThisRoom.merge(kind.name(), 1, Integer::sum);
                monsters.add(new Monster(kind.name(), Placement.atCell(spot[0], spot[1])));
            }
        }
        return monsters;
    }

    /**
     * Stand the boss's guard with it: the kinds the file names, in its order, on every floor.
     *
     * <p>In a keep the first four stand at the court's corners, a cell in from each wall — so with two healers named
     * before two summoners, the healers hold one diagonal and the summoners the other. Any more, and every guard on a
     * floor with no keep, stand on the next free cell of a square ring round the boss's own.
     *
     * <p>No dice, so a floor's chambers, fillers and furniture are drawn exactly as they were before its boss had a
     * guard. The ring is {@code BossGuardRing} cells out -- a boss is wide -- with its corners taken first and then the
     * middles of its sides. A cell that is rock, outside the chamber's footprint or stood on already is passed over, and
     * when one ring has no floor left the next ring out is tried.
     */
    private static List<Monster> guard(Cave cave, Room room, Keep keep, DungeonSettings settings) {
        var wanted = new ArrayList<String>();
        for (var guard : settings.bossGuards()) {
            for (int n = 0; n < guard.count(); n++) {
                wanted.add(guard.kind());
            }
        }
        var guards = new ArrayList<Monster>();
        if (keep != null) {
            for (var corner : keep.corners()) {
                if (guards.size() < wanted.size()) {
                    guards.add(new Monster(wanted.get(guards.size()), Placement.atCell(corner[0], corner[1])));
                }
            }
        }
        int bx = room.centerCellX();
        int by = room.centerCellY();
        int widest = Math.max(room.w(), room.h());
        for (int ring = settings.bossGuardRing();
                ring <= widest && guards.size() < wanted.size(); ring++) {
            for (var cell : ringAround(ring)) {
                int cx = bx + cell[0];
                int cy = by + cell[1];
                if (guards.size() < wanted.size() && inside(room, cx, cy) && cave.isFloor(cx, cy)
                        && nobodyOn(guards, cx, cy)) {
                    guards.add(new Monster(wanted.get(guards.size()), Placement.atCell(cx, cy)));
                }
            }
        }
        return guards;
    }

    /** Whether no guard stands on the cell yet: in a small keep the ring's corners are the court's. */
    private static boolean nobodyOn(List<Monster> guards, int cx, int cy) {
        var cell = Placement.atCell(cx, cy);
        return guards.stream().noneMatch(guard -> guard.at().equals(cell));
    }

    /** The cells of the square {@code ring} out: corners, then the middles, then the rest. */
    private static List<int[]> ringAround(int ring) {
        var cells = new ArrayList<int[]>();
        cells.add(new int[] {-ring, -ring});
        cells.add(new int[] {ring, ring});
        cells.add(new int[] {ring, -ring});
        cells.add(new int[] {-ring, ring});
        cells.add(new int[] {0, -ring});
        cells.add(new int[] {0, ring});
        cells.add(new int[] {-ring, 0});
        cells.add(new int[] {ring, 0});
        for (int along = 1; along < ring; along++) {
            for (int side : new int[] {-1, 1}) {
                cells.add(new int[] {side * along, -ring});
                cells.add(new int[] {side * along, ring});
                cells.add(new int[] {-ring, side * along});
                cells.add(new int[] {ring, side * along});
            }
        }
        return cells;
    }

    /** Inside the chamber's footprint, which is what "in the boss's chamber" means. */
    private static boolean inside(Room room, int cx, int cy) {
        return cx >= room.x() && cx < room.x() + room.w()
                && cy >= room.y() && cy < room.y() + room.h();
    }

    /**
     * Scatter things through the chambers: a pillar to walk round, a statue, a barrel.
     *
     * <p>Solid, and that is the point — a chamber with nothing in it is a floor with
     * a fight on it, and something to put between yourself and a skeleton is the
     * difference between a room and a place. They are ordinary templates with a
     * shape and no body, so the engine bakes them into the navigation grid and
     * nothing shoots at them.
     *
     * <p>Only in the open: two cells of floor all round each, so none ever stands in
     * a tunnel or a gap between the rock. A pillar in a doorway is a doorway a wide
     * monster cannot use, and this game has spent enough of its life on units wedged
     * in corridors.
     *
     * <p>And never where something already stands. A solid thing dropped on a
     * skeleton leaves the skeleton inside an obstacle, which is not merely untidy:
     * a search that begins on blocked ground finds no path at all, so that
     * skeleton never moves again and the room it was guarding is a room the
     * player walks through unopposed.
     */
    private static List<Prop> scatter(DeterministicRng rng, Cave cave, List<Room> rooms,
            DungeonSettings settings, List<Monster> monsters, Placement hero, Placement boss) {
        var kinds = settings.propKinds();
        var props = new ArrayList<Prop>();
        if (kinds.isEmpty() || settings.maxPropsPerRoom() <= 0) {
            return List.copyOf(props);
        }
        int totalWeight = 0;
        for (var kind : kinds) {
            totalWeight += kind.weight();
        }
        if (totalWeight <= 0) {
            return List.copyOf(props);
        }

        var taken = new java.util.HashSet<Long>();
        for (var monster : monsters) {
            taken.add(cellKey(monster.at()));
        }
        taken.add(cellKey(hero));
        taken.add(cellKey(boss));

        for (var room : rooms) {
            int count = rng.nextInt(settings.minPropsPerRoom(), settings.maxPropsPerRoom());
            var spots = cave.openAround(room, 2);
            for (int i = 0; i < count && !spots.isEmpty(); i++) {
                var spot = spots.get(rng.nextInt(spots.size()));
                if (!taken.add(((long) spot[1] << 32) | spot[0])) {
                    continue; // one to a cell, never on anybody
                }
                props.add(new Prop(drawProp(rng, kinds, totalWeight), Placement.atCell(spot[0], spot[1])));
            }
        }
        return List.copyOf(props);
    }

    private static long cellKey(Placement at) {
        return ((long) at.cellY() << 32) | at.cellX();
    }

    private static String drawProp(DeterministicRng rng,
            List<DungeonSettings.PropKind> kinds, int totalWeight) {
        int roll = rng.nextInt(totalWeight);
        for (var kind : kinds) {
            roll -= kind.weight();
            if (roll < 0) {
                return kind.template();
            }
        }
        return kinds.get(kinds.size() - 1).template();
    }

    /**
     * Pick a kind, the commoner ones more often.
     *
     * <p>Walked in list order — which is file order — so a seed draws the same
     * creature everywhere, and re-ordering the blocks in the file is a change to
     * the dungeons it generates rather than a silent no-op.
     */
    private static MonsterKind draw(DeterministicRng rng, List<MonsterKind> available,
            int totalWeight) {
        int roll = rng.nextInt(totalWeight);
        for (var kind : available) {
            roll -= kind.weight();
            if (roll < 0) {
                return kind;
            }
        }
        return available.get(available.size() - 1);
    }

    /** The kinds this room still has space for. */
    private static List<MonsterKind> roomLeftFor(List<MonsterKind> kinds,
            java.util.Map<String, Integer> placed) {
        var open = new ArrayList<MonsterKind>(kinds.size());
        for (var kind : kinds) {
            if (kind.maxPerRoom() <= 0 || placed.getOrDefault(kind.name(), 0) < kind.maxPerRoom()) {
                open.add(kind);
            }
        }
        return open;
    }

    private static int weightOf(List<MonsterKind> kinds) {
        int total = 0;
        for (var kind : kinds) {
            total += kind.weight();
        }
        return total;
    }

    private static boolean occupied(List<int[]> used, int cx, int cy) {
        for (var cell : used) {
            if (cell[0] == cx && cell[1] == cy) {
                return true;
            }
        }
        return false;
    }

    /** Where a chamber is walked to and from, and where whoever it is for stands: its middle cell. */
    private static Placement middleOf(Room room) {
        return Placement.atCell(room.centerCellX(), room.centerCellY());
    }
}
