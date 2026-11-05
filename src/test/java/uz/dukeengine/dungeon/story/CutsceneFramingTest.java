package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * A shot that names a zone but not how to look at it looks as the player's camera does, and played from the hero's
 * shoulder that is low and level: behind the town's 8 m walls, or in them (the Ritual shot stood in a gatehouse). So
 * every shot of Act I's cutscenes that flies to a place says its turn, its pitch and its zoom.
 */
class CutsceneFramingTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    /** The cutscenes of Act I (act1.duke) and of its turning (medallion.duke). */
    private static final List<String> ACT_ONE = List.of("Summoning", "Ritual", "Landing", "CapitalShown", "CastleShown",
            "Turned");

    private static boolean looks(Cutscene.Shot shot) {
        return !Float.isNaN(shot.pitch()) && !Float.isNaN(shot.zoom()) && !Float.isNaN(shot.turn());
    }

    @Test
    void everyShotOfActOneThatFliesToAPlaceSaysHowItLooksAtIt() {
        var bare = new ArrayList<String>();
        var found = new ArrayList<String>();
        for (var cutscene : SETTINGS.story().cutscenes()) {
            if (!ACT_ONE.contains(cutscene.name())) {
                continue;
            }
            found.add(cutscene.name());
            int at = 0;
            for (var shot : cutscene.shots()) {
                at++;
                boolean flies = !shot.zone().isBlank() || !shot.path().isEmpty();
                boolean pathLooks = !shot.path().isEmpty() && shot.path().stream()
                        .anyMatch(key -> !Float.isNaN(key.pitch()) && !Float.isNaN(key.zoom()));
                if (flies && !looks(shot) && !pathLooks) {
                    bare.add(cutscene.name() + " shot " + at);
                }
            }
        }
        assertTrue(found.containsAll(ACT_ONE), "every cutscene looked at: " + found);
        assertTrue(bare.isEmpty(), "shots that look as the player's camera does, from his shoulder: " + bare);
    }
}
