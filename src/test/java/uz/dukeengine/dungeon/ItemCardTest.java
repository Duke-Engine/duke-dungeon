package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.loot.Items;
import uz.dukeengine.rpg.item.Loot;

/**
 * A thing's card in colour, painted over the bag's own so it hides it whole, comparing it with what is worn; and the
 * belt's keys and slots.
 */
class ItemCardTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Items ITEMS = SETTINGS.items();

    /** What was drawn, in order: text with its colour, and every filled rectangle. */
    private static final class Drawn implements Canvas {

        record Text(String text, int argb) {
        }

        final List<Text> text = new ArrayList<>();
        final List<float[]> rects = new ArrayList<>();
        final List<Integer> fills = new ArrayList<>();
        final List<String> pictures = new ArrayList<>();

        @Override
        public int width() {
            return 1600;
        }

        @Override
        public int height() {
            return 900;
        }

        @Override
        public void drawImage(Image image, float x0, float y0, float x1, float y1, int argb, Blend blend) {
            pictures.add(image.path());
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
            rects.add(new float[] {x, y, w, h});
            fills.add(argb);
        }

        @Override
        public void openRect(float x, float y, float w, float h, float lineWidth, int argb) {
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
            this.text.add(new Text(text, argb));
        }

        @Override
        public Measure measure(Font font, String text) {
            return new Measure(text.length() * font.pixelHeight() / 2, font.pixelHeight());
        }

        /** The colour {@code said} is drawn in on the last card that starts with {@code name}: the game's, over the bag's. */
        int colourOf(String name, String said) {
            int from = 0;
            for (int i = 0; i < text.size(); i++) {
                from = text.get(i).text().equals(name) ? i : from;
            }
            return text.subList(from, text.size()).stream().filter(each -> each.text().equals(said)).findFirst()
                    .orElseThrow().argb() & 0xFFFFFF;
        }
    }

    /** A match being played, its bag drawn by the engine's screen and the game's over it. */
    private record Played(Dungeon.Session session, DialogueScreen screen) {
    }

    private static Played played() {
        var session = Dungeon.newSession(21L);
        session.game().runHeadless(2);
        var bag = Bag.of(SETTINGS, (button, ended) -> { }, System::nanoTime);
        Bag.show(bag, session, SETTINGS);
        return new Played(session, new DialogueScreen(bag, bag, SETTINGS));
    }

    /** The pointer on the backpack's {@code place}, as the bag lays itself out on a 1600 by 900 window, and painted. */
    private static Drawn pointAt(Played played, int place) {
        var bag = played.session().progress().getLoot();
        float slot = Math.clamp(Math.round(900 * 0.05f), 34, 72);
        float gap = Math.max(3f, slot / 10f);
        float pad = Math.max(6f, slot / 6f);
        float heading = Math.round(slot * 0.27f) * 1.4f;
        int rows = Math.max((bag.backpackSize() + 1) / 2, bag.wornSlots().size());
        float wide = pad * 2f + 2 * slot + gap + slot + gap * 3f;
        float high = pad * 2f + heading + gap + rows * slot + (rows - 1) * gap;
        float left = 1600 - wide - 16f;
        float top = (900 - high) / 2f - 900 * 0.06f;
        float x = left + pad + slot + gap * 3f + (place % 2) * (slot + gap);
        float y = top + pad + heading + gap + (place / 2) * (slot + gap);
        played.screen().take(new CanvasInput.Pointer(Math.round(x) + 5, Math.round(y) + 5));
        var canvas = new Drawn();
        played.screen().paint(canvas);
        return canvas;
    }

    @Test
    void theBagsCardIsInTheRaritysColourAndComparesWithWhatIsWorn() {
        var played = played();
        var bag = played.session().progress().getLoot();
        var worn = ITEMS.loot(ITEMS.make(ITEMS.base("qisqa_qilich"), Items.COMMON, 1, 3));
        var rare = ITEMS.loot(ITEMS.make(ITEMS.base("keng_qilich"), Items.RARE, 7, 42));
        bag.take(worn, 0, 0); // worn: its slot was free
        bag.take(rare, 0, 0); // carried, in the backpack's first place
        played.session().game().runHeadless(1);

        var drawn = pointAt(played, 0);
        assertEquals(ITEMS.rules().colours().get(Items.RARE), drawn.colourOf(rare.name(), rare.name()),
                "the name, in gold");
        for (var line : Bag.cardLines(rare, SETTINGS)) {
            assertEquals(line.colour(), drawn.colourOf(rare.name(), line.text()), line.text());
        }
        int better = ITEMS.figures(rare).get("ATTACK") - ITEMS.figures(worn).get("ATTACK");
        var compared = Bag.compared(rare, bag, SETTINGS);
        assertEquals(ITEMS.rules().compareWord(), compared.getFirst().text());
        assertTrue(compared.contains(new Items.Line("+" + better + "% Zarba", ITEMS.rules().betterColour())),
                "more damage, green: " + compared);
        assertTrue(drawn.text.stream().anyMatch(t -> t.text().equals(ITEMS.rules().compareWord())),
                "under its hints on the bag's card");
        assertTrue(Bag.compared(worn, bag, SETTINGS).isEmpty(), "what is worn compares with nothing");
    }

    @Test
    void theBeltShowsItsStacksAndItsKeysDrink() {
        var played = played();
        var session = played.session();
        var bag = session.progress().getLoot();
        var stack = ITEMS.potion(ITEMS.potion("hayot_shishasi"), 4, 9);
        bag.take(stack, 0, 0);
        session.game().runHeadless(1);
        var drawn = new Drawn();
        played.screen().paint(drawn);
        assertTrue(drawn.pictures.contains(stack.icon()), "the stack on the belt");
        assertTrue(drawn.text.stream().anyMatch(t -> t.text().equals("4")), "with its count");

        var keys = Main.controls(SETTINGS);
        assertTrue(keys.pressNow(session.game(), '1'), "1 is the belt's");
        session.game().runHeadless(2);
        assertEquals(3, bag.at(0).value(), "and drinks from its first stack");
        assertFalse(keys.pressNow(session.game(), (char) ('1' + ITEMS.rules().beltSize())),
                "a key past the belt is not its");
    }

    @Test
    void theBeltsKeysAreNamedOnTheSettingsScreen() {
        var keys = Main.controls(SETTINGS);
        assertEquals("Belbog'dan ichish: 1", keys.wordOf('1'));
        assertEquals("Belbog'dan ichish: 4", keys.wordOf('4'));
    }

    @Test
    void theCharacterSheetSaysACriticalBlow() {
        var session = played().session();
        var bag = session.progress().getLoot();
        bag.take(new Loot("crit_ring", "Uzuk", "", "ATTRIBUTE", 1, 0, 1, "Strength", "", 0, 0, 1,
                uz.dukeengine.rpg.item.ItemUse.NONE, "", "Uzuk", List.of(), List.of(
                        new uz.dukeengine.rpg.item.Affix("CRIT_CHANCE", 12),
                        new uz.dukeengine.rpg.item.Affix("CRIT_DAMAGE", 40))), 0, 0);
        session.game().runHeadless(3);
        var rows = CharacterSheet.figures(session.run().getSheet(), SETTINGS);
        assertTrue(rows.contains(new CharacterSheet.Row(ITEMS.rules().critChanceWord(), "12% (+40%)")), rows.toString());
    }

    @Test
    void anEpicsStoryIsBrokenToTheCardsWidth() {
        var played = played();
        var bag = played.session().progress().getLoot();
        Loot epic = ITEMS.gear(1, 10, Items.EPIC);
        var said = played.screen().cards().said(epic, bag).stream().map(Items.Line::text).toList();
        assertEquals(epic.name(), said.getFirst());
        var lore = ITEMS.lines(epic).stream().filter(line -> line.colour() == ITEMS.rules().loreColour()).toList();
        assertTrue(lore.size() >= 2, "a long story is more than one line");
        assertTrue(lore.stream().allMatch(line -> line.text().length() <= ITEMS.rules().wrap()), lore.toString());
    }
}
