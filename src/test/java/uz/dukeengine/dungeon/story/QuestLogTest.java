package uz.dukeengine.dungeon.story;

import uz.dukeengine.rpg.story.Quest;
import uz.dukeengine.rpg.story.QuestLog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Tracker;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;

/** Each goal moves on its own event and only on it, a finished step does what it says, and quests chain. */
class QuestLogTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.parse(Content.data() + "\n" + """
            Quest
              Name = Walk
              Kind = SIDE
              Title = Yur
              Steps = [
                Step
                  Goal = REACH
                  Target = Well
                  Text = Quduqqa bor
                  OnDone = Done
                    Starts = Second
                  End
                End
              ]
            End

            Quest
              Name = Second
              Kind = MAIN
              Title = Ikkinchi
              Steps = [
                Step
                  Goal = TALK
                  Target = Commander
                  Text = Gaplash
                End
              ]
            End
            """);

    private static QuestLog log() {
        return new QuestLog(SETTINGS.story(), SETTINGS.loot(), SETTINGS.lootDrops().joinCount());
    }

    private static LootBag bag() {
        return SETTINGS.lootDrops().bag(); // a hero's, which wears things and carries the rest
    }

    private static Loot item(String id) {
        return SETTINGS.loot().stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow();
    }

    private static QuestLog.Active of(QuestLog log, String quest) {
        return log.active().stream().filter(a -> a.quest().equals(quest)).findFirst().orElseThrow();
    }

    @Test
    void killCountsOnlyItsOwnDeaths() {
        var log = log();
        var bag = bag();
        log.start("WallSkeletons");
        log.died("Runner", List.of(bag));
        log.reached("Well", bag);
        log.holding(List.of(bag));
        assertEquals(0, log.active().getFirst().count());
        for (int i = 0; i < 4; i++) {
            log.died("Skeleton", List.of(bag));
        }
        assertEquals(new QuestLog.Active("WallSkeletons", 0, 4, 0), log.active().getFirst());
        assertEquals("Skeletlarni qiring 4/5", log.entries().getFirst().steps().getFirst().text());
        log.died("Skeleton", List.of(bag));
        assertEquals(1, log.active().getFirst().step(), "five down, back to her");
    }

    @Test
    void talkingHandsInPaysAndFinishes() {
        var log = log();
        var bag = bag();
        assertTrue(log.talk("Commander", bag, false));
        assertEquals(List.of("WallSkeletons", "LostLocket"), log.active().stream().map(QuestLog.Active::quest).toList(),
                "her greeting starts both, in its order");
        assertFalse(log.talk("Commander", bag, false), "walking up again says nothing new");
        assertTrue(log.talk("Commander", bag, true), "asked, she greets again");
        assertEquals(0, of(log, "WallSkeletons").step(), "talking is not killing");
        for (int i = 0; i < 5; i++) {
            log.died("Skeleton", List.of(bag));
        }
        assertEquals(QuestLog.Mark.HAND_IN, log.markOf("Commander"));
        assertTrue(log.talk("Commander", bag, false));
        assertEquals(List.of("WallSkeletons"), log.finished());
        assertEquals(150, log.takeExperience());
        assertEquals(0, log.takeExperience(), "paid once");
        assertEquals("Gauntlet", bag.at(bag.wornPlace("Zirh")).id(), "the reward is on him, its slot free");
        assertEquals("CommanderThanks", log.said().getLast());
    }

    @Test
    void collectCountsTheBagsAndGiveTakesIt() {
        var log = log();
        var bag = bag();
        log.start("LostLocket");
        log.died("Skeleton", List.of(bag));
        log.talk("Commander", bag, false);
        assertEquals(0, of(log, "LostLocket").step());
        bag.take(item("Locket"), 0, 0);
        assertEquals("Locket", bag.at(0).id(), "a quest thing is carried in the backpack, worn nowhere");
        log.talk("Commander", bag, false);
        assertEquals(0, of(log, "LostLocket").step(), "held is not counted until the bags are looked at");
        log.holding(List.of(bag(), bag));
        assertEquals(1, of(log, "LostLocket").step(), "found");
        assertTrue(log.talk("Commander", bag, false), "handed in");
        assertTrue(log.finished().contains("LostLocket"));
        assertTrue(bag.slots().stream().noneMatch(each -> each != null && each.id().equals("Locket")),
                "she has it now");
    }

    @Test
    void reachMovesOnlyForItsZoneAndChainsTheNext() {
        var log = log();
        var bag = bag();
        log.start("Walk");
        log.reached("Gate", bag);
        log.died("Skeleton", List.of(bag));
        log.talk("Commander", bag, true);
        assertEquals(0, of(log, "Walk").step());
        log.reached("Well", bag);
        assertTrue(log.finished().contains("Walk"));
        assertEquals(QuestLog.Mark.HAND_IN, log.markOf("Commander"), "its OnDone started the next, which waits on her");
        log.start("Walk");
        assertTrue(log.active().stream().noneMatch(a -> a.quest().equals("Walk")), "a finished quest stays finished");
    }

    @Test
    void theTrackerListsMainFirstThenSideInStartOrder() {
        var log = log();
        log.start("Walk");
        log.start("LostLocket");
        log.start("WallSkeletons");
        assertEquals(List.of(uz.dukeengine.rpg.story.Quest.Kind.MAIN, uz.dukeengine.rpg.story.Quest.Kind.SIDE, uz.dukeengine.rpg.story.Quest.Kind.SIDE),
                log.entries().stream().map(uz.dukeengine.rpg.story.Tracked::kind).toList());
        assertEquals(List.of("Devordagi skeletlar", "Yur", "Yo'qolgan tumor"),
                log.entries().stream().map(uz.dukeengine.rpg.story.Tracked::title).toList());
    }

    @Test
    void aMarkSaysAQuestToGive() {
        var log = log();
        assertEquals(QuestLog.Mark.GIVE, log.markOf("Commander"));
        log.talk("Commander", bag(), false);
        assertEquals(QuestLog.Mark.NONE, log.markOf("Commander"));
    }
}
