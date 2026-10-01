package uz.dukeengine.dungeon.world;

import java.util.List;
import uz.dukeengine.core.content.Effect;
import uz.dukeengine.core.data.Link;

/**
 * How the creatures of every kind fight and move, where no one kind's block has a say.
 *
 * @param closeDistance      how close a fighter walks before stopping to let its weapon work —
 *     shorter than any weapon's reach on purpose
 * @param wayAheadProbe      how far ahead a walking thing looks for a body; see {@code WayAhead}
 * @param retreatTurnDegrees how much further aside each try turns when a monster that keeps its
 *     distance backs away and straight back is stone
 * @param retreatTurns       and how many tries it makes each side of straight back before it is
 *     cornered
 * @param summonTurnDegrees  how much further aside each try at a spot for what a monster calls up;
 *     see {@code Summoning}
 * @param summonTurns        and how many tries each way before it has run out of spots
 * @param arrowTemplate      the creature an archer's shot becomes once it is in the air
 * @param arrowSpeed         how fast it travels, in world units per second
 * @param arrowMuzzleOffset  how far in front of an archer his arrow appears — the bow, not his chest
 * @param stunLook           what a stunned creature wears while it stands dazed: an {@code Effect} played on it
 *     by whatever stunned it, lasting as long as the longest {@code StunFrames} any skill has. Blank for nothing
 * @param auraMarkLooks      what a creature an aura reaches wears under its feet, by how many kinds of aura reach it:
 *     the first {@code Effect} for one kind, the second for two, the third for three, and a count past the list's end
 *     the last. Played on it by its own book, every {@code auraMarkTickFrames}. Empty for nothing
 * @param auraMarkTickFrames how often, in logic frames, a creature's book plays its mark on it -- the beat each look
 *     above is measured two of, as an aura's {@code TickFrames} is. At least 1 while any is named
 */
public record Combat(float skeletonSenseRadius, float skeletonChaseRadius, int skeletonRepathFrames,
        float closeDistance, int heroRepathFrames, float wayAheadProbe, float retreatTurnDegrees,
        int retreatTurns, float summonTurnDegrees, int summonTurns, String arrowTemplate, float arrowSpeed,
        float arrowMuzzleOffset, @Link(Effect.class) String stunLook, @Link(Effect.class) List<String> auraMarkLooks,
        int auraMarkTickFrames) {

    /** What a block leaves out. */
    public static final Combat DEFAULTS = new Combat(90f, 150f, 10, 4f, 10, 5f, 30f, 3, 45f, 4, "Arrow",
            260f, 5f, "", List.of(), 30);
}
