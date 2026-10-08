package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Tracker;

/**
 * What the game says over the world: the list of missions down the window's right edge, and what each hero says in a
 * bubble over his head, above his bar.
 *
 * <p>Drawn on the game's own canvas with the bag, on the window's thread, and only reading: the list is the run's,
 * written whole on the simulation's thread every frame ({@code DungeonRun.getTracker}) and empty once the run is lost
 * or won, or the boss has fallen and the floor is closing, when nothing is drawn but the banner; a hero's words are
 * his bag's note ({@code LootBag.say}) — what he makes of the gate, why he kept the key, that the bag is full, what he
 * just picked up — and where he is on the screen is where the client put his bar this frame
 * ({@link Canvas#barOf}): a bar off the window has no bubble.
 *
 * <p>The list has no slab: small text with a thin shadow over a soft dark backing. It is folded to a small tab, and
 * unfolded again, by the run's fold key ({@link #fold}).
 */
final class MissionScreen {

    /** Whether the list is folded to its tab: the window's, flipped by a key on the window's thread. */
    private static volatile boolean folded;

    private MissionScreen() {
    }

    /** Fold the list, or unfold it. */
    static void fold() {
        folded = !folded;
    }

    static boolean folded() {
        return folded;
    }

    static void folded(boolean now) {
        folded = now;
    }

    /** The list, and a bubble over every hero with something to say. */
    static void paint(Canvas canvas, Dungeon.Session match, DungeonSettings settings) {
        var look = settings.menu();
        var snapshot = match.game().getSnapshot();
        tracker(canvas, match.run().getTracker(), snapshot.frame(), folded, settings);
        var heroes = settings.heroes().stream().map(hero -> hero.name()).collect(Collectors.toUnmodifiableSet());
        for (var unit : snapshot.units()) {
            var progress = heroes.contains(unit.templateName()) ? match.run().progressOf(unit.playerIndex()) : null;
            var said = progress == null ? "" : progress.getLoot().noteAt(snapshot.frame());
            var bar = said.isEmpty() ? null : canvas.barOf(unit.id());
            if (bar != null && seen(canvas, bar)) {
                bubble(canvas, said, bar, look);
            }
        }
    }

    /**
     * Whether the hero whose bar this is can be seen. The client places a bar, shown or not, until the thing it is over
     * is well past the left, right or top edge of the window, so one may stand wholly beyond the side edges or with its
     * top above the window: what he says is not said there.
     */
    private static boolean seen(Canvas canvas, Canvas.Box bar) {
        return bar.x() + bar.width() >= 0f && bar.x() <= canvas.width() && bar.y() >= 0f;
    }
    /** One line of the list: its words, its colour, and how far in under its title it stands. */
    record Row(String text, int argb, boolean indented) {
    }

    /**
     * The lines of the list at {@code frame}, top to bottom: each mission's title, ◆ in gold for the floor's own and
     * ◇ in plain bone for a side one, and under it its steps, • for the one to do — brightening as it begins, for the
     * run's {@code GlowFrames} — and ✓, dimmed, for one just done.
     */
    static List<Row> rows(Tracker tracker, int frame, DungeonSettings settings) {
        var look = settings.menu();
        int gold = 0xFF000000 | look.torchColour();
        int bone = 0xFF000000 | look.boneColour();
        int dim = 0x90000000 | look.boneColour();
        var rows = new ArrayList<Row>();
        for (var entry : tracker.entries()) {
            boolean main = entry.kind() == Tracker.Kind.MAIN;
            rows.add(new Row((main ? "◆ " : "◇ ") + entry.title(), main ? gold : bone, false));
            for (var step : entry.steps()) {
                if (step.done()) {
                    rows.add(new Row("✓ " + step.text(), dim, true));
                } else {
                    float glow = Tracker.glow(frame, step.since(), settings.run().glowFrames());
                    rows.add(new Row("• " + step.text(), lit(bone, glow), true));
                }
            }
        }
        return rows;
    }

    /** {@code argb} brought toward white by {@code glow}, 0 for itself and 1 for white. */
    private static int lit(int argb, float glow) {
        int out = argb & 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int part = (argb >> shift) & 0xFF;
            out |= Math.round(part + (255 - part) * glow) << shift;
        }
        return out;
    }

    /**
     * Where a list {@code wide} by {@code high} stands in a window of {@code across} by {@code down}: against the right
     * edge and a little down from the top, and always wholly on the window.
     */
    static Canvas.Box boxOf(int across, int down, float wide, float high) {
        float left = Math.max(0f, across - wide - 12f);
        float top = Math.clamp(down * 0.03f, 0f, Math.max(0f, down - high));
        return new Canvas.Box(left, top, wide, high);
    }

    /**
     * The list at the right edge, over a soft dark backing, its words shadowed to read on any ground; or, folded, only
     * the run's tab. Nothing while there is nothing listed.
     */
    static void tracker(Canvas canvas, Tracker tracker, int frame, boolean folded, DungeonSettings settings) {
        if (tracker.entries().isEmpty()) {
            return;
        }
        var look = settings.menu();
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.018f), 12, 22));
        List<Row> rows = folded
                ? List.of(new Row(settings.run().missionsTab(), 0x90000000 | look.boneColour(), false))
                : rows(tracker, frame, settings);
        float widest = 0f;
        float line = 0f;
        for (var row : rows) {
            var size = canvas.measure(font, row.text());
            widest = Math.max(widest, size.width() + (row.indented() ? font.pixelHeight() : 0f));
            line = Math.max(line, size.lineHeight());
        }
        float pad = font.pixelHeight() * 0.5f;
        var box = boxOf(canvas.width(), canvas.height(), widest + pad * 2f, line * rows.size() + pad * 2f);
        canvas.fillRect(box.x(), box.y(), box.width(), box.height(), 0x70000000);
        float y = box.y() + pad;
        for (var row : rows) {
            float x = box.x() + pad + (row.indented() ? font.pixelHeight() : 0f);
            canvas.drawText(font, row.text(), x + 1f, y + 1f, 0xC0000000);
            canvas.drawText(font, row.text(), x, y, row.argb());
            y += line;
        }
    }

    /**
     * His words over his bar, the bubble's tail pointing down at it, kept on the screen: sideways by the window's
     * edges, and, for a bar with no room above it, lowered from the top edge over the bar.
     */
    private static void bubble(Canvas canvas, String words, Canvas.Box bar, MenuStyle look) {
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.018f), 12, 22));
        var size = canvas.measure(font, words);
        float pad = font.pixelHeight() * 0.5f;
        float wide = size.width() + pad * 2f;
        float high = size.lineHeight() + pad;
        float middle = bar.x() + bar.width() / 2f;
        float left = Math.clamp(middle - wide / 2f, 4f, Math.max(4f, canvas.width() - wide - 4f));
        float top = Math.max(4f, bar.y() - high - pad);
        canvas.fillRect(left, top, wide, high, 0xF0000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 1.5f, 0xFF000000 | look.stoneEdgeColour());
        canvas.fillTriangle(middle - pad, top + high, middle + pad, top + high, middle, top + high + pad,
                0xF0000000 | look.stoneDeepColour());
        canvas.drawText(font, words, left + pad, top + pad / 2f, 0xFF000000 | look.boneColour());
    }
}
