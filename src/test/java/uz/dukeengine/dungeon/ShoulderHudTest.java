package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Sheet;

/** The third-person HUD: his health and mana in the bottom left corner, and nothing else of its own. */
class ShoulderHudTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final class Drawn implements Canvas {
        final List<String> text = new ArrayList<>();
        final List<float[]> filled = new ArrayList<>();

        @Override
        public int width() {
            return 1280;
        }

        @Override
        public int height() {
            return 720;
        }

        @Override
        public void drawImage(Image image, float x0, float y0, float x1, float y1, int argb, Blend blend) {
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
            filled.add(new float[] {x, y, w, h});
        }

        @Override
        public void openRect(float x, float y, float w, float h, float lineWidth, int argb) {
        }

        @Override
        public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        }

        @Override
        public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        }

        @Override
        public void line(float x0, float y0, float x1, float y1, float width, int argb, int argbEnd) {
        }

        @Override
        public void clip(float x0, float y0, float x1, float y1) {
        }

        @Override
        public void noClip() {
        }

        @Override
        public void drawText(Font font, String text, float x, float y, int argb) {
            this.text.add(text);
        }

        @Override
        public Measure measure(Font font, String text) {
            return new Measure(text.length() * font.pixelHeight() / 2, font.pixelHeight());
        }
    }

    private static Sheet sheet(int health, int mana, int maxMana) {
        return new Sheet(1, "Garen", "", 1, 0, 30, List.of(), 30, 100, 0, 18, 6, health, 980, mana, maxMana, 0, 0, 0,
                List.of(), 0, -1, 0, 0);
    }

    @Test
    void healthAndManaAreBarsInTheBottomLeftCornerAndTheHeroWithNoPoolHasOnlyHealth() {
        var both = new Drawn();
        ShoulderHud.paint(both, sheet(500, 20, 50), SETTINGS);
        assertEquals(List.of("500 / 980"), both.text.stream().distinct().toList(), "the health in words, the mana in its bar alone");
        assertTrue(both.filled.stream().allMatch(r -> r[0] < 1280 / 2f && r[1] > 720 / 2f), "the lower left corner");
        float lowest = (float) both.filled.stream().mapToDouble(r -> r[1] + r[3]).max().orElseThrow();
        assertTrue(lowest < 720f && lowest > 680f, "at the foot of the screen, inside it: " + lowest);

        var one = new Drawn();
        ShoulderHud.paint(one, sheet(980, 0, 0), SETTINGS);
        assertTrue(one.filled.size() < both.filled.size(), "no mana bar for no pool");
    }

    @Test
    void nobodyIsDrawnForNoHero() {
        var none = new Drawn();
        ShoulderHud.paint(none, null, SETTINGS);
        assertTrue(none.filled.isEmpty() && none.text.isEmpty());
    }
}
