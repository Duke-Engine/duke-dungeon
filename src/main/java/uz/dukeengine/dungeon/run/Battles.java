package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.story.Battle;
import uz.dukeengine.dungeon.story.Shockwave;

/**
 * The opening battle as one thing, laid by the story's {@code BATTLE} act and by the menu's backdrop alike -- the same
 * armies on the same spot (docs/plan/2026-10-15-seamless-campaign.md) -- and the hero's landing's knock-back. In the
 * simulation: deterministic, the world's own dice untouched.
 */
public final class Battles {

    /** How high over a ritualist's feet her hands are, in units: where her beam leaves from. */
    public static final float HANDS = 9f;

    /** What a battle laid: the kingdom's soldiers, the enemy army, and the ritualists. */
    public record Laid(List<GameObject> kingdom, List<GameObject> enemy, List<GameObject> ritualists) {
    }

    private Battles() {
    }

    /** The battle laid with no bounty on the enemy: the menu's backdrop, which pays nobody. */
    public static Laid lay(GameLogic logic, Battle battle, Function<String, StaticMap.Zone> zones, int heroSide,
            int dungeonSide, DungeonSettings settings, int depth) {
        return lay(logic, battle, zones, heroSide, dungeonSide, settings, depth, null);
    }

    /**
     * The battle laid: the kingdom's kinds along its line on the heroes' side, the enemy's in its zone on the
     * dungeon's (each paying its killer, where {@code payer} is given) and called on at the kingdom's line but its
     * bosses, and the ritualists round the landing on the neutral side, facing its middle. Everyone grown to the floor's
     * depth, as the story's ALLY and SPAWN grow them.
     */
    static Laid lay(GameLogic logic, Battle battle, Function<String, StaticMap.Zone> zones, int heroSide,
            int dungeonSide, DungeonSettings settings, int depth, Bounty.Payer payer) {
        var line = zones.apply(battle.kingdomZone());
        var army = zones.apply(battle.enemyZone());
        var landing = zones.apply(battle.landing());
        var kingdom = new ArrayList<GameObject>();
        var enemy = new ArrayList<GameObject>();
        var ritualists = new ArrayList<GameObject>();
        int level = settings.beforeBossLevel(depth);
        if (line != null) {
            for (int i = 0; i < battle.kingdom().size(); i++) {
                var one = one(logic, battle.kingdom().get(i), line, i, heroSide);
                if (one != null) {
                    Spawner.scale(one, level, settings);
                    kingdom.add(one);
                }
            }
        }
        if (army != null) {
            float cell = PathGrid.DEFAULT_CELL_SIZE;
            var at = line == null ? null : new Coord3D(line.middleX(cell), line.middleY(cell), 0f);
            for (int i = 0; i < battle.enemy().size(); i++) {
                var kind = battle.enemy().get(i);
                var one = one(logic, kind, army, i, dungeonSide);
                if (one == null) {
                    continue;
                }
                Spawner.scale(one, level, settings);
                if (payer != null) {
                    one.addModule(new Bounty(one, level, payer));
                }
                var legs = one.getLocomotor();
                if (at != null && legs != null && settings.story().boss(kind) == null) {
                    legs.moveTo(at); // the army comes on at the line; its commander stays behind it
                }
                enemy.add(one);
            }
        }
        var template = logic.getThingFactory().findTemplate(battle.ritualist());
        if (landing != null && template != null) {
            var middle = middleOf(logic, landing);
            for (int i = 0; i < battle.ritualists(); i++) {
                var xy = battle.ritualistAt(i, middle.x(), middle.y());
                var flat = new Coord3D(xy[0], xy[1], 0f);
                var one = logic.spawn(template, new Coord3D(xy[0], xy[1], logic.groundHeight(flat)),
                        PlayerList.NEUTRAL_INDEX);
                one.setOrientation((float) StrictMath.atan2(middle.y() - xy[1], middle.x() - xy[0]));
                ritualists.add(one);
            }
        }
        return new Laid(List.copyOf(kingdom), List.copyOf(enemy), List.copyOf(ritualists));
    }

