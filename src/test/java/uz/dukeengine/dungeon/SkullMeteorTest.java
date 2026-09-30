package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.game.DukeGame;

/**
 * The fire mage's ultimate: the Mage's own meteor from the other side. Open from its sixth level, cast at where he
 * stands whenever it is open, ready and paid for, its mark a thing of its own -- and its fireball after it while it
 * recharges.
 *
 * <p>Fought in an open room against a Rogue holding his ground inside its band, the mage made its level by the
 * spawner's own step before he is there to cast at.
 */
class SkullMeteorTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String MAGE = "SkeletonMage";
    private static final String MARK = "SkullMeteorMark";

    private static Skill meteor() {
        return SETTINGS.skillsFor(MAGE).getFirst();
    }

    private static Skill fireball() {
        return SETTINGS.skillsFor(MAGE).stream().filter(skill -> skill.key() == 'Q').findFirst().orElseThrow();
    }

    /** An open room forty cells by thirty, stone only round its edge. */
    private static String room() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                text.append(x == 0 || y == 0 || x == 39 || y == 29 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private record Fight(DukeGame game, GameObject hero, GameObject mage) {
    }

    /** The fire mage made {@code level} where it stands, and then a Rogue holding his ground inside its band. */
    private static Fight atLevel(int level) {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn(MAGE, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        var mage = creature(game, MAGE);
        Spawner.scale(mage, level, SETTINGS);
        arena.orders().hold(arena.hero().getIndex(), true);
        game.spawn("Rogue", arena.hero(), 240f, 150f);
        return new Fight(game, creature(game, "Rogue"), mage);
    }

    /** The frame each new one of {@code template} appeared on, over the next frames. */
    private static List<Integer> appearing(DukeGame game, int frames, String template) {
        var seen = new HashSet<Integer>();
        var when = new ArrayList<Integer>();
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var object : game.getLogic().getObjects()) {
                if (object.getTemplate().name().equals(template) && seen.add(object.getId().value())) {
                    when.add(game.getLogic().getFrame());
                }
            }
        }
        return when;
    }

    // ---- as shipped ----

    /** Written first in the fire mage's block, with its figures: an ultimate of one rank, open from level 6. */
    @Test
    void theFireMagesMeteorIsWrittenFirst() {
        var meteor = meteor();

        assertEquals(SkillEffect.METEOR, meteor.effect());
        assertEquals('R', meteor.key());
        assertEquals(6, meteor.levelForRank(1), "open from its sixth level");
        assertTrue(meteor.isUltimate(), "an ultimate, by the rule that makes one");
        assertEquals(1, meteor.maxRank());
        assertEquals(90f, meteor.damage(), 0.001f);
        assertEquals(30f, meteor.radius(), 0.001f);
        assertEquals(70f, meteor.range(), 0.001f);
        assertEquals(45, meteor.windUpFrames());
        assertEquals(600, meteor.cooldownFrames());
        assertEquals(50, meteor.manaCost());
        assertEquals("Meteor", meteor.name());
        assertEquals(MARK, meteor.projectile());
        assertFalse(meteor.hasLook(), "a monster's cast is drawn by what it throws");
        assertEquals(SkillEffect.SKILLSHOT, SETTINGS.skillsFor(MAGE).get(1).effect(), "and its fireball second");
    }

    /** Its mark is its own, drawn violet as its fireball, and measured by its own skill: the Mage's keeps its own. */
    @Test
    void itsMarkIsItsOwnAndMeasuredByItsOwnSkill() {
        var mark = SETTINGS.projectile(MARK);
        assertEquals("SkullMeteor", mark.effect());
        assertEquals(SETTINGS.projectile(fireball().projectile()).tint(), mark.tint(), "its fireball's violet");

        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);

        assertEquals(meteor().radius(), visuals.getEffectReach(mark.effect()), 0.001f, "as wide as its blast");
        assertEquals(meteor().windUpFrames() / (float) GameConstants.LOGICFRAMES_PER_SECOND,
                visuals.getEffectSeconds(mark.effect()), 0.001f, "and lying there as long as it falls");
        var his = SETTINGS.skillsFor("Mage").stream().filter(skill -> skill.effect() == SkillEffect.METEOR)
                .findFirst().orElseThrow();
        assertEquals(his.radius(), visuals.getEffectReach(SETTINGS.projectile(his.projectile()).effect()), 0.001f,
                "and the Mage's own meteor is as wide as his");
    }

    /** It is heard as it appears, as the rift is. */
    @Test
    void itIsHeardAsItAppears() {
        assertTrue(SETTINGS.sounds().stream().anyMatch(sound -> sound.name().equals("spawned." + MARK)));
    }

    // ---- in a fight ----

    /** Below its sixth level it is never cast: full, and with him inside its band for as long as its cooldown. */
    @Test
    void belowItsSixthLevelItIsNeverCast() {
        var fight = atLevel(5);

        assertTrue(fight.mage().findModule(uz.dukeengine.dungeon.skill.SkillBook.class).getMana()
                >= meteor().manaCost(), "the premise: it could pay for one");
        assertEquals(List.of(), appearing(fight.game(), meteor().cooldownFrames(), MARK));
    }

    /** From its sixth level it is cast at where he stands, and what marks the floor is its own mark. */
    @Test
    void fromItsSixthLevelItIsCastAtWhereHeStands() {
        var fight = atLevel(6);

        GameObject mark = null;
        for (int frame = 0; frame < 60 && mark == null; frame++) {
            fight.game().runHeadless(1);
            mark = creature(fight.game(), MARK);
        }

        assertNotNull(mark, "no meteor was called down on him");
        assertEquals(fight.hero().getPosition().x(), mark.getPosition().x(), 0.5f, "where he stands");
        assertEquals(fight.hero().getPosition().y(), mark.getPosition().y(), 0.5f, "where he stands");
    }

    /** While it recharges, its fireball follows: a gesture after the meteor, and no second meteor. */
    @Test
    void whileItRechargesItsFireballFollows() {
        var fight = atLevel(6);
        int swing = SETTINGS.monster(MAGE).swingFrames();
        var game = fight.game();
        var marks = new ArrayList<Integer>();
        var fireballs = new ArrayList<Integer>();
        var seen = new HashSet<Integer>();

        for (int frame = 0; frame < 300; frame++) {
            game.runHeadless(1);
            for (var object : game.getLogic().getObjects()) {
                var name = object.getTemplate().name();
                if (seen.add(object.getId().value())) {
                    if (name.equals(MARK)) {
                        marks.add(game.getLogic().getFrame());
                    } else if (name.equals(fireball().projectile())) {
                        fireballs.add(game.getLogic().getFrame());
                    }
                }
            }
        }

        assertEquals(1, marks.size(), "one meteor in ten seconds of a twenty-second cooldown: " + marks);
        assertFalse(fireballs.isEmpty(), "and no fireball while it recharged");
        assertTrue(fireballs.getFirst() >= marks.getFirst() + swing,
                "the fireball left inside the meteor's gesture: " + marks + " then " + fireballs);
    }
}
