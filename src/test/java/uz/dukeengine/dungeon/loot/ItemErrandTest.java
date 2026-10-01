package uz.dukeengine.dungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.combat.message.CombatOrder;

/**
 * A hero sent for a thing on the floor, or to put one of his down: he walks there and it changes hands — and the
 * next thing he is told calls it off, as it calls off any errand.
 *
 * <p>In an open room, so nothing here turns on where a seed put a wall.
 */
class ItemErrandTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Loot BLADE = new Loot("Blade", "Blade", "", LootKind.ATTACK, 8, 10, 1);
    private static final Loot SHIELD = new Loot("Shield", "Shield", "", LootKind.ARMOUR, 4, 10, 1);

    /** How long he stands getting nowhere before he gives an errand up, here: four seconds, so a test need not wait. */
    private static final int STUCK = 120;

    /** One cell of floor between two walls, thirty cells long. */
    private static String corridor() {
        var text = new StringBuilder();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 30; x++) {
                text.append(y == 1 && x > 0 && x < 29 ? '.' : '#');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static String room() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                text.append(x == 0 || y == 0 || x == 39 || y == 29 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record Room(DukeGame game, GameObject hero, int floorOwner) {

        ItemErrand.Rules rules() {
            return new ItemErrand.Rules(SETTINGS.lootDrops().pickupRange(), 100_000, "Chest", floorOwner, "Full",
                    "No use", "No way", STUCK);
        }

        /** A chest holding {@code item} at {@code x}, as a monster would have left it. */
        GameObject chestAt(float x, Loot item) {
            var chest = GroundItem.lay(game.getLogic(), "Chest", item, new Coord3D(x, 150f, 0f), floorOwner);
            assertNotNull(chest, "the shipped chest can hold a thing");
            return chest;
        }

        List<GameObject> chests() {
            return game.getLogic().getObjects().stream()
                    .filter(object -> object.getTemplate().name().equals("Chest") && !object.isEffectivelyDead())
                    .toList();
        }
    }

    /** The hero at 100, across the room from whatever he is sent to. */
    private static Room room(float heroAt) {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), heroAt, 150f);
        game.runHeadless(1);
        var hero = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Rogue")).findFirst().orElseThrow();
        return new Room(game, hero, arena.dungeon().getIndex());
    }

    @Test
    void walkingOverAThingTakesNothing() {
        var room = room(100f);
        var chest = room.chestAt(100f, BLADE);
        room.game().runHeadless(30);

        assertEquals(BLADE, chest.findModule(GroundItem.class).getHolding(), "he is standing on it, and it is not his");
    }

    @Test
    void sentForAThingHeWalksToItAndTakesIt() {
        var room = room(100f);
        var chest = room.chestAt(260f, BLADE);
        var bag = new LootBag();

        assertTrue(ItemErrand.pickUp(room.hero(), chest, bag, room.rules()));
        room.game().runHeadless(10);
        assertTrue(bag.getFound().isEmpty(), "not from across the room");
        room.game().runHeadless(300);

        assertEquals(List.of(BLADE), bag.getFound());
        assertTrue(room.chests().isEmpty(), "and the floor is empty where it lay");
        assertEquals(BLADE.name(), bag.noteAt(room.game().getLogic().getFrame()), "and the panel says so");
    }

    @Test
    void aFullBagLeavesItLying() {
        var room = room(100f);
        var chest = room.chestAt(110f, BLADE);
        var bag = new LootBag(1);
        bag.take(SHIELD, 0, 0);

        ItemErrand.pickUp(room.hero(), chest, bag, room.rules());
        room.game().runHeadless(20);

        assertEquals(List.of(SHIELD), bag.getFound());
        assertEquals(BLADE, chest.findModule(GroundItem.class).getHolding(), "it stays where it was");
        assertEquals("Full", bag.noteAt(room.game().getLogic().getFrame()), "and the panel says why");
    }

    /** The player said something else on the way: a walk, an order the engine applies, takes the errand off him. */
    @Test
    void theNextOrderCallsItOff() {
        var room = room(100f);
        var chest = room.chestAt(300f, BLADE);
        var bag = new LootBag();
        ItemErrand.pickUp(room.hero(), chest, bag, room.rules());
        room.game().runHeadless(5);

        room.game().postCommand(new CombatOrder.MoveTo(room.hero().getPlayerIndex(), List.of(room.hero().getId()),
                new Coord3D(300f, 60f, 0f)));
        room.game().runHeadless(300);

        assertTrue(bag.getFound().isEmpty(), "he went where he was sent last");
        assertNull(room.hero().findModule(ItemErrand.class), "and the errand is off him");
        assertEquals(BLADE, chest.findModule(GroundItem.class).getHolding());
    }

    @Test
    void sentToPutAThingDownHeWalksThereAndLeavesIt() {
        var room = room(100f);
        var bag = new LootBag();
        bag.take(SHIELD, 0, 0);
        bag.take(BLADE, 0, 0);

        assertTrue(ItemErrand.drop(room.hero(), 1, new Coord3D(250f, 150f, 0f), bag, room.rules()));
        room.game().runHeadless(10);
        assertEquals(List.of(SHIELD, BLADE), bag.getFound(), "not until he gets there");
        room.game().runHeadless(300);

        assertEquals(List.of(SHIELD), bag.getFound());
        var lying = room.chests();
        assertEquals(1, lying.size(), "it is on the floor");
        var chest = lying.getFirst();
        assertEquals(BLADE, chest.findModule(GroundItem.class).getHolding());
        assertTrue(Math.abs(chest.getPosition().x() - 250f) <= SETTINGS.lootDrops().pickupRange(),
                "where he was sent to put it: " + chest.getPosition());
        assertEquals(room.floorOwner(), chest.getPlayerIndex(), "the dungeon's, and nobody's hero's");
    }

    /** A thing that names what it lies as lies as that: the key goes down as a key, not into a chest. */
    @Test
    void theKeyPutDownLiesAsAKey() {
        var room = room(100f);
        var bag = new LootBag();
        var key = SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
        bag.take(key, 0, 0);

        assertTrue(ItemErrand.drop(room.hero(), 0, new Coord3D(250f, 150f, 0f), bag, room.rules()));
        room.game().runHeadless(300);

        assertTrue(room.chests().isEmpty(), "not in a chest");
        var lying = room.game().getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Key")).findFirst().orElse(null);
        assertNotNull(lying, "it does not lie as a key");
        assertEquals(key, lying.findModule(GroundItem.class).getHolding());
    }

    /** Sent to put a thing down past a wall he cannot get round, he puts it down as near as he got. */
    @Test
    void aPlaceHeCannotReachHasItPutDownWhereHeGotTo() {
        var map = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                map.append(x == 0 || y == 0 || x == 39 || y == 29 || x == 23 ? '#' : '.');
            }
            map.append('\n');
        }
        var arena = Dungeon.world(map.toString(), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 100f, 150f);
        game.runHeadless(1);
        var hero = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Rogue")).findFirst().orElseThrow();
        var room = new Room(game, hero, arena.dungeon().getIndex());
        var bag = new LootBag();
        bag.take(BLADE, 0, 0);

        ItemErrand.drop(hero, 0, new Coord3D(320f, 150f, 0f), bag, room.rules());
        game.runHeadless(600);

        assertTrue(bag.getFound().isEmpty(), "it is out of his bag");
        assertEquals(1, room.chests().size());
        assertTrue(room.chests().getFirst().getPosition().x() < 230f, "on his side of the wall: "
                + room.chests().getFirst().getPosition());
    }

    /**
     * A body in his way that never goes does not keep him on the errand for ever. His friend stands in a corridor one
     * cell wide with the thing past him: his brain stands him behind the friend until the way opens, and nothing asks
     * a friend aside. Once he has stood getting nowhere for as long as the rules say, he gives it up and says why, and
     * the thing stays where it lies.
     */
    @Test
    void aBodyThatNeverGoesHasHimGiveItUpAndSayWhy() {
        var arena = Dungeon.world(corridor(), null, SETTINGS, uz.dukeengine.dungeon.content.Content.units(),
                List.of(new LootBag(), new LootBag()));
        var game = arena.game();
        game.spawn("Rogue", arena.heroes().get(0), 25f, 15f);
        game.spawn("Knight", arena.heroes().get(1), 70f, 15f); // his friend, standing in the way
        game.runHeadless(1);
        var hero = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Rogue")).findFirst().orElseThrow();
        var room = new Room(game, hero, arena.dungeon().getIndex());
        var chest = GroundItem.lay(game.getLogic(), "Chest", BLADE, new Coord3D(250f, 15f, 0f), room.floorOwner());
        var bag = new LootBag();

        assertTrue(ItemErrand.pickUp(hero, chest, bag, room.rules()));
        game.runHeadless(STUCK + 90);

        assertEquals("No way", bag.noteAt(game.getLogic().getFrame()), "he is still waiting behind his friend");
        assertTrue(hero.findModule(ItemErrand.class).isOver(), "with the errand open");
        assertEquals(BLADE, chest.findModule(GroundItem.class).getHolding(), "and the thing stays where it lies");
        assertTrue(hero.getPosition().x() < 70f, "he got past his friend after all: " + hero.getPosition());
    }

    @Test
    void anEmptySlotOrNothingThereIsNoErrand() {
        var room = room(100f);
        var bag = new LootBag();

        assertFalse(ItemErrand.drop(room.hero(), 0, new Coord3D(200f, 150f, 0f), bag, room.rules()));
        assertFalse(ItemErrand.pickUp(room.hero(), room.hero(), bag, room.rules()), "he holds nothing to take");
        assertFalse(ItemErrand.toTheGate(room.hero(), room.chestAt(200f, BLADE), bag, room.rules()),
                "a chest is no gate to go up to");
        assertNull(room.hero().findModule(ItemErrand.class));
    }

    /** Nothing in the slot, nothing in it that does anything, or nothing to use it on: no errand, and he stays. */
    @Test
    void aSlotWithNothingToUseOrNothingToUseItOnIsNoErrand() {
        var room = room(100f);
        var chest = room.chestAt(200f, SHIELD);
        var key = SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
        var bag = new LootBag();
        bag.take(BLADE, 0, 0);
        bag.take(key, 0, 0);

        assertFalse(ItemErrand.use(room.hero(), 2, chest, bag, room.rules()), "an empty slot");
        assertFalse(ItemErrand.use(room.hero(), 0, chest, bag, room.rules()), "a blade does nothing when used");
        assertFalse(ItemErrand.use(room.hero(), 1, null, bag, room.rules()), "the key on nothing");
        assertFalse(ItemErrand.use(room.hero(), 1, chest, null, room.rules()), "and with no bag");
        assertFalse(ItemErrand.use(null, 1, chest, bag, room.rules()), "by nobody");
        assertNull(room.hero().findModule(ItemErrand.class));
        assertTrue(ItemErrand.use(room.hero(), 1, chest, bag, room.rules()), "the key on a chest is an errand");
    }
}
