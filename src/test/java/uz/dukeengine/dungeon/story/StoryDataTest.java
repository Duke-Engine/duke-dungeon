package uz.dukeengine.dungeon.story;

import uz.dukeengine.rpg.story.Speaker;
import uz.dukeengine.rpg.story.Conversation;
import uz.dukeengine.rpg.story.Npc;
import uz.dukeengine.rpg.story.Quest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.run.Tracker;

/** The story's blocks are read from the files, and a name that points at nothing is refused by a message. */
class StoryDataTest {

    private static Exception refused(String blocks) {
        return assertThrows(RuntimeException.class, () -> DungeonSettings.parse(Content.data() + "\n" + blocks));
    }

    @Test
    void theShippedStoryLoads() {
        var story = DungeonSettings.load().story();
        var quest = story.quest("WallSkeletons");
        assertNotNull(quest);
        assertEquals(uz.dukeengine.rpg.story.Quest.Kind.MAIN, quest.kind());
        assertEquals(Quest.Goal.KILL, quest.steps().getFirst().goal());
        assertEquals(5, quest.steps().getFirst().count());
        assertEquals("CommanderThanks", quest.steps().get(1).onDone().says());
        assertEquals(4, story.conversation("CommanderGreets").lines().size());
        assertEquals("Commander", story.npc("Commander").name());
        assertEquals(10, story.dialogue().checkFrames());
    }

    @Test
    void aLineByNobodyIsRefused() {
        var wrong = refused("""
                Conversation
                  Name = Odd
                  Lines = [
                    Line
                      Speaker = Stranger
                      Text = "Kim?"
                    End
                  ]
                End
                """);
        assertTrue(wrong.getMessage().contains("Stranger"), wrong.getMessage());
    }

    @Test
    void aMissingConversationIsRefused() {
        var wrong = refused("""
                Npc
                  Name = Skeleton
                  Talk = NoSuchTalk
                End
                """);
        assertTrue(wrong.getMessage().contains("NoSuchTalk"), wrong.getMessage());
    }

    @Test
    void aStepOfAnUnknownKindIsRefused() {
        var wrong = refused("""
                Quest
                  Name = Fly
                  Title = Uch
                  Steps = [
                    Step
                      Goal = FLY
                      Target = Skeleton
                      Text = Uch
                    End
                  ]
                End
                """);
        assertTrue(wrong.getMessage().contains("FLY"), wrong.getMessage());
    }

    @Test
    void aKillOfNothingAndAQuestStartedFromNowhereAreRefused() {
        var kill = refused("""
                Quest
                  Name = Ghosts
                  Title = Arvohlar
                  Steps = [
                    Step
                      Goal = KILL
                      Target = Ghost
                      Text = Arvohlar
                    End
                  ]
                End
                """);
        assertTrue(kill.getMessage().contains("Ghost"), kill.getMessage());
        var start = refused("""
                Conversation
                  Name = Hint
                  Lines = [
                    Line
                      Speaker = Hero
                      Text = Hmm
                    End
                  ]
                  Starts = [NoSuchQuest]
                End
                """);
        assertTrue(start.getMessage().contains("NoSuchQuest"), start.getMessage());
    }

    @Test
    void aZoneIsARectangleOrACircle() {
        var box = StaticMap.Zone.of("Gate 2 3 4 2");
        assertTrue(box.holds(25f, 35f, 10f));
        assertFalse(box.holds(65f, 35f, 10f));
        var round = StaticMap.Zone.of("Well 10 10 2");
        assertTrue(round.holds(105f + 19f, 105f, 10f));
        assertFalse(round.holds(105f + 21f, 105f, 10f));
        assertEquals("Gate 2 3 4 2", box.written());
        assertEquals("Well 10 10 2", round.written());
    }
}
