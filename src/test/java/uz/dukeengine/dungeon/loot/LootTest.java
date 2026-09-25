package uz.dukeengine.dungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/**
 * What a dead monster leaves, and what picking it up is worth.
 *
 * <p>Nothing here asserts a balance figure — which items exist and what they are
 * worth is written in {@code data/world/world.duke}, so a re-tune should move these tests
 * with it. What is held still is the mechanism: that the draw is the seed's and
 * not the clock's, that a monster's own draw does not depend on when it died,
 * that the floor decides what may be found, and that a run's findings die with it.
 */
class LootTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    private static final List<Loot> DECK = List.of(
            new Loot("Blade", "Blade", "", LootKind.ATTACK, 10, 10, 1),
            new Loot("Plate", "Plate", "", LootKind.ARMOUR, 5, 10, 3));

    /** Always drops, so a test about what drops is not a test about whether. */
    private static LootTable always(long seed) {
        return new LootTable(DECK, seed, 100, 100, 0);
    }

    // ---- the draw ----

    @Test
    void theSameMonsterAlwaysLeavesTheSameThing() {
        var table = always(99L);
        var first = table.dropFor(41, 2, false);
        assertNotNull(first);
        assertEquals(first.id(), table.dropFor(41, 2, false).id(),
                "a seed is the whole run, the loot included");
    }

    /**
     * And it does not depend on the order the player killed things in.
     *
     * <p>A generator advanced once per death would be reproducible too, but only
     * for a player who took the same route — which would make a replay depend on
     * where he walked rather than on what he ordered.
     */
    @Test
    void theOrderOfDeathsDoesNotChangeTheDraw() {
        var table = always(99L);
        var alone = table.dropFor(41, 2, false);
        table.dropFor(7, 2, false);
        table.dropFor(8, 2, false);
        table.dropFor(9, 2, false);
        assertEquals(alone.id(), table.dropFor(41, 2, false).id());
    }

    @Test
    void differentMonstersLeaveDifferentThings() {
        var table = always(99L);
        boolean anyDifferent = false;
        for (int id = 1; id <= 40 && !anyDifferent; id++) {
            anyDifferent = !table.dropFor(id, 3, false).id().equals(table.dropFor(1, 3, false).id());
        }
        assertTrue(anyDifferent, "every monster leaving the same item is not a table");
    }

    @Test
    void mostDeathsLeaveNothing() {
        var stingy = new LootTable(DECK, 5L, 20, 100, 0);
        int dropped = 0;
        for (int id = 1; id <= 200; id++) {
            if (stingy.dropFor(id, 1, false) != null) {
                dropped++;
            }
        }
        // Twenty percent of two hundred, give or take the draw.
        assertTrue(dropped > 20 && dropped < 60, "dropped " + dropped + " of 200");
    }

    @Test
    void theBossAlwaysLeavesSomething() {
        var stingy = new LootTable(DECK, 5L, 0, 100, 0);
        assertNull(stingy.dropFor(3, 1, false), "nothing drops at zero percent");
        assertNotNull(stingy.dropFor(3, 1, true), "except from the thing guarding the way down");
    }

    @Test
    void aDeepItemIsNotFoundInTheFirstRooms() {
        var table = always(1234L);
        for (int id = 1; id <= 60; id++) {
            assertEquals("Blade", table.dropFor(id, 1, false).id(),
                    "Plate has MinDepth 3 and must not appear on the first floor");
        }
        boolean anyPlate = false;
        for (int id = 1; id <= 60 && !anyPlate; id++) {
            anyPlate = "Plate".equals(table.dropFor(id, 3, false).id());
        }
        assertTrue(anyPlate, "and must appear once the floor is deep enough");
    }

    @Test
    void whatIsFoundIsWorthMoreDeeperDown() {
        // One item, so the two depths are certainly comparing the same thing:
        // with a second in the deck a deeper floor could simply have drawn it.
        var growing = new LootTable(List.of(DECK.get(0)), 7L, 100, 100, 50);
        int shallow = growing.dropFor(11, 1, false).value();
        int deep = growing.dropFor(11, 3, false).value();
        assertTrue(deep > shallow, shallow + " -> " + deep);
        // Computed from the depth in one step: two floors at fifty percent is +100%.
        assertEquals(shallow * 2, deep);
    }

    // ---- what a bag comes to ----

    @Test
    void aBagAddsUpWhatIsInIt() {
        var bag = new LootBag();
        bag.take(DECK.get(0), 0, 10);
        bag.take(DECK.get(0), 0, 10);
        bag.take(DECK.get(1), 0, 10);

        assertEquals(20, bag.attackPercent());
        assertEquals(5, bag.armourPercent());
        assertEquals(0, bag.health());
        assertEquals(3, bag.getFound().size());
    }

    @Test
    void theMessageStopsBeingSaid() {
        var bag = new LootBag();
        bag.take(DECK.get(0), 100, 30);

        assertEquals("Blade", bag.noteAt(100));
        assertEquals("Blade", bag.noteAt(129));
        assertEquals("", bag.noteAt(130), "it has been said; the panel goes quiet again");
    }

    @Test
    void aBagHasRoomForSoManyAndNoMore() {
        var bag = new LootBag(2);
        assertTrue(bag.take(DECK.get(0), 0, 10));
        assertTrue(bag.take(DECK.get(1), 0, 10));

        assertTrue(bag.isFull());
        assertFalse(bag.take(DECK.get(0), 0, 10), "a full bag takes nothing more");
        assertEquals(2, bag.getFound().size());
    }

    @Test
    void whatIsTakenOutLeavesItsSlotForTheNext() {
        var bag = new LootBag(3);
        bag.take(DECK.get(0), 0, 10);
        bag.take(DECK.get(1), 0, 10);
        int before = bag.version();

        assertEquals(DECK.get(0), bag.remove(0));
        assertTrue(bag.version() > before, "a change the figures have to hear of");
        assertEquals(java.util.Arrays.asList(null, DECK.get(1), null), bag.slots());
        assertEquals(0, bag.attackPercent(), "and it no longer counts");

        bag.take(DECK.get(0), 0, 10);
        assertEquals(DECK.get(0), bag.slots().getFirst(), "the next goes into the first empty slot");
        assertNull(bag.remove(2), "an empty slot gives nothing");
        assertNull(bag.remove(9), "nor one the bag does not have");
    }

    // ---- in the game ----

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /** Every death leaves something, so a test about what is left is not a test about whether. */
    private static final DungeonSettings GENEROUS = DungeonSettings.parse("""
            LootDrops
              Template = Chest
              DropPercent = 100
              BossDropPercent = 100
              PickupRange = 14
              ValuePercentPerDepth = 0
              NoteFrames = 90
            End
            """);

    /** A monster killed on a real floor, and the chest it left where it fell. */
    private static GameObject killOneFor(DukeGame game) {
        var victim = game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() != game.getLocalPlayerIndex())
                .filter(object -> object.getBody() != null)
                .findFirst().orElseThrow();
        game.getLogic().destroyObject(victim);
        game.runHeadless(2);
        var chest = find(game, "Chest");
        assertNotNull(chest, "a monster that always drops should have left something");
        return chest;
    }

    /**
     * A monster killed on a real floor leaves a chest, and walking over it takes nothing: he has to be sent.
     *
     * <p>Driven through the shipped game rather than a fixture, because what could go wrong is wiring: that a
     * monster carries the drop at all, and that the chest the data file describes holds what it dropped.
     */
    @Test
    void aChestIsLeftAndWalkingOverItTakesNothing() {
        var session = Dungeon.newSession(21L, GENEROUS);
        var game = session.game();
        game.runHeadless(1);
        var chest = killOneFor(game);
        var lying = chest.findModule(GroundItem.class);
        assertNotNull(lying);
        assertNotNull(lying.getHolding(), "and it should be holding something");

        // Placed rather than ordered: this is about standing on it, not about the pathfinder.
        find(game, "Rogue").setPosition(chest.getPosition());
        game.runHeadless(30);

        assertNotNull(find(game, "Chest"), "standing on it is not taking it");
        assertTrue(session.progress().getLoot().getFound().isEmpty());
    }

    /** And a click on it — the order the client sends — hands over what it held. */
    @Test
    void aClickOnTheChestHandsItOver() {
        var session = Dungeon.newSession(21L, GENEROUS);
        var game = session.game();
        game.runHeadless(1);
        var chest = killOneFor(game);
        var item = chest.findModule(GroundItem.class).getHolding();
        find(game, "Rogue").setPosition(chest.getPosition());

        game.postCommand(uz.dukeengine.dungeon.party.PartyOrders.of(
                new PickUp(game.getLocalPlayerIndex(), chest.getId())));
        game.runHeadless(3);

        assertNull(find(game, "Chest"), "the chest is gone the moment it is his");
        assertEquals(List.of(item.id()),
                session.progress().getLoot().getFound().stream().map(Loot::id).toList());
    }

    /** What he puts down lies on the floor, the dungeon's to keep until somebody picks it up. */
    @Test
    void whatHePutsDownLiesOnTheFloor() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var hero = find(game, "Rogue");
        var player = game.getLogic().getRtsPlayer(game.getLocalPlayerIndex());
        float plain = player.getWeaponDamageBonus();
        var blade = new Loot("Blade", "Blade", "", LootKind.ATTACK, 25, 10, 1);
        session.progress().getLoot().take(blade, 0, 30);
        game.runHeadless(2);
        assertTrue(player.getWeaponDamageBonus() > plain, "a sword in his bag is a sword in his hand");

        game.postCommand(uz.dukeengine.dungeon.party.PartyOrders.of(
                new DropItem(game.getLocalPlayerIndex(), 0, hero.getPosition())));
        game.runHeadless(3);

        assertTrue(session.progress().getLoot().getFound().isEmpty(), "out of his bag");
        assertEquals(plain, player.getWeaponDamageBonus(), 0.0001f, "and out of his hand");
        var chest = find(game, "Chest");
        assertNotNull(chest, "and on the floor");
        assertEquals(blade, chest.findModule(GroundItem.class).getHolding());
        assertTrue(chest.getPlayerIndex() != game.getLocalPlayerIndex(), "nobody's hero's to select");
    }

    /**
     * ★ Putting a heart down and taking it up again heals nothing. What he carries is room in him, not health:
     * were a heart to bring the health it makes room for, that would be a heal any time he liked.
     */
    @Test
    void puttingAHeartDownAndTakingItUpAgainHealsNothing() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var body = find(game, "Rogue").getBody();
        var bag = session.progress().getLoot();
        var heart = new Loot("Heart", "Heart", "", LootKind.HEALTH, 90, 10, 1);
        float bare = body.getMaxHealth();

        bag.take(heart, 0, 30);
        game.runHeadless(2);
        assertEquals(bare + 90f, body.getMaxHealth(), 0.01f, "room for ninety more");
        body.setHealth(body.getMaxHealth() * 0.4f);
        float wounded = body.getHealth();

        bag.remove(0);
        game.runHeadless(2);
        assertEquals(bare, body.getMaxHealth(), 0.01f, "the room goes with it");
        bag.take(heart, 0, 30);
        game.runHeadless(2);

        assertEquals(bare + 90f, body.getMaxHealth(), 0.01f, "and comes back with it");
        assertEquals(wounded, body.getHealth(), 1f, "and none of it came back filled");
    }

    @Test
    void whatHeFoundIsFeltAndThenLostWithTheRun() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var player = game.getLogic().getRtsPlayer(game.getLocalPlayerIndex());
        float plain = player.getWeaponDamageBonus();

        session.progress().getLoot().take(
                new Loot("Blade", "Blade", "", LootKind.ATTACK, 25, 10, 1), 0, 30);
        game.runHeadless(2);
        assertTrue(player.getWeaponDamageBonus() > plain,
                "a sword he found should reach the arrow he looses");

        game.getLogic().destroyObject(find(game, "Rogue"));
        game.runHeadless(DungeonSettings.load().run().respawnDelayFrames() + 4);
        assertTrue(session.progress().getLoot().getFound().isEmpty(),
                "a new run starts with nothing, what he found included");
        assertEquals(plain, player.getWeaponDamageBonus(), 0.0001f,
                "and the bonus goes with it rather than outliving him");
    }

    @Test
    void theFileDecidesWhatCanBeFound() {
        assertFalse(SHIPPED.loot().isEmpty(), "the shipped game leaves something behind");
        assertEquals("Chest", SHIPPED.lootDrops().template());
        for (var item : SHIPPED.loot()) {
            assertFalse(item.name().isBlank(), item.id() + " has nothing to say for itself");
            assertTrue(item.value() > 0, item.id() + " is worth nothing");
        }
    }
}
