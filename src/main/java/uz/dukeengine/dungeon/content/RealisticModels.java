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

    /** The world kit and the fort kit, every file of them: read behind the loading picture, not a stall on the first walk. */
    public static List<String> sceneryToReadFirst() {
        var models = new java.util.ArrayList<String>();
        for (var tree : List.of("spruce_a", "spruce_b", "spruce_c", "pine_a", "pine_b", "oak_a", "oak_b", "birch_a", "birch_b",
                "dead_a", "dead_b")) {
            models.add("models/world_kit/" + tree + ".glb");
            models.add("models/world_kit/" + tree + "_far.glb");
        }
        for (var piece : List.of("bush_a", "bush_b", "bush_berry", "bush_bare", "pumpkin_patch", "fern", "grass_tuft", "grass_short", "rock_a", "rock_b", "rock_c",
                "rock_moss", "crag_a", "crag_b", "crag_c", "stump_a", "stump_moss", "column_stump", "fence", "banner_post",
                "house_a", "house_b", "house_c", "house_long", "row_a", "row_b", "row_c", "fort_wall_ruined")) {
            models.add("models/world_kit/" + piece + ".glb");
        }
        for (var piece : List.of("town_wall_long", "town_wall_tower", "town_gatehouse", "town_tower_round",
                "town_tower_round_low", "fort_wall", "fort_wall_gable", "fort_gatehouse", "fort_tower_cone", "fort_turret",
                "fort_keep", "dark/fort_wall", "dark/fort_wall_gable", "dark/fort_gatehouse", "dark/fort_tower_cone",
                "dark/fort_turret", "dark/fort_keep")) {
            models.add("models/buildings/fort_kit/" + piece + ".glb");
        }
        return models;
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
