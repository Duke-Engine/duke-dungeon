package uz.dukeengine.dungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.item.Loot;

/** A monster of the shipped game leaves a generated thing, the same one off the same death on every machine. */
class ItemDropTest {

    private static final Items ITEMS = uz.dukeengine.dungeon.content.DungeonSettings.load().items();

    /** The first monster of the floor, made to always drop (or, as a boss, to drop as one), killed. */
    private static List<Loot> killOne(long seed, boolean boss) {
        var session = Dungeon.newSession(seed);
        var game = session.game();
        game.runHeadless(1);
        var victim = game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() != game.getLocalPlayerIndex())
                .filter(object -> object.findModule(ItemDrop.class) != null)
                .findFirst().orElseThrow();
        victim.removeModule(victim.findModule(ItemDrop.class));
        victim.addModule(new ItemDrop(victim, ITEMS, "Chest", 100, 100, boss));
        game.getLogic().destroyObject(victim);
        game.runHeadless(2);
        return lying(game);
    }

    private static List<Loot> lying(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .map(object -> object.findModule(GroundItem.class))
                .filter(item -> item != null && item.getHolding() != null)
                .map(GroundItem::getHolding)
                .toList();
    }

    @Test
    void aMonsterOfTheShippedGameDropsAGeneratedThing() {
        var items = ITEMS;
        var dropped = killOne(21L, false);
        assertEquals(1, dropped.size());
        var thing = dropped.getFirst();
        assertTrue(items.made(thing.id()) != null, "made, not taken off the catalogue: " + thing.id());
        assertEquals(thing, items.resolve(thing.id()), "and made again from its id");
    }

    @Test
    void theSameDeathDropsTheSameThing() {
        assertEquals(killOne(33L, false), killOne(33L, false));
    }

    @Test
    void aBossDropsTwoThingsAndNeverACommonOne() {
        var items = ITEMS;
        for (long seed : new long[] {5L, 6L, 7L}) {
            var dropped = killOne(seed, true);
            assertEquals(items.rules().bossDrops(), dropped.size());
            for (var thing : dropped) {
                assertTrue(items.rarityOf(thing) > Items.COMMON, thing.id());
            }
        }
    }

    @Test
    void aMonsterOfTheShippedGameCarriesTheGeneratedDrop() {
        var session = Dungeon.newSession(21L);
        session.game().runHeadless(1);
        List<GameObject> monsters = session.game().getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() != session.game().getLocalPlayerIndex())
                .filter(object -> object.getBody() != null)
                .filter(object -> object.findModule(uz.dukeengine.rpg.hero.LevelBonus.class) != null)
                .toList();
        assertTrue(!monsters.isEmpty());
        for (var monster : monsters) {
            assertNotNull(monster.findModule(ItemDrop.class), monster.getTemplate().name());
        }
    }
}
