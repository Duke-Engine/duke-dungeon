import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

/**
 * The pictures a bag draws things with, in one look: a rounded square framed in the colour of the thing's rarity, a
 * soft glow behind, and the thing itself -- a game-icons.net drawing filled with its material's gradient, outlined
 * dark and lifted off the stone by a shadow, or a painted picture set in the same frame with the same shadow.
 *
 * <p>What is drawn is listed in {@code art/icons/items.csv}: a row a picture, with its group (wired: the game's own
 * things, at the bag's 64 pixels into {@code icons/items/}; library: the starter set, at 128 into
 * {@code icons/items/library/} with an index; skills, commands and stats: the panel's pictures, at 128 into
 * {@code icons/skills/} and {@code icons/commands/} and at 64 into {@code icons/stats/}; effect: a particle's picture,
 * the drawing alone with no frame, at 64 into {@code effects/}), its rarity, where it comes from and the gradient it
 * is filled with. Every picture is painted at 128 and halved for 64.
 *
 * <p>Its sources live beside it, so it runs from the repository alone: the outline of every game-icons drawing used in
 * {@code art/icons/items/glyphs.txt} (one {@code author/name<TAB>path} a line, the one path each of their SVGs is),
 * and the painted pictures cut and keyed in {@code art/icons/items/painted/}. Both are written by {@code --import}
 * from the packs as downloaded, which is only needed when a row names something not yet there.
 *
 * <pre>
 * java art/icons/ItemIcons.java                                   # every picture the manifest lists
 * java art/icons/ItemIcons.java --sheets DIR                      # and a sheet of each group
 * java art/icons/ItemIcons.java --only loot                      # one group alone: the loot frames
 * java art/icons/ItemIcons.java --import DOWNLOADS                # gi/, raven/128/, flare_armor.png, osare_relvad.png
 * </pre>
 *
 * Run from the repository's root.
 */
public final class ItemIcons {

    static final int BIG = 128;
    static final Path MANIFEST = Path.of("art/icons/items.csv");
    static final Path GLYPHS = Path.of("art/icons/items/glyphs.txt");
    static final Path PAINTED = Path.of("art/icons/items/painted");
    static final Path RESOURCES = Path.of("src/main/resources");
    static final Path WIRED = RESOURCES.resolve("icons/items");
    static final Path LIBRARY = WIRED.resolve("library");

    /** The frame's colour by rarity: the names the manifest uses. */
    static final Map<String, Color> RARITY = Map.of(
            "common", new Color(0x9AA0A8),
            "magic", new Color(0x3F86FF),
            "rare", new Color(0xE8B23A),
            "epic", new Color(0xA55CF0));

    /** A material's gradient, light at the top to dark at the foot. */
    static final Map<String, Color[]> TINTS = new LinkedHashMap<>();

    static {
        tint("steel", 0xF4F8FF, 0xB4C4DC, 0x56657E);
        tint("darksteel", 0xC4CAD6, 0x6E7788, 0x272D3A);
        tint("iron", 0xD8DBE0, 0x8E949C, 0x43484F);
        tint("silver", 0xFFFFFF, 0xCCD2DA, 0x79808C);
        tint("gold", 0xFFF4B0, 0xF0BE3C, 0x94600E);
        tint("bronze", 0xFFE0A8, 0xCF8F4E, 0x744016);
        tint("copper", 0xFFD2B4, 0xD8784A, 0x753216);
        tint("leather", 0xEAB984, 0xA66E3A, 0x553116);
        tint("wood", 0xE2B27A, 0x9E6E3A, 0x55371A);
        tint("cloth", 0xF2EADA, 0xC0B49C, 0x6A604C);
        tint("parchment", 0xFFF6DC, 0xE6CE96, 0x9C7C4C);
        tint("bone", 0xFFFBEA, 0xDED4B6, 0x8A8166);
        tint("stone", 0xE4E2DC, 0xA6A298, 0x57534B);
        tint("red", 0xFFB4AC, 0xE23C34, 0x760E10);
        tint("blue", 0xBCDAFF, 0x3C80EA, 0x103478);
        tint("green", 0xCCF6B4, 0x4EC03C, 0x186616);
        tint("purple", 0xEACCFF, 0x9C52E2, 0x481A7E);
        tint("yellow", 0xFFF8B4, 0xF2D232, 0x967606);
        tint("orange", 0xFFDAA4, 0xF28C2A, 0x863806);
        tint("cyan", 0xD4FCFF, 0x52D2EA, 0x0E6E88);
        tint("white", 0xFFFFFF, 0xE6EEF6, 0x96A2B0);
        tint("bread", 0xFFE6B2, 0xD8A052, 0x865216);
        tint("meat", 0xFFC4A4, 0xC85C42, 0x661E12);
        tint("fire", 0xFFF2A4, 0xFF8C22, 0xAC1E06);
        tint("frost", 0xF2FFFF, 0x8CDCFF, 0x2868AE);
        tint("arcane", 0xF2D4FF, 0xB262FF, 0x38168C);
        tint("holy", 0xFFFCE2, 0xFFE272, 0xAC861E);
        tint("poison", 0xE2FFB4, 0x8AD232, 0x38680E);
        tint("nature", 0xDAF6B4, 0x6CB242, 0x285816);
        tint("blood", 0xFF9C92, 0xC21A1A, 0x560404);
    }

