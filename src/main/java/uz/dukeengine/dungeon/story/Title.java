package uz.dukeengine.dungeon.story;

/**
 * A title card a story shows with its {@code TITLE} act — a chapter's name, an act's end — large in the middle of a
 * darkened screen, faded in, held and faded out, in seconds (the engine's {@code DukeGame.titleCard}, E42). Drawn only.
 *
 * @param subtext the smaller line under it, or blank
 * @param small   drawn small at the top of the screen instead, the game going on under it (the owner, 2026-10-20: a
 *                chapter opened in the open world is news, not an interruption)
 */
public record Title(String name, String text, String subtext, float fadeIn, float hold, float fadeOut,
        boolean small) {

    public static final Title DEFAULTS = new Title("", "", "", 0.8f, 2.5f, 0.8f, false);
}
