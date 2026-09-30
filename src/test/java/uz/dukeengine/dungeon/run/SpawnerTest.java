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
     * Depth 3 of seed 422, where the way in lies near the keep: a party spread out from the fountain a cell at a
     * time, over floor that joins the keep at its threshold, stood its fifth hero on that threshold.
     */
    @Test
    void aPartyOfFiveNeverStandsInTheKeep() {
        var floor = DungeonGenerator.generate(422L, SETTINGS, 3);
        var keep = floor.keep();
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
        assertTrue(nearIt, "this floor no longer brings the party up to its keep: find one that does");
    }
}
