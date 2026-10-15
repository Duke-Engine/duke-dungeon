package uz.dukeengine.dungeon.loot;

/**
 * A draught: drunk from the belt, keys 1-4, it gives back a share of what he can hold. A stack of them is one thing in
 * the backpack, its {@code Value} how many. See docs/plan/2026-10-15-loot.md.
 *
 * @param heal  percent of his most health it gives back
 * @param mana  and of his most mana
 * @param level the item level it drops from
 * @param price one's price
 * @param stack how many make one stack
 */
public record Potion(String name, String displayName, String icon, int heal, int mana, int level, int price, int weight,
        int stack) {

    public static final Potion DEFAULTS = new Potion("", "", "", 0, 0, 1, 20, 10, 10);
}
