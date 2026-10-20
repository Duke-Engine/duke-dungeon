package uz.dukeengine.dungeon.story;

/**
 * How strong the Black Knight stands (docs/plan/2026-10-19-open-world-act1.md): {@code least} percent of himself at
 * the least, and the rest as much as there are graves and caves left of all the world has (every Graveyard and Cave
 * block, gone or not). Laid on {@code map}, the {@code boss} has his health and his blow cut to it; and while the quest
 * {@code during} is going the tracker says it, {@code word} its words with {@code %d} the percent.
 */
public record KnightStrength(String boss, String map, int least, String word, String during) {

    public static final KnightStrength DEFAULTS = new KnightStrength("", "", 50, "", "");

    /** The percent he stands at with {@code left} of {@code all} graves and caves still standing. */
    public int percent(int left, int all) {
        if (all <= 0) {
            return 100;
        }
        return least + (100 - least) * Math.clamp(left, 0, all) / all;
    }
}
