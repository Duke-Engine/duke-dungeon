package uz.dukeengine.dungeon.stage;

import java.util.List;
import java.util.Map;
import uz.dukeengine.core.data.Paint;
import uz.dukeengine.core.map.Dressed;
import uz.dukeengine.core.map.Painted;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;

/**
 * What a stage's ground is painted with, as the client is handed it beside the grid: the paint rows, a character a
 * cell, and what each character is. A stage that carries this is drawn as painted ground rather than by the kit of a
 * look — see {@code Visuals.paintBlend} — and nothing in the simulation reads a word of it but the footprints of its
 * scenery, which stand in the way.
 *
 * @param name     the stage's own name, which the client says it by
 * @param rows     a row of characters for each row of cells
 * @param palette  each character's picture, as the game's resources name it, or a colour; in the order written
 * @param coverage how many cells one copy of each picture covers
 * @param scenery  what stands about on the ground for its look — trees, rocks, grass — in the order written
 */
public record StagePaint(String name, @Paint List<String> rows, Map<String, String> palette,
        Map<String, Float> coverage, List<GeneratedDungeon.Piece> scenery) implements Painted, Dressed {

    public StagePaint {
        scenery = scenery == null ? List.of() : List.copyOf(scenery);
    }

    /** Painted ground with nothing standing on it. */
    public StagePaint(String name, List<String> rows, Map<String, String> palette, Map<String, Float> coverage) {
        this(name, rows, palette, coverage, List.of());
    }
}
