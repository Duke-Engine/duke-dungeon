package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.SpeedModifier;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;

/**
 * The hero's sprint: while Shift is held he runs {@code share} times as fast. Switched by the {@link
 * uz.dukeengine.dungeon.party.PartyOrders} order {@code sprint}, which every machine hears, so the legs stay one world.
 */
@ModuleGroup(ModuleGroups.MOVEMENT)
public final class Sprint extends UpdateModule implements SpeedModifier, uz.dukeengine.core.save.Saveable {

    /** No configuration: the share is the Controls block's. */
    public record Data() implements ModuleData {
    }

    private final float share;
    private boolean on;

    public Sprint(GameObject owner, float share) {
        super(owner);
        this.share = share;
    }

    /** Run fast from now, or at his own pace. */
    public void set(boolean sprinting) {
        this.on = sprinting;
    }

    public boolean isOn() {
        return on;
    }

    @Override
    public float speedShare() {
        return on ? share : 1f;
    }

    @Override
    public void update() {
    }

    /** Whether Shift is held: the one thing the legs remember. */
    @Override
    public void save(uz.dukeengine.core.save.SaveOut out) {
        out.writeBoolean(on);
    }

    @Override
    public void load(uz.dukeengine.core.save.SaveIn in) {
        on = in.readBoolean();
    }
}
