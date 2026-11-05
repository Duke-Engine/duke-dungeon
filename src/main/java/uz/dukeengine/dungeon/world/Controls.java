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
 * @param cameraDistance     how far back the camera stands along its line of sight, world units (12 is his height)
 * @param cameraSide         how far to the right of him it looks past him
 * @param cameraLookHeight   how high over his feet the point it looks past him at lies
 * @param cameraPitch        how steeply it looks down when the game begins, degrees
 * @param cameraPitchMin     the least it looks down, degrees: under 0 looks up at him, but the ground stops it
 * @param cameraPitchMax     the most it looks down, degrees: short of straight overhead
 * @param cameraSensitivity  how far the mouse turns it for each pixel, degrees
 * @param cameraFollowSeconds the time constant it eases after him with
 * @param cameraReleaseSeconds the time constant it eases back out with once a wall is no longer in the way
 * @param cameraBlockerHeight how high a thing must stand to pull the camera in: lower ones are seen over
 */
public record Controls(String dodgeWord, String sprintWord, int sprintPercent, String interactKey, String interactWord,
        float interactReach,
        float interactCone, List<String> interactOrders, List<String> interactWords, float cameraDistance,
        float cameraSide, float cameraLookHeight, float cameraPitch, float cameraPitchMin, float cameraPitchMax,
        float cameraSensitivity, float cameraFollowSeconds, float cameraReleaseSeconds, float cameraBlockerHeight) {

    /**
     * What a block leaves out: E, fourteen units, a hundred degrees, no prompt words; a camera two of his heights back
     * and a quarter of one to his right.
     */
    public static final Controls DEFAULTS = new Controls("Dodge", "Sprint", 150, "E", "Use", 14f, 100f, List.of(),
            List.of(), 24f, 3.5f, 10f, 14f, -12f, 70f, 0.25f, 0.1f, 0.5f, 12f);

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
