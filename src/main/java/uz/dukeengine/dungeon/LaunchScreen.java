package uz.dukeengine.dungeon;

import java.util.function.Consumer;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.dungeon.world.MainMenu;
import uz.dukeengine.dungeon.world.Splash;

/**
 * What the window shows before the front menu, and while any map loads: the maker's credit, the game's name coming up
 * gold out of the dark, then a load screen — the name small at its top, a bar, a hint — that clears off once what it
 * hid is there. The bar follows the engine's own figures: the scene behind the front menu (its 100 is told once a
 * frame of it has run) and every match being loaded. The words and timings are {@code data/world/menu.duke}'s Splash.
 *
 * <p>Over everything the game draws, and the first to see the input: while it is up nothing reaches the menu behind
 * it, and after {@link Splash#skipAfter} a key or a click skips the splash.
 */
final class LaunchScreen implements Painter, CanvasInput {

    enum Phase { ENGINE, TITLE, LOADING, FADE, DONE }

    private final Painter under;
    private final CanvasInput underInput;
    private final Splash splash;
    private final MainMenu menu;
    /** Asked once, as the splash ends: let the scene behind the menu be made now. */
    private final Runnable loadingBegins;
    private final Consumer<String> sound;

    private Phase phase;
    private float inPhase;
    private float sinceLaunch;
    private int target;
    private float shown;
    private float tipClock;
    private long last;

    LaunchScreen(Painter under, CanvasInput underInput, Splash splash, MainMenu menu, Runnable loadingBegins,
            Consumer<String> sound) {
        this.under = under;
        this.underInput = underInput;
        this.splash = splash;
        this.menu = menu;
        this.loadingBegins = loadingBegins;
        this.sound = sound;
        enter(splash.engineWord().isBlank() || splash.engineSeconds() <= 0f ? Phase.TITLE : Phase.ENGINE);
    }

    Phase phase() {
        return phase;
    }

    /** The bar as drawn, 0 to 100: easing after the engine's figure, never ahead of it. */
    float shown() {
        return shown;
    }

    /** The hint showing now, or null where there are none. */
    String tip() {
        var tips = splash.tips();
        return tips.isEmpty() ? null : tips.get((int) (tipClock / splash.tipSeconds()) % tips.size());
    }

    /**
     * How far what is loading has got, 0 to 100, as the engine tells it — the scene behind the menu, or a match. A
     * figure lower than the last is a new load, and the bar starts again; one under 100 after the screen has cleared
     * puts it back up.
     */
    void loaded(int percent) {
        int p = Math.clamp(percent, 0, 100);
        if (p < target) {
            shown = 0f;
        }
        target = p;
        if (p < 100 && (phase == Phase.FADE || phase == Phase.DONE)) {
            shown = 0f;
            enter(Phase.LOADING);
        }
    }

    /** Skip the splash, where it may be skipped yet: straight to the load screen. */
    boolean skip() {
        if ((phase == Phase.ENGINE || phase == Phase.TITLE) && sinceLaunch >= splash.skipAfter()) {
            enter(Phase.LOADING);
            return true;
        }
        return false;
    }

    /** {@code seconds} more of it. */
    void step(float seconds) {
        float dt = Math.max(0f, seconds);
        inPhase += dt;
        sinceLaunch += dt;
        switch (phase) {
            case ENGINE -> {
                if (inPhase >= splash.engineSeconds()) {
                    enter(Phase.TITLE);
                }
            }
            case TITLE -> {
                if (inPhase >= splash.titleSeconds()) {
                    enter(Phase.LOADING);
                }
            }
            case LOADING -> {
                tipClock += dt;
                shown = Math.min(target, shown + splash.barSpeed() * dt);
                if (shown >= 100f) {
                    enter(Phase.FADE);
                }
            }
            case FADE -> {
                if (inPhase >= splash.fadeSeconds()) {
                    enter(Phase.DONE);
                }
            }
            case DONE -> {
            }
        }
    }

    private void enter(Phase next) {
        boolean firstLoad = next == Phase.LOADING && (phase == Phase.ENGINE || phase == Phase.TITLE);
        phase = next;
        inPhase = 0f;
        if (next == Phase.TITLE && !splash.sound().isBlank() && sound != null) {
            sound.accept(splash.sound());
        }
        if (firstLoad && loadingBegins != null) {
            loadingBegins.run();
        }
    }

