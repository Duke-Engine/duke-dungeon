package uz.dukeengine.dungeon.world;

/**
 * The run's own pace and words: how long it holds on a death, a descent and a win, what the
 * banner says at each, who walks in when nobody has chosen, and what stands where they walk in.
 *
 * @param respawnDelayFrames how long a death holds before he stands again, in logic frames
 * @param descendDelayFrames how long the finished floor stays open after the boss falls — long
 *     enough to walk to what it left behind; a floor that closed in the same frame took the
 *     boss's own drop away with it
 * @param victoryFrames      how long the word stays up after the last boss falls. Longer than a
 *     death's: a death is an interruption and a win is an ending, and an ending wants to be
 *     looked at
 * @param defaultHero        which hero walks in until one is chosen
 * @param nextDepthWord      what the banner says as he goes down, the floor's number put in for
 *     {@code %d} — a word rather than a string built in Java, like every other word he reads
 * @param wayIn              the template that stands where every floor lets the heroes in, with them round it —
 *     a fountain; empty for nothing
 */
public record Run(int respawnDelayFrames, int descendDelayFrames, int victoryFrames, String defaultHero,
        String diedWord, String wonWord, String nextDepthWord, String wayIn) {

    /** What a block leaves out. */
    public static final Run DEFAULTS = new Run(60, 75, 150, "Rogue", "You died", "You won", "Depth %d", "");

    /** What the banner says as he goes down to {@code depth}. */
    public String nextDepthWord(int depth) {
        return nextDepthWord.replace("%d", Integer.toString(depth));
    }
}
