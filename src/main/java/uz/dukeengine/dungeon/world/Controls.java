package uz.dukeengine.dungeon.world;

import java.util.List;

/**
 * How the hero is played, from his shoulder: the mouse turns the camera, W A S D run him, Shift sprints, Space rolls,
 * E works the nearest thing in front of him -- see {@code data/world/controls.duke}. The keys themselves are fixed in
 * {@code Main}; what is here is what the data may tune and the words the player reads.
 *
 * @param dodgeWord     what Space does, on the keys page
 * @param sprintWord    what Shift does, on the keys page
 * @param sprintPercent how fast he runs while Shift is held, as a share of his speed: 150 is half as fast again
 * @param interactKey   the key that works, talks to or takes the nearest thing in front of him
 * @param interactWord  what that key is called on the keys page
 * @param interactReach how near the interact key looks, world units
 * @param interactCone  how far round his facing it looks, degrees either side counted together
 * @param interactOrders the context orders' words (Talk, Chop, ...) the interact prompt names
 * @param interactWords  and what the prompt says for each, in the same order
 */
public record Controls(String dodgeWord, String sprintWord, int sprintPercent, String interactKey, String interactWord,
        float interactReach,
        float interactCone, List<String> interactOrders, List<String> interactWords) {

    /** What a block leaves out: E, fourteen units, a hundred degrees, no prompt words. */
    public static final Controls DEFAULTS = new Controls("Dodge", "Sprint", 150, "E", "Use", 14f, 100f, List.of(), List.of());

    public Controls {
        dodgeWord = dodgeWord == null ? "" : dodgeWord;
        sprintWord = sprintWord == null ? "" : sprintWord;
        interactWord = interactWord == null ? "" : interactWord;
        interactKey = interactKey == null ? "" : interactKey.strip().toUpperCase(java.util.Locale.ROOT);
        interactOrders = interactOrders == null ? List.of() : List.copyOf(interactOrders);
        interactWords = interactWords == null ? List.of() : List.copyOf(interactWords);
    }

    /**
     * What the interact key's prompt says over a thing whose context order is {@code order} ({@code Talk},
     * {@code Chop}, ...): {@code "[E] Gaplashish"}, the key as bound; null for an order the file names no word for.
     */
    public String promptOf(String order, char key) {
        int at = interactOrders.indexOf(order);
        return at < 0 || at >= interactWords.size() ? null : "[" + key + "] " + interactWords.get(at);
    }
}