    /** The material a category is filled with when its row names none. */
    static final Map<String, String> CATEGORY_TINT = Map.ofEntries(
            Map.entry("weapon", "steel"), Map.entry("shield", "steel"), Map.entry("armour", "steel"),
            Map.entry("jewellery", "gold"), Map.entry("coin", "gold"), Map.entry("potion", "red"),
            Map.entry("scroll", "parchment"), Map.entry("book", "leather"), Map.entry("key", "bronze"),
            Map.entry("gem", "blue"), Map.entry("ore", "stone"), Map.entry("material", "leather"),
            Map.entry("tool", "iron"), Map.entry("food", "bread"), Map.entry("quest", "gold"),
            Map.entry("container", "wood"), Map.entry("skill", "arcane"), Map.entry("stat", "gold"),
            Map.entry("command", "silver"));

    static void tint(String name, int light, int mid, int dark) {
        TINTS.put(name, new Color[] {new Color(light), new Color(mid), new Color(dark)});
    }

    record Row(String group, String name, String category, String rarity, String source, String tint) {

        String kind() {
            return source.substring(0, source.indexOf(':'));
        }

        String ref() {
            return source.substring(source.indexOf(':') + 1);
        }

        Color[] colours() {
            var named = tint.isEmpty() ? CATEGORY_TINT.get(category) : tint;
            var colours = TINTS.get(named);
            if (colours == null) {
                throw new IllegalArgumentException(name + ": no gradient called " + named);
            }
            return colours;
        }
    }

