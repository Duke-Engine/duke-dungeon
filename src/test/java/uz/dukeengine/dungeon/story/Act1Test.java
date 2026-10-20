package uz.dukeengine.dungeon.story;

import uz.dukeengine.rpg.story.Trigger;
import uz.dukeengine.rpg.story.Merchant;
import uz.dukeengine.rpg.story.Script;
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
        session.run().skipTo(CAMPAIGN, 1, null);
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

    /** A zone of the map being played, its own or a place the story adds to it. */
    static uz.dukeengine.dungeon.map.StaticMap.Zone zone(String map, String name) {
        var all = new ArrayList<>(Stages.load(map, SETTINGS).story().zones());
        all.addAll(SETTINGS.placesOn(map));
        return all.stream().filter(z -> z.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
    }

    private static void standIn(Dungeon.Session session, String zone) {
        var at = zone(session.run().getStageName(), zone);
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
        assertEquals("world", run.getStageName(), "the act opens on the one world");

        // 1. The battle before the north gate: the soldiers on his side, the hero landed, the foe beaten and cut down.
        var soldiers = all(game, "Soldier");
        assertEquals(4, soldiers.size(), "the kingdom's soldiers stand in the line");
        assertEquals(hero(session).getPlayerIndex(), soldiers.getFirst().getPlayerIndex(), "on his side");
        throughTheWave(session);
        assertEquals("world", run.getStageName(), "no other map: the battle won, he stays on the world");
        assertTrue(all(game, "EnemyCommander").isEmpty(), "the enemy commander is dead");

        // 2. The palace: the queen's welcome, her gold and her medallion, worn; the training begun.
        standIn(session, "Saroy");
        walkUpTo(session, "Act1Queen");
        play(session, 5);
        var bag = run.progressOf(hero(session).getPlayerIndex()).getLoot();
        assertEquals("Medallion", bag.at(bag.wornPlace("Tumor")).id(), "worn at his neck");
        assertTrue(run.goldOf(hero(session).getPlayerIndex()) >= 100, "her gold");
        assertTrue(run.getQuests().finished().contains("FollowCommander"), "the commander has led him here");
        assertTrue(run.getQuests().active().stream().anyMatch(q -> q.quest().equals("Training")));
        assertFalse(all(game, "Merchant").isEmpty(), "the merchant is on the plaza");

        // 3. The old graves: the commander and the queen's people come up, each grave is struck down, then the raiser
        // they kept whole; he goes on alone.
        standIn(session, "Eski_qabrlar");
        play(session, 5);
        assertEquals(1, all(game, "Act1CommanderFighter").size(), "the commander fights beside him");
        assertEquals(3, all(game, "Soldier").size(), "the queen's soldiers come up");
        assertEquals(2, all(game, "KingdomArcher").size(), "her archers");
        assertEquals(1, all(game, "KingdomHealer").size(), "and a healer");
        for (var grave : all(game, "Grave")) {
            grave.getBody().setHealth(1f); // beaten: its UNYIELDING keeps it from falling by the blow
            play(session, 3);
        }
        assertTrue(all(game, "Grave").isEmpty(), "every grave struck down");
        if (!all(game, "GraveRaiser").isEmpty()) { // his people may have had it already
            strike(session, "GraveRaiser");
        }
        play(session, 5);
        assertTrue(script.flagged("Trained"), "the graves gone");
        assertTrue(script.flagged("WarBegun"), "and he goes on alone, the commander back to the city");
        assertTrue(all(game, "Soldier").isEmpty(), "her soldiers with her");
        assertTrue(all(game, "KingdomArcher").isEmpty() && all(game, "KingdomHealer").isEmpty());
        assertEquals(1, all(game, "Act1Commander").size(), "she walks back to the city herself");
        assertTrue(run.getQuests().active().stream().anyMatch(q -> q.quest().equals("ToWar")));
        assertEquals("world", run.getStageName(), "all of it on the world");

        // 4. The road north: into the Black Knight's camp, and so into his castle. Nothing of the world cleared, he
        // stands at his whole strength.
        int all = SETTINGS.graveyards().size() + SETTINGS.caves().size();
        assertEquals(SETTINGS.knightStrength().percent(all - 1, all), run.knightStrength(), "only the old graves gone");
        standIn(session, "Qora_qarorgoh");
        playUntil(session, "qora_qasr", 60);

        // 5. The castle: its lord's death is the turning, saved for Act 2, and the act ends.
        var saved = new ArrayList<String>();
        run.onSaveInto((slot, save) -> saved.add(slot + ":" + save.givenTo()));
        play(session, 5);
        strike(session, "DarkLord");
        play(session, 20);
        assertEquals("Act1Queen", run.medallionGivenTo(), "the medallion is back in her hand");
        assertEquals(List.of("act1:Act1Queen"), saved, "the run saved for Act 2");
        assertFalse(all(game, "Act1Queen").isEmpty(), "she is seen, in a vision");
        play(session, 100);
        assertTrue(script.state().fired().contains("ActOneEnds"), "1-qism tugadi");
        assertEquals(DungeonRun.State.WON, run.getState());
        assertEquals("1-qism tugadi", game.titleCardShowing().text(), "the act ends on its title card");
    }

    /**
     * The first wave beaten: the battle let go, the dead out of the graves struck down as they come, until the line's
     * two fall back (BattleWon).
     */
    static void throughTheWave(Dungeon.Session session) {
        playUntilFlag(session, "HeroLanded", 1200);
        for (int i = 0; i < 1200 && session.run().getQuests().active().stream()
                .noneMatch(q -> q.quest().equals("FirstWave")) && !session.run().getScript().flagged("BattleWon"); i++) {
            play(session, 1);
        }
        for (int i = 0; i < 60 && !session.run().getScript().flagged("BattleWon"); i++) {
            if (!all(session.game(), "Skeleton").isEmpty()) {
                strike(session, "Skeleton");
            }
            play(session, 2);
        }
        assertTrue(session.run().getScript().flagged("BattleWon"), "the wave beaten");
    }

    /** Through the first wave the commander fights beside him, on his side; the wave beaten, she leads him on. */
    @Test
    void theCommanderFightsTheFirstWaveBesideHim() {
        var session = newGame();
        var game = session.game();
        playUntilFlag(session, "HeroLanded", 1200);
        for (int i = 0; i < 1200 && session.run().getQuests().active().stream()
                .noneMatch(q -> q.quest().equals("FirstWave")); i++) {
            play(session, 1);
        }
        play(session, 3);
        var fighter = all(game, "Act1CommanderFighter");
        assertEquals(1, fighter.size(), "her sword out");
        assertEquals(hero(session).getPlayerIndex(), fighter.getFirst().getPlayerIndex(), "on his side");
        assertTrue(all(game, "Act1Commander").isEmpty(), "not the commander who only talks");
        var stood = new java.util.concurrent.atomic.AtomicReference<>(fighter.getFirst().getPosition());

        throughTheWave(session);
        play(session, 3);
        assertTrue(all(game, "Act1CommanderFighter").isEmpty(), "her sword away");
        assertEquals(1, all(game, "Act1Commander").size(), "the commander again, to lead him");
        var her = all(game, "Act1Commander").getFirst().getPosition();
        assertTrue(Math.hypot(her.x() - stood.get().x(), her.y() - stood.get().y()) < 200f,
                "where she fought, not somewhere else: " + her);
    }

    /** Played on until {@code flag} is set, at most {@code frames}. */
    static void playUntilFlag(Dungeon.Session session, String flag, int frames) {
        for (int i = 0; i < frames && !session.run().getScript().flagged(flag); i++) {
            play(session, 1);
        }
        assertTrue(session.run().getScript().flagged(flag), flag);
    }

    /** A shot's camera framing and a title are read and checked with the files. */
    @Test
    void theStoryRefusesATitleItDoesNotHave() {
        var wrong = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(uz.dukeengine.dungeon.content.Content.data() + "\n" + """
                        Trigger
                          Name = NoSuchCard
                          When = MAP_START
                          Actions = [
                            Action
                              Act = TITLE
                              Target = Nowhere
                            End
                          ]
                        End
                        """));
        assertTrue(wrong.getMessage().contains("Nowhere"), wrong.getMessage());
    }

    @Test
    void aDeathInTheCampaignGoesBackToItsLastCheckpoint() {
        var session = newGame();
        throughTheWave(session);
        standIn(session, "Saroy");
        walkUpTo(session, "Act1Queen");
        play(session, 5);
        assertTrue(session.run().getScript().flagged("Welcomed"));
        hero(session).getBody().damage(1e7f);
        play(session, SETTINGS.run().respawnDelayFrames() + 5);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState());
        assertEquals("world", session.run().getStageName(), "back on the world, not the battle");
        assertTrue(session.run().getScript().flagged("Welcomed"), "with the queen's welcome behind him");
        var bag = session.run().progressOf(hero(session).getPlayerIndex()).getLoot();
        assertEquals("Medallion", bag.at(bag.wornPlace("Tumor")).id(), "and her medallion on");
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
        session.run().skipTo(4);
        session.game().runHeadless(3);
        session.run().getScript().flag("WarBegun");
        var mouth = zone("world", "Gor_1");
        hero(session).setPosition(new Coord3D(mouth.middleX(CELL), mouth.middleY(CELL), hero(session).getPosition().z()));
        play(session, SETTINGS.story().dialogue().checkFrames() * 2 + 3);
        assertEquals("Gor_1", session.run().getStageName(), "down a cave's mouth");
        var save = SaveGame.read(session.run().saveNow().text(), "test");
        assertEquals("Gor_1", save.stage());
        var floors = Floors.resumed(save, SETTINGS);
        assertEquals("Gor_1", floors.stageName());
        assertTrue(floors.descends());
        assertEquals(0, floors.lastDepth(), "the story says where it ends");
    }

    /**
     * A save on a map the act left (docs/plan/2026-10-15-seamless-campaign.md, "Old saves") loads on the world at the
     * joint that took its place: the training descent's at the old graves, its hero and his things kept.
     */
    @Test
    void anOldSaveOnARetiredMapLoadsOnTheWorld() {
        for (var old : List.of("jang", "poytaxt", "mashq")) {
            var save = new SaveGame(5L, old, 1, CAMPAIGN.hero(), 333, List.of(), List.of(), null,
                    new Script.State(List.of(), List.of("HeroLanded"), List.of(), List.of()), 77);
            var floors = Floors.resumed(save, SETTINGS);
            assertEquals("world", floors.stageName(), old + " is the world now");
            var session = Dungeon.newSession(5L, SETTINGS);
            session.run().resume(save, floors);
            session.game().runHeadless(3);
            assertEquals("world", session.run().getStageName());
            assertTrue(session.run().getScript().flagged("Act1"), old + ": the act's world story on");
            assertTrue(session.run().getScript().flagged("HeroLanded"), old + ": its own flags kept");
            assertEquals(77, session.run().goldOf(hero(session).getPlayerIndex()), old + ": his gold kept");
            if (old.equals("mashq")) {
                var graves = zone("world", "Eski_qabrlar");
                var at = hero(session).getPosition();
                assertTrue(graves.holds(at.x(), at.y(), CELL), "the training's save starts at the old graves");
            }
        }
    }
}
