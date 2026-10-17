package uz.dukeengine.dungeon.stage;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.dungeon.gen.DeterministicRng;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Piece;
import uz.dukeengine.dungeon.map.StaticMap.Zone;
import uz.dukeengine.dungeon.world.PaintedGround;

/**
 * What grows on a painted stage's ground: grass, flowers, ferns, reeds, dry tufts -- many small pieces of scenery, for
 * the look alone. None has a footprint, so the simulation never reads one (it reads scenery for its footprints and
 * nothing else); they are laid after the map's own scenery, so a felled tree keeps its number; and every machine grows
 * the same ones from the map's seed. What grows on which surface, and how thickly, is the scene's {@link
 * PaintedGround#cover()}: nothing on a road, in water or stone, on a cliff, under a wall or a rock, or in a zone it
 * keeps off.
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

    /** The cover {@code paint} grows over {@code floor}, from {@code seed}; none where the scene names none. */
    public static List<Piece> lay(StagePaint paint, GeneratedDungeon floor, List<Zone> zones, PaintedGround ground,
            long seed) {
        if (ground.cover().isEmpty() || paint.rows().isEmpty()) {
            return List.of();
        }
        var rows = paint.rows();
        int height = rows.size();
        int width = rows.getFirst().length();
        // Each paint character's kinds, their total weight and how many a cell.
        var kinds = new List<?>[128];
        var total = new int[128];
        var perCell = new float[128];
        for (var entry : paint.palette().entrySet()) {
            var written = ground.cover().get(entry.getValue());
            char c = entry.getKey().charAt(0);
            if (written == null || c >= 128) {
                continue;
            }
            var these = kinds(written);
            kinds[c] = these;
            total[c] = these.stream().mapToInt(Kind::weight).sum();
            perCell[c] = ground.coverPerCell().getOrDefault(entry.getValue(), 0f);
        }
        var bare = bare(paint, floor, zones, ground, width, height);
        var rng = new DeterministicRng(seed ^ 0x636F766572L);
        var pieces = new ArrayList<Piece>();
        for (int y = 0; y < height; y++) {
            var row = rows.get(y);
            for (int x = 0; x < width; x++) {
                char c = row.charAt(x);
                if (c >= 128 || kinds[c] == null || bare[y * width + x]) {
                    continue;
                }
                float share = perCell[c];
                int count = (int) share + (rng.nextInt(1000) < (share - (int) share) * 1000 ? 1 : 0);
                for (int i = 0; i < count; i++) {
                    int pick = rng.nextInt(total[c]);
                    Kind kind = null;
                    for (var each : kinds[c]) {
                        kind = (Kind) each;
                        pick -= kind.weight();
                        if (pick < 0) {
                            break;
                        }
                    }
                    float across = x + (5 + rng.nextInt(91)) / 100f;
                    float down = y + (5 + rng.nextInt(91)) / 100f;
                    float size = kind.scale() * (80 + rng.nextInt(41)) / 100f;
                    pieces.add(new Piece(kind.model(), across, down, rng.nextInt(360), Math.round(size * 100) / 100f,
                            kind.tint(), 0f));
                }
            }
        }
        return pieces;
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
