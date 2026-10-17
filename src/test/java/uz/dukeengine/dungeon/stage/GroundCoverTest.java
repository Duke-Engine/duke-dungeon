package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.Dressed;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** The open world's ground cover: the client's to grow (E98), off roads, stone and towns, and never scenery. */
class GroundCoverTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Stage WORLD = Stages.load("world", SETTINGS);

    private static uz.dukeengine.client3d.GroundCover cover() {
        return GroundCover.forClient(WORLD.paint(), WORLD.floor(), WORLD.story().zones(), SETTINGS.paintedGround(),
                WORLD.seed());
    }

    @Test
    void everyCoveredSurfaceHandsTheClientItsKinds() {
        var cover = cover();
        assertNotNull(cover);
        assertFalse(cover.kinds().isEmpty());
        for (var picture : cover.kinds().keySet()) {
            assertTrue(WORLD.paint().palette().containsValue(picture), "a surface the map doesn't paint: " + picture);
            assertTrue(cover.perCell().getOrDefault(picture, 0f) > 0f, "nothing a cell on " + picture);
        }
        assertEquals(WORLD.seed(), cover.seed());
    }

    @Test
    void nothingGrowsOnARoadInStoneOrInTown() {
        var cover = cover();
        var road = WORLD.paint().palette().get(String.valueOf(Roads.ROAD));
        assertFalse(cover.kinds().containsKey(road), "the road grows nothing");
        var bare = cover.bare();
        var rows = WORLD.paint().rows();
        var cells = WORLD.floor().asciiMap().strip().lines().toList();
        var city = WORLD.story().zones().stream().filter(z -> z.name().equals("Poytaxt")).findFirst().orElseThrow();
        for (int y = 0; y < rows.size(); y += 3) {
            for (int x = 0; x < rows.get(y).length(); x += 3) {
                boolean shut = cells.get(y).charAt(x) == '#' || city.holds(x + 0.5f, y + 0.5f, 1f);
                if (shut) {
                    assertTrue(bare.at(x, y), "cover could grow at " + x + "," + y);
                }
            }
        }
        assertTrue(bare.at(-1, 0) && bare.at(0, rows.size()), "off the map is bare");
    }

    /** Played, the map's scenery is its own and nothing more: the cover is the client's. */
    @Test
    void playedTheSceneryIsTheMapsOwn() {
        var game = Dungeon.newStageSession(WORLD, SETTINGS).game();
        game.runHeadless(1);
        assertEquals(WORLD.paint().scenery(), ((Dressed) game.getMapRecord()).scenery());
    }
}
