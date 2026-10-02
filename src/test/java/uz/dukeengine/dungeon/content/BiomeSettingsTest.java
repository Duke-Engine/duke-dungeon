package uz.dukeengine.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.map.ProceduralMap;
import uz.dukeengine.dungeon.world.Theme;

/** What a map says about the biomes it mixes, and what it may not say. */
class BiomeSettingsTest {

    /** The shipped descent mixes all six, the woods first, the worked and barren places last. */
    @Test
    void theShippedDescentMixesTheSixBiomes() {
        assertEquals(List.of("Forest", "Autumn", "PineWood", "DeadLand", "Dungeon", "Mine"),
                DungeonSettings.load().biomes().all().stream().map(Theme::name).toList());
    }

    /** And a map that lists none is back to one theme a floor. */
    @Test
    void listingNoneTurnsThemOff() {
        assertTrue(BiomeFile.listing().biomes().isEmpty());
    }

    @Test
    void aMapThatListsBiomesGetsThoseThemesInItsOrder() {
        var biomes = BiomeFile.listing("Dungeon", "Forest").biomes();

        assertEquals(List.of("Dungeon", "Forest"), biomes.all().stream().map(Theme::name).toList());
        assertEquals(new ProceduralMap.Count(3, 4), biomes.count(), "three or four regions, as the file says");
        assertEquals(15, biomes.minShare(), "none under a seventh of the floor, near enough");
        assertEquals(40, biomes.size(), "a sweep of weather about forty cells across, as the file says");
        assertEquals(new Theme.Climate(-6, -5), biomes.perDepth(), "and the drift per floor down it gives");
    }

    /** A map that says nothing about the weather gets forty cells and no drift. */
    @Test
    void theWeatherHasDefaults() {
        var data = BiomeFile.textListing("Dungeon", "Forest").replace("  BiomeSize = 40\n", "")
                .replace("  ClimatePerDepth = [-6, -5]\n", "");
        var biomes = DungeonSettings.parse(data).biomes();

        assertEquals(40, biomes.size());
        assertEquals(new Theme.Climate(0, 0), biomes.perDepth());
    }

    /** And one that says nothing about its regions gets three or four, none under fifteen percent. */
    @Test
    void theRegionsHaveDefaults() {
        var data = BiomeFile.textListing("Dungeon", "Forest").replace("  BiomeCount = [3, 4]\n", "")
                .replace("  MinBiomeShare = 15\n", "");
        var biomes = DungeonSettings.parse(data).biomes();

        assertEquals(new ProceduralMap.Count(3, 4), biomes.count());
        assertEquals(15, biomes.minShare());
    }

    /** Every biome sits somewhere in the climate, or there is no saying where it grows. */
    @Test
    void aBiomeWithNoClimateIsRefusedByName() {
        var forest = ShippedBlock.of("Forest");
        var withoutClimate = forest.text().replaceFirst("(?m)^  Climate = .*\\n", "");
        var data = BiomeFile.textListing("Dungeon", "Forest").replace(forest.text(), withoutClimate);

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("Forest"), refused.getMessage());
    }

    @Test
    void aClimateOutsideZeroToAHundredIsRefused() {
        var forest = ShippedBlock.of("Forest");
        var data = BiomeFile.textListing("Dungeon", "Forest")
                .replace(forest.text(), forest.with("Climate", "[120, 50]").text());

        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
    }

    @Test
    void aClimateFeatureSmallerThanARoomIsRefused() {
        var data = BiomeFile.textListing("Dungeon", "Forest").replace("  BiomeSize = 40\n", "  BiomeSize = 4\n");

        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
    }

    /** A map is laid out in a region at the least, and a range that runs backwards draws from nothing. */
    @Test
    void aBiomeCountOfNoRegionsOrOneThatRunsBackwardsIsRefusedByName() {
        for (var count : List.of("[0, 4]", "[-1, 3]", "[4, 3]")) {
            var data = BiomeFile.textListing("Dungeon", "Forest")
                    .replace("  BiomeCount = [3, 4]\n", "  BiomeCount = " + count + "\n");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data), count);
            assertTrue(refused.getMessage().contains("BiomeCount"), count + ": " + refused.getMessage());
        }
    }

    /** One percent of the floor is the least a share can ask, and half of it the most that any two regions can keep. */
    @Test
    void aMinBiomeShareOutsideOneToFiftyIsRefusedByName() {
        for (var share : List.of("0", "-5", "51", "100")) {
            var data = BiomeFile.textListing("Dungeon", "Forest")
                    .replace("  MinBiomeShare = 15\n", "  MinBiomeShare = " + share + "\n");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data), share);
            assertTrue(refused.getMessage().contains("MinBiomeShare"), share + ": " + refused.getMessage());
        }
        for (var share : List.of("1", "50")) {
            var data = BiomeFile.textListing("Dungeon", "Forest")
                    .replace("  MinBiomeShare = 15\n", "  MinBiomeShare = " + share + "\n");

            assertEquals(Integer.parseInt(share), DungeonSettings.parse(data).biomes().minShare());
        }
    }

    /**
     * Asking for more regions than the share lets fit is allowed: the generator lowers the count it draws, and the
     * file says so rather than refusing it. Eight at fifty percent is a floor of two.
     */
    @Test
    void moreRegionsThanTheShareLetsFitAreNotRefused() {
        var data = BiomeFile.textListing("Dungeon", "Forest").replace("  BiomeCount = [3, 4]\n", "  BiomeCount = [3, 8]\n")
                .replace("  MinBiomeShare = 15\n", "  MinBiomeShare = 50\n");
        var biomes = DungeonSettings.parse(data).biomes();

        assertEquals(new ProceduralMap.Count(3, 8), biomes.count());
        assertEquals(50, biomes.minShare());
    }

    /** The rules about regions belong to a map that mixes biomes: one that lists none is not asked about them. */
    @Test
    void aMapThatMixesNoBiomesIsNotAskedAboutRegions() {
        var data = BiomeFile.textListing().replace("  BiomeCount = [3, 4]\n", "  BiomeCount = [0, 0]\n")
                .replace("  MinBiomeShare = 15\n", "  MinBiomeShare = 0\n");

        assertTrue(DungeonSettings.parse(data).biomes().isEmpty());
    }
}
