package uz.dukeengine.dungeon.loot;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.module.Errand;
import uz.dukeengine.rts.module.RtsModuleGroups;
import uz.dukeengine.rts.module.WeaponUpdate;

/**
 * A hero sent to pick a thing up off the floor, or to put one of his down: he walks there, and when he is near
 * enough it changes hands.
 *
 * <p>An errand in the engine's sense — a module on him while it lasts — so any order the player gives him after it,
 * a walk, an attack, a stop, gives it up as it gives up every errand. A walk somewhere else it notices for itself,
 * since a skill that moves him does not go through the engine's door: his legs going anywhere but here is the
 * errand over. Legs that got as near as they could without getting here leave a thing he was sent for lying, and
 * put a thing he was sending down where they stopped.
 *
 * <p>Deterministic: sent by an order, on every machine on the same frame; checked on a frame boundary; near enough
 * is a sum of squares, with no root taken.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class ItemErrand extends UpdateModule implements Errand {

    /**
     * What every errand of a game shares.
     *
     * @param reach      how near he has to be for a thing to change hands
     * @param noteFrames how long the panel says what happened, in logic frames
     * @param template   what a thing he puts down becomes on the floor
     * @param floorOwner whose a thing on the floor is: the dungeon's, so it is nobody's hero's to select
     * @param fullWord   what the panel says when his bag has no room for what he was sent for
     */
    public record Rules(float reach, int noteFrames, String template, int floorOwner, String fullWord) {
    }

    private final LootBag bag;
    private final Rules rules;
    private final Coord3D goal;
    /** What he was sent for; null when he was sent to put something down. */
    private final ObjectId item;
    /** The slot he is putting down; -1 when he was sent to pick something up. */
    private final int slot;
    /** Done or given up: nothing more to do until the next order takes it off him. */
    private boolean over;

    private ItemErrand(GameObject hero, LootBag bag, Rules rules, Coord3D goal, ObjectId item, int slot) {
        super(hero);
        this.bag = bag;
        this.rules = rules;
        this.goal = new Coord3D(goal.x(), goal.y(), 0f);
        this.item = item;
        this.slot = slot;
    }

    /** Send {@code hero} for what {@code thing} holds, into {@code bag}; false where there is nothing there. */
    public static boolean pickUp(GameObject hero, GameObject thing, LootBag bag, Rules rules) {
        var lying = thing == null ? null : thing.findModule(GroundItem.class);
        if (hero == null || bag == null || lying == null || lying.getHolding() == null) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, thing.getPosition(), thing.getId(), -1));
        return true;
    }

    /** Send {@code hero} to put what is in {@code slot} of {@code bag} down at {@code place}; false for an empty slot. */
    public static boolean drop(GameObject hero, int slot, Coord3D place, LootBag bag, Rules rules) {
        if (hero == null || bag == null || place == null || bag.at(slot) == null) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, place, null, slot));
        return true;
    }

    /** What the player said last is what he does: whatever he was at is given up, and off he goes. */
    private static void send(GameObject hero, ItemErrand errand) {
        Errand.giveUpAll(hero);
        var weapon = hero.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.holdFire(); // as a walk does: the fight he was in is not what he was sent to do
        }
        var legs = hero.getLocomotor();
        if (legs != null) {
            if (errand.near(hero)) {
                legs.stop();
            } else if (errand.item != null) {
                legs.moveExactlyTo(errand.goal); // onto the thing, not onto a place of his own beside it
            } else {
                legs.moveTo(errand.goal);
            }
        }
        hero.addModule(errand);
    }

    @Override
    public void update() {
        var hero = getOwner();
        var world = hero.getWorld();
        if (over || world == null || hero.isEffectivelyDead()) {
            return;
        }
        var legs = hero.findModule(MoveUpdate.class);
        var going = legs == null ? null : legs.getGoal();
        if (going != null && !going.equals(goal)) {
            over = true; // sent somewhere else
            return;
        }
        GroundItem lying = null;
        if (item != null) {
            var thing = world.findObject(item);
            lying = thing == null ? null : thing.findModule(GroundItem.class);
            if (lying == null || lying.getHolding() == null) {
                over = true; // somebody got there first
                return;
            }
        }
        if (!near(hero)) {
            boolean asNearAsHeCan = legs != null && !legs.isMoving() && legs.stoppedShort();
            if (!asNearAsHeCan || lying != null) {
                over = asNearAsHeCan; // a thing he cannot reach stays where it is
                return;
            }
            // A place he cannot reach: it goes down where he could get to, which is what the player wanted most.
        }
        over = true;
        if (legs != null && legs.isMoving()) {
            legs.stop();
        }
        if (lying != null) {
            // Into the bag if there is room for it, or if it makes up a set that joins into less room than it takes.
            if (bag.take(lying.getHolding(), world.getFrame(), rules.noteFrames())) {
                lying.take();
            } else {
                bag.say(rules.fullWord(), world.getFrame(), rules.noteFrames());
            }
            return;
        }
        var put = bag.remove(slot);
        if (put != null) {
            GroundItem.lay(world, put.liesAs(rules.template()), put, hero.getPosition(), rules.floorOwner());
        }
    }

    /** Whether he stands near enough to it, across the floor: how high either is does not come into it. */
    private boolean near(GameObject hero) {
        float dx = hero.getPosition().x() - goal.x();
        float dy = hero.getPosition().y() - goal.y();
        return dx * dx + dy * dy <= rules.reach() * rules.reach();
    }

    /** Whether it has been done or given up. */
    public boolean isOver() {
        return over;
    }
}
