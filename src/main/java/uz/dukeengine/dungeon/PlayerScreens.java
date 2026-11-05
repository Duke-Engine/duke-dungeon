package uz.dukeengine.dungeon;

import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The player's four screens -- the character sheet, the quest log, the world map, the Hunar (crafting) list -- over
 * everything else the game draws, and the small row of their keys over the panel: see
 * {@code docs/plan/2026-10-15-player-screens.md}.
 *
 * <p>One is open at a time: its key opens it, and shuts it again; another's key changes to that one; Esc shuts it. The
 * keys open them through the game's hotkeys ({@code Main}), which the client answers only in play -- never on a menu
 * or while a line of chat is being typed -- and while one is open this takes them itself, with Esc. A conversation or
 * a cutscene shuts it: they have the screen. Played alone the world waits while one is open, as it waits for the
 * dialogue box -- the engine's own pause, let go of only where it was taken here; in a party it does not, since pausing
 * one machine of a lock-step game stalls them all.
 *
 * <p>Drawn over the dialogue box, whose painter and input it wraps: what it does not take, the box is offered.
 */
final class PlayerScreens implements Painter, CanvasInput {

    enum Open { NONE, SHEET, QUESTS, MAP, CRAFT }

    private final DialogueScreen under;
    private final DungeonSettings settings;
    private final QuestLogScreen quests;
    private final WorldMapScreen map;
    private final CraftScreen craft;
    /** Which is open: flipped by the hotkeys and by this, both on the window's thread. */
    private volatile Open open = Open.NONE;
    /** Whether the world was paused here, so it is this that lets it go. */
    private boolean pausedHere;
    /** The canvas last painted on, for the log's way to the map, which needs its size. */
    private Canvas last;

    PlayerScreens(DialogueScreen under, DungeonSettings settings) {
        this.under = under;
        this.settings = settings;
        this.quests = new QuestLogScreen(settings);
        this.map = new WorldMapScreen(settings);
        this.craft = new CraftScreen(settings);
        this.map.onTravel(this::close);
    }

    Open open() {
        return open;
    }

    QuestLogScreen quests() {
        return quests;
    }

    WorldMapScreen map() {
        return map;
    }

    /** {@code which} opened, or shut where it is open already; whatever else was open is shut. */
    void toggle(Open which) {
        if (open == which || which == Open.NONE) {
            close();
            return;
        }
        if (which == Open.MAP) {
            map.reset();
        }
        open = which;
        under.trade().close(); // one window at a time
    }

    void close() {
        open = Open.NONE;
    }

    /** The screen a key's scan code opens, or NONE for a key that is no screen's. */
    static Open forKey(int code, DungeonSettings settings) {
        var keys = settings.screens().keys();
        for (int i = 0; i < keys.size(); i++) {
            if (uz.dukeengine.client3d.Hotkeys.codeOf(keys.get(i)) == code) {
                return Open.values()[i + 1];
            }
        }
        return Open.NONE;
    }

    @Override
    public void paint(Canvas canvas) {
        last = canvas;
        under.paint(canvas);
        var match = DialogueScreen.match();
        boolean playing = match != null && match.game().getLogic() != null;
        if (!playing || under.busy()) {
            open = Open.NONE;
        }
        // A tower's keeper talked to: the map, to travel from, once what he says has been read.
        wantMap |= playing && keeperAsked(match);
        if (wantMap && playing && !under.busy()) {
            wantMap = false;
            if (open != Open.MAP) {
                toggle(Open.MAP);
            }
        }
        pause(playing && open != Open.NONE, match);
        if (!playing || under.busy()) {
            return;
        }
        switch (open) {
            case SHEET -> CharacterSheet.paint(canvas, match.run().getSheet(), settings);
            case QUESTS -> quests.paint(canvas, match);
            case MAP -> map.paint(canvas, match);
            case CRAFT -> craft.paint(canvas, match);
            case NONE -> {
                ShoulderHud.paint(canvas, match.run().getSheet(), settings);
                if (hintsStillShown(match)) {
                    hints(canvas, settings, canvas.height() - ShoulderHud.height(canvas));
                }
            }
        }
    }

