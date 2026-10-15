package uz.dukeengine.dungeon.story;

/**
 * A knock-back from a point, as the story's {@code SHOCKWAVE} act plays it: every creature of the dungeon's side
 * standing within {@code radius} (units) of the zone's middle is thrown {@code distance} straight out from it, along an
 * arc {@code height} high over {@code frames}, and stunned {@code stunFrames} -- the hero's landing. Game-side until the
 * skill system has an outward push (E82, {@code SkillEffect.PUSH}); then the story casts that instead.
 *
 * @param landed an Effect played on each as it comes down (its dust); blank for none
 */
public record Shockwave(String name, float radius, float distance, float height, int frames, int stunFrames,
        String landed) {

    public static final Shockwave DEFAULTS = new Shockwave("", 100f, 40f, 15f, 20, 60, "");
}
