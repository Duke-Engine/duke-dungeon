package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.craft.Crafting;
import uz.dukeengine.dungeon.loot.Items;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.rpg.item.LootBag;

/** Crafting by recipe: docs/plan/2026-10-15-crafting.md. */
class CraftingTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final Items ITEMS = SHIPPED.items();
    private static final Crafting CRAFTING = SHIPPED.crafting();

    private static int indexOf(String recipe) {
        for (int i = 0; i < CRAFTING.recipes().size(); i++) {
            if (CRAFTING.recipes().get(i).name().equals(recipe)) {
                return i;
            }
        }
        throw new AssertionError("no recipe " + recipe);
    }

    private static Crafting.Recipe recipe(String name) {
        return CRAFTING.recipes().get(indexOf(name));
    }

    private static void give(LootBag bag, String potion, int count) {
        bag.take(ITEMS.potion(ITEMS.potion(potion), count, count * 7L + potion.length()), 0, 0);
    }

    /** The things on {@code base} in the bag, worn or carried -- one taken in is worn where its slot is free -- by id. */
    private static List<String> made(LootBag bag, String base) {
        var out = new java.util.ArrayList<String>();
        for (var item : bag.slots()) {
            var thing = item == null ? null : ITEMS.made(item.id());
            if (thing != null && thing.base() != null && thing.base().name().equals(base)) {
                out.add(item.id());
            }
        }
        return out;
    }

    private static Dungeon.Session started() {
        var session = Dungeon.newSession(21L);
        session.game().runHeadless(2);
        return session;
    }

    private static void craft(Dungeon.Session session, String recipe) {
        var game = session.game();
        game.postCommand(PartyOrders.of(new Craft(game.getLocalPlayerIndex(), indexOf(recipe))));
        // The order is heard a frame or two on, as every order is; at work from then, or refused.
        for (int waited = 0; waited < 5 && !session.run().working(game.getLocalPlayerIndex()); waited++) {
            game.runHeadless(1);
        }
    }

    @Test
    void aCraftSpendsItsPartsAndMakesTheThingAfterItsFrames() {
        var session = started();
        var game = session.game();
        var bag = session.progress().getLoot();
        give(bag, "otin", 2);
        give(bag, "ruda", 2);
        var dagger = recipe("xanjar");
        assertTrue(dagger.known(), "a basic, known from the start");

        craft(session, "xanjar");
        assertTrue(session.run().working(game.getLocalPlayerIndex()), "at work where he stands");
        assertEquals(SHIPPED.food().clipOf("Craft"), FoodTest.hero(game).getBusyClip(), "kneeling over it");
        game.runHeadless(dagger.frames() - 5);
        assertTrue(made(bag, "xanjar").isEmpty(), "two seconds' work, not yet done");
        assertEquals(2, Crafting.materials(bag, ITEMS, "ruda"), "and nothing spent yet");
        game.runHeadless(6);
        assertEquals(1, made(bag, "xanjar").size(), "the dagger in his bag");
        assertEquals(1, Crafting.materials(bag, ITEMS, "otin"), "for one wood");
        assertEquals(0, Crafting.materials(bag, ITEMS, "ruda"), "and two ore");
        assertFalse(session.run().working(game.getLocalPlayerIndex()));
        assertEquals(new Craft(3, 7), PartyOrders.commandOf(PartyOrders.of(new Craft(3, 7))), "on the wire");
    }

    @Test
    void aBlowTakenCallsItOffAndNothingIsSpent() {
        var session = started();
        var game = session.game();
        var bag = session.progress().getLoot();
        give(bag, "teri", 3);
        craft(session, "postin");
        game.runHeadless(20);
        FoodTest.hero(game).getBody().damage(5f);
        game.runHeadless(recipe("postin").frames());
        assertFalse(session.run().working(game.getLocalPlayerIndex()), "called off");
        assertTrue(made(bag, "postin").isEmpty(), "nothing made");
        assertEquals(3, Crafting.materials(bag, ITEMS, "teri"), "and nothing spent");
    }

    @Test
    void aStepCallsItOffAndNothingIsSpent() {
        var session = started();
        var game = session.game();
        var bag = session.progress().getLoot();
        give(bag, "teri", 2);
        craft(session, "choriq");
        game.runHeadless(10);
        var hero = FoodTest.hero(game);
        var here = hero.getPosition();
        hero.getLocomotor().moveTo(new uz.dukeengine.core.math.Coord3D(here.x() + 20f, here.y(), here.z()));
        game.runHeadless(recipe("choriq").frames());
        assertTrue(made(bag, "choriq").isEmpty(), "nothing made");
        assertEquals(2, Crafting.materials(bag, ITEMS, "teri"), "and nothing spent");
    }

    @Test
    void aRecipeHeDoesNotKnowOrCannotPayIsRefused() {
        var session = started();
        var game = session.game();
        var bag = session.progress().getLoot();
        give(bag, "ruda", 10);
        give(bag, "otin", 4);
        assertFalse(recipe("keng_qilich").known());
        craft(session, "keng_qilich");
        assertFalse(session.run().working(game.getLocalPlayerIndex()), "not a recipe he knows");
        game.postCommand(PartyOrders.of(new Craft(game.getLocalPlayerIndex(), 999)));
        game.runHeadless(5);
        assertFalse(session.run().working(game.getLocalPlayerIndex()), "nor one there is not");
        assertTrue(recipe("oltin_uzuk").gold() > session.run().goldOf(game.getLocalPlayerIndex()));
        craft(session, "oltin_uzuk");
        assertFalse(session.run().working(game.getLocalPlayerIndex()), "nor one he cannot pay for");
        game.runHeadless(120);
        assertEquals(10, Crafting.materials(bag, ITEMS, "ruda"), "nothing spent");

        var signature = CRAFTING.recipes().stream().filter(each -> !each.hero().isBlank()
                && !each.hero().equals(SHIPPED.run().defaultHero())).findFirst().orElseThrow();
        session.run().getScript().flag(Crafting.FLAG + signature.name());
        assertFalse(session.run().knows(game.getLocalPlayerIndex(), signature), "another hero's own, never his");
    }

    @Test
    void whatIsRolledIsTheSameOnEveryMachine() {
        var ids = new java.util.ArrayList<String>();
        for (int run = 0; run < 2; run++) {
            var session = started();
            var bag = session.progress().getLoot();
            give(bag, "otin", 1);
            give(bag, "ruda", 2);
            craft(session, "xanjar");
            session.game().runHeadless(recipe("xanjar").frames() + 2);
            ids.addAll(made(bag, "xanjar"));
        }
        assertEquals(2, ids.size());
        assertEquals(ids.get(0), ids.get(1), "drawn from the map's seed, the frame and the player");
        var one = ITEMS.craft("xanjar", 1, recipe("xanjar").rarity(), 3, 99L);
        assertEquals(one.id(), ITEMS.craft("xanjar", 1, recipe("xanjar").rarity(), 3, 99L).id());
        assertEquals(Items.EPIC, ITEMS.rarityOf(ITEMS.craft("olovli_tigh", 1, List.of(), 1, 5L)), "a unique as written");
    }

    @Test
    void twoMagicRingsMakeARareOneAndTheRareRingIsKept() {
        var session = started();
        var game = session.game();
        var bag = session.progress().getLoot();
        var ring = ITEMS.base("oltin_uzuk");
        var rare = ITEMS.loot(ITEMS.make(ring, Items.RARE, 3, 1L));
        bag.take(rare, 0, 0);
        bag.take(ITEMS.loot(ITEMS.make(ring, Items.MAGIC, 3, 2L)), 0, 0);
        bag.take(ITEMS.loot(ITEMS.make(ring, Items.MAGIC, 3, 3L)), 0, 0);
        session.run().getScript().flag(Crafting.FLAG + "olmos_uzuk");
        // ponytail: no gold to give a test hero here, so the recipe's gold is checked by the refusal test alone.
        var free = new Crafting.Recipe("olmos", "olmos_uzuk", 1, List.of(0, 0, 1000, 0), 1, List.of("#uzuk/M 2"), 0,
                30, true, "");
        var match = CRAFTING.match(free, bag, ITEMS);
        assertTrue(match.all());
        assertEquals(2, match.things().size());
        assertTrue(match.things().stream().noneMatch(place -> bag.at(place).id().equals(rare.id())),
                "the least rare that fits is taken");
        assertTrue(session.run().knows(game.getLocalPlayerIndex(), recipe("olmos_uzuk")), "learnt by its flag");
        assertEquals(Items.RARE, ITEMS.rarityOf(ITEMS.craft("olmos_uzuk", 1, recipe("olmos_uzuk").rarity(), 3, 4L)));
    }

    @Test
    void aScrollTeachesTheNextRecipeAndTheSaveKeepsIt() {
        var session = started();
        var game = session.game();
        var bag = session.progress().getLoot();
        var next = CRAFTING.recipes().stream().filter(each -> !each.known() && each.hero().isBlank()).findFirst()
                .orElseThrow();
        assertFalse(session.run().knows(game.getLocalPlayerIndex(), next));
        give(bag, CRAFTING.scroll(), 1);
        game.runHeadless(2);
        assertTrue(session.run().knows(game.getLocalPlayerIndex(), next), "the scroll taught " + next.name());
        assertEquals(0, Crafting.materials(bag, ITEMS, CRAFTING.scroll()), "and is used up");

        var save = SaveGame.read(session.run().saveNow().text(), "test");
        var again = Dungeon.newSession(21L);
        again.run().resume(save, Floors.resumed(save, SHIPPED));
        again.game().runHeadless(2);
        assertTrue(again.run().knows(again.game().getLocalPlayerIndex(), next), "a load knows it still");
        assertNotNull(SHIPPED.story().merchant("Merchant").stock().stream()
                .filter(ware -> ware.item().equals(CRAFTING.scroll())).findFirst().orElse(null), "the merchant sells it");
    }
}
