package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stage;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.dungeon.story.MapStory;
import uz.dukeengine.rpg.story.QuestLog;
import uz.dukeengine.game.DukeGame;

/** The run's quests go into a save and come back out of it; a save from before quests were saved starts them anew. */
class QuestSaveTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The demo map, starting its SIDE quest itself, so a load can be seen not to start it again. */
    private static Stage stage() {
        var talk = Stages.load("talk", SETTINGS);
        var story = talk.story();
        return new Stage(talk.id(), talk.name(), talk.description(), talk.difficulty(), talk.players(), talk.seed(),
                talk.floor(), talk.paint(), new MapStory(story.npcs(), story.zones(), story.items(),
                        List.of("LostLocket")));
    }

    private static GameObject named(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template)).toList();
    }

    /** Played a while: greeted, two skeletons down, the locket found. */
    private static SaveGame playedAndSaved() {
        var session = Dungeon.newStageSession(stage(), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        var commander = named(game, "Commander");
        var cameIn = named(game, SETTINGS.run().defaultHero()).getPosition();
        named(game, SETTINGS.run().defaultHero()).setPosition(new Coord3D(commander.getPosition().x() + 12f,
                commander.getPosition().y(), commander.getPosition().z()));
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() + 1);
        named(game, SETTINGS.run().defaultHero()).setPosition(cameIn); // away from her, so nothing is handed in
        for (var skeleton : all(game, "Skeleton").subList(0, 2)) {
            skeleton.getBody().setHealth(0f);
        }
        var locket = SETTINGS.loot().stream().filter(item -> item.id().equals("Locket")).findFirst().orElseThrow();
        session.progress().getLoot().take(locket, 0, 0);
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() + 1);
        return SaveGame.read(session.run().saveNow().text(), "test");
    }

    private static Dungeon.Session loaded(SaveGame save) {
        var session = Dungeon.newStageSession(stage(), SETTINGS);
        session.run().resume(save, Floors.ofStage(stage()));
        session.game().runHeadless(2);
        return session;
    }

    @Test
    void theQuestsGoThroughTheSaveText() {
        var quests = new QuestLog.State(List.of(new QuestLog.Active("WallSkeletons", 0, 3, 0),
                new QuestLog.Active("LostLocket", 1, 0, 0)), List.of("Walk"), List.of("CommanderGreets"));
        var save = new SaveGame(5L, "talk", 1, "Rogue", 0, List.of(), List.of(), quests);
        assertEquals(save, SaveGame.read(save.text(), "test"));
        var none = new SaveGame(5L, "talk", 1, "Rogue", 0, List.of(), List.of(),
                new QuestLog.State(List.of(), List.of(), List.of()));
        assertEquals(none, SaveGame.read(none.text(), "test"), "a run with no quests yet is still a run with quests");
        assertNull(SaveGame.read(new SaveGame(5L, null, 1, "Rogue", 0, List.of(), List.of()).text(), "test")
                .quests(), "a save written before quests were saved has none");
    }

    @Test
    void aLoadPutsTheQuestsBackAndDoesNotStartTheMapsAgain() {
        var save = playedAndSaved();
        assertEquals(List.of(new QuestLog.Active("LostLocket", 1, 0, 0), new QuestLog.Active("WallSkeletons", 0, 2, 0)),
                save.quests().active().stream().map(a -> new QuestLog.Active(a.quest(), a.step(), a.count(), 0))
                        .toList(), "the map's first, then hers, as they were started");

        var session = loaded(save);
        var log = session.run().getQuests();
        assertEquals(save.quests().active(), log.active(), "the locket is still found, the skeletons still two down");
        assertEquals(List.of("CommanderGreets"), log.heard());
        assertEquals(List.of(), log.said(), "nobody says again what was heard before the save");
        assertEquals(QuestLog.Mark.HAND_IN, session.run().getMarks().getFirst().mark(), "the locket waits on her");
        assertTrue(session.run().getTracker().entries().stream()
                .anyMatch(entry -> entry.steps().getFirst().text().equals("Skeletlarni qiring 2/5")));
    }

    @Test
    void aSaveFromBeforeQuestsWereSavedStartsTheMapsQuests() {
        var played = playedAndSaved();
        var old = new SaveGame(played.seed(), played.stage(), played.depth(), played.hero(), played.experience(),
                played.ranks(), played.bag());
        var log = loaded(old).run().getQuests();
        assertEquals(List.of("LostLocket"), log.active().stream().map(QuestLog.Active::quest).toList(),
                "the map starts its own again, and hers waits on her greeting");
        assertEquals(List.of(), log.heard());
    }
}
