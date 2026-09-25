package uz.dukeengine.dungeon.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.game.DukeGame;

/**
 * A party's rules, with every player on one machine: the heroes are said before the first floor is laid, a fallen
 * hero waits while the others fight on, the boss down stands everyone up on the next floor, and what a hero picks
 * up is his.
 */
class PartyTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** A party of two on one machine: the first says the Rogue as the match starts, the test says the second. */
    private static Dungeon.Session party(DungeonSettings settings) {
        var session = Dungeon.newPartySession(PartyMatch.endless(4242L), 2, null, settings, "Rogue");
        session.game().runHeadless(1);
        session.game().postCommand(PartyOrders.of(new ChooseHero(2, "Knight")));
        session.game().runHeadless(3);
        return session;
    }

    private static GameObject heroOf(DukeGame game, int player, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() == player && object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    @Test
    void theFirstFloorWaitsUntilEveryHeroIsSaid() {
        var session = Dungeon.newPartySession(PartyMatch.endless(4242L), 2, null, SETTINGS, "Rogue");
        var game = session.game();
        game.runHeadless(10);
        assertTrue(game.getLogic().getObjects().isEmpty(), "nobody stands on a floor one hero short");

        game.postCommand(PartyOrders.of(new ChooseHero(2, "Knight")));
        game.runHeadless(3);

        var rogue = heroOf(game, 1, "Rogue");
        var knight = heroOf(game, 2, "Knight");
        assertNotNull(rogue, "the first player's hero is on the floor");
        assertNotNull(knight, "and the second's");
        assertNotEquals(rogue.getPosition(), knight.getPosition(), "each on a cell of his own");
        assertTrue(rogue.getPosition().distance(knight.getPosition()) < 40f, "and side by side at the way in");
    }

    @Test
    void aHeroThisGameDoesNotHaveIsNotAHero() {
        var session = Dungeon.newPartySession(PartyMatch.endless(4242L), 2, null, SETTINGS, "Rogue");
        var game = session.game();
        game.runHeadless(1);
        game.postCommand(PartyOrders.of(new ChooseHero(2, "Dragon")));
        game.runHeadless(5);
        assertTrue(game.getLogic().getObjects().isEmpty(), "the floor still waits for a real one");
    }

    @Test
    void aFallenHeroWaitsWhileTheOthersFightOn() {
        var session = party(SETTINGS);
        var game = session.game();
        game.getLogic().destroyObject(heroOf(game, 2, "Knight"));
        game.runHeadless(5);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState(), "one hero down is not the run lost");

        game.getLogic().destroyObject(heroOf(game, 1, "Rogue"));
        game.runHeadless(2);
        assertEquals(DungeonRun.State.DEAD, session.run().getState(), "every hero down is");

        game.runHeadless(SETTINGS.run().respawnDelayFrames() + 3);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState(), "and a new run begins for all");
        assertNotNull(heroOf(game, 1, "Rogue"));
        assertNotNull(heroOf(game, 2, "Knight"));
    }

    @Test
    void theBossDownTheWholePartyGoesDownStandingAgain() {
        var session = party(SETTINGS);
        var game = session.game();
        game.getLogic().destroyObject(heroOf(game, 2, "Knight"));
        game.runHeadless(2);
        var boss = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(SETTINGS.bossKindAt(1)))
                .findFirst().orElseThrow();
        game.getLogic().destroyObject(boss);
        game.runHeadless(SETTINGS.run().descendDelayFrames() + 3);

        assertEquals(2, session.run().getDepth(), "down a floor");
        assertNotNull(heroOf(game, 1, "Rogue"), "the one who stood");
        assertNotNull(heroOf(game, 2, "Knight"), "and the one who fell, standing again");
    }

    @Test
    void whatAHeroStandsOnIsHis() {
        var settings = DungeonSettings.parse("""
                LootDrops
                  Template = Chest
                  DropPercent = 100
                  BossDropPercent = 100
                  PickupRange = 14
                  ValuePercentPerDepth = 0
                  NoteFrames = 90
                End
                """);
        var session = party(settings);
        var game = session.game();
        var victim = game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() > 2 && object.getBody() != null)
                .findFirst().orElseThrow();
        var where = victim.getPosition();
        game.getLogic().destroyObject(victim);
        game.runHeadless(2);
        var chest = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Chest")).findFirst().orElseThrow();
        var item = chest.findModule(uz.dukeengine.dungeon.loot.LootUpdate.class).getHolding();

        heroOf(game, 2, "Knight").setPosition(where);
        game.runHeadless(2);

        assertEquals(List.of(item.id()),
                session.run().progressOf(2).getLoot().getFound().stream().map(Loot::id).toList(), "his");
        assertEquals(List.of(), session.run().progressOf(1).getLoot().getFound(), "and nobody else's");
        assertNull(game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Chest")).findFirst().orElse(null));
    }

    @Test
    void eachPlayerHasASeatOfHisOwn() {
        var session = party(SETTINGS);
        assertNotNull(session.run().progressOf(1));
        assertNotNull(session.run().progressOf(2));
        assertNotEquals(session.run().progressOf(1), session.run().progressOf(2));
        assertNotEquals(session.run().learntOf(1), session.run().learntOf(2));
        assertNull(session.run().progressOf(3), "the dungeon has no seat");
    }
}
