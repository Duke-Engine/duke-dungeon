package uz.dukeengine.dungeon.world;

/**
 * The run's own pace and words: how long it holds on a death, a descent and a win, what the
 * banner says at each, who walks in when nobody has chosen, and what stands where they walk in.
 *
 * @param respawnDelayFrames how long a death holds before he stands again, in logic frames
 * @param descendDelayFrames how long the finished floor stays open after the boss falls — long
 *     enough to walk to what it left behind; a floor that closed in the same frame took the
 *     boss's own drop away with it
 * @param victoryFrames      how long a won run holds before it starts again, after the last boss falls. Longer than a
 *     death's: a death is an interruption and a win is an ending, and an ending wants to be
 *     looked at
 * @param defaultHero        which hero walks in until one is chosen
 * @param nextDepthWord      what the banner says as he goes down, the floor's number put in for
 *     {@code %d} — a word rather than a string built in Java, like every other word he reads
 * @param wayIn              the template that stands where every floor lets the heroes in, with them round it —
 *     a fountain; empty for nothing
 * @param clearWord          what the tracker says while the floor's own outside its keep still stand: the first
 *     {@code %d} how many of them are dead, the second how many there were
 * @param takeKeyWord        and while the key lies on the floor
 * @param giveKeyWord        and while a hero carries it
 * @param killBossWord       and once the gate is open — or on a floor with no keep, all it ever says
 * @param missionTitle       the floor's main mission, as the tracker titles it
 * @param missionsTab        what the folded tracker says, the key that unfolds it in it
 * @param foldKey            the letter that folds and unfolds the tracker
 * @param doneFrames         how long a step finished stays listed, ticked off, in logic frames
 * @param glowFrames         how long a step just begun glows, in logic frames
 */
public record Run(int respawnDelayFrames, int descendDelayFrames, int victoryFrames, String defaultHero,
        String diedWord, String nextDepthWord, String wayIn, String clearWord, String takeKeyWord,
        String giveKeyWord, String killBossWord, String missionTitle, String missionsTab, String foldKey,
        int doneFrames, int glowFrames) {

    /** What a block leaves out. */
    public static final Run DEFAULTS = new Run(60, 75, 150, "Rogue", "You died", "Depth %d", "",
            "Kill everything outside the keep %d/%d", "Take the key", "Give the key to the gate", "Kill the boss",
            "Boss keep", "Missions (J)", "J", 90, 20);

    /** What the banner says as he goes down to {@code depth}. */
    public String nextDepthWord(int depth) {
        return nextDepthWord.replace("%d", Integer.toString(depth));
    }

    /** What the tracker says with {@code killed} of the floor's own outside its keep dead, of {@code of}. */
    public String clearWord(int killed, int of) {
        return clearWord.replaceFirst("%d", Integer.toString(killed)).replaceFirst("%d", Integer.toString(of));
    }
}
