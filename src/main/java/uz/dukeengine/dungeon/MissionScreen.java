package uz.dukeengine.dungeon;

import java.util.stream.Collectors;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * What the game says over the world: the floor's mission at the top of the window, a step at a time, and what each
 * hero says in a bubble over his head, above his bar.
 *
 * <p>Drawn on the game's own canvas with the bag, on the window's thread, and only reading: the tracker's words are
 * the run's, written whole on the simulation's thread every frame ({@code DungeonRun.getTracker}); a hero's are his
 * bag's note ({@code LootBag.say}) — what he makes of the gate, why he kept the key, that the bag is full, what he
 * just picked up — and where he is on the screen is where the client put his bar this frame ({@link Canvas#barOf}).
 */
final class MissionScreen {

    private MissionScreen() {
    }

    /** The tracker, and a bubble over every hero with something to say. */
    static void paint(Canvas canvas, Dungeon.Session match, DungeonSettings settings) {
        var look = settings.menu();
        var words = match.run().getTracker();
        if (!words.isEmpty()) {
            tracker(canvas, words, look);
        }
        var heroes = settings.heroes().stream().map(hero -> hero.name()).collect(Collectors.toUnmodifiableSet());
        var snapshot = match.game().getSnapshot();
        for (var unit : snapshot.units()) {
            var progress = heroes.contains(unit.templateName()) ? match.run().progressOf(unit.playerIndex()) : null;
            var said = progress == null ? "" : progress.getLoot().noteAt(snapshot.frame());
            var bar = said.isEmpty() ? null : canvas.barOf(unit.id());
            if (bar != null) {
                bubble(canvas, said, bar, look);
            }
        }
    }

    /** The step, on a slab of stone at the top of the window, in the middle. */
    private static void tracker(Canvas canvas, String words, MenuStyle look) {
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.024f), 14, 30)).bold(true);
        var size = canvas.measure(font, words);
        float pad = font.pixelHeight() * 0.6f;
        float wide = size.width() + pad * 2f;
        float high = size.lineHeight() + pad;
        float left = (canvas.width() - wide) / 2f;
        float top = canvas.height() * 0.02f;
        canvas.fillRect(left, top, wide, high, 0xE0000000 | look.stoneColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());
        canvas.drawText(font, words, left + pad, top + pad / 2f, 0xFF000000 | look.torchColour());
    }

    /** His words over his bar, the bubble's tail pointing down at it, kept on the screen. */
    private static void bubble(Canvas canvas, String words, Canvas.Box bar, MenuStyle look) {
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.018f), 12, 22));
        var size = canvas.measure(font, words);
        float pad = font.pixelHeight() * 0.5f;
        float wide = size.width() + pad * 2f;
        float high = size.lineHeight() + pad;
        float middle = bar.x() + bar.width() / 2f;
        float left = Math.clamp(middle - wide / 2f, 4f, Math.max(4f, canvas.width() - wide - 4f));
        float top = bar.y() - high - pad;
        canvas.fillRect(left, top, wide, high, 0xF0000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 1.5f, 0xFF000000 | look.stoneEdgeColour());
        canvas.fillTriangle(middle - pad, top + high, middle + pad, top + high, middle, top + high + pad,
                0xF0000000 | look.stoneDeepColour());
        canvas.drawText(font, words, left + pad, top + pad / 2f, 0xFF000000 | look.boneColour());
    }
}
