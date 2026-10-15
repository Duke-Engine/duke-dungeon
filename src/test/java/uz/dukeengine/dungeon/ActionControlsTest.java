package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Hotkeys;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.world.Controls;
import uz.dukeengine.dungeon.world.Controls.Scheme;

/**
 * The third scheme's keys (docs/plan/2026-10-15-action-controls.md): a hero's four on 1-4, the belt on 5-8, E left
 * for the interact, and everything else as modern until the engine's E90-E94 land. A skill keeps its own key as its
 * name: only the key pressed changes.
 */
class ActionControlsTest {

    private static DungeonSettings played(Scheme scheme) {
        var settings = DungeonSettings.load();
        settings.useScheme(scheme);
        return settings;
    }

    @Test
    void theMigrationIsOneToFourAndTheBeltFiveToEight() {
        var action = played(Scheme.ACTION);
        assertEquals('1', action.letterOf('Q'));
        assertEquals('2', action.letterOf('W'));
        assertEquals('3', action.letterOf('E'));
        assertEquals('4', action.letterOf('R'));
        assertEquals('V', action.letterOf('V'), "the dodge's own name, pressed on Space, is no slot");
        int belt = action.items().rules().beltSize();
        assertEquals(4, belt, "the owner said 5 6 7; the belt holds four, so 8 too");
        for (int i = 0; i < belt; i++) {
            assertEquals((char) ('5' + i), action.beltKey(i));
            assertEquals((char) ('1' + i), played(Scheme.MODERN).beltKey(i), "modern keeps the belt on 1-4");
        }
    }

    /** Every hero's four, pressed on 1-4, each asking for what its own key asked for in classic. */
    @Test
    void everyHeroCastsHisFourOnTheDigitsAndNothingClashes() {
        var classic = played(Scheme.CLASSIC);
        var action = played(Scheme.ACTION);
        for (var hero : action.heroes()) {
            var before = Hotkeys.create();
            Main.aimsFor(before, classic, hero.name());
            var keys = Main.controls(action);
            Main.aimsFor(keys, action, hero.name());
            for (var skill : action.skillsFor(hero.name())) {
                char digit = action.letterOf(skill.key());
                assertEquals("1234".charAt("QWER".indexOf(skill.key())), digit, hero.name() + "'s " + skill.key());
                assertEquals(before.aimOf(skill.key()), keys.aimOf(digit), hero.name() + "'s " + skill.key());
                assertEquals(skill.name(), keys.wordOf(digit), "the keys page names the skill, not the belt");
            }
            for (char free : "WASDQRFE".toCharArray()) {
                assertFalse(keys.claimedKeys().contains(free), hero.name() + ": " + free + " is not bound in action");
            }
            for (char belt : "5678".toCharArray()) {
                assertTrue(keys.claimedKeys().contains(belt), "the belt's " + belt);
            }
        }
    }

    @Test
    void actionPlaysAsModernUntilTheEngineLands() {
        assertTrue(Controls.walksOnKeys(Scheme.ACTION));
        assertTrue(Controls.walksOnKeys(Scheme.MODERN));
        assertFalse(Controls.walksOnKeys(Scheme.CLASSIC));
        var action = played(Scheme.ACTION);
        assertEquals(uz.dukeengine.client3d.MouseScheme.MODERN, Main.mouseOf(action));
        assertEquals(uz.dukeengine.client3d.CameraFollow.of(null), Main.looks(action).getCameraFollow());
    }

    /** The Settings row: a kept 0 or 1 is what it was, and the third choice is action. */
    @Test
    void theRowHasAThirdChoiceAndKeptChoicesStay() {
        var controls = DungeonSettings.load().controls();
        assertEquals(3, controls.choices().size());
        assertEquals(Scheme.MODERN, Controls.ofChoice(0));
        assertEquals(Scheme.CLASSIC, Controls.ofChoice(1));
        assertEquals(Scheme.ACTION, Controls.ofChoice(2));
        assertEquals(Scheme.MODERN, Controls.ofChoice(7), "a choice from nowhere is the default");
        for (var scheme : Scheme.values()) {
            assertEquals(scheme, Controls.ofChoice(Controls.choiceOf(scheme)));
        }
        assertTrue(controls.actionWord().contains("hozircha"), "the row says it plays as modern for now");
    }

    @Test
    void theInteractPromptNamesEveryContextOrder() {
        var controls = DungeonSettings.load().controls();
        assertEquals("[E] Gaplashish", controls.promptOf(uz.dukeengine.dungeon.party.PartyOrders.TALK, 'E'));
        for (var order : uz.dukeengine.dungeon.food.Food.WORDS) {
            assertNotEquals(null, controls.promptOf(order, 'E'), order);
        }
        for (var order : List.of(uz.dukeengine.dungeon.party.PartyOrders.PICK_UP,
                uz.dukeengine.dungeon.party.PartyOrders.TO_THE_GATE)) {
            assertNotEquals(null, controls.promptOf(order, 'E'), order);
        }
        assertNull(controls.promptOf("Nothing", 'E'));
    }

    @Test
    void anActionKeyOnAnotherKeysPlaceIsRefused() {
        var data = Content.data();
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("\"W = 2\"", "\"W = 5\"")), "5 is the belt's");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("\"W = 2\"", "\"W = E\"")), "a skill on a digit");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("ActionBeltKeys = \"5678\"", "ActionBeltKeys = \"567\"")),
                "a digit for each of the four stacks");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("InteractKey = E", "InteractKey = M")), "M is the map's");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("InteractKey = E", "InteractKey = W")), "W is a step");
    }
}
