package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.StageCheck;
import uz.dukeengine.dungeon.stage.StageFile;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/**
 * One person placed on a map by a name of their own (docs/plan/2026-10-21-npc-brains-and-quests.md): maps/people
 * names Walker, Sentry and Smith in its Npcs, and its people.duke says what each of them is.
 */
class PersonTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;

    static Dungeon.Session session() {
        var session = Dungeon.newStageSession(Stages.load("people", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        session.game().getLogic().setSleep(null);
        return session;
    }

    /** The thing standing nearest the middle of cell {@code (x, y)}. */
    static GameObject at(DukeGame game, int x, int y) {
        GameObject nearest = null;
        double best = Double.MAX_VALUE;
        for (var thing : game.getLogic().getObjects()) {
            double far = Math.hypot(thing.getPosition().x() - (x + 0.5f) * CELL,
                    thing.getPosition().y() - (y + 0.5f) * CELL);
            if (far < best) {
                best = far;
                nearest = thing;
            }
        }
        return nearest;
    }

    @Test
    void aPersonIsDrawnAsItsKind() {
        var game = session().game();
        var walker = at(game, 5, 5);
        assertEquals("Villager", walker.getTemplate().name(), "Walker is drawn as the Kind his block names");
        assertEquals(PlayerList.NEUTRAL_INDEX, walker.getPlayerIndex(), "on nobody's side");
        assertEquals("Blacksmith", at(game, 8, 25).getTemplate().name());
    }

    @Test
    void aPersonIsAnNpcOfItsOwnName() {
        var smith = SETTINGS.story().npc("Smith");
        assertNotNull(smith, "the story knows him by his own name");
        assertEquals("SmithHello", smith.talk());
        assertEquals("Temirchi", smith.role());
        assertEquals(Person.Brain.PATROL, SETTINGS.person("Walker").brain());
        assertEquals(java.util.List.of("PostA", "PostB"), SETTINGS.person("Walker").points());
    }

    @Test
    void aPersonWhoseKindIsNoCreatureIsRefused() {
        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(Content.data()
                + "\nPerson\n  Name = Ghost\n  Kind = Nobody\nEnd\n"));
        assertTrue(refused.getMessage().contains("Nobody"), refused.getMessage());
    }

    @Test
    void aPatrolPointThatIsNoZoneIsRefused() {
        var settings = DungeonSettings.parse(Content.data()
                + "\nPerson\n  Name = Walker\n  Kind = Villager\n  Brain = PATROL\n  Points = [PostA, Nowhere]\nEnd\n");
        var stage = StageFile.read(Stages.found("people").text(), "people");
        var problems = StageCheck.problems(stage, settings);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("Walker") && problem.contains("Nowhere")),
                problems.toString());
        assertTrue(problems.stream().noneMatch(problem -> problem.contains("PostA")), problems.toString());
    }

    @Test
    void ordinaryNpcsAreUnchanged() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        var commander = session.game().getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Commander")).findFirst().orElseThrow();
        assertEquals(PlayerList.NEUTRAL_INDEX, commander.getPlayerIndex());
        assertEquals(8.5f * CELL, commander.getPosition().x(), 0.5f);
        assertEquals(10.5f * CELL, commander.getPosition().y(), 0.5f);
    }
}
