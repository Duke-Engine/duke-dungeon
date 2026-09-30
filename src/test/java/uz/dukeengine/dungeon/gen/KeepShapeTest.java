package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.world.Theme;

/** The keep's cells — wall, doorway, stair, court — and the rule for where one goes. */
class KeepShapeTest {

    /** Nine across at (10, 10), its gate in the north wall. */
    private static final Keep NORTH = new Keep(new Room(10, 10, 9, 9), Keep.Side.NORTH, 0);

    @Test
    void itsGateStandsInTheMiddleOfItsSideInADoorwayThreeWide() {
        assertArrayEquals(new int[] {14, 10}, NORTH.gate());
        for (int x = 10; x < 19; x++) {
            assertEquals(x >= 13 && x <= 15, NORTH.isDoorway(x, 10), "cell " + x + ",10");
        }
        assertFalse(NORTH.isDoorway(14, 11), "the doorway is in the wall, not the court");
    }

    @Test
    void itsStairIsTheStepOutsideItsDoorwayAndItsRoadLeavesBeyondIt() {
        for (int x = 13; x <= 15; x++) {
            assertTrue(NORTH.isStair(x, 9), "cell " + x + ",9");
        }
        assertFalse(NORTH.isStair(12, 9), "beside the stair is the ground outside");
        assertFalse(NORTH.isStair(14, 11), "inside the doorway is the court");
        assertArrayEquals(new int[] {14, 8}, NORTH.roadStart());
    }

    @Test
    void itsCourtIsInsideItsRingAStoreyUp() {
        assertTrue(NORTH.isCourt(11, 11));
        assertTrue(NORTH.isCourt(17, 17));
        assertFalse(NORTH.isCourt(10, 14), "the ring is wall");
        assertFalse(NORTH.isCourt(18, 14));
        assertEquals('1', NORTH.storeyAt(14, 14));
        assertEquals('1', NORTH.storeyAt(14, 10), "the doorway is level with the court");
        assertEquals('/', NORTH.storeyAt(14, 9));
        assertEquals('0', NORTH.storeyAt(14, 8));
    }

    @Test
    void itHoldsItsSquareAndItsStairAndNothingElse() {
        assertTrue(NORTH.holds(10, 10), "a corner of its wall");
        assertTrue(NORTH.holds(18, 18));
        assertTrue(NORTH.holds(14, 9), "its stair");
        assertFalse(NORTH.holds(12, 9));
        assertFalse(NORTH.holds(14, 8), "the road is the floor's");
        assertFalse(NORTH.holds(19, 14));
    }

    @Test
    void aGateInAWallRunningDownTheMapFacesAQuarterTurn() {
        var west = new Keep(new Room(10, 10, 9, 9), Keep.Side.WEST, 0);

        assertArrayEquals(new int[] {10, 14}, west.gate());
        assertTrue(west.isDoorway(10, 13) && west.isDoorway(10, 15));
        assertTrue(west.isStair(9, 14));
        assertArrayEquals(new int[] {8, 14}, west.roadStart());
        assertEquals((float) (StrictMath.PI / 2), west.facing());
        assertEquals(0f, NORTH.facing());
    }

    /** Reusable cave for placement tests. */
    private static Cave twoRoomCave(int seed) {
        var rooms = List.of(new Room(3, 10, 9, 9), new Room(20, 10, 9, 9));
        var links = List.of(new Link(0, 1));
        return Cave.carve(new DeterministicRng(seed), 60, 30, rooms, links, 1, 2, 30, Theme.Terrain.DEFAULTS);
    }

    /** In solid rock beside the deeper of two chambers, its gate toward it, its road no longer than it may be. */
    @Test
    void itGoesInSolidRockBesideTheDeepestChamberFacingIt() {
        var rooms = List.of(new Room(3, 10, 9, 9), new Room(20, 10, 9, 9));
        var links = List.of(new Link(0, 1));
        var cave = twoRoomCave(1);

        var keep = Keep.site(cave, rooms, links, List.of(9), 30);

        assertNotNull(keep, "a floor that is mostly rock has room for a keep");
        assertEquals(1, keep.chamber(), "beside the deeper of the two");
        var walls = keep.walls();
        for (int y = walls.y() - 1; y <= walls.y() + walls.h(); y++) {
            for (int x = walls.x() - 1; x <= walls.x() + walls.w(); x++) {
                assertTrue(cave.isStone(x, y), "rock at " + x + "," + y + " before it is built");
            }
        }
        var chamber = rooms.get(1);
        int road = Math.abs(chamber.centerCellX() - walls.centerCellX())
                + Math.abs(chamber.centerCellY() - walls.centerCellY()) - walls.w() / 2;
        assertTrue(road <= 30, "its road is no longer than a chamber may stand from another: " + road);
        var gate = keep.gate();
        var out = keep.roadStart();
        assertTrue(Math.abs(out[0] - chamber.centerCellX()) + Math.abs(out[1] - chamber.centerCellY())
                        < Math.abs(gate[0] - chamber.centerCellX()) + Math.abs(gate[1] - chamber.centerCellY()),
                "its gate faces away from its chamber: " + keep);
    }

    /** And none where the rock has no square big enough. */
    @Test
    void thereIsNoneWhereTheRockHasNoRoom() {
        var rooms = List.of(new Room(2, 2, 11, 11), new Room(14, 2, 11, 11));
        var links = List.of(new Link(0, 1));
        var cave = Cave.carve(new DeterministicRng(1), 27, 15, rooms, links, 1, 2, 30, Theme.Terrain.DEFAULTS);

        assertNull(Keep.site(cave, rooms, links, List.of(15, 9), 30));
    }

