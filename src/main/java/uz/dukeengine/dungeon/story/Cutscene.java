package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * A few shots in turn, the camera the cutscene's and the screen letterboxed while they play. A shot's {@code Path} is
 * one smooth move through its keys (E85, {@code GameCamera.along}) over its Frames: each a zone's middle (blank: where
 * the camera stands as the shot begins) faced {@code Turn}, tilted {@code Pitch} degrees down and {@code Zoom} back --
 * each, left out, the key's before it.
 */
public record Cutscene(String name, List<Shot> shots) {

    public static final Cutscene DEFAULTS = new Cutscene("", List.of());

    public Cutscene {
        shots = shots == null ? List.of() : List.copyOf(shots);
    }

    /**
     * One shot: the camera flown to a zone's middle over {@code frames}, a conversation said, or both, and then held
     * for {@code hold} frames. Over the same frames it may turn to face {@code turn} degrees (0 north, as play's), tilt
     * to look down {@code pitch} degrees and stand {@code zoom} units back — each left as it is when not given — all
     * eased as {@code ease} says (E41).
     */
    public record Shot(String zone, int frames, int hold, String says, float turn, float pitch, float zoom,
            uz.dukeengine.game.GameCamera.Ease ease, List<Key> path) {

        public static final Shot DEFAULTS = new Shot("", 0, 0, "", Float.NaN, Float.NaN, Float.NaN,
                uz.dukeengine.game.GameCamera.Ease.IN_OUT, List.of());

        public Shot {
            path = path == null ? List.of() : List.copyOf(path);
        }

        /** Whether it turns, tilts or draws the camera in or back, or flies a path. */
        public boolean framed() {
            return !Float.isNaN(turn) || !Float.isNaN(pitch) || !Float.isNaN(zoom) || !path.isEmpty();
        }

        /** How long the shot lasts, in frames. */
        public int length() {
            return Math.max(1, (zone.isBlank() && !framed() ? 0 : frames) + hold);
        }
    }

    /** One key of a shot's camera path. */
    public record Key(String zone, float turn, float pitch, float zoom) {

        public static final Key DEFAULTS = new Key("", Float.NaN, Float.NaN, Float.NaN);
    }
}
