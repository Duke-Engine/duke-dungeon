package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * When something happens, do a list of things, in order — see {@code docs/plan/2026-10-08-story-scripting.md}.
 *
 * @param map     the map it belongs to, by its name; blank for every floor
 * @param target  what of: a zone, a Monster, a quest, a conversation, a trigger, a flag — by {@link #when}
 * @param step    for {@link When#STEP_DONE}, which step, 1 its first
 * @param delay   frames after the event before it acts
 * @param repeat  whether it fires every time, rather than once
 */
public record Trigger(String name, String map, When when, String target, int step, int delay, boolean repeat,
        List<Action> actions) {

    public static final Trigger DEFAULTS = new Trigger("", "", null, "", 0, 0, false, List.of());

    public Trigger {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /** What a trigger waits for. */
    public enum When {
        MAP_START, ZONE, DIED, QUEST_STARTED, QUEST_DONE, STEP_DONE, HEARD, AFTER, FLAG
    }

    /** What a trigger does. */
    public enum Act {
        SAY, START_QUEST, FINISH_QUEST, SPAWN, WALK, OPEN_GATE, GIVE, TAKE, FLAG, MAP, CUTSCENE,
        /** The turning: the medallion leaves the hero for the Npc named, his look darkens, he goes to the monsters. */
        CURSE
    }

    /**
     * One thing done.
     *
     * @param target what: a conversation, quest, Monster, Npc, LootItem, flag, map or cutscene — by {@link #act}
     * @param count  how many, for SPAWN
     * @param zone   where, for SPAWN and WALK
     */
    public record Action(Act act, String target, int count, String zone) {

        public static final Action DEFAULTS = new Action(null, "", 1, "");
    }
}
