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

    private static final List<String> FOLDERS = List.of("models/characters/realistic/", "models/props/realistic/");
    /** The hero lives with the heroes; he is named, not moved. */
    private static final List<String> FILES = List.of("models/characters/heroes/hero_lange.glb");

    private RealisticModels() {
    }

    /** Whether the model at this resource path ({@code models/...}) keeps its maps. */
    public static boolean keepsMaps(String path) {
        return path != null && (FILES.contains(path) || FOLDERS.stream().anyMatch(path::startsWith));
    }
}
