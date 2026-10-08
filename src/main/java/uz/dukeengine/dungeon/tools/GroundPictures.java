package uz.dukeengine.dungeon.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import javax.imageio.ImageIO;

/**
 * The pictures a painted map's ground is drawn with, painted rather than found: grass, dirt, rock, snow, sand, the
 * dark floor of a deep forest and the mud of a marsh,
 * a picture of noise whose four channels break the borders between them, and a large one whose red channel varies
 * each surface so one picture over a valley does not repeat to the eye.
 *
 * <p>Each is value noise summed over a few octaves on a lattice that wraps at the picture's edge, so every picture
 * tiles by construction: the right-hand column runs on into the left-hand one as any column runs into the next. The
 * same seed paints the same picture, pixel for pixel. Run once from the repository's root, and the pictures are
 * written where the game reads them:
 *
 * <pre>./gradlew writeGroundPictures</pre>
 */
public final class GroundPictures {

    /** Where the game reads them: palette values are {@code ground/<name>.png}. */
    static final Path FOLDER = Path.of("src", "main", "resources", "ground");

    static final int SURFACE = 512;
    static final int NOISE = 256;
    static final int VARIATION = 512;

    private static final long SEED = 20261008L;

    /** Every picture by its file's name, each painted at the size it is shipped at. */
    static final Map<String, Function<Integer, BufferedImage>> ALL = Map.of(
            "grass", GroundPictures::grass,
            "dirt", GroundPictures::dirt,
            "rock", GroundPictures::rock,
            "snow", GroundPictures::snow,
            "sand", GroundPictures::sand,
            "forest", GroundPictures::forest,
            "mud", GroundPictures::mud,
            "noise", GroundPictures::noise,
            "variation", GroundPictures::variation);

    static int sizeOf(String name) {
        return switch (name) {
            case "noise" -> NOISE;
            case "variation" -> VARIATION;
            default -> SURFACE;
        };
    }

    private GroundPictures() {
    }

    public static void main(String[] args) throws IOException {
        var out = args.length > 0 ? Path.of(args[0]) : FOLDER;
        Files.createDirectories(out);
        for (var picture : ALL.entrySet()) {
            var file = out.resolve(picture.getKey() + ".png");
            ImageIO.write(picture.getValue().apply(sizeOf(picture.getKey())), "png", file.toFile());
            System.out.println("wrote " + file.toAbsolutePath());
        }
    }

    // ---- the pictures ----

    static BufferedImage grass(int size) {
        return surface(size, (u, v) -> {
            double body = fbm(u, v, 4, 5, SEED + 10);
            double clumps = fbm(u, v, 16, 3, SEED + 11);
            double blade = speck(u, v, size, SEED + 12);
            double t = 0.55 * body + 0.45 * clumps;
            var colour = ramp(t, new double[][] {{38, 62, 22}, {66, 102, 36}, {98, 132, 48}, {136, 146, 66}});
            return shade(colour, 0.82 + 0.3 * blade);
        });
    }

    static BufferedImage dirt(int size) {
        return surface(size, (u, v) -> {
            double body = fbm(u, v, 4, 5, SEED + 20);
            double clods = fbm(u, v, 24, 2, SEED + 21);
            double pebble = speck(u, v, size, SEED + 22);
            var colour = ramp(0.6 * body + 0.4 * clods,
                    new double[][] {{72, 52, 34}, {104, 78, 50}, {134, 104, 70}, {150, 124, 92}});
            // Now and then a pebble, lighter than the earth round it.
            return pebble > 0.93 ? shade(colour, 1.35) : shade(colour, 0.88 + 0.2 * pebble);
        });
    }

    static BufferedImage rock(int size) {
        return surface(size, (u, v) -> {
            double body = fbm(u, v, 3, 6, SEED + 30);
            // Ridged: the folds of the rock, dark where a crack runs.
            double ridge = 1 - Math.abs(2 * fbm(u, v, 6, 4, SEED + 31) - 1);
            double grain = speck(u, v, size, SEED + 32);
            var colour = ramp(0.5 * body + 0.5 * ridge * ridge,
                    new double[][] {{58, 56, 54}, {92, 89, 84}, {128, 124, 116}, {164, 158, 148}});
            return shade(colour, 0.86 + 0.24 * grain);
        });
    }

    static BufferedImage snow(int size) {
        return surface(size, (u, v) -> {
            double drift = fbm(u, v, 3, 5, SEED + 40);
            double crust = fbm(u, v, 20, 2, SEED + 41);
            double glint = speck(u, v, size, SEED + 42);
            var colour = ramp(0.7 * drift + 0.3 * crust,
                    new double[][] {{176, 188, 208}, {208, 216, 230}, {232, 236, 244}, {250, 250, 252}});
            return glint > 0.97 ? new double[] {255, 255, 255} : shade(colour, 0.96 + 0.06 * glint);
        });
    }

    static BufferedImage sand(int size) {
        return surface(size, (u, v) -> {
            double body = fbm(u, v, 4, 4, SEED + 50);
            double warp = fbm(u, v, 3, 2, SEED + 51);
            // Ripples the wind left: a whole number of waves across, bent by the warp, so they wrap too.
            double ripple = 0.5 + 0.5 * Math.sin(2 * Math.PI * (9 * v + 2 * u + 0.6 * warp));
            double grain = speck(u, v, size, SEED + 52);
            var colour = ramp(0.6 * body + 0.25 * ripple + 0.15 * grain,
                    new double[][] {{168, 140, 94}, {196, 170, 120}, {218, 198, 150}, {232, 216, 172}});
            return shade(colour, 0.94 + 0.1 * grain);
        });
    }

