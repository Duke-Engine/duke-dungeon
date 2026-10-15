package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.food.Food;
import uz.dukeengine.dungeon.gen.DeterministicRng;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.item.LootBag;

/**
 * Food and fire as they are played: the world's trees, bushes, wells, fires and animals laid with the map, a hero's
 * work at one of them -- walked to, then a few seconds busy, then what it gives in his bag -- what was taken growing
 * back, the fires he builds going out, and an animal's meat where it fell. See docs/plan/2026-10-15-food-and-fire.md.
 *
 * <p>All of it the simulation's, on its thread: frames and never a clock, a draw from the map's seed, and lists in the
 * order they were made. Nothing here is saved: a loaded game lays the map, and with it the world's things, fresh.
 */
public final class Camp {

    private final DungeonSettings settings;
    private final Food food;
    /** Each player's work, by his index: walked to (not started) or under way. */
    private final TreeMap<Integer, Chore> chores = new TreeMap<>();
    /** What was taken and grows back, in the order it was taken. */
    private final List<Growing> growing = new ArrayList<>();
    /** The fires heroes built, in the order they were lit. */
    private final List<Burning> burning = new ArrayList<>();
    /** The animals the world laid: not the dungeon's creatures a zone is cleared of. */
    private final List<ObjectId> animals = new ArrayList<>();

    private static final class Chore {
        final ObjectId thing;
        final Food.Gather gather; // null for a meal at a fire
        Coord3D goal;
        final int orderedAt;
        int startedAt = -1;
        int endsAt;
        Coord3D standing;
        float health;

        Chore(ObjectId thing, Food.Gather gather, Coord3D goal, int orderedAt) {
            this.thing = thing;
            this.gather = gather;
            this.goal = goal;
            this.orderedAt = orderedAt;
        }
    }

    private record Growing(String thing, Coord3D at, float facing, int frame, ObjectId leaves) {
    }

    private record Burning(ObjectId fire, int outAt) {
    }

    public Camp(DungeonSettings settings) {
        this.settings = settings;
        this.food = settings.food();
    }

    /**
     * The world's things for map {@code map}, laid as the map is: each Forage line's count of its thing round its zone,
     * between its two distances, on ground a hero stands on and clear of scenery, drawn from {@code seed}. Animals
     * stand for {@code animalsSide} at {@code level}. What grew and burnt on the map before is forgotten.
     */
    public void lay(GameLogic logic, String map, long seed, List<StaticMap.Zone> zones, int animalsSide, int level) {
        chores.clear();
        growing.clear();
        burning.clear();
        animals.clear();
        var grid = logic.getPathGrid();
        if (grid == null) {
            return;
        }
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        var laid = new ArrayList<Coord3D>();
        int line = 0;
        for (var place : food.forage()) {
            line++;
            var zone = zones.stream().filter(each -> each.name().equals(place.zone())).findFirst().orElse(null);
            var template = logic.getThingFactory().findTemplate(place.thing());
            if (!place.map().equals(map) || zone == null || template == null) {
                continue;
            }
            var rng = new DeterministicRng(seed * 1_000_003L + line);
            boolean animal = settings.monster(place.thing()) != null;
            int placed = 0;
            // ponytail: a few tries a thing on open ground, and fewer laid where there is none; enough for a village.
            for (int tries = 0; placed < place.count() && tries < place.count() * 20; tries++) {
                double angle = rng.nextInt(3600) * Math.PI / 1800.0;
                float distance = (place.from() + rng.nextInt(Math.max(1, (place.to() - place.from()) * 10 + 1)) / 10f)
                        * cell;
                float x = zone.middleX(cell) + (float) Math.cos(angle) * distance;
                float y = zone.middleY(cell) + (float) Math.sin(angle) * distance;
                if (!open(logic, x, y, laid)) {
                    continue;
                }
                var at = new Coord3D(x, y, logic.groundHeight(new Coord3D(x, y, 0f)));
                var thing = logic.spawn(template, at, animal ? animalsSide : uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX);
                thing.setOrientation(rng.nextInt(360) * (float) Math.PI / 180f);
                if (animal) {
                    Spawner.scale(thing, level, settings);
                    animals.add(thing.getId());
                }
                laid.add(at);
                placed++;
            }
        }
    }

