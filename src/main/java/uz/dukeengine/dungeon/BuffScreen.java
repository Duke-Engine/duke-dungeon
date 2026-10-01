package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.game.DukeGame;

/**
 * What is on a creature the player picks, as Dota draws it: over its bar, a small picture for each aura that reaches
 * it and for a haste while one burns on it, framed in the colour its own look starts in -- an aura's ring on the floor,
 * a haste's glow on the body -- and the pointer on one says what it adds.
 *
 * <p>The creature he has selected alone shows its row always; the one the pointer rests on, while the pointer is on it,
 * on its row or on its bar -- and while it is on the row, the row stays that one's, whatever the pick names behind it.
 * A pointer on the bag's slab, or over the panel at the foot of the window, names nothing, whatever stands behind it
 * there; a bar behind the panel has no row over it, and one at the top of the window has its row kept on the window.
 *
 * <p>What is on them is read after every frame, on the simulation's thread -- {@link SkillBook#aurasOn}, the one rule
 * the circle under a creature asks too, and the haste its book counts down -- and handed over whole, so the window
 * never reads a book in the middle of being written. Owned by the {@link BagScreen}, which paints it under the bag,
 * draws the card of the picture the pointer is on in its own style, and takes a press on a picture, so a click there
 * neither selects nor orders what is behind it.
 */
final class BuffScreen {

    /**
     * How tall the engine's panel at the foot of the window is in the pixels it was designed at: its band of 172 and a
     * pad of 10 over and under it ({@code HeroPanel}'s {@code SLAB_HEIGHT}), grown as the panel is.
     *
     * <p>ponytail: the engine's figure copied, and the squeeze it gives a window too narrow for its blocks left out --
     * an engine that told a game where its panel stands would make this exact: asked as E15 in
     * {@code docs/plan/2026-09-29-engine-requests.md}.
     */
    private static final float PANEL_SLAB = 192f;

    /**
     * One thing on a creature: the skill it is, what it is worth there -- a percent, or a mana aura's tenths of a point
     * a second -- and, for a haste, the frames it has left; an aura lasts while it reaches, and has none.
     */
    record Buff(Skill skill, int figure, int framesLeft) {
    }

    /** A card to draw: what it says, the hints under it, and where its top left goes. */
    record Card(List<String> said, List<String> hints, float x, float y) {
    }

    private final DungeonSettings settings;

    /**
     * The skill each kind is shown as: the first of that kind in the files.
     *
     * <p>ponytail: the first of each kind, and the files have one of each today; two of a kind would want the card to
     * name the one whose figure it shows, which only its bearer's book knows.
     */
    private final Map<SkillEffect, Skill> kinds = new EnumMap<>(SkillEffect.class);

    /** The colour each look starts in -- the first of its layers', as the file writes it -- by the look's name. */
    private final Map<String, Integer> starts = new HashMap<>();

    /**
     * What is on the creatures picked, by id: read after every frame, on the simulation's thread, and handed over
     * whole -- so the window draws an answer, never a book in the middle of being written.
     */
    private volatile Map<Integer, List<Buff>> buffs = Map.of();

    /** The last thing the pointer rested on, kept when it leaves for the row over it: the simulation's thread's own. */
    private int lastPointed = -1;

    /**
     * The creature whose row -- or bar, or the stretch between -- the pointer was on when last painted, or -1: the
     * window's word to the simulation's thread, so a row the pointer is reading stays that one's whatever the pick
     * names behind it.
     */
    private volatile int reading = -1;

    /** Where each picture was drawn this frame: the window's own, so a press on one is known. */
    private final List<Canvas.Box> drawn = new ArrayList<>();

    BuffScreen(DungeonSettings settings) {
        this.settings = settings;
        for (var skill : settings.skills()) {
            kinds.putIfAbsent(skill.effect(), skill);
        }
        for (var effect : settings.effects()) {
            if (!effect.layers().isEmpty() && !effect.layers().getFirst().colour().isEmpty()) {
                starts.put(effect.name(), effect.layers().getFirst().colour().getFirst());
            }
        }
    }

