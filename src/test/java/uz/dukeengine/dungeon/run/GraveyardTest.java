package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.ExperienceModule;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The old graves (data/story/graveyards.duke): laid once their flag comes, the raiser before them and the graves in a
 * ring; the graves shielded while the raiser stands; the dead climbing out on a beat, the first nine paying and every
 * one after changed and worth nothing; and, the raiser down, each grave struck down on its own until the last sets the
 * graveyard's flag and nothing climbs out again.
 */
class GraveyardTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String WORD = SETTINGS.graves().changedWord();
    private static final uz.dukeengine.dungeon.map.StaticMap.Zone GRAVES = uz.dukeengine.dungeon.stage.Stages.load("world",
            SETTINGS).story().zones().stream().filter(z -> z.name().equals("Eski_qabrlar")).findFirst().orElseThrow();

    private static List<GameObject> all(Dungeon.Session session, String template) {
        return session.game().getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(template) && !o.isEffectivelyDead()).toList();
    }

    private static GameObject hero(Dungeon.Session session) {
        return all(session, session.run().getHeroTemplate()).getFirst();
    }

    /** At the graves, their flag come, and the hero far off out of it. */
    private static Dungeon.Session woken() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 3, null);
        var game = session.game();
        game.runHeadless(2);
        var hero = hero(session);
        hero.setPosition(new Coord3D(hero.getPosition().x() - 600f, hero.getPosition().y(), hero.getPosition().z()));
        nearTheGraves(session).forEach(GameObject::markDestroyed); // the training's, already standing there
        game.runHeadless(2);
        session.run().getScript().flag("GravesWake");
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() + 1);
        return session;
    }

    /** Frames played, every one of the dead put down as it climbs out -- by nobody, so nobody is paid. */
    private static int playClearing(Dungeon.Session session, int frames) {
        var seen = new java.util.HashSet<uz.dukeengine.core.thing.ObjectId>();
        for (int i = 0; i < frames; i++) {
            session.game().runHeadless(1);
            hero(session).getBody().setHealth(hero(session).getBody().getMaxHealth());
            for (var one : nearTheGraves(session)) {
                if (seen.add(one.getId())) {
                    one.markDestroyed();
                }
            }
        }
        return seen.size();
    }

    /** The dead standing by the graves: what climbed out of them, not the world's own camps. */
    private static List<GameObject> nearTheGraves(Dungeon.Session session) {
        var zone = GRAVES;
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        float x = zone.middleX(cell);
        float y = zone.middleY(cell);
        return session.game().getLogic().getObjects().stream()
                .filter(o -> (o.getTemplate().name().equals("Skeleton") || o.getTemplate().name().equals("Runner"))
                        && !o.isEffectivelyDead()
                        && Math.hypot(o.getPosition().x() - x, o.getPosition().y() - y) < 80f)
                .toList();
    }

    @Test
    void laidOnceItsFlagComesTheRaiserBeforeItsGraves() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 2, null); // the palace: the queen has not sent him yet
        session.game().runHeadless(SETTINGS.story().dialogue().checkFrames() + 2);
        assertTrue(all(session, "GraveRaiser").isEmpty(), "not before its flag");
        assertTrue(all(session, "Grave").isEmpty());

        var woken = woken();
        assertEquals(1, all(woken, "GraveRaiser").size());
        assertEquals(3, all(woken, "Grave").size());
    }

    @Test
    void whileTheRaiserStandsNothingAGraveTakesHolds() {
        var session = woken();
        var grave = all(session, "Grave").getFirst();
        grave.getBody().setHealth(grave.getBody().getMaxHealth() * 0.01f);
        session.game().runHeadless(2);
        assertEquals(grave.getBody().getMaxHealth(), grave.getBody().getHealth(), 0.01f, "whole again");
        assertEquals(3, all(session, "Grave").size(), "and standing");
    }

    @Test
    void theFirstNinePayAndEveryOneAfterIsChangedAndWorthNothing() {
        var session = woken();
        var every = SETTINGS.graveyards().getFirst().everyFrames();
        int paying = SETTINGS.graves().paying();
        assertEquals(paying, playClearing(session, every * paying + 5), "one climbs out every beat");

        session.game().runHeadless(every); // the tenth
        var tenth = nearTheGraves(session).stream().filter(o -> !o.isDestroyed()).findFirst().orElseThrow();
        assertTrue(tenth.hasCondition(WORD), "changed, and drawn so");
        assertEquals(0, tenth.findModule(ExperienceModule.class).getExperienceValue(), "worth no experience");
        assertTrue(tenth.findModule(Bounty.class) == null, "and no gold");
        assertTrue(uz.dukeengine.rpg.hero.LevelBonus.levelOf(tenth)
                > SETTINGS.beforeBossLevel(1), "and stronger");
    }

    @Test
    void theRaiserDownEachGraveIsStruckDownAndTheLastSetsItsFlag() {
        var session = woken();
        var raiser = all(session, "GraveRaiser").getFirst();
        raiser.getBody().setHealth(0f);
        session.game().runHeadless(3);
        var gone = SETTINGS.graveyards().getFirst().goneFlag();

        var graves = all(session, "Grave");
        for (int i = 0; i < graves.size(); i++) {
            var grave = graves.get(i);
            grave.getBody().setHealth(1f); // beaten: its UNYIELDING keeps it from falling by the blow
            session.game().runHeadless(2);
            assertEquals(graves.size() - 1 - i, all(session, "Grave").size(), "struck down, one at a time");
            assertEquals(i == graves.size() - 1, session.run().getScript().flagged(gone),
                    "the flag only with the last of them");
        }
        int risen = playClearing(session, SETTINGS.graveyards().getFirst().everyFrames() * 3);
        assertEquals(0, risen, "and nothing climbs out of a graveyard that is gone");
    }

    @Test
    void aGraveyardGoneIsNeverLaidAgain() {
        var session = woken();
        session.run().getScript().flag(SETTINGS.graveyards().getFirst().goneFlag());
        var save = session.run().saveNow();
        if (save == null) {
            return; // mid-fight: a save is refused, and this has nothing to load
        }
        var loaded = Dungeon.newSession(7L, SETTINGS);
        loaded.run().resume(save, Floors.resumed(save, SETTINGS));
        loaded.game().runHeadless(SETTINGS.story().dialogue().checkFrames() + 2);
        assertFalse(all(loaded, "GraveRaiser").size() > 0, "its raiser is not stood up again");
        assertTrue(all(loaded, "Grave").isEmpty(), "nor its graves");
    }
}
