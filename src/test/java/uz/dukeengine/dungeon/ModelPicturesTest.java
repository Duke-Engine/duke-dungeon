package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/**
 * Every model the game ships carries its base colour and no other picture. The client dresses each piece over the
 * first texture jME's material hands it, and jME hands a normal map before the base colour: Lange was drawn in his
 * normal map, pale lavender, in the playtest of 1.0.41. See art/models/base_colour_only.js and E62.
 */
class ModelPicturesTest {

    @Test
    void noModelCarriesAPictureTheClientWouldDrawInsteadOfItsColours() throws IOException {
        var offending = new ArrayList<String>();
        try (var files = Files.walk(Path.of("src/main/resources/models"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".glb")).toList()) {
                var json = jsonOf(Files.readAllBytes(file));
                for (var map : new String[] {"normalTexture", "occlusionTexture", "emissiveTexture",
                        "metallicRoughnessTexture"}) {
                    if (json.contains("\"" + map + "\"")) {
                        offending.add(file + " " + map);
                    }
                }
            }
        }
        assertTrue(offending.isEmpty(), "run art/models/base_colour_only.js on: " + offending);
    }

    private static String jsonOf(byte[] glb) {
        int length = ByteBuffer.wrap(glb, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return new String(glb, 20, length, StandardCharsets.UTF_8);
    }
}
