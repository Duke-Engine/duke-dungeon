package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Floors;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.run.SkipCutscene;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/** The demo's story, played headless: the cutscene in the far hall, the ambush, a skip, and a save. */
class TalkStoryTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int CHECK = SETTINGS.story().dialogue().checkFrames();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static GameObject hero(DukeGame game) {
        return all(game, SETTINGS.run().defaultHero()).getFirst();
    }

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template)).toList();
    }

    private static StaticMap.Zone zone(String name) {
        return Stages.load("talk", SETTINGS).story().zones().stream().filter(z -> z.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static void into(DukeGame game, String zone) {
        var at = zone(zone);
        var hero = hero(game);
        hero.setPosition(new Coord3D(at.middleX(CELL), at.middleY(CELL), hero.getPosition().z()));
    }

    private static Dungeon.Session session() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        return session;
    }

    @Test
    void comingIntoTheHallPlaysTheCutsceneOnce() {
        var session = session();
        var game = session.game();
        into(game, "HallDoor");
        game.runHeadless(CHECK + 1);
        assertTrue(session.run().inCutscene(), "the hall's cutscene");
        assertTrue(game.camera().isDriven(), "its camera");
        game.runHeadless(60);
        var chest = zone("Chest");
        assertEquals(chest.middleX(CELL), game.camera().current().x(), 0.5f, "flown to the chest");
        game.runHeadless(40);
        assertEquals("CommanderSeesChest", session.run().getQuests().said().getLast());
        game.runHeadless(20);
        assertFalse(session.run().inCutscene(), "over");
        assertFalse(game.camera().isDriven(), "and the camera his again");

        into(game, "Corridor");
        game.runHeadless(CHECK + 1);
        into(game, "HallDoor");
        game.runHeadless(CHECK + 1);
        assertFalse(session.run().inCutscene(), "once only");
    }

    @Test
    void anyPlayersSkipEndsIt() {
        var session = session();
        var game = session.game();
        into(game, "HallDoor");
        game.runHeadless(CHECK + 1);
        assertTrue(session.run().inCutscene());
        game.postCommand(PartyOrders.of(new SkipCutscene(hero(game).getPlayerIndex())));
        game.runHeadless(2);
        assertFalse(session.run().inCutscene());
        assertFalse(game.camera().isDriven());
        assertFalse(session.run().getQuests().said().contains("CommanderSeesChest"), "its line skipped with it");
    }

    @Test
    void herQuestHandedInSpringsTheAmbush() {
        var session = session();
        var game = session.game();
        var commander = all(game, "Commander").getFirst();
        var cameIn = hero(game).getPosition();
        hero(game).setPosition(new Coord3D(commander.getPosition().x() + 12f, commander.getPosition().y(),
                commander.getPosition().z()));
        game.runHeadless(CHECK + 1);
        hero(game).setPosition(cameIn);
        var before = all(game, "Skeleton").size();
        for (var skeleton : all(game, "Skeleton").subList(0, 5)) {
            skeleton.getBody().setHealth(0f);
        }
        game.runHeadless(2);
        hero(game).setPosition(new Coord3D(commander.getPosition().x() + 12f, commander.getPosition().y(),
                commander.getPosition().z()));
        game.runHeadless(CHECK + 3);
        assertTrue(session.run().getQuests().finished().contains("WallSkeletons"));
        assertTrue(session.run().getQuests().said().contains("AmbushLine"), "she warns him");
        var ambush = zone("Ambush");
        var risen = all(game, "Skeleton").stream()
                .filter(s -> ambush.holds(s.getPosition().x(), s.getPosition().y(), CELL)).count();
        assertEquals(3, risen, "three rise at the ambush");
        assertEquals(before - 5 + 3, all(game, "Skeleton").size());
    }

    @Test
    void whatHasFiredIsSavedAndDoesNotFireAgainOnLoad() {
        var session = session();
        var game = session.game();
        into(game, "HallDoor");
        game.runHeadless(CHECK + 1);
        game.postCommand(PartyOrders.of(new SkipCutscene(hero(game).getPlayerIndex())));
        game.runHeadless(2);
        var save = SaveGame.read(session.run().saveNow().text(), "test");
        assertTrue(save.script().fired().contains("HallSeen"));

        var loaded = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        loaded.run().resume(save, Floors.ofStage(Stages.load("talk", SETTINGS)));
        loaded.game().runHeadless(2);
        into(loaded.game(), "HallDoor");
        loaded.game().runHeadless(CHECK + 1);
        assertFalse(loaded.run().inCutscene(), "the hall was seen before the save");

        var old = new SaveGame(save.seed(), save.stage(), save.depth(), save.hero(), save.experience(), save.ranks(),
                save.bag(), save.quests());
        var fresh = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        fresh.run().resume(old, Floors.ofStage(Stages.load("talk", SETTINGS)));
        fresh.game().runHeadless(2);
        into(fresh.game(), "HallDoor");
        fresh.game().runHeadless(CHECK + 1);
        assertTrue(fresh.run().inCutscene(), "a save from before triggers were saved: none fired");
    }
}
