package uz.dukeengine.dungeon.world;

import java.util.Map;

/**
 * How a painted map's ground is drawn: blended from its paint as Warcraft III blends its tiles, each surface tinted and
 * varied, its steep ground drawn as rock. Look rather than rule, like the fog and the tile kit: the simulation never
 * reads it, and a floor drawn by a kit — every floor of the descent — draws nothing of it.
 *
 * @param blend             whether the ground is blended at all; no, and each cell is drawn its one surface
 * @param noise             a picture whose four channels break the borders between surfaces; none for smooth borders
 * @param raggedness        how far the noise moves a border, 0 for none to 1 for across a whole cell
 * @param sharpness         how narrow a border is: 1 a ramp across the cell, more a narrower one
 * @param noiseCoverage     how many cells one copy of the noise covers
 * @param variation         a large picture whose red channel varies each surface, so one picture does not repeat to
 *     the eye; none for no variation
 * @param variationCoverage how many cells one copy of the variation covers
 * @param tints             what each surface's picture is multiplied by, {@code 0xRRGGBB}, by the palette value that
 *     names it; one not named is drawn as its picture is
 * @param variations        how much the variation varies each surface, 0 to 1, by the palette value that names it
 * @param cliffs            the rock picture steep ground is drawn with; none, and steep ground is drawn as its paint
 * @param cliffSlope        the lean, in degrees from level, past which ground is cliff
 * @param cliffCoverage     how many cells one copy of the rock picture covers
 * @param quarters          whether each cell is cut two by two, for smoother hills and a truer blend
 * @param streamCells       how far round the camera a painted map's ground is built, a chunk at a time; 0 builds it
 *     whole
 */
public record PaintedGround(boolean blend, String noise, float raggedness, float sharpness, float noiseCoverage,
        String variation, float variationCoverage, Map<String, Integer> tints, Map<String, Float> variations,
        String cliffs, float cliffSlope, float cliffCoverage, boolean quarters, int streamCells) {

    /** What a block leaves out: no blend, no cliffs, the ground drawn a surface a cell. */
    public static final PaintedGround DEFAULTS = new PaintedGround(false, null, 0f, 1f, 8f, null, 64f, Map.of(),
            Map.of(), null, 40f, 4f, false, 0);

    public PaintedGround {
        tints = tints == null ? Map.of() : Map.copyOf(tints);
        variations = variations == null ? Map.of() : Map.copyOf(variations);
    }
}
