package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.run.TalkTo;
import uz.dukeengine.dungeon.run.Tracker;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.stage.StageFile;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/** The demo map, played headless end to end: walk up and talk, kill five, hand in, be paid; and the right-click. */
class TalkMapTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int CHECK = SETTINGS.story().dialogue().checkFrames();

    private static GameObject named(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template)).toList();
    }

    private static void beside(GameObject hero, GameObject person) {
        var at = person.getPosition();
        hero.setPosition(new Coord3D(at.x() + 12f, at.y(), at.z()));
    }

    private static Dungeon.Session session() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        return session;
    }

    @Test
    void theMapKeepsItsPeopleThroughTheFile() {
        var stage = Stages.load("talk", SETTINGS);
        var again = StageFile.read(StageFile.write(stage), "again");
        assertEquals(stage.story(), again.story());
        assertEquals("Commander", stage.story().npcs().getFirst().kind());
        assertEquals("Locket", stage.story().items().getFirst().kind());
    }

    @Test
    void talkKillFiveHandInAndBePaid() {
        var session = session();
        var game = session.game();
        var run = session.run();
        var commander = named(game, "Commander");
        assertNotNull(commander, "she stands on the map");
        assertEquals(PlayerList.NEUTRAL_INDEX, commander.getPlayerIndex(), "on nobody's side");
        assertTrue(run.getMarks().contains(new DungeonRun.Mark(commander.getId().value(), QuestLog.Mark.GIVE)),
                "a gold ! over her: " + run.getMarks());
        assertNotNull(named(game, "Chest"), "her locket lies in the far hall");

        var hero = named(game, SETTINGS.run().defaultHero());
        beside(hero, commander);
        game.runHeadless(CHECK + 1);
        assertEquals(List.of("CommanderGreets"), run.getQuests().said(), "walking up, she greets him");
        var titles = run.getTracker().entries().stream().map(Tracker.Entry::title).toList();
        assertEquals(List.of(SETTINGS.run().missionTitle(), "Devordagi skeletlar", "Yo'qolgan tumor"), titles,
                "the floor's own first, then her MAIN, then her SIDE");

        int before = session.progress().getExperience();
        var skeletons = all(game, "Skeleton");
        assertTrue(skeletons.size() >= 5);
        for (var skeleton : skeletons.subList(0, 5)) {
            skeleton.getBody().setHealth(0f);
        }
        game.runHeadless(2);
        assertEquals("Qo'mondonga qayting", run.getTracker().entries().get(1).steps().getFirst().text());
        assertEquals(QuestLog.Mark.HAND_IN, run.getMarks().getFirst().mark());

        hero = named(game, SETTINGS.run().defaultHero());
        beside(hero, commander);
        game.runHeadless(CHECK + 1);
        assertTrue(run.getQuests().finished().contains("WallSkeletons"), "handed in");
        assertTrue(run.getQuests().said().contains("CommanderThanks"));
        game.runHeadless(2);
        assertTrue(session.progress().getExperience() >= before + 150, "and paid");
        assertTrue(session.progress().getLoot().slots().stream()
                .anyMatch(item -> item != null && item.id().equals("Gauntlet")), "with her gauntlet");
    }

    @Test
    void aRightClickWalksHimOverAndSheGreetsAgain() {
        var session = session();
        var game = session.game();
        var commander = named(game, "Commander");
        var hero = named(game, SETTINGS.run().defaultHero());
        beside(hero, commander);
        game.runHeadless(CHECK + 1);
        assertEquals(1, session.run().getQuests().said().size());
        game.runHeadless(CHECK * 3);
        assertEquals(1, session.run().getQuests().said().size(), "standing by her, nothing new is said");

        game.postCommand(PartyOrders.of(new TalkTo(hero.getPlayerIndex(), commander.getId())));
        game.runHeadless(2);
        assertEquals(2, session.run().getQuests().said().size(), "asked, she greets him again");
    }
}
