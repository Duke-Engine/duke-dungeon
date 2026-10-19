package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.dungeon.story.MapStory;
import uz.dukeengine.rpg.story.QuestLog;

/**
 * The owner's ask after MSI 1.0.39: a quest taken shows where to go -- on the world map, by an arrow round the hero,
 * and by a pillar of light at its place. The cellar's skeletons first: he took that quest and did not know where to go.
 */
class QuestMarkerTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static QuestLog.State killing(String quest) {
        return new QuestLog.State(List.of(new QuestLog.Active(quest, 0, 0, 0)), List.of(), List.of());
    }

    /** The cellar's KILL names its Where: with no skeleton in sight, the mark stands on the cellar. */
    @Test
    void theCellarsSkeletonsAreMarkedOnTheCellar() {
        var cellar = StaticMap.Zone.of("Yerto'la 121 453 2");
        var poytaxt = new MapStory(List.of(), List.of(cellar), List.of(), List.of());

        assertArrayEquals(new float[] {cellar.middleX(CELL), cellar.middleY(CELL)},
                QuestLogScreen.whereIs("Cellar", killing("Cellar"), SETTINGS.story(), poytaxt));
        assertEquals("Yerto'la", uz.dukeengine.dungeon.run.DungeonRun.placeOf(SETTINGS.story(), "Cellar", 0),
                "and its pillar of light stands there");
        assertTrue(SETTINGS.story().quest("Cellar").steps().getFirst().text().contains("yerto'la"),
                "the tracker says where the cellar is");
    }

    /** The cellar the quest names is a zone of the world's capital, where it is given (the seamless campaign). */
    @Test
    void theCellarIsAZoneOfTheCapital() {
        assertTrue(Stages.load("world", SETTINGS).story().zones().stream().anyMatch(z -> z.name().equals("Yerto'la")),
                "the capital has its cellar");
        assertFalse(SETTINGS.story().quest("Cellar").steps().getFirst().where().isBlank());
    }

    /** A KILL with its creature in sight is marked on the nearest of them to the hero. */
    @Test
    void aKillInSightIsMarkedOnTheNearest() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        // Stood by the skeletons, so they are in sight: the snapshot holds only what he sees.
        var one = game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals("Skeleton"))
                .findFirst().orElseThrow();
        game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(SETTINGS.run().defaultHero()))
                .findFirst().orElseThrow().setPosition(one.getPosition());
        game.runHeadless(2);
        var skeletons = game.getSnapshot().units().stream().filter(u -> u.templateName().equals("Skeleton")).toList();
        assertTrue(skeletons.size() >= 2, "the premise: skeletons in the snapshot");
        var near = skeletons.getFirst();

        var at = QuestLogScreen.whereIs("WallSkeletons", killing("WallSkeletons"), SETTINGS.story(),
                session.run().storyNow(), game.getSnapshot().units(), near.x() + 1f, near.y());
        assertNotNull(at);
        assertArrayEquals(new float[] {near.x(), near.y()}, at, "the nearest skeleton");
    }

    /** On the world's capital, the cellar quest taken, a pillar of light stands on the cellar. */
    @Test
    void theCellarStandsInAPillarOfLightWhileTheQuestWantsIt() {
        var session = Dungeon.newStageSession(Stages.load("world", SETTINGS), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        session.run().getQuests().start("Cellar");
        var cellar = Stages.load("world", SETTINGS).story().zones().stream()
                .filter(z -> z.name().equals("Yerto'la")).findFirst().orElseThrow();
        game.watch();
        boolean lit = false;
        for (int frame = 0; frame <= SETTINGS.items().rules().beamFrames() && !lit; frame++) {
            game.runHeadless(1);
            for (var event : game.getSnapshot().events()) {
                lit |= event instanceof EffectPlayed played && played.name().equals(SETTINGS.run().questBeam())
                        && Math.abs(played.where().x() - cellar.middleX(CELL)) < 0.5f
                        && Math.abs(played.where().y() - cellar.middleY(CELL)) < 0.5f;
            }
        }
        assertTrue(lit, "no pillar on the cellar in a whole BeamFrames");
    }

    /** A quest's place is never taken for the key: its pillar and its colour are its own. */
    @Test
    void aQuestsPlaceLooksUnlikeTheKey() {
        var run = SETTINGS.run();
        assertFalse(run.questBeam().isBlank());
        assertTrue(!run.questBeam().equals(run.keyBeam()), "the same pillar as the key");
        assertTrue(run.questColour() != SETTINGS.menu().torchColour(), "the key's gold");
    }

    /** On the minimap (E61): the cellar quest's target, a still diamond in the quest colour. */
    @Test
    void theCellarIsMarkedOnTheMinimap() {
        var session = Dungeon.newStageSession(Stages.load("world", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        session.run().getQuests().start("Cellar");
        session.game().runHeadless(2);
        var cellar = Stages.load("world", SETTINGS).story().zones().stream()
                .filter(z -> z.name().equals("Yerto'la")).findFirst().orElseThrow();

        var marks = MissionScreen.minimapMarks(session, SETTINGS);
        assertTrue(marks.contains(new uz.dukeengine.client3d.MinimapMark(cellar.middleX(CELL), cellar.middleY(CELL),
                SETTINGS.run().questColour())), "" + marks);
        assertTrue(MissionScreen.minimapMarks(null, SETTINGS).isEmpty(), "no match, no marks");
    }

    /** On the minimap (E61): the floor's key lying, a gold diamond that blinks, where it lies. */
    @Test
    void theKeyIsMarkedOnTheMinimapAndBlinks() {
        var session = Dungeon.newSession(21L, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        game.getLogic().getObjects().stream()
                .filter(o -> o.findModule(uz.dukeengine.rpg.mission.Mission.Counted.class) != null).toList()
                .forEach(o -> game.getLogic().destroyObject(o));
        game.runHeadless(1);
        var key = session.run().keyLying();
        assertNotNull(key, "the premise: the key dropped");

        assertTrue(MissionScreen.minimapMarks(session, SETTINGS).contains(new uz.dukeengine.client3d.MinimapMark(
                key.x(), key.y(), SETTINGS.menu().torchColour(), null, true)));
    }
}
