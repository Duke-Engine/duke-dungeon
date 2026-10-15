package uz.dukeengine.dungeon.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fast travel between the watchtowers of a map: a tower is a zone whose name begins {@code towers}; it is found as
 * every place is, by the hero standing in it (saved with the places found), and from the world map -- or from its
 * keeper, who opens the map -- he goes to any found tower for {@code cost} gold, under the title card {@code card}. A
 * hero who comes back to the map from a save, or from his death, comes back at the last tower he found.
 *
 * @param towers      how a tower's zone is named: the start of its name
 * @param cost        the gold one journey costs
 * @param keeper      the Npc who keeps a tower; talked to, he opens the world map to travel from
 * @param card        the Title shown as he goes, or blank for none
 * @param noGoldWord  what the purse says when he has too little
 * @param unknownWord what is said of a tower he has not found
 * @param travelHint  the world map's line for it: the cost ({cost}) and how to go
 * @param opens       {@code "Flag = Zone"}: once the story's flag is up, the tower nearest that zone is found -- a land
 *                    freed opens its road
 */
public record Travel(String towers, int cost, String keeper, String card, String noGoldWord, String unknownWord,
        String travelHint, List<String> opens) {

    /** What a block leaves out: no towers anywhere. */
    public static final Travel DEFAULTS = new Travel("", 0, "", "", "Not enough gold", "Not found yet",
            "Click a tower to travel", List.of());

    public Travel {
        opens = opens == null ? List.of() : List.copyOf(opens);
        for (var pair : opens) {
            if (!pair.contains(" = ")) {
                throw new IllegalArgumentException("Travel Opens: \"" + pair + "\" is not \"Flag = Zone\"");
            }
        }
    }

    /** The zones whose towers are opened by the flags that are up. */
    public List<String> opened(Predicate<String> flagged) {
        var zones = new ArrayList<String>();
        for (var pair : opens) {
            int at = pair.indexOf(" = ");
            if (flagged.test(pair.substring(0, at).strip())) {
                zones.add(pair.substring(at + 3).strip());
            }
        }
        return zones;
    }
}
