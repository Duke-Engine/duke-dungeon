package uz.dukeengine.dungeon.story;

import java.util.List;

/** A person who trades: the Npc of that name, and what a map starts him with — each thing, its price, how many. */
public record Merchant(String name, List<Ware> stock) {

    public static final Merchant DEFAULTS = new Merchant("", List.of());

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
