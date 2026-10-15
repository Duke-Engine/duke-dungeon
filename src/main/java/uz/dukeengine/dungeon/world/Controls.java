package uz.dukeengine.dungeon.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ways the hero is played -- the modern action-RPG way (WASD walks him, the mouse aims), the classic
 * Dota/WC3 way (the mouse does everything) and the action way (the mouse turns the camera, skills on 1-4) -- and the letters each casts his four skills on: see
 * {@code data/world/controls.duke} and {@code docs/plan/2026-10-15-modern-controls.md}.
 *
 * <p>A skill keeps its own Key (Q W E R) as its name everywhere -- the cast order, his ranks, the save, an item's
 * {@code SKILL_RANK:E} -- and the scheme only says which letter on the keyboard presses it.
 *
 * @param scheme          which of the three a player with no choice of his own plays: {@code MODERN}, {@code CLASSIC} or {@code ACTION}
 * @param schemeWord      the row's word on Settings
 * @param modernWord      the modern scheme's word
 * @param classicWord     the classic scheme's
 * @param modernSkillKeys each {@code "Slot = Letter"}: the skill on Key Slot is pressed on Letter in the modern scheme
 * @param dodgeWord       what Space does in the modern scheme, on the keys page
 * @param actionWord      the action scheme's word (docs/plan/2026-10-15-action-controls.md)
 * @param actionSkillKeys each {@code "Slot = Digit"}: the skill on Key Slot is pressed on Digit in the action scheme
 * @param actionBeltKeys  the belt's keys in the action scheme, one digit a stack, in the belt's order
 * @param interactKey     the action scheme's interact key: the nearest thing in front of him worked, talked to, taken
 * @param interactOrders  the context orders' words (Talk, Chop, ...) the interact prompt names
 * @param interactWords   and what the prompt says for each, in the same order
 */
public record Controls(String scheme, String schemeWord, String modernWord, String classicWord,
        List<String> modernSkillKeys, String dodgeWord, String actionWord, List<String> actionSkillKeys,
        String actionBeltKeys, String interactKey, List<String> interactOrders, List<String> interactWords) {

    /** The three schemes, in the Settings row's order. */
    public enum Scheme {
        MODERN, CLASSIC, ACTION
    }

    /** What a block leaves out: classic (a world with no Controls block, a test's), the four on Q E R F, English. */
    public static final Controls DEFAULTS = new Controls("CLASSIC", "Controls", "Modern (WASD)", "Classic (mouse)",
            List.of("Q = Q", "W = E", "E = R", "R = F"), "Dodge", "Action", List.of("Q = 1", "W = 2", "E = 3", "R = 4"),
            "5678", "E", List.of(), List.of());

    /** The Settings row's key in the player's settings file (E79): 0 modern, 1 classic, 2 action, as the row lists them. */
    public static final String ROW = "game.controls";

    public Controls {
        scheme = scheme == null || scheme.isBlank() ? "CLASSIC" : scheme.strip().toUpperCase(java.util.Locale.ROOT);
        modernSkillKeys = modernSkillKeys == null ? List.of() : List.copyOf(modernSkillKeys);
        dodgeWord = dodgeWord == null ? "" : dodgeWord;
        actionWord = actionWord == null ? "" : actionWord;
        actionSkillKeys = actionSkillKeys == null ? List.of() : List.copyOf(actionSkillKeys);
        actionBeltKeys = actionBeltKeys == null ? "" : actionBeltKeys.strip();
        interactKey = interactKey == null ? "" : interactKey.strip().toUpperCase(java.util.Locale.ROOT);
        interactOrders = interactOrders == null ? List.of() : List.copyOf(interactOrders);
        interactWords = interactWords == null ? List.of() : List.copyOf(interactWords);
    }

    /**
     * Whether {@code played} walks him on W A S D: modern, or action -- which plays as modern (the camera following
     * him, the dodge, the left button's swing) with its own keys on top until its engine requests land (E90-E95).
     */
    public static boolean walksOnKeys(Scheme played) {
        return played != Scheme.CLASSIC;
    }

    /** The file's scheme. */
    public Scheme fileScheme() {
        return Scheme.valueOf(scheme);
    }

    /** The modern letters by the skill's own key, in the file's order. */
    public Map<Character, Character> modernKeys() {
        return pairs(modernSkillKeys, "ModernSkillKeys");
    }

    /** The action scheme's digits by the skill's own key, in the file's order. */
    public Map<Character, Character> actionKeys() {
        return pairs(actionSkillKeys, "ActionSkillKeys");
    }

    private static Map<Character, Character> pairs(List<String> listed, String field) {
        var keys = new LinkedHashMap<Character, Character>();
        for (var pair : listed) {
            var sides = pair.split("=");
            if (sides.length != 2 || sides[0].strip().length() != 1 || sides[1].strip().length() != 1) {
                throw new IllegalArgumentException("Controls " + field + ": \"" + pair + "\" is not \"Slot = Key\"");
            }
            keys.put(Character.toUpperCase(sides[0].strip().charAt(0)),
                    Character.toUpperCase(sides[1].strip().charAt(0)));
        }
        return keys;
    }

    /** The letter that presses the skill on {@code slot} under {@code played}; a slot the file maps to none keeps its own. */
    public char letterOf(Scheme played, char slot) {
        var keys = switch (played) {
            case MODERN -> modernKeys();
            case ACTION -> actionKeys();
            case CLASSIC -> Map.<Character, Character>of();
        };
        return keys.getOrDefault(Character.toUpperCase(slot), slot);
    }

    /** The key that eats from belt stack {@code belt} (0 first) under {@code played}: action's, else 1 to its size. */
    public char beltKey(Scheme played, int belt) {
        return played == Scheme.ACTION && belt < actionBeltKeys.length() ? actionBeltKeys.charAt(belt)
                : (char) ('1' + belt);
    }

    /**
     * What the interact key's prompt says over a thing whose context order is {@code order} ({@code Talk},
     * {@code Chop}, ...): {@code "[E] Gaplashish"}, the key as bound; null for an order the file names no word for.
     */
    public String promptOf(String order, char key) {
        int at = interactOrders.indexOf(order);
        return at < 0 || at >= interactWords.size() ? null : "[" + key + "] " + interactWords.get(at);
    }

    /** The Settings row's choices, in its order: modern, classic, then action (a kept 0 or 1 stays what it was). */
    public List<String> choices() {
        return List.of(modernWord, classicWord, actionWord);
    }

    /** A row's index as the scheme. */
    public static Scheme ofChoice(int choice) {
        return choice >= 0 && choice < Scheme.values().length ? Scheme.values()[choice] : Scheme.MODERN;
    }

    /** And the scheme as the row's index. */
    public static int choiceOf(Scheme scheme) {
        return scheme.ordinal();
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
