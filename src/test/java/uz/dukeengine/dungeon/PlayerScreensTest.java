package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.client3d.Picture;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.run.Sheet;
import uz.dukeengine.dungeon.run.Tracker;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.dungeon.story.MapStory;
import uz.dukeengine.rpg.story.QuestLog;
import uz.dukeengine.game.DukeGame;

/**
 * The player's screens: the sheet says the hero in the data's words, the log lists the quests as the run keeps them,
 * the map stands where its view says, and one screen is open at a time on its key.
 */
class PlayerScreensTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** What was drawn: every line of text, every picture, every portrait and every triangle. */
    static final class Drawn implements Canvas {

        final List<String> text = new ArrayList<>();
        final List<String> pictures = new ArrayList<>();
        final List<Integer> portraits = new ArrayList<>();
        int made;
        int triangles;

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
        public void drawPicture(Picture picture, float x0, float y0, float x1, float y1, int argb, Blend blend) {
            made++;
        }

        @Override
        public void drawPortrait(int unitId, float x0, float y0, float x1, float y1) {
            portraits.add(unitId);
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
        }

        @Override
        public void openRect(float x, float y, float w, float h, float lineWidth, int argb) {
        }

        @Override
        public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
            triangles++;
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
            this.text.add(text);
        }

        @Override
        public Measure measure(Font font, String text) {
            return new Measure(text.length() * font.pixelHeight() / 2, font.pixelHeight());
        }
    }

    private static GameObject named(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    private static Dungeon.Session talk() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        session.game().runHeadless(2);
        return session;
    }

    /** The hero walked up to the commander, who gives him her two quests. */
    private static void greeted(Dungeon.Session session) {
        var game = session.game();
        var commander = named(game, "Commander");
        var hero = named(game, SETTINGS.run().defaultHero());
        hero.setPosition(new Coord3D(commander.getPosition().x() + 12f, commander.getPosition().y(),
                commander.getPosition().z()));
        session.run().talkTo(game, hero.getPlayerIndex(), commander.getId()); // asked: in the box
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() + 1);
    }

    // ---- the sheet ----

    @Test
    void theSheetSaysTheHeroAsTheRunCountsHimInTheDataWords() {
        var session = talk();
        var sheet = session.run().getSheet();
        assertNotNull(sheet, "a hero standing has a sheet");
        assertEquals(Main.displayNameOf(session, SETTINGS.run().defaultHero()), sheet.name());
        assertEquals(1, sheet.level());
        assertEquals(SETTINGS.attributeArt().size(), sheet.attributes().size(), "every attribute the file lists");
        assertEquals(1, sheet.attributes().stream().filter(Sheet.Attribute::primary).count(), "one primary");
        assertEquals(SETTINGS.lootDrops().wornSlots(), sheet.worn().stream().map(Sheet.Worn::slot).toList(),
                "a worn slot each, in the bag's order");
        assertTrue(sheet.damage() > 0 && sheet.blowsPerSecond() > 0, "he hits, and how often");
        assertEquals(sheet.maxHealth(), sheet.health(), "whole at the start");
        assertEquals(-1, sheet.souls(), "no souls without the medallion");

        var labels = CharacterSheet.figures(sheet, SETTINGS).stream().map(CharacterSheet.Row::label).toList();
        assertTrue(labels.containsAll(List.of(SETTINGS.hud().attackWord(), SETTINGS.lootDrops().attackSpeedWord(),
                SETTINGS.hud().armourWord(), SETTINGS.hud().healthWord(), SETTINGS.hud().speedWord())), "" + labels);

        var drawn = new Drawn();
        CharacterSheet.paint(drawn, sheet, SETTINGS);
        assertTrue(drawn.text.containsAll(List.of(SETTINGS.screens().sheetTitle(), sheet.name(),
                SETTINGS.screens().attributesWord(), SETTINGS.screens().figuresWord(),
                SETTINGS.screens().wornWord(), SETTINGS.story().trade().goldWord() + ": " + sheet.gold())),
                "" + drawn.text);
        assertTrue(drawn.text.contains(SETTINGS.attributeArt().get(0).word())
                || drawn.text.contains(SETTINGS.attributeArt().get(0).word() + " ★"), "" + drawn.text);
        assertEquals(List.of(sheet.id()), drawn.portraits, "his face, alive");
    }

    @Test
    void anAttributeSaysWhatAllOfItGives() {
        // Strength: 12 health a point; and as a primary, the blow each point adds.
        var strength = new Sheet.Attribute(0, 10, 10, false);
        assertEquals(List.of("+120 " + SETTINGS.hud().healthWord()), CharacterSheet.gives(strength, SETTINGS));
        var primary = CharacterSheet.gives(new Sheet.Attribute(0, 10, 10, true), SETTINGS);
        assertEquals(2, primary.size());
        assertTrue(primary.get(1).endsWith(SETTINGS.hud().attackWord()), "" + primary);
    }

    // ---- the quest log ----

    @Test
    void theLogListsTheQuestsGoingMainFirstWithTheirCountsAndDescriptions() {
        var session = talk();
        assertTrue(QuestLogScreen.entries(session.run().questsNow(), SETTINGS.story()).isEmpty(), "nothing yet");
        greeted(session);
        var entries = QuestLogScreen.entries(session.run().questsNow(), SETTINGS.story());
        assertEquals(List.of("WallSkeletons", "LostLocket"), entries.stream().map(QuestLogScreen.Entry::quest).toList());
        var first = entries.getFirst();
        assertEquals(uz.dukeengine.rpg.story.Quest.Kind.MAIN, first.kind());
        assertEquals("Devordagi skeletlar", first.title());
        assertEquals(List.of(new QuestLogScreen.Line("Skeletlarni qiring 0/5", false)), first.steps());
        assertEquals(SETTINGS.story().quest("WallSkeletons").description(), first.description());
        assertFalse(first.description().isBlank(), "the data gives it one");

        var log = new QuestLogScreen(SETTINGS);
        var drawn = new Drawn();
        log.paint(drawn, session);
        assertTrue(drawn.text.containsAll(List.of(SETTINGS.screens().questsTitle(), SETTINGS.screens().activeWord(),
                "◆ Devordagi skeletlar", "◇ Yo'qolgan tumor", "• Skeletlarni qiring 0/5")), "" + drawn.text);
    }

    @Test
    void stepsDoneAreTickedFinishedQuestsComeLastAndTargetsAreWhereTheMapSays() {
        var state = new QuestLog.State(List.of(new QuestLog.Active("WallSkeletons", 1, 0, 0)), List.of("LostLocket"),
                List.of());
        var entries = QuestLogScreen.entries(state, SETTINGS.story());
        assertEquals(List.of(new QuestLogScreen.Line("Skeletlarni qiring 5/5", true),
                new QuestLogScreen.Line("Qo'mondonga qayting", false)), entries.getFirst().steps());
        assertTrue(entries.get(1).finished(), "the finished last");
        assertEquals("WallSkeletons", QuestLogScreen.picked(entries, "").quest(), "the first going is picked");

        var story = new MapStory(List.of(StaticMap.Placed.of("Commander 4 7")), List.of(), List.of(), List.of());
        assertTrue(java.util.Arrays.equals(new float[] {45f, 75f},
                QuestLogScreen.whereIs("WallSkeletons", state, SETTINGS.story(), story)), "a TALK is the person");
        var killing = new QuestLog.State(List.of(new QuestLog.Active("WallSkeletons", 0, 2, 0)), List.of(), List.of());
        assertNull(QuestLogScreen.whereIs("WallSkeletons", killing, SETTINGS.story(), story), "a KILL is nowhere");
        assertNull(QuestLogScreen.whereIs("LostLocket", state, SETTINGS.story(), story), "a finished one neither");
    }

    // ---- the map ----

    @Test
    void theMapViewGoesFromTheWorldToTheScreenAndBack() {
        var view = new WorldMapScreen.View();
        view.frame(new Canvas.Box(100f, 50f, 800f, 600f), 5120f, 5120f);
        var middle = view.toScreen(2560f, 2560f);
        assertEquals(500f, middle.x(), 1e-3f, "the world's middle in the box's");
        assertEquals(350f, middle.y(), 1e-3f);
        assertEquals(600f / 5120f, view.scale(), 1e-6f, "fitted whole");
        for (var point : new float[][] {{0f, 0f}, {1234f, 4321f}, {5120f, 77f}}) {
            var back = view.toWorld(view.toScreen(point[0], point[1]).x(), view.toScreen(point[0], point[1]).y());
            assertEquals(point[0], back.x(), 0.01f);
            assertEquals(point[1], back.y(), 0.01f);
        }

        var under = view.toWorld(300f, 200f);
        view.zoom(3, 300f, 200f);
        var still = view.toScreen(under.x(), under.y());
        assertEquals(300f, still.x(), 0.01f, "the wheel zooms about the pointer");
        assertEquals(200f, still.y(), 0.01f);
        assertTrue(view.scale() > 600f / 5120f);

        var before = view.toScreen(1000f, 1000f);
        view.pan(40f, -25f);
        var after = view.toScreen(1000f, 1000f);
        assertEquals(before.x() + 40f, after.x(), 0.01f, "a drag carries the map with it");
        assertEquals(before.y() - 25f, after.y(), 0.01f);

        view.zoom(-50, 0f, 0f);
        assertEquals(600f / 5120f, view.scale(), 1e-6f, "never further out than the whole");
        view.centreOn(4000f, 1000f, 4f);
        var on = view.toScreen(4000f, 1000f);
        assertEquals(500f, on.x(), 0.01f, "the log's target in the middle");
        assertEquals(350f, on.y(), 0.01f);

        assertEquals(5120f, WorldMapScreen.covered(1024, 1024, 5f, 512)[0], 1e-3f, "two cells a texel");
        assertEquals(300f, WorldMapScreen.covered(30, 32, 10f, 512)[0], 1e-3f, "a texel a cell, a small map whole");
        assertEquals(320f, WorldMapScreen.covered(30, 32, 10f, 512)[1], 1e-3f);
    }

    @Test
    void theMapNamesOnlyThePlacesTheDataNamesBrightWhereHeHasBeen() {
        var names = SETTINGS.screens().placeNames();
        assertEquals("Qorong'i o'rmon", names.get("Qorong'i_o'rmon"), "an Uzbek label, apostrophes and all");
        var story = new MapStory(List.of(), List.of(StaticMap.Zone.of("Poytaxt 101 452 24 18"),
                StaticMap.Zone.of("Corridor 14 9 8 2"), StaticMap.Zone.of("Yashil_dala 205 317 16")), List.of(),
                List.of());
        var places = WorldMapScreen.places(story, names, List.of("Poytaxt"));
        assertEquals(List.of(new WorldMapScreen.Place("Poytaxt", 1130f, 4610f, true),
                new WorldMapScreen.Place("Yashil dala", 2055f, 3175f, false)), places);
        assertTrue(WorldMapScreen.places(story, Map.of(), List.of()).isEmpty(), "no names, no places");
    }

    @Test
    void theMapIsDrawnWithTheHeroOnItAndTheQuestTargets() {
        var session = talk();
        greeted(session);
        session.game().runHeadless(1);
        var map = new WorldMapScreen(SETTINGS);
        map.picture(new Picture(8, 8));
        var drawn = new Drawn();
        map.paint(drawn, session);
        assertTrue(drawn.text.contains(SETTINGS.screens().mapTitle()), "" + drawn.text);
        assertEquals(1, drawn.made, "the world's picture under it");
        assertEquals(2, drawn.triangles, "the hero's arrow, over its shadow");
        assertEquals(List.of(SETTINGS.story().dialogue().giveMark()), drawn.pictures,
                "one mark: the locket lying; the killing has no place");
    }

    // ---- one at a time, on its key ----

    @Test
    void eachKeyOpensItsScreenShutsItAndOpeningOneShutsAnother() {
        assertEquals(46, uz.dukeengine.client3d.Hotkeys.codeOf('C'));
        assertEquals(38, uz.dukeengine.client3d.Hotkeys.codeOf('l'));
        assertEquals(50, uz.dukeengine.client3d.Hotkeys.codeOf('M'));
        assertEquals(36, uz.dukeengine.client3d.Hotkeys.codeOf('J'));
        var session = talk();
        var screens = new PlayerScreens(new DialogueScreen(null, null, SETTINGS), SETTINGS);
        DialogueScreen.show(session);
        var game = session.game();

        screens.toggle(PlayerScreens.Open.SHEET);
        assertEquals(PlayerScreens.Open.SHEET, screens.open());
        screens.paint(new Drawn());
        assertTrue(game.getLogic().isGamePaused(), "alone, the world waits");
        assertTrue(screens.take(key('M')));
        assertEquals(PlayerScreens.Open.MAP, screens.open(), "the map's key shuts the sheet");
        assertTrue(screens.take(key('L')));
        assertEquals(PlayerScreens.Open.QUESTS, screens.open());
        assertTrue(screens.take(key('L')));
        assertEquals(PlayerScreens.Open.NONE, screens.open(), "its own key shuts it");
        screens.paint(new Drawn());
        assertFalse(game.getLogic().isGamePaused(), "and the world goes on");

        screens.toggle(PlayerScreens.Open.MAP);
        assertTrue(screens.take(new CanvasInput.Button(10, 10, CanvasInput.Mouse.RIGHT, true, false)),
                "no order to the world through it");
        assertTrue(screens.take(new CanvasInput.Key(CanvasInput.ESCAPE, true, false)));
        assertEquals(PlayerScreens.Open.NONE, screens.open(), "Esc shuts it");
        assertFalse(screens.take(key('M')), "shut, its key is the hotkeys' again");

        var shut = new Drawn();
        screens.paint(shut);
        assertTrue(shut.text.containsAll(List.of("C", "L", "M", "J", SETTINGS.screens().sheetTitle(),
                SETTINGS.screens().trackerWord())), "the key hints over the panel: " + shut.text);
    }

    @Test
    void aConversationShutsTheScreenAndTheLogsButtonOpensTheMapOnTheTarget() {
        var session = talk();
        var screens = new PlayerScreens(new DialogueScreen(null, null, SETTINGS), SETTINGS);
        DialogueScreen.show(session);
        screens.toggle(PlayerScreens.Open.QUESTS);
        greeted(session);
        screens.paint(new Drawn());
        assertEquals(PlayerScreens.Open.NONE, screens.open(), "she is talking: the log is shut");

        assertTrue(screens.take(new CanvasInput.Key(CanvasInput.ESCAPE, true, false))); // her greeting skipped
        screens.paint(new Drawn());
        screens.toggle(PlayerScreens.Open.QUESTS);
        screens.quests().pick("LostLocket");
        screens.paint(new Drawn());
        var button = screens.quests().hits().stream().filter(QuestLogScreen.Hit::map).findFirst().orElseThrow();
        screens.take(new CanvasInput.Button((int) button.x() + 2, (int) button.y() + 2, CanvasInput.Mouse.LEFT,
                true, false));
        assertEquals(PlayerScreens.Open.MAP, screens.open(), "the button opens the map");
        var target = QuestLogScreen.whereIs("LostLocket", session.run().questsNow(), SETTINGS.story(),
                session.run().storyNow());
        var on = screens.map().view().toScreen(target[0], target[1]);
        var box = WorldMapScreen.boxOf(new Drawn());
        assertEquals(box.x() + box.width() / 2f, on.x(), 0.5f, "on the locket");
        assertEquals(box.y() + box.height() / 2f, on.y(), 0.5f);
    }

    @Test
    void theKeysAreSaidInTheGamesWordsOnTheSettingsList() {
        var keys = Main.controls(uz.dukeengine.dungeon.content.DungeonSettings.load()); // the orders' words too
        Main.screensOn(keys, new PlayerScreens(new DialogueScreen(null, null, SETTINGS), SETTINGS), SETTINGS);
        assertEquals(SETTINGS.screens().sheetTitle(), keys.wordOf('C'));
        assertEquals(SETTINGS.screens().questsTitle(), keys.wordOf('L'));
        assertEquals(SETTINGS.screens().mapTitle(), keys.wordOf('M'));
        assertEquals(SETTINGS.screens().trackerWord(), keys.wordOf('J'));
        assertEquals(SETTINGS.hud().cmdAttackWord(), keys.wordOf('A'), "an order as its button says it");
        var skill = SETTINGS.skillsFor(SETTINGS.run().defaultHero()).getFirst();
        assertEquals(skill.name(), keys.wordOf(skill.key()), "a skill by its name");
        var said = SETTINGS.mainMenu().said();
        assertEquals("Tugmalar", said.get("Keys"), "the list's heading in Uzbek");
        assertTrue(said.keySet().containsAll(List.of("Pause", "Select all", "Pan up")), "the client's own controls");
    }

    @Test
    void thePlacesFoundAreSaved() {
        var bare = new uz.dukeengine.dungeon.run.SaveGame(1L, null, 1, "Rogue", 0, List.of(), List.of());
        assertEquals(List.of(), uz.dukeengine.dungeon.run.SaveGame.read(bare.text(), "test").places(),
                "an older save found none");
        var found = bare.withPlaces(List.of("Poytaxt", "Qorong'i_o'rmon"));
        assertEquals(found.places(), uz.dukeengine.dungeon.run.SaveGame.read(found.text(), "test").places());
    }

    @Test
    void theHunarScreenListsWhatHeKnowsLightsWhatHeCanMakeAndAClickCraftsIt() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var items = SETTINGS.items();
        var bag = session.progress().getLoot();
        bag.take(items.potion(items.potion("otin"), 1, 1), 0, 0);
        bag.take(items.potion(items.potion("ruda"), 2, 2), 0, 0);
        assertEquals(PlayerScreens.Open.CRAFT, PlayerScreens.forKey(key('K').code(), SETTINGS), "K opens it");

        var screen = new CraftScreen(SETTINGS);
        var drawn = new Drawn();
        screen.paint(drawn, session);
        assertTrue(drawn.text.contains(SETTINGS.crafting().title()), "" + drawn.text);
        assertTrue(drawn.text.contains("Xanjar") && drawn.text.contains("Choriq"), "the basics he knows");
        assertFalse(drawn.text.contains("Keng qilich"), "not what he has not learnt");
        var ready = screen.rows().stream().filter(CraftScreen.Row::ready).toList();
        assertEquals(1, ready.size(), "only the dagger: one wood, two ore");
        var row = ready.getFirst();
        assertTrue(screen.take(new CanvasInput.Button((int) row.x() + 5, (int) row.y() + 5, CanvasInput.Mouse.LEFT,
                true, false), session), "a click on it crafts and shuts the screen");
        for (int waited = 0; waited < 5 && !session.run().working(game.getLocalPlayerIndex()); waited++) {
            game.runHeadless(1);
        }
        assertTrue(session.run().working(game.getLocalPlayerIndex()), "he is at it");
    }

    private static CanvasInput.Key key(char letter) {
        return new CanvasInput.Key(uz.dukeengine.client3d.Hotkeys.codeOf(letter), true, false);
    }
}
