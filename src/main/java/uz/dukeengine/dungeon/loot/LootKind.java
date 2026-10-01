package uz.dukeengine.dungeon.loot;

/**
 * What a thing found on the floor is worth.
 *
 * <p>The figures a hero is made of: what he hits for, how much of him there is, how
 * much of a blow gets through, what he casts out of — and the attributes those figures
 * are worked out from. Besides those, the key to the boss's keep, which is none of them.
 *
 * <p>Which items exist, what they are called and what each is worth is written in
 * {@code data/world/world.duke}. This is the part that needs Java.
 */
public enum LootKind {

    /** Adds to his attack, as a percentage of what it was. */
    ATTACK,

    /** Adds flat maximum health: room for it, not the health itself — see {@code GrowableBody.setMaxHealth}. */
    HEALTH,

    /** Takes a percentage off what reaches him. */
    ARMOUR,

    /**
     * Adds flat maximum mana: a bigger pool rather than a draught out of a flask. Room only, as a heart is — see
     * {@code SkillBook.resize}.
     */
    MANA,

    /**
     * Whole points of the attribute the item's {@code Attribute} line names — whichever
     * attribute that is, so an item for an attribute the file adds later is a block and
     * no Java.
     */
    ATTRIBUTE,

    /**
     * None of his figures: the key to the boss's keep, which opens its gate and gives him nothing. It never joins
     * with another, and it is given rather than found — see {@code run/Mission}. It belongs to the floor it was found
     * on: a floor laid takes every key out of every bag — see {@code DungeonRun}. Its {@code Use} is
     * {@code UNLOCK}, and only a key has that Use; a keep needs a key: the file is refused otherwise.
     */
    KEY
}
