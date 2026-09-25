package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The fountain as every theme draws it: a model that loads with its colours, standing on its own foot, about as
 * wide as the basin it is walked round — and carrying water that is an effect the game declares.
 */
class FountainArtTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static boolean hasTexture(Spatial model) {
        if (model instanceof com.jme3.scene.Geometry geometry) {
            var material = geometry.getMaterial();
            return material != null && material.getParams().stream()
                    .anyMatch(param -> param.getValue() instanceof com.jme3.texture.Texture);
        }
        return model instanceof Node node && node.getChildren().stream().anyMatch(FountainArtTest::hasTexture);
    }

    @Test
    void everyThemeDrawsTheFountainStandingOnItsFootWithItsWater() {
        var assets = new DesktopAssetManager(true);
        int themes = 0;
        for (var theme : SETTINGS.themes().all()) {
            var look = theme.monsters().stream().filter(monster -> monster.name().equals("Fountain"))
                    .findFirst().orElse(null);
            assertNotNull(look, theme.name() + " has no fountain to draw at the way in");
            themes++;

            var model = assets.loadModel(look.model());
            assertNotNull(model, look.model());
            assertTrue(hasTexture(model), look.model() + " loads without its colours");
            model.updateGeometricState();
            var box = (BoundingBox) model.getWorldBound();
            float foot = box.getCenter().y - box.getYExtent();
            assertEquals(0f, foot, 0.01f, "the model stands on its own foot rather than sunk to its middle");
            float across = 2f * Math.max(box.getXExtent(), box.getZExtent()) * look.modelScale();
            assertTrue(across > 13f && across < 17f, "about as wide as the basin that is walked round: " + across);

            assertTrue(SETTINGS.effects().stream().anyMatch(effect -> effect.name().equals(look.effect())),
                    "the water it carries, " + look.effect() + ", is an effect the game has");
        }
        assertTrue(themes > 0);
    }
}
