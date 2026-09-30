package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.combat.LevelBonus;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.level.GrowableBody;
import uz.dukeengine.dungeon.loot.LootDrop;
import uz.dukeengine.dungeon.loot.LootTable;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.stage.StageCheck;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.GamePlayer;
import uz.dukeengine.rts.module.ExperienceModule;

/**
 * Puts a generated floor into the world, and makes each of its inhabitants as
 * dangerous as its level says it should be.
 *
 * <p>Shared by the first floor and every one after it, because they are the same
 * act: the only difference between the dungeon a run opens on and the one that
 * follows a dead boss is the number. Two copies of this would drift, and the one
 * that drifted would be the deeper floors nobody tests as often.
 *
 * <p>A level is applied to the individual, not to the template. The same Runner
 * appears on every floor and at every level — a template says what a thing is,
 * and this says where it was found. All three effects use seams the engine already
 * offers rather than new engine features: a growable body for health, a damage
 * modifier module for damage, and a replaced experience module for what killing it
 * is worth.
 */
public final class Spawner {

    private Spawner() {
    }

    /** Everything a floor puts in the world — the heroes in the order they were given — and the boss it hangs its exit on. */
    public record Placed(List<GameObject> heroes, GameObject boss, List<GameObject> monsters) {
    }

    /**
     * Lay out a floor: each player's hero at the way in, each monster at its level along the way -- {@code depth} is
     * the place's tier -- the boss at its own in its keep — or the furthest room, where none fits — and, from
     * {@code drops}, what the inhabitants leave behind.
     *
     * <p>The heroes are told rather than looked up, because once a player may choose there is no single answer in
     * the file to look up: {@code DefaultHero} is who plays when nobody was asked, and a menu is somebody being
     * asked. The first stands where the floor lets him in; the rest on the nearest floor beside him, a cell each.
     * Where the run names something to stand at the way in — the fountain — it stands there and they round it.
     *
     * <p>What they leave behind is hung on each monster as it is placed rather than written into its creature
     * block, beside its level and for the same reason: a template says what a thing is, and what it leaves
     * depends on where it was met.
     */
    public static Placed place(DukeGame game, List<GamePlayer> heroPlayers, List<String> heroTemplates,
            GamePlayer dungeonPlayer, GeneratedDungeon dungeon, DungeonSettings settings, int depth,
            LootTable drops) {
        var logic = game.getLogic();
        // What stands where the floor lets them in -- the fountain -- with the heroes round it rather than in it.
        var standing = settings.run().wayIn();
        var fountain = standing.isBlank() || logic.getThingFactory().findTemplate(standing) == null ? null
                : roomForTheWayIn(dungeon);
        var underIt = fountain == null ? java.util.Set.<Long>of() : around(fountain, 1);
        var spots = wayIn(dungeon, heroPlayers.size(), fountain == null ? dungeon.hero() : fountain, underIt);
        var heroes = new ArrayList<GameObject>();
        for (int i = 0; i < heroPlayers.size(); i++) {
            var name = heroTemplates.get(i);
            var template = name == null ? null : logic.getThingFactory().findTemplate(name);
            heroes.add(template == null || i >= spots.size() ? null
                    : logic.spawn(template, at(logic, spots.get(i)), heroPlayers.get(i).getIndex()));
        }

        var monsters = new ArrayList<GameObject>();
        var levels = levelsOf(dungeon, settings, depth);
        for (int i = 0; i < levels.length; i++) {
            var monster = dungeon.monsters().get(i);
            var spawned = spawn(game, dungeonPlayer, monster.kind(), at(logic, monster.at()));
            if (spawned != null) {
                scale(spawned, levels[i], settings);
                dropsFrom(spawned, drops, settings, depth, false);
                monsters.add(spawned);
            }
        }

        // Whatever stands about in the rooms. Spawned like anything else and then
        // left alone: they have a shape, so the engine bakes them into the
        // navigation grid and bodies stop at them, and no body, so neither brain
        // will ever pick one as something to hit. All but the gate, which is
        // turned: its shape is a box as long as its doorway, which it shuts only
        // lying along the wall, where every other prop is round, or has no shape.
        for (var prop : dungeon.props()) {
            if (!underIt.contains(key(prop.at().cellX(), prop.at().cellY()))) {
                var thing = spawn(game, dungeonPlayer, prop.kind(), at(logic, prop.at()));
                if (thing != null && thing.findModule(GateUpdate.class) != null) {
                    thing.setOrientation(acrossTheDoorway(dungeon, prop.at()));
                }
            }
        }

        var boss = spawn(game, dungeonPlayer, dungeon.boss().kind(), at(logic, dungeon.boss().at()));
        if (boss != null) {
            scale(boss, settings.bossLevel(depth), settings);
            dropsFrom(boss, drops, settings, depth, true);
        }
        if (fountain != null) {
            spawn(game, dungeonPlayer, standing, at(logic, fountain));
        }
        return new Placed(java.util.Collections.unmodifiableList(heroes), boss, List.copyOf(monsters));
    }

