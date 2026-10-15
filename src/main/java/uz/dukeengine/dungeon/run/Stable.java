package uz.dukeengine.dungeon.run;

import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.mount.Mount;
import uz.dukeengine.rpg.hero.HeroProgress;
import uz.dukeengine.rpg.item.LootBag;

/**
 * Riding as it is played: the horse a hero owns (the whistle in his bag) called to him, got up onto, ridden at twice his
 * walk and got off -- by his key, a blow, an attack or a cast, or a town or a map he may not ride in. See
 * docs/plan/2026-10-15-mounts.md.
 *
 * <p>The simulation's, on its thread: frames, never a clock. While he rides the horse is no thing of the world: he is
 * drawn as his riding look ({@code drawAs}, which the checksum and the save leave out, so it is set again every
 * frame), and his legs are set to his riding speed every frame, since a level or a thing put on gives him new legs at
 * his walking speed. Engine requests E82 and E83 would make the look a rider on a mount and the speed a skill.
 */
public final class Stable {

    private final DungeonSettings settings;
    private final Mount mount;
    /** Each player's horse and seat, by his index. */
    private final TreeMap<Integer, Rider> riders = new TreeMap<>();
    private String map = "";
    private List<StaticMap.Zone> zones = List.of();

    private static final class Rider {
        /** The horse standing about, or null while he rides it or has not called it on this map. */
        ObjectId horse;
        boolean mounted;
        /** Walked up to the horse to get on, and the frame he started getting up (-1 not yet). */
        boolean mounting;
        int startedAt = -1;
        Coord3D standing;
        Coord3D goal;
        int orderedAt;
        /** His health the frame before: a fall in it is a blow, which unseats him. */
        float health;
        /** The look and the legs he was given, so a body he gets is given them again. */
        ObjectId body;
    }

    public Stable(DungeonSettings settings) {
        this.settings = settings;
        this.mount = settings.mount();
    }

    /**
     * A map laid: every horse of the last one gone with it. Each of {@code mounted} -- a save's riders, or whoever rode
     * onto this map -- is back on his horse if he may ride here.
     */
    public void lay(String map, List<StaticMap.Zone> zones, Collection<Integer> mounted) {
        this.map = map == null ? "" : map;
        this.zones = zones == null ? List.of() : List.copyOf(zones);
        riders.clear();
        if (mount.on() && mount.maps().contains(this.map)) {
            for (int player : mounted) {
                rider(player).mounted = true;
            }
        }
    }

    private Rider rider(int player) {
        return riders.computeIfAbsent(player, key -> new Rider());
    }

    /** Whether this player's hero is on his horse. */
    public boolean mounted(int player) {
        var rider = riders.get(player);
        return rider != null && rider.mounted;
    }

    /** The players riding now, for the save and the next map. */
    public List<Integer> riding() {
        return riders.entrySet().stream().filter(entry -> entry.getValue().mounted).map(java.util.Map.Entry::getKey)
                .toList();
    }

    /** Whether {@code hero} may ride where he stands: on a map out of doors and in none of its towns. */
    boolean mayRide(GameObject hero) {
        if (!mount.on() || !mount.maps().contains(map) || hero == null) {
            return false;
        }
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        var at = hero.getPosition();
        return zones.stream().noneMatch(zone -> mount.noRideZones().contains(zone.name())
                && zone.holds(at.x(), at.y(), cell));
    }

    private boolean owns(LootBag bag) {
        for (int place = 0; place < bag.backpackSize(); place++) {
            var kind = settings.items().potionOf(bag.at(place));
            if (kind != null && kind.name().equals(mount.whistle())) {
                return true;
            }
        }
        return false;
    }

    /**
     * His key, or a right-click on his horse: off it if he rides; else up onto it if it stands near or he clicked it
     * (walked over to it first); else called -- if he owns one and may ride here, which is said when not.
     */
    public void press(GameLogic logic, int player, GameObject hero, HeroProgress progress, ObjectId clicked) {
        var bag = progress.getLoot();
        if (hero == null || hero.isEffectivelyDead() || !mount.on()) {
            return;
        }
        var rider = rider(player);
        if (rider.mounted) {
            dismount(logic, player, hero, progress);
            return;
        }
        var horse = rider.horse == null ? null : logic.findObject(rider.horse);
        if (clicked != null && (horse == null || !horse.getId().equals(clicked))) {
            return; // somebody else's horse
        }
        if (!owns(bag)) {
            say(bag, mount.noHorseWord(), logic.getFrame());
            return;
        }
        if (!mayRide(hero)) {
            say(bag, mount.indoorsWord(), logic.getFrame());
            return;
        }
        if (horse != null && (clicked != null || near(hero, horse))) {
            var its = horse.getLocomotor();
            if (its != null) {
                its.stop(); // it stands to be got onto
            }
            rider.mounting = true;
            rider.startedAt = -1;
            rider.orderedAt = logic.getFrame();
            rider.goal = null;
            if (!near(hero, horse)) {
                var legs = hero.getLocomotor();
                if (legs != null) {
                    legs.moveTo(horse.getPosition());
                }
                var move = hero.findModule(MoveUpdate.class);
                rider.goal = move == null ? null : move.getGoal();
            }
            return;
        }
        call(logic, rider, hero, horse);
    }

