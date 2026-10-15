package uz.dukeengine.dungeon.run;

import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;

/**
 * The endless descent: a new floor from the next seed along the chain.
 *
 * <p>The seed advances once per floor handed out and never anywhere else, so a
 * session's whole sequence of dungeons is a function of the one seed it started
 * with — which is what makes a run reproducible from its seed alone.
 *
 * <p>A campaign's descent is the same floors under a name its triggers know, opening at a depth of its own and with
 * no bottom: the story says when it ends — see {@code docs/plan/2026-10-15-act1.md}.
 */
final class GeneratedFloors implements Floors {

    private final DungeonSettings settings;
    private long seed;
    /** Whether the next floor is the one {@link #seed} already names — a saved run picked up — not the next. */
    private boolean standing;
    /** A campaign descent's name, or null for the endless descent. */
    private final String name;
    private final int firstDepth;

    GeneratedFloors(long seed, DungeonSettings settings) {
        this(seed, settings, false);
    }

    GeneratedFloors(long seed, DungeonSettings settings, boolean standing) {
        this(seed, settings, standing, null, 1);
    }

    GeneratedFloors(long seed, DungeonSettings settings, boolean standing, String name, int firstDepth) {
        this.seed = seed;
        this.settings = settings;
        this.standing = standing;
        this.name = name;
        this.firstDepth = firstDepth;
    }

    @Override
    public GeneratedDungeon next(int depth) {
        if (!standing) {
            seed = DungeonGenerator.nextSeed(seed);
        }
        standing = false;
        return DungeonGenerator.generate(seed, settings, depth);
    }

    @Override
    public long seed() {
        return seed;
    }

    /** A descent starts at the top, which is what makes it a descent; a campaign's where its block says. */
    @Override
    public int firstDepth() {
        return firstDepth;
    }

    /** The bosses are the floors: the last one stands on the bottom. A campaign's descent has none. */
    @Override
    public int lastDepth() {
        return name == null ? settings.finalDepth() : 0;
    }

    @Override
    public String stageName() {
        return name;
    }

    @Override
    public boolean descends() {
        return true;
    }
}
