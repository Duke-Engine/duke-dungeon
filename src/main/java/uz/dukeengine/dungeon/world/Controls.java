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
 */
public record Controls(String scheme, String schemeWord, String modernWord, String classicWord,
        List<String> modernSkillKeys) {

    /** The two schemes. */
    public enum Scheme {
        MODERN, CLASSIC
    }

    /** What a block leaves out: classic, the four on Q E R F, and English. */
    public static final Controls DEFAULTS = new Controls("CLASSIC", "Controls", "Modern (WASD)", "Classic (mouse)",
            List.of("Q = Q", "W = E", "E = R", "R = F"));

    /** Where the player's own choice is kept, beside the hero he last asked an address for (PartyFront). */
    static final String REMEMBERED = "controls";

    public Controls {
        scheme = scheme == null || scheme.isBlank() ? "CLASSIC" : scheme.strip().toUpperCase(java.util.Locale.ROOT);
        modernSkillKeys = modernSkillKeys == null ? List.of() : List.copyOf(modernSkillKeys);
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

    /**
     * The player's own choice, once he has made one, else the file's. Kept in the user's preferences until the
     * engine's Settings screen can hold a game's row (E79); {@code -Dlasthero.controls=modern} beats both.
     */
    public Scheme remembered() {
        var asked = System.getProperty("lasthero.controls");
        if (asked == null) {
            asked = java.util.prefs.Preferences.userNodeForPackage(Controls.class).get(REMEMBERED, scheme);
        }
        try {
            return Scheme.valueOf(asked.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fileScheme();
        }
    }
}
