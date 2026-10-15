package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.MenuSide;
import uz.dukeengine.client3d.Shell;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** The front menu in the game's words and at its side; the credits; and a second game after the first was quit. */
class MainMenuTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void theGameIsCalledLastHeroFromTheMenuData() {
        assertEquals("Last Hero", SETTINGS.mainMenu().title());
        assertEquals("Last Hero", Dungeon.newSession(1L, SETTINGS).game().getTitle(), "the window title and the menu heading");
    }

    @Test
    void theRowsAreTheGamesWordsAtTheLeft() {
        var shell = FrontMenu.shell(SETTINGS, List.of(), List.of());

        assertEquals(List.of(Shell.Entry.SETTINGS, Shell.Entry.QUIT),
                shell.entries().stream().map(java.util.Map.Entry::getKey).toList());
        assertEquals(List.of("Sozlamalar", "Chiqish"),
                shell.entries().stream().map(java.util.Map.Entry::getValue).toList());
        assertSame(MenuSide.LEFT, shell.menuSide(), "the owner: the rows at one side, the scene across the rest");
        assertTrue(shell.menuMargin() > 0f);
        assertEquals(SETTINGS.mainMenu().margin(), shell.menuMargin());
    }

    @Test
    void everyFixedWordOfTheClientsMenusIsSaidInUzbek() {
        var shell = FrontMenu.shell(SETTINGS, List.of(), List.of());
        for (var english : List.of("PAUSED", "Resume", "Settings", "Quit to the main menu", "Quit to the desktop",
                "ESC resume", "QUIT TO THE MAIN MENU?", "QUIT TO THE DESKTOP?", "everything on this floor is lost",
                "Keep playing", "Quit", "ESC keep playing", "SETTINGS", "Fullscreen", "NO", "YES", "Size",
                "MONITOR", "Anti-aliasing", "OFF", "Volume", "Effects", "Voice", "Music", "Track", "Save", "Cancel", "Back",
                "anti-aliasing from the next launch", "unsaved changes",
                "UP DOWN choose    LEFT RIGHT change    ESC back", "UP DOWN choose    ENTER take")) {
            assertTrue(SETTINGS.mainMenu().said().containsKey(english), "not said: " + english);
        }
        assertEquals("Bosh menyuga chiqish", shell.wordFor("Quit to the main menu"));
        assertEquals("O'yindan chiqish", shell.wordFor("Quit to the desktop"));
    }

    @Test
    void theCreditsAreCreditsMdsRowsEachWithItsMakerAndLicence() {
        var credits = FrontMenu.credits(SETTINGS, Content.read("CREDITS.md"));
        assertEquals("Mualliflar", credits.label());
        var rows = credits.next().options();
        assertTrue(rows.size() > 30, "only " + rows.size() + " rows");
        var kaykit = rows.stream().filter(row -> row.label().equals("Dungeon Pack")).findFirst().orElseThrow();
        assertTrue(kaykit.blurb().contains("KayKit") && kaykit.blurb().endsWith("CC0"), kaykit.blurb());
        assertFalse(kaykit.blurb().contains("**") || kaykit.blurb().contains("]("), kaykit.blurb());
        for (var row : rows) {
            assertNotNull(row.next(), row.label() + " would start the game");
        }
        var hut = rows.stream().filter(row -> row.label().equals("\"Baba Yaga's hut\"")).findFirst().orElseThrow();
        assertTrue(hut.blurb().startsWith("Inuciian") && hut.blurb().contains("CC BY 4.0"), hut.blurb());
        assertTrue(rows.stream().noneMatch(row -> row.label().startsWith("Industrial")),
                "the music's own table is not an asset row");
    }

    @Test
    void afterQuittingEveryAnswerThatStartsAlsoPlaysTheFreshMatch() {
        var taken = new ArrayList<String>();
        var hero = new Shell.Question("WHO", "", List.of(
                new Shell.Option("Rogue", "", () -> taken.add("rogue"))));
        var wayIn = new Shell.Question("IN", "", List.of(
                new Shell.Option("Endless", "", () -> taken.add("endless"), hero),
                new Shell.Option("Stage", "", null, new Shell.Question("WHERE", "", List.of(
                        new Shell.Option("hall", "", null, hero)))),
                new Shell.Option("Waiting", "", null, new Shell.Question("WAIT", "", List.of())),
                new Shell.Option("Continue", "", () -> taken.add("continue"))));

        var fresh = FrontMenu.startingFresh(wayIn.options(), () -> taken.add("startMatch"));

        var endless = fresh.getFirst();
        endless.taken().run();
        endless.next().options().getFirst().taken().run();
        assertEquals(List.of("endless", "rogue", "startMatch"), taken);
        assertSame(endless.next(), fresh.get(1).next().options().getFirst().next(),
                "the hero asked by both ways is still one question");
        assertTrue(fresh.get(2).next().options().isEmpty(), "a waiting room starts nothing");
        assertSame(wayIn.options().get(3), fresh.get(3), "Continue starts its own match");
    }

    @Test
    void aSecondGameIsAFreshMatchNotTheOneThatWasPlayed() {
        var played = Dungeon.newSession(3L, SETTINGS);
        played.game().runHeadless(1);
        played.game().stop();
        var started = new ArrayList<uz.dukeengine.game.DukeGame>();
        var next = Dungeon.newSession(4L, SETTINGS);
        var rows = FrontMenu.startingFresh(List.of(new Shell.Option("NEW", "", null, Main.whoToPlay(next, SETTINGS,
                Main.looks(SETTINGS), Main.controls(SETTINGS)))), () -> started.add(next.game()));

        rows.getFirst().next().options().getFirst().taken().run();

        assertEquals(List.of(next.game()), started);
        assertFalse(next.game().hasStarted(), "handed to the client to start, never one already played");
        next.game().runHeadless(30);
        var hero = SETTINGS.heroes().getFirst().name();
        assertTrue(next.game().getLogic().getObjects().stream()
                .anyMatch(thing -> thing.getTemplate().name().equals(hero)), "no " + hero + " in the new match");
    }
}
