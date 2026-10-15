package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * The game's credits, rolled by a story's {@code CREDITS} act at its end (the engine's {@code DukeGame.creditsRoll},
 * E49): the makers of what it is made of, grouped by kind, from {@code CREDITS.md} — see
 * {@code docs/plan/2026-10-15-act2.md}.
 *
 * @param seconds how long the whole roll takes to scroll by
 * @param music   the music cue played as it rolls; blank for whatever plays
 * @param lines   in order: a line that starts {@code "# "} is a heading, every other a name
 */
public record Credits(float seconds, String music, List<String> lines) {

    public static final Credits DEFAULTS = new Credits(40f, "", List.of());

    public Credits {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** The lines as the engine rolls them. */
    public List<uz.dukeengine.game.CreditsRoll.Line> rolled() {
        return lines.stream().map(line -> line.startsWith("# ")
                ? uz.dukeengine.game.CreditsRoll.Line.heading(line.substring(2))
                : uz.dukeengine.game.CreditsRoll.Line.name(line)).toList();
    }
}
