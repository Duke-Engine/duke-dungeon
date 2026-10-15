package uz.dukeengine.dungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.hero.CriticalStrike;
import uz.dukeengine.rpg.item.Affix;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.Stat;

/** A worn thing's affixes reach the hero (E43, E44): critical blows, quicker legs, a skill's rank. */
class AffixesTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final Items ITEMS = SHIPPED.items();

    private static GameObject hero(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(SHIPPED.run().defaultHero()))
                .findFirst().orElseThrow();
    }

    private static Loot ring(Affix... affixes) {
        return new Loot("test_ring", "Uzuk", "", Stat.ATTRIBUTE, 1, 0, 1, "Strength", "", 0, 0, 1, ItemUse.NONE, "",
                "Uzuk", List.of(), List.of(affixes));
    }

    @Test
    void aWornCriticalAffixPutsACriticalStrikeOnTheHeroAndTakingItOffEndsIt() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var bag = session.progress().getLoot();
        bag.take(ring(new Affix(Stat.CRIT_CHANCE, 20), new Affix(Stat.CRIT_DAMAGE, 50)), 0, 0);
        game.runHeadless(2);
        var critical = hero(game).findModule(CriticalStrike.class);
        assertNotNull(critical, "HeroProgress gave him critical blows");
        assertEquals(20, critical.chance());
        assertEquals(50, critical.damage());

        bag.remove(bag.wornPlace("Uzuk"));
        game.runHeadless(2);
        assertEquals(0, hero(game).findModule(CriticalStrike.class).chance(), "gone with the ring");
    }

    @Test
    void quickerLegsAndASkillsRankWhileWorn() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var progress = session.progress();
        float before = progress.figuresOf(hero(game), progress.found()).speed();
        var boots = new Loot("test_boots", "Etik", "", Stat.ATTRIBUTE, 1, 0, 1, "Agility", "", 0, 0, 1,
                ItemUse.NONE, "", "Etik", List.of(), List.of(new Affix(Stat.MOVE_SPEED, 20),
                        new Affix(Stat.SKILL_RANK + ":Q", 1)));
        progress.getLoot().take(boots, 0, 0);
        game.runHeadless(2);
        assertTrue(progress.figuresOf(hero(game), progress.found()).speed() > before * 1.15f, "a fifth quicker");
        assertEquals(1, progress.getLoot().skillRanks('Q'), "and a rank on Q's skill");
    }

    @Test
    void aCriticalChanceRollsWithHowMuchHarderItLands() {
        int seen = 0;
        for (long seed = 1; seed < 400; seed++) {
            var made = ITEMS.make(ITEMS.base("gurzi"), Items.RARE, 10, seed);
            if (made.affixes().stream().anyMatch(affix -> affix.stat().equals(Stat.CRIT_CHANCE))) {
                seen++;
                assertTrue(made.affixes().stream().anyMatch(affix -> affix.stat().equals(Stat.CRIT_DAMAGE)),
                        made.id() + ": a critical blow is a harder one");
            }
            var skill = made.affixes().stream().filter(affix -> affix.stat().startsWith(Stat.SKILL_RANK)).toList();
            skill.forEach(affix -> assertTrue(affix.stat().matches("SKILL_RANK:[QWE]"), affix.stat()));
        }
        assertTrue(seen > 0, "a mace may roll critical blows");
        var card = ITEMS.say(Stat.SKILL_RANK + ":Q", "", 1);
        assertEquals("+1 Q mahorati", card);
    }

    @Test
    void anEpicHasFourToSixAffixes() {
        for (var unique : ITEMS.uniques()) {
            assertTrue(unique.gives().size() >= 4 && unique.gives().size() <= 6, unique.name());
        }
    }
}
