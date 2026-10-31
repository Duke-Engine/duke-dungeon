package uz.dukeengine.dungeon.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The game's languages. Uzbek is what every data file says and stays the default: it is not a table, the data's own
 * words are its source. Every other language is a table in {@value #FOLDER}, one row a line,
 * {@code Key = "the words"  ; uz: the Uzbek they translate}, under a first row {@code Language = English} that names it.
 *
 * <p>A key is where a text lives, not what it says: {@code <BlockType>.<BlockName>.<Field>}, the block being the one
 * opened at the left margin (a {@code Conversation}, a {@code Quest}, the {@code Hud}) and its name the
 * {@code Name = } written two spaces in (a block without one is just its type: {@code MainMenu.Subtitle}). The second,
 * third... text under the same field of the same block (or the same list) is {@code .1}, {@code .2}...: the three line
 * texts of {@code Conversation BattleHolds} are {@code Conversation.BattleHolds.Text}, {@code .Text.1} and
 * {@code .Text.2}. A key two files would both derive is written, for each of them, with the file before it --
 * {@code story/act1:Quest.Training.Title} (the path under {@code data/}, or {@code maps/<map>/<file>} for a map
 * folder's blocks, without {@code .duke}) -- see {@link #places}. Words the code says itself have keys of their own:
 * {@code Map.<map>.Title}, {@code Map.<map>.Description} and {@link #CODE}.
 *
 * <p>A table is applied to a data file's text as it is read ({@link Content#read}, and a map folder's blocks in
 * {@link DungeonSettings}), before anything parses it, so no record, binder or screen knows there are languages. A key
 * the table lacks is said in Uzbek (a line a branch adds is Uzbek until somebody adds its row). Only places that hold
 * text are rewritten: every quoted string, and the unquoted value of a field whose name says it is text
 * ({@link #displayKey}) -- an id that happens to spell an Uzbek word is never touched. The rows themselves are written
 * and checked by {@code tools.LangTable} ({@code ./gradlew langReport}). See {@code docs/plan/2026-10-15-english.md}.
 */
public final class Lang {

    /** Where the tables are: {@code data/lang/<code>.duke}, a language a file. */
    public static final String FOLDER = "data/lang";

    /** The English table. */
    public static final String TABLE = FOLDER + "/en.duke";

    /** The player's choice in the engine's settings file: an index into {@link #NAMES}. */
    public static final String SETTING = "game.language";

    /** Uzbek's code: always the first language. */
    private static final String UZBEK = "uz";

    /** The languages by their codes ({@code uz}, then each table's file name), in the order of {@link #SETTING}. */
    public static final List<String> CODES = codes();

    /** The languages by their own names, in the order of {@link #SETTING}'s values. */
    public static final List<String> NAMES = names();

    /**
     * The words the game's code says itself, by key, in Uzbek: a table's rows for them are checked against these.
     * Kept in step with the call that says each ({@code Main}'s settings row).
     */
    public static final Map<String, String> CODE = Map.of("Settings.Language", "Til (qayta ochilganda)");

    private static final Pattern FIELD = Pattern.compile("(\\s*)([A-Za-z_]\\w*)(\\s*=\\s*)(.*)");
    private static final Pattern WORD = Pattern.compile("[A-Za-z_]\\w*");
    private static final Pattern ARG = Pattern.compile("--lang=(\\w+)");
    private static final Set<String> TEXT_KEYS = Set.of("DisplayName", "Role", "Title", "Subtitle", "Blurb", "Lore",
            "Description", "Subtext", "Label", "Slot", "Slots", "Prefix", "ShortName", "Text", "Caption", "Tip",
            "Word", "FirstNames", "SecondNames", "Name", "Treasure", "AboutSave", "RankSuffix", "MissionsTab", "WornIn");

    /** Fields holding a list of words that are text too (bare or quoted): the worn places' names, the rarities. */
    private static final Set<String> BARE_LISTS = Set.of("Slots", "WornSlots", "Rarities", "FirstNames", "SecondNames");

    /**
     * Fields whose strings are ids -- zones, maps, paths, sounds, lists of ids -- whatever they look like: never offered,
     * so an id that spells a word (a zone called "Yo'lak") is never changed.
     */
    static final Set<String> ID_KEYS = Set.of("Zone", "Open", "Where", "At", "Cells", "Map", "Model", "Icon", "Picture",
            "Look", "Sound", "Music", "Plays", "Flags", "Items", "Needs", "Gives", "Forage", "Recipes", "Gathers",
            "Hunts", "Clips", "Opens", "Ambience", "SceneLook", "Also", "Route", "Files", "Kind",
            "Modules", "Animations", "Cue", "Cues", "Sfx", "Footsteps", "Start", "Talk", "Says", "Starts", "Base", "Points");

    /** Files written in English to begin with (sounds, projectiles, rigs, a test's creatures): nothing is missing there. */
    private static final List<String> ENGLISH_ALREADY = List.of("data/sounds/", "data/projectiles/",
            "data/animations/", "data/fixture");

    private static volatile Map<String, String> spoken;

    private Lang() {
    }

    /** One place that holds text: asked of {@link #rewrite} by its key, answered with the replacement or null. */
    @FunctionalInterface
    interface Look {
        String of(String key, String field, String value, boolean unquoted, int indent);
    }

    /** A place before it has its key: what {@link #rewrite}'s helpers offer. */
    @FunctionalInterface
    private interface Spot {
        String of(String field, String value, boolean unquoted, int indent);
    }

    /**
     * One place a table can say: the file it is in (null for the code's own words and the maps' titles), its key, the
     * field it stands under, its Uzbek, and whether it is words a player reads (and so missing where untranslated).
     */
    public record Place(String file, String key, String field, String uzbek, boolean text) {
    }

    /** One row of a table: its key, its words, the Uzbek its {@code ; uz:} says they translate (or null), its line. */
    public record Row(String key, String said, String uzbek, int line) {
    }

    /** A language's table: its name in itself ({@code Language = }) and its rows by key, in the file's order. */
    public record Table(String language, Map<String, Row> rows) {
    }

    /** Speak language {@code choice}, an index into {@link #CODES}: 0 (or anything unknown) Uzbek. */
    public static void use(int choice) {
        spoken = choice > 0 && choice < CODES.size() ? said(table(CODES.get(choice))) : null;
    }

    /** The choice for this launch: {@code --lang=en} or {@code --lang=uz}, else what the player kept, else Uzbek. */
    public static int chosen(String[] args, int kept) {
        for (var arg : args) {
            var m = ARG.matcher(arg);
            if (m.matches()) {
                return Math.max(0, CODES.indexOf(m.group(1).toLowerCase(Locale.ROOT)));
            }
        }
        return kept > 0 && kept < CODES.size() ? kept : 0;
    }

    /** Words from outside the data files (a map's title, say) by key, in the language spoken; the Uzbek if untranslated. */
    public static String t(String key, String uzbek) {
        var table = spoken;
        var said = table == null || uzbek == null ? null : table.get(key);
        return said != null ? said : uzbek;
    }

    /** A data file's (or a map folder's {@code maps/<map>/<file>.duke}) text in the language spoken. */
    static String apply(String name, String text) {
        var table = spoken;
        if (table == null || !name.startsWith("data/") && !name.startsWith("maps/") || name.startsWith(FOLDER + "/")) {
            return text;
        }
        var where = where(name) + ":";
        return rewrite(text, (key, field, value, unquoted, indent) -> {
            if (!offered(field, unquoted, indent)) {
                return null;
            }
            var said = table.get(where + key);
            return said != null ? said : table.get(key);
        });
    }

    /** How a key two files share is told apart: {@code data/story/act1.duke} is {@code story/act1}. */
    static String where(String name) {
        var at = name.startsWith("data/") ? name.substring(5) : name;
        return at.endsWith(".duke") ? at.substring(0, at.length() - 5) : at;
    }

    /** Whether a place {@link #rewrite} offers can be said in another language at all: never an id. */
    static boolean offered(String field, boolean unquoted, int indent) {
        if (ID_KEYS.contains(field) || unquoted && !textKey(field)) {
            return false;
        }
        // A block's own Name (indent 2) is its id; a skill's Name, nested in a Skills list, is what it is called.
        return !(unquoted && field.equals("Name") && indent < 4);
    }

    /** Whether an offered place is words a player reads: letters, not a path, a bare Name only with a space or an '. */
    static boolean reads(String field, String value, boolean unquoted) {
        if (!value.matches("(?s).*\\p{L}.*")) {
            return false;
        }
        return unquoted
                ? !field.equals("Name") || value.contains(" ") || value.contains("'")
                : !field.equals("Cells") && !value.matches("[^\\s%]*/[^\\s%]*");
    }

    /**
     * Every place a table can say, with its final key, in the order a table lists them: each data file's (and the test
     * creatures'), each shipped map folder's blocks, each shipped map's title and description, then {@link #CODE}.
     *
     * <p>A key derived in two files is written with each file before it ({@link #where}); one the code says is never
     * changed. A key that is still said twice after that is refused, naming both places.
     */
    public static List<Place> places() {
        var found = new ArrayList<Place>();
        var files = new ArrayList<>(Content.files());
        files.add(Content.FIXTURE_CREATURES);
        for (var file : files) {
            collect(file, read(file), found);
        }
        var maps = uz.dukeengine.core.map.MapPackages.shipped("maps");
        for (var map : maps) {
            for (var extra : map.extras()) {
                collect("maps/" + map.name() + "/" + extra.getFileName(), read(extra), found);
            }
        }
        for (var map : maps) {
            var head = map.head();
            var title = unquote(head.getOrDefault("DisplayName", ""));
            var about = unquote(head.getOrDefault("Description", ""));
            if (!title.isBlank() && !title.equals(map.name())) {
                found.add(new Place(null, "Map." + map.name() + ".Title", "Title", title, reads("", title, false)));
            }
            if (!about.isBlank()) {
                found.add(new Place(null, "Map." + map.name() + ".Description", "Description", about,
                        reads("", about, false)));
            }
        }
        CODE.forEach((key, uzbek) -> found.add(new Place(null, key, key, uzbek, true)));
        // Which keys more than one file derives: those are written with their file.
        var fileOf = new HashMap<String, String>();
        var shared = new HashSet<String>();
        for (var place : found) {
            var first = fileOf.putIfAbsent(place.key(), String.valueOf(place.file()));
            if (first != null && !first.equals(String.valueOf(place.file()))) {
                shared.add(place.key());
            }
        }
        var keyed = new ArrayList<Place>(found.size());
        var seen = new HashMap<String, Place>();
        for (var place : found) {
            var key = place.file() != null && shared.contains(place.key()) ? where(place.file()) + ":" + place.key()
                    : place.key();
            var it = new Place(place.file(), key, place.field(), place.uzbek(), place.text());
            var before = seen.putIfAbsent(key, it);
            if (before != null) {
                throw new IllegalStateException("the key " + key + " is said twice: in " + before.file() + " ("
                        + before.uzbek() + ") and in " + it.file() + " (" + it.uzbek() + ")");
            }
            keyed.add(it);
        }
        return keyed;
    }

    /** Every place of one file {@link #offered} that a table can say, under the key it derives in that file. */
    private static void collect(String file, String text, List<Place> into) {
        boolean english = ENGLISH_ALREADY.stream().anyMatch(file::startsWith);
        rewrite(text, (key, field, value, unquoted, indent) -> {
            if (offered(field, unquoted, indent)) {
                into.add(new Place(file, key, field, value, !english && reads(field, value, unquoted)));
            }
            return null;
        });
    }

    /** A language's table by its code ({@code en}), as its file holds it. */
    public static Table table(String code) {
        var name = FOLDER + "/" + code + ".duke";
        return parse(read(name), name);
    }

    private static Map<String, String> said(Table table) {
        var said = new HashMap<String, String>();
        table.rows().forEach((key, row) -> said.put(key, row.said()));
        return said;
    }

    /** Whether an unquoted value (or list item) under this field name is text a player reads. */
    static boolean textKey(String key) {
        return displayKey(key) || BARE_LISTS.contains(key);
    }

    /** Whether an unquoted value under this field name is text a player reads. */
    static boolean displayKey(String key) {
        return TEXT_KEYS.contains(key) || key.endsWith("Word") || key.endsWith("Hint") || key.endsWith("Title")
                || key.endsWith("Blurb") || key.endsWith("Prompt");
    }

    /**
     * {@code text} with every place that holds text offered to {@code look} by its key (see the class's note); the
     * answer, where not null, replaces it (quoted). Lines, comments and everything else stay as they were.
     */
    static String rewrite(String text, Look look) {
        var out = new StringBuilder(text.length());
        String listKey = null;
        var lines = text.split("\n", -1);
        var blocks = blocks(lines);
        var counts = new HashMap<String, Integer>();
        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
            var block = blocks[i];
            Spot spot = (field, value, unquoted, indent) -> {
                var key = block + field;
                int n = counts.merge(key, 1, Integer::sum) - 1;
                return look.of(n == 0 ? key : key + "." + n, field, value, unquoted, indent);
            };
            int semi = commentAt(line);
            var code = semi < 0 ? line : line.substring(0, semi);
            var comment = semi < 0 ? "" : line.substring(semi);
            var field = FIELD.matcher(code);
            if (field.matches()) {
                var key = field.group(2);
                var value = field.group(4);
                var stripped = value.strip();
                int indent = field.group(1).length();
                if (BARE_LISTS.contains(key) && stripped.startsWith("[") && stripped.endsWith("]")) {
                    code = field.group(1) + key + field.group(3) + bareList(stripped, key, indent, spot)
                            + value.substring(value.stripTrailing().length());
                } else if (stripped.startsWith("\"") || stripped.startsWith("[")) {
                    code = field.group(1) + key + field.group(3) + quoted(value, key, indent, spot);
                    listKey = stripped.startsWith("[") && !closes(stripped) ? key : null;
                } else if (!stripped.isEmpty()) {
                    var said = spot.of(key, stripped, true, indent);
                    if (said != null) {
                        code = field.group(1) + key + field.group(3) + quote(said)
                                + value.substring(value.stripTrailing().length());
                    }
                }
            } else if (code.indexOf('"') >= 0) {
                code = quoted(code, listKey == null ? "" : listKey, 0, spot);
            }
            if (listKey != null && !field.matches() && closes(code)) {
                listKey = null;
            }
            out.append(code).append(comment);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Each line's block as its keys begin: {@code Conversation.BattleHolds.} -- the word a line at the left margin
     * opens a block with, and the {@code Name = } two spaces in before the next such line -- or {@code MainMenu.} for
     * a block without a name; empty before the first block.
     */
    private static String[] blocks(String[] lines) {
        var blocks = new String[lines.length];
        var current = "";
        for (int i = 0; i < lines.length; i++) {
            var opens = opens(lines[i]);
            if (opens != null) {
                current = opens + ".";
                for (int j = i + 1; j < lines.length && word(lines[j]) == null; j++) {
                    var named = FIELD.matcher(code(lines[j]));
                    if (named.matches() && named.group(1).length() == 2 && named.group(2).equals("Name")) {
                        current = opens + "." + unquote(named.group(4).strip()) + ".";
                        break;
                    }
                }
            }
            blocks[i] = current;
        }
        return blocks;
    }

    /** The word a line at the left margin opens a block with, or null. */
    private static String opens(String line) {
        var word = word(line);
        return word == null || word.equals("End") ? null : word;
    }

    /** A line at the left margin that is a single word (a block's opening, or its End), or null. */
    private static String word(String line) {
        var code = code(line).stripTrailing();
        return !code.isEmpty() && !Character.isWhitespace(code.charAt(0)) && WORD.matcher(code).matches() ? code : null;
    }

    private static String code(String line) {
        int semi = commentAt(line);
        return semi < 0 ? line : line.substring(0, semi);
    }

    /** {@code s} with each of its quoted strings offered to {@code spot} under {@code key}. */
    private static String quoted(String s, String key, int indent, Spot spot) {
        var out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '"') {
                out.append(c);
                i++;
                continue;
            }
            int end = i + 1;
            while (end < s.length() && s.charAt(end) != '"') {
                end += s.charAt(end) == '\\' ? 2 : 1;
            }
            if (end >= s.length()) {
                out.append(s, i, s.length());
                break;
            }
            var said = spot.of(key, unescape(s.substring(i + 1, end)), false, indent);
            out.append(said == null ? s.substring(i, end + 1) : quote(said));
            i = end + 1;
        }
        return out.toString();
    }

    /** A one-line list of bare or quoted words: each offered, the list put back with its items a comma apart. */
    private static String bareList(String list, String key, int indent, Spot spot) {
        var items = new ArrayList<String>();
        var inner = list.substring(1, list.length() - 1);
        int from = 0;
        boolean inQuote = false;
        for (int i = 0; i <= inner.length(); i++) {
            if (i < inner.length() && inner.charAt(i) == '"') {
                inQuote = !inQuote;
            }
            if (i == inner.length() || (inner.charAt(i) == ',' && !inQuote)) {
                var item = inner.substring(from, i).strip();
                from = i + 1;
                boolean wasQuoted = item.length() > 1 && item.startsWith("\"") && item.endsWith("\"");
                var word = wasQuoted ? unescape(item.substring(1, item.length() - 1)) : item;
                var said = item.isEmpty() ? null : spot.of(key, word, !wasQuoted, indent);
                items.add(said == null ? item : quote(said));
            }
        }
        return "[" + String.join(", ", items) + "]";
    }

    /** The first {@code ;} outside quotes, or -1. */
    private static int commentAt(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                i++;
                while (i < line.length() && line.charAt(i) != '"') {
                    i += line.charAt(i) == '\\' ? 2 : 1;
                }
            } else if (c == ';') {
                return i;
            }
        }
        return -1;
    }

    /** Whether a {@code ]} stands outside quotes. */
    private static boolean closes(String code) {
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '"') {
                i++;
                while (i < code.length() && code.charAt(i) != '"') {
                    i += code.charAt(i) == '\\' ? 2 : 1;
                }
            } else if (c == ']') {
                return true;
            }
        }
        return false;
    }

    /** {@code s} in quotes, its quotes and backslashes escaped: how a table and a data file write a string. */
    public static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String unescape(String s) {
        return s.indexOf('\\') < 0 ? s : s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** A map head's value without the quotes it may have on. */
    private static String unquote(String s) {
        return s.length() > 1 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    /** A resource's text, its line ends made {@code \n}, with no language applied. */
    private static String read(String name) {
        try (var stream = Lang.class.getResourceAsStream("/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("missing file: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + name, e);
        }
    }

    /** A map folder's file's text, the same way; a shipped map found through maps.list names a classpath path. */
    static String read(Path path) {
        if (!path.isAbsolute() && !Files.exists(path)) {
            return read(path.toString().replace('\\', '/'));
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
        }
    }

    /** Uzbek, then every table in {@value #FOLDER} by its file's name, in name order. */
    private static List<String> codes() {
        var codes = new ArrayList<String>();
        codes.add(UZBEK);
        try {
            var url = Lang.class.getResource("/" + FOLDER);
            if (url == null) {
                return List.copyOf(codes);
            }
            var uri = url.toURI();
            Path folder;
            if ("jar".equals(uri.getScheme())) {
                // The jar's file system is left open: the maps are read through it too (MapPackages).
                java.nio.file.FileSystem fs;
                try {
                    fs = FileSystems.newFileSystem(uri, Map.of());
                } catch (FileSystemAlreadyExistsException already) {
                    fs = FileSystems.getFileSystem(uri);
                }
                folder = fs.getPath("/" + FOLDER);
            } else {
                folder = Path.of(uri);
            }
            try (var inside = Files.list(folder)) {
                inside.map(path -> path.getFileName().toString()).filter(file -> file.endsWith(".duke")).sorted()
                        .forEach(file -> codes.add(file.substring(0, file.length() - 5)));
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("could not list the languages in " + FOLDER, e);
        }
        return List.copyOf(codes);
    }

    private static List<String> names() {
        var names = new ArrayList<String>();
        names.add("O'zbekcha");
        for (var code : CODES.subList(1, CODES.size())) {
            names.add(table(code).language());
        }
        return List.copyOf(names);
    }

    /**
     * A table's rows: {@code Language = Name} once, then {@code Key = "words"  ; uz: Uzbek} a line; {@code ;} lines and
     * blanks are comments (a key not yet translated is written as one: {@code ; Key = ""  ; uz: ...}).
     */
    public static Table parse(String text, String name) {
        var rows = new LinkedHashMap<String, Row>();
        String language = null;
        int number = 0;
        for (var line : text.split("\n", -1)) {
            number++;
            if (line.isBlank() || line.stripLeading().startsWith(";")) {
                continue;
            }
            if (language == null && line.startsWith("Language = ")) {
                language = line.substring(11).strip();
                continue;
            }
            int eq = line.indexOf(" = \"");
            int end = eq + 4;
            while (eq > 0 && end < line.length() && line.charAt(end) != '"') {
                end += line.charAt(end) == '\\' ? 2 : 1;
            }
            var rest = eq > 0 && end < line.length() ? line.substring(end + 1).stripLeading() : null;
            if (rest == null || !rest.isBlank() && !rest.startsWith("; uz:") || line.substring(0, eq).contains(" ")) {
                throw new IllegalStateException(name + ":" + number + " is not Key = \"words\"  ; uz: Uzbek: " + line);
            }
            var key = line.substring(0, eq);
            var uzbek = rest.isBlank() ? null : rest.substring(5).replaceFirst("^ ", "");
            if (rows.put(key, new Row(key, unescape(line.substring(eq + 4, end)), uzbek, number)) != null) {
                throw new IllegalStateException(name + ":" + number + " says " + key + " twice");
            }
        }
        if (language == null) {
            throw new IllegalStateException(name + " does not say its Language = ");
        }
        return new Table(language, rows);
    }
}
