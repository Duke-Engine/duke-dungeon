package uz.dukeengine.dungeon.content;

/**
 * A death that is felt: when a {@code template} dies, {@code look} -- an EffectList, which is where a camera knock
 * lives -- is played where it fell. Drawing only.
 *
 * @param template the creature, by its template's name
 * @param look     the EffectList played
 */
public record BigDeath(String template, String look) {

    public static final BigDeath DEFAULTS = new BigDeath("", "");

    /** The client's name for this death. */
    public String moment() {
        return "died." + template;
    }
}
