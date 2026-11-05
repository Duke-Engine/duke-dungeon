package uz.dukeengine.dungeon;

import com.jme3.app.state.ScreenshotAppState;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/**
 * The first minutes of Act I as the third-person client draws them, for a person to look at: the real story (a new game
 * of the first act, as {@code --act1=1} begins it), the real client with the game's own canvas, the opening cutscene,
 * the landing, the talk, the hero let go, his first steps and the first blows. One picture at each beat:
 * {@code opening_1.png} onward, into {@code RENDER_OUT} or {@code build/render}; the console says where he and the
 * camera were. Tagged {@code render}: {@code ./gradlew renderCheck --tests '*OpeningRenderCheckTest'}.
 */
@Tag("render")
class OpeningRenderCheckTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int SHOTS = 12;

    @Test
    void theOpeningOfActOne() throws Exception {
        var folder = Path.of(System.getenv("RENDER_OUT") != null ? System.getenv("RENDER_OUT") : "build/render");
        Files.createDirectories(folder);
        for (int n = 1; n <= SHOTS; n++) {
            Files.deleteIfExists(folder.resolve("opening_" + n + ".png"));
        }
        var campaign = SETTINGS.campaign();
        var session = Dungeon.newSession(7L, SETTINGS);
        var game = session.game();
        var visuals = Main.looks(SETTINGS);
        var feet = Main.feet(SETTINGS);
        var keys = Main.controls(SETTINGS, feet);
        Main.heroChosen(session, SETTINGS, visuals, keys, campaign.hero());
        session.run().skipTo(campaign, 1, null);
        var duke = Duke3D.of(game, visuals).window(1280, 720).hotkeys(keys).controls(feet).smoothMotion(true);
        var screens = RenderRig.canvas(duke, session, SETTINGS, true);
        // Through an intro the player has no hand in it: the letterbox and the box alone, no interface.
        duke.interfaceHidden(() -> session.run().inIntro());
        duke.startMatch(game);
        var app = duke.application();
        var settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        settings.setSamples(0);
        app.setSettings(settings);
        var shots = new ScreenshotAppState[SHOTS + 1];
        for (int n = 1; n <= SHOTS; n++) {
            shots[n] = new ScreenshotAppState(folder.toAbsolutePath() + "/", "opening_" + n);
            shots[n].setIsNumbered(false);
            app.getStateManager().attach(shots[n]);
        }
        app.start(JmeContext.Type.OffscreenSurface);
        for (int waited = 0; game.getLogic() == null && waited < 240; waited++) {
            Thread.sleep(1000);
        }
        var logic = game.getLogic();
        var run = session.run();
        var script = run.getScript();
        int[] taken = {0};
        Runnable shot = () -> {
            int n = ++taken[0];
            try {
                app.enqueue(shots[n]::takeScreenshot);
                Thread.sleep(900);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            System.out.println("OPENING " + n + " frame " + logic.getFrame() + " cutscene " + run.cutsceneName()
                    + " intro " + run.inIntro() + " " + where(game, campaign.hero()));
        };

        // 1: the first frames, the camera down close over the line.
        waitFor(() -> logic.getFrame() >= 25, 120);
        shot.run();
        // 2: the ritual, the camera on the landing; 3: the meteor falling.
        waitFor(() -> "Ritual".equals(run.cutsceneName()), 60);
        int ritual = logic.getFrame();
        waitFor(() -> logic.getFrame() >= ritual + 110, 60);
        shot.run();
        waitFor(() -> hero(game, campaign.hero()) != null && hero(game, campaign.hero()).getPosition().z() < 1800f, 60);
        shot.run();
        // 4: he has landed: kneeling in the smoke, the camera low and close; 5: what he is told; 6: back over the line.
        waitFor(() -> script.flagged("HeroLanded"), 60);
        int landed = logic.getFrame();
        waitFor(() -> logic.getFrame() >= landed + 20, 30);
        shot.run();
        waitFor(() -> logic.getFrame() >= landed + 75, 60);
        shot.run();
        // Space reads the box a line at a time, as the player does; the world waits while one is up.
        for (int line = 0; line < 3; line++) {
            screens.take(new uz.dukeengine.client3d.CanvasInput.Key(uz.dukeengine.client3d.CanvasInput.SPACE, true, false));
            Thread.sleep(700);
        }
        shot.run();
        // 7: through the rest of it (Space, Space), then let go: his first frame as himself.
        for (int i = 0; i < 60 && run.inIntro(); i++) {
            screens.take(new uz.dukeengine.client3d.CanvasInput.Key(uz.dukeengine.client3d.CanvasInput.SPACE, true, false));
            Thread.sleep(600);
        }
        // The commander's call to the graves is a box too, and the world waits for it: Space until the frames run.
        for (int i = 0; i < 30; i++) {
            int before = logic.getFrame();
            Thread.sleep(700);
            if (logic.getFrame() > before + 5) {
                break;
            }
            screens.take(new uz.dukeengine.client3d.CanvasInput.Key(uz.dukeengine.client3d.CanvasInput.SPACE, true, false));
        }
        Thread.sleep(2500);
        shot.run();
        // 8: his first steps, forward: the way the camera looks (north).
        Main.walk(game, 192);
        Thread.sleep(2000);
        shot.run();
        Main.walk(game, uz.dukeengine.action.ActionOrders.NONE);
        // 9: toward the nearest of the enemy, and the first blows.
        var foe = nearestFoe(game, campaign.hero());
        System.out.println("OPENING foe " + (foe == null ? "none" : foe.templateName() + " at " + foe.x() + "," + foe.y()));
        if (foe != null) {
            var me = hero(game, campaign.hero());
            double turn = Math.atan2(foe.y() - me.getPosition().y(), foe.x() - me.getPosition().x());
            int angle = (int) Math.floorMod(Math.round(turn / (2 * Math.PI) * 256), 256);
            Main.walk(game, angle);
            for (int i = 0; i < 40; i++) {
                Thread.sleep(250);
                var now = hero(game, campaign.hero());
                if (now != null && Math.hypot(foe.x() - now.getPosition().x(), foe.y() - now.getPosition().y()) < 40) {
                    break;
                }
            }
            Main.walk(game, uz.dukeengine.action.ActionOrders.NONE);
        }
        shot.run();
        for (int blow = 0; blow < 3; blow++) {
            Main.strike(game, false, SETTINGS);
            Thread.sleep(blow == 0 ? 300 : 450);
        }
        shot.run();
        Thread.sleep(1500);
        shot.run();
        Thread.sleep(1000);
        app.stop();
        for (int n = 1; n <= taken[0]; n++) {
            if (!Files.exists(folder.resolve("opening_" + n + ".png"))) {
                throw new java.io.IOException("no picture opening_" + n + ".png in " + folder);
            }
        }
    }

    private static GameObject hero(DukeGame game, String name) {
        var logic = game.getLogic();
        return logic == null ? null : logic.getObjects().stream().filter(o -> o.getTemplate().name().equals(name))
                .findFirst().orElse(null);
    }

    private static String where(DukeGame game, String name) {
        var me = hero(game, name);
        return me == null ? "no hero" : "hero at " + me.getPosition() + " facing " + me.getOrientation();
    }

    private static UnitView nearestFoe(DukeGame game, String name) {
        var me = hero(game, name);
        if (me == null) {
            return null;
        }
        UnitView best = null;
        double distance = Double.MAX_VALUE;
        for (var unit : game.getSnapshot().units()) {
            if (unit.hostile() && unit.health() > 0f && !unit.structure()) {
                double d = Math.hypot(unit.x() - me.getPosition().x(), unit.y() - me.getPosition().y());
                if (d < distance) {
                    distance = d;
                    best = unit;
                }
            }
        }
        return best;
    }

    private static void waitFor(BooleanSupplier condition, int seconds) throws InterruptedException {
        for (int i = 0; i < seconds * 10 && !condition.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
    }
}
