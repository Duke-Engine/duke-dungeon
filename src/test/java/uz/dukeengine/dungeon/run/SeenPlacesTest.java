package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;

/** What the fog is opened over besides his eyes (E103): the capital, always; and what a cutscene's camera is brought to. */
class SeenPlacesTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static boolean covers(java.util.List<float[]> places, float x, float y) {
        return places.stream().anyMatch(p -> Math.hypot(p[0] - x, p[1] - y) <= p[2]);
    }

    @Test
    void theCapitalIsAlwaysOpenAndACutscenesPlaceWhileItIsShown() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 2, null);
        session.game().runHeadless(2);
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        var zones = Stages.load("world", SETTINGS).story().zones();
        var capital = zones.stream().filter(z -> z.name().equals("Poytaxt")).findFirst().orElseThrow();
        var places = session.run().seenPlaces();
        for (int y = capital.y(); y <= capital.y() + capital.height(); y += 3) {
            for (int x = capital.x(); x <= capital.x() + capital.width(); x += 3) {
                assertTrue(covers(places, (x + 0.5f) * cell, (y + 0.5f) * cell), "the capital open at " + x + "," + y);
            }
        }
        var graves = zones.stream().filter(z -> z.name().equals("Eski_qabrlar")).findFirst().orElseThrow();
        assertTrue(!covers(places, graves.middleX(cell), graves.middleY(cell)), "the old graves are not");
    }
}
