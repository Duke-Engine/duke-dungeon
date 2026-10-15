package uz.dukeengine.dungeon.content;

/**
 * What a critical blow adds: a creature of {@code template} (blank for every one) struck by a critical blow plays
 * {@code plays} -- an EffectList, where a camera knock lives -- on top of what a heavy blow does. Drawing only.
 *
 * @param template   the creature, or blank for all; a block naming one wins over the blank one
 * @param damageType the kind of damage (NORMAL where weapons name none)
 * @param plays      the EffectList played
 */
public record CritBlow(String template, String damageType, String plays) {

    public static final CritBlow DEFAULTS = new CritBlow("", "NORMAL", "");

    /** The client's name for a critical hurt of {@code template}. */
    public String momentFor(String template) {
        return "hurt." + template + "." + damageType.toLowerCase(java.util.Locale.ROOT) + ".crit";
    }
}
