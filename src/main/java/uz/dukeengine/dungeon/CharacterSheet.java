package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Sheet;

/**
 * The character sheet (C): his live portrait, name, level and experience; his attributes and what they give; what
 * everything comes to; what he wears; his gold, and his souls while he wears the medallion. Only reading: the sheet is
 * the run's ({@code DungeonRun.getSheet}), worked out on the simulation's thread, and every word is the data's -- the
 * Hud's and the bag's for the figures, as the panel and the bag call them. What he wears is shown, not changed: the bag
 * does the swapping.
 */
final class CharacterSheet {

    private CharacterSheet() {
    }

    /** One figure: its word, and what it comes to. */
    record Row(String label, String value) {
    }

    /** What everything he has comes to, a row each, in the data's words; a figure worth nothing to him is left out. */
    static List<Row> figures(Sheet sheet, DungeonSettings settings) {
        var hud = settings.hud();
        var drops = settings.lootDrops();
        var rows = new ArrayList<Row>();
        rows.add(new Row(hud.attackWord(), Integer.toString(sheet.damage())));
        if (sheet.blowsPerSecond() > 0) {
            rows.add(new Row(drops.attackSpeedWord(), hundredths(sheet.blowsPerSecond())
                    + settings.screens().perSecondWord()
                    + (sheet.attackSpeed() > 0 ? " (+" + sheet.attackSpeed() + "%)" : "")));
        }
        rows.add(new Row(hud.armourWord(), sheet.armour() + "%"));
        rows.add(new Row(hud.healthWord(), sheet.health() + " / " + sheet.maxHealth()));
        if (sheet.maxMana() > 0) {
            rows.add(new Row(hud.manaWord(), sheet.mana() + " / " + sheet.maxMana()));
        }
        if (sheet.healthRegen() > 0) {
            rows.add(new Row(drops.healthRegenWord(), tenths(sheet.healthRegen())));
        }
        if (sheet.manaRegen() > 0 && sheet.maxMana() > 0) {
            rows.add(new Row(drops.manaRegenWord(), tenths(sheet.manaRegen())));
        }
        rows.add(new Row(hud.speedWord(), Integer.toString(sheet.speed())));
        if (sheet.lifesteal() > 0) {
            rows.add(new Row(hud.lifestealWord(), sheet.lifesteal() + "%"));
        }
        return rows;
    }

    /**
     * What all of one attribute he has gives him, a line each, in the Hud's words: {@code +264 Jon}, and for his
     * primary what it adds to his blow -- the file's figures a point, times his points.
     */
    static List<String> gives(Sheet.Attribute attribute, DungeonSettings settings) {
        var hud = settings.hud();
        var rules = settings.attributeRules();
        var each = rules.attributes().get(attribute.index());
        var said = new ArrayList<String>();
        give(said, hud.healthWord(), each.healthPerPoint().value(), attribute.value());
        give(said, hud.speedWord(), each.speedPerPoint().value(), attribute.value());
        give(said, hud.manaWord(), each.manaPerPoint().value(), attribute.value());
        if (attribute.primary()) {
            give(said, hud.attackWord(), rules.damagePerPrimary(), attribute.value());
        }
        return said;
    }

    private static void give(List<String> said, String word, int hundredthsAPoint, int points) {
        if (hundredthsAPoint != 0 && points != 0) {
            said.add("+" + hundredths(hundredthsAPoint * points) + " " + word);
        }
    }

    /** A figure kept in hundredths, as it reads: 125 is "1.25", 1200 is "12". */
    static String hundredths(int value) {
        return java.math.BigDecimal.valueOf(value, 2).stripTrailingZeros().toPlainString();
    }

    private static String tenths(int value) {
        return java.math.BigDecimal.valueOf(value, 1).stripTrailingZeros().toPlainString();
    }

