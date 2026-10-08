package uz.dukeengine.dungeon.story;

import java.util.List;

/** A few shots in turn, the camera the cutscene's and the screen letterboxed while they play. */
public record Cutscene(String name, List<Shot> shots) {

    public static final Cutscene DEFAULTS = new Cutscene("", List.of());

    public Cutscene {
        shots = shots == null ? List.of() : List.copyOf(shots);
    }

    /**
     * One shot: the camera flown to a zone's middle over {@code frames}, a conversation said, or both, and then held
     * for {@code hold} frames.
     */
    public record Shot(String zone, int frames, int hold, String says) {

        public static final Shot DEFAULTS = new Shot("", 0, 0, "");

        /** How long the shot lasts, in frames. */
        public int length() {
            return Math.max(1, (zone.isBlank() ? 0 : frames) + hold);
        }
    }
}
