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

    /** A player's map folder: its .map the people map's, and a .duke of the given blocks. */
    private static void playerMap(java.nio.file.Path theirs, String name, String duke) throws java.io.IOException {
        var folder = java.nio.file.Files.createDirectories(theirs.resolve(name));
        var shipped = DungeonSettings.class.getResourceAsStream("/maps/people/people.map");
        java.nio.file.Files.write(folder.resolve(name + ".map"), shipped.readAllBytes());
        java.nio.file.Files.writeString(folder.resolve(name + ".duke"), duke);
    }

    /**
     * A player's own map whose blocks are broken, or that writes a name another player's map writes, is skipped -- with
     * a warning -- rather than stopping the game; the good ones are still read.
     */
    @Test
    void aPlayersBrokenMapIsSkipped(@org.junit.jupiter.api.io.TempDir java.nio.file.Path theirs)
            throws java.io.IOException {
        playerMap(theirs, "agood", "Speaker\n  Name = PlayerGood\nEnd\n");
        playerMap(theirs, "broken", "Person\n  Name = PlayerBroken\n  Kind = Nobody\nEnd\n");
        playerMap(theirs, "north", "Speaker\n  Name = PlayerTwice\nEnd\n");
        playerMap(theirs, "south", "Speaker\n  Name = PlayerTwice\nEnd\n");
        var text = DungeonSettings.mapBlocksText(theirs);
        assertTrue(text.contains("PlayerGood"), text);
        assertTrue(!text.contains("PlayerBroken"), "the broken map's blocks are skipped");
        assertTrue(text.indexOf("PlayerTwice") == text.lastIndexOf("PlayerTwice"), "the second of the two is skipped");
        assertTrue(text.contains("; The talk map's own blocks"), "the shipped maps are read still");
        assertNotNull(DungeonSettings.parse(Content.data() + "\n" + text).story().speaker("PlayerGood"));
    }

    @Test
    void theSameNameInDifferentKindsOfBlockIsNoClash() {
        var both = new LinkedHashMap<String, String>();
        both.put("north", "Speaker\n  Name = Smith\nEnd\n");
        both.put("south", "Conversation\n  Name = Smith\nEnd\n");
        assertTrue(DungeonSettings.joinBlocks(both).contains("Conversation"));
    }
}
