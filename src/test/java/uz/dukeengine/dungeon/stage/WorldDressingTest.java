package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.run.Floors;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.game.DukeGame;

/**
 * The open world's dressing (WorldMapWriter.dressing): villages with their people, walked to from the city; the
 * travellers on their rounds; the landmarks and the bridges; and fast travel between the watchtowers -- only to one
 * found, for its gold, and the towers found kept by a save.
 */
class WorldDressingTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static Stage world() {
        return Stages.load("world", SETTINGS);
    }

    private static GameObject named(DukeGame game, String name) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + name));
    }

    @Test
    void theVillagesAreNamedPeopledAndWalkedToFromTheCity() {
        var stage = world();
        var villages = stage.story().zones().stream()
                .filter(z -> WorldMapWriter.VILLAGES.contains(z.name())).toList();
        assertTrue(villages.size() >= 4, "only " + villages.size() + " villages");
        var game = Dungeon.newStageSession(stage, SETTINGS).game();
        game.runHeadless(1);
        var logic = game.getLogic();
        var hero = named(game, SETTINGS.run().defaultHero());
        for (var village : villages) {
            var people = stage.story().npcs().stream()
                    .filter(p -> Math.hypot(p.x() - village.x(), p.y() - village.y()) <= village.radius())
                    .filter(p -> p.kind().equals("Villager") || p.kind().equals("Farmer")).toList();
            assertFalse(people.isEmpty(), village.name() + " has nobody in it");
            for (var person : people) {
                var npc = SETTINGS.story().npc(person.kind());
                assertNotNull(npc, "no Npc block for " + person.kind());
                assertEquals(1, SETTINGS.story().conversation(npc.talk()).lines().size(), "a greeting, one line");
            }
            var middle = new Coord3D(village.middleX(CELL), village.middleY(CELL), 0f);
            var path = logic.findPath(hero.getPosition(), middle);
            assertFalse(path.isEmpty(), "no way to " + village.name());
            var end = path.getWaypoints().getLast();
            assertTrue(Math.hypot(end.x() - middle.x(), end.y() - middle.y()) < 3 * CELL,
                    "the way to " + village.name() + " stops at " + end);
        }
    }

    @Test
    void theLandmarksTheBridgesAndTheTowersAreInTheWorld() {
        var stage = world();
        var names = stage.story().zones().stream().map(z -> z.name()).toList();
        for (var landmark : List.of(WorldMapWriter.TEMPLE, WorldMapWriter.STONES, WorldMapWriter.BATTLEFIELD)) {
            assertTrue(names.contains(landmark), "no " + landmark);
            assertTrue(SETTINGS.screens().placeNames().containsKey(landmark), landmark + " is no place on the map");
        }
        var scenery = stage.paint().scenery();
        assertTrue(scenery.stream().anyMatch(p -> p.model().endsWith("town_bridge.gltf")), "no bridge");
        assertTrue(scenery.stream().anyMatch(p -> p.model().contains("weapons/medieval/")), "no battlefield arms");
        var towers = DungeonRun.towers(stage.story(), SETTINGS.travel());
        assertEquals(5, towers.size(), "a tower a road but the forest's");
        assertEquals(towers.size(), stage.story().npcs().stream().filter(p -> p.kind().equals("TowerKeeper"))
                .count(), "a keeper a tower");
    }

    @Test
    void theTravellersWalkTheirRounds() {
        var stage = world();
        var placeNames = stage.story().zones().stream().map(z -> z.name()).toList();
        for (var who : List.of("Traveller", "Pedlar")) {
            var npc = SETTINGS.story().npc(who);
            assertFalse(npc.route().isEmpty(), who + " has no round");
            assertTrue(placeNames.containsAll(npc.route()), who + "'s round names a place the world has not: "
                    + npc.route());
        }
        assertNotNull(SETTINGS.story().merchant("Pedlar"), "the pedlar sells nothing");
        var game = Dungeon.newStageSession(stage, SETTINGS).game();
        game.runHeadless(1);
        var traveller = named(game, "Traveller");
        var from = traveller.getPosition();
        game.runHeadless(SETTINGS.story().trade().wanderFrames() * 3);
        var now = named(game, "Traveller").getPosition();
        assertTrue(Math.hypot(now.x() - from.x(), now.y() - from.y()) > 2 * CELL, "the traveller stays put");
    }

    @Test
    void travelGoesOnlyToATowerFoundAndIsPaidForAndTheSaveKeepsWhatWasFound() {
        var stage = world();
        var towers = DungeonRun.towers(stage.story(), SETTINGS.travel());
        var first = towers.get(0);
        var second = towers.get(1);
        int cost = SETTINGS.travel().cost();
        assertTrue(cost > 0);

        // A run with gold and the second tower found, as a save would bring it back.
        var session = Dungeon.newStageSession(stage, SETTINGS);
        session.game().runHeadless(1);
        var base = session.run().saveNow();
        var save = new SaveGame(base.seed(), base.stage(), base.depth(), base.hero(), base.experience(),
                base.ranks(), base.bag(), base.quests(), base.script(), 3 * cost, base.souls(), base.givenTo(),
                List.of(second.name()));
        var played = Dungeon.newStageSession(stage, SETTINGS);
        played.run().resume(save, Floors.ofStage(stage));
        var game = played.game();
        game.runHeadless(2);
        var hero = named(game, SETTINGS.run().defaultHero());
        int me = hero.getPlayerIndex();

        // To the first, not found: nothing happens, nothing is paid.
        var before = hero.getPosition();
        played.run().travel(game, me, 0);
        assertEquals(before, named(game, SETTINGS.run().defaultHero()).getPosition(), "went to a tower not found");
        assertEquals(3 * cost, played.run().goldOf(me));

        // To the second, found: there, and paid.
        played.run().travel(game, me, 1);
        var there = named(game, SETTINGS.run().defaultHero()).getPosition();
        assertEquals(second.middleX(CELL), there.x(), 0.01f);
        assertEquals(second.middleY(CELL), there.y(), 0.01f);
        assertEquals(2 * cost, played.run().goldOf(me));

        // The first found by standing in it, and then gone to; the save keeps both.
        named(game, SETTINGS.run().defaultHero()).setPosition(
                new Coord3D(first.middleX(CELL), first.middleY(CELL), there.z()));
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() + 1);
        assertTrue(played.run().visited().contains(first.name()), "standing at the tower did not find it");
        played.run().travel(game, me, 1);
        played.run().travel(game, me, 0);
        assertEquals(first.middleX(CELL), named(game, SETTINGS.run().defaultHero()).getPosition().x(), 0.01f);
        var kept = SaveGame.read(played.run().saveNow().text(), "test");
        assertTrue(kept.places().containsAll(List.of(first.name(), second.name())), "the save forgot " + kept.places());

        // With no gold left, refused.
        played.run().travel(game, me, 1);
        assertEquals(0, played.run().goldOf(me));
        var poor = named(game, SETTINGS.run().defaultHero()).getPosition();
        played.run().travel(game, me, 0);
        assertEquals(poor, named(game, SETTINGS.run().defaultHero()).getPosition(), "travelled with no gold");
    }
}
