package uz.dukeengine.dungeon.world;

import java.util.List;
import uz.dukeengine.core.data.Clip;

/**
 * A hero's attack combinations, as his {@code Combos} block in his data file writes them: a graph of steps, each the
 * swing of one clip, and which step a light press (the left button) or a heavy one (the right) leads to from it. A
 * chain is a path through it -- light, light, light; light, light, heavy; light, heavy; heavy, heavy.
 *
 * <p>All times are frames of the simulation's thirty a second, counted from the frame a step begins (0).
 *
 * @param hero      the creature template these combinations are the hero's of
 * @param light     the step a light press begins a chain with, when he is not mid-chain
 * @param heavy     the step a heavy press begins a chain with
 * @param aimArc    degrees, counted both sides together, round his facing in which a swing's start looks for the
 *     enemy to turn to: soft aim, never a lock
 * @param aimRange  how far from his surface that look reaches
 * @param hitArc    degrees, counted both sides together, round his facing in which a step's blow strikes
 * @param steps     every step, by name
 */
public record Combos(String hero, String light, String heavy, float aimArc, float aimRange, float hitArc,
        List<Step> steps) {

    /**
     * One swing.
     *
     * @param name          what the other steps and the chain's starts call it
     * @param clip          the clip it plays, one of his model's
     * @param damagePercent what the blow deals, as a share of his weapon's blow: 100 is the whole of it
     * @param hitFrame      the frame the blow lands, where the clip's blade comes down
     * @param windowOpen    the first frame a press chains onward (a press made earlier is kept until then)
     * @param windowClose   the last frame a press chains onward; after it the chain is over, and the swing runs on
     * @param frames        how long the step holds him, whole: the clip's length
     * @param lunge         how far forward it carries him, units, over the frames up to the blow; 0 for none
     * @param light         the step a light press in the window leads to, or empty for none
     * @param heavy         the step a heavy press in the window leads to, or empty for none
     * @param staggerFrames how long the blow stuns whom it strikes; 0 for a blow that does not
     */
    public record Step(String name, @Clip String clip, int damagePercent, int hitFrame, int windowOpen,
            int windowClose, int frames, float lunge, String light, String heavy, int staggerFrames) {

        public Step {
            light = light == null ? "" : light;
            heavy = heavy == null ? "" : heavy;
        }

        /** The step a press of this weight leads to from here, by name: empty for none. */
        public String after(boolean heavyPress) {
            return heavyPress ? heavy : light;
        }
    }

    /** What a hero with no combinations has: no steps, so a press begins nothing. */
    public static final Combos NONE = new Combos("", "", "", 0f, 0f, 0f, List.of());

    public Combos {
        hero = hero == null ? "" : hero;
        light = light == null ? "" : light;
        heavy = heavy == null ? "" : heavy;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** The step called {@code name}, or null. */
    public Step step(String name) {
        for (var step : steps) {
            if (step.name().equals(name)) {
                return step;
            }
        }
        return null;
    }

    /** The step a press of this weight begins a chain with. */
    public Step first(boolean heavyPress) {
        return step(heavyPress ? heavy : light);
    }
}
