package uz.dukeengine.dungeon.story;

/**
 * The queen's medallion and its passive, "Jon yutish": the soul of every enemy its wearer kills is his, and every
 * {@code soulsPerStep} of them make his blows {@code percentPerStep} harder, up to {@code maxPercent}; and the turning,
 * which takes it from him — see {@code docs/plan/2026-10-09-medallion.md}.
 *
 * @param item           the LootItem it is; blank for a game with no medallion
 * @param skillName      what its card calls the passive
 * @param soulsWord      its card's count of souls, {@code %d} the count
 * @param soulsPerStep   how many souls one step of the bonus takes
 * @param percentPerStep how much harder each step makes his blows and skills, in percent
 * @param maxPercent     the most the steps add up to
 * @param wisp           the Projectile template a soul flies to him as; blank for none seen
 * @param wispSpeed      how far it flies a frame, in world units
 * @param cursedWord     the word his look holds once cursed: the client's tint for it is darker
 * @param cursedRed      the tint's red, added to his own colour; below nothing takes away
 * @param cursedGreen    and its green
 * @param cursedBlue     and its blue
 * @param cursedEaseFrames how long the darkening takes to come over him
 * @param givenLook      the Effect played on the person it is given to, as it appears in her hand; blank for none
 * @param givenLookFrames how long that lasts
 * @param turnedWord     the banner as the act ends with him cursed
 */
public record Medallion(String item, String skillName, String soulsWord, int soulsPerStep, int percentPerStep,
        int maxPercent, String wisp, float wispSpeed, String cursedWord, float cursedRed, float cursedGreen,
        float cursedBlue, int cursedEaseFrames, String givenLook, int givenLookFrames, String turnedWord) {

    public static final Medallion DEFAULTS = new Medallion("", "Jon yutish", "Jonlar: %d", 10, 1, 20, "", 4f,
            "CURSED", -0.3f, -0.4f, -0.25f, 60, "", 60, "Qahramon la'natlandi");

    /** What {@code souls} add to his blows: a step for every {@link #soulsPerStep}, never past the cap. */
    public int percentFor(int souls) {
        return soulsPerStep < 1 ? 0 : Math.min(maxPercent, souls / soulsPerStep * percentPerStep);
    }
}
