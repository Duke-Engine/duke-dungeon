package uz.dukeengine.dungeon;

import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.loot.DropItem;
import uz.dukeengine.dungeon.loot.GroundItem;
import uz.dukeengine.dungeon.loot.ItemUse;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootBag;
import uz.dukeengine.dungeon.loot.UseItem;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.GateUpdate;
import uz.dukeengine.core.view.CommandButton;
import uz.dukeengine.core.view.WorldSnapshot;

/**
 * The hero's bag as the player handles it: its slots on a slab of stone at the right of the window, what a thing
 * gives when the pointer rests on it — in the bag or lying on the floor — and a thing taken in hand with the right
 * button and put down on the floor with the left; or, a thing that does something, taken in hand with the left and
 * used on the thing the next click is on.
 *
 * <p>Nothing here touches the world. Picking up is the click the client already sends on a thing the game names a
 * word for ({@link PartyOrders#PICK_UP}), and so is going up to the keep's gate ({@link PartyOrders#TO_THE_GATE});
 * putting down is the client's aim at the ground, whose place comes back as a {@link DropItem} order, and using a
 * thing is its aim at a thing, whose id comes back as a {@link UseItem} order — so all of them go down the road
 * every order goes, to every machine of a party.
 *
 * <p>On the window's thread, but for what is handed to the match and run on the simulation's: the rule that names
 * the pickup, and a look after every frame at what lies on the floor. Both only read, and change nothing. What the
 * pointer rests on is the window's own word ({@code DukeGame.getPointedAt}), so it is said whatever is selected.
 */
final class BagScreen implements Painter, CanvasInput {

    /** The id of the aim that puts {@code slot} down. */
    private static final String DROP = "drop:";
    /** The id of the aim that takes {@code slot} to a thing, to be used on it. */
    private static final String USE = "use:";
    /** The pointer while a thing is in hand — a Cursor block of that name. */
    private static final String HOLDING = "Drop";
    /** How long the world may stand still before the bag is taken for a menu over it, and put away. */
    private static final long STILL_NANOS = 500_000_000L;

    private final DungeonSettings settings;
    private final Duke3D duke;

    /** The match being played: the one the window opened with, or the party's that replaced it. */
    private volatile Dungeon.Session session;

    /**
     * What lies on the floor, by the id of the thing it lies in: read off the world after every frame, on the
     * simulation's thread, and handed over whole — so the pointer can say what it rests on whatever is selected.
     */
    private volatile java.util.Map<Integer, Loot> lying = java.util.Map.of();

    // ---- the window's own ----

    private int mouseX = -1;
    private int mouseY = -1;
    /** The slot in hand, or -1; and which aim put it there, so the end of an older aim does not let go of it. */
    private int held = -1;
    private int aims;
    private Dungeon.Session heldIn;
    /** Whether the thing in hand is to be used rather than put down: no word beside it about the floor. */
    private boolean using;
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

    /** Where the time is read, in nanoseconds: the window's own clock, or one a test holds still. */
    private final java.util.function.LongSupplier clock;

    BagScreen(DungeonSettings settings, Duke3D duke) {
        this(settings, duke, System::nanoTime);
    }

    BagScreen(DungeonSettings settings, Duke3D duke, java.util.function.LongSupplier clock) {
        this.settings = settings;
        this.duke = duke;
        this.clock = clock;
    }

    /**
     * The match now being played, told before it starts: a click on a thing lying on the floor is a pickup in it, a
     * click on the keep's gate is the walk up to it, a place the drop aim was given becomes the order to put the
     * thing down there, and a thing the use aim was given becomes the order to take it there.
     */
    void show(Dungeon.Session match) {
        var game = match.game();
        game.contextOrder((selection, target) -> {
            var item = target.findModule(GroundItem.class);
            if (item != null && item.getHolding() != null) {
                return PartyOrders.PICK_UP;
            }
            // And the keep's gate, which he is sent up to: see ItemErrand.
            return target.findModule(GateUpdate.class) == null ? null : PartyOrders.TO_THE_GATE;
        });
        game.onTick(ticked -> lying = lyingIn(ticked));
        game.onCommandPressed(press -> {
            int put = slotOf(DROP, press.id());
            if (put >= 0 && press.place() != null) {
                game.postCommand(PartyOrders.of(new DropItem(game.getLocalPlayerIndex(), put, press.place())));
            }
            int used = slotOf(USE, press.id());
            if (used >= 0 && press.target() >= 0) {
                game.postCommand(PartyOrders.of(new UseItem(game.getLocalPlayerIndex(), used,
                        new ObjectId(press.target()))));
            }
        });
        session = match;
    }

