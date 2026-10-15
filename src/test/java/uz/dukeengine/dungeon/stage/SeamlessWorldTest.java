package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap.Zone;

/**
 * Act 1's places folded into the open world (docs/plan/2026-10-15-seamless-campaign.md, section 1): the field before
 * the north gate with its lines and its landing, the corridor into the gate, the old graves, and in the city the
 * palace's court with the throne, the plaza and the cellar -- every one on ground a hero walks to.
 */
class SeamlessWorldTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Stage WORLD = Stages.load("world", SETTINGS);

    private static Zone zone(String name) {
        return WORLD.story().zones().stream().filter(z -> z.name().equals(name)).findFirst().orElse(null);
    }

    private static boolean inside(Zone outer, Zone inner) {
        return outer.holds(inner.middleX(1f), inner.middleY(1f), 1f);
    }

    @Test
    void theWorldHoldsTheActsPlaces() {
        for (var name : new String[] {"Darvoza_oldi", "Saf", "Dushman_safi", "Qonish", "Yo'lak", "Eski_qabrlar",
                "Saroy", "Taxt", "Bozor", "Yerto'la"}) {
            assertNotNull(zone(name), name);
        }
    }

    @Test
    void theBattleIsFoughtOutsideTheCityAndThePalaceIsInsideIt() {
        var city = zone("Poytaxt");
        var field = zone("Darvoza_oldi");
        assertFalse(inside(city, field), "the field is before the gate, not in the city");
        assertTrue(inside(field, zone("Saf")) && inside(field, zone("Dushman_safi")) && inside(field, zone("Qonish")),
                "the two lines and the landing are on the field");
        assertTrue(zone("Saf").middleY(1f) > zone("Dushman_safi").middleY(1f), "the kingdom's line nearer the gate");
        assertFalse(inside(city, zone("Eski_qabrlar")), "the graves are outside the walls");
        assertTrue(inside(city, zone("Saroy")) && inside(zone("Saroy"), zone("Taxt")), "the throne in the palace's court");
        assertTrue(inside(city, zone("Bozor")) && inside(city, zone("Yerto'la")));
        var corridor = zone("Yo'lak");
        assertTrue(inside(corridor, field) || corridor.holds(field.middleX(1f), field.middleY(1f), 1f),
                "the corridor starts at the field");
    }

    @Test
    void everyPlaceIsOnGroundTheHeroWalksTo() {
        var problems = StageCheck.problems(WORLD, SETTINGS);
        assertTrue(problems.isEmpty(), problems.toString());
        var grid = uz.dukeengine.core.pathfind.MapLoader.fromText(WORLD.floor().asciiMap());
        for (var name : new String[] {"Qonish", "Eski_qabrlar", "Taxt", "Bozor", "Yerto'la", "Saf",
                "Dushman_safi"}) {
            var z = zone(name);
            int x = (int) z.middleX(1f);
            int y = (int) z.middleY(1f);
            assertFalse(grid.isBlocked(x, y), name + " stands on open ground");
        }
    }
}
