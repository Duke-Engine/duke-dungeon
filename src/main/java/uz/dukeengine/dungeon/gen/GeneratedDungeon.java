package uz.dukeengine.dungeon.gen;

import java.util.List;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The result of generating a dungeon: everything the game needs to lay one out,
 * and nothing about how it is drawn.
 *
 * <p>This is plain data on purpose. Generation lives entirely below the client —
 * it hands back an ASCII map and world positions, and the 3D client decides how
 * to render them. That separation is what lets the whole generator be tested
 * headlessly, and the connectivity guarantee checked as a property of the map
 * rather than of a picture.
 *
 * @param asciiMap  the map in the same {@code #}=stone / {@code .}=floor form the
 *                  engine's {@code MapLoader} already reads; row 0 is {@code cy=0}
 * @param levelMap  the same grid again, saying which storey each cell stands on: a digit
 *                  is a storey and {@code /} is a stair. Read by
 *                  {@code MapLoader.levels}, so the rule for what may be walked
 *                  between two of them belongs to the engine rather than here. The
 *                  generator lays every floor on storey 0 and lets the relief do the
 *                  climbing; a stage drawn before it did may still have storeys
 * @param hero      where the hero starts (world units)
 * @param monsters  what fills the chambers the hero does not start in, each with
 *                  the kind that was drawn for it
 * @param boss      the one in the furthest chamber — killing it opens the way down
 * @param bossRoom  which chamber that is, so the client can point at it
 * @param rooms     the chambers' footprints, in placement order — {@code rooms[0]} is
 *                  the one the hero starts in
 * @param links     which chambers a tunnel joins, as a spanning tree. Exposed because "the
 *                  tunnels are short" is a property worth testing directly. The loops a
 *                  terrain asks for are carved and not listed: they are floor, and no
 *                  part of the guarantee
 * @param props       what stands about in the chambers — solid, and not alive
 * @param roomStoreys which storey each room stands on, in the same order as {@code rooms}:
 *                  0 for every chamber the generator grows, and whatever a stage drawn in
 *                  storeys wrote
 * @param relief      how the ground rises and falls, corner by corner, or {@code null}
 *                  where it lies flat
 */
public record GeneratedDungeon(
        String asciiMap,
        String levelMap,
        Placement hero,
        List<Monster> monsters,
        Monster boss,
        int bossRoom,
        List<Room> rooms,
        List<Link> links,
        List<Integer> roomStoreys,
        List<Prop> props,
        HeightMap relief,
        /** How tall one storey of this floor stands, in world units; 0 leaves the world's own. */
        float levelHeight) {

    /** A spot in the world, in world units (not cells). */
    public record Placement(float x, float y) {

        /**
         * The centre of a cell, which is where a dungeon stands everything it
         * places — hero, monster, boss and prop alike.
         *
         * <p>Here rather than inside the generator because a stage file writes
         * cells and reads them back. Two copies of this arithmetic is a frozen
         * dungeon that plays half a cell away from the one it was cut from, and
         * half a cell is the difference between a doorway and a wall.
         */
        public static Placement atCell(int cx, int cy) {
            float cell = PathGrid.DEFAULT_CELL_SIZE;
            return new Placement((cx + 0.5f) * cell, (cy + 0.5f) * cell);
        }

        public int cellX() {
            return (int) Math.floor(x / PathGrid.DEFAULT_CELL_SIZE);
        }

        public int cellY() {
            return (int) Math.floor(y / PathGrid.DEFAULT_CELL_SIZE);
        }
    }

    /**
     * Something to fight, and what kind of thing it is.
     *
     * <p>The kind is a name rather than a type because the list of kinds lives in
     * a data file: the generator picks from what the file describes and never
     * learns what a Runner is.
     */
    public record Monster(String kind, Placement at) {
    }

    /**
     * Something standing in a room: a pillar, a statue, a barrel.
     *
     * <p>A kind and a place, like a monster — and for the same reason. What a
     * Pillar is lives in data/props/ and how it is drawn lives in the theme; the
     * generator only decides that one goes here.
     */
    public record Prop(String kind, Placement at) {
    }

    /**
     * The footprint a chamber was grown in, in cell coordinates: its floor lies inside it, round its middle, and
     * nothing of another chamber does. A stage drawn before chambers were grown has plain rectangles of floor here.
     */
    public record Room(int x, int y, int w, int h) {

        /**
         * The cell the room is walked to and from: its middle, which is always floor.
         *
         * <p>Public because the reachability walk is not only the generator's any
         * more: a hand-edited stage is checked by the same rule, and a check that
         * measured rooms from a different corner would pass floors the generator
         * would have rejected.
         */
        public int centerCellX() {
            return x + w / 2;
        }

        public int centerCellY() {
            return y + h / 2;
        }
    }

    /**
     * A corridor between two rooms, by index. {@code from} was already part of the
     * connected set when the corridor was carved and {@code to} was joined to it by
     * this corridor — which is what makes the links a spanning tree and so the
     * dungeon one reachable space.
     */
    public record Link(int from, int to) {
    }
}
