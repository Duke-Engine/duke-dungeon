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

    /** Landed, he cannot die until the first wave is beaten: a killing blow leaves him at his floor. */
    @Test
    void fromHisLandingTheFirstWaveCannotKillHim() {
        var session = newGame();
        var script = session.run().getScript();
        for (int i = 0; i < 2400 && !script.flagged("HeroLanded"); i++) {
            frame(session, true);
        }
        session.game().runHeadless(2); // the rank told
        var hero = hero(session);
        hero.getBody().damage(1e7f);
        session.game().runHeadless(2);
        assertFalse(hero.isEffectivelyDead(), "his health stops at its floor");
        assertTrue(hero.getBody().getHealth() > 0f);
        script.flag("BattleWon");
        session.game().runHeadless(2);
        hero.getBody().damage(1e7f);
        session.game().runHeadless(2);
        assertTrue(hero.isEffectivelyDead(), "mortal again after");
    }

    /** The line's words are called out over their heads, not said in a box: the battle never stops for them. */
    @Test
    void theLineShoutsAndTheBattleGoesOn() {
        var session = newGame();
        session.game().runHeadless(5);
        var shout = session.run().getBarks().stream().filter(b -> b.conversation().equals("BattleHolds")).toList();
        assertEquals(1, shout.size(), "called out once");
        assertEquals(-1, shout.getFirst().person(), "its speakers found as it is drawn");
        assertFalse(session.run().getQuests().said().contains("BattleHolds"), "never a box");
        assertFalse(all(session.game(), "Act1CommanderFighter").isEmpty(), "she fights in her line");
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
            if (session.run().getQuests().active().stream().anyMatch(q -> q.quest().equals("FirstWave"))) {
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
        assertTrue(session.run().getQuests().active().stream().anyMatch(q -> q.quest().equals("FirstWave")),
                "the dead come out of the graves at the line");
        session.game().runHeadless(200);
        assertTrue(logic.getBeams().isEmpty(), "every beam out");
    }

    /**
     * The owner's rule: three ritualists 120 degrees apart round the landing, 115-125 out, facing it, untouchable --
     * and the landing the field's middle, so the ring stands round the whole battle rather than on a flank of it.
     */
    @Test
    void theThreeStandRoundTheLandingAndNobodyTouchesThem() {
        var session = newGame();
        var three = all(session.game(), "Ritualist");
        assertEquals(3, three.size());
        var middle = landing();
        var field = Act1Test.zone("world", "Darvoza_oldi");
        assertEquals(field.middleX(CELL), middle[0], 0.01f, "the summoning is over the battle's middle");
        assertEquals(field.middleY(CELL), middle[1], 0.01f, "the summoning is over the battle's middle");
        var angles = new ArrayList<Double>();
        for (var one : three) {
            float dx = one.getPosition().x() - middle[0];
            float dy = one.getPosition().y() - middle[1];
            double far = Math.hypot(dx, dy);
            assertTrue(far >= 115 && far <= 125, "out " + far);
            assertFalse(Act1Test.zone("world", "Saf").holds(one.getPosition().x(), one.getPosition().y(), CELL),
                    "none stands on the kingdom's line");
            assertFalse(Act1Test.zone("world", "Dushman_safi").holds(one.getPosition().x(), one.getPosition().y(), CELL),
                    "none stands on the enemy's line");
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
        while (!script.flagged("HeroLanded")) {
            frame(session, true);
        }
        // Where each stood as he struck (the cast is this frame's; the throw moves them from the next).
        for (var foe : session.game().getLogic().getObjects()) {
            if (foe.getBody() != null && !foe.isEffectivelyDead() && isEnemy(session, foe)) {
                before.put(foe, (float) Math.hypot(foe.getPosition().x() - middle[0],
                        foe.getPosition().y() - middle[1]));
            }
        }
        // The knock-back is his PUSH, cast through his skill book as he strikes (E82): not the story's own hands.
        var wave = SETTINGS.bookFor(CAMPAIGN.hero()).stream()
                .filter(skill -> skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.PUSH).findFirst().orElseThrow();
        var book = hero(session).findModule(uz.dukeengine.rpg.skill.SkillBook.class);
        assertTrue(book.getLastCastFrame() >= 0 && session.game().getLogic().getFrame() - book.getLastCastFrame() <= 2,
                "he cast it as he struck: last cast at " + book.getLastCastFrame());
        float highest = 0f;
        for (int i = 0; i < wave.durationFrames() + 2; i++) {
            for (var foe : before.keySet()) {
                highest = Math.max(highest, foe.getPosition().z()
                        - session.game().getLogic().groundHeight(foe.getPosition()));
            }
            session.game().runHeadless(1);
        }
        // Those inside its reach as he struck: every one stunned, and thrown its whole Distance out where nothing stands
        // in the way -- the farthest first, through each other (E95); only stone or a body that is no victim stops one.
        // He lands in the battle's middle now, so the enemies pressed against the kingdom's own line have it behind
        // them: the wave stuns them where they stand, and the line (a body that is no victim) stops the throw.
        var line = Act1Test.zone("world", "Saf");
        double landingToLine = Math.hypot(middle[0] - line.middleX(CELL), middle[1] - line.middleY(CELL));
        int inside = 0;
        int thrown = 0;
        for (var each : before.entrySet()) {
            var foe = each.getKey();
            if (each.getValue() <= wave.radius() - 10f && !foe.isEffectivelyDead()) {
                inside++;
                float now = (float) Math.hypot(foe.getPosition().x() - middle[0], foe.getPosition().y() - middle[1]);
                assertTrue(foe.hasStatus(ObjectStatus.DISABLED), "stunned");
                if (now > each.getValue() + wave.distance() / 2f) {
                    thrown++;
                } else {
                    // Only a body that is no victim stops one: the kingdom's line, the commander fighting out ahead
                    // of it, or one beyond the wave's reach that it never moved, standing against it.
                    double toLine = Math.hypot(foe.getPosition().x() - line.middleX(CELL),
                            foe.getPosition().y() - line.middleY(CELL));
                    var at = foe.getPosition();
                    var hero = hero(session);
                    boolean againstOne = session.game().getLogic().getObjects().stream()
                            .anyMatch(o -> o != hero && o != foe && o.getBody() != null
                                    && !o.isEffectivelyDead()
                                    && Math.hypot(o.getPosition().x() - at.x(), o.getPosition().y() - at.y()) < 30f);
                    assertTrue(toLine < landingToLine || againstOne,
                            "stopped only by a body that is no victim: " + toLine + " against the landing's "
                                    + landingToLine);
                }
            }
        }
        assertTrue(inside > 0, "the premise: enemies within the wave");
        // Half at least: the rest pressed on the line and on the commander fighting in it.
        assertTrue(thrown * 2 >= inside, thrown + " of " + inside + " thrown out from the landing");
        assertTrue(thrown > 0, "the wave throws the rest clear");
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
        while (!script.state().fired().contains("BattleWon")) { // the trigger that lets the battle go
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
        session.game().runHeadless(1); // the trigger fired: its actions done by the next frame
        assertFalse(session.run().holdsTheBattle(), "let go when the talk is over");
        session.game().runHeadless(3);
        assertFalse(hero(session).hasStatus(ObjectStatus.DISABLED), "free to move");
    }

    /**
     * The opening's camera is one smooth path (E85): from where it stands, over the field, past the kingdom's line and
     * then back to the middle of it, wide enough to hold the whole ring of ritualists -- through the line's middle on
     * the way, never standing still until it arrives.
     */
    @Test
    void theOpeningsCameraFliesOnePath() {
        var session = newGame();
        var camera = session.game().camera();
        for (int i = 0; i < 300 && !"Summoning".equals(session.run().cutsceneName()); i++) {
            frame(session, false);
        }
        assertEquals("Summoning", session.run().cutsceneName());
        frame(session, false);
        var line = Act1Test.zone("world", "Saf");
        var middle = landing();
        double nearest = Double.MAX_VALUE;
        int stood = 0;
        var was = camera.current();
        // Down close over the line (45 frames and 5 held), its line said, then the path's 150 frames.
        for (int i = 0; i < 65; i++) {
            frame(session, false);
            was = camera.current();
        }
        for (int i = 0; i < 148; i++) {
            frame(session, false);
            var now = camera.current();
            nearest = Math.min(nearest, Math.hypot(now.x() - line.middleX(CELL), now.y() - line.middleY(CELL)));
            if (i > 10 && i < 138 && Math.hypot(now.x() - was.x(), now.y() - was.y()) < 1e-3) {
                stood++;
            }
            was = now;
        }
        frame(session, false);
        frame(session, false);
        var end = camera.current();
        assertTrue(nearest < 20, "past the kingdom's line: " + nearest);
        assertEquals(0, stood, "it stood still " + stood + " frames between its ends (the eases' first and last)");
        assertEquals(middle[0], end.x(), 1f, "onto the landing");
        assertEquals(middle[1], end.y(), 1f);
        assertEquals(Math.toRadians(38), end.pitch(), 0.02, "low, taking in the whole ring");
    }

    private static boolean isEnemy(Dungeon.Session session, GameObject thing) {
        var hero = hero(session);
        return thing.getPlayerIndex() != hero.getPlayerIndex()
                && thing.getPlayerIndex() != PlayerList.NEUTRAL_INDEX
                && SETTINGS.battle("Opening").enemy().contains(thing.getTemplate().name());
    }
}
