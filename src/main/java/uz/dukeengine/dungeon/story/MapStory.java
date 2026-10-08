package uz.dukeengine.dungeon.story;

import java.util.List;
import uz.dukeengine.dungeon.map.StaticMap;

/**
 * What a map brings to the story: the people standing on it, its named places, the things lying on its floor, and
 * the quests it starts with — each in the order the file writes it.
 */
public record MapStory(List<StaticMap.Placed> npcs, List<StaticMap.Zone> zones, List<StaticMap.Placed> items,
        List<String> quests) {

    /** A map with none of it: every floor of the descent, and every map drawn before there were people. */
    public static final MapStory NONE = new MapStory(List.of(), List.of(), List.of(), List.of());

    public MapStory {
        npcs = List.copyOf(npcs);
        zones = List.copyOf(zones);
        items = List.copyOf(items);
        quests = List.copyOf(quests);
    }
}
