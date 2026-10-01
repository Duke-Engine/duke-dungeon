package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.dungeon.world.Hud;
import uz.dukeengine.game.DukeGame;

/**
 * What is on a creature the player picks, drawn over its bar as Dota draws it: a small picture for each aura on it and
 * a haste, framed in its kind's colour, and a card under the pointer saying what it adds -- on a floor being played,
 * through the bag that owns it, with a summoner standing on the hero and its haste burning.
 */
class BuffScreenTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Where the client placed the picked one's bar: well clear of every edge, and of the panel at the window's foot. */
    private static final Canvas.Box BAR = new Canvas.Box(700f, 400f, 80f, 8f);

    /** Every picture drawn and where, every square filled and framed, every line of text, on a window of a size. */
    private static final class Drawn implements Canvas {

        record Picture(String path, float x0, float y0, float x1, float y1) {
            int middleX() {
                return Math.round((x0 + x1) / 2f);
            }

            int middleY() {
                return Math.round((y0 + y1) / 2f);
            }
        }

        record Square(float x, float y, float w, float h, int argb) {
            boolean holds(float px, float py) {
                return px >= x && px <= x + w && py >= y && py <= y + h;
            }
        }

        record Line(String text, float x, float y) {
        }

        final List<Picture> pictures = new ArrayList<>();
        final List<Square> filled = new ArrayList<>();
        final List<Square> framed = new ArrayList<>();
        final List<Line> lines = new ArrayList<>();
        private final int wide;
        private final int high;
        /** Where each thing's bar stands, by its id: nowhere, unless a test says. */
        IntFunction<Box> bars = id -> null;

        Drawn(int wide, int high) {
            this.wide = wide;
            this.high = high;
        }

        Drawn() {
            this(1600, 900);
        }

        void clear() {
            pictures.clear();
            filled.clear();
            framed.clear();
            lines.clear();
        }

        Picture picture(String path) {
            return pictures.stream().filter(it -> it.path().equals(path)).findFirst().orElse(null);
        }

        List<String> text() {
            return lines.stream().map(Line::text).toList();
        }

        /** The squares framed right over {@link #BAR}, left to right: the row. */
        List<Square> row() {
            return framed.stream()
                    .filter(it -> it.y() + it.h() <= BAR.y() && it.y() + it.h() > BAR.y() - 15f
                            && it.x() >= BAR.x() - 1f && it.x() < BAR.x() + 300f)
                    .sorted(Comparator.comparingDouble(Square::x)).toList();
        }

        @Override
        public int width() {
            return wide;
        }

        @Override
        public int height() {
            return high;
        }

        @Override
        public void drawImage(Image image, float x0, float y0, float x1, float y1, int argb, Blend blend) {
            pictures.add(new Picture(image.path(), x0, y0, x1, y1));
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
            filled.add(new Square(x, y, w, h, argb));
        }

        @Override
        public void openRect(float x, float y, float w, float h, float lineWidth, int argb) {
            framed.add(new Square(x, y, w, h, argb));
        }

        @Override
        public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        }

        @Override
        public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        }

        @Override
        public void line(float x0, float y0, float x1, float y1, float width, int argb, int argbEnd) {
        }

        @Override
        public void clip(float x0, float y0, float x1, float y1) {
        }

        @Override
        public void noClip() {
        }

        @Override
        public void drawText(Font font, String text, float x, float y, int argb) {
            lines.add(new Line(text, x, y));
        }

        @Override
        public Measure measure(Font font, String text) {
            return new Measure(text.length() * 8, font.pixelHeight() + 4);
        }

        @Override
        public Box barOf(int id) {
            return bars.apply(id);
        }
    }

    /** A floor being played with the bag drawn over it, and the one a summoner standing on the hero has hastened. */
    private record Floor(DukeGame game, BagScreen bag, GameObject hastened) {

        int id() {
            return hastened.getId().value();
        }

        /** Drawn on {@code canvas}, its bar placed at {@code bar}. */
        Drawn paint(Drawn canvas, Canvas.Box bar) {
            canvas.clear();
            canvas.bars = at -> at == id() ? bar : null;
            bag.paint(canvas);
            return canvas;
        }
    }

    /**
     * Seed 21's first floor, the bag -- drawn by {@code settings} -- told of it before it starts, as the game tells it,
     * and a summoner of the dungeon's put down on the hero, its haste cast by hand: on the sturdiest of its own near it,
     * or on itself, in its might either way. That one is picked alone, and a frame has gone by.
     */
    private static Floor floor(DungeonSettings settings) {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        var bag = new BagScreen(settings, Duke3D.of(game, Visuals.create()), () -> 0L);
        bag.show(session);
        game.runHeadless(2);
        var logic = game.getLogic();
        var hero = logic.getObjects().stream().filter(one -> one.getTemplate().name().equals("Rogue")).findFirst()
                .orElseThrow();
        int dungeon = logic.getObjects().stream().filter(one -> one.getPlayerIndex() != hero.getPlayerIndex()
                && one.findModule(WeaponUpdate.class) != null).findFirst().orElseThrow().getPlayerIndex();
        var summoner = logic.spawn(logic.findTemplate("SkeletonSummoner"), hero.getPosition(), dungeon);
        assertTrue(summoner.findModule(SkillBook.class).cast('W', 1, null, null), "the premise: it cast its haste");
        var hastened = logic.getObjects().stream().filter(one -> one.findModule(SkillBook.class) != null
                && one.findModule(SkillBook.class).getHasteFrames() > 0).findFirst().orElseThrow();
        game.setSelection(List.of(hastened.getId().value()));
        game.runHeadless(1);
        return new Floor(game, bag, hastened);
    }

    /** The shipped skill of a kind. */
    private static Skill skill(SkillEffect kind) {
        return SETTINGS.skills().stream().filter(skill -> skill.effect() == kind).findFirst().orElseThrow();
    }

    /** The colour {@code look} starts in: the first of its layers', as the file writes it. */
    private static int startOf(String look) {
        var effect = SETTINGS.effects().stream().filter(it -> it.name().equals(look)).findFirst().orElseThrow();
        return effect.layers().getFirst().colour().getFirst();
    }

    /** What a buff adds, in the game's own words: the figure the simulation gave, and the word the file gives it. */
    @Test
    void whatEachAddsIsSaidInTheGamesOwnWords() {
        var hud = SETTINGS.hud();
        var drops = SETTINGS.lootDrops();

        assertEquals("+50% " + hud.attackWord(),
                BuffScreen.addsOf(new BuffScreen.Buff(skill(SkillEffect.DAMAGE_AURA), 50, 0), SETTINGS));
        assertEquals("+5 " + drops.manaRegenWord(),
                BuffScreen.addsOf(new BuffScreen.Buff(skill(SkillEffect.MANA_AURA), 50, 0), SETTINGS),
                "tenths of a point a second, as a whole point");
        assertEquals("+2.5 " + drops.manaRegenWord(),
                BuffScreen.addsOf(new BuffScreen.Buff(skill(SkillEffect.MANA_AURA), 25, 0), SETTINGS));
        assertEquals("10% " + hud.lifestealWord(),
                BuffScreen.addsOf(new BuffScreen.Buff(skill(SkillEffect.LIFESTEAL_AURA), 10, 0), SETTINGS));
        assertEquals("+75% " + drops.attackSpeedWord() + " · 5" + hud.secondsWord(),
                BuffScreen.addsOf(new BuffScreen.Buff(skill(SkillEffect.HASTE), 75, 150), SETTINGS),
                "and the seconds it has left");
        assertEquals("+75% " + drops.attackSpeedWord() + " · 1" + hud.secondsWord(),
                BuffScreen.addsOf(new BuffScreen.Buff(skill(SkillEffect.HASTE), 75, 1), SETTINGS),
                "a part of a second is a second");
        assertFalse(hud.lifestealWord().isBlank(), "a lifesteal's share has a word");
        assertNotEquals(Hud.DEFAULTS.lifestealWord(), hud.lifestealWord(), "the file says it, not the default");
    }

    /**
     * Over the picked one's bar, left-aligned with it: one small square a buff, side by side in SkillEffect order -- the
     * haste, then the might -- each the skill's own picture, framed in the colour its look starts in.
     */
    @Test
    void oneSmallPictureABuffOverItsBarInOrderFramedInItsKindsColour() {
        var floor = floor(SETTINGS);
        var drawn = floor.paint(new Drawn(), BAR);

        var row = drawn.row();
        assertEquals(2, row.size(), "a square for the haste and one for the might: " + drawn.framed);
        assertEquals(BAR.x(), row.get(0).x(), 0.01f, "left-aligned with the bar");
        assertTrue(row.get(1).x() >= row.get(0).x() + row.get(0).w(), "side by side: " + row);
        for (var square : row) {
            assertEquals(square.w(), square.h(), 0.01f, "square: " + square);
        }
        assertEquals(List.of(startOf(skill(SkillEffect.HASTE).look()), startOf(skill(SkillEffect.DAMAGE_AURA).look())),
                row.stream().map(square -> square.argb() & 0xFFFFFF).toList(), "framed in the colour of each one's look");
        var pictures = List.of(skill(SkillEffect.HASTE).icon(), skill(SkillEffect.DAMAGE_AURA).icon());
        for (int at = 0; at < 2; at++) {
            var picture = drawn.picture(pictures.get(at));
            assertNotNull(picture, pictures.get(at) + " is drawn: " + drawn.pictures);
            assertTrue(row.get(at).holds(picture.x0(), picture.y0()) && row.get(at).holds(picture.x1(), picture.y1()),
                    "inside its frame: " + picture + " in " + row.get(at));
        }

        var design = SETTINGS.panelLook().designWidth();
        var small = floor.paint(new Drawn(Math.round(design), 900), BAR).row();
        assertEquals(SETTINGS.hud().buffIcon(), small.getFirst().w(), 0.01f, "BuffIcon across, at the design's width");
        assertTrue(SETTINGS.hud().buffIcon() >= 16f && SETTINGS.hud().buffIcon() <= 24f,
                "and that is small: " + SETTINGS.hud().buffIcon());
    }

    /** A skill with no picture is a stone square with the first letter of its name, framed as any is. */
    @Test
    void aSkillWithNoPictureIsAStoneSquareWithTheFirstLetterOfItsName() {
        var might = skill(SkillEffect.DAMAGE_AURA);
        var block = ShippedBlock.of("SkeletonSummoner").text();
        var bare = block.replace("      Icon = " + might.icon() + "\n", "");
        assertNotEquals(block, bare, "the premise: the might's picture was taken out");
        var floor = floor(DungeonSettings.parse(Content.data().replace(block, bare)));

        var drawn = floor.paint(new Drawn(), BAR);

        var row = drawn.row();
        assertEquals(2, row.size(), "the premise: the haste and the might are both there: " + drawn.framed);
        assertNull(drawn.picture(might.icon()), "no picture for the might");
        var letter = drawn.lines.stream().filter(line -> line.text().equals(might.name().substring(0, 1)))
                .findFirst().orElse(null);
        assertNotNull(letter, "the might's first letter: " + drawn.text());
        assertTrue(row.get(1).holds(letter.x(), letter.y()), "in its square: " + letter + " in " + row.get(1));
        assertTrue(drawn.filled.stream().anyMatch(square -> square.x() == row.get(1).x() && square.y() == row.get(1).y()
                && square.w() == row.get(1).w()), "on stone: " + drawn.filled);
        assertNotNull(drawn.picture(skill(SkillEffect.HASTE).icon()), "and the haste keeps its own");
    }

    /** Where the client placed no bar this frame -- off the screen, or none at all -- nothing is drawn. */
    @Test
    void nothingIsDrawnOverACreatureTheClientPlacedNoBarFor() {
        var floor = floor(SETTINGS);
        var drawn = floor.paint(new Drawn(), null);

        for (var kind : List.of(SkillEffect.HASTE, SkillEffect.DAMAGE_AURA)) {
            assertNull(drawn.picture(skill(kind).icon()), kind + ": " + drawn.pictures);
            int colour = startOf(skill(kind).look());
            assertTrue(drawn.framed.stream().noneMatch(square -> (square.argb() & 0xFFFFFF) == colour),
                    kind + "'s frame: " + drawn.framed);
        }
    }

    /**
     * The pointer on a picture draws its card, as the bag's are drawn: the skill's name, what it adds from the figures --
     * a haste with the seconds it has left -- and its line of what it does; hung under the row, so none of the row is
     * hidden from the pointer's next move.
     */
    @Test
    void thePointerOnAPictureSaysWhatItIsAndWhatItAdds() {
        var floor = floor(SETTINGS);
        var drawn = floor.paint(new Drawn(), BAR);
        var book = floor.hastened().findModule(SkillBook.class);

        for (var buff : List.of(new BuffScreen.Buff(skill(SkillEffect.HASTE), 75, book.getHasteFrames()),
                new BuffScreen.Buff(skill(SkillEffect.DAMAGE_AURA), 50, 0))) {
            var picture = floor.paint(drawn, BAR).picture(buff.skill().icon());
            floor.bag().take(new CanvasInput.Pointer(picture.middleX(), picture.middleY()));
            floor.paint(drawn, BAR);
            var said = List.of(buff.skill().name(), BuffScreen.addsOf(buff, SETTINGS), buff.skill().blurb());
            assertFalse(buff.skill().blurb().isBlank(), "the premise: " + buff.skill().name() + " says what it does");
            assertTrue(drawn.text().containsAll(said), "the pointer on " + buff.skill().name() + " says " + said + ": "
                    + drawn.text());
            var name = drawn.lines.stream().filter(line -> line.text().equals(buff.skill().name())).findFirst()
                    .orElseThrow();
            var card = drawn.filled.reversed().stream().filter(slab -> slab.holds(name.x(), name.y())).findFirst()
                    .orElseThrow();
            var row = drawn.row();
            assertTrue(card.y() >= row.getFirst().y() + row.getFirst().h(), "under the row: " + card + ", " + row);
        }

        floor.bag().take(new CanvasInput.Pointer(40, 40));
        floor.paint(drawn, BAR);
        assertFalse(drawn.text().contains(skill(SkillEffect.DAMAGE_AURA).name()), "and nothing once it is off them");
    }

    /** A left press on a picture is the screen's, and so is its letting go: one beside the row goes to the world. */
    @Test
    void aPressOnAPictureIsTakenAndOneBesideItIsNot() {
        var floor = floor(SETTINGS);
        var drawn = floor.paint(new Drawn(), BAR);
        var might = drawn.picture(skill(SkillEffect.DAMAGE_AURA).icon());
        assertNotNull(might, "the premise: the might is drawn: " + drawn.pictures);

        assertTrue(floor.bag().take(new CanvasInput.Button(might.middleX(), might.middleY(), CanvasInput.Mouse.LEFT,
                true, false)), "a press on it selects nothing behind it, and orders nothing");
        assertTrue(floor.bag().take(new CanvasInput.Button(might.middleX(), might.middleY(), CanvasInput.Mouse.LEFT,
                false, false)), "and its letting go with it");
        int beside = Math.round(BAR.x()) - 6;
        assertFalse(floor.bag().take(new CanvasInput.Button(beside, might.middleY(), CanvasInput.Mouse.LEFT, true,
                false)), "one beside the row is the world's");
        assertFalse(floor.bag().take(new CanvasInput.Button(beside, might.middleY(), CanvasInput.Mouse.LEFT, false,
                false)), "and so is its letting go");
    }

    /**
     * The picked one's row always; the pointed one's while the pointer is on it or on its row, and not once it is off
     * both; and none behind the panel at the window's foot: a pointer over it names nothing, and a bar behind it has no
     * row over it.
     */
    @Test
    void thePickedOnesRowAlwaysThePointedOnesWhileThePointerIsOnItOrItsRow() {
        var floor = floor(SETTINGS);
        var game = floor.game();
        var drawn = new Drawn();
        var haste = skill(SkillEffect.HASTE).icon();
        floor.bag().take(new CanvasInput.Pointer(40, 40));
        var onRow = floor.paint(drawn, BAR).picture(haste);
        assertNotNull(onRow, "picked alone, its row: " + drawn.pictures);

        game.setSelection(List.of());
        game.setPointedAt(floor.id());
        game.runHeadless(1);
        assertNotNull(floor.paint(drawn, BAR).picture(haste), "picked no more, and the pointer on it");
        game.setPointedAt(-1);
        assertNull(floor.paint(drawn, BAR).picture(haste), "the pointer off it and off its row");
        floor.bag().take(new CanvasInput.Pointer(onRow.middleX(), onRow.middleY()));
        assertNotNull(floor.paint(drawn, BAR).picture(haste), "the pointer on its row");

        game.setPointedAt(floor.id());
        floor.bag().take(new CanvasInput.Pointer(40, 890));
        assertNull(floor.paint(drawn, BAR).picture(haste), "a pointer over the panel names nothing behind it");

        game.setSelection(List.of(floor.id()));
        game.setPointedAt(-1);
        game.runHeadless(1);
        floor.bag().take(new CanvasInput.Pointer(40, 40));
        assertNotNull(floor.paint(drawn, BAR).picture(haste), "the premise: picked again, its row");
        assertNull(floor.paint(drawn, new Canvas.Box(BAR.x(), 860f, BAR.width(), BAR.height())).picture(haste),
                "its bar behind the panel");
    }

    /**
     * Every aura and haste the files hold has a picture that is there to be drawn -- the stat pictures, until aura art
     * is painted -- and a line on what it does.
     */
    @Test
    void everyAuraAndHasteHasAPictureThereToBeDrawnAndALineOnWhatItDoes() {
        var shown = SETTINGS.skills().stream()
                .filter(skill -> skill.effect().isAura() || skill.effect() == SkillEffect.HASTE).toList();
        assertEquals(4, shown.size(), "the premise: three auras and a haste");
        for (var skill : shown) {
            assertFalse(skill.icon().isBlank(), skill.name() + " names a picture");
            assertNotNull(BuffScreenTest.class.getResource("/" + skill.icon()), skill.name() + " draws " + skill.icon());
            assertFalse(skill.blurb().isBlank(), skill.name() + " says what it does");
        }
    }
}
