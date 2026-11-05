package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/**
 * The open world's gates (run/Gates, data/props/world_gate.duke): one across every gatehouse's passage, shut -- the
 * path grid closed between the piers -- until a hero comes near or the story opens it, and then open for good.
 */
class GatesTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static Dungeon.Session world() {
        var session = Dungeon.newStageSession(Stages.load("world", SETTINGS), SETTINGS);
        session.game().runHeadless(2);
        return session;
    }

    private static java.util.List<GameObject> gates(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template)).toList();
    }

    private static boolean shut(DukeGame game, float x, float y) {
        var grid = game.getLogic().getPathGrid();
        var at = new Coord3D(x, y, 0f);
        return grid.isBlocked(grid.toCellX(at), grid.toCellY(at));
    }

    private static GameObject hero(Dungeon.Session session) {
        return gates(session.game(), session.run().getHeroTemplate()).getFirst();
    }

    @Test
    void everyGatehouseHasItsGateShutAcrossItsPassage() {
        var session = world();
        var game = session.game();
        var scenery = Stages.load("world", SETTINGS).paint().scenery();
        long town = scenery.stream().filter(p -> p.model().endsWith("fort_kit/town_gatehouse.glb")).count();
        long fort = scenery.stream().filter(p -> p.model().endsWith("fort_kit/fort_gatehouse.glb")).count();
        long dark = scenery.stream().filter(p -> p.model().endsWith("dark/fort_gatehouse.glb")).count();
        assertTrue(town >= 2 && fort >= 1 && dark >= 1, town + " town, " + fort + " royal, " + dark + " dark gatehouses");
        assertEquals(town, gates(game, "TownGate").size());
        // The royal gate is by the way in: the hero begins within reach of it, so it has opened already.
        assertEquals(fort, gates(game, "Portcullis").size() + gates(game, "OpenPortcullis").size());
        assertEquals(dark, gates(game, "DarkPortcullis").size());
        for (var gate : gates(game, "TownGate")) {
            // Its middle is the passage's, and the cells either side of it along the wall are closed.
            assertTrue(shut(game, gate.getPosition().x(), gate.getPosition().y()), "the gate's own cell is open");
        }
    }

    @Test
    void aHeroNearAGateOpensItAndTheStoryOpensTheRest() {
        var session = world();
        var game = session.game();
        var gate = gates(game, "TownGate").getFirst();
        var at = gate.getPosition();
        assertTrue(shut(game, at.x(), at.y()));
        // A hero walks up to it: open, its shape gone, the open gate standing in its place with the same turn.
        hero(session).setPosition(new Coord3D(at.x() + 30f, at.y() + 30f, at.z()));
        game.runHeadless(3);
        assertFalse(shut(game, at.x(), at.y()), "the gate did not open for a hero at its foot");
        assertEquals(1, gates(game, "OpenTownGate").size());
        // The others stay shut until somebody comes: the story's OPEN_GATE opens every one standing.
        assertFalse(gates(game, "TownGate").isEmpty());
        for (var other : gates(game, "TownGate")) {
            other.findModule(uz.dukeengine.rpg.mission.GateUpdate.class).open();
        }
        game.runHeadless(2);
        assertTrue(gates(game, "TownGate").isEmpty());
    }

    /** Act 1's world: the first trigger of the act opens every gate as the map begins, so its routes run through them. */
    @Test
    void actOneOpensEveryGateAsTheMapBegins() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 1, null);
        var game = session.game();
        game.runHeadless(60);
        assertTrue(gates(game, "TownGate").isEmpty() && gates(game, "Portcullis").isEmpty()
                && gates(game, "DarkPortcullis").isEmpty(), "a gate of the world is still shut");
        assertFalse(gates(game, "OpenTownGate").isEmpty(), "no gate stands open");
    }
}