    /** One of {@code kind} at the {@code i}th cell of {@code zone} (round again where it has fewer), on {@code side}. */
    public static GameObject one(GameLogic logic, String kind, StaticMap.Zone zone, int i, int side) {
        var template = logic.getThingFactory().findTemplate(kind);
        if (template == null) {
            return null;
        }
        var cells = zone.cells();
        var cell = cells.get(i % cells.size());
        var at = uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement.atCell(cell[0], cell[1]);
        var flat = new Coord3D(at.x(), at.y(), 0f);
        return logic.spawn(template, new Coord3D(at.x(), at.y(), logic.groundHeight(flat)), side);
    }

    /** The middle of {@code zone}, on the ground. */
    public static Coord3D middleOf(GameLogic logic, StaticMap.Zone zone) {
        float cell = PathGrid.DEFAULT_CELL_SIZE;
        var flat = new Coord3D(zone.middleX(cell), zone.middleY(cell), 0f);
        return new Coord3D(flat.x(), flat.y(), logic.groundHeight(flat));
    }

    /** Stunned {@code frames}: the engine's {@code DISABLED} on its own timers, as the skill system's daze is. */
    public static void daze(GameObject thing, int frames) {
        var timers = frames <= 0 || thing.isEffectivelyDead() ? null
                : thing.findModule(uz.dukeengine.combat.module.StatusUpdate.class);
        if (timers != null) {
            timers.apply(ObjectStatus.DISABLED, frames);
        }
    }

    /**
     * The landing's knock-back from {@code middle}: every creature of {@code dungeonSide} alive within the wave's
     * Radius thrown Distance straight out (short of stone) along an arc Height high over Frames, stunned StunFrames and
     * wearing {@code stunLook}. Game-side until the skill system has an outward push (E82). Returns how many it threw.
     */
    static int shockwave(GameLogic logic, Shockwave wave, Coord3D middle, int dungeonSide, String stunLook) {
        int thrown = 0;
        var grid = logic.getPathGrid();
        for (var thing : List.copyOf(logic.getObjects())) {
            if (thing.getBody() == null || thing.isEffectivelyDead() || thing.getPlayerIndex() != dungeonSide) {
                continue;
            }
            var at = thing.getPosition();
            float dx = at.x() - middle.x();
            float dy = at.y() - middle.y();
            float far = (float) Math.sqrt(dx * dx + dy * dy);
            if (far > wave.radius()) {
                continue;
            }
            if (far < 0.01f) {
                dx = 1f;
                dy = 0f;
                far = 1f;
            }
            Coord3D to = at;
            for (float reach = wave.distance(); reach > 0f; reach -= PathGrid.DEFAULT_CELL_SIZE / 2) {
                float x = at.x() + dx / far * reach;
                float y = at.y() + dy / far * reach;
                int cx = (int) Math.floor(x / PathGrid.DEFAULT_CELL_SIZE);
                int cy = (int) Math.floor(y / PathGrid.DEFAULT_CELL_SIZE);
                if (grid == null || !grid.isBlocked(cx, cy)) {
                    var flat = new Coord3D(x, y, 0f);
                    to = new Coord3D(x, y, logic.groundHeight(flat));
                    break;
                }
            }
            uz.dukeengine.combat.module.Errand.giveUpAll(thing);
            var move = uz.dukeengine.core.module.ScriptedMove.along(thing, to, wave.frames(), wave.height());
            if (!wave.landed().isBlank()) {
                move.onLanded(() -> logic.effect(wave.landed(), thing));
            }
            daze(thing, wave.frames() + wave.stunFrames());
            if (stunLook != null && !stunLook.isBlank()) {
                logic.effect(stunLook, thing);
            }
            thrown++;
        }
        return thrown;
    }

    /**
     * The clip a POSE plays on {@code monster}: {@code clip} where it is drawn on its licensed model (whose clips are
     * ours to make), {@code orElse} on a stand-in that has none of that name -- or {@code clip} where none is said.
     */
    static String clipFor(DungeonSettings settings, String monster, String clip, String orElse) {
        var kind = settings.monster(monster);
        var model = kind == null || kind.look() == null ? null : kind.look().model();
        boolean licensed = model != null && model.startsWith("models/licensed/");
        return licensed || orElse == null || orElse.isBlank() ? clip : orElse;
    }
}
