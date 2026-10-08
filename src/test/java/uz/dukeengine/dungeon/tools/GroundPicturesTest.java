package uz.dukeengine.dungeon.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The ground pictures are laid across the world copy after copy, so the one thing a picture may never have is a seam
 * where a copy meets the next; and they are painted from a seed, so the files shipped are what the seed paints.
 */
class GroundPicturesTest {

    /** Across the wrap a picture changes no more than it does between two neighbouring columns or rows inside it. */
    @Test
    void everyShippedPictureTilesWithoutASeam() throws IOException {
        for (var name : GroundPictures.ALL.keySet()) {
            var picture = shipped(name);
            int size = picture.getWidth();
            double across = 0;
            double within = 0;
            double down = 0;
            double withinDown = 0;
            for (int i = 0; i < size; i++) {
                across += difference(picture.getRGB(size - 1, i), picture.getRGB(0, i));
                within += difference(picture.getRGB(size / 2, i), picture.getRGB(size / 2 + 1, i));
                down += difference(picture.getRGB(i, size - 1), picture.getRGB(i, 0));
                withinDown += difference(picture.getRGB(i, size / 2), picture.getRGB(i, size / 2 + 1));
            }
            assertTrue(across <= 1.5 * within + size, name + ": its left and right edges do not meet, "
                    + across / size + " against " + within / size + " between two columns inside it");
            assertTrue(down <= 1.5 * withinDown + size, name + ": its top and bottom edges do not meet, "
                    + down / size + " against " + withinDown / size + " between two rows inside it");
        }
    }

    @Test
    void theSameSeedPaintsTheSamePicture() {
        for (var picture : GroundPictures.ALL.entrySet()) {
            assertArrayEquals(pixels(picture.getValue().apply(64)), pixels(picture.getValue().apply(64)),
                    picture.getKey());
        }
    }

    /** What the game ships is what the generator paints, pixel for pixel: nothing was touched up by hand. */
    @Test
    void theShippedPicturesAreTheGenerators() throws IOException {
        for (var picture : GroundPictures.ALL.entrySet()) {
            var shipped = shipped(picture.getKey());
            assertEquals(GroundPictures.sizeOf(picture.getKey()), shipped.getWidth(), picture.getKey());
            assertArrayEquals(pixels(picture.getValue().apply(shipped.getWidth())), pixels(shipped),
                    picture.getKey() + " is not what ./gradlew writeGroundPictures paints");
        }
    }

    private static BufferedImage shipped(String name) throws IOException {
        try (var in = GroundPicturesTest.class.getClassLoader().getResourceAsStream("ground/" + name + ".png")) {
            assertNotNull(in, "ground/" + name + ".png is not among the game's resources");
            return ImageIO.read(in);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    /** The difference between two pixels, summed over their four channels. */
    private static int difference(int a, int b) {
        int sum = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            sum += Math.abs((a >>> shift & 0xFF) - (b >>> shift & 0xFF));
        }
        return sum;
    }
}
