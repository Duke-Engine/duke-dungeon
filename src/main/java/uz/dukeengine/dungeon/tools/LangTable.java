package uz.dukeengine.dungeon.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import uz.dukeengine.dungeon.content.Lang;
import uz.dukeengine.dungeon.content.Lang.Place;

/**
 * A language's table ({@code data/lang/<code>.duke}, see {@link Lang}) written from the data as it stands, and checked
 * against it. Run from the repository's root:
 *
 * <pre>
 * ./gradlew langReport                          # every table: what it lacks, what is stale, what is unused
 * ./gradlew langTable --args="en"               # en.duke again: every key in file order, its English kept by key
 * ./gradlew langTable --args="de --language=Deutsch"   # a new language: every key there, none translated yet
 * </pre>
 *
 * <p>Writing keeps each row's words and its {@code ; uz:} (so a stale row stays stale until somebody looks at it),
 * drops rows whose key the data no longer has, and lists every key a player reads that the table lacks as a comment,
 * {@code ; Key = ""  ; uz: ...}. Notes written into a table by hand are not kept.
 *
 * <p>{@code --old=<file>} took the English from the table of before keys ({@code "the Uzbek" => "the English"}, with
 * {@code "@field Uzbek"} and {@code "@data/file.duke Uzbek"} rows) the way the game read it then: the file's row, then
 * the field's, then the Uzbek's. That was the one-time move to keys (2026-10-21), kept for a branch that still has a
 * table of before.
 */
public final class LangTable {

    private static final Path FOLDER = Path.of("src", "main", "resources", Lang.FOLDER);

    private LangTable() {
    }

    /**
     * What a table lacks and has wrong, against the data: the keys a player reads that it has no row for, by file
     * (null for the maps' titles and the code's words); its rows whose {@code ; uz:} is not the Uzbek at their key; its
     * rows whose key the data does not have; and how many keys there are to say and how many it says.
     */
    public record Report(Map<String, List<Place>> missing, List<String> stale, List<String> unused, int keys,
            int translated) {

        public int missingCount() {
            return missing.values().stream().mapToInt(List::size).sum();
        }

        /** The report as {@code ./gradlew langReport} prints it. */
        public String print(String code) {
            var out = new StringBuilder();
            out.append(code).append(": ").append(keys).append(" keys, ").append(translated).append(" translated, ")
                    .append(missingCount()).append(" missing, ").append(stale.size()).append(" stale, ")
                    .append(unused.size()).append(" unused\n");
            missing.forEach((file, places) -> {
                out.append("  missing in ").append(file == null ? "the maps' titles and the code" : file).append(" (")
                        .append(places.size()).append(")\n");
                places.forEach(place -> out.append("    ").append(place.key()).append("  ; uz: ").append(place.uzbek())
                        .append('\n'));
            });
            stale.forEach(line -> out.append("  stale ").append(line).append('\n'));
            unused.forEach(key -> out.append("  unused ").append(key).append('\n'));
            return out.toString();
        }
    }

    /** {@code table} against the data as it stands. */
    public static Report report(Lang.Table table) {
        var places = new LinkedHashMap<String, Place>();
        for (var place : Lang.places()) {
            places.put(place.key(), place);
        }
        var missing = new LinkedHashMap<String, List<Place>>();
        int keys = 0;
        int translated = 0;
        for (var place : places.values()) {
            boolean has = table.rows().containsKey(place.key());
            if (has || place.text()) {
                keys++;
            }
            if (has) {
                translated++;
            } else if (place.text()) {
                missing.computeIfAbsent(place.file(), file -> new ArrayList<>()).add(place);
            }
        }
        var stale = new ArrayList<String>();
        var unused = new ArrayList<String>();
        for (var row : table.rows().values()) {
            var place = places.get(row.key());
            if (place == null) {
                unused.add(row.key());
            } else if (row.uzbek() == null || !row.uzbek().strip().equals(place.uzbek().strip())) {
                stale.add(row.key() + ": the table translates \"" + row.uzbek() + "\", the data says \"" + place.uzbek()
                        + "\"");
            }
        }
        return new Report(missing, stale, unused, keys, translated);
    }

