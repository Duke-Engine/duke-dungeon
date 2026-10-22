package uz.dukeengine.dungeon.run;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;

/**
 * The horse a hero owns (the whistle in his bag): called to him, walked up to, and his MOUNT skill cast on it; ridden on
 * its Saddle bone while the skill holds; laid beside him when it ends. See docs/plan/2026-10-15-mounts.md.
 *
 * <p>Riding is the skill's (E89): the mounting time, the speed, the look, the held fire, a blow, a second press and the
 * zones he may not ride in. What is left here is the horse as a thing of the world, an attack he orders (which gets him
 * off), the zones he rides into, and his legs shown quicker on the buff row. The simulation's, on its thread.
 */
public final class Stable {

    private final DungeonSettings settings;
    private final Mount mount;
    /** Each player's horse and seat, by his index. */
    private final TreeMap<Integer, Rider> riders = new TreeMap<>();
    private String map = "";
    private List<StaticMap.Zone> zones = List.of();

    private static final class Rider {
        /** His horse on this map, or null: standing about, or carrying him. */
        ObjectId horse;
        /** Riding as the last frame ended, as his book said. */
        boolean riding;
        /** Walking up to the horse to get on: where he was sent, and when. */
        boolean walking;
        Coord3D goal;
        int orderedAt;
        /** Riding as this map was laid: back on a horse as soon as he stands on it. */
        boolean ridesOnLay;
    }

    public Stable(DungeonSettings settings) {
        this.settings = settings;
        this.mount = settings.mount();
    }

    /**
     * A map laid: every horse of the last one gone with it. Each of {@code mounted} -- a save's riders, or whoever rode
     * onto this map -- is back on a horse if he may ride here.
     */
    public void lay(String map, List<StaticMap.Zone> zones, Collection<Integer> mounted) {
        this.map = map == null ? "" : map;
        this.zones = zones == null ? List.of() : List.copyOf(zones);
        riders.clear();
        if (mount.on() && mount.maps().contains(this.map)) {
            for (int player : mounted) {
                rider(player).ridesOnLay = true;
            }
        }
    }

    /** The zones a point is in, as a skill's NotIn reads them: the map's, and Ichkari on a map not out of doors. */
    public Set<String> zonesAt(Coord3D at) {
        var in = new HashSet<String>();
        if (!mount.maps().contains(map)) {
            in.add(Mount.INDOORS);
        }
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        for (var zone : zones) {
            if (zone.holds(at.x(), at.y(), cell)) {
                in.add(zone.name());
            }
        }
        return in;
    }

    private Rider rider(int player) {
        return riders.computeIfAbsent(player, key -> new Rider());
    }

    /** Whether this player's hero is on his horse. */
    public boolean mounted(int player) {
        var rider = riders.get(player);
        return rider != null && rider.riding;
    }

    /** The players riding now, for the save and the next map. */
    public List<Integer> riding() {
        return riders.entrySet().stream().filter(entry -> entry.getValue().riding).map(java.util.Map.Entry::getKey)
                .toList();
    }

    /** His MOUNT skill, or null. */
    private Skill skillOf(GameObject hero) {
        return settings.bookFor(hero.getTemplate().name()).stream().filter(s -> s.effect() == SkillEffect.MOUNT)
                .findFirst().orElse(null);
    }

