package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import uz.dukeengine.combat.module.Errand;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.world.Combos;
import uz.dukeengine.rpg.Facing;

/**
 * The hero's attack combinations, played out in the simulation (the {@link Combos} block is the data): a press of the
 * left button (light) or the right (heavy) begins a chain, a press made during a swing is kept, and one that falls in
 * the step's window plays the step the data leads to -- else the chain is over and a press begins a new one.
 *
 * <p>Every swing turns him to the nearest enemy inside the soft-aim arc and range (none: it goes where he faces), holds
 * his clip on him (a held direction walks on under it, the swing played from his upper body), carries him forward by
 * the step's lunge where he is not walking,
 * and lands its blow on the step's frame on every enemy inside the hit arc, for the step's share of his weapon's
 * damage -- through {@link WeaponUpdate#blow}, so his bonuses, criticals and the sword's seal all count as ever. A
 * heavy step may stun whom it hits. A roll or a stun cuts the chain off; a blow that does not stagger him does not.
 *
 * <p>Counted in frames of its own, so it is the same on every machine and in a save.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class Combo extends UpdateModule implements Saveable {

    /** No configuration: the combinations are the hero's block in his data file. */
    public record Data() implements ModuleData {
    }

    /** How far past his reach a swing looks for middles, the bodies' own sizes beyond them (as the engine's swing does). */
    private static final float LOOK = 40f;

    /** A press that has not found its step yet. */
    private enum Press { NONE, LIGHT, HEAVY }

    private final Combos combos;
    private final String stunLook;

    /** The swing under way, or null; and the frame of it this is, 0 on the frame it began. */
    private Combos.Step step;
    private int age;
    private Press kept = Press.NONE;

    public Combo(GameObject owner, Combos combos, String stunLook) {
        super(owner);
        this.combos = combos;
        this.stunLook = stunLook == null ? "" : stunLook;
    }

    /**
     * The left button (light) or the right (heavy) pressed: it begins a chain, or is kept for the one under way. Not
     * while he is stunned, dead, or rolling out of harm's way (the roll is his, and a press then would swing over it).
     */
    public void press(boolean heavy) {
        var owner = getOwner();
        if (!combos.isEmpty() && !owner.isEffectivelyDead() && !owner.hasStatus(ObjectStatus.DISABLED)
                && !owner.hasStatus(ObjectStatus.INVULNERABLE)) {
            kept = heavy ? Press.HEAVY : Press.LIGHT;
        }
    }

    /** The chain given up and the press with it: a roll's, or a stun's. The clip is left to whatever took over. */
    public void cancel() {
        step = null;
        age = 0;
        kept = Press.NONE;
    }

    /** The step he is swinging, or null. */
    public Combos.Step step() {
        return step;
    }

    /** Frames into the swing under way: 0 on the frame it began. */
    public int age() {
        return age;
    }

    @Override
    public void update() {
        var owner = getOwner();
        if (owner.isEffectivelyDead() || owner.hasStatus(ObjectStatus.DISABLED)) {
            cancel();
            return;
        }
        if (step == null) {
            if (kept != Press.NONE) {
                start(combos.first(kept == Press.HEAVY));
            }
            return;
        }
        age++;
        if (owner.getBusyClip() == null) {
            owner.busy(step.clip(), step.frames() - age, true); // a blow that did not stagger him broke it off: it goes on
        }
        if (age <= step.hitFrame()) {
            lunge();
        }
        if (age == step.hitFrame()) {
            strike();
        }
        if (kept != Press.NONE && age >= step.windowOpen() && age <= step.windowClose()) {
            var next = combos.step(step.after(kept == Press.HEAVY));
            if (next != null) {
                start(next);
                return;
            }
        }
        if (age >= step.frames()) {
            step = null; // over; a press kept past the window begins the next chain
            if (kept != Press.NONE) {
                start(combos.first(kept == Press.HEAVY));
            }
        }
    }

    /** Begin {@code next}: turned to the enemy soft aim finds, his errands and route walks given up, his clip on. */
    private void start(Combos.Step next) {
        kept = Press.NONE;
        step = next;
        age = 0;
        if (next == null) {
            return;
        }
        var owner = getOwner();
        var near = enemies(combos.aimRange(), combos.aimArc());
        if (!near.isEmpty()) {
            Facing.turnToward(owner, near.getFirst());
        }
        Errand.giveUpAll(owner);
        owner.busy(next.clip(), next.frames(), true); // a held direction walks on under the swing
        if (owner.getLocomotor() instanceof MoveUpdate legs && !legs.isSteered() && legs.isMoving()) {
            legs.stop(); // a route walk ends here; a held direction does not
        }
    }

    /** Forward by the step's lunge, an equal part a frame up to the blow, as far as the ground and bodies allow. */
    private void lunge() {
        if (step.lunge() <= 0f || !(getOwner().getLocomotor() instanceof MoveUpdate legs) || legs.isSteered()) {
            return;
        }
        var here = getOwner().getPosition();
        float each = step.lunge() / step.hitFrame();
        float facing = getOwner().getOrientation();
        legs.stepStraightTo(new Coord3D(here.x() + (float) StrictMath.cos(facing) * each,
                here.y() + (float) StrictMath.sin(facing) * each, here.z()));
    }

    /** The blow of the step: every enemy in reach and arc, stunned if the step stuns. */
    private void strike() {
        var owner = getOwner();
        var weapon = owner.findModule(WeaponUpdate.class);
        if (weapon == null) {
            return;
        }
        var victims = enemies(weapon.reach(), combos.hitArc());
        weapon.blow(victims, step.damagePercent() / 100f);
        if (step.staggerFrames() <= 0) {
            return;
        }
        for (var victim : victims) {
            var clock = victim.isEffectivelyDead() ? null : victim.findModule(StatusUpdate.class);
            if (clock != null) {
                clock.apply(ObjectStatus.DISABLED, step.staggerFrames());
                if (!stunLook.isBlank() && victim.hasStatus(ObjectStatus.DISABLED)) {
                    owner.getWorld().effect(stunLook, victim);
                }
            }
        }
    }

    /**
     * The living enemies within {@code reach} of his surface and inside {@code arcDegrees} (both sides together) round
     * his facing, nearest first and the lowest id before another at the same gap: the same on every machine.
     */
    private List<GameObject> enemies(float reach, float arcDegrees) {
        var owner = getOwner();
        World world = owner.getWorld();
        var weapon = owner.findModule(WeaponUpdate.class);
        if (world == null || weapon == null) {
            return List.of();
        }
        float half = (float) Math.toRadians(arcDegrees) / 2f;
        var here = owner.getPosition();
        float facing = owner.getOrientation();
        var found = new ArrayList<GameObject>();
        for (var other : world.objectsInRange(here, reach + LOOK, thing -> thing != owner)) {
            if (other.isEffectivelyDead() || other.getBody() == null
                    || world.getRelationship(owner.getPlayerIndex(), other.getPlayerIndex()) != Relationship.ENEMIES
                    || !weapon.canFireAt(other) || World.reachBetween(owner, other) > reach) {
                continue;
            }
            var at = other.getPosition();
            float off = (float) StrictMath.atan2(at.y() - here.y(), at.x() - here.x()) - facing;
            off = (float) StrictMath.atan2(StrictMath.sin(off), StrictMath.cos(off));
            if (Math.abs(off) <= half) {
                found.add(other);
            }
        }
        found.sort(Comparator.comparingDouble((GameObject other) -> World.reachBetween(owner, other))
                .thenComparingInt(other -> other.getId().value()));
        return found;
    }

    /** One number for the world's checksum: which step, how far into it, which press is kept. */
    public long checksum() {
        return step == null ? kept.ordinal()
                : ((long) (combos.steps().indexOf(step) + 1) << 24) ^ ((long) age << 4) ^ kept.ordinal();
    }

    /** The swing under way and the press kept for it. */
    @Override
    public void save(SaveOut out) {
        out.writeString(step == null ? null : step.name()).writeInt(age).writeEnum(kept);
    }

    @Override
    public void load(SaveIn in) {
        var name = in.readString();
        step = name == null ? null : combos.step(name);
        age = in.readInt();
        kept = in.readEnum(Press.class);
    }
}
