package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;

/**
 * The open world fades into haze at the edge of the ground built round the camera, and nothing else does: the same
 * maps its things sleep on and its routes are sectored on, asked of the record the client is handed.
 */
class WideWorldLookTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void onlyTheWideWorldFadesIntoHaze() {
        var visuals = Visuals.create();
        Main.paintedGround(visuals, SETTINGS.paintedGround());
        assertEquals(SETTINGS.paintedGround().hazeFrom(), visuals.getHazeFrom());
        assertTrue(visuals.getHazeTo() > visuals.getHazeFrom()
                && visuals.getHazeTo() <= SETTINGS.paintedGround().streamCells(),
                "the haze must be whole before the ground built round the camera ends");

        var world = Dungeon.newStageSession(Stages.load("world", SETTINGS), SETTINGS).game();
        world.runHeadless(2);
        assertTrue(visuals.hazes(world.getMapRecord()), "the open world does not fade at its edge");
        assertTrue(world.getLogic().getRouteSectors() > 0, "and it is the same world its routes are sectored on");

        var painted = Dungeon.newStageSession(Stages.load("painted", SETTINGS), SETTINGS).game();
        painted.runHeadless(2);
        assertFalse(visuals.hazes(painted.getMapRecord()), "a small painted stage is drawn whole");

        var descent = Dungeon.newSession(11L).game();
        descent.runHeadless(2);
        assertFalse(visuals.hazes(descent.getMapRecord()), "a floor of the descent is drawn whole to its walls");
        assertFalse(visuals.hazes(null), "and so is no map at all");
    }
}
