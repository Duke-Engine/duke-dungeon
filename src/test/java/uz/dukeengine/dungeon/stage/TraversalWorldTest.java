package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.TraversalLink;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The open world's traversal links (E81, data/world/traversal.duke): each crosses water or mud no walk crosses near
 * by, stands on walkable ground at both ends, keeps out of the towns and the campaign's places, is crossed both ways,
 * plays a clip every hero has, and reaches the engine scaled to world units.
 */
class TraversalWorldTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Stage WORLD = Stages.load("world", SETTINGS);
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static List<TraversalLink> links() {
        return WORLD.paint().traversalLinks();
    }

    private static char cell(Coord3D at) {
        var rows = WORLD.floor().asciiMap().strip().lines().toList();
        return rows.get((int) at.y()).charAt((int) at.x());
    }

    @Test
    void theWorldMapKeepsItsLinksAsTheDataWritesThem() {
        assertEquals(WorldMapWriter.traversalLinks(), links(), "world.map out of step with traversal.duke");
        assertEquals(12, links().size());
        assertTrue(links().stream().allMatch(TraversalLink::bothWays), "every link is crossed both ways");
    }

    @Test
    void eachLinkCrossesWhatNoWalkCrossesFromGroundAtBothEnds() {
        for (var link : links()) {
            assertTrue(cell(link.a()) != '#' && cell(link.b()) != '#', "an end in water or stone: " + link);
            var mid = new Coord3D((link.a().x() + link.b().x()) / 2f, (link.a().y() + link.b().y()) / 2f, 0f);
            assertEquals('#', cell(mid), "it crosses nothing: " + link);
            float span = (float) Math.hypot(link.b().x() - link.a().x(), link.b().y() - link.a().y());
            assertTrue(span >= 3f && span <= 5f, "a jump of " + span + " cells: " + link);
        }
    }

    @Test
    void noLinkStandsInATownOrACampaignPlace() {
        var kept = List.of("Poytaxt", "Saroy", "Bozor", "Yerto'la", "Darvoza_oldi", "Yo'lak", "Eski_qabrlar",
                "Olmazor", "Bog'iston", "Qoratepa", "Tolzor", "Yangiobod");
        for (var zone : WORLD.story().zones()) {
            if (!kept.contains(zone.name())) {
                continue;
            }
            for (var link : links()) {
                for (var end : List.of(link.a(), link.b())) {
                    assertFalse(zone.holds(end.x(), end.y(), 1f),
                            "a link end in " + zone.name() + ": " + link);
                }
            }
        }
    }

    @Test
    void playedTheEngineHasThemInWorldUnitsWithAClipEveryHeroHas() {
        var game = Dungeon.newStageSession(WORLD, SETTINGS).game();
        game.runHeadless(1);
        var played = game.getLogic().traversalLinks();
        assertEquals(links().size(), played.size());
        assertEquals(links().getFirst().a().x() * CELL, played.getFirst().a().x(), 1e-3f, "scaled to world units");
        assertTrue(played.stream().allMatch(link -> link.clip().equals("Jump")));
    }
}
