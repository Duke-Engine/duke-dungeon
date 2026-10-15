package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.map.StaticMap.Zone;
import uz.dukeengine.rpg.skill.SkillEffect;

/**
 * The bestiary (data/units/bestiary_*.duke, docs/plan/2026-10-15-bestiary.md): eight more families with their camps,
 * and their small groups walking the land -- all in the world, on their own lands, and none where the campaign's
 * opening is fought before the world is opened.
 */
class BestiaryTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Stage WORLD = Stages.load("world", SETTINGS);
    /** The places the seamless campaign's gates keep the hero in until Act 1 is done. */
    private static final List<String> CAMPAIGN = List.of("Poytaxt", "Darvoza_oldi", "Yo'lak", "Eski_qabrlar");
    /** How far past their edge the campaign's places are kept clear, in cells: no fight is pulled into them. */
    private static final int CLEAR = 20;

    private static Set<String> bestiary() {
        var names = new LinkedHashSet<String>();
        for (var wild : WorldMapWriter.Wild.values()) {
            if (wild.ordinal() >= WorldMapWriter.Wild.ROADS.ordinal()) {
                names.add(wild.boss);
                names.addAll(List.of(wild.kinds));
            }
        }
        for (var beasts : WorldMapWriter.BEASTS) {
            names.addAll(List.of((String[]) beasts[3]));
        }
        return names;
    }

    private static double outside(Zone zone, int x, int y) {
        if (zone.radius() > 0) {
            return Math.hypot(x - zone.x(), y - zone.y()) - zone.radius();
        }
        double dx = Math.max(Math.max(zone.x() - x, 0), x - (zone.x() + zone.width()));
        double dy = Math.max(Math.max(zone.y() - y, 0), y - (zone.y() + zone.height()));
        return Math.hypot(dx, dy);
    }

    /** Every one is written, never comes up on a floor of the descent unless it is weighted to, and stands somewhere. */
    @Test
    void everyBestiaryMonsterIsWrittenAndPlaced() {
        var placed = WORLD.floor().monsters().stream().map(GeneratedDungeon.Monster::kind).toList();
        assertTrue(bestiary().size() >= 50, "the bestiary is " + bestiary().size());
        for (var name : bestiary()) {
            var kind = SETTINGS.monster(name);
            assertNotNull(kind, name + " has no Monster block");
            assertTrue(placed.contains(name), name + " stands nowhere in the world");
        }
    }

    /** The groups walk their own lands: none of them in the campaign's places or near them. */
    @Test
    void noBestiaryMonsterStandsInTheCampaignsClosedPlaces() {
        var names = bestiary();
        var problems = new ArrayList<String>();
        for (var zone : WORLD.story().zones()) {
            if (!CAMPAIGN.contains(zone.name())) {
                continue;
            }
            for (var monster : WORLD.floor().monsters()) {
                if (names.contains(monster.kind())
                        && outside(zone, monster.at().cellX(), monster.at().cellY()) < CLEAR) {
                    problems.add(monster.kind() + " at " + monster.at().cellX() + "," + monster.at().cellY()
                            + " by " + zone.name());
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.toString());
    }

    /** A camp's boss casts one to three skills of the skill system, each one it can cast at him or wears. */
    @Test
    void everyBossHasOneToThreeSkills() {
        for (var wild : WorldMapWriter.Wild.values()) {
            if (wild.ordinal() < WorldMapWriter.Wild.ROADS.ordinal()) {
                continue;
            }
            var skills = SETTINGS.skillsFor(wild.boss);
            assertTrue(skills.size() >= 1 && skills.size() <= 3, wild.boss + " has " + skills.size() + " skills");
            assertTrue(skills.stream().anyMatch(s -> !s.effect().isPassive()), wild.boss + " casts nothing");
            for (var skill : skills) {
                if (skill.effect() == SkillEffect.SUMMON) {
                    skill.summons().keySet().forEach(kind -> assertNotNull(SETTINGS.monster(kind), kind));
                }
            }
            assertEquals(99, SETTINGS.monster(wild.boss).minDepth(), wild.boss + " would come up in the descent");
        }
    }
}
