package uz.dukeengine.dungeon.gen;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Piece;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;

/**
 * What stands and lies about on a floor that mixes biomes: each biome's grove trees on its groves, and its grass,
 * bushes, pebbles and ore scattered over its own floor and nowhere else.
 *
 * <p>A grove's tree is in the way as far as its trunk — the floor is walked finer than it is drawn, so a body goes
 * between two where they leave it room. Everything else is for the look alone: no footprint, so nothing walks into
 * it, which is also why it may lie anywhere on the floor, a tunnel included. Only the way in is kept clear: the
 * fountain stands there, and grass through its basin is not a fountain.
 *
 * <p>Drawn from a stream of its own, in a fixed order — row by row, and each biome's scatters in the file's order —
 * so a seed lays the same scenery on every machine and turning it on moves no other draw.
 */
final class Scenery {

    private Scenery() {
    }

    /** How far from the way in's middle the floor is left bare, in cells: the fountain's five across. */
    private static final int CLEAR = 2;

    static List<Piece> scatter(long seed, Cave cave, BiomeMap biomes, Room wayIn) {
        var rng = new DeterministicRng(seed ^ 0x7363656E657279L);
        var pieces = new ArrayList<Piece>();
        // The groves first: their trees are the one piece of scenery in anybody's way, as far as the trunk. Kept off
        // the edge of their cell, so every trunk stands on ground no chamber's floor was promised as open.
        for (var cell : cave.groves()) {
            var grove = biomes.at(cell[0], cell[1]).grove();
            if (grove == null || grove.models().isEmpty()) {
                continue;
            }
            for (int n = 0; n < grove.perCell(); n++) {
                var model = grove.models().get(rng.nextInt(grove.models().size()));
                float across = cell[0] + 0.25f + rng.nextInt(51) / 100f;
                float down = cell[1] + 0.25f + rng.nextInt(51) / 100f;
                float facing = rng.nextInt(360);
                float size = grove.scale() * (1f + grove.variety() * (rng.nextInt(201) - 100) / 100f);
                pieces.add(new Piece(model, across, down, facing, size, 0xFFFFFF, grove.footprint()));
            }
        }
        var keep = cave.keep();
        for (int y = 0; y < cave.height(); y++) {
            for (int x = 0; x < cave.width(); x++) {
                if (!cave.isFloor(x, y) || Math.abs(x - wayIn.centerCellX()) <= CLEAR
                        && Math.abs(y - wayIn.centerCellY()) <= CLEAR || keep != null && keep.holds(x, y)) {
                    continue;
                }
                for (var scatter : biomes.at(x, y).scenery()) {
                    if (rng.nextInt(100) >= scatter.perHundred()) {
                        continue;
                    }
                    // Anywhere in the cell but its very edge, turned any way, a little bigger or smaller.
                    float across = x + 0.15f + rng.nextInt(71) / 100f;
                    float down = y + 0.15f + rng.nextInt(71) / 100f;
                    float facing = rng.nextInt(360);
                    float size = scatter.scale() * (1f + scatter.variety() * (rng.nextInt(201) - 100) / 100f);
                    pieces.add(new Piece(scatter.model(), across, down, facing, size, scatter.tint(), 0f));
                }
            }
        }
        return List.copyOf(pieces);
    }
}
