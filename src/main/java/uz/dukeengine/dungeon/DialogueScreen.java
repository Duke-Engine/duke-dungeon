package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.story.Conversation;
import uz.dukeengine.dungeon.story.Speaker;

/**
 * The box at the bottom of the screen a conversation is said in: the speaker's picture in a frame — or the first
 * letter of their name, where they have none — their name, the line, and how to go on. A click, Space or Enter is the
 * next line; Esc skips the rest. No choices.
 *
 * <p>The window's alone, and only reading the simulation: the run says every conversation it has played, in order
 * ({@code QuestLog.said}), and this shows them one at a time, counting how many it has shown. The simulation never
 * waits for it. Played alone, the world is paused while a conversation is on the screen — the engine's own pause, the
 * flag Esc's menu sets, set from the window as that menu sets it; in a party it is not, since pausing one machine of a
 * lock-step game stalls them all: the box is only shown, and the fight goes on under it.
 *
 * <p>Drawn over the bag, whose painter and input it wraps: what it does not take, the bag is offered.
 */
final class DialogueScreen implements Painter, CanvasInput {

    /** The keys it answers, by the keyboard's scan code ({@code com.jme3.input.KeyInput}'s). */
    static final int ESCAPE = 1;
    static final int ENTER = 28;
    static final int SPACE = 57;
    static final int NUMPAD_ENTER = 156;

    /** The match now being played, told by {@link Bag#show}: this one, or a party's that replaces it. */
    private static volatile Dungeon.Session match;

    private final Painter under;
    private final CanvasInput underInput;
    private final DungeonSettings settings;

    /** The match the counts below are of; a new match is shown from its own first conversation. */
    private Dungeon.Session counted;
    private int shown;
    private int line;
    /** Whether the world was paused here, so it is this that lets it go again — and nothing else's pause. */
    private boolean pausedHere;

    DialogueScreen(Painter under, CanvasInput underInput, DungeonSettings settings) {
        this.under = under;
        this.underInput = underInput;
        this.settings = settings;
    }

    static void show(Dungeon.Session now) {
        match = now;
    }

    /** The conversation on the screen now, and the line of it — or null for none. */
    record Showing(Conversation conversation, int line) {
    }

    Showing showing() {
        var now = match;
        if (now == null || now.game().getLogic() == null) {
            return null;
        }
        if (now != counted) {
            counted = now;
            shown = 0;
            line = 0;
        }
        var said = now.run().getQuests().said();
        while (shown < said.size()) {
            var conversation = settings.story().conversation(said.get(shown));
            if (conversation != null && line < conversation.lines().size()) {
                return new Showing(conversation, line);
            }
            shown++;
            line = 0;
        }
        return null;
    }

    /** The next line, or the end of the conversation. */
    void next() {
        if (showing() != null) {
            line++;
        }
    }

    /** The rest of this conversation, skipped. */
    void skip() {
        if (showing() != null) {
            shown++;
            line = 0;
        }
    }

    @Override
    public void paint(Canvas canvas) {
        if (under != null) {
            under.paint(canvas);
        }
        var now = showing();
        pause(now != null);
        if (now != null) {
            box(canvas, now);
        }
    }

    @Override
    public void resized(int width, int height) {
        if (under != null) {
            under.resized(width, height);
        }
    }

    @Override
    public boolean take(Event event) {
        if (showing() == null) {
            return underInput != null && underInput.take(event);
        }
        switch (event) {
            case Key key -> {
                boolean on = key.code() == SPACE || key.code() == ENTER || key.code() == NUMPAD_ENTER;
                if (key.down() && !key.repeat() && on) {
                    next();
                } else if (key.down() && key.code() == ESCAPE) {
                    skip();
                }
                return on || key.code() == ESCAPE;
            }
            case Button button -> {
                if (button.down() && button.button() == Mouse.LEFT) {
                    next();
                }
                return true; // a click on the box, or past it, is not an order while she is talking
            }
            default -> {
                return underInput != null && underInput.take(event);
            }
        }
    }

