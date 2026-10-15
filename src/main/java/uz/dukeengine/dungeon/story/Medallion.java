package uz.dukeengine.dungeon.story;

/**
 * The queen's medallion and the turning, which takes it from him — see {@code docs/plan/2026-10-09-medallion.md}. Its
 * passive, "Jon yutish", is the engine's {@code SOUL_DRINK}: a Skill block on its LootItem, lent to whoever wears it.
 *
 * @param item           the LootItem it is; blank for a game with no medallion
 * @param soulsWord      the word for its souls, on its card and in the buff row: {@code Jonlar: 37}
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
 * @param lockedWord     what he says when he tries to take it off, drop it or put another in its place in the campaign,
 *                       where it cannot leave him (the owner, 2026-10-15)
 */
public record Medallion(String item, String soulsWord, String wisp, float wispSpeed, String cursedWord,
        float cursedRed, float cursedGreen, float cursedBlue, int cursedEaseFrames, String givenLook,
        int givenLookFrames, String turnedWord, String lockedWord) {

    public static final Medallion DEFAULTS = new Medallion("", "Jonlar", "", 4f, "CURSED", -0.3f, -0.4f, -0.25f, 60,
            "", 60, "Qahramon la'natlandi", "The medallion will not leave me");
}
