package uz.dukeengine.dungeon.content;

/**
 * The shipped files with the descent mixing biomes: its {@code ProceduralMap} given a {@code Biomes} line and
 * everything else as shipped.
 *
 * <p>The shipped descent lists none until the client can draw a look per cell, so every test of a biome floor
 * turns them on here rather than trusting the file to have done it.
 */
public final class BiomeFile {

    private BiomeFile() {
    }

    /** The shipped settings, the descent mixing {@code biomes} in that order. */
    public static DungeonSettings listing(String... biomes) {
        return DungeonSettings.parse(textListing(biomes));
    }

    /** The same, as the text of the files, for a test that changes something more. */
    public static String textListing(String... biomes) {
        var endless = ShippedBlock.of("Endless");
        var listed = endless.text().replace("  Name = Endless\n",
                "  Name = Endless\n  Biomes = [" + String.join(", ", biomes) + "]\n");
        return Content.data().replace(endless.text(), listed);
    }
}
