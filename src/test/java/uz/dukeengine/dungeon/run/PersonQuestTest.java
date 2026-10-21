package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.story.QuestLog;

/**
 * A Person's quest, end to end (docs/plan/2026-10-21-npc-brains-and-quests.md): the smith of maps/people offers it,
 * it is done -- to the yard, two skeletons, back to him -- and paid, experience and a thing by its Done and gold by a
 * trigger; and a save taken with it going loads with it going, and the patroller walking on. In the run's package for
 * its Floors, as the save is loaded.
 */
class PersonQuestTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
    private static final int CHECK = SETTINGS.story().dialogue().checkFrames();

    private static Dungeon.Session session() {
        var session = Dungeon.newStageSession(Stages.load("people", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        return session;
    }

    private static GameObject named(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    private static GameObject hero(DukeGame game) {
        return named(game, SETTINGS.run().defaultHero());
    }

    private static void standAt(DukeGame game, float x, float y) {
        var hero = hero(game);
        hero.setPosition(new Coord3D(x, y, hero.getPosition().z()));
    }

    private static boolean going(Dungeon.Session session) {
        return session.run().getQuests().active().stream().anyMatch(q -> q.quest().equals("SmithsOre"));
    }

    /** Asked and accepted: the hero beside the smith, talking, saying yes. */
    private static GameObject accepted(Dungeon.Session session) {
        var game = session.game();
        var smith = named(game, "Blacksmith");
        standAt(game, smith.getPosition().x() + 12f, smith.getPosition().y());
        int player = hero(game).getPlayerIndex();
        session.run().talkTo(game, player, smith.getId());
        session.run().answer(player, session.run().getQuests().said().size(), true);
        return smith;
    }

    @Test
    void aPersonGivesItsQuestAndShowsTheMark() {
        var session = session();
        assertEquals(QuestLog.Mark.GIVE, session.run().getQuests().markOf("Smith"), "a gold ! over the smith");
        accepted(session);
        assertTrue(going(session), "accepted: " + session.run().getQuests().active());
    }

    @Test
    void theQuestIsDoneAndPays() {
        var session = session();
        var game = session.game();
        var run = session.run();
        var smith = accepted(session);
        int player = hero(game).getPlayerIndex();

        standAt(game, 12.5f * CELL, 20.5f * CELL); // the yard
        game.runHeadless(CHECK + 1);
        var logic = game.getLogic();
        for (int i = 0; i < 2; i++) {
            var skeleton = logic.spawn(logic.getThingFactory().findTemplate("Skeleton"),
                    new Coord3D((24.5f + i) * CELL, 25.5f * CELL, 0f), run.dungeonPlayer().getIndex());
            skeleton.getBody().setHealth(0f);
        }
        game.runHeadless(2);

        int experience = session.progress().getExperience();
        int gold = run.goldOf(player);
        standAt(game, smith.getPosition().x() + 12f, smith.getPosition().y());
        game.runHeadless(CHECK + 1);
        run.talkTo(game, player, smith.getId());
        game.runHeadless(CHECK + 2);
        assertTrue(run.getQuests().finished().contains("SmithsOre"), "handed in: " + run.getQuests().active());
        assertEquals(experience + 50, session.progress().getExperience(), "its Done's experience");
        assertEquals(gold + 30, run.goldOf(player), "and the trigger's gold");
        assertTrue(session.progress().getLoot().slots().stream()
                .anyMatch(item -> item != null && item.id().equals("Gauntlet")), "and its gauntlet");
    }

    @Test
    void aSaveMidQuestLoadsWithTheQuestTakenAndThePatrolGoingOn() {
        var session = session();
        accepted(session);
        standAt(session.game(), 26.5f * CELL, 3.5f * CELL); // back where he came in
        session.game().runHeadless(CHECK + 1);
        var save = session.run().saveNow();
        assertNotNull(save, "nothing is fighting, so it saves");

        var loaded = Dungeon.newStageSession(Stages.load("people", SETTINGS), SETTINGS);
        loaded.run().resume(SaveGame.read(save.text(), "test"), Floors.resumed(save, SETTINGS));
        loaded.game().runHeadless(2);
        assertTrue(going(loaded), "still taken: " + loaded.run().getQuests().active());
        var walker = named(loaded.game(), "Villager");
        var from = walker.getPosition();
        loaded.game().runHeadless(300);
        assertTrue(walker.getPosition().distance(from) > CELL, "the patroller walks on: " + walker.getPosition());
    }
}
