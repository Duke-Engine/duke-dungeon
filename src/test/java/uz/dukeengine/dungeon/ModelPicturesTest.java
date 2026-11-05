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
import uz.dukeengine.dungeon.content.RealisticModels;

/**
 * Every model the game ships carries its base colour and no other picture, but the realistic ones
 * ({@link RealisticModels}). Since E64 the client dresses a piece in its base colour by name, so this is a size rule,
 * not a correctness one: flat-shaded art gains nothing from normal, occlusion, metal-rough or emissive maps and they
 * only fatten the files. See art/models/base_colour_only.js. The realistic Act I models are drawn from their normal and
 * metal-rough maps (the client is told so per model: {@code UnitVisual.keepsMaps}), so they keep them, and nothing
 * else does.
 */
class ModelPicturesTest {

    @Test
    void noModelCarriesAPictureTheClientWouldDrawInsteadOfItsColours() throws IOException {
        var offending = new ArrayList<String>();
        try (var files = Files.walk(Path.of("src/main/resources/models"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".glb")).toList()) {
                var path = Path.of("src/main/resources").relativize(file).toString().replace(java.io.File.separatorChar, '/');
                if (RealisticModels.keepsMaps(path)) {
                    continue;
                }
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

    /** The other half of the rule: a realistic model that lost its maps would be drawn flat, and nothing would say so. */
    @Test
    void theRealisticModelsKeepTheirNormalMaps() throws IOException {
        var flat = new ArrayList<String>();
        try (var files = Files.walk(Path.of("src/main/resources/models"))) {
            for (var file : files.filter(f -> f.toString().endsWith(".glb")).toList()) {
                var path = Path.of("src/main/resources").relativize(file).toString().replace(java.io.File.separatorChar, '/');
                if (RealisticModels.keepsMaps(path) && !jsonOf(Files.readAllBytes(file)).contains("\"normalTexture\"")) {
                    flat.add(path);
                }
            }
        }
        assertTrue(flat.isEmpty(), "named as realistic but carrying no normal map: " + flat);
    }

    private static String jsonOf(byte[] glb) {
        int length = ByteBuffer.wrap(glb, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return new String(glb, 20, length, StandardCharsets.UTF_8);
    }
}
