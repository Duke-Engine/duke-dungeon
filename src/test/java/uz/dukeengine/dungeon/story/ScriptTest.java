package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** Triggers are read and checked from data, fire on their event once (or again, with Repeat), wait out a delay. */
class ScriptTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.parse(Content.data() + "\n" + """
            Trigger
              Name = Door
              Map = here
              When = ZONE
              Target = Door
              Actions = [
                Action
                  Act = FLAG
                  Target = Opened
                End
              ]
            End

            Trigger
              Name = Again
              When = ZONE
              Target = Well
              Repeat = Yes
              Actions = [
                Action
                  Act = FLAG
                  Target = Wet
                End
              ]
            End

            Trigger
              Name = Later
              When = AFTER
              Target = Door
              Delay = 3
              Actions = [
                Action
                  Act = SAY
                  Target = AmbushLine
                End
              ]
            End

            Trigger
              Name = SecondStep
              When = STEP_DONE
              Target = WallSkeletons
              Step = 2
              Actions = [
                Action
                  Act = FLAG
                  Target = Two
                End
              ]
            End
            """);

    private static Exception refused(String blocks) {
        return assertThrows(RuntimeException.class, () -> DungeonSettings.parse(Content.data() + "\n" + blocks));
    }

    private static List<String> names(List<Trigger> due) {
        return due.stream().map(Trigger::name).toList();
    }

    @Test
    void theShippedStoryHasTheDemosTriggersAndCutscene() {
        var story = DungeonSettings.load().story();
        assertEquals(Trigger.When.ZONE, story.trigger("HallSeen").when());
        assertEquals(Trigger.Act.SPAWN, story.trigger("Ambush").actions().get(1).act());
        assertEquals(2, story.cutscene("ChestReveal").shots().size());
    }

    @Test
    void aTriggerOfNothingIsRefused() {
        var when = refused("""
                Trigger
                  Name = Odd
                  When = SOMETIME
                  Actions = [
                    Action
                      Act = FLAG
                      Target = X
                    End
                  ]
                End
                """);
        assertTrue(when.getMessage().contains("SOMETIME"), when.getMessage());
        var say = refused("""
                Trigger
                  Name = Mute
                  When = MAP_START
                  Actions = [
                    Action
                      Act = SAY
                      Target = NoSuchTalk
                    End
                  ]
                End
                """);
        assertTrue(say.getMessage().contains("NoSuchTalk"), say.getMessage());
        var after = refused("""
                Trigger
                  Name = Orphan
                  When = AFTER
                  Target = Nobody
                  Actions = [
                    Action
                      Act = FLAG
                      Target = X
                    End
                  ]
                End
                """);
        assertTrue(after.getMessage().contains("Nobody"), after.getMessage());
    }

    @Test
    void aZoneFiresOnceOnItsMapAndARepeatOnlyOnComingBackIn() {
        var script = new Script(SETTINGS.story());
        script.mapStarted("elsewhere");
        script.standing(List.of("Door"));
        assertEquals(List.of(), names(script.due()), "Door belongs to another map");
        script.mapStarted("here");
        script.standing(List.of("Door", "Well"));
        assertEquals(List.of("Door", "Again"), names(script.due()));
        script.standing(List.of("Door", "Well"));
        assertEquals(List.of(), names(script.due()), "standing in them is not coming in");
        script.standing(List.of());
        script.standing(List.of("Door", "Well"));
        assertEquals(List.of("Again"), names(script.due()), "Door fires once; the repeat again");
    }

    @Test
    void afterWaitsOutItsDelay() {
        var script = new Script(SETTINGS.story());
        script.mapStarted("here");
        script.standing(List.of("Door"));
        assertEquals(List.of("Door"), names(script.due()));
        assertEquals(List.of(), names(script.due()));
        assertEquals(List.of(), names(script.due()));
        assertEquals(List.of("Later"), names(script.due()), "three frames after Door");
    }

    @Test
    void aStepIsHeardByItsNumberAndTheStateComesBack() {
        var script = new Script(SETTINGS.story());
        script.heard(Trigger.When.STEP_DONE, "WallSkeletons", 1);
        assertEquals(List.of(), names(script.due()));
        script.heard(Trigger.When.STEP_DONE, "WallSkeletons", 2);
        assertEquals(List.of("SecondStep"), names(script.due()));
        script.flag("Two");
        script.mapStarted("here");
        script.standing(List.of("Door"));

        var again = new Script(SETTINGS.story());
        again.restore(script.state());
        assertEquals(script.state(), again.state());
        assertTrue(again.flagged("Two"));
        again.standing(List.of());
        again.standing(List.of("Door"));
        assertEquals(List.of("Door"), names(again.due()).subList(0, 1),
                "Door was waiting when saved, and fires; it does not fire twice");
        assertEquals(List.of(), names(again.due()).stream().filter("Door"::equals).toList());
    }
}
