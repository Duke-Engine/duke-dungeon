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
import uz.dukeengine.dungeon.story.QuestLog;

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
        var cellar = StaticMap.Zone.of("Cellar 33 2 4 4");
        var poytaxt = new MapStory(List.of(), List.of(cellar), List.of(), List.of());

        assertArrayEquals(new float[] {cellar.middleX(CELL), cellar.middleY(CELL)},
                QuestLogScreen.whereIs("Cellar", killing("Cellar"), SETTINGS.story(), poytaxt));
        assertEquals("Cellar", uz.dukeengine.dungeon.run.DungeonRun.placeOf(SETTINGS.story(), "Cellar", 0),
                "and its pillar of light stands there");
        assertTrue(SETTINGS.story().quest("Cellar").steps().getFirst().text().contains("yerto'la"),
                "the tracker says where the cellar is");
    }

    /** The cellar the quest names is a zone of the capital, the map it is given on. */
    @Test
    void theCellarIsAZoneOfTheCapital() {
        assertTrue(Stages.load("poytaxt", SETTINGS).story().zones().stream().anyMatch(z -> z.name().equals("Cellar")),
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

    /** On the capital, the cellar quest taken, a pillar of light stands on the cellar. */
    @Test
    void theCellarStandsInAPillarOfLightWhileTheQuestWantsIt() {
        var session = Dungeon.newStageSession(Stages.load("poytaxt", SETTINGS), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        session.run().getQuests().start("Cellar");
        var cellar = Stages.load("poytaxt", SETTINGS).story().zones().stream()
                .filter(z -> z.name().equals("Cellar")).findFirst().orElseThrow();
        game.watch();
        boolean lit = false;
        for (int frame = 0; frame <= SETTINGS.items().rules().beamFrames() && !lit; frame++) {
            game.runHeadless(1);
            for (var event : game.getSnapshot().events()) {
                lit |= event instanceof EffectPlayed played && played.name().equals(SETTINGS.run().keyBeam())
                        && Math.abs(played.where().x() - cellar.middleX(CELL)) < 0.5f
                        && Math.abs(played.where().y() - cellar.middleY(CELL)) < 0.5f;
            }
        }
        assertTrue(lit, "no pillar on the cellar in a whole BeamFrames");
    }
}
