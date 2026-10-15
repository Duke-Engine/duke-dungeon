package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.loot.Items;
import uz.dukeengine.dungeon.run.Belt;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;

/**
 * The belt, and a ware's card in the trade window — see docs/plan/2026-10-15-loot.md.
 *
 * <p><b>The belt.</b> The first four potion stacks of the backpack, drawn over the bottom panel with their keys, counts
 * and the cooldown; keys 1-4 are bound in {@code Main.controls} and send the {@code Drink} order.
 *
 * <p><b>The trade card.</b> The bag draws its own cards in the game's colours (E45, {@code Bag.coloured}); the trade
 * window is the game's, and draws the same lines beside the ware the pointer is on.
 *
 * <p>The window's alone, and only reading.
 */
final class ItemCard {

    /** How long a frame may go unchanged before the world counts as not being played (the bag's own figure). */
    private static final long STILL_NANOS = 500_000_000L;

    private final DungeonSettings settings;
    private final Items items;

    private int mouseX = -1;
    private int mouseY = -1;
    private int lastFrame = -1;
    private long lastFrameAt;

    ItemCard(DungeonSettings settings) {
        this.settings = settings;
        this.items = settings.items();
    }

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

    /** The belt, while the world is played. */
    void paint(Canvas canvas, Dungeon.Session match) {
        var bag = match == null ? null : Bag.bagOf(match);
        if (bag != null && live(match)) {
            belt(canvas, match, bag);
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
                tip(canvas, said(things.get(i), bag), row.x() + row.width() + 8f, row.y());
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
            // The key that eats it, as the scheme plays: 1-4, or action's 5-8.
            canvas.drawText(small, String.valueOf(settings.beltKey(i)), x + 3f, top + 1f,
                    0xFF000000 | look.torchColour());
        }
    }

    /** A card's lines: the name in its rarity's colour, the bag's coloured lines, and the difference to what is worn. */
    List<Items.Line> said(Loot item, LootBag bag) {
        var look = settings.menu();
        var out = new ArrayList<Items.Line>();
        out.add(new Items.Line(LootBag.nameOf(item), items.colourOf(item, look.torchColour())));
        if (bag.wornPlace(item.slot()) >= 0 && !settings.lootDrops().wornIn().isEmpty()) {
            out.add(new Items.Line(settings.lootDrops().wornIn() + item.slot(), look.boneColour()));
        }
        var lines = Bag.cardLines(item, settings);
        if (lines == null) {
            Bag.gives(item, settings).stream().filter(said -> !said.isEmpty())
                    .forEach(said -> out.add(new Items.Line(said, look.boneColour())));
        } else {
            out.addAll(lines);
        }
        out.addAll(Bag.compared(item, bag, settings));
        return out;
    }

    /** A card beside {@code (x, y)}, its first line big, kept on the screen. */
    void tip(Canvas canvas, List<Items.Line> lines, float x, float y) {
        var look = settings.menu();
        float slot = Math.clamp(Math.round(canvas.height() * 0.05f), 34, 72);
        float pad = Math.max(6f, slot / 6f);
        var big = Canvas.Font.of("Georgia", Math.round(slot * 0.34f)).bold(true);
        var small = Canvas.Font.of("Georgia", Math.round(slot * 0.27f));
        float across = 0f;
        float down = 0f;
        for (int i = 0; i < lines.size(); i++) {
            var measure = canvas.measure(i == 0 ? big : small, lines.get(i).text());
            across = Math.max(across, measure.width());
            down += measure.lineHeight() + (i == 0 ? 0f : 2f);
        }
        float w = across + pad * 2f;
        float h = down + pad * 2f;
        float at = Math.clamp(x, 4f, Math.max(4f, canvas.width() - w - 4f));
        float from = Math.clamp(y, 4f, Math.max(4f, canvas.height() - h - 4f));
        canvas.fillRect(at, from, w, h, 0xF0000000 | look.stoneDeepColour());
        canvas.openRect(at, from, w, h, 1.5f, 0xFF000000 | lines.getFirst().colour());
        float row = from + pad;
        for (int i = 0; i < lines.size(); i++) {
            var font = i == 0 ? big : small;
            canvas.drawText(font, lines.get(i).text(), at + pad, row, 0xFF000000 | lines.get(i).colour());
            row += canvas.measure(font, lines.get(i).text()).lineHeight() + 2f;
        }
    }
}
