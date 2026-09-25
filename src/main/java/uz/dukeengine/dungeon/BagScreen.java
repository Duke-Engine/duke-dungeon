package uz.dukeengine.dungeon;

import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.loot.DropItem;
import uz.dukeengine.dungeon.loot.GroundItem;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootBag;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.game.view.CommandButton;
import uz.dukeengine.game.view.WorldSnapshot;

/**
 * The hero's bag as the player handles it: its slots on a slab of stone at the right of the window, what a thing
 * gives when the pointer rests on it — in the bag or lying on the floor — and a thing taken in hand with the right
 * button and put down on the floor with the left.
 *
 * <p>Nothing here touches the world. Picking up is the click the client already sends on a thing the game names a
 * word for ({@link PartyOrders#PICK_UP}); putting down is the client's aim at the ground, whose place comes back as
 * a {@link DropItem} order — so both go down the road every order goes, to every machine of a party.
 *
 * <p>On the window's thread, but for the two rules handed to the match, which the simulation asks as it builds a
 * frame: they only name and remember, and change nothing.
 */
final class BagScreen implements Painter, CanvasInput {

    /** The id of the aim that puts {@code slot} down. */
    private static final String DROP = "drop:";
    /** The pointer while a thing is in hand — a Cursor block of that name. */
    private static final String HOLDING = "Drop";
    /** How long the world may stand still before the bag is taken for a menu over it, and put away. */
    private static final long STILL_NANOS = 500_000_000L;

    private final DungeonSettings settings;
    private final Duke3D duke;

    /** The match being played: the one the window opened with, or the party's that replaced it. */
    private volatile Dungeon.Session session;

    /** What the thing under the pointer holds, when the last frame found one there; the simulation's to write. */
    private volatile Loot pointed;

    // ---- the window's own ----

    private int mouseX = -1;
    private int mouseY = -1;
    /** The slot in hand, or -1; and which aim put it there, so the end of an older aim does not let go of it. */
    private int held = -1;
    private int aims;
    private Dungeon.Session heldIn;
    /** Whether the left button went down on the bag, so its letting go stays with it. */
    private boolean pressedHere;

    /** Where the bag was last drawn; nothing is on the screen when {@link #shown} is false. */
    private boolean shown;
    private float left;
    private float top;
    private float wide;
    private float high;
    private float slot;
    private float gap;
    private float pad;
    private float heading;
    private int columns;

    private int lastFrame = -1;
    private long lastFrameAt;

    BagScreen(DungeonSettings settings, Duke3D duke) {
        this.settings = settings;
        this.duke = duke;
    }

    /**
     * The match now being played, told before it starts: a click on a thing lying on the floor is a pickup in it,
     * and a place the drop aim was given becomes the order to put the thing down there.
     */
    void show(Dungeon.Session match) {
        var game = match.game();
        game.contextOrder((selection, target) -> {
            var lying = target.findModule(GroundItem.class);
            var item = lying == null ? null : lying.getHolding();
            pointed = item;
            return item == null ? null : PartyOrders.PICK_UP;
        });
        game.onCommandPressed(press -> {
            int at = slotOf(press.id());
            if (at >= 0 && press.place() != null) {
                game.postCommand(PartyOrders.of(new DropItem(game.getLocalPlayerIndex(), at, press.place())));
            }
        });
        session = match;
    }

