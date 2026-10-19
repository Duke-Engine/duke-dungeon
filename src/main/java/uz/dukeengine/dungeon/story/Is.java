package uz.dukeengine.dungeon.story;

import uz.dukeengine.rpg.story.QuestLog;
import uz.dukeengine.rpg.story.Script;
import uz.dukeengine.rpg.story.Trigger;

import java.util.Set;

/**
 * What a trigger's condition asks, as this game's files write it: the engine's flags (E22) and the game's own, which it
 * judges itself ({@code script.judgedBy}). Of a party, any hero holding or wearing it will do.
 */
public final class Is {

    /** The flag is set. */
    public static final String FLAG_SET = "FLAG_SET";
    /** The flag is not set. */
    public static final String FLAG_UNSET = "FLAG_UNSET";
    /** A hero carries the LootItem, worn or in his backpack. */
    public static final String HOLDS = "HOLDS";
    /** A hero wears the LootItem. */
    public static final String WEARS = "WEARS";
    /** No hero carries the LootItem anywhere: HOLDS turned round. */
    public static final String LACKS = "LACKS";
    /** The quest is going. */
    public static final String QUEST_ACTIVE = "QUEST_ACTIVE";
    /** The quest is finished. */
    public static final String QUEST_DONE = "QUEST_DONE";
    /** One of the Monster stands alive on the floor. */
    public static final String ALIVE = "ALIVE";
    /** None of the Monster stands alive on the floor. */
    public static final String DEAD = "DEAD";

    /** Every word a condition may ask: a file that writes another is refused as it loads. */
    public static final Set<String> ALL = Set.of(FLAG_SET, FLAG_UNSET, HOLDS, WEARS, LACKS, QUEST_ACTIVE, QUEST_DONE,
            ALIVE, DEAD);

    private Is() {
    }
}
