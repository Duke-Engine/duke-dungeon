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

    private static Drawn pointAt(Played played, int place) {
        var bag = played.session().progress().getLoot();
        var probe = new Drawn();
        var layout = ItemCard.Layout.of(probe, bag);
        played.screen().take(new CanvasInput.Pointer(Math.round(layout.slotX(place)) + 5,
                Math.round(layout.slotY(place)) + 5));
        var canvas = new Drawn();
        played.screen().paint(canvas);
        return canvas;
    }

    @Test
    void theCardIsInItsRaritysColourAndComparesWithWhatIsWorn() {
        var played = played();
        var bag = played.session().progress().getLoot();
        var worn = ITEMS.loot(ITEMS.make(ITEMS.base("qisqa_qilich"), Items.COMMON, 1, 3));
        var rare = ITEMS.loot(ITEMS.make(ITEMS.base("keng_qilich"), Items.RARE, 7, 42));
        bag.take(worn, 0, 0); // worn: its slot was free
        bag.take(rare, 0, 0); // carried, in the backpack's first place
        played.session().game().runHeadless(1);

        var drawn = pointAt(played, 0);
        assertEquals(ITEMS.rules().colours().get(Items.RARE), drawn.colourOf(rare.name(), rare.name()), "the name, in gold");
        assertEquals(ITEMS.rules().colours().get(Items.RARE), drawn.colourOf(rare.name(), ITEMS.rarityLine(rare).text()));
        for (var line : ITEMS.lines(rare)) {
            assertEquals(line.colour(), drawn.colourOf(rare.name(), line.text()), line.text());
        }
        assertTrue(drawn.text.stream().anyMatch(t -> t.text().equals(ITEMS.rules().compareWord())));
        int better = rare.value() - worn.value();
        assertTrue(better > 0);
        var compared = drawn.text.subList(drawn.text.indexOf(new Drawn.Text(ITEMS.rules().compareWord(),
                0xFF000000 | SETTINGS.menu().hintColour())), drawn.text.size());
        assertTrue(compared.contains(new Drawn.Text("+" + better + "% Zarba", 0xFF000000 | ITEMS.rules().betterColour())),
                "more damage, green: " + compared);
    }

    @Test
    void theGamesCardCoversTheBagsWhole() {
        var played = played();
        var bag = played.session().progress().getLoot();
        bag.take(ITEMS.loot(ITEMS.make(ITEMS.base("qisqa_qilich"), Items.MAGIC, 1, 3)), 0, 0);
        bag.take(ITEMS.loot(ITEMS.make(ITEMS.base("gurzi"), Items.RARE, 9, 8)), 0, 0);
        played.session().game().runHeadless(1);
        var drawn = pointAt(played, 0);
        int stone = SETTINGS.menu().stoneDeepColour();
        int theirs = -1;
        for (int i = 0; i < drawn.rects.size(); i++) {
            if (drawn.fills.get(i) == (0xF0000000 | stone)) {
                theirs = i;
            }
        }
        assertTrue(theirs >= 0, "the bag drew its own card");
        var card = drawn.rects.get(theirs);
        float[] ours = null;
        for (int i = theirs + 1; i < drawn.rects.size(); i++) {
            var rect = drawn.rects.get(i);
            if (drawn.fills.get(i) == (0xFF000000 | stone) && rect[2] >= card[2] && rect[3] >= card[3]) {
                ours = rect;
            }
        }
        assertNotNull(ours, "and the game's over it");
        assertTrue(ours[0] <= card[0] && ours[1] <= card[1] && ours[0] + ours[2] >= card[0] + card[2]
                && ours[1] + ours[3] >= card[1] + card[3], "every corner of it");
    }

    @Test
    void theBeltShowsItsStacksAndAKeyDrinks() {
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
        for (int key = 1; key <= ITEMS.rules().beltSize(); key++) {
            int each = key;
            assertTrue(drawn.text.stream().anyMatch(t -> t.text().equals(Integer.toString(each))), "key " + key);
        }

        assertTrue(played.screen().take(new CanvasInput.Key(ItemCard.KEY_1, true, false)), "1 is the belt's");
        session.game().runHeadless(2);
        assertEquals(3, bag.at(0).value(), "and drinks from its first stack");
        assertFalse(played.screen().take(new CanvasInput.Key(ItemCard.KEY_1 + ITEMS.rules().beltSize(), true, false)),
                "a key past the belt is not its");
    }

    @Test
    void aThingOnTheFloorHasTheSameCard() {
        var played = played();
        var card = played.screen().cards();
        var bag = played.session().progress().getLoot();
        Loot epic = ITEMS.gear(1, 10, Items.EPIC);
        var said = card.said(epic, bag, -1).stream().map(Items.Line::text).toList();
        assertEquals(epic.name(), said.getFirst());
        assertTrue(said.contains(ITEMS.lines(epic).getLast().text()), "its story");
    }
}
