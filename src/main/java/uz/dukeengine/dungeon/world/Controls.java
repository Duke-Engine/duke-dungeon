package uz.dukeengine.dungeon.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The two ways the hero is played -- the modern action-RPG way (WASD walks him, the mouse aims) and the classic
 * Dota/WC3 way (the mouse does everything) -- and the letters each casts his four skills on: see
 * {@code data/world/controls.duke} and {@code docs/plan/2026-10-15-modern-controls.md}.
 *
 * <p>A skill keeps its own Key (Q W E R) as its name everywhere -- the cast order, his ranks, the save, an item's
 * {@code SKILL_RANK:E} -- and the scheme only says which letter on the keyboard presses it.
 *
 * @param scheme          which of the two a player with no choice of his own plays: {@code MODERN} or {@code CLASSIC}
 * @param schemeWord      the row's word on Settings
 * @param modernWord      the modern scheme's word
 * @param classicWord     the classic scheme's
 * @param modernSkillKeys each {@code "Slot = Letter"}: the skill on Key Slot is pressed on Letter in the modern scheme
 * @param dodgeWord       what Space does in the modern scheme, on the keys page
 */
public record Controls(String scheme, String schemeWord, String modernWord, String classicWord,
        List<String> modernSkillKeys, String dodgeWord) {

    /** The two schemes. */
    public enum Scheme {
        MODERN, CLASSIC
    }

    /** What a block leaves out: classic (a world with no Controls block, a test's), the four on Q E R F, English. */
    public static final Controls DEFAULTS = new Controls("CLASSIC", "Controls", "Modern (WASD)", "Classic (mouse)",
            List.of("Q = Q", "W = E", "E = R", "R = F"), "Dodge");

    /** The Settings row's key in the player's settings file (E79): 0 modern, 1 classic, as the row lists them. */
    public static final String ROW = "game.controls";

    public Controls {
        scheme = scheme == null || scheme.isBlank() ? "CLASSIC" : scheme.strip().toUpperCase(java.util.Locale.ROOT);
        modernSkillKeys = modernSkillKeys == null ? List.of() : List.copyOf(modernSkillKeys);
        dodgeWord = dodgeWord == null ? "" : dodgeWord;
    }

    /** The file's scheme. */
    public Scheme fileScheme() {
        return Scheme.valueOf(scheme);
    }

    /** The modern letters by the skill's own key, in the file's order. */
    public Map<Character, Character> modernKeys() {
        var keys = new LinkedHashMap<Character, Character>();
        for (var pair : modernSkillKeys) {
            var sides = pair.split("=");
            if (sides.length != 2 || sides[0].strip().length() != 1 || sides[1].strip().length() != 1) {
                throw new IllegalArgumentException("Controls ModernSkillKeys: \"" + pair + "\" is not \"Slot = Letter\"");
            }
            keys.put(Character.toUpperCase(sides[0].strip().charAt(0)),
                    Character.toUpperCase(sides[1].strip().charAt(0)));
        }
        return keys;
    }

    /** The letter that presses the skill on {@code slot} under {@code played}; a slot the file maps to none keeps its own. */
    public char letterOf(Scheme played, char slot) {
        return played == Scheme.MODERN ? modernKeys().getOrDefault(Character.toUpperCase(slot), slot) : slot;
    }

    /** The Settings row's choices, in its order: modern, then classic. */
    public List<String> choices() {
        return List.of(modernWord, classicWord);
    }

    /** A row's index as the scheme. */
    public static Scheme ofChoice(int choice) {
        return choice == 1 ? Scheme.CLASSIC : Scheme.MODERN;
    }

    /** And the scheme as the row's index. */
    public static int choiceOf(Scheme scheme) {
        return scheme == Scheme.CLASSIC ? 1 : 0;
    }

    /**
     * The player's own choice on Settings (E79's row, read at launch), else the file's. {@code -Dlasthero.controls}
     * beats it, for tests and playtests only.
     */
    public Scheme remembered() {
        var asked = System.getProperty("lasthero.controls");
        if (asked != null) {
            try {
                return Scheme.valueOf(asked.strip().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return fileScheme();
            }
        }
        return ofChoice(uz.dukeengine.client3d.Shell.setting(ROW, choiceOf(fileScheme())));
    }
}