    private boolean near(GameObject hero, GameObject horse) {
        float dx = hero.getPosition().x() - horse.getPosition().x();
        float dy = hero.getPosition().y() - horse.getPosition().y();
        return dx * dx + dy * dy <= mount.reach() * mount.reach();
    }

    /**
     * The horse called: one standing on this map runs to him; with none, a new one runs in from CallDistance away, out
     * of the first of eight ways round him -- from behind him on, the same on every machine -- that is open ground, or
     * appears beside him in a puff where none is.
     */
    private void call(GameLogic logic, Rider rider, GameObject hero, GameObject horse) {
        var here = hero.getPosition();
        logic.sound(mount.call(), hero, 0);
        if (horse == null) {
            var template = logic.getThingFactory().findTemplate(mount.horse());
            if (template == null) {
                return;
            }
            Coord3D from = null;
            for (int way = 0; way < 8 && from == null; way++) {
                double angle = hero.getOrientation() + Math.PI + way * Math.PI / 4;
                float x = here.x() + (float) Math.cos(angle) * mount.callDistance();
                float y = here.y() + (float) Math.sin(angle) * mount.callDistance();
                if (open(logic, x, y)) {
                    from = new Coord3D(x, y, logic.groundHeight(new Coord3D(x, y, 0f)));
                }
            }
            boolean puff = from == null;
            if (puff) {
                from = beside(logic, hero);
            }
            horse = logic.spawn(template, from, PlayerList.NEUTRAL_INDEX);
            horse.setOrientation((float) Math.atan2(here.y() - from.y(), here.x() - from.x()));
            rider.horse = horse.getId();
            if (puff) {
                logic.effect(mount.puff(), horse);
                return;
            }
        }
        var legs = horse.getLocomotor();
        if (legs != null) {
            legs.moveTo(beside(logic, hero));
        }
    }

    /** A step to his right, where a horse comes to stand by him. */
    private static Coord3D beside(GameLogic logic, GameObject hero) {
        double angle = hero.getOrientation() - Math.PI / 2;
        float x = hero.getPosition().x() + (float) Math.cos(angle) * 9f;
        float y = hero.getPosition().y() + (float) Math.sin(angle) * 9f;
        if (!open(logic, x, y)) {
            x = hero.getPosition().x();
            y = hero.getPosition().y();
        }
        return new Coord3D(x, y, logic.groundHeight(new Coord3D(x, y, 0f)));
    }

    private static boolean open(GameLogic logic, float x, float y) {
        var grid = logic.getPathGrid();
        if (grid == null) {
            return false;
        }
        var at = new Coord3D(x, y, 0f);
        int cx = grid.toCellX(at);
        int cy = grid.toCellY(at);
        return grid.inBounds(cx, cy) && !grid.isBlocked(cx, cy) && !grid.isCliff(cx, cy);
    }