    /**
     * The level each of a floor's monsters stands at, in the order the floor lists them, in a place of {@code tier}:
     * its share of the way from the way in to the middle of the chamber before the boss's -- the one the keep's road
     * leaves from, or the boss's own place where there is no keep -- by the steps the floor is walked in (see
     * {@link StageCheck#walk}). Everything past that chamber, the keep's court and its guard included, stands at its
     * level.
     */
    static int[] levelsOf(GeneratedDungeon dungeon, DungeonSettings settings, int tier) {
        var walk = StageCheck.walk(dungeon);
        int way = -1;
        if (dungeon.keep() != null) {
            var chamber = dungeon.rooms().get(dungeon.keep().chamber());
            way = walk.to(chamber.centerCellX(), chamber.centerCellY());
        } else if (dungeon.boss() != null && dungeon.boss().at() != null) {
            way = walk.to(dungeon.boss().at().cellX(), dungeon.boss().at().cellY());
        }
        var levels = new int[dungeon.monsters().size()];
        for (int i = 0; i < levels.length; i++) {
            var at = dungeon.monsters().get(i).at();
            levels[i] = settings.levelAlong(tier, walk.to(at.cellX(), at.cellY()), way);
        }
        return levels;
    }

    /** How many steps from the way in a fountain may stand, looking for room enough round it. */
    private static final int ROOM_SEARCH_STEPS = 8;

