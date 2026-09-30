package uz.dukeengine.dungeon.loot;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.run.GateUpdate;
import uz.dukeengine.rts.module.Errand;
import uz.dukeengine.rts.module.RtsModuleGroups;
import uz.dukeengine.rts.module.WeaponUpdate;

/**
 * A hero sent to pick a thing up off the floor, to put one of his down, to use one of his on a thing, or up to the
 * keep's gate: he walks there, and when he is near enough it is done — the thing changes hands, is used, or he says
 * what he makes of the gate.
 *
 * <p>An errand in the engine's sense — a module on him while it lasts — so any order the player gives him after it,
 * a walk, an attack, a stop, gives it up as it gives up every errand. A walk somewhere else it notices for itself,
 * since a skill that moves him does not go through the engine's door: his legs going anywhere but here is the
 * errand over. Legs that stopped short of it are where he was, not how near he can get, and he looks again from
 * there; legs that find no way nearer from where he stands have got as near as they could, and leave a thing he was
 * sent for lying, and he says so, or put a thing he was sending down where they stopped. A thing with a shape — the
 * gate — is walked up to rather than onto: as near as he can get to it, its edge is there. And he is never on it for
 * ever: stood getting nowhere for as long as the rules say, he gives it up the same way.
 *
 * <p>Deterministic: sent by an order, on every machine on the same frame; checked on a frame boundary; near enough
 * is a sum of squares, how far a shape reaches is the engine's own figure, and how long he has stood is counted in
 * frames — the same on every machine.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class ItemErrand extends UpdateModule implements Errand {

    /**
     * What every errand of a game shares.
     *
     * @param reach       how near he has to be for a thing to change hands
     * @param noteFrames  how long what he says is said, in logic frames
     * @param template    what a thing he puts down lies in, when it names nothing of its own
     * @param floorOwner  whose a thing on the floor is: the dungeon's, so it is nobody's hero to select
     * @param fullWord    what he says when his bag has no room for what he was sent for
     * @param noUseWord   what he says when he was sent to use a thing on something it does nothing to
     * @param noWayWord   what he says when he gives up on getting to a thing
     * @param stuckFrames how long he stands getting nowhere before he gives it up, in logic frames
     */
    public record Rules(float reach, int noteFrames, String template, int floorOwner, String fullWord,
            String noUseWord, String noWayWord, int stuckFrames) {
    }

    /** What he was sent to do when he gets there. */
    private enum Act {
        /** Take what a thing lying there holds. */
        TAKE,
        /** Put what is in a slot of his down. */
        PUT,
        /** Use what is in a slot of his on a thing. */
        USE,
        /** Say what he makes of the keep's gate. */
        LOOK
    }

    private final LootBag bag;
    private final Rules rules;
    private final Act act;
    private final Coord3D goal;
    /**
     * What he was sent to: the thing to take, to use one of his on, or the gate to look at; null when he was sent to
     * put one down.
     */
    private final ObjectId thing;
    /** The slot he is putting down or using; -1 otherwise. */
    private final int slot;
    /** Done or given up: nothing more to do until the next order takes it off him. */
    private boolean over;
    /** The spot he has stood within a cell of, and the frame he first stood there: see {@link #gettingNowhere}. */
    private Coord3D stoodAt;
    private int stoodSince;

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

    /**
     * Send {@code hero} to take what is in {@code slot} of {@code bag} to {@code thing} and use it there; false for a
     * slot with nothing in it that can be used, or nothing to use it on.
     */
    public static boolean use(GameObject hero, int slot, GameObject thing, LootBag bag, Rules rules) {
        var item = bag == null ? null : bag.at(slot);
        if (hero == null || thing == null || item == null || item.use() == ItemUse.NONE) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, Act.USE, thing.getPosition(), thing.getId(), slot));
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
            if (!gettingNowhere(hero, world)) {
                if (legs == null || legs.isMoving() || going == null && !legs.stoppedShort()) {
                    // On his way -- or stopped by his brain for a body in the way, with no goal: he is not there until
                    // it takes him on (HeroBrain.mindTheWayOnHisErrand).
                    return;
                }
                if (!atItsEdge(hero, there) && !arrived(legs, going)) {
                    // His legs stopped short of it -- a leg they gave up on, a route that ran out at a body, a way
                    // round one that came back with nowhere in it -- and where they stopped is where he was, not how
                    // near he can get. He looks again from there, by the walk his brain takes a walk up again with:
                    // to the place, which ends on the block beside a thing with a shape.
                    legs.moveTo(goal);
                    if (legs.isMoving()) {
                        return;
                    }
                }
            }
            // As near as he can get. A place he could not reach is where he got to, and a thing lying there that he
            // cannot reach stays where it is, and he says so -- but a thing with a shape is walked up to rather than
            // onto, and its edge is there.
            // ponytail: a way shut only by bodies (a pack standing where the road runs) ends it here too, at once.
            // Waiting it out -- standing, looking again every HeroRepathFrames -- is the upgrade, but it wants a
            // better measure of getting nowhere than a cell: tried, a hero then went round in a loop wider than one.
            if (there != null && !atItsEdge(hero, there)) {
                over = true;
                bag.say(rules.noWayWord(), world.getFrame(), rules.noteFrames());
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
            case USE -> {
                var item = bag.at(slot);
                if (item == null || item.use() != ItemUse.UNLOCK) {
                    return; // nothing in hand that opens anything, any more
                }
                var gate = there.findModule(GateUpdate.class);
                if (gate == null) {
                    bag.say(rules.noUseWord(), frame, rules.noteFrames()); // it opens the gate and nothing else
                } else {
                    bag.remove(slot);
                    gate.open();
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

    /**
     * Whether he has stood getting nowhere for as long as the rules say: within a cell of one spot, short of it.
     *
     * <p>However that came about, it is not going to end by itself: his brain standing him behind a body that neither
     * goes nor falls -- a friend in a corridor, a thing that mends faster than he hurts it -- or his legs planning
     * round two bodies that hold him between them, one and then the other, for as long as anyone lets them. Asked
     * every frame he is short of it, so a step of a cell anywhere starts the count again: a long way round is not
     * getting nowhere, and a fight he stands in is -- once it has lasted that long.
     */
    private boolean gettingNowhere(GameObject hero, World world) {
        var at = hero.getPosition();
        if (stoodAt == null || !within(at, stoodAt, world.cellSize())) {
            stoodAt = at;
            stoodSince = world.getFrame();
            return false;
        }
        return world.getFrame() - stoodSince >= rules.stuckFrames();
    }

    /**
     * Whether he stands at the edge of a thing with a shape: a walk taken up again is to the place (see above), and ends
     * on the block beside such a thing, not short of anything and at its edge. Asked once his legs have stopped of
     * themselves: a hero his brain has stopped for a body in the way is not there however near he stands, until he has
     * stood getting nowhere as long as the rules allow.
     */
    private boolean atItsEdge(GameObject hero, GameObject there) {
        return there != null && near(hero, rules.reach() + there.getGeometry().footprintRadius());
    }

    /**
     * Whether his legs walked where they were going and that was the end of it: as near as the ground would let them,
     * which is an answer, where legs that stopped short are not one.
     */
    private static boolean arrived(MoveUpdate legs, Coord3D going) {
        return going != null && !legs.stoppedShort() && legs.isGoalReachable();
    }

    /** Whether he stands within {@code reach} of it, across the floor: how high either is does not come into it. */
    private boolean near(GameObject hero, float reach) {
        return within(hero.getPosition(), goal, reach);
    }

    private static boolean within(Coord3D a, Coord3D b, float reach) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return dx * dx + dy * dy <= reach * reach;
    }

    /** Whether it has been done or given up. */
    public boolean isOver() {
        return over;
    }
}
