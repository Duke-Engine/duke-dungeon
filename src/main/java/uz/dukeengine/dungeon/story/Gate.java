package uz.dukeengine.dungeon.story;

import uz.dukeengine.rpg.story.QuestLog;
import uz.dukeengine.rpg.story.Script;
import uz.dukeengine.rpg.story.Trigger;

import java.util.List;

/**
 * The world closed by the story (docs/plan/2026-10-15-seamless-campaign.md, section 6): on its map, while its
 * {@code If} holds, the land outside its {@code Open} zones is shut on the path grid (E84) -- a band round them no route
 * crosses -- and a hero who walks into its edge says {@code Word}. A load (or a death's checkpoint) under it starts at
 * {@code Start}. The first gate of the files that holds is the one kept, so a later act opens the world step by step
 * with gates of its own.
 *
 * @param start the zone a run laid under this gate starts in; blank for the map's own way in
 */
public record Gate(String name, String map, List<Trigger.Condition> If, List<String> open, String start,
        String word) {

    public static final Gate DEFAULTS = new Gate("", "", List.of(), List.of(), "", "");

    public Gate {
        If = If == null ? List.of() : List.copyOf(If);
        open = open == null ? List.of() : List.copyOf(open);
    }
}