    /** A deep forest's floor: moss and old needles, darker and browner than a meadow's grass. */
    static BufferedImage forest(int size) {
        return surface(size, (u, v) -> {
            double body = fbm(u, v, 4, 5, SEED + 80);
            double litter = fbm(u, v, 20, 2, SEED + 81);
            double needle = speck(u, v, size, SEED + 82);
            var colour = ramp(0.6 * body + 0.4 * litter,
                    new double[][] {{24, 36, 18}, {40, 56, 26}, {58, 70, 32}, {78, 72, 40}});
            return needle > 0.94 ? shade(colour, 1.3) : shade(colour, 0.85 + 0.25 * needle);
        });
    }

    /** A marsh's mud: dark wet earth, and lighter puddles where the sky shows in it. */
    static BufferedImage mud(int size) {
        return surface(size, (u, v) -> {
            double body = fbm(u, v, 4, 5, SEED + 90);
            double puddle = fbm(u, v, 6, 3, SEED + 91);
            double grain = speck(u, v, size, SEED + 92);
            var colour = ramp(body, new double[][] {{36, 30, 22}, {54, 44, 30}, {70, 60, 42}, {84, 76, 58}});
            // A puddle's edge fades into the mud rather than being cut out of it.
            double wet = Math.clamp((puddle - 0.6) / 0.08, 0, 1);
            var water = new double[] {52, 60, 60};
            return shade(new double[] {lerp(colour[0], water[0], wet), lerp(colour[1], water[1], wet),
                    lerp(colour[2], water[2], wet)}, 0.9 + 0.15 * grain);
        });
    }

    /** Four channels, each its own noise: the blend reads each to move a different surface's border. */
    static BufferedImage noise(int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double u = (double) x / size;
                double v = (double) y / size;
                int r = byteOf(fbm(u, v, 4, 4, SEED + 60));
                int g = byteOf(fbm(u, v, 4, 4, SEED + 61));
                int b = byteOf(fbm(u, v, 4, 4, SEED + 62));
                int a = byteOf(fbm(u, v, 4, 4, SEED + 63));
                image.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    /** Low and slow: two octaves from a lattice of three across, read for its red channel. */
    static BufferedImage variation(int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int r = byteOf(stretch(fbm((double) x / size, (double) y / size, 3, 2, SEED + 70)));
                image.setRGB(x, y, r << 16 | r << 8 | r);
            }
        }
        return image;
    }

    // ---- how they are painted ----

    private interface Painter {
        double[] at(double u, double v);
    }

    private static BufferedImage surface(int size, Painter painter) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                var colour = painter.at((double) x / size, (double) y / size);
                image.setRGB(x, y, channel(colour[0]) << 16 | channel(colour[1]) << 8 | channel(colour[2]));
            }
        }
        return image;
    }

    /**
     * Fractal value noise at ({@code u}, {@code v}) in [0, 1): {@code octaves} layers, the first on a lattice
     * {@code base} cells across, each next twice as fine and half as strong. Every lattice wraps at the picture's
     * edge, so the sum does too. Roughly 0..1, centred on a half.
     */
    public static double fbm(double u, double v, int base, int octaves, long seed) {
        double sum = 0;
        double weight = 0;
        double amplitude = 1;
        for (int o = 0; o < octaves; o++) {
            int period = base << o;
            sum += amplitude * value(u * period, v * period, period, seed * 131 + o);
            weight += amplitude;
            amplitude *= 0.5;
        }
        return sum / weight;
    }

    /** Value noise on a lattice that repeats every {@code period} cells, smoothly between its points. */
    private static double value(double x, double y, int period, long seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = fade(x - x0);
        double fy = fade(y - y0);
        double a = lattice(x0, y0, period, seed);
        double b = lattice(x0 + 1, y0, period, seed);
        double c = lattice(x0, y0 + 1, period, seed);
        double d = lattice(x0 + 1, y0 + 1, period, seed);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fy);
    }

    /** One pixel's own grain: a lattice as fine as the picture, so it wraps as the picture does. */
    private static double speck(double u, double v, int size, long seed) {
        return lattice((int) Math.round(u * size), (int) Math.round(v * size), size, seed);
    }

    private static double lattice(int x, int y, int period, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + Math.floorMod(x, period) * 0xBF58476D1CE4E5B9L
                + Math.floorMod(y, period) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) / (double) (1L << 53);
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Noise summed over octaves gathers about a half; this spreads it back toward the whole of 0..1. */
    private static double stretch(double t) {
        return Math.clamp((t - 0.5) * 2.2 + 0.5, 0, 1);
    }

    /** A colour along stops spaced evenly over 0..1, {@code t} stretched first so the whole ramp is used. */
    private static double[] ramp(double t, double[][] stops) {
        double at = stretch(t) * (stops.length - 1);
        int i = Math.min((int) at, stops.length - 2);
        double f = at - i;
        return new double[] {lerp(stops[i][0], stops[i + 1][0], f), lerp(stops[i][1], stops[i + 1][1], f),
                lerp(stops[i][2], stops[i + 1][2], f)};
    }

    private static double[] shade(double[] colour, double by) {
        return new double[] {colour[0] * by, colour[1] * by, colour[2] * by};
    }

    private static int channel(double value) {
        return (int) Math.clamp(Math.round(value), 0, 255);
    }

    private static int byteOf(double t) {
        return channel(t * 255);
    }
}
