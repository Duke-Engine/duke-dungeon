package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.craft.Crafting;
import uz.dukeengine.dungeon.food.Food;
import uz.dukeengine.dungeon.gen.DeterministicRng;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.item.LootBag;

/**
 * Food and fire as they are played: the world's bushes, wells, fires and animals laid with the map, its own scenery
 * trees felled (E66), a hero's work at one of them -- walked to, then a few seconds busy looping his work's clip (E67),
 * then what it gives in his bag -- what was taken growing back, the fires he builds going out, and an animal's meat where
 * it fell. See docs/plan/2026-10-15-food-and-fire.md.
 *
 * <p>All of it the simulation's, on its thread: frames and never a clock, a draw from the map's seed, and lists in the
 * order they were made. Of it the save keeps the trees felled (the logic's removed scenery); what is laid is laid fresh,
 * and what was felled grows back its whole time after a load.
 */
public final class Camp {

    /** How near a click must come to a tree's footprint to mean the tree: a click on its crown counts. */
    static final float CLICK_REACH = 8f;

    private final DungeonSettings settings;
    private final Food food;
    private final Crafting crafting;
    /** The map's seed: what a crafted thing's roll is drawn from, with the frame and the player. */
    private long seed;
    /** Each player's work, by his index: walked to (not started) or under way. */
    private final TreeMap<Integer, Chore> chores = new TreeMap<>();
    /** What was taken and grows back, in the order it was taken. */
    private final List<Growing> growing = new ArrayList<>();
    /** The fires heroes built, in the order they were lit. */
    private final List<Burning> burning = new ArrayList<>();
    /** The animals the world laid: not the dungeon's creatures a zone is cleared of. */
    private final List<ObjectId> animals = new ArrayList<>();
    /** The map laid, and the model of each piece of its scenery by index, for the trees that may be felled. */
    private String map = "";
    private List<String> scenery = List.of();

    private static final class Chore {
        final ObjectId thing; // null for a piece of scenery
        final int piece; // the scenery's index, or -1
        final Food.Gather gather; // null for a meal at a fire
        /** What is crafted, where he stands: no thing, no walk (docs/plan/2026-10-15-crafting.md); else null. */
        Crafting.Recipe recipe;
        int level;
        Coord3D goal;
        final int orderedAt;
        int startedAt = -1;
        int endsAt;
        Coord3D standing;
        float health;

        Chore(ObjectId thing, int piece, Food.Gather gather, Coord3D goal, int orderedAt) {
            this.thing = thing;
            this.piece = piece;
            this.gather = gather;
            this.goal = goal;
            this.orderedAt = orderedAt;
        }
    }

    /** What grows back: a thing of {@code thing}, or the scenery {@code piece}, at {@code at}. */
    private record Growing(String thing, int piece, Coord3D at, float facing, int frame, ObjectId leaves) {
    }

    private record Burning(ObjectId fire, int outAt) {
    }

    public Camp(DungeonSettings settings) {
        this.settings = settings;
        this.food = settings.food();
        this.crafting = settings.crafting();
    }

    /**
     * The world's things for map {@code map}, laid as the map is: each Forage line's count of its thing round its zone,
     * between its two distances, on ground a hero stands on and clear of scenery, drawn from {@code seed}. Animals
     * stand for {@code animalsSide} at {@code level}. {@code models} is the model of each piece of the map's scenery,
     * by index; {@code felled} the pieces a save says were felled, taken away again and growing back from now. What grew
     * and burnt on the map before is forgotten.
     */
    public void lay(GameLogic logic, String map, long seed, List<StaticMap.Zone> zones, int animalsSide, int level,
            List<String> models, List<Integer> felled) {
        chores.clear();
        growing.clear();
        burning.clear();
        animals.clear();
        this.map = map;
        this.seed = seed;
        this.scenery = models == null ? List.of() : List.copyOf(models);
        var grid = logic.getPathGrid();
        if (grid == null) {
            return;
        }
        if (felled != null && !felled.isEmpty() && food.sceneryMaps().contains(map)) {
            logic.setRemovedScenery(felled);
            for (int piece : felled) {
                var gather = gatherOf(piece);
                var footprint = logic.sceneryPiece(piece);
                if (gather != null && footprint != null) {
                    var at = new Coord3D(footprint.x(), footprint.y(),
                            logic.groundHeight(new Coord3D(footprint.x(), footprint.y(), 0f)));
                    growing.add(new Growing("", piece, at, 0f, logic.getFrame() + gather.regrow(),
                            leave(logic, gather, at, 0f)));
                }
            }
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
                var thing = logic.spawn(template, at, animal ? animalsSide : PlayerList.NEUTRAL_INDEX);
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
        if (grid.isBlocked(cx, cy) || grid.isCliff(cx, cy) || !grid.clearOfCircles(x, y, 5f)
                || grid.hasStepCosts() && grid.stepPercent(cx, cy) < 100) {
            return false; // nor on a road
        }
        // Open all round, so a thing never shuts a gap one cell wide.
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.isBlocked(cx + dx, cy + dy)) {
                    return false;
                }
            }
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

