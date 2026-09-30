package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import uz.dukeengine.dungeon.loot.UseItem;
import uz.dukeengine.dungeon.party.ChooseHero;
import uz.dukeengine.dungeon.party.PartyMatch;
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

    /** The shipped game but for one word: its key is told to lie as a thing no file has made. */
    private static DungeonSettings keyLyingAsNothing() {
        var data = Content.data();
        var shipped = "  LiesAs = Key\n";
        assertTrue(data.contains(shipped), "the shipped key no longer says how it lies");
        return DungeonSettings.parse(data.replace(shipped, "  LiesAs = NoSuchThing\n"));
    }

    /** What lies on the floor, each thing as what it holds: a chest's item, the key on the ground. */
    private static List<Loot> lying(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .map(object -> object.findModule(GroundItem.class))
                .filter(thing -> thing != null && thing.getHolding() != null)
                .map(GroundItem::getHolding)
                .toList();
    }

    private static long keysLying(DukeGame game) {
        return lying(game).stream().filter(item -> item.kind() == LootKind.KEY).count();
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
     * The last two of them falling in one frame — one splash — leave one key between them, not one each: a count of
     * the deaths reaches all of them once, where a count of those still standing would find none left at either.
     */
    @Test
    void theLastTwoFallingInOneFrameLeaveOneKey() {
        var session = opened(SETTINGS);
        var game = session.game();
        var own = outside(session);
        for (var monster : own.subList(0, own.size() - 2)) {
            game.getLogic().destroyObject(monster);
            game.runHeadless(1);
        }
        assertEquals(0, keysLying(game), "a key while two of them stand");

        game.getLogic().destroyObject(own.get(own.size() - 2));
        game.getLogic().destroyObject(own.getLast());
        game.runHeadless(1);

        assertEquals(1, keysLying(game), "one key for the last two");
        assertEquals(own.size(), session.run().getMission().killed());
        assertEquals(Mission.Step.TAKE, session.run().getStep());
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

    /** A fallen hero leaves the key and nothing else: the rest of what he carries is his, for when he stands again. */
    @Test
    void aHeroWhoFallsKeepsWhatElseHeCarries() {
        var session = opened(SETTINGS);
        var game = session.game();
        var bag = session.progress().getLoot();
        var other = SETTINGS.loot().stream().filter(item -> item.kind() != LootKind.KEY).findFirst().orElseThrow();
        bag.take(other, 0, 0);
        bag.take(key(), 0, 0);

        game.getLogic().destroyObject(find(game, "Rogue"));
        game.runHeadless(1);

        assertEquals(List.of(other), bag.getFound(), "the rest of it is still his");
        assertEquals(List.of(key()), lying(game), "and only the key lies where he fell");
    }

    /** A key that cannot be laid is not lost with the hero who carries it: it stays in his bag, for when he stands. */
    @Test
    void aKeyThatCannotBeLaidStaysInTheBagOfTheHeroWhoFell() {
        var settings = keyLyingAsNothing();
        var session = opened(settings);
        var game = session.game();
        var bag = session.progress().getLoot();
        bag.take(settings.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow(), 0, 0);

        game.getLogic().destroyObject(find(game, "Rogue"));
        game.runHeadless(1);

        assertTrue(bag.holds(LootKind.KEY), "it left his bag and lies nowhere");
        assertEquals(0, keysLying(game));
    }

    /**
     * A floor whose key cannot be laid says so when its last monster falls, naming the template, rather than goes on
     * to a key that lies nowhere and a gate that never opens.
     */
    @Test
    void aKeyThatCannotBeLaidIsSaidLoudlyWhenTheLastOfThemFalls() {
        var session = opened(keyLyingAsNothing());
        var game = session.game();
        var own = outside(session);
        for (var monster : own.subList(0, own.size() - 1)) {
            game.getLogic().destroyObject(monster);
            game.runHeadless(1);
        }
        game.getLogic().destroyObject(own.getLast());

        var thrown = assertThrows(IllegalStateException.class, () -> game.runHeadless(1));
        assertTrue(thrown.getMessage().contains("NoSuchThing"), "it names the template: " + thrown.getMessage());
    }

    // ---- a key belongs to the floor it was found on ----

    /**
     * The hero of the first floor with a key in his bag when its boss falls: the floor closes, the second is laid, and
     * the bag is what he carries into it.
     */
    private static Dungeon.Session downWithAKey() {
        var session = opened(SETTINGS);
        var game = session.game();
        session.progress().getLoot().take(key(), 0, 0);

        game.getLogic().destroyObject(find(game, SETTINGS.bossKindAt(1)));
        game.runHeadless(SETTINGS.run().descendDelayFrames() + 4);

        assertEquals(2, session.run().getDepth(), "the premise: he went down a floor with a key in his bag");
        return session;
    }

    /** A key carried past its floor is left behind: the next floor is laid, and the bag he brings to it holds none. */
    @Test
    void aKeyDoesNotOutliveItsFloorInTheBagOfAHeroWhoCarriesItDown() {
        var session = downWithAKey();

        assertFalse(session.progress().getLoot().holds(LootKind.KEY), "he took the key down with him");
    }

    /**
     * The floor below says its own step: the count of what stands outside its keep and, the last of them fallen and
     * the floor's own key lying where it fell, "take it" — not "give it" of a key that was never this floor's.
     */
    @Test
    void theFloorBelowCountsItsOwnOutsideWhateverWasCarriedDown() {
        var session = downWithAKey();
        var game = session.game();
        var run = session.run();

        assertEquals(Mission.Step.CLEAR, run.getStep());
        assertTrue(run.getMission().outside() > 0, "the premise: the floor puts somebody outside its keep");
        assertEquals(SETTINGS.run().clearWord(0, run.getMission().outside()), run.getTracker(),
                "its own count of its own outside");

        var own = game.getLogic().getObjects().stream()
                .filter(object -> object.findModule(Mission.Counted.class) != null).toList();
        assertEquals(run.getMission().outside(), own.size(), "the premise: all of them stand, and all are counted");
        own.forEach(monster -> game.getLogic().destroyObject(monster));
        game.runHeadless(1);

        assertEquals(1, keysLying(game), "the floor's own key lies where the last of them fell");
        assertEquals(Mission.Step.TAKE, run.getStep(), "and it is not in anybody's bag yet");
        assertEquals(SETTINGS.run().takeKeyWord(), run.getTracker());
    }

    /** A key from the floor above opens nothing on this one: sent to the gate from its slot, he has none to give. */
    @Test
    void theFloorBelowKeepsItsGateShutToAKeyFromAbove() {
        var session = downWithAKey();
        var game = session.game();
        var gate = find(game, "Gate");
        find(game, "Rogue").setPosition(gate.getPosition());

        game.postCommand(PartyOrders.of(new UseItem(game.getLocalPlayerIndex(), 0, gate.getId())));
        game.runHeadless(5);

        assertNotNull(find(game, "Gate"), "the key from the floor above opened this floor's gate");
        assertNull(find(game, "OpenGate"));
    }

    /** Only the key is left behind: the rest of what he carries goes down with him, whatever sits either side of it. */
    @Test
    void theRestOfWhatHeCarriesGoesDownWithHimWhenTheKeyIsLeftBehind() {
        var session = opened(SETTINGS);
        var game = session.game();
        var bag = session.progress().getLoot();
        var things = SETTINGS.loot().stream().filter(item -> item.kind() != LootKind.KEY).limit(2).toList();
        bag.take(things.get(0), 0, 0);
        bag.take(key(), 0, 0);
        bag.take(things.get(1), 0, 0);

        game.getLogic().destroyObject(find(game, SETTINGS.bossKindAt(1)));
        game.runHeadless(SETTINGS.run().descendDelayFrames() + 4);

        assertEquals(2, session.run().getDepth(), "the premise: he went down a floor");
        assertEquals(things, bag.getFound(), "all of it but the key");
    }

    /** It is every seat's bag that is emptied of keys, not the first player's alone: a party carries one key each. */
    @Test
    void everyHeroOfAPartyLeavesHisKeyBehind() {
        var session = Dungeon.newPartySession(PartyMatch.endless(4242L), 2, null, SETTINGS, "Rogue");
        var game = session.game();
        game.runHeadless(1);
        game.postCommand(PartyOrders.of(new ChooseHero(2, "Knight")));
        game.runHeadless(3);
        session.run().progressOf(1).getLoot().take(key(), 0, 0);
        session.run().progressOf(2).getLoot().take(key(), 0, 0);

        game.getLogic().destroyObject(find(game, SETTINGS.bossKindAt(1)));
        game.runHeadless(SETTINGS.run().descendDelayFrames() + 4);

        assertEquals(2, session.run().getDepth(), "the premise: the party went down a floor, a key each");
        assertFalse(session.run().progressOf(1).getLoot().holds(LootKind.KEY), "the first hero's");
        assertFalse(session.run().progressOf(2).getLoot().holds(LootKind.KEY), "and the second's");
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
