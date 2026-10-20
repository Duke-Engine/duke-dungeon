package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * A battle laid on a map as one thing -- the opening battle before the capital's gate, which the menu's backdrop and
 * the story's {@code BATTLE} act both lay, so the two are the same armies on the same spot (see
 * {@code docs/plan/2026-10-15-seamless-campaign.md}).
 *
 * @param kingdom    the kinds on the heroes' side, one each, laid along {@code kingdomZone}
 * @param enemy      the kinds on the dungeon's side, one each, laid in {@code enemyZone}; each is called on at the
 *                   kingdom's line but those a Boss block names, who stay behind their army
 * @param leader     the commander fighting in the kingdom's line in the backdrop (the story places its own); blank for none
 * @param ritualist  the Monster the ritualists are, on the neutral side: nobody's enemy, so nothing targets or hurts
 *                   them and they fight nobody
 * @param ritualists how many, on a ring round {@code landing}, the first to its north and the rest evenly round,
 *                   each facing the middle
 * @param ring       how far out from the landing's middle they stand, in units
 */
public record Battle(String name, String map, List<String> kingdom, List<String> enemy, String kingdomZone,
        String enemyZone, String leader, String ritualist, int ritualists, String landing, float ring) {

    public static final Battle DEFAULTS = new Battle("", "", List.of(), List.of(), "", "", "", "", 0, "", 30f);

    public Battle {
        kingdom = kingdom == null ? List.of() : List.copyOf(kingdom);
        enemy = enemy == null ? List.of() : List.copyOf(enemy);
    }

    /** Where the {@code i}th ritualist stands, x then y, round the landing's middle at ({@code mx}, {@code my}). */
    public float[] ritualistAt(int i, float mx, float my) {
        double angle = -Math.PI / 2 + 2 * Math.PI * i / Math.max(1, ritualists);
        return new float[] {mx + ring * (float) Math.cos(angle), my + ring * (float) Math.sin(angle)};
    }
}
