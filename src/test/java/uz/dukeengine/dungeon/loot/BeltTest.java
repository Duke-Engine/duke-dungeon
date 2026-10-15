package uz.dukeengine.dungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Belt;
import uz.dukeengine.dungeon.run.Drink;
import uz.dukeengine.game.DukeGame;

/** Potions stack in the backpack, the belt is its first stacks, and keys 1-4 drink from it on a cooldown. */
class BeltTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final Items ITEMS = SHIPPED.items();

    private static GameObject hero(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(SHIPPED.run().defaultHero()))
                .findFirst().orElseThrow();
    }

    @Test
    void stacksOfOnePotionJoinIntoTheFirstAsTheyComeIn() {
        var bag = SHIPPED.lootDrops().bag();
        var health = ITEMS.potion("hayot_shishasi");
        bag.take(ITEMS.potion(health, 3, 1), 0, 0);
        bag.take(ITEMS.potion(ITEMS.potion("mana_shishasi"), 2, 2), 0, 0);
        bag.take(ITEMS.potion(health, 4, 3), 0, 0);
        bag.take(ITEMS.potion(health, 5, 4), 0, 0);
        Belt.stack(bag, ITEMS);
        assertEquals(health.stack(), bag.at(0).value(), "the first full");
        assertEquals("mana_shishasi", ITEMS.potionOf(bag.at(1)).name());
        assertNull(bag.at(2), "an emptied stack leaves its place");
        assertEquals(3 + 4 + 5 - health.stack(), bag.at(3).value(), "and what did not fit stays where it was");

        assertEquals(List.of(0, 1, 3), Belt.places(bag, ITEMS, 4), "the belt: the first stacks, in order");
    }

    @Test
    void aKeyDrinksFromTheBeltOnItsCooldown() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var bag = session.progress().getLoot();
        var health = ITEMS.potion("hayot_shishasi");
        bag.take(ITEMS.potion(health, 2, 11), 0, 0);
        bag.take(ITEMS.potion(health, 1, 12), 0, 0);
        game.runHeadless(2);
        assertEquals(3, bag.at(0).value(), "joined as they came in");

        var body = hero(game).getBody();
        body.setHealth(body.getMaxHealth() * 0.2f);
        float before = body.getHealth();
        int player = game.getLocalPlayerIndex();
        game.postCommand(PartyOrders.of(new Drink(player, 0)));
        game.runHeadless(2);
        assertEquals(before + body.getMaxHealth() * health.heal() / 100f, body.getHealth(), 2f,
                "a share of his most health back");
        assertEquals(2, bag.at(0).value(), "one fewer");

        float drunk = body.getHealth();
        game.postCommand(PartyOrders.of(new Drink(player, 0)));
        game.runHeadless(2);
        assertEquals(drunk, body.getHealth(), 2f, "the belt is not ready again yet");
        assertEquals(2, bag.at(0).value());

        game.runHeadless(ITEMS.rules().beltFrames());
        game.postCommand(PartyOrders.of(new Drink(player, 0)));
        game.postCommand(PartyOrders.of(new Drink(player, 3)));
        game.runHeadless(2);
        assertEquals(1, bag.at(0).value(), "ready again; and a key with nothing on its belt does nothing");
    }

    @Test
    void theLastDraughtEmptiesItsPlace() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var bag = session.progress().getLoot();
        bag.take(ITEMS.potion(ITEMS.potion("mana_shishasi"), 1, 5), 0, 0);
        game.postCommand(PartyOrders.of(new Drink(game.getLocalPlayerIndex(), 0)));
        game.runHeadless(2);
        assertNull(bag.at(0));
    }

    @Test
    void theDrinkOrderCrossesTheWire() {
        assertEquals(new Drink(2, 3), PartyOrders.commandOf(PartyOrders.of(new Drink(2, 3))));
    }

    @Test
    void aStackSaysItsDraughtAndCount() {
        var lines = ITEMS.lines(ITEMS.potion(ITEMS.potion("tiklanish_eliksiri"), 4, 1)).stream()
                .map(Items.Line::text).toList();
        assertEquals(List.of("+40% Jon", "+40% Mana", "Soni: 4"), lines);
        assertTrue(Items.figures(ITEMS.potion(ITEMS.potion("hayot_shishasi"), 4, 1)).isEmpty(),
                "a stack in the backpack moves nothing");
    }
}
