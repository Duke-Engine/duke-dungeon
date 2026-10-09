package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/**
 * The owner's playtest of the talk map: "after the boss dies, the game restarts by itself". A won run used to start
 * again once its VictoryFrames were up, on every map; a map with a story is won and stays won — or its story goes on.
 */
class StoryWinTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static GameObject boss(DukeGame game, String kind) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(kind)).findFirst()
                .orElseThrow();
    }

    private static Dungeon.Session wonOn(String map, DungeonSettings settings) {
        var stage = Stages.load(map, settings);
        var session = Dungeon.newStageSession(stage, settings);
        session.game().runHeadless(1);
        boss(session.game(), stage.floor().boss().kind()).getBody().damage(10_000_000f);
        session.game().runHeadless(3);
        assertEquals(DungeonRun.State.WON, session.run().getState(), map + ": the boss down wins it");
        session.game().runHeadless(settings.run().victoryFrames() + 30);
        return session;
    }

    @Test
    void aStoryMapStaysWonAfterItsBossFalls() {
        var session = wonOn("talk", SETTINGS);
        assertEquals(DungeonRun.State.WON, session.run().getState(), "won, and still won");
        assertEquals(0, session.run().getRunCount(), "nothing started again");
    }

    @Test
    void aPlainStageIsStillWonAndPlayedAgain() {
        var session = wonOn("first", SETTINGS);
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

    @Test
    void theOpenWorldStaysWonToo() {
        var session = wonOn("world", SETTINGS);
        assertEquals(DungeonRun.State.WON, session.run().getState(), "the queen's castle taken is not a new game");
        assertEquals(0, session.run().getRunCount());
    }
}
