package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.rpg.story.Trigger;

/** The game writes the engine's words as its own constants (a switch wants them so): they must be the engine's. */
class WordsTest {

    @Test
    void theGamesWordsForTheEnginesAreTheEnginesWords() {
        assertEquals(Trigger.When.MAP_START, When.MAP_START);
        assertEquals(Trigger.When.ZONE, When.ZONE);
        assertEquals(Trigger.When.QUEST_STARTED, When.QUEST_STARTED);
        assertEquals(Trigger.When.QUEST_DONE, When.QUEST_DONE);
        assertEquals(Trigger.When.STEP_DONE, When.STEP_DONE);
        assertEquals(Trigger.When.HEARD, When.HEARD);
        assertEquals(Trigger.When.AFTER, When.AFTER);
        assertEquals(Trigger.When.FLAG, When.FLAG);
        assertEquals(Trigger.Is.FLAG_SET, Is.FLAG_SET);
        assertEquals(Trigger.Is.FLAG_UNSET, Is.FLAG_UNSET);
    }
}
