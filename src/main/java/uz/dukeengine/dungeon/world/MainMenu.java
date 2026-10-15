package uz.dukeengine.dungeon.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The front menu and the client's fixed menu words, in the game's own: see {@code data/world/menu.duke} and
 * {@code docs/plan/2026-09-30-main-menu.md}.
 *
 * @param title     the game's name: window title and the heading over the front menu (a stand-in; the owner's call)
 * @param side      where the menus' rows stand: {@code LEFT}, {@code RIGHT} or {@code MIDDLE}
 * @param margin    how far in from that edge, in pixels
 * @param words     every fixed word of the client's menus, each {@code "English = said"} (see {@code Shell.word})
 * @param sceneBattle the Battle block the live scene behind the menu is: the campaign's opening battle, on its map at
 *                  its spot (docs/plan/2026-10-15-seamless-campaign.md, section 3)
 * @param scenePitch how steeply the scene's camera looks down, in degrees above the ground; 0 leaves the client's
 * @param sceneZoom  how far back it stands, in world units; 0 leaves the client's
 * @param sceneShift how many cells left of the fight it looks, so the fight stands right of a menu at the left
 */
public record MainMenu(String title, String subtitle, String settingsWord, String quitWord, String side,
        float margin, String creditsWord, String creditsBlurb, String creditsTitle, String creditsHint,
        List<String> words, String sceneBattle, float scenePitch, float sceneZoom, int sceneShift) {

    /** What a block leaves out: the client's English, in the middle, and no scene. */
    public static final MainMenu DEFAULTS = new MainMenu("Last Hero", "", "Settings", "Quit", "MIDDLE", 0f, "Credits",
            "Who made what this game is drawn with", "CREDITS", "ESC back", List.of(), "", 0f, 0f, 0);

    public MainMenu {
        words = words == null ? List.of() : List.copyOf(words);
        sceneBattle = sceneBattle == null ? "" : sceneBattle;
    }

    /** The words by the English they replace. */
    public Map<String, String> said() {
        var said = new LinkedHashMap<String, String>();
        for (var pair : words) {
            int at = pair.indexOf(" = ");
            if (at < 0) {
                throw new IllegalArgumentException("MainMenu Words: \"" + pair + "\" is not \"English = said\"");
            }
            said.put(pair.substring(0, at), pair.substring(at + 3));
        }
        return said;
    }
}