    /**
     * A frame of this player's horse and seat: getting up (called off by a step, a blow or another order), or riding --
     * his look and legs set, his weapon's own picks let go, and off it at a blow, an attack he ordered, or a place he
     * may not ride.
     */
    public void tick(GameLogic logic, int player, GameObject hero, HeroProgress progress) {
        var rider = riders.get(player);
        if (rider == null || hero == null) {
            return;
        }
        if (hero.isEffectivelyDead()) {
            if (rider.mounted) {
                dismount(logic, player, hero, progress);
            }
            rider.mounting = false;
            return;
        }
        var body = hero.getBody();
        float health = body == null ? 0f : body.getHealth();
        boolean struck = body != null && hero.getId().equals(rider.body) && health < rider.health - 0.01f;
        rider.health = health;
        rider.body = hero.getId();
        if (rider.mounting) {
            getUp(logic, rider, hero, struck);
            return;
        }
        if (!rider.mounted) {
            // The horse he called stops by him, wherever he has gone since.
            var horse = rider.horse == null ? null : logic.findObject(rider.horse);
            var its = horse == null ? null : horse.findModule(MoveUpdate.class);
            if (its != null && its.isMoving()) {
                float dx = hero.getPosition().x() - horse.getPosition().x();
                float dy = hero.getPosition().y() - horse.getPosition().y();
                if (dx * dx + dy * dy <= mount.reach() * mount.reach()) {
                    its.stop();
                }
            }
            return;
        }
        var weapon = hero.findModule(WeaponUpdate.class);
        if (struck || !mayRide(hero) || weapon != null && (weapon.isTargetOrdered() || weapon.getToward() != null
                || weapon.getGroundTarget() != null)) {
            dismount(logic, player, hero, progress);
            return;
        }
        if (weapon != null && weapon.isAttacking()) {
            weapon.holdFire(); // a target it picked for itself: riding past a camp does not unseat him
        }
        hero.drawAs(mount.lookOf(hero.getTemplate().name()), -1);
        var legs = hero.findModule(MoveUpdate.class);
        if (legs != null && progress != null) {
            legs.setSpeed(mount.riding(walking(hero, progress)), turnRate(hero));
            if (legs.isMoving() && logic.getFrame() % mount.gallopFrames() == 0) {
                logic.sound(mount.gallop(), hero, 0); // heard, not decided: an event, out of the checksum
            }
        }
    }

    private void getUp(GameLogic logic, Rider rider, GameObject hero, boolean struck) {
        var horse = rider.horse == null ? null : logic.findObject(rider.horse);
        var move = hero.findModule(MoveUpdate.class);
        if (horse == null || move == null || struck) {
            stopGettingUp(rider, hero);
            return;
        }
        int frame = logic.getFrame();
        if (rider.startedAt < 0) {
            if (near(hero, horse)) {
                if (move.isMoving()) {
                    move.stop();
                }
                uz.dukeengine.rpg.Facing.turnToward(hero, horse);
                rider.startedAt = frame;
                rider.standing = hero.getPosition();
                hero.busy(mount.mountClip(), mount.mountFrames());
            } else if (!move.isMoving() && frame > rider.orderedAt + 2
                    || move.getGoal() != null && !move.getGoal().equals(rider.goal)) {
                rider.mounting = false; // sent elsewhere, or got no nearer
            }
            return;
        }
        float dx = hero.getPosition().x() - rider.standing.x();
        float dy = hero.getPosition().y() - rider.standing.y();
        if (move.isMoving() || dx * dx + dy * dy > 0.25f) {
            stopGettingUp(rider, hero);
            return;
        }
        if (frame >= rider.startedAt + mount.mountFrames()) {
            rider.mounting = false;
            rider.mounted = true;
            logic.destroyObject(horse);
            rider.horse = null;
            logic.effect(mount.puff(), hero);
            hero.drawAs(mount.lookOf(hero.getTemplate().name()), -1);
        }
    }

    private static void stopGettingUp(Rider rider, GameObject hero) {
        if (rider.startedAt >= 0) {
            hero.busy(null, 0);
        }
        rider.mounting = false;
        rider.startedAt = -1;
    }

    /** Off his horse, at once: drawn as himself, at his walk, the horse standing by him where he got off. */
    public void dismount(GameLogic logic, int player, GameObject hero, HeroProgress progress) {
        var rider = riders.get(player);
        if (rider == null || !rider.mounted) {
            return;
        }
        rider.mounted = false;
        if (hero == null) {
            return;
        }
        hero.drawAs(null, -1);
        var legs = hero.findModule(MoveUpdate.class);
        if (legs != null && progress != null) {
            legs.setSpeed(walking(hero, progress), turnRate(hero));
        }
        var template = logic.getThingFactory().findTemplate(mount.horse());
        if (template != null && !hero.isEffectivelyDead()) {
            var horse = logic.spawn(template, beside(logic, hero), PlayerList.NEUTRAL_INDEX);
            horse.setOrientation(hero.getOrientation());
            rider.horse = horse.getId();
        }
        logic.effect(mount.puff(), hero);
    }

    /** His walking speed, as his level and what he carries make it: what his legs are given back. */
    private static float walking(GameObject hero, HeroProgress progress) {
        return progress.figuresOf(hero, progress.found()).speed();
    }

    private static float turnRate(GameObject hero) {
        return uz.dukeengine.rpg.hero.HeroBase.of(hero.getTemplate()).turnRate();
    }

    private void say(LootBag bag, String words, int frame) {
        if (!words.isBlank()) {
            bag.say(words, frame, settings.lootDrops().noteFrames());
        }
    }
}
