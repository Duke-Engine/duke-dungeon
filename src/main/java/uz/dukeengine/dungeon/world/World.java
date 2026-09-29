package uz.dukeengine.dungeon.world;

import uz.dukeengine.core.thing.Layered;
import uz.dukeengine.core.thing.WorldTemplate;

/**
 * The world as the engine reads it: its name, and how far apart two storeys stand — every map this
 * world lays, the first floor and each one after it, stands its storeys that far apart. Everything
 * else of the world is the game's own, in blocks of their own.
 *
 * @param navigationCellsPerCell how many cells a side a floor that mixes biomes is walked at for each
 *     one it is drawn at: 2 walks ten-unit cells on five-unit ones, so a body goes between two trees
 *     where their trunks leave it room. 1 walks it as it is drawn
 */
public record World(String name, float levelHeight, int navigationCellsPerCell) implements WorldTemplate, Layered {

    /** What a block leaves out. */
    public static final World DEFAULTS = new World("Dungeon", 6f, 1);
}
