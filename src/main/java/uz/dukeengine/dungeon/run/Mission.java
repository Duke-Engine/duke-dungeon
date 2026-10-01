package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.loot.GroundItem;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootBag;
import uz.dukeengine.dungeon.loot.LootKind;
import uz.dukeengine.dungeon.world.Run;
import uz.dukeengine.rts.module.RtsModuleGroups;

/**
 * What a floor with a keep asks of the party, in four steps: kill every monster it put outside the keep, take the
 * key the last of them leaves where it fell, give the key to the gate, and kill the boss behind it.
 *
 * <p>Kept by the run, one to a floor, and the same on every machine: it counts the deaths the engine tells it of, on
 * the frame they happen, lays the key with the world's own spawn, and reads the rest off the world as it stands —
 * whether anyone carries the key, whether the gate still stands. Nothing here reads a clock or a hash.
 *
 * <p>Only the floor's own are waited for: what a summoner calls up later was never put there by the floor. A floor
 * that put none outside lays the key at once, where the heroes came in.
 */
public final class Mission {

    /** The four steps, in the order a floor asks them. */
    public enum Step {
        /** Kill everything the floor put outside the keep. */
        CLEAR,
        /** Take the key the last of them left. */
        TAKE,
        /** Give it to the gate. */
        GIVE,
        /** Kill the boss: the gate is open, or there was no keep. */
        KILL
    }

    private final Loot key;
    /** What a thing lies in when it names nothing of its own; the key names itself. */
    private final String chest;
    private final int floorOwner;
    /** The gate across the keep's doorway, or null where the floor stood none. */
    private final ObjectId gate;
    /** How many the floor put outside the keep, and how many of them have died. */
    private final int outside;
    private int killed;
    private boolean keyLaid;

    private Mission(Loot key, String chest, int floorOwner, ObjectId gate, int outside) {
        this.key = key;
        this.chest = chest;
        this.floorOwner = floorOwner;
        this.gate = gate;
        this.outside = outside;
    }

    /**
     * The mission of a floor just laid, or null for one with no keep — a stage, or a floor where none fitted — or a
     * game with no key to give. Every monster placed outside the keep is told to report its death; a floor that put
     * none there lays the key now, where the first hero came in.
     */
    static Mission of(World world, GeneratedDungeon floor, Spawner.Placed placed, DungeonSettings settings,
            int floorOwner) {
        var keep = floor.keep();
        var key = settings.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElse(null);
        if (keep == null || key == null) {
            return null;
        }
        var theirs = new ArrayList<GameObject>();
        for (var monster : placed.monsters()) {
            var at = new GeneratedDungeon.Placement(monster.getPosition().x(), monster.getPosition().y());
            if (!keep.holds(at.cellX(), at.cellY())) {
                theirs.add(monster);
            }
        }
        var mission = new Mission(key, settings.lootDrops().template(), floorOwner,
                placed.gate() == null ? null : placed.gate().getId(), theirs.size());
        for (var monster : theirs) {
            monster.addModule(new Counted(monster, mission));
        }
        if (theirs.isEmpty()) {
            mission.lay(world, wayIn(floor, placed));
        }
        return mission;
    }

    /** Where the heroes came in: where the first of them stands, or the floor's own way in. */
    private static Coord3D wayIn(GeneratedDungeon floor, Spawner.Placed placed) {
        for (var hero : placed.heroes()) {
            if (hero != null) {
                return hero.getPosition();
            }
        }
        return new Coord3D(floor.hero().x(), floor.hero().y(), 0f);
    }

    /** One of the floor's own outside the keep has died: the last of them leaves the key where it fell. */
    private void fell(GameObject monster) {
        killed++;
        if (killed == outside && !keyLaid) {
            lay(monster.getWorld(), monster.getPosition());
        }
    }

    /**
     * Lay the key on the floor at {@code at}. Where that cannot be done — the template it lies as is one nobody has
     * made, or one that holds nothing — the key would lie nowhere and the gate never open, so it is said, loudly and
     * naming the template, rather than left as a floor that cannot be finished.
     */
    private void lay(World world, Coord3D at) {
        var template = key.liesAs(chest);
        if (GroundItem.lay(world, template, key, at, floorOwner) == null) {
            throw new IllegalStateException("the floor's key cannot be laid: '" + template
                    + "' is not a template of a thing that lies holding an item");
        }
        keyLaid = true;
    }

    /** Where the party stands in it now: the key not laid yet, lying, carried, or given and the gate open. */
    public Step step(World world, List<LootBag> bags) {
        if (!keyLaid) {
            return Step.CLEAR;
        }
        if (gate != null && GateUpdate.stands(world, gate)) {
            return bags.stream().anyMatch(bag -> bag.holds(LootKind.KEY)) ? Step.GIVE : Step.TAKE;
        }
        return Step.KILL;
    }

    /** What the tracker says at {@code step}, in the run's words: the count while there is one. */
    public String words(Run run, Step step) {
        return switch (step) {
            case CLEAR -> run.clearWord(killed, outside);
            case TAKE -> run.takeKeyWord();
            case GIVE -> run.giveKeyWord();
            case KILL -> run.killBossWord();
        };
    }

    /** How many monsters the floor put outside its keep. */
    public int outside() {
        return outside;
    }

    /** How many of them have died. */
    public int killed() {
        return killed;
    }

    /** On each monster the floor put outside the keep: its death, told to the mission it belongs to. */
    @ModuleGroup(RtsModuleGroups.ECONOMY)
    static final class Counted extends Module implements DieModule {

        private final Mission mission;

        Counted(GameObject monster, Mission mission) {
            super(monster);
            this.mission = mission;
        }

        @Override
        public void onDie() {
            mission.fell(getOwner());
        }
    }
}
