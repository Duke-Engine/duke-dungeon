package uz.dukeengine.dungeon.story;

import uz.dukeengine.rpg.story.QuestLog;
import uz.dukeengine.rpg.story.Script;
import uz.dukeengine.rpg.story.Trigger;

import java.util.Set;

/**
 * What a trigger waits for, as this game's files write it: the engine's words (E22) and the game's own. A word the
 * engine does not know is told to its Script by the game ({@code script.heard(word, ...)}).
 */
public final class When {

    public static final String MAP_START = "MAP_START";
    public static final String ZONE = "ZONE";
    public static final String QUEST_STARTED = "QUEST_STARTED";
    public static final String QUEST_DONE = "QUEST_DONE";
    public static final String STEP_DONE = "STEP_DONE";
    public static final String HEARD = "HEARD";
    public static final String AFTER = "AFTER";
    public static final String FLAG = "FLAG";
    /** A Monster of the dungeon's dies. */
    public static final String DIED = "DIED";
    /** A Monster that cannot be killed is beaten — see {@link Boss#beaten}. */
    public static final String BEATEN = "BEATEN";
    /** A cutscene has played to its end, or was skipped. */
    public static final String SEEN = "SEEN";
    /** A floor is laid at the depth its Target names: a campaign's descent reaching it. */
    public static final String DEPTH = "DEPTH";
    /** The zone named holds no creature of the dungeon's standing any more, heard as one of them dies: a camp cleared. */
    public static final String CLEARED = "CLEARED";

    /** Every word a trigger may wait for: a file that writes another is refused as it loads. */
    public static final Set<String> ALL = Set.of(MAP_START, ZONE, QUEST_STARTED, QUEST_DONE, STEP_DONE, HEARD, AFTER,
            FLAG, DIED, BEATEN, SEEN, DEPTH, CLEARED);

    private When() {
    }
}
