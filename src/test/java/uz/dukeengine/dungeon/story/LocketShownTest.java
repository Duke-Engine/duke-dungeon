package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Read;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.item.GroundItem;

/**
 * The owner's ask after his playtest: as the commander hands out the locket quest the game shows the chest — the
 * camera to it while she says where it is, and back — and only the chest glows, only while the quest wants it.
 */
class LocketShownTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int CHECK = SETTINGS.story().dialogue().checkFrames();
    private static final String GLOW = SETTINGS.story().dialogue().glowLook();

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template)).toList();
    }

    private static GameObject chest(DukeGame game) {
        return game.getLogic().getObjects().stream().filter(o -> {
            var lying = o.findModule(GroundItem.class);
            return lying != null && lying.getHolding() != null && lying.getHolding().id().equals("Locket");
        }).findFirst().orElseThrow();
    }

    /** What wore the glow over {@code frames}, by id. */
    private static Set<Integer> glowing(DukeGame game, int frames) {
        game.watch();
        var lit = new HashSet<Integer>();
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var event : game.getSnapshot().events()) {
                if (event instanceof EffectPlayed played && played.name().equals(GLOW) && played.riding() != null) {
                    lit.add(played.riding().value());
                }
            }
        }
        return lit;
    }

    private static Dungeon.Session greeted() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        var commander = all(game, "Commander").getFirst();
        var hero = all(game, SETTINGS.run().defaultHero()).getFirst();
        hero.setPosition(new Coord3D(commander.getPosition().x() + 12f, commander.getPosition().y(),
                commander.getPosition().z()));
        game.runHeadless(CHECK + 1);
        return session;
    }

    @Test
    void givingTheLocketQuestShowsTheChestAndComesBack() {
        var session = greeted();
        var game = session.game();
        assertFalse(session.run().inCutscene(), "not while her greeting is on the screen");
        game.runHeadless(60);
        assertFalse(session.run().inCutscene(), "however long he reads it");
        int hero = all(game, SETTINGS.run().defaultHero()).getFirst().getPlayerIndex();
        game.postCommand(PartyOrders.of(new Read(hero, session.run().getQuests().said().size())));
        game.runHeadless(2); // an order is heard a frame after it is posted
        assertTrue(session.run().inCutscene(), "read: she points the chest out");
        game.runHeadless(46);
        var chest = Stages.load("talk", SETTINGS).story().zones().stream().filter(z -> z.name().equals("Chest"))
                .findFirst().orElseThrow();
        assertEquals(chest.middleX(PathGrid.DEFAULT_CELL_SIZE), game.camera().current().x(), 0.5f, "on the chest");
        game.runHeadless(20);
        assertTrue(session.run().getQuests().said().contains("LocketHint"), "while she says where it is");
        game.runHeadless(20);
        assertFalse(session.run().inCutscene());
        assertFalse(game.camera().isDriven(), "and back to him");
    }

    @Test
    void onlyTheChestGlowsAndOnlyWhileTheQuestWantsIt() {
        var plain = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        plain.game().runHeadless(1);
        assertEquals(Set.of(), glowing(plain.game(), 70), "before the quest, nothing glows");

        var session = greeted();
        var game = session.game();
        var chest = chest(game);
        assertEquals(Set.of(chest.getId().value()), glowing(game, 70), "the chest, and nothing else");

        var locket = chest.findModule(GroundItem.class).take();
        session.progress().getLoot().take(locket, 0, 0);
        game.runHeadless(CHECK + 1);
        assertEquals(Set.of(), glowing(game, 70), "taken: the quest wants it no more");
    }

    @Test
    void aMachineThatNeverSaysItHasReadStillGetsTheCutsceneInTime() {
        var session = greeted();
        session.game().runHeadless(SETTINGS.story().dialogue().readWaitFrames() + 1);
        assertTrue(session.run().inCutscene() || session.run().getQuests().said().contains("LocketHint"),
                "a window that never answers holds it no longer than ReadWaitFrames");
    }
}
