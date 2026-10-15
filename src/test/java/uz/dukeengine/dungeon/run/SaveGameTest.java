package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.skill.SkillRanks;

/** A saved run as text, and back: equal, or refused with a word that says why. */
class SaveGameTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    private static final SaveGame SAVED = new SaveGame(-8124402213L, null, 3, "Rogue", 420,
            List.of(new SaveGame.Rank('Q', 2), new SaveGame.Rank('W', 1)),
            List.of(new SaveGame.Item(0, "Gauntlet", 2, 9, 4), new SaveGame.Item(4, "Boots", 1, 3, 0)));

    @Test
    void aSaveReadsBackEqual() {
        assertEquals(SAVED, SaveGame.read(SAVED.text(), "test"));
    }

    @Test
    void aStageSaveKeepsItsStageName() {
        var stage = new SaveGame(5L, "the old keep", 2, "Mage", 0, List.of(), List.of());
        assertEquals(stage, SaveGame.read(stage.text(), "test"));
    }

    @Test
    void anotherVersionIsRefusedWithAClearWord() {
        var older = SAVED.text().replace("Version = " + SaveGame.VERSION, "Version = 0");
        var refused = assertThrows(SaveGame.Refused.class, () -> SaveGame.read(older, "slot1.duke"));
        assertTrue(refused.getMessage().contains("slot1.duke"), refused.getMessage());
        assertTrue(refused.getMessage().contains("version 0"), refused.getMessage());
    }

    @Test
    void brokenTextIsRefusedNotCrashedOn() {
        for (var broken : List.of("", "hello [", "Save\n  Version = 1\n  Seed = x\nEnd\n",
                "Save\n  Version = 1\nEnd\n", SAVED.text().substring(0, SAVED.text().length() / 2))) {
            var refused = assertThrows(SaveGame.Refused.class, () -> SaveGame.read(broken, "autosave.duke"));
            assertTrue(refused.getMessage().startsWith("autosave.duke"), refused.getMessage());
        }
    }

    @Test
    void numbersNoGameWritesAreRefused() {
        // Found by SaveFuzzTest: a level drawn as that many marks ran the heap out, a worth past an int's hundredths
        // overflowed the hero's figures as the floor was laid.
        var boots = new SaveGame(11L, null, 2, "Rogue", 0, List.of(), List.of(new SaveGame.Item(2, "Boots", 1, 3, 0)));
        var good = boots.text();
        for (var broken : List.of(good.replace("Level = 1", "Level = 2147483647"),
                good.replace("Value = 3", "Value = 2147483647"), good.replace("Extra = 0", "Extra = -2147483648"),
                good.replace("Depth = 2", "Depth = 0"), good.replace("Experience = 0", "Experience = -5"),
                good.replace("Slot = 2", "Slot = -3"))) {
            assertThrows(SaveGame.Refused.class, () -> SaveGame.read(broken, "slot2.duke"), broken);
        }
        assertEquals(boots, SaveGame.read(good, "slot2.duke"));
    }

    @Test
    void aBagAndASkillBookComeBackAsTheyWere() {
        var bag = SHIPPED.lootDrops().bag();
        var gauntlet = item("Gauntlet");
        var boots = item("Boots");
        bag.take(gauntlet, 0, 0); // worn
        bag.take(gauntlet.worth(5), 0, 0); // carried, in the backpack's first place
        bag.take(item("Key"), 0, 0); // carried, in its second
        bag.take(boots.worth(7), 0, 0); // worn
        bag.remove(0); // a gap, which comes back a gap
        assertEquals(gauntlet, bag.at(bag.wornPlace("Zirh")));
        var learnt = new SkillRanks(SHIPPED.progression().skillSpread());
        learnt.startWith(SHIPPED.skillsFor("Rogue"));
        var keys = SHIPPED.skillsFor("Rogue").stream().filter(skill -> !skill.isUltimate()).toList();
        learnt.raise(keys.get(0).key(), 5);
        learnt.raise(keys.get(0).key(), 5);
        learnt.raise(keys.get(1).key(), 5);

        var saved = SaveGame.read(SaveGame.of(1L, null, 1, "Rogue", SHIPPED.levelling().totalXpFor(5), learnt, bag)
                .text(), "test");
        var bagAgain = SHIPPED.lootDrops().bag();
        var learntAgain = new SkillRanks(SHIPPED.progression().skillSpread());
        saved.restore(bagAgain, learntAgain, SHIPPED);

        assertEquals(bag.slots(), bagAgain.slots(), "worn where it was worn, carried where it was carried");
        assertNull(bagAgain.at(0), "the gap is where it was");
        assertEquals(bag.attributes(SHIPPED.attributeRules()), bagAgain.attributes(SHIPPED.attributeRules()),
                "and what he wears acts as it did");
        for (var skill : SHIPPED.skillsFor("Rogue")) {
            assertEquals(learnt.rankOf(skill.key()), learntAgain.rankOf(skill.key()), "rank of " + skill.key());
        }
        assertEquals("", bagAgain.noteAt(0), "putting things back says nothing");
    }

    /**
     * A save of version 1 was written when his six slots all acted: each thing goes back as a pick-up would, worn
     * where its slot is free and otherwise carried — so what he had on is on, and a second of a kind is carried.
     */
    @Test
    void aSaveFromBeforeWornSlotsPutsOnWhatItCanAndCarriesTheRest() {
        var old = new SaveGame(1L, null, 1, "Rogue", 0, List.of(),
                List.of(new SaveGame.Item(0, "Gauntlet", 1, 3, 0), new SaveGame.Item(1, "Key", 1, 0, 0),
                        new SaveGame.Item(3, "Gauntlet", 2, 9, 2), new SaveGame.Item(5, "Ring", 1, 30, 0)))
                .text().replace("Version = " + SaveGame.VERSION, "Version = 1");
        var saved = SaveGame.read(old, "old.duke");
        var bag = SHIPPED.lootDrops().bag();
        saved.restore(bag, new SkillRanks(2), SHIPPED);

        assertEquals("Gauntlet", bag.at(bag.wornPlace("Zirh")).id(), "the first gauntlet put on");
        assertEquals(1, bag.at(bag.wornPlace("Zirh")).level());
        assertEquals("Key", bag.at(0).id(), "the key carried");
        assertEquals(2, bag.at(1).level(), "the second gauntlet carried, as it was");
        assertEquals(30, bag.at(bag.wornPlace("Uzuk")).value(), "the ring on");
        assertEquals("", bag.noteAt(0), "putting things back says nothing");
    }

    @Test
    void anItemTheDataNoLongerHasIsLeftOut() {
        var saved = new SaveGame(1L, null, 1, "Rogue", 0, List.of(),
                List.of(new SaveGame.Item(0, "NoSuchThing", 1, 1, 0), new SaveGame.Item(1, "Boots", 1, 3, 0)));
        var bag = new LootBag(6);
        saved.restore(bag, new SkillRanks(2), SHIPPED);
        assertNull(bag.at(0));
        assertEquals("Boots", bag.at(1).id());
    }

    private static uz.dukeengine.rpg.item.Loot item(String id) {
        return SHIPPED.loot().stream().filter(loot -> loot.id().equals(id)).findFirst().orElseThrow();
    }
}
