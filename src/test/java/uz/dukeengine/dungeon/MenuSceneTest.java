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
}
