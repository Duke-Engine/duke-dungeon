package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.loot.Items;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Belt;
import uz.dukeengine.dungeon.run.Drink;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;

/**
 * A thing's card in colour, and the belt — see docs/plan/2026-10-15-loot.md.
 *
 * <p><b>The card.</b> The engine's bag draws a thing's card in one colour (E45 asks for more). This paints the game's
 * over it, in the same place with the same fonts and every line the engine's has, so it covers it whole: the name in
 * its rarity's colour, the rarity and item level, the base figure, the affixes, an epic's story, and what it would
 * change against what is worn in its slot, green and red. The same card is shown for a thing lying on the floor, and
 * beside a ware of the trade window.
 *
 * <p><b>The belt.</b> The first four potion stacks of the backpack, drawn over the bottom panel with their keys, counts
 * and the cooldown; keys 1-4 send the {@link Drink} order.
 *
 * <p>The window's alone, and only reading: what lies on the floor is read after every frame, as the bag reads it.
 */
final class ItemCard {

    /** The keyboard's scan codes of 1 to 9 ({@code com.jme3.input.KeyInput}'s): 1 is 2. */
    static final int KEY_1 = 2;
    /** How long a frame may go unchanged before the world counts as not being played (the bag's own figure). */
    private static final long STILL_NANOS = 500_000_000L;

    private final DungeonSettings settings;
    private final Items items;

    private int mouseX = -1;
    private int mouseY = -1;
    private int lastFrame = -1;
    private long lastFrameAt;

    /** Everything lying on the floor of the match shown, by the id of the thing it lies in: written after each frame. */
    private static volatile Map<Integer, Loot> lying = Map.of();

    ItemCard(DungeonSettings settings) {
        this.settings = settings;
        this.items = settings.items();
    }

    /** Read after every frame of {@code game}: what lies where, for a card over a thing on the floor. */
    static void watch(uz.dukeengine.game.DukeGame game) {
        game.onTick(ticked -> {
            var found = new java.util.HashMap<Integer, Loot>();
            for (var thing : ticked.getLogic().getObjects()) {
                var item = thing.findModule(uz.dukeengine.rpg.item.GroundItem.class);
                if (item != null && item.getHolding() != null) {
                    found.put(thing.getId().value(), item.getHolding());
                }
            }
            lying = Map.copyOf(found);
        });
    }

    // ---- input ----

    /** Where the pointer is, from every event that says. Never takes the event. */
    void pointer(CanvasInput.Event event) {
        switch (event) {
            case CanvasInput.Pointer pointer -> {
                mouseX = pointer.x();
                mouseY = pointer.y();
            }
            case CanvasInput.Button button -> {
                mouseX = button.x();
                mouseY = button.y();
            }
            default -> {
            }
        }
    }

    /** Keys 1 to the belt's size, while the world is played: a draught from that stack, as this game's order. */
    boolean drinks(CanvasInput.Event event, Dungeon.Session match) {
        if (!(event instanceof CanvasInput.Key key) || key.code() < KEY_1
                || key.code() >= KEY_1 + items.rules().beltSize() || match == null
                || match.game().getLogic() == null || Bag.bagOf(match) == null) {
            return false;
        }
        if (key.down() && !key.repeat()) {
            match.game().postCommand(PartyOrders.of(new Drink(match.game().getLocalPlayerIndex(),
                    key.code() - KEY_1)));
        }
        return true;
    }

    // ---- painting ----

    /** The belt, and the card over the bag's or a thing on the floor's. */
    void paint(Canvas canvas, Dungeon.Session match) {
        var bag = match == null ? null : Bag.bagOf(match);
        if (bag == null || !live(match)) {
            return;
        }
        belt(canvas, match, bag);
        var layout = Layout.of(canvas, bag);
        int over = layout.slotAt(mouseX, mouseY, bag);
        var item = over < 0 ? null : bag.slots().get(over);
        if (item != null) {
            tip(canvas, layout, said(item, bag, over), hintsIn(bag, over, item), layout.slotX(over) - layout.gap,
                    layout.slotY(over), true);
            return;
        }
        if (!layout.onTheBag(mouseX, mouseY)) {
            var under = lying.get(match.game().getPointedAt());
            if (under != null) {
                tip(canvas, layout, said(under, bag, -1), List.of(line(settings.lootDrops().takeHint(), hint())),
                        mouseX + 20, mouseY + 20, false);
            }
        }
    }

