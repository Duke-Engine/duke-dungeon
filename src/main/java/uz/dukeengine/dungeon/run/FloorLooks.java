package uz.dukeengine.dungeon.run;

import java.util.List;
import uz.dukeengine.core.map.Dressed;
import uz.dukeengine.core.map.Looked;
import uz.dukeengine.core.map.Subdivided;
import uz.dukeengine.dungeon.gen.BiomeMap;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.gen.Keep;

/**
 * What a floor that mixes biomes is, beyond its grid: every cell in its biome's look — the tone this floor draws for
 * it, by the name the client registered the theme and tone under — each biome's scenery standing on its own floor,
 * and how finely the floor is walked. The keep's cells are the one exception: they wear the keep's look, whatever
 * biome they stand in.
 *
 * <p>The looks are the client's alone. The scenery is too, but for a grove's trees, whose trunks the simulation keeps
 * bodies out of; and the walking is the simulation's. Every machine grows the same biomes from the same seed, so
 * every machine reads the same record.
 *
 * @param looks one per biome, in {@link BiomeMap#biomes()}'s order; null for a biome that has no tone to wear,
 *     which the client draws in the floor's own look
 * @param navigationCellsPerCell how many cells a side the floor is walked at for each one it is drawn at
 * @param keep     the boss's keep, drawn in {@code keepLook} whatever biome it stands in; null for none
 * @param keepLook the look the keep is drawn in, by the name the client registered it under; null for the biome's
 */
record FloorLooks(String name, BiomeMap biomes, List<String> looks, List<GeneratedDungeon.Piece> scenery,
        int navigationCellsPerCell, Keep keep, String keepLook) implements Looked, Dressed, Subdivided {

    @Override
    public String lookAt(int cx, int cy) {
        if (keep != null && keepLook != null && keep.holds(cx, cy)) {
            return keepLook;
        }
        return looks.get(biomes.indexAt(cx, cy));
    }
}
