package uz.dukeengine.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.loot.ItemUse;
import uz.dukeengine.dungeon.loot.LootKind;

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

    /**
     * A key opens the gate and does nothing else: one whose Use is not UNLOCK is a thing carried for nothing, and the
     * file is refused naming that item and the rule — not only as a keep whose gate nothing opens.
     */
    @Test
    void aKeyThatOpensNothingIsRefusedNamingIt() {
        var data = Content.data().replace("  Use = UNLOCK\n", "");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("LootItem Key") && refused.getMessage().contains("KEY")
                && refused.getMessage().contains("UNLOCK"), refused.getMessage());
    }

    /** Whether or not the file builds a keep: a key is a key, and carried for nothing wherever it is. */
    @Test
    void aKeyThatOpensNothingIsRefusedWithNoKeepToo() {
        var data = Content.data().replace("  Use = UNLOCK\n", "")
                .replaceFirst("(?s)  Keep = Keep\\n.*?\\n  End\\n", "");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("LootItem Key") && refused.getMessage().contains("UNLOCK"),
                refused.getMessage());
    }

    /**
     * The gate opens only to a key, and a key is what the floor lays: a file with a keep and no item of Kind = KEY
     * would build a floor with nothing to open its gate with. The key here is an ordinary thing, with no Use either,
     * so that it is the keep that is refused and not an item.
     */
    @Test
    void aKeepWithNoKeyToLayIsRefused() {
        var data = Content.data().replace("  Kind = KEY\n  Use = UNLOCK\n", "  Kind = ATTACK\n");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("Keep") && refused.getMessage().contains("Kind = KEY"),
                refused.getMessage());
    }

    /**
     * Only a key opens the gate. What clears the bags at each floor, what the tracker looks for, what the floor lays
     * and what never joins all ask an item's Kind, and only the opening asks its Use: a thing that is not a key and
     * has Use = UNLOCK would open a gate and be none of those, so it is refused naming it — the keep's own key
     * standing right beside it, and whether or not the file builds a keep.
     */
    @Test
    void aThingThatOpensTheGateAndIsNotAKeyIsRefusedNamingIt() {
        var crowbar = """
                LootItem
                  Name = Crowbar
                  Icon = icons/stats/stat_strength.png
                  Kind = ATTACK
                  Use = UNLOCK
                  Value = 5
                  Weight = 10
                End
                """;
        var noKeep = Content.data().replaceFirst("(?s)  Keep = Keep\\n.*?\\n  End\\n", "");

        for (var data : new String[] {Content.data() + crowbar, noKeep + crowbar}) {
            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
            assertTrue(refused.getMessage().contains("LootItem Crowbar") && refused.getMessage().contains("UNLOCK")
                    && refused.getMessage().contains("KEY"), refused.getMessage());
        }
    }

    /** And with no keep there is no gate for a key to open, so the same file, with no key at all, is read. */
    @Test
    void aMapWithNoKeepNeedsNoKey() {
        var data = Content.data().replaceFirst("(?s)  Keep = Keep\\n.*?\\n  End\\n", "")
                .replace("  Kind = KEY\n  Use = UNLOCK\n", "  Kind = ATTACK\n");

        var settings = DungeonSettings.parse(data);

        assertTrue(settings.keep().sizes().isEmpty(), "no keep");
        assertTrue(settings.loot().stream().noneMatch(item -> item.kind() == LootKind.KEY
                || item.use() == ItemUse.UNLOCK), "and no key to open it");
    }

    /** The shipped files keep the rules: the descent builds a keep, and its key is what opens its gate, and only it. */
    @Test
    void theShippedFilesGiveTheKeepAKeyThatOpens() {
        var settings = DungeonSettings.load();
        var keys = settings.loot().stream().filter(item -> item.kind() == LootKind.KEY).toList();

        assertFalse(settings.keep().sizes().isEmpty(), "the premise: the descent builds a keep");
        assertFalse(keys.isEmpty(), "a keep with no key to lay");
        assertTrue(keys.stream().allMatch(key -> key.use() == ItemUse.UNLOCK), "a key that opens nothing");
        assertTrue(settings.loot().stream().filter(item -> item.use() == ItemUse.UNLOCK)
                .allMatch(item -> item.kind() == LootKind.KEY), "a thing that opens the gate and is not a key");
    }

    /** Drawn in the Keep theme, and a Look naming no theme is refused — by the link or by the check, either way. */
    @Test
    void itIsDrawnInATheme() {
        assertEquals("Keep", DungeonSettings.load().keep().look());
        var data = Content.data().replace("    Look = Keep\n", "    Look = Castle\n");

        assertThrows(RuntimeException.class, () -> DungeonSettings.parse(data));
    }
}
