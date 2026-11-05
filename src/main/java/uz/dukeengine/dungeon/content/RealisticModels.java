package uz.dukeengine.dungeon.content;

import java.util.List;

/**
 * Which model files keep their normal and metallic-roughness pictures, by path.
 *
 * <p>Every other model carries its base colour alone (ModelPicturesTest): flat-shaded art gains nothing from the maps
 * and they only fatten the files. The realistic characters and props of Act I are drawn from their maps, so they
 * keep them, and the client is told so for exactly these ({@code UnitVisual.keepsMaps}).
 */
public final class RealisticModels {

    private static final List<String> FOLDERS = List.of("models/characters/realistic/", "models/props/realistic/",
            "models/buildings/realistic/");
    /** The hero lives with the heroes; he is named, not moved. */
    private static final List<String> FILES = List.of("models/characters/heroes/hero_lange.glb");

    private RealisticModels() {
    }

    /** The far mesh of a tree of the world kit (models/world_kit/spruce_a.glb, ...spruce_a_far.glb); null for anything else. */
    public static String farMesh(String path) {
        return path != null && path.matches("models/world_kit/(spruce|pine|oak|birch|dead)_[abc]\\.glb")
                ? path.substring(0, path.length() - 4) + "_far.glb" : null;
    }

    /** Whether a piece of scenery casts the sun's shadow: the world kit's, all but what is too low or small to show one. */
    public static boolean castsSun(String path) {
        return path != null && path.startsWith("models/world_kit/") && !path.contains("fern") && !path.contains("grass");
    }

    /** Whether the model at this resource path ({@code models/...}) keeps its maps. */
    public static boolean keepsMaps(String path) {
        return path != null && (FILES.contains(path) || FOLDERS.stream().anyMatch(path::startsWith));
    }
}
