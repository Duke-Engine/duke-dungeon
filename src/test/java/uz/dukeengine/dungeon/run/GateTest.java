package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.loot.ItemErrand;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootBag;
import uz.dukeengine.dungeon.loot.LootKind;
import uz.dukeengine.dungeon.loot.UseItem;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.game.DukeGame;

/**
 * The keep's gate: across its doorway nothing passes, a hero walking up to it leaves it shut, only its open() opens it,
 * and the floor turns it true.
 */
class GateTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Where the gate stands: the middle of a doorway three cells wide, in a wall down the middle of a hall. */
    private static final Coord3D DOORWAY = new Coord3D(205f, 155f, 0f);

    /** A hall forty by thirty with a wall down x = 20, open at y 14 to 16. */
    private static String hall() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean border = x == 0 || y == 0 || x == 39 || y == 29;
                boolean wall = x == 20 && (y < 14 || y > 16);
                text.append(border || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /**
     * The hall with the gate in its doorway, turned along the wall — which runs down the map's y. A frame first:
     * before the world starts, a spawn is only booked for its first frame.
     */
    private static Dungeon.Arena withAGate() {
        var arena = Dungeon.world(hall(), SETTINGS);
        arena.game().spawn("Gate", arena.dungeon(), DOORWAY.x(), DOORWAY.y());
        arena.game().runHeadless(1);
        find(arena.game(), "Gate").setOrientation((float) (StrictMath.PI / 2));
        return arena;
    }

    private static boolean shut(DukeGame game, float x, float y) {
        var grid = game.getLogic().getPathGrid();
        var at = new Coord3D(x, y, 0f);
        return grid.isBlocked(grid.toCellX(at), grid.toCellY(at));
    }

    /** What an errand here shares: the shipped reach, and a note that lasts, so what he says can be read. */
    private static ItemErrand.Rules rules(Dungeon.Arena arena) {
        return rules(arena, SETTINGS.lootDrops().pickupRange());
    }

    /** The same with a reach of its own, for a test that must not turn on how far the shipped one happens to reach. */
    private static ItemErrand.Rules rules(Dungeon.Arena arena, float reach) {
        var drops = SETTINGS.lootDrops();
        return new ItemErrand.Rules(reach, 100_000, drops.template(), arena.dungeon().getIndex(), drops.fullWord(),
                drops.noUseWord(), drops.noWayWord(), drops.stuckFrames());
    }

    private static Loot key() {
        return SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
    }

    @Test
    void itShutsTheWholeDoorway() {
        var arena = withAGate();
        arena.game().runHeadless(2);

        for (float y : new float[] {145f, 155f, 165f}) {
            assertTrue(shut(arena.game(), DOORWAY.x(), y), "the doorway is open at y " + y);
        }
    }

    @Test
    void theDungeonsOwnDoNotOpenIt() {
        var arena = withAGate();
        arena.game().spawn("Skeleton", arena.dungeon(), 185f, 155f);
        arena.game().runHeadless(60);

        assertNotNull(find(arena.game(), "Gate"), "it opened for a skeleton");
    }

    /** A hero walking up to it, and standing at it, leaves it shut: only the key opens it now. */
    @Test
    void aHeroWalkingUpToItLeavesItShut() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        hero.getLocomotor().moveTo(new Coord3D(300f, 155f, 0f));
        game.runHeadless(150);

        assertNotNull(find(game, "Gate"), "it opened for him");
        assertNull(find(game, "OpenGate"), "and something stands where it stood");
        assertTrue(shut(game, DOORWAY.x(), DOORWAY.y()), "the doorway is open");
        assertTrue(hero.getPosition().x() > DOORWAY.x() - 25f,
                "he came up to it, within the reach it used to open at: " + hero.getPosition());
        assertTrue(hero.getPosition().x() < DOORWAY.x(), "and he is still on his side of it: " + hero.getPosition());
    }

    /** Sent up to it without the key, he walks there and says he has to find one — and it stays shut. */
    @Test
    void sentUpToItWithoutTheKeyHeSaysHeMustFindIt() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var gate = find(game, "Gate");
        var bag = new LootBag();

        assertTrue(ItemErrand.toTheGate(hero, gate, bag, rules(arena)));
        game.runHeadless(150);

        assertEquals("Boss xonasi uchun kalit topishim kerak", bag.noteAt(game.getLogic().getFrame()));
        assertTrue(hero.getPosition().x() > 180f, "he said it from where he stood: " + hero.getPosition());
        assertNotNull(find(game, "Gate"), "and it is still shut");
    }

    /** With the key in his bag he says he has to give it to the gate — which still does not open by itself. */
    @Test
    void sentUpToItWithTheKeyHeSaysHeMustGiveItToTheGate() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var bag = new LootBag();
        bag.take(key(), 0, 0);

        assertTrue(ItemErrand.toTheGate(find(game, "Rogue"), find(game, "Gate"), bag, rules(arena)));
        game.runHeadless(150);

        assertEquals("Kalit menda — uni darvozaga berishim kerak", bag.noteAt(game.getLogic().getFrame()));
        assertNotNull(find(game, "Gate"), "it opened without being given the key");
        assertTrue(bag.holds(LootKind.KEY), "and the key is still his");
    }

    /**
     * A walk his brain takes up again — once a body in the way has let him by, see HeroBrain.mindTheWayOnHisErrand —
     * is a walk to the place, and ends on the free block beside the gate: where it was sent it could not go, and it
     * is not short of anything either. That block is outside the reach, and he says it from there all the same.
     *
     * <p>Stands in for that resume: the test moves his legs itself, as the brain would, rather than wait for a body
     * to stand in his way; and it has a reach of its own, so the block is outside it whatever the shipped one is.
     */
    @Test
    void sentUpToItAWalkTakenUpAgainEndsBesideItAndHeSaysItThere() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var bag = new LootBag();
        float reach = 12f;

        assertTrue(ItemErrand.toTheGate(hero, find(game, "Gate"), bag, rules(arena, reach)));
        game.runHeadless(5);
        hero.getLocomotor().moveTo(DOORWAY);
        game.runHeadless(150);

        float dx = hero.getPosition().x() - DOORWAY.x();
        float dy = hero.getPosition().y() - DOORWAY.y();
        assertTrue(Math.sqrt(dx * dx + dy * dy) > reach,
                "he is beyond the reach, or this is not the walk that ends there: " + hero.getPosition());
        assertEquals("Boss xonasi uchun kalit topishim kerak", bag.noteAt(game.getLogic().getFrame()));
        assertNotNull(find(game, "Gate"), "and it is still shut");
    }

    /**
     * A hero his brain has stopped on the way — for a body in the doorway, say — is not there yet, however near the
     * gate he stands: he says it when he gets there, once the brain takes him on again.
     */
    @Test
    void sentUpToItAHeroStoppedOnTheWayIsNotThereYet() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var gate = find(game, "Gate");
        var bag = new LootBag();
        float reach = SETTINGS.lootDrops().pickupRange();

        assertTrue(ItemErrand.toTheGate(hero, gate, bag, rules(arena)));
        game.runHeadless(30);
        hero.getLocomotor().stop();
        game.runHeadless(30);

        float dx = hero.getPosition().x() - DOORWAY.x();
        float dy = hero.getPosition().y() - DOORWAY.y();
        float apart = (float) Math.sqrt(dx * dx + dy * dy);
        assertTrue(apart > reach && apart <= reach + gate.getGeometry().footprintRadius(),
                "he is stopped where a hero at its edge would be, or this tells nothing: " + hero.getPosition());
        assertEquals("", bag.noteAt(game.getLogic().getFrame()), "he said it from where he was stopped");

        hero.getLocomotor().moveTo(DOORWAY);
        game.runHeadless(150);

        assertEquals("Boss xonasi uchun kalit topishim kerak", bag.noteAt(game.getLogic().getFrame()));
    }

    /** Given the key, the gate opens — the owner's swing — and the key is gone from his bag. */
    @Test
    void usingTheKeyOnItOpensItAndTheKeyIsGone() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var bag = new LootBag();
        bag.take(key(), 0, 0);

        assertTrue(ItemErrand.use(find(game, "Rogue"), 0, find(game, "Gate"), bag, rules(arena)));
        game.runHeadless(150);

        assertNull(find(game, "Gate"), "the key did not open it");
        assertNotNull(find(game, "OpenGate"), "and nothing stands where it stood");
        assertNull(bag.at(0), "the key stayed in his bag");
        assertFalse(bag.holds(LootKind.KEY));
    }

    /** Used on anything else it does nothing: he says so, and keeps it. */
    @Test
    void usingTheKeyOnAnythingElseKeepsItAndSaysSo() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.spawn("Pillar", arena.dungeon(), 120f, 100f);
        game.runHeadless(2);
        var bag = new LootBag();
        bag.take(key(), 0, 0);

        assertTrue(ItemErrand.use(find(game, "Rogue"), 0, find(game, "Pillar"), bag, rules(arena)));
        game.runHeadless(150);

        assertEquals(key(), bag.at(0), "he gave it to a pillar");
        assertEquals("Bu kalit faqat boss darvozasini ochadi", bag.noteAt(game.getLogic().getFrame()));
        assertNotNull(find(game, "Gate"), "and the gate is as it was");
    }

    /**
     * He arrives as a hero sent up to look does: a walk his brain takes up again ends on the free block beside the
     * gate, outside the reach, and he gives it the key from there all the same. Stands in for that resume as
     * sentUpToItAWalkTakenUpAgainEndsBesideItAndHeSaysItThere does, with a reach of its own.
     */
    @Test
    void usingTheKeyAWalkTakenUpAgainEndsBesideItAndItOpensFromThere() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var bag = new LootBag();
        bag.take(key(), 0, 0);
        float reach = 12f;

        assertTrue(ItemErrand.use(hero, 0, find(game, "Gate"), bag, rules(arena, reach)));
        game.runHeadless(5);
        hero.getLocomotor().moveTo(DOORWAY);
        game.runHeadless(150);

        float dx = hero.getPosition().x() - DOORWAY.x();
        float dy = hero.getPosition().y() - DOORWAY.y();
        assertTrue(Math.sqrt(dx * dx + dy * dy) > reach,
                "he is beyond the reach, or this is not the walk that ends there: " + hero.getPosition());
        assertNull(find(game, "Gate"), "it stayed shut: he was not counted as there");
        assertFalse(bag.holds(LootKind.KEY), "and the key is still his");
    }

    /** He takes it there: a moment after he is sent, from across the hall, it is still his and the gate still shut. */
    @Test
    void theKeyIsUsedWhereHeTakesItAndNotFromAcrossTheHall() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var bag = new LootBag();
        bag.take(key(), 0, 0);

        assertTrue(ItemErrand.use(find(game, "Rogue"), 0, find(game, "Gate"), bag, rules(arena)));
        game.runHeadless(10);

        assertNotNull(find(game, "Gate"), "it opened from across the hall");
        assertTrue(bag.holds(LootKind.KEY), "and the key is his still");
        game.runHeadless(150);
        assertNull(find(game, "Gate"), "and once he is there it opens");
    }

    /**
     * The whole road on a real floor, with the game's own rules: the order the bag's aim sends is obeyed, the key goes
     * to the gate, the gate opens and the key is gone from his bag. Placed at the gate rather than walked there, since
     * this is about the order and not the pathfinder.
     */
    @Test
    void theOrderGivesTheFloorsGateTheKeyFromHisBagAndItOpens() {
        var session = Dungeon.newSession(21L, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var bag = session.progress().getLoot();
        var gauntlet = SETTINGS.loot().stream().filter(item -> item.id().equals("Gauntlet")).findFirst().orElseThrow();
        bag.take(gauntlet, 0, 0);
        bag.take(key(), 0, 0); // the slot after it, so it is the slot the order names that counts
        var gate = find(game, "Gate");
        find(game, "Rogue").setPosition(gate.getPosition());

        game.postCommand(PartyOrders.of(new UseItem(game.getLocalPlayerIndex(), 1, gate.getId())));
        game.runHeadless(5);

        assertNull(find(game, "Gate"), "the key did not open it");
        assertNotNull(find(game, "OpenGate"), "and nothing stands where it stood");
        assertFalse(bag.holds(LootKind.KEY), "the key stayed in his bag");
        assertEquals(gauntlet, bag.at(0), "and what he carried beside it went with it");
    }

    /**
     * And with the game's own words: the key sent to anything but the gate — himself, for a thing that is there to be
     * sent to — stays in his bag, and what he says is what the file says.
     */
    @Test
    void theOrderOnAnythingElseKeepsTheKeyAndHeSaysTheFilesWord() {
        var session = Dungeon.newSession(21L, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var bag = session.progress().getLoot();
        bag.take(key(), 0, 0);

        game.postCommand(PartyOrders.of(new UseItem(game.getLocalPlayerIndex(), 0, find(game, "Rogue").getId())));
        game.runHeadless(5);

        assertEquals(SETTINGS.lootDrops().noUseWord(), bag.noteAt(game.getLogic().getFrame()));
        assertEquals(key(), bag.at(0), "he kept it");
        assertNotNull(find(game, "Gate"), "and the gate is as it was");
    }

    /** Opened, the same gate stands open where it stood, turned as it was, and he walks on through. */
    @Test
    void openedItStandsOpenWhereItStoodAndHeWalksOnThrough() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");

        find(game, "Gate").findModule(GateUpdate.class).open();
        game.runHeadless(2);

        assertNull(find(game, "Gate"), "it stayed shut");
        var open = find(game, "OpenGate");
        assertNotNull(open, "nothing stands where it stood");
        assertEquals(DOORWAY.x(), open.getPosition().x(), 0.01f);
        assertEquals(DOORWAY.y(), open.getPosition().y(), 0.01f);
        assertEquals((float) (StrictMath.PI / 2), open.getOrientation(), 1e-6f, "turned as the gate was");
        assertFalse(shut(game, DOORWAY.x(), DOORWAY.y()), "and the doorway open, the open gate in nobody's way");
        hero.getLocomotor().moveTo(new Coord3D(300f, 155f, 0f));
        game.runHeadless(250);
        assertTrue(hero.getPosition().x() > 260f, "and he walked on through: " + hero.getPosition());
    }

    /**
     * Opened twice in a frame, it opens once: marked destroyed, it stays in the world until the frame's reap, so two
     * hands can reach it, and only one open gate may stand in its place.
     */
    @Test
    void openedTwiceItStandsOpenOnce() {
        var arena = withAGate();
        var game = arena.game();
        var gate = find(game, "Gate").findModule(GateUpdate.class);

        gate.open();
        gate.open();
        game.runHeadless(2);

        var open = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("OpenGate")).count();
        assertEquals(1, open, "a second opening stood a second open gate in the first");
    }

    /** On a floor of the descent, it stands across its keep's doorway, every cell of it shut. */
    @Test
    void theFloorTurnsItAcrossItsDoorway() {
        for (long seed : new long[] {3L, 11L, 42L}) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 1);
            var game = Dungeon.newSession(seed, SETTINGS).game();
            game.runHeadless(2);

            var gate = find(game, "Gate");
            assertNotNull(gate, "seed " + seed + ": the keep has no gate");
            var keep = floor.keep();
            assertEquals(keep.facing(), gate.getOrientation(), 1e-6f, "seed " + seed);
            var walls = keep.walls();
            for (int y = walls.y(); y < walls.y() + walls.h(); y++) {
                for (int x = walls.x(); x < walls.x() + walls.w(); x++) {
                    if (keep.isDoorway(x, y)) {
                        assertTrue(shut(game, x * 10f + 5f, y * 10f + 5f), "seed " + seed + ": open at " + x + "," + y);
                    }
                }
            }
        }
    }
}
