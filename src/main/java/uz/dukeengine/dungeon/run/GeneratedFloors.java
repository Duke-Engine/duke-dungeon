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
 */
final class GeneratedFloors implements Floors {

    private final DungeonSettings settings;
    private long seed;
    /** Whether the next floor is the one {@link #seed} already names — a saved run picked up — not the next. */
    private boolean standing;

    GeneratedFloors(long seed, DungeonSettings settings) {
        this(seed, settings, false);
    }

    GeneratedFloors(long seed, DungeonSettings settings, boolean standing) {
        this.seed = seed;
        this.settings = settings;
        this.standing = standing;
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

    /** A descent starts at the top, which is what makes it a descent. */
    @Override
    public int firstDepth() {
        return 1;
    }

    /** The bosses are the floors: the last one stands on the bottom. */
    @Override
    public int lastDepth() {
        return settings.finalDepth();
    }
}
