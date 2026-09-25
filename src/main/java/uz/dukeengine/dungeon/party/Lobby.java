package uz.dukeengine.dungeon.party;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.util.function.Consumer;
import uz.dukeengine.game.MultiplayerSession;

/**
 * Where a party meets before it starts: a host waits on a port until everyone is in, a guest calls a host's address
 * and waits until the host starts.
 *
 * <p>Both wait on a thread of their own — the window has to go on drawing while a friend finds the address — and
 * say how it ended on that thread: the session, once the party is complete, or why it never will be. The handshake
 * and the lock-step behind it are the engine's ({@link MultiplayerSession}); what is here is only the waiting, and
 * the giving up.
 *
 * <p>Nothing in it is about a local network except the address it shows. A guest calls an address and a port, so a
 * host whose port is open to the internet is already reachable from anywhere; what playing online adds later is a
 * place to meet when nobody can open one, and that is a different thing to call, not a different lobby.
 */
public final class Lobby {

    private volatile ServerSocket listening;
    private volatile boolean cancelled;

    private Lobby() {
    }

    /**
     * Wait on {@code port} until {@code players - 1} guests have joined, then hand over the session. Every guest is
     * told {@code match} as it arrives.
     */
    public static Lobby host(int port, int players, PartyMatch match, Consumer<MultiplayerSession> complete,
            Consumer<Exception> failed) {
        var lobby = new Lobby();
        lobby.start("dungeon-host", () -> {
            try (var server = new ServerSocket(port)) {
                lobby.listening = server;
                if (lobby.cancelled) {
                    return null;
                }
                return MultiplayerSession.host(server, players, match.written(), null);
            }
        }, complete, failed);
        return lobby;
    }

    /** Call the host at {@code address} — {@code host} or {@code host:port} — and wait for it to start. */
    public static Lobby join(String address, int defaultPort, Consumer<MultiplayerSession> complete,
            Consumer<Exception> failed) {
        var lobby = new Lobby();
        lobby.start("dungeon-join", () -> {
            var called = Address.parse(address, defaultPort);
            return MultiplayerSession.join(called.host(), called.port());
        }, complete, failed);
        return lobby;
    }

    /**
     * Give up waiting. A host stops listening at once; a guest cannot take back a call the engine is making, so
     * the session it brings back, if it brings one, is closed rather than played.
     */
    public void cancel() {
        cancelled = true;
        var server = listening;
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // it was closing anyway
            }
        }
    }

    private interface Meeting {
        MultiplayerSession meet() throws IOException;
    }

    private void start(String name, Meeting meeting, Consumer<MultiplayerSession> complete,
            Consumer<Exception> failed) {
        var thread = new Thread(() -> {
            try {
                var session = meeting.meet();
                if (session == null) {
                    return;
                }
                if (cancelled) {
                    session.close();
                    return;
                }
                complete.accept(session);
            } catch (IOException | RuntimeException e) {
                if (!cancelled) {
                    failed.accept(e);
                }
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** Where a guest calls: a host's name or number, and a port. */
    public record Address(String host, int port) {

        /** {@code host} or {@code host:port}, the port {@code defaultPort} where none is written. */
        public static Address parse(String written, int defaultPort) {
            var text = written == null ? "" : written.strip();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("no address was given");
            }
            int colon = text.lastIndexOf(':');
            if (colon < 0 || text.indexOf(':') != colon) {
                return new Address(text, defaultPort); // no port, or an IPv6 address with none
            }
            try {
                int port = Integer.parseInt(text.substring(colon + 1));
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("a port is 1 to 65535, not " + port);
                }
                return new Address(text.substring(0, colon), port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + text.substring(colon + 1) + "' is not a port", e);
            }
        }
    }

    /**
     * The address others on this network reach this machine at — what a host reads out to its guests — or the
     * machine's own name where it has none on a local network.
     */
    public static String lanAddress() {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                var face = interfaces.nextElement();
                if (!face.isUp() || face.isLoopback() || face.isVirtual()) {
                    continue;
                }
                for (var address : java.util.Collections.list(face.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (IOException e) {
            return "127.0.0.1";
        }
    }
}
