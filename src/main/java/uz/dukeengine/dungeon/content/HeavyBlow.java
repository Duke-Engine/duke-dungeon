package uz.dukeengine.dungeon.content;

/**
 * What a blow too heavy to be ordinary looks like: from {@code majorAt} damage a creature's hurt is a MAJOR one (the
 * client's {@code hurt.<template>.<type>.major}), and it plays {@code look} -- an EffectList, which is where a camera
 * knock lives. Drawing only; nothing the simulation reads.
 *
 * <p>A blank {@code template} is every creature; a block naming one wins over it, so a boss that takes big numbers all
 * day can be given a higher bar than a skeleton.
 *
 * @param template   the creature it is for, or blank for all
 * @param damageType the kind of damage, as the weapons name it (NORMAL where they name none)
 * @param majorAt    the least a blow is worth, after armour, to count
 * @param look       the EffectList played at the creature struck
 */
public record HeavyBlow(String template, String damageType, float majorAt, String look) {

    public static final HeavyBlow DEFAULTS = new HeavyBlow("", "NORMAL", 40f, "");

    /** The client's name for a heavy hurt of {@code template}. */
    public String momentFor(String template) {
        return "hurt." + template + "." + damageType.toLowerCase(java.util.Locale.ROOT) + ".major";
    }
}
