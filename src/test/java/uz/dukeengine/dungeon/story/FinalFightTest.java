package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.run.Fighting;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/** The end: a boss beaten and not killed, a boss's phases, the call, the queen's death and the hall played through. */
class FinalFightTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template)).toList();
    }

    private static GameObject one(DukeGame game, String template) {
        return all(game, template).getFirst();
    }

    private static Dungeon.Session session(String map, DungeonSettings settings) {
        var session = Dungeon.newStageSession(Stages.load(map, settings), settings);
        session.game().runHeadless(2);
        return session;
    }

    private static float gap(GameObject a, GameObject b) {
        float dx = a.getPosition().x() - b.getPosition().x();
        float dy = a.getPosition().y() - b.getPosition().y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Test
    void theUnkillableStopsAtHisShareAndIsBeaten() {
        var session = session("hall", SETTINGS);
        var game = session.game();
        var him = one(game, "CursedHero");
        float most = him.getBody().getMaxHealth();

        him.getBody().damage(10_000_000f);
        game.runHeadless(2);
        assertTrue(all(game, "CursedHero").contains(him), "a killing blow does not kill him");
        assertEquals(most * 0.25f, him.getBody().getHealth(), 0.5f, "he stops at a quarter");
        assertEquals(PlayerList.NEUTRAL_INDEX, him.getPlayerIndex(), "beaten: nobody's enemy");
        assertTrue(session.run().getScript().state().fired().contains("CursedBeaten"), "BEATEN heard");
        assertTrue(session.run().getScript().flagged("CursedBeaten"));

        him.getBody().damage(10_000_000f);
        game.runHeadless(300);
        assertTrue(all(game, "CursedHero").contains(him), "and nothing kills him after");
        assertEquals(most * 0.25f, him.getBody().getHealth(), 0.5f, "nor takes anything from him");
    }

    /** The talk map's Warden, given phases: each begins at its share, in turn, once. */
    @Test
    void phasesBeginAtTheirThresholdsInTurnAndOnce() {
        var settings = DungeonSettings.parse(Content.data() + "\n" + """
                Boss
                  Name = Warden
                  Phases = [
                    Phase
                      DamageTaken = 50
                      Skills = [q]
                    End,
                    Phase
                      Below = 50
                      Actions = [
                        Action
                          Act = FLAG
                          Target = Half
                        End,
                        Action
                          Act = SPAWN
                          Target = Skeleton
                          Zone = Ambush
                        End
                      ]
                    End,
                    Phase
                      Below = 20
                      Actions = [
                        Action
                          Act = FLAG
                          Target = Low
                        End
                      ]
                    End
                  ]
                End
                """);
        var session = session("talk", settings);
        var game = session.game();
        var warden = one(game, "Warden");
        var body = warden.getBody();
        assertEquals(50f, body.estimateDamage(100f, DamageType.NORMAL), "the first phase, begun at the start: half of every blow");
        assertEquals(List.of("Q"), warden.findModule(Fighting.class).castOnly(), "and only its Q");
        int skeletons = all(game, "Skeleton").size();

        body.setHealth(body.getMaxHealth() * 0.6f);
        game.runHeadless(3);
        assertFalse(session.run().getScript().flagged("Half"), "not below its share yet");

        body.setHealth(body.getMaxHealth() * 0.1f);
        game.runHeadless(1);
        assertTrue(session.run().getScript().flagged("Half"), "the second, at half");
        assertFalse(session.run().getScript().flagged("Low"), "one phase a frame: the third waits for it");
        game.runHeadless(2);
        assertTrue(session.run().getScript().flagged("Low"), "then the third");
        assertEquals(100f, body.estimateDamage(100f, DamageType.NORMAL), "a phase that says nothing takes every blow whole");
        assertEquals(List.of(), warden.findModule(Fighting.class).castOnly(), "and casts all");

        body.setHealth(body.getMaxHealth());
        game.runHeadless(3);
        body.setHealth(body.getMaxHealth() * 0.1f);
        game.runHeadless(3);
        assertEquals(skeletons + 1, all(game, "Skeleton").size(), "each phase's actions once");
    }

    @Test
    void theQueenCallsHimToHerSideAndTakesNothingWhileHeStands() {
        var session = session("hall", SETTINGS);
        var game = session.game();
        var queen = one(game, "QueenBoss");
        var him = one(game, "CursedHero");
        float far = gap(queen, him);

        queen.getBody().damage(10_000_000f);
        game.runHeadless(1);
        assertEquals(queen.getBody().getMaxHealth(), queen.getBody().getHealth(), "no blow takes from her yet");

        session.run().getScript().flag("FightBegins");
        game.runHeadless(240);
        assertTrue(session.run().getQuests().said().contains("QueenCalls"), "she calls him");
        assertTrue(gap(queen, him) < far / 2f, "and he comes to her side: " + far + " -> " + gap(queen, him));
    }

    @Test
    void noSaveIsWrittenMidFight() {
        assertNull(session("hall", SETTINGS).run().saveNow(), "the queen stands: the hall's entry save stands");
        assertNotNull(session("talk", SETTINGS).run().saveNow(), "a floor with no boss of the story's saves");
    }

    @Test
    void aBossBlockNamingNoMonsterIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(Content.data() + "\n" + """
                Boss
                  Name = Nobody
                  Beaten = 25
                End
                """));
    }

    /** The demo played through headless: the door, the intro, the call, him beaten, her phase, her death, the end. */
    @Test
    void theHallEndToEnd() {
        var session = session("hall", SETTINGS);
        var game = session.game();
        var run = session.run();
        var hero = one(game, SETTINGS.run().defaultHero());
        var door = Stages.load("hall", SETTINGS).story().zones().stream().filter(z -> z.name().equals("HallDoor"))
                .findFirst().orElseThrow();
        hero.setPosition(new Coord3D(door.middleX(CELL), door.middleY(CELL), hero.getPosition().z()));
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() + 1);
        assertTrue(run.inCutscene(), "the queen shown on her throne");
        game.runHeadless(200);
        assertFalse(run.inCutscene());
        assertTrue(run.getQuests().said().contains("QueenGreets"));
        game.runHeadless(3);
        assertTrue(run.getScript().flagged("FightBegins"), "seen, the fight begins");
        assertTrue(run.getQuests().said().contains("QueenCalls"));

        var him = one(game, "CursedHero");
        him.getBody().damage(10_000_000f);
        game.runHeadless(3);
        assertTrue(run.getQuests().said().contains("HeroSpares"), "spared");
        assertTrue(run.getQuests().said().contains("QueenAlone"), "and she fights herself");

        var queen = one(game, "QueenBoss");
        queen.getBody().damage(10_000_000f);
        game.runHeadless(3);
        assertTrue(all(game, "QueenBoss").isEmpty(), "her blows are hers to take now: she falls");
        assertTrue(run.getScript().flagged("MedallionBroken"), "the medallion breaks");
        assertEquals(DungeonRun.State.RUNNING, run.getState(), "the ending first");
        game.runHeadless(SETTINGS.story().dialogue().readWaitFrames() + 200);
        assertTrue(run.getQuests().said().contains("QueenFalls"), "the ending's lines");
        game.runHeadless(100);
        assertEquals(DungeonRun.State.WON, run.getState(), "then won");
        assertTrue(game.getSnapshot().banner().contains("La'nat ko'tarildi"), game.getSnapshot().banner());
        assertTrue(all(game, "CursedHero").contains(him), "and he is still there, himself again");
        game.runHeadless(SETTINGS.run().victoryFrames() + 30);
        assertEquals(DungeonRun.State.WON, run.getState(), "and stays won");
        assertEquals(0, run.getRunCount());
    }
}
