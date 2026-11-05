package uz.dukeengine.dungeon.ai;

import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.script.UnitScript;
import uz.dukeengine.rpg.Facing;
import uz.dukeengine.rpg.item.ItemErrand;
import uz.dukeengine.rpg.skill.SkillBook;

/**
 * What the hero does of himself in a game played from his shoulder: nothing he was not told.
 *
 * <p>His weapon acquires nothing and he walks to nothing — the player runs him and swings him (the action module's
 * orders), and a swing's lock on an enemy is the weapon's own target. What this keeps is the three small things that
 * the player cannot say with a key:
 *
 * <ul>
 * <li>his weapon picks no fight by itself (it is built so, in {@code Dungeon}, and asked again each frame);
 * <li>he faces what his weapon is on while he stands, and lets go of a target that is dead;
 * <li>he waits, rather than shoves, when a body stands in the doorway of an errand he was sent on (E on a thing on the
 *     floor, on a person, on the gate): see {@link ItemErrand} and {@link WayAhead}.
 * </ul>
 */
@ModuleGroup(ModuleGroups.SCRIPT)
public final class HeroBrain extends UnitScript {

    /** Its block: the word a unit's {@code Modules} list opens with, and nothing to say inside it. */
    public record Data() implements ModuleData {
    }

    private final DungeonSettings settings;

    /**
     * The spot in front of him that a body is standing in while one is, so he stands still instead of shoving; null
     * when he is free to walk.
     */
    private Coord3D waitingOn;

    /** Where an errand's walk was taking him when a body stopped it, so he can carry on; null unless he is waiting. */
    private Coord3D errand;

    /** The errand he was walking for when the body stopped him, if he was on one: the walk is that errand's. */
    private ItemErrand walkingFor;

    /** How many times he has set off again on his errand without his legs taking it up. */
    private int resumeTries;
    private static final int RESUME_TRIES = 60;

    public HeroBrain(DungeonSettings settings) {
        this.settings = settings;
    }

    @Override
    public void onUpdate() {
        var weapon = unit().findModule(WeaponUpdate.class);
        var move = unit().findModule(MoveUpdate.class);
        if (weapon == null || move == null) {
            return;
        }
        if (!weapon.isAcquiringNothing()) {
            weapon.acquiresNothing(true); // a hero built without a start; his first order is not cut
        }
        var book = unit().findModule(SkillBook.class);
        if (book != null && book.keepsBusy()) {
            return; // a roll or a charge under way has his legs until it ends
        }
        var current = weapon.isAttacking() ? world().findObject(weapon.getTarget()) : null;
        if (current != null) {
            if (current.isEffectivelyDead()) {
                weapon.holdFire();
            } else if (!move.isMoving()) {
                Facing.turnToward(unit(), current);
            }
        }
        mindTheWayOnHisErrand(move);
    }

    /**
     * Stop rather than shove when a body is in the doorway of an errand's walk, and carry on when the way clears.
     *
     * <p>Where he was going is kept because stopping throws it away, and this is a pause and not a cancellation. It is
     * given up the moment his legs take another walk (the player ran, or sent him elsewhere), the errand is over, or
     * two seconds of tries have passed. The errand is told he is held, and let go, so it does not read legs that
     * stopped as legs that arrived.
     */
    private void mindTheWayOnHisErrand(MoveUpdate move) {
        if (errand != null) {
            if (move.isMoving() || walkingFor != null && walkingFor.isOver()) {
                forgetTheErrand();
                return;
            }
            if (WayAhead.stillShut(unit(), waitingOn, errand, settings.combat().wayAheadProbe(), null)) {
                return;
            }
            // Kept until his legs take it up: the body that fell in his way can turn the first try away for a frame
            // or two while it lies there.
            moveTo(errand.x(), errand.y());
            if (++resumeTries >= RESUME_TRIES) {
                forgetTheErrand();
            }
            return;
        }
        // A held direction has no goal: the keys are the player's, and nothing tells them again once they are let go here.
        if (!move.isMoving() || move.getGoal() == null) {
            return;
        }
        var ahead = WayAhead.noWayPast(unit(), settings.combat().wayAheadProbe(), null);
        if (ahead != null) {
            errand = move.getGoal();
            waitingOn = ahead;
            var on = unit().findModule(ItemErrand.class);
            walkingFor = on == null || on.isOver() ? null : on; // one done, still on him, is not what this is for
            if (walkingFor != null) {
                walkingFor.heldForABody(true);
            }
            move.stop();
        }
    }

    private void forgetTheErrand() {
        if (walkingFor != null) {
            walkingFor.heldForABody(false);
        }
        errand = null;
        waitingOn = null;
        walkingFor = null;
        resumeTries = 0;
    }
}
