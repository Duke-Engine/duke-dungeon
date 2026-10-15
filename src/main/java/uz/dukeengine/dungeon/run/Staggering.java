package uz.dukeengine.dungeon.run;

import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.core.module.DamageListener;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Stagger;

/**
 * A hero's stagger at a heavy blow -- see {@link Stagger}. The stun is the skill system's own (the engine's
 * {@code DISABLED} on his {@link StatusUpdate}, as a stunning shot leaves it); the clip is his {@code Hurt}, played as
 * the engine's busy clip for exactly the stun's frames, so the window shows a flinch when, and only when, he cannot
 * strike.
 *
 * <p>ponytail: a game-side listener only until engine request E72 (a passive {@code STAGGER} skill effect, a crit
 * staggering too) lands; then this goes and the heroes' Skills carry it.
 */
@ModuleGroup(ModuleGroups.SCRIPT)
public final class Staggering extends Module implements DamageListener {

    /** It reads no fields; the block only says the hero has one. The rule is the Stagger block's. */
    public record Data() implements ModuleData {
    }

    private final Stagger rule;
    private final float heavyAt;
    private final String clip;
    private int until = Integer.MIN_VALUE;
    private int immuneUntil = Integer.MIN_VALUE;

    /**
     * @param heavyAt the least blow, after armour, that staggers him
     * @param clip    his flinch, played for the stagger's frames; null for none
     */
    public Staggering(GameObject owner, Stagger rule, float heavyAt, String clip) {
        super(owner);
        this.rule = rule;
        this.heavyAt = heavyAt;
        this.clip = clip;
    }

    @Override
    public void onDamage(DamageType type, float amount, ObjectId attacker, int frame) {
        var owner = getOwner();
        if (frame < until) {
            owner.busy(clip, until - frame); // a blow breaks off the busy clip; the stagger it shows goes on
            return;
        }
        if (rule.frames() <= 0 || amount < heavyAt || frame < immuneUntil || owner.isEffectivelyDead()
                || owner.hasStatus(ObjectStatus.DISABLED)) {
            return; // light, inside his immunity, dead, or stunned already by something longer
        }
        var timers = owner.findModule(StatusUpdate.class);
        if (timers == null) {
            return;
        }
        timers.apply(ObjectStatus.DISABLED, rule.frames());
        until = frame + rule.frames();
        immuneUntil = until + rule.immuneFrames();
        var legs = owner.getLocomotor();
        if (legs != null && legs.isMoving()) {
            legs.stop(); // knocked out of his stride: the order is dropped, and one given in the stagger waits for it
        }
        owner.busy(clip, rule.frames());
    }

    /** The frame his stagger ends on, or earlier than now for none. */
    public int until() {
        return until;
    }
}
