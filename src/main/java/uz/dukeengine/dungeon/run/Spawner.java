package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.combat.DepthBonus;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.level.GrowableBody;
import uz.dukeengine.dungeon.loot.LootDrop;
import uz.dukeengine.dungeon.loot.LootTable;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.GamePlayer;
import uz.dukeengine.rts.module.ExperienceModule;

/**
 * Puts a generated floor into the world, and makes its inhabitants as dangerous
 * as their depth says they should be.
 *
 * <p>Shared by the first floor and every one after it, because they are the same
 * act: the only difference between the dungeon a run opens on and the one that
 * follows a dead boss is the number. Two copies of this would drift, and the one
 * that drifted would be the deeper floors nobody tests as often.
 *
 * <p>Depth is applied to the individual, not to the template. The same Runner
 * appears on every floor — a template says what a thing is, and this says where
 * it was found. All three effects use seams the engine already offers rather than
 * new engine features: a growable body for health, a damage modifier module for
 * damage, and a replaced experience module for what killing it is worth.
 */
public final class Spawner {

    private Spawner() {
    }

    /** Everything a floor puts in the world — the heroes in the order they were given — and the boss it hangs its exit on. */
    public record Placed(List<GameObject> heroes, GameObject boss, List<GameObject> monsters) {
    }

    /**
     * Lay out a floor: each player's hero at the way in, its monsters scaled to {@code depth}, the boss in the
     * furthest room, and — from {@code drops} — what the inhabitants leave behind.
     *
     * <p>The heroes are told rather than looked up, because once a player may choose there is no single answer in
     * the file to look up: {@code DefaultHero} is who plays when nobody was asked, and a menu is somebody being
     * asked. The first stands where the floor lets him in; the rest on the nearest floor beside him, a cell each.
     *
     * <p>What they leave behind is hung on each monster as it is placed rather than written into its creature
     * block, beside the depth bonus and for the same reason: a template says what a thing is, and what it leaves
     * depends on where it was met.
     */
    public static Placed place(DukeGame game, List<GamePlayer> heroPlayers, List<String> heroTemplates,
            GamePlayer dungeonPlayer, GeneratedDungeon dungeon, DungeonSettings settings, int depth,
            LootTable drops) {
        var logic = game.getLogic();
        var spots = wayIn(dungeon, heroPlayers.size());
        var heroes = new ArrayList<GameObject>();
        for (int i = 0; i < heroPlayers.size(); i++) {
            var name = heroTemplates.get(i);
            var template = name == null ? null : logic.getThingFactory().findTemplate(name);
            heroes.add(template == null || i >= spots.size() ? null
                    : logic.spawn(template, at(logic, spots.get(i)), heroPlayers.get(i).getIndex()));
        }

        var monsters = new ArrayList<GameObject>();
        for (var monster : dungeon.monsters()) {
            var spawned = spawn(game, dungeonPlayer, monster.kind(), at(logic, monster.at()));
            if (spawned != null) {
                scale(spawned, settings.monsterHealthAt(depth), settings.monsterDamageAt(depth),
                        settings.experienceAt(depth));
                dropsFrom(spawned, drops, settings, depth, false);
                monsters.add(spawned);
            }
        }

        // Whatever stands about in the rooms. Spawned like anything else and then
        // left alone: they have a shape, so the engine bakes them into the
        // navigation grid and bodies stop at them, and no body, so neither brain
        // will ever pick one as something to hit.
        for (var prop : dungeon.props()) {
            spawn(game, dungeonPlayer, prop.kind(), at(logic, prop.at()));
        }

        var boss = spawn(game, dungeonPlayer, dungeon.boss().kind(), at(logic, dungeon.boss().at()));
        if (boss != null) {
            scale(boss, settings.bossHealthAt(depth), settings.bossDamageAt(depth),
                    settings.experienceAt(depth));
            dropsFrom(boss, drops, settings, depth, true);
        }
        return new Placed(java.util.Collections.unmodifiableList(heroes), boss, List.copyOf(monsters));
    }

    /**
     * Where {@code count} heroes come in: the floor's own way in, then the nearest open cells beside it — walked
     * out from it a step at a time over floor of the same storey, so a party never stands on the far side of a
     * wall or up a ledge, and never where a monster, a boss or a prop already does.
     */
    static List<GeneratedDungeon.Placement> wayIn(GeneratedDungeon dungeon, int count) {
        var spots = new ArrayList<GeneratedDungeon.Placement>();
        var entrance = dungeon.hero();
        if (entrance == null || count <= 0) {
            return spots;
        }
        spots.add(entrance);
        var rows = dungeon.levelMap().strip().split("\n");
        var taken = new java.util.HashSet<Long>();
        taken.add(key(entrance.cellX(), entrance.cellY()));
        for (var monster : dungeon.monsters()) {
            taken.add(key(monster.at().cellX(), monster.at().cellY()));
        }
        for (var prop : dungeon.props()) {
            taken.add(key(prop.at().cellX(), prop.at().cellY()));
        }
        if (dungeon.boss() != null && dungeon.boss().at() != null) {
            taken.add(key(dungeon.boss().at().cellX(), dungeon.boss().at().cellY()));
        }
        char storey = storeyAt(rows, entrance.cellX(), entrance.cellY());
        var seen = new java.util.HashSet<Long>();
        var queue = new java.util.ArrayDeque<int[]>();
        seen.add(key(entrance.cellX(), entrance.cellY()));
        queue.add(new int[] {entrance.cellX(), entrance.cellY()});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && spots.size() < count) {
            var at = queue.poll();
            for (var step : steps) {
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

    /** The storey a cell stands on as the map writes it — a digit — or rock for anything else and off the map. */
    private static char storeyAt(String[] rows, int x, int y) {
        if (y < 0 || y >= rows.length || x < 0 || x >= rows[y].length()) {
            return '#';
        }
        char cell = rows[y].charAt(x);
        return Character.isDigit(cell) ? cell : '#';
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
     * Make one monster worth its depth.
     *
     * <p>Each multiplier is computed from the depth in one step rather than
     * compounded floor by floor, so the tenth floor is the same whether it was
     * reached by playing or asked for directly.
     */
    private static void scale(GameObject monster, float health, float damage, float experience) {
        if (monster.getBody() instanceof GrowableBody body && health > 1f) {
            body.growMaxHealth(body.getMaxHealth() * (health - 1f));
        }
        if (damage != 1f || health != 1f) {
            // The health figure rides along for whatever it calls up.
            monster.addModule(new DepthBonus(monster, damage, health));
        }
        // What killing it is worth is fixed by its template, and the template is
        // the same on every floor — so the module is swapped for one that says a
        // deeper number, in the place the old one held.
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
