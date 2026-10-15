package uz.dukeengine.dungeon.story;

/**
 * A title card a story shows with its {@code TITLE} act — a chapter's name, an act's end — large in the middle of a
 * darkened screen, faded in, held and faded out, in seconds (the engine's {@code DukeGame.titleCard}, E42). Drawn only.
 *
 * @param subtext the smaller line under it, or blank
 */
public record Title(String name, String text, String subtext, float fadeIn, float hold, float fadeOut) {

    public static final Title DEFAULTS = new Title("", "", "", 0.8f, 2.5f, 0.8f);
}
