package uz.dukeengine.dungeon.combat;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;

/**
 * A creature that drinks from its blows: every blow it lands gives it back {@code Percent} of what the blow dealt, as
 * health, never above its maximum. Every boss carries it, so a fight with one that goes on is lost faster than its
 * bar says.
 *
 * <p>It is told rather than listening. A blow lands in two places in this game -- a swing where the striker stands,
 * which {@link Swing} hears the moment before the weapon lands it, and a shot when it arrives, in
 * {@link ArrowUpdate} -- and each hands the blow's figure here. The figure is what the blow dealt, not what the victim
 * had left: a kill is no special case.
 *
 * <p>Deterministic: one multiplication of the blow's own figure by a whole percentage, on the simulation's frame, and
 * the body's own {@code heal}, which the client shows as it shows every other.
 */
@ModuleGroup({ModuleGroups.COMBAT, ModuleGroups.BODY})
public final class Lifesteal extends Module {

    /** @param percent what share of each blow's damage comes back to it as health */
    public record Data(int percent) implements ModuleData {
    }

    private final Data data;

    public Lifesteal(GameObject owner, Data data) {
        super(owner);
        this.data = data;
    }

    /**
     * {@code striker} landed a blow worth {@code dealt}: if it drinks from its blows, and is still standing to, it
     * gets its share back. Nobody, the dead, and a creature without the module get nothing.
     */
    public static void drink(GameObject striker, float dealt) {
        var thirst = striker == null ? null : striker.findModule(Lifesteal.class);
        if (thirst == null || dealt <= 0f || striker.isEffectivelyDead() || striker.getBody() == null) {
            return;
        }
        striker.getBody().heal(dealt * thirst.data.percent() / 100f);
    }
}
