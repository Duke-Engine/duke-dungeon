package uz.dukeengine.dungeon.world;

import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.Stat;

/**
 * One thing that can be found on a floor. A new one is a block and no Java.
 *
 * @param name        its id, which nothing but the file and the game read
 * @param displayName what the panel calls it; its name when the block gives none
 * @param kind        which figure it moves, a word of {@link Stat}'s; a key's moves none
 * @param minTier     the floor shallower than which it is not found, its tier
 * @param attribute   which attribute a {@code Kind = ATTRIBUTE} item gives, by the name a hero's
 *     block uses for it
 * @param extra       what it gives beside its figure once three of it are joined into the second level, a word of
 *     {@link Stat}'s; blank for nothing
 * @param extraValue  how much of that the second level brings; every level after is three of the one before
 * @param use         what a left click on it in the bag does; {@code NONE}, the default, for a thing that only
 *     counts while it is carried, and {@code UNLOCK} for a key
 * @param liesAs      the template it lies on the floor as; blank for the chest everything else lies in
 * @param slot        the worn slot it goes in, one of LootDrops' WornSlots; blank for a thing only carried — a key, a
 *     quest thing, a thing to sell — which acts on nothing
 */
public record LootItem(String name, String displayName, String icon, String kind, int value, int weight,
        int minTier, String attribute, String extra, int extraValue, ItemUse use, String liesAs, String slot) {

    /** What a block leaves out. */
    public static final LootItem DEFAULTS = new LootItem("", "", "", Stat.ATTACK, 0, 10, 1, "", "",
            0, ItemUse.NONE, "", "");

    /** The thing as it is found: at the first level, with its extra still to come. */
    public Loot loot() {
        return new Loot(name, displayName == null || displayName.isBlank() ? name : displayName, icon, kind, value,
                weight, minTier, attribute, extra, 0, extraValue, 1, use, liesAs, slot);
    }
}