    /** The work of the scenery piece {@code piece}: its model's, on a map whose scenery is worked; null for none. */
    private Food.Gather gatherOf(int piece) {
        return piece < 0 || piece >= scenery.size() || !food.sceneryMaps().contains(map) ? null
                : food.gather(scenery.get(piece));
    }

    /** The piece of this map's scenery a click at {@code place} means and that may be felled, or -1. */
    public int pieceAt(GameLogic logic, Coord3D place) {
        if (place == null || !food.sceneryMaps().contains(map)) {
            return -1;
        }
        int piece = logic.sceneryNear(place, CLICK_REACH);
        return gatherOf(piece) == null ? -1 : piece;
    }

    /** What a click on the ground at {@code place} gives: the chop's word over a tree that may be felled, else null. */
    public String wordAt(GameLogic logic, Coord3D place) {
        int piece = pieceAt(logic, place);
        return piece < 0 ? null : gatherOf(piece).word();
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
     * A player's hero sent to work {@code id} -- or, with none, the tree of the map's scenery at {@code place}: at it
     * now if he is near enough, or walked over. A thing nobody works is no work. Whatever he was doing is the caller's
     * to call off.
     */
    public void order(GameLogic logic, int player, GameObject hero, ObjectId id, Coord3D place) {
        stop(player, hero);
        if (hero == null || hero.isEffectivelyDead()) {
            return;
        }
        Chore chore;
        if (id == null) {
            int piece = pieceAt(logic, place);
            var footprint = piece < 0 ? null : logic.sceneryPiece(piece);
            if (footprint == null) {
                return;
            }
            // Beside its trunk, on his side of it.
            var here = hero.getPosition();
            float dx = here.x() - footprint.x();
            float dy = here.y() - footprint.y();
            float length = Math.max(0.001f, (float) Math.sqrt(dx * dx + dy * dy));
            float off = footprint.radius() + food.reach() * 0.6f;
            float x = footprint.x() + dx / length * off;
            float y = footprint.y() + dy / length * off;
            chore = new Chore(null, piece, gatherOf(piece),
                    new Coord3D(x, y, logic.groundHeight(new Coord3D(x, y, 0f))), logic.getFrame());
        } else {
            var thing = logic.findObject(id);
            if (thing == null) {
                return;
            }
            var name = thing.getTemplate().name();
            var gather = food.gather(name);
            if (gather == null && !name.equals(food.campfire())) {
                return;
            }
            chore = new Chore(id, -1, gather,
                    gather == null || uz.dukeengine.core.thing.Solid.of(thing.getTemplate()).footprintRadius() <= 0f
                            ? thing.getPosition() : logic.standingNextTo(hero, thing), logic.getFrame());
        }
        chores.put(player, chore);
        if (!near(logic, hero, chore)) {
            var legs = hero.getLocomotor();
            if (legs != null) {
                legs.moveTo(chore.goal);
            }
            var move = hero.findModule(MoveUpdate.class);
            chore.goal = move == null ? null : move.getGoal(); // where the legs took it to be
        }
    }

    /**
     * A player's hero set to craft {@code recipe} where he stands, at item level {@code level}: started now -- his
     * clip looping, the hammer ringing -- or, his backpack short of a part, refused with a word. Whatever he was
     * doing is the caller's to call off; whether he knows it and has the gold is the caller's to judge.
     */
    public void craft(GameLogic logic, int player, GameObject hero, LootBag bag, Crafting.Recipe recipe, int level) {
        stop(player, hero);
        var move = hero == null ? null : hero.findModule(MoveUpdate.class);
        if (hero == null || hero.isEffectivelyDead() || move == null) {
            return;
        }
        var chore = new Chore(null, -1, null, hero.getPosition(), logic.getFrame());
        chore.recipe = recipe;
        chore.level = level;
        chores.put(player, chore);
        start(logic, player, hero, bag, chore, move, logic.getFrame());
    }

    private boolean near(GameLogic logic, GameObject hero, Chore chore) {
        if (chore.recipe != null) {
            return true;
        }
        var here = hero.getPosition();
        if (chore.piece >= 0) {
            var footprint = logic.sceneryPiece(chore.piece);
            float dx = here.x() - footprint.x();
            float dy = here.y() - footprint.y();
            float reach = footprint.radius() + food.reach() + uz.dukeengine.core.thing.Solid.of(hero.getTemplate()).footprintRadius();
            return dx * dx + dy * dy <= reach * reach;
        }
        var thing = logic.findObject(chore.thing);
        if (chore.gather != null) {
            return World.reachBetween(hero, thing) <= food.reach();
        }
        float dx = here.x() - thing.getPosition().x();
        float dy = here.y() - thing.getPosition().y();
        return dx * dx + dy * dy <= food.fireRadius() * food.fireRadius();
    }

    /** Whether this player's hero is at work now, for whatever wants to show it. */
    public boolean working(int player) {
        var chore = chores.get(player);
        return chore != null && chore.startedAt >= 0;
    }

    /** This player's work called off: his clip ended (E67). */
    private void stop(int player, GameObject hero) {
        var chore = chores.remove(player);
        if (chore != null && chore.startedAt >= 0 && hero != null) {
            hero.busy(null, 0);
        }
    }

    /** Whether the work's thing is still there: the thing standing, or the tree not yet felled. */
    private static boolean there(GameLogic logic, Chore chore) {
        return chore.recipe != null || (chore.piece >= 0 ? !logic.isSceneryRemoved(chore.piece)
                : logic.findObject(chore.thing) != null);
    }

    /**
     * A frame of this player's work: walking up, starting, busy, done -- or called off, nothing spent, by an order
     * that sends him elsewhere, a step, or a blow taken (the owner: moving or being hit cancels it). {@code gold} is
     * what his purse holds; what a craft finished this frame cost of it is returned, else 0.
     */
    public int tick(GameLogic logic, int player, GameObject hero, LootBag bag, int gold) {
        var chore = chores.get(player);
        if (chore == null) {
            return 0;
        }
        var move = hero == null ? null : hero.findModule(MoveUpdate.class);
        if (!there(logic, chore) || hero == null || hero.isEffectivelyDead() || move == null) {
            stop(player, hero);
            return 0;
        }
        int frame = logic.getFrame();
        var body = hero.getBody();
        if (chore.startedAt < 0) {
            if (near(logic, hero, chore)) {
                start(logic, player, hero, bag, chore, move, frame);
            } else if (!move.isMoving() && frame > chore.orderedAt + 2
                    || move.getGoal() != null && chore.goal != null && !move.getGoal().equals(chore.goal)) {
                chores.remove(player); // sent elsewhere, or got no nearer
            }
            return 0;
        }
        var here = hero.getPosition();
        float dx = here.x() - chore.standing.x();
        float dy = here.y() - chore.standing.y();
        // A blow taken ends his clip (E67), and so his work; a fall in health the same frame a fountain makes up for
        // would not show.
        boolean struck = food.clipOf(word(chore)) != null && hero.getBusyClip() == null && frame < chore.endsAt;
        if (move.isMoving() || dx * dx + dy * dy > 0.25f || struck || body.getHealth() < chore.health - 0.01f) {
            stop(player, hero);
            return 0;
        }
        chore.health = body.getHealth();
        var word = word(chore);
        if ((frame - chore.startedAt) % food.beatFrames() == 0) {
            // Seen and heard, not decided: events, out of the checksum.
            logic.effect("Work" + word, hero);
            logic.sound("work." + word.toLowerCase(java.util.Locale.ROOT), hero, 0);
        }
        if (frame >= chore.endsAt) {
            stop(player, hero);
            if (chore.recipe != null) {
                return crafted(logic, hero, bag, chore, frame, player, gold);
            } else if (chore.gather == null) {
                cook(bag, frame, player);
            } else {
                gathered(logic, bag, chore, frame, player);
            }
        }
        return 0;
    }

    private static String word(Chore chore) {
        return chore.recipe != null ? Food.CRAFT : chore.gather == null ? Food.COOK : chore.gather.word();
    }

    private int framesOf(Chore chore) {
        if (chore.recipe != null) {
            return chore.recipe.frames() > 0 ? chore.recipe.frames() : crafting.frames();
        }
        return chore.gather == null ? food.cookFrames() : chore.gather.frames();
    }

    private void start(GameLogic logic, int player, GameObject hero, LootBag bag, Chore chore, MoveUpdate move,
            int frame) {
        if (chore.recipe != null ? !crafting.match(chore.recipe, bag, settings.items()).all()
                : chore.gather == null && recipe(bag) == null) {
            say(bag, chore.recipe != null ? crafting.missingWord() : food.nothingWord(), frame);
            chores.remove(player);
            return;
        }
        if (move.isMoving()) {
            move.stop();
        }
        if (chore.piece >= 0) {
            var footprint = logic.sceneryPiece(chore.piece);
            hero.setOrientation((float) Math.atan2(footprint.y() - hero.getPosition().y(),
                    footprint.x() - hero.getPosition().x()));
        } else if (chore.thing != null) { // crafting: where he stands, the way he faces
            uz.dukeengine.rpg.Facing.turnToward(hero, logic.findObject(chore.thing));
        }
        chore.startedAt = frame;
        int frames = framesOf(chore);
        chore.endsAt = frame + frames;
        chore.standing = hero.getPosition();
        chore.health = hero.getBody().getHealth();
        hero.busy(food.clipOf(word(chore)), frames); // his work's clip, looped while he stands at it (E67)
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

    private void cook(LootBag bag, int frame, int player) {
        var recipe = recipe(bag);
        if (recipe == null) {
            return; // eaten or dropped while it cooked
        }
        recipe.from().forEach(part -> spend(bag, part));
        give(bag, recipe.makes(), 1, frame, player);
    }

    /**
     * A craft done: its parts spent from the backpack and its gold from the purse, if they are all still there, and
     * the thing made in the bag -- or at his feet, the bag full. Returns the gold it cost, 0 when nothing was made.
     */
    private int crafted(GameLogic logic, GameObject hero, LootBag bag, Chore chore, int frame, int player, int gold) {
        var recipe = chore.recipe;
        var items = settings.items();
        var match = crafting.match(recipe, bag, items);
        if (!match.all() || gold < recipe.gold()) {
            say(bag, crafting.missingWord(), frame); // dropped or sold while he worked
            return 0;
        }
        // The things first, the last place first, so a stack spent empty never moves a place still to be taken.
        match.things().stream().sorted(java.util.Comparator.reverseOrder()).forEach(place -> bag.remove(place));
        for (var part : recipe.parts()) {
            if (items.potion(part.what()) != null) {
                for (int i = 0; i < part.count(); i++) {
                    spend(bag, part.what());
                }
            }
        }
        var made = items.craft(recipe.makes(), recipe.count(), recipe.rarity(), Math.max(chore.level, recipe.level()),
                seed * 1_000_003L + frame * 31L + player);
        if (!bag.take(made, frame, settings.lootDrops().noteFrames())) {
            GroundItem.lay(logic, made.liesAs(settings.lootDrops().template()), made, hero.getPosition(), player);
        }
        logic.sound("craft.done", hero, 0);
        return recipe.gold();
    }

    private void gathered(GameLogic logic, LootBag bag, Chore chore, int frame, int player) {
        var gather = chore.gather;
        if (!give(bag, gather.item(), gather.count(), frame, player)) {
            say(bag, settings.story().trade().fullWord(), frame);
            return;
        }
        if (gather.regrow() <= 0) {
            return; // a well is never used up
        }
        if (chore.piece >= 0) {
            // A tree of the map's own felled: gone from the ground and the picture, on every machine (E66).
            var footprint = logic.sceneryPiece(chore.piece);
            var at = new Coord3D(footprint.x(), footprint.y(),
                    logic.groundHeight(new Coord3D(footprint.x(), footprint.y(), 0f)));
            logic.removeScenery(chore.piece);
            growing.add(new Growing("", chore.piece, at, 0f, frame + gather.regrow(), leave(logic, gather, at, 0f)));
            return;
        }
        var thing = logic.findObject(chore.thing);
        var at = thing.getPosition();
        float facing = thing.getOrientation();
        logic.destroyObject(thing);
        growing.add(new Growing(gather.thing(), -1, at, facing, frame + gather.regrow(),
                leave(logic, gather, at, facing)));
    }

    /** What stands where a thing was taken until it grows back -- a stump -- or null for nothing. */
    private static ObjectId leave(GameLogic logic, Food.Gather gather, Coord3D at, float facing) {
        var stump = gather.leaves().isEmpty() ? null : logic.getThingFactory().findTemplate(gather.leaves());
        if (stump == null) {
            return null;
        }
        var left = logic.spawn(stump, at, PlayerList.NEUTRAL_INDEX);
        left.setOrientation(facing);
        return left.getId();
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
                PlayerList.NEUTRAL_INDEX);
        burning.add(new Burning(fire.getId(), frame + food.fireFrames()));
        stop(player, hero);
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
            if (grown.leaves() != null && logic.findObject(grown.leaves()) != null) {
                logic.destroyObject(logic.findObject(grown.leaves()));
            }
            if (grown.piece() >= 0) {
                logic.restoreScenery(grown.piece());
            } else {
                var template = logic.getThingFactory().findTemplate(grown.thing());
                if (template != null) {
                    logic.spawn(template, grown.at(), PlayerList.NEUTRAL_INDEX).setOrientation(grown.facing());
                }
            }
            it.remove();
        }
    }
}
