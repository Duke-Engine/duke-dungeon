package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The pictures art/icons/ItemIcons.java paints: every thing the game ships is drawn with one that is there and loads at
 * the bag's size, the purse has its coins, and the starter library's index names only files that are there, each with
 * where it came from and under what licence.
 */
class ItemIconsTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Set<String> LICENCES = Set.of("CC0", "CC BY 3.0");

    private static BufferedImage load(String path) throws IOException {
        var url = ItemIconsTest.class.getClassLoader().getResource(path);
        assertNotNull(url, "no picture at " + path);
        var image = ImageIO.read(url);
        assertNotNull(image, path + " is not a picture");
        return image;
    }

    @Test
    void everyThingTheGameShipsHasAPictureAtTheBagsSize() throws IOException {
        for (var item : SETTINGS.loot()) {
            assertTrue(item.icon().startsWith("icons/items/"), item.id() + " is drawn with " + item.icon());
            var image = load(item.icon());
            assertEquals(64, image.getWidth(), item.id());
            assertEquals(64, image.getHeight(), item.id());
            assertEquals(0, image.getRGB(0, 0) >>> 24, item.id() + ": its frame's corner is clear");
            assertEquals(255, image.getRGB(32, 32) >>> 24, item.id() + ": its middle is painted");
        }
    }

    @Test
    void thePurseHasItsCoins() throws IOException {
        var image = load(SETTINGS.story().trade().goldIcon());
        assertEquals(64, image.getWidth());
    }

    @Test
    void theLibraryIndexNamesOnlyFilesThereEachWithItsMakerAndLicence() throws Exception {
        var folder = "icons/items/library/";
        var index = new String(ItemIconsTest.class.getClassLoader().getResourceAsStream(folder + "index.csv")
                .readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        assertEquals("name,category,rarity,file,source,author,licence", index.getFirst());
        var listed = new HashSet<String>();
        for (var line : index.subList(1, index.size())) {
            var cells = line.split(",", -1);
            assertEquals(7, cells.length, line);
            assertTrue(listed.add(cells[3]), "listed twice: " + cells[3]);
            assertFalse(cells[5].isBlank(), line);
            assertTrue(LICENCES.contains(cells[6]), line);
            var image = load(folder + cells[3]);
            assertEquals(128, image.getWidth(), cells[3]);
        }
        assertTrue(listed.size() >= 100, "a library of " + listed.size());
        var dir = Path.of(ItemIconsTest.class.getClassLoader().getResource(folder + "index.csv").toURI()).getParent();
        try (var files = Files.list(dir)) {
            files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".png"))
                    .forEach(name -> assertTrue(listed.contains(name), name + " is not in the index"));
        }
    }
}
