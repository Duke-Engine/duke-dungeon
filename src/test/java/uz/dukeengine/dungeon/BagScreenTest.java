package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            if (item.kind() == LootKind.KEY) {
                assertEquals("", said, "a key gives nothing, and says nothing of it");
                continue;
            }
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

    /**
     * A bag drawn over {@code game} with its clock held still, so whether the world is being played is for its frames
     * to say and not for how long the machine running the test took between two paints.
     */
    private static BagScreen bagOver(uz.dukeengine.game.DukeGame game) {
        return new BagScreen(SETTINGS,
                uz.dukeengine.client3d.Duke3D.of(game, uz.dukeengine.client3d.Visuals.create()), () -> 0L);
    }

    /**
     * A world that has stood still for half a second is taken for one a menu is over, and the bag is put away with
     * it; the next frame that comes brings it back. By the clock handed in, never the machine's.
     */
    @Test
    void aWorldStandingStillPutsTheBagAwayAndAFrameBringsItBack() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        var now = new java.util.concurrent.atomic.AtomicLong();
        var bag = new BagScreen(SETTINGS,
                uz.dukeengine.client3d.Duke3D.of(game, uz.dukeengine.client3d.Visuals.create()), now::get);
        bag.show(session);
        game.runHeadless(2);
        var drawn = new Drawn();
        bag.paint(drawn);
        assertTrue(drawn.text.contains(SETTINGS.hud().itemsWord()), "drawn while frames come");
        assertTrue(drawn.text.contains(session.run().getTracker()), "with the mission's step over the world");

        now.addAndGet(600_000_000L); // six tenths of a second, and not a frame
        drawn.clear();
        bag.paint(drawn);
        assertTrue(drawn.text.isEmpty(), "put away while the world stands still: " + drawn.text);

        game.runHeadless(1);
        drawn.clear();
        bag.paint(drawn);
        assertTrue(drawn.text.contains(SETTINGS.hud().itemsWord()), "and back with the next frame");
        assertTrue(drawn.text.contains(session.run().getTracker()), "the step with it");
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
        var blade = SETTINGS.loot().stream().filter(item -> item.id().equals("Gauntlet")).findFirst().orElseThrow();
        session.progress().getLoot().take(blade, 0, 30);
        var duke = uz.dukeengine.client3d.Duke3D.of(game, uz.dukeengine.client3d.Visuals.create());
        var bag = new BagScreen(SETTINGS, duke, () -> 0L);
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

    /**
     * A thing that does something is used, not only put down: a left click on it in the bag takes it in hand, and
     * the thing the next click is on is where he takes it — here the keep's gate.
     */
    @Test
    void aLeftClickOnTheKeyTakesItToTheThingClickedNext() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var key = SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
        session.progress().getLoot().take(key, 0, 30);
        var bag = bagOver(game);
        bag.show(session);
        var drawn = new Drawn();
        bag.paint(drawn);
        var picture = drawn.picture(key.icon());
        assertNotNull(picture, "the key is drawn in its slot: " + drawn.pictures);

        assertTrue(bag.take(new uz.dukeengine.client3d.CanvasInput.Button(picture.middleX(), picture.middleY(),
                uz.dukeengine.client3d.CanvasInput.Mouse.LEFT, true, false)), "a left click on it is the bag's");
        drawn.clear();
        bag.paint(drawn);
        assertFalse(drawn.text.contains(SETTINGS.lootDrops().dropHint()), "in hand to be used, not put down");

        // Where the client's aim was pressed: on the gate.
        var gate = find(game, "Gate");
        game.pressCommand("use:0", null, 0f, gate.getId().value());
        game.runHeadless(2);
        assertNotNull(find(game, "Rogue").findModule(uz.dukeengine.dungeon.loot.ItemErrand.class),
                "he is not on his way to the gate with it");
    }

    /**
     * What a click takes in hand: the left button only a thing that does something, to use — with no word beside it
     * about the floor — and the right button any thing, to put down; and each says what is in hand now, whatever was
     * in it before. A thing in hand is drawn a second time, beside the pointer, as well as in its own slot.
     */
    @Test
    void theLeftButtonTakesInHandOnlyAThingThatDoesSomethingAndTheRightOneAnyThing() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var gauntlet = SETTINGS.loot().stream().filter(item -> item.id().equals("Gauntlet")).findFirst().orElseThrow();
        var key = SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
        session.progress().getLoot().take(gauntlet, 0, 30);
        session.progress().getLoot().take(key, 0, 30);
        var bag = bagOver(game);
        bag.show(session);
        var drawn = new Drawn();
        bag.paint(drawn);
        var onGauntlet = drawn.picture(gauntlet.icon());
        var onKey = drawn.picture(key.icon());
        assertNotNull(onGauntlet, "the gauntlet is drawn in its slot: " + drawn.pictures);
        assertNotNull(onKey, "the key is drawn in its slot: " + drawn.pictures);

        assertTrue(bag.take(new uz.dukeengine.client3d.CanvasInput.Button(onGauntlet.middleX(), onGauntlet.middleY(),
                uz.dukeengine.client3d.CanvasInput.Mouse.LEFT, true, false)), "a left click on the bag is the bag's");
        drawn.clear();
        bag.paint(drawn);
        assertEquals(1, drawn.pictures.stream().filter(it -> it.path().equals(gauntlet.icon())).count(),
                "it does nothing, so it is not taken in hand: " + drawn.pictures);
        assertTrue(drawn.text.contains(SETTINGS.lootDrops().takeHint()), "only pointed at: " + drawn.text);

        bag.take(new uz.dukeengine.client3d.CanvasInput.Button(onKey.middleX(), onKey.middleY(),
                uz.dukeengine.client3d.CanvasInput.Mouse.LEFT, true, false));
        drawn.clear();
        bag.paint(drawn);
        assertEquals(2, drawn.pictures.stream().filter(it -> it.path().equals(key.icon())).count(),
                "the key is in hand, drawn beside the pointer as well as in its slot: " + drawn.pictures);
        assertFalse(drawn.text.contains(SETTINGS.lootDrops().dropHint()), "to be used, not put down");

        bag.take(new uz.dukeengine.client3d.CanvasInput.Button(onGauntlet.middleX(), onGauntlet.middleY(),
                uz.dukeengine.client3d.CanvasInput.Mouse.RIGHT, true, false));
        drawn.clear();
        bag.paint(drawn);
        assertEquals(2, drawn.pictures.stream().filter(it -> it.path().equals(gauntlet.icon())).count(),
                "a right click takes any thing in hand: " + drawn.pictures);
        assertTrue(drawn.text.contains(SETTINGS.lootDrops().dropHint()), "and this one is to be put down");
    }

    /**
     * The use aim pressed on a thing is an order of the game's own, as a drop is: the form the wire and the replay
     * carry, so every machine of a party hears it — with the thing it was pressed on and the slot it names. Pressed on
     * no thing, it is no order at all.
     */
    @Test
    void theUseAimPressedOnAThingIsAnOrderEveryMachineHears() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        var heard = new java.util.ArrayList<uz.dukeengine.rts.message.GameMessage.GameOrder>();
        game.onOrder(heard::add); // before the world starts, as the game's own is
        bagOver(game).show(session);
        game.runHeadless(2);
        var gate = find(game, "Gate");
        var expected = uz.dukeengine.dungeon.party.PartyOrders.of(
                new uz.dukeengine.dungeon.loot.UseItem(game.getLocalPlayerIndex(), 2, gate.getId()));

        game.pressCommand("use:2", null, 0f, -1);
        game.runHeadless(2);
        assertTrue(heard.stream().noneMatch(order -> order.word().equals(expected.word())),
                "pressed on no thing: " + heard);

        game.pressCommand("use:2", null, 0f, gate.getId().value());
        game.runHeadless(2);
        assertEquals(List.of(expected), heard.stream().filter(order -> order.word().equals(expected.word())).toList());
    }

    /** A key gives nothing, so the pointer on it says what it is and how to take it, with no empty row for a figure. */
    @Test
    void aKeyInTheBagIsSaidWithoutAnEmptyRowForAFigure() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var key = SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
        session.progress().getLoot().take(key, 0, 30);
        var bag = bagOver(game);
        bag.show(session);
        var drawn = new Drawn();

        bag.paint(drawn);
        var picture = drawn.picture(key.icon());
        assertNotNull(picture, "the key is drawn in its slot: " + drawn.pictures);

        bag.take(new uz.dukeengine.client3d.CanvasInput.Pointer(picture.middleX(), picture.middleY()));
        drawn.clear();
        bag.paint(drawn);
        assertTrue(drawn.text.contains(key.name()) && drawn.text.contains(SETTINGS.lootDrops().takeHint()),
                "the pointer on it says what it is and how to take it: " + drawn.text);
        assertTrue(drawn.text.stream().noneMatch(String::isEmpty), "and leaves no row empty: " + drawn.text);
    }

    /**
     * A thing that does something says how it is used, beside how it is taken, on its card in the bag — the key's left
     * click would be known to nobody otherwise. A thing that does nothing says only how it is taken, and so does the
     * key lying on the floor, where a left click does nothing to it.
     */
    @Test
    void aThingThatDoesSomethingSaysHowToUseItInTheBagAndOnlyThere() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        var bag = bagOver(game);
        bag.show(session);
        game.runHeadless(2);
        var gauntlet = SETTINGS.loot().stream().filter(item -> item.id().equals("Gauntlet")).findFirst()
                .orElseThrow();
        var key = SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
        session.progress().getLoot().take(gauntlet, 0, 30);
        session.progress().getLoot().take(key, 0, 30);
        var take = SETTINGS.lootDrops().takeHint();
        var use = SETTINGS.lootDrops().useHint();
        assertNotEquals(uz.dukeengine.dungeon.world.LootDrops.DEFAULTS.useHint(), use,
                "the file says it, not the default");
        var drawn = new Drawn();
        bag.paint(drawn);
        var onGauntlet = drawn.picture(gauntlet.icon());
        var onKey = drawn.picture(key.icon());
        assertNotNull(onGauntlet, "the gauntlet is drawn in its slot: " + drawn.pictures);
        assertNotNull(onKey, "the key is drawn in its slot: " + drawn.pictures);

        bag.take(new uz.dukeengine.client3d.CanvasInput.Pointer(onGauntlet.middleX(), onGauntlet.middleY()));
        drawn.clear();
        bag.paint(drawn);
        assertTrue(drawn.text.contains(gauntlet.name()) && drawn.text.contains(take) && !drawn.text.contains(use),
                "a thing that does nothing is only taken: " + drawn.text);

        bag.take(new uz.dukeengine.client3d.CanvasInput.Pointer(onKey.middleX(), onKey.middleY()));
        drawn.clear();
        bag.paint(drawn);
        assertTrue(drawn.text.contains(key.name()) && drawn.text.contains(take) && drawn.text.contains(use),
                "the key is taken, and used: " + drawn.text);
        assertTrue(drawn.text.indexOf(take) < drawn.text.indexOf(use), "how to take it first: " + drawn.text);

        // Put down, the key lies on the floor, and the pointer on it there says only how to take it.
        var hero = find(game, "Rogue");
        game.pressCommand("drop:1", hero.getPosition(), 0f, -1);
        game.runHeadless(4);
        var lying = find(game, "Key");
        assertNotNull(lying, "the key is not on the floor");
        bag.take(new uz.dukeengine.client3d.CanvasInput.Pointer(40, 40));
        game.setPointedAt(lying.getId().value());
        game.runHeadless(2);
        drawn.clear();
        bag.paint(drawn);
        assertTrue(drawn.text.contains(key.name()) && drawn.text.contains(take) && !drawn.text.contains(use),
                "lying on the floor it is only taken: " + drawn.text);
    }

    /** The pointer on a thing lying on the floor, with his hero in hand, says what it gives and how to take it. */
    @Test
    void aThingOnTheFloorSaysWhatItGives() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        var bag = bagOver(game);
        bag.show(session); // before it starts, as the game tells it
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var blade = SETTINGS.loot().stream().filter(item -> item.id().equals("Gauntlet")).findFirst().orElseThrow();
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

    /**
     * ★ And says it whatever is selected. A left click on the chest selects the chest, and one on the floor selects
     * nothing, and either way his hero is no longer selected — which is exactly when a player wants to know what
     * lies there before he sends anyone for it.
     */
    @Test
    void aThingOnTheFloorSaysWhatItGivesWhateverIsSelected() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        var bag = bagOver(game);
        bag.show(session);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var blade = SETTINGS.loot().stream().filter(item -> item.id().equals("Gauntlet")).findFirst().orElseThrow();
        session.progress().getLoot().take(blade, 0, 30);
        game.pressCommand("drop:0", hero.getPosition(), 0f, -1);
        game.runHeadless(4);
        var chest = find(game, "Chest");
        assertNotNull(chest);

        for (var selected : List.of(List.<Integer>of(), List.of(chest.getId().value()))) {
            game.setSelection(selected);
            game.setPointedAt(chest.getId().value());
            game.runHeadless(2);
            var drawn = new Drawn();
            bag.paint(drawn);
            assertTrue(drawn.text.contains(blade.name()) && drawn.text.contains(BagScreen.bonusOf(blade, SETTINGS)),
                    "with " + selected + " selected: " + drawn.text);
        }

        game.setPointedAt(-1);
        game.runHeadless(1);
        var drawn = new Drawn();
        bag.paint(drawn);
        assertFalse(drawn.text.contains(blade.name()), "and nothing once the pointer is off it: " + drawn.text);
    }

    /**
     * A click on the keep's gate is an order of the game's own, as a click on a chest is — so the ring round it
     * flashes yellow, and the pointer over it is the cursor of the order's name — and the order sends the hero up to
     * it. A click on a monster is no such order.
     */
    @Test
    void aClickOnTheGateSendsHimUpToIt() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        var bag = bagOver(game);
        bag.show(session);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var gate = find(game, "Gate");
        assertNotNull(gate, "the floor's keep has no gate");
        var monster = game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() != hero.getPlayerIndex()
                        && object.findModule(uz.dukeengine.rts.module.WeaponUpdate.class) != null)
                .findFirst().orElseThrow();

        game.setSelection(List.of(hero.getId().value()));
        game.setPointedAt(monster.getId().value());
        game.runHeadless(2);
        assertNull(game.getSnapshot().contextOrder(), "a click on a monster is no walk up to a gate");

        game.setPointedAt(gate.getId().value());
        game.runHeadless(2);
        assertEquals(uz.dukeengine.dungeon.party.PartyOrders.TO_THE_GATE, game.getSnapshot().contextOrder());
        assertTrue(SETTINGS.cursors().stream()
                .anyMatch(pointer -> pointer.name().equals(uz.dukeengine.dungeon.party.PartyOrders.TO_THE_GATE)),
                "no cursor has the order's name, so the pointer over the gate stays the plain one");

        // What the client sends for that click.
        game.postCommand(new uz.dukeengine.rts.message.GameMessage.GameOrder(hero.getPlayerIndex(),
                game.getSnapshot().contextOrder(), List.of(hero.getId()), gate.getPosition(), gate.getId(), 0));
        game.runHeadless(2);
        assertNotNull(hero.findModule(uz.dukeengine.dungeon.loot.ItemErrand.class), "he is not on his way to it");
    }

    /** A click on a chest is answered as an attack is — the ring round it, blinking — but yellow, not the arrowheads. */
    @Test
    void aPickupIsAnsweredWithTheRingInYellow() {
        var mark = SETTINGS.orderMark();
        assertTrue(mark.ringsContextOrders(), "the ring, not a walk's arrowheads");
        assertEquals(0xFFD23C, mark.contextColour());
        assertTrue(mark.contextColour() != mark.attackColour(), "and not a fight's colour");
    }

    /**
     * The same, down the client's own road: the window's raw pointer turned into the game's by the client's own
     * listener, and the tooltip drawn on the client's own canvas, words and all.
     */
    @Test
    void thePointerOnAThingInTheBagSaysSoThroughTheClientsOwnInputAndCanvas() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        var bag = bagOver(game);
        bag.show(session);
        game.runHeadless(2);
        var blade = SETTINGS.loot().stream().filter(item -> item.id().equals("Gauntlet")).findFirst().orElseThrow();
        session.progress().getLoot().take(blade, 0, 30);
        int height = 900;
        var drawn = new Drawn(); // the same size of window: where the blade's slot is
        bag.paint(drawn);
        var slot = drawn.picture(blade.icon());

        var before = uz.dukeengine.client3d.RealCanvas.frame(1600, height);
        bag.paint(before);
        // As the window says it: counted from the bottom, and moved.
        uz.dukeengine.client3d.RealCanvas.inputs(bag, height).onMouseMotionEvent(
                new com.jme3.input.event.MouseMotionEvent(slot.middleX(), height - slot.middleY(), 3, 3, 0, 0));
        var after = uz.dukeengine.client3d.RealCanvas.frame(1600, height);
        bag.paint(after);

        int more = uz.dukeengine.client3d.RealCanvas.triangles(after)
                - uz.dukeengine.client3d.RealCanvas.triangles(before);
        assertTrue(more > 40, "a card with its words on it, drawn over the slot: " + more + " triangles more");
    }

    /** Painted over the front end too, before anybody is seated: it draws nothing there, and does not throw. */
    @Test
    void beforeTheMatchStartsNothingIsDrawn() {
        var session = Dungeon.newSession(21L);
        var bag = bagOver(session.game());
        bag.show(session);
        var drawn = new Drawn();

        bag.paint(drawn);

        assertTrue(drawn.pictures.isEmpty() && drawn.text.isEmpty(), "nothing over the menu: " + drawn.text);
    }

    /** A joined thing says which level it is, what it gives, and what it brings beside that. */
    @Test
    void aJoinedThingSaysItsLevelAndItsExtra() {
        var tome = SETTINGS.loot().stream().filter(item -> item.id().equals("Tome")).findFirst().orElseThrow();
        var boots = SETTINGS.loot().stream().filter(item -> item.id().equals("Boots")).findFirst().orElseThrow();
        var second = tome.joined(3);
        var third = boots.joined(3).joined(3);

        assertEquals(tome.name() + " II", uz.dukeengine.dungeon.loot.LootBag.nameOf(second));
        assertEquals("+9 Aql", BagScreen.bonusOf(second, SETTINGS));
        assertEquals("+2 " + SETTINGS.lootDrops().manaRegenWord(), BagScreen.extraOf(second, SETTINGS));
        assertEquals("+27 Epchillik", BagScreen.bonusOf(third, SETTINGS));
        assertEquals("+60% " + SETTINGS.lootDrops().attackSpeedWord(), BagScreen.extraOf(third, SETTINGS));
        assertEquals("", BagScreen.extraOf(tome, SETTINGS), "nothing beside its figure at the first level");
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
