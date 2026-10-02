package uz.dukeengine.dungeon.content;

import java.util.List;
import uz.dukeengine.dungeon.map.ProceduralMap;
import uz.dukeengine.dungeon.world.Theme;

/**
 * The themes one floor of the descent mixes, how many places it is laid out in, and how the climate that gives each
 * place its biome is drawn.
 *
 * @param all      the biomes, in the file's order — which is also how a tie between two equally near is broken
 * @param count    the fewest and most regions a floor is laid out in, before any is given a biome
 * @param minShare the least share of a floor's cells, in percent, one region may cover
 * @param size     about how many cells across one sweep of climate is: the weather that decides each region's biome
 * @param perDepth how far the floor's whole climate drifts per floor down
 */
public record Biomes(List<Theme> all, ProceduralMap.Count count, int minShare, int size, Theme.Climate perDepth) {

    public Biomes {
        all = List.copyOf(all);
    }

    /** A floor that wears one theme whole. */
    public boolean isEmpty() {
        return all.isEmpty();
    }
}
