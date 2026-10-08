package uz.dukeengine.dungeon.map;

import java.util.List;
import java.util.Map;
import uz.dukeengine.core.data.Grid;
import uz.dukeengine.core.data.Paint;
import uz.dukeengine.core.data.Relief;
import uz.dukeengine.core.map.Described;
import uz.dukeengine.core.map.MapTemplate;
import uz.dukeengine.core.map.Painted;
import uz.dukeengine.core.map.Peopled;
import uz.dukeengine.core.thing.Layered;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.content.Prop;

/**
 * A map drawn once and never again: the same rooms, the same monsters in the same corners, every
 * time — so that losing teaches something. Drawn from a seed by {@code :dungeon:newMap}, filled on
 * the IDE's Map tab, and what a stage is played from.
 *
 * <p>Every position is a cell, counted from the top-left of {@link #cells}, which a dungeon
 * stands everything it places in the middle of.
 *
 * @param name        its machine name, what a command line asks for
 * @param difficulty  the depth it is fought at, and so the whole of how hard it is
 * @param players     how many it was built for
 * @param seed        the seed the floor was cut from: the loot and the look of the place are still
 *     drawn from it, so a map plays the same way every time rather than merely having the same shape
 * @param entrance    where the hero comes in; none while an author is still deciding
 * @param cells       the floor, a row of characters for each row of cells: {@code #} is stone, a
 *     digit the storey a cell stands on, {@code /} a stair
 * @param relief      how the floor rises and falls over its storeys: a row of whole numbers for every row of cell
 *     corners, each that corner's height in steps of a sixteenth of a cell; none, and every floor is flat
 * @param rooms       the rooms the floor was cut into, in the order they were placed — the first is
 *     where the hero starts
 * @param links       which rooms a corridor joins, by their place in {@code rooms}
 * @param boss        who waits in the last room; killing it wins the map
 * @param levelHeight how tall one of its storeys is, in world units; 0 leaves the world's own. How WIDE a cell
 *     is stays the engine's here: a dungeon counts sight and placement in cells of {@code PathGrid.DEFAULT_CELL_SIZE},
 *     so a map at another width would be a map its own monsters could not see across
 * @param paint       what the ground is painted with, a row of characters for each row of cells, each one the palette
 *     names; none, and the floor is drawn by the kit of its look, as every floor of the descent is
 * @param palette     what each character of the paint is: a picture's path under the game's resources, or a colour
 * @param coverage    how many cells one copy of each picture covers, by the palette's characters
 */
