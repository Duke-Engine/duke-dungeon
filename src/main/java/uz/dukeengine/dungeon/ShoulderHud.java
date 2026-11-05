package uz.dukeengine.dungeon;

import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Sheet;

/**
 * The only thing on the screen while the hero is played from his shoulder: his health and, where he has a pool, his
 * mana, in the bottom left corner. The belt ({@link ItemCard}) is at the foot's middle and the minimap in the right
 * corner; his level, attributes and portrait are on the sheet (C).
 */
final class ShoulderHud {

    private ShoulderHud() {
    }

    /** How tall the bars are, with their gap, for what is laid above them. */
    static float height(Canvas canvas) {
        return margin(canvas) + healthHeight(canvas) * 1.6f + 3f;
    }

    private static float margin(Canvas canvas) {
        return Math.max(12f, canvas.height() * 0.025f);
    }

    private static float healthHeight(Canvas canvas) {
        return Math.clamp(canvas.height() * 0.024f, 14f, 24f);
    }

    static void paint(Canvas canvas, Sheet sheet, DungeonSettings settings) {
        if (sheet == null || sheet.maxHealth() <= 0) {
            return;
        }
        var look = settings.menu();
        float margin = margin(canvas);
        float wide = Math.clamp(canvas.width() * 0.2f, 180f, 360f);
        float high = healthHeight(canvas);
        float manaHigh = high * 0.6f;
        float x = margin;
        float manaY = canvas.height() - margin - manaHigh;
        float healthY = sheet.maxMana() > 0 ? manaY - 3f - high : canvas.height() - margin - high;
        bar(canvas, look.stoneDeepColour(), look.stoneEdgeColour(), x, healthY, wide, high,
                sheet.health() / (float) sheet.maxHealth(), 0x3C0303, sheet.health() + " / " + sheet.maxHealth(),
                look.boneColour());
        if (sheet.maxMana() > 0) {
            bar(canvas, look.stoneDeepColour(), look.stoneEdgeColour(), x, manaY, wide, manaHigh,
                    sheet.mana() / (float) sheet.maxMana(), 0x04144E, null, look.boneColour());
        }
    }

    /** The canvas colours are drawn as light, not as paint: the fills are given darker than they are meant to look. */
    private static void bar(Canvas canvas, int back, int edge, float x, float y, float w, float h, float fill,
            int colour, String words, int ink) {
        fill = Math.clamp(fill, 0f, 1f);
        canvas.fillRect(x, y, w, h, 0xC0000000 | back);
        canvas.fillRect(x + 1f, y + 1f, (w - 2f) * fill, h - 2f, 0xFF000000 | colour);
        canvas.fillRect(x + 1f, y + 1f, (w - 2f) * fill, (h - 2f) * 0.4f, 0x20FFFFFF);
        canvas.openRect(x, y, w, h, 1f, 0xFF000000 | edge);
        if (words != null) {
            var font = Canvas.Font.of("Georgia", Math.max(9, Math.round(h * 0.7f))).bold(true);
            var size = canvas.measure(font, words);
            float tx = x + (w - size.width()) / 2f;
            float ty = y + (h - size.lineHeight()) / 2f;
            canvas.drawText(font, words, tx + 1f, ty + 1f, 0xFF000000);
            canvas.drawText(font, words, tx, ty, 0xFF000000 | ink);
        }
    }
}