    /** The card of the ware or bag thing the pointer is on in the trade window, beside it. */
    void trade(Canvas canvas, TradeScreen trade, Dungeon.Session match) {
        var bag = match == null ? null : Bag.bagOf(match);
        if (bag == null) {
            return;
        }
        var rows = trade.rows();
        var things = trade.things();
        for (int i = 0; i < rows.size() && i < things.size(); i++) {
            var row = rows.get(i);
            if (things.get(i) != null && row.holds(mouseX, mouseY)) {
                var layout = Layout.of(canvas, bag);
                tip(canvas, layout, said(things.get(i), bag, -1), List.of(), row.x() + row.width() + 8f, row.y(),
                        false);
                return;
            }
        }
    }

    /** Whether the world is being played, as the bag counts it: frames coming, and not paused under a menu. */
    private boolean live(Dungeon.Session match) {
        var snapshot = match.game().getSnapshot();
        long now = System.nanoTime();
        if (snapshot.frame() != lastFrame) {
            lastFrame = snapshot.frame();
            lastFrameAt = now;
        }
        return snapshot.frame() > 0 && !snapshot.paused() && now - lastFrameAt < STILL_NANOS;
    }

    /** The belt: its slots over the middle of the bottom panel, each its stack, count, key and the cooldown. */
    private void belt(Canvas canvas, Dungeon.Session match, LootBag bag) {
        int size = items.rules().beltSize();
        if (size <= 0) {
            return;
        }
        var look = settings.menu();
        float slot = Math.clamp(Math.round(canvas.height() * 0.045f), 28, 56);
        float gap = Math.max(3f, slot / 10f);
        float wide = size * slot + (size - 1) * gap;
        var panel = canvas.bottomPanel();
        float floor = panel == null ? canvas.height() - 8f : panel.y() - 8f;
        float left = (canvas.width() - wide) / 2f;
        float top = floor - slot;
        var places = Belt.places(bag, items, size);
        int frame = match.game().getSnapshot().frame();
        float waiting = Math.clamp((match.run().beltReadyOf(match.game().getLocalPlayerIndex()) - frame)
                / (float) Math.max(1, items.rules().beltFrames()), 0f, 1f);
        var small = Canvas.Font.of("Georgia", Math.max(10, Math.round(slot * 0.3f))).bold(true);
        for (int i = 0; i < size; i++) {
            float x = left + i * (slot + gap);
            canvas.fillRect(x, top, slot, slot, 0xC0000000 | look.stoneDeepColour());
            canvas.openRect(x, top, slot, slot, 1f, 0xFF000000 | look.stoneLitColour());
            if (i < places.size()) {
                var stack = bag.at(places.get(i));
                float inset = slot * 0.1f;
                canvas.drawImage(Canvas.Image.of(stack.icon()), x + inset, top + inset, x + slot - inset,
                        top + slot - inset, 0xFFFFFFFF, Canvas.Blend.ALPHA);
                var count = Integer.toString(stack.value());
                var measure = canvas.measure(small, count);
                float cx = x + slot - measure.width() - 3f;
                float cy = top + slot - measure.lineHeight() - 1f;
                canvas.drawText(small, count, cx + 1f, cy + 1f, 0xFF000000);
                canvas.drawText(small, count, cx, cy, 0xFF000000 | look.boneColour());
            }
            if (waiting > 0f) {
                canvas.fillRect(x, top, slot, slot * waiting, 0xA0000000);
            }
            var key = Integer.toString(i + 1);
            canvas.drawText(small, key, x + 3f, top + 1f, 0xFF000000 | look.torchColour());
        }
    }

    /** A card's lines over its hints: the name, rarity, slot, what it gives, and the difference to what is worn. */
    List<Items.Line> said(Loot item, LootBag bag, int place) {
        var look = settings.menu();
        var out = new ArrayList<Items.Line>();
        out.add(line(LootBag.nameOf(item), items.colourOf(item, look.torchColour())));
        var rarity = items.rarityLine(item);
        if (rarity != null) {
            out.add(rarity);
        }
        int wornPlace = bag.wornPlace(item.slot());
        if (wornPlace >= 0 && !settings.lootDrops().wornIn().isEmpty()) {
            out.add(line(settings.lootDrops().wornIn() + item.slot(), look.boneColour()));
        }
        var lines = items.lines(item);
        if (lines == null) {
            for (var said : Bag.gives(item, settings)) {
                if (!said.isEmpty()) {
                    out.add(line(said, look.boneColour()));
                }
            }
        } else {
            out.addAll(lines);
        }
        var worn = wornPlace < 0 || place == wornPlace ? null : bag.at(wornPlace);
        if (worn != null) {
            var change = items.compare(item, worn);
            if (!change.isEmpty()) {
                out.add(line(items.rules().compareWord(), look.hintColour()));
                out.addAll(change);
            }
        }
        return out;
    }