    /** How much of the screen still covers what is behind it, 0 to 1. */
    float cover() {
        return switch (phase) {
            case DONE -> 0f;
            case FADE -> 1f - Math.min(1f, inPhase / Math.max(0.01f, splash.fadeSeconds()));
            default -> 1f;
        };
    }

    // ---- input ----


    @Override
    public boolean take(Event event) {
        if (phase == Phase.DONE) {
            return underInput != null && underInput.take(event);
        }
        boolean asks = event instanceof Key key && key.down() && !key.repeat()
                && (key.code() == ESCAPE || key.code() == ENTER || key.code() == SPACE)
                || event instanceof Button button && button.down() && button.button() == Mouse.LEFT;
        if (asks) {
            skip();
        }
        return true; // nothing reaches the menu under it while it is up
    }

    // ---- drawing ----

    @Override
    public void resized(int width, int height) {
        under.resized(width, height);
    }

    @Override
    public void paint(Canvas canvas) {
        under.paint(canvas);
        long now = System.nanoTime();
        // A long first frame (the window opening) is not let through as one big step: the splash is seen from its start.
        step(last == 0L ? 0f : Math.min(0.1f, (now - last) / 1e9f));
        last = now;
        draw(canvas);
    }

    void draw(Canvas canvas) {
        float cover = cover();
        if (cover <= 0f) {
            return;
        }
        float w = canvas.width();
        float h = canvas.height();
        switch (phase) {
            case ENGINE -> {
                canvas.fillRect(0f, 0f, w, h, 0xFF000000);
                float a = envelope(inPhase, splash.engineSeconds(), 0.3f);
                var font = Canvas.Font.of(splash.titleFont(), Math.round(h * 0.035f));
                centred(canvas, font, splash.engineWord(), w / 2f, h * 0.48f, argb(a, splash.textColour()));
            }
            case TITLE -> drawTitle(canvas, w, h);
            default -> drawLoading(canvas, w, h, cover);
        }
    }

    private void drawTitle(Canvas canvas, float w, float h) {
        canvas.fillRect(0f, 0f, w, h, 0xFF000000);
        float rise = Math.min(1f, inPhase / Math.max(0.01f, splash.riseSeconds()));
        float eased = 1f - (1f - rise) * (1f - rise) * (1f - rise);
        // Gone dark again over its last half second, into the load screen.
        float out = Math.min(1f, Math.max(0f, (splash.titleSeconds() - inPhase) / 0.5f));
        float a = eased * out;
        embers(canvas, w, h, a, sinceLaunch);
        // Grows a little as it comes: whole steps of 4 pixels, so the canvas bakes a handful of faces, not one a frame.
        int px = Math.round(h * 0.13f * (0.9f + 0.1f * eased) / 4f) * 4;
        var font = Canvas.Font.of(splash.titleFont(), px);
        var title = menu.title().toUpperCase(java.util.Locale.ROOT);
        float y = h * 0.40f;
        var measure = canvas.measure(font, title);
        float x = (w - measure.width()) / 2f;
        for (int i = 3; i >= 1; i--) { // the glow: the name again, wider and fainter
            int glow = argb(a * 0.10f, splash.glowColour());
            float d = i * px * 0.025f;
            canvas.drawText(font, title, x - d, y, glow);
            canvas.drawText(font, title, x + d, y, glow);
            canvas.drawText(font, title, x, y - d, glow);
            canvas.drawText(font, title, x, y + d, glow);
        }
        canvas.drawText(font, title, x, y, argb(a, splash.titleColour()));
        // A light passing over the letters once they are up: the name redrawn white within a moving strip.
        float sweep = (inPhase - splash.riseSeconds() * 0.8f) / 0.9f;
        if (sweep > 0f && sweep < 1f) {
            float band = measure.width() * 0.12f;
            float at = x - band + sweep * (measure.width() + band * 2f);
            canvas.clip(at, y - px, at + band, y + measure.lineHeight() + px);
            canvas.drawText(font, title, x, y, argb(a * 0.55f, 0xFFF6DC));
            canvas.noClip();
        }
        float sub = Math.min(1f, Math.max(0f, (inPhase - splash.riseSeconds() * 0.6f) / 0.6f)) * out;
        var small = Canvas.Font.of("Georgia", Math.round(h * 0.028f));
        centred(canvas, small, menu.subtitle(), w / 2f, y + measure.lineHeight() + h * 0.02f,
                argb(sub * 0.85f, splash.textColour()));
    }