    /**
     * Where the thing that stands at the way in goes: the way in itself, or the nearest cell to it with open floor
     * two cells all round — so there is a walk round it, and nothing it stands on is anybody's — or {@code null}
     * for a floor with no such cell near where the heroes come in.
     */
    static GeneratedDungeon.Placement roomForTheWayIn(GeneratedDungeon dungeon) {
        var entrance = dungeon.hero();
        if (entrance == null) {
            return null;
        }
        var rows = dungeon.levelMap().strip().split("\n");
        char storey = storeyAt(rows, entrance.cellX(), entrance.cellY());
        var standing = new java.util.HashSet<Long>();
        for (var monster : dungeon.monsters()) {
            standing.add(key(monster.at().cellX(), monster.at().cellY()));
        }
        if (dungeon.boss() != null && dungeon.boss().at() != null) {
            standing.add(key(dungeon.boss().at().cellX(), dungeon.boss().at().cellY()));
        }
        var seen = new java.util.HashSet<Long>();
        var queue = new java.util.ArrayDeque<int[]>();
        seen.add(key(entrance.cellX(), entrance.cellY()));
        queue.add(new int[] {entrance.cellX(), entrance.cellY(), 0});
        while (!queue.isEmpty()) {
            var at = queue.poll();
            var here = GeneratedDungeon.Placement.atCell(at[0], at[1]);
            if (openAround(rows, storey, at[0], at[1], 2)
                    && java.util.Collections.disjoint(standing, around(here, 1))) {
                return here;
            }
            if (at[2] >= ROOM_SEARCH_STEPS) {
                continue;
            }
            for (var step : STEPS) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                if (storeyAt(rows, x, y) == storey && seen.add(key(x, y))) {
                    queue.add(new int[] {x, y, at[2] + 1});
                }
            }
        }
        return null;
    }

    /** Whether every cell within {@code reach} of a cell, the corners too, is floor of {@code storey}. */
    private static boolean openAround(String[] rows, char storey, int x, int y, int reach) {
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                if (storeyAt(rows, x + dx, y + dy) != storey) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The cells within {@code reach} of a place's own, the corners too. */
    private static java.util.Set<Long> around(GeneratedDungeon.Placement at, int reach) {
        var cells = new java.util.HashSet<Long>();
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                cells.add(key(at.cellX() + dx, at.cellY() + dy));
            }
        }
        return java.util.Set.copyOf(cells);
    }

    /** The four ways out of a cell, in the order every walk over the floor here takes them. */
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** The floor's own way in, with nothing standing there that they gather round. */
    static List<GeneratedDungeon.Placement> wayIn(GeneratedDungeon dungeon, int count) {
        return wayIn(dungeon, count, dungeon.hero(), java.util.Set.of());
    }

    /**
     * Where {@code count} heroes come in: {@code from}, then the nearest open cells beside it — walked out from it
     * a step at a time over floor of the same storey, so a party never stands on the far side of a wall or up a
     * ledge, and never where a monster, a boss or a prop already does, nor on the cells {@code standing} keeps: a
     * fountain's, which they gather round.
     */
    static List<GeneratedDungeon.Placement> wayIn(GeneratedDungeon dungeon, int count,
            GeneratedDungeon.Placement from, java.util.Set<Long> standing) {
        var spots = new ArrayList<GeneratedDungeon.Placement>();
        if (from == null || count <= 0) {
            return spots;
        }
        var rows = dungeon.levelMap().strip().split("\n");
        var taken = new java.util.HashSet<>(standing);
        for (var monster : dungeon.monsters()) {
            taken.add(key(monster.at().cellX(), monster.at().cellY()));
        }
        for (var prop : dungeon.props()) {
            taken.add(key(prop.at().cellX(), prop.at().cellY()));
        }
        if (dungeon.boss() != null && dungeon.boss().at() != null) {
            taken.add(key(dungeon.boss().at().cellX(), dungeon.boss().at().cellY()));
        }
        char storey = storeyAt(rows, from.cellX(), from.cellY());
        // The first where he is let in, or -- a fountain standing there -- on the nearest open floor to it; the rest
        // beside him, so a party comes in together on one side of it rather than round both.
        var first = nearestOpen(rows, storey, from, taken);
        if (first == null) {
            return spots;
        }
        spots.add(first);
        taken.add(key(first.cellX(), first.cellY()));
        var seen = new java.util.HashSet<Long>();
        var queue = new java.util.ArrayDeque<int[]>();
        seen.add(key(first.cellX(), first.cellY()));
        queue.add(new int[] {first.cellX(), first.cellY()});
        while (!queue.isEmpty() && spots.size() < count) {
            var at = queue.poll();
            for (var step : STEPS) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                if (storeyAt(rows, x, y) != storey || !seen.add(key(x, y))) {
                    continue;
                }
                queue.add(new int[] {x, y});
                if (spots.size() < count && taken.add(key(x, y))) {
                    spots.add(GeneratedDungeon.Placement.atCell(x, y));
                }
            }
        }
        return spots;
    }

    /** {@code from} if nothing keeps it, or else the nearest cell of its storey nothing does; {@code null} for none. */
    private static GeneratedDungeon.Placement nearestOpen(String[] rows, char storey, GeneratedDungeon.Placement from,
            java.util.Set<Long> taken) {
        var seen = new java.util.HashSet<Long>();
        var queue = new java.util.ArrayDeque<int[]>();
        seen.add(key(from.cellX(), from.cellY()));
        queue.add(new int[] {from.cellX(), from.cellY()});
        while (!queue.isEmpty()) {
            var at = queue.poll();
            if (!taken.contains(key(at[0], at[1]))) {
                return GeneratedDungeon.Placement.atCell(at[0], at[1]);
            }
            for (var step : STEPS) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                if (storeyAt(rows, x, y) == storey && seen.add(key(x, y))) {
                    queue.add(new int[] {x, y});
                }
            }
        }
        return null;
    }

    /** The storey a cell stands on as the map writes it — a digit — or rock for anything else and off the map. */
    private static char storeyAt(String[] rows, int x, int y) {
        if (y < 0 || y >= rows.length || x < 0 || x >= rows[y].length()) {
            return '#';
        }
        char cell = rows[y].charAt(x);
        return Character.isDigit(cell) ? cell : '#';
    }

    /**
     * Which way a gate faces to stand across the doorway it was put in: along the wall, which is the way the rock lies
     * two cells off either side of it — the cell either side of it is the doorway's own.
     */
    static float acrossTheDoorway(GeneratedDungeon dungeon, GeneratedDungeon.Placement at) {
        var rows = dungeon.asciiMap().strip().split("\n");
        int x = at.cellX();
        int y = at.cellY();
        return rock(rows, x - 2, y) && rock(rows, x + 2, y) ? 0f : (float) (StrictMath.PI / 2);
    }

    /** Whether a cell is rock as the map writes it, off the map included. */
    private static boolean rock(String[] rows, int x, int y) {
        return y < 0 || y >= rows.length || x < 0 || x >= rows[y].length() || rows[y].charAt(x) == '#';
    }

    private static long key(int x, int y) {
        return ((long) y << 32) | (x & 0xFFFFFFFFL);
    }

    private static GameObject spawn(DukeGame game, GamePlayer owner, String kind, Coord3D where) {
        var template = game.getLogic().getThingFactory().findTemplate(kind);
        return template == null ? null
                : game.getLogic().spawn(template, where, owner.getIndex());
    }

    /**
     * Make one creature its level -- a monster placed on the floor, its boss, or whatever rises from a rift: its
     * health grown, a {@link LevelBonus} saying the level and what it gives, its worth set, its pool -- if its kind
     * names one -- sized and filled, and the word its bar reads the level from ({@code level:8}; the {@code UnitBar}'s
     * {@code LevelWord}). The one step for all of them, so a creature that rises is made exactly as one placed there
     * would be.
     *
     * <p>Each multiplier is computed from the level in one step rather than
     * compounded level by level, so a level is the same however it was reached.
     */
    public static void scale(GameObject monster, int level, DungeonSettings settings) {
        float health = settings.healthAtLevel(level);
        if (monster.getBody() instanceof GrowableBody body && health > 1f) {
            body.growMaxHealth(body.getMaxHealth() * (health - 1f));
        }
        monster.addModule(new LevelBonus(monster, level, settings.damageAtLevel(level), health));
        if (!settings.unitBar().levelWord().isBlank()) {
            monster.setCondition(settings.unitBar().levelWord() + level);
        }
        // What it casts out of, grown by its level and full: a monster is met rested. A kind that names no pool is
        // given none, and casts free.
        var book = monster.findModule(SkillBook.class);
        var kind = settings.monster(monster.getTemplate().name());
        if (book != null && kind != null && kind.maxMana() > 0) {
            book.poolOf(settings.manaAtLevel(kind.maxMana(), level), settings.manaAtLevel(kind.manaRegen(), level));
            book.fillMana();
        }
        float experience = settings.experienceAtLevel(level);
        // What killing it is worth is fixed by its template, and the template is
        // the same at every level — so the module is swapped for one that says a
        // bigger number, in the place the old one held.
        //
        // The replacement carries no ranks, which is not a loss: monsters here are
        // written with `ExperienceRequired = 0 0 0`, meaning they count experience
        // and never promote. Giving one a ladder would need that ladder read back
        // off the old module, which the engine does not expose — worth knowing
        // before writing a monster that levels up.
        var worth = monster.findModule(ExperienceModule.class);
        if (worth != null && experience != 1f) {
            var scaled = new ExperienceModule.Data(
                    Math.round(worth.getExperienceValue() * experience), List.of(), false);
            monster.replaceModule(worth, new ExperienceModule(monster, scaled));
        }
    }

    /** Give a monster something to leave behind, if the game asked for loot at all. */
    private static void dropsFrom(GameObject monster, LootTable drops, DungeonSettings settings,
            int depth, boolean boss) {
        if (drops != null && !settings.lootDrops().template().isBlank()) {
            monster.addModule(new LootDrop(monster, drops, settings.lootDrops().template(), depth, boss));
        }
    }

    /**
     * Where the generator put something, standing on the floor that is under it.
     *
     * <p>A dungeon has storeys now, and a monster spawned at zero on the second
     * one is a monster sunk to the waist in its own floor until it takes a step.
     */
    private static Coord3D at(uz.dukeengine.core.GameLogic logic, GeneratedDungeon.Placement placement) {
        var ground = new Coord3D(placement.x(), placement.y(), 0f);
        return new Coord3D(placement.x(), placement.y(), logic.groundHeight(ground));
    }
}
