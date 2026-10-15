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
 * Every model the game ships carries its base colour and no other picture. Since E64 the client dresses a piece in
 * its base colour by name, so this is a size rule, not a correctness one: the engine does not light by normal,
 * occlusion, metal-rough or emissive maps, and they only fatten the files. See art/models/base_colour_only.js.
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