    /** The slot a drop aim's id names, or -1 for an id that is not one. */
    static int slotOf(String id) {
        if (id == null || !id.startsWith(DROP)) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(DROP.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** What a thing gives, as the pointer says it: {@code +8% Zarba}, {@code +3 Kuch}. */
    static String bonusOf(Loot item, DungeonSettings settings) {
        var hud = settings.hud();
        return "+" + item.value() + switch (item.kind()) {
            case ATTACK -> "% " + hud.attackWord();
            case ARMOUR -> "% " + hud.armourWord();
            case HEALTH -> " " + hud.healthWord();
            case MANA -> " " + hud.manaWord();
            case ATTRIBUTE -> {
                var rules = settings.attributeRules();
                int at = rules.indexOf(item.attribute());
                yield " " + (at < 0 ? item.attribute() : rules.attributes().get(at).word());
            }
        };
    }

    // ---- drawing ----

    @Override
    public void paint(Canvas canvas) {
        var match = session;
        var bag = match == null ? null : bagOf(match);
        shown = bag != null && live(match.game().getSnapshot());
        if (!shown) {
            return;
        }
        if (heldIn != match) {
            held = -1; // a match that is over took what was in hand with it
        }
        var slots = bag.slots();
        layOut(canvas, slots.size());
        var look = settings.menu();
        canvas.fillRect(left, top, wide, high, 0xE0000000 | look.stoneColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());
        canvas.drawText(small(), settings.hud().itemsWord(), left + pad, top + pad, 0xFF000000 | look.boneColour());
        int over = slotAt(mouseX, mouseY);
        for (int at = 0; at < slots.size(); at++) {
            float x = slotX(at);
            float y = slotY(at);
            canvas.fillRect(x, y, slot, slot, 0xFF000000 | look.stoneDeepColour());
            boolean lit = at == held || at == over && slots.get(at) != null;
            canvas.openRect(x, y, slot, slot, lit ? 2f : 1f,
                    0xFF000000 | (lit ? look.torchColour() : look.stoneLitColour()));
            var item = slots.get(at);
            if (item != null) {
                float inset = slot * 0.14f;
                canvas.drawImage(Canvas.Image.of(item.icon()), x + inset, y + inset, x + slot - inset,
                        y + slot - inset, at == held ? 0x55FFFFFF : 0xFFFFFFFF, Canvas.Blend.ALPHA);
            }
        }
        var inHand = held >= 0 && held < slots.size() ? slots.get(held) : null;
        if (inHand != null) {
            float size = slot * 0.7f;
            canvas.drawImage(Canvas.Image.of(inHand.icon()), mouseX + 10, mouseY + 10, mouseX + 10 + size,
                    mouseY + 10 + size, 0xD0FFFFFF, Canvas.Blend.ALPHA);
            tip(canvas, List.of(settings.lootDrops().dropHint()), mouseX + 14, mouseY + 14 + size, false);
        } else if (over >= 0 && slots.get(over) != null) {
            var item = slots.get(over);
            tip(canvas, lines(item), slotX(over) - gap, slotY(over), true);
        } else if (PartyOrders.PICK_UP.equals(match.game().getSnapshot().contextOrder()) && pointed != null) {
            tip(canvas, lines(pointed), mouseX + 20, mouseY + 20, false);
        }
    }

    /** Name, what it gives, and how to take it. */
    private List<String> lines(Loot item) {
        return List.of(item.name(), bonusOf(item, settings), settings.lootDrops().takeHint());
    }

    /**
     * A card of {@code lines}: the first a name, the second what it gives, the last a hint. Beside {@code (x, y)} —
     * to its left when {@code leftOf}, as a slot's is — and kept on the screen.
     */
    private void tip(Canvas canvas, List<String> lines, float x, float y, boolean leftOf) {
        var look = settings.menu();
        var fonts = List.of(big(), small(), small());
        float across = 0f;
        float down = 0f;
        for (int i = 0; i < lines.size(); i++) {
            var font = fonts.get(Math.min(i, fonts.size() - 1));
            var measure = canvas.measure(font, lines.get(i));
            across = Math.max(across, measure.width());
            down += measure.lineHeight() + (i == 0 ? 0f : 2f);
        }
        float w = across + pad * 2f;
        float h = down + pad * 2f;
        float at = leftOf ? x - w : x;
        at = Math.clamp(at, 4f, Math.max(4f, canvas.width() - w - 4f));
        float from = Math.clamp(y, 4f, Math.max(4f, canvas.height() - h - 4f));
        canvas.fillRect(at, from, w, h, 0xF0000000 | look.stoneDeepColour());
        canvas.openRect(at, from, w, h, 1.5f, 0xFF000000 | look.stoneEdgeColour());
        float line = from + pad;
        int[] colours = {look.torchColour(), look.boneColour(), look.hintColour()};
        for (int i = 0; i < lines.size(); i++) {
            var font = fonts.get(Math.min(i, fonts.size() - 1));
            canvas.drawText(font, lines.get(i), at + pad, line, 0xFF000000 | colours[Math.min(i, colours.length - 1)]);
            line += canvas.measure(font, lines.get(i)).lineHeight() + 2f;
        }
    }

    private Canvas.Font big() {
        return Canvas.Font.of("Georgia", Math.round(slot * 0.34f)).bold(true);
    }

    private Canvas.Font small() {
        return Canvas.Font.of("Georgia", Math.round(slot * 0.27f));
    }

    /** Two columns at the right of the window, a little above its middle, sized to its height. */
    private void layOut(Canvas canvas, int count) {
        slot = Math.clamp(Math.round(canvas.height() * 0.05f), 34, 72);
        gap = Math.max(3f, slot / 10f);
        pad = Math.max(6f, slot / 6f);
        heading = Math.round(slot * 0.27f) * 1.4f;
        columns = 2;
        int rows = (count + columns - 1) / columns;
        wide = pad * 2f + columns * slot + (columns - 1) * gap;
        high = pad * 2f + heading + gap + rows * slot + (rows - 1) * gap;
        left = canvas.width() - wide - 16f;
        top = (canvas.height() - high) / 2f - canvas.height() * 0.06f;
    }

    private float slotX(int at) {
        return left + pad + (at % columns) * (slot + gap);
    }

    private float slotY(int at) {
        return top + pad + heading + gap + (at / columns) * (slot + gap);
    }

    /** The slot under {@code (x, y)}, or -1. */
    private int slotAt(int x, int y) {
        if (!shown) {
            return -1;
        }
        var match = session;
        var bag = match == null ? null : bagOf(match);
        int count = bag == null ? 0 : bag.slots().size();
        for (int at = 0; at < count; at++) {
            if (x >= slotX(at) && x < slotX(at) + slot && y >= slotY(at) && y < slotY(at) + slot) {
                return at;
            }
        }
        return -1;
    }

    private boolean onTheBag(int x, int y) {
        return shown && x >= left && x < left + wide && y >= top && y < top + high;
    }

    /** This machine's hero's bag in {@code match}, or null before the run has seated him. */
    private static LootBag bagOf(Dungeon.Session match) {
        var progress = match.run().progressOf(match.game().getLocalPlayerIndex());
        return progress == null ? null : progress.getLoot();
    }

    /** Whether the world is being played: frames coming, and not paused under a menu. */
    private boolean live(WorldSnapshot snapshot) {
        long now = System.nanoTime();
        if (snapshot.frame() != lastFrame) {
            lastFrame = snapshot.frame();
            lastFrameAt = now;
        }
        return snapshot.frame() > 0 && !snapshot.paused() && now - lastFrameAt < STILL_NANOS;
    }

    // ---- the hand ----

    @Override
    public boolean take(Event event) {
        switch (event) {
            case Pointer pointer -> {
                mouseX = pointer.x();
                mouseY = pointer.y();
                return false;
            }
            case Button button -> {
                mouseX = button.x();
                mouseY = button.y();
                return click(button);
            }
            default -> {
                return false;
            }
        }
    }

    /** A button on the bag stays on the bag; the right one on a thing in it takes that thing in hand. */
    private boolean click(Button button) {
        if (button.button() == Mouse.LEFT && !button.down()) {
            boolean mine = pressedHere;
            pressedHere = false;
            return mine;
        }
        if (!onTheBag(button.x(), button.y())) {
            return false;
        }
        if (button.button() == Mouse.LEFT) {
            pressedHere = true;
            return true;
        }
        if (button.button() != Mouse.RIGHT || !button.down()) {
            return button.button() != Mouse.RIGHT;
        }
        var match = session;
        int at = slotAt(button.x(), button.y());
        var bag = match == null ? null : bagOf(match);
        var item = bag == null || at < 0 ? null : bag.slots().get(at);
        if (item == null) {
            // Nothing to take. With a thing in hand, the client's own second thoughts let go of it.
            return held < 0;
        }
        int aim = ++aims;
        held = at;
        heldIn = match;
        duke.aim(new CommandButton(DROP + at, item.icon(), item.name(), null, true, CommandButton.Aim.GROUND, null),
                0f, HOLDING, outcome -> {
                    if (aims == aim) {
                        held = -1;
                    }
                });
        return true;
    }
}
