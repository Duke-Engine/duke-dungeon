package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.run.Battles;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.dungeon.story.Battle;
import uz.dukeengine.game.DukeGame;

/**
 * The live scene behind the front menu: the campaign's opening battle on the open world, before the capital's north
 * gate (docs/plan/2026-10-15-seamless-campaign.md, section 3) -- the Battle block the story lays for the intro, so the
 * same armies stand on the same spot, the three ritualists round the landing, apart from it. A real fight of the game's
 * own creatures and brains, the fallen coming back at their line after a while, so it never ends; the camera drifting
 * slowly over it. The world's own hero is kept out of sight high over the landing, his eyes keeping the field awake.
 *
 * <p>Local only: made by the client for its front end ({@code Duke3D.backdrop}), never part of a played match, and
 * torn down when one starts (E83 asks that it be kept as the match). Its dice are wound to {@link #SEED}, so it plays
 * the same way every time.
 */
final class MenuScene {

    static final long SEED = 20261009L;
    /** How long a fallen creature lies before another of its kind comes in at its side's line: three seconds. */
    static final int RESPAWN_FRAMES = 90;
    /** How long the camera takes to drift from one end of its path to the other: twenty seconds. */
    static final int DRIFT_FRAMES = 600;
    /** How high over the landing the world's hero is kept, out of sight. */
    static final float SKY = 3000f;

    private final DukeGame game;
    private final Dungeon.Session session;
    private final DungeonSettings settings;
    private final Battle battle;
    private final List<StaticMap.Zone> zones;
    private final int shift;
    private final List<Slot> slots = new ArrayList<>();
    private int deaths;

    private MenuScene(Dungeon.Session session, DungeonSettings settings, Battle battle, List<StaticMap.Zone> zones,
            int shift) {
        this.session = session;
        this.game = session.game();
        this.settings = settings;
        this.battle = battle;
        this.zones = zones;
        this.shift = shift;
    }

    /** A fresh scene, not yet started. */
    static MenuScene of(DungeonSettings settings) {
        var menu = settings.mainMenu();
        var battle = settings.battle(menu.sceneBattle());
        if (battle == null) {
            throw new IllegalStateException("MainMenu's SceneBattle names no Battle block: " + menu.sceneBattle());
        }
        var stage = Stages.load(battle.map(), settings);
        var zones = new ArrayList<>(stage.story().zones());
        zones.addAll(settings.placesOn(battle.map()));
        var session = Dungeon.newStageSession(stage, settings);
        session.game().randomSeed(SEED);
        // The act's own hero, out of sight over the landing: when "New game" makes this the played match (E83) he is
        // the one who comes down in the meteor.
        session.run().startWith(session.game(), settings.campaign().hero());
        var scene = new MenuScene(session, settings, battle, zones, menu.sceneShift());
        var game = session.game();
        game.onStart(started -> {
            // Each kind's own (Uzbek) name over it, as a run's status line gives them -- never the template's English.
            var status = new StringBuilder();
            for (var kind : java.util.stream.Stream.concat(battle.kingdom().stream(), battle.enemy().stream())
                    .distinct().toList()) {
                var template = started.getLogic().getThingFactory().findTemplate(kind);
                if (template != null) {
                    status.append("|who=").append(kind).append(',')
                            .append(uz.dukeengine.core.thing.Titled.of(template));
                }
            }
            started.setStatus(status.toString());
            scene.lay(started);
            var camera = started.camera();
            // Higher than play's camera, turned to face north as play's is, and looking left of the fight so the
            // fight stands to the right of the menu at the left. See MainMenu's Scene words.
            if (menu.scenePitch() > 0f) {
                camera.pitch((float) Math.toRadians(menu.scenePitch()));
            }
            if (menu.sceneZoom() > 0f) {
                camera.zoom(menu.sceneZoom());
            }
            camera.angle(0f);
            camera.moveTo(scene.aim(-1), scene.fieldY(), 0);
        });
        game.onDied(died -> scene.deaths++);
        game.onTick(scene::tick);
        return scene;
    }

    DukeGame game() {
        return game;
    }

    /** How many have fallen since the scene began, on both sides. */
    int deaths() {
        return deaths;
    }

    /** How many of each side stand now. */
    int standing(boolean kingdomsSide) {
        return (int) slots.stream().filter(slot -> slot.kingdom == kingdomsSide && slot.standing()).count();
    }

