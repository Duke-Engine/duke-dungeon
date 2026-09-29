package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.BiomeFile;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** The floor carved out of the rock: grown rather than cut, all of it reachable, and its loops no short cut. */
class CaveTest {

    /**
     * Whole floors, each its depth's theme: these tune one theme's ground and watch what it does, and a floor that
     * mixes biomes never asks the depth's theme — BiomeFloorTest holds the same promises for those.
     */
    private static final DungeonSettings SETTINGS = BiomeFile.listing();

    /** The shipped files with {@code from} rewritten as {@code to}, which has to be there to rewrite. */
    private static String rewritten(String data, String from, String to) {
        assertTrue(data.contains(from), "the shipped files no longer say " + from.strip());
        return data.replace(from, to);
    }

    private static String[] rows(GeneratedDungeon floor) {
        return floor.asciiMap().strip().split("\n");
    }

    /** Chambers are grown, not cut: hardly a footprint comes out a solid rectangle of floor, in either ground. */
    @Test
    void chambersAreNotRectangles() {
        int chambers = 0;
        int rectangles = 0;
        for (long seed = 0; seed < 40; seed++) {
            for (int depth : new int[] {1, 3}) {
                var floor = DungeonGenerator.generate(seed, SETTINGS, depth);
                var rows = rows(floor);
                for (var room : floor.rooms()) {
                    chambers++;
                    boolean solid = true;
                    for (int y = room.y(); y < room.y() + room.h() && solid; y++) {
                        for (int x = room.x(); x < room.x() + room.w(); x++) {
                            solid &= rows[y].charAt(x) != '#';
                        }
                    }
                    rectangles += solid ? 1 : 0;
                }
            }
        }
        assertTrue(rectangles * 20 < chambers, rectangles + " of " + chambers + " chambers came out rectangles");
    }

    /** Nothing is left carved and cut off: every cell of floor can be walked to from the way in. */
    @Test
    void everyCellOfFloorCanBeWalkedTo() {
        for (long seed = 0; seed <= 60; seed++) {
            for (int depth = 1; depth <= 4; depth++) {
                var floor = DungeonGenerator.generate(seed, SETTINGS, depth);
                var rows = rows(floor);
                var reached = new boolean[rows.length][rows[0].length()];
                var queue = new ArrayDeque<int[]>();
                reached[floor.hero().cellY()][floor.hero().cellX()] = true;
                queue.add(new int[] {floor.hero().cellX(), floor.hero().cellY()});
                int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                while (!queue.isEmpty()) {
                    var at = queue.poll();
                    for (var step : steps) {
                        int x = at[0] + step[0];
                        int y = at[1] + step[1];
                        if (rows[y].charAt(x) != '#' && !reached[y][x]) {
                            reached[y][x] = true;
                            queue.add(new int[] {x, y});
                        }
                    }
                }
                for (int y = 0; y < rows.length; y++) {
                    for (int x = 0; x < rows[y].length(); x++) {
                        assertTrue(rows[y].charAt(x) == '#' || reached[y][x],
                                "seed " + seed + ", depth " + depth + ": floor at " + x + "," + y + " is cut off");
                    }
                }
            }
        }
    }

    /**
     * A loop joins chambers the same number of tunnels from the way in, or one apart, never the boss's, and never
     * two the tree already joins — so a walk to the boss still crosses as many chambers as the tree says.
     */
    @Test
    void aLoopIsNeverAShortCut() {
        int loops = 0;
        for (long seed = 0; seed < 60; seed++) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 1);
            var depth = new int[floor.rooms().size()];
            for (var link : floor.links()) {
                depth[link.to()] = depth[link.from()] + 1;
            }
            for (var loop : Cave.loops(floor.rooms(), floor.links(), floor.bossRoom(), SETTINGS.maxRoomSpacing(), 100)) {
                loops++;
                assertTrue(loop.from() != floor.bossRoom() && loop.to() != floor.bossRoom(),
                        "seed " + seed + ": a loop into the boss's chamber");
                assertTrue(Math.abs(depth[loop.from()] - depth[loop.to()]) <= 1,
                        "seed " + seed + ": a loop from " + depth[loop.from()] + " tunnels in to " + depth[loop.to()]);
                assertFalse(floor.links().contains(loop) || floor.links().contains(
                        new GeneratedDungeon.Link(loop.to(), loop.from())), "seed " + seed + ": a loop the tree has");
            }
        }
        assertTrue(loops > 60, "asked for as many as there are chambers, and only " + loops + " were ever found");
    }

    /** No loops asked for, none drawn — and the tree is untouched either way. */
    @Test
    void aTerrainWithNoLoopsHasNone() {
        var floor = DungeonGenerator.generate(3L, SETTINGS, 1);
        assertEquals(java.util.List.of(), Cave.loops(floor.rooms(), floor.links(), floor.bossRoom(), 30, 0));
    }

    /**
     * Islands only ever take floor away, and only where they were asked for: the same floor with none has every
     * cell of it, and more.
     */
    @Test
    void islandsAreRockLeftStandingInTheFloor() {
        var bare = DungeonSettings.parse(rewritten(rewritten(BiomeFile.textListing(),
                "    IslandsPerRoom = [1, 3]\n", "    IslandsPerRoom = [0, 0]\n"),
                "    IslandsPerRoom = [0, 2]\n", "    IslandsPerRoom = [0, 0]\n"));
        int fewer = 0;
        for (long seed = 0; seed < 20; seed++) {
            var withIslands = rows(DungeonGenerator.generate(seed, SETTINGS, 1));
            var without = rows(DungeonGenerator.generate(seed, bare, 1));
            int taken = 0;
            for (int y = 0; y < without.length; y++) {
                for (int x = 0; x < without[y].length(); x++) {
                    assertFalse(withIslands[y].charAt(x) != '#' && without[y].charAt(x) == '#',
                            "seed " + seed + ": an island made floor at " + x + "," + y);
                    taken += withIslands[y].charAt(x) == '#' && without[y].charAt(x) != '#' ? 1 : 0;
                }
            }
            fewer += taken > 0 ? 1 : 0;
        }
        assertTrue(fewer >= 15, "the wood asks for a grove in every glade, and only " + fewer + " floors of 20 had one");
    }
}
