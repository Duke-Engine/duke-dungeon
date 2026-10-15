package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.loot.ItemDrop;
import uz.dukeengine.rpg.item.GroundItem;

/**
 * The wilds' monsters (data/units/wilds_*.duke): a family a land -- forest, desert, highlands, marsh -- each with its
 * common one, its stronger one, one that shoots, an elite and a camp boss, and a camp of them in the open world.
 */
class WildsTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static List<GeneratedDungeon.Monster> placed() {
        return WorldMapWriter.stage(SETTINGS).floor().monsters();
    }

    /** Every land's family is written, never comes up on a floor of the descent, and moves by clips of its own. */
    @Test
    void everyLandHasItsFamily() {
        assertEquals(4, WorldMapWriter.Wild.values().length);
        for (var wild : WorldMapWriter.Wild.values()) {
            var names = new HashSet<>(List.of(wild.kinds));
            names.add(wild.boss);
            assertTrue(names.size() >= 5, wild + " is a family of " + names);
            boolean shoots = false;
            for (var name : names) {
                var kind = SETTINGS.monster(name);
                assertNotNull(kind, wild + "'s " + name + " has no Monster block");
                assertEquals(99, kind.minDepth(), name + " would come up in the descent");
                var look = SETTINGS.lookOf(kind);
                assertTrue(look.hasModel() && look.death() != null && look.walk() != null, name + " has no clips");
                shoots |= kind.keepFurthest() > 0f;
            }
            assertTrue(shoots, wild + " has nothing that shoots");
        }
    }

    /** An elite is its kind made bigger, tinted, harder and better-dropping, with an affix or two of the skill system. */
    @Test
    void anEliteIsItsKindGrownWithAffixes() {
        var rules = SETTINGS.items().rules();
        for (var wild : WorldMapWriter.Wild.values()) {
            var elite = SETTINGS.monster(wild.elite());
            var skills = SETTINGS.skillsFor(wild.elite());
            assertTrue(skills.size() >= 1 && skills.size() <= 2, wild.elite() + " has " + skills.size() + " affixes");
            assertTrue(rules.elites().contains(wild.elite()), wild.elite() + " does not drop as an elite");
            var base = List.of(wild.kinds).stream().map(SETTINGS::monster)
                    .filter(kind -> kind != elite && SETTINGS.lookOf(kind).model().equals(SETTINGS.lookOf(elite).model()))
                    .findFirst().orElseThrow(() -> new AssertionError(wild.elite() + " is no kind of the family grown"));
            assertTrue(SETTINGS.lookOf(elite).modelScale() > SETTINGS.lookOf(base).modelScale(), "not bigger");
            assertNotEquals(0xFFFFFF, SETTINGS.lookOf(elite).tint(), wild.elite() + " is not tinted");
        }
        for (var wild : WorldMapWriter.Wild.values()) {
            assertTrue(rules.campBosses().contains(wild.boss), wild.boss + " does not drop as a boss");
        }
    }

    /** Each land's camp stands in the world: its boss on the land's own ground, its elite and its family round him. */
    @Test
    void everyCampIsPlacedOnItsLand() {
        var monsters = placed();
        var regions = WorldMapWriter.regions(WorldMapWriter.SEED);
        for (var wild : WorldMapWriter.Wild.values()) {
            var boss = monsters.stream().filter(m -> m.kind().equals(wild.boss)).toList();
            assertEquals(1, boss.size(), wild.boss + " stands " + boss.size() + " times");
            var at = boss.getFirst().at();
            if (wild.region != null) {
                assertEquals(wild.region, regions[at.cellY() * WorldMapWriter.SIZE + at.cellX()], wild + "'s camp");
            }
            for (var kind : wild.kinds) {
                assertTrue(monsters.stream().anyMatch(m -> m.kind().equals(kind)
                                && Math.hypot(m.at().cellX() - at.cellX(), m.at().cellY() - at.cellY()) < 12),
                        kind + " is not in the " + wild + " camp");
            }
            for (var site : WorldMapWriter.SITES) {
                assertTrue(Math.hypot(site.x() - at.cellX(), site.y() - at.cellY()) >= site.radius() + 20,
                        wild + "'s camp is on " + site.zone() + "'s ground");
            }
        }
    }

    /** A camp's boss, killed, leaves what a map's boss does. */
    @Test
    void aCampBossDropsAsABoss() {
        var stage = Stages.load("world", SETTINGS);
        var game = uz.dukeengine.dungeon.Dungeon.newStageSession(stage, SETTINGS).game();
        game.runHeadless(1);
        var chief = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(WorldMapWriter.Wild.FOREST.boss)).findFirst().orElseThrow();
        assertNotNull(chief.findModule(ItemDrop.class), "he carries no drop");
        var lying = game.getLogic().getObjects().stream().filter(o -> o.findModule(GroundItem.class) != null).count();
        game.getLogic().destroyObject(chief);
        game.runHeadless(2);
        long after = game.getLogic().getObjects().stream().filter(o -> o.findModule(GroundItem.class) != null).count();
        assertEquals(SETTINGS.items().rules().bossDrops(), after - lying);
        assertFalse(SETTINGS.items().isElite(WorldMapWriter.Wild.FOREST.boss), "a boss, not an elite");
    }
}
