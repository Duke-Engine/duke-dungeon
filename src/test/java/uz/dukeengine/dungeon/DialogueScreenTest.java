package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/** The box says a conversation a line at a time, pauses the world alone, and is skipped; and people wear marks. */
class DialogueScreenTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** What was drawn: every line of text, and every picture's path. */
    private static final class Drawn implements Canvas {

        final List<String> text = new ArrayList<>();
        final List<String> pictures = new ArrayList<>();
        final List<float[]> rects = new ArrayList<>();

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
            rects.add(new float[] {x, y, w, h, argb});
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
            this.text.add(text);
        }

        @Override
        public Measure measure(Font font, String text) {
            return new Measure(text.length() * font.pixelHeight() / 2, font.pixelHeight());
        }

        @Override
        public Point screenOf(float x, float y, float height) {
            return new Point(800f, 400f);
        }
    }

    private static GameObject named(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    private static Drawn painted(DialogueScreen screen) {
        var canvas = new Drawn();
        screen.paint(canvas);
        return canvas;
    }

    @Test
    void aConversationIsSaidALineAtATimeWithTheWorldPausedAndCanBeSkipped() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        var screen = new DialogueScreen(null, null, SETTINGS);
        DialogueScreen.show(session);
        assertFalse(painted(screen).text.stream().anyMatch(t -> t.contains("Nihoyat")), "nobody has talked yet");

        var commander = named(game, "Commander");
        var hero = named(game, SETTINGS.run().defaultHero());
        hero.setPosition(new Coord3D(commander.getPosition().x() + 12f, commander.getPosition().y(),
                commander.getPosition().z()));
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() + 1);

        var first = painted(screen);
        assertTrue(first.text.contains("Qo'mondon Laylo"), first.text.toString());
        assertTrue(first.text.stream().anyMatch(t -> t.startsWith("Nihoyat")), first.text.toString());
        assertTrue(first.text.contains(SETTINGS.story().dialogue().continueHint()));
        assertTrue(game.getLogic().isGamePaused(), "alone, the world waits for her");

        assertTrue(screen.take(new CanvasInput.Key(DialogueScreen.SPACE, true, false)));
        var second = painted(screen);
        assertTrue(second.text.contains(Main.displayNameOf(session, SETTINGS.run().defaultHero())),
                "the hero answers, by his own name: " + second.text);
        assertTrue(screen.take(new CanvasInput.Button(10, 10, CanvasInput.Mouse.RIGHT, true, false)),
                "no order goes through the box");

        assertTrue(screen.take(new CanvasInput.Key(DialogueScreen.ESCAPE, true, false)));
        var after = painted(screen);
        assertFalse(after.text.contains(SETTINGS.story().dialogue().continueHint()), "skipped");
        assertFalse(game.getLogic().isGamePaused(), "and the world goes on");
        assertFalse(screen.take(new CanvasInput.Key(DialogueScreen.SPACE, true, false)),
                "with nothing said, a key is the game's again");
    }

    @Test
    void aPersonWithAQuestToGiveWearsTheMark() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        var canvas = new Drawn();
        MissionScreen.marks(canvas, session, session.game().getSnapshot(), SETTINGS);
        assertEquals(List.of(SETTINGS.story().dialogue().giveMark()), canvas.pictures);
    }

    @Test
    void aCutsceneLetterboxesTheScreenTakesHisHandAndEscSkipsIt() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        var screen = new DialogueScreen(null, (CanvasInput) event -> false, SETTINGS);
        DialogueScreen.show(session);
        var door = Stages.load("talk", SETTINGS).story().zones().stream().filter(z -> z.name().equals("HallDoor"))
                .findFirst().orElseThrow();
        var hero = named(game, SETTINGS.run().defaultHero());
        hero.setPosition(new Coord3D(door.middleX(10f), door.middleY(10f), hero.getPosition().z()));
        game.runHeadless(SETTINGS.story().dialogue().checkFrames() + 1);
        assertTrue(session.run().inCutscene());

        var canvas = painted(screen);
        assertTrue(canvas.rects.stream().anyMatch(r -> r[1] == 0f && r[2] == 1600f && (int) r[4] == 0xFF000000),
                "a black bar across the top");
        assertTrue(screen.take(new CanvasInput.Button(10, 10, CanvasInput.Mouse.RIGHT, true, false)),
                "no order goes to the world");
        assertTrue(screen.take(new CanvasInput.Key(30, true, false)), "nor a key");

        assertTrue(screen.take(new CanvasInput.Key(DialogueScreen.ESCAPE, true, false)));
        game.runHeadless(2);
        assertFalse(session.run().inCutscene(), "skipped");
        assertFalse(screen.take(new CanvasInput.Key(30, true, false)), "and his hand is his again");
    }

    @Test
    void theTradeWindowListsHisStockAndAClickBuys() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        var screen = new DialogueScreen(null, (CanvasInput) event -> false, SETTINGS);
        DialogueScreen.show(session);
        var hero = named(game, SETTINGS.run().defaultHero());
        for (var skeleton : game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Skeleton")).toList()) {
            skeleton.getBody().damage(1_000_000f, uz.dukeengine.core.module.DamageType.NORMAL,
                    new uz.dukeengine.core.module.Death(uz.dukeengine.core.module.DeathType.NORMAL, hero.getId(),
                            hero.getPlayerIndex()));
        }
        game.runHeadless(2);
        var merchant = named(game, "Merchant");
        hero.setPosition(new Coord3D(merchant.getPosition().x() + 12f, merchant.getPosition().y(),
                merchant.getPosition().z()));
        game.postCommand(uz.dukeengine.dungeon.party.PartyOrders.of(
                new uz.dukeengine.dungeon.run.TalkTo(hero.getPlayerIndex(), merchant.getId())));
        game.runHeadless(2);
        int gold = session.run().goldOf(hero.getPlayerIndex());
        assertTrue(screen.take(new CanvasInput.Key(DialogueScreen.ESCAPE, true, false)), "the greeting first");

        var canvas = painted(screen);
        assertTrue(canvas.text.contains("Savdogar Botir"), canvas.text.toString());
        assertTrue(canvas.text.stream().anyMatch(t -> t.startsWith("Kuch qo'lqopi — 80")), canvas.text.toString());
        assertTrue(canvas.text.contains(SETTINGS.story().trade().goldWord() + ": " + gold));

        var ware = screen.trade().rows().stream().filter(row -> row.ware() && row.index() == 0).findFirst()
                .orElseThrow();
        assertTrue(screen.take(new CanvasInput.Button(Math.round(ware.x() + 5), Math.round(ware.y() + 5),
                CanvasInput.Mouse.LEFT, true, false)));
        game.runHeadless(2);
        assertEquals(gold - 80, session.run().goldOf(hero.getPlayerIndex()), "bought, through the world");

        assertTrue(screen.take(new CanvasInput.Key(DialogueScreen.ESCAPE, true, false)));
        assertTrue(painted(screen).text.stream().noneMatch(t -> t.equals("Savdogar Botir")), "closed");
    }
}
