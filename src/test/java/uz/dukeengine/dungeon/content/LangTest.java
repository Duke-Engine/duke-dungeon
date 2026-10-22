package uz.dukeengine.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.tools.LangTable;

/**
 * The languages (docs/plan/2026-10-15-english.md): texts are known by keys, the English table is well formed, fits the
 * font and matches the data -- no key the data lacks, no {@code ; uz:} that is not the data's Uzbek, the menus and the
 * HUD whole -- and English reads as the same data as Uzbek. What English still lacks is listed, not failed.
 */
class LangTest {

    /** The files whose every string must be translated: the menus and the HUD. The rest are warnings. */
    private static final Set<String> CORE = Set.of("data/world/menu.duke", "data/world/hud.duke",
            "data/world/screens.duke", "data/world/saves.duke", "data/world/party.duke", "data/world/controls.duke",
            "data/world/travel.duke", "data/world/world.duke");

    private static final Pattern PLACEHOLDER = Pattern.compile("%[-0-9.]*[sdf]|\\{\\w+}");

    @AfterEach
    void backToUzbek() {
        Lang.use(0);
    }

    private static List<String> files() {
        var files = new ArrayList<>(Content.files());
        files.add(Content.FIXTURE_CREATURES);
        return files;
    }

    /** A data file as it stands on disk, before any language. */
    private static String rawText(String file) {
        Lang.use(0);
        return Content.read(file);
    }

    /** A Person's Points are zones of its map: a quoted one ("Bog'iston") is an id, never words to translate. */
    @Test
    void aPersonsPointsAreIds() {
        assertFalse(Lang.offered("Points", false, 2), "a quoted zone in Points is not offered to a table");
    }

    @Test
    void aKeyIsWhereTheTextLives() {
        var text = """
                Conversation
                  Lines = [
                    Line
                      Text = "Birinchi"
                    End,
                    Line
                      Text = "Ikkinchi"
                    End
                  ]
                  Name = BattleHolds
                  Words = ["bir", "ikki"]
                End
                Hud
                  Title = Bosh
                  Slots = [Qurol, Zirh]
                End
                """;
        var keys = new ArrayList<String>();
        Lang.rewrite(text, (key, field, value, unquoted, indent) -> {
            keys.add(key + " " + value);
            return null;
        });
        assertEquals(List.of("Conversation.BattleHolds.Text Birinchi", "Conversation.BattleHolds.Text.1 Ikkinchi",
                "Conversation.BattleHolds.Name BattleHolds", "Conversation.BattleHolds.Words bir",
                "Conversation.BattleHolds.Words.1 ikki", "Hud.Title Bosh", "Hud.Slots Qurol", "Hud.Slots.1 Zirh"), keys);
        // And the real thing: the three line texts of the battle's holding conversation, a quest's title.
        var said = new LinkedHashMap<String, String>();
        Lang.places().forEach(place -> said.put(place.key(), place.uzbek()));
        assertTrue(said.containsKey("Conversation.BattleHolds.Text.2"), "keys by block, field and count");
        assertTrue(said.containsKey("Quest.Training.Title"), "a quest's title");
        assertEquals("Til (qayta ochilganda)", said.get("Settings.Language"));
    }

    @Test
    void keysTwoFilesShareAreToldApartByTheirFile() {
        assertEquals("story/act1", Lang.where("data/story/act1.duke"));
        assertEquals("maps/talk/talk", Lang.where("maps/talk/talk.duke"));
        var keys = new HashSet<String>();
        for (var place : Lang.places()) {
            assertTrue(keys.add(place.key()), "one place a key: " + place.key());
        }
    }

    @Test
    void theTableIsWellFormed() {
        var table = Lang.table("en");
        assertEquals("English", table.language());
        assertTrue(table.rows().size() > 500, "the English table has its rows: " + table.rows().size());
        for (var row : table.rows().values()) {
            assertFalse(row.said().isBlank() && row.uzbek() != null && !row.uzbek().isBlank(),
                    "empty English for: " + row.key());
            // The client's fixed words are pairs; their English is the half the client already says.
            if (row.uzbek() != null && !row.uzbek().contains(" = ")) {
                assertEquals(placeholders(row.uzbek()), placeholders(row.said()),
                        "the same %d / {name} slots in both languages: " + row.key());
            }
        }
    }

    private static List<String> placeholders(String s) {
        var found = new ArrayList<String>();
        var m = PLACEHOLDER.matcher(s);
        while (m.find()) {
            found.add(m.group());
        }
        found.sort(null);
        return found;
    }

