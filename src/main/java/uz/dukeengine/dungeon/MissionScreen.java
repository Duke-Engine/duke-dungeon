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

    /**
     * The face the marks (diamonds, tick, dot) are drawn in: Georgia has none of them but the dot, and the font gives
     * every code point from 256 up to its wide family. Present on every Windows since 7.
     */
    static final String MARKS = "Segoe UI Symbol";

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
        marks(canvas, match, snapshot, settings);
        pointers(canvas, match, snapshot, settings);
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
     * Over every person with something for the hero, a mark: ! for a quest to give, ? for one waiting on them — the
     * Dialogue block's pictures, in its colour, standing over their head where the client puts it on the screen.
     */
    static void marks(Canvas canvas, Dungeon.Session match, uz.dukeengine.core.view.WorldSnapshot snapshot,
            DungeonSettings settings) {
        var marks = match.run().getMarks();
        if (marks.isEmpty()) {
            return;
        }
        var dialogue = settings.story().dialogue();
        float size = Math.clamp(canvas.height() * 0.04f, 20f, 48f);
        for (var unit : snapshot.units()) {
            for (var mark : marks) {
                if (mark.id() != unit.id() || mark.mark() == uz.dukeengine.dungeon.story.QuestLog.Mark.NONE) {
                    continue;
                }
                var over = canvas.screenOf(unit.x(), unit.y(), unit.z() + 18f);
                if (over == null) {
                    continue;
                }
                var picture = mark.mark() == uz.dukeengine.dungeon.story.QuestLog.Mark.GIVE ? dialogue.giveMark()
                        : dialogue.handInMark();
                canvas.drawImage(Canvas.Image.of(picture), over.x() - size / 2f, over.y() - size, over.x() + size / 2f,
                        over.y(), 0xFF000000 | dialogue.markColour(), Canvas.Blend.ALPHA);
            }
        }
    }

    /**
     * Where the hero is wanted, shown from where he stands: every quest going whose step is on this map (in the
     * Dialogue block's mark colour), and the floor's key lying, with its {@code KeyDroppedWord} floating up over it as
     * it drops. Off the screen, an arrow on a ring round him points the way; on it, the target's glow or pillar of light
     * says where. Nothing while the tracker lists nothing (the banner has the screen).
     */
    static void pointers(Canvas canvas, Dungeon.Session match, uz.dukeengine.core.view.WorldSnapshot snapshot,
            DungeonSettings settings) {
        if (match.run().getTracker().entries().isEmpty()) {
            return;
        }
        var hero = WorldMapScreen.heroOf(match, settings);
        if (hero == null) {
            return;
        }
        int questColour = 0xFF000000 | settings.story().dialogue().markColour();
        for (var going : match.run().questsNow().active()) {
            var target = QuestLogScreen.whereIs(going.quest(), match, settings);
            if (target != null) {
                point(canvas, hero, target[0], target[1], hero.z(), questColour);
            }
        }
        var key = match.run().keyLying();
        if (key == null) {
            return;
        }
        int gold = 0xFF000000 | settings.menu().torchColour();
        point(canvas, hero, key.x(), key.y(), key.z(), gold);
        var burst = settings.goldBurst();
        float life = 2f * burst.floatSeconds() * uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;
        float age = (snapshot.frame() - key.frame()) / life;
        var over = age < 0f || age >= 1f ? null : canvas.screenOf(key.x(), key.y(), key.z() + burst.floatHeight());
        if (over != null) {
            var words = settings.run().keyDroppedWord();
            var font = Canvas.Font.of("Georgia", Math.max(12, Math.round(canvas.height() * burst.textSize() * 1.3f)));
            var size = canvas.measure(font, words);
            int alpha = Math.round((age <= 0.5f ? 1f : 2f - 2f * age) * 255f) << 24;
            float x = over.x() - size.width() / 2f;
            float y = over.y() - size.lineHeight() - age * burst.floatRise() * canvas.height();
            canvas.drawText(font, words, x + 1.5f, y + 1.5f, alpha);
            canvas.drawText(font, words, x, y, alpha | gold & 0xFFFFFF);
        }
    }

    /** An arrow on the ring round {@code hero} pointing at the world point {@code (x, y, z)}, while it is off screen. */
    static void point(Canvas canvas, uz.dukeengine.core.view.UnitView hero, float x, float y, float z, int argb) {
        var there = canvas.screenOf(x, y, z);
        if (there != null && there.x() >= 0f && there.x() <= canvas.width() && there.y() >= 0f
                && there.y() <= canvas.height()) {
            return; // on the screen: its light says where
        }
        float dx = x - hero.x();
        float dy = y - hero.y();
        float far = (float) Math.hypot(dx, dy);
        var here = canvas.screenOf(hero.x(), hero.y(), hero.z());
        var toward = far < 1f ? null : canvas.screenOf(hero.x() + dx / far * 20f, hero.y() + dy / far * 20f, hero.z());
        var arrow = here == null || toward == null ? null : arrowAt(here, toward, canvas.height() * 0.16f);
        if (arrow != null) {
            WorldMapScreen.arrow(canvas, arrow[0], arrow[1], arrow[2], Math.clamp(canvas.height() * 0.03f, 14f, 30f),
                    argb);
        }
    }

    /**
     * Where the arrow stands and the way it points, {x, y, radians}: on a ring {@code ring} pixels round {@code here},
     * the way the screen carries a step from {@code here} toward the target ({@code toward}); null for no way.
     */
    static float[] arrowAt(Canvas.Point here, Canvas.Point toward, float ring) {
        float dx = toward.x() - here.x();
        float dy = toward.y() - here.y();
        float length = (float) Math.hypot(dx, dy);
        if (length < 1e-3f) {
            return null;
        }
        return new float[] {here.x() + dx / length * ring, here.y() + dy / length * ring, (float) Math.atan2(dy, dx)};
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
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.018f), 12, 22))
                .wideFamily(MARKS);
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
