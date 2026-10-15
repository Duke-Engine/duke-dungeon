package uz.dukeengine.dungeon.story;

/**
 * A named place a story adds to a map from its own data, written as a map's Zones line ({@code name x y radius} or
 * {@code name x y width height}, in cells): where its people stand and its triggers listen, without the map's file —
 * the open world's is written by a program (see {@code docs/plan/2026-10-15-act2.md}).
 */
public record Place(String map, String zone) {

    public static final Place DEFAULTS = new Place("", "");
}
