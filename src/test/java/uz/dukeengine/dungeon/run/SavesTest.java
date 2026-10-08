package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The saves folder: made when first written to, read newest first, and a broken file left out rather than fatal. */
class SavesTest {

    private static SaveGame atDepth(int depth) {
        return new SaveGame(depth, null, depth, "Rogue", 0, List.of(), List.of());
    }

    @Test
    void aMissingFolderIsMadeByTheFirstSave(@TempDir Path home) throws Exception {
        var saves = new Saves(home.resolve("not/yet/saves"));
        saves.write(Saves.AUTOSAVE, atDepth(2));
        assertTrue(Files.isRegularFile(home.resolve("not/yet/saves/autosave.duke")));
        assertEquals(atDepth(2), saves.list().getFirst().game());
    }

    @Test
    void anEmptyOrMissingFolderHoldsNothing(@TempDir Path home) {
        assertEquals(List.of(), new Saves(home.resolve("nowhere")).list());
    }

    @Test
    void savesAreListedNewestFirstAndABrokenOneIsLeftOut(@TempDir Path home) throws Exception {
        var saves = new Saves(home);
        saves.write(Saves.AUTOSAVE, atDepth(1));
        saves.write("slot1", atDepth(2));
        Files.setLastModifiedTime(home.resolve("autosave.duke"), FileTime.fromMillis(1_000_000L));
        Files.setLastModifiedTime(home.resolve("slot1.duke"), FileTime.fromMillis(2_000_000L));
        Files.writeString(home.resolve("slot2.duke"), "Save\n  Version = 99\nEnd\n");

        var listed = saves.list();
        assertEquals(List.of("slot1", Saves.AUTOSAVE), listed.stream().map(Saves.Saved::slot).toList());
        assertEquals(2, listed.getFirst().game().depth());
    }

    @Test
    void writingAgainReplacesTheSave(@TempDir Path home) throws Exception {
        var saves = new Saves(home);
        saves.write(Saves.AUTOSAVE, atDepth(1));
        saves.write(Saves.AUTOSAVE, atDepth(3));
        assertEquals(1, saves.list().size());
        assertEquals(3, saves.list().getFirst().game().depth());
    }
}
