package uz.dukeengine.dungeon.run;

import java.util.Set;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;

/**
 * The gate of the boss's keep: it stands across the doorway, and nothing walks through it until it is opened.
 *
 * <p>For now a hero walking up to it opens it — the key that will open it instead is the next piece of work, and it
 * will call {@link #open} where this does. Opening swaps it for the template its block names: the gate with a shape
 * goes, so the engine lays the navigation grid again without it and the doorway is walked at once, and the same gate
 * with no shape stands in its place, turned as it was, swinging open as it appears.
 *
 * <p>Deterministic: looked at on the world's own frame, by whether a living hero stands within its reach.
 */
@ModuleGroup(ModuleGroups.EFFECT)
public final class GateUpdate extends UpdateModule {

    /**
     * @param reach       how near a hero must come to open it, in world units, from its middle
     * @param everyFrames how often it looks, in logic frames (30 = once a second)
     * @param opens       the template that stands in its place once it is open; blank, and nothing does
     */
    public record Data(float reach, int everyFrames, String opens) implements ModuleData {

        /** What a block leaves out. */
        static final Data DEFAULTS = new Data(25f, 10, "");

        public Data {
            opens = opens == null ? "" : opens;
        }
    }

    private final Data data;
    /** The templates that are heroes: the ones that open it. */
    private final Set<String> heroes;
    /** Opened already: a second opening, the same frame, would stand a second open gate in the first. */
    private boolean opened;

    public GateUpdate(GameObject owner, Data data, Set<String> heroes) {
        super(owner);
        this.data = data;
        this.heroes = Set.copyOf(heroes);
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (opened || world == null || owner.isEffectivelyDead() || data.everyFrames() <= 0
                || world.getFrame() % data.everyFrames() != 0) {
            return;
        }
        var near = world.objectsInRange(owner.getPosition(), data.reach(), thing -> thing.getBody() != null
                && !thing.isEffectivelyDead() && heroes.contains(thing.getTemplate().name()));
        if (!near.isEmpty()) {
            open();
        }
    }

    /**
     * Open it: this gate goes, its shape with it, and in its place, turned as it was, stands the template
     * {@code Opens} names — the same gate swung open, in nobody's way.
     */
    public void open() {
        if (opened) {
            return;
        }
        opened = true;
        var owner = getOwner();
        var world = owner.getWorld();
        var open = world == null || data.opens().isBlank() ? null : world.findTemplate(data.opens());
        if (open != null) {
            world.spawn(open, owner.getPosition(), owner.getPlayerIndex()).setOrientation(owner.getOrientation());
        }
        owner.markDestroyed();
    }
}