    /** On its card in the bag, the hints the bag's card has — and for a potion, the keys that drink it. */
    private List<Items.Line> hintsIn(LootBag bag, int place, Loot item) {
        var drops = settings.lootDrops();
        var hints = new ArrayList<String>();
        hints.add(drops.takeHint());
        if (item.use() != ItemUse.NONE) {
            hints.add(drops.useHint());
        } else if (place >= bag.backpackSize() && bag.wears()) {
            hints.add(drops.offHint());
        } else if (bag.wornPlace(item.slot()) >= 0) {
            hints.add(drops.wearHint());
        }
        if (items.potionOf(item) != null) {
            hints.add(items.rules().drinkHint());
        }
        return hints.stream().filter(hint -> !hint.isEmpty()).map(hint -> line(hint, hint())).toList();
    }

    private int hint() {
        return settings.menu().hintColour();
    }

    private static Items.Line line(String text, int colour) {
        return new Items.Line(text, colour);
    }

    /**
     * A card as the bag draws one: {@code said}, the first line big, then {@code hints}; beside {@code (x, y)}, to its
     * left when {@code leftOf}, kept on the screen — so drawn where the bag's is, over it.
     */
    private void tip(Canvas canvas, Layout layout, List<Items.Line> said, List<Items.Line> hints, float x, float y,
            boolean leftOf) {
        var look = settings.menu();
        var lines = new ArrayList<>(said);
        lines.addAll(hints);
        float across = 0f;
        float down = 0f;
        for (int i = 0; i < lines.size(); i++) {
            var measure = canvas.measure(i == 0 ? layout.big() : layout.small(), lines.get(i).text());
            across = Math.max(across, measure.width());
            down += measure.lineHeight() + (i == 0 ? 0f : 2f);
        }
        float pad = layout.pad;
        float w = across + pad * 2f;
        float h = down + pad * 2f;
        float at = Math.clamp(leftOf ? x - w : x, 4f, Math.max(4f, canvas.width() - w - 4f));
        float from = Math.clamp(y, 4f, Math.max(4f, canvas.height() - h - 4f));
        canvas.fillRect(at, from, w, h, 0xFF000000 | look.stoneDeepColour());
        int edge = said.isEmpty() ? look.stoneEdgeColour() : said.getFirst().colour();
        canvas.openRect(at, from, w, h, 1.5f, 0xFF000000 | edge);
        float row = from + pad;
        for (int i = 0; i < lines.size(); i++) {
            var font = i == 0 ? layout.big() : layout.small();
            canvas.drawText(font, lines.get(i).text(), at + pad, row, 0xFF000000 | lines.get(i).colour());
            row += canvas.measure(font, lines.get(i).text()).lineHeight() + 2f;
        }
    }

    /** The bag's layout, worked out as the bag works it out: its slots' places and its card's fonts. */
    record Layout(float slot, float gap, float pad, float heading, int backpack, boolean wornColumn, float left,
            float top, float wide, float high) {

        static Layout of(Canvas canvas, LootBag bag) {
            float slot = Math.clamp(Math.round(canvas.height() * 0.05f), 34, 72);
            float gap = Math.max(3f, slot / 10f);
            float pad = Math.max(6f, slot / 6f);
            float heading = Math.round(slot * 0.27f) * 1.4f;
            int columns = 2;
            int backpack = bag.backpackSize();
            boolean worn = bag.wears();
            int rows = Math.max((backpack + columns - 1) / columns, worn ? bag.wornSlots().size() : 0);
            float wide = pad * 2f + columns * slot + (columns - 1) * gap + (worn ? slot + gap * 3f : 0f);
            float high = pad * 2f + heading + gap + rows * slot + (rows - 1) * gap;
            float left = canvas.width() - wide - 16f;
            float top = (canvas.height() - high) / 2f - canvas.height() * 0.06f;
            return new Layout(slot, gap, pad, heading, backpack, worn, left, top, wide, high);
        }

        float slotX(int at) {
            if (at >= backpack) {
                return left + pad;
            }
            return left + pad + (wornColumn ? slot + gap * 3f : 0f) + (at % 2) * (slot + gap);
        }

        float slotY(int at) {
            int row = at >= backpack ? at - backpack : at / 2;
            return top + pad + heading + gap + row * (slot + gap);
        }

        int slotAt(int x, int y, LootBag bag) {
            for (int at = 0; at < bag.slots().size(); at++) {
                if (x >= slotX(at) && x < slotX(at) + slot && y >= slotY(at) && y < slotY(at) + slot) {
                    return at;
                }
            }
            return -1;
        }

        boolean onTheBag(int x, int y) {
            return x >= left && x < left + wide && y >= top && y < top + high;
        }

        Canvas.Font big() {
            return Canvas.Font.of("Georgia", Math.round(slot * 0.34f)).bold(true);
        }

        Canvas.Font small() {
            return Canvas.Font.of("Georgia", Math.round(slot * 0.27f));
        }
    }
}
