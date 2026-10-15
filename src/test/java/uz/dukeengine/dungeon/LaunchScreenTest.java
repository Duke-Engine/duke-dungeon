package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.dungeon.LaunchScreen.Phase;
import uz.dukeengine.dungeon.content.DungeonSettings;

class LaunchScreenTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private final List<String> heard = new ArrayList<>();
    private int released;
    private final List<CanvasInput.Event> passed = new ArrayList<>();

    private LaunchScreen screen() {
        return new LaunchScreen(canvas -> { }, event -> passed.add(event), SETTINGS.splash(), SETTINGS.mainMenu(),
                () -> released++, heard::add);
    }

    private static void run(LaunchScreen screen, float seconds) {
        for (float t = 0f; t < seconds; t += 0.01f) {
            screen.step(0.01f);
        }
    }

    @Test
    void theSplashIsTheMakerThenTheNameThenTheLoadScreen() {
        var splash = SETTINGS.splash();
        var screen = screen();
        assertEquals(Phase.ENGINE, screen.phase());
        run(screen, splash.engineSeconds() + 0.02f);
        assertEquals(Phase.TITLE, screen.phase());
        assertEquals(List.of(splash.sound()), heard, "the name lands with its sting");
        assertEquals(0, released, "the scene behind the menu waits through the splash");
        run(screen, splash.titleSeconds() + 0.02f);
        assertEquals(Phase.LOADING, screen.phase());
        assertEquals(1, released);
        float total = splash.engineSeconds() + splash.titleSeconds();
        assertTrue(total >= 2.5f && total <= 4.5f, "about three seconds of splash: " + total);
    }

    @Test
    void aKeyOrAClickSkipsTheSplashOnlyAfterItsFirstMoment() {
        var screen = screen();
        assertTrue(screen.take(new CanvasInput.Key(CanvasInput.ESCAPE, true, false)), "taken, not passed to the menu");
        assertEquals(Phase.ENGINE, screen.phase(), "too soon to skip");
        run(screen, SETTINGS.splash().skipAfter() + 0.02f);
        screen.take(new CanvasInput.Button(10, 10, CanvasInput.Mouse.LEFT, true, false));
        assertEquals(Phase.LOADING, screen.phase());
        assertEquals(1, released);
        assertTrue(passed.isEmpty(), "nothing reaches the menu while the screen is up");
    }

    @Test
    void theBarFollowsTheEngineAndTheMenuComesOnlyAtAHundred() {
        var screen = screen();
        run(screen, SETTINGS.splash().skipAfter() + 0.02f);
        screen.skip();
        screen.loaded(40);
        run(screen, 2f);
        assertEquals(40f, screen.shown(), 0.001f, "never ahead of the engine");
        assertEquals(Phase.LOADING, screen.phase());
        screen.loaded(99);
        run(screen, 2f);
        assertEquals(Phase.LOADING, screen.phase(), "99 is not there yet");
        screen.loaded(100);
        assertEquals(99f, screen.shown(), 0.001f, "eased, not jumped");
        run(screen, 0.1f);
        assertEquals(Phase.FADE, screen.phase());
        assertTrue(screen.cover() < 1f);
        run(screen, SETTINGS.splash().fadeSeconds() + 0.02f);
        assertEquals(Phase.DONE, screen.phase());
        assertEquals(0f, screen.cover());
        var key = new CanvasInput.Key(CanvasInput.ENTER, true, false);
        screen.take(key);
        assertEquals(List.of(key), passed, "once it is gone the input goes on to the screens");
    }

    @Test
    void aMatchLoadingLaterPutsTheLoadScreenBackUp() {
        var screen = screen();
        run(screen, SETTINGS.splash().skipAfter() + 0.02f);
        screen.skip();
        screen.loaded(100);
        run(screen, 3f);
        assertEquals(Phase.DONE, screen.phase());
        screen.loaded(5);
        assertEquals(Phase.LOADING, screen.phase());
        assertEquals(0f, screen.shown());
        assertEquals(1, released, "the scene's hold is let go once only");
        screen.loaded(100);
        run(screen, 3f);
        assertEquals(Phase.DONE, screen.phase());
    }

    @Test
    void theHintsTurnOver() {
        var screen = screen();
        run(screen, SETTINGS.splash().skipAfter() + 0.02f);
        screen.skip();
        var first = screen.tip();
        run(screen, SETTINGS.splash().tipSeconds() + 0.05f);
        assertFalse(first.equals(screen.tip()));
    }

    @Test
    void itsWordsAreUzbek() {
        var splash = SETTINGS.splash();
        assertEquals("Yuklanmoqda…", splash.loadingWord());
        assertTrue(splash.tips().size() >= 4);
        assertTrue(splash.tips().stream().anyMatch(tip -> tip.contains("gulxan")));
        assertTrue(splash.tips().stream().anyMatch(tip -> tip.contains("xarita")));
        assertEquals("Last Hero", SETTINGS.mainMenu().title());
        assertFalse(SETTINGS.mainMenu().subtitle().isBlank());
        assertTrue(SETTINGS.sounds().stream().anyMatch(cue -> cue.name().equals(splash.sound())),
                "the sting is a cue the game has");
    }

    @Test
    void theSplashAndTheLoadScreenAreDrawnIntoPictures() throws Exception {
        var screen = screen();
        run(screen, SETTINGS.splash().engineSeconds() * 0.5f);
        var engine = new PlayerScreensMockRenderTest.Java2D(1600, 900);
        screen.draw(engine);
        engine.save("splash-engine.png");
        run(screen, SETTINGS.splash().engineSeconds() * 0.5f + SETTINGS.splash().riseSeconds() + 0.1f);
        var title = new PlayerScreensMockRenderTest.Java2D(1600, 900);
        screen.draw(title);
        title.save("splash-title.png");
        run(screen, SETTINGS.splash().titleSeconds());
        screen.loaded(55);
        run(screen, 1f);
        var loading = new PlayerScreensMockRenderTest.Java2D(1600, 900);
        screen.draw(loading);
        loading.save("splash-loading.png");
        assertEquals(Phase.LOADING, screen.phase());
    }
}
