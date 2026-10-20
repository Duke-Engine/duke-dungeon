package uz.dukeengine.dungeon.ai;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgModuleGroups;

/**
 * Kept to a place, as a graveyard's dead are to their graves (Graveyards): its brain sees no enemy standing further
 * than {@code radius} from {@code home}, and one that has strayed past it gives up and walks back. Held on the creature
 * so its brain reads it where it reads everything else of the creature's.
 */
@ModuleGroup(RpgModuleGroups.ITEMS)
public final class Leash extends Module {

    private final Coord3D home;
    private final float radius;

    public Leash(GameObject owner, Coord3D home, float radius) {
        super(owner);
        this.home = home;
        this.radius = radius;
    }

    public Coord3D home() {
        return home;
    }

    /** Whether {@code at} is past the leash. */
    public boolean beyond(Coord3D at) {
        return Math.hypot(at.x() - home.x(), at.y() - home.y()) > radius;
    }
}
