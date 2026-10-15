package uz.dukeengine.dungeon.loot;

import java.util.List;

/**
 * What a generated thing is made on: a sword, a coat of mail, a ring — its slot, the figure it moves, how high it rolls
 * and from which item level it is found. See docs/plan/2026-10-15-loot.md.
 *
 * @param name        its id, which the ids of everything made on it start with: lower case, no {@code -}
 * @param icon        the icon library's name for its picture; it is drawn framed in its rarity's colour, from
 *     {@code icons/items/loot/<icon>_<rarity>.png}
 * @param kind        the figure it moves, a {@code Stat} word: ATTACK, ARMOUR, HEALTH, MANA or ATTRIBUTE
 * @param attributes  for an ATTRIBUTE thing, the attributes it may give, one drawn: boots agility, a ring any
 * @param level       the item level it is found from
 * @param value       its figure, from and to, as found
 * @param price       what it costs common at level 1; a rarity and every level past the first raise it
 * @param weight      how often it is the one drawn among those its level allows
 */
public record ItemBase(String name, String displayName, String slot, String icon, String kind, List<String> attributes,
        int level, List<Integer> value, int price, int weight) {

    public static final ItemBase DEFAULTS = new ItemBase("", "", "", "", "", List.of(), 1, List.of(1, 1), 20, 10);

    public ItemBase {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        value = value == null ? List.of(1, 1) : List.copyOf(value);
    }
}
