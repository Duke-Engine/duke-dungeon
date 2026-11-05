package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * Act 1 is the Knight's, whoever {@code DefaultHero} says walks in before anybody chooses (the Rogue, which the
 * endless descent and the stages still offer: their tests, and the saves, start him).
 */
class ActOneKnightTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void aNewActOneGameIsPlayedAsTheKnightAndAsksNobody() {
        var act = SETTINGS.campaign();
        assertEquals("Knight", act.hero());
        var session = Dungeon.newSession(11L, SETTINGS);
        var front = Main.howToPlay(session, SETTINGS, Main.looks(SETTINGS), Main.controls(SETTINGS));
        var row = front.options().stream().filter(option -> option.label().equals(act.word())).findFirst().orElseThrow();
        assertTrue(row.next().options().isEmpty(), "the first act's row asks who goes in, and it should not: " + row.next());

        session.run().skipTo(act, 1, null);
        session.game().runHeadless(1);
        assertEquals("Knight", session.run().getHeroTemplate());
        var names = session.game().getLogic().getObjects().stream().map(object -> object.getTemplate().name()).toList();
        assertTrue(names.contains("Knight"));
        assertFalse(names.contains("Rogue"), "the Rogue is in Act 1's world");
        assertFalse(names.contains("Mage"), "the Mage is in Act 1's world");
    }

    @Test
    void theMenuBackdropOpensOnTheKnightToo() {
        var scene = MenuScene.of(SETTINGS);
        assertEquals("Knight", scene.session().run().getHeroTemplate());
    }
}
