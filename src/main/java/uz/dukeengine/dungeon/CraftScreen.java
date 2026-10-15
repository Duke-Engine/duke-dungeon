package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.craft.Crafting;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Craft;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;

/**
 * The Hunar screen (K): the recipes his hero knows, a row each -- what it makes in its rarity's colour, what it takes,
 * each part green when his backpack has it and red when not, its gold and seconds -- a row he can make now lit; the
 * pointer on a row shows its thing's card (E45's colours, compared with what he wears), and a click on a lit row is the
 * {@link Craft} order, the screen shut so he is seen at work. See docs/plan/2026-10-15-crafting.md.
 *
 * <p>The window's alone and only asking: what he knows, carries and can make is read off the run as the last frame
 * left it, and the simulation judges the order again on every machine.
 */
final class CraftScreen {

    /** A row drawn this frame: where, which recipe, and whether a click makes it. */
    record Row(float x, float y, float width, float height, int recipe, boolean ready, Loot makes) {

        boolean holds(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final DungeonSettings settings;
    private final ItemCard card;
    private final List<Row> rows = new ArrayList<>();
    /** The first row shown, the wheel scrolling it. */
    private int first;
    private int mouseX = -1;
    private int mouseY = -1;

    CraftScreen(DungeonSettings settings) {
        this.settings = settings;
        this.card = new ItemCard(settings);
    }

    /** Where each row was drawn last. */
    List<Row> rows() {
        return rows;
    }

    /** What a recipe makes, as the screen shows it: a unique as it is, a base common, at {@code level}. */
    Loot sample(Crafting.Recipe recipe, int level) {
        return settings.items().craft(recipe.makes(), recipe.count(), List.of(), Math.max(level, recipe.level()), 0);
    }

    /** A part as the screen says it: its thing's name, a least rarity's word, and how many past one. */
    String said(Crafting.Part part) {
        var items = settings.items();
        var what = part.what();
        String name;
        if (what.startsWith("*") || what.startsWith("#")) {
            name = what.substring(1).replace('_', ' ');
        } else if (items.potion(what) != null) {
            name = items.potion(what).displayName();
        } else {
            var made = items.resolve(what);
            name = made == null ? what : LootBag.nameOf(made);
        }
        var rarities = items.rules().rarities();
        if (part.least() > 0 && part.least() < rarities.size()) {
            name += " (" + rarities.get(part.least()) + "+)";
        }
        return part.count() > 1 ? name + " ×" + part.count() : name;
    }

    /** The recipes this machine's hero knows, by their place in the file. */
    static List<Integer> known(Dungeon.Session match, Crafting crafting) {
        var known = new ArrayList<Integer>();
        int player = match.game().getLocalPlayerIndex();
        for (int i = 0; i < crafting.recipes().size(); i++) {
            if (match.run().knows(player, crafting.recipes().get(i))) {
                known.add(i);
            }
        }
        return known;
    }

    void paint(Canvas canvas, Dungeon.Session match) {
        rows.clear();
        var bag = Bag.bagOf(match);
        if (bag == null) {
            return;
        }
        var crafting = settings.crafting();
        var items = settings.items();
        var rules = items.rules();
        var look = settings.menu();
        int text = Math.clamp(Math.round(canvas.height() * 0.019f), 12, 22);
        var font = Canvas.Font.of("Georgia", text).wideFamily(MissionScreen.MARKS);
        var bold = font.bold(true);
        var small = Canvas.Font.of("Georgia", Math.max(10, Math.round(text * 0.8f))).wideFamily(MissionScreen.MARKS);
        float line = canvas.measure(font, "Ag").lineHeight();
        float pad = text;
        float wide = Math.min(canvas.width() - 32f, text * 46f);
        float high = Math.min(canvas.height() - 32f, text * 34f);
        var box = PlayerScreens.frame(canvas, wide, high, crafting.title(), settings.screens().closeHint(), settings);
        int bone = 0xFF000000 | look.boneColour();
        int dim = 0xA0000000 | look.boneColour();
        int gold = 0xFF000000 | look.torchColour();

        var known = known(match, crafting);
        var sheet = match.run().getSheet();
        int level = sheet == null ? 1 : Math.max(1, sheet.level());
        float rowHigh = line * 2.6f;
        float top = box.y() + pad + line * 1.6f;
        float bottom = box.y() + box.height() - pad * 1.6f - line * 1.4f;
        int fits = Math.max(1, (int) ((bottom - top) / rowHigh));
        first = Math.clamp(first, 0, Math.max(0, known.size() - fits));
        if (known.isEmpty()) {
            canvas.drawText(font, crafting.noneWord(), box.x() + pad, top, dim);
        }
        float x = box.x() + pad;
        float width = box.width() - pad * 2f;
        float y = top;
        for (int k = first; k < known.size() && k < first + fits; k++) {
            int index = known.get(k);
            var recipe = crafting.recipes().get(index);
            var has = crafting.match(recipe, bag, items);
            var made = sample(recipe, level);
            boolean ready = has.all() && match.run().goldOf(match.game().getLocalPlayerIndex()) >= recipe.gold();
            var row = new Row(x, y, width, rowHigh - 4f, index, ready, made);
            rows.add(row);
            boolean under = row.holds(mouseX, mouseY);
            canvas.fillRect(x, y, width, rowHigh - 4f, (under ? 0xF0000000 : 0xC0000000)
                    | (ready ? look.stoneLitColour() : look.stoneColour()));
            canvas.openRect(x, y, width, rowHigh - 4f, ready ? 1.5f : 1f,
                    ready ? gold : 0xFF000000 | look.stoneEdgeColour());
            float icon = rowHigh - 12f;
            if (made != null && !made.icon().isBlank()) {
                canvas.drawImage(Canvas.Image.of(made.icon()), x + 4f, y + 4f, x + 4f + icon, y + 4f + icon,
                        ready ? 0xFFFFFFFF : 0xA0FFFFFF, Canvas.Blend.ALPHA);
            }
            float tx = x + icon + 12f;
            var name = made == null ? recipe.makes() : LootBag.nameOf(made);
            if (recipe.count() > 1) {
                name += " ×" + recipe.count();
            }
            int nameColour = 0xFF000000 | (made == null ? look.boneColour()
                    : items.colourOf(made, look.boneColour()));
            canvas.drawText(bold, name, tx, y + 3f, nameColour);
            // Its chances, for a thing whose rarity is drawn.
            var chances = new StringBuilder();
            for (int r = 0; r < recipe.rarity().size() && r < rules.rarities().size(); r++) {
                int weight = recipe.rarity().get(r);
                if (weight > 0 && weight < 1000) {
                    chances.append(chances.isEmpty() ? "" : " · ").append(rules.rarities().get(r)).append(' ')
                            .append(Math.round(weight / 10f)).append('%');
                }
            }
            if (!chances.isEmpty()) {
                canvas.drawText(small, chances.toString(), tx + canvas.measure(bold, name).width() + 12f, y + 5f,
                        dim);
            }
            float px = tx;
            var parts = recipe.parts();
            for (int p = 0; p < parts.size(); p++) {
                var said = said(parts.get(p)) + (p < parts.size() - 1 ? "," : "");
                int colour = 0xFF000000 | (has.have().get(p) ? rules.betterColour() : rules.worseColour());
                canvas.drawText(small, said, px, y + line + 6f, colour);
                px += canvas.measure(small, said).width() + 8f;
            }
            var cost = (recipe.gold() > 0 ? recipe.gold() + " " + crafting.goldWord() + "   " : "")
                    + seconds(recipe.frames() > 0 ? recipe.frames() : crafting.frames()) + crafting.secondsWord();
            canvas.drawText(small, cost, x + width - canvas.measure(small, cost).width() - 8f, y + line + 6f,
                    recipe.gold() > 0 ? gold : dim);
            y += rowHigh;
        }
        int toLearn = (int) crafting.recipes().stream().filter(recipe -> !match.run()
                .knows(match.game().getLocalPlayerIndex(), recipe) && heroes(recipe, match)).count();
        if (toLearn > 0) {
            canvas.drawText(small, crafting.toLearnWord().formatted(toLearn), box.x() + pad,
                    box.y() + box.height() - pad * 0.6f - line * 1.4f, dim);
        }
        for (var row : rows) {
            if (row.holds(mouseX, mouseY) && row.makes() != null) {
                card.tip(canvas, card.said(row.makes(), bag), row.x() + row.width() * 0.55f, row.y() + row.height());
            }
        }
    }

    /** Whether a recipe is for this machine's hero at all: anybody's, or his own. */
    private static boolean heroes(Crafting.Recipe recipe, Dungeon.Session match) {
        return recipe.hero().isBlank() || recipe.hero().equals(match.run().getHeroTemplate());
    }

    private static String seconds(int frames) {
        return java.math.BigDecimal.valueOf(frames * 10L / 30L, 1).stripTrailingZeros().toPlainString();
    }

    /**
     * An event on the open screen: the pointer followed, the wheel scrolling the list, a click on a lit row the
     * {@link Craft} order. Returns whether the screen is to shut.
     */
    boolean take(CanvasInput.Event event, Dungeon.Session match) {
        switch (event) {
            case CanvasInput.Pointer pointer -> {
                mouseX = pointer.x();
                mouseY = pointer.y();
            }
            case CanvasInput.Wheel wheel -> first = Math.max(0, first + Integer.signum(wheel.notches()));
            case CanvasInput.Button button when button.down() && button.button() == CanvasInput.Mouse.LEFT -> {
                for (var row : rows) {
                    if (row.holds(button.x(), button.y()) && row.ready() && match != null) {
                        match.game().postCommand(PartyOrders.of(new Craft(match.game().getLocalPlayerIndex(),
                                row.recipe())));
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return false;
    }
}
