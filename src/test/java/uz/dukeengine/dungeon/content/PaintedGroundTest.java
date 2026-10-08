package uz.dukeengine.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.dungeon.world.PaintedGround;

/** How a painted map's ground is drawn, read from data/world/scene.duke, and refused where a number makes no sense. */
class PaintedGroundTest {

    @Test
    void theShippedLookBlendsCutsInQuartersAndDrawsCliffs() {
        var ground = DungeonSettings.load().paintedGround();

        assertTrue(ground.blend());
        assertTrue(ground.quarters());
        assertEquals("ground/noise.png", ground.noise());
        assertEquals("ground/variation.png", ground.variation());
        assertEquals("ground/rock.png", ground.cliffs());
        assertTrue(ground.cliffSlope() > 0f && ground.cliffSlope() < 45f,
                "a cliff is drawn before the ground is too steep to walk, at 45 degrees");
        assertEquals(5, ground.tints().size());
        assertTrue(ground.streamCells() > 0);
    }

    @Test
    void aGameThatWritesNoneDrawsPaintAsItAlwaysWas() {
        assertFalse(PaintedGround.DEFAULTS.blend());
        assertFalse(PaintedGround.DEFAULTS.quarters());
        assertEquals(null, PaintedGround.DEFAULTS.cliffs());
        assertEquals(0, PaintedGround.DEFAULTS.streamCells());
    }

    @Test
    void numbersOutsideTheirRangeAreRefused() {
        refused("Raggedness = 1.5", "Raggedness");
        refused("Sharpness = 0.5", "Sharpness");
        refused("NoiseCoverage = 0", "coverage");
        refused("CliffSlope = 90", "CliffSlope");
        refused("StreamCells = -1", "StreamCells");
        refused("Tints = [ground/grass.png = 0x1000000]", "tint");
        refused("Variations = [ground/grass.png = 2]", "variation");
    }

    /** And every surface it paints is one PaintedGround gives a look, so none is drawn plain by an oversight. */
    @Test
    void everySurfaceItPaintsHasALookOfItsOwn() {
        var settings = DungeonSettings.load();
        var ground = settings.paintedGround();

        for (var picture : Stages.load("painted", settings).paint().palette().values()) {
            assertTrue(ground.tints().containsKey(picture) && ground.variations().containsKey(picture), picture);
        }
        var resources = getClass().getClassLoader();
        assertNotNull(resources.getResource(ground.noise()));
        assertNotNull(resources.getResource(ground.variation()));
        assertNotNull(resources.getResource(ground.cliffs()));
    }

    private static void refused(String line, String said) {
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse("PaintedGround\n  " + line + "\nEnd\n"));
        assertTrue(thrown.getMessage().contains(said), thrown.getMessage());
    }
}