    /** Everything lying on the floor of {@code game}, by the id of the thing it lies in. */
    private static java.util.Map<Integer, Loot> lyingIn(uz.dukeengine.game.DukeGame game) {
        var found = new java.util.HashMap<Integer, Loot>();
        for (var thing : game.getLogic().getObjects()) {
            var item = thing.findModule(GroundItem.class);
            if (item != null && item.getHolding() != null) {
                found.put(thing.getId().value(), item.getHolding());
            }
        }
        return java.util.Map.copyOf(found);
    }

    /** The slot a drop aim's id names, or -1 for an id that is not one. */
    static int slotOf(String id) {
        return slotOf(DROP, id);
    }

    /** The slot an aim's id names after {@code kind} — a drop's or a use's — or -1 for an id that is not one. */
    private static int slotOf(String kind, String id) {
        if (id == null || !id.startsWith(kind)) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(kind.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** What a thing gives, as the pointer says it: {@code +8% Zarba}, {@code +3 Kuch} — and of a key, nothing. */
    static String bonusOf(Loot item, DungeonSettings settings) {
        var hud = settings.hud();
        return switch (item.kind()) {
            case ATTACK -> "+" + item.value() + "% " + hud.attackWord();
            case ARMOUR -> "+" + item.value() + "% " + hud.armourWord();
            case HEALTH -> "+" + item.value() + " " + hud.healthWord();
            case MANA -> "+" + item.value() + " " + hud.manaWord();
            case ATTRIBUTE -> {
                var rules = settings.attributeRules();
                int at = rules.indexOf(item.attribute());
                yield "+" + item.value() + " " + (at < 0 ? item.attribute() : rules.attributes().get(at).word());
            }
            case KEY -> ""; // it opens a gate, and gives him nothing
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
        // The floor's mission at the top, and what the heroes say over their heads -- under the bag and its cards.
        MissionScreen.paint(canvas, match, settings);
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
                if (item.level() > 1) {
                    // Which level, in the corner: II, III.
                    var mark = "I".repeat(item.level());
                    var size = canvas.measure(small(), mark);
                    canvas.drawText(small(), mark, x + slot - size.width() - 3f, y + slot - size.lineHeight() - 1f,
                            0xFF000000 | look.torchColour());
                }
            }
        }
        var inHand = held >= 0 && held < slots.size() ? slots.get(held) : null;
        if (inHand != null) {
            float size = slot * 0.7f;
            canvas.drawImage(Canvas.Image.of(inHand.icon()), mouseX + 10, mouseY + 10, mouseX + 10 + size,
                    mouseY + 10 + size, 0xD0FFFFFF, Canvas.Blend.ALPHA);
            if (!using) {
                tip(canvas, List.of(), List.of(settings.lootDrops().dropHint()), mouseX + 14, mouseY + 14 + size,
                        false);
            }
        } else if (over >= 0 && slots.get(over) != null) {
            var item = slots.get(over);
            tip(canvas, lines(item), hintsOf(item, true), slotX(over) - gap, slotY(over), true);
        } else if (!onTheBag(mouseX, mouseY) && lying.get(match.game().getPointedAt()) instanceof Loot under) {
            tip(canvas, lines(under), hintsOf(under, false), mouseX + 20, mouseY + 20, false);
        }
    }

    /** Name, and what it gives if it gives anything. */
    private List<String> lines(Loot item) {
        var said = new java.util.ArrayList<String>();
        said.add(LootBag.nameOf(item));
        var bonus = bonusOf(item, settings);
        if (!bonus.isEmpty()) {
            said.add(bonus);
        }
        var extra = extraOf(item, settings);
        if (!extra.isEmpty()) {
            said.add(extra);
        }
        return said;
    }

    /**
     * How to take it and, on its card in the bag, how to use it if it does something: the left button, see
     * {@link #click}. Lying on the floor a left click does nothing to it, so there it says only how to take it.
     */
    private List<String> hintsOf(Loot item, boolean inBag) {
        var drops = settings.lootDrops();
        if (inBag && item.use() != ItemUse.NONE) {
            return List.of(drops.takeHint(), drops.useHint());
        }
        return List.of(drops.takeHint());
    }

    /** What a thing gives beside its figure past the first level, as the pointer says it — {@code +2 Mana/s}. */
    static String extraOf(Loot item, DungeonSettings settings) {
        var drops = settings.lootDrops();
        return item.extraValue() <= 0 ? "" : switch (item.extra()) {
            case NONE -> "";
            case HEALTH_REGEN -> "+" + item.extraValue() + " " + drops.healthRegenWord();
            case MANA_REGEN -> "+" + item.extraValue() + " " + drops.manaRegenWord();
            case ATTACK_SPEED -> "+" + item.extraValue() + "% " + drops.attackSpeedWord();
        };
    }

    /**
     * A card: {@code said}, the first line a name and then what it gives, if anything, and under it {@code hints}, how
     * to handle it — a card of hints alone has its first as its heading. Beside {@code (x, y)} — to its left when
     * {@code leftOf}, as a slot's is — and kept on the screen.
     */
    private void tip(Canvas canvas, List<String> said, List<String> hints, float x, float y, boolean leftOf) {
        var lines = new java.util.ArrayList<>(said);
        lines.addAll(hints);
        var look = settings.menu();
        float across = 0f;
        float down = 0f;
        for (int i = 0; i < lines.size(); i++) {
            var font = i == 0 ? big() : small();
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
        for (int i = 0; i < lines.size(); i++) {
            var font = i == 0 ? big() : small();
            // A name, what it gives, and last how to handle it: torch, bone and the hints' grey.
            int colour = i == 0 ? look.torchColour() : i >= said.size() ? look.hintColour() : look.boneColour();
            canvas.drawText(font, lines.get(i), at + pad, line, 0xFF000000 | colour);
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

    /**
     * This machine's hero's bag in {@code match}, or null before the match has numbered its players: in the front
     * end, which this is painted over too, and for the moment the match is being built.
     */
    private static LootBag bagOf(Dungeon.Session match) {
        if (match.game().getLogic() == null) {
            return null;
        }
        try {
            var progress = match.run().progressOf(match.game().getLocalPlayerIndex());
            return progress == null ? null : progress.getLoot();
        } catch (IllegalStateException notNumberedYet) {
            return null;
        }
    }

    /** Whether the world is being played: frames coming, and not paused under a menu. */
    private boolean live(WorldSnapshot snapshot) {
        long now = clock.getAsLong();
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

    /**
     * A button on the bag stays on the bag; the right one on a thing in it takes that thing in hand to put down, and
     * the left one on a thing that does something takes it in hand to use.
     */
    private boolean click(Button button) {
        if (button.button() == Mouse.LEFT && !button.down()) {
            boolean mine = pressedHere;
            pressedHere = false;
            return mine;
        }
        if (!onTheBag(button.x(), button.y())) {
            return false;
        }
        var match = session;
        int at = slotAt(button.x(), button.y());
        var bag = match == null ? null : bagOf(match);
        var item = bag == null || at < 0 ? null : bag.slots().get(at);
        if (button.button() == Mouse.LEFT) {
            pressedHere = true;
            if (item != null && item.use() != ItemUse.NONE) {
                hold(match, at, item, USE, CommandButton.Aim.UNIT);
            }
            return true;
        }
        if (button.button() != Mouse.RIGHT || !button.down()) {
            return button.button() != Mouse.RIGHT;
        }
        if (item == null) {
            // Nothing to take. With a thing in hand, the client's own second thoughts let go of it.
            return held < 0;
        }
        hold(match, at, item, DROP, CommandButton.Aim.GROUND);
        return true;
    }

    /**
     * Take the thing in {@code at} in hand: to be put down where the next click on the ground is, or — a thing that
     * does something — taken to the thing the next click is on. The client arms the aim, and says when it is over.
     */
    private void hold(Dungeon.Session match, int at, Loot item, String kind, CommandButton.Aim aim) {
        int armed = ++aims;
        held = at;
        heldIn = match;
        using = aim == CommandButton.Aim.UNIT;
        duke.aim(new CommandButton(kind + at, item.icon(), item.name(), null, true, aim, null), 0f, HOLDING,
                outcome -> {
                    if (aims == armed) {
                        held = -1;
                    }
                });
    }
}
