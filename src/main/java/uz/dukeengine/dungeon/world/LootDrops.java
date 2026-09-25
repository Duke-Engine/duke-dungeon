package uz.dukeengine.dungeon.world;

/**
 * When a floor gives something up, how it is picked up, and what a hero carries it in.
 *
 * @param template             the creature a dropped item becomes; empty means nothing is ever
 *     dropped
 * @param dropPercent          the chance a monster drops something, out of a hundred
 * @param bossDropPercent      and a boss
 * @param pickupRange          how close he has to walk before a thing changes hands, picked up or put down
 * @param valuePercentPerDepth how much more an item is worth each floor down
 * @param noteFrames           how long the panel says what he just found, in logic frames
 * @param slots                how many things his bag holds
 * @param fullWord             what the panel says when he was sent for something and has no room for it
 * @param takeHint             under a thing the pointer is on: how to take it
 * @param dropHint             beside the pointer while a thing is in his hand: how to put it down
 */
public record LootDrops(String template, int dropPercent, int bossDropPercent, float pickupRange,
        int valuePercentPerDepth, int noteFrames, int slots, String fullWord, String takeHint, String dropHint) {

    /** What a block leaves out. */
    public static final LootDrops DEFAULTS = new LootDrops("", 20, 100, 14f, 20, 90, 6, "The bag is full",
            "Right button: take", "Left button: put it down");
}
