package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.Layout;
import uz.dukeengine.dungeon.party.ChooseHero;
import uz.dukeengine.dungeon.party.PartyMatch;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.stage.Stage;
import uz.dukeengine.game.DukeGame;

/** What the game says over the world: the mission's step at the top, and what a hero says over his head. */
class MissionScreenTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    /**
     * Every line drawn and where, and every slab filled, on a window 1600 by 900 where every thing's bar stands at
     * one place — or, where {@link #bars} is set, at the place it names for each.
     */
    private static final class Drawn implements Canvas {

        record Line(String text, float x, float y) {
        }

        /** A slab filled: what a line of words is set on. */
        record Slab(float x, float y, float w, float h) {
        }

        final List<Line> lines = new ArrayList<>();
        final List<Slab> slabs = new ArrayList<>();
        final Box bar;
        /** Where each thing's bar stands, by the thing's id, when they do not all stand at {@link #bar}. */
        IntFunction<Box> bars;

        Drawn(Box bar) {
            this.bar = bar;
        }

        Line line(String text) {
            return lines.stream().filter(line -> line.text().equals(text)).findFirst().orElse(null);
        }

        /** The slab a line is set on: the last filled with the line's top left in it, which is the one on top. */
        Slab under(Line line) {
            return slabs.reversed().stream()
                    .filter(slab -> slab.x() <= line.x() && line.x() <= slab.x() + slab.w()
                            && slab.y() <= line.y() && line.y() <= slab.y() + slab.h())
                    .findFirst().orElse(null);
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
            slabs.add(new Slab(x, y, w, h));
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
            return bars == null ? bar : bars.apply(id);
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
        var hero = find(game, "Rogue");
        logic.spawn(logic.findTemplate("Skeleton"), hero.getPosition(), hero.getPlayerIndex());
        var words = SETTINGS.lootDrops().fullWord();
        session.progress().getLoot().say(words, logic.getFrame(), 90);
        game.runHeadless(1);
        long his = game.getSnapshot().units().stream().filter(unit -> unit.playerIndex() == hero.getPlayerIndex())
                .count();
        assertTrue(his >= 2, "the premise: he and the creature beside him are both his, and both seen: " + his);
        var drawn = new Drawn(new Canvas.Box(700f, 400f, 60f, 8f));

        MissionScreen.paint(drawn, session, SETTINGS);

        assertEquals(1, drawn.lines.stream().filter(line -> line.text().equals(words)).count(),
                "one bubble, over the hero, and none over what is his beside him: " + drawn.lines);
    }

    /**
     * The client places a bar until the thing it is over is well past an edge of the window, and says where it is
     * shown or not: what a hero says is said where he can be seen, and nowhere else. A bar that is half on the window
     * is on it.
     */
    @Test
    void aBarPlacedOffTheWindowHasNoBubbleOverIt() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var words = SETTINGS.lootDrops().fullWord();
        session.progress().getLoot().say(words, game.getLogic().getFrame(), 90);
        game.runHeadless(1);

        record Placed(String where, Canvas.Box bar) {
        }
        for (var off : List.of(
                new Placed("left of the window", new Canvas.Box(-200f, 400f, 60f, 8f)),
                new Placed("right of the window", new Canvas.Box(1700f, 400f, 60f, 8f)),
                new Placed("above the window", new Canvas.Box(700f, -50f, 60f, 8f)))) {
            var drawn = new Drawn(off.bar());
            MissionScreen.paint(drawn, session, SETTINGS);
            assertNull(drawn.line(words), "a bubble over a bar " + off.where() + ": " + drawn.lines);
        }
        for (var on : List.of(
                new Placed("past the left edge", new Canvas.Box(-40f, 400f, 60f, 8f)),
                new Placed("past the right edge", new Canvas.Box(1580f, 400f, 60f, 8f)))) {
            var drawn = new Drawn(on.bar());
            MissionScreen.paint(drawn, session, SETTINGS);
            var said = drawn.line(words);
            assertNotNull(said, "no bubble over a bar half " + on.where() + ": " + drawn.lines);
            var slab = drawn.under(said);
            assertTrue(slab.x() >= 4f && slab.x() + slab.w() <= 1596f, "and it is kept on the window: " + slab);
        }
    }

    /**
     * A bar near the top of the window has no room above it for the bubble; it is not cut off by the window's edge,
     * and a bubble that has the room stands right over its bar, not at the top of the window.
     */
    @Test
    void aBubbleOverABarNearTheTopOfTheWindowIsNotCutOff() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var words = SETTINGS.lootDrops().fullWord();
        session.progress().getLoot().say(words, game.getLogic().getFrame(), 90);
        game.runHeadless(1);

        for (float fromTheTop : new float[] {0f, 10f, 30f}) {
            var drawn = new Drawn(new Canvas.Box(700f, fromTheTop, 60f, 8f));
            MissionScreen.paint(drawn, session, SETTINGS);
            var said = drawn.line(words);
            assertNotNull(said, "no bubble over a bar " + fromTheTop + " from the top: " + drawn.lines);
            var slab = drawn.under(said);
            assertNotNull(slab, "no slab under his words: " + drawn.slabs);
            assertTrue(slab.y() >= 4f, "cut off by the top of the window, " + fromTheTop + " from it: " + slab);
        }

        var drawn = new Drawn(new Canvas.Box(700f, 400f, 60f, 8f));
        MissionScreen.paint(drawn, session, SETTINGS);
        var slab = drawn.under(drawn.line(words));
        float gap = 400f - (slab.y() + slab.h());
        assertTrue(gap > 0f && gap < 20f, "with room above it, it stands right over its bar: " + slab);
    }

    /**
     * In a party every hero says what his own bag was told, over his own head: the local player's note is not
     * everybody's.
     */
    @Test
    void eachHeroOfAPartySaysHisOwnWordsOverHisOwnHead() {
        var session = Dungeon.newPartySession(PartyMatch.endless(4242L), 2, null, SETTINGS, "Rogue");
        var game = session.game();
        game.runHeadless(1);
        game.postCommand(PartyOrders.of(new ChooseHero(2, "Knight")));
        game.runHeadless(3);
        var rogue = find(game, "Rogue");
        var knight = find(game, "Knight");
        var rogueWords = SETTINGS.lootDrops().fullWord();
        var knightWords = SETTINGS.lootDrops().noUseWord();
        int frame = game.getLogic().getFrame();
        session.run().progressOf(rogue.getPlayerIndex()).getLoot().say(rogueWords, frame, 90);
        session.run().progressOf(knight.getPlayerIndex()).getLoot().say(knightWords, frame, 90);
        game.runHeadless(1);
        var seen = game.getSnapshot().units().stream().map(unit -> unit.id()).toList();
        assertTrue(seen.contains(rogue.getId().value()) && seen.contains(knight.getId().value()),
                "the premise: both heroes are on the screen");
        var drawn = new Drawn(null);
        drawn.bars = id -> id == rogue.getId().value() ? new Canvas.Box(300f, 400f, 60f, 8f)
                : id == knight.getId().value() ? new Canvas.Box(1200f, 600f, 60f, 8f) : null;

        MissionScreen.paint(drawn, session, SETTINGS);

        var first = drawn.line(rogueWords);
        var second = drawn.line(knightWords);
        assertNotNull(first, "the first hero says nothing: " + drawn.lines);
        assertNotNull(second, "the second hero says nothing: " + drawn.lines);
        assertTrue(first.x() < 330f && first.x() + rogueWords.length() * 8 > 330f && first.y() < 400f,
                "his own words over the first: " + first);
        assertTrue(second.x() < 1230f && second.x() + knightWords.length() * 8 > 1230f && second.y() < 600f
                && second.y() > 400f, "and the second's over the second: " + second);
    }

    /**
     * A run that is over has no step to say: the banner has the screen, and the last line of the floor it ended on
     * is not left under it — through the wait, until the next floor is laid and says its own.
     */
    @Test
    void theStepIsNotLeftUnderTheBannerOfADeath() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var run = session.run();
        assertFalse(run.getTracker().isEmpty(), "the premise: a step is said while he fights");

        game.getLogic().destroyObject(find(game, "Rogue"));
        game.runHeadless(1);
        assertEquals(DungeonRun.State.DEAD, run.getState());
        assertEquals("", run.getTracker(), "the floor he died on says nothing more");
        var drawn = new Drawn(null);
        MissionScreen.paint(drawn, session, SETTINGS);
        assertTrue(drawn.lines.isEmpty(), "and nothing is drawn under the banner: " + drawn.lines);

        game.runHeadless(SETTINGS.run().respawnDelayFrames() / 2);
        assertEquals(DungeonRun.State.DEAD, run.getState(), "the premise: still waiting");
        assertEquals("", run.getTracker(), "and still nothing to say");

        game.runHeadless(SETTINGS.run().respawnDelayFrames());
        assertEquals(DungeonRun.State.RUNNING, run.getState());
        assertFalse(run.getTracker().isEmpty(), "the next floor says its step");
    }

    /** The same for a win: the boss down, the banner up, and no "kill the boss" under it. */
    @Test
    void theStepIsNotLeftUnderTheBannerOfAWin() {
        var floor = DungeonGenerator.generate(21L, SETTINGS, 1,
                Layout.sized(SETTINGS, SETTINGS.mapWidth(), SETTINGS.mapHeight(), SETTINGS.maxRooms()));
        var session = Dungeon.newStageSession(new Stage("test", "Test", "", 1, 1, 21L, floor), SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var run = session.run();
        assertEquals(SETTINGS.run().killBossWord(), run.getTracker(), "the premise: the boss stands, and it is said");

        game.getLogic().destroyObject(find(game, floor.boss().kind()));
        game.runHeadless(2);
        assertEquals(DungeonRun.State.WON, run.getState());
        assertEquals("", run.getTracker(), "the boss is dead, and there is nothing left to tell him to do");
        var drawn = new Drawn(null);
        MissionScreen.paint(drawn, session, SETTINGS);
        assertTrue(drawn.lines.isEmpty(), "nothing is drawn under the banner: " + drawn.lines);

        game.runHeadless(SETTINGS.run().victoryFrames() / 2);
        assertEquals(DungeonRun.State.WON, run.getState(), "the premise: still looking at the word");
        assertEquals("", run.getTracker(), "and still nothing to say");

        game.runHeadless(SETTINGS.run().victoryFrames());
        assertEquals(DungeonRun.State.RUNNING, run.getState());
        assertEquals(SETTINGS.run().killBossWord(), run.getTracker(), "the next run says its step again");
    }

    /**
     * The boss down on a floor with another below it: the banner names the next depth and the floor stays open
     * a while, with no "kill the boss" under it for a boss that is dead — until the next floor is laid.
     */
    @Test
    void theStepIsNotLeftUnderTheBannerOfADescent() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var run = session.run();
        assertFalse(run.getTracker().isEmpty(), "the premise: a step is said while the boss stands");

        game.getLogic().destroyObject(find(game, SETTINGS.bossKindAt(run.getDepth())));
        game.runHeadless(2);
        assertEquals(DungeonRun.State.RUNNING, run.getState());
        assertEquals(1, run.getDepth(), "the premise: the floor is not left yet");
        assertTrue(game.getSnapshot().banner().contains(SETTINGS.run().nextDepthWord(2)),
                "the premise: the banner names the next depth: " + game.getSnapshot().banner());
        assertEquals("", run.getTracker(), "the boss is dead, and there is nothing left to tell him to do");
        var drawn = new Drawn(null);
        MissionScreen.paint(drawn, session, SETTINGS);
        assertTrue(drawn.lines.isEmpty(), "nothing is drawn under the banner: " + drawn.lines);

        game.runHeadless(SETTINGS.run().descendDelayFrames() / 2);
        assertEquals(1, run.getDepth(), "the premise: still waiting to close");
        assertEquals("", run.getTracker(), "and still nothing to say");

        game.runHeadless(SETTINGS.run().descendDelayFrames());
        assertEquals(2, run.getDepth());
        assertFalse(run.getTracker().isEmpty(), "the next floor says its step");
    }
}
