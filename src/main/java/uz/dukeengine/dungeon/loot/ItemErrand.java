package uz.dukeengine.dungeon.loot;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.run.GateUpdate;
import uz.dukeengine.rts.module.Errand;
import uz.dukeengine.rts.module.RtsModuleGroups;
import uz.dukeengine.rts.module.WeaponUpdate;

/**
 * A hero sent to pick a thing up off the floor, to put one of his down, or up to the keep's gate: he walks there, and
 * when he is near enough it is done — the thing changes hands, or he says what he makes of the gate.
 *
 * <p>An errand in the engine's sense — a module on him while it lasts — so any order the player gives him after it,
 * a walk, an attack, a stop, gives it up as it gives up every errand. A walk somewhere else it notices for itself,
 * since a skill that moves him does not go through the engine's door: his legs going anywhere but here is the
 * errand over. Legs that got as near as they could without getting here leave a thing he was sent for lying, and
 * put a thing he was sending down where they stopped. A thing with a shape — the gate — is walked up to rather than
 * onto: as near as he can get to it, its edge is there.
 *
 * <p>Deterministic: sent by an order, on every machine on the same frame; checked on a frame boundary; near enough
 * is a sum of squares, and how far a shape reaches is the engine's own figure, the same on every machine.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class ItemErrand extends UpdateModule implements Errand {

    /**
     * What every errand of a game shares.
     *
     * @param reach      how near he has to be for a thing to change hands
     * @param noteFrames how long what he says is said, in logic frames
     * @param template   what a thing he puts down lies in, when it names nothing of its own
     * @param floorOwner whose a thing on the floor is: the dungeon's, so it is nobody's hero to select
     * @param fullWord   what he says when his bag has no room for what he was sent for
     */
    public record Rules(float reach, int noteFrames, String template, int floorOwner, String fullWord) {
    }

    /** What he was sent to do when he gets there. */
    private enum Act {
        /** Take what a thing lying there holds. */
        TAKE,
        /** Put what is in a slot of his down. */
        PUT,
        /** Say what he makes of the keep's gate. */
        LOOK
    }

    private final LootBag bag;
    private final Rules rules;
    private final Act act;
    private final Coord3D goal;
    /** What he was sent to: the thing to take, or the gate to look at; null when he was sent to put one down. */
    private final ObjectId thing;
    /** The slot he is putting down; -1 otherwise. */
    private final int slot;
    /** Done or given up: nothing more to do until the next order takes it off him. */
    private boolean over;

    private ItemErrand(GameObject hero, LootBag bag, Rules rules, Act act, Coord3D goal, ObjectId thing, int slot) {
        super(hero);
        this.bag = bag;
        this.rules = rules;
        this.act = act;
        this.goal = new Coord3D(goal.x(), goal.y(), 0f);
        this.thing = thing;
        this.slot = slot;
    }

    /** Send {@code hero} for what {@code thing} holds, into {@code bag}; false where there is nothing there. */
    public static boolean pickUp(GameObject hero, GameObject thing, LootBag bag, Rules rules) {
        var lying = thing == null ? null : thing.findModule(GroundItem.class);
        if (hero == null || bag == null || lying == null || lying.getHolding() == null) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, Act.TAKE, thing.getPosition(), thing.getId(), -1));
        return true;
    }

    /** Send {@code hero} to put what is in {@code slot} of {@code bag} down at {@code place}; false for an empty slot. */
    public static boolean drop(GameObject hero, int slot, Coord3D place, LootBag bag, Rules rules) {
        if (hero == null || bag == null || place == null || bag.at(slot) == null) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, Act.PUT, place, null, slot));
        return true;
    }

    /**
     * Send {@code hero} up to the keep's gate, to say when he is there whether he has the key — into {@code bag}'s
     * note; false for a thing that is not a gate.
     */
    public static boolean toTheGate(GameObject hero, GameObject gate, LootBag bag, Rules rules) {
        if (hero == null || bag == null || gate == null || gate.findModule(GateUpdate.class) == null) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, Act.LOOK, gate.getPosition(), gate.getId(), -1));
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
            if (errand.near(hero, errand.rules.reach())) {
                legs.stop();
            } else if (errand.thing != null) {
                legs.moveExactlyTo(errand.goal); // onto the thing, or as near it as he can get
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
        var there = thing == null ? null : world.findObject(thing);
        var lying = there == null ? null : there.findModule(GroundItem.class);
        if (thing != null && (there == null || act == Act.TAKE && (lying == null || lying.getHolding() == null))) {
            over = true; // gone, or somebody got there first
            return;
        }
        if (!near(hero, rules.reach())) {
            boolean stopped = legs != null && !legs.isMoving();
            boolean asNearAsHeCan = stopped && legs.stoppedShort();
            // A walk his brain took up again (HeroBrain.mindTheWayOnHisErrand) is to the place, and ends on the block
            // beside a thing with a shape: not short of anything, and at its edge. Still a walk with a goal -- one his
            // brain stopped for a body in the way has none, and he is not there until it takes him on.
            boolean atItsEdge = going != null && stopped && there != null
                    && near(hero, rules.reach() + there.getGeometry().footprintRadius());
            if (!asNearAsHeCan && !atItsEdge) {
                return;
            }
            // As near as he could get. A place he could not reach is where he got to, and a thing lying there that
            // he could not reach stays where it is -- but a thing with a shape is walked up to rather than onto, and
            // its edge is there.
            if (there != null && !atItsEdge) {
                over = true;
                return;
            }
        }
        over = true;
        if (legs != null && legs.isMoving()) {
            legs.stop();
        }
        int frame = world.getFrame();
        switch (act) {
            case TAKE -> {
                // Into the bag if there is room for it, or if it makes up a set that joins into less room than it
                // takes.
                if (bag.take(lying.getHolding(), frame, rules.noteFrames())) {
                    lying.take();
                } else {
                    bag.say(rules.fullWord(), frame, rules.noteFrames());
                }
            }
            case PUT -> {
                var put = bag.remove(slot);
                if (put != null) {
                    GroundItem.lay(world, put.liesAs(rules.template()), put, hero.getPosition(), rules.floorOwner());
                }
            }
            case LOOK -> {
                var gate = there.findModule(GateUpdate.class);
                if (gate != null) {
                    bag.say(gate.lineFor(bag.holds(LootKind.KEY)), frame, rules.noteFrames());
                }
            }
        }
    }

    /** Whether he stands within {@code reach} of it, across the floor: how high either is does not come into it. */
    private boolean near(GameObject hero, float reach) {
        float dx = hero.getPosition().x() - goal.x();
        float dy = hero.getPosition().y() - goal.y();
        return dx * dx + dy * dy <= reach * reach;
    }

    /** Whether it has been done or given up. */
    public boolean isOver() {
        return over;
    }
}