    /** The world paused while a conversation is shown, played alone; let go once none is. */
    private void pause(boolean talking) {
        var now = match;
        var logic = now == null ? null : now.game().getLogic();
        if (logic == null || now.game().isMultiplayer()) {
            return;
        }
        if (talking && !logic.isGamePaused()) {
            logic.setGamePaused(true);
            pausedHere = true;
        } else if (!talking && pausedHere) {
            logic.setGamePaused(false);
            pausedHere = false;
        }
    }

    /** Who says it, as the box writes them: a Speaker's DisplayName, or the hero's. */
    String nameOf(String speaker) {
        if (Speaker.HERO.equals(speaker)) {
            var now = match;
            return now == null ? speaker : Main.displayNameOf(now, now.run().getHeroTemplate());
        }
        var known = settings.story().speaker(speaker);
        return known == null || known.displayName().isBlank() ? speaker : known.displayName();
    }

    private void box(Canvas canvas, Showing now) {
        var look = settings.menu();
        var said = now.conversation().lines().get(now.line());
        var speaker = settings.story().speaker(said.speaker());
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.022f), 13, 26));
        var small = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.016f), 11, 18));
        float pad = font.pixelHeight();
        float wide = Math.min(canvas.width() - 32f, Math.max(480f, canvas.width() * 0.55f));
        float face = font.pixelHeight() * 5f;
        float textWide = wide - face - pad * 3f;
        var rows = wrap(canvas, font, said.text(), textWide);
        float lineHigh = canvas.measure(font, "Ag").lineHeight();
        float high = Math.max(face + pad * 2f, pad * 2f + lineHigh * (rows.size() + 2) + pad * 0.5f);
        var panel = canvas.bottomPanel();
        float floor = panel == null ? canvas.height() - 16f : panel.y() - 8f;
        float left = (canvas.width() - wide) / 2f;
        float top = Math.max(8f, floor - high);
        canvas.fillRect(left, top, wide, high, 0xE8000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());

        // The face: the speaker's picture, framed, or the first letter of their name.
        float fx = left + pad;
        float fy = top + pad;
        int gold = 0xFF000000 | look.torchColour();
        var name = nameOf(said.speaker());
        if (speaker != null && !speaker.picture().isBlank()) {
            canvas.drawImage(Canvas.Image.of(speaker.picture()), fx, fy, fx + face, fy + face, 0xFFFFFFFF,
                    Canvas.Blend.ALPHA);
        } else {
            canvas.fillRect(fx, fy, face, face, 0xFF000000 | look.stoneEdgeColour());
            var big = Canvas.Font.of("Georgia", Math.round(face * 0.6f));
            var letter = name.isEmpty() ? "?" : name.substring(0, 1);
            var size = canvas.measure(big, letter);
            canvas.drawText(big, letter, fx + (face - size.width()) / 2f, fy + (face - size.lineHeight()) / 2f, gold);
        }
        canvas.openRect(fx, fy, face, face, 2f, gold);

        float tx = fx + face + pad;
        float ty = top + pad;
        canvas.drawText(font, name, tx, ty, gold);
        ty += lineHigh * 1.2f;
        int bone = 0xFF000000 | look.boneColour();
        for (var row : rows) {
            canvas.drawText(font, row, tx, ty, bone);
            ty += lineHigh;
        }
        var hint = settings.story().dialogue().continueHint();
        var hintSize = canvas.measure(small, hint);
        canvas.drawText(small, hint, left + wide - pad - hintSize.width(), top + high - pad * 0.5f
                - hintSize.lineHeight(), 0x90000000 | look.boneColour());
    }

    /** {@code text} cut into rows no wider than {@code wide}, at the spaces. */
    static List<String> wrap(Canvas canvas, Canvas.Font font, String text, float wide) {
        var rows = new ArrayList<String>();
        var row = new StringBuilder();
        for (var word : text.split(" ")) {
            var longer = row.isEmpty() ? word : row + " " + word;
            if (!row.isEmpty() && canvas.measure(font, longer).width() > wide) {
                rows.add(row.toString());
                row.setLength(0);
                row.append(word);
            } else {
                row.setLength(0);
                row.append(longer);
            }
        }
        if (!row.isEmpty()) {
            rows.add(row.toString());
        }
        return rows;
    }
}
