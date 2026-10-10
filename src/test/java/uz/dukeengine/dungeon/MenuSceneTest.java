package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** The scene behind the front menu: a real fight that never ends, the same every time. */
class MenuSceneTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void theFightGoesOnAndTheFallenComeBack() {
        var scene = MenuScene.of(SETTINGS);
        scene.game().runHeadless(30);
        long began = System.nanoTime();
        scene.game().runHeadless(1800); // a minute
        double perFrame = (System.nanoTime() - began) / 1e6 / 1800;
        System.out.printf("menu scene: %.3f ms a frame, %d fallen in a minute%n", perFrame, scene.deaths());

        assertTrue(scene.deaths() >= 4, "only " + scene.deaths() + " fell in a minute: nobody is fighting");
        scene.game().runHeadless(MenuScene.RESPAWN_FRAMES + 30);
        assertTrue(scene.standing(true) > 0 && scene.standing(false) > 0, "a side is gone and stays gone");
        assertTrue(perFrame < 4.0, perFrame + " ms a frame is too dear for a backdrop");
    }

    @Test
    void itPlaysTheSameWayEveryTime() {
        var one = MenuScene.of(SETTINGS);
        var two = MenuScene.of(SETTINGS);
        one.game().runHeadless(900);
        two.game().runHeadless(900);
        assertEquals(one.deaths(), two.deaths());
        assertEquals(one.game().getLogic().getObjects().size(), two.game().getLogic().getObjects().size());
    }

    @Test
    void theClearingWearsTheLookTheDataNames() {
        var scene = MenuScene.of(SETTINGS);
        scene.game().runHeadless(2);
        assertTrue(scene.game().getSnapshot().status().contains("|look=" + SETTINGS.mainMenu().sceneLook()));
    }

    /** The owner, 2026-10-10: the camera somewhat higher, and the fight to the right of the menu at the left. */
    @Test
    void theCameraLooksFromHigherThanPlayAndLeftOfTheFight() {
        var scene = MenuScene.of(SETTINGS);
        scene.game().runHeadless(2);
        var seen = scene.game().camera().current();
        var menu = SETTINGS.mainMenu();
        assertEquals(Math.toRadians(menu.scenePitch()), seen.pitch(), 1e-4);
        assertTrue(seen.pitch() > Math.atan2(0.82, 0.57), "no higher than the camera a game is played with");
        assertEquals(menu.sceneZoom(), seen.zoom(), 1e-3);
        assertTrue(menu.sceneZoom() > 140f, "no further back than play's");
        float fight = (MenuScene.FIGHT_COLUMN + 0.5f) * uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        assertTrue(seen.x() < fight, "it looks at " + seen.x() + ", not left of the fight at " + fight);
        assertEquals(0f, seen.angle(), 1e-6f);
    }
}
