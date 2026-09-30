package uz.dukeengine.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** What a map says about the boss's keep, and what it may not say. */
class KeepSettingsTest {

    /** The shipped descent builds one on every floor, in four sizes, with a gate in it. */
    @Test
    void theShippedDescentBuildsAKeepWithAGate() {
        var keep = DungeonSettings.load().keep();

        assertEquals(List.of(15, 13, 11, 9), keep.sizes());
        assertEquals("Gate", keep.gate());
    }

    /** A map that says nothing about a keep builds none. */
    @Test
    void aMapThatSaysNothingBuildsNone() {
        var data = Content.data().replaceFirst("(?s)  Keep = Keep\\n.*?\\n  End\\n", "");

        assertTrue(DungeonSettings.parse(data).keep().sizes().isEmpty());
    }

    /** Odd, so the boss and the gate each have a middle cell. */
    @Test
    void anEvenSizeIsRefusedByNumber() {
        var data = Content.data().replace("    Sizes = [15, 13, 11, 9]\n", "    Sizes = [15, 14]\n");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("14"), refused.getMessage());
    }

    /** The boss in the middle and a mage at each corner, a cell in from each wall: seven across at least. */
    @Test
    void aKeepTooSmallForItsGuardIsRefused() {
        var data = Content.data().replace("    Sizes = [15, 13, 11, 9]\n", "    Sizes = [15, 5]\n");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().endsWith(": 5"), refused.getMessage());
    }

    /** Seven across is the least that keeps the four corners off the boss, and it is enough. */
    @Test
    void aKeepSevenAcrossIsAccepted() {
        var data = Content.data().replace("    Sizes = [15, 13, 11, 9]\n", "    Sizes = [15, 7]\n");

        assertEquals(List.of(15, 7), DungeonSettings.parse(data).keep().sizes());
    }

    /** Largest first: the order is the preference, so a list that rises would build the smallest keep every time. */
    @Test
    void sizesNotLargestFirstAreRefused() {
        for (var sizes : new String[] {"[9, 15]", "[15, 15]"}) {
            var data = Content.data().replace("    Sizes = [15, 13, 11, 9]\n", "    Sizes = " + sizes + "\n");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
            assertTrue(refused.getMessage().contains("largest first"), refused.getMessage());
            assertTrue(refused.getMessage().contains(sizes), refused.getMessage());
        }
    }

    /** Something has to stand in the doorway. */
    @Test
    void aKeepWithNoGateIsRefused() {
        var data = Content.data().replace("    Gate = Gate\n", "");

        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
    }

    /** Drawn in the Keep theme, and a Look naming no theme is refused — by the link or by the check, either way. */
    @Test
    void itIsDrawnInATheme() {
        assertEquals("Keep", DungeonSettings.load().keep().look());
        var data = Content.data().replace("    Look = Keep\n", "    Look = Castle\n");

        assertThrows(RuntimeException.class, () -> DungeonSettings.parse(data));
    }
}
