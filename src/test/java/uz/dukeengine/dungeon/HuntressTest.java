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

    /**
     * The bow's grip stays in her fist in every clip: her middle finger's joints are curled round the slot
     * it hangs from, each within the grip's radius and a finger of it, whatever the clip does with her arm.
     * A grip held in the palm with the fingers open beside it -- what she had -- is 5 to 8 cm off them.
     */
    @Test
    void theBowStaysInHerFistInEveryClip() {
        var model = her();
        var library = ASSETS.loadModel("animations/characters/huntress.glb");
        var composer = AnimationLibrary.findControl(library, com.jme3.anim.AnimComposer.class);
        AnimationLibrary.copy(library, model, composer.getAnimClipsNames());
        var mine = AnimationLibrary.findControl(model, com.jme3.anim.AnimComposer.class);
        var armature = AnimationLibrary.findControl(model, SkinningControl.class).getArmature();
        var slot = armature.getJoint("handslot.l");
        var joints = java.util.List.of(armature.getJoint("R_MiddleF1_028"), armature.getJoint("R_MiddleF2_029"));
        assertTrue(mine.getAnimClipsNames().size() >= 10, "her clips did not arrive: " + mine.getAnimClipsNames());
        for (var clip : mine.getAnimClipsNames()) {
            mine.setCurrentAction(clip);
            for (int step = 0; step < 32; step++) {
                mine.update(0.05f);
                model.updateLogicalState(0.05f);
                armature.update();
                if (step < 12) {
                    continue; // jME cross-fades into a clip from the last pose -- here the open hand at rest
                }
                for (var joint : joints) {
                    float apart = slot.getModelTransform().getTranslation()
                            .distance(joint.getModelTransform().getTranslation());
                    assertTrue(apart < 0.045f, "in " + clip + " the bow's grip is " + apart + " from her "
                            + joint.getName() + ": her fingers are not round it");
                }
            }
        }
    }

    @Test
    void theRogueIsDrawnAsTheHuntress() {
        var rogue = SETTINGS.heroNamed("Rogue");
        assertEquals(MODEL, rogue.model());
        assertEquals(java.util.List.of("animations/characters/huntress.glb"), rogue.animations(),
                "her clips are on her own skeleton, not the kit's");
    }

    /** The elven bow in her left hand and its dagger on her hip, each on a bone her exporter gives her. */
    @Test
    void sheCarriesTheElvenBowAndItsDagger() {
        var held = SETTINGS.heroNamed("Rogue").held();
        assertEquals(java.util.List.of("models/weapons/elven/bow.glb", "models/weapons/elven/dagger.glb"),
                held.stream().map(h -> h.model()).toList());
        assertEquals(java.util.List.of("handslot.l", "sheath.l"), held.stream().map(h -> h.bone()).toList());
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
