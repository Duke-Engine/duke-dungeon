package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/**
 * Act 2 played headless in the open world, from the heroine's landing to the end of the final fight, the credits and
 * the front menu: see docs/plan/2026-10-15-act2.md. A blow that names her stands in for each fight the chain waits
 * on; every line is read and every cutscene seen at once.
 */
class Act2Test {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Campaign ACT2 = SETTINGS.campaign("act2");
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .filter(o -> !o.isEffectivelyDead()).toList();
    }

    private static GameObject heroine(Dungeon.Session session) {
        return all(session.game(), ACT2.hero()).getFirst();
    }

    private static Dungeon.Session act2(int joint, SaveGame carried) {
        var session = Dungeon.newSession(11L, SETTINGS);
        session.run().skipTo(ACT2, joint, carried);
        session.game().runHeadless(2);
        return session;
    }

    private static void play(Dungeon.Session session, int frames) {
        var game = session.game();
        for (int i = 0; i < frames; i++) {
            session.run().read(game.getLocalPlayerIndex(), 100_000);
            if (session.run().inCutscene()) {
                session.run().skipCutscene(game);
            }
            game.runHeadless(1);
        }
    }

    private static StaticMap.Zone zone(Dungeon.Session session, String name) {
        var map = session.run().getStageName();
        return Stream.concat(Stages.load(map, SETTINGS).story().zones().stream(), SETTINGS.placesOn(map).stream())
                .filter(z -> z.name().equals(name)).findFirst().orElseThrow();
    }

    private static void standIn(Dungeon.Session session, String name) {
        var at = zone(session, name);
        var her = heroine(session);
        her.setPosition(new Coord3D(at.middleX(CELL), at.middleY(CELL), her.getPosition().z()));
        play(session, SETTINGS.story().dialogue().checkFrames() * 2 + 3);
    }

    private static void walkUpTo(Dungeon.Session session, String person) {
        var who = all(session.game(), person).getFirst();
        var her = heroine(session);
        her.setPosition(new Coord3D(who.getPosition().x() + 12f, who.getPosition().y(), her.getPosition().z()));
        play(session, SETTINGS.story().dialogue().checkFrames() * 2 + 3);
    }

    private static void strike(Dungeon.Session session, GameObject foe) {
        var her = heroine(session);
        foe.getBody().damage(1e8f, DamageType.NORMAL, new Death(DeathType.NORMAL, her.getId(), her.getPlayerIndex()));
    }

    /** Every creature of the dungeon's standing in the zone struck down by her. */
    private static void clearOut(Dungeon.Session session, String name) {
        var at = zone(session, name);
        int her = heroine(session).getPlayerIndex();
        var foes = session.game().getLogic().getObjects().stream()
                .filter(o -> o.getBody() != null && !o.isEffectivelyDead() && o.getPlayerIndex() != her
                        && o.getPlayerIndex() != uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX
                        && at.holds(o.getPosition().x(), o.getPosition().y(), CELL))
                .toList();
        assertFalse(foes.isEmpty(), name + "'s camp holds somebody to fight");
        foes.forEach(foe -> strike(session, foe));
        play(session, 3);
    }

    private static boolean going(Dungeon.Session session, String quest) {
        return session.run().getQuests().active().stream().anyMatch(q -> q.quest().equals(quest));
    }

    @Test
    void actTwoPlaysFromTheLandingToTheMenu() {
        var session = act2(1, null);
        var game = session.game();
        var run = session.run();
        var script = run.getScript();
        assertEquals("world", run.getStageName());
        assertEquals("Rogue", run.getHeroTemplate(), "the heroine");
        assertTrue(script.flagged("act1Level=10"), "no Act 1 save: its hero taken at the act's DefaultLevel");

        // The landing: her card, the fall into the square, the commander, the city's talk.
        assertEquals("2-qism: Kamonchi", game.titleCardShowing().text());
        play(session, 60);
        assertTrue(script.flagged("HeroineArrived"));
        var arrival = zone(session, "Arrival");
        assertTrue(arrival.holds(heroine(session).getPosition().x(), heroine(session).getPosition().y(), CELL));
        assertFalse(all(game, "Act1Commander").isEmpty(), "the commander meets her");
        assertTrue(going(session, "Rumours"));
        walkUpTo(session, "Elder");
        assertTrue(going(session, "Witch"), "the elder sends her to the witch");

        // The witch: her price, the necromancer, her secret; the girl let out of her cage.
        walkUpTo(session, "BabaYaga");
        assertTrue(going(session, "WitchsPrice"));
        strike(session, all(game, "Necromancer").getFirst());
        play(session, 5);
        var bag = run.progressOf(heroine(session).getPlayerIndex()).getLoot();
        assertTrue(bag.slots().stream().anyMatch(item -> item != null && item.id().equals("CageKey")),
                "the cage's key was his");
        walkUpTo(session, "BabaYaga");
        assertTrue(script.flagged("SecretKnown"), "the queen's secret told");
        walkUpTo(session, "Damsel");
        play(session, 30);
        assertTrue(run.getQuests().finished().contains("CagedGirl"), "the girl is free");

        // The lands: each camp cleared, each changing something; all three open the castle.
        int gold = run.goldOf(heroine(session).getPlayerIndex());
        clearOut(session, "Yashil_dala");
        play(session, 5);
        assertTrue(script.flagged("FreedGreen"));
        assertFalse(all(game, "Merchant").isEmpty(), "a merchant on the freed meadow");
        clearOut(session, "Toshli_adir");
        clearOut(session, "Qumli_dasht");
        play(session, 5);
        assertTrue(script.flagged("LandsFreed"));
        assertTrue(run.goldOf(heroine(session).getPlayerIndex()) >= gold + 300, "the freed folk's thanks");
        assertTrue(going(session, "ToTheQueen"));

        // The queen's castle opens: on to her hall.
        standIn(session, "Malika_qasri");
        for (int i = 0; i < 300 && !"hall".equals(run.getStageName()); i++) {
            play(session, 1);
        }
        assertEquals("hall", run.getStageName());
        play(session, 5);
        var him = all(game, "CursedHero").getFirst();
        assertNotNull(him.findModule(uz.dukeengine.rpg.hero.LevelBonus.class), "grown to Act 1's hero's level");

        // The final fight: the intro, he is beaten not killed, she dies, the souls rise — the end.
        standIn(session, "HallDoor");
        play(session, 10);
        him.getBody().damage(1e8f);
        play(session, 10);
        assertTrue(script.flagged("CursedBeaten"), "spared");
        play(session, 5);
        strike(session, all(game, "QueenBoss").getFirst());
        var ended = new boolean[1];
        run.onTheEnd(() -> ended[0] = true);
        for (int i = 0; i < 2000 && !ended[0]; i++) {
            play(session, 1);
        }
        assertTrue(script.state().fired().contains("TheEnd"), "the ending played");
        assertTrue(script.state().fired().contains("EndCard"), "Oxiri");
        assertTrue(ended[0], "the credits rolled, and back to the front menu");
        assertEquals(DungeonRun.State.WON, run.getState());
    }

    @Test
    void actOnesSaveCarriesOver() {
        var act1 = new SaveGame(1L, "qora_qasr", 5, "Knight", 1200, List.of(), List.of(), null,
                new Script.State(List.of(), List.of("Trained", "LandsFreed"), List.of(), List.of()), 0)
                .withMedallion(80, "Act1Queen");
        var session = act2(1, act1);
        var flags = session.run().getScript().state().flags();
        assertTrue(flags.contains("Trained"), "Act 1's flags carried");
        int level = SETTINGS.levelling().levelFor(1200);
        assertTrue(flags.contains("act1Level=" + level), "and its hero's level: " + flags);
        assertEquals("Rogue", session.run().getHeroTemplate(), "her own hero, new");
        assertFalse(session.run().isCursed(heroine(session).getPlayerIndex()), "not cursed: his curse is not hers");
    }

    @Test
    void eachJointCanBeSkippedTo() {
        for (int n = 1; n <= ACT2.joints().size(); n++) {
            var session = act2(n, null);
            var joint = ACT2.joints().get(n - 1);
            assertEquals(joint.map(), session.run().getStageName(), "--act2=" + n);
            for (var quest : joint.quests()) {
                assertTrue(going(session, quest) || session.run().getQuests().finished().contains(quest),
                        "--act2=" + n + ": " + quest);
            }
            if (!joint.at().isBlank()) {
                var at = zone(session, joint.at());
                var her = heroine(session).getPosition();
                assertTrue(at.holds(her.x(), her.y(), CELL), "--act2=" + n + " puts her at " + joint.at());
            }
        }
    }

    @Test
    void theCastleStaysShutUntilTheLandsAreFreed() {
        var session = act2(3, null);
        standIn(session, "Malika_qasri");
        play(session, 30);
        assertEquals("world", session.run().getStageName(), "shut");
        assertTrue(session.run().getQuests().said().contains("CastleSealed"));
    }

    @Test
    void aFreedCampStaysFreedWhenTheWorldIsLaidAgain() {
        var session = act2(3, null);
        clearOut(session, "Yashil_dala");
        play(session, 5);
        assertTrue(session.run().getScript().flagged("FreedGreen"));
        heroine(session).getBody().damage(1e8f);
        play(session, SETTINGS.run().respawnDelayFrames() + 10);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState());
        assertTrue(session.run().getScript().flagged("FreedGreen"), "the checkpoint kept it");
        var at = zone(session, "Yashil_dala");
        int her = heroine(session).getPlayerIndex();
        assertTrue(session.game().getLogic().getObjects().stream().noneMatch(o -> o.getBody() != null
                && !o.isEffectivelyDead() && o.getPlayerIndex() != her
                && o.getPlayerIndex() != uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX
                && at.holds(o.getPosition().x(), o.getPosition().y(), CELL)), "its camp stays empty");
    }
}
