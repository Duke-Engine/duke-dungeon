package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.rpg.BuffScreen;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Titled;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.ChooseHero;
import uz.dukeengine.dungeon.party.PartyMatch;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Target;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/** The target frame: what he picked out, in a small window of its own, while his panel stays his. */
class TargetFrameTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Every word drawn and every picture, on a window 1600 by 900. */
    private static final class Drawn implements Canvas {

        final List<String> text = new ArrayList<>();
        final List<Integer> portraits = new ArrayList<>();
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
        public void drawText(Font font, String said, float x, float y, int argb) {
            text.add(said);
        }

        @Override
        public void drawPortrait(int unitId, float x0, float y0, float x1, float y1) {
            portraits.add(unitId);
        }

        @Override
        public Measure measure(Font font, String said) {
            return new Measure(said.length() * 8, font.pixelHeight() + 4);
        }
    }

    private static GameObject named(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    /** A person picked out: her name, the role the file gives her, and no level or blow she does not have. */
    @Test
    void aPersonIsFramedWithHerRole() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        var commander = named(game, "Commander");
        session.orders().watch(game.getLocalPlayerIndex(), commander.getId());
        game.runHeadless(1);

        var target = session.run().getTarget();
        assertNotNull(target, "she is framed");
        assertEquals("Qo'mondon", target.role(), "her role, from the file");
        assertEquals(0, target.level(), "a person holds no level");
        assertEquals(0, target.attack(), "nor a blow");

        var drawn = new Drawn();
        Bag.targetFrame(drawn, session, SETTINGS);
        assertTrue(drawn.text.contains(Titled.of(commander.getTemplate())), "her name: " + drawn.text);
        assertTrue(drawn.text.contains("Qo'mondon"), "her role under it: " + drawn.text);
        assertTrue(drawn.text.stream().noneMatch(said -> said.endsWith(SETTINGS.hud().rankSuffix())),
                "no level: " + drawn.text);
        assertTrue(drawn.text.stream().noneMatch(said -> said.contains(" / ")), "nor health to lose: " + drawn.text);
    }

    /** In a party, a fellow hero picked out is framed too: his level and his figures, as his own run counts them. */
    @Test
    void aFellowHeroIsFramedWithHisLevel() {
        var session = Dungeon.newPartySession(PartyMatch.endless(4242L), 2, null, SETTINGS, "Rogue");
        var game = session.game();
        game.runHeadless(1);
        game.postCommand(PartyOrders.of(new ChooseHero(2, "Knight")));
        game.runHeadless(3);
        var rogue = named(game, "Rogue");
        var knight = named(game, "Knight");
        var mine = rogue.getPlayerIndex() == game.getLocalPlayerIndex() ? rogue : knight;
        var fellow = mine == rogue ? knight : rogue;
        session.orders().watch(mine.getPlayerIndex(), fellow.getId());
        game.runHeadless(1);

        var target = session.run().getTarget();
        assertNotNull(target, "the fellow is framed");
        assertEquals(fellow.getId().value(), target.id());
        assertEquals(session.run().progressOf(fellow.getPlayerIndex()).getLevel(), target.level());
        assertTrue(target.attack() > 0, "his blow: " + target);
        assertTrue(game.getSnapshot().status().startsWith("name=" + Titled.of(mine.getTemplate())),
                "and the bar is still the player's own hero: " + game.getSnapshot().status());
    }

    /** Nothing picked, nothing drawn. */
    @Test
    void nothingPickedDrawsNothing() {
        var drawn = new Drawn();
        TargetFrame.paint(drawn, null, List.of(), SETTINGS);
        assertTrue(drawn.text.isEmpty() && drawn.pictures.isEmpty());
    }

    /** A monster's frame: its name, its level, its health, its blow beside its picture, and what is on it. */
    @Test
    void aMonstersFrameSaysItsFiguresAndItsBuffs() {
        var aura = SETTINGS.skills().stream().filter(skill -> !skill.icon().isBlank()).findFirst().orElseThrow();
        var target = new Target(9, "Skelet", "", 8, 30, 40, 12, 0, "S");
        var drawn = new Drawn();

        TargetFrame.paint(drawn, target, List.of(new BuffScreen.Buff(aura, 50, 0)), SETTINGS);

        assertTrue(drawn.text.containsAll(List.of("Skelet", "8" + SETTINGS.hud().rankSuffix(), "30 / 40", "12", "S")),
                drawn.text.toString());
        assertTrue(drawn.pictures.contains(SETTINGS.hud().statIcons().get(0)), "the blow's picture");
        assertTrue(!drawn.pictures.contains(SETTINGS.hud().statIcons().get(1)), "no armour, no armour picture");
        assertTrue(drawn.pictures.contains(aura.icon()), "what is on it: " + drawn.pictures);
        assertTrue(drawn.portraits.equals(List.of(9)), "its live portrait, over the letter: " + drawn.portraits);
    }
}
