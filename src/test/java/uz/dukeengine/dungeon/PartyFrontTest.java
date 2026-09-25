package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.Shell;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The way in, as the menu walks it: who goes in first; alone is the path it always was, a host is asked how many
 * and then everything the game alone asks, and a guest who he is — and both end in a room to wait in rather than a
 * start. Built when the game opens, so a path that cannot be built is a game that cannot open.
 */
class PartyFrontTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static Shell.Question whoGoesIn(DungeonSettings settings) {
        var session = Dungeon.newSession(3L, settings);
        var visuals = Visuals.create();
        var keys = Main.controls(settings);
        var duke = Duke3D.of(session.game(), visuals);
        return Main.whoGoesIn(session, settings, visuals, keys, duke, new BagScreen(settings, duke));
    }

    /** The last question down a path, following the first row of each: one with no rows, or whose row starts. */
    private static Shell.Question endOf(Shell.Question question) {
        var at = question;
        while (!at.options().isEmpty() && at.options().getFirst().next() != null) {
            at = at.options().getFirst().next();
        }
        return at;
    }

    @Test
    void theFirstQuestionIsWhoGoesIn() {
        var party = SETTINGS.party();
        var who = whoGoesIn(SETTINGS);

        assertEquals(party.whoWord(), who.title());
        assertEquals(java.util.List.of(party.aloneWord(), party.hostWord(), party.joinWord()),
                who.options().stream().map(Shell.Option::label).toList());
    }

    @Test
    void aloneIsThePathItAlwaysWas() {
        var alone = whoGoesIn(SETTINGS).options().getFirst().next();
        assertEquals(SETTINGS.hud().chooseModeWord(), alone.title());
        assertNull(endOf(alone).options().getFirst().next(), "and taking a hero starts the game");
    }

    @Test
    void aHostIsAskedHowManyAndEndsWaitingForThem() {
        var party = SETTINGS.party();
        var howMany = whoGoesIn(SETTINGS).options().get(1).next();

        assertEquals(party.howManyWord(), howMany.title());
        assertEquals(party.maxPlayers() - 1, howMany.options().size(), "two heroes up to the most a party may be");
        assertEquals(party.playersWord().formatted(2), howMany.options().getFirst().label());
        var waiting = endOf(howMany);
        assertEquals(party.waitingWord(), waiting.title());
        assertTrue(waiting.hint().contains("2"), "it says how many it waits for: " + waiting.hint());
        assertTrue(waiting.options().isEmpty(), "and nothing starts from it but the party");
    }

    @Test
    void aGuestIsAskedWhoHeIsAndEndsWaitingForTheHost() {
        var party = SETTINGS.party();
        var who = whoGoesIn(SETTINGS).options().get(2).next();

        assertEquals(SETTINGS.heroes().size(), who.options().size(), "every hero the game has");
        assertEquals(party.joiningWord(), who.options().getFirst().next().title());
    }

    @Test
    void aBuildWhosePartiesAreOneHeroAsksWhatItAlwaysAsked() {
        var shipped = uz.dukeengine.dungeon.content.Content.data();
        assertTrue(shipped.contains("  MaxPlayers = 4\n"), "the shipped party is no longer four");
        var alone = DungeonSettings.parse(shipped.replace("  MaxPlayers = 4\n", "  MaxPlayers = 1\n"));
        assertEquals(SETTINGS.hud().chooseModeWord(), whoGoesIn(alone).title());
    }
}
