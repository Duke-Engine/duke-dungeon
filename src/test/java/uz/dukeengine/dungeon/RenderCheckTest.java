package uz.dukeengine.dungeon;

import com.jme3.app.state.ScreenshotAppState;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/**
 * Pictures of the game as it is drawn, for a person to look at: the real client, its real lighting and shadows, its
 * third-person camera, rendered to a file with an offscreen context -- a hidden window, no screen and no mouse of
 * anybody's.
 *
 * <p>Tagged {@code render}: the ordinary {@code ./gradlew test} leaves it out (a machine with no GPU has nothing to draw
 * with); {@code ./gradlew renderCheck} runs it. The pictures go to the folder in {@code RENDER_OUT}, or
 * {@code build/render}.
 */
@Tag("render")
class RenderCheckTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    /** Seconds from the start to the shot: the art is read and warmed under a loading bar first. */
    private static final int SECONDS = 14;
    /** 256ths of a turn, 0 along +x counter-clockwise: toward smaller y, where the camera looks. */
    private static final int NORTH = 192;

    /** One figure: its template, where it stands in cells of the arena, and the frame from its Idle on. */
    private record Figure(String kind, float x, float y) {
    }

    private static String arena() {
        var text = new StringBuilder();
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 60; x++) {
                text.append(x == 0 || y == 0 || x == 59 || y == 39 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    @Test
    void theKnightBesideASoldierAndASkeleton() throws Exception {
        // The hero at the middle, facing the way the camera starts looking (north: -y); the others a few steps ahead of
        // him, the soldier at his right and the skeleton at his left.
        shoot("knight_soldier_skeleton", List.of(
                new Figure("Soldier", 163f, 238f), new Figure("Skeleton", 137f, 238f)));
    }

    @Test
    void theKingdomsPeople() throws Exception {
        shoot("people", List.of(
                new Figure("Act1Commander", 128f, 236f), new Figure("Soldier", 142f, 236f),
                new Figure("Militia", 158f, 236f), new Figure("KingdomArcher", 172f, 236f),
                new Figure("KingdomHealer", 128f, 222f), new Figure("Blacksmith", 142f, 222f),
                new Figure("OldWoman", 158f, 222f), new Figure("Merchant", 172f, 222f),
                new Figure("EnemyCommander", 135f, 208f), new Figure("DarkLord", 165f, 208f)));
    }

    @Test
    void theDead() throws Exception {
        shoot("dead", List.of(
                new Figure("Skeleton", 128f, 236f), new Figure("Runner", 142f, 236f),
                new Figure("Brute", 158f, 236f), new Figure("SkeletonMage", 172f, 236f),
                new Figure("Revenant", 135f, 222f), new Figure("GraveRaiser", 165f, 222f),
                new Figure("Rat", 146f, 244f), new Figure("Bat", 154f, 244f)));
    }

    @Test
    void theProps() throws Exception {
        shoot("props", List.of(
                new Figure("Pillar", 126f, 236f), new Figure("Statue", 144f, 236f),
                new Figure("Barrel", 158f, 236f), new Figure("Well", 176f, 232f),
                new Figure("Grave", 140f, 218f)));
    }

    private static void shoot(String shotName, List<Figure> figures) throws Exception {
        var folder = Path.of(System.getenv("RENDER_OUT") != null ? System.getenv("RENDER_OUT") : "build/render");
        Files.createDirectories(folder);
        var name = System.getenv("RENDER_FLAT") != null ? shotName + "_colour_only" : shotName;
        var picture = folder.resolve(name + ".png");
        Files.deleteIfExists(picture);

        var world = Dungeon.world(arena(), SETTINGS);
        var game = world.game();
        game.localPlayer(world.hero());
        game.spawn("Knight", world.hero(), 150f, 250f);
        for (var figure : figures) {
            // On his side, so nobody comes for anybody: they stand in their Idle as the picture is taken.
            game.spawn(figure.kind(), world.hero(), figure.x(), figure.y());
        }
        var visuals = Main.looks(SETTINGS);
        if (System.getenv("RENDER_FLAT") != null) {
            // The old rule, for comparison: every model drawn in its colour alone.
            for (var kind : SETTINGS.monsters()) {
                visuals.unit(kind.name(), unit -> unit.keepsMaps(false));
            }
            visuals.unit("Knight", unit -> unit.keepsMaps(false));
        }
        visuals.discoveredBy("Knight"); // the dark opens round his eyes, as Main.heroChosen does
        // What a theme dresses its pillars, statues and barrels in, drawn here whichever floor this is: the Keep's.
        for (var theme : SETTINGS.themes().all()) {
            if (theme.name().equals("Keep")) {
                theme.monsters().forEach(themed -> visuals.unit(themed.name(), Main.dressed(themed, SETTINGS)));
            }
        }
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
        // Let the scene stand, turn him to face the way the camera looks (a step north), then stop the simulation.
        // Until the simulation runs (the art is read and warmed under a loading bar first), then a moment more.
        for (int waited = 0; game.getLogic() == null && waited < 120; waited++) {
            Thread.sleep(1000);
        }
        // Who the hero is, as a run's status line says it: the shoulder HUD draws no bar over him, as in the game.
        game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals("Knight")).findFirst()
                .ifPresent(knight -> game.setStatus("|hero=" + knight.getId().value()));
        Thread.sleep(SECONDS * 1000L - 4000);
        Main.walk(game, NORTH);
        Thread.sleep(500);
        Main.walk(game, uz.dukeengine.action.ActionOrders.NONE);
        Thread.sleep(1500);
        pause(game);
        Thread.sleep(1500);
        app.enqueue(shots::takeScreenshot);
        long direct = java.lang.management.ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class)
                .stream().filter(pool -> pool.getName().equals("direct")).mapToLong(java.lang.management.BufferPoolMXBean::getMemoryUsed).sum();
        long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println("MEMORY " + name + ": direct buffers " + direct / 1_000_000 + " MB, heap " + heap / 1_000_000 + " MB");
        Thread.sleep(2500);
        app.stop();
        if (!Files.exists(picture)) {
            throw new IOException("no picture was written to " + picture);
        }
    }

    private static void pause(DukeGame game) {
        game.togglePause();
    }
}
