package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.world.Theme;

/**
 * The keep's key as the game draws it: the owner's model lying on the floor where it fell, one look in every biome,
 * and its picture in the bag the size of every other.
 */
class KeyArtTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static Theme.ThemeMonster look() {
        var look = SETTINGS.looks().stream().filter(one -> one.name().equals("Key")).findFirst().orElse(null);
        assertNotNull(look, "nothing says what the key looks like");
        return look;
    }

    @Test
    void itLiesFlatOnTheFloorWhereItFellAboutHalfACellLong() {
        var model = new DesktopAssetManager(true).loadModel(look().model());
        model.updateGeometricState();
        var box = (BoundingBox) model.getWorldBound();

        assertEquals(0f, box.getCenter().y - box.getYExtent(), 0.01f, "on the floor rather than sunk into it");
        assertEquals(0f, box.getCenter().x, 0.05f, "where it fell, not beside it");
        assertEquals(0f, box.getCenter().z, 0.05f, "where it fell, not beside it");
        assertTrue(box.getYExtent() < Math.max(box.getXExtent(), box.getZExtent()) / 4f, "lying down, as a key does");
        float length = 2f * Math.max(box.getXExtent(), box.getZExtent()) * look().modelScale();
        assertTrue(length > 5f && length < 9f, "a little over half a cell long: " + length);
    }

    /** One look, named once: no theme dresses it, and outside every theme it is the owner's key. */
    @Test
    void everyBiomeDrawsItTheOneWay() {
        for (var theme : SETTINGS.themes().all()) {
            assertTrue(theme.monsters().stream().noneMatch(themed -> themed.name().equals("Key")),
                    theme.name() + " dresses the key its own way");
        }
        assertEquals(look().model(), Main.looks(SETTINGS).of("Key").modelFor(1f, java.util.Set.of()));
    }

    @Test
    void itsPictureIsTheSizeOfEveryOtherAndClearRoundTheKey() throws IOException {
        var image = ImageIO.read(KeyArtTest.class.getClassLoader().getResource("icons/items/key_crown.png"));

        assertNotNull(image, "the bag has no picture of it");
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        for (int[] corner : new int[][] {{0, 0}, {63, 0}, {0, 63}, {63, 63}}) {
            assertEquals(0, image.getRGB(corner[0], corner[1]) >>> 24, "clear in its corners, so the slot shows");
        }
        int drawn = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                drawn += (image.getRGB(x, y) >>> 24) > 0 ? 1 : 0;
            }
        }
        assertTrue(drawn > 200, "and a key in the middle: " + drawn + " pixels of it");
    }
}
