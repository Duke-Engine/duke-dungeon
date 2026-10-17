package uz.dukeengine.dungeon.stage;

import java.util.List;
import java.util.Map;
import uz.dukeengine.core.data.Paint;
import uz.dukeengine.core.map.Dressed;
import uz.dukeengine.core.map.Painted;
import uz.dukeengine.core.map.Sectored;
import uz.dukeengine.core.map.Subdivided;
import uz.dukeengine.core.map.Zoned;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.map.StaticMap;

/**
 * What a stage's ground is painted with, as the client is handed it beside the grid: the paint rows, a character a
 * cell, and what each character is. A stage that carries this is drawn as painted ground rather than by the kit of a
 * look — see {@code Visuals.paintBlend} — and nothing in the simulation reads a word of it but the footprints of its
 * scenery, which stand in the way, and how wide its route sectors are.
 *
 * @param name     the stage's own name, which the client says it by
 * @param rows     a row of characters for each row of cells
 * @param palette  each character's picture, as the game's resources name it, or a colour; in the order written
 * @param coverage how many cells one copy of each picture covers
 * @param scenery  what stands about on the ground for its look — trees, rocks, grass — in the order written
 * @param water    its lakes and rivers, drawn by the client; the cells under them are the map's own {@code #} to block
 * @param walked   how many cells a side it is walked at for each one it is drawn at -- the world's
 *     {@code NavigationCellsPerCell} once it is played -- so a tree stands in the way as far as its trunk, and not as
 *     the whole of the ten-unit cell it stands in
 */
public record StagePaint(String name, @Paint List<String> rows, Map<String, String> palette,
        Map<String, Float> coverage, List<GeneratedDungeon.Piece> scenery, List<StaticMap.Water> water, int walked)
        implements Painted, Dressed, Zoned, Sectored, Subdivided {

    /**
     * How many cells a side a map is before its routes are looked for by sectors, and how wide a sector then is: a
     * world this wide routes across in milliseconds where a search of its whole grid takes far longer, and every
     * smaller map searches its grid whole as it always did.
     */
    static final int SECTORED_FROM = 256;
    static final int SECTOR = 32;

    public StagePaint {
        scenery = scenery == null ? List.of() : List.copyOf(scenery);
        water = water == null ? List.of() : List.copyOf(water);
        walked = Math.max(1, walked);
    }

    /** Walked a cell to a cell, as it is drawn. */
    public StagePaint(String name, List<String> rows, Map<String, String> palette, Map<String, Float> coverage,
            List<GeneratedDungeon.Piece> scenery, List<StaticMap.Water> water) {
        this(name, rows, palette, coverage, scenery, water, 1);
    }

    /** The same stage walked at {@code cellsPerCell} cells a side for each of its own. */
    public StagePaint walkedAt(int cellsPerCell) {
        return new StagePaint(name, rows, palette, coverage, scenery, water, cellsPerCell);
    }

    @Override
    public int navigationCellsPerCell() {
        return walked;
    }

    /** Painted ground with nothing standing on it. */
    public StagePaint(String name, List<String> rows, Map<String, String> palette, Map<String, Float> coverage) {
        this(name, rows, palette, coverage, List.of(), List.of());
    }

    /**
     * Whether {@code map} -- a map's record, as the client is asked about it -- is a wide world: one whose routes are
     * sectored, whose things sleep far from its heroes and whose ground fades into haze. One answer for all three.
     */
    public static boolean isWide(Object map) {
        return map instanceof StagePaint paint && paint.wide();
    }

    /** Whether it is a world wide enough to be routed by sectors — and so to sleep, see {@code Dungeon.open}. */
    public boolean wide() {
        return !rows.isEmpty() && Math.max(rows.size(), rows.getFirst().length()) >= SECTORED_FROM;
    }

    @Override
    public List<StaticMap.Water> areas() {
        return water;
    }

    @Override
    public int routeSectors() {
        return wide() ? SECTOR : 0;
    }
}
