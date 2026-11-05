package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.AnimComposer;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;

/**
 * The world's gates as models (art/models/fort_models.py): shut as the file rests, and the `Open` clip swings the leaves in /
 * lifts the portcullis, the clip the open gate plays as it appears (data/props/world_gate.duke).
 */
class WorldGateClipTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    private static Spatial named(Spatial from, String name) {
        if (name.equals(from.getName())) {
            return from;
        }
        if (from instanceof Node node) {
            for (var child : node.getChildren()) {
                var found = named(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static AnimComposer composerOf(Spatial model) {
        var own = model.getControl(AnimComposer.class);
        if (own != null || !(model instanceof Node node)) {
            return own;
        }
        for (var child : node.getChildren()) {
            var found = composerOf(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Where the part is once the clip is at {@code time} seconds, in the model's own frame. */
    private static Vector3f at(Spatial model, String part, String clip, double time) {
        var composer = composerOf(model);
        assertNotNull(composer, "no clips on the model");
        assertTrue(composer.getAnimClipsNames().contains(clip), composer.getAnimClipsNames().toString());
        var action = composer.setCurrentAction(clip, AnimComposer.DEFAULT_LAYER, true);
        action.setSpeed(0);
        composer.setTime(AnimComposer.DEFAULT_LAYER, time);
        composer.update(1f / 60);
        model.updateGeometricState();
        var spatial = named(model, part);
        return spatial.getWorldBound().getCenter().clone();
    }

    @Test
    void theTownGateRestsShutAndItsLeavesSwingInWhenOpened() {
        var model = ASSETS.loadModel("models/buildings/realistic/town_gate.glb");
        model.updateGeometricState();
        var shut = named(model, "Gate_Leaf_L").getWorldBound().getCenter().clone();
        var opened = at(model, "Gate_Leaf_L", "Open", 1.99);
        // A leaf 1.5 m across, hinged at the passage's edge, shut across it: open it lies along the passage, a metre and a
        // half in from where it stood and a metre and a half toward the city.
        assertTrue(shut.distance(opened) > 1f, "the leaf did not move: " + shut + " then " + opened);
    }

    @Test
    void thePortcullisRestsDownAndRisesWhenOpened() {
        var model = ASSETS.loadModel("models/buildings/realistic/fort_portcullis.glb");
        model.updateGeometricState();
        var down = named(model, "Portcullis").getWorldBound().getCenter().y;
        var up = at(model, "Portcullis", "Open", 1.99).y;
        assertEquals(3.3f, up - down, 0.2f, "it rises into the vault");
    }
}