    /** The menu font is a bitmap of ASCII and a few dashes: anything else would be drawn as a blank. */
    @Test
    void theEnglishFitsTheFont() throws IOException {
        var glyphs = new HashSet<Integer>();
        try (var in = LangTest.class.getResourceAsStream("/fonts/cinzel-22.fnt")) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                var m = Pattern.compile("^char id=(\\d+)").matcher(line);
                if (m.find()) {
                    glyphs.add(Integer.parseInt(m.group(1)));
                }
            }
        }
        var bad = new ArrayList<String>();
        for (var row : Lang.table("en").rows().values()) {
            row.said().codePoints().filter(c -> !glyphs.contains(c)).forEach(
                    c -> bad.add(row.key() + " = " + row.said() + "  has U+" + Integer.toHexString(c)));
        }
        assertTrue(bad.isEmpty(), "English text the font cannot draw: " + bad);
    }

    @Test
    void uzbekIsWhatTheFilesSay() {
        Lang.use(0);
        assertEquals("Salom", Lang.t("Settings.Language", "Salom"));
        for (var file : files()) {
            var raw = new String(readResource(file), StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
            assertEquals(raw, Content.read(file), file + " is read as it is in Uzbek");
        }
    }

    private static byte[] readResource(String name) {
        try (var in = LangTest.class.getResourceAsStream("/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** English changes words and nothing else: the same lines, the same fields, and the game loads on it. */
    @Test
    void englishIsTheSameDataInOtherWords() {
        var uzbek = new LinkedHashMap<String, String>();
        for (var file : files()) {
            uzbek.put(file, rawText(file));
        }
        Lang.use(1);
        int changed = 0;
        for (var file : files()) {
            var was = uzbek.get(file).split("\n", -1);
            var now = Content.read(file).split("\n", -1);
            assertEquals(was.length, now.length, file + " keeps its lines");
            for (int i = 0; i < was.length; i++) {
                if (!was[i].equals(now[i])) {
                    changed++;
                    var head = was[i].replaceAll("^(\\s*\\w+\\s*=\\s*).*$", "$1");
                    // A line of a list has no field; a line with one keeps it, and the English follows.
                    assertTrue(head.equals(was[i]) || now[i].startsWith(head.stripTrailing()),
                            file + ":" + (i + 1) + " keeps its field: " + now[i]);
                }
            }
        }
        assertTrue(changed > 500, "English rewrote the files: " + changed + " lines");
        // And everything reads on it: every block binds, every reference still finds its id.
        var settings = DungeonSettings.of(null);
        assertNotEquals(null, settings);
        assertEquals("Last Hero", settings.mainMenu().title());
        assertEquals("Settings", settings.mainMenu().settingsWord());
        // The client's fixed words each say their English: "Resume = Resume".
        assertEquals("Resume", settings.mainMenu().said().get("Resume"));
        assertEquals("Quit to the desktop", settings.mainMenu().said().get("Quit to the desktop"));
        // The words the code says, by their keys.
        assertEquals("Commander", Lang.t("Map.talk.Title", "Qo'mondon"));
        assertEquals("Language (after a restart)", Lang.t("Settings.Language", "Til (qayta ochilganda)"));
        assertEquals("Nimadir", Lang.t("Map.nowhere.Title", "Nimadir"), "a key the table lacks says its Uzbek");
    }

    /** A map folder's blocks are said in the language too, by the keys they derive there. */
    @Test
    void aMapFoldersBlocksAreTranslated() {
        Lang.use(1);
        var said = Lang.apply("maps/somewhere/people.duke", "Run\n  DiedWord = \"O'ldingiz\"\nEnd\n");
        assertEquals("Run\n  DiedWord = \"You died\"\nEnd\n", said);
    }

    @Test
    void theLaunchChoice() {
        assertEquals(List.of("uz", "en"), Lang.CODES);
        assertEquals(List.of("O'zbekcha", "English"), Lang.NAMES);
        assertEquals(1, Lang.chosen(new String[] {"--lang=en"}, 0));
        assertEquals(0, Lang.chosen(new String[] {"--lang=uz"}, 1));
        assertEquals(1, Lang.chosen(new String[0], 1));
        assertEquals(0, Lang.chosen(new String[0], 0));
        assertEquals(0, Lang.chosen(new String[0], 7));
    }

    /**
     * The table against the data: no row the data has no key for, no {@code ; uz:} other than the data's Uzbek at its
     * key (a stale row: its Uzbek was changed, its English was not looked at), the menus and the HUD whole. Every other
     * key English lacks is a warning, listed in build/lang-missing.txt and the log ({@code ./gradlew langReport} too).
     */
    @Test
    void theTableMatchesTheData() throws IOException {
        var report = LangTable.report(Lang.table("en"));
        var printed = report.print("en");
        var out = Path.of("build", "lang-missing.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, printed);
        System.out.println(printed.length() > 6000 ? printed.substring(0, 6000) + "..." : printed);
        assertTrue(report.unused().isEmpty(), "rows whose key the data does not have: " + report.unused());
        assertTrue(report.stale().isEmpty(), "rows whose Uzbek has changed since: " + report.stale());
        var core = new ArrayList<String>();
        report.missing().forEach((file, places) -> {
            if (file == null || CORE.contains(file)) {
                places.forEach(place -> core.add(place.key() + " (" + place.uzbek() + ")"));
            }
        });
        assertTrue(core.isEmpty(), "the menus, the HUD and the stage list are in English entirely, lacking: " + core);
    }
}
