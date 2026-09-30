package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** What the game says over the world: the mission's step at the top, and what a hero says over his head. */
class MissionScreenTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Every line drawn and where, on a window 1600 by 900 where every thing's bar stands at one place. */
    private static final class Drawn implements Canvas {

        record Line(String text, float x, float y) {
        }

        final List<Line> lines = new ArrayList<>();
        final Box bar;

        Drawn(Box bar) {
            this.bar = bar;
        }

        Line line(String text) {
            return lines.stream().filter(line -> line.text().equals(text)).findFirst().orElse(null);
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
        public void drawText(Font font, String text, float x, float y, int argb) {
            lines.add(new Line(text, x, y));
        }

        @Override
        public Measure measure(Font font, String text) {
            return new Measure(text.length() * 8, font.pixelHeight() + 4);
        }

        @Override
        public Box barOf(int id) {
            return bar;
        }
    }

    @Test
    void theTrackerSaysTheStepAtTheTopOfTheScreen() {
        var session = Dungeon.newSession(21L);
        session.game().runHeadless(2);
        var drawn = new Drawn(null);

        MissionScreen.paint(drawn, session, SETTINGS);

        var tracker = drawn.line(session.run().getTracker());
        assertNotNull(tracker, "the step is not drawn: " + drawn.lines);
        assertTrue(tracker.y() < 90f, "and it is not at the top: " + tracker);
        assertTrue(session.run().getTracker().endsWith("0/" + session.run().getMission().outside()),
                "with its count: " + session.run().getTracker());
    }

    @Test
    void whatAHeroSaysIsSaidOverHisHeadAboveHisBar() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var words = SETTINGS.lootDrops().fullWord();
        session.progress().getLoot().say(words, game.getLogic().getFrame(), 90);
        game.runHeadless(1);
        var drawn = new Drawn(new Canvas.Box(700f, 400f, 60f, 8f));

        MissionScreen.paint(drawn, session, SETTINGS);

        var said = drawn.line(words);
        assertNotNull(said, "he says nothing: " + drawn.lines);
        assertTrue(said.y() < 400f, "above his bar: " + said);
        assertTrue(said.x() < 730f && said.x() + words.length() * 8 > 730f, "and over it: " + said);
    }

    @Test
    void onceItHasBeenSaidTheBubbleIsGone() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var words = SETTINGS.lootDrops().fullWord();
        session.progress().getLoot().say(words, game.getLogic().getFrame(), 1);
        game.runHeadless(3);
        var drawn = new Drawn(new Canvas.Box(700f, 400f, 60f, 8f));

        MissionScreen.paint(drawn, session, SETTINGS);

        assertNull(drawn.line(words), "it is still being said: " + drawn.lines);
    }

    /** The bubble is the hero's: what else his player owns — a summoned creature beside him — says nothing. */
    @Test
    void aCreatureOfHisThatIsNotHeHasNothingToSay() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var logic = game.getLogic();
        var hero = logic.getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Rogue")).findFirst().orElseThrow();
        logic.spawn(logic.findTemplate("Skeleton"), hero.getPosition(), hero.getPlayerIndex());
        var words = SETTINGS.lootDrops().fullWord();
        session.progress().getLoot().say(words, logic.getFrame(), 90);
        game.runHeadless(1);
        assertEquals(2, game.getSnapshot().units().stream().filter(unit -> unit.playerIndex() == hero.getPlayerIndex())
                .count(), "he and the creature beside him are both his, and both seen");
        var drawn = new Drawn(new Canvas.Box(700f, 400f, 60f, 8f));

        MissionScreen.paint(drawn, session, SETTINGS);

        assertEquals(1, drawn.lines.stream().filter(line -> line.text().equals(words)).count(),
                "one bubble, over the hero, and none over what is his beside him: " + drawn.lines);
    }
}
