package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/**
 * Act 1's open-world part walked the way a player walks it: every place reached by an order to go there, never by
 * being put there -- so a shut gate, a wall with no way through or a route that gives up shows here, where Act1Test,
 * which puts the hero in place, cannot see it.
 */
class CampaignWalkTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Campaign CAMPAIGN = SETTINGS.campaign();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .filter(o -> !o.isEffectivelyDead()).toList();
    }

    private static GameObject hero(Dungeon.Session session) {
        return all(session.game(), session.run().getHeroTemplate()).getFirst();
    }

    private static void play(Dungeon.Session session, int frames) {
        var game = session.game();
        for (int i = 0; i < frames; i++) {
            session.run().read(game.getLocalPlayerIndex(), 10_000);
            if (session.run().inCutscene()) {
                session.run().skipCutscene(game);
            }
            game.runHeadless(1);
        }
    }

    private static double distance(GameObject a, float x, float y) {
        return Math.hypot(a.getPosition().x() - x, a.getPosition().y() - y);
    }

    private static Coord3D lastSeen;

    /**
     * One frame of a walk: the order given again now and then, and -- as a player would -- whatever has him pinned for
     * ten seconds cut down. The walk is what is tested, not a fight on the way, so he is kept whole.
     */
    private static void step(Dungeon.Session session, GameObject hero, int i, float x, float y) {
        hero.getBody().setHealth(hero.getBody().getMaxHealth());
        if (i % 300 == 0) {
            var at = hero.getPosition();
            if (lastSeen != null && i > 0 && Math.hypot(at.x() - lastSeen.x(), at.y() - lastSeen.y()) < 5) {
                session.game().getLogic().getObjects().stream()
                        .filter(o -> o.getBody() != null && !o.isEffectivelyDead()
                                && o.getPlayerIndex() != hero.getPlayerIndex()
                                && o.getPlayerIndex() != uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX
                                && Math.hypot(o.getPosition().x() - at.x(), o.getPosition().y() - at.y()) < 50)
                        .toList()
                        .forEach(foe -> foe.getBody().damage(1e8f, uz.dukeengine.core.module.DamageType.NORMAL,
                                new uz.dukeengine.core.module.Death(uz.dukeengine.core.module.DeathType.NORMAL,
                                        hero.getId(), hero.getPlayerIndex())));
            }
            lastSeen = at;
        }
        if (i % 60 == 0) {
            hero.getLocomotor().moveTo(new Coord3D(x, y, 0f));
        }
        play(session, 1);
    }

    /** Ordered to ({@code x}, {@code y}) and played on until he is within {@code near} of it, at most {@code frames}. */
    private static void walkTo(Dungeon.Session session, String what, float x, float y, float near, int frames) {
        var hero = hero(session);
        for (int i = 0; i < frames && distance(hero, x, y) > near; i++) {
            step(session, hero, i, x, y);
        }
        assertTrue(distance(hero, x, y) <= near, "he never reached " + what + ": stands at " + hero.getPosition()
                + ", " + (int) distance(hero, x, y) + " short of (" + (int) x + ", " + (int) y + ")");
    }

    private static void walkToZone(Dungeon.Session session, String zone, int frames) {
        var at = Act1Test.zone(session.run().getStageName(), zone);
        var hero = hero(session);
        // Inside the place is enough: its middle may be under a grave mound or a throne.
        for (int i = 0; i < frames && !at.holds(hero.getPosition().x(), hero.getPosition().y(), CELL); i++) {
            step(session, hero, i, at.middleX(CELL), at.middleY(CELL));
        }
        assertTrue(at.holds(hero.getPosition().x(), hero.getPosition().y(), CELL), "he never got into " + zone
                + ": stands at " + hero.getPosition());
        play(session, SETTINGS.story().dialogue().checkFrames() + 3);
    }

    private static void walkToPerson(Dungeon.Session session, String person, int frames) {
        var who = all(session.game(), person).getFirst();
        walkTo(session, person, who.getPosition().x(), who.getPosition().y(), 2 * CELL, frames);
        play(session, SETTINGS.story().dialogue().checkFrames() + 3);
    }

    @Test
    void actOnesWorldIsWalkedFromTheBattleToTheWar() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(CAMPAIGN, 1, null);
        session.game().runHeadless(1);
        var run = session.run();
        var script = run.getScript();

        Act1Test.playUntilFlag(session, "HeroLanded", 1200);
        Act1Test.playUntilFlag(session, "BattleWon", 1200);

        // Behind the commander to the palace, keeping near her as a player does.
        var commander = all(session.game(), "Act1Commander").getFirst();
        for (int i = 0; i < 9000 && run.getQuests().active().stream()
                .anyMatch(q -> q.quest().equals("FollowCommander")); i++) {
            if (i % 30 == 0) {
                var her = commander.getPosition();
                hero(session).getLocomotor().moveTo(new Coord3D(her.x() - 15f, her.y() + 15f, 0f));
            }
            play(session, 1);
        }
        assertTrue(run.getQuests().finished().contains("FollowCommander"), "followed her all the way to the palace");

        walkToPerson(session, "Act1Queen", 3000);
        assertTrue(run.getQuests().active().stream().anyMatch(q -> q.quest().equals("Training")), "the training set");

        walkToZone(session, "Eski_qabrlar", 6000);
        assertTrue(!all(session.game(), "Reaper").isEmpty(), "the graves' keeper rose");
        var reaper = all(session.game(), "Reaper").getFirst();
        reaper.getBody().damage(1e7f, uz.dukeengine.core.module.DamageType.NORMAL,
                new uz.dukeengine.core.module.Death(uz.dukeengine.core.module.DeathType.NORMAL, hero(session).getId(),
                        hero(session).getPlayerIndex()));
        play(session, 10);
        assertTrue(script.flagged("Trained"), "trained");

        walkToZone(session, "Saroy", 6000);
        assertTrue(script.flagged("WarBegun"), "the war council held");

        walkToZone(session, "Darvoza_oldi", 6000);
        for (int i = 0; i < 120 && !"urush".equals(run.getStageName()); i++) {
            play(session, 1);
        }
        assertEquals("urush", run.getStageName(), "out by the field to the war");
    }

    /** Followed halfway, then killed: back from the death, the commander can still be followed to the palace. */
    @Test
    void aDeathWhileFollowingTheCommanderLeavesTheWayOpen() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(CAMPAIGN, 1, null);
        session.game().runHeadless(1);
        var run = session.run();
        Act1Test.playUntilFlag(session, "BattleWon", 2400);
        for (int i = 0; i < 600; i++) {
            if (i % 30 == 0) {
                var her = all(session.game(), "Act1Commander").getFirst().getPosition();
                hero(session).getLocomotor().moveTo(new Coord3D(her.x() - 15f, her.y() + 15f, 0f));
            }
            play(session, 1);
        }
        hero(session).getBody().damage(1e7f);
        play(session, SETTINGS.run().respawnDelayFrames() + 30);
        assertEquals(uz.dukeengine.dungeon.run.DungeonRun.State.RUNNING, run.getState(), "back from the death");
        for (int i = 0; i < 12000 && run.getQuests().active().stream()
                .anyMatch(q -> q.quest().equals("FollowCommander")); i++) {
            if (i % 30 == 0) {
                var her = all(session.game(), "Act1Commander").getFirst().getPosition();
                hero(session).getLocomotor().moveTo(new Coord3D(her.x() - 15f, her.y() + 15f, 0f));
            }
            play(session, 1);
        }
        assertTrue(run.getQuests().finished().contains("FollowCommander")
                || run.getScript().flagged("Welcomed"), "still led to the palace after the death");
    }

    /** Saved halfway behind the commander and loaded into a new game: the walk goes on to the palace. */
    @Test
    void aSaveWhileFollowingTheCommanderLoadsAndTheWalkGoesOn() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(CAMPAIGN, 1, null);
        session.game().runHeadless(1);
        var saves = new ArrayList<uz.dukeengine.dungeon.run.SaveGame>();
        session.run().onCheckpoint(saves::add);
        Act1Test.playUntilFlag(session, "BattleWon", 2400);
        for (int i = 0; i < 600; i++) {
            if (i % 30 == 0) {
                var her = all(session.game(), "Act1Commander").getFirst().getPosition();
                hero(session).getLocomotor().moveTo(new Coord3D(her.x() - 15f, her.y() + 15f, 0f));
            }
            play(session, 1);
        }
        var save = session.run().saveNow();
        var loaded = Dungeon.newSession(7L, SETTINGS);
        loaded.run().resume(save, uz.dukeengine.dungeon.run.Floors.resumed(save, SETTINGS));
        loaded.game().runHeadless(2);
        var run = loaded.run();
        for (int i = 0; i < 12000 && run.getQuests().active().stream()
                .anyMatch(q -> q.quest().equals("FollowCommander")); i++) {
            if (i % 30 == 0) {
                var her = all(loaded.game(), "Act1Commander").getFirst().getPosition();
                hero(loaded).getLocomotor().moveTo(new Coord3D(her.x() - 15f, her.y() + 15f, 0f));
            }
            play(loaded, 1);
        }
        assertTrue(run.getQuests().finished().contains("FollowCommander"), "led to the palace, before or after the load");
        assertTrue(run.getQuests().active().stream().anyMatch(q -> q.quest().equals("MeetTheQueen")),
                "and the log says to see the queen: " + run.getQuests().active());
        walkToPerson(loaded, "Act1Queen", 6000);
        assertTrue(run.getScript().flagged("Welcomed"), "welcomed after the load");
        assertTrue(run.getQuests().finished().contains("MeetTheQueen"));
    }

    /** Every fighter of the dungeon's standing in {@code zone} struck down by the hero. */
    private static void clearOut(Dungeon.Session session, String zone) {
        var at = Act1Test.zone(session.run().getStageName(), zone);
        var hero = hero(session);
        session.game().getLogic().getObjects().stream()
                .filter(o -> o.getBody() != null && !o.isEffectivelyDead() && o.getPlayerIndex() != hero.getPlayerIndex()
                        && o.getPlayerIndex() != uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX
                        && at.holds(o.getPosition().x(), o.getPosition().y(), CELL))
                .toList()
                .forEach(foe -> foe.getBody().damage(1e8f, uz.dukeengine.core.module.DamageType.NORMAL,
                        new uz.dukeengine.core.module.Death(uz.dukeengine.core.module.DeathType.NORMAL, hero.getId(),
                                hero.getPlayerIndex())));
        play(session, 5);
    }

    /** A camp cleared before the witch told the secret still counts once she has: a player goes where he likes. */
    @Test
    void aCampClearedBeforeTheSecretIsFreedOnceItIsKnown() {
        var session = Dungeon.newSession(11L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign("act2"), 1, null);
        session.game().runHeadless(2);
        var script = session.run().getScript();
        play(session, 60);
        walkToZone(session, "Yashil_dala", 20000);
        clearOut(session, "Yashil_dala");
        play(session, 30);
        assertTrue(!script.flagged("FreedGreen"), "not yet: the secret is not known");
        walkToPerson(session, "Elder", 20000);
        walkToPerson(session, "BabaYaga", 20000);
        walkToPerson(session, "Necromancer", 20000);
        all(session.game(), "Necromancer").forEach(n -> n.getBody().damage(1e8f, uz.dukeengine.core.module.DamageType.NORMAL,
                new uz.dukeengine.core.module.Death(uz.dukeengine.core.module.DeathType.NORMAL,
                        hero(session).getId(), hero(session).getPlayerIndex())));
        play(session, 5);
        walkToPerson(session, "BabaYaga", 20000);
        assertTrue(script.flagged("SecretKnown"));
        play(session, SETTINGS.story().dialogue().checkFrames() * 2 + 5);
        assertTrue(script.flagged("FreedGreen"), "the meadow cleared earlier is freed now");
    }

    @Test
    void actTwosWorldIsWalkedFromTheLandingToTheQueensCastle() {
        var session = Dungeon.newSession(11L, SETTINGS);
        var act2 = SETTINGS.campaign("act2");
        session.run().skipTo(act2, 1, null);
        session.game().runHeadless(2);
        var run = session.run();
        var script = run.getScript();
        play(session, 60);
        assertTrue(script.flagged("HeroineArrived"));

        walkToPerson(session, "Elder", 6000);
        walkToPerson(session, "BabaYaga", 20000);
        assertTrue(run.getQuests().active().stream().anyMatch(q -> q.quest().equals("WitchsPrice")), "her price set");
        // Her price: the necromancer, walked to and cut down; back to her for the queen's secret.
        walkToPerson(session, "Necromancer", 20000);
        all(session.game(), "Necromancer").forEach(n -> n.getBody().damage(1e8f, uz.dukeengine.core.module.DamageType.NORMAL,
                new uz.dukeengine.core.module.Death(uz.dukeengine.core.module.DeathType.NORMAL,
                        hero(session).getId(), hero(session).getPlayerIndex())));
        play(session, 5);
        walkToPerson(session, "BabaYaga", 20000);
        assertTrue(script.flagged("SecretKnown"), "the queen's secret told");

        for (var camp : List.of("Yashil_dala", "Toshli_adir", "Qumli_dasht")) {
            walkToZone(session, camp, 20000);
            clearOut(session, camp);
        }
        play(session, 10);
        assertTrue(script.flagged("LandsFreed"), "the three lands freed");

        walkToZone(session, "Malika_qasri", 20000);
        for (int i = 0; i < 300 && !"hall".equals(run.getStageName()); i++) {
            play(session, 1);
        }
        assertEquals("hall", run.getStageName(), "into the queen's hall");
    }
}
