package uz.dukeengine.dungeon.run;

import java.util.List;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgModuleGroups;
import uz.dukeengine.rpg.mission.Mission;

/**
 * The floor's key dropped before the last of the floor's own outside its keep falls: at the kill that leaves no more
 * of them standing than the run's leeway ({@code Run.keyLeeway}) — one skeleton missed in a far corner no longer keeps
 * the gate shut (the owner, 2026-10-15). On each of them, beside the engine's {@link Mission.Counted}.
 *
 * <p>The engine's mission lays the key where its count of deaths reaches all of them; this tells it the deaths of the
 * few still standing early, through the dying one's own count, so the key lies where that one fell. Their own deaths
 * later count past the end, which the mission ignores once its key is laid. Deterministic: deaths in the order the
 * engine runs them, nothing read but who stands. ponytail: drives the engine's {@code Counted} by hand; an engine
 * {@code Mission.begin} that takes the leeway (E61) retires this.
 */
@ModuleGroup(RpgModuleGroups.ITEMS)
final class EarlyKey extends Module implements DieModule {

    /** One floor's: its mission, its own outside the keep, and how many may still stand when the key drops. */
    static final class Tally {

        private final Mission mission;
        private final List<GameObject> outside;
        private final int leeway;

        Tally(Mission mission, List<GameObject> outside, int leeway) {
            this.mission = mission;
            this.outside = outside;
            this.leeway = leeway;
        }
    }

    private final Tally tally;

    EarlyKey(GameObject monster, Tally tally) {
        super(monster);
        this.tally = tally;
    }

    @Override
    public void onDie() {
        var mission = tally.mission;
        var dying = getOwner();
        var world = dying.getWorld();
        if (world == null || mission.killed() >= mission.outside()) {
            return; // the key is laid already
        }
        int standing = 0;
        for (var other : tally.outside) {
            if (other != dying && world.findObject(other.getId()) != null && !other.isEffectivelyDead()
                    && !other.isDestroyed()) {
                standing++;
            }
        }
        var counted = dying.findModule(Mission.Counted.class);
        if (standing > tally.leeway || counted == null) {
            return;
        }
        // The rest told now, through this one's count: the last of them is this one, and the key lies where it fell.
        for (int left = mission.outside() - mission.killed(); left > 0; left--) {
            counted.onDie();
        }
    }
}
