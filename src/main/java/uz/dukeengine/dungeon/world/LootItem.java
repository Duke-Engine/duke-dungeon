package uz.dukeengine.dungeon.world;

import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootExtra;
import uz.dukeengine.dungeon.loot.LootKind;

/**
 * One thing that can be found on a floor. A new one is a block and no Java.
 *
 * @param name        its id, which nothing but the file and the game read
 * @param displayName what the panel calls it; its name when the block gives none
 * @param attribute   which attribute a {@code Kind = ATTRIBUTE} item gives, by the name a hero's
 *     block uses for it
 * @param extra       what it gives beside its figure once three of it are joined into the second level
 * @param extraValue  how much of that the second level brings; every level after is three of the one before
 */
public record LootItem(String name, String displayName, String icon, LootKind kind, int value, int weight,
        int minDepth, String attribute, LootExtra extra, int extraValue) {

    /** What a block leaves out. */
    public static final LootItem DEFAULTS = new LootItem("", "", "", LootKind.ATTACK, 0, 10, 1, "", LootExtra.NONE,
            0);

    /** The thing as it is found: at the first level, with its extra still to come. */
    public Loot loot() {
        return new Loot(name, displayName == null || displayName.isBlank() ? name : displayName, icon, kind, value,
                weight, minDepth, attribute, extra, 0, extraValue, 1);
    }
}
