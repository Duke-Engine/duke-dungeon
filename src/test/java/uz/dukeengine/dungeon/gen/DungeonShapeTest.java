package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The shape of a floor: wide enough to walk down, tight enough to be a place, and
 * with the way out at the far end of it.
 */
class DungeonShapeTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /**
     * Every corridor is wider than the largest creature.
     *
     * <p>Not tidiness — this is the setting that stopped units ending up inside
     * walls. A mover in a corridor its own width has nowhere to step when it needs
     * to get past a neighbour, and the only way it can go is sideways into stone.
     * The largest creature is read from the data rather than named here, so adding
     * a bigger monster than the corridors allow fails loudly instead of producing
     * a dungeon it cannot walk through.
     */
    @Test
    void corridorsAreWiderThanTheLargestCreature() {
        float widest = 0f;
        for (var line : Content.units().split("\n")) {
            if (line.trim().startsWith("GeometryMajorRadius")) {
                widest = Math.max(widest,
                        Float.parseFloat(line.substring(line.indexOf('=') + 1).trim()));
            }
        }
        float corridor = SETTINGS.corridorWidth() * PathGrid.DEFAULT_CELL_SIZE;

        assertTrue(corridor > widest * 2f,
                "corridors are " + corridor + " wide and the largest creature is "
                        + (widest * 2f) + " across");
    }

    /**
     * Nothing on the floor is narrower than a tunnel: every cell of it lies inside a square of floor that wide.
     *
     * <p>Every cell rather than the tunnels, because the tunnels are no longer the only place a body can be
     * squeezed. A chamber's worn edge leaves spurs and slits, and a slit a cell wide is the corridor that used to
     * wedge the boss, wherever it happens to be. Over the ground both themes carve, the wood's ragged and the
     * cellar's close.
     */
    @Test
    void nothingOnTheFloorIsNarrowerThanATunnel() {
        int side = SETTINGS.corridorWidth();
        for (long seed = 0; seed <= 40; seed++) {
            for (int depth : new int[] {1, 3}) {
                var rows = DungeonGenerator.generate(seed, SETTINGS, depth).asciiMap().strip().split("\n");
                for (int y = 0; y < rows.length; y++) {
                    for (int x = 0; x < rows[y].length(); x++) {
                        if (rows[y].charAt(x) != '#') {
                            assertTrue(inAWholeSquare(rows, x, y, side),
                                    "seed " + seed + ", depth " + depth + ": the floor at " + x + "," + y
                                            + " is narrower than " + side + " cells");
                        }
                    }
                }
            }
        }
    }

    /** Whether some {@code side}-wide square of floor holds the cell. */
    private static boolean inAWholeSquare(String[] rows, int x, int y, int side) {
        for (int top = y - side + 1; top <= y; top++) {
            for (int left = x - side + 1; left <= x; left++) {
                if (allFloor(rows, left, top, side)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allFloor(String[] rows, int left, int top, int side) {
        for (int y = top; y < top + side; y++) {
            for (int x = left; x < left + side; x++) {
                if (y < 0 || x < 0 || y >= rows.length || x >= rows[y].length() || rows[y].charAt(x) == '#') {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The boss is at the far end, and getting to it means going through the floor
     * rather than around it.
     */
    @Test
    void reachingTheBossMeansCrossingMostOfTheFloor() {
        int totalRooms = 0;
        int totalOnTheWay = 0;
        for (long seed = 0; seed <= 60; seed++) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 1);
            int onTheWay = roomsOnTheWayToTheBoss(floor);

            assertTrue(onTheWay >= 2,
                    "seed " + seed + ": the boss was practically next door, "
                            + onTheWay + " rooms in");
            totalRooms += floor.rooms().size();
            totalOnTheWay += onTheWay;
        }
        // Not every room can be on one path through a tree, but the walk to the
        // boss should be a journey rather than a turning. Two fifths rather than
        // half: a tree's longest walk is a smaller share the more rooms it holds,
        // and on floors of twenty-odd rooms it came out at 48% -- ten rooms deep,
        // which is a journey by any measure. Under two fifths the boss is round
        // the side rather than at the end.
        assertTrue(totalOnTheWay * 5 >= totalRooms * 2,
                "the boss should be deep in the floor: " + totalOnTheWay
                        + " rooms on the way out of " + totalRooms);
    }

    /** How many rooms the hero passes through to reach the boss, counting both ends. */
    private static int roomsOnTheWayToTheBoss(GeneratedDungeon floor) {
        var parent = new int[floor.rooms().size()];
        java.util.Arrays.fill(parent, -1);
        for (var link : floor.links()) {
            parent[link.to()] = link.from();
        }
        int rooms = 1;
        for (int at = floor.bossRoom(); at > 0; at = parent[at]) {
            rooms++;
        }
        return rooms;
    }

    /** The hero starts at one end of that walk, and the boss at the other. */
    @Test
    void theHeroStartsAtTheEntranceAndNeverInTheBossRoom() {
        for (long seed = 0; seed <= 60; seed++) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 1);
            var entrance = floor.rooms().get(0);

            assertNotEquals(0, floor.bossRoom(), "seed " + seed + ": the boss took the entrance");
            assertEquals(entrance.centerCellX() * 10 + 5, floor.hero().x(), 0.01f);
            assertEquals(entrance.centerCellY() * 10 + 5, floor.hero().y(), 0.01f);
        }
    }

    /**
     * Every boss looks like nothing else, because on the minimap that is all there
     * is.
     *
     * <p>Every one of them, now that there are four: the last floor is not the
     * only one whose furthest room has to announce itself.
     */
    @Test
    void everyBossIsDrawnUnlikeAnythingElse() {
        assertTrue(SETTINGS.finalDepth() > 0, "the descent was left without a bottom");
        var bossNames = new HashSet<String>();
        for (int depth = 1; depth <= SETTINGS.finalDepth(); depth++) {
            bossNames.add(SETTINGS.bossKindAt(depth));
        }
        var ordinary = new HashSet<Integer>();
        for (var kind : SETTINGS.monsters()) {
            if (!bossNames.contains(kind.name())) {
                ordinary.add(kind.colour());
            }
        }

        for (var name : bossNames) {
            var boss = SETTINGS.monster(name);
            assertNotNull(boss, name + " is named as a boss but is no kind of monster");
            assertFalse(ordinary.contains(boss.colour()),
                    name + " shares its colour with an ordinary monster");
            assertTrue(boss.scale() > 1.5f,
                    name + " should be visibly bigger than anything it is met with");
            assertEquals(0, boss.weight(),
                    name + " would be rolled into an ordinary room as well as its own");
        }
    }

    /** And one waits on every floor there is, up to the last. */
    @Test
    void everyDepthHasABossOfItsOwn() {
        var seen = new HashSet<String>();
        for (int depth = 1; depth <= SETTINGS.finalDepth(); depth++) {
            assertTrue(seen.add(SETTINGS.bossKindAt(depth)),
                    "depth " + depth + " repeats a boss the player has already beaten");
        }
        assertEquals(SETTINGS.finalDepth(), seen.size());
    }

    /** Packing the rooms closer must not strand one. */
    @Test
    void aTighterFloorIsStillOnePlace() {
        var tight = DungeonSettings.parse("""
                ProceduralMap
                  Generation = Layout
                    MaxRoomSpacing = 14
                  End
                End
                """);

        for (long seed = 0; seed <= 60; seed++) {
            var floor = DungeonGenerator.generate(seed, tight, 1);
            assertEquals(floor.rooms().size() - 1, floor.links().size(),
                    "seed " + seed + ": every room but the first must be joined");
        }
    }

    /** And the setting really is a setting. */
    @Test
    void changingTheFileChangesTheShape() {
        var wide = DungeonSettings.parse("""
                ProceduralMap
                  Generation = Layout
                    CorridorWidth = 4
                  End
                End
                """);

        // Both from the same defaults, differing only in the corridor: comparing a
        // re-tuned file against the shipped one would be measuring the rooms.
        var narrowFloor = DungeonGenerator.generate(9L, DungeonSettings.parse(""), 1);
        var wideFloor = DungeonGenerator.generate(9L, wide, 1);

        assertTrue(openCells(wideFloor) > openCells(narrowFloor),
                "wider corridors should carve more floor");
    }

    private static int openCells(GeneratedDungeon floor) {
        int open = 0;
        for (var line : floor.asciiMap().split("\n")) {
            for (var c : line.toCharArray()) {
                if (c == '.') {
                    open++;
                }
            }
        }
        return open;
    }
}
