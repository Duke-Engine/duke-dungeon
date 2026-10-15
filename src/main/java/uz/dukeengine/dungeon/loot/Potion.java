package uz.dukeengine.dungeon.loot;

/**
 * A stack's kind: a meal or a drink, eaten from the belt, keys 1-4, that gives back a share of what he can hold over
 * {@code frames} -- or, healing and filling nothing, a material such as wood, which stacks and is never eaten. A stack
 * of them is one thing in the backpack, its {@code Value} how many. The old potions are these with no frames: all at
 * once. See docs/plan/2026-10-15-loot.md and docs/plan/2026-10-15-food-and-fire.md.
 *
 * @param heal  percent of his most health it gives back
 * @param mana  and of his most mana
 * @param level the item level it drops from
 * @param price one's price
 * @param stack how many make one stack
 * @param frames how long a meal takes to give it all back; 0 at once
 */
public record Potion(String name, String displayName, String icon, int heal, int mana, int level, int price, int weight,
        int stack, int frames) {

    /** Whether it is eaten or drunk at all: what heals or fills. Wood is not. */
    public boolean eaten() {
        return heal > 0 || mana > 0;
    }

    public static final Potion DEFAULTS = new Potion("", "", "", 0, 0, 1, 20, 10, 10, 0);
}
