package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.AnimComposer;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Held;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.MonsterLook;

/**
 * The licensed models (CGTrader Royalty Free, in the git-ignored licensed-assets/ folder) are drawn where they are
 * shipped and the stand-ins where they are not -- so a fresh clone and CI play the same game, only plainer.
 */
class LicensedLookTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    /** Who wears which licensed model. */
    private static final Map<String, String> LICENSED = Map.ofEntries(
            Map.entry("Queen", "models/licensed/queen.glb"),
            Map.entry("QueenBoss", "models/licensed/queen.glb"),
            Map.entry("SkeletonSummoner", "models/licensed/ritualist.glb"),
            // The bestiary's (data/units/bestiary_*.duke).
            Map.entry("Bandit", "models/licensed/bandit.glb"),
            Map.entry("BanditVeteran", "models/licensed/bandit.glb"),
            Map.entry("Troll", "models/licensed/troll.glb"),
            Map.entry("Zombie", "models/licensed/zombie.glb"),
            Map.entry("CaveSpider", "models/licensed/spider.glb"),
            Map.entry("BroodMother", "models/licensed/spider.glb"),
            Map.entry("EarthGolem", "models/licensed/golem.glb"),
            Map.entry("IceGolem", "models/licensed/golem.glb"),
            Map.entry("FireGolem", "models/licensed/golem.glb"),
            Map.entry("LightGolem", "models/licensed/golem.glb"),
            Map.entry("ShadowGolem", "models/licensed/golem.glb"),
            Map.entry("Minotaur", "models/licensed/minotaur.glb"));

    private static boolean shipped(String path) {
        return LicensedLookTest.class.getClassLoader().getResource(path) != null;
    }

    private static MonsterLook look(String model) {
        return new MonsterLook(model, null, 7f, 0xFFFFFF, 90f, null, List.of(), "Idle", "Walk", "Attack", null,
                "Death", Held.NOTHING, null, 10f, "spine");
    }

    @Test
    void aLicensedModelThatIsNotShippedFallsBackToTheStandIn() {
        var standIn = look("models/characters/heroes/mage.glb");
        assertSame(standIn, MonsterLook.licensedOr(look("models/licensed/nobody-has-this.glb"), standIn));
        assertSame(standIn, MonsterLook.licensedOr(null, standIn));
        var shipped = look("models/characters/heroes/mage.glb");
        assertSame(shipped, MonsterLook.licensedOr(shipped, standIn), "a shipped model is drawn");
    }

    @Test
    void eachOneWearsItsLicensedModelExactlyWhenItIsShipped() {
        for (var entry : LICENSED.entrySet()) {
            var kind = SETTINGS.monsters().stream().filter(k -> k.name().equals(entry.getKey())).findFirst()
                    .orElseThrow();
            var model = SETTINGS.lookOf(kind).model();
            if (shipped(entry.getValue())) {
                assertEquals(entry.getValue(), model, entry.getKey() + " is shipped licensed but not drawn so");
            } else {
                assertTrue(model != null && !model.startsWith("models/licensed/"),
                        entry.getKey() + " names a licensed model that is not here: " + model);
                assertTrue(shipped(model), entry.getKey() + "'s stand-in " + model + " is not shipped");
            }
        }
    }

    /** With the licensed files present: every clip a licensed look names, and the summoning, is in its file. */
    @Test
    void aShippedLicensedModelCarriesEveryClipItsLookNames() {
        Assumptions.assumeTrue(LICENSED.values().stream().anyMatch(LicensedLookTest::shipped),
                "no licensed model in this checkout (licensed-assets/ absent)");
        var assets = new DesktopAssetManager(true);
        for (var kind : SETTINGS.monsters()) {
            var look = SETTINGS.lookOf(kind);
            if (look.model() == null || !look.model().startsWith("models/licensed/")) {
                continue;
            }
            var composer = composer(assets.loadModel(look.model()));
            assertNotNull(composer, look.model() + " carries no clips");
            for (var clip : new String[] {look.idle(), look.walk(), look.attack(), look.death()}) {
                assertNotNull(composer.getAnimClip(clip), look.model() + " has no " + clip);
            }
            for (var skill : SETTINGS.skillsFor(kind.name())) {
                if (skill.castAnim() != null && !skill.castAnim().isBlank()) {
                    assertNotNull(composer.getAnimClip(skill.castAnim()), look.model() + " has no " + skill.castAnim());
                }
            }
            assertTrue(look.walkStride() > 0f, kind.name() + "'s walk is not paced");
        }
    }

    private static AnimComposer composer(Spatial spatial) {
        var found = spatial.getControl(AnimComposer.class);
        if (found != null || !(spatial instanceof Node node)) {
            return found;
        }
        for (var child : node.getChildren()) {
            var deeper = composer(child);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }
}
