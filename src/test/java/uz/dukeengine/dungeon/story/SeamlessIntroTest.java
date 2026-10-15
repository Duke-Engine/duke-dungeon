package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/**
 * The intro of the seamless campaign, headless (docs/plan/2026-10-15-seamless-campaign.md, section 4): the battle laid
 * with the ritualists round the landing, the ritual, the beams, the pillar, the meteor, the impact's knock-back, the
 * battle held through what he is told, and only then the commander's lead.
 */
class SeamlessIntroTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Campaign CAMPAIGN = SETTINGS.campaign();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static Dungeon.Session newGame() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(CAMPAIGN, 1, null);
        session.game().runHeadless(1);
        return session;
    }

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .filter(o -> !o.isEffectivelyDead()).toList();
    }

    private static GameObject hero(Dungeon.Session session) {
        return all(session.game(), CAMPAIGN.hero()).getFirst();
    }

    private static float[] landing() {
        var zone = Act1Test.zone("world", "Qonish");
        return new float[] {zone.middleX(CELL), zone.middleY(CELL)};
    }

    /** One frame on, every line read at once; a cutscene plays its own frames unless {@code skip}. */
    private static void frame(Dungeon.Session session, boolean skip) {
        session.run().read(session.game().getLocalPlayerIndex(), 10_000);
        if (skip && session.run().inCutscene()) {
            session.run().skipCutscene(session.game());
        }
        session.game().runHeadless(1);
    }

    /**
     * The beats as the world shows them, each at its frame (the data's Delays): the ritual's clip on the three, their
     * three beams, the pillar, the hero leaving the sky, the landing, the talk, the lead.
     */
    @Test
    void theBeatsComeInTheirOrderAtTheDatasFrames() {
        var session = newGame();
        var script = session.run().getScript();
        var logic = session.game().getLogic();
        var at = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < 2400 && !at.containsKey("lead"); i++) {
            frame(session, true);
            int now = logic.getFrame();
            var three = all(session.game(), "Ritualist");
            if (!three.isEmpty() && three.getFirst().getBusyClip() != null) {
                at.putIfAbsent("ritual", now);
            }
            if (logic.getBeams().size() >= 3) {
                at.putIfAbsent("beams", now);
            }
            if (logic.getBeams().size() >= 4) {
                at.putIfAbsent("pillar", now);
            }
            if (at.containsKey("ritual") && hero(session).getPosition().z() < 2000f) {
                at.putIfAbsent("meteor", now);
            }
            if (script.flagged("HeroLanded")) {
                at.putIfAbsent("impact", now);
            }
            if (session.run().cutsceneName() != null && session.run().cutsceneName().equals("Landing")
                    || script.state().fired().contains("BattleWon")) {
                at.putIfAbsent("talk", now);
            }
            if (script.flagged("BattleWon")) {
                at.putIfAbsent("lead", now);
            }
        }
        assertEquals(List.of("ritual", "beams", "pillar", "meteor", "impact", "talk", "lead"),
                new ArrayList<>(at.keySet()), "every beat, in order: " + at);
        int began = at.get("ritual");
        assertEquals(105, at.get("beams") - began, 1, "the beams fire 105 frames into the ritual: " + at);
        assertEquals(120, at.get("pillar") - began, 1, "the pillar rises at 120");
        assertEquals(150, at.get("meteor") - began, 1, "the meteor falls at 150");
        assertEquals(174, at.get("impact") - began, 1, "and strikes 24 frames later");
        assertTrue(session.run().getQuests().active().stream().anyMatch(q -> q.quest().equals("FollowCommander")),
                "the commander leads him on");
        session.game().runHeadless(200);
        assertTrue(logic.getBeams().isEmpty(), "every beam out");
    }

    /** The owner's rule: three ritualists 120 degrees apart round the landing, 25-35 out, facing it, untouchable. */
    @Test
    void theThreeStandRoundTheLandingAndNobodyTouchesThem() {
        var session = newGame();
        var three = all(session.game(), "Ritualist");
        assertEquals(3, three.size());
        var middle = landing();
        var angles = new ArrayList<Double>();
        for (var one : three) {
            float dx = one.getPosition().x() - middle[0];
            float dy = one.getPosition().y() - middle[1];
            double far = Math.hypot(dx, dy);
            assertTrue(far >= 25 && far <= 35, "out " + far);
            angles.add(Math.toDegrees(Math.atan2(dy, dx)));
            double facing = Math.toDegrees(one.getOrientation());
            double toward = Math.toDegrees(Math.atan2(-dy, -dx));
            assertEquals(0, Math.abs(Math.IEEEremainder(facing - toward, 360)), 1, "facing the middle");
            assertEquals(PlayerList.NEUTRAL_INDEX, one.getPlayerIndex(), "nobody's enemy");
        }
        angles.sort(Double::compare);
        for (int i = 0; i < 3; i++) {
            double gap = Math.IEEEremainder(angles.get((i + 1) % 3) - angles.get(i), 360);
            assertEquals(120, gap < 0 ? gap + 360 : gap, 3, "120 degrees apart: " + angles);
        }
        for (int i = 0; i < 160; i++) {
            frame(session, true);
            for (var one : three) {
                assertTrue(one.getBody() == null || one.getBody().getHealth() >= one.getBody().getMaxHealth(),
                        "a ritualist was hurt");
            }
        }
        assertEquals(3, all(session.game(), "Ritualist").size(), "all three stand through the ritual");
    }

    /** The impact throws the enemy out and stuns them, the ritualists vanish, and he kneels at the landing. */
    @Test
    void theImpactThrowsTheEnemyClearAndHoldsTheBattle() {
        var session = newGame();
        var script = session.run().getScript();
        var middle = landing();
        float skyHigh = 0f;
        Map<GameObject, Float> before = new LinkedHashMap<>();
        // Up to the frame the meteor leaves the sky.
        while (skyHigh < 1000f || hero(session).getPosition().z() >= skyHigh) {
            frame(session, true);
            skyHigh = Math.max(skyHigh, hero(session).getPosition().z());
        }
        assertTrue(skyHigh > 1000f, "held out of sight in the sky until the meteor: " + skyHigh);
        for (var foe : session.game().getLogic().getObjects()) {
            if (foe.getBody() != null && !foe.isEffectivelyDead() && isEnemy(session, foe)) {
                before.put(foe, (float) Math.hypot(foe.getPosition().x() - middle[0],
                        foe.getPosition().y() - middle[1]));
            }
        }
        while (!script.flagged("HeroLanded")) {
            frame(session, true);
        }
        var wave = SETTINGS.shockwave("MeteorShock");
        float highest = 0f;
        for (int i = 0; i < wave.frames() + 2; i++) {
            for (var foe : before.keySet()) {
                highest = Math.max(highest, foe.getPosition().z()
                        - session.game().getLogic().groundHeight(foe.getPosition()));
            }
            session.game().runHeadless(1);
        }
        int thrown = 0;
        for (var each : before.entrySet()) {
            var foe = each.getKey();
            if (each.getValue() <= wave.radius() && !foe.isEffectivelyDead()) {
                float now = (float) Math.hypot(foe.getPosition().x() - middle[0], foe.getPosition().y() - middle[1]);
                assertTrue(now > each.getValue(), "thrown out from the landing");
                assertTrue(foe.hasStatus(ObjectStatus.DISABLED), "and stunned");
                thrown++;
            }
        }
        assertTrue(thrown > 0, "the premise: enemies within the wave");
        assertTrue(highest > 5f, "through the air: " + highest);
        var at = hero(session).getPosition();
        assertTrue(Act1Test.zone("world", "Qonish").holds(at.x(), at.y(), CELL), "he stands where he struck");
        assertEquals(session.game().getLogic().groundHeight(at), at.z(), 0.01f, "on the ground");
        assertEquals("HeroLanding", hero(session).getBusyClip(), "kneeling in the smoke, rising");
        assertTrue(all(session.game(), "Ritualist").isEmpty(), "the ritualists are gone in a flash");
        assertTrue(session.run().holdsTheBattle(), "the battle held");
    }

    /** From the impact to the dialogue's end the hero is held and lands no blow; then he is let go. */
    @Test
    void theHeroDoesNotFightBeforeTheDialogueEnds() {
        var session = newGame();
        var script = session.run().getScript();
        while (!script.flagged("HeroLanded")) {
            frame(session, true);
        }
        var foes = session.game().getLogic().getObjects().stream()
                .filter(o -> o.getBody() != null && !o.isEffectivelyDead() && isEnemy(session, o)).toList();
        var health = new LinkedHashMap<GameObject, Float>();
        foes.forEach(foe -> health.put(foe, foe.getBody().getHealth()));
        var stood = hero(session).getPosition();
        int held = 0;
        // The cutscene plays its own frames: the lines are read at once, the shots run their length.
        while (!script.flagged("BattleWon")) {
            assertTrue(hero(session).hasStatus(ObjectStatus.DISABLED), "held while he is told");
            frame(session, false);
            held++;
            assertTrue(held < 2000, "the dialogue ends");
        }
        assertTrue(held > 60, "the talk took its frames: " + held);
        for (var each : health.entrySet()) {
            var foe = each.getKey();
            assertTrue(foe.isEffectivelyDead() || foe.getBody().getHealth() >= each.getValue() - 0.01f,
                    "nobody was struck while the battle was held");
        }
        assertEquals(stood.x(), hero(session).getPosition().x(), 0.01f, "he did not step");
        assertFalse(session.run().holdsTheBattle(), "let go when the talk is over");
        session.game().runHeadless(3);
        assertFalse(hero(session).hasStatus(ObjectStatus.DISABLED), "free to move");
    }

    private static boolean isEnemy(Dungeon.Session session, GameObject thing) {
        var hero = hero(session);
        return thing.getPlayerIndex() != hero.getPlayerIndex()
                && thing.getPlayerIndex() != PlayerList.NEUTRAL_INDEX
                && SETTINGS.battle("Opening").enemy().contains(thing.getTemplate().name());
    }
}
