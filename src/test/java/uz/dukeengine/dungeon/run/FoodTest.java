package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.food.Food;
import uz.dukeengine.dungeon.loot.Items;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.item.LootBag;
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

    // ---- work: chopping, picking, building a fire, cooking ----

    private static final uz.dukeengine.dungeon.food.Food FOOD = SHIPPED.food();

    /** Open ground {@code from} to 60 units from the hero, the first of eight ways round him that has it. */
    private static Coord3D open(DukeGame game, GameObject hero, float from) {
        var logic = game.getLogic();
        var grid = logic.getPathGrid();
        var here = hero.getPosition();
        for (float distance = from; distance < 60f; distance += 2f) {
            for (int way = 0; way < 8; way++) {
                float x = here.x() + (float) Math.cos(way * Math.PI / 4) * distance;
                float y = here.y() + (float) Math.sin(way * Math.PI / 4) * distance;
                var at = new Coord3D(x, y, 0f);
                if (!grid.isBlocked(grid.toCellX(at), grid.toCellY(at)) && grid.clearOfCircles(x, y, 6f)) {
                    return new Coord3D(x, y, logic.groundHeight(at));
                }
            }
        }
        throw new AssertionError("no open ground by the hero");
    }

    private static GameObject lay(DukeGame game, String template, Coord3D at) {
        var logic = game.getLogic();
        return logic.spawn(logic.getThingFactory().findTemplate(template), at, PlayerList.NEUTRAL_INDEX);
    }

    private static int count(LootBag bag, String potion) {
        int count = 0;
        for (int place = 0; place < bag.backpackSize(); place++) {
            var kind = ITEMS.potionOf(bag.at(place));
            if (kind != null && kind.name().equals(potion)) {
                count += bag.at(place).value();
            }
        }
        return count;
    }

    private static boolean standing(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().anyMatch(thing -> thing.getTemplate().name().equals(template));
    }

    /** Sent to work {@code thing}: the frames until he is at it, at most a few seconds' walk. */
    private static void workAt(Dungeon.Session session, GameObject thing) {
        var game = session.game();
        int player = game.getLocalPlayerIndex();
        game.postCommand(PartyOrders.of(new Work(player, thing.getId())));
        for (int walked = 0; walked < 200 && !session.run().working(player); walked++) {
            game.runHeadless(1);
        }
        assertTrue(session.run().working(player), "he walked up and set to it");
    }

    private static final uz.dukeengine.dungeon.stage.Stage WORLD = uz.dukeengine.dungeon.stage.Stages.load("world",
            SHIPPED);

    /** The tree of the world's own scenery nearest the hero that may be felled, by index. */
    private static int nearestTree(DukeGame game) {
        var logic = game.getLogic();
        var here = hero(game).getPosition();
        var models = WORLD.paint().scenery();
        int best = -1;
        float bestGap = Float.MAX_VALUE;
        for (int piece = 0; piece < models.size(); piece++) {
            var footprint = logic.sceneryPiece(piece);
            if (footprint == null || FOOD.gather(models.get(piece).model()) == null) {
                continue;
            }
            float gap = (float) Math.hypot(footprint.x() - here.x(), footprint.y() - here.y());
            if (gap < bestGap) {
                best = piece;
                bestGap = gap;
            }
        }
        return best;
    }

    @Test
    void choppingATreeOfTheWorldGivesWoodFellsItAndTheSaveKeepsItFelled() {
        var session = Dungeon.newStageSession(WORLD, SHIPPED);
        var game = session.game();
        game.runHeadless(2);
        var logic = game.getLogic();
        int player = game.getLocalPlayerIndex();
        int tree = nearestTree(game);
        var footprint = logic.sceneryPiece(tree);
        var chop = FOOD.gather(WORLD.paint().scenery().get(tree).model());
        assertEquals(Food.CHOP, session.run().groundWord(game, new Coord3D(footprint.x() + 3f, footprint.y(), 0f)),
                "the pointer over its crown is an axe");

        game.postCommand(PartyOrders.of(new Work(player, null, new Coord3D(footprint.x(), footprint.y(), 0f))));
        for (int walked = 0; walked < 1200 && !session.run().working(player); walked++) {
            game.runHeadless(1);
        }
        assertTrue(session.run().working(player), "he walked up to the tree and set to it");
        assertEquals("Interact", hero(game).getBusyClip(), "looping his work's clip");
        game.runHeadless(chop.frames() - 10);
        assertEquals(0, count(session.progress().getLoot(), "otin"), "a few seconds' work, not yet done");
        assertFalse(logic.isSceneryRemoved(tree), "and the tree still stands");
        game.runHeadless(12);
        assertEquals(chop.count(), count(session.progress().getLoot(), "otin"), "his wood");
        assertTrue(logic.isSceneryRemoved(tree), "the tree is felled from the world's scenery");
        assertTrue(standing(game, "Stump"), "and its stump stands until it grows back");
        assertEquals(null, hero(game).getBusyClip(), "done, his clip ends");

        var save = SaveGame.read(session.run().saveNow().text(), "test");
        assertEquals(List.of(tree), save.felled(), "the save keeps it felled");
        var again = Dungeon.newStageSession(WORLD, SHIPPED);
        again.run().resume(save, Floors.resumed(save, SHIPPED));
        again.game().runHeadless(2);
        assertTrue(again.game().getLogic().isSceneryRemoved(tree), "and a load fells it again");
        assertTrue(standing(again.game(), "Stump"));
    }

    @Test
    void aBlowTakenCallsTheWorkOffAndNothingIsSpent() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var hero = hero(game);
        var bush = lay(game, "BerryBush", open(game, hero, 20f));
        workAt(session, bush);
        assertEquals("Interact", hero.getBusyClip());
        game.runHeadless(20);
        hero.getBody().setHealth(hero.getBody().getHealth() - 5f); // struck
        game.runHeadless(FOOD.gather("BerryBush").frames());
        assertFalse(session.run().working(game.getLocalPlayerIndex()), "called off");
        assertEquals(null, hero.getBusyClip(), "and his clip with it");
        assertEquals(0, count(session.progress().getLoot(), "rezavor"));
        assertNotNull(game.getLogic().findObject(bush.getId()), "the bush stands");
    }

    @Test
    void aFireCostsItsWoodAndBurnsOut() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var bag = session.progress().getLoot();
        int player = game.getLocalPlayerIndex();
        bag.take(ITEMS.potion(ITEMS.potion("otin"), FOOD.buildWood() - 1, 1), 0, 0);
        game.postCommand(PartyOrders.of(new Work(player, null)));
        game.runHeadless(2);
        assertFalse(standing(game, "Campfire"), "too little wood: no fire");
        assertEquals(FOOD.buildWood() - 1, count(bag, "otin"), "and nothing spent");
        assertEquals(new Work(3, null), PartyOrders.commandOf(PartyOrders.of(new Work(3, null))), "on the wire");

        bag.take(ITEMS.potion(ITEMS.potion("otin"), 3, 2), 0, 0);
        game.postCommand(PartyOrders.of(new Work(player, null)));
        game.runHeadless(2);
        assertTrue(standing(game, "Campfire"), "a fire at his feet");
        assertEquals(2, count(bag, "otin"), "for its wood");
        game.runHeadless(FOOD.fireFrames() + 5);
        assertFalse(standing(game, "Campfire"), "and it burns out");
    }

    @Test
    void cookingIsAtAFireAndTakesItsSeconds() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var hero = hero(game);
        var bag = session.progress().getLoot();
        bag.take(ITEMS.potion(ITEMS.potion("xom_gosht"), 2, 1), 0, 0);
        var fire = lay(game, "Campfire", open(game, hero, FOOD.fireRadius() + 15f));
        int player = game.getLocalPlayerIndex();

        game.postCommand(PartyOrders.of(new Work(player, fire.getId())));
        game.runHeadless(2);
        assertFalse(session.run().working(player), "too far from the fire to cook: he goes to it first");
        workAt(session, fire);
        float dx = hero.getPosition().x() - fire.getPosition().x();
        float dy = hero.getPosition().y() - fire.getPosition().y();
        assertTrue(dx * dx + dy * dy <= FOOD.fireRadius() * FOOD.fireRadius(), "cooking within its reach");
        game.runHeadless(FOOD.cookFrames() - 5);
        assertEquals(0, count(bag, "qovurilgan_gosht"), "three and a half seconds, not yet");
        game.runHeadless(6);
        assertEquals(1, count(bag, "qovurilgan_gosht"), "the meal in his bag");
        assertEquals(1, count(bag, "xom_gosht"), "for one raw");
        assertTrue(FOOD.cookFrames() >= 90 && FOOD.cookFrames() <= 120, "three to four seconds");
    }

    private static List<String> laid(DukeGame game) {
        var kinds = List.of("Campfire", "BerryBush", "PumpkinPatch", "Deer", "Boar");
        return game.getLogic().getObjects().stream().filter(thing -> kinds.contains(thing.getTemplate().name()))
                .map(thing -> thing.getTemplate().name() + " " + Math.round(thing.getPosition().x()) + " "
                        + Math.round(thing.getPosition().y()))
                .toList();
    }

    @Test
    void theOpenWorldIsLaidWithItsThingsTheSameEveryTimeAndADeerLeavesMeat() {
        var stage = WORLD;
        var game = Dungeon.newStageSession(stage, SHIPPED).game();
        game.runHeadless(1);
        var things = laid(game);
        java.util.function.Function<String, Long> of = kind -> things.stream()
                .filter(line -> line.startsWith(kind + " ")).count();
        assertTrue(of.apply("Campfire") >= 12, "a fire by the villages and towers: " + of.apply("Campfire"));
        assertTrue(of.apply("BerryBush") >= 12 && of.apply("PumpkinPatch") >= 10, "bushes and pumpkins");
        assertTrue(of.apply("Deer") >= 8 && of.apply("Boar") >= 4, "animals: " + things);
        var again = Dungeon.newStageSession(stage, SHIPPED).game();
        again.runHeadless(1);
        assertEquals(things, laid(again), "drawn from the map's seed: the same every time");

        var deer = game.getLogic().getObjects().stream().filter(thing -> thing.getTemplate().name().equals("Deer"))
                .findFirst().orElseThrow();
        var at = deer.getPosition();
        deer.getBody().damage(100_000f);
        game.runHeadless(3);
        var chest = SHIPPED.lootDrops().template();
        assertTrue(game.getLogic().getObjects().stream().anyMatch(thing -> thing.getTemplate().name().equals(chest)
                && Math.abs(thing.getPosition().x() - at.x()) < 1f && Math.abs(thing.getPosition().y() - at.y()) < 1f),
                "its meat lies where it fell");
    }

    @Test
    void aBushPickedGrowsBack() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var spot = open(game, hero(game), 20f);
        var bush = lay(game, "BerryBush", spot);
        var pick = FOOD.gather("BerryBush");
        workAt(session, bush);
        game.runHeadless(pick.frames() + 1);
        assertEquals(pick.count(), count(session.progress().getLoot(), "rezavor"));
        assertFalse(standing(game, "BerryBush"), "picked bare");
        game.runHeadless(pick.regrow() - 10);
        assertFalse(standing(game, "BerryBush"), "not yet");
        game.runHeadless(12);
        var grown = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("BerryBush")).findFirst().orElseThrow();
        assertEquals(spot.x(), grown.getPosition().x(), 0.01f, "where it stood");
        assertEquals(spot.y(), grown.getPosition().y(), 0.01f);
    }
}
