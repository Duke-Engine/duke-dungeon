package uz.dukeengine.dungeon.content;

import java.util.List;
import uz.dukeengine.dungeon.world.Theme;

/**
 * The themes one floor of the descent mixes, and how the climate that places them is laid.
 *
 * @param all      the biomes, in the file's order — which is also how a tie between two equally near is broken
 * @param size     about how many cells across one sweep of climate is
 * @param perDepth how far the floor's whole climate drifts per floor down
 */
public record Biomes(List<Theme> all, int size, Theme.Climate perDepth) {

    public Biomes {
        all = List.copyOf(all);
    }

    /** A floor that wears one theme whole. */
    public boolean isEmpty() {
        return all.isEmpty();
    }
}
