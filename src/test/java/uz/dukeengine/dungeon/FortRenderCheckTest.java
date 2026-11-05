package uz.dukeengine.dungeon;

import com.jme3.app.state.ScreenshotAppState;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;

/**
 * Pictures of Act I's walls and gates as the third-person camera sees them, for a person to look at: the real client on
 * the real open world, offscreen (see {@link RenderCheckTest}). The hero stands where the shot says and the camera
 * comes behind him. Tagged {@code render}; {@code ./gradlew renderCheck} runs it, into {@code RENDER_OUT} or
 * {@code build/render}.
 */
@Tag("render")
class FortRenderCheckTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    /** The same turns as RenderCheckTest: 256ths of a turn, 0 east, 64 south (+y), 192 north. */
    private static final int EAST = 0;
    private static final int SOUTH = 64;
    private static final int WEST = 128;
    private static final int NORTH = 192;
    private static final float CELL = 10f;

    /** The city's gate on its north wall (56, 218): shut from outside, with nobody near. */
    @Test
    void theTownGateShut() throws Exception {
        shoot("gate_shut", 58.5f, 211f, SOUTH, 0);
    }

    /** The same, a hero at its foot: it has opened and its leaves have swung in. */
    @Test
    void theTownGateOpen() throws Exception {
        shoot("gate_open", 58.5f, 213.5f, SOUTH, 6000);
    }

    /** The town wall beside the hero, at his eye height: the camera behind him, the wall on his right. */
    @Test
    void theTownWallBesideTheHero() throws Exception {
        shoot("town_wall_eye", 46.5f, 216.2f, EAST, 0);
    }

    /** A joint of two wall runs at a low round tower (50, 218), from outside. */
    @Test
    void aWallRunJoint() throws Exception {
        shoot("wall_joint", 50.5f, 211.5f, SOUTH, 0);
    }

    /** The queen's castle, dark, from outside its west wall, gate at (158, 52). */
    @Test
    void theQueensCastleDark() throws Exception {
        shoot("queens_castle_dark", 150.5f, 47.5f, EAST, 0);
    }

    /** The queen's portcullis, down, from outside, nobody near. */
    @Test
    void theQueensGateShut() throws Exception {
        shoot("queens_gate_shut", 148.5f, 51.5f, EAST, 0);
    }

    /** The royal castle's gate onto the plaza (54, 229), open from the way in; seen from the plaza. */
    @Test
    void theRoyalGateOpen() throws Exception {
        shoot("royal_gate_open", 61.5f, 230.5f, WEST, 0);
    }

    /** A corner of the queen's castle (158, 43): the old stepped tower beside the new wall and turret. */
    @Test
    void theQueensCorner() throws Exception {
        shoot("queens_corner", 151.5f, 37.5f, 29, 0);
    }

    /** The city's street of house fronts (y 236) with the palace's keep and tower behind, from the south. */
    @Test
    void theCityStreet() throws Exception {
        shoot("city_street", 49.5f, 240.5f, NORTH, 0);
    }

    /**
     * The client's camera turned to look the way {@code turn} (256ths, as the walk orders) goes: it starts looking north
     * and only the mouse turns it, which nothing here has, so the client's own turn is called. Test-only, by reflection.
     */
    private static void turnCamera(Object app, int turn) throws Exception {
        // yaw 0 looks up the map (-y); the gaze is (-sin yaw, -cos yaw) on the ground's (x, y).
        double toward = turn * 2 * Math.PI / 256;
        float yaw = (float) Math.atan2(-Math.cos(toward), -Math.sin(toward));
        for (var type = app.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField("camera");
                field.setAccessible(true);
                var focus = field.get(app);
                var turnBy = focus.getClass().getDeclaredMethod("turnBy", float.class);
                turnBy.setAccessible(true);
                turnBy.invoke(focus, yaw);
                return;
            } catch (NoSuchFieldException _) {
                // up one class
            }
        }
        throw new IllegalStateException("no camera on " + app.getClass());
    }

    private static void shoot(String name, float cellX, float cellY, int turn, int settleMillis)
            throws Exception {
        var folder = Path.of(System.getenv("RENDER_OUT") != null ? System.getenv("RENDER_OUT") : "build/render");
        Files.createDirectories(folder);
        var picture = folder.resolve(name + ".png");
        Files.deleteIfExists(picture);

        var session = Dungeon.newStageSession(Stages.load("world", SETTINGS), SETTINGS);
        var game = session.game();
        session.run().startWith(game, "Knight");
        var visuals = Main.looks(SETTINGS);
        visuals.discoveredBy("Knight");
        var feet = Main.feet(SETTINGS);
        var duke = Duke3D.of(game, visuals).window(1280, 720).hotkeys(Main.controls(SETTINGS, feet)).controls(feet)
                .smoothMotion(true);
        duke.startMatch(game);
        var app = duke.application();
        var settings = new AppSettings(true);
        settings.setResolution(1280, 720);
        settings.setSamples(0);
        app.setSettings(settings);
        var shots = new ScreenshotAppState(folder.toAbsolutePath() + "/", name);
        shots.setIsNumbered(false);
        app.getStateManager().attach(shots);
        app.start(JmeContext.Type.OffscreenSurface);
        for (int waited = 0; game.getLogic() == null && waited < 180; waited++) {
            Thread.sleep(1000);
        }
        Thread.sleep(8000);
        // To where the shot says, on the simulation thread; then a step the way he faces, so the
        // camera comes round behind him.
        turnCamera(app, turn);
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Knight")).findFirst().orElseThrow();
        var logic = game.getLogic();
        // On the simulation's own thread, as everything that changes the world is, and the simulation running.
        game.runOnSimThread(() -> {
            logic.setGamePaused(false);
            hero.setPosition(new Coord3D(cellX * CELL, cellY * CELL,
                    logic.groundHeight(new Coord3D(cellX * CELL, cellY * CELL, 0f))));
        });
        Thread.sleep(500);
        Main.walk(game, turn);
        Thread.sleep(400);
        Main.walk(game, uz.dukeengine.action.ActionOrders.NONE);
        Thread.sleep(6000 + settleMillis);
        game.runOnSimThread(() -> logic.setGamePaused(true));
        Thread.sleep(1500);
        app.enqueue(() -> app.getRenderer().getStatistics().setEnabled(true));
        Thread.sleep(1000);
        app.enqueue(shots::takeScreenshot);
        Thread.sleep(500);
        var report = new StringBuilder();
        app.enqueue(() -> {
            var stats = app.getRenderer().getStatistics();
            var labels = stats.getLabels();
            var data = new int[labels.length];
            stats.getData(data);
            for (int i = 0; i < labels.length; i++) {
                report.append(labels[i]).append('=').append(data[i]).append(' ');
            }
        });
        Thread.sleep(1500);
        long direct = java.lang.management.ManagementFactory
                .getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class).stream()
                .filter(pool -> pool.getName().equals("direct")).mapToLong(java.lang.management.BufferPoolMXBean::getMemoryUsed)
                .sum();
        long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println("MEMORY " + name + ": direct buffers " + direct / 1_000_000 + " MB, heap " + heap / 1_000_000
                + " MB; hero at " + hero.getPosition());
        var gates = new java.util.TreeMap<String, Integer>();
        for (var thing : game.getLogic().getObjects()) {
            var template = thing.getTemplate().name();
            if (template.contains("Gate") || template.contains("Portcullis")) {
                gates.merge(template, 1, Integer::sum);
                System.out.println("GATE " + template + " at " + thing.getPosition() + " owner " + thing.getPlayerIndex());
            }
        }
        System.out.println("FPS " + name + ": " + app.getTimer().getFrameRate());
        System.out.println("PAUSED " + logic.isGamePaused());
        System.out.println("HERO " + hero.getId() + " owner " + hero.getPlayerIndex() + " at " + hero.getPosition());
        System.out.println("GATES " + name + ": " + gates);
        System.out.println("STATS " + name + ": " + report);
        Thread.sleep(2000);
        app.stop();
        if (!Files.exists(picture)) {
            throw new IOException("no picture was written to " + picture);
        }
    }
}
