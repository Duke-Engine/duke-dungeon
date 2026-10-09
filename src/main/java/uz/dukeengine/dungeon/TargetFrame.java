package uz.dukeengine.dungeon;

import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.rpg.BuffScreen;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Target;

/**
 * The target frame: a small window at the top middle of the screen about the one creature he has picked out to look
 * at — its face, name and level, a person's role, its health, what it hits for and shrugs off, and what is on it —
 * while the hero's own panel at the foot stays his. WoW's and Diablo's answer to "what is that".
 *
 * <p>Painted on the game's canvas with the mission list, on the window's thread, and only reading: the frame is the
 * run's ({@code DungeonRun.getTarget}), worked out whole on the simulation's thread, and the buffs are the engine's
 * {@link BuffScreen}'s, which reads what is on the creature picked alone after every frame. Nothing is drawn when
 * nothing (or the hero himself) is picked.
 *
 * <p>The face is the creature alive in its portrait, as the hero bar draws his (E26's {@code Canvas.drawPortrait}),
 * over the first letter of its name, which shows for one with no portrait look.
 */
final class TargetFrame {

    private TargetFrame() {
    }

    /** {@code target} at the top middle of the canvas, with what {@code buffs} says is on it; nothing for null. */
    static void paint(Canvas canvas, Target target, List<BuffScreen.Buff> buffs, DungeonSettings settings) {
        if (target == null) {
            return;
        }
        var look = settings.menu();
        var hud = settings.hud();
        int text = Math.clamp(Math.round(canvas.height() * 0.017f), 11, 20);
        var font = Canvas.Font.of("Georgia", text);
        var bold = font.bold(true);
        float pad = text * 0.5f;
        float face = text * 3.6f;
        float wide = Math.clamp(canvas.width() * 0.2f, 230f, 360f);
        var line = canvas.measure(font, "Ag").lineHeight();
        boolean hurtable = target.maxHealth() > 0;
        float rows = line * (target.role().isBlank() ? 2 : 3) + (hurtable ? line * 0.9f + pad * 0.5f : 0f);
        float high = Math.max(face, rows) + pad * 2f;
        float left = (canvas.width() - wide) / 2f;
        float top = Math.max(4f, canvas.height() * 0.015f);
        int bone = 0xFF000000 | look.boneColour();
        int dim = 0xA0000000 | look.boneColour();
        int edge = 0xFF000000 | look.stoneEdgeColour();

        canvas.fillRect(left, top, wide, high, 0xD8000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 2f, edge);

        // The face: its live portrait in a frame, over the first letter of its name for one that has none.
        float fx = left + pad;
        float fy = top + pad;
        canvas.fillRect(fx, fy, face, face, 0xFF000000 | look.stoneColour());
        canvas.openRect(fx, fy, face, face, 2f, edge);
        var big = Canvas.Font.of("Georgia", Math.round(face * 0.6f)).bold(true);
        var letter = canvas.measure(big, target.letter());
        canvas.drawText(big, target.letter(), fx + (face - letter.width()) / 2f,
                fy + (face - letter.lineHeight()) / 2f, 0xFF000000 | look.torchColour());
        canvas.drawPortrait(target.id(), fx + 2f, fy + 2f, fx + face - 2f, fy + face - 2f);

        // Its name, and beside it its level; under it a person's role.
        float x = fx + face + pad;
        float right = left + wide - pad;
        float y = fy;
        canvas.drawText(bold, target.name(), x, y, bone);
        if (target.level() > 0) {
            var level = target.level() + hud.rankSuffix();
            canvas.drawText(font, level, right - canvas.measure(font, level).width(), y, dim);
        }
        y += line;
        if (!target.role().isBlank()) {
            canvas.drawText(font, target.role(), x, y, dim);
            y += line;
        }

        // Its health, as a bar with the figures on it -- none for a person, whom nothing can hurt.
        var small = Canvas.Font.of("Georgia", Math.max(9, Math.round(text * 0.8f)));
        if (hurtable) {
            float barHigh = line * 0.9f;
            canvas.fillRect(x, y, right - x, barHigh, 0xFF000000 | look.stoneDeepColour());
            float share = Math.clamp((float) target.health() / target.maxHealth(), 0f, 1f);
            canvas.fillRect(x, y, (right - x) * share, barHigh, 0xFF000000 | look.bloodColour());
            canvas.openRect(x, y, right - x, barHigh, 1f, edge);
            var hp = target.health() + " / " + target.maxHealth();
            var hpSize = canvas.measure(small, hp);
            canvas.drawText(small, hp, x + (right - x - hpSize.width()) / 2f,
                    y + (barHigh - hpSize.lineHeight()) / 2f, bone);
            y += barHigh + pad * 0.5f;
        }

        // What it hits for and shrugs off, and then what is on it.
        float icon = line;
        var pictures = hud.statIcons();
        x = figure(canvas, font, target.attack(), pictures.get(0), x, y, icon, bone);
        x = figure(canvas, font, target.armour(), pictures.get(1), x, y, icon, bone);
        for (var buff : buffs) {
            var skill = buff.skill();
            if (skill == null) {
                continue;
            }
            if (skill.icon().isBlank()) {
                var first = skill.name().isEmpty() ? "?" : skill.name().substring(0, 1);
                canvas.drawText(small, first, x + icon * 0.25f, y, bone);
            } else {
                canvas.drawImage(Canvas.Image.of(skill.icon()), x, y, x + icon, y + icon, 0xFFFFFFFF,
                        Canvas.Blend.ALPHA);
            }
            canvas.openRect(x, y, icon, icon, 1f, edge);
            x += icon + pad * 0.5f;
        }
    }

    /** One figure with its picture, where it is worth anything; where the next thing goes. */
    private static float figure(Canvas canvas, Canvas.Font font, int value, String picture, float x, float y,
            float icon, int argb) {
        if (value <= 0) {
            return x;
        }
        canvas.drawImage(Canvas.Image.of(picture), x, y, x + icon, y + icon, 0xFFFFFFFF, Canvas.Blend.ALPHA);
        var said = String.valueOf(value);
        canvas.drawText(font, said, x + icon + 2f, y, argb);
        return x + icon + 2f + canvas.measure(font, said).width() + icon * 0.5f;
    }
}
