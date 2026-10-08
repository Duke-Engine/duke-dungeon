package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** "Enter the dungeon" with a save to go back to: continue, load, or a new game — and with none, nothing new. */
class SaveMenuTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Shell.Question FRESH = new Shell.Question("NEW", "", List.of(new Shell.Option("x", "", null)));

    private static Saves.Saved saved(String slot, int depth, long when) {
        return new Saves.Saved(slot, new SaveGame(1L, null, depth, "Rogue", 0, List.of(), List.of()),
                FileTime.fromMillis(when));
    }

    @Test
    void withNoSaveTheWayInIsAsItWas() {
        assertSame(FRESH, Main.savedGames(SETTINGS, List.of(), name -> name, saved -> { }, FRESH));
    }

    @Test
    void continueLoadsTheNewestAndLoadListsThemAll() {
        var loaded = new ArrayList<String>();
        var newest = saved(Saves.AUTOSAVE, 3, 2);
        var older = saved("slot1", 1, 1);
        var words = SETTINGS.saveMenu();

        var question = Main.savedGames(SETTINGS, List.of(newest, older), name -> name, s -> loaded.add(s.slot()),
                FRESH);

        assertEquals(List.of(words.continueWord(), words.loadWord(), words.newWord()),
                question.options().stream().map(Shell.Option::label).toList());
        var go = question.options().getFirst();
        assertEquals(String.format(words.aboutSave(), "Rogue", 1, 3), go.blurb());
        assertNull(go.next(), "continuing starts the game");
        go.taken().run();
        assertEquals(List.of(Saves.AUTOSAVE), loaded);

        var slots = question.options().get(1).next();
        assertEquals(List.of(words.autosaveWord(), String.format(words.slotWord(), "1")),
                slots.options().stream().map(Shell.Option::label).toList());
        slots.options().get(1).taken().run();
        assertEquals(List.of(Saves.AUTOSAVE, "slot1"), loaded);
        assertSame(FRESH, question.options().get(2).next(), "a new game walks the path it always did");
    }
}
