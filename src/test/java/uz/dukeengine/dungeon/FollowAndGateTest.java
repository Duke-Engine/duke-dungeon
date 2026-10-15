package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.stage.Stages;

/**
 * The follow mission and the gated world of the seamless campaign (docs/plan/2026-10-15-seamless-campaign.md, sections
 * 5 and 6): the mark over the commander, her waiting when he strays, the step done at the palace; and the world shut
 * but for the way to the capital, opened by the story's flags.
 */
class FollowAndGateTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static StaticMap.Zone zone(String name) {
        var all = new ArrayList<>(Stages.load("world", SETTINGS).story().zones());
        all.addAll(SETTINGS.placesOn("world"));
        return all.stream().filter(z -> z.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<GameObject> all(Dungeon.Session session, String template) {
        return session.game().getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(template) && !o.isEffectivelyDead()).toList();
    }

    private static GameObject hero(Dungeon.Session session) {
        return all(session, SETTINGS.campaign().hero()).getFirst();
    }

    private static void play(Dungeon.Session session, int frames) {
        for (int i = 0; i < frames; i++) {
            session.run().read(session.game().getLocalPlayerIndex(), 10_000);
            if (session.run().inCutscene()) {
                session.run().skipCutscene(session.game());
            }
            session.game().runHeadless(1);
        }
    }

    /** A new game played through the intro to the commander's lead. */
    private static Dungeon.Session led() {
        var session = Dungeon.newSession(11L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 1, null);
        session.game().runHeadless(1);
        for (int i = 0; i < 2400 && !session.run().getScript().flagged("BattleWon"); i++) {
            play(session, 1);
        }
        assertTrue(session.run().getScript().flagged("BattleWon"), "the premise: the intro played");
        play(session, 2);
        return session;
    }

    private static void put(GameObject thing, float x, float y) {
        var logic = thing.getWorld();
        thing.setPosition(new Coord3D(x, y, logic.groundHeight(new Coord3D(x, y, 0f))));
    }

    @Test
    void theCommanderWearsTheMarkAndIsOnTheMinimap() {
        var session = led();
        var commander = all(session, "Act1Commander").getFirst();
        session.game().watch();
        boolean marked = false;
        for (int i = 0; i <= SETTINGS.story().dialogue().glowFrames() && !marked; i++) {
            session.game().runHeadless(1);
            for (var event : session.game().getSnapshot().events()) {
                marked |= event instanceof EffectPlayed played && played.name().equals("FollowMark")
                        && commander.getId().equals(played.riding());
            }
        }
        assertTrue(marked, "the follow mark over her head");
        var marks = MissionScreen.minimapMarks(session, SETTINGS);
        assertTrue(marks.stream().anyMatch(mark -> Math.abs(mark.x() - commander.getPosition().x()) < 25f
                && Math.abs(mark.y() - commander.getPosition().y()) < 25f), "and marked on the minimap: " + marks);
    }

    @Test
    void sheWaitsWhenHeStraysAndTheStepIsDoneAtThePalace() {
        var session = led();
        var commander = all(session, "Act1Commander").getFirst();
        var near = SETTINGS.story().dialogue();
        // He stays back on the field, far from her: she stops and waits.
        var field = zone("Darvoza_oldi");
        // To the field's north edge, the far side from the line she stands at: 8 cells each way was within a few units
        // of FollowFar, and one more person on the map put her that much nearer.
        put(hero(session), field.middleX(CELL), field.middleY(CELL) - 11 * CELL);
        play(session, 10);
        assertTrue(Math.hypot(hero(session).getPosition().x() - commander.getPosition().x(),
                hero(session).getPosition().y() - commander.getPosition().y()) > near.followFar(), "the premise");
        var stood = commander.getPosition();
        play(session, 60);
        assertEquals(stood.x(), commander.getPosition().x(), 0.5f, "she waits for him");
        // He keeps by her side: she leads him to the palace, and the step is done there.
        var palace = zone("Saroy");
        for (int i = 0; i < 6000 && session.run().getQuests().active().stream()
                .anyMatch(q -> q.quest().equals("FollowCommander")); i++) {
            var her = commander.getPosition();
            put(hero(session), her.x() - 20f, her.y() + 20f);
            play(session, 1);
        }
        assertTrue(session.run().getQuests().finished().contains("FollowCommander")
                || session.run().getScript().flagged("Welcomed"), "followed to the end");
        assertTrue(palace.holds(commander.getPosition().x(), commander.getPosition().y(), CELL),
                "she stands in the palace's court");
    }

    @Test
    void theWorldIsShutButForTheWayToTheCapitalUntilTheStoryOpensIt() {
        var session = led();
        assertEquals("Act1Battle", session.run().gateName());
        // The old graves are shut while he is led to the palace: put there, he is put back.
        var graves = zone("Eski_qabrlar");
        put(hero(session), graves.middleX(CELL), graves.middleY(CELL));
        play(session, 2);
        var at = hero(session).getPosition();
        assertFalse(graves.holds(at.x(), at.y(), CELL) && !zone("Darvoza_oldi").holds(at.x(), at.y(), CELL),
                "put back inside the open land");
        // Far out in the world: the same.
        put(hero(session), 300 * CELL, 300 * CELL);
        play(session, 2);
        assertTrue(hero(session).getPosition().x() < 200 * CELL, "the far world is shut");
        // Welcomed by the queen, the graves open.
        session.run().getScript().flag("Welcomed");
        play(session, 2);
        assertEquals("Act1Capital", session.run().gateName());
        put(hero(session), graves.middleX(CELL), graves.middleY(CELL));
        play(session, 2);
        at = hero(session).getPosition();
        assertTrue(graves.holds(at.x(), at.y(), CELL), "the graves are open now");
    }

    @Test
    void theOpenWorldOffTheCampaignIsNotGated() {
        var session = Dungeon.newStageSession(Stages.load("world", SETTINGS), SETTINGS);
        session.game().runHeadless(2);
        assertNull(session.run().gateName(), "no Act1 flag, no gate");
        var hero = session.game().getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(SETTINGS.run().defaultHero())).findFirst().orElseThrow();
        put(hero, 300 * CELL, 300 * CELL);
        session.game().runHeadless(2);
        assertTrue(hero.getPosition().x() > 290 * CELL, "the world is open");
    }

    @Test
    void aSaveUnderAGateLoadsAtItsStart() {
        var session = led();
        var save = SaveGame.read(session.run().saveNow().text(), "test");
        var loaded = Dungeon.newSession(11L, SETTINGS);
        loaded.run().resume(save, uz.dukeengine.dungeon.run.Floors.resumed(save, SETTINGS));
        loaded.game().runHeadless(3);
        var at = hero(loaded).getPosition();
        assertTrue(zone("Darvoza_oldi").holds(at.x(), at.y(), CELL), "back on the field, where she leads from");
        assertTrue(loaded.run().getQuests().active().stream().anyMatch(q -> q.quest().equals("FollowCommander")));
        assertFalse(all(loaded, "Act1Commander").isEmpty(), "and she is there to follow");
    }
}
