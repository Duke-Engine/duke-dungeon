package uz.dukeengine.dungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.rpg.skill.SkillRanks;

/** A save keeps every generated thing exactly: its id is its roll, a stack's Value its count. */
class LootSaveTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final Items ITEMS = SHIPPED.items();

    @Test
    void aRareAnEpicAndAStackComeBackAsTheyWere() {
        var bag = SHIPPED.lootDrops().bag();
        var rare = ITEMS.loot(ITEMS.make(ITEMS.base("muz_uzuk"), Items.RARE, 9, 123456789L));
        var epic = ITEMS.gear(4, 10, Items.EPIC);
        var stack = ITEMS.potion(ITEMS.potion("hayot_shishasi"), 7, 99);
        bag.take(rare, 0, 0); // worn
        bag.take(epic, 0, 0);
        bag.take(stack, 0, 0);
        bag.take(ITEMS.loot(ITEMS.make(ITEMS.base("muz_uzuk"), Items.MAGIC, 3, 5L)), 0, 0); // a second ring, carried

        var text = SaveGame.of(1L, null, 1, "Rogue", 0, new SkillRanks(2), bag).text();
        var bagAgain = SHIPPED.lootDrops().bag();
        SaveGame.read(text, "test").restore(bagAgain, new SkillRanks(2), SHIPPED);

        assertEquals(bag.slots(), bagAgain.slots(), "every roll, worn and carried, where it was");
        assertEquals(rare, bagAgain.at(bagAgain.wornPlace("Uzuk")));
        assertEquals(7, bagAgain.slots().stream().filter(item -> item != null && ITEMS.potionOf(item) != null)
                .findFirst().orElseThrow().value(), "and the stack its seven");
        assertEquals(bag.attributes(SHIPPED.attributeRules()), bagAgain.attributes(SHIPPED.attributeRules()));
        assertEquals(bag.manaRegen() + bag.attackSpeedPercent() + bag.healthRegen(),
                bagAgain.manaRegen() + bagAgain.attackSpeedPercent() + bagAgain.healthRegen());
    }
}
