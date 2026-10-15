package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.hero.GrowableBody;

/**
 * The owner, 2026-10-15: while the hero flinches he must not strike -- a swing begun under a flinch was drawn as
 * nothing and the enemy lost health with no blow shown. So a heavy blow staggers him in the simulation (the skill
 * system's stun), his Hurt clip plays for exactly the stagger, and a crowd cannot hold him in one.
 */
class StaggerTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String KNIGHT = "Knight";

    private record Fight(uz.dukeengine.game.DukeGame game, GameObject knight, GameObject skeleton) {
        void run(int frames) {
            game.runHeadless(frames);
        }

        boolean staggered() {
            return knight.hasStatus(ObjectStatus.DISABLED);
        }
    }

    /** The knight at a sturdy skeleton that stands still and strikes nothing, swinging at it. */
    private static Fight fight() {
        var world = Dungeon.world(room(), SETTINGS, Content.units());
        var game = world.game();
        game.spawn(KNIGHT, world.hero(), 100f, 150f);
        game.spawn("Skeleton", world.dungeon(), 108f, 150f);
        game.runHeadless(1);
        var knight = find(game, KNIGHT);
        var skeleton = find(game, "Skeleton");
        for (var each : new GameObject[] {knight, skeleton}) {
            ((GrowableBody) each.getBody()).setMaxHealth(100_000f);
            each.getBody().setHealth(100_000f);
        }
        var timers = skeleton.findModule(uz.dukeengine.combat.module.StatusUpdate.class);
        timers.apply(ObjectStatus.DISABLED, Integer.MAX_VALUE);
        skeleton.setStatus(ObjectStatus.DISABLED);
        knight.findModule(uz.dukeengine.combat.module.WeaponUpdate.class).attack(skeleton.getId());
        var fight = new Fight(game, knight, skeleton);
        fight.run(60); // into his rhythm of blows
        return fight;
    }

    private static float heavy() {
        return SETTINGS.heavyAt(KNIGHT) * 4f; // well past his bar after his armour
    }

    @Test
    void aHeavyBlowStaggersHimAndHisSwordLandsNothingUntilItEnds() {
        var fight = fight();
        assertTrue(fight.skeleton.getBody().getHealth() < 100_000f, "he never struck, so this proves nothing");
        // Struck just as his next blow winds up (25 frames of reload after one lands, then 9 of wind-up): unstaggered,
        // that blow would land inside the stagger's 11 frames.
        float was = fight.skeleton.getBody().getHealth();
        for (int i = 0; i < 40 && fight.skeleton.getBody().getHealth() == was; i++) {
            fight.run(1);
        }
        fight.run(24);

        fight.knight.getBody().damage(heavy());
        assertTrue(fight.staggered(), "a heavy blow did not stagger him");
        int frames = 0;
        while (fight.staggered()) {
            float before = fight.skeleton.getBody().getHealth();
            fight.run(1);
            frames++;
            assertEquals(before, fight.skeleton.getBody().getHealth(), 0f, "his sword landed while he staggered");
            assertTrue(frames <= SETTINGS.stagger().frames(), "the stagger outlasts its " + SETTINGS.stagger().frames());
        }
        assertEquals(SETTINGS.stagger().frames(), frames, "the stagger is not its Frames long");
        float after = fight.skeleton.getBody().getHealth();
        fight.run(60);
        assertTrue(fight.skeleton.getBody().getHealth() < after, "he never struck again after it");
    }

    @Test
    void aLightBlowDoesNotStaggerHim() {
        var fight = fight();
        fight.knight.getBody().damage(SETTINGS.heavyAt(KNIGHT) / 4f);
        assertFalse(fight.staggered(), "a light blow staggered him");
        assertNull(fight.knight.getBusyClip(), "a light blow made him flinch");
    }

    @Test
    void noSecondStaggerTakesHimInsideHisImmunity() {
        var fight = fight();
        var rule = SETTINGS.stagger();
        assertTrue(rule.frames() >= 10 && rule.frames() <= 12, "a stagger of " + rule.frames() + " frames");
        assertTrue(rule.immuneFrames() >= 30 && rule.immuneFrames() <= 45, "immune " + rule.immuneFrames());

        fight.knight.getBody().damage(heavy());
        fight.run(rule.frames() + 5);
        assertFalse(fight.staggered());
        fight.knight.getBody().damage(heavy());
        assertFalse(fight.staggered(), "staggered again inside his immunity: a crowd could hold him");

        fight.run(rule.immuneFrames());
        fight.knight.getBody().damage(heavy());
        assertTrue(fight.staggered(), "his immunity never ended");
    }

    /** The window's flinch is the stagger: his Hurt clip, set for exactly its frames and gone as it ends. */
    @Test
    void hisHurtClipIsBusyForExactlyTheStaggersFrames() {
        var fight = fight();
        var hurt = SETTINGS.heroNamed(KNIGHT).hurt();
        fight.knight.getBody().damage(heavy());
        int frames = 0;
        for (int i = 0; i < 40; i++) {
            boolean staggered = fight.staggered();
            assertEquals(staggered ? hurt : null, fight.knight.getBusyClip(), "frame " + i + " of it");
            frames += staggered ? 1 : 0;
            if (i == 3) {
                fight.knight.getBody().damage(1f); // a blow inside it breaks off the busy clip; the flinch stays
            }
            fight.run(1);
        }
        assertEquals(SETTINGS.stagger().frames(), frames);
    }

    private static String room() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 30; x++) {
                text.append(x == 0 || y == 0 || x == 29 || y == 29 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject find(uz.dukeengine.game.DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template)).findFirst().orElseThrow();
    }
}
