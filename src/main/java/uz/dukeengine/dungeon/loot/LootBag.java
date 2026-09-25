package uz.dukeengine.dungeon.loot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What the hero carries: a few slots, each empty or holding one thing he picked up off the floor, and what it all
 * comes to.
 *
 * <p>Session state, beside {@link uz.dukeengine.dungeon.level.HeroProgress} and for the
 * same reasons: a floor
 * gives him a new body, and a sword he found has to survive that; a death is
 * meant to take everything, and is told rather than left to infer.
 *
 * <p>What is in it counts for as long as it is in it. He picks a thing up by being sent to it, and puts one down
 * the same way — see {@link ItemErrand} — and a full bag leaves the next thing lying where it was.
 *
 * <p>Written on the simulation thread only. The window reads {@link #slots()}, a copy taken each time the bag
 * changes, so it never sees one half-changed.
 *
 * <p>It also carries the words for the last thing he found, because something has
 * to say so and the run loop is what writes the line the panel reads.
 */
public final class LootBag {

    /** How many things a bag holds when nobody says otherwise. */
    public static final int SLOTS = 6;

    private final Loot[] slots;

    /** The slots as the window reads them: replaced whole on every change, never changed in place. */
    private volatile List<Loot> view;

    /** Counts every change, so whoever works figures out of the bag knows when to work them again. */
    private int version;

    /** The words for what he just picked up, and the frame they stop being said. */
    private String note = "";
    private int noteUntilFrame;

    public LootBag() {
        this(SLOTS);
    }

    public LootBag(int slots) {
        this.slots = new Loot[Math.max(1, slots)];
        changed();
    }

    /** Take one into the first empty slot, and remember it long enough to say so; false when there is no room. */
    public boolean take(Loot item, int frame, int noteFrames) {
        for (int at = 0; at < slots.length; at++) {
            if (slots[at] == null) {
                slots[at] = item;
                say(item.name(), frame, noteFrames);
                changed();
                return true;
            }
        }
        return false;
    }

    /** Take what is in {@code slot} out of the bag: what it was, or {@code null} for an empty or unknown slot. */
    public Loot remove(int slot) {
        if (slot < 0 || slot >= slots.length || slots[slot] == null) {
            return null;
        }
        var out = slots[slot];
        slots[slot] = null;
        changed();
        return out;
    }

    /** What is in {@code slot}, or {@code null}. */
    public Loot at(int slot) {
        return slot < 0 || slot >= slots.length ? null : slots[slot];
    }

    /** Whether the next thing picked up would have nowhere to go. */
    public boolean isFull() {
        return Arrays.stream(slots).allMatch(item -> item != null);
    }

    /** Say something on the panel for a while — what he found, or why he left it lying. */
    public void say(String words, int frame, int noteFrames) {
        note = words;
        noteUntilFrame = frame + noteFrames;
    }

    /** Everything he carries, in slot order. */
    public List<Loot> getFound() {
        var found = new ArrayList<Loot>();
        for (var item : slots) {
            if (item != null) {
                found.add(item);
            }
        }
        return List.copyOf(found);
    }

    /** Every slot in order, {@code null} where empty — from any thread. */
    public List<Loot> slots() {
        return view;
    }

    /** Goes up with every change. */
    public int version() {
        return version;
    }

    /** Forget everything: a run has ended. */
    public void clear() {
        Arrays.fill(slots, null);
        note = "";
        noteUntilFrame = 0;
        changed();
    }

    /** What to say about the last thing he picked up, or "" once it has been said. */
    public String noteAt(int frame) {
        return frame < noteUntilFrame ? note : "";
    }

    private void changed() {
        version++;
        view = Collections.unmodifiableList(Arrays.asList(slots.clone()));
    }

    // ---- what it all comes to ----

    /** Percent added to his weapon damage by everything he carries. */
    public int attackPercent() {
        return totalOf(LootKind.ATTACK);
    }

    /** Flat maximum health added by everything he carries. */
    public int health() {
        return totalOf(LootKind.HEALTH);
    }

    /** Flat maximum mana added by everything he carries. */
    public int mana() {
        return totalOf(LootKind.MANA);
    }

    /** Percent of incoming damage removed by everything he carries. */
    public int armourPercent() {
        return totalOf(LootKind.ARMOUR);
    }

    /**
     * The attributes everything he carries adds, in the order {@code rules} lists them.
     *
     * <p>An item naming an attribute the rules do not know adds nothing; the settings
     * file refuses such an item when it is read, so this is only ever a test's.
     */
    public uz.dukeengine.dungeon.level.Attributes attributes(uz.dukeengine.dungeon.level.AttributeRules rules) {
        var points = new int[rules.attributes().size()];
        for (var item : slots) {
            if (item == null || item.kind() != LootKind.ATTRIBUTE) {
                continue;
            }
            int at = rules.indexOf(item.attribute());
            if (at >= 0) {
                points[at] = Math.addExact(points[at], item.value());
            }
        }
        return uz.dukeengine.dungeon.level.Attributes.ofWhole(points);
    }

    private int totalOf(LootKind kind) {
        int total = 0;
        for (var item : slots) {
            if (item != null && item.kind() == kind) {
                total += item.value();
            }
        }
        return total;
    }
}
