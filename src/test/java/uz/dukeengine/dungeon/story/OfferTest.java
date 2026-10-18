package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * A side quest is offered, not given: walking past the person starts nothing; asking him says the offer and puts the
 * question; only an accepted offer starts its quest, a turned-down one waits to be asked again, and an accepted one is
 * not offered twice.
 */
class OfferTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Campaign CAMPAIGN = SETTINGS.campaign();

    private static boolean going(Dungeon.Session session, String quest) {
        return session.run().getQuests().active().stream().anyMatch(q -> q.quest().equals(quest));
    }

    private static void play(Dungeon.Session session, int frames) {
        for (int i = 0; i < frames; i++) {
            session.run().read(session.game().getLocalPlayerIndex(), 100_000);
            if (session.run().inCutscene()) {
                session.run().skipCutscene(session.game());
            }
            session.game().runHeadless(1);
        }
    }

    private static int count(Dungeon.Session session, String conversation) {
        return (int) session.run().getQuests().said().stream().filter(conversation::equals).count();
    }

    @Test
    void theBlacksmithOffersAndTheHeroAcceptsOrNot() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(CAMPAIGN, 2, null);
        session.game().runHeadless(2);
        var game = session.game();
        var run = session.run();
        var smith = game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals("Blacksmith"))
                .findFirst().orElseThrow();
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(run.getHeroTemplate())).findFirst().orElseThrow();
        int player = hero.getPlayerIndex();
        assertEquals(QuestLog.Mark.GIVE, run.getQuests().markOf("Blacksmith"), "the offer is marked over his head");

        // Walking up to him, and standing there, offers nothing and starts nothing.
        hero.setPosition(new Coord3D(smith.getPosition().x() + 12f, smith.getPosition().y(), hero.getPosition().z()));
        play(session, SETTINGS.story().dialogue().checkFrames() * 4);
        assertEquals(0, count(session, "BlacksmithAsks"), "not said to a hero who only came near");
        assertFalse(going(session, "Cellar"));

        // Asked: said, with the question; turned down, nothing starts and the mark stays.
        run.talkTo(game, player, smith.getId());
        assertEquals(1, count(session, "BlacksmithAsks"));
        assertFalse(going(session, "Cellar"), "not before he has answered");
        run.answer(player, run.getQuests().said().size(), false);
        assertFalse(going(session, "Cellar"), "turned down");
        assertEquals(QuestLog.Mark.GIVE, run.getQuests().markOf("Blacksmith"));

        // Asked again: the offer again, and accepted this time.
        run.talkTo(game, player, smith.getId());
        assertEquals(2, count(session, "BlacksmithAsks"), "offered again");
        run.answer(player, run.getQuests().said().size(), true);
        assertTrue(going(session, "Cellar"), "accepted");
        assertTrue(run.getQuests().heard().contains("BlacksmithAsks"));

        // Accepted once, it is not offered again.
        run.talkTo(game, player, smith.getId());
        assertEquals(2, count(session, "BlacksmithAsks"), "not offered twice");
    }

    @Test
    void anAnswerToNothingIsNothing() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(CAMPAIGN, 2, null);
        session.game().runHeadless(2);
        session.run().answer(session.game().getLocalPlayerIndex(), 99, true);
        session.run().answer(session.game().getLocalPlayerIndex(), 0, true);
        assertFalse(going(session, "Cellar"));
    }

    @Test
    void everyOfferIsOfferedByOnePersonWhoSaysItAndHasQuestsToGive() {
        for (var conversation : SETTINGS.story().conversations()) {
            if (conversation.offer()) {
                assertFalse(conversation.starts().isEmpty(), conversation.name() + " offers nothing");
                assertFalse(conversation.lines().isEmpty(), conversation.name() + " says nothing");
            }
        }
    }
}
