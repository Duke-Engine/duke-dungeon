package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.hero.LevelBonus;

/**
 * A kill that pays shows it: coins out of the corpse and the gold floating over it -- and a kill that pays nothing
 * shows nothing.
 */
class GoldBurstTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template)).toList();
    }

    private static GameObject hero(DukeGame game) {
        return all(game, SETTINGS.run().defaultHero()).getFirst();
    }

    private static Dungeon.Session session() {
        var session = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        return session;
    }

    private static void killed(GameObject hero, GameObject monster) {
        monster.getBody().damage(1_000_000f, DamageType.NORMAL,
                new Death(DeathType.NORMAL, hero.getId(), hero.getPlayerIndex()));
    }

    /** What a placed monster pays with as it dies: its Bounty, the one module that hears a death and pays. */
    private static uz.dukeengine.core.module.DieModule bounty(GameObject monster) {
        return monster.getModules().stream().filter(module -> module.getClass().getSimpleName().equals("Bounty"))
                .map(uz.dukeengine.core.module.DieModule.class::cast).findFirst().orElse(null);
    }

    private static List<EffectPlayed> coinsPlayed(DukeGame game) {
        return game.getLogic().drainEvents().stream().filter(EffectPlayed.class::isInstance)
                .map(EffectPlayed.class::cast)
                .filter(played -> played.name().equals(SETTINGS.goldBurst().coinLook())).toList();
    }

    @Test
    void aPaidKillBurstsCoinsAndFloatsItsGoldOverTheCorpse() {
        var session = session();
        var game = session.game();
        var hero = hero(game);
        var skeleton = all(game, "Skeleton").getFirst();
        var at = skeleton.getPosition();
        int gold = SETTINGS.story().trade().goldFor(LevelBonus.levelOf(skeleton));
        game.getLogic().drainEvents();
        // Its death as the world hears it, without running a frame: a frame's client takes the events it posts.
        bounty(skeleton).onDie(new Death(DeathType.NORMAL, hero.getId(), hero.getPlayerIndex()));
        var coins = coinsPlayed(game);

        var burst = SETTINGS.goldBurst();
        assertEquals("CoinBurst", burst.coinLook());
        assertEquals(burst.burstsFor(gold), coins.size(), "the coins, once for every " + burst.goldPerBurst());
        assertTrue(coins.stream().allMatch(played -> hero.getId().equals(played.toward())),
                "every burst played toward the hero whose kill it was: " + coins);
        var layers = SETTINGS.effects().stream().filter(effect -> effect.name().equals(burst.coinLook()))
                .findFirst().orElseThrow().layers();
        assertTrue(layers.stream().anyMatch(layer -> Boolean.TRUE.equals(layer.home()) && layer.homeDelay() != null
                && layer.bounce() != null && layer.bounce() > 0f && layer.flip() != null),
                "and its coins bounce, spin over and fly into him");
        var paid = session.run().payments();
        assertEquals(1, paid.size());
        assertEquals(new DungeonRun.Payment(paid.getFirst().frame(), hero.getPlayerIndex(), gold, at.x(), at.y(),
                at.z()), paid.getFirst(), "his gold, where it fell");

        int frame = paid.getFirst().frame();
        var floating = TradeScreen.floating(paid, hero.getPlayerIndex(), frame + 3, SETTINGS);
        assertEquals(1, floating.size());
        assertEquals("+" + gold, floating.getFirst().text());
        assertEquals(at.z() + burst.floatHeight(), floating.getFirst().z(), 1e-4f);
        assertEquals(1f, floating.getFirst().alpha(), "solid at first");
        int life = Math.round(burst.floatSeconds() * uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND);
        var late = TradeScreen.floating(paid, hero.getPlayerIndex(), frame + life - 2, SETTINGS);
        assertTrue(late.getFirst().alpha() < 0.2f && late.getFirst().rise() > floating.getFirst().rise(),
                "risen and nearly gone");
        assertTrue(TradeScreen.floating(paid, hero.getPlayerIndex(), frame + life, SETTINGS)
                .isEmpty(), "and gone");
        assertTrue(TradeScreen.floating(paid, hero.getPlayerIndex() + 1, frame + 3, SETTINGS)
                .isEmpty(), "another player's gold is not floated on this machine");

        var said = new java.util.ArrayList<String>();
        new TradeScreen(SETTINGS).floats(new Painted(said), session);
        assertTrue(said.contains("+" + gold), "drawn over the corpse: " + said);
    }

    /** A window that keeps what was written on it, with every point of the world in the middle of it. */
    private record Painted(List<String> said) implements uz.dukeengine.client3d.Canvas {
        public int width() {
            return 1920;
        }

        public int height() {
            return 1080;
        }

        public Point screenOf(float x, float y, float height) {
            return new Point(960f, 540f);
        }

        public void drawImage(Image image, float x0, float y0, float x1, float y1, int argb, Blend blend) {
        }

        public void fillRect(float x, float y, float w, float h, int argb) {
        }

        public void openRect(float x, float y, float w, float h, float lineWidth, int argb) {
        }

        public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        }

        public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        }

        public void line(float x0, float y0, float x1, float y1, float width, int argb, int argbEnd) {
        }

        public void clip(float x0, float y0, float x1, float y1) {
        }

        public void noClip() {
        }

        public void drawText(Font font, String text, float x, float y, int argb) {
            said.add(text);
        }

        public Measure measure(Font font, String text) {
            return new Measure(text.length() * font.pixelHeight() / 2, font.pixelHeight());
        }
    }

    @Test
    void aBigPurseIsMoreCoinsAndABiggerNumber() {
        var burst = SETTINGS.goldBurst();
        assertEquals(0, burst.burstsFor(0));
        assertEquals(1, burst.burstsFor(1));
        assertTrue(burst.burstsFor(40) > burst.burstsFor(8));
        assertEquals(burst.mostBursts(), burst.burstsFor(100_000), "capped");
        var small = new DungeonRun.Payment(0, 0, burst.bigGold() - 1, 0f, 0f, 0f);
        var big = new DungeonRun.Payment(0, 0, burst.bigGold(), 0f, 0f, 0f);
        var shown = TradeScreen.floating(List.of(small, big), 0, 1, SETTINGS);
        assertEquals(1f, shown.get(0).scale());
        assertEquals(burst.bigScale(), shown.get(1).scale());
    }

    /** A thing something summoned carries no bounty: its death pays nothing, and nothing bursts or floats. */
    @Test
    void aSummonsDeathShowsNothing() {
        var session = session();
        var game = session.game();
        var hero = hero(game);
        var logic = game.getLogic();
        var skeleton = all(game, "Skeleton").getFirst();
        var summoned = logic.spawn(logic.getThingFactory().findTemplate("Skeleton"), skeleton.getPosition(),
                skeleton.getPlayerIndex());
        assertNotNull(summoned);
        assertEquals(null, bounty(summoned), "nothing to pay with, so nothing to play");
        killed(hero, summoned);
        game.runHeadless(2);
        assertTrue(session.run().payments().isEmpty());
        assertEquals(0, session.run().goldOf(hero.getPlayerIndex()));
    }
}
