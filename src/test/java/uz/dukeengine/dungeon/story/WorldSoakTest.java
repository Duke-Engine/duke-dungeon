package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;

/**
 * Both acts' open world left to run for a long while -- most of a day and its weather, travellers on their rounds,
 * camps and night creatures stirring, the hero stood in the capital: nothing in the simulation may throw, and the run
 * is still running at the end.
 */
class WorldSoakTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static void soak(String act, int joint, int frames) {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(act), joint, null);
        session.game().runHeadless(2);
        var game = session.game();
        for (int i = 0; i < frames; i++) {
            session.run().read(game.getLocalPlayerIndex(), 100_000);
            if (session.run().inCutscene()) {
                session.run().skipCutscene(game);
            }
            game.runHeadless(1);
        }
        assertEquals(DungeonRun.State.RUNNING, session.run().getState(), act + " joint " + joint + " still runs");
        assertTrue(game.getLogic().getFrame() >= frames, "it played them all");
    }

    @Test
    void actOnesWorldRunsALongWhile() {
        soak("act1", 3, 20_000);
    }

    @Test
    void actTwosWorldRunsALongWhile() {
        soak("act2", 1, 20_000);
    }
}
