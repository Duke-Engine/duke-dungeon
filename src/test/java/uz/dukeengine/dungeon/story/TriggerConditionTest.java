package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/** A trigger's If: checked as its When is heard; false, it does not fire and its once is not spent. */
class TriggerConditionTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int CHECK = SETTINGS.story().dialogue().checkFrames();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static GameObject hero(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(SETTINGS.run().defaultHero())).findFirst().orElseThrow();
    }

    private static void to(DukeGame game, String map, String zone, DungeonSettings settings) {
        var at = Stages.load(map, settings).story().zones().stream().filter(z -> z.name().equals(zone))
                .findFirst().orElseThrow();
        var hero = hero(game);
        hero.setPosition(new Coord3D(at.middleX(CELL), at.middleY(CELL), hero.getPosition().z()));
        game.runHeadless(CHECK + 3);
    }

    private static Dungeon.Session session(String map, DungeonSettings settings) {
        var session = Dungeon.newStageSession(Stages.load(map, settings), settings);
        session.game().runHeadless(1);
        return session;
    }

    private static uz.dukeengine.rpg.item.LootBag bag(Dungeon.Session session) {
        return session.run().progressOf(hero(session.game()).getPlayerIndex()).getLoot();
    }

    private static uz.dukeengine.rpg.item.Loot medallion() {
        return SETTINGS.loot().stream().filter(item -> item.id().equals(SETTINGS.medallion().item())).findFirst()
                .orElseThrow();
    }

    @Test
    void theAltarCursesOnlyAHeroWearingTheMedallion() {
        var session = session("medalyon", SETTINGS);
        var game = session.game();
        var cameIn = hero(game).getPosition();
        to(game, "medalyon", "Altar", SETTINGS);
        assertFalse(session.run().getScript().state().fired().contains("Turning"), "no medallion: he walks by");

        // Carried, not worn: into the backpack.
        var bag = bag(session);
        bag.put(0, medallion());
        hero(game).setPosition(cameIn);
        game.runHeadless(CHECK + 1);
        to(game, "medalyon", "Altar", SETTINGS);
        assertFalse(session.run().getScript().state().fired().contains("Turning"), "in his pack: he walks by");

        // Worn: taken.
        bag.remove(0);
        bag.put(bag.wornPlace("Tumor"), medallion());
        hero(game).setPosition(cameIn);
        game.runHeadless(CHECK + 1);
        to(game, "medalyon", "Altar", SETTINGS);
        assertTrue(session.run().getScript().state().fired().contains("Turning"), "worn: the altar takes him");
    }

    @Test
    void flagsQuestsAndCreaturesAreAsked() {
        var settings = DungeonSettings.parse(Content.data() + "\n" + """
                Trigger
                  Name = AllHold
                  Map = talk
                  When = FLAG
                  Target = Go
                  If = [
                    Condition
                      Is = FLAG_UNSET
                      Target = Never
                    End,
                    Condition
                      Is = ALIVE
                      Target = Warden
                    End,
                    Condition
                      Is = QUEST_ACTIVE
                      Target = WallSkeletons
                    End
                  ]
                  Actions = [
                    Action
                      Act = FLAG
                      Target = Held
                    End
                  ]
                End

                Trigger
                  Name = OneFails
                  Map = talk
                  When = FLAG
                  Target = Go
                  If = [
                    Condition
                      Is = QUEST_DONE
                      Target = WallSkeletons
                    End
                  ]
                  Actions = [
                    Action
                      Act = FLAG
                      Target = Failed
                    End
                  ]
                End
                """);
        var session = session("talk", settings);
        session.run().getQuests().start("WallSkeletons");
        session.run().getScript().flag("Go");
        session.game().runHeadless(3);
        assertTrue(session.run().getScript().flagged("Held"), "every If held");
        assertFalse(session.run().getScript().flagged("Failed"), "one did not: it did not fire");
        assertFalse(session.run().getScript().state().fired().contains("OneFails"), "and its once is not spent");
    }

    @Test
    void anIfOfNothingIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(Content.data() + "\n" + """
                Trigger
                  Name = Bad
                  When = MAP_START
                  If = [
                    Condition
                      Is = WEARS
                      Target = NoSuchThing
                    End
                  ]
                  Actions = [
                    Action
                      Act = FLAG
                      Target = X
                    End
                  ]
                End
                """));
    }
}
