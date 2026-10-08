package uz.dukeengine.dungeon.stage;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import uz.dukeengine.core.map.MapPackages;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement;

/**
 * The picture that sits beside a map: the floor from above, a square a cell, with what stands on it marked.
 *
 * <p>A map's folder holds one — {@code preview.png} — and it is what the screen a map is chosen on shows of it.
 * Generals keeps a {@code .tga} beside every map and Warcraft III bakes one into the archive, both for the same
 * reason: a player choosing between maps is choosing between places, and a place is a shape before it is a name.
 *
 * <p>Written by the generator for the map it draws, and for maps already drawn by:
 *
 * <pre>{@code ./gradlew :dungeon:writeMapPreviews}</pre>
 *
 * <p>which reads each map and draws its picture without touching the map itself. The Map tab of the IDE writes
 * the same picture for the map being edited — a map drawn by hand has one without any of this being run.
 */
public final class MapPicture {

    /** How wide or tall the picture is at its longer side; a cell is whole pixels, so it may come out under it. */
    private static final int SIDE = 512;

    private static final Color BACKGROUND = new Color(18, 18, 21);
    private static final Color ROCK = new Color(28, 28, 32);
    private static final Color FLOOR = new Color(72, 72, 82);
    private static final Color STAIR = new Color(150, 130, 80);
    private static final Color ENTRANCE = new Color(90, 170, 255);
    private static final Color BOSS = new Color(255, 90, 90);

    private MapPicture() {
    }

    /** The floor of {@code stage} from above. */
    public static BufferedImage of(Stage stage) {
        var image = of(stage.floor());
        return stage.paint() == null ? image : painted(image, stage.paint());
    }

    /**
     * The picture of a painted stage: each cell in the colour of the surface it is painted with, as bright as the
     * relief drawn under it. The marks of what stands on it are kept.
     */
    private static BufferedImage painted(BufferedImage image, StagePaint paint) {
        var colours = new java.util.HashMap<Character, Color>();
        for (var entry : paint.palette().entrySet()) {
            colours.put(entry.getKey().charAt(0), surfaceColour(entry.getValue()));
        }
        int cell = image.getWidth() / paint.rows().getFirst().length();
        float lit = groundOf('0', 0).getRed();
        for (int y = 0; y < paint.rows().size(); y++) {
            var row = paint.rows().get(y);
            for (int x = 0; x < row.length(); x++) {
                var colour = colours.get(row.charAt(x));
                for (int py = y * cell; py < (y + 1) * cell; py++) {
                    for (int px = x * cell; px < (x + 1) * cell; px++) {
                        var under = new Color(image.getRGB(px, py));
                        if (under.getRed() != under.getGreen()) {
                            continue; // a mark of something standing there
                        }
                        float shade = Math.min(1.4f, 0.7f * under.getRed() / lit);
                        image.setRGB(px, py, new Color(Math.min(255, (int) (colour.getRed() * shade)),
                                Math.min(255, (int) (colour.getGreen() * shade)),
                                Math.min(255, (int) (colour.getBlue() * shade))).getRGB());
                    }
                }
            }
        }
        return image;
    }

    /** What a palette value looks like from far off: its colour, or the average of its picture. */
    private static Color surfaceColour(String value) {
        if (value.startsWith("#")) {
            return new Color(Integer.parseInt(value.substring(1), 16));
        }
        try (var in = MapPicture.class.getClassLoader().getResourceAsStream(value)) {
            var picture = in == null ? null : ImageIO.read(in);
            if (picture == null) {
                return FLOOR;
            }
            long r = 0;
            long g = 0;
            long b = 0;
            for (int y = 0; y < picture.getHeight(); y++) {
                for (int x = 0; x < picture.getWidth(); x++) {
                    int rgb = picture.getRGB(x, y);
                    r += rgb >> 16 & 0xFF;
                    g += rgb >> 8 & 0xFF;
                    b += rgb & 0xFF;
                }
            }
            long count = (long) picture.getWidth() * picture.getHeight();
            return new Color((int) (r / count), (int) (g / count), (int) (b / count));
        } catch (IOException e) {
            return FLOOR;
        }
    }

