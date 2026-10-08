package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * A few lines said in turn, with no choices, and the quests they hand out as they are said.
 *
 * @param starts the quests started as it plays, in this order
 */
public record Conversation(String name, List<Line> lines, List<String> starts) {

    public static final Conversation DEFAULTS = new Conversation("", List.of(), List.of());

    public Conversation {
        lines = lines == null ? List.of() : List.copyOf(lines);
        starts = starts == null ? List.of() : List.copyOf(starts);
    }

    /** One line: who says it, by a {@link Speaker}'s name, and what. */
    public record Line(String speaker, String text) {
    }
}
