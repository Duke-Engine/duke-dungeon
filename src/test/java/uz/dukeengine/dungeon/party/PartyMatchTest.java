package uz.dukeengine.dungeon.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What a host settles reaches a guest as it was settled, and a line from another build is refused by name. */
class PartyMatchTest {

    @Test
    void theHostsSettlingIsReadBackAsItWasWritten() {
        for (var match : new PartyMatch[] {PartyMatch.endless(-4242424242L), PartyMatch.endless(0L),
                PartyMatch.stage("first"), PartyMatch.stage("the long dark")}) {
            assertEquals(match, PartyMatch.read(match.written()));
        }
    }

    @Test
    void aLineFromAnotherBuildIsRefusedWithAReason() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> PartyMatch.read("duke-dungeon party 2\nendless 5"));
        assertTrue(error.getMessage().contains("different build"), error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PartyMatch.read(""));
        assertThrows(IllegalArgumentException.class, () -> PartyMatch.read(PartyMatch.HEADER + "\nendless five"));
        assertThrows(IllegalArgumentException.class, () -> PartyMatch.read(PartyMatch.HEADER + "\nskirmish x"));
    }

    @Test
    void anAddressIsAHostAndAPort() {
        assertEquals(new Lobby.Address("192.168.1.23", 7777), Lobby.Address.parse(" 192.168.1.23 ", 7777));
        assertEquals(new Lobby.Address("192.168.1.23", 9000), Lobby.Address.parse("192.168.1.23:9000", 7777));
        assertEquals(new Lobby.Address("dungeon.example.org", 7777), Lobby.Address.parse("dungeon.example.org", 7777));
        assertEquals(new Lobby.Address("::1", 7777), Lobby.Address.parse("::1", 7777));
        assertThrows(IllegalArgumentException.class, () -> Lobby.Address.parse("", 7777));
        assertThrows(IllegalArgumentException.class, () -> Lobby.Address.parse("host:port", 7777));
        assertThrows(IllegalArgumentException.class, () -> Lobby.Address.parse("host:70000", 7777));
    }
}