    /**
     * Any floor from above — a floor of the descent too. One that mixes biomes has each region washed with its
     * biome's colour, floor and rock alike, so the landscape reads before anything stands in it.
     */
    public static BufferedImage of(uz.dukeengine.dungeon.gen.GeneratedDungeon floor) {
        var rows = floor.levelMap().strip().lines().toList();
        int across = rows.stream().mapToInt(String::length).max().orElse(1);
        int down = Math.max(rows.size(), 1);
        int cell = Math.max(1, SIDE / Math.max(across, down));
        var image = new BufferedImage(across * cell, down * cell, BufferedImage.TYPE_INT_RGB);
        var g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BACKGROUND);
            g2.fillRect(0, 0, image.getWidth(), image.getHeight());
            var relief = floor.relief();
            int highest = relief == null ? 0 : highestOf(relief);
            var biomes = floor.biomes();
            for (int y = 0; y < rows.size(); y++) {
                var row = rows.get(y);
                for (int x = 0; x < across; x++) {
                    int rise = relief == null || highest == 0 ? 0 : heightOf(relief, x, y) * HILL_LIFT / highest;
                    char ground = x < row.length() ? row.charAt(x) : '#';
                    var colour = groundOf(ground, rise);
                    if (biomes != null) {
                        colour = washed(colour, biomeColour(biomes, biomes.at(x, y)), ground == '#' ? 0.18f : 0.45f);
                    }
                    g2.setColor(colour);
                    g2.fillRect(x * cell, y * cell, cell, cell);
                }
            }
            for (var prop : floor.props()) {
                mark(g2, cell, prop.at(), colourOf(prop.kind()), 0.7f);
            }
            for (var monster : floor.monsters()) {
                mark(g2, cell, monster.at(), colourOf(monster.kind()), 0.8f);
            }
            if (floor.boss() != null) {
                mark(g2, cell, floor.boss().at(), BOSS, 1.4f);
            }
            mark(g2, cell, floor.hero(), ENTRANCE, 1.4f);
        } finally {
            g2.dispose();
        }
        return image;
    }

    /** The picture written into {@code folder} as {@code preview.png}, replacing the one there. */
    public static Path write(Stage stage, Path folder) {
        var path = folder.resolve("preview.png");
        try {
            Files.createDirectories(folder);
            ImageIO.write(of(stage), "png", path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + path, e);
        }
        return path;
    }

    /** Every map the game keeps, given its picture, from the map as it stands rather than from its seed. */
    public static void main(String[] args) {
        for (var pack : MapPackages.under(MapWriter.FOLDER)) {
            var stage = StageFile.read(pack.text(), pack.name());
            System.out.println("wrote " + write(stage, pack.file().getParent()).toAbsolutePath());
        }
    }

    /**
     * Rock, or floor a shade lighter for every storey up and for however high the ground stands, or — a stair,
     * being neither — a colour of its own.
     *
     * @param rise how much lighter the ground's own height makes it, 0 at the lowest to {@link #HILL_LIFT} at the top
     */
    private static Color groundOf(char cell, int rise) {
        if (cell == '#') {
            return ROCK;
        }
        if (!Character.isDigit(cell)) {
            return STAIR;
        }
        int lift = Math.min(cell - '0', 4) * 22 + rise;
        return new Color(Math.min(255, FLOOR.getRed() + lift), Math.min(255, FLOOR.getGreen() + lift),
                Math.min(255, FLOOR.getBlue() + lift));
    }

    /** How much lighter the highest ground on a map is drawn than its lowest: a hill reads as one from above. */
    private static final int HILL_LIFT = 90;

    /** A cell's height: the middle of its four corners. */
    private static int heightOf(uz.dukeengine.core.pathfind.HeightMap relief, int x, int y) {
        return (relief.at(x, y) + relief.at(x + 1, y) + relief.at(x, y + 1) + relief.at(x + 1, y + 1)) / 4;
    }

    private static int highestOf(uz.dukeengine.core.pathfind.HeightMap relief) {
        int highest = 0;
        for (int y = 0; y < relief.rows(); y++) {
            for (int x = 0; x < relief.columns(); x++) {
                highest = Math.max(highest, relief.at(x, y));
            }
        }
        return highest;
    }

    /**
     * A biome's own colour: the hues shared out evenly round the wheel in the map's order, rather than drawn from
     * the name as a kind's are — five biomes whose names happened to hash near one another would be one blur.
     */
    public static Color biomeColour(uz.dukeengine.dungeon.gen.BiomeMap biomes,
            uz.dukeengine.dungeon.world.Theme biome) {
        return Color.getHSBColor(biomes.biomes().indexOf(biome) / (float) biomes.biomes().size(), 0.55f, 0.95f);
    }

    /** {@code base} with {@code share} of {@code wash} over it. */
    private static Color washed(Color base, Color wash, float share) {
        return new Color(Math.round(base.getRed() + (wash.getRed() - base.getRed()) * share),
                Math.round(base.getGreen() + (wash.getGreen() - base.getGreen()) * share),
                Math.round(base.getBlue() + (wash.getBlue() - base.getBlue()) * share));
    }

    /** A kind's own colour, drawn from its name so two kinds are two colours without anybody choosing them. */
    private static Color colourOf(String kind) {
        return Color.getHSBColor((kind.hashCode() & 0xFFFF) / 65535f, 0.55f, 0.95f);
    }

    private static void mark(java.awt.Graphics2D g2, int cell, Placement at, Color colour, float size) {
        if (at == null) {
            return;
        }
        int wide = Math.max(2, (int) (cell * size));
        g2.setColor(colour);
        g2.fillOval(at.cellX() * cell + cell / 2 - wide / 2, at.cellY() * cell + cell / 2 - wide / 2, wide, wide);
    }
}