    @Override
    public void resized(int width, int height) {
        under.resized(width, height);
    }

    /** A screen of his open holds the keys from his feet, as whatever is under it may. */
    @Override
    public boolean holdsKeys() {
        return open != Open.NONE || under.holdsKeys();
    }


    @Override
    public boolean take(Event event) {
        if (open == Open.NONE || under.busy()) {
            return under.take(event);
        }
        switch (event) {
            case Key key -> {
                if (!key.down()) {
                    return under.take(event); // a key let go is the game's: it may be holding it
                }
                if (key.repeat()) {
                    return forKey(key.code(), settings) != Open.NONE || key.code() == CanvasInput.ESCAPE
                            || under.take(event);
                }
                if (key.code() == CanvasInput.ESCAPE) {
                    close();
                    return true;
                }
                var which = forKey(key.code(), settings);
                if (which != Open.NONE) {
                    toggle(which);
                    return true;
                }
                return under.take(event); // his other keys are still his: the bar's, the camera's
            }
            case Typed typed -> {
                return under.take(event);
            }
            default -> {
                return switch (open) {
                    case MAP -> map.take(event);
                    case QUESTS -> questsTake(event);
                    case CRAFT -> {
                        if (craft.take(event, DialogueScreen.match())) {
                            close(); // he is seen at work
                        }
                        yield true;
                    }
                    default -> true; // a click on a screen is not an order to the world
                };
            }
        }
    }

    /** A click on the log: a quest picked, the finished folded, or the map opened on a quest's target. */
    private boolean questsTake(Event event) {
        if (event instanceof Button button && button.down() && button.button() == Mouse.LEFT) {
            var hit = quests.click(button.x(), button.y());
            var match = DialogueScreen.match();
            if (hit != null && hit.map() && match != null && last != null) {
                var target = QuestLogScreen.whereIs(hit.quest(), match, settings);
                if (target != null) {
                    toggle(Open.MAP);
                    map.centreOn(last, match, target);
                }
            }
        }
        return true;
    }

    /** The match the keepers' asks were last counted in, and how many of them have been seen. */
    private Dungeon.Session counted;
    private int seenAsks;
    /** A keeper asked, and the map not yet opened for it. */
    private boolean wantMap;

    /** Whether a tower's keeper has been asked, since last looked, to send this machine's hero on. */
    private boolean keeperAsked(Dungeon.Session match) {
        if (match != counted) {
            counted = match;
            seenAsks = 0;
        }
        var asks = match.run().getTravelAsks();
        boolean asked = false;
        for (; seenAsks < asks.size(); seenAsks++) {
            asked |= asks.get(seenAsks).player() == match.game().getLocalPlayerIndex();
        }
        return asked;
    }

    /** The world paused while a screen is open, played alone; let go once none is, where it was taken here. */
    private void pause(boolean shown, Dungeon.Session match) {
        var logic = match == null ? null : match.game().getLogic();
        if (logic == null || match.game().isMultiplayer()) {
            return;
        }
        if (shown && !logic.isGamePaused()) {
            logic.setGamePaused(true);
            pausedHere = true;
        } else if (!shown && pausedHere) {
            logic.setGamePaused(false);
            pausedHere = false;
        }
    }

    // ---- what the three share ----

