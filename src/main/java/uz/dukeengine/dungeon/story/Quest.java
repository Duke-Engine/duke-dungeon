package uz.dukeengine.dungeon.story;

import java.util.List;
import uz.dukeengine.dungeon.run.Tracker;

/**
 * Something to do, step by step: its kind (the tracker's ◆ MAIN or ◇ SIDE), its title and its steps, done in order.
 */
public record Quest(String name, Tracker.Kind kind, String title, List<Step> steps) {

    public static final Quest DEFAULTS = new Quest("", Tracker.Kind.SIDE, "", List.of());

    public Quest {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** What a step waits for. */
    public enum Goal {
        /** A hero standing in a zone of the map. */
        REACH,
        /** {@code count} deaths of a Monster template. */
        KILL,
        /** {@code count} of a LootItem in the heroes' bags. */
        COLLECT,
        /** A hero talking to an Npc. */
        TALK,
        /** A hero who holds the item talking to an Npc; it leaves his bag. */
        GIVE
    }

    /**
     * One step: its goal, what the goal is of, and the tracker's line — {@code %d/%d} in it the count so far and the
     * count asked.
     *
     * @param target a zone (REACH), a Monster (KILL), a LootItem (COLLECT) or an Npc (TALK, GIVE)
     * @param item   what a GIVE hands over
     * @param onDone what finishing it does, or null for nothing
     */
    public record Step(Goal goal, String target, int count, String item, String text, Done onDone) {

        public static final Step DEFAULTS = new Step(null, "", 1, "", "", null);

        /** The tracker's line at {@code so far}. */
        public String said(int soFar) {
            return text.replaceFirst("%d", Integer.toString(soFar)).replaceFirst("%d", Integer.toString(count));
        }
    }

    /**
     * What finishing a step does: a conversation played, experience to every hero, an item into the bag of the hero
     * who finished it (the first hero's for a step nobody in particular finished), and another quest started.
     */
    public record Done(String says, int experience, String item, String starts) {

        public static final Done DEFAULTS = new Done("", 0, "", "");
    }
}
