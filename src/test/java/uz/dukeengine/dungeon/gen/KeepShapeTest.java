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

    /** In solid rock beside the deeper of two chambers, its gate toward it, its road no longer than it may be. */
    @Test
    void itGoesInSolidRockBesideTheDeepestChamberFacingIt() {
        var rooms = List.of(new Room(3, 10, 9, 9), new Room(20, 10, 9, 9));
        var links = List.of(new Link(0, 1));
        var cave = Cave.carve(new DeterministicRng(1), 60, 30, rooms, links, 1, 2, 30, Theme.Terrain.DEFAULTS);

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
}
