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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The second language (docs/plan/2026-10-15-english.md): the table is well formed and fits the font, English reads
 * as the same data as Uzbek, and every player-facing string is listed that the table does not yet translate.
 */
class LangTest {

    /** The files whose every string must be translated: the menus and the HUD. The rest are warnings. */
    private static final Set<String> CORE = Set.of("data/world/menu.duke", "data/world/hud.duke",
            "data/world/screens.duke", "data/world/saves.duke", "data/world/party.duke", "data/world/controls.duke",
            "data/world/travel.duke", "data/world/world.duke");

    /** Fields whose strings are ids, paths, coordinates or sound names, whatever they look like. */
    private static final Set<String> NOT_TEXT = Set.of("Zone", "Cells", "Model", "Icon", "Picture", "Map", "Look",
            "Sound", "Music", "Plays", "Flags", "Items", "Needs", "Gives", "Forage", "Recipes", "Gathers", "Hunts",
            "Clips", "Opens", "Ambience", "ModernSkillKeys", "SceneLook", "Also", "Route", "Files", "Kind",
            "Modules", "Animations", "Cue", "Cues", "Sfx", "Footsteps");

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

    /** How many player-facing strings the passes of {@link #missing} have looked at. */
    private static int seen;

    /** Every player-facing string of a file that is not in the table, the way Lang.apply would meet it. */
    private static List<String> missing(String file, java.util.Map<String, String> table) {
        // These were written in English to begin with (sounds, projectiles, rigs): nothing to translate.
        if (file.startsWith("data/sounds/") || file.startsWith("data/projectiles/") || file.startsWith("data/animations/")
                || file.startsWith("data/fixture")) {
            return List.of();
        }
        var lost = new ArrayList<String>();
        Lang.rewrite(rawText(file), (key, value, unquoted, indent) -> {
            // A bare Name is an id unless it has spaces or an apostrophe in it; every quoted string is offered.
            boolean text = unquoted
                    ? key.equals("Name") ? value.contains(" ") || value.contains("'") : Lang.textKey(key)
                    : !key.equals("Cells");
            if (!text || NOT_TEXT.contains(key) || !value.matches(".*\\p{L}.*")
                    || !unquoted && value.matches("[^\\s%]*/[^\\s%]*")) {
                return null;
            }
            seen++;
            if (!table.containsKey(value) && !table.containsKey("@" + key + " " + value)
                    && !table.containsKey("@" + file + " " + value)) {
                lost.add(key + " = " + value);
            }
            return null;
        });
        return lost;
    }

    /** A data file as it stands on disk, before any language. */
    private static String rawText(String file) {
        Lang.use(0);
        return Content.read(file);
    }

    @Test
    void theTableIsWellFormed() {
        var table = Lang.table();
        assertTrue(table.size() > 500, "the English table has its rows: " + table.size());
        for (var row : table.entrySet()) {
            assertFalse(row.getValue().isBlank() && !row.getKey().isBlank(), "empty English for: " + row.getKey());
            var key = row.getKey().startsWith("@") ? row.getKey().substring(row.getKey().indexOf(' ') + 1)
                    : row.getKey();
            // The client's fixed words are pairs; their English is the half the client already says.
            if (!key.contains(" = ")) {
                assertEquals(placeholders(key), placeholders(row.getValue()),
                        "the same %d / {name} slots in both languages: " + row.getKey());
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
        for (var row : Lang.table().entrySet()) {
            row.getValue().codePoints().filter(c -> !glyphs.contains(c)).forEach(
                    c -> bad.add(row.getValue() + "  has U+" + Integer.toHexString(c)));
        }
        assertTrue(bad.isEmpty(), "English text the font cannot draw: " + bad);
    }

    @Test
    void uzbekIsWhatTheFilesSay() {
        Lang.use(0);
        assertFalse(Lang.isEnglish());
        assertEquals("Salom", Lang.t("Salom"));
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
        var uzbek = new java.util.LinkedHashMap<String, String>();
        for (var file : files()) {
            uzbek.put(file, rawText(file));
        }
        Lang.use(1);
        assertTrue(Lang.isEnglish());
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
        assertEquals("Commander", Lang.t("Qo'mondon"));
    }

    @Test
    void theLaunchChoice() {
        assertEquals(1, Lang.chosen(new String[] {"--lang=en"}, 0));
        assertEquals(0, Lang.chosen(new String[] {"--lang=uz"}, 1));
        assertEquals(1, Lang.chosen(new String[0], 1));
        assertEquals(0, Lang.chosen(new String[0], 0));
    }

    /**
     * Every player-facing string the table lacks, as a warning list in build/lang-missing.txt and the log: a line a
     * branch adds is Uzbek in English until a row is added. Only the menus and the HUD must be complete.
     */
    @Test
    void whatEnglishLacks() throws IOException {
        var table = Lang.table();
        var report = new StringBuilder();
        var core = new ArrayList<String>();
        int lacking = 0;
        seen = 0;
        var every = new LinkedHashSet<String>();
        for (var file : files()) {
            for (var line : missing(file, table)) {
                every.add(line);
                lacking++;
                report.append(file).append('\t').append(line).append('\n');
                if (CORE.contains(file)) {
                    core.add(file + ": " + line);
                }
            }
        }
        // The stage list is a menu too: each shipped map's title and description.
        Lang.use(0);
        for (var stage : uz.dukeengine.dungeon.stage.Stages.all()) {
            for (var raw : new String[] {stage.title(), stage.description()}) {
                var text = raw.length() > 1 && raw.startsWith("\"") && raw.endsWith("\"") ? raw.substring(1, raw.length() - 1) : raw;
                if (!text.isBlank() && !table.containsKey(text) && !stage.name().equals(text)) {
                    core.add("map " + stage.name() + ": " + text);
                    report.append("maps/").append(stage.name()).append('\t').append(text).append('\n');
                    lacking++;
                }
            }
        }
        var out = Path.of("build", "lang-missing.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println("English covers " + (seen - lacking) + " of " + seen + " player-facing strings in the data files ("
                + (100 * (seen - lacking) / Math.max(1, seen)) + "%); lacking " + lacking + " (" + every.size()
                + " distinct), listed in " + out);
        if (lacking > 0) {
            System.out.println(report.length() > 6000 ? report.substring(0, 6000) + "..." : report);
        }
        assertTrue(core.isEmpty(), "the menus and the HUD are in English entirely, lacking: " + core);
    }
}
