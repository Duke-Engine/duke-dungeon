package uz.dukeengine.dungeon;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.Hotkeys;
import uz.dukeengine.client3d.Shell;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.Lobby;
import uz.dukeengine.dungeon.party.PartyMatch;
import uz.dukeengine.game.MultiplayerSession;

/**
 * The two ways a party meets from the menu: hosting one on this machine, and joining one on another.
 *
 * <p>Both are paths of the same stone menu the game alone walks, and end in a waiting room rather than a start:
 * the host picks how many, how and where, then who he is; a guest picks who he is, then says the host's address.
 * The waiting is {@link Lobby}'s, on a thread of its own; when the party is complete the match is built from what
 * the host settled — every machine building the same one — and the client is asked to play it.
 *
 * <p>Waiting is let go of when the player walks another way: taking any row of the question before this one, or
 * choosing his hero again, which waits afresh. The address a guest typed is remembered for the next evening.
 */
final class PartyFront {

    private static final Logger LOG = Logger.getLogger(PartyFront.class.getName());

    private final Dungeon.Session session;
    private final DungeonSettings settings;
    private final Visuals visuals;
    private final Hotkeys keys;
    private final Duke3D duke;
    private final BagScreen bag;

    /** The meeting under way, if any. */
    private volatile Lobby lobby;
    /** What the host has settled so far: the descent, unless a stage was chosen. */
    private volatile PartyMatch match;

    PartyFront(Dungeon.Session session, DungeonSettings settings, Visuals visuals, Hotkeys keys, Duke3D duke,
            BagScreen bag) {
        this.session = session;
        this.settings = settings;
        this.visuals = visuals;
        this.keys = keys;
        this.duke = duke;
        this.bag = bag;
    }

    /** Stop waiting for whoever was being waited for. */
    void cancel() {
        var waiting = lobby;
        lobby = null;
        if (waiting != null) {
            waiting.cancel();
        }
    }

    // ---- hosting ----

    /** How many heroes the party is, the host counted — and from there how, where and who. */
    Shell.Question host() {
        var party = settings.party();
        var rows = new java.util.ArrayList<Shell.Option>();
        for (int players = 2; players <= party.maxPlayers(); players++) {
            int count = players;
            rows.add(new Shell.Option(party.playersWord().formatted(count), "", () -> {
                cancel();
                match = null;
            }, hostHow(count)));
        }
        return new Shell.Question(party.howManyWord(), party.whoHint(), rows);
    }

    /** The descent or a stage, as the game alone asks it — settling the party's match rather than a run. */
    private Shell.Question hostHow(int players) {
        return Main.howToPlay(settings,
                Main.whoToPlay(session, settings, visuals, keys, hero -> host(players, hero), waitingFor(players)),
                () -> match = null,
                (listed, stage) -> match = PartyMatch.stage(listed.name()));
    }

    /** The host's waiting room: the address the others type, and how many it waits for. */
    private Shell.Question waitingFor(int players) {
        var party = settings.party();
        return new Shell.Question(party.waitingWord(), party.waitingHint().formatted(Lobby.lanAddress(), players),
                List.of());
    }

    /** Listen for the others; once every one of them is in, play. */
    private void host(int players, String hero) {
        cancel();
        // The seed is drawn here, once, and every guest is told it: a machine drawing its own would be in
        // another dungeon from the first frame.
        var chosen = match == null ? PartyMatch.endless(System.nanoTime()) : match;
        lobby = Lobby.host(settings.party().port(), players, chosen, net -> play(chosen, net, hero), this::tell);
    }

    // ---- joining ----

    /** Who he goes in as — and then the host's address, and a room to wait in until the host starts. */
    Shell.Question join() {
        var party = settings.party();
        return Main.whoToPlay(session, settings, visuals, keys, this::join,
                new Shell.Question(party.joiningWord(), party.joiningHint(), List.of()));
    }

    /**
     * Ask for the address — off the window's thread, since the question waits for an answer — then call it. The
     * host's line says what is being played; a line this build cannot read is refused here, with why.
     */
    private void join(String hero) {
        cancel();
        var asking = new Thread(() -> {
            var address = askAddress();
            if (address == null) {
                return;
            }
            lobby = Lobby.join(address, settings.party().port(), net -> {
                PartyMatch match;
                try {
                    match = PartyMatch.read(net.getScenarioSpec());
                } catch (IllegalArgumentException e) {
                    net.close();
                    tell(e);
                    return;
                }
                play(match, net, hero);
            }, this::tell);
        }, "dungeon-address");
        asking.setDaemon(true);
        asking.start();
    }

    /** The host's address as the player types it — {@code host} or {@code host:port} — or null for none. */
    private String askAddress() {
        var remembered = java.util.prefs.Preferences.userNodeForPackage(PartyFront.class);
        var last = remembered.get("address", "");
        var answer = new String[1];
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> answer[0] = javax.swing.JOptionPane.showInputDialog(
                    null, settings.party().addressPrompt(), last));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "the address could not be asked for", e);
            return null;
        }
        if (answer[0] == null || answer[0].isBlank()) {
            return null;
        }
        remembered.put("address", answer[0].strip());
        return answer[0].strip();
    }

    // ---- the party, met ----

    /** Build the party's match from what the host settled, with this machine in the seat it was given, and play. */
    private void play(PartyMatch match, MultiplayerSession net, String hero) {
        lobby = null;
        try {
            var party = Dungeon.newPartySession(match, net.getPlayerCount(), net, settings, hero);
            bag.show(party);
            duke.startMatch(party.game());
        } catch (RuntimeException e) {
            net.close();
            tell(e);
        }
    }

    /** Why the party did not meet, said where the player will see it rather than only in the log. */
    private void tell(Exception failure) {
        LOG.log(Level.WARNING, "the party could not meet", failure);
        var said = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(null, said,
                settings.party().failedWord(), javax.swing.JOptionPane.WARNING_MESSAGE));
    }
}
