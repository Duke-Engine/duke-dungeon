package uz.dukeengine.dungeon.world;

import java.util.List;

/**
 * What the window shows before the front menu, and while any map loads: a maker's credit, the game's name coming up
 * out of the dark, then a load screen with a bar and a hint. Its words and timings; the name and the line under it are
 * the MainMenu's. See {@code data/world/menu.duke}.
 *
 * @param engineWord   the maker's credit shown first, alone on black; blank for none
 * @param engineSeconds how long it stays
 * @param titleSeconds  how long the name stays, its rise included
 * @param riseSeconds   how long the name takes to come up out of the dark
 * @param skipAfter     how long into the splash a key or a click first skips it
 * @param sound         the cue the name lands with; blank for none
 * @param titleFont     the face the name is set in (a font file from the resource root, as the canvas takes one)
 * @param titleColour   the name's gold, 0xRRGGBB
 * @param glowColour    its glow and the embers rising past it
 * @param textColour    the small print: the line under the name, the hint, the maker's credit
 * @param loadingWord   what the bar says over it
 * @param tips          the hints the load screen turns through
 * @param tipSeconds    how long each stays
 * @param barSpeed      how fast the bar may fill, in percent a second, so it never jumps
 * @param fadeSeconds   how long the load screen takes to clear off what it was hiding
 */
public record Splash(String engineWord, float engineSeconds, float titleSeconds, float riseSeconds, float skipAfter,
        String sound, String titleFont, int titleColour, int glowColour, int textColour, String loadingWord,
        List<String> tips, float tipSeconds, float barSpeed, float fadeSeconds) {

    public static final Splash DEFAULTS = new Splash("", 0f, 3f, 1.2f, 0.5f, "", "fonts/Cinzel-Bold.ttf", 0xE8C37A,
            0xE8A33D, 0xD9CFBA, "Loading", List.of(), 4f, 120f, 0.6f);

    public Splash {
        engineWord = engineWord == null ? "" : engineWord;
        sound = sound == null ? "" : sound;
        titleFont = titleFont == null || titleFont.isBlank() ? "fonts/Cinzel-Bold.ttf" : titleFont;
        loadingWord = loadingWord == null ? "" : loadingWord;
        tips = tips == null ? List.of() : List.copyOf(tips);
        tipSeconds = tipSeconds > 0f ? tipSeconds : 4f;
        barSpeed = barSpeed > 0f ? barSpeed : 120f;
    }
}