public record StaticMap(String name, String displayName, String description, int difficulty, int players, long seed,
        float levelHeight,
        Cell entrance, @Grid List<String> cells, @Relief List<String> relief, List<Room> rooms, List<Link> links,
        @uz.dukeengine.core.data.Link(Monster.class) Placed boss, @uz.dukeengine.core.data.Link(Monster.class) List<Placed> monsters,
        @uz.dukeengine.core.data.Link(Prop.class) List<Placed> props,
        @Paint List<String> paint, Map<String, String> palette, Map<String, Float> coverage,
        @uz.dukeengine.core.data.Link(Monster.class) List<Placed> npcs, List<Zone> zones, List<Placed> items,
        List<String> quests)
        implements MapTemplate, Described, Peopled, Layered, Painted {

    /** What a block leaves out. */
    public static final StaticMap DEFAULTS = new StaticMap("", "", "", 1, 1, 0L, 0f, null, List.of(), List.of(),
            List.of(), List.of(), null, List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(),
            List.of(), List.of());

    public StaticMap {
        cells = cells == null ? List.of() : List.copyOf(cells);
        relief = relief == null ? List.of() : List.copyOf(relief);
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        links = links == null ? List.of() : List.copyOf(links);
        monsters = monsters == null ? List.of() : List.copyOf(monsters);
        props = props == null ? List.of() : List.copyOf(props);
        paint = paint == null ? List.of() : List.copyOf(paint);
        // Kept in the order written: the blend counts a corner's surfaces in the palette's order.
        palette = palette == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(palette));
        coverage = coverage == null ? Map.of() : Map.copyOf(coverage);
        npcs =npcs == null ? List.of() : List.copyOf(npcs);
        zones = zones == null ? List.of() : List.copyOf(zones);
        items = items == null ? List.of() : List.copyOf(items);
        quests = quests == null ? List.of() : List.copyOf(quests);
    }

    /**
     * A named place, in cells, for a quest to send a hero to: {@code name x y width height}, a rectangle from its
     * top-left cell, or {@code name x y radius}, a circle round a cell's middle. Zero {@code radius} is a rectangle.
     */
    public record Zone(String name, int x, int y, int width, int height, int radius) {

        public static Zone of(String written) {
            var words = written.strip().split("\\s+");
            if (words.length != 4 && words.length != 5) {
                throw new IllegalArgumentException("'" + written
                        + "' is a name, then x y width height (a rectangle) or x y radius (a circle), in cells");
            }
            int x = Integer.parseInt(words[1]);
            int y = Integer.parseInt(words[2]);
            return words.length == 4 ? new Zone(words[0], x, y, 0, 0, Integer.parseInt(words[3]))
                    : new Zone(words[0], x, y, Integer.parseInt(words[3]), Integer.parseInt(words[4]), 0);
        }

        /** Whether the world point {@code (px, py)} is in it, a cell being {@code cell} units across. */
        public boolean holds(float px, float py, float cell) {
            if (radius > 0) {
                float dx = px - (x + 0.5f) * cell;
                float dy = py - (y + 0.5f) * cell;
                return dx * dx + dy * dy <= radius * cell * radius * cell;
            }
            int cx = (int) Math.floor(px / cell);
            int cy = (int) Math.floor(py / cell);
            return cx >= x && cx < x + width && cy >= y && cy < y + height;
        }

        /** Its middle, in world units, a cell being {@code cell} across. */
        public float middleX(float cell) {
            return radius > 0 ? (x + 0.5f) * cell : (x + width / 2f) * cell;
        }

        public float middleY(float cell) {
            return radius > 0 ? (y + 0.5f) * cell : (y + height / 2f) * cell;
        }

        /** Its cells, {@code [x, y]} each, in reading order: the middle's first for a circle. Never empty. */
        public java.util.List<int[]> cells() {
            var cells = new java.util.ArrayList<int[]>();
            if (radius > 0) {
                cells.add(new int[] {x, y});
                for (int cy = y - radius; cy <= y + radius; cy++) {
                    for (int cx = x - radius; cx <= x + radius; cx++) {
                        if ((cx != x || cy != y) && (cx - x) * (cx - x) + (cy - y) * (cy - y) <= radius * radius) {
                            cells.add(new int[] {cx, cy});
                        }
                    }
                }
            } else {
                for (int cy = y; cy < y + Math.max(1, height); cy++) {
                    for (int cx = x; cx < x + Math.max(1, width); cx++) {
                        cells.add(new int[] {cx, cy});
                    }
                }
            }
            return cells;
        }

        /** As a map file writes it. */
        public String written() {
            return radius > 0 ? name + " " + x + " " + y + " " + radius
                    : name + " " + x + " " + y + " " + width + " " + height;
        }
    }

    /** A cell, written {@code [x, y]}. */
    public record Cell(int x, int y) {
    }

    /** A room: {@code x y width height storey}. */
    public record Room(int x, int y, int width, int height, int storey) {

        public static Room of(String written) {
            var numbers = numbers(written, 5, "x y width height storey");
            return new Room(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4]);
        }

        public boolean contains(int cx, int cy) {
            return cx >= x && cx < x + width && cy >= y && cy < y + height;
        }
    }

    /** A corridor: the two rooms it joins, {@code from to}. */
    public record Link(int from, int to) {

        public static Link of(String written) {
            var numbers = numbers(written, 2, "the two rooms it joins");
            return new Link(numbers[0], numbers[1]);
        }
    }

    /** One thing standing on the map: its kind, then the cell — {@code Skeleton 17 16}. */
    public record Placed(String kind, int x, int y) {

        public static Placed of(String written) {
            var words = written.strip().split("\\s+");
            if (words.length != 3) {
                throw new IllegalArgumentException("'" + written + "' is a kind, then the cell it stands in: x y");
            }
            return new Placed(words[0], Integer.parseInt(words[1]), Integer.parseInt(words[2]));
        }
    }

    private static int[] numbers(String written, int count, String what) {
        var words = written.strip().split("\\s+");
        if (words.length != count) {
            throw new IllegalArgumentException("'" + written + "' is " + what);
        }
        var numbers = new int[count];
        for (int i = 0; i < count; i++) {
            numbers[i] = Integer.parseInt(words[i]);
        }
        return numbers;
    }
}
