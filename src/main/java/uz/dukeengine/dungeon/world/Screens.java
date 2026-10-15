package uz.dukeengine.dungeon.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's three screens -- the character sheet, the quest log and the world map -- their keys and every word they
 * say beside the words other blocks already own (the Hud's figures, the purse's gold, the medallion's souls): see
 * {@code data/world/screens.duke} and {@code docs/plan/2026-10-15-player-screens.md}.
 *
 * @param sheetKey      the letter that opens and shuts the character sheet
 * @param questsKey     and the quest log
 * @param mapKey        and the world map
 * @param sheetTitle    the sheet's heading, and its word on the HUD's key hints
 * @param questsTitle   the quest log's
 * @param mapTitle      the map's
 * @param trackerWord   the mission tracker's word beside its own key on the HUD's key hints
 * @param experienceWord what the bar under his level is called
 * @param attributesWord the heading over his attributes
 * @param figuresWord   the heading over what they and his things come to
 * @param wornWord      the heading over what he wears
 * @param emptyWord     under a worn slot with nothing in it
 * @param perSecondWord after his blows a second
 * @param activeWord    the heading over the quests going
 * @param finishedWord  the heading over the finished ones, folded under it
 * @param noQuestsWord  what the log says with nothing going
 * @param showOnMapWord the button that opens the map on a quest's target
 * @param closeHint     at the foot of every screen
 * @param mapHint       and at the map's: the wheel and the drag
 * @param places        the map's named places, each {@code "Zone = said"}: a zone of a map, by its name, and what the
 *                      map writes over it; a zone not named here is no place on the map
 * @param mostTexels    how many texels a side the world's picture is at most
 */
public record Screens(String sheetKey, String questsKey, String mapKey, String sheetTitle, String questsTitle,
        String mapTitle, String trackerWord, String experienceWord, String attributesWord, String figuresWord,
        String wornWord,
        String emptyWord, String perSecondWord, String activeWord, String finishedWord, String noQuestsWord,
        String showOnMapWord, String closeHint, String mapHint, List<String> places, int mostTexels) {

    /** What a block leaves out: the keys, and English. */
    public static final Screens DEFAULTS = new Screens("C", "L", "M", "Hero", "Quests", "Map", "Missions",
            "Experience",
            "Attributes", "Figures", "Worn", "empty", "/s", "Active", "Finished", "Nothing to do", "Show on the map",
            "Esc closes", "Wheel zooms, drag pans", List.of(), 512);

    public Screens {
        places = places == null ? List.of() : List.copyOf(places);
    }

    /** The named places by their zone, in the file's order. */
    public Map<String, String> placeNames() {
        var said = new LinkedHashMap<String, String>();
        for (var pair : places) {
            int at = pair.indexOf(" = ");
            if (at < 0) {
                throw new IllegalArgumentException("Screens Places: \"" + pair + "\" is not \"Zone = said\"");
            }
            said.put(pair.substring(0, at).strip(), pair.substring(at + 3).strip());
        }
        return said;
    }

    /** The three keys, sheet, quests, map, as their upper-case letters. */
    public List<Character> keys() {
        return List.of(letter(sheetKey), letter(questsKey), letter(mapKey));
    }

    private static char letter(String key) {
        return key == null || key.isBlank() ? ' ' : Character.toUpperCase(key.strip().charAt(0));
    }
}
