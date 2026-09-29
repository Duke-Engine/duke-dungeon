package uz.dukeengine.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.world.Theme;

/** What a map says about the biomes it mixes, and what it may not say. */
class BiomeSettingsTest {

    /** Off as shipped: a floor wears one look until the client can draw one per cell. */
    @Test
    void theShippedDescentMixesNone() {
        assertTrue(DungeonSettings.load().biomes().isEmpty());
    }

    @Test
    void aMapThatListsBiomesGetsThoseThemesInItsOrder() {
        var biomes = BiomeFile.listing("Dungeon", "Forest").biomes();

        assertEquals(List.of("Dungeon", "Forest"), biomes.all().stream().map(Theme::name).toList());
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
}
