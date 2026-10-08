package uz.dukeengine.dungeon.run;

import java.util.List;

/**
 * The missions the window lists at its right edge, as the simulation saw them in one frame: whole and unchangeable,
 * so the window's thread reads it without a lock ({@code DungeonRun.getTracker}). Today a run has one entry, the
 * floor's main mission; the list is a list so a side mission has a place to stand when there is one.
 *
 * @param entries the missions, in the order they are listed; none while the banner has the screen
 */
public record Tracker(List<Entry> entries) {

    /** Nothing to list. */
    public static final Tracker NONE = new Tracker(List.of());

    public Tracker {
        entries = List.copyOf(entries);
    }

    /** What kind of mission it is: the floor's own, or one it offers on the side. */
    public enum Kind {
        MAIN, SIDE
    }

    /**
     * One line under a mission.
     *
     * @param text  what it says, the count in it where the step counts
     * @param done  whether it is finished and only still listed for a moment
     * @param since the logic frame it began at, or, for a done step, the frame it was finished at
     */
    public record Step(String text, boolean done, int since) {
    }

    /** One mission: its kind, its title and the steps listed under it — the current one, and any just finished. */
    public record Entry(Kind kind, String title, List<Step> steps) {

        public Entry {
            steps = List.copyOf(steps);
        }
    }

    /** The words of the first mission's current step, or blank when nothing is listed. */
    public String words() {
        return entries.stream().flatMap(entry -> entry.steps().stream()).filter(step -> !step.done())
                .map(Step::text).findFirst().orElse("");
    }

    /**
     * How lit a step that began at frame {@code since} is at frame {@code now}: 1 just begun, falling to 0 once
     * {@code glowFrames} have gone.
     */
    public static float glow(int now, int since, int glowFrames) {
        if (glowFrames <= 0) {
            return 0f;
        }
        return Math.clamp(1f - (now - since) / (float) glowFrames, 0f, 1f);
    }
}
