package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Piece;
import uz.dukeengine.game.DukeGame;

/**
 * A painted map's trees stand in the way as far as their trunks and no further. A fence of trunks too close to pass
 * runs across a field with one gap in it: a hero ordered across goes through the gap where it is half again his width,
 * and is stopped where it is narrower than he is. Set along by shares of a cell, so neither the fence nor its gap
 * lines up with any grid's cells. Walked a cell to a cell, as the world was, a route found a gap of 15 six times in ten and
 * wanted 20 -- twice his width -- to be sure of it: a trunk closed the whole ten-unit cell round it.
 */
class TrunkGapTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int WIDE = 40;
    private static final int TALL = 24;
    /** The fence stands down x = 20 cells; he starts west of it and is sent east. */
    private static final float FENCE = 20f;
    private static final String HERO = "Knight";

    @Test
    void aGapHalfAgainHisWidthLetsHimThrough() {
        float width = 2 * radius();
        for (int at = 0; at < 10; at++) {
            assertTrue(crossed(1.5f * width, at / 10f), "he could not go through a gap of " + 1.5f * width
                    + ", set " + at + " tenths along");
        }
    }

    @Test
    void aGapNarrowerThanHimStopsHim() {
        float width = 2 * radius();
        for (int at = 0; at < 10; at += 3) {
            assertFalse(crossed(0.8f * width, at / 10f), "he squeezed through a gap of " + 0.8f * width + ", set "
                    + at + " tenths along");
        }
    }

    /** The world is walked at the world's own NavigationCellsPerCell, and a stage's paint says so. */
    @Test
    void aPaintedStageIsWalkedFinerThanItIsDrawn() {
        int k = SETTINGS.world().navigationCellsPerCell();
        assertTrue(k >= 2, "a painted world walked a cell to a cell closes the whole of a cell round each trunk");
        var arena = arena(List.of());
        arena.game().runHeadless(1);
        assertEquals(k, arena.game().getLogic().getPathGrid().cellsPerMapCell());
    }

    private static float radius() {
        var arena = Dungeon.world(field(), SETTINGS);
        arena.game().runHeadless(1);
        return Solid.of(arena.game().getLogic().getThingFactory().findTemplate(HERO)).footprintRadius();
    }

    /** Whether he crossed a fence of trunks whose one gap is {@code gap} units wide, edge to edge. */
    private static boolean crossed(float gap, float along) {
        float trunk = WorldMapWriter.TRUNK;
        float tight = 2f; // units between two trunks of the fence: nobody's way through
        var pieces = new ArrayList<Piece>();
        float y = along;
        float middle = TALL / 2f + along;
        while (y < TALL) {
            pieces.add(new Piece("models/terrain/forest/tree.gltf", FENCE + along, y, 0f, 1f, 0xFFFFFF, trunk));
            boolean theGap = y < middle && y + (tight + 2 * trunk * 10) / 10f >= middle;
            y += ((theGap ? gap : tight) + 2 * trunk * 10f) / 10f;
        }
        var arena = arena(pieces);
        var game = arena.game();
        // Six cells off the gap, so it is the route that finds it and not a walk straight at it that slides through.
        game.spawn(HERO, arena.hero(), 80f, middle * 10f + 60f);
        game.runHeadless(2);
        var hero = find(game, HERO);
        hero.getLocomotor().moveTo(new Coord3D(320f, middle * 10f + 60f, 0f));
        game.runHeadless(600);
        return hero.getPosition().x() > (FENCE + 2) * 10f;
    }

    private static Dungeon.Arena arena(List<Piece> pieces) {
        var arena = Dungeon.world(field(), SETTINGS);
        var rows = new ArrayList<String>();
        for (int y = 0; y < TALL; y++) {
            rows.add("g".repeat(WIDE));
        }
        DukeGame game = arena.game();
        game.applyMapTerrain(game.getTerrain(), new StagePaint("fence", rows, Map.of("g", "ground/grass.png"),
                Map.of(), pieces, List.of()).walkedAt(SETTINGS.world().navigationCellsPerCell()));
        return arena;
    }

    /** Open ground walled round. */
    private static String field() {
        var text = new StringBuilder();
        for (int y = 0; y < TALL; y++) {
            for (int x = 0; x < WIDE; x++) {
                text.append(x == 0 || y == 0 || x == WIDE - 1 || y == TALL - 1 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }
}