    private void drawLoading(Canvas canvas, float w, float h, float cover) {
        // Dark, warmer across the middle as the menu's gloom is, in bands.
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            float t = i / (bands - 1f);
            float warm = 1f - Math.abs(t - 0.55f) * 2f;
            int r = Math.round(8 + 22 * Math.max(0f, warm));
            int g = Math.round(6 + 16 * Math.max(0f, warm));
            int b = Math.round(5 + 11 * Math.max(0f, warm));
            canvas.fillRect(0f, h * i / bands, w, h / bands + 1f, argb(cover, (r << 16) | (g << 8) | b));
        }
        embers(canvas, w, h, cover * 0.8f, sinceLaunch);
        var titleFont = Canvas.Font.of(splash.titleFont(), Math.round(h * 0.05f));
        centred(canvas, titleFont, menu.title().toUpperCase(java.util.Locale.ROOT), w / 2f, h * 0.08f,
                argb(cover, splash.titleColour()));
        float barW = Math.min(w * 0.5f, 640f);
        float barH = Math.max(6f, h * 0.012f);
        float barX = (w - barW) / 2f;
        float barY = h * 0.78f;
        var word = Canvas.Font.of("Georgia", Math.round(h * 0.024f));
        String said = splash.loadingWord() + "  " + Math.round(shown) + "%";
        centred(canvas, word, said, w / 2f, barY - h * 0.045f, argb(cover, splash.textColour()));
        canvas.fillRect(barX - 2f, barY - 2f, barW + 4f, barH + 4f, argb(cover, 0x0A0806));
        canvas.openRect(barX - 2f, barY - 2f, barW + 4f, barH + 4f, 1f, argb(cover * 0.8f, 0x5A5042));
        canvas.fillRect(barX, barY, barW * shown / 100f, barH, argb(cover, splash.glowColour()));
        canvas.fillRect(barX, barY, barW * shown / 100f, barH * 0.4f, argb(cover * 0.5f, splash.titleColour()));
        var tip = tip();
        if (tip != null) {
            float into = tipClock % splash.tipSeconds();
            float a = envelope(into, splash.tipSeconds(), 0.4f);
            var small = Canvas.Font.of("Georgia", Math.round(h * 0.022f));
            centred(canvas, small, tip, w / 2f, barY + barH + h * 0.035f, argb(cover * a * 0.8f, splash.textColour()));
        }
    }

    /** Sparks rising slowly from the foot of the screen, each on its own path: the same every launch. */
    private static void embers(Canvas canvas, float w, float h, float alpha, float time) {
        for (int i = 0; i < 40; i++) {
            float seed = (i * 0.6180339f) % 1f;
            float speed = 0.04f + 0.05f * ((i * 0.4142135f) % 1f);
            float up = (time * speed + seed * 3.7f) % 1f;
            float x = w * ((i * 0.7548776f) % 1f) + (float) Math.sin(time * 0.8f + i) * w * 0.01f;
            float y = h * (1f - up * 0.75f);
            float a = alpha * (1f - up) * 0.7f;
            float size = 1.5f + 2f * seed;
            canvas.fillRect(x, y, size, size, argb(a, i % 3 == 0 ? 0xFFD089 : 0xE8A33D));
        }
    }

    /** In over {@code edge} of {@code length}, held, out over the last {@code edge}. */
    static float envelope(float at, float length, float edge) {
        float in = Math.min(1f, at / edge);
        float out = Math.min(1f, Math.max(0f, (length - at) / edge));
        return Math.max(0f, Math.min(in, out));
    }

    private static void centred(Canvas canvas, Canvas.Font font, String text, float cx, float y, int argb) {
        if (text == null || text.isBlank() || (argb >>> 24) == 0) {
            return;
        }
        canvas.drawText(font, text, cx - canvas.measure(font, text).width() / 2f, y, argb);
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255f);
        return a << 24 | (rgb & 0xFFFFFF);
    }
}
