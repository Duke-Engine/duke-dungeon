package uz.dukeengine.dungeon.content;

/**
 * The shipped files with the descent mixing exactly the biomes a test names — or none — and everything else as
 * shipped.
 *
 * <p>So a test of what a biome does says which biomes it means, rather than leaning on whatever the file lists
 * this week; and a test of the old whole-floor themes can still have them.
 */
public final class BiomeFile {

    private BiomeFile() {
    }

    /** The shipped settings, the descent mixing {@code biomes} in that order — none, for a floor of one theme. */
    public static DungeonSettings listing(String... biomes) {
        return DungeonSettings.parse(textListing(biomes));
    }

    /** The same, as the text of the files, for a test that changes something more. */
    public static String textListing(String... biomes) {
        var endless = ShippedBlock.of("Endless");
        var line = biomes.length == 0 ? "" : "  Biomes = [" + String.join(", ", biomes) + "]\n";
        var listed = endless.text().replaceFirst("(?m)^  Biomes = .*\\n", "")
                .replace("  Name = Endless\n", "  Name = Endless\n" + line);
        return Content.data().replace(endless.text(), listed);
    }
}
