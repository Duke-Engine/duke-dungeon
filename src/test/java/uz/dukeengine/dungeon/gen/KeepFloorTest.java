package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Prop;

/** Every floor of the descent ends in the boss's keep, and the keep keeps every promise a floor makes. */
class KeepFloorTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int SEEDS = 200;

    private static GeneratedDungeon floor(long seed) {
        return DungeonGenerator.generate(seed, SETTINGS, 1 + (int) (seed % 4));
    }

    @Test
    void everyFloorEndsInAKeepWithItsBossInTheMiddleAndItsGateInTheDoorway() {
        int shortOfTheEnd = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            var floor = floor(seed);
            var keep = floor.keep();
            assertNotNull(keep, "seed " + seed + " has no keep");
            assertTrue(SETTINGS.keep().sizes().contains(keep.size()), "seed " + seed + ": " + keep.size() + " across");
            assertEquals(floor.rooms().size() - 1, floor.bossRoom(), "seed " + seed + ": the keep is the last room");
            assertEquals(keep.walls(), floor.rooms().get(floor.bossRoom()));
            assertEquals(Placement.atCell(keep.walls().centerCellX(), keep.walls().centerCellY()), floor.boss().at(),
                    "seed " + seed + ": the boss waits in the middle of the court");
            int boss = floor.bossRoom();
            assertEquals(List.of(new Link(keep.chamber(), boss)),
                    floor.links().stream().filter(link -> link.from() == boss || link.to() == boss).toList(),
                    "seed " + seed + ": one road, to its own chamber");
            var gate = keep.gate();
            assertTrue(floor.props().contains(new Prop(SETTINGS.keep().gate(), Placement.atCell(gate[0], gate[1]))),
                    "seed " + seed + ": no gate in the doorway");
            // Nobody stands in the way in: the gate is solid, and a body inside it never moves again.
            assertTrue(floor.monsters().stream().noneMatch(m -> keep.isDoorway(m.at().cellX(), m.at().cellY())
                            || keep.isStair(m.at().cellX(), m.at().cellY())),
                    "seed " + seed + ": something stands in the keep's way in");
            // The gate is in the side facing its chamber: stepping out of it goes toward the chamber's middle.
            var chamber = floor.rooms().get(keep.chamber());
            var out = keep.roadStart();
            assertTrue(Math.abs(out[0] - chamber.centerCellX()) + Math.abs(out[1] - chamber.centerCellY())
                            < Math.abs(gate[0] - chamber.centerCellX()) + Math.abs(gate[1] - chamber.centerCellY()),
                    "seed " + seed + ": its gate faces away from its chamber");
            var depth = new int[floor.rooms().size()];
            int deepest = 0;
            for (var link : floor.links()) {
                depth[link.to()] = depth[link.from()] + 1;
                if (link.to() != boss) {
                    deepest = Math.max(deepest, depth[link.to()]);
                }
            }
            if (depth[keep.chamber()] < deepest - 1) {
                shortOfTheEnd++;
            }
        }
        assertTrue(shortOfTheEnd <= SEEDS / 50, shortOfTheEnd + " keeps stood two or more tunnels short of the end");
    }

    /** The last floor's guard stands inside the court, never on its wall or in its doorway. */
    @Test
    void theGuardStandsInTheCourt() {
        int depth = SETTINGS.finalDepth();
        int guards = SETTINGS.bossGuardsAt(depth).stream().mapToInt(guard -> guard.count()).sum();
        for (long seed = 0; seed < 40; seed++) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, depth);
            var keep = floor.keep();
            assertEquals(guards, floor.monsters().stream()
                    .filter(monster -> keep.isCourt(monster.at().cellX(), monster.at().cellY())).count(),
                    "seed " + seed);
        }
    }

    /** Up its stair and through its doorway, and no other way: the engine's own step rule over map and storeys. */
    @Test
    void theCourtIsReachedOnlyThroughItsDoorway() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var floor = floor(seed);
            var keep = floor.keep();
            var open = reached(floor, false);
            var shut = reached(floor, true);
            var walls = keep.walls();
            for (int y = walls.y(); y < walls.y() + walls.h(); y++) {
                for (int x = walls.x(); x < walls.x() + walls.w(); x++) {
                    if (keep.isCourt(x, y)) {
                        assertTrue(open[y][x], "seed " + seed + ": court cell " + x + "," + y + " is out of reach");
                        assertFalse(shut[y][x], "seed " + seed + ": court cell " + x + "," + y
                                + " is reached round the gate");
                    }
                }
            }
            for (int room = 0; room < floor.bossRoom(); room++) {
                var middle = floor.rooms().get(room);
                assertTrue(shut[middle.centerCellY()][middle.centerCellX()],
                        "seed " + seed + ": chamber " + room + " is cut off");
            }
        }
    }

    /** Court and doorway a storey up, a stair before the doorway, a ring of wall, and everything else on the ground. */
    @Test
    void itStandsAStoreyUpWithAStairToItsDoorway() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var floor = floor(seed);
            var keep = floor.keep();
            var levels = floor.levelMap().strip().split("\n");
            var cells = floor.asciiMap().strip().split("\n");
            for (int y = 0; y < levels.length; y++) {
                for (int x = 0; x < levels[y].length(); x++) {
                    boolean wall = keep.holds(x, y) && !keep.isCourt(x, y) && !keep.isDoorway(x, y)
                            && !keep.isStair(x, y);
                    if (wall) {
                        assertEquals('#', cells[y].charAt(x), "seed " + seed + ": the ring is open at " + x + "," + y);
                    }
                    char wanted = cells[y].charAt(x) == '#' ? '#'
                            : keep.isCourt(x, y) || keep.isDoorway(x, y) ? '1' : keep.isStair(x, y) ? '/' : '0';
                    assertEquals(wanted, levels[y].charAt(x), "seed " + seed + " at " + x + "," + y);
                }
            }
            assertEquals(1, floor.roomStoreys().get(floor.bossRoom()));
        }
    }

    /** No grass, pebble or ore on it: it is built. */
    @Test
    void nothingIsStrewnOnIt() {
        for (long seed = 0; seed < 60; seed++) {
            var floor = floor(seed);
            for (var piece : floor.scenery()) {
                assertFalse(floor.keep().holds((int) piece.x(), (int) piece.y()), "seed " + seed + ": " + piece);
            }
        }
    }

    /** A stage keeps no look for one, as it keeps no biome for a cell: it is cut without. */
    @Test
    void aStageIsCutWithoutOne() {
        var stage = DungeonGenerator.generate(3L, SETTINGS, 1,
                Layout.sized(SETTINGS, SETTINGS.mapWidth(), SETTINGS.mapHeight(), SETTINGS.maxRooms()));

        assertNull(stage.keep());
        assertTrue(stage.props().stream().noneMatch(prop -> prop.kind().equals(SETTINGS.keep().gate())));
    }

    @Test
    void theSameSeedBuildsTheSameKeep() {
        assertEquals(floor(7L).keep(), floor(7L).keep());
    }

    /** Every cell the way in walks to by the engine's step rule, with the doorway shut or not. */
    private static boolean[][] reached(GeneratedDungeon floor, boolean shut) {
        var grid = MapLoader.fromText(floor.asciiMap());
        MapLoader.levels(grid, floor.levelMap());
        var keep = floor.keep();
        if (shut) {
            var walls = keep.walls();
            for (int y = walls.y(); y < walls.y() + walls.h(); y++) {
                for (int x = walls.x(); x < walls.x() + walls.w(); x++) {
                    if (keep.isDoorway(x, y)) {
                        grid.setBlocked(x, y, true);
                    }
                }
            }
        }
        var reached = new boolean[grid.getHeight()][grid.getWidth()];
        var queue = new ArrayDeque<int[]>();
        int startX = floor.hero().cellX();
        int startY = floor.hero().cellY();
        reached[startY][startX] = true;
        queue.add(new int[] {startX, startY});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            var at = queue.poll();
            for (var step : steps) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                if (grid.inBounds(x, y) && !reached[y][x] && grid.canStep(at[0], at[1], x, y)) {
                    reached[y][x] = true;
                    queue.add(new int[] {x, y});
                }
            }
        }
        return reached;
    }
}