    /** Whether {@code hero} may ride where he stands: he has the skill, and stands in none of its NotIn. */
    boolean mayRide(GameObject hero) {
        var skill = hero == null || !mount.on() ? null : skillOf(hero);
        return skill != null && zonesAt(hero.getPosition()).stream().noneMatch(skill.notIn()::contains);
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
        if (hero == null || hero.isEffectivelyDead() || !mount.on()) {
            return;
        }
        var rider = rider(player);
        var book = hero.findModule(SkillBook.class);
        if (rider.riding || book != null && book.isRiding()) {
            dismount(logic, player, hero, progress);
            return;
        }
        var horse = rider.horse == null ? null : logic.findObject(rider.horse);
        if (clicked != null && (horse == null || !horse.getId().equals(clicked))) {
            return; // somebody else's horse
        }
        var bag = progress.getLoot();
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
            rider.walking = false;
            if (near(hero, horse)) {
                getUp(hero, horse);
                return;
            }
            var legs = hero.getLocomotor();
            if (legs != null) {
                legs.moveTo(horse.getPosition());
            }
            var move = hero.findModule(MoveUpdate.class);
            rider.walking = true;
            rider.goal = move == null ? null : move.getGoal();
            rider.orderedAt = logic.getFrame();
            return;
        }
        call(logic, rider, hero, horse);
    }

    private boolean near(GameObject hero, GameObject horse) {
        float dx = hero.getPosition().x() - horse.getPosition().x();
        float dy = hero.getPosition().y() - horse.getPosition().y();
        return dx * dx + dy * dy <= mount.reach() * mount.reach();
    }

    /** At the horse: his MOUNT skill cast -- its wind-up his getting up, called off by a step or a blow. */
    private void getUp(GameObject hero, GameObject horse) {
        var skill = skillOf(hero);
        var book = hero.findModule(SkillBook.class);
        var move = hero.findModule(MoveUpdate.class);
        if (skill == null || book == null) {
            return;
        }
        if (move != null && move.isMoving()) {
            move.stop();
        }
        uz.dukeengine.rpg.Facing.turnToward(hero, horse);
        book.cast(skill.key(), 1, null, null);
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
            horse = spawn(logic, rider, puff ? beside(logic, hero) : from);
            if (horse == null) {
                return;
            }
            horse.setOrientation((float) Math.atan2(here.y() - horse.getPosition().y(),
                    here.x() - horse.getPosition().x()));
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

    private GameObject spawn(GameLogic logic, Rider rider, Coord3D at) {
        var template = logic.getThingFactory().findTemplate(mount.horse());
        if (template == null) {
            return null;
        }
        var horse = logic.spawn(template, at, PlayerList.NEUTRAL_INDEX);
        rider.horse = horse.getId();
        return horse;
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
     * A frame of this player's horse and seat: walking up to it, then the skill cast; riding -- the horse under him,
     * his legs on the buff row, and off at an attack he ordered or a place he may not ride; and the horse laid beside
     * him the frame the skill ends, however it ended.
     */
    public void tick(GameLogic logic, int player, GameObject hero, HeroProgress progress) {
        var rider = riders.get(player);
        if (rider == null || hero == null) {
            return;
        }
        var book = hero.findModule(SkillBook.class);
        if (book == null) {
            return;
        }
        var horse = rider.horse == null ? null : logic.findObject(rider.horse);
        if (rider.ridesOnLay && !hero.isEffectivelyDead()) {
            rider.ridesOnLay = false;
            if (mayRide(hero)) {
                horse = horse != null ? horse : spawn(logic, rider, hero.getPosition());
                if (horse != null) {
                    getUp(hero, horse);
                }
            }
        }
        if (rider.walking) {
            walkUp(logic, rider, hero, horse);
        }
        if (book.isRiding() && horse != null) {
            var weapon = hero.findModule(WeaponUpdate.class);
            if (!mayRide(hero) || weapon != null && (weapon.isTargetOrdered() || weapon.getPointAttack() != null
                    || weapon.getGroundTarget() != null)) {
                book.dismount(); // an attack he ordered, or a town he rode into
            } else {
                rider.riding = true;
                // The horse goes where he goes: drawn under him (E88), and where he gets off.
                // Off the ground while it carries him (AIRBORNE): no walker bumps into it, he least of all.
                horse.setStatus(uz.dukeengine.core.thing.ObjectStatus.AIRBORNE);
                horse.setPosition(hero.getPosition());
                horse.setOrientation(hero.getOrientation());
                hero.rideOn(horse.getId(), mount.bone(), mount.seatOffset());
                var skill = skillOf(hero);
                book.give(SkillEffect.MOVE_SPEED, hero.getId().value(), skill == null ? 0 : skill.boostAt(1),
                        logic.getFrame() + 1);
                return;
            }
        }
        if (rider.riding) {
            offIt(logic, rider, hero, horse);
        }
        if (horse != null && !rider.riding) {
            // The horse he called stops by him, wherever he has gone since.
            var its = horse.findModule(MoveUpdate.class);
            if (its != null && its.isMoving() && near(hero, horse)) {
                its.stop();
            }
        }
    }

    /** Walking up to the horse: at it, the skill cast; sent elsewhere, or got no nearer, given up. */
    private void walkUp(GameLogic logic, Rider rider, GameObject hero, GameObject horse) {
        var move = hero.findModule(MoveUpdate.class);
        if (horse == null || move == null) {
            rider.walking = false;
        } else if (near(hero, horse)) {
            rider.walking = false;
            getUp(hero, horse);
        } else if (!move.isMoving() && logic.getFrame() > rider.orderedAt + 2
                || move.getGoal() != null && !move.getGoal().equals(rider.goal)) {
            rider.walking = false;
        }
    }

    /** The skill ended: drawn as himself again, the horse standing beside him where he got off. */
    private void offIt(GameLogic logic, Rider rider, GameObject hero, GameObject horse) {
        rider.riding = false;
        hero.rideOn(null, null, null);
        if (horse != null) {
            var at = beside(logic, hero);
            horse.clearStatus(uz.dukeengine.core.thing.ObjectStatus.AIRBORNE);
            horse.setPosition(at);
            horse.setOrientation(hero.getOrientation());
        }
        logic.effect(mount.puff(), hero);
    }

    /** Off his horse, at once: a skill pressed on horseback, work, his key again. The rest is the next tick's. */
    public void dismount(GameLogic logic, int player, GameObject hero, HeroProgress progress) {
        var rider = riders.get(player);
        var book = hero == null ? null : hero.findModule(SkillBook.class);
        if (book != null) {
            book.dismount();
        }
        if (rider != null && rider.riding && hero != null) {
            offIt(logic, rider, hero, rider.horse == null ? null : logic.findObject(rider.horse));
        }
    }

    private void say(LootBag bag, String words, int frame) {
        if (!words.isBlank()) {
            bag.say(words, frame, settings.lootDrops().noteFrames());
        }
    }
}
