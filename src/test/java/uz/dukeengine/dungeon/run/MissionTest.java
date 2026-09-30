package uz.dukeengine.dungeon.run;

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
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.Layout;
import uz.dukeengine.dungeon.loot.GroundItem;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootKind;
import uz.dukeengine.dungeon.loot.PickUp;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.stage.Stage;
import uz.dukeengine.game.DukeGame;

/**
 * A floor's mission, as the run keeps it: every monster the floor put outside the keep killed, the key the last of
 * them leaves taken, given to the gate, and the boss — each step in order, said by the tracker.
 */
class MissionTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final long SEED = 21L;

    private static Dungeon.Session opened(DungeonSettings settings) {
        var session = Dungeon.newSession(SEED, settings);
        session.game().runHeadless(2);
        return session;
    }

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static Loot key() {
        return SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
    }

    private static int cellOf(float at) {
        return (int) Math.floor(at / 10f);
    }

    /** Everything of the dungeon's with a body that stands outside the keep: the floor's own, and nothing it raised. */
    private static List<GameObject> outside(Dungeon.Session session) {
        var game = session.game();
        var keep = DungeonGenerator.generate(SEED, SETTINGS, 1).keep();
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() != game.getLocalPlayerIndex() && object.getBody() != null)
                .filter(object -> !keep.holds(cellOf(object.getPosition().x()), cellOf(object.getPosition().y())))
                .toList();
    }

    /** The shipped descent with no monster in any chamber: only the boss and its guard, in the keep. */
    private static DungeonSettings nobodyOutside() {
        var data = Content.data();
        var shipped = "    MinSkeletonsPerRoom = 2\n    MaxSkeletonsPerRoom = 6\n";
        assertTrue(data.contains(shipped), "the shipped map no longer fills its chambers this way");
        return DungeonSettings.parse(data.replace(shipped,
                "    MinSkeletonsPerRoom = 0\n    MaxSkeletonsPerRoom = 0\n"));
    }

    @Test
    void itCountsTheMonstersTheFloorPutOutsideTheKeep() {
        var floor = DungeonGenerator.generate(SEED, SETTINGS, 1);
        var keep = floor.keep();
        int placed = (int) floor.monsters().stream()
                .filter(monster -> !keep.holds(monster.at().cellX(), monster.at().cellY())).count();
        var session = opened(SETTINGS);
        var mission = session.run().getMission();

        assertTrue(placed > 0, "a floor with nobody outside proves nothing here");
        assertEquals(placed, mission.outside(), "the floor's own outside the keep, the guard not among them");
        assertEquals(SETTINGS.run().clearWord(0, placed), session.run().getTracker());

        session.game().getLogic().destroyObject(outside(session).getFirst());
        session.game().runHeadless(1);
        assertEquals(1, mission.killed());
        assertEquals(SETTINGS.run().clearWord(1, placed), session.run().getTracker(), "killed, of all");
    }

    @Test
    void theLastOfThemLeavesTheKeyWhereItFell() {
        var session = opened(SETTINGS);
        var game = session.game();
        Coord3D last = null;
        for (var monster : outside(session)) {
            assertNull(find(game, "Key"), "a key while one of them stands");
            last = monster.getPosition();
            game.getLogic().destroyObject(monster);
            game.runHeadless(1);
        }

        var key = find(game, "Key");
        assertNotNull(key, "the last of them left no key");
        assertTrue(key.getPosition().distance(last) < 2f, "where it fell: " + key.getPosition() + ", not " + last);
        assertEquals(key(), key.findModule(GroundItem.class).getHolding(), "lying as a chest lies, holding the key");
    }

    /**
     * What a summoner calls up was never the floor's own: its death is not one the mission counts, so it is not the
     * last of them; and the floor's own last leaves the key though something raised still stands.
     */
    @Test
    void whatIsCalledUpLaterIsNeitherCountedNorWaitedFor() {
        var session = opened(SETTINGS);
        var game = session.game();
        var logic = game.getLogic();
        var mission = session.run().getMission();
        var own = outside(session);
        for (var monster : own.subList(0, own.size() - 1)) {
            logic.destroyObject(monster);
            game.runHeadless(1);
        }
        var last = own.getLast();

        // Two more of the dungeon's, raised outside the keep as a summoner's rift raises them: one dies, one stands.
        var skeleton = logic.findTemplate("Skeleton");
        var dies = logic.spawn(skeleton, last.getPosition(), last.getPlayerIndex());
        var stands = logic.spawn(skeleton, last.getPosition(), last.getPlayerIndex());
        game.runHeadless(1);
        logic.destroyObject(dies);
        game.runHeadless(1);
        assertEquals(own.size() - 1, mission.killed(), "it was not the floor's to count");
        assertNull(find(game, "Key"), "and its death is not the last of them");
        assertEquals(Mission.Step.CLEAR, session.run().getStep());

        logic.destroyObject(last);
        game.runHeadless(1);
        assertNotNull(find(game, "Key"), "the last of the floor's own leaves it, the one raised still standing");
        assertNotNull(logic.findObject(stands.getId()), "the one raised was left standing");

        logic.destroyObject(stands);
        game.runHeadless(1);
        assertEquals(own.size(), mission.killed(), "its death, too, is nobody's to count");
    }

    @Test
    void aFloorThatPutNobodyOutsideLeavesTheKeyAtTheWayInFromTheStart() {
        var session = opened(nobodyOutside());
        var game = session.game();

        assertEquals(0, session.run().getMission().outside());
        var key = find(game, "Key");
        assertNotNull(key, "no key lies anywhere");
        assertTrue(key.getPosition().distance(find(game, "Rogue").getPosition()) < 10f,
                "where the heroes came in: " + key.getPosition());
        assertEquals(Mission.Step.TAKE, session.run().getStep());
    }

    @Test
    void theStepsComeInOrderAndTheTrackerSaysEach() {
        var session = opened(SETTINGS);
        var game = session.game();
        var run = session.run();
        var words = SETTINGS.run();
        assertEquals(Mission.Step.CLEAR, run.getStep());

        for (var monster : outside(session)) {
            game.getLogic().destroyObject(monster);
            game.runHeadless(1);
        }
        assertEquals(Mission.Step.TAKE, run.getStep(), "every one of them dead, and the key lying");
        assertEquals(words.takeKeyWord(), run.getTracker());

        var key = find(game, "Key");
        find(game, "Rogue").setPosition(key.getPosition());
        game.postCommand(PartyOrders.of(new PickUp(game.getLocalPlayerIndex(), key.getId())));
        game.runHeadless(3);
        assertEquals(Mission.Step.GIVE, run.getStep(), "the key in his bag");
        assertEquals(words.giveKeyWord(), run.getTracker());

        find(game, "Gate").findModule(GateUpdate.class).open();
        game.runHeadless(2);
        assertEquals(Mission.Step.KILL, run.getStep(), "the gate open");
        assertEquals(words.killBossWord(), run.getTracker());
    }

    @Test
    void aHeroWhoFallsWithTheKeyLeavesItWhereHeFell() {
        var session = opened(SETTINGS);
        var game = session.game();
        var bag = session.progress().getLoot();
        bag.take(key(), 0, 0);
        var hero = find(game, "Rogue");
        var where = hero.getPosition();

        game.getLogic().destroyObject(hero);
        game.runHeadless(1);

        assertFalse(bag.holds(LootKind.KEY), "he took it with him");
        var key = find(game, "Key");
        assertNotNull(key, "it is nowhere");
        assertTrue(key.getPosition().distance(where) < 2f, "where he fell: " + key.getPosition());
        assertEquals(key(), key.findModule(GroundItem.class).getHolding());
    }

    /** The tracker's words are the owner's, as the spec writes them. */
    @Test
    void theTrackerSaysItInTheOwnersWords() {
        var words = SETTINGS.run();
        assertEquals("Qal'adan tashqaridagi barcha monstrlarni o'ldiring — 12/40", words.clearWord(12, 40));
        assertEquals("Kalitni oling", words.takeKeyWord());
        assertEquals("Kalitni boss darvozasiga bering", words.giveKeyWord());
        assertEquals("Bossni o'ldiring", words.killBossWord());
    }

    /** A stage is cut without a keep, and so has no mission: its tracker says only the last line. */
    @Test
    void aStageHasNoMission() {
        var floor = DungeonGenerator.generate(SEED, SETTINGS, 1,
                Layout.sized(SETTINGS, SETTINGS.mapWidth(), SETTINGS.mapHeight(), SETTINGS.maxRooms()));
        var session = Dungeon.newStageSession(new Stage("test", "Test", "", 1, 1, SEED, floor), SETTINGS);
        session.game().runHeadless(2);

        assertNull(session.run().getMission());
        assertEquals(Mission.Step.KILL, session.run().getStep());
        assertEquals(SETTINGS.run().killBossWord(), session.run().getTracker());
    }
}
