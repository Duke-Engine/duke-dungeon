import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * The marks an aura's bearer wears on the floor, painted rather than found: white on clear, for an Effect layer to
 * colour. A ring of runes for the thirst (the look of Warcraft III's vampiric aura), ripples for the mana (its
 * brilliance aura), and a sigil of eight points for the might.
 *
 * <p>Run once from the repository's root, and the pictures are written where the game reads them:
 *
 * <pre>java art/effects/AuraDecals.java src/main/resources/effects</pre>
 */
public final class AuraDecals {

    private static final int SIZE = 256;
    private static final double MIDDLE = SIZE / 2.0;

    public static void main(String[] args) throws IOException {
        var out = new File(args.length > 0 ? args[0] : "src/main/resources/effects");
        out.mkdirs();
        write(runes(), new File(out, "aura_runes.png"));
        write(ripples(), new File(out, "aura_ripples.png"));
        write(sigil(), new File(out, "aura_sigil.png"));
    }

    /** Two rings with runes standing between them, and a thin ring inside. */
    static BufferedImage runes() {
        var image = blank();
        var g = pen(image);
        ring(g, 118, 4f);
        ring(g, 92, 3f);
        ring(g, 62, 2f);
        int count = 18;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            var at = g.getTransform();
            g.translate(MIDDLE + 105 * Math.cos(angle), MIDDLE + 105 * Math.sin(angle));
            g.rotate(angle + Math.PI / 2);
            rune(g, i);
            g.setTransform(at);
        }
        g.dispose();
        return glow(image);
    }

    /** Rings going out from the middle, each brighter on one side than the other, so they read as turning water. */
    static BufferedImage ripples() {
        var image = blank();
        double[] radii = {30, 52, 74, 96, 116};
        float[] widths = {10f, 9f, 8f, 7f, 6f};
        for (int r = 0; r < radii.length; r++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double dx = x + 0.5 - MIDDLE;
                    double dy = y + 0.5 - MIDDLE;
                    double away = Math.abs(Math.hypot(dx, dy) - radii[r]);
                    if (away > widths[r]) {
                        continue;
                    }
                    double across = 1 - away / widths[r];
                    double turn = 0.45 + 0.55 * (0.5 + 0.5 * Math.cos(Math.atan2(dy, dx) * 2 + r * 1.3));
                    double fade = 1 - 0.08 * r;
                    put(image, x, y, across * across * turn * fade);
                }
            }
        }
        return glow(image);
    }

    /** A double ring round a star of eight points, a small ring at each point. */
    static BufferedImage sigil() {
        var image = blank();
        var g = pen(image);
        ring(g, 118, 4f);
        ring(g, 104, 2.5f);
        var star = new Path2D.Double();
        for (int i = 0; i <= 8; i++) {
            double angle = 2 * Math.PI * ((i * 3) % 8) / 8 - Math.PI / 2;
            double x = MIDDLE + 100 * Math.cos(angle);
            double y = MIDDLE + 100 * Math.sin(angle);
            if (i == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(star);
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8 - Math.PI / 2;
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(MIDDLE + 100 * Math.cos(angle) - 7, MIDDLE + 100 * Math.sin(angle) - 7, 14, 14));
        }
        g.dispose();
        return glow(image);
    }

    /**
     * One rune, 14 wide and 18 tall about its own middle, made of the strokes the old alphabets were cut with: a
     * stave and its branches. Which branches, by its place in the ring.
     */
    private static void rune(Graphics2D g, int which) {
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double[][][] shapes = {
            {{0, -9, 0, 9}, {0, -9, 6, -4}, {0, -2, 6, 3}},
            {{-4, -9, -4, 9}, {-4, -9, 5, -3}, {5, -3, 5, 9}},
            {{0, -9, 0, 9}, {0, -5, 6, 0}, {6, 0, 0, 5}},
            {{-5, -9, 5, 9}, {5, -9, -5, 9}},
            {{0, -9, 0, 9}, {-6, -3, 0, -9}, {6, -3, 0, -9}},
            {{-6, -9, 6, -9}, {0, -9, 0, 9}, {-6, 9, 6, 9}},
            {{-5, -9, -5, 9}, {5, -9, 5, 9}, {-5, -2, 5, 2}},
            {{0, -9, 0, 9}, {0, 0, -6, -6}, {0, 0, 6, -6}},
            {{-6, 0, 0, -9}, {0, -9, 6, 0}, {6, 0, 0, 9}, {0, 9, -6, 0}},
            {{0, -9, 0, 9}, {0, -9, -6, -3}, {0, 9, 6, 3}},
        };
        for (double[] line : shapes[which % shapes.length]) {
            g.draw(new Line2D.Double(line[0], line[1], line[2], line[3]));
        }
    }

    private static void ring(Graphics2D g, double radius, float width) {
        g.setStroke(new BasicStroke(width));
        g.draw(new Ellipse2D.Double(MIDDLE - radius, MIDDLE - radius, 2 * radius, 2 * radius));
    }

    private static BufferedImage blank() {
        return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D pen(BufferedImage image) {
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);
        return g;
    }

    private static void put(BufferedImage image, int x, int y, double alpha) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        int was = image.getRGB(x, y) >>> 24;
        image.setRGB(x, y, (Math.max(a, was) << 24) | 0xFFFFFF);
    }

    /**
     * The strokes with a soft light round them: the alpha blurred wide and laid under the sharp, so the mark glows as
     * the game's other floor marks do rather than lying there as ink.
     */
    private static BufferedImage glow(BufferedImage sharp) {
        int[] alpha = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                alpha[y * SIZE + x] = sharp.getRGB(x, y) >>> 24;
            }
        }
        int[] soft = blur(blur(alpha, 4), 4);
        var out = blank();
        for (int i = 0; i < alpha.length; i++) {
            int a = Math.max(alpha[i], (int) (soft[i] * 0.7));
            out.setRGB(i % SIZE, i / SIZE, (a << 24) | 0xFFFFFF);
        }
        return out;
    }

    private static int[] blur(int[] in, int radius) {
        int[] across = new int[in.length];
        int[] down = new int[in.length];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int sum = 0;
                int n = 0;
                for (int k = -radius; k <= radius; k++) {
                    int at = x + k;
                    if (at >= 0 && at < SIZE) {
                        sum += in[y * SIZE + at];
                        n++;
                    }
                }
                across[y * SIZE + x] = sum / n;
            }
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int sum = 0;
                int n = 0;
                for (int k = -radius; k <= radius; k++) {
                    int at = y + k;
                    if (at >= 0 && at < SIZE) {
                        sum += across[at * SIZE + x];
                        n++;
                    }
                }
                down[y * SIZE + x] = sum / n;
            }
        }
        return down;
    }

    private static void write(BufferedImage image, File to) throws IOException {
        ImageIO.write(image, "png", to);
        System.out.println("wrote " + to);
    }
}
