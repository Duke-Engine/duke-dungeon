package uz.dukeengine.dungeon.loot;

import java.util.List;

/**
 * Something a magic or rare thing may roll beside its base's figure, as the engine's {@code Affix} (E43): any figure on
 * any slot it names. See docs/plan/2026-10-15-loot.md.
 *
 * @param stat   the figure it moves: a {@code Stat} word, ATTRIBUTE (the thing's own attribute, or one drawn among the
 *     three), LIFESTEAL (lent as a passive skill)
 * @param prefix the word a magic thing is named with, before its base: {@code O'tkir}
 * @param slots  the worn slots it may roll on
 * @param low    the least it rolls at item level 1 and at 15, drawn straight between
 * @param high   and the most
 * @param also   fixed figures it brings with it, each a Stat and how much: a critical chance's {@code CRIT_DAMAGE 30}
 */
public record Affix(String name, String stat, String prefix, List<String> slots, List<Integer> low, List<Integer> high,
        int weight, List<String> also) {

    public static final Affix DEFAULTS = new Affix("", "", "", List.of(), List.of(1, 1), List.of(1, 1), 10,
            List.of());

    public Affix {
        slots = slots == null ? List.of() : List.copyOf(slots);
        low = low == null ? List.of(1, 1) : List.copyOf(low);
        high = high == null ? List.of(1, 1) : List.copyOf(high);
        also = also == null ? List.of() : List.copyOf(also);
    }
}
