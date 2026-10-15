package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Hotkeys;
import uz.dukeengine.client3d.KeyMap;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.world.Controls.Scheme;

/**
 * The two control schemes' keys (docs/plan/2026-10-15-modern-controls.md): modern casts a hero's four on Q E R F and
 * leaves W A S D to his feet; classic is the keys as they always were. A skill keeps its own key as its name.
 */
class ModernControlsTest {

    private static DungeonSettings played(Scheme scheme) {
        var settings = DungeonSettings.load();
        settings.useScheme(scheme);
        return settings;
    }

    @Test
    void theFileIsClassicUntilTheEngineWalksHimAndTheMigrationIsQErf() {
        var shipped = DungeonSettings.load();
        assertEquals(Scheme.CLASSIC, shipped.scheme(), "modern waits for E74-E80");
        assertEquals(Map.of('Q', 'Q', 'W', 'E', 'E', 'R', 'R', 'F'), shipped.controls().modernKeys());
        assertEquals('W', shipped.letterOf('W'), "classic presses a skill on its own key");
    }

    /** Every hero's four, pressed on the modern letters, each asking for what its own key asked for in classic. */
    @Test
    void everyHeroCastsHisFourOnQErfInTheModernScheme() {
        var classic = played(Scheme.CLASSIC);
        var modern = played(Scheme.MODERN);
        for (var hero : modern.heroes()) {
            var before = Hotkeys.create();
            Main.aimsFor(before, classic, hero.name());
            var after = Hotkeys.create();
            Main.aimsFor(after, modern, hero.name());
            for (var skill : modern.skillsFor(hero.name())) {
                char letter = modern.letterOf(skill.key());
                assertEquals("QERF".charAt("QWER".indexOf(skill.key())), letter, hero.name() + "'s " + skill.key());
                assertEquals(before.aimOf(skill.key()), after.aimOf(letter), hero.name() + "'s " + skill.key());
                assertEquals(skill.name(), after.wordOf(letter));
            }
            for (char feet : "WASD".toCharArray()) {
                assertFalse(after.claimedKeys().contains(feet), hero.name() + ": " + feet + " is a step");
            }
        }
    }

    /** Modern takes the orders' letters off and W A S D off the camera; classic keeps both. */
    @Test
    void modernLeavesWasdToHisFeetAndClassicIsAsItWas() {
        var modern = Main.controls(played(Scheme.MODERN));
        for (char feet : "WASD".toCharArray()) {
            assertFalse(modern.claimedKeys().contains(feet), feet + " is a step, not an order or a skill");
            for (var pan : new KeyMap.Control[] {KeyMap.Control.PAN_UP, KeyMap.Control.PAN_LEFT,
                    KeyMap.Control.PAN_DOWN, KeyMap.Control.PAN_RIGHT}) {
                assertTrue(modern.keyMap().keysOf(pan).stream().noneMatch(key -> key.code() == Hotkeys.codeOf(feet)),
                        feet + " pans no camera in the modern scheme");
                assertFalse(modern.keyMap().keysOf(pan).isEmpty(), "the arrows still pan");
            }
        }
        var classic = Main.controls(played(Scheme.CLASSIC));
        assertEquals(Hotkeys.Aim.UNIT_OR_GROUND, classic.aimOf('A'));
        assertEquals(Hotkeys.Aim.OPEN_GROUND, classic.aimOf('F'), "classic F is still the walk order");
        assertTrue(classic.claimedKeys().contains('W'));
    }

    @Test
    void aModernLetterOnWalkingOrAnotherKeyIsRefused() {
        var data = Content.data();
        var onFoot = data.replace("\"W = E\"", "\"W = S\"");
        assertNotEquals(data, onFoot, "the premise: the shipped file presses W's skill on E");
        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(onFoot));
        assertTrue(refused.getMessage().contains("Controls"), refused.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("\"W = E\"", "\"W = C\"")), "C is the sheet's");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("\"W = E\", ", "").replace("Scheme = CLASSIC", "Scheme = MODERN")),
                "a slot on a step's key needs a letter of its own where modern is the file's");
    }
}
