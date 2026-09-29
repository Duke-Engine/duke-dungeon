package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.BiomeFile;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** What lies about on a floor that mixes biomes: each biome's own, on its own floor, as often as it says. */
class SceneryTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    @Test
    void aSeedLaysTheSameScenery() {
        assertEquals(DungeonGenerator.generate(5L, SHIPPED, 2).scenery(),
                DungeonGenerator.generate(5L, SHIPPED, 2).scenery());
    }

    /** Every piece on floor, in a biome that lists what it is, and none where the fountain stands. */
    @Test
    void everyPieceLiesOnItsOwnBiomesFloor() {
        for (long seed = 1; seed <= 20; seed++) {
            var floor = DungeonGenerator.generate(seed, SHIPPED, 1);
            var rows = floor.asciiMap().split("\n");
            var wayIn = floor.rooms().getFirst();
            assertFalse(floor.scenery().isEmpty(), "seed " + seed + " has nothing lying about at all");
            for (var piece : floor.scenery()) {
                int x = (int) piece.x();
                int y = (int) piece.y();
                assertEquals('.', rows[y].charAt(x), "seed " + seed + ": " + piece + " is in the rock");
                var biome = floor.biomes().at(x, y);
                // A grove's tree is the grove's; everything else is one of the biome's scatters.
                boolean grows = piece.footprint() > 0f
                        ? biome.grove() != null && biome.grove().models().contains(piece.model())
                        : biome.scenery().stream().anyMatch(scatter -> scatter.model().equals(piece.model()));
                assertTrue(grows, "seed " + seed + ": " + piece.model() + " stands in " + biome.name()
                        + ", which grows none");
                assertFalse(Math.abs(x - wayIn.centerCellX()) <= 2 && Math.abs(y - wayIn.centerCellY()) <= 2,
                        "seed " + seed + ": " + piece + " lies where the fountain stands");
            }
        }
    }

    /** As often as the file says: the wood's short grass on about a third of its floor, over many floors. */
    @Test
    void asOftenAsTheBiomeSays() {
        int cells = 0;
        int grass = 0;
        for (long seed = 1; seed <= 20; seed++) {
            var floor = DungeonGenerator.generate(seed, SHIPPED, 1);
            var rows = floor.asciiMap().split("\n");
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    if (rows[y].charAt(x) == '.' && floor.biomes().at(x, y).name().equals("Forest")) {
                        cells++;
                    }
                }
            }
            grass += (int) floor.scenery().stream()
                    .filter(piece -> piece.model().endsWith("forest/grass_short.gltf")
                            && floor.biomes().at((int) piece.x(), (int) piece.y()).name().equals("Forest"))
                    .count();
        }
        double share = (double) grass / cells;
        assertTrue(share > 0.28 && share < 0.40, "the file asks for 35 a hundred and got " + share * 100);
    }

    /** A floor that wears one theme whole — a stage's — has none: the stage's file has nowhere to keep it. */
    @Test
    void aFloorOfOneThemeHasNone() {
        assertTrue(DungeonGenerator.generate(5L, BiomeFile.listing(), 1).scenery().isEmpty());
    }
}
