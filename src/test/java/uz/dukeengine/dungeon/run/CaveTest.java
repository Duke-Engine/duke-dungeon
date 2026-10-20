package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.stage.Stages;

/**
 * The open world's caves (data/story/caves.duke): stepping into a mouth takes him down to its floor; its last monster
 * dead, the cave is gone and he is back at its mouth; a cave gone is never gone into again, and every mouth and every
 * cave's floor is one the game can lay.
 */
class CaveTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
    private static final java.util.List<StaticMap.Zone> ZONES = Stages.load("world", SETTINGS).story().zones();

    private static StaticMap.Zone zone(String name) {
        return ZONES.stream().filter(z -> z.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
    }

    private static GameObject hero(Dungeon.Session session) {
        return session.game().getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(session.run().getHeroTemplate())).findFirst().orElseThrow();
    }

    private static void play(Dungeon.Session session, int frames) {
        for (int i = 0; i < frames; i++) {
            session.run().read(session.game().getLocalPlayerIndex(), 10_000);
            if (session.run().inCutscene()) {
                session.run().skipCutscene(session.game());
            }
            session.game().runHeadless(1);
            var hero = hero(session);
            hero.getBody().setHealth(hero.getBody().getMaxHealth());
        }
    }

    private static void standIn(Dungeon.Session session, String name) {
        var at = zone(name);
        var hero = hero(session);
        hero.setPosition(new Coord3D(at.middleX(CELL), at.middleY(CELL), hero.getPosition().z()));
        play(session, SETTINGS.story().dialogue().checkFrames() * 2 + 3);
    }

    /** On the world, the hero gone on alone: the caves open. */
    private static Dungeon.Session alone() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 3, null);
        session.game().runHeadless(2);
        session.run().getScript().flag("WarBegun");
        play(session, 2);
        return session;
    }

    @Test
    void everyCaveHasAMouthOnTheWorldAndAFloorTheGameCanLay() {
        assertTrue(SETTINGS.caves().size() >= 6, "the one by the graves and the wilds' five");
        for (var cave : SETTINGS.caves()) {
            zone(cave.zone());
            assertNotNull(SETTINGS.descent(cave.name()), cave.name() + " has a floor of its own");
            assertNotNull(Floors.named(cave.name(), 1L, SETTINGS, false).next(1), cave.name());
        }
        for (var yard : SETTINGS.graveyards()) {
            zone(yard.zone());
        }
    }

    @Test
    void aMouthTakesHimDownAndTheFloorClearedBringsHimBackUpAndTheCaveIsGone() {
        var session = alone();
        var run = session.run();
        standIn(session, "Gor_1");
        assertEquals("Gor_1", run.getStageName(), "down into the cave");

        // Every monster of the floor dead: the cave gone, and he is back at its mouth.
        for (int round = 0; round < 20 && "Gor_1".equals(run.getStageName()); round++) {
            session.game().getLogic().getObjects().stream()
                    .filter(o -> o.findModule(Bounty.class) != null && !o.isEffectivelyDead())
                    .forEach(o -> o.getBody().damage(1e7f));
            play(session, SETTINGS.story().dialogue().checkFrames() + 3);
        }
        assertEquals("world", run.getStageName(), "back up on the world");
        assertTrue(run.getScript().flagged("GoneGor_1"), "the cave gone");
        var mouth = zone("Gor_1");
        var at = hero(session).getPosition();
        assertTrue(Math.hypot(at.x() - mouth.middleX(CELL), at.y() - mouth.middleY(CELL)) < 40f,
                "at its mouth: " + at);
        assertTrue(session.game().getLogic().getObjects().stream().anyMatch(o -> o.getTemplate().name().equals("CaveSeal")
                && Math.hypot(o.getPosition().x() - mouth.middleX(CELL), o.getPosition().y() - mouth.middleY(CELL)) < 20f),
                "its mouth sealed");

        // Out of the mouth and in again: a cave gone takes nobody in.
        hero(session).setPosition(new Coord3D(at.x() + 60f, at.y(), at.z()));
        play(session, SETTINGS.story().dialogue().checkFrames() + 3);
        standIn(session, "Gor_1");
        assertEquals("world", run.getStageName(), "never gone into again");
    }

    @Test
    void beforeHeGoesOnAloneAMouthTakesNobodyIn() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 3, null);
        session.game().runHeadless(2);
        standIn(session, "Gor_kirish");
        assertEquals("world", session.run().getStageName());
    }
}
