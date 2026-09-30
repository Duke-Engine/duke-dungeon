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
 * @param biomes        the themes one floor mixes, each where its {@code Climate} suits it; empty is a
 *     floor that wears one theme whole, chosen by depth from {@code themes}
 * @param biomeSize     about how many cells across one sweep of climate is — so how big a biome's
 *     region comes out
 * @param climatePerDepth how far the whole floor's climate drifts per floor down, wild then alive
 * @param keep          the boss's keep at the end of every floor — see {@link Keep}
 */
public record ProceduralMap(String name, Layout generation, @Link(Theme.class) List<String> themes,
        Themes.WhenExhausted whenExhausted,
        PerRoom propsPerRoom, Descent descent, @Link(Theme.class) List<String> biomes, int biomeSize,
        Theme.Climate climatePerDepth, Keep keep) {

    /** What a block leaves out. */
    public static final ProceduralMap DEFAULTS = new ProceduralMap("", Layout.DEFAULTS, List.of(),
            Themes.WhenExhausted.REPEAT, new PerRoom(0, 3), Descent.DEFAULTS, List.of(), 40,
            new Theme.Climate(0, 0), Keep.DEFAULTS);

    public ProceduralMap {
        themes = themes == null ? List.of() : List.copyOf(themes);
        biomes = biomes == null ? List.of() : List.copyOf(biomes);
        climatePerDepth = climatePerDepth == null ? new Theme.Climate(0, 0) : climatePerDepth;
        keep = keep == null ? Keep.DEFAULTS : keep;
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

    /**
     * The boss's keep: a walled court on the ground at the far end of each floor of the descent, its gate in the
     * middle of the side it is approached from — see {@code gen/Keep}.
     *
     * @param sizes how many cells across it may be, walls included, tried largest first; odd, so the boss and the
     *     gate each have a middle cell. None, and the boss waits in the furthest chamber, open, as it always did
     * @param gate  what stands in its doorway until it is opened: a template in {@code data/props/}
     * @param look  the theme it is drawn in whatever biome it stands in; blank leaves it the biome's
     */
    public record Keep(List<Integer> sizes, String gate, @Link(Theme.class) String look) {

        /** What a block leaves out: no keep at all. */
        public static final Keep DEFAULTS = new Keep(List.of(), "", "");

        public Keep {
            sizes = sizes == null ? List.of() : List.copyOf(sizes);
            gate = gate == null ? "" : gate;
            look = look == null ? "" : look;
        }
    }
}
