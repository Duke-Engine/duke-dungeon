package uz.dukeengine.dungeon.world;

import uz.dukeengine.core.data.Clip;
import uz.dukeengine.core.data.Link;
import java.util.List;
import uz.dukeengine.core.content.AnimationSet;
import uz.dukeengine.core.content.Effect;
import uz.dukeengine.client3d.Held;
import uz.dukeengine.dungeon.content.MonsterLook;

/**
 * One kind of place a floor can be: the ground it is carved into, the kit it is built from, the colour of its
 * dark, its variations, and whatever creatures walk it differently.
 *
 * <p>Two halves, and only one of them is a look. The {@link Terrain} is the ground: how ragged a chamber is, how
 * a tunnel winds, how high the hills rise — a forest is open and rolling where a cellar is close and cut, and
 * that is a different floor, not the same one dressed differently. Everything else — the kit, the tones, the dark,
 * the creatures' skins — changes no cell, no monster and no number the simulation reads, which is what lets a
 * test prove two themes that differ only in look play one run.
 *
 * <p>Adding one is a file and a folder of models, and no Java: the block says what the kit is, its
 * {@code Tones} how it varies, its {@code Monsters} what lives there and its {@code Terrain} what the ground
 * is like; which depth wears which is the map's to say.
 *
 * <p>Every asset it names is a whole path from the resource root, so a kit's files may sit
 * wherever its author keeps them.
 *
 * @param tileSize      what one floor tile was modelled at, in the model's units
 * @param wallTileSize  what one wall was modelled at, when that is not the same — kits are not all
 *     authored on one module, and one of these lays two-unit floors inside four-unit walls
 * @param wallHeight    how tall a wall stands, which is where the roof over the rock is laid
 * @param wallLift      how far up a wall must move to stand on the floor rather than half sunk into
 *     it — kits disagree about where a wall's origin is
 * @param wallShift     how far back it must move for its face to land on the boundary and its body
 *     in the stone
 * @param ownMaterials  whether the models carry their own colours. A kit drawn on one atlas wants
 *     one shared skin; a kit that ships no texture and says what colour each of its parts is wants
 *     what it came with
 * @param wallFillsRock whether the wall piece is a thing that stands rather than a surface that
 *     tiles — see {@link Standing}; the next three say how such a thing is scattered
 * @param stairs        the flight of steps between two storeys, or null for a kit that ships none —
 *     then the client builds them out of blocks
 * @param rockFace      what the exposed side of a raised block of rock is drawn with, or null for a
 *     theme that needs none. Only a theme whose wall is a thing rather than a surface does: masonry
 *     walls a two-storey block in two courses and those courses ARE its sides, while a tree is drawn
 *     once on top and leaves the sides drawn by nothing at all
 * @param capTint       a colour over the lid on the rock and nothing else, packed {@code 0xRRGGBB}.
 *     The lid is a floor tile laid at the top of the walls — the same model and the same picture as
 *     the floor of a room — so without this there is nothing to tell the player which of the two he
 *     can walk on
 * @param storeyShadePercent how much lighter each storey up is drawn, as a percentage; 100 is flat.
 *     The same question as {@code capTint} one level out: a raised room is the same tiles higher up
 * @param fogTint       what the dark is coloured here, packed {@code 0xRRGGBB} — bluish under ice, red
 *     under lava, black in plain stone
 * @param terrain       the ground its floors are carved into — see {@link Terrain}
 * @param climate       where it grows when a floor mixes biomes — see {@link Climate} — or null for a theme that is
 *     only ever a whole floor's
 * @param scenery       what lies about on its floor for its look alone — grass, bushes, pebbles, ore — when it is a
 *     biome of a floor that mixes them; see {@link Scatter}
 * @param grove         what a block of rock left standing in one of its chambers is instead, when it is a biome:
 *     trees on open ground, each in the way only as far as its trunk — see {@link Grove} — or null for rock, as a
 *     cavern's pillar is
 */