    /** The match it is drawn over, told before it starts: what is on those picked is read after its every frame. */
    void show(DukeGame game) {
        game.onTick(ticked -> buffs = pickedIn(ticked));
    }

    /** What was last handed over, by the id of the creature it is on. */
    Map<Integer, List<Buff>> buffs() {
        return buffs;
    }

    /**
     * What is on the creature selected alone and on the one the pointer rests on, or last rested on, by id -- the one
     * whose row the pointer is on before whatever the pick names behind that row.
     */
    private Map<Integer, List<Buff>> pickedIn(DukeGame game) {
        var selection = game.getSelection();
        int onRow = reading;
        int pointed = game.getPointedAt();
        if (onRow >= 0 || pointed >= 0) {
            lastPointed = onRow >= 0 ? onRow : pointed;
        }
        // In this order, which is the order the rows are drawn in: no hash is walked to draw them.
        var found = new LinkedHashMap<Integer, List<Buff>>();
        for (int id : new int[] {selection.size() == 1 ? selection.getFirst() : -1, lastPointed}) {
            var creature = id < 0 ? null : game.getLogic().findObject(new ObjectId(id));
            var on = creature == null ? List.<Buff>of() : on(creature);
            if (!on.isEmpty()) {
                found.put(id, on);
            }
        }
        return found.isEmpty() ? Map.of() : Collections.unmodifiableMap(found);
    }

    /**
     * What is on {@code creature}, in {@link SkillEffect} order: a haste while one burns on it, and then each aura that
     * reaches it, by the one rule the circle under it asks ({@link SkillBook#aurasOn}). Nothing on one that has fallen.
     */
    private List<Buff> on(GameObject creature) {
        var book = creature.findModule(SkillBook.class);
        if (book == null || creature.isEffectivelyDead()) {
            return List.of();
        }
        var on = new ArrayList<Buff>();
        if (book.getHasteFrames() > 0) {
            on.add(new Buff(kinds.get(SkillEffect.HASTE), book.getHastePercent(), book.getHasteFrames()));
        }
        for (var kind : SkillBook.aurasOn(creature)) {
            on.add(new Buff(kinds.get(kind), SkillBook.auraOn(creature, kind), 0));
        }
        return List.copyOf(on);
    }

    /** What it adds, as the pointer says it: {@code +50% Zarba}, {@code +5 Mana/s}, {@code +75% Hujum tezligi · 4s}. */
    static String addsOf(Buff buff, DungeonSettings settings) {
        var hud = settings.hud();
        var drops = settings.lootDrops();
        int figure = buff.figure();
        return switch (buff.skill().effect()) {
            case DAMAGE_AURA -> "+" + figure + "% " + hud.attackWord();
            case MANA_AURA -> "+" + java.math.BigDecimal.valueOf(figure, 1).stripTrailingZeros().toPlainString() + " "
                    + drops.manaRegenWord();
            case LIFESTEAL_AURA -> figure + "% " + hud.lifestealWord();
            case HASTE -> "+" + figure + "% " + drops.attackSpeedWord() + " · "
                    + Math.ceilDiv(buff.framesLeft(), GameConstants.LOGICFRAMES_PER_SECOND) + hud.secondsWord();
            default -> "";
        };
    }

    // ---- drawing ----

