package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Tracker;
import uz.dukeengine.dungeon.story.MapStory;
import uz.dukeengine.dungeon.story.QuestLog;
import uz.dukeengine.dungeon.story.Story;

/**
 * The quest log (L): the quests going, MAIN before SIDE, each with its steps -- the ones done ticked, the one to do
 * with its count -- and the finished ones folded under their heading; on the right the quest picked, its steps and its
 * description, and a button that opens the map on where it wants him, where it says. Only reading: the log is the
 * run's as the last frame left it ({@code DungeonRun.questsNow}), and every word is the data's.
 */
final class QuestLogScreen {

    /** One step as the log says it: its words, the count in them, and whether it is done. */
    record Line(String text, boolean done) {
    }

    /** One quest as the log lists it. */
    record Entry(String quest, Tracker.Kind kind, String title, List<Line> steps, String description,
            boolean finished) {
    }

    /** A place on the screen a click does something at: picks a quest, folds the finished, or opens the map. */
    record Hit(float x, float y, float width, float height, String quest, boolean fold, boolean map) {

        boolean holds(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private final DungeonSettings settings;
    /** The quest picked, by name; blank for the first going. */
    private String picked = "";
    /** Whether the finished quests are unfolded under their heading. */
    private boolean unfolded;
    private final List<Hit> hits = new ArrayList<>();

    QuestLogScreen(DungeonSettings settings) {
        this.settings = settings;
    }

    /**
     * Every quest of {@code state} as the log lists them: the ones going, MAIN then SIDE in the order begun, then the
     * finished in the order finished. A quest the files no longer have is left out.
     */
    static List<Entry> entries(QuestLog.State state, Story story) {
        var entries = new ArrayList<Entry>();
        for (var kind : Tracker.Kind.values()) {
            for (var going : state.active()) {
                var quest = story.quest(going.quest());
                if (quest == null || quest.kind() != kind) {
                    continue;
                }
                var steps = new ArrayList<Line>();
                for (int i = 0; i <= going.step() && i < quest.steps().size(); i++) {
                    var step = quest.steps().get(i);
                    steps.add(i < going.step() ? new Line(step.said(step.count()), true)
                            : new Line(step.said(going.count()), false));
                }
                entries.add(new Entry(quest.name(), kind, quest.title(), steps, quest.description(), false));
            }
        }
        for (var name : state.finished()) {
            var quest = story.quest(name);
            if (quest != null) {
                entries.add(new Entry(name, quest.kind(), quest.title(),
                        quest.steps().stream().map(step -> new Line(step.said(step.count()), true)).toList(),
                        quest.description(), true));
            }
        }
        return entries;
    }

    /**
     * Where the step {@code quest} is on wants him, in world units on the map {@code story} lays: a REACH's zone, a
     * TALK's or GIVE's person, a COLLECT's thing lying -- or null for a step with nowhere (a KILL, a thing not on this
     * map, a quest finished).
     */
    static float[] whereIs(String quest, QuestLog.State state, Story story, MapStory where) {
        var going = state.active().stream().filter(each -> each.quest().equals(quest)).findFirst().orElse(null);
        var known = story.quest(quest);
        if (going == null || known == null || going.step() >= known.steps().size()) {
            return null;
        }
        var step = known.steps().get(going.step());
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        switch (step.goal()) {
            case REACH -> {
                for (var zone : where.zones()) {
                    if (zone.name().equals(step.target())) {
                        return new float[] {zone.middleX(cell), zone.middleY(cell)};
                    }
                }
            }
            case TALK, GIVE, COLLECT -> {
                for (var placed : step.goal() == uz.dukeengine.dungeon.story.Quest.Goal.COLLECT ? where.items()
                        : where.npcs()) {
                    if (placed.kind().equals(step.target())) {
                        return new float[] {(placed.x() + 0.5f) * cell, (placed.y() + 0.5f) * cell};
                    }
                }
            }
            default -> {
            }
        }
        return null;
    }

    /** The quest picked: the one asked for while it is listed, else the first going, else nothing. */
    static Entry picked(List<Entry> entries, String asked) {
        return entries.stream().filter(entry -> entry.quest().equals(asked)).findFirst()
                .orElse(entries.stream().filter(entry -> !entry.finished()).findFirst()
                        .orElse(null));
    }

    void pick(String quest) {
        picked = quest;
    }

    String picked() {
        return picked;
    }

    List<Hit> hits() {
        return List.copyOf(hits);
    }

    /** A click at {@code (x, y)}: the hit it landed on, having picked or folded, or null for none. */
    Hit click(int x, int y) {
        for (var hit : hits) {
            if (hit.holds(x, y)) {
                if (hit.fold()) {
                    unfolded = !unfolded;
                } else if (hit.quest() != null) {
                    picked = hit.quest();
                }
                return hit;
            }
        }
        return null;
    }

    /** The log in the middle of the window. */
    void paint(Canvas canvas, Dungeon.Session match) {
        hits.clear();
        var look = settings.menu();
        var screens = settings.screens();
        var story = settings.story();
        var state = match.run().questsNow();
        var entries = entries(state, story);
        int text = Math.clamp(Math.round(canvas.height() * 0.019f), 12, 22);
        var font = Canvas.Font.of("Georgia", text).wideFamily(MissionScreen.MARKS);
        var bold = font.bold(true);
        var small = Canvas.Font.of("Georgia", Math.max(10, Math.round(text * 0.85f))).wideFamily(MissionScreen.MARKS);
        float line = canvas.measure(font, "Ag").lineHeight() * 1.4f;
        float pad = text;
        float wide = Math.min(canvas.width() - 32f, text * 50f);
        float high = Math.min(canvas.height() - 32f, text * 34f);
        var box = PlayerScreens.frame(canvas, wide, high, screens.questsTitle(), screens.closeHint(), settings);
        int gold = 0xFF000000 | look.torchColour();
        int bone = 0xFF000000 | look.boneColour();
        int dim = 0xA0000000 | look.boneColour();
        float top = box.y() + pad + line * 1.4f;
        float listWide = box.width() * 0.4f - pad;
        float lx = box.x() + pad;

        // The list on the left: what is going, then the finished folded under their heading.
        float y = top;
        var going = entries.stream().filter(entry -> !entry.finished()).toList();
        var done = entries.stream().filter(Entry::finished).toList();
        canvas.drawText(bold, screens.activeWord(), lx, y, gold);
        y += line;
        if (going.isEmpty()) {
            canvas.drawText(small, screens.noQuestsWord(), lx, y, dim);
            y += line;
        }
        var chosen = picked(entries, picked);
        for (var entry : going) {
            y = listed(canvas, font, entry, entry == chosen, lx, y, listWide, line, settings);
        }
        if (!done.isEmpty()) {
            y += line * 0.4f;
            var heading = (unfolded ? "▾ " : "▸ ") + screens.finishedWord() + " (" + done.size() + ")";
            canvas.drawText(bold, heading, lx, y, gold);
            hits.add(new Hit(lx, y, listWide, line, null, true, false));
            y += line;
            if (unfolded) {
                for (var entry : done) {
                    y = listed(canvas, font, entry, entry == chosen, lx, y, listWide, line, settings);
                }
            }
        }

        // The quest picked on the right: its title, its steps, what it is about, and the way to the map.
        if (chosen == null) {
            return;
        }
        float rx = box.x() + box.width() * 0.4f + pad;
        float rightWide = box.x() + box.width() - pad - rx;
        canvas.line(rx - pad * 0.5f, top, rx - pad * 0.5f, box.y() + box.height() - pad * 2f, 1f,
                0x80000000 | look.stoneEdgeColour());
        y = top;
        canvas.drawText(bold, mark(chosen) + chosen.title(), rx, y, chosen.kind() == Tracker.Kind.MAIN ? gold : bone);
        y += line * 1.2f;
        for (var step : chosen.steps()) {
            canvas.drawText(font, (step.done() ? "✓ " : "• ") + step.text(), rx, y, step.done() ? dim : bone);
            y += line;
        }
        y += line * 0.4f;
        for (var row : DialogueScreen.wrap(canvas, small, chosen.description(), rightWide)) {
            canvas.drawText(small, row, rx, y, dim);
            y += line * 0.85f;
        }
        if (!chosen.finished() && whereIs(chosen.quest(), state, story, match.run().storyNow()) != null) {
            var button = screens.showOnMapWord();
            var size = canvas.measure(font, button);
            float bw = size.width() + pad * 1.5f;
            float bh = line * 1.1f;
            float by = box.y() + box.height() - pad * 2.2f - bh;
            canvas.fillRect(rx, by, bw, bh, 0xFF000000 | look.stoneColour());
            canvas.openRect(rx, by, bw, bh, 2f, gold);
            canvas.drawText(font, button, rx + pad * 0.75f, by + (bh - size.lineHeight()) / 2f, gold);
            hits.add(new Hit(rx, by, bw, bh, chosen.quest(), false, true));
        }
    }

    /** One quest's title in the list, the one picked lit behind; where the next goes. */
    private float listed(Canvas canvas, Canvas.Font font, Entry entry, boolean chosen, float x, float y, float wide,
            float line, DungeonSettings settings) {
        var look = settings.menu();
        // Under a quest going, the step it is on and its count, as the tracker says it.
        boolean stepShown = !entry.finished() && !entry.steps().isEmpty();
        float high = stepShown ? line * 1.75f : line;
        if (chosen) {
            canvas.fillRect(x - 4f, y - 2f, wide + 8f, high, 0x60000000 | look.torchColour());
        }
        int argb = entry.finished() ? 0x90000000 | look.boneColour()
                : entry.kind() == Tracker.Kind.MAIN ? 0xFF000000 | look.torchColour() : 0xFF000000 | look.boneColour();
        canvas.drawText(font, mark(entry) + entry.title(), x, y, argb);
        if (stepShown) {
            var small = Canvas.Font.of(font.family(), Math.max(10, Math.round(font.pixelHeight() * 0.85f)))
                    .wideFamily(MissionScreen.MARKS);
            canvas.drawText(small, "• " + entry.steps().getLast().text(), x + font.pixelHeight(), y + line * 0.85f,
                    0xA0000000 | look.boneColour());
        }
        hits.add(new Hit(x, y, wide, high, entry.quest(), false, false));
        return y + high;
    }

    /** The tracker's mark: ◆ for a MAIN, ◇ for a SIDE, ✓ for one finished. */
    private static String mark(Entry entry) {
        return entry.finished() ? "✓ " : entry.kind() == Tracker.Kind.MAIN ? "◆ " : "◇ ";
    }
}
