package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.run.Floors;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/**
 * Act 1 played headless from the opening battle to the turning, every joint's map laid and every trigger chain run:
 * see docs/plan/2026-10-15-act1.md. The fights themselves are the game's; here a blow that names the hero stands in
 * for each one the chain waits on, and every line is read and every cutscene seen at once.
 */
class Act1Test {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Campaign CAMPAIGN = SETTINGS.campaign();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .filter(o -> !o.isEffectivelyDead()).toList();
    }

    private static GameObject hero(Dungeon.Session session) {
        return all(session.game(), CAMPAIGN.hero()).getFirst();
    }

    /** A new game as the front menu's campaign row starts it. */
    private static Dungeon.Session newGame() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().playing(Floors.named(CAMPAIGN.joints().getFirst().map(), 7L, SETTINGS, false));
        session.run().startWith(session.game(), CAMPAIGN.hero());
        session.game().runHeadless(1);
        return session;
    }

    /** The story played on for {@code frames}: every line read the moment it is said, every cutscene seen at once. */
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

    /** Played on until {@code map} is laid, at most {@code frames}. */
    private static void playUntil(Dungeon.Session session, String map, int frames) {
        for (int i = 0; i < frames && !map.equals(session.run().getStageName()); i++) {
            play(session, 1);
        }
        assertEquals(map, session.run().getStageName(), "the chain went on to " + map);
    }

    private static void standIn(Dungeon.Session session, String zone) {
        var at = Stages.load(session.run().getStageName(), SETTINGS).story().zones().stream()
                .filter(z -> z.name().equals(zone)).findFirst().orElseThrow();
        var hero = hero(session);
        hero.setPosition(new Coord3D(at.middleX(CELL), at.middleY(CELL), hero.getPosition().z()));
        play(session, SETTINGS.story().dialogue().checkFrames() + 3);
    }

    private static void walkUpTo(Dungeon.Session session, String person) {
        var who = all(session.game(), person).getFirst();
        var hero = hero(session);
        hero.setPosition(new Coord3D(who.getPosition().x() + 12f, who.getPosition().y(), hero.getPosition().z()));
        play(session, SETTINGS.story().dialogue().checkFrames() + 3);
    }

    /** The hero deals {@code template} a blow that would kill anything. */
    private static void strike(Dungeon.Session session, String template) {
        var hero = hero(session);
        var foe = all(session.game(), template).getFirst();
        foe.getBody().damage(1e7f, DamageType.NORMAL, new Death(DeathType.NORMAL, hero.getId(), hero.getPlayerIndex()));
        play(session, 2);
    }

    /** A floor of a campaign descent finished: its boss down, the next floor laid behind it. */
    private static void throughTheFloor(Dungeon.Session session) {
        int depth = session.run().getDepth();
        strike(session, SETTINGS.bossKindAt(depth));
        for (int i = 0; i < 600 && session.run().getDepth() == depth; i++) {
            play(session, 1);
        }
    }

    @Test
    void everyJointsMapIsOneTheGameCanLay() {
        assertEquals(5, CAMPAIGN.joints().size());
        for (var joint : CAMPAIGN.joints()) {
            assertNotNull(Floors.named(joint.map(), 1L, SETTINGS, false).next(1), joint.map());
            if (CAMPAIGN.descent(joint.map()) == null) {
                assertEquals(List.of(), uz.dukeengine.dungeon.stage.StageCheck.problems(
                        Stages.load(joint.map(), SETTINGS), SETTINGS), joint.map());
            }
        }
    }

    @Test
    void actOnePlaysFromTheBattleToTheTurning() {
        var session = newGame();
        var game = session.game();
        var run = session.run();
        var script = run.getScript();
        assertEquals("jang", run.getStageName());

        // 1. The battle: the soldiers on his side, the opening seen, the light, the hero landed, the foe thrown back.
        var soldiers = all(game, "Soldier");
        assertEquals(4, soldiers.size(), "the kingdom's soldiers stand in the line");
        assertEquals(hero(session).getPlayerIndex(), soldiers.getFirst().getPlayerIndex(), "on his side");
        play(session, 30);
        assertTrue(script.flagged("HeroLanded"), "the opening seen, he lands");
        var landing = Stages.load("jang", SETTINGS).story().zones().stream().filter(z -> z.name().equals("Landing"))
                .findFirst().orElseThrow();
        assertTrue(landing.holds(hero(session).getPosition().x(), hero(session).getPosition().y(), CELL)
                || script.flagged("HeroLanded"), "in the light");
        var foe = all(game, "EnemyCommander").getFirst();
        strike(session, "EnemyCommander");
        assertTrue(all(game, "EnemyCommander").contains(foe), "the hero beats him, never kills him");
        play(session, 120);
        assertTrue(foe.isEffectivelyDead() || !all(game, "EnemyCommander").contains(foe), "the commander cuts him down");
        playUntil(session, "poytaxt", 400);

        // 2. The capital: the queen's welcome, her gold and her medallion, worn; the training begun at the gate.
        walkUpTo(session, "Act1Queen");
        play(session, 5);
        var bag = run.progressOf(hero(session).getPlayerIndex()).getLoot();
        assertEquals("Medallion", bag.at(bag.wornPlace("Tumor")).id(), "worn at his neck");
        assertTrue(run.goldOf(hero(session).getPlayerIndex()) >= 100, "her gold");
        assertTrue(run.getQuests().active().stream().anyMatch(q -> q.quest().equals("Training")));
        assertFalse(all(game, "Merchant").isEmpty(), "the merchant is there");
        standIn(session, "Gate");
        playUntil(session, "mashq", 60);

        // 3. The training: two floors of the descent, then back to the capital.
        assertEquals(1, run.getDepth());
        throughTheFloor(session);
        assertEquals(2, run.getDepth(), "a boss of a campaign's descent opens its next floor");
        throughTheFloor(session);
        playUntil(session, "poytaxt", 60);
        assertTrue(script.flagged("Trained"));
        assertEquals("Medallion", bag.at(bag.wornPlace("Tumor")).id(), "he still wears it");

        // 4. The war: the council, the gate, two keep floors.
        play(session, 5);
        assertTrue(run.getQuests().active().stream().anyMatch(q -> q.quest().equals("ToWar")));
        standIn(session, "Gate");
        playUntil(session, "urush", 60);
        assertEquals(3, run.getDepth());
        assertNotNull(run.getMission(), "a keep floor: clear outside, the key, the gate, the boss");
        throughTheFloor(session);
        throughTheFloor(session);
        playUntil(session, "qora_qasr", 60);

        // 5. The castle: its lord's death is the turning, saved for Act 2, and the act ends.
        var saved = new ArrayList<String>();
        run.onSaveInto((slot, save) -> saved.add(slot + ":" + save.givenTo()));
        play(session, 5);
        strike(session, "DarkLord");
        play(session, 20);
        assertEquals("Act1Queen", run.medallionGivenTo(), "the medallion is back in her hand");
        assertTrue(run.isCursed(hero(session).getPlayerIndex()) || run.medallionGivenTo().equals("Act1Queen"));
        assertEquals(List.of("act1:Act1Queen"), saved, "the run saved for Act 2");
        assertFalse(all(game, "Act1Queen").isEmpty(), "she is seen, in a vision");
        play(session, 100);
        assertTrue(script.state().fired().contains("ActOneEnds"), "1-qism tugadi");
        assertEquals(DungeonRun.State.WON, run.getState());
    }

    @Test
    void aDeathInTheCampaignGoesBackToTheFloorHeEntered() {
        var session = newGame();
        play(session, 30);
        strike(session, "EnemyCommander");
        play(session, 120);
        playUntil(session, "poytaxt", 400);
        walkUpTo(session, "Act1Queen");
        int gold = session.run().goldOf(hero(session).getPlayerIndex());
        hero(session).getBody().damage(1e7f);
        play(session, SETTINGS.run().respawnDelayFrames() + 5);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState());
        assertEquals("poytaxt", session.run().getStageName(), "back in the capital, not the battle");
        assertTrue(session.run().getScript().flagged("HeroLanded"), "with the battle behind him");
        assertTrue(session.run().goldOf(hero(session).getPlayerIndex()) < gold || gold == 0,
                "as he came in: the queen's gift is hers to give again");
    }

    @Test
    void eachJointCanBeSkippedToForTesting() {
        for (int n = 1; n <= CAMPAIGN.joints().size(); n++) {
            var session = Dungeon.newSession(3L, SETTINGS);
            session.run().skipTo(n);
            session.game().runHeadless(3);
            var joint = CAMPAIGN.joints().get(n - 1);
            assertEquals(joint.map(), session.run().getStageName(), "--act1=" + n);
            assertEquals(CAMPAIGN.hero(), session.run().getHeroTemplate());
            for (var flag : joint.flags()) {
                assertTrue(session.run().getScript().flagged(flag), flag);
            }
            var bag = session.run().progressOf(hero(session).getPlayerIndex()).getLoot();
            for (var item : joint.items()) {
                assertEquals(item, bag.at(bag.wornPlace("Tumor")).id(), "worn: " + item);
            }
        }
    }

    @Test
    void aCampaignDescentSaveLoadsBackOntoItsFloors() {
        var session = Dungeon.newSession(3L, SETTINGS);
        session.run().skipTo(3);
        session.game().runHeadless(3);
        var save = SaveGame.read(session.run().saveNow().text(), "test");
        assertEquals("mashq", save.stage());
        var floors = Floors.resumed(save, SETTINGS);
        assertEquals("mashq", floors.stageName());
        assertTrue(floors.descends());
        assertEquals(0, floors.lastDepth(), "the story says where it ends");
    }
}
