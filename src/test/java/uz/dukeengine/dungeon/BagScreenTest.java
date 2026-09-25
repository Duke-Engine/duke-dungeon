package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootKind;

/** What the bag says of a thing when the pointer rests on it, and what its hand's aim is called. */
class BagScreenTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void whatAThingGivesIsSaidInTheGamesOwnWords() {
        var hud = SETTINGS.hud();
        assertEquals("+8% " + hud.attackWord(),
                BagScreen.bonusOf(new Loot("Blade", "Blade", "", LootKind.ATTACK, 8, 1, 1), SETTINGS));
        assertEquals("+4% " + hud.armourWord(),
                BagScreen.bonusOf(new Loot("Shield", "Shield", "", LootKind.ARMOUR, 4, 1, 1), SETTINGS));
        assertEquals("+40 " + hud.healthWord(),
                BagScreen.bonusOf(new Loot("Bread", "Bread", "", LootKind.HEALTH, 40, 1, 1), SETTINGS));
        assertEquals("+25 " + hud.manaWord(),
                BagScreen.bonusOf(new Loot("Stone", "Stone", "", LootKind.MANA, 25, 1, 1), SETTINGS));
        assertEquals("+3 Kuch", BagScreen.bonusOf(
                new Loot("Gauntlet", "Gauntlet", "", LootKind.ATTRIBUTE, 3, 1, 1, "Strength"), SETTINGS),
                "an attribute by the word the panel calls it");
    }

    /** Every shipped thing has a picture that is there to be drawn, and something to say for itself. */
    @Test
    void everyShippedThingCanBeDrawnAndSaid() {
        for (var item : SETTINGS.loot()) {
            assertNotNull(BagScreen.class.getResource("/" + item.icon()), item.id() + " draws " + item.icon());
            var said = BagScreen.bonusOf(item, SETTINGS);
            assertTrue(said.startsWith("+" + item.value()) && !said.contains("null"), item.id() + ": " + said);
        }
        assertTrue(SETTINGS.loot().stream().anyMatch(item -> item.kind() == LootKind.ATTRIBUTE),
                "something the dungeon leaves adds to what the hero is made of");
    }

    /** What was drawn, as a window would have drawn it: every picture's path and place, and every line of text. */
    private static final class Drawn implements uz.dukeengine.client3d.Canvas {

        record Picture(String path, float x0, float y0, float x1, float y1, int argb) {
            int middleX() {
                return Math.round((x0 + x1) / 2f);
            }

            int middleY() {
                return Math.round((y0 + y1) / 2f);
            }
        }

        final java.util.List<Picture> pictures = new java.util.ArrayList<>();
        final java.util.List<String> text = new java.util.ArrayList<>();

        void clear() {
            pictures.clear();
            text.clear();
        }

        Picture picture(String path) {
            return pictures.stream().filter(it -> it.path().equals(path)).findFirst().orElse(null);
        }

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
            pictures.add(new Picture(image.path(), x0, y0, x1, y1, argb));
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
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
        public void drawText(Font font, String line, float x, float y, int argb) {
            text.add(line);
        }

        @Override
        public Measure measure(Font font, String line) {
            return new Measure(line.length() * 8, font.pixelHeight() + 4);
        }
    }

    private static uz.dukeengine.core.thing.GameObject find(uz.dukeengine.game.DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /**
     * The bag, handled the way a hand handles it: what is in it is drawn, the pointer on a thing says what it
     * gives, the right button takes it in hand, and the place the client's aim comes back with is where he goes to
     * put it down.
     */
    @Test
    void aThingInTheBagIsShownTakenInHandAndPutDown() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var blade = SETTINGS.loot().stream().filter(item -> item.id().equals("Blade")).findFirst().orElseThrow();
        session.progress().getLoot().take(blade, 0, 30);
        var duke = uz.dukeengine.client3d.Duke3D.of(game, uz.dukeengine.client3d.Visuals.create());
        var bag = new BagScreen(SETTINGS, duke);
        bag.show(session);
        var drawn = new Drawn();

        bag.paint(drawn);
        var picture = drawn.picture(blade.icon());
        assertNotNull(picture, "the blade is drawn in its slot: " + drawn.pictures);
        assertTrue(drawn.text.contains(SETTINGS.hud().itemsWord()), "under the bag's heading");

        bag.take(new uz.dukeengine.client3d.CanvasInput.Pointer(picture.middleX(), picture.middleY()));
        drawn.clear();
        bag.paint(drawn);
        assertTrue(drawn.text.contains(blade.name()) && drawn.text.contains(BagScreen.bonusOf(blade, SETTINGS)),
                "the pointer on it says what it is and what it gives: " + drawn.text);

        assertTrue(bag.take(new uz.dukeengine.client3d.CanvasInput.Button(picture.middleX(), picture.middleY(),
                uz.dukeengine.client3d.CanvasInput.Mouse.RIGHT, true, false)), "the right button is the bag's");
        drawn.clear();
        bag.paint(drawn);
        assertTrue(drawn.text.contains(SETTINGS.lootDrops().dropHint()), "in hand, and told how to put it down");
        assertTrue(bag.take(new uz.dukeengine.client3d.CanvasInput.Button(picture.middleX(), picture.middleY(),
                uz.dukeengine.client3d.CanvasInput.Mouse.LEFT, true, false)), "a click on the bag stays on it");
        assertFalse(bag.take(new uz.dukeengine.client3d.CanvasInput.Button(40, 40,
                uz.dukeengine.client3d.CanvasInput.Mouse.LEFT, true, false)), "one on the world goes to the world");

        // Where the client's aim was put down, as the window reports it.
        var hero = find(game, "Rogue");
        game.pressCommand("drop:0", hero.getPosition(), 0f, -1);
        game.runHeadless(4);
        assertTrue(session.progress().getLoot().getFound().isEmpty(), "out of his bag");
        assertNotNull(find(game, "Chest"), "and on the floor");
    }

    /** The pointer on a thing lying on the floor, with his hero in hand, says what it gives and how to take it. */
    @Test
    void aThingOnTheFloorSaysWhatItGives() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var bag = new BagScreen(SETTINGS,
                uz.dukeengine.client3d.Duke3D.of(game, uz.dukeengine.client3d.Visuals.create()));
        bag.show(session);
        var blade = SETTINGS.loot().stream().filter(item -> item.id().equals("Blade")).findFirst().orElseThrow();
        session.progress().getLoot().take(blade, 0, 30);
        game.pressCommand("drop:0", hero.getPosition(), 0f, -1);
        game.runHeadless(4);
        var chest = find(game, "Chest");
        assertNotNull(chest);

        game.setSelection(List.of(hero.getId().value()));
        game.setPointedAt(chest.getId().value());
        game.runHeadless(2);
        assertEquals(uz.dukeengine.dungeon.party.PartyOrders.PICK_UP, game.getSnapshot().contextOrder(),
                "a click on it is a pickup, and the pointer shows that");
        var drawn = new Drawn();
        bag.paint(drawn);
        assertTrue(drawn.text.contains(blade.name()) && drawn.text.contains(SETTINGS.lootDrops().takeHint()),
                "and says what is in it: " + drawn.text);
    }

    /** A click on a chest is answered as an attack is — the ring round it, blinking — but yellow, not the arrowheads. */
    @Test
    void aPickupIsAnsweredWithTheRingInYellow() {
        var mark = SETTINGS.orderMark();
        assertTrue(mark.ringsContextOrders(), "the ring, not a walk's arrowheads");
        assertEquals(0xFFD23C, mark.contextColour());
        assertTrue(mark.contextColour() != mark.attackColour(), "and not a fight's colour");
    }

    @Test
    void aDropAimNamesItsSlot() {
        assertEquals(3, BagScreen.slotOf("drop:3"));
        assertEquals(-1, BagScreen.slotOf("cast:Q"));
        assertEquals(-1, BagScreen.slotOf("drop:x"));
        assertEquals(-1, BagScreen.slotOf(null));
        assertFalse(BagScreen.slotOf("drop:0") < 0);
    }
}
