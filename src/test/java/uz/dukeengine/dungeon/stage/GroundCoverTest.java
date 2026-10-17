package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.Dressed;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Piece;

/** The open world's ground grown over: thick, for the look alone, the same every time, and off roads, stone and towns. */
class GroundCoverTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Stage WORLD = Stages.load("world", SETTINGS);

    private static List<Piece> cover() {
        return GroundCover.lay(WORLD.paint(), WORLD.floor(), WORLD.story().zones(), SETTINGS.paintedGround(),
                WORLD.seed());
    }

    @Test
    void itIsThickAndStandsInNobodysWay() {
        var cover = cover();
        assertTrue(cover.size() > 100_000, "only " + cover.size() + " pieces of cover");
        assertTrue(cover.stream().allMatch(p -> p.footprint() == 0f && p.boxLength() == 0f));
        assertEquals(cover, cover(), "grown differently from the same seed");
    }

    @Test
    void nothingGrowsOnARoadInStoneOrInTown() {
        var rows = WORLD.paint().rows();
        var cells = WORLD.floor().asciiMap().strip().lines().toList();
        var city = WORLD.story().zones().stream().filter(z -> z.name().equals("Poytaxt")).findFirst().orElseThrow();
        for (var piece : cover()) {
            int x = (int) piece.x();
            int y = (int) piece.y();
            assertTrue(rows.get(y).charAt(x) != Roads.ROAD, "cover on the road at " + x + "," + y);
            assertTrue(cells.get(y).charAt(x) != '#', "cover in stone or water at " + x + "," + y);
            assertTrue(!city.holds(x + 0.5f, y + 0.5f, 1f), "cover in the city at " + x + "," + y);
        }
    }

    /** Played, the client is handed it after the map's own scenery, every piece of which keeps its number. */
    @Test
    void playedItStandsAfterTheMapsOwnScenery() {
        var game = Dungeon.newStageSession(WORLD, SETTINGS).game();
        game.runHeadless(1);
        var scenery = ((Dressed) game.getMapRecord()).scenery();
        var own = WORLD.paint().scenery();
        assertEquals(own.size() + cover().size(), scenery.size());
        assertEquals(own, scenery.subList(0, own.size()));
    }
}
