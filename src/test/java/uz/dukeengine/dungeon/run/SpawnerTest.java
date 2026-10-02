package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.loot.LootBag;

/**
 * Where a floor stands the heroes it lets in: round the fountain at the way in, a cell each, and never in the keep —
 * not in its court, and not on the threshold before its gate, where its gate can be seen from.
 */
class SpawnerTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /**
     * Depth 3 of seed 422, with the way in at the foot of the keep's road: a party spread out from the fountain a
     * cell at a time, over floor that joins the keep at its threshold, stood its fifth hero on that threshold.
     *
     * <p>The way in is laid there by hand. Seed 422 at depth 3 once drew its way in beside its keep, and no floor the
     * generator draws does it now: when biomes were laid out as a few large regions every floor was carved
     * differently, and of 80,000 drawn since (four depths, 20,000 seeds each) 1,205 have their way in within five
     * cells of the keep and in none would a party, left to spread, have stood in it. A scene that waits on luck
     * means nothing the first time the luck changes.
     */
    @Test
    void aPartyOfFiveNeverStandsInTheKeep() {
        var drawn = DungeonGenerator.generate(422L, SETTINGS, 3);
        var keep = drawn.keep();
        var floor = withTheWayInAtTheFootOfTheRoad(drawn);
        var arena = Dungeon.world(floor.asciiMap(), floor.levelMap(), SETTINGS, Content.units(),
                Collections.nCopies(5, new LootBag()));
        arena.game().runHeadless(1);

        var placed = Spawner.place(arena.game(), arena.heroes(), Collections.nCopies(5, SETTINGS.run().defaultHero()),
                arena.dungeon(), floor, SETTINGS, 3, null);

        assertEquals(5, placed.heroes().size());
        boolean nearIt = false;
        for (var hero : placed.heroes()) {
            assertNotNull(hero, "a hero with nowhere to stand");
            var at = new GeneratedDungeon.Placement(hero.getPosition().x(), hero.getPosition().y());
            assertFalse(keep.holds(at.cellX(), at.cellY()),
                    "a hero stands in the keep, at cell " + at.cellX() + "," + at.cellY());
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    nearIt |= keep.holds(at.cellX() + dx, at.cellY() + dy);
                }
            }
        }
        assertTrue(nearIt, "this floor no longer brings the party up to its keep: lay the way in nearer it");
    }

    /** The same floor, its heroes let in where the road leaves the keep: two cells out from the gate. */
    private static GeneratedDungeon withTheWayInAtTheFootOfTheRoad(GeneratedDungeon floor) {
        var keep = floor.keep();
        var gate = keep.gate();
        var out = switch (keep.side()) {
            case NORTH -> new int[] {0, -1};
            case EAST -> new int[] {1, 0};
            case SOUTH -> new int[] {0, 1};
            case WEST -> new int[] {-1, 0};
        };
        var foot = GeneratedDungeon.Placement.atCell(gate[0] + 2 * out[0], gate[1] + 2 * out[1]);
        return new GeneratedDungeon(floor.asciiMap(), floor.levelMap(), foot, floor.monsters(), floor.boss(),
                floor.bossRoom(), floor.rooms(), floor.links(), floor.roomStoreys(), floor.props(), floor.relief(),
                floor.levelHeight(), floor.biomes(), floor.scenery(), keep);
    }
}
