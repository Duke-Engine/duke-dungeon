package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * A person who trades: the Npc of that name, and what a map starts him with — each thing, its price, how many — and
 * {@code gear} more things rolled at the hero's level each time a map stocks him (docs/plan/2026-10-15-loot.md).
 */
public record Merchant(String name, List<Ware> stock, int gear) {

    public static final Merchant DEFAULTS = new Merchant("", List.of(), 0);

    public Merchant {
        stock = stock == null ? List.of() : List.copyOf(stock);
    }

    /** One thing he sells: a LootItem, its price in gold, how many — written {@code Gauntlet 80 2}. */
    public record Ware(String item, int price, int count) {

        public static Ware of(String written) {
            var words = written.strip().split("\s+");
            if (words.length != 3) {
                throw new IllegalArgumentException("'" + written + "' is a LootItem, its price, then how many");
            }
            return new Ware(words[0], Integer.parseInt(words[1]), Integer.parseInt(words[2]));
        }
    }
}
