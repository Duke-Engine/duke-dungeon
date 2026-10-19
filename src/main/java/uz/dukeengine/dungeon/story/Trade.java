package uz.dukeengine.dungeon.story;

/**
 * Gold and trading: what a kill pays, what a thing sells for, how often people stroll, and every word the purse and
 * the trade window say — see {@code docs/plan/2026-10-08-people-and-trade.md}.
 *
 * @param goldPerKill  what every kill of the dungeon's pays the killer
 * @param goldPerLevel and more for each of its levels
 * @param sellPercent  what a thing sells for, in percent of its price
 * @param basePrice    the price of a thing no merchant stocks
 * @param wanderFrames how often a wandering person picks somewhere new to stroll to
 * @param gainWord     the gold a kill paid, floated over the corpse; {@code %d} the gold
 * @param goldIcon     the coins drawn before the purse, a path from the resource root
 * @param bossGold     what a boss pays, whatever its level: a floor's, or a story's Boss
 */
public record Trade(int goldPerKill, int goldPerLevel, int sellPercent, int basePrice, int wanderFrames,
        String goldWord, String gainWord, String stockWord, String bagWord, String closeHint, String noGoldWord,
        String fullWord, String soldOutWord, String cantSellWord, String goldIcon, int bossGold) {

    public static final Trade DEFAULTS = new Trade(4, 2, 40, 30, 150, "Oltin", "+%d oltin", "Sotiladi",
            "Xaltangiz", "Esc — yopish", "Oltin yetmaydi", "Xalta to'la", "Qolmadi", "Buni sotib bo'lmaydi",
            "icons/items/gold.png", 10);

    /** What a kill of a monster at {@code level} pays; a boss, {@code bossGold} and no more. */
    public int goldFor(int level, boolean boss) {
        if (boss) {
            return Math.max(0, bossGold);
        }
        return Math.max(0, goldPerKill + goldPerLevel * Math.max(1, level));
    }
}