public record Theme(
        String name,
        float tileSize,
        float wallTileSize,
        float wallHeight,
        float wallLift,
        float wallShift,
        boolean ownMaterials,
        boolean wallFillsRock,
        int wallClump,
        float wallSpread,
        float wallVariety,
        String stairs,
        String rockFace,
        int capTint,
        int storeyShadePercent,
        int fogTint,
        List<Tone> tones,
        List<ThemeMonster> monsters,
        Terrain terrain,
        Climate climate,
        List<Scatter> scenery,
        Grove grove) {

    /** What a block leaves out. */
    public static final Theme DEFAULTS = new Theme("", 4f, 0f, 4f, 0f, 0f, false, false, 1, 0f, 0f, null, null,
            0xFFFFFF, 100, 0, List.of(), List.of(), Terrain.DEFAULTS, null, List.of(), null);

    public Theme {
        // A kit whose walls are on the same module as its floors says so by not saying anything,
        // and then this record has to give the answer rather than the zero it was handed — or every
        // reader has to know to ask twice.
        wallTileSize = wallTileSize > 0f ? wallTileSize : tileSize;
        tones = List.copyOf(tones);
        monsters = List.copyOf(monsters);
        terrain = terrain == null ? Terrain.DEFAULTS : terrain;
        scenery = scenery == null ? List.of() : List.copyOf(scenery);
    }

    /**
     * One kind of thing that lies about on a biome's floor for its look alone: nothing walks into it, nothing reads
     * it, and every machine scatters the same ones in the same places from the same seed.
     *
     * @param model      the model, a whole path from the resource root
     * @param perHundred how many to every hundred cells of this biome's floor
     * @param scale      how large against the model as it was made — about 2.5 is the kit's own proportion
     * @param variety    how much each differs in size, as a fraction either way
     * @param tint       a colour over it, packed {@code 0xRRGGBB}; white leaves it as it was made
     */
    public record Scatter(String model, int perHundred, float scale, float variety, int tint) {

        /** What a block leaves out. */
        public static final Scatter DEFAULTS = new Scatter(null, 0, 2.5f, 0.3f, 0xFFFFFF);
    }

    /**
     * A grove: what a biome's chambers have standing in them where a cavern has a pillar of rock — trees on open
     * ground, each in the way only as far as its trunk, so a body goes between two where they leave it room and a
     * wider one goes round.
     *
     * <p>Only on a floor that mixes biomes, which is walked finer than it is drawn; a floor of one theme keeps its
     * groves as rock with the kit's trees on it, as it always had.
     *
     * @param models    the trees, whole paths from the resource root; each tree takes one by the draw
     * @param perCell   how many stand in each cell of the grove
     * @param scale     how large against the model as it was made
     * @param variety   how much each differs in size, as a fraction either way
     * @param footprint how far round its middle each is in the way, in cells: its trunk, not its crown
     */
    public record Grove(List<String> models, int perCell, float scale, float variety, float footprint) {

        /** What a block leaves out. */
        public static final Grove DEFAULTS = new Grove(List.of(), 1, 2.5f, 0.3f, 0.15f);

        public Grove {
            models = models == null ? List.of() : List.copyOf(models);
        }
    }

    /**
     * The ground a theme's floors are carved into: the shape of the place rather than what it is built from.
     *
     * <p>Chambers are grown in the footprints the map's {@code Generation} places, and joined by tunnels along the
     * same tree; what this says is what they are like. Every number is the file's, so a close stone cellar and an
     * open rolling wood come out of one generator.
     *
     * @param ragged         how far a chamber's edge strays from a smooth curve, as a percentage: 0 is an
     *     ellipse, 60 is a cave that bulges and notches all round
     * @param winding        how far a tunnel bends away from the straight line, as a percentage of its length
     * @param loops          extra tunnels beyond the ones that join every chamber, as a percentage of the
     *     chambers: a way round rather than only a way through. Never into the boss's chamber, so it is as deep
     *     as it ever was
     * @param islandsPerRoom rock standing inside a chamber — a pillar in a cavern, a grove in a glade — fewest
     *     and most, written {@code [0, 2]}. Only where floor rings it, so it never cuts anything off
     * @param rise           how high the ground rises above its lowest point, in steps of a sixteenth of a cell
     * @param hillSize       about how many cells across a hill is
     * @param slope          the steepest the ground may get across one cell, in steps: a cell is a cliff from 16,
     *     so 15 is the most there is and every cell stays walkable
     * @param level          how flat a chamber's floor is laid, as a percentage: 100 levels it at the height of its
     *     middle — so the climbing happens in the tunnels — and 0 lets the hills run straight through it
     */
    public record Terrain(int ragged, int winding, int loops,
            uz.dukeengine.dungeon.map.ProceduralMap.PerRoom islandsPerRoom, int rise, int hillSize, int slope,
            int level) {

        /** What a block leaves out, and the ground of a floor no theme was named for. */
        public static final Terrain DEFAULTS = new Terrain(25, 35, 20,
                new uz.dukeengine.dungeon.map.ProceduralMap.PerRoom(0, 1), 32, 8, 6, 50);
    }

    /**
     * Where a biome grows, as a point in the two things a floor's climate varies in, each 0 to 100.
     *
     * <p>A floor that mixes biomes draws both across itself as broad hills of noise, and every cell grows the biome
     * whose point is nearest the climate there — so a biome is wherever the weather suits it, the way a Minecraft
     * world lays its deserts and its taigas, and two biomes meet where their climates do. The map may also drift
     * the whole floor's climate with depth: the same numbers, read as a shift per floor down.
     *
     * @param wild  worked stone at 0 — halls, a mine — and wilderness at 100
     * @param alive barren at 0 — dead land, bare rock — and lush at 100
     */
    public record Climate(int wild, int alive) {
    }

    /** How the wall piece stands, gathered from the four lines that say it. */
    public Standing standing() {
        return new Standing(wallFillsRock, wallClump, wallSpread, wallVariety);
    }

    /**
     * What a theme's wall piece is, when it is not masonry.
     *
     * <p>Every number here answers the same question: is the piece a <em>surface</em> — a course
     * of stone, a panel, a fence — or a <em>thing</em>? A surface tiles. Two storeys of it is two
     * courses, every one the same size, all facing the way the boundary faces, and that is right; a
     * wall that varied would not read as a wall.
     *
     * <p>A thing does none of that. It has one body, so a block of rock one cell thick is one tree
     * rather than a face on each side of it — drawing both put a tree at the foot of the rock and a
     * second on the roof with the lid between them. Two storeys of tree is not two trees either, it
     * is a bigger tree. And a row of identical trees at identical spacing is not a wood, it is an
     * orchard: the eye reads the spacing before it reads the tree, so the grid the map is built on
     * shows straight through the art.
     *
     * @param fillsRock the piece fills a block of rock instead of facing it — once per block, in the
     *     middle of it, grown to its height
     * @param clump     how many stand where the plan asks for one
     * @param spread    how far from that point they scatter, as a fraction of a cell
     * @param variety   how much they differ in size, as a fraction either way
     */
    public record Standing(boolean fillsRock, int clump, float spread, float variety) {

        public Standing {
            clump = Math.max(1, clump);
        }
    }

    /**
     * A small variation within a theme: a different floor pattern, a different wall, a warmer or
     * colder cast over both.
     *
     * <p>Which one a floor gets is drawn from the seed, so two runs down the same seed see the same
     * room and the same stone — and a player who plays the same theme twice in one run does not see
     * the same floor twice.
     */
    public record Tone(String name, String floor, String wall, String corner, int tint, List<String> walls) {

        /** What a block leaves out. */
        public static final Tone DEFAULTS = new Tone("", null, null, null, 0xFFFFFF, List.of());

        public Tone {
            walls = walls == null ? List.of() : List.copyOf(walls);
        }

        /**
         * Every model the wall is drawn from: {@code Wall} and then the {@code Walls} beside it, each placement taking
         * one by where it stands — so a cliff line is several rocks and a wood's edge trees with bushes between them,
         * the same ones in the same places every time the floor is drawn. The client sizes them all by the theme's
         * one module, so they are modelled alike.
         */
        public List<String> allWalls() {
            var all = new java.util.ArrayList<String>();
            if (wall != null) {
                all.add(wall);
            }
            all.addAll(walls);
            return List.copyOf(all);
        }

        public java.awt.Color awtTint() {
            return new java.awt.Color(tint);
        }
    }

    /**
     * A creature drawn differently while this theme lasts.
     *
     * <p>The kind is untouched — the same health, the same reach, the same brain. Only the model
     * changes, which is how a skeleton becomes a robot when the floor becomes a spaceship without a
     * single number moving.
     *
     * @param name       the creature template it redraws
     * @param animations the {@code AnimationSet} to take clips from, when its own model does not carry
     *     them. Usually none: a model of its own carries its own clips, and copying them onto it from a
     *     second copy of the same file rebinds the tracks to the wrong skeleton
     * @param death      what it plays when it falls
     * @param playOnce   what it plays once from its first frame and holds on its last, in place of any idle: a gate
     *     swinging open as it appears. None for a thing that plays its roles
     */
    public record ThemeMonster(String name, String model, String texture, float modelScale, int tint, float facing,
            @Link(AnimationSet.class) String animations, @Clip String idle, @Clip String walk, @Clip String attack,
            @Clip String hurt, @Clip String death, @Link(Effect.class) String effect, Held held,
            @Clip String playOnce) {

        /** What a block leaves out. */
        public static final ThemeMonster DEFAULTS = new ThemeMonster("", null, null, 1f, 0xFFFFFF, 90f, null,
                null, null, null, null, null, null, Held.NOTHING, null);

        /** What it is drawn as, which is a monster's look and nothing else. */
        public MonsterLook look() {
            return new MonsterLook(model, texture, modelScale, tint, facing, animations, List.of(), idle, walk,
                    attack, hurt, death, held, effect);
        }
    }

    public java.awt.Color awtFogTint() {
        return new java.awt.Color(fogTint);
    }
}
