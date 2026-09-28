package uz.dukeengine.dungeon.loot;

/**
 * What a thing gives beside its figure once three of it have been joined: the second level begins it, and every
 * level after is worth three of the one before, this included.
 */
public enum LootExtra {

    /** Nothing beside its figure, at any level. */
    NONE,

    /** Whole points of health a second, on top of what comes back on its own. */
    HEALTH_REGEN,

    /** Whole points of mana a second, on top of what comes back on its own. */
    MANA_REGEN,

    /** Percent faster between blows: every wait of his weapon's is shortened by it. */
    ATTACK_SPEED
}