    /**
     * The rows over the bars of those shown, the pointer at {@code (x, y)} -- {@code covered} while it is on something
     * drawn over them, the bag's slab -- and the card of the picture it is on, or null, for the bag to draw last, over
     * everything.
     */
    Card paint(Canvas canvas, DukeGame game, int x, int y, boolean covered) {
        drawn.clear();
        // Sized as the panel is: the window's width over the one it was designed at, within its least and most.
        var panel = settings.panelLook();
        float scale = Math.clamp(canvas.width() / panel.designWidth(), panel.minScale(), panel.maxScale());
        float foot = canvas.height() - (panel.blocks().isEmpty() ? 0f : PANEL_SLAB * scale);
        float size = settings.hud().buffIcon() * scale;
        float gap = Math.max(2f, size * 0.15f);
        var selection = game.getSelection();
        int picked = selection.size() == 1 ? selection.getFirst() : -1;
        // A pointer on the bag, or over the panel at the foot, names nothing, whatever stands behind it there.
        boolean free = !covered && y < foot;
        int pointed = free ? game.getPointedAt() : -1;
        int over = -1;
        Card card = null;
        for (var row : buffs.entrySet()) {
            var bar = canvas.barOf(row.getKey());
            // Kept on the window: a bar with no room above it has its row lowered over it, as a bubble is.
            float top = bar == null ? 0f : Math.max(4f, bar.y() - gap - size);
            if (bar == null || top + size > foot) {
                continue; // no bar placed this frame, or one behind the panel
            }
            var on = row.getValue();
            // The row, its bar and the stretch between: the pointer on its way up from the body keeps it standing.
            float right = Math.max(bar.x() + bar.width(), bar.x() + on.size() * (size + gap));
            float bottom = Math.max(top + size, bar.y() + bar.height());
            boolean onTheRow = free && x >= bar.x() && x < right && y >= top && y < bottom;
            if (onTheRow) {
                over = row.getKey();
            }
            if (row.getKey() != picked && row.getKey() != pointed && !onTheRow) {
                continue;
            }
            for (int at = 0; at < on.size(); at++) {
                var box = new Canvas.Box(bar.x() + at * (size + gap), top, size, size);
                draw(canvas, on.get(at), box);
                drawn.add(box);
                if (free && holds(box, x, y)) {
                    // Hung under the row, over the bar: the rest of the row stays in sight, to be pointed at next.
                    card = cardOf(on.get(at), box.x(), top + size + gap);
                }
            }
        }
        reading = over;
        return card;
    }

    /** Whether a picture was drawn under {@code (x, y)} this frame. */
    boolean pictureAt(int x, int y) {
        return drawn.stream().anyMatch(box -> holds(box, x, y));
    }

    private static boolean holds(Canvas.Box box, float x, float y) {
        return x >= box.x() && x < box.x() + box.width() && y >= box.y() && y < box.y() + box.height();
    }

    /**
     * One picture on stone, framed in the colour its look starts in; a skill with no picture, the first letter of its
     * name on the stone instead.
     */
    private void draw(Canvas canvas, Buff buff, Canvas.Box box) {
        var look = settings.menu();
        var skill = buff.skill();
        float size = box.width();
        canvas.fillRect(box.x(), box.y(), size, size, 0xF0000000 | look.stoneDeepColour());
        if (skill.icon().isBlank()) {
            var letter = skill.name().isEmpty() ? "" : skill.name().substring(0, 1);
            var font = Canvas.Font.of("Georgia", Math.round(size * 0.6f)).bold(true);
            var measure = canvas.measure(font, letter);
            canvas.drawText(font, letter, box.x() + (size - measure.width()) / 2f,
                    box.y() + (size - measure.lineHeight()) / 2f, 0xFF000000 | look.boneColour());
        } else {
            float inset = Math.max(2f, size * 0.1f);
            canvas.drawImage(Canvas.Image.of(skill.icon()), box.x() + inset, box.y() + inset,
                    box.x() + size - inset, box.y() + size - inset, 0xFFFFFFFF, Canvas.Blend.ALPHA);
        }
        canvas.openRect(box.x(), box.y(), size, size, 2f,
                0xFF000000 | starts.getOrDefault(skill.look(), look.stoneEdgeColour()));
    }

    /** Its name, what it adds, and under them its line on what it does. */
    private Card cardOf(Buff buff, float x, float y) {
        var skill = buff.skill();
        return new Card(List.of(skill.name(), addsOf(buff, settings)),
                skill.blurb().isBlank() ? List.of() : List.of(skill.blurb()), x, y);
    }
}