    /** The ritualists standing, as laid round the landing. */
    List<GameObject> ritualists() {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(battle.ritualist()))
                .toList();
    }

    /** The zone of the scene's map called {@code name}, or null. */
    StaticMap.Zone zone(String name) {
        return zones.stream().filter(zone -> zone.name().equals(name)).findFirst().orElse(null);
    }

    private GameObject hero() {
        var id = session.run().heroId();
        return id == null ? null : game.getLogic().findObject(id);
    }

    /** The battle laid, as the story lays it; the leader at the line; the hero up out of sight over the landing. */
    private void lay(DukeGame started) {
        var logic = started.getLogic();
        var hero = hero();
        int heroSide = hero == null ? 1 : hero.getPlayerIndex();
        int dungeonSide = session.run().dungeonSide();
        var laid = session.run().layBattle(logic, battle.name());
        for (int i = 0; i < laid.kingdom().size(); i++) {
            slots.add(new Slot(true, battle.kingdom().get(i), i, heroSide, laid.kingdom().get(i)));
        }
        for (int i = 0; i < laid.enemy().size(); i++) {
            slots.add(new Slot(false, battle.enemy().get(i), i, dungeonSide, laid.enemy().get(i)));
        }
        var line = zone(battle.kingdomZone());
        var leader = battle.leader().isBlank() ? null : logic.getThingFactory().findTemplate(battle.leader());
        if (line != null && leader != null) {
            standIn = logic.spawn(leader, Battles.middleOf(logic, line), heroSide); // fighting in her line
        }
        keepHeroAway();
    }

    /** The leader at the line, a backdrop's figure only: the story places its own person once the game is played. */
    private GameObject standIn;
    /** Whether the backdrop has been made the played match (E83): the scene's own rules stop, the story's begin. */
    private volatile boolean handedOver;

    /** The session the scene runs: the match "New game" adopts (E83). */
    Dungeon.Session session() {
        return session;
    }

    /** Whether it has started: there is a world to adopt. */
    boolean running() {
        return game.getLogic() != null && !handedOver;
    }

    /**
     * Made the played match (E83), on the simulation's thread: the armies fight on as they stand and the ritualists stay
     * where they are, warded as ever (nobody's foe, no body); the fallen come back no more, the camera drifts no more,
     * the hero is the story's to hold, and the leader's stand-in goes -- the story places the commander herself.
     */
    void handOver() {
        handedOver = true;
        if (standIn != null && game.getLogic() != null) {
            game.getLogic().destroyObject(standIn);
            standIn = null;
        }
    }

    /**
     * The backdrop made {@code campaign}'s played match, on the simulation's thread (the engine's onBackdropAdopted,
     * E83): the scene handed over, the first seat taken by this machine, and the act begun on the world as it stands --
     * the same frames going on, nothing laid again.
     */
    void adoptAsMatch(DukeGame running, uz.dukeengine.dungeon.story.Campaign campaign) {
        handOver();
        running.takeSeat(session.run().heroPlayer());
        session.run().adopt(running, campaign);
    }

    private void tick(DukeGame running) {
        if (handedOver) {
            return;
        }
        keepHeroAway();
        var logic = running.getLogic();
        int frame = logic.getFrame();
        for (var slot : slots) {
            if (slot.standing()) {
                continue;
            }
            if (slot.fellAt < 0) {
                slot.fellAt = frame;
            }
            if (frame - slot.fellAt < RESPAWN_FRAMES) {
                continue;
            }
            var zone = zone(slot.kingdom ? battle.kingdomZone() : battle.enemyZone());
            var one = zone == null ? null : Battles.one(logic, slot.kind, zone, slot.place, slot.side);
            if (one == null) {
                continue;
            }
            uz.dukeengine.dungeon.run.Spawner.scale(one, settings.beforeBossLevel(session.run().getDepth()), settings);
            slot.thing = one;
            slot.fellAt = -1;
            var line = zone(battle.kingdomZone());
            var legs = one.getLocomotor();
            if (!slot.kingdom && line != null && legs != null) {
                legs.moveTo(Battles.middleOf(logic, line)); // come on at the line again
            }
        }
        if (frame % DRIFT_FRAMES == 1) {
            // Back and forth along the field, a few cells either way, never turning.
            running.camera().moveTo(aim((frame / DRIFT_FRAMES) % 2 == 0 ? 1 : -1), fieldY(), DRIFT_FRAMES);
        }
    }

    /** The world's hero, kept high over the landing and still: his eyes keep the field awake, nobody sees him. */
    private void keepHeroAway() {
        var hero = hero();
        var landing = zone(battle.landing());
        if (hero == null || landing == null) {
            return;
        }
        var foot = Battles.middleOf(game.getLogic(), landing);
        hero.setPosition(new Coord3D(foot.x(), foot.y(), foot.z() + SKY));
        Battles.daze(hero, 2);
    }

    /** The field's middle along the map's y: between the two lines. */
    float fieldY() {
        var field = zone(battle.enemyZone());
        var line = zone(battle.kingdomZone());
        return field == null || line == null ? 0f
                : (field.middleY(PathGrid.DEFAULT_CELL_SIZE) + line.middleY(PathGrid.DEFAULT_CELL_SIZE)) / 2f;
    }

    /** Where the fight is, across: between the two lines. */
    float fightX() {
        var field = zone(battle.enemyZone());
        var line = zone(battle.kingdomZone());
        return field == null || line == null ? 0f
                : (field.middleX(PathGrid.DEFAULT_CELL_SIZE) + line.middleX(PathGrid.DEFAULT_CELL_SIZE)) / 2f;
    }

    /** Where the camera looks across the field: {@link #shift} cells left of the fight, {@code side} drifts over. */
    float aim(int side) {
        return fightX() + (3 * side - shift) * PathGrid.DEFAULT_CELL_SIZE;
    }

    /** One place in the battle: its side, its kind, its cell in its zone, and who stands in it now. */
    private static final class Slot {

        final boolean kingdom;
        final String kind;
        final int place;
        final int side;
        GameObject thing;
        int fellAt = -1;

        Slot(boolean kingdom, String kind, int place, int side, GameObject thing) {
            this.kingdom = kingdom;
            this.kind = kind;
            this.place = place;
            this.side = side;
            this.thing = thing;
        }

        boolean standing() {
            return thing != null && !thing.isEffectivelyDead() && thing.getWorld() != null
                    && thing.getWorld().findObject(thing.getId()) != null;
        }
    }
}
