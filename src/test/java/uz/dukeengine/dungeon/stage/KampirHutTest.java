package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.AnimComposer;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * Yalmog'iz kampir's hut in the dark wood: Inuciian's models (sorted by kind under models/, the manifest
 * models/assets.txt naming them his; written by art/inuciian/export_models.py) all load, stand on their foot and wear their pictures; the open world's site is
 * hers, its people stand in it and say what the story gives them.
 */
class KampirHutTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    /** Inuciian's models, as the manifest (path | pack | licence | old path) names them. */
    private static List<String> models() throws IOException {
        try (var in = KampirHutTest.class.getClassLoader().getResourceAsStream("models/assets.txt")) {
            assertNotNull(in, "no models/assets.txt");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#")).map(line -> line.split("\\|"))
                    .filter(row -> row[2].trim().equals("models/licenses/baba_yagas_hut.txt"))
                    .map(row -> row[0].trim()).filter(path -> path.endsWith(".glb")).sorted().toList();
        }
    }

    private static boolean pictured(Spatial model) {
        var found = new boolean[1];
        model.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry && geometry.getMaterial() != null) {
                found[0] |= geometry.getMaterial().getParams().stream()
                        .anyMatch(param -> param.getValue() instanceof com.jme3.texture.Texture);
            }
        });
        return found[0];
    }

    /** Every piece loads, stands with its foot on the ground about its middle and is not huge; the people and the hut wear their pictures. */
    @Test
    void everyModelLoadsStandingOnItsFoot() throws Exception {
        var all = models();
        assertTrue(all.size() >= 35, "only " + all.size() + " of Inuciian's models");
        for (var path : all) {
            var model = ASSETS.loadModel(path);
            model.updateModelBound();
            model.updateGeometricState();
            var box = (BoundingBox) model.getWorldBound();
            float bottom = box.getCenter().y - box.getYExtent();
            float height = 2 * box.getYExtent();
            assertTrue(height > 0.5f && height < 50f, path + " stands " + height + " tall");
            if (!path.contains("caged")) { // her cage hangs above the cell she stands in
                assertEquals(0f, bottom, 0.6f, path + " stands at " + bottom + " rather than on the ground");
                assertTrue(Math.abs(box.getCenter().x) < box.getXExtent() && Math.abs(box.getCenter().z)
                        < box.getZExtent(), path + " is beside its origin, not over it");
            }
            if (path.contains("people/") || path.endsWith("kampir_imp.glb") || path.endsWith("hut.glb")) {
                assertTrue(pictured(model), path + " carries no picture"); // the rest may be colours alone
            }
        }
    }

    /** The dark wood has its dark copies of the foliage, and the autumn ones are kept beside them. */
    @Test
    void theDarkWoodHasItsDarkCopiesAndKeepsTheAutumnOnes() throws Exception {
        var all = models();
        for (var kind : List.of("pine", "bush_large", "tree_leafy", "grass_tall", "grass_short")) {
            assertTrue(all.stream().anyMatch(path -> path.endsWith("/" + kind + ".glb")), kind);
            assertTrue(all.stream().anyMatch(path -> path.endsWith("/" + kind + "_dark.glb")), kind + "_dark");
        }
    }

    /** The people who move carry the clip their blocks name, Idle; the girl's cage swings. */
    @Test
    void thePeopleCarryTheirIdle() {
        for (var path : List.of("models/characters/people/baba_yaga.glb", "models/characters/monsters/kampir_imp.glb",
                "models/characters/people/damsel_caged.glb")) {
            var model = ASSETS.loadModel(path);
            var composer = new AnimComposer[1];
            model.depthFirstTraversal(spatial -> {
                if (spatial.getControl(AnimComposer.class) != null) {
                    composer[0] = spatial.getControl(AnimComposer.class);
                }
            });
            assertNotNull(composer[0], path + " carries no clips");
            assertNotNull(composer[0].getAnimClip("Idle"), path + " has no Idle");
        }
    }

    /** The world's forest site is hers: named, flat, walked to, her people standing in it with their words. */
    @Test
    void theWorldsForestSiteIsTheKampirsHut() {
        var site = WorldMapWriter.SITES.get(WorldMapWriter.HUT);
        assertEquals("Kampir_kulbasi", site.zone());
        var stage = Stages.load("world", SETTINGS);
        var zone = stage.story().zones().stream().filter(z -> z.name().equals("Kampir_kulbasi")).findFirst()
                .orElseThrow();
        assertTrue(stage.story().zones().stream().noneMatch(z -> z.name().startsWith("Maxluq")));
        var walk = StageCheck.walk(stage.floor());
        assertTrue(walk.to(zone.x(), zone.y()) >= 0, "the hut cannot be walked to");

        var story = SETTINGS.story();
        for (var who : List.of("BabaYaga", "Damsel", "Imp")) {
            var placed = stage.story().npcs().stream().filter(npc -> npc.kind().equals(who)).findFirst()
                    .orElseThrow(() -> new AssertionError(who + " is not in the world"));
            assertTrue(Math.hypot(placed.x() - zone.x(), placed.y() - zone.y()) <= zone.radius(),
                    who + " stands outside the yard");
            assertNotNull(story.npc(who), "no Npc block for " + who);
        }
        assertEquals(3, story.conversation(story.npc("BabaYaga").talk()).lines().size());
        assertEquals(2, story.conversation(story.npc("Damsel").talk()).lines().size(), "her ask, and where the key is (Act 2)");
        assertEquals("Yalmog'iz kampir", story.speaker("BabaYaga").displayName());

        var models = stage.paint().scenery().stream().map(piece -> piece.model()).distinct().toList();
        assertTrue(models.contains("models/buildings/hut/hut.glb"), "no hut in the yard");
        assertTrue(models.contains("models/nature/trees/pine_dark.glb"), "no dark pines in the dark wood");
        assertTrue(models.contains("models/terrain/forest/pine.gltf"), "the KayKit pines are gone from the wood");
        assertFalse(stage.floor().props().stream().noneMatch(prop -> prop.kind().equals("Cauldron")),
                "no cauldron");
    }
}