    /** Ground a thing may stand on: open, no cliff, clear of scenery, and a step from every thing laid. */
    private static boolean open(GameLogic logic, float x, float y, List<Coord3D> laid) {
        var grid = logic.getPathGrid();
        var at = new Coord3D(x, y, 0f);
        int cx = grid.toCellX(at);
        int cy = grid.toCellY(at);
        if (grid.isBlocked(cx, cy) || grid.isCliff(cx, cy) || !grid.clearOfCircles(x, y, 5f)) {
            return false;
        }
        for (var other : laid) {
            float dx = other.x() - x;
            float dy = other.y() - y;
            if (dx * dx + dy * dy < 12f * 12f) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code thing} is one of the world's animals: hunted, but no camp's guard. */
    public boolean isAnimal(GameObject thing) {
        return animals.contains(thing.getId());
    }

    /** A death heard: an animal's meat, lying where it fell for whoever takes it. */
    public void died(World world, uz.dukeengine.core.event.ObjectDied death) {
        for (var hunt : food.hunts()) {
            var meat = settings.items().potion(hunt.item());
            if (hunt.animal().equals(death.templateName()) && meat != null && world != null) {
                var item = settings.items().potion(meat, hunt.count(), death.frame() * 31L + death.object().value());
                GroundItem.lay(world, item.liesAs(settings.lootDrops().template()), item, death.position(),
                        death.playerIndex());
            }
        }
    }

    /**
     * A player's hero sent to work {@code id}: at it now if he is near enough, or walked over. A thing nobody works is
     * no work. Whatever he was doing is the caller's to call off.
     */
    public void order(GameLogic logic, int player, GameObject hero, ObjectId id) {
        chores.remove(player);
        var thing = id == null ? null : logic.findObject(id);
        if (hero == null || hero.isEffectivelyDead() || thing == null) {
            return;
        }
        var name = thing.getTemplate().name();
        var gather = food.gather(name);
        if (gather == null && !name.equals(food.campfire())) {
            return;
        }
        var goal = gather == null ? thing.getPosition() : logic.standingNextTo(hero, thing);
        var chore = new Chore(id, gather, goal, logic.getFrame());
        chores.put(player, chore);
        if (!near(hero, thing, gather)) {
            var legs = hero.getLocomotor();
            if (legs != null) {
                legs.moveTo(goal);
            }
            var move = hero.findModule(MoveUpdate.class);
            chore.goal = move == null ? null : move.getGoal(); // where the legs took it to be
        }
    }

    private boolean near(GameObject hero, GameObject thing, Food.Gather gather) {
        if (gather != null) {
            return World.reachBetween(hero, thing) <= food.reach();
        }
        float dx = hero.getPosition().x() - thing.getPosition().x();
        float dy = hero.getPosition().y() - thing.getPosition().y();
        return dx * dx + dy * dy <= food.fireRadius() * food.fireRadius();
    }

    /** Whether this player's hero is at work now, for whatever wants to show it. */
    public boolean working(int player) {
        var chore = chores.get(player);
        return chore != null && chore.startedAt >= 0;
    }

    /**
     * A frame of this player's work: walking up, starting, busy, done -- or called off, nothing spent, by an order
     * that sends him elsewhere, a step, or a blow taken (the owner: moving or being hit cancels it).
     */
    public void tick(GameLogic logic, int player, GameObject hero, LootBag bag) {
        var chore = chores.get(player);
        if (chore == null) {
            return;
        }
        var thing = logic.findObject(chore.thing);
        var move = hero == null ? null : hero.findModule(MoveUpdate.class);
        if (thing == null || hero == null || hero.isEffectivelyDead() || move == null) {
            chores.remove(player);
            return;
        }
        int frame = logic.getFrame();
        var body = hero.getBody();
        if (chore.startedAt < 0) {
            if (near(hero, thing, chore.gather)) {
                start(logic, player, hero, bag, chore, move, frame);
            } else if (!move.isMoving() && frame > chore.orderedAt + 2
                    || move.getGoal() != null && chore.goal != null && !move.getGoal().equals(chore.goal)) {
                chores.remove(player); // sent elsewhere, or got no nearer
            }
            return;
        }
        var here = hero.getPosition();
        float dx = here.x() - chore.standing.x();
        float dy = here.y() - chore.standing.y();
        if (move.isMoving() || dx * dx + dy * dy > 0.25f || body.getHealth() < chore.health - 0.01f) {
            chores.remove(player);
            return;
        }
        chore.health = body.getHealth();
        var word = chore.gather == null ? Food.COOK : chore.gather.word();
        if ((frame - chore.startedAt) % food.beatFrames() == 0) {
            // Seen and heard, not decided: events, out of the checksum.
            logic.effect("Work" + word, hero);
            logic.sound("work." + word.toLowerCase(java.util.Locale.ROOT), hero, 0);
        }
        if (frame >= chore.endsAt) {
            chores.remove(player);
            if (chore.gather == null) {
                cook(logic, bag, frame, player);
            } else {
                gathered(logic, bag, thing, chore.gather, frame, player);
            }
        }
    }

    private void start(GameLogic logic, int player, GameObject hero, LootBag bag, Chore chore, MoveUpdate move,
            int frame) {
        if (chore.gather == null && recipe(bag) == null) {
            say(bag, food.nothingWord(), frame);
            chores.remove(player);
            return;
        }
        if (move.isMoving()) {
            move.stop();
        }
        var thing = logic.findObject(chore.thing);
        uz.dukeengine.rpg.Facing.turnToward(hero, thing);
        chore.startedAt = frame;
        chore.endsAt = frame + (chore.gather == null ? food.cookFrames() : chore.gather.frames());
        chore.standing = hero.getPosition();
        chore.health = hero.getBody().getHealth();
    }

    /** The first recipe, best first, whose every part is in the bag; null for none. */
    private Food.Recipe recipe(LootBag bag) {
        for (var recipe : food.recipes()) {
            if (recipe.from().stream().allMatch(part -> placeOf(bag, part) >= 0)) {
                return recipe;
            }
        }
        return null;
    }

    private int placeOf(LootBag bag, String potion) {
        var items = settings.items();
        for (int place = 0; place < bag.backpackSize(); place++) {
            var kind = items.potionOf(bag.at(place));
            if (kind != null && kind.name().equals(potion)) {
                return place;
            }
        }
        return -1;
    }

    /** One fewer of {@code potion} in the bag, an emptied stack gone. */
    private void spend(LootBag bag, String potion) {
        int place = placeOf(bag, potion);
        var stack = bag.remove(place);
        if (stack.value() > 1) {
            bag.put(place, stack.worth(stack.value() - 1));
        }
    }

    private void cook(GameLogic logic, LootBag bag, int frame, int player) {
        var recipe = recipe(bag);
        if (recipe == null) {
            return; // eaten or dropped while it cooked
        }
        recipe.from().forEach(part -> spend(bag, part));
        give(bag, recipe.makes(), 1, frame, player);
    }

    private void gathered(GameLogic logic, LootBag bag, GameObject thing, Food.Gather gather, int frame, int player) {
        if (!give(bag, gather.item(), gather.count(), frame, player)) {
            say(bag, settings.story().trade().fullWord(), frame);
            return;
        }
        if (gather.regrow() <= 0) {
            return; // a well is never used up
        }
        var at = thing.getPosition();
        float facing = thing.getOrientation();
        logic.destroyObject(thing);
        ObjectId leaves = null;
        var stump = gather.leaves().isEmpty() ? null : logic.getThingFactory().findTemplate(gather.leaves());
        if (stump != null) {
            var left = logic.spawn(stump, at, uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX);
            left.setOrientation(facing);
            leaves = left.getId();
        }
        growing.add(new Growing(gather.thing(), at, facing, frame + gather.regrow(), leaves));
    }

    /** {@code count} of {@code potion} into the bag, a stack of its own until the run joins it; false for no room. */
    private boolean give(LootBag bag, String potion, int count, int frame, int player) {
        var kind = settings.items().potion(potion);
        return kind != null && bag.take(settings.items().potion(kind, count, frame * 31L + player), frame,
                settings.lootDrops().noteFrames());
    }

    private void say(LootBag bag, String words, int frame) {
        if (!words.isBlank()) {
            bag.say(words, frame, settings.lootDrops().noteFrames());
        }
    }

    /**
     * A fire built at the hero's feet, if he carries the wood for it; it burns its frames and goes out. Says why not
     * when he does not.
     */
    public void build(GameLogic logic, int player, GameObject hero, LootBag bag) {
        var template = logic.getThingFactory().findTemplate(food.campfire());
        if (hero == null || hero.isEffectivelyDead() || template == null) {
            return;
        }
        int frame = logic.getFrame();
        int have = 0;
        for (int place = 0; place < bag.backpackSize(); place++) {
            var kind = settings.items().potionOf(bag.at(place));
            if (kind != null && kind.name().equals(food.wood())) {
                have += bag.at(place).value();
            }
        }
        if (have < food.buildWood()) {
            say(bag, food.noWoodWord().formatted(food.buildWood()), frame);
            return;
        }
        for (int i = 0; i < food.buildWood(); i++) {
            spend(bag, food.wood());
        }
        var here = hero.getPosition();
        float facing = hero.getOrientation();
        // A step in front of him, so he stands by it and not in it.
        float x = here.x() + (float) Math.cos(facing) * 8f;
        float y = here.y() + (float) Math.sin(facing) * 8f;
        var fire = logic.spawn(template, new Coord3D(x, y, logic.groundHeight(new Coord3D(x, y, 0f))),
                uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX);
        burning.add(new Burning(fire.getId(), frame + food.fireFrames()));
        chores.remove(player);
    }

    /** A frame of the world's own: what was taken grows back (not under a hero), and built fires go out. */
    public void grow(GameLogic logic, List<GameObject> heroes) {
        int frame = logic.getFrame();
        for (var it = burning.iterator(); it.hasNext(); ) {
            var fire = it.next();
            if (frame >= fire.outAt()) {
                var thing = logic.findObject(fire.fire());
                if (thing != null) {
                    logic.destroyObject(thing);
                }
                it.remove();
            }
        }
        for (var it = growing.iterator(); it.hasNext(); ) {
            var grown = it.next();
            if (frame < grown.frame() || heroes.stream().anyMatch(hero -> hero != null
                    && Math.abs(hero.getPosition().x() - grown.at().x()) < 5f
                    && Math.abs(hero.getPosition().y() - grown.at().y()) < 5f)) {
                continue;
            }
            var template = logic.getThingFactory().findTemplate(grown.thing());
            if (grown.leaves() != null && logic.findObject(grown.leaves()) != null) {
                logic.destroyObject(logic.findObject(grown.leaves()));
            }
            if (template != null) {
                logic.spawn(template, grown.at(), uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX)
                        .setOrientation(grown.facing());
            }
            it.remove();
        }
    }
}
