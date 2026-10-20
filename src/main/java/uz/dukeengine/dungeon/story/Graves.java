package uz.dukeengine.dungeon.story;

/**
 * What every graveyard shares (see {@link Graveyard}): how many of a graveyard's dead are worth killing, and what the
 * rest are. A grave's first {@code paying} risen pay as any monster does; every one after is changed -- drawn in
 * {@code changedWord}'s tint, {@code changedLevels} stronger, and worth no experience, no drop and no gold -- so a
 * graveyard cannot be farmed. See docs/plan/2026-10-19-open-world-act1.md.
 *
 * @param paying        how many of a graveyard's risen pay
 * @param changedLevels how many levels stronger a changed one stands
 * @param changedWord   the condition a changed one carries, which the client tints it by
 * @param changedRed    the tint, added to its colour; so too {@code changedGreen} and {@code changedBlue}
 * @param easeFrames    how long the tint takes to come on
 */
public record Graves(int paying, int changedLevels, String changedWord, float changedRed, float changedGreen,
        float changedBlue, int easeFrames) {

    public static final Graves DEFAULTS = new Graves(9, 3, "CHANGED", -0.35f, -0.25f, 0.15f, 20);
}