    /** The sheet in the middle of the window; nothing for no hero. */
    static void paint(Canvas canvas, Sheet sheet, DungeonSettings settings) {
        if (sheet == null) {
            return;
        }
        var look = settings.menu();
        var screens = settings.screens();
        int text = Math.clamp(Math.round(canvas.height() * 0.019f), 12, 22);
        var font = Canvas.Font.of("Georgia", text).wideFamily(MissionScreen.MARKS);
        var bold = font.bold(true);
        var small = Canvas.Font.of("Georgia", Math.max(10, Math.round(text * 0.8f)));
        float line = canvas.measure(font, "Ag").lineHeight() * 1.35f;
        float pad = text;
        float wide = Math.min(canvas.width() - 32f, text * 46f);
        float high = Math.min(canvas.height() - 32f, text * 34f);
        var box = PlayerScreens.frame(canvas, wide, high, screens.sheetTitle(), screens.closeHint(), settings);
        int gold = 0xFF000000 | look.torchColour();
        int bone = 0xFF000000 | look.boneColour();
        int dim = 0xA0000000 | look.boneColour();
        int edge = 0xFF000000 | look.stoneEdgeColour();

        // Left: his face, alive, and under it his name, title, level, experience, gold and souls.
        float left = box.x() + pad;
        float top = box.y() + pad + line * 1.4f;
        float column = (box.width() - pad * 4f) / 3f;
        float face = Math.min(column, high * 0.36f);
        canvas.fillRect(left, top, face, face, 0xFF000000 | look.stoneColour());
        var big = Canvas.Font.of("Georgia", Math.round(face * 0.5f)).bold(true);
        var letter = sheet.name().isEmpty() ? "?" : sheet.name().substring(0, 1);
        var size = canvas.measure(big, letter);
        canvas.drawText(big, letter, left + (face - size.width()) / 2f, top + (face - size.lineHeight()) / 2f, gold);
        canvas.drawPortrait(sheet.id(), left + 2f, top + 2f, left + face - 2f, top + face - 2f);
        canvas.openRect(left, top, face, face, 2f, gold);
        float y = top + face + pad * 0.6f;
        canvas.drawText(bold, sheet.name(), left, y, bone);
        y += line;
        if (!sheet.title().isBlank()) {
            canvas.drawText(font, sheet.title(), left, y, dim);
            y += line;
        }
        canvas.drawText(font, sheet.level() + settings.hud().rankSuffix(), left, y, gold);
        y += line;
        float share = sheet.experienceToGo() <= 0 ? 1f
                : Math.clamp((float) sheet.experience() / sheet.experienceToGo(), 0f, 1f);
        PlayerScreens.bar(canvas, small, left, y, face, line * 0.8f, share,
                screens.experienceWord() + " " + sheet.experience() + " / " + sheet.experienceToGo(),
                0xFF000000 | look.torchColour(), settings);
        y += line * 1.2f;
        canvas.drawText(font, settings.story().trade().goldWord() + ": " + sheet.gold(), left, y, gold);
        y += line;
        if (sheet.souls() >= 0) {
            canvas.drawText(font, settings.medallion().soulsWord() + ": " + sheet.souls(), left, y, gold);
        }

        // Middle: his attributes, each with its picture, what he has (found in green) and what it gives.
        float mx = left + column + pad;
        y = top;
        canvas.drawText(bold, screens.attributesWord(), mx, y, gold);
        y += line * 1.2f;
        var art = settings.attributeArt();
        float icon = line;
        for (var attribute : sheet.attributes()) {
            var its = art.get(attribute.index());
            if (its.icon() != null && !its.icon().isBlank()) {
                canvas.drawImage(Canvas.Image.of(its.icon()), mx, y, mx + icon, y + icon, 0xFFFFFFFF,
                        Canvas.Blend.ALPHA);
            }
            var named = its.word() + (attribute.primary() ? " ★" : "");
            canvas.drawText(attribute.primary() ? bold : font, named, mx + icon + 6f, y, bone);
            var value = Integer.toString(attribute.value());
            float vx = mx + column - canvas.measure(font, value).width();
            canvas.drawText(font, value, vx, y, bone);
            int found = attribute.value() - attribute.bare();
            if (found > 0) {
                var plus = "+" + found;
                canvas.drawText(small, plus, vx - canvas.measure(small, plus).width() - 4f, y + 2f,
                        0xFF000000 | settings.unitBar().friend());
            }
            y += line;
            for (var gives : gives(attribute, settings)) {
                canvas.drawText(small, gives, mx + icon + 6f, y, dim);
                y += line * 0.8f;
            }
            y += line * 0.3f;
        }

        // Right: what it all comes to.
        float rx = mx + column + pad;
        y = top;
        canvas.drawText(bold, screens.figuresWord(), rx, y, gold);
        y += line * 1.2f;
        for (var row : figures(sheet, settings)) {
            canvas.drawText(font, row.label(), rx, y, dim);
            canvas.drawText(font, row.value(), rx + column - canvas.measure(font, row.value()).width(), y, bone);
            y += line;
        }

        // Along the foot: what he wears, a framed slot each with its word under it.
        if (!sheet.worn().isEmpty()) {
            float slot = Math.min(line * 2.2f, (box.width() - pad * 2f) / sheet.worn().size() - pad * 0.5f);
            float sy = box.y() + box.height() - pad * 1.6f - line - slot - line * 0.8f;
            canvas.drawText(bold, screens.wornWord(), left, sy - line * 1.1f, gold);
            float sx = left;
            for (var worn : sheet.worn()) {
                canvas.fillRect(sx, sy, slot, slot, 0xFF000000 | look.stoneColour());
                if (!worn.empty() && !worn.icon().isBlank()) {
                    canvas.drawImage(Canvas.Image.of(worn.icon()), sx + 3f, sy + 3f, sx + slot - 3f, sy + slot - 3f,
                            0xFFFFFFFF, Canvas.Blend.ALPHA);
                }
                canvas.openRect(sx, sy, slot, slot, worn.empty() ? 1f : 2f, worn.empty() ? edge : gold);
                if (worn.empty()) {
                    var empty = canvas.measure(small, screens.emptyWord());
                    canvas.drawText(small, screens.emptyWord(), sx + (slot - empty.width()) / 2f,
                            sy + (slot - empty.lineHeight()) / 2f, 0x60000000 | look.boneColour());
                }
                var under = canvas.measure(small, worn.slot());
                canvas.drawText(small, worn.slot(), sx + (slot - under.width()) / 2f, sy + slot + 3f,
                        worn.empty() ? dim : bone);
                sx += slot + pad * 0.5f;
            }
        }
    }
}
