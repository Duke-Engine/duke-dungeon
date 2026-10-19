package uz.dukeengine.dungeon.story;

import uz.dukeengine.rpg.story.Trigger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/**
 * What a boss's fall does, map by map. The owner, 2026-10-09: "remove the 'you won' text after the boss dies". A
 * story map is not won by its boss at all — its story says what follows (a trigger on the death, its WIN, a MAP) — and
 * no boss's fall puts up a banner anywhere. A plain stage is still won by it, quietly, and played again.
 */
class StoryWinTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static GameObject boss(DukeGame game, String kind) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(kind)).findFirst()
                .orElseThrow();
    }

    /** The map's boss struck down, and a few frames for its fall to be seen. */
    private static Dungeon.Session felled(String map, DungeonSettings settings) {
        var stage = Stages.load(map, settings);
        var session = Dungeon.newStageSession(stage, settings);
        session.game().runHeadless(1);
        boss(session.game(), stage.floor().boss().kind()).getBody().damage(10_000_000f);
        session.game().runHeadless(3);
        return session;
    }

    private static void noWinOnTheBoss(String map) {
        var session = felled(map, SETTINGS);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState(), map + ": her fall is not the end of it");
        assertEquals("", session.game().getSnapshot().banner(), map + ": and no banner says won");
        session.game().runHeadless(SETTINGS.run().victoryFrames() + 30);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState());
        assertEquals(0, session.run().getRunCount(), map + ": nothing started again");
    }

    @Test
    void aStoryMapIsNotWonByItsBoss() {
        noWinOnTheBoss("talk");
    }

    @Test
    void norTheOpenWorld() {
        noWinOnTheBoss("world");
    }

    @Test
    void aPlainStageIsStillWonQuietlyAndPlayedAgain() {
        var session = felled("first", SETTINGS);
        assertEquals(DungeonRun.State.WON, session.run().getState(), "the boss down wins it");
        assertEquals("", session.game().getSnapshot().banner(), "with no banner");
        session.game().runHeadless(SETTINGS.run().victoryFrames() + 30);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState(), "learn it, win it, and again");
        assertEquals(1, session.run().getRunCount());
    }

    @Test
    void aTriggerOnTheBossesDeathDecidesWhatFollows() {
        var settings = DungeonSettings.parse(Content.data() + "\n" + """
                Trigger
                  Name = OnToTheNext
                  Map = talk
                  When = DIED
                  Target = Warden
                  Delay = 10
                  Actions = [
                    Action
                      Act = MAP
                      Target = first
                    End
                  ]
                End
                """);
        var stage = Stages.load("talk", settings);
        var session = Dungeon.newStageSession(stage, settings);
        session.game().runHeadless(1);
        boss(session.game(), "Warden").getBody().damage(10_000_000f);
        session.game().runHeadless(30);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState(), "the story sent him on");
        assertEquals("first", session.run().getStageName());
        assertEquals(0, session.run().getRunCount(), "on, not again");
    }

    /** A WIN that gives no words ends the map with no banner, and it stays ended. */
    @Test
    void aWinWithNoWordsHasNoBanner() {
        var settings = DungeonSettings.parse(Content.data() + "\n" + """
                Trigger
                  Name = Done
                  Map = talk
                  When = DIED
                  Target = Warden
                  Actions = [
                    Action
                      Act = WIN
                    End
                  ]
                End
                """);
        var stage = Stages.load("talk", settings);
        var session = Dungeon.newStageSession(stage, settings);
        session.game().runHeadless(1);
        boss(session.game(), "Warden").getBody().damage(10_000_000f);
        session.game().runHeadless(30);
        assertEquals(DungeonRun.State.WON, session.run().getState(), "the story ended it");
        assertEquals("", session.game().getSnapshot().banner());
        session.game().runHeadless(SETTINGS.run().victoryFrames() + 30);
        assertEquals(DungeonRun.State.WON, session.run().getState(), "and it stays ended");
        assertTrue(session.run().getTracker().entries().isEmpty());
    }
}
