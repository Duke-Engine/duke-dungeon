package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.SkinningControl;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.AnimationLibrary;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The Rogue is drawn as neilhancart's "Huntress", made game-ready by
 * art/huntress/export_huntress.py -- and what that script promises is checked on the file it wrote.
 */
class HuntressTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String MODEL = "models/heroes/huntress.glb";

    /** One loader, so her pictures are decoded once: each load of them is direct memory the tests run short of. */
    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    private static Spatial her() {
        var model = ASSETS.loadModel(MODEL);
        model.updateGeometricState();
        return model;
    }

    @Test
    void theRogueIsDrawnAsTheHuntress() {
        var rogue = SETTINGS.heroNamed("Rogue");
        assertEquals(MODEL, rogue.model());
        assertEquals(java.util.List.of("animations/characters/huntress.glb"), rogue.animations(),
                "her clips are on her own skeleton, not the kit's");
    }

    /** jME skins at most 255 joints, four to a vertex; she came with 438. */
    @Test
    void herSkeletonFitsWhatTheEngineSkins() {
        var skin = AnimationLibrary.findControl(her(), SkinningControl.class);
        assertNotNull(skin, "she is not skinned");
        int joints = skin.getArmature().getJointCount();
        assertTrue(joints <= 255, "jME's hardware skinning stops at 255 joints, and she has " + joints);
        assertNotNull(skin.getArmature().getJoint("handslot.l"), "her bow has no bone to hang from");
    }

    /** About 24k triangles, and standing 2.18 tall on the origin, as the KayKit rogue did. */
    @Test
    void sheIsTheSizeAndWeightSheWasMadeAt() {
        var model = her();
        int[] triangles = {0};
        model.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry) {
                triangles[0] += geometry.getMesh().getTriangleCount();
            }
        });
        assertTrue(triangles[0] > 20_000 && triangles[0] < 26_000, "she is " + triangles[0] + " triangles");

        var box = (BoundingBox) model.getWorldBound();
        float floor = box.getCenter().y - box.getYExtent();
        assertEquals(2.18f, box.getYExtent() * 2f, 0.05f, "her ModelScale is 12 / 2.18");
        assertEquals(0f, floor, 0.05f, "her feet are not on the origin");
    }
}
