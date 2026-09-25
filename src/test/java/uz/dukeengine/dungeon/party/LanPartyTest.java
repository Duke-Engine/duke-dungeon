package uz.dukeengine.dungeon.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.ai.HoldGround;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.MultiplayerSession;

/**
 * Two machines, one dungeon: a host and a guest meet through the lobby over a real socket, each builds the party's
 * match from the host's line, and the two worlds stay the same world frame after frame — with each player's hero,
 * and each player's orders heard by both.
 */
class LanPartyTest {

    private static final int PORT = 17811;
    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void twoMachinesPlayOneDungeon() throws Exception {
        var match = PartyMatch.endless(777L);
        var hosting = new CompletableFuture<MultiplayerSession>();
        Lobby.host(PORT, 2, match, hosting::complete, hosting::completeExceptionally);
        Thread.sleep(300); // the host listening before the guest calls
        var joining = new CompletableFuture<MultiplayerSession>();
        Lobby.join("127.0.0.1:" + PORT, 7777, joining::complete, joining::completeExceptionally);
        var hosted = hosting.get(10, TimeUnit.SECONDS);
        var joined = joining.get(10, TimeUnit.SECONDS);
        try {
            assertEquals(match, PartyMatch.read(joined.getScenarioSpec()), "the guest plays the host's match");
            var host = Dungeon.newPartySession(match, 2, hosted, SETTINGS, "Rogue");
            var guest = Dungeon.newPartySession(PartyMatch.read(joined.getScenarioSpec()), 2, joined, SETTINGS,
                    "Knight");

            var hostSums = new TreeMap<Integer, Long>();
            var guestSums = new TreeMap<Integer, Long>();
            boolean ordered = false;
            for (int attempt = 0; attempt < 40_000 && (frame(host.game()) < 240 || frame(guest.game()) < 240);
                    attempt++) {
                boolean moved = step(host.game(), hostSums) | step(guest.game(), guestSums);
                if (!ordered && frame(guest.game()) >= 90) {
                    // The guest's own order, said on his machine: both have to hear it.
                    guest.game().postCommand(PartyOrders.of(new HoldGround(2, true)));
                    ordered = true;
                }
                if (!moved) {
                    Thread.sleep(1);
                }
            }

            assertTrue(frame(host.game()) >= 240 && frame(guest.game()) >= 240, "both machines kept playing");
            assertFalse(hosted.isDesynced() || joined.isDesynced(), "and never came apart");
            var common = new TreeMap<>(hostSums);
            common.keySet().retainAll(guestSums.keySet());
            assertTrue(common.size() > 200, "compared over " + common.size() + " frames");
            for (var frame : common.keySet()) {
                assertEquals(hostSums.get(frame), guestSums.get(frame), "the worlds differ at frame " + frame);
            }

            assertEquals(1, host.game().getLocalPlayerIndex());
            assertEquals(2, guest.game().getLocalPlayerIndex());
            for (var game : new DukeGame[] {host.game(), guest.game()}) {
                assertNotNull(heroOf(game, 1, "Rogue"), "the host's hero, on both machines");
                assertNotNull(heroOf(game, 2, "Knight"), "and the guest's");
            }
            assertTrue(host.orders().isHolding(2), "the host heard the guest's order");
            assertTrue(guest.orders().isHolding(2), "as the guest did");
            assertFalse(host.orders().isHolding(1), "and it was his alone");
        } finally {
            hosted.close();
            joined.close();
        }
    }

    /**
     * And a fight is one fight on both: each player sends his hero to fight his way to the boss, and a minute of
     * blows, deaths, experience and whatever they leave behind comes out the same world on either machine.
     */
    @Test
    void aFightIsOneFightOnBothMachines() throws Exception {
        var match = PartyMatch.endless(4040L);
        var hosting = new CompletableFuture<MultiplayerSession>();
        Lobby.host(PORT + 1, 2, match, hosting::complete, hosting::completeExceptionally);
        Thread.sleep(300);
        var joining = new CompletableFuture<MultiplayerSession>();
        Lobby.join("127.0.0.1:" + (PORT + 1), 7777, joining::complete, joining::completeExceptionally);
        var hosted = hosting.get(10, TimeUnit.SECONDS);
        var joined = joining.get(10, TimeUnit.SECONDS);
        try {
            var host = Dungeon.newPartySession(match, 2, hosted, SETTINGS, "Rogue");
            var guest = Dungeon.newPartySession(match, 2, joined, SETTINGS, "Knight");
            var hostSums = new TreeMap<Integer, Long>();
            var guestSums = new TreeMap<Integer, Long>();
            int startingCount = -1;
            boolean sent = false;
            for (int attempt = 0; attempt < 200_000 && (frame(host.game()) < 1800 || frame(guest.game()) < 1800);
                    attempt++) {
                boolean moved = step(host.game(), hostSums) | step(guest.game(), guestSums);
                if (!sent && frame(host.game()) >= 30 && frame(guest.game()) >= 30) {
                    var boss = host.game().getLogic().getObjects().stream()
                            .filter(object -> object.getTemplate().name().equals(SETTINGS.bossKindAt(1)))
                            .findFirst().orElseThrow();
                    startingCount = monsters(host.game());
                    host.game().postCommand(PartyOrders.of(new uz.dukeengine.dungeon.ai.AttackMove(1,
                            boss.getPosition())));
                    guest.game().postCommand(PartyOrders.of(new uz.dukeengine.dungeon.ai.AttackMove(2,
                            boss.getPosition())));
                    sent = true;
                }
                if (!moved) {
                    Thread.sleep(1);
                }
            }

            assertTrue(frame(host.game()) >= 1800 && frame(guest.game()) >= 1800, "both machines kept playing");
            assertFalse(hosted.isDesynced() || joined.isDesynced(), "and never came apart");
            var common = new TreeMap<>(hostSums);
            common.keySet().retainAll(guestSums.keySet());
            for (var frame : common.keySet()) {
                assertEquals(hostSums.get(frame), guestSums.get(frame), "the worlds differ at frame " + frame);
            }
            assertTrue(monsters(host.game()) < startingCount || host.run().getDepth() > 1,
                    "monsters fell, or the floor was won: there was a fight for the comparison to be about");
        } finally {
            hosted.close();
            joined.close();
        }
    }

    /** The dungeon's creatures still standing: whatever has a body and is nobody's hero. */
    private static int monsters(DukeGame game) {
        return (int) game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() > 2 && object.getBody() != null
                        && !object.isEffectivelyDead())
                .count();
    }

    private static int frame(DukeGame game) {
        return game.getLogic() == null ? 0 : game.getLogic().getFrame();
    }

    /** One attempt at a frame; its checksum kept if it was taken. */
    private static boolean step(DukeGame game, Map<Integer, Long> sums) {
        int before = frame(game);
        game.runHeadless(1);
        int after = game.getLogic().getFrame();
        if (after == before) {
            return false;
        }
        sums.put(after, game.getLogic().checksum());
        return true;
    }

    private static uz.dukeengine.core.thing.GameObject heroOf(DukeGame game, int player, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() == player && object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }
}
