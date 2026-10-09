package uz.dukeengine.dungeon.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The front menu and the client's fixed menu words, in the game's own: see {@code data/world/menu.duke} and
 * {@code docs/plan/2026-09-30-main-menu.md}.
 *
 * @param side      where the menus' rows stand: {@code LEFT}, {@code RIGHT} or {@code MIDDLE}
 * @param margin    how far in from that edge, in pixels
 * @param words     every fixed word of the client's menus, each {@code "English = said"} (see {@code Shell.word})
 * @param sceneLook the theme and tone the live scene behind the menu wears, as a run names one
 * @param soldiers  the kingdom's side of the scene, one creature per entry
 * @param foes      the skeletons' side, one creature per entry
 */
public record MainMenu(String subtitle, String playWord, String settingsWord, String quitWord, String side,
        float margin, String creditsWord, String creditsBlurb, String creditsTitle, String creditsHint,
        List<String> words, String sceneLook, List<String> soldiers, List<String> foes) {

    /** What a block leaves out: the client's English, in the middle, and no scene. */
    public static final MainMenu DEFAULTS = new MainMenu("", "Play", "Settings", "Quit", "MIDDLE", 0f, "Credits",
            "Who made what this game is drawn with", "CREDITS", "ESC back", List.of(), null, List.of(), List.of());

    public MainMenu {
        words = words == null ? List.of() : List.copyOf(words);
        soldiers = soldiers == null ? List.of() : List.copyOf(soldiers);
        foes = foes == null ? List.of() : List.copyOf(foes);
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
