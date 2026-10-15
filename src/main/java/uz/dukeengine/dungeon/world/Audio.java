package uz.dukeengine.dungeon.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the game sounds like as a whole, where no one sound has a say.
 *
 * @param voiceGapSeconds the least time between two of the hero's lines — here rather than on each
 *     cue, because what is being prevented is two voices at once, and it is no better when they are
 *     saying different things
 * @param music           which music cue plays where, each {@code "Where = cue"}: a map by its name ({@code descent}
 *     for the generated floors), a zone of a map by its name (a zone's beats its map's), or a moment — {@code menu},
 *     {@code battle}, {@code boss}, {@code won} or {@code won.<map>}, {@code dead}, {@code cutscene.<Name>}; and
 *     {@code default} for anywhere else. See {@code Soundscape}
 * @param ambience        the loop under a place, each {@code "Where = cue"}, a map or a zone as for the music; a place
 *     not named is quiet
 * @param fadeSeconds     how long one track fades out and the next one in
 * @param battleReach     how near an enemy that is fighting has to be for the battle music
 * @param bossReach       and how near one of the bosses has to be seen for the boss's
 * @param battleSeconds   how long the battle music holds after the last blow, so a lull is not a change of track
 * @param bosses          the creatures whose sight is the boss's music, beside every Boss block's
 */
public record Audio(float voiceGapSeconds, List<String> music, List<String> ambience, float fadeSeconds,
        float battleReach, float bossReach, float battleSeconds, List<String> bosses) {

    /** What a block leaves out: no music by place, which leaves the player's own choice of track playing. */
    public static final Audio DEFAULTS = new Audio(1.5f, List.of(), List.of(), 2.5f, 30f, 70f, 8f, List.of());

    public Audio {
        music = music == null ? List.of() : List.copyOf(music);
        ambience = ambience == null ? List.of() : List.copyOf(ambience);
        bosses = bosses == null ? List.of() : List.copyOf(bosses);
    }

    /** The music's places, by where, in the file's order. */
    public Map<String, String> musicByPlace() {
        return pairs(music, "Music");
    }

    /** The loops' places, by where. */
    public Map<String, String> ambienceByPlace() {
        return pairs(ambience, "Ambience");
    }

    /** The cue of the first of {@code places} the list names — the zones he stands in, then his map — or null. */
    public static String firstOf(Map<String, String> named, List<String> places) {
        for (var place : places) {
            var cue = place == null ? null : named.get(place);
            if (cue != null) {
                return cue;
            }
        }
        return null;
    }

    private static Map<String, String> pairs(List<String> written, String field) {
        var said = new LinkedHashMap<String, String>();
        for (var pair : written) {
            int at = pair.indexOf(" = ");
            if (at < 0) {
                throw new IllegalArgumentException("Audio " + field + ": \"" + pair + "\" is not \"Where = cue\"");
            }
            said.put(pair.substring(0, at).strip(), pair.substring(at + 3).strip());
        }
        return said;
    }
}
