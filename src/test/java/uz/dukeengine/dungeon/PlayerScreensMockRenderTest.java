package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.Picture;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;

/**
 * The three screens drawn as the window's canvas would draw them, with Java2D, into {@code build/mock-renders}: a look
 * at them without the game running. The portrait, which only the window keeps, is a framed placeholder.
 */
class PlayerScreensMockRenderTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Path OUT = Path.of("build", "mock-renders");

    /** The canvas over a picture: what the window's card does, near enough to judge a layout by. */
    static final class Java2D implements Canvas {

        final BufferedImage image;
        private final Graphics2D g;
        private final Map<String, java.awt.Image> loaded = new HashMap<>();

        Java2D(int width, int height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }

        private static Color colour(int argb) {
            return new Color(argb, true);
        }

        private static java.awt.Font font(Canvas.Font font, String text) {
            var awt = new java.awt.Font(font.family(), font.bold() ? java.awt.Font.BOLD : java.awt.Font.PLAIN, font.pixelHeight());
            if (awt.canDisplayUpTo(text) >= 0 && font.wideFamily() != null) {
                awt = new java.awt.Font(font.wideFamily(), awt.getStyle(), font.pixelHeight());
            }
            return awt;
        }

        @Override
        public int width() {
            return image.getWidth();
        }

        @Override
        public int height() {
            return image.getHeight();
        }

        @Override
        public void drawImage(Image picture, float x0, float y0, float x1, float y1, int argb, Blend blend) {
            var awt = loaded.computeIfAbsent(picture.path(), path -> {
                try (var in = PlayerScreensMockRenderTest.class.getClassLoader().getResourceAsStream(path)) {
                    return in == null ? null : ImageIO.read(in);
                } catch (java.io.IOException e) {
                    return null;
                }
            });
            if (awt != null) {
                // ponytail: the tint is not drawn; a picture shows in its own colours
                g.drawImage(awt, Math.round(x0), Math.round(y0), Math.round(x1 - x0), Math.round(y1 - y0), null);
            }
        }

        @Override
        public void drawPicture(Picture picture, float x0, float y0, float x1, float y1, int argb, Blend blend) {
            var awt = new BufferedImage(picture.width(), picture.height(), BufferedImage.TYPE_INT_ARGB);
            awt.setRGB(0, 0, picture.width(), picture.height(), picture.argb(), 0, picture.width());
            g.drawImage(awt, Math.round(x0), Math.round(y0), Math.round(x1 - x0), Math.round(y1 - y0), null);
        }

        @Override
        public void drawPortrait(int unitId, float x0, float y0, float x1, float y1) {
            g.setPaint(new java.awt.GradientPaint(x0, y0, new Color(0x5A4A3A), x1, y1, new Color(0x1A1410)));
            g.fill(new java.awt.geom.Ellipse2D.Float(x0 + (x1 - x0) * 0.3f, y0 + (y1 - y0) * 0.15f,
                    (x1 - x0) * 0.4f, (y1 - y0) * 0.45f));
            g.fill(new java.awt.geom.RoundRectangle2D.Float(x0 + (x1 - x0) * 0.15f, y0 + (y1 - y0) * 0.6f,
                    (x1 - x0) * 0.7f, (y1 - y0) * 0.4f, 30f, 30f));
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
            g.setColor(colour(argb));
            g.fill(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
        }

        @Override
        public void openRect(float x, float y, float w, float h, float lineWidth, int argb) {
            g.setColor(colour(argb));
            g.setStroke(new BasicStroke(lineWidth));
            g.draw(new java.awt.geom.Rectangle2D.Float(x + lineWidth / 2f, y + lineWidth / 2f, w - lineWidth,
                    h - lineWidth));
        }

        @Override
        public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
            var path = new Path2D.Float();
            path.moveTo(x0, y0);
            path.lineTo(x1, y1);
            path.lineTo(x2, y2);
            path.closePath();
            g.setColor(colour(argb));
            g.fill(path);
        }

        @Override
        public void line(float x0, float y0, float x1, float y1, float width, int argb) {
            g.setColor(colour(argb));
            g.setStroke(new BasicStroke(width));
            g.draw(new java.awt.geom.Line2D.Float(x0, y0, x1, y1));
        }

        @Override
        public void line(float x0, float y0, float x1, float y1, float width, int argb, int argbEnd) {
            line(x0, y0, x1, y1, width, argb);
        }

        @Override
        public void clip(float x0, float y0, float x1, float y1) {
            g.setClip(new java.awt.geom.Rectangle2D.Float(x0, y0, x1 - x0, y1 - y0));
        }

        @Override
        public void noClip() {
            g.setClip(null);
        }

        @Override
        public void drawText(Canvas.Font font, String text, float x, float y, int argb) {
            var awt = font(font, text);
            g.setFont(awt);
            g.setColor(colour(argb));
            g.drawString(text, x, y + g.getFontMetrics().getAscent());
        }

        @Override
        public Measure measure(Canvas.Font font, String text) {
            var metrics = g.getFontMetrics(font(font, text));
            return new Measure(metrics.stringWidth(text), metrics.getHeight());
        }

        @Override
        public Box bottomPanel() {
            return new Box(0f, height() - 150f, width(), 150f);
        }

        void save(String name) throws java.io.IOException {
            Files.createDirectories(OUT);
            ImageIO.write(image, "png", OUT.resolve(name).toFile());
        }
    }

    /** A dungeon-ish backdrop for what the screens are drawn over: the world, dimmed by the screen itself. */
    private static Java2D backdrop() {
        var canvas = new Java2D(1600, 900);
        canvas.fillRect(0f, 0f, 1600f, 900f, 0xFF2E3A2A);
        for (int i = 0; i < 40; i++) {
            canvas.fillRect((i * 397) % 1600, (i * 211) % 750, 90f, 60f, 0xFF3C4A34);
        }
        canvas.fillRect(0f, 750f, 1600f, 150f, 0xFF1A1410);
        return canvas;
    }

    @Test
    void theThreeScreensAreDrawnIntoPictures() throws Exception {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var commander = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Commander")).findFirst().orElseThrow();
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(SETTINGS.run().defaultHero())).findFirst().orElseThrow();
        hero.setPosition(new Coord3D(commander.getPosition().x() + 12f, commander.getPosition().y(),
                commander.getPosition().z()));
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() + 2);

        var sheet = backdrop();
        CharacterSheet.paint(sheet, session.run().getSheet(), SETTINGS);
        sheet.save("character-sheet.png");

        var log = backdrop();
        var quests = new QuestLogScreen(SETTINGS);
        quests.pick("LostLocket");
        quests.paint(log, session);
        log.save("quest-log.png");

        var hints = backdrop();
        PlayerScreens.hints(hints, SETTINGS);
        hints.save("hud-key-hints.png");

        // The open world's map, from its own preview picture standing in for the one the client paints.
        var world = Dungeon.newStageSession(Stages.load("world", SETTINGS), SETTINGS);
        world.game().runHeadless(SETTINGS.story().dialogue().checkFrames() + 1);
        var map = new WorldMapScreen(SETTINGS);
        var preview = ImageIO.read(PlayerScreensMockRenderTest.class.getClassLoader()
                .getResourceAsStream("maps/world/preview.png"));
        var picture = new Picture(preview.getWidth(), preview.getHeight());
        preview.getRGB(0, 0, preview.getWidth(), preview.getHeight(), picture.argb(), 0, preview.getWidth());
        map.picture(picture);
        var drawn = backdrop();
        map.paint(drawn, world);
        drawn.save("world-map.png");

        assertTrue(Files.size(OUT.resolve("world-map.png")) > 0);
    }
}
