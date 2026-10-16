package uz.dukeengine.dungeon.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The game's second language. Uzbek is what every data file says and stays the default; English is an overlay:
 * the table {@value #TABLE}, each line {@code "the Uzbek exactly as a data file has it" => "the English"}.
 *
 * <p>The overlay is applied to a data file's text as it is read ({@link Content#read}), before anything parses it, so
 * no record, binder or screen knows there are two languages. A word the table lacks is said in Uzbek (a line a
 * branch adds is Uzbek until somebody adds its row). Only places that hold text are rewritten: every quoted string,
 * and the unquoted value of a field whose name says it is text ({@link #displayKey}) -- so an id that happens to
 * spell an Uzbek word is never touched. See {@code docs/plan/2026-10-15-english.md}.
 */
public final class Lang {

    /** The English of every Uzbek string. */
    public static final String TABLE = "data/lang/en.duke";

    /** The player's choice in the engine's settings file: an index into {@link #NAMES}. */
    public static final String SETTING = "game.language";

    /** The two languages by their own names, in the order of {@link #SETTING}'s values. */
    public static final List<String> NAMES = List.of("O'zbekcha", "English");

    private static final Pattern FIELD = Pattern.compile("(\\s*)([A-Za-z_]\\w*)(\\s*=\\s*)(.*)");
    private static final Pattern ARG = Pattern.compile("--lang=(\\w+)");
    private static final Set<String> TEXT_KEYS = Set.of("DisplayName", "Role", "Title", "Subtitle", "Blurb", "Lore",
            "Description", "Subtext", "Label", "Slot", "Slots", "Prefix", "ShortName", "Text", "Caption", "Tip",
            "Word", "FirstNames", "SecondNames", "Name", "Treasure", "AboutSave", "RankSuffix", "MissionsTab", "WornIn");

    /** Fields holding a list of words that are text too (bare or quoted): the worn places' names, the rarities. */
    private static final Set<String> BARE_LISTS = Set.of("Slots", "WornSlots", "Rarities", "FirstNames", "SecondNames");

    /**
     * Fields whose strings are ids -- zones, maps, paths, sounds, lists of ids -- whatever they look like: never offered,
     * so an id that spells a word the table has (a zone called "Yo'lak") is never changed.
     */
    static final Set<String> ID_KEYS = Set.of("Zone", "Open", "Where", "At", "Cells", "Map", "Model", "Icon", "Picture",
            "Look", "Sound", "Music", "Plays", "Flags", "Items", "Needs", "Gives", "Forage", "Recipes", "Gathers",
            "Hunts", "Clips", "Opens", "Ambience", "SceneLook", "Also", "Route", "Files", "Kind",
            "Modules", "Animations", "Cue", "Cues", "Sfx", "Footsteps", "Start", "Talk", "Says", "Starts", "Base");

    private static volatile Map<String, String> english;
    private static volatile Map<String, String> loaded;

    private Lang() {
    }

    /** One place that holds text: asked of {@link #rewrite}, answered with the replacement or null. */
    @FunctionalInterface
    interface Look {
        String of(String key, String value, boolean unquoted, int indent);
    }

    /** Speak language {@code choice}: 0 Uzbek, 1 English. */
    public static void use(int choice) {
        english = choice == 1 ? table() : null;
    }

    /** Whether the game speaks English. */
    public static boolean isEnglish() {
        return english != null;
    }

    /** The choice for this launch: {@code --lang=en} or {@code --lang=uz}, else what the player kept, else Uzbek. */
    public static int chosen(String[] args, int kept) {
        for (var arg : args) {
            var m = ARG.matcher(arg);
            if (m.matches()) {
                return m.group(1).equalsIgnoreCase("en") ? 1 : 0;
            }
        }
        return kept == 1 ? 1 : 0;
    }

    /** A string from outside the data files (a map's title, say) in the language spoken; itself where untranslated. */
    public static String t(String uzbek) {
        var table = english;
        if (table == null || uzbek == null) {
            return uzbek;
        }
        // A map's head hands its quoted values over with the quotes on.
        boolean quoted = uzbek.length() > 1 && uzbek.startsWith("\"") && uzbek.endsWith("\"");
        var said = table.get(quoted ? uzbek.substring(1, uzbek.length() - 1) : uzbek);
        return said != null ? said : uzbek;
    }

    /** A data file's text in the language spoken. */
    static String apply(String name, String text) {
        var table = english;
        if (table == null || !name.startsWith("data/") || name.equals(TABLE)) {
            return text;
        }
        return rewrite(text, (key, value, unquoted, indent) -> {
            if (ID_KEYS.contains(key) || unquoted && !textKey(key)) {
                return null;
            }
            // A block's own Name (indent 2) is its id; a skill's Name, nested in a Skills list, is what it is called.
            if (unquoted && key.equals("Name") && indent < 4) {
                return null;
            }
            // A row may be for one field alone ("@BoostWord Kuch" says Kuch there and nowhere else), or one file's
            // ("@data/props/kampir.duke Qo'riqchi"), where the same Uzbek word means two things.
            var said = table.get("@" + name + " " + value);
            if (said == null) {
                said = table.get("@" + key + " " + value);
            }
            return said != null ? said : table.get(value);
        });
    }

    /** Every Uzbek string the English table has a row for, row by row, as the table holds them. */
    public static Map<String, String> table() {
        var table = loaded;
        if (table == null) {
            table = parse(read());
            loaded = table;
        }
        return table;
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
     * {@code text} with every place that holds text offered to {@code look}; the answer, where not null, replaces it
     * (quoted). Lines, comments and everything else stay as they were.
     */
    static String rewrite(String text, Look look) {
        var out = new StringBuilder(text.length());
        String listKey = null;
        var lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
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
                    code = field.group(1) + key + field.group(3) + bareList(stripped, key, indent, look)
                            + value.substring(value.stripTrailing().length());
                } else if (stripped.startsWith("\"") || stripped.startsWith("[")) {
                    code = field.group(1) + key + field.group(3) + quoted(value, key, indent, look);
                    listKey = stripped.startsWith("[") && !closes(stripped) ? key : null;
                } else if (!stripped.isEmpty()) {
                    var said = look.of(key, stripped, true, indent);
                    if (said != null) {
                        code = field.group(1) + key + field.group(3) + quote(said)
                                + value.substring(value.stripTrailing().length());
                    }
                }
            } else if (code.indexOf('"') >= 0) {
                code = quoted(code, listKey == null ? "" : listKey, 0, look);
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

    /** {@code s} with each of its quoted strings offered to {@code look} under {@code key}. */
    private static String quoted(String s, String key, int indent, Look look) {
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
            var said = look.of(key, unescape(s.substring(i + 1, end)), false, indent);
            out.append(said == null ? s.substring(i, end + 1) : quote(said));
            i = end + 1;
        }
        return out.toString();
    }

    /** A one-line list of bare or quoted words: each offered, the list put back with its items a comma apart. */
    private static String bareList(String list, String key, int indent, Look look) {
        var items = new java.util.ArrayList<String>();
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
                var said = item.isEmpty() ? null : look.of(key, word, !wasQuoted, indent);
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

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String unescape(String s) {
        return s.indexOf('\\') < 0 ? s : s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String read() {
        try (var stream = Lang.class.getResourceAsStream("/" + TABLE)) {
            if (stream == null) {
                throw new IllegalStateException("missing language table: " + TABLE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + TABLE, e);
        }
    }

    /** The table's rows: {@code "uzbek" => "english"} per line; {@code ;} lines and blanks are comments. */
    static Map<String, String> parse(String text) {
        var rows = new LinkedHashMap<String, String>();
        int number = 0;
        for (var line : text.split("\n", -1)) {
            number++;
            if (line.isBlank() || line.stripLeading().startsWith(";")) {
                continue;
            }
            int arrow = line.indexOf("\" => \"");
            if (!line.startsWith("\"") || !line.endsWith("\"") || arrow < 0) {
                throw new IllegalStateException(TABLE + ":" + number + " is not \"Uzbek\" => \"English\": " + line);
            }
            var uzbek = unescape(line.substring(1, arrow));
            var said = unescape(line.substring(arrow + 6, line.length() - 1));
            if (rows.put(uzbek, said) != null) {
                throw new IllegalStateException(TABLE + ":" + number + " says \"" + uzbek + "\" twice");
            }
        }
        return rows;
    }
}
