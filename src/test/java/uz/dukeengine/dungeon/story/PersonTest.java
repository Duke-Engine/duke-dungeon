package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.StageCheck;
import uz.dukeengine.dungeon.stage.StageFile;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/**
 * One person placed on a map by a name of their own (docs/plan/2026-10-21-npc-brains-and-quests.md): maps/people
 * names Walker, Sentry and Smith in its Npcs, and its people.duke says what each of them is.
 */
class PersonTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;

    static Dungeon.Session session() {
        var session = Dungeon.newStageSession(Stages.load("people", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        session.game().getLogic().setSleep(null);
        return session;
    }

    /** The thing standing nearest the middle of cell {@code (x, y)}. */
    static GameObject at(DukeGame game, int x, int y) {
        GameObject nearest = null;
        double best = Double.MAX_VALUE;
        for (var thing : game.getLogic().getObjects()) {
            double far = Math.hypot(thing.getPosition().x() - (x + 0.5f) * CELL,
                    thing.getPosition().y() - (y + 0.5f) * CELL);
            if (far < best) {
                best = far;
                nearest = thing;
            }
        }
        return nearest;
    }

    @Test
    void aPersonIsDrawnAsItsKind() {
        var game = session().game();
        var walker = at(game, 5, 5);
        assertEquals("Villager", walker.getTemplate().name(), "Walker is drawn as the Kind his block names");
        assertEquals(PlayerList.NEUTRAL_INDEX, walker.getPlayerIndex(), "on nobody's side");
        assertEquals("Blacksmith", at(game, 8, 25).getTemplate().name());
    }

    @Test
    void aPersonIsAnNpcOfItsOwnName() {
        var smith = SETTINGS.story().npc("Smith");
        assertNotNull(smith, "the story knows him by his own name");
        assertEquals("SmithHello", smith.talk());
        assertEquals("Temirchi", smith.role());
        assertEquals(Person.Brain.PATROL, SETTINGS.person("Walker").brain());
        assertEquals(java.util.List.of("PostA", "PostB"), SETTINGS.person("Walker").points());
    }

    @Test
    void aPersonWhoseKindIsNoCreatureIsRefused() {
        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(Content.data()
                + "\nPerson\n  Name = Ghost\n  Kind = Nobody\nEnd\n"));
        assertTrue(refused.getMessage().contains("Nobody"), refused.getMessage());
    }

    @Test
    void aPatrolPointThatIsNoZoneIsRefused() {
        var settings = DungeonSettings.parse(Content.data()
                + "\nPerson\n  Name = Walker\n  Kind = Villager\n  Brain = PATROL\n  Points = [PostA, Nowhere]\nEnd\n");
        var stage = StageFile.read(Stages.found("people").text(), "people");
        var problems = StageCheck.problems(stage, settings);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("Walker") && problem.contains("Nowhere")),
                problems.toString());
        assertTrue(problems.stream().noneMatch(problem -> problem.contains("PostA")), problems.toString());
    }

    @Test
    void ordinaryNpcsAreUnchanged() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        var commander = session.game().getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Commander")).findFirst().orElseThrow();
        assertEquals(PlayerList.NEUTRAL_INDEX, commander.getPlayerIndex());
        assertEquals(8.5f * CELL, commander.getPosition().x(), 0.5f);
        assertEquals(10.5f * CELL, commander.getPosition().y(), 0.5f);
    }

    // ---- brains: stand, wander, patrol ----

    /** How far, in cells, a thing stands from the middle of cell {@code (x, y)}. */
    static double cellsFrom(GameObject thing, int x, int y) {
        return Math.hypot(thing.getPosition().x() - (x + 0.5f) * CELL, thing.getPosition().y() - (y + 0.5f) * CELL)
                / CELL;
    }

    /**
     * The frame, within {@code frames}, at which {@code thing} first comes within a cell and a half of {@code (x, y)} --
     * the Sentry walks the same posts, and one standing on a post keeps the other a step off it; -1 never.
     */
    static int reaches(DukeGame game, GameObject thing, int x, int y, int frames) {
        for (int i = 0; i < frames; i++) {
            if (cellsFrom(thing, x, y) <= 1.5) {
                return i;
            }
            game.runHeadless(1);
        }
        return -1;
    }

    /** The people map played by the shipped settings with one change to people.duke. */
    static Dungeon.Session sessionWith(String was, String is) {
        var duke = Stages.found("people").extrasText();
        assertTrue(duke.contains(was), was);
        var settings = DungeonSettings.parse(Content.data() + "\n" + duke.replace(was, is));
        var session = Dungeon.newStageSession(Stages.load("people", settings), settings);
        session.game().runHeadless(1);
        session.game().getLogic().setSleep(null);
        return session;
    }

    @Test
    void aPatrollerWalksToTheNextPostWaitsAndComesBack() {
        var game = session().game();
        var walker = at(game, 5, 5);
        assertTrue(reaches(game, walker, 13, 5, 900) >= 0, "to PostB: " + cellsFrom(walker, 13, 5));
        game.runHeadless(150); // to its middle, its second there, and away again
        assertTrue(cellsFrom(walker, 13, 5) > 0.5, "it does not stay at PostB");
        assertTrue(reaches(game, walker, 5, 5, 900) >= 0, "and back to PostA: " + cellsFrom(walker, 5, 5));
    }

    @Test
    void aStanderStaysHome() {
        var game = session().game();
        var smith = at(game, 8, 25);
        game.runHeadless(600);
        assertTrue(cellsFrom(smith, 8, 25) <= 0.5, "Smith keeps his anvil: " + cellsFrom(smith, 8, 25));
    }

    @Test
    void aPatrollerWhosePointIsWalledOffGoesOn() {
        var game = sessionWith("Points = [PostA, PostB]\n  Mode = BACK_AND_FORTH",
                "Points = [PostA, PostC, PostB]\n  Mode = BACK_AND_FORTH").game();
        var walker = at(game, 5, 5);
        assertTrue(reaches(game, walker, 13, 5, 1500) >= 0,
                "past the stone round PostC, on to PostB: " + walker.getPosition());
    }

    /**
     * A traveller whose Route names a place whose middle is stone -- as a castle stands in the capital's middle --
     * walks to the open cell nearest it and goes on, rather than walking at the stone for ever.
     */
    @Test
    void aTravellerWhosePlaceIsBlockedInTheMiddleGoesOn() {
        var data = Content.data();
        var route = "Route = [Olmazor, Poytaxt, \"Bog'iston\", Poytaxt]";
        assertTrue(data.contains(route));
        var settings = DungeonSettings.parse(data.replace(route, "Route = [PostB, PostC]") + "\n"
                + Stages.found("people").extrasText());
        var text = Stages.found("people").text();
        assertTrue(text.contains("Smith 8 25,"));
        var session = Dungeon.newStageSession(StageFile.read(text.replace("Smith 8 25,",
                "Smith 8 25,\n    Traveller 20 8,"), "people"), settings);
        var game = session.game();
        game.runHeadless(1);
        game.getLogic().setSleep(null);
        var traveller = at(game, 20, 8);
        assertEquals("Traveller", traveller.getTemplate().name());
        // PostC, the nearer, first; then on to PostB.
        assertTrue(reaches(game, traveller, 13, 5, 3000) >= 0, "past PostC, on to PostB: " + traveller.getPosition());
    }

    // ---- the guard: patrols on the hero's side, and fights within its leash ----

    static GameObject hero(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals(SETTINGS.run().defaultHero())).findFirst()
                .orElseThrow();
    }

    /** A Skeleton of the dungeon's at cell {@code (x, y)}. */
    static GameObject skeleton(Dungeon.Session session, float x, float y) {
        var logic = session.game().getLogic();
        return logic.spawn(logic.getThingFactory().findTemplate("Skeleton"),
                new uz.dukeengine.core.math.Coord3D((x + 0.5f) * CELL, (y + 0.5f) * CELL, 0f),
                session.run().dungeonPlayer().getIndex());
    }

    /** How far, in cells, the nearer of the two posts is. */
    static double fromAPost(GameObject thing) {
        return Math.min(cellsFrom(thing, 5, 5), cellsFrom(thing, 13, 5));
    }

    @Test
    void aGuardFightsAFoeWithinItsLeash() {
        var session = session();
        var game = session.game();
        var sentry = at(game, 12, 20);
        assertEquals("Act1CommanderFighter", sentry.getTemplate().name());
        assertEquals(hero(game).getPlayerIndex(), sentry.getPlayerIndex(), "on the hero's side");
        var foe = skeleton(session, sentry.getPosition().x() / CELL + 2.5f, sentry.getPosition().y() / CELL - 0.5f);
        float whole = foe.getBody().getHealth();
        for (int i = 0; i < 300 && !foe.isEffectivelyDead() && foe.getBody().getHealth() >= whole; i++) {
            game.runHeadless(1);
        }
        assertTrue(foe.isEffectivelyDead() || foe.getBody().getHealth() < whole, "struck by the guard");
    }

    @Test
    void aGuardIgnoresAFoeBeyondItsLeash() {
        var session = session();
        var game = session.game();
        var sentry = at(game, 12, 20);
        assertTrue(reaches(game, sentry, 13, 5, 600) >= 0, "the guard comes to its round: " + sentry.getPosition());
        // Eight cells below PostB: within what it sees, past its six cells' leash from anywhere on its round.
        var foe = skeleton(session, 13, 13);
        var held = foe.getPosition();
        float whole = foe.getBody().getHealth();
        for (int i = 0; i < 300; i++) {
            foe.setPosition(held);
            game.runHeadless(1);
            assertTrue(fromAPost(sentry) <= 6.5, "the guard keeps to its round: " + sentry.getPosition());
        }
        assertEquals(whole, foe.getBody().getHealth(), "untouched");
    }

    @Test
    void aGuardDrawnOffComesBackAndPatrolsOn() {
        var session = session();
        var game = session.game();
        var sentry = at(game, 12, 20);
        assertTrue(reaches(game, sentry, 13, 5, 600) >= 0, "the guard comes to its round");
        var foe = skeleton(session, 13, 10); // at its leash's edge
        for (int i = 0; i < 900 && !foe.isEffectivelyDead(); i++) {
            game.runHeadless(1);
        }
        assertTrue(foe.isEffectivelyDead(), "the guard kills it");
        int back = -1;
        for (int i = 0; i < 600 && back < 0; i++) {
            back = fromAPost(sentry) <= 1.5 ? i : -1;
            game.runHeadless(1);
        }
        assertTrue(back >= 0, "back at a post: " + sentry.getPosition());
        boolean atB = cellsFrom(sentry, 13, 5) <= 1.5;
        assertTrue(reaches(game, sentry, atB ? 5 : 13, 5, 900) >= 0, "and on to the other: " + sentry.getPosition());
    }

    @Test
    void aGuardIsNotTalkedTo() {
        var session = session();
        var game = session.game();
        var run = session.run();
        var sentry = at(game, 12, 20);
        var hero = hero(game);
        hero.setPosition(new uz.dukeengine.core.math.Coord3D(sentry.getPosition().x() + 12f, sentry.getPosition().y(),
                sentry.getPosition().z()));
        int said = run.getQuests().said().size();
        int barked = run.getQuests().barked().size();
        run.talkTo(game, hero.getPlayerIndex(), sentry.getId());
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() * 3);
        assertEquals(said, run.getQuests().said().size(), "nothing said");
        assertEquals(barked, run.getQuests().barked().size(), "nor called out");
    }
}
