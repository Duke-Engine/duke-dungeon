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
 * The old graves (data/story/graveyards.duke), an ender dragon's crystals: laid with the map and dormant until the
 * queen sends him; the raiser kept whole while a grave stands, each grave struck down on its own; the dead climbing out
 * on a beat, the first nine paying and every one after changed and worth nothing; the raiser left unhurt raising its
 * graves again; and the raiser killed setting the graveyard's flag.
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
        session.game().getLogic().getObjects().stream() // and his allies: the graves are this test's alone
                .filter(o -> o.getTemplate() != null && List.of("Act1CommanderFighter", "Soldier", "KingdomArcher",
                        "KingdomHealer").contains(o.getTemplate().name()))
                .forEach(GameObject::markDestroyed);
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
    void laidWithTheMapButDormantUntilTheQueenSendsHim() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 1, null); // the opening: the queen has not sent him yet
        session.game().runHeadless(SETTINGS.story().dialogue().checkFrames() + 2);
        assertEquals(1, all(session, "GraveRaiser").size(), "seen from the start");
        assertEquals(3, all(session, "Grave").size());
        var grave = all(session, "Grave").getFirst();
        grave.getBody().setHealth(grave.getBody().getMaxHealth() * 0.01f);
        session.game().runHeadless(SETTINGS.graveyards().getFirst().everyFrames() * 2);
        assertEquals(grave.getBody().getMaxHealth(), grave.getBody().getHealth(), 0.01f, "dormant: nothing of it hurt");
        assertEquals(3, all(session, "Grave").size());
        assertTrue(session.game().getLogic().getObjects().stream()
                .noneMatch(o -> o.findModule(uz.dukeengine.dungeon.ai.Leash.class) != null
                        && o.getPlayerIndex() == session.run().dungeonSide()), "nothing raised");
    }

    @Test
    void theRaiserStandsAmongItsGravesAndThrowsFromFarOff() {
        var session = woken();
        var raiser = all(session, "GraveRaiser").getFirst();
        var stood = raiser.getPosition();
        var hero = hero(session);
        // In its reach but well off it: it throws from where it stands, and never comes.
        hero.setPosition(new Coord3D(stood.x() + 100f, stood.y(), hero.getPosition().z()));
        for (int i = 0; i < 200; i++) {
            session.game().runHeadless(1);
            hero.getBody().setHealth(hero.getBody().getMaxHealth());
        }
        assertEquals(stood.x(), raiser.getPosition().x(), 0.01f, "it never walked");
        assertEquals(stood.y(), raiser.getPosition().y(), 0.01f);
    }

    @Test
    void itsDeadKeepToTheirGravesAndDoNotChaseHimAway() {
        var session = woken();
        var every = SETTINGS.graveyards().getFirst().everyFrames();
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        float leash = SETTINGS.graveyards().getFirst().leash() * cell;
        var hero = hero(session);
        // He comes up to them, then runs far off: they follow no further than their leash.
        hero.setPosition(new Coord3D(GRAVES.middleX(cell) + leash * 0.6f, GRAVES.middleY(cell), hero.getPosition().z()));
        for (int i = 0; i < every * 2 + 60; i++) {
            session.game().runHeadless(1);
            hero.getBody().setHealth(hero.getBody().getMaxHealth());
        }
        hero.setPosition(new Coord3D(GRAVES.middleX(cell) + leash * 4f, GRAVES.middleY(cell), hero.getPosition().z()));
        for (int i = 0; i < 400; i++) {
            session.game().runHeadless(1);
            hero.getBody().setHealth(hero.getBody().getMaxHealth());
        }
        var risen = session.game().getLogic().getObjects().stream()
                .filter(o -> o.findModule(uz.dukeengine.dungeon.ai.Leash.class) != null && !o.isEffectivelyDead())
                .toList();
        assertTrue(!risen.isEmpty(), "some of the dead stand");
        for (var one : risen) {
            assertTrue(Math.hypot(one.getPosition().x() - GRAVES.middleX(cell), one.getPosition().y() - GRAVES.middleY(cell))
                    <= leash + 20f, "back at its graves, not after him: " + one.getPosition());
        }
    }

    @Test
    void whileAGraveStandsNothingTheRaiserTakesHoldsAndEachGraveFallsOnItsOwn() {
        var session = woken();
        var raiser = all(session, "GraveRaiser").getFirst();
        var graves = all(session, "Grave");
        for (int i = 0; i < graves.size(); i++) {
            raiser.getBody().setHealth(1f);
            session.game().runHeadless(2);
            assertEquals(raiser.getBody().getMaxHealth(), raiser.getBody().getHealth(), 0.01f, "kept whole by its graves");
            graves.get(i).getBody().setHealth(1f); // beaten: its UNYIELDING keeps it from falling by the blow
            session.game().runHeadless(2);
            assertEquals(graves.size() - 1 - i, all(session, "Grave").size(), "struck down, one at a time");
        }
        assertFalse(session.run().getScript().flagged(SETTINGS.graveyards().getFirst().goneFlag()), "not gone yet");
        raiser.getBody().setHealth(raiser.getBody().getMaxHealth() * 0.5f);
        session.game().runHeadless(2);
        assertTrue(raiser.getBody().getHealth() < raiser.getBody().getMaxHealth() * 0.6f, "its graves down, it is hurt");
    }

    @Test
    void leftUnhurtItRaisesItsGravesAgainAndAStrikeBreaksTheSpell() {
        var session = woken();
        var yard = SETTINGS.graveyards().getFirst();
        var raiser = all(session, "GraveRaiser").getFirst();
        all(session, "Grave").forEach(g -> g.getBody().setHealth(1f));
        session.game().runHeadless(2);
        assertTrue(all(session, "Grave").isEmpty());

        playClearing(session, yard.calmFrames() + yard.restoreFrames() / 2);
        raiser.getBody().setHealth(raiser.getBody().getHealth() - 5f); // struck in the middle of it
        playClearing(session, yard.restoreFrames() / 2 + 10);
        assertTrue(all(session, "Grave").isEmpty(), "the spell broken");

        playClearing(session, yard.calmFrames() + yard.restoreFrames() + 5);
        assertEquals(3, all(session, "Grave").size(), "left alone, every grave stands again");
        raiser.getBody().setHealth(1f);
        session.game().runHeadless(2);
        assertEquals(raiser.getBody().getMaxHealth(), raiser.getBody().getHealth(), 0.01f, "and keeps it whole again");
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
    void aGraveIsABuildingNoPullMovesIt() {
        var session = woken();
        var grave = all(session, "Grave").getFirst();
        var stood = grave.getPosition();
        grave.setPosition(new Coord3D(stood.x() + 60f, stood.y() - 40f, stood.z())); // as the knight's R drags it
        session.game().runHeadless(2);
        assertEquals(stood.x(), grave.getPosition().x(), 0.01f, "back where it stands");
        assertEquals(stood.y(), grave.getPosition().y(), 0.01f);
    }

    @Test
    void theRaiserKilledSetsItsFlagAndNothingClimbsOutAgain() {
        var session = woken();
        all(session, "Grave").forEach(g -> g.getBody().setHealth(1f));
        session.game().runHeadless(2);
        all(session, "GraveRaiser").getFirst().getBody().setHealth(0f);
        session.game().runHeadless(3);
        assertTrue(session.run().getScript().flagged(SETTINGS.graveyards().getFirst().goneFlag()));
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
