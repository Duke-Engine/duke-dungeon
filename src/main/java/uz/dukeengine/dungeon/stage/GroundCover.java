package uz.dukeengine.dungeon.stage;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.map.StaticMap.Zone;
import uz.dukeengine.dungeon.world.PaintedGround;

/**
 * What grows on a painted stage's ground: grass, flowers, ferns, reeds, dry tufts -- for the look alone, grown by the
 * client round its camera and drawn instanced (E98), never scenery and never in the simulation. What grows on which
 * surface, and how thickly, is the scene's {@link PaintedGround#cover()}: nothing on a road, in water or stone, on a
 * cliff, under a wall or a rock, or in a zone it keeps off.
 */
public final class GroundCover {

    /** One kind of cover: its model, its share by weight, its size. */
    record Kind(String model, int weight, float scale, int tint) {
    }

    private GroundCover() {
    }

    /** {@code path weight scale [0xRRGGBB]}, held apart by commas. */
    public static List<Kind> kinds(String written) {
        var kinds = new ArrayList<Kind>();
        for (var one : written.split(",")) {
            var words = one.trim().split("\\s+");
            if (words.length < 3 || words.length > 4) {
                throw new IllegalArgumentException("each cover is a model, a weight and a scale: '" + one.trim() + "'");
            }
            int weight = Integer.parseInt(words[1]);
            float scale = Float.parseFloat(words[2]);
            int tint = words.length == 4 ? Integer.decode(words[3]) : 0xFFFFFF;
            if (weight <= 0 || scale <= 0f) {
                throw new IllegalArgumentException("a cover's weight and scale are more than none: '" + one.trim() + "'");
            }
            kinds.add(new Kind(words[0], weight, scale, tint));
        }
        return kinds;
    }

    /**
     * How far round the camera the client grows cover, in map cells.
     * ponytail: one number for every map; move it into PaintedGround if a map's haze sits nearer.
     */
    static final float REACH = 32f;

    /**
     * The same cover as the client grows it round its camera (E98): each surface's kinds and share, the cells nothing
     * grows in, the map's seed. Null where the scene names no cover.
     */
    public static uz.dukeengine.client3d.GroundCover forClient(StagePaint paint, GeneratedDungeon floor,
            List<Zone> zones, PaintedGround ground, long seed) {
        if (ground.cover().isEmpty() || paint.rows().isEmpty()) {
            return null;
        }
        var kinds = new java.util.HashMap<String, List<uz.dukeengine.client3d.GroundCover.Kind>>();
        var perCell = new java.util.HashMap<String, Float>();
        for (var picture : paint.palette().values()) {
            var written = ground.cover().get(picture);
            if (written == null || kinds.containsKey(picture)) {
                continue;
            }
            kinds.put(picture, kinds(written).stream()
                    .map(k -> new uz.dukeengine.client3d.GroundCover.Kind(k.model(), k.weight(), k.scale(), k.tint()))
                    .toList());
            perCell.put(picture, ground.coverPerCell().getOrDefault(picture, 0f));
        }
        int width = paint.rows().getFirst().length();
        int height = paint.rows().size();
        var bare = bare(paint, floor, zones, ground, width, height);
        return new uz.dukeengine.client3d.GroundCover(kinds, perCell, seed,
                (cx, cy) -> cx < 0 || cy < 0 || cx >= width || cy >= height || bare[cy * width + cx], REACH, 1f);
    }

    /** The cells nothing grows in: stone and water, cliffs, under the map's walls and rocks, and the zones kept off. */
    private static boolean[] bare(StagePaint paint, GeneratedDungeon floor, List<Zone> zones, PaintedGround ground,
            int width, int height) {
        var bare = new boolean[width * height];
        var cells = floor.asciiMap().strip().lines().toList();
        var relief = floor.relief();
        for (int y = 0; y < height; y++) {
            var row = y < cells.size() ? cells.get(y) : "";
            for (int x = 0; x < width; x++) {
                bare[y * width + x] = x >= row.length() || row.charAt(x) == '#'
                        || relief != null && relief.isCliff(x, y);
            }
        }
        for (var piece : paint.scenery()) {
            float reach = piece.boxLength() > 0f ? Math.max(piece.boxLength(), piece.boxDepth()) / 2f + 0.5f
                    : piece.footprint() >= 0.3f ? piece.footprint() : -1f;
            if (reach < 0f) {
                continue;
            }
            for (int y = (int) (piece.y() - reach); y <= (int) (piece.y() + reach); y++) {
                for (int x = (int) (piece.x() - reach); x <= (int) (piece.x() + reach); x++) {
                    if (x >= 0 && y >= 0 && x < width && y < height) {
                        bare[y * width + x] = true;
                    }
                }
            }
        }
        for (var zone : zones) {
            if (!ground.coverKeepsOff().contains(zone.name())) {
                continue;
            }
            for (var cell : zone.cells()) {
                if (cell[0] >= 0 && cell[1] >= 0 && cell[0] < width && cell[1] < height) {
                    bare[cell[1] * width + cell[0]] = true;
                }
            }
        }
        return bare;
    }
}
