package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The realistic fort kit's real footprints (the piers of a gatehouse, a keep's box, bigger towers) must leave Act I's
 * places standable and reachable: every zone of the world, and the people the map stands on it, are on open cells and
 * a way leads to each from where the hero comes in -- through the gates, which open as he comes near.
 */
class ActOneFootprintsTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    @Test
    void everyPlaceAndPersonOfTheWorldStandsOnAnOpenCellAHeroCanReach() {
        var stage = Stages.load("world", SETTINGS);
        var session = Dungeon.newStageSession(stage, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var logic = game.getLogic();
        var grid = logic.getPathGrid();
        var hero = logic.getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(session.run().getHeroTemplate())).findFirst().orElseThrow();
        var zones = new ArrayList<>(stage.story().zones());
        zones.addAll(SETTINGS.placesOn("world"));
        var places = new java.util.LinkedHashMap<Coord3D, String>();
        for (var zone : zones) {
            if (zone.name().equals("CageSpot")) {
                continue; // the damsel's cage, shut until the story lets her out
            }
            places.put(new Coord3D(zone.middleX(CELL), zone.middleY(CELL), 0f), zone.name());
        }
        for (var person : stage.story().npcs()) {
            if (person.kind().equals("Damsel")) {
                continue; // caged in the old woman's yard: the story lets her out
            }
            places.put(new Coord3D((person.x() + 0.5f) * CELL, (person.y() + 0.5f) * CELL, 0f), person.kind());
        }
        // The places by a piece of the kit: the city, the royal and the queen's castles, the Black Knight's camp.
        var kit = stage.paint().scenery().stream().filter(p -> p.model().contains("fort_kit/")).toList();
        places.keySet().removeIf(place -> kit.stream().noneMatch(p ->
                Math.hypot(p.x() * CELL - place.x(), p.y() * CELL - place.y()) < 15 * CELL));
        assertTrue(places.size() > 8, "only " + places.size() + " places to check");
        for (var entry : places.entrySet()) {
            var place = entry.getKey();
            assertFalse(grid.isBlocked(grid.toCellX(place), grid.toCellY(place)), entry.getValue() + " at " + place + " is walled up");
            // The gates are shut and open as he comes: put him by each in turn is the walk's job (CampaignWalkTest);
            // here the way must exist once they are open.
            for (var thing : logic.getObjects()) {
                var gate = thing.findModule(uz.dukeengine.rpg.mission.GateUpdate.class);
                if (gate != null) {
                    gate.open();
                }
            }
            game.runHeadless(1);
            if (Math.hypot(hero.getPosition().x() - place.x(), hero.getPosition().y() - place.y()) < 4 * CELL) {
                continue; // he is there already, in the fountain's ring
            }
            var path = logic.findPath(hero.getPosition(), place);
            assertFalse(path.isEmpty(), "no way from the way in to " + entry.getValue() + " at " + place);
        }
    }
}
