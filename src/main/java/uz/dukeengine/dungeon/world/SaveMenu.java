package uz.dukeengine.dungeon.world;

/**
 * The saved games on the way in: the first question "Enter the dungeon" asks when there is a save to go back to.
 * See {@code docs/plan/2026-10-08-saving.md}.
 *
 * @param aboutSave the line under a save: {@code %s} the hero, then {@code %d} his level and {@code %d} the floor
 * @param slotWord  a manual slot's name, {@code %s} its number
 * @param saveWord  the pause menu's row that saves into a slot, and {@code saveBlurb} under it
 */
public record SaveMenu(String title, String hint, String continueWord, String loadWord, String loadBlurb,
        String newWord, String newBlurb, String loadTitle, String autosaveWord, String slotWord, String aboutSave,
        String saveWord, String saveBlurb, String saveTitle) {

    /** What a block leaves out. */
    public static final SaveMenu DEFAULTS = new SaveMenu("THE DUNGEON", "UP DOWN choose    ENTER take    ESC back",
            "Continue", "Load", "A saved game", "New game", "A new run from the first floor", "LOAD A GAME",
            "Autosave", "Slot %s", "%s, level %d, floor %d", "Save", "Only a game alone is saved",
            "SAVE THE GAME");
}
