package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The kingdom's archer looses her arrow as her bow's string is let go, not as she starts to draw it.
 *
 * <p>The realistic archer's file has no arrow and a string that does not move: the game draws the arrow. Her Attack
 * draws to the jaw at 0.9 s and looses at 1.2 s (the fastest her string hand moves, which MonsterClipFitTest
 * measures), so the shot leaves 36 frames after the wind-up begins, from just in front of her bow hand.
 */
class ArcherDrawTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void theArrowLeavesAtTheReleaseAndFromTheBow() {
        var arena = Dungeon.world("##############\n#............#\n#............#\n#............#\n##############\n", SETTINGS);
        var game = arena.game();
        // Her side's archer and a skeleton in her range: she sees it at once and starts to draw.
        game.spawn("KingdomArcher", arena.hero(), 40f, 20f);
        game.spawn("Skeleton", arena.dungeon(), 90f, 20f);
        game.runHeadless(1);
        var archer = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("KingdomArcher")).findFirst().orElseThrow();

        int drawnAt = -1;
        uz.dukeengine.core.thing.GameObject arrow = null;
        for (int frame = 1; frame <= 90 && arrow == null; frame++) {
            game.runHeadless(1);
            arrow = game.getLogic().getObjects().stream()
                    .filter(object -> object.getTemplate().name().equals("Arrow")).findFirst().orElse(null);
            drawnAt = frame;
        }
        assertNotNull(arrow, "she never loosed");
        assertTrue(drawnAt >= 34 && drawnAt <= 40, "the arrow left " + drawnAt + " frames after the first, not at her"
                + " release (36)");
        float ahead = arrow.getPosition().distance(archer.getPosition());
        assertEquals(4f, ahead, 1.5f, "it appears " + ahead + " units from her, not across her bow hand");
    }
}
