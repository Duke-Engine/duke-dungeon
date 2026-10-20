package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * A graveyard the dead climb out of, its raiser standing in the middle of its graves (the owner, 2026-10-20: an ender
 * dragon's crystals). Every {@code everyFrames} one of {@code raises} climbs out of a grave, the kinds in turn, while
 * fewer than {@code mostStanding} of them stand. While a grave stands, the raiser takes nothing that holds -- the graves
 * keep it whole -- and each grave is struck down on its own; the last down, the raiser can be killed, and left a while
 * unhurt it raises them all again. The raiser dead sets {@code gone}, which saves with the story, and the graveyard is
 * never laid again. See {@link Graves} for what the risen pay.
 *
 * @param map          the map it is on, by name
 * @param zone         the zone it stands in
 * @param wakes        a flag it waits for before it is laid; blank for from the start
 * @param gone         the flag set when its last grave is struck down; blank for {@code Gone} and its name
 * @param raiser       the Monster that raises them
 * @param grave        the Monster each grave is: something that never moves, beaten rather than killed (an UNYIELDING
 *                     skill) so the game decides when it falls
 * @param graves       how many graves
 * @param ring         how far from the zone's middle the graves stand, in cells
 * @param raises       the Monsters that climb out, in turn
 * @param everyFrames  how often one climbs out
 * @param mostStanding how many of its risen may stand at once
 * @param level        the level they stand at; 0 for the floor's level before its boss
 * @param shieldLook   an Effect played on the raiser every {@code shieldFrames} while a grave keeps it; blank for none
 * @param risesLook    an Effect played where one climbs out
 * @param brokenLook   an Effect played where a grave is struck down
 * @param raiseBeam    a Laser from the raiser's hands to each grave while it stands, and to the one a body climbs
 *                     out of; blank for none
 * @param leash        how far from the graveyard's middle, in cells, its dead go after anybody: past it they turn
 *                     back to their graves
 * @param raisesAfter  a flag before which it stands dormant -- laid and seen, nothing raised and nothing of it hurt;
 *                     blank for raising once laid
 * @param calmFrames   how long its raiser, its graves all down, is left unhurt before it begins to raise them again
 * @param restoreFrames how long that takes: struck in it, it begins again; through it, every grave stands again
 * @param restoreClip  the clip it plays the while
 * @param restoreLook  an Effect played on it every second of it
 */
public record Graveyard(String name, String map, String zone, String wakes, String gone, String raiser, String grave,
        int graves, float ring, List<String> raises, int everyFrames, int mostStanding, int level, String shieldLook,
        int shieldFrames, String risesLook, String brokenLook, String raiseBeam, float leash, String raisesAfter,
        int calmFrames, int restoreFrames, String restoreClip, String restoreLook) {

    public static final Graveyard DEFAULTS = new Graveyard("", "world", "", "", "", "", "Grave", 3, 2f, List.of(), 150,
            6, 0, "", 30, "", "", "", 10f, "", 90, 210, "", "");

    public Graveyard {
        raises = raises == null ? List.of() : List.copyOf(raises);
    }

    /** The flag its last grave sets. */
    public String goneFlag() {
        return gone == null || gone.isBlank() ? "Gone" + name : gone;
    }
}
