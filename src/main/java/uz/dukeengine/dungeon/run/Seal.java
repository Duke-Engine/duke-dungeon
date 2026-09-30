package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.gen.Keep;

/**
 * The keep's shut gate, as a rule: while it stands, nothing that hurts or mends passes between the keep and the rest
 * of the floor. Every blow, shot, burst, falling meteor and mending asks {@link #parts} rather than saying the rule
 * again.
 *
 * <p>Within the keep is its square — the court, and the ring of wall with the doorway the gate stands in. The threshold
 * is outside, with the rest of the floor: a hero on it is on the gate's far side from the boss. Nobody stands in the
 * doorway while the gate does, its shape filling it, so where the line falls matters only to what flies: a shot from
 * outside is spent as it reaches the gate's cell, one from within as it leaves it.
 *
 * <p>One to a game, as {@code Orders} is, handed where the game is assembled to everything that asks, and told of each
 * floor as the run lays it. A floor with no keep — a stage, or one where none fitted — parts nothing; nor does one
 * whose gate has been opened.
 *
 * <p>Deterministic and cheap, being asked on every blow: two cells tested against the keep's square, and only where
 * they fall either side of it, the gate looked up by its id. No clock, and nothing iterated.
 */
public final class Seal {

    private Keep keep;
    /** The gate across its doorway: standing, not opened, is what seals it. */
    private ObjectId gate;

    /** The floor just laid: its keep and the gate across its doorway, either null where it has none. */
    public void floor(Keep keep, GameObject gate) {
        this.keep = keep;
        this.gate = gate == null ? null : gate.getId();
    }

    /** Whether the shut gate parts {@code a} from {@code b}: one within the keep and one outside, the gate standing. */
    public boolean parts(World world, Coord3D a, Coord3D b) {
        if (keep == null || gate == null || within(a) == within(b)) {
            return false;
        }
        var standing = world.findObject(gate);
        return standing != null && !standing.isDestroyed();
    }

    private boolean within(Coord3D at) {
        return keep.within(cell(at.x()), cell(at.y()));
    }

    private static int cell(float at) {
        return (int) Math.floor(at / PathGrid.DEFAULT_CELL_SIZE);
    }
}
