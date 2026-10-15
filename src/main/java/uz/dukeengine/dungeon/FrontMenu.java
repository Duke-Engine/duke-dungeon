package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.client3d.MenuSide;
import uz.dukeengine.client3d.Shell;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The front menu in the game's words, laid at a side: its rows, the client's fixed words said in Uzbek, the credits,
 * and the way a second game is started after the first was quit. See {@code docs/plan/2026-09-30-main-menu.md}.
 */
final class FrontMenu {

    private FrontMenu() {
    }

    /**
     * The shell the client is launched with: the game's rows on the front menu (continue, a new game, load, the
     * credits), then Settings and Quit — no Play — every fixed word, and the side.
     */
    static Shell shell(DungeonSettings settings, List<Shell.Option> frontRows, List<Shell.Option> pauseRows) {
        var menu = settings.mainMenu();
        var shell = Shell.create()
                .frontRows(frontRows)
                .entry(Shell.Entry.SETTINGS, menu.settingsWord())
                .entry(Shell.Entry.QUIT, menu.quitWord())
                .menuAt(MenuSide.valueOf(menu.side()), menu.margin())
                .pauseRows(pauseRows);
        // Boshqaruv: Zamonaviy (WASD) / Klassik (sichqoncha), kept by the client and read at the next launch
        // (Controls.remembered): the keys, the mouse and the camera change together, so not half of them live.
        var controls = settings.controls();
        shell.settingsRow(uz.dukeengine.dungeon.world.Controls.ROW, controls.schemeWord(), controls.choices(),
                uz.dukeengine.dungeon.world.Controls.choiceOf(controls.fileScheme()), chosen -> { });
        menu.said().forEach(shell::word);
        return shell;
    }

    /** The credits row: every asset's maker and licence, each opening a page of its own, ESC back. */
    static Shell.Option credits(DungeonSettings settings, String creditsMd) {
        var menu = settings.mainMenu();
        var rows = new ArrayList<Shell.Option>();
        for (var row : creditRows(creditsMd)) {
            rows.add(new Shell.Option(row[0], row[1], null, new Shell.Question(row[0], row[1], List.of())));
        }
        return new Shell.Option(menu.creditsWord(), menu.creditsBlurb(), null,
                new Shell.Question(menu.creditsTitle(), menu.creditsHint(), rows));
    }

    /**
     * The rows of CREDITS.md's asset tables (What | Who | Licence | Where), the engine's kit and this game's: each
     * {what, "who · licence"}, the markdown taken off and the what cut at its dash.
     */
    static List<String[]> creditRows(String creditsMd) {
        var rows = new ArrayList<String[]>();
        boolean inTable = false;
        for (var line : creditsMd.lines().toList()) {
            if (line.startsWith("## ") && !line.equals("## The kit") && !line.equals("## Duke Dungeon")) {
                break; // another game's assets
            }
            if (line.startsWith("| What |")) {
                inTable = true;
                continue;
            }
            if (!line.startsWith("|")) {
                inTable = false;
                continue;
            }
            if (!inTable || line.startsWith("|---")) {
                continue;
            }
            var cells = line.split("\\|");
            if (cells.length < 4) {
                continue;
            }
            var what = plain(cells[1]);
            int dash = what.indexOf(" — ");
            rows.add(new String[] {dash > 0 ? what.substring(0, dash) : what,
                    plain(cells[2]) + " · " + plain(cells[3])});
        }
        return rows;
    }

    private static String plain(String cell) {
        return cell.replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1").replace("**", "").replace("`", "").strip();
    }

    /**
     * The same front rows, for a match that is not the one the window opened with: every answer in their questions
     * that would start the game also asks the client to play {@code start}'s fresh match — a match is played once, so
     * after Quit to the main menu the next game is always a new one. A row with no question (Continue) starts its own
     * match and is left as it is; questions shared by two branches stay shared.
     */
    static List<Shell.Option> startingFresh(List<Shell.Option> rows, Runnable start) {
        var done = new IdentityHashMap<Shell.Question, Shell.Question>();
        return rows.stream().map(row -> row.next() == null ? row : new Shell.Option(row.label(), row.blurb(),
                row.taken(), fresh(row.next(), start, done), row.picture())).toList();
    }

    private static Shell.Question fresh(Shell.Question question, Runnable start,
            Map<Shell.Question, Shell.Question> done) {
        var made = done.get(question);
        if (made != null) {
            return made;
        }
        var options = new ArrayList<Shell.Option>();
        for (var option : question.options()) {
            if (option.next() != null) {
                options.add(new Shell.Option(option.label(), option.blurb(), option.taken(),
                        fresh(option.next(), start, done), option.picture()));
                continue;
            }
            var taken = option.taken();
            options.add(new Shell.Option(option.label(), option.blurb(), () -> {
                if (taken != null) {
                    taken.run();
                }
                start.run();
            }, null, option.picture()));
        }
        made = new Shell.Question(question.title(), question.hint(), options);
        done.put(question, made);
        return made;
    }
}