    /**
     * A table's text: its head, {@code Language = <language>}, then every place's row under its file's heading, in the
     * data's order. {@code said} gives a place's words in the language (null where it has none: a comment line where
     * the place is words a player reads, nothing otherwise); {@code uzbek} what its {@code ; uz:} says.
     */
    public static String write(String language, List<Place> places, Function<Place, String> said,
            Function<Place, String> uzbek) {
        var out = new StringBuilder();
        out.append("""
                ; Last Hero in %s. A row a line: Key = "the words"  ; uz: the Uzbek they translate. The key is where the
                ; text lives in the data (see content/Lang.java and docs/plan/2026-10-15-english.md); Uzbek is the data's own
                ; words and the default, so a key this table lacks is said in Uzbek. A key not yet translated is a comment:
                ; take its ; off and write the words. Written by ./gradlew langTable (rows are kept by key; notes added here
                ; by hand are not), checked by LangTest and ./gradlew langReport. The menu font draws plain ASCII and the
                ; dashes, no more.
                Language = %s
                """.formatted(language, language));
        String file = "";
        var section = new StringBuilder();
        for (var place : places) {
            if (!String.valueOf(place.file()).equals(file)) {
                flush(out, file, section);
                file = String.valueOf(place.file());
            }
            var words = said.apply(place);
            if (words != null) {
                section.append(place.key()).append(" = ").append(Lang.quote(words)).append("  ; uz: ")
                        .append(uzbek.apply(place)).append('\n');
            } else if (place.text()) {
                section.append("; ").append(place.key()).append(" = \"\"  ; uz: ").append(uzbek.apply(place))
                        .append('\n');
            }
        }
        flush(out, file, section);
        return out.toString();
    }

    private static void flush(StringBuilder out, String file, StringBuilder section) {
        if (!section.isEmpty()) {
            out.append("\n; ---- ").append(file.equals("null") ? "the maps' titles and the code's own words" : file)
                    .append(" ----\n").append(section);
            section.setLength(0);
        }
    }

    /** The English of a place by the table of before keys, the way {@code Lang.apply} read it then; null if none. */
    public static String before(Map<String, String> old, Place place) {
        if (place.file() == null) {
            return old.get(place.uzbek());
        }
        var said = old.get("@" + place.file() + " " + place.uzbek());
        if (said == null) {
            said = old.get("@" + place.field() + " " + place.uzbek());
        }
        return said != null ? said : old.get(place.uzbek());
    }

    /** The rows of a table of before keys: {@code "uzbek" => "english"} a line. */
    public static Map<String, String> parseBefore(String text) {
        var rows = new LinkedHashMap<String, String>();
        int number = 0;
        for (var line : text.replace("\r\n", "\n").split("\n", -1)) {
            number++;
            if (line.isBlank() || line.stripLeading().startsWith(";")) {
                continue;
            }
            int arrow = line.indexOf("\" => \"");
            if (!line.startsWith("\"") || !line.endsWith("\"") || arrow < 0) {
                throw new IllegalStateException(number + " is not \"Uzbek\" => \"English\": " + line);
            }
            var uzbek = unescape(line.substring(1, arrow));
            if (rows.put(uzbek, unescape(line.substring(arrow + 6, line.length() - 1))) != null) {
                throw new IllegalStateException(number + " says \"" + uzbek + "\" twice");
            }
        }
        return rows;
    }

    private static String unescape(String s) {
        return s.indexOf('\\') < 0 ? s : s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("--report")) {
            for (var code : Lang.CODES.subList(1, Lang.CODES.size())) {
                System.out.print(report(Lang.table(code)).print(code));
            }
            return;
        }
        String code = null;
        String language = null;
        Path old = null;
        for (var arg : args) {
            if (arg.startsWith("--language=")) {
                language = arg.substring(11);
            } else if (arg.startsWith("--old=")) {
                old = Path.of(arg.substring(6));
            } else {
                code = arg;
            }
        }
        if (code == null) {
            throw new IllegalArgumentException("which language? langTable --args=\"en\"");
        }
        var file = FOLDER.resolve(code + ".duke");
        var places = Lang.places();
        String text;
        if (old != null) {
            var before = parseBefore(Files.readString(old, StandardCharsets.UTF_8));
            text = write(language == null ? "English" : language, places, place -> before(before, place),
                    Place::uzbek);
        } else {
            var kept = Files.exists(file)
                    ? Lang.parse(Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"), file.toString())
                    : new Lang.Table(language == null ? code : language, Map.of());
            text = write(language != null ? language : kept.language(), places, place -> {
                var row = kept.rows().get(place.key());
                return row == null ? null : row.said();
            }, place -> {
                var row = kept.rows().get(place.key());
                return row == null || row.uzbek() == null ? place.uzbek() : row.uzbek();
            });
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        System.out.println("wrote " + file);
    }
}
