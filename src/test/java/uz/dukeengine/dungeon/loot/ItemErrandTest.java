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
            return rules(STUCK, 100_000);
        }

        /** The same with limits of its own on how long he may get no nearer: not fighting, and fighting or not. */
        ItemErrand.Rules rules(int stuck, int stuckFighting) {
            return new ItemErrand.Rules(SETTINGS.lootDrops().pickupRange(), 100_000, "Chest", floorOwner, "Full",
                    "No use", "No way", stuck, stuckFighting);
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

    /** Deaf, so a skeleton in his way stands there to be shot rather than coming at him. */
    private static final DungeonSettings DEAF = DungeonSettings.parse("""
            Monster
              Name = Skeleton
              SenseRadius = 1
              ChaseRadius = 1
              CloseDistance = 4
            End
            """);

    /** The hero at 25 in a corridor one cell wide, a chest at 250, and at 70 something standing in his way. */
    private record Corridor(DukeGame game, GameObject hero, GameObject inTheWay, GameObject chest, Room room) {
    }

    /**
     * The corridor with a friend of his in the way -- a second hero's Knight, whom nothing asks aside and whom he will
     * not shoot -- or, {@code aFriend} false, one of the dungeon's: a deaf skeleton, which stands there and is shot.
     */
    private static Corridor inACorridorWith(boolean aFriend) {
        var arena = Dungeon.world(corridor(), null, aFriend ? SETTINGS : DEAF,
                uz.dukeengine.dungeon.content.Content.units(), List.of(new LootBag(), new LootBag()));
        var game = arena.game();
        game.spawn("Rogue", arena.heroes().get(0), 25f, 15f);
        if (aFriend) {
            game.spawn("Knight", arena.heroes().get(1), 70f, 15f);
        } else {
            game.spawn("Skeleton", arena.dungeon(), 70f, 15f);
        }
        game.runHeadless(1);
        var hero = named(game, "Rogue");
        var room = new Room(game, hero, arena.dungeon().getIndex());
        var chest = GroundItem.lay(game.getLogic(), "Chest", BLADE, new Coord3D(250f, 15f, 0f), room.floorOwner());
        return new Corridor(game, hero, named(game, aFriend ? "Knight" : "Skeleton"), chest, room);
    }

    private static GameObject named(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template)).findFirst().orElseThrow();
    }

    /**
     * A body in his way that never goes does not keep him on the errand for ever. His friend stands in a corridor one
     * cell wide with the thing past him: his brain stands him behind the friend until the way opens, and nothing asks
     * a friend aside. Once he has gone as long as the rules say without getting any nearer, he gives it up and says
     * why, and the thing stays where it lies.
     */
    @Test
    void aBodyThatNeverGoesHasHimGiveItUpAndSayWhy() {
        var corridor = inACorridorWith(true);
        var hero = corridor.hero();
        var bag = new LootBag();

        assertTrue(ItemErrand.pickUp(hero, corridor.chest(), bag, corridor.room().rules()));
        corridor.game().runHeadless(STUCK + 90);

        assertEquals("No way", bag.noteAt(corridor.game().getLogic().getFrame()),
                "he is still waiting behind his friend");
        assertTrue(hero.findModule(ItemErrand.class).isOver(), "with the errand open");
        assertEquals(BLADE, corridor.chest().findModule(GroundItem.class).getHolding(),
                "and the thing stays where it lies");
        assertTrue(hero.getPosition().x() < 70f, "he got past his friend after all: " + hero.getPosition());
    }

    /**
     * Given up, it stays given up: when his friend goes at last, his brain does not walk him on to the thing it had
     * stood him short of -- to stand there having said he could not get there.
     */
    @Test
    void anErrandGivenUpIsNotWalkedOnWhenTheWayOpens() {
        var corridor = inACorridorWith(true);
        var hero = corridor.hero();
        var bag = new LootBag();
        assertTrue(ItemErrand.pickUp(hero, corridor.chest(), bag, corridor.room().rules()));
        corridor.game().runHeadless(STUCK + 90);
        assertEquals("No way", bag.noteAt(corridor.game().getLogic().getFrame()), "or this tells nothing");
        var gaveUpAt = hero.getPosition();

        corridor.inTheWay().getBody().damage(1e9f); // the friend falls, and the way is open
        corridor.game().runHeadless(300);

        assertTrue(hero.getPosition().distance(gaveUpAt) < 5f,
                "having said he could not get there, he went on anyway, to " + hero.getPosition());
    }

    /**
     * A body in his way that he is fighting is not a way that stays shut. His brain stands him behind a skeleton in
     * the corridor and he shoots it; however long that takes -- longer here than the rules let him get no nearer
     * while not fighting -- he is not getting nowhere. It falls, and he goes on and takes the thing.
     */
    @Test
    void aBodyHeIsFightingDoesNotMakeHimGiveItUp() {
        var corridor = inACorridorWith(false);
        corridor.inTheWay().getBody().setHealth(10f); // five of his arrows here: four seconds of fighting
        var bag = new LootBag();

        assertTrue(ItemErrand.pickUp(corridor.hero(), corridor.chest(), bag, corridor.room().rules(30, 100_000)));
        corridor.game().runHeadless(600);

        assertTrue(corridor.inTheWay().isEffectivelyDead(), "the skeleton still stands, so this tells nothing");
        assertEquals(List.of(BLADE), bag.getFound(),
                "he gave it up while he was fighting his way: " + bag.noteAt(corridor.game().getLogic().getFrame()));
    }

    /**
     * But a fight in his way that never ends -- a skeleton mended as fast as he hurts it -- ends the errand all the
     * same, once he has gone as long as the rules say, fighting or not, without getting any nearer.
     */
    @Test
    void aFightThatNeverEndsEndsTheErrandAllTheSame() {
        var corridor = inACorridorWith(false);
        var bag = new LootBag();
        assertTrue(ItemErrand.pickUp(corridor.hero(), corridor.chest(), bag, corridor.room().rules(100_000, 200)));

        var body = corridor.inTheWay().getBody();
        for (int frame = 0; frame < 400; frame++) {
            body.setHealth(body.getMaxHealth());
            corridor.game().runHeadless(1);
        }

        assertEquals("No way", bag.noteAt(corridor.game().getLogic().getFrame()), "he is fighting it still");
        assertEquals(BLADE, corridor.chest().findModule(GroundItem.class).getHolding());
    }

    /**
     * Each cell nearer starts the count again. His brain stands him behind his friend for most of what the rules let
     * him stand; the friend walks on a way and stops, he follows him nearer and is stood still again -- longer, all
     * told, than the rules allow, but never that long since he last got nearer. He is still on it, and once the way
     * opens he takes the thing.
     */
    @Test
    void aCellNearerStartsTheCountAgain() {
        var corridor = inACorridorWith(true);
        var game = corridor.game();
        var hero = corridor.hero();
        var bag = new LootBag();
        assertTrue(ItemErrand.pickUp(hero, corridor.chest(), bag, corridor.room().rules()));
        game.runHeadless(40 + STUCK * 3 / 4);
        assertFalse(hero.getLocomotor().isMoving(), "his brain never stood him still, so this tells nothing");

        corridor.inTheWay().getLocomotor().moveTo(new Coord3D(150f, 15f, 0f)); // a way on, and he stops again
        game.runHeadless(100 + STUCK * 3 / 4);
        assertFalse(hero.getLocomotor().isMoving(), "his brain never stood him still again, so this tells nothing");
        assertTrue(hero.getPosition().x() > 100f, "he never followed him nearer, so this tells nothing");
        assertEquals("", bag.noteAt(game.getLogic().getFrame()), "he counted the stand from before he got nearer");

        corridor.inTheWay().getBody().damage(1e9f);
        game.runHeadless(300);
        assertEquals(List.of(BLADE), bag.getFound());
    }

    /**
     * A finished errand still on him is not what a walk is for. He has put a thing down at his feet, and his legs are
     * then given a walk by something other than an order -- as a level gained gives a walk back -- that his brain
     * stands still behind his friend: when the friend goes, he goes on.
     */
    @Test
    void aWalkHeldAfterAnErrandIsDoneGoesOnWhenTheWayOpens() {
        var corridor = inACorridorWith(true);
        var game = corridor.game();
        var hero = corridor.hero();
        var bag = new LootBag();
        bag.take(SHIELD, 0, 0);
        assertTrue(ItemErrand.drop(hero, 0, hero.getPosition(), bag, corridor.room().rules()));
        game.runHeadless(2);
        assertTrue(hero.findModule(ItemErrand.class).isOver(), "the errand is not done, so this tells nothing");

        hero.getLocomotor().moveTo(new Coord3D(200f, 15f, 0f));
        game.runHeadless(90);
        assertFalse(hero.getLocomotor().isMoving(), "his brain never stood him still, so this tells nothing");
        corridor.inTheWay().getBody().damage(1e9f);
        game.runHeadless(300);

        assertTrue(hero.getPosition().x() > 150f, "he stood where his brain stood him: " + hero.getPosition());
    }

    /**
     * Gone before he gets there -- somebody else took what was in it -- the errand is over, and his legs stop rather
     * than walk him on to the empty floor.
     */
    @Test
    void aThingTakenBeforeHeGetsThereStopsHisWalk() {
        var room = room(100f);
        var chest = room.chestAt(320f, BLADE);
        var bag = new LootBag();
        assertTrue(ItemErrand.pickUp(room.hero(), chest, bag, room.rules()));
        room.game().runHeadless(30);

        chest.findModule(GroundItem.class).take();
        room.game().runHeadless(2);
        var stoppedAt = room.hero().getPosition();
        room.game().runHeadless(60);

        assertFalse(room.hero().getLocomotor().isMoving(), "his legs walk on");
        assertTrue(room.hero().getPosition().distance(stoppedAt) < 2f, "he walked on, to " + room.hero().getPosition());
        assertEquals("", bag.noteAt(room.game().getLogic().getFrame()), "and he said something of it");
    }

    /**
     * Asked aside on his way, he goes on afterwards. A friend walking the other way along his line meets him head on,
     * and he, the later in, steps aside: which ends a walk exactly onto a thing, though his legs go on naming its place
     * as their goal, and their step aside as an arrival. He looks again from where he stepped to, goes on and takes it.
     */
    @Test
    void askedAsideOnTheWayHeGoesOnAndTakesIt() {
        var arena = Dungeon.world(room(), null, SETTINGS, uz.dukeengine.dungeon.content.Content.units(),
                List.of(new LootBag(), new LootBag()));
        var game = arena.game();
        game.spawn("Knight", arena.heroes().get(1), 300f, 150f); // in first, so the right of way is his
        game.spawn("Rogue", arena.heroes().get(0), 50f, 150f);
        game.runHeadless(1);
        var hero = named(game, "Rogue");
        var room = new Room(game, hero, arena.dungeon().getIndex());
        var chest = room.chestAt(350f, BLADE);
        var bag = new LootBag();

        assertTrue(ItemErrand.pickUp(hero, chest, bag, room.rules()));
        named(game, "Knight").getLocomotor().moveTo(new Coord3D(20f, 150f, 0f));
        game.runHeadless(450);

        assertEquals(List.of(BLADE), bag.getFound(),
                "he said '" + bag.noteAt(game.getLogic().getFrame()) + "', at " + hero.getPosition());
    }

    /**
     * Given up, he stops where he is. An errand that ends while his legs are still walking it -- here a way round that
     * leads off from the thing for longer than the rules let him go without getting nearer -- does not leave them
     * walking on to where he has just said he cannot get.
     */
    @Test
    void anErrandGivenUpOnTheWayStopsHisLegs() {
        // A pocket open to the west, the hero in it and the chest outside it to the east: his way leads off west.
        var map = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean pocket = (y == 10 || y == 20) && x >= 3 && x <= 14 || x == 14 && y >= 10 && y <= 20;
                map.append(x == 0 || y == 0 || x == 39 || y == 29 || pocket ? '#' : '.');
            }
            map.append('\n');
        }
        var arena = Dungeon.world(map.toString(), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 100f, 150f);
        game.runHeadless(1);
        var hero = named(game, "Rogue");
        var room = new Room(game, hero, arena.dungeon().getIndex());
        var chest = GroundItem.lay(game.getLogic(), "Chest", BLADE, new Coord3D(250f, 150f, 0f), room.floorOwner());
        var bag = new LootBag();

        assertTrue(ItemErrand.pickUp(hero, chest, bag, room.rules(100_000, 30)));
        game.runHeadless(60);
        assertEquals("No way", bag.noteAt(game.getLogic().getFrame()), "he never gave it up, so this tells nothing");
        var gaveUpAt = hero.getPosition();
        game.runHeadless(120);

        assertFalse(hero.findModule(uz.dukeengine.core.module.MoveUpdate.class).isMoving(), "his legs walk on");
        assertTrue(hero.getPosition().distance(gaveUpAt) < 2f, "he walked on, to " + hero.getPosition());
    }

    /**
     * A skill is the player's latest word too: cast on the way, it ends the errand he was on, as any order does. He
     * stands where the dash put him, the thing he was taking down stays in his bag, and nothing is said of it later.
     */
    @Test
    void aSkillCastOnTheWayEndsAnErrandToPutAThingDown() {
        var room = room(100f);
        var bag = new LootBag();
        bag.take(BLADE, 0, 0);
        assertTrue(ItemErrand.drop(room.hero(), 0, new Coord3D(320f, 150f, 0f), bag, room.rules()));
        room.game().runHeadless(10);

        castHisDash(room);
        room.game().runHeadless(STUCK + 120);

        assertNull(room.hero().findModule(ItemErrand.class), "the errand is still on him");
        assertEquals(List.of(BLADE), bag.getFound(), "it went down where he stood, long after the cast");
        assertTrue(room.chests().isEmpty());
    }

    /** And an errand to take a thing: the chest stays where it lies, and he never says he could not get to it. */
    @Test
    void aSkillCastOnTheWayEndsAnErrandToTakeAThing() {
        var room = room(100f);
        var chest = room.chestAt(320f, BLADE);
        var bag = new LootBag();
        assertTrue(ItemErrand.pickUp(room.hero(), chest, bag, room.rules()));
        room.game().runHeadless(10);

        castHisDash(room);
        room.game().runHeadless(STUCK + 120);

        assertNull(room.hero().findModule(ItemErrand.class), "the errand is still on him");
        assertEquals("", bag.noteAt(room.game().getLogic().getFrame()),
                "he spoke of a chest he was not after any more");
        assertEquals(BLADE, chest.findModule(GroundItem.class).getHolding());
    }

    /** The Rogue's E: a dash, cast as the player's key casts it. */
    private static void castHisDash(Room room) {
        var book = room.hero().findModule(uz.dukeengine.dungeon.skill.SkillBook.class);
        assertTrue(book.cast('E', 1), "the dash did not go off, so this tells nothing");
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
