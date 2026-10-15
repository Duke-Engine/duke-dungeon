package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Buy;
import uz.dukeengine.dungeon.run.Floors;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.run.Sell;
import uz.dukeengine.dungeon.run.TalkTo;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.hero.LevelBonus;

/** Gold from kills, the merchant's trade as orders, people who walk and stroll, and the purse saved. */
class TradeTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int CHECK = SETTINGS.story().dialogue().checkFrames();

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template)).toList();
    }

    private static GameObject hero(DukeGame game) {
        return all(game, SETTINGS.run().defaultHero()).getFirst();
    }

    private static Dungeon.Session session() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        return session;
    }

    /** The hero kills {@code monster}: a blow from him, so the kill is his. */
    private static void killed(GameObject hero, GameObject monster) {
        monster.getBody().damage(1_000_000f, DamageType.NORMAL,
                new Death(DeathType.NORMAL, hero.getId(), hero.getPlayerIndex()));
    }

    private static void beside(DukeGame game, GameObject person) {
        var at = person.getPosition();
        hero(game).setPosition(new Coord3D(at.x() + 12f, at.y(), at.z()));
    }

    @Test
    void theMerchantIsReadAndAStockOfNothingIsRefused() {
        var story = SETTINGS.story();
        assertEquals(11, story.merchant("Merchant").stock().size());
        assertEquals(6, story.merchant("Merchant").gear(), "and six things rolled at his level");
        assertEquals(new Merchant.Ware("Gauntlet", 80, 2), story.merchant("Merchant").stock().getFirst());
        assertEquals(40, story.trade().sellPercent());
        var wrong = assertThrows(RuntimeException.class, () -> DungeonSettings.parse(Content.data() + """

                Merchant
                  Name = Merchant
                  Stock = [Moonstone 10 1]
                End
                """));
        assertTrue(wrong.getMessage().contains("Moonstone"), wrong.getMessage());
    }

    @Test
    void aKillPaysTheKillerByTheMonstersLevel() {
        var session = session();
        var game = session.game();
        var skeleton = all(game, "Skeleton").getFirst();
        int level = LevelBonus.levelOf(skeleton);
        killed(hero(game), skeleton);
        game.runHeadless(2);
        int player = hero(game).getPlayerIndex();
        assertEquals(SETTINGS.story().trade().goldFor(level), session.run().goldOf(player));
        all(game, "Skeleton").getFirst().getBody().damage(1_000_000f);
        game.runHeadless(2);
        assertEquals(SETTINGS.story().trade().goldFor(level), session.run().goldOf(player), "nobody's kill pays nobody");
    }

    /** E27: an arrow names its archer, so the Rogue's arrow kill pays him — it paid nobody before. */
    @Test
    void aRoguesArrowKillPaysHim() {
        var session = session();
        var game = session.game();
        var hero = hero(game);
        var skeleton = all(game, "Skeleton").getFirst();
        int level = LevelBonus.levelOf(skeleton);
        var at = hero.getPosition();
        skeleton.setPosition(new Coord3D(at.x() + 30f, at.y(), at.z()));
        skeleton.getBody().setHealth(1f);
        game.postCommand(new uz.dukeengine.combat.message.CombatOrder.AttackObject(hero.getPlayerIndex(),
                List.of(hero.getId()), skeleton.getId()));
        game.runHeadless(120);
        assertTrue(!all(game, "Skeleton").contains(skeleton), "shot dead");
        assertEquals(SETTINGS.story().trade().goldFor(level), session.run().goldOf(hero.getPlayerIndex()),
                "and the arrow's kill is his gold");
    }

    @Test
    void buyingAndSellingAreOrdersCheckedInTheWorld() {
        var session = session();
        var game = session.game();
        var run = session.run();
        var merchant = all(game, "Merchant").getFirst();
        int player = hero(game).getPlayerIndex();

        game.postCommand(PartyOrders.of(new Buy(player, merchant.getId(), 0)));
        game.runHeadless(2);
        assertEquals(0, run.goldOf(player), "too far away, and no gold: nothing");

        for (var skeleton : all(game, "Skeleton").subList(0, 7)) {
            killed(hero(game), skeleton);
        }
        game.runHeadless(2);
        int rich = run.goldOf(player);
        assertTrue(rich >= 80, "seven kills pay for a gauntlet: " + rich);

        beside(game, merchant);
        game.postCommand(PartyOrders.of(new TalkTo(player, merchant.getId())));
        game.runHeadless(2);
        assertEquals(List.of(new uz.dukeengine.dungeon.run.DungeonRun.Opened(player, merchant.getId().value())),
                run.getTrades(), "a right-click on him opens his window");

        // He wears a gauntlet already: the one he buys is carried.
        var bag = session.progress().getLoot();
        int zirh = bag.wornPlace("Zirh");
        var worn = SETTINGS.loot().stream().filter(item -> item.id().equals("Gauntlet")).findFirst().orElseThrow();
        bag.take(worn, 0, 0);
        game.postCommand(PartyOrders.of(new Buy(player, merchant.getId(), 0)));
        game.runHeadless(2);
        assertEquals(rich - 80, run.goldOf(player));
        assertEquals("Gauntlet", bag.at(0).id(), "bought as a pick-up is: into the backpack, its slot taken");
        assertEquals(worn, bag.at(zirh), "what he wears as it was");
        assertEquals(1, run.stockOf(merchant.getId().value()).getFirst().count(), "one left");

        game.postCommand(PartyOrders.of(new Sell(player, merchant.getId(), zirh)));
        game.runHeadless(2);
        assertEquals(rich - 80, run.goldOf(player), "what he wears is not for sale");
        assertEquals(worn, bag.at(zirh));

        game.postCommand(PartyOrders.of(new Sell(player, merchant.getId(), 0)));
        game.runHeadless(2);
        assertEquals(rich - 80 + 32, run.goldOf(player), "sold at 40 of the hundred");
        assertEquals(null, session.progress().getLoot().at(0));

        var locket = SETTINGS.loot().stream().filter(item -> item.id().equals("Locket")).findFirst().orElseThrow();
        session.progress().getLoot().take(locket, 0, 0);
        int before = run.goldOf(player);
        game.postCommand(PartyOrders.of(new Sell(player, merchant.getId(), 0)));
        game.runHeadless(2);
        assertEquals(before, run.goldOf(player), "a quest thing does not sell");
        assertEquals("Locket", session.progress().getLoot().at(0).id());
    }

    @Test
    void thePurseIsSavedAndALoadRestocksTheMerchant() {
        var session = session();
        var game = session.game();
        killed(hero(game), all(game, "Skeleton").getFirst());
        game.runHeadless(2);
        int gold = session.run().goldOf(hero(game).getPlayerIndex());
        assertTrue(gold > 0);
        var save = SaveGame.read(session.run().saveNow().text(), "test");
        assertEquals(gold, save.gold());

        var loaded = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        loaded.run().resume(save, Floors.ofStage(Stages.load("talk", SETTINGS)));
        loaded.game().runHeadless(2);
        assertEquals(gold, loaded.run().goldOf(hero(loaded.game()).getPlayerIndex()));
        var merchant = all(loaded.game(), "Merchant").getFirst();
        assertEquals(2, loaded.run().stockOf(merchant.getId().value()).getFirst().count());
    }

    @Test
    void theMerchantStrollsRoundHomeAndTheCommanderWalksWhereSheIsSent() {
        var session = session();
        var game = session.game();
        var merchant = all(game, "Merchant").getFirst();
        var home = merchant.getPosition();
        game.runHeadless(SETTINGS.story().trade().wanderFrames() * 2);
        var now = merchant.getPosition();
        assertNotEquals(home, now, "he strolled");
        float wander = SETTINGS.story().npc("Merchant").wander();
        float dx = now.x() - home.x();
        float dy = now.y() - home.y();
        assertTrue(dx * dx + dy * dy <= (wander + 1f) * (wander + 1f), "and no further than his Wander");

        var commander = all(game, "Commander").getFirst();
        var wall = Stages.load("talk", SETTINGS).story().zones().stream().filter(z -> z.name().equals("Wall"))
                .findFirst().orElseThrow();
        var script = SETTINGS.story().trigger("Ambush");
        assertTrue(script.actions().stream().anyMatch(action -> action.act() == Trigger.Act.WALK));
        session.run().getQuests().finishNow("WallSkeletons");
        game.runHeadless(300);
        float cell = PathGrid.DEFAULT_CELL_SIZE;
        float cx = commander.getPosition().x() - wall.middleX(cell);
        float cy = commander.getPosition().y() - wall.middleY(cell);
        assertTrue(cx * cx + cy * cy <= 15f * 15f, "she stepped back to the wall: " + commander.getPosition());
    }
}
