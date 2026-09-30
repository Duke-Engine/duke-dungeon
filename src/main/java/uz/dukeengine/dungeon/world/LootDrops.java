package uz.dukeengine.dungeon.world;

/**
 * When a floor gives something up, how it is picked up, what a hero carries it in, and how alike things join.
 *
 * @param template             the creature a dropped item becomes; empty means nothing is ever
 *     dropped
 * @param dropPercent          the chance a monster drops something, out of a hundred
 * @param bossDropPercent      and a boss
 * @param pickupRange          how close he has to walk before a thing changes hands, picked up or put down
 * @param valuePercentPerDepth how much more an item is worth each floor down
 * @param noteFrames           how long what he says is said, on the panel and in the bubble over his head — what he
 *     just found, why he left it lying, what he makes of the gate — in logic frames
 * @param slots                how many things his bag holds
 * @param fullWord             what the panel and the bubble say when he was sent for something and has no room for it
 * @param takeHint             under a thing the pointer is on: how to take it
 * @param dropHint             beside the pointer while a thing is in his hand: how to put it down
 * @param useHint              under a thing in his bag that does something — the key — after how to take it: how to
 *     use it
 * @param joinCount            how many of the same thing at the same level join into one of the next
 * @param topLevel             the highest level things join to
 * @param healthRegenWord      what a thing's health a second is called when the pointer rests on it
 * @param manaRegenWord        and its mana a second
 * @param attackSpeedWord      and its quicker blows
 * @param noUseWord            what he says when he was sent to use a thing on something it does nothing to — the key
 *     on anything but the keep's gate
 */
public record LootDrops(String template, int dropPercent, int bossDropPercent, float pickupRange,
        int valuePercentPerDepth, int noteFrames, int slots, String fullWord, String takeHint, String dropHint,
        String useHint, int joinCount, int topLevel, String healthRegenWord, String manaRegenWord,
        String attackSpeedWord, String noUseWord) {

    /** What a block leaves out. */
    public static final LootDrops DEFAULTS = new LootDrops("", 20, 100, 14f, 20, 90, 6, "The bag is full",
            "Right button: take", "Left button: put it down", "Left button: use it", 3, 3, "Health/s", "Mana/s",
            "Attack speed", "It does nothing here");
}