    /**
     * A screen's frame in the middle of the window, {@code wide} by {@code high}: the stone slab with its edge, its
     * title at the top, its close hint at the foot. Where it stands.
     */
    static Canvas.Box frame(Canvas canvas, float wide, float high, String title, String hint,
            DungeonSettings settings) {
        var look = settings.menu();
        float left = (canvas.width() - wide) / 2f;
        float top = Math.max(16f, (canvas.height() - high) / 2.4f);
        canvas.fillRect(0f, 0f, canvas.width(), canvas.height(), 0x70000000);
        canvas.fillRect(left, top, wide, high, 0xF4000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());
        canvas.openRect(left + 4f, top + 4f, wide - 8f, high - 8f, 1f, 0x60000000 | look.stoneEdgeColour());
        int text = Math.clamp(Math.round(canvas.height() * 0.026f), 14, 30);
        var heading = Canvas.Font.of("Georgia", text).bold(true);
        var size = canvas.measure(heading, title);
        canvas.drawText(heading, title, left + (wide - size.width()) / 2f, top + text * 0.6f,
                0xFF000000 | look.torchColour());
        var small = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.015f), 10, 18));
        var hintSize = canvas.measure(small, hint);
        canvas.drawText(small, hint, left + wide - hintSize.width() - text * 0.6f,
                top + high - hintSize.lineHeight() - text * 0.5f, 0x90000000 | look.boneColour());
        return new Canvas.Box(left, top, wide, high);
    }

    /** A bar {@code share} full with its words on it. */
    static void bar(Canvas canvas, Canvas.Font font, float x, float y, float wide, float high, float share,
            String words, int argb, DungeonSettings settings) {
        var look = settings.menu();
        canvas.fillRect(x, y, wide, high, 0xFF000000 | look.stoneDeepColour());
        canvas.fillRect(x, y, wide * Math.clamp(share, 0f, 1f), high, 0xB0000000 | argb & 0xFFFFFF);
        canvas.openRect(x, y, wide, high, 1f, 0xFF000000 | look.stoneEdgeColour());
        var size = canvas.measure(font, words);
        canvas.drawText(font, words, x + (wide - size.width()) / 2f, y + (high - size.lineHeight()) / 2f,
                0xFF000000 | look.boneColour());
    }

    /** One key hint: the key's letter and the screen's word. */
    record Hint(String key, String word) {
    }

    /** The HUD's key hints, left to right: the four screens', then the tracker's fold. */
    static java.util.List<Hint> hints(DungeonSettings settings) {
        var screens = settings.screens();
        return java.util.List.of(new Hint(screens.sheetKey(), screens.sheetTitle()),
                new Hint(screens.questsKey(), screens.questsTitle()), new Hint(screens.mapKey(), screens.mapTitle()),
                new Hint(screens.craftKey(), settings.crafting().title()),
                new Hint(settings.run().foldKey(), screens.trackerWord()));
    }

    /** How long, from the first frame played, the screens' keys are written over the corner: then nothing but the HUD. */
    private static final long HINTS_NANOS = 20_000_000_000L;
    private Dungeon.Session hinted;
    private long hintsFrom;

    private boolean hintsStillShown(Dungeon.Session match) {
        long now = System.nanoTime();
        if (hinted != match) {
            hinted = match;
            hintsFrom = now;
        }
        return now - hintsFrom < HINTS_NANOS;
    }

    /** The key hints in a small row at the left over the panel: a key cap with its letter, and the word beside it. */
    static void hints(Canvas canvas, DungeonSettings settings) {
        var panel = canvas.bottomPanel();
        hints(canvas, settings, panel == null ? canvas.height() : panel.y());
    }

    /** The same row, its feet on {@code floor}. */
    static void hints(Canvas canvas, DungeonSettings settings, float floor) {
        var look = settings.menu();
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.014f), 10, 16));
        var bold = font.bold(true);
        float cap = canvas.measure(font, "Ag").lineHeight() + 4f;
        float y = floor - cap - 8f;
        float x = 12f;
        for (var hint : hints(settings)) {
            canvas.fillRect(x, y, cap, cap, 0xC0000000 | look.stoneDeepColour());
            canvas.openRect(x, y, cap, cap, 1f, 0xFF000000 | look.stoneEdgeColour());
            var letter = canvas.measure(bold, hint.key());
            canvas.drawText(bold, hint.key(), x + (cap - letter.width()) / 2f, y + (cap - letter.lineHeight()) / 2f,
                    0xFF000000 | look.torchColour());
            x += cap + 4f;
            var word = canvas.measure(font, hint.word());
            canvas.drawText(font, hint.word(), x + 1f, y + (cap - word.lineHeight()) / 2f + 1f, 0xC0000000);
            canvas.drawText(font, hint.word(), x, y + (cap - word.lineHeight()) / 2f, 0xC0000000 | look.boneColour());
            x += word.width() + 12f;
        }
    }
}
