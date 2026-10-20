package uz.dukeengine.dungeon.story;

/**
 * The window's words and looks for people and their talk, and how often the simulation looks for a hero by one.
 *
 * @param continueHint what is written under a line
 * @param giveMark     the picture over a person with a quest to give
 * @param handInMark   the picture over a person a quest waits on
 * @param markColour   both marks' colour, RGB
 * @param checkFrames  how often, in frames, bags, zones and people's radii are looked at
 * @param glowLook     the effect a quest step's target wears while the step wants it (a step that says Glow); blank for
 *                     none
 * @param glowFrames   how often it is played on them again
 * @param readWaitFrames how long, in frames the world goes on, a cutscene waits for every player to have read what
 *                     was said before it, at most — for a machine with no window, or a player away from his keys
 * @param followLook  the effect over whoever a FOLLOW step leads him after, played every GlowFrames; blank for none
 * @param followNear  how near, in units, a hero keeps for the one he follows to walk on
 * @param followFar   how far he may stray before they stop and wait for him
 * @param offerQuestion what an offer asks once its lines are said
 * @param offerHint   what is written under that question: the keys that answer it
 * @param closePitch  how steeply, in degrees, the camera looks at a face in a talk with him
 * @param closeZoom   how far back, in units, it stands from the one who speaks
 */
public record Dialogue(String continueHint, String giveMark, String handInMark, int markColour, int checkFrames,
        String glowLook, int glowFrames, int readWaitFrames, String followLook, float followNear, float followFar,
        String offerQuestion, String offerHint, float closePitch, float closeZoom) {

    public static final Dialogue DEFAULTS = new Dialogue("Davom etish uchun bosing / Space",
            "ui/cursors/default/mark_exclamation.png", "ui/cursors/default/mark_question.png", 0xE8C15A, 10, "", 30,
            300, "", 90f, 150f, "Topshiriqni qabul qilasizmi?", "Enter / chap tugma: qabul qilaman  |  Esc / o'ng tugma: hozir emas",
            22f, 70f);
}
