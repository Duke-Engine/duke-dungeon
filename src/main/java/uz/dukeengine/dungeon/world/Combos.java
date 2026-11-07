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
 * @param sheath    his weapons put away out of a fight and taken out for one, or null for a hero always armed
 */
public record Combos(String hero, String light, String heavy, float aimArc, float aimRange, float hitArc,
        List<Step> steps, Sheath sheath) {

    /**
     * His sword on his back and his shield slung while nothing threatens him, drawn as an enemy comes near or a button
     * is pressed, put away again after a while with neither. The client shows the pieces on his back while he holds
     * the word {@code SHEATHED}; it changes on the frame his hands close on them, or let them go.
     *
     * @param drawClip     the clip of taking them out, played over his walk
     * @param drawFrames   how long it holds him, whole
     * @param drawGrip     the frame his hands close on hilt and rim: from then on he is armed
     * @param sheatheClip  the clip of putting them away
     * @param sheatheFrames how long it holds him, whole
     * @param sheatheLetGo the frame his hands let them go on his back: from then on he is not
     * @param threatRange  how near a living enemy (from his surface) has him draw
     * @param calmFrames   how long with no enemy that near and no swing he keeps them out
     * @param calmIdle     the idle he stands at ease in, his hands empty
     * @param calmWalk     the run he goes at ease in, paced as his own
     * @param inHands      the pieces of his model held: hidden while they are on his back
     * @param onBack       the pieces of his model on his back: shown only then
     */
    public record Sheath(@Clip String drawClip, int drawFrames, int drawGrip, @Clip String sheatheClip,
            int sheatheFrames, int sheatheLetGo, float threatRange, int calmFrames, @Clip String calmIdle,
            @Clip String calmWalk, List<String> inHands, List<String> onBack) {

        public Sheath {
            inHands = inHands == null ? List.of() : List.copyOf(inHands);
            onBack = onBack == null ? List.of() : List.copyOf(onBack);
        }
    }

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
    public static final Combos NONE = new Combos("", "", "", 0f, 0f, 0f, List.of(), null);

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
