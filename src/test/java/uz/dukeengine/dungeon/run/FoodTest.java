package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.loot.Items;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillRanks;

/** Food and drink in place of potions: a meal heals over its seconds, a drink fills. See food-and-fire.md. */
class FoodTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final Items ITEMS = SHIPPED.items();

    static GameObject hero(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(SHIPPED.run().defaultHero()))
                .findFirst().orElseThrow();
    }

    @Test
    void aMealHealsOverItsSecondsNotAtOnce() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var meal = ITEMS.potion("qovurilgan_gosht");
        session.progress().getLoot().take(ITEMS.potion(meal, 2, 1), 0, 0);
        game.runHeadless(2);
        var body = hero(game).getBody();
        float share = body.getMaxHealth() * meal.heal() / 100f;
        // What he gets back on his own over the same frames, to be told apart from the meal's.
        body.setHealth(body.getMaxHealth() * 0.2f);
        float before = body.getHealth();
        game.runHeadless(meal.frames() + 10);
        float own = body.getHealth() - before;

        body.setHealth(body.getMaxHealth() * 0.2f);
        game.postCommand(PartyOrders.of(new Drink(game.getLocalPlayerIndex(), 0)));
        game.runHeadless(10);
        assertTrue(body.getHealth() - before < share / 3f + own, "a few frames in, only a little of it");
        game.runHeadless(meal.frames());
        assertEquals(before + share + own, body.getHealth(), share * 0.1f + 2f, "and all of it by its end");
        assertEquals(1, session.progress().getLoot().at(0).value(), "one eaten");
    }

    @Test
    void aDrinkFillsHisManaOverItsSeconds() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var book = hero(game).findModule(SkillBook.class);
        int max = book.getMaxMana();
        assertTrue(max > 0, "the hero has a pool");
        var water = ITEMS.potion("suv");
        session.progress().getLoot().take(ITEMS.potion(water, 1, 1), 0, 0);
        game.runHeadless(2);
        int regen = book.getManaRegen();
        int frames = water.frames() + 2;
        book.resize(0, 0);
        book.resize(max, regen); // emptied
        game.runHeadless(frames);
        int own = book.getMana(); // what his own trickle gives over the same frames
        book.resize(0, 0);
        book.resize(max, regen);

        game.postCommand(PartyOrders.of(new Drink(game.getLocalPlayerIndex(), 0)));
        game.runHeadless(frames);
        assertEquals(max * water.mana() / 100 + own, book.getMana(), 3, "a quarter of his pool back, and his own");
    }

    @Test
    void woodStacksButIsNeverOnTheBelt() {
        var bag = SHIPPED.lootDrops().bag();
        bag.take(ITEMS.potion(ITEMS.potion("otin"), 3, 1), 0, 0);
        bag.take(ITEMS.potion(ITEMS.potion("non"), 1, 2), 0, 0);
        bag.take(ITEMS.potion(ITEMS.potion("otin"), 2, 3), 0, 0);
        Belt.stack(bag, ITEMS);
        assertEquals(5, bag.at(0).value(), "wood joins wood");
        assertEquals(List.of(1), Belt.places(bag, ITEMS, 4), "the bread is the belt's first, the wood never");
    }

    @Test
    void aSaveHoldingTheOldPotionsLoadsAndTheyStillWork() {
        // A save written before food: a stack of the old health potion, as its id was written then.
        var bag = SHIPPED.lootDrops().bag();
        bag.take(ITEMS.potion(ITEMS.potion("hayot_shishasi"), 3, 42), 0, 0);
        var text = SaveGame.of(1L, null, 1, "Rogue", 0, new SkillRanks(2), bag).text();
        assertTrue(text.contains("hayot_shishasi-P1-"), "the old id as it was saved");
        var again = SHIPPED.lootDrops().bag();
        SaveGame.read(text, "old").restore(again, new SkillRanks(2), SHIPPED);
        var potion = ITEMS.potionOf(again.at(0));
        assertNotNull(potion, "it loads as what it was");
        assertEquals(3, again.at(0).value());
        assertEquals(List.of(0), Belt.places(again, ITEMS, 4), "on the belt");
        assertEquals(0, potion.weight(), "but nothing drops it any more");
        assertTrue(SHIPPED.story().merchant("Merchant").stock().stream()
                .noneMatch(ware -> ware.item().equals("hayot_shishasi")), "and nobody sells it");
    }
}
