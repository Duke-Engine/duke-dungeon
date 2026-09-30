package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.game.DukeGame;

/**
 * The keep's gate: across its doorway nothing passes, a hero walking up to it leaves it shut, only its open() opens it,
 * and the floor turns it true.
 */
class GateTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Where the gate stands: the middle of a doorway three cells wide, in a wall down the middle of a hall. */
    private static final Coord3D DOORWAY = new Coord3D(205f, 155f, 0f);

    /** A hall forty by thirty with a wall down x = 20, open at y 14 to 16. */
    private static String hall() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean border = x == 0 || y == 0 || x == 39 || y == 29;
                boolean wall = x == 20 && (y < 14 || y > 16);
                text.append(border || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /**
     * The hall with the gate in its doorway, turned along the wall — which runs down the map's y. A frame first:
     * before the world starts, a spawn is only booked for its first frame.
     */
    private static Dungeon.Arena withAGate() {
        var arena = Dungeon.world(hall(), SETTINGS);
        arena.game().spawn("Gate", arena.dungeon(), DOORWAY.x(), DOORWAY.y());
        arena.game().runHeadless(1);
        find(arena.game(), "Gate").setOrientation((float) (StrictMath.PI / 2));
        return arena;
    }

    private static boolean shut(DukeGame game, float x, float y) {
        var grid = game.getLogic().getPathGrid();
        var at = new Coord3D(x, y, 0f);
        return grid.isBlocked(grid.toCellX(at), grid.toCellY(at));
    }

    @Test
    void itShutsTheWholeDoorway() {
        var arena = withAGate();
        arena.game().runHeadless(2);

        for (float y : new float[] {145f, 155f, 165f}) {
            assertTrue(shut(arena.game(), DOORWAY.x(), y), "the doorway is open at y " + y);
        }
    }

    @Test
    void theDungeonsOwnDoNotOpenIt() {
        var arena = withAGate();
        arena.game().spawn("Skeleton", arena.dungeon(), 185f, 155f);
        arena.game().runHeadless(60);

        assertNotNull(find(arena.game(), "Gate"), "it opened for a skeleton");
    }

    /** A hero walking up to it, and standing at it, leaves it shut: only the key opens it now. */
    @Test
    void aHeroWalkingUpToItLeavesItShut() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        hero.getLocomotor().moveTo(new Coord3D(300f, 155f, 0f));
        game.runHeadless(150);

        assertNotNull(find(game, "Gate"), "it opened for him");
        assertNull(find(game, "OpenGate"), "and something stands where it stood");
        assertTrue(shut(game, DOORWAY.x(), DOORWAY.y()), "the doorway is open");
        assertTrue(hero.getPosition().x() > DOORWAY.x() - 25f,
                "he came up to it, within the reach it used to open at: " + hero.getPosition());
        assertTrue(hero.getPosition().x() < DOORWAY.x(), "and he is still on his side of it: " + hero.getPosition());
    }

    /** Opened, the same gate stands open where it stood, turned as it was, and he walks on through. */
    @Test
    void openedItStandsOpenWhereItStoodAndHeWalksOnThrough() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");

        find(game, "Gate").findModule(GateUpdate.class).open();
        game.runHeadless(2);

        assertNull(find(game, "Gate"), "it stayed shut");
        var open = find(game, "OpenGate");
        assertNotNull(open, "nothing stands where it stood");
        assertEquals(DOORWAY.x(), open.getPosition().x(), 0.01f);
        assertEquals(DOORWAY.y(), open.getPosition().y(), 0.01f);
        assertEquals((float) (StrictMath.PI / 2), open.getOrientation(), 1e-6f, "turned as the gate was");
        assertFalse(shut(game, DOORWAY.x(), DOORWAY.y()), "and the doorway open, the open gate in nobody's way");
        hero.getLocomotor().moveTo(new Coord3D(300f, 155f, 0f));
        game.runHeadless(250);
        assertTrue(hero.getPosition().x() > 260f, "and he walked on through: " + hero.getPosition());
    }

    /**
     * Opened twice in a frame, it opens once: marked destroyed, it stays in the world until the frame's reap, so two
     * hands can reach it, and only one open gate may stand in its place.
     */
    @Test
    void openedTwiceItStandsOpenOnce() {
        var arena = withAGate();
        var game = arena.game();
        var gate = find(game, "Gate").findModule(GateUpdate.class);

        gate.open();
        gate.open();
        game.runHeadless(2);

        var open = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("OpenGate")).count();
        assertEquals(1, open, "a second opening stood a second open gate in the first");
    }

    /** On a floor of the descent, it stands across its keep's doorway, every cell of it shut. */
    @Test
    void theFloorTurnsItAcrossItsDoorway() {
        for (long seed : new long[] {3L, 11L, 42L}) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 1);
            var game = Dungeon.newSession(seed, SETTINGS).game();
            game.runHeadless(2);

            var gate = find(game, "Gate");
            assertNotNull(gate, "seed " + seed + ": the keep has no gate");
            var keep = floor.keep();
            assertEquals(keep.facing(), gate.getOrientation(), 1e-6f, "seed " + seed);
            var walls = keep.walls();
            for (int y = walls.y(); y < walls.y() + walls.h(); y++) {
                for (int x = walls.x(); x < walls.x() + walls.w(); x++) {
                    if (keep.isDoorway(x, y)) {
                        assertTrue(shut(game, x * 10f + 5f, y * 10f + 5f), "seed " + seed + ": open at " + x + "," + y);
                    }
                }
            }
        }
    }
}