    public static void main(String[] args) throws IOException {
        Path sheets = null;
        Path downloads = null;
        String only = null;
        for (int i = 0; i < args.length; i += 2) {
            switch (args[i]) {
                case "--sheets" -> sheets = Path.of(args[i + 1]);
                case "--import" -> downloads = Path.of(args[i + 1]);
                case "--only" -> only = args[i + 1];
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        var rows = rows();
        if (downloads != null) {
            importSources(rows, downloads);
            return;
        }
        var glyphs = glyphs();
        var made = new LinkedHashMap<Row, BufferedImage>();
        Files.createDirectories(LIBRARY);
        for (var row : rows) {
            if (only != null && !row.group().equals(only)) {
                continue;
            }
            if (row.group().equals("loot")) {
                // A generated thing's picture in each rarity's frame, at the bag's 64: icons/items/loot/<name>_<rarity>.
                Files.createDirectories(WIRED.resolve("loot"));
                for (var rarity : List.of("common", "magic", "rare", "epic")) {
                    var framed = new Row(row.group(), row.name(), row.category(), rarity, row.source(), row.tint());
                    var big = render(framed, glyphs);
                    made.put(framed, big);
                    write(half(big), WIRED.resolve("loot/" + row.name() + "_" + rarity + ".png"));
                }
                continue;
            }
            var big = row.group().equals("effect") ? glyph(parse(glyphOf(row, glyphs)), row.colours())
                    : render(row, glyphs);
            made.put(row, big);
            switch (row.group()) {
                case "wired" -> write(half(big), WIRED.resolve(row.name() + ".png"));
                case "library" -> write(big, LIBRARY.resolve(row.name() + ".png"));
                // The panel's: a skill and an order at 128, a figure at 64, in their own folders.
                case "skills", "commands" -> write(big, RESOURCES.resolve("icons/" + row.group() + "/" + row.name()
                        + ".png"));
                case "stats" -> write(half(big), RESOURCES.resolve("icons/stats/" + row.name() + ".png"));
                // A particle's picture: the drawing alone, no frame, at 64.
                case "effect" -> write(half(big), RESOURCES.resolve("effects/" + row.name() + ".png"));
                default -> throw new IllegalArgumentException(row.name() + ": no group " + row.group());
            }
        }
        if (only == null) {
            writeIndex(rows);
        }
        if (sheets != null) {
            Files.createDirectories(sheets);
            for (var group : rows.stream().map(Row::group).distinct().toList()) {
                var some = new LinkedHashMap<Row, BufferedImage>();
                made.forEach((row, image) -> {
                    if (row.group().equals(group)) {
                        some.put(row, image);
                    }
                });
                ImageIO.write(sheet(group, some), "png", sheets.resolve(group + ".png").toFile());
            }
        }
        var counts = new TreeMap<String, Long>();
        rows.forEach(row -> counts.merge(row.group(), 1L, Long::sum));
        System.out.println(rows.size() + " pictures: " + counts);
    }

    static String glyphOf(Row row, Map<String, String> glyphs) throws IOException {
        var d = glyphs.get(row.ref());
        if (d == null) {
            throw new IOException(row.ref() + " is not in " + GLYPHS + ": run --import first");
        }
        return d;
    }

    static List<Row> rows() throws IOException {
        var rows = new ArrayList<Row>();
        var lines = Files.readAllLines(MANIFEST);
        for (var line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            var cells = (line + ",").split(",", -1);
            rows.add(new Row(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5]));
        }
        return rows;
    }

    // ------------------------------------------------------------------ sources

    static Map<String, String> glyphs() throws IOException {
        var glyphs = new TreeMap<String, String>();
        if (Files.exists(GLYPHS)) {
            for (var line : Files.readAllLines(GLYPHS)) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    glyphs.put(line.substring(0, tab), line.substring(tab + 1));
                }
            }
        }
        return glyphs;
    }

    private static final Pattern PATH_D = Pattern.compile("<path[^>]*\\sd=\"([^\"]+)\"");

    /** Copies what the rows name out of the packs as downloaded: drawings into glyphs.txt, paintings cut and keyed. */
    static void importSources(List<Row> rows, Path downloads) throws IOException {
        var glyphs = glyphs();
        var gameIcons = downloads.resolve("gi/icons/000000/transparent/1x1");
        Files.createDirectories(PAINTED);
        BufferedImage flare = null;
        List<Rectangle2D> osareTiles = null;
        BufferedImage osare = null;
        for (var row : rows) {
            switch (row.kind()) {
                case "gi" -> {
                    var svg = Files.readString(gameIcons.resolve(row.ref() + ".svg"));
                    var paths = PATH_D.matcher(svg);
                    if (!paths.find()) {
                        throw new IOException(row.ref() + ": no path");
                    }
                    var d = paths.group(1);
                    if (paths.find() || svg.contains("transform") || svg.contains("evenodd")) {
                        throw new IOException(row.ref() + ": more than the one plain path this reads");
                    }
                    glyphs.put(row.ref(), d);
                }
                case "raven" -> Files.copy(downloads.resolve("raven/128/" + row.ref() + ".png"),
                        PAINTED.resolve("raven_" + row.ref() + ".png"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                case "flare" -> {
                    if (flare == null) {
                        flare = ImageIO.read(downloads.resolve("flare_armor.png").toFile());
                    }
                    int[] at = cell(row.ref());
                    var piece = flare.getSubimage(at[1] * 64, at[0] * 64, 64, 64);
                    ImageIO.write(keyed(piece), "png", PAINTED.resolve(painted(row)).toFile());
                }
                case "osare" -> {
                    if (osare == null) {
                        osare = ImageIO.read(downloads.resolve("osare_relvad.png").toFile());
                        osareTiles = tiles(osare);
                    }
                    int[] at = cell(row.ref());
                    var tile = osareTiles.stream().filter(t -> rowOf(t) == at[0]).toList().get(at[1]);
                    var piece = osare.getSubimage((int) tile.getX(), (int) tile.getY(), (int) tile.getWidth(),
                            (int) tile.getHeight());
                    ImageIO.write(keyed(piece), "png", PAINTED.resolve(painted(row)).toFile());
                }
                case "res" -> {
                    // already in the repository
                }
                default -> throw new IOException(row.name() + ": no source kind " + row.kind());
            }
        }
        var out = new StringBuilder();
        glyphs.forEach((name, d) -> out.append(name).append('\t').append(d).append('\n'));
        Files.createDirectories(GLYPHS.getParent());
        Files.writeString(GLYPHS, out);
        System.out.println(glyphs.size() + " drawings in " + GLYPHS);
    }

    static int[] cell(String ref) {
        var parts = ref.split("\\.");
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    static String painted(Row row) {
        return switch (row.kind()) {
            case "raven" -> "raven_" + row.ref() + ".png";
            default -> row.kind() + "_" + row.ref().replace('.', '_') + ".png";
        };
    }

    /** OSARE's sheet: dark squares on grey, by rows. A row is a band 72 pixels high. */
    static int rowOf(Rectangle2D tile) {
        return (int) (tile.getY() / 72);
    }

    static List<Rectangle2D> tiles(BufferedImage sheet) {
        int bg = sheet.getRGB(0, 0);
        int w = sheet.getWidth();
        int h = sheet.getHeight();
        var seen = new boolean[w * h];
        var tiles = new ArrayList<Rectangle2D>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (seen[y * w + x] || near(sheet.getRGB(x, y), bg, 12)) {
                    continue;
                }
                int x0 = x, y0 = y, x1 = x, y1 = y;
                var todo = new ArrayDeque<int[]>();
                todo.add(new int[] {x, y});
                seen[y * w + x] = true;
                while (!todo.isEmpty()) {
                    var p = todo.poll();
                    x0 = Math.min(x0, p[0]);
                    y0 = Math.min(y0, p[1]);
                    x1 = Math.max(x1, p[0]);
                    y1 = Math.max(y1, p[1]);
                    for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = p[0] + step[0];
                        int ny = p[1] + step[1];
                        if (nx >= 0 && ny >= 0 && nx < w && ny < h && !seen[ny * w + nx]
                                && !near(sheet.getRGB(nx, ny), bg, 12)) {
                            seen[ny * w + nx] = true;
                            todo.add(new int[] {nx, ny});
                        }
                    }
                }
                if (x1 - x0 >= 48 && y1 - y0 >= 48 && x1 - x0 <= 90) {
                    tiles.add(new Rectangle2D.Double(x0, y0, x1 - x0 + 1, y1 - y0 + 1));
                }
            }
        }
        tiles.sort((a, b) -> rowOf(a) != rowOf(b) ? Integer.compare(rowOf(a), rowOf(b))
                : Double.compare(a.getX(), b.getX()));
        return tiles;
    }

    static boolean near(int a, int b, int by) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs((a >> shift & 255) - (b >> shift & 255)) > by) {
                return false;
            }
        }
        return true;
    }

    /**
     * A painting on black, its black taken away: what is black and joined to the edge goes clear, and the pixels
     * bordering it fade with how dark they are, so the edge stays soft and nothing dark inside the painting is holed.
     */
    static BufferedImage keyed(BufferedImage piece) {
        int w = piece.getWidth();
        int h = piece.getHeight();
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var clear = new boolean[w * h];
        var todo = new ArrayDeque<int[]>();
        for (int x = 0; x < w; x++) {
            todo.add(new int[] {x, 0});
            todo.add(new int[] {x, h - 1});
        }
        for (int y = 0; y < h; y++) {
            todo.add(new int[] {0, y});
            todo.add(new int[] {w - 1, y});
        }
        while (!todo.isEmpty()) {
            var p = todo.poll();
            int i = p[1] * w + p[0];
            if (clear[i] || brightness(piece.getRGB(p[0], p[1])) > 26) {
                continue;
            }
            clear[i] = true;
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = p[0] + step[0];
                int ny = p[1] + step[1];
                if (nx >= 0 && ny >= 0 && nx < w && ny < h) {
                    todo.add(new int[] {nx, ny});
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = piece.getRGB(x, y);
                if (clear[y * w + x]) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                boolean edge = false;
                for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + step[0];
                    int ny = y + step[1];
                    edge |= nx >= 0 && ny >= 0 && nx < w && ny < h && clear[ny * w + nx];
                }
                int sourceAlpha = rgb >>> 24;
                int alpha = edge ? Math.min(255, brightness(rgb) * 255 / 70) : 255;
                alpha = alpha * sourceAlpha / 255;
                if (alpha == 0) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                float lift = edge ? 255f / Math.max(alpha, 1) : 1f;
                int r = Math.min(255, Math.round((rgb >> 16 & 255) * Math.min(lift, 2f)));
                int g = Math.min(255, Math.round((rgb >> 8 & 255) * Math.min(lift, 2f)));
                int b = Math.min(255, Math.round((rgb & 255) * Math.min(lift, 2f)));
                out.setRGB(x, y, alpha << 24 | r << 16 | g << 8 | b);
            }
        }
        return out;
    }

    static int brightness(int rgb) {
        return Math.max(rgb >> 16 & 255, Math.max(rgb >> 8 & 255, rgb & 255));
    }

    // ------------------------------------------------------------------ drawing

    static BufferedImage render(Row row, Map<String, String> glyphs) throws IOException {
        var frameColour = RARITY.get(row.rarity());
        if (frameColour == null) {
            throw new IllegalArgumentException(row.name() + ": no rarity " + row.rarity());
        }
        var image = new BufferedImage(BIG, BIG, BufferedImage.TYPE_INT_ARGB);
        var g = pen(image);
        var frame = new RoundRectangle2D.Double(3, 3, BIG - 6, BIG - 6, 26, 26);

        // The ground: a glow of the rarity's colour in the middle, dark at the rim.
        var middle = mix(frameColour, new Color(0x22242A), 0.70f);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(BIG / 2.0, BIG * 0.44), BIG * 0.62f,
                new float[] {0f, 0.55f, 1f}, new Color[] {middle, mix(middle, new Color(0x0C0D10), 0.55f),
                        new Color(0x0A0B0E)}));
        g.fill(frame);

        g.setClip(frame);
        BufferedImage thing;
        if (row.kind().equals("gi")) {
            thing = glyph(parse(glyphOf(row, glyphs)), row.colours());
        } else {
            var source = row.kind().equals("res") ? RESOURCES.resolve(row.ref()) : PAINTED.resolve(painted(row));
            var read = ImageIO.read(source.toFile());
            if (read == null) {
                throw new IOException(row.name() + ": cannot read " + source);
            }
            thing = paintedThing(read);
        }
        var shadow = blurred(silhouette(thing, new Color(0, 0, 0)), 3);
        g.setComposite(AlphaComposite.SrcOver.derive(0.8f));
        g.drawImage(shadow, 2, 4, null);
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(thing, 0, 0, null);
        g.setClip(null);

        // The frame: the rarity's colour, lit from above, with a dark line inside and a gloss along the top.
        g.setStroke(new BasicStroke(4f));
        g.setPaint(new LinearGradientPaint(0, 3, 0, BIG - 3, new float[] {0f, 1f},
                new Color[] {mix(frameColour, Color.WHITE, 0.65f), mix(frameColour, Color.BLACK, 0.55f)}));
        g.draw(frame);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(0, 0, 0, 170));
        g.draw(new RoundRectangle2D.Double(6, 6, BIG - 12, BIG - 12, 20, 20));
        g.setClip(new RoundRectangle2D.Double(6, 6, BIG - 12, BIG - 12, 20, 20));
        g.setPaint(new LinearGradientPaint(0, 6, 0, BIG * 0.42f, new float[] {0f, 1f},
                new Color[] {new Color(255, 255, 255, 34), new Color(255, 255, 255, 0)}));
        g.fill(new Ellipse2D.Double(-BIG * 0.2, -BIG * 0.55, BIG * 1.4, BIG * 0.95));
        g.dispose();
        return image;
    }

    /** A game-icons drawing on clear: shaded with its gradient, outlined dark, lit along its top. */
    static BufferedImage glyph(Path2D.Double outline, Color[] colours) {
        var bounds = outline.getBounds2D();
        double box = 82;
        double scale = box / Math.max(bounds.getWidth(), bounds.getHeight());
        var place = new AffineTransform();
        place.translate(BIG / 2.0, BIG / 2.0 + 1);
        place.scale(scale, scale);
        place.translate(-bounds.getCenterX(), -bounds.getCenterY());
        Shape shape = place.createTransformedShape(outline);
        var at = shape.getBounds2D();

        var image = new BufferedImage(BIG, BIG, BufferedImage.TYPE_INT_ARGB);
        var g = pen(image);
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(mix(colours[2], new Color(0x080604), 0.75f));
        g.draw(shape);
        g.setPaint(new LinearGradientPaint((float) at.getMinX(), (float) at.getMinY(), (float) at.getMaxX() * 0.3f
                + (float) at.getMinX() * 0.7f, (float) at.getMaxY(), new float[] {0f, 0.45f, 1f}, colours));
        g.fill(shape);
        g.setClip(shape);
        g.setPaint(new LinearGradientPaint(0, (float) at.getMinY(), 0, (float) (at.getMinY() + at.getHeight() * 0.5),
                new float[] {0f, 1f}, new Color[] {new Color(255, 255, 255, 120), new Color(255, 255, 255, 0)}));
        g.fill(at);
        g.dispose();
        return image;
    }

    /** A painting on clear, trimmed to what is painted, fitted to the frame, with a thin dark rim round it. */
    static BufferedImage paintedThing(BufferedImage source) {
        int x0 = source.getWidth(), y0 = source.getHeight(), x1 = -1, y1 = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) > 16) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;
        double scale = 90.0 / Math.max(w, h);
        int dw = (int) Math.round(w * scale);
        int dh = (int) Math.round(h * scale);
        var fitted = new BufferedImage(BIG, BIG, BufferedImage.TYPE_INT_ARGB);
        var g = pen(fitted);
        int dx = (BIG - dw) / 2;
        int dy = (BIG - dh) / 2 + 1;
        g.drawImage(source, dx, dy, dx + dw, dy + dh, x0, y0, x1 + 1, y1 + 1, null);
        g.dispose();

        var out = new BufferedImage(BIG, BIG, BufferedImage.TYPE_INT_ARGB);
        g = pen(out);
        var rim = silhouette(fitted, new Color(0x0A0806));
        g.setComposite(AlphaComposite.SrcOver.derive(0.85f));
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            g.drawImage(rim, (int) Math.round(Math.cos(a) * 1.6), (int) Math.round(Math.sin(a) * 1.6), null);
        }
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(fitted, 0, 0, null);
        g.dispose();
        return out;
    }

    static BufferedImage silhouette(BufferedImage image, Color colour) {
        var out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int rgb = colour.getRGB() & 0xFFFFFF;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                out.setRGB(x, y, (image.getRGB(x, y) & 0xFF000000) | rgb);
            }
        }
        return out;
    }

    /** Three box blurs of the alpha, which is near enough a gaussian for a shadow. */
    static BufferedImage blurred(BufferedImage image, int radius) {
        int w = image.getWidth();
        int h = image.getHeight();
        var alpha = new float[w * h];
        for (int i = 0; i < alpha.length; i++) {
            alpha[i] = image.getRGB(i % w, i / w) >>> 24;
        }
        for (int pass = 0; pass < 3; pass++) {
            alpha = box(alpha, w, h, radius, true);
            alpha = box(alpha, w, h, radius, false);
        }
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int rgb = image.getRGB(w / 2, h / 2) & 0xFFFFFF;
        for (int i = 0; i < alpha.length; i++) {
            out.setRGB(i % w, i / w, Math.min(255, Math.round(alpha[i])) << 24 | rgb);
        }
        return out;
    }

    static float[] box(float[] in, int w, int h, int r, boolean across) {
        var out = new float[in.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0;
                for (int k = -r; k <= r; k++) {
                    int sx = across ? Math.clamp(x + k, 0, w - 1) : x;
                    int sy = across ? y : Math.clamp(y + k, 0, h - 1);
                    sum += in[sy * w + sx];
                }
                out[y * w + x] = sum / (2 * r + 1);
            }
        }
        return out;
    }

    /** Half the size, each pixel the average of four, weighed by how much of each is there. */
    static BufferedImage half(BufferedImage big) {
        int w = big.getWidth() / 2;
        int h = big.getHeight() / 2;
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float a = 0, r = 0, g = 0, b = 0;
                for (int k = 0; k < 4; k++) {
                    int p = big.getRGB(x * 2 + k % 2, y * 2 + k / 2);
                    float pa = (p >>> 24) / 255f;
                    a += pa;
                    r += (p >> 16 & 255) * pa;
                    g += (p >> 8 & 255) * pa;
                    b += (p & 255) * pa;
                }
                if (a == 0) {
                    continue;
                }
                out.setRGB(x, y, Math.round(a / 4 * 255) << 24 | Math.round(r / a) << 16 | Math.round(g / a) << 8
                        | Math.round(b / a));
            }
        }
        return out;
    }

    static Graphics2D pen(BufferedImage image) {
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static Color mix(Color a, Color b, float towardB) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * towardB),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * towardB),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * towardB));
    }

    /** As small as the PNG writer makes it: the strongest deflate. */
    static void write(BufferedImage image, Path to) throws IOException {
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        var param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0f);
        Files.deleteIfExists(to);
        try (var out = ImageIO.createImageOutputStream(to.toFile())) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    // ------------------------------------------------------------------ the index and the sheets

    static void writeIndex(List<Row> rows) throws IOException {
        var out = new StringBuilder("name,category,rarity,file,source,author,licence\n");
        for (var row : rows) {
            if (row.group().equals("library")) {
                out.append(String.join(",", row.name(), row.category(), row.rarity(), row.name() + ".png",
                        sourceOf(row), authorOf(row), licenceOf(row))).append('\n');
            }
        }
        Files.writeString(LIBRARY.resolve("index.csv"), out);
    }

    static String sourceOf(Row row) {
        return switch (row.kind()) {
            case "gi" -> "game-icons.net " + row.ref();
            case "raven" -> "Ravenmore Fantasy Icon Pack 2.0 " + row.ref();
            case "flare" -> "Armor Icons by Equipment Slot " + row.ref();
            case "osare" -> "OSARE weapon icons " + row.ref();
            default -> row.ref();
        };
    }

    static final Map<String, String> AUTHOR_NAMES = Map.of("darkzaitzev", "DarkZaitzev", "lucasms", "Lucas",
            "heavenly-dog", "HeavenlyDog", "generalace135", "GeneralAce135", "spencerdub", "SpencerDub");

    static String authorOf(Row row) {
        return switch (row.kind()) {
            case "gi" -> {
                var folder = row.ref().substring(0, row.ref().indexOf('/'));
                var named = AUTHOR_NAMES.get(folder);
                if (named != null) {
                    yield named;
                }
                var words = new ArrayList<String>();
                for (var word : folder.split("-")) {
                    words.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
                }
                yield String.join(" ", words);
            }
            case "raven" -> "Ravenmore";
            case "flare" -> "Clint Bellanger; Blarumyrran; crowline; Justin Nichol";
            case "osare" -> "Blarumyrran";
            default -> "the owner's asset folder";
        };
    }

    static String licenceOf(Row row) {
        return switch (row.kind()) {
            case "gi" -> row.ref().startsWith("zeromancer/") || row.ref().startsWith("viscious-speed/") ? "CC0"
                    : "CC BY 3.0";
            case "raven" -> "CC BY 3.0";
            case "flare", "osare" -> "CC0";
            default -> "unconfirmed";
        };
    }

    static BufferedImage sheet(String group, Map<Row, BufferedImage> icons) {
        int columns = 10;
        int cell = 140;
        int label = 18;
        int rows = Math.max(1, (icons.size() + columns - 1) / columns);
        var sheet = new BufferedImage(columns * cell + 20, rows * (cell + label) + 50, BufferedImage.TYPE_INT_RGB);
        var g = pen(sheet);
        g.setColor(new Color(0x1B1C20));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setColor(new Color(0xE8DCC0));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(group + " - " + icons.size() + " pictures", 12, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        int i = 0;
        for (var entry : icons.entrySet()) {
            int x = 10 + i % columns * cell;
            int y = 44 + i / columns * (cell + label);
            g.drawImage(entry.getValue(), x + 6, y + 4, null);
            g.setColor(new Color(0xC8C0B0));
            var name = entry.getKey().name();
            int w = g.getFontMetrics().stringWidth(name);
            g.drawString(name, x + (cell - w) / 2, y + cell + 8);
            i++;
        }
        g.dispose();
        return sheet;
    }

    // ------------------------------------------------------------------ SVG paths

    /** One SVG path's {@code d}: every command but none of the styling, which game-icons' drawings do not have. */
    static Path2D.Double parse(String d) {
        var path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        var in = new Cursor(d);
        char command = 0;
        double x = 0, y = 0, startX = 0, startY = 0, controlX = 0, controlY = 0;
        char last = 0;
        while (in.more()) {
            if (in.atCommand()) {
                command = in.next();
            } else if (command == 0) {
                throw new IllegalArgumentException("path starts without a command: " + d);
            }
            boolean rel = Character.isLowerCase(command);
            double ox = rel ? x : 0;
            double oy = rel ? y : 0;
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    x = ox + in.number();
                    y = oy + in.number();
                    path.moveTo(x, y);
                    startX = x;
                    startY = y;
                    command = rel ? 'l' : 'L';
                    last = 'M';
                    continue;
                }
                case 'L' -> {
                    x = ox + in.number();
                    y = oy + in.number();
                    path.lineTo(x, y);
                }
                case 'H' -> {
                    x = ox + in.number();
                    path.lineTo(x, y);
                }
                case 'V' -> {
                    y = oy + in.number();
                    path.lineTo(x, y);
                }
                case 'C' -> {
                    double x1 = ox + in.number(), y1 = oy + in.number();
                    controlX = ox + in.number();
                    controlY = oy + in.number();
                    x = ox + in.number();
                    y = oy + in.number();
                    path.curveTo(x1, y1, controlX, controlY, x, y);
                }
                case 'S' -> {
                    double x1 = "CS".indexOf(last) >= 0 ? 2 * x - controlX : x;
                    double y1 = "CS".indexOf(last) >= 0 ? 2 * y - controlY : y;
                    controlX = ox + in.number();
                    controlY = oy + in.number();
                    x = ox + in.number();
                    y = oy + in.number();
                    path.curveTo(x1, y1, controlX, controlY, x, y);
                }
                case 'Q' -> {
                    controlX = ox + in.number();
                    controlY = oy + in.number();
                    x = ox + in.number();
                    y = oy + in.number();
                    path.quadTo(controlX, controlY, x, y);
                }
                case 'T' -> {
                    controlX = "QT".indexOf(last) >= 0 ? 2 * x - controlX : x;
                    controlY = "QT".indexOf(last) >= 0 ? 2 * y - controlY : y;
                    x = ox + in.number();
                    y = oy + in.number();
                    path.quadTo(controlX, controlY, x, y);
                }
                case 'A' -> {
                    double rx = in.number(), ry = in.number(), angle = in.number();
                    boolean large = in.flag(), sweep = in.flag();
                    double tx = ox + in.number(), ty = oy + in.number();
                    arc(path, x, y, rx, ry, angle, large, sweep, tx, ty);
                    x = tx;
                    y = ty;
                }
                case 'Z' -> {
                    path.closePath();
                    x = startX;
                    y = startY;
                    command = 0;
                }
                default -> throw new IllegalArgumentException("no command " + command);
            }
            last = Character.toUpperCase(command == 0 ? 'Z' : command);
        }
        return path;
    }

    /** An elliptical arc as cubic curves, by the SVG specification's endpoint-to-centre conversion. */
    static void arc(Path2D.Double path, double x1, double y1, double rx, double ry, double degrees, boolean large,
            boolean sweep, double x2, double y2) {
        if (rx == 0 || ry == 0) {
            path.lineTo(x2, y2);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(degrees);
        double cos = Math.cos(phi), sin = Math.sin(phi);
        double dx = (x1 - x2) / 2, dy = (y1 - y2) / 2;
        double x1p = cos * dx + sin * dy;
        double y1p = -sin * dx + cos * dy;
        double lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry);
        if (lambda > 1) {
            rx *= Math.sqrt(lambda);
            ry *= Math.sqrt(lambda);
        }
        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double coef = (large == sweep ? -1 : 1) * Math.sqrt(Math.max(0, num / den));
        double cxp = coef * rx * y1p / ry;
        double cyp = -coef * ry * x1p / rx;
        double cx = cos * cxp - sin * cyp + (x1 + x2) / 2;
        double cy = sin * cxp + cos * cyp + (y1 + y2) / 2;
        double start = Math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
        double end = Math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx);
        double delta = end - start;
        if (sweep && delta < 0) {
            delta += 2 * Math.PI;
        } else if (!sweep && delta > 0) {
            delta -= 2 * Math.PI;
        }
        int pieces = (int) Math.ceil(Math.abs(delta) / (Math.PI / 2) - 1e-9);
        double step = delta / Math.max(1, pieces);
        double k = 4.0 / 3 * Math.tan(step / 4);
        double a = start;
        for (int i = 0; i < pieces; i++) {
            double b = a + step;
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            double[] p1 = {ca - k * sa, sa + k * ca};
            double[] p2 = {cb + k * sb, sb - k * cb};
            double[] p3 = {cb, sb};
            double[][] points = {p1, p2, p3};
            double[] out = new double[6];
            for (int j = 0; j < 3; j++) {
                double ex = points[j][0] * rx;
                double ey = points[j][1] * ry;
                out[j * 2] = cos * ex - sin * ey + cx;
                out[j * 2 + 1] = sin * ex + cos * ey + cy;
            }
            path.curveTo(out[0], out[1], out[2], out[3], out[4], out[5]);
            a = b;
        }
    }

    static final class Cursor {
        private final String text;
        private int at;

        Cursor(String text) {
            this.text = text;
        }

        void skip() {
            while (at < text.length() && (Character.isWhitespace(text.charAt(at)) || text.charAt(at) == ',')) {
                at++;
            }
        }

        boolean more() {
            skip();
            return at < text.length();
        }

        boolean atCommand() {
            skip();
            char c = text.charAt(at);
            return Character.isLetter(c) && c != 'e' && c != 'E';
        }

        char next() {
            skip();
            return text.charAt(at++);
        }

        boolean flag() {
            skip();
            char c = text.charAt(at++);
            if (c != '0' && c != '1') {
                throw new IllegalArgumentException("not an arc flag at " + (at - 1) + " in " + text);
            }
            return c == '1';
        }

        double number() {
            skip();
            int from = at;
            if (at < text.length() && (text.charAt(at) == '-' || text.charAt(at) == '+')) {
                at++;
            }
            boolean dot = false;
            while (at < text.length()) {
                char c = text.charAt(at);
                if (Character.isDigit(c)) {
                    at++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    at++;
                } else if ((c == 'e' || c == 'E') && at + 1 < text.length()) {
                    at++;
                    if (text.charAt(at) == '-' || text.charAt(at) == '+') {
                        at++;
                    }
                } else {
                    break;
                }
            }
            if (from == at) {
                throw new IllegalArgumentException("no number at " + at + " in " + text);
            }
            return Double.parseDouble(text.substring(from, at));
        }
    }
}
