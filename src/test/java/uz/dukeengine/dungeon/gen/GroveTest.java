package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.BiomeFile;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * A wood's groves on a floor that mixes biomes: trees on open ground, each in the way as far as its trunk, where a
 * whole-floor theme would have left a block of rock. The owner's complaint was two trees side by side that a hero
 * could see between and not walk between; this is the half of the answer the game gives, the engine's finer walking
 * the other.
 */
class GroveTest {

    private static final DungeonSettings WOODS = BiomeFile.listing("Forest", "Autumn", "PineWood", "DeadLand");

    private static long key(int x, int y) {
        return ((long) y << 32) | x;
    }

    /** A grove's ground is walkable, its trees stand on it with their trunks' footprints, and nobody is set down in one. */
    @Test
    void aGroveIsTreesOnOpenGroundThatNobodyIsSetDownIn() {
        int trees = 0;
        for (long seed = 1; seed <= 30; seed++) {
            var floor = DungeonGenerator.generate(seed, WOODS, 1);
            var rows = floor.asciiMap().split("\n");
            var standing = new HashSet<Long>();
            floor.monsters().forEach(monster -> standing.add(key(monster.at().cellX(), monster.at().cellY())));
            floor.props().forEach(prop -> standing.add(key(prop.at().cellX(), prop.at().cellY())));
            standing.add(key(floor.boss().at().cellX(), floor.boss().at().cellY()));
            standing.add(key(floor.hero().cellX(), floor.hero().cellY()));
            for (var piece : floor.scenery()) {
                if (piece.footprint() <= 0f) {
                    continue;
                }
                trees++;
                int x = (int) piece.x();
                int y = (int) piece.y();
                assertEquals('.', rows[y].charAt(x), "seed " + seed + ": a grove tree stands in rock at " + x + "," + y);
                assertFalse(standing.contains(key(x, y)), "seed " + seed + ": something is set down in the grove at "
                        + x + "," + y);
                assertTrue(piece.footprint() < 0.5f, "seed " + seed + ": a trunk as wide as its cell is a wall");
            }
        }
        assertTrue(trees > 30, "thirty floors of woods grew only " + trees + " grove trees");
    }

    /** A cavern's and a mine's islands are still rock: a pillar of stone is something nobody walks between. */
    @Test
    void aCavernsPillarIsStillRock() {
        var underground = BiomeFile.listing("Dungeon", "Mine");
        for (long seed = 1; seed <= 20; seed++) {
            assertTrue(DungeonGenerator.generate(seed, underground, 1).scenery().stream()
                    .noneMatch(piece -> piece.footprint() > 0f), "seed " + seed + " grew a grove underground");
        }
    }
}
