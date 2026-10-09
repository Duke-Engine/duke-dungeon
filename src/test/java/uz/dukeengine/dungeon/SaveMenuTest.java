package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Shell;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.run.Saves;

/** The way in: continue (only with a save), a new game, load (only with saves), the credits; and saving in play. */
class SaveMenuTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Shell.Question FRESH = new Shell.Question("NEW", "", List.of(new Shell.Option("x", "", null)));

    private static Saves.Saved saved(String slot, int depth, long when) {
        return new Saves.Saved(slot, new SaveGame(1L, null, depth, "Rogue", 0, List.of(), List.of()),
                FileTime.fromMillis(when));
    }

    private static final Shell.Option CREDITS = new Shell.Option("CREDITS", "", null,
            new Shell.Question("C", "", List.of()));

    @Test
    void withNoSaveThereIsNoContinueAndNoLoad() {
        var question = Main.savedGames(SETTINGS, List.of(), name -> name, saved -> { }, FRESH, CREDITS);
        assertEquals(List.of(SETTINGS.saveMenu().newWord(), "CREDITS"),
                question.options().stream().map(Shell.Option::label).toList());
        assertSame(FRESH, question.options().getFirst().next(), "a new game walks the path it always did");
    }

    @Test
    void continueLoadsTheNewestAndLoadListsThemAll() {
        var loaded = new ArrayList<String>();
        var newest = saved(Saves.AUTOSAVE, 3, 2);
        var older = saved("slot1", 1, 1);
        var words = SETTINGS.saveMenu();

        var question = Main.savedGames(SETTINGS, List.of(newest, older), name -> name, s -> loaded.add(s.slot()),
                FRESH, CREDITS);

        assertEquals(List.of(words.continueWord(), words.newWord(), words.loadWord(), "CREDITS"),
                question.options().stream().map(Shell.Option::label).toList());
        var go = question.options().getFirst();
        assertEquals(String.format(words.aboutSave(), "Rogue", 1, 3), go.blurb());
        assertNull(go.next(), "continuing starts the game");
        go.taken().run();
        assertEquals(List.of(Saves.AUTOSAVE), loaded);

        var slots = question.options().get(2).next();
        assertEquals(List.of(words.autosaveWord(), String.format(words.slotWord(), "1")),
                slots.options().stream().map(Shell.Option::label).toList());
        slots.options().get(1).taken().run();
        assertEquals(List.of(Saves.AUTOSAVE, "slot1"), loaded);
        assertSame(FRESH, question.options().get(1).next(), "a new game walks the path it always did");
    }

    // ---- in the game: Saqlash, and leaving ----

    @Test
    void saqlashWritesTheGameAsItIsIntoItsSlot(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home) {
        var session = Dungeon.newSession(11L, SETTINGS);
        session.game().runHeadless(1);
        var saves = new Saves(home);

        assertTrue(Main.saveTo(session, saves, "slot2"));
        var written = saves.list();
        assertEquals("slot2", written.getFirst().slot());
        assertEquals(session.run().saveNow(), written.getFirst().game());
    }

    @Test
    void nothingIsWrittenForAGameNotStarted(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home) {
        var saves = new Saves(home);
        assertFalse(Main.saveTo(Dungeon.newSession(11L, SETTINGS), saves, Saves.AUTOSAVE));
        assertEquals(List.of(), saves.list());
    }

    @Test
    void thePauseMenusRowOffersTheThreeSlots() {
        var words = SETTINGS.saveMenu();
        var written = new ArrayList<String>();
        var row = Main.saveRow(SETTINGS, written::add);
        assertEquals(words.saveWord(), row.label());
        var slots = row.next().options();
        assertEquals(3, slots.size());
        assertEquals(String.format(words.slotWord(), "2"), slots.get(1).label());
        slots.get(1).taken().run();
        assertEquals(List.of("slot2"), written);
    }
}
