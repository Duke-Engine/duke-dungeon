package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** The scene behind the front menu: a real fight that never ends, the same every time. */
class MenuSceneTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void theFightGoesOnAndTheFallenComeBack() {
        var scene = MenuScene.of(SETTINGS);
        scene.game().runHeadless(30);
        long began = System.nanoTime();
        scene.game().runHeadless(1800); // a minute
        double perFrame = (System.nanoTime() - began) / 1e6 / 1800;
        System.out.printf("menu scene: %.3f ms a frame, %d fallen in a minute%n", perFrame, scene.deaths());

        assertTrue(scene.deaths() >= 4, "only " + scene.deaths() + " fell in a minute: nobody is fighting");
        scene.game().runHeadless(MenuScene.RESPAWN_FRAMES + 30);
        assertTrue(scene.standing(true) > 0 && scene.standing(false) > 0, "a side is gone and stays gone");
        assertTrue(perFrame < 4.0, perFrame + " ms a frame is too dear for a backdrop");
    }

    @Test
    void itPlaysTheSameWayEveryTime() {
        var one = MenuScene.of(SETTINGS);
        var two = MenuScene.of(SETTINGS);
        one.game().runHeadless(900);
        two.game().runHeadless(900);
        assertEquals(one.deaths(), two.deaths());
        assertEquals(one.game().getLogic().getObjects().size(), two.game().getLogic().getObjects().size());
    }

    /**
     * The seamless campaign (docs/plan/2026-10-15-seamless-campaign.md, section 3): the backdrop is the opening battle
     * on the open world at its field before the gate -- the Battle block the story lays, the same armies, the same spot.
     */
    @Test
    void theBackdropIsTheOpeningBattleOnTheWorld() {
        var scene = MenuScene.of(SETTINGS);
        scene.game().runHeadless(2);
        var battle = SETTINGS.battle(SETTINGS.mainMenu().sceneBattle());
        assertEquals("world", battle.map());
        assertEquals(battle.kingdom().size(), scene.standing(true), "the kingdom's line, every one of it");
        assertEquals(battle.enemy().size(), scene.standing(false), "the enemy army, every one of it");
        var field = scene.zone("Darvoza_oldi");
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        long onTheField = scene.game().getLogic().getObjects().stream()
                .filter(thing -> battle.kingdom().contains(thing.getTemplate().name())
                        || battle.enemy().contains(thing.getTemplate().name()))
                .filter(thing -> field.holds(thing.getPosition().x(), thing.getPosition().y(), cell)).count();
        assertEquals(battle.kingdom().size() + battle.enemy().size(), onTheField,
                "both armies stand on the field before the gate");
        var seen = scene.game().camera().current();
        assertTrue(field.holds(seen.x() + SETTINGS.mainMenu().sceneShift() * cell, seen.y(), cell),
                "the camera looks at the field");
    }

    /**
     * The owner's rules for the three ritualists: round the landing 120 degrees apart, 25 to 35 out, facing it, and
     * nobody hits them -- through a minute of the backdrop's fight none of them is hurt or gone.
     */
    @Test
    void theRitualistsStandRoundTheLandingAndNobodyHurtsThem() {
        var scene = MenuScene.of(SETTINGS);
        scene.game().runHeadless(2);
        var three = scene.ritualists();
        assertEquals(3, three.size());
        var landing = scene.zone("Qonish");
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        var angles = new java.util.ArrayList<Double>();
        for (var one : three) {
            double dx = one.getPosition().x() - landing.middleX(cell);
            double dy = one.getPosition().y() - landing.middleY(cell);
            double far = Math.hypot(dx, dy);
            assertTrue(far >= 25 && far <= 35, "out " + far);
            angles.add(Math.toDegrees(Math.atan2(dy, dx)));
        }
        angles.sort(Double::compare);
        for (int i = 0; i < 3; i++) {
            double gap = (angles.get((i + 1) % 3) - angles.get(i) + 360) % 360;
            assertEquals(120, gap, 3, "120 degrees apart: " + angles);
        }
        var where = three.stream().map(one -> one.getPosition()).toList();
        scene.game().runHeadless(1800);
        var after = scene.ritualists();
        assertEquals(3, after.size(), "all three still stand");
        for (int i = 0; i < 3; i++) {
            var one = after.get(i);
            assertTrue(one.getBody() == null || one.getBody().getHealth() >= one.getBody().getMaxHealth(),
                    "nobody hurt a ritualist");
            assertEquals(where.get(i).x(), one.getPosition().x(), 0.01f, "nobody moved one");
        }
    }

    /** The playtest of 1.0.41: "Knight", "Skeleton" over the backdrop's fighters. Each kind is named in Uzbek. */
    @Test
    void theFightersAreNamedInTheGamesOwnWords() {
        var scene = MenuScene.of(SETTINGS);
        scene.game().runHeadless(2);
        var status = scene.game().getSnapshot().status();
        var reading = uz.dukeengine.client3d.UnitBarReading.read(status);
        assertEquals("Skelet", reading.names().get("Skeleton"), status);
        for (var kind : SETTINGS.battle(SETTINGS.mainMenu().sceneBattle()).enemy()) {
            assertTrue(reading.names().containsKey(kind) && !reading.names().get(kind).equals(kind),
                    kind + " would be drawn by its template name: " + status);
        }
    }

    /** The owner, 2026-10-10: the camera somewhat higher, and the fight to the right of the menu at the left. */
    @Test
    void theCameraLooksFromHigherThanPlayAndLeftOfTheFight() {
        var scene = MenuScene.of(SETTINGS);
        scene.game().runHeadless(2);
        var seen = scene.game().camera().current();
        var menu = SETTINGS.mainMenu();
        assertEquals(Math.toRadians(menu.scenePitch()), seen.pitch(), 1e-4);
        assertTrue(seen.pitch() > Math.atan2(0.82, 0.57), "no higher than the camera a game is played with");
        assertEquals(menu.sceneZoom(), seen.zoom(), 1e-3);
        assertTrue(menu.sceneZoom() > 140f, "no further back than play's");
        float fight = scene.fightX();
        assertTrue(seen.x() < fight, "it looks at " + seen.x() + ", not left of the fight at " + fight);
        assertEquals(0f, seen.angle(), 1e-6f);
    }

    /**
     * "New game" of the first act makes the backdrop the played match (E83): the same world, its frames going on, the
     * seat taken by this machine, the armies as they stand (not laid again), and the act's intro played on it to the
     * commander's lead -- nothing loaded.
     */
    @Test
    void aNewGameAdoptsTheBackdropAndLoadsNothing() {
        var scene = MenuScene.of(SETTINGS);
        var game = scene.game();
        game.runHeadless(300);
        var logic = game.getLogic();
        int frame = logic.getFrame();
        var battle = SETTINGS.battle(SETTINGS.mainMenu().sceneBattle());
        var soldiers = logic.getObjects().stream().filter(o -> o.getTemplate().name().equals("Soldier")).toList();
        scene.adoptAsMatch(game, SETTINGS.campaign());
        var run = scene.session().run();
        assertEquals(run.heroPlayer().getIndex(), game.getLocalPlayerIndex(), "the seat taken");
        assertTrue(run.getScript().flagged("Act1"), "the act begun");
        for (int i = 0; i < 2400 && !run.getScript().flagged("BattleWon"); i++) {
            run.read(game.getLocalPlayerIndex(), 10_000);
            if (run.inCutscene()) {
                run.skipCutscene(game);
            }
            game.runHeadless(1);
            long laid = logic.getObjects().stream().filter(o -> o.getTemplate().name().equals("Soldier")).count();
            assertTrue(laid <= battle.kingdom().stream().filter("Soldier"::equals).count(), "the battle laid once");
            if (i == 5) {
                assertEquals(3, scene.ritualists().size(), "the backdrop's ritualists are the intro's");
            }
        }
        assertTrue(run.getScript().flagged("HeroLanded") && run.getScript().flagged("BattleWon"),
                "the intro played on the adopted world");
        assertTrue(game.getLogic() == logic, "the same world: nothing laid again, nothing loaded");
        assertTrue(logic.getFrame() > frame + 200, "its frames went on from " + frame + ": " + logic.getFrame());
        assertTrue(soldiers.stream().anyMatch(o -> logic.findObject(o.getId()) != null),
                "the backdrop's own soldiers fight on in it");
        assertEquals(SETTINGS.campaign().hero(), run.getHeroTemplate(), "the act's hero came down");
    }
}
