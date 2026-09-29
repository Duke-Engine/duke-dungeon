package uz.dukeengine.dungeon.map;

import java.util.List;
import java.util.Map;
import uz.dukeengine.core.data.Link;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.content.Themes;
import uz.dukeengine.dungeon.world.Theme;

/**
 * A map drawn from a seed every time it is played: how big a floor is and what goes in it, which
 * themes its floors are built from, and how the descent through them grows harder.
 *
 * <p>Nothing here is a floor. It is what the generator is asked for, so the same map at the same
 * seed is the same floor on every machine, and a new seed is somewhere nobody has been.
 *
 * @param themes        the {@code Theme} each floor is built from, in order: depth one wears the
 *     first. Past the end, {@code whenExhausted} says what happens
 * @param propsPerRoom  how many things stand about in a room, fewest and most — the one the hero
 *     starts in included. Kept low: things to walk round are furniture, and a room full of
 *     furniture is a room nobody can fight in
 */
public record ProceduralMap(String name, Layout generation, @Link(Theme.class) List<String> themes,
        Themes.WhenExhausted whenExhausted,
        PerRoom propsPerRoom, Descent descent) {

    /** What a block leaves out. */
    public static final ProceduralMap DEFAULTS = new ProceduralMap("", Layout.DEFAULTS, List.of(),
            Themes.WhenExhausted.REPEAT, new PerRoom(0, 3), Descent.DEFAULTS);

    public ProceduralMap {
        themes = themes == null ? List.of() : List.copyOf(themes);
    }

    /**
     * How big a floor is and how many chambers it is cut into. What the ground between them is like — how ragged,
     * how winding, how hilly — is the theme's; see {@code Theme.Terrain}.
     *
     * @param minRoomSize     the smallest footprint a chamber is grown in, in cells across
     * @param corridorWidth   how many cells across a tunnel is at its narrowest — wide enough for the largest
     *     creature to pass, and nothing anywhere on the floor is narrower
     * @param maxRoomSpacing  how far a new chamber may sit from the nearest already placed, in cells
     */
    public record Layout(int mapWidth, int mapHeight, int minRooms, int maxRooms, int minRoomSize, int maxRoomSize,
            int roomGap, int placementAttempts, int corridorWidth, int maxRoomSpacing, int minSkeletonsPerRoom,
            int maxSkeletonsPerRoom) {

        /** What a block leaves out. */
        public static final Layout DEFAULTS = new Layout(96, 72, 5, 8, 5, 9, 1, 600, 2, 24, 2, 6);
    }

    /** Fewest and most, written {@code [0, 3]}. */
    public record PerRoom(int min, int max) {
    }

    /**
     * How the descent grows harder, floor by floor.
     *
     * @param bosses        one per floor, in order, and the list is also how many floors there are:
     *     kill the last and the run is won. Empty is a descent with no bottom
     * @param bossGuards    who stands with the boss, and how many of each — in the order written
     * @param bossGuardRing how many cells out from the boss its guard stands
     */
    public record Descent(@Link(Monster.class) List<String> bosses,
            @Link(Monster.class) Map<String, Integer> bossGuards, int bossGuardRing,
            int monsterHealthPercentPerDepth, int monsterDamagePercentPerDepth, int monsterCountPercentPerDepth,
            int bossHealthPercentPerDepth, int bossDamagePercentPerDepth, int experiencePercentPerDepth) {

        /** What a block leaves out. */
        public static final Descent DEFAULTS = new Descent(List.of(), Map.of(), 2, 25, 15, 20, 40, 25, 30);

        public Descent {
            bosses = bosses == null ? List.of() : List.copyOf(bosses);
            bossGuards = bossGuards == null ? Map.of() : bossGuards;
        }
    }
}
