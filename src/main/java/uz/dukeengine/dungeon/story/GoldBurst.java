package uz.dukeengine.dungeon.story;

/**
 * What a kill that pays looks like: coins bursting out of the corpse, and the gold taken floating up over it and
 * fading. Drawing only -- the coins are an effect played as an event, the number is drawn by the game's own painter --
 * so nothing here is part of what the simulation decides.
 *
 * @param coinLook      the Effect played at the corpse, once for every {@code goldPerBurst} paid; blank for none
 * @param goldPerBurst  how much gold one more play of it stands for
 * @param mostBursts    and the most it is played for one kill, however much is paid
 * @param floatSeconds  how long the number is up, rising and fading
 * @param holdShare     how much of that it stays solid before it starts to fade, 0 to 1
 * @param floatHeight   how far over the corpse's feet it starts, in world units
 * @param floatRise     how far it rises over its life, as a share of the window's height
 * @param textSize      how tall its letters are, as a share of the window's height
 * @param bigGold       from this much paid it is drawn bigger
 * @param bigScale      and this much bigger
 * @param colour        its colour, packed RGB
 */
public record GoldBurst(String coinLook, int goldPerBurst, int mostBursts, float floatSeconds, float holdShare,
        float floatHeight, float floatRise, float textSize, int bigGold, float bigScale, int colour) {

    public static final GoldBurst DEFAULTS = new GoldBurst("", 10, 6, 1.2f, 0.35f, 16f, 0.07f, 0.03f, 40, 1.35f,
            0xFFD447);

    /** How many times the coin look is played for {@code gold} paid: at least once, at most {@link #mostBursts}. */
    public int burstsFor(int gold) {
        if (gold <= 0) {
            return 0;
        }
        return Math.clamp((gold + goldPerBurst - 1) / Math.max(1, goldPerBurst), 1, Math.max(1, mostBursts));
    }
}