    /** Larger sizes are tried first beside the deepest chamber. */
    @Test
    void largerSizeFirstBesideTheDeepest() {
        var rooms = List.of(new Room(3, 10, 9, 9), new Room(20, 10, 9, 9));
        var links = List.of(new Link(0, 1));
        var cave = twoRoomCave(1);

        var keep = Keep.site(cave, rooms, links, List.of(11, 9), 30);

        assertNotNull(keep, "an 11-cell keep fits in the same rock");
        assertEquals(11, keep.size(), "phase 1 returns the larger size");
        assertEquals(1, keep.chamber(), "an 11 fits beside either chamber; the deepest is tried first");
    }

    /** A larger keep one tunnel short of the deepest beats a smaller one beside the deepest. */
    @Test
    void aLargerKeepOneTunnelShortBeatsASmallerOneBesideTheDeepest() {
        // With Link(0, 1): chamber 1 is the deepest, chamber 0 is one tunnel short
        var rooms = List.of(new Room(20, 10, 9, 9), new Room(3, 10, 9, 9));
        var links = List.of(new Link(0, 1));
        var cave = Cave.carve(new DeterministicRng(1), 60, 30, rooms, links, 1, 2, 12, Theme.Terrain.DEFAULTS);

        var keep = Keep.site(cave, rooms, links, List.of(11, 9), 12);

        assertNotNull(keep, "an 11-cell keep fits in solid rock");
        assertEquals(11, keep.size(), "the 11 beside chamber 0 (one-tunnel-short) beats 9 beside chamber 1 (deepest)");
        assertEquals(0, keep.chamber(), "beside chamber 0, which is one tunnel from the deepest");
    }

    /** The road cannot exceed maxSpacing. */
    @Test
    void roadBoundEnforced() {
        var rooms = List.of(new Room(3, 10, 9, 9), new Room(20, 10, 9, 9));
        var links = List.of(new Link(0, 1));
        var cave = twoRoomCave(1);

        assertNull(Keep.site(cave, rooms, links, List.of(9), 3), "no keep fits with road <= 3");
    }

    /** Shortest road wins, and ties go to reading order. */
    @Test
    void shortestRoadAndReadingOrder() {
        var rooms = List.of(new Room(3, 10, 9, 9), new Room(20, 10, 9, 9));
        var links = List.of(new Link(0, 1));

        // Test across multiple seeds; seed 2 has ties that test reading-order tie-breaking
        for (int seed = 1; seed <= 20; seed++) {
            var cave = twoRoomCave(seed);
            var keep = Keep.site(cave, rooms, links, List.of(9), 30);
            if (keep == null) {
                continue;
            }
            var chamber = rooms.get(1);
            int chosenRoad = Math.abs(chamber.centerCellX() - keep.walls().centerCellX())
                    + Math.abs(chamber.centerCellY() - keep.walls().centerCellY()) - keep.walls().w() / 2;

            // Brute force: check every valid position
            int earlierOrEqual = 0;
            for (int y = 2; y + 9 + 2 <= 30; y++) {
                for (int x = 2; x + 9 + 2 <= 60; x++) {
                    // Must be surrounded by rock
                    boolean surrounded = true;
                    for (int dy = -1; dy <= 9; dy++) {
                        for (int dx = -1; dx <= 9; dx++) {
                            if (!cave.isStone(x + dx, y + dy)) {
                                surrounded = false;
                                break;
                            }
                        }
                        if (!surrounded) break;
                    }
                    if (!surrounded) {
                        continue;
                    }
                    // Check road length
                    int road = Math.abs(chamber.centerCellX() - (x + 4))
                            + Math.abs(chamber.centerCellY() - (y + 4)) - 4;
                    if (road > 30) {
                        continue;
                    }
                    if (road < chosenRoad || (road == chosenRoad && (y < keep.walls().y()
                            || (y == keep.walls().y() && x < keep.walls().x())))) {
                        earlierOrEqual++;
                    }
                }
            }
            assertEquals(0, earlierOrEqual, "seed " + seed + ": no position has shorter road or equal road in earlier order");
        }
    }

    /** All four sides work: EAST has the gate on the right, SOUTH at the bottom. */
    @Test
    void eastAndSouthSidesSymmetric() {
        var east = new Keep(new Room(10, 10, 9, 9), Keep.Side.EAST, 0);
        assertArrayEquals(new int[] {18, 14}, east.gate(), "EAST gate on the right wall middle");
        assertTrue(east.isDoorway(18, 13) && east.isDoorway(18, 14) && east.isDoorway(18, 15),
                "EAST doorway runs along x=18");
        assertTrue(east.isStair(19, 14), "EAST stair is x=19");
        assertArrayEquals(new int[] {20, 14}, east.roadStart(), "EAST road starts beyond the stair");
        assertEquals((float) (StrictMath.PI / 2), east.facing(), "EAST facing is a quarter turn");

        var south = new Keep(new Room(10, 10, 9, 9), Keep.Side.SOUTH, 0);
        assertArrayEquals(new int[] {14, 18}, south.gate(), "SOUTH gate on the bottom wall middle");
        assertTrue(south.isDoorway(13, 18) && south.isDoorway(14, 18) && south.isDoorway(15, 18),
                "SOUTH doorway runs along y=18");
        assertTrue(south.isStair(14, 19), "SOUTH stair is y=19");
        assertArrayEquals(new int[] {14, 20}, south.roadStart(), "SOUTH road starts beyond the stair");
        assertEquals(0f, south.facing(), "SOUTH facing is the same as NORTH");
    }
}
