package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.action.ActionOrders;
import uz.dukeengine.client3d.KeyMap;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Sprinting;
import uz.dukeengine.game.DukeGame;

/**
 * The hero played from his shoulder (PLAN-dungeon-action.md): the mouse turns the camera, W A S D run him, Shift
 * sprints, Space rolls, E works the nearest thing, 1-4 drink, C L M K open the screens, T calls the horse. Everything
 * he does reaches the simulation as an order of the game's own, so a party hears it.
 */
class ControlsTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    private static GameObject hero(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals(SHIPPED.run().defaultHero())).findFirst()
                .orElseThrow();
    }

    /** The keys a hero on foot claims: his own, and none of an RTS's or a skill's. */
    @Test
    void theKeysAreTheOnesOfAManOnFoot() {
        var claimed = Main.controls(SHIPPED).claimedKeys();

        for (char key : "E 1234TJG".toCharArray()) {
            if (key != 'G' || !SHIPPED.food().buildKey().isBlank()) {
                assertTrue(claimed.contains(key), key + " is the game's");
            }
        }
        for (char free : "WASDQRFHXZ".toCharArray()) {
            assertFalse(claimed.contains(free), free + " is a step, or nothing: no order and no skill is on it");
        }
    }

    /** The client's own controls are cut down to Escape, pause, the full screen, Alt and Shift. */
    @Test
    void theClientsOwnKeysAreCutDownToWhatAManOnFootKeeps() {
        var keys = Main.onFootKeys();

        assertEquals(2, keys.keysOf(KeyMap.Control.SPRINT).size(), "either Shift");
        assertEquals(2, keys.keysOf(KeyMap.Control.FREE_POINTER).size(), "either Alt");
        assertEquals(1, keys.keysOf(KeyMap.Control.OPTIONS).size());
        for (var gone : List.of(KeyMap.Control.PAN_UP, KeyMap.Control.PAN_LEFT, KeyMap.Control.PAN_DOWN,
                KeyMap.Control.PAN_RIGHT, KeyMap.Control.STOP, KeyMap.Control.CENTRE_ON_BASE,
                KeyMap.Control.ADD_TO_SELECTION, KeyMap.Control.SELECT_ALL, KeyMap.Control.TURN_LEFT)) {
            assertTrue(keys.keysOf(gone).isEmpty(), gone + " is an RTS's");
        }
    }

    /** The prompt over the nearest thing is in the game's words, for every order a click gives a word to. */
    @Test
    void theInteractPromptNamesEveryContextOrder() {
        var controls = SHIPPED.controls();

        assertEquals("[E] Gaplashish", controls.promptOf(PartyOrders.TALK, 'E'));
        for (var order : uz.dukeengine.dungeon.food.Food.WORDS) {
            assertNotNull(controls.promptOf(order, 'E'), order);
        }
        for (var order : List.of(PartyOrders.PICK_UP, PartyOrders.TO_THE_GATE)) {
            assertNotNull(controls.promptOf(order, 'E'), order);
        }
        assertEquals("[E] Minish", controls.promptOf(uz.dukeengine.dungeon.mount.Mount.RIDE, 'E'), "E on his horse");
        assertNull(controls.promptOf("Nothing", 'E'));
    }

    @Test
    void anInteractKeyOnAStepOrAnotherKeysPlaceIsRefused() {
        var data = Content.data();
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("InteractKey = E", "InteractKey = W")), "W is a step");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("InteractKey = E", "InteractKey = M")), "M is the map's");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("SprintPercent = 150", "SprintPercent = 50")),
                "a sprint is faster than a walk");
    }

    /** The held keys' order moves him, and letting go stops him: through the same order a party hears. */
    @Test
    void theHeldKeysRunHim() {
        var session = Dungeon.newSession(7L, SHIPPED);
        var game = session.game();
        game.runHeadless(5);
        var start = hero(game).getPosition();

        Main.walk(game, 0);
        game.runHeadless(30);
        var run = hero(game).getPosition();
        assertTrue(run.x() - start.x() > 10f, "he ran along +x: " + start + " to " + run);

        Main.walk(game, uz.dukeengine.client3d.action.DirectionKeys.NONE);
        game.runHeadless(5);
        var stopped = hero(game).getPosition();
        game.runHeadless(20);
        assertEquals(0f, hero(game).getPosition().distance(stopped), 0.01f, "and let go, he stands");
    }

    /** Shift: half as fast again over the same time, along whichever way is open from where he starts. */
    @Test
    void shiftMakesHimRunFasterAndLettingGoEndsIt() {
        int open = openWay();
        float most = distanceRun(false, open);
        float sprinted = distanceRun(true, open);

        assertTrue(most > 20f, "a way to run along: " + most);
        assertEquals(1.5f, sprinted / most, 0.1f, "the Controls block's SprintPercent");
    }

    /** The way (256ths of a turn) most open from where he starts: the longest of four runs. */
    private static int openWay() {
        int open = 0;
        float most = 0f;
        for (int angle = 0; angle < 256; angle += 64) {
            float ran = distanceRun(false, angle);
            if (ran > most) {
                most = ran;
                open = angle;
            }
        }
        return open;
    }

    private static float distanceRun(boolean sprint, int angle) {
        var session = Dungeon.newSession(7L, SHIPPED);
        var game = session.game();
        game.runHeadless(5);
        var start = hero(game).getPosition();
        if (sprint) {
            game.postCommand(PartyOrders.of(new Sprinting(game.getLocalPlayerIndex(), true)));
        }
        Main.walk(game, angle);
        game.runHeadless(30);
        return hero(game).getPosition().distance(start);
    }


    @Test
    void sprintingTravelsAsAnOrder() {
        var order = PartyOrders.of(new Sprinting(2, true));
        assertEquals(new Sprinting(2, true), PartyOrders.commandOf(order));
        assertEquals(new Sprinting(2, false), PartyOrders.commandOf(PartyOrders.of(new Sprinting(2, false))));
    }

    /** Space: the roll of his Dodge skill, the way he runs, out of harm's way for its first frames. */
    @Test
    void spaceRollsHimTheWayHeRuns() {
        var session = Dungeon.newSession(7L, SHIPPED);
        var game = session.game();
        game.runHeadless(5);
        var start = hero(game).getPosition();

        Main.roll(game, openWay(), SHIPPED);
        game.runHeadless(24); // heard, then the roll's eighteen frames

        assertEquals(16f, hero(game).getPosition().distance(start), 2.5f, "sixteen along the way he ran");
    }

    /**
     * The roll is long enough to be one and is out of harm's way for most of it, not all: an eighteenth frame of it
     * (0.6s) he is up and a blow takes him again. Its clip, 1.43s long, is played once through in the roll's frames.
     */
    @Test
    void theRollLastsLongEnoughToBeOneAndHisClipIsFittedToIt() {
        var skill = SHIPPED.bookFor("Knight").stream()
                .filter(one -> one.effect() == uz.dukeengine.rpg.skill.SkillEffect.MODULE).findFirst().orElseThrow();
        assertTrue(skill.durationFrames() >= 15 && skill.durationFrames() <= 24, "a roll of 0.5 to 0.8s");
        assertTrue(skill.tickFrames() >= skill.durationFrames() / 2 && skill.tickFrames() < skill.durationFrames(),
                "out of harm's way for more than half of it and not the last of it");
        assertEquals(skill.durationFrames() / (float) uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND,
                Main.looks(SHIPPED).of("Knight").busyFitOf(skill.castAnim()), 1e-5f,
                "his Dodge clip is played over the roll's frames");

        var session = Dungeon.newSession(7L, SHIPPED);
        session.run().startWith(session.game(), "Knight"); // the file's default hero is the archer
        var game = session.game();
        game.runHeadless(10);
        var knight = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Knight")).findFirst().orElseThrow();
        Main.roll(game, 0, SHIPPED);
        game.runHeadless(6);
        assertTrue(knight.hasStatus(uz.dukeengine.core.thing.ObjectStatus.INVULNERABLE), "out of harm's way rolling");
        assertEquals("Dodge", knight.getBusyClip());
        game.runHeadless(skill.durationFrames());
        assertFalse(knight.hasStatus(uz.dukeengine.core.thing.ObjectStatus.INVULNERABLE), "and not for ever");
        assertNull(knight.getBusyClip(), "the clip is let go with the roll");
    }

    /** The camera behind his shoulder comes from the file: how far back, how high, how far it may tilt. */
    @Test
    void theCameraFramesHimFromTheControlsBlock() {
        var look = Main.shoulder(SHIPPED.controls());

        assertEquals(24f, look.distance(), "3.6 m back of a 12-unit, 1.8 m man");
        assertEquals(10f, look.lookHeight(), "looking at his upper chest");
        assertTrue(look.side() > 0f && look.side() < 6f, "over his right shoulder");
        assertTrue(look.pitchMin() < 0f && look.pitchMax() < Math.PI / 2, "up at him from below, never straight overhead");
        assertEquals(Math.toRadians(14), look.pitchStart(), 1e-6);
        assertTrue(look.blockerHeight() >= 12f, "a wall pulls it in; a barrel does not");
    }

    @Test
    void aCameraThatCannotStandIsRefused() {
        var data = Content.data();
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("CameraPitchMax = 70", "CameraPitchMax = 95")), "overhead and past");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("CameraPitchMin = -12", "CameraPitchMin = 80")),
                "its least above its most");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("CameraDistance = 24", "CameraDistance = 0")), "inside his head");
    }

    /** The hero fights what he is told to and nothing else: no sight of a monster makes his weapon pick it. */
    @Test
    void theHeroPicksNoFightOfHisOwnAndSwingsWhenTold() {
        var world = Dungeon.world("##########\n#........#\n#........#\n#........#\n##########\n", SHIPPED);
        var game = world.game();
        game.spawn("Knight", world.hero(), 30f, 20f);
        game.spawn("Skeleton", world.dungeon(), 38f, 20f);
        game.runHeadless(1);
        var knight = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Knight")).findFirst().orElseThrow();
        var skeleton = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Skeleton")).findFirst().orElseThrow();
        float before = skeleton.getBody().getHealth();

        game.runHeadless(120);
        assertEquals(before, skeleton.getBody().getHealth(), 0.01f, "the skeleton in reach is untouched until he swings");

        game.postCommand(ActionOrders.toward(knight.getPlayerIndex(), List.of(knight.getId()),
                skeleton.getPosition(), false));
        game.runHeadless(120);
        assertTrue(skeleton.getBody().getHealth() < before, "one swing, told, and it is hurt");
    }

    /** The autosave comes when it comes: with W held, or a swing under way, it still writes. */
    @Test
    void aSaveWhileHeRunsOrSwingsStillWrites() {
        var session = Dungeon.newSession(7L, SHIPPED);
        var game = session.game();
        game.runHeadless(5);

        Main.walk(game, openWay());
        game.postCommand(ActionOrders.toward(game.getLocalPlayerIndex(), List.of(hero(game).getId()),
                hero(game).getPosition(), true));
        game.runHeadless(10);

        assertNotNull(session.run().saveNow(), "the run is written as it stands");
    }
}
