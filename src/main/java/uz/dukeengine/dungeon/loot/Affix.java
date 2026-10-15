package uz.dukeengine.dungeon.loot;

import java.util.List;

/**
 * Something a magic or rare thing may roll beside its base's figure. See docs/plan/2026-10-15-loot.md.
 *
 * <p>Its {@code Stat} decides how it reaches the hero, which is all the engine's {@code Loot} carries: one of the base's
 * own figure (ATTACK, ARMOUR, HEALTH, MANA, ATTRIBUTE) is added to the thing's figure and rolls only on a base of that
 * figure; one of HEALTH_REGEN, MANA_REGEN or ATTACK_SPEED is its extra, one a thing; LIFESTEAL is a passive it lends.
 *
 * @param prefix the word a magic thing is named with, before its base: {@code O'tkir}
 * @param slots  the worn slots it may roll on
 * @param low    the least it rolls at item level 1 and at 15, drawn straight between
 * @param high   and the most
 */
public record Affix(String name, String stat, String prefix, List<String> slots, List<Integer> low, List<Integer> high,
        int weight) {

    public static final Affix DEFAULTS = new Affix("", "", "", List.of(), List.of(1, 1), List.of(1, 1), 10);

    public Affix {
        slots = slots == null ? List.of() : List.copyOf(slots);
        low = low == null ? List.of(1, 1) : List.copyOf(low);
        high = high == null ? List.of(1, 1) : List.copyOf(high);
    }
}
