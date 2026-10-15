package uz.dukeengine.dungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.Stat;
import uz.dukeengine.rpg.skill.SkillEffect;

/** Generated things: rolled the same from the same seed, made again from their ids, by level, named and compared. */
class ItemsTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final Items ITEMS = SHIPPED.items();

    @Test
    void theShippedGameRollsThings() {
        assertTrue(ITEMS.active());
        assertEquals(35, ITEMS.bases().size());
        assertEquals(13, ITEMS.affixes().size());
        assertEquals(9, ITEMS.uniques().size(), "six found, three only crafted");
        assertEquals(19, ITEMS.potions().size(),
                "four old potions kept for old saves, nine meals and drinks, wood, and crafting's four and its scroll");
    }

    @Test
    void theSameSeedRollsTheSameThing() {
        for (long seed = 1; seed < 300; seed++) {
            for (var source : Items.Source.values()) {
                assertEquals(ITEMS.drop(seed, 6, source), ITEMS.drop(seed, 6, source));
            }
        }
        var ids = new HashSet<String>();
        for (long seed = 1; seed < 300; seed++) {
            ids.add(ITEMS.drop(seed, 6, Items.Source.NORMAL).id());
        }
        assertEquals(299, ids.size(), "every seed its own thing: no two ever join");
    }

    @Test
    void anIdMakesItsThingAgainExactly() {
        for (long seed = 1; seed < 400; seed++) {
            var item = ITEMS.drop(seed, 1 + (int) (seed % 15), Items.Source.BOSS);
            assertEquals(item, ITEMS.resolve(item.id()), item.id());
        }
        assertNull(ITEMS.made("Key"), "the data's own things are not generated");
        assertEquals("Key", ITEMS.resolve("Key").id());
        assertNull(ITEMS.resolve("nothing-at-all"));
    }

    @Test
    void anAffixRollsWithinItsRangeAtItsLevel() {
        var otkir = ITEMS.affixes().stream().filter(affix -> affix.name().equals("otkir")).findFirst().orElseThrow();
        assertEquals(2, Items.along(otkir.low(), 1));
        assertEquals(12, Items.along(otkir.low(), 15));
        assertEquals(24, Items.along(otkir.high(), 40), "past the top it rolls as at the top");
        var sword = ITEMS.base("qisqa_qilich");
        for (int level : new int[] {1, 8, 15}) {
            for (long seed = 1; seed < 200; seed++) {
                for (var affix : ITEMS.make(sword, Items.RARE, level, seed).affixes()) {
                    if (affix.stat().equals(Stat.ATTACK)) {
                        assertTrue(affix.value() >= Items.along(otkir.low(), level)
                                && affix.value() <= Items.along(otkir.high(), level), affix + " at " + level);
                    }
                }
            }
        }
        int early = 0;
        int late = 0;
        for (long seed = 1; seed < 200; seed++) {
            early += ITEMS.make(sword, Items.MAGIC, 1, seed).affixes().stream().mapToInt(Items.Rolled::value).sum();
            late += ITEMS.make(sword, Items.MAGIC, 15, seed).affixes().stream().mapToInt(Items.Rolled::value).sum();
        }
        assertTrue(late > early * 2, "deeper things roll higher: " + early + " then " + late);
    }

    @Test
    void aRareIsNamedFromTwoListsAndCarriesThreeOrFourAffixes() {
        var rules = ITEMS.rules();
        int fours = 0;
        for (long seed = 1; seed < 100; seed++) {
            var made = ITEMS.make(ITEMS.base("keng_qilich"), Items.RARE, 7, seed);
            var words = made.name().split(" ", 2);
            assertTrue(rules.firstNames().contains(words[0]) && rules.secondNames().contains(words[1]), made.name());
            long rolled = made.affixes().stream().filter(affix -> !affix.prefix().isEmpty()).count();
            assertTrue(rolled >= 3 && rolled <= 4, made.name() + ": " + made.affixes());
            assertEquals(rolled, made.affixes().stream().filter(affix -> !affix.prefix().isEmpty())
                    .map(Items.Rolled::prefix).distinct().count(), "never one affix twice");
            fours += rolled == 4 ? 1 : 0;
            var loot = ITEMS.loot(made);
            assertEquals(made.name(), loot.name());
            assertEquals(made.figure(), loot.value(), "the sword's own figure");
            long lifesteal = made.affixes().stream().filter(affix -> affix.stat().equals(Items.LIFESTEAL)).count();
            assertEquals(made.affixes().size() - lifesteal, loot.affixes().size(), "each affix the engine's, "
                    + "but lifesteal, which is a lent skill");
            assertEquals(lifesteal, loot.skills().stream().filter(skill -> skill.effect() == SkillEffect.LIFESTEAL)
                    .count());
            assertTrue(loot.icon().endsWith("sword_broad_rare.png"));
        }
        assertTrue(fours > 10, "four affixes now and then: " + fours);
        var magic = ITEMS.make(ITEMS.base("qisqa_qilich"), Items.MAGIC, 3, 5);
        assertTrue(magic.name().endsWith("qisqa qilich"), magic.name());
        assertEquals("Qisqa qilich", ITEMS.make(ITEMS.base("qisqa_qilich"), Items.COMMON, 3, 5).name());
    }

    @Test
    void dropsFollowTheirLevelAndSource() {
        int bossCommon = 0;
        int normalRare = 0;
        int bossRare = 0;
        for (long seed = 1; seed < 2000; seed++) {
            var boss = ITEMS.drop(seed, 4, Items.Source.BOSS);
            bossCommon += ITEMS.rarityOf(boss) == Items.COMMON ? 1 : 0;
            bossRare += ITEMS.rarityOf(boss) >= Items.RARE ? 1 : 0;
            normalRare += ITEMS.rarityOf(ITEMS.drop(seed, 4, Items.Source.NORMAL)) >= Items.RARE ? 1 : 0;
            var made = ITEMS.made(ITEMS.drop(seed, 3, Items.Source.ELITE).id());
            if (made.base() != null) {
                assertTrue(made.base().level() <= 3, "nothing deeper than its level: " + made.id());
            }
        }
        assertEquals(0, bossCommon, "a boss drops nothing common");
        assertTrue(bossRare > normalRare * 4, bossRare + " rare off bosses, " + normalRare + " off the rest");
        // No unique is found at level 1: an epic there is a rare.
        assertEquals(Items.RARE, ITEMS.rarityOf(ITEMS.gear(9, 1, Items.EPIC)));
        assertEquals(Items.EPIC, ITEMS.rarityOf(ITEMS.gear(9, 10, Items.EPIC)));
    }

    @Test
    void anEpicIsItsUniqueWithItsStory() {
        var made = ITEMS.unique(ITEMS.uniques().stream().filter(each -> each.name().equals("qonli_ahd"))
                .findFirst().orElseThrow(), 5, 77);
        var loot = ITEMS.loot(made);
        assertEquals("Qonli ahd", loot.name());
        assertEquals(Stat.ATTRIBUTE, loot.kind());
        assertEquals("Strength", loot.attribute());
        assertEquals(2, loot.value(), "the ring's best");
        assertTrue(loot.affixes().contains(new uz.dukeengine.rpg.item.Affix("Strength", 5)), "and five more, an affix");
        assertTrue(loot.skills().stream().anyMatch(skill -> skill.effect() == SkillEffect.DAMAGE_AURA));
        var lines = ITEMS.lines(loot).stream().map(Items.Line::text).toList();
        assertTrue(lines.contains("+8% Qon so'rish"), lines.toString());
        var story = ITEMS.lines(loot).stream().filter(line -> line.colour() == ITEMS.rules().loreColour()).toList();
        assertTrue(story.getFirst().text().startsWith("Har zarbadan"), "its story last, broken to the card");
        assertEquals(ITEMS.rules().loreColour(), ITEMS.lines(loot).getLast().colour());
    }

    @Test
    void theCardComparesWithWhatIsWorn() {
        var worn = new Loot("old", "Old", "", Stat.ATTACK, 10, 0, 1);
        var better = new Loot("new", "New", "", Stat.ATTACK, 14, 0, 1, "", Stat.ATTACK_SPEED, 6, 0, 1,
                uz.dukeengine.rpg.item.ItemUse.NONE, "", "Qurol");
        var lines = ITEMS.compare(better, worn);
        assertEquals(List.of("+4% Zarba", "+6% Hujum tezligi"), lines.stream().map(Items.Line::text).toList());
        assertTrue(lines.stream().allMatch(line -> line.colour() == ITEMS.rules().betterColour()));
        var back = ITEMS.compare(worn, better);
        assertEquals(List.of("-4% Zarba", "-6% Hujum tezligi"), back.stream().map(Items.Line::text).toList());
        assertTrue(back.stream().allMatch(line -> line.colour() == ITEMS.rules().worseColour()));
        assertTrue(ITEMS.compare(worn, worn).isEmpty(), "the same thing changes nothing");
    }

    @Test
    void rarerAndDeeperThingsCostMore() {
        var base = ITEMS.base("keng_qilich");
        int common = ITEMS.price(ITEMS.loot(ITEMS.make(base, Items.COMMON, 5, 1)));
        int magic = ITEMS.price(ITEMS.loot(ITEMS.make(base, Items.MAGIC, 5, 1)));
        int rare = ITEMS.price(ITEMS.loot(ITEMS.make(base, Items.RARE, 5, 1)));
        int deeper = ITEMS.price(ITEMS.loot(ITEMS.make(base, Items.RARE, 9, 1)));
        assertTrue(common < magic && magic < rare && rare < deeper, common + " " + magic + " " + rare + " " + deeper);
        assertEquals(-1, ITEMS.price(ITEMS.resolve("Key")), "the data's own are priced by the merchant");
        var potions = ITEMS.potion(ITEMS.potion("hayot_shishasi"), 3, 9);
        assertEquals(60, ITEMS.price(potions));
        assertNotNull(ITEMS.potionOf(potions));
        assertNotEquals(-1, ITEMS.rarityOf(ITEMS.gear(3, 3, Items.MAGIC)));
    }
}
