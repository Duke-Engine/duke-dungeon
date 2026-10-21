package uz.dukeengine.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every map folder's own blocks are read with the game's, at startup: the people and quests a map keeps beside it are
 * there whichever way the map is reached -- the campaign's world, the menu, --map (docs/plan/2026-10-21-npc-brains-and-quests.md).
 */
class MapBlocksTest {

    @Test
    void aMapFoldersBlocksAreInTheGamesSettings() {
        assertTrue(DungeonSettings.mapBlocksText().contains("; The talk map's own blocks"),
                "the shipped talk.duke is read: " + DungeonSettings.mapBlocksText());
        var text = DungeonSettings.joinBlocks(Map.of("probe", "Speaker\n  Name = MapBlockProbe\nEnd\n"));
        assertNotNull(DungeonSettings.parse(Content.data() + "\n" + text).story().speaker("MapBlockProbe"));
    }

    @Test
    void oneNameInTwoMapsIsRefused() {
        var both = new LinkedHashMap<String, String>();
        both.put("north", "Speaker\n  Name = GateGuard\nEnd\n");
        both.put("south", "Speaker\n  Name = GateGuard\nEnd\n");
        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.joinBlocks(both));
        assertTrue(refused.getMessage().contains("north") && refused.getMessage().contains("south")
                && refused.getMessage().contains("GateGuard"), refused.getMessage());
    }

    @Test
    void theSameNameInDifferentKindsOfBlockIsNoClash() {
        var both = new LinkedHashMap<String, String>();
        both.put("north", "Speaker\n  Name = Smith\nEnd\n");
        both.put("south", "Conversation\n  Name = Smith\nEnd\n");
        assertTrue(DungeonSettings.joinBlocks(both).contains("Conversation"));
    }
}
