package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.EffectLayer;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Hero;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.module.StatusUpdate;

/**
 * A stun: the engine's own {@code DISABLED}, worn for as long as what stunned it says -- no step, no blow and no
 * cast while it lasts, and all three back once it is over.
 *
 * <p>The fire mage's fireball is the one thing that stuns, so it is fought for real: its own brain throws at a Rogue
 * standing in its band, told to pick no fights and with a bow that reaches nothing, so nothing he does ends the fight
 * before it has been had.
 */
class StunTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String FIRE_MAGE = "SkeletonMage";

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

    /** What a unit's block is built from, as the shipped files write it. */
    private static List<ModuleData> modulesOf(String unit) {
        for (var record : Content.records(Content.units(), "units")) {
            if (record instanceof Hero hero && hero.name().equals(unit)) {
                return hero.modules();
            }
            if (record instanceof Monster monster && monster.name().equals(unit)) {
                return monster.modules();
            }
        }
        throw new AssertionError("the shipped files have no unit called " + unit);
    }

    /** The shipped units with the Rogue's bow reaching nothing: whatever is hurt here, the fire mage hurt. */
    private static String unarmedRogue() {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text());
    }

    private record Fight(DukeGame game, List<GameObject> heroes) {
    }

    /** The fire mage at {@code (mageX, mageY)}, and a Rogue at each pair of {@code heroXy}, holding his ground. */
    private static Fight fireMage(DungeonSettings settings, float mageX, float mageY, float... heroXy) {
        var arena = Dungeon.world(room(), settings, unarmedRogue());
        var game = arena.game();
        for (int i = 0; i + 1 < heroXy.length; i += 2) {
            game.spawn("Rogue", arena.hero(), heroXy[i], heroXy[i + 1]);
        }
        game.spawn(FIRE_MAGE, arena.dungeon(), mageX, mageY);
        game.runHeadless(1);
        // A player has an index once the game has started, and not before.
        arena.orders().hold(arena.hero().getIndex(), true);
        var heroes = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Rogue")).toList();
        return new Fight(game, heroes);
    }

    /** Runs the fight a frame at a time until {@code hero} is stunned: whether he was, within {@code frames}. */
    private static boolean untilStunned(DukeGame game, GameObject hero, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            if (hero.hasStatus(ObjectStatus.DISABLED)) {
                return true;
            }
        }
        return false;
    }

    // ---- who can be stunned, and what it stops ----

    /** Every hero carries the timers a stun is counted down on, as every monster already does. */
    @Test
    void everyHeroCarriesTheTimersForWhatHeWears() {
        assertFalse(SETTINGS.heroes().isEmpty(), "the shipped files name no hero");
        for (var hero : SETTINGS.heroes()) {
            assertTrue(modulesOf(hero.name()).stream().anyMatch(StatusUpdate.Data.class::isInstance),
                    hero.name() + " carries no StatusUpdate, so nothing can stun him");
        }
    }

    /**
     * Stunned, he casts nothing and spends nothing -- the cooldown stands as it stood, as it does for a cast he
     * cannot pay for -- and the frame the stun is over, the same key goes off.
     */
    @Test
    void aStunnedHeroCastsNothingAndCastsAgainOnceItIsOver() {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Rogue");
        var book = hero.findModule(SkillBook.class);

        hero.findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 30);

        assertFalse(book.cast('W', 1), "he cast while stunned");
        assertTrue(book.isReady('W'), "and the cast that never went off spent its cooldown");
        game.runHeadless(30);
        assertFalse(hero.hasStatus(ObjectStatus.DISABLED), "thirty frames on, he is still stunned");
        assertTrue(book.cast('W', 1), "and the stun over, he still cannot cast");
    }

    // ---- the fire mage's fireball ----

    /** As shipped: its fireball stuns for a second. */
    @Test
    void theFireMagesFireballStunsForASecond() {
        assertEquals(30, SETTINGS.skillsFor(FIRE_MAGE).getFirst().stunFrames());
    }

    /**
     * What it strikes stands dazed for exactly its StunFrames: {@code DISABLED} on the frame it lands and for that many
     * frames in all, and free on the next.
     */
    @Test
    void itStunsWhatItStrikesForExactlyItsFrames() {
        var fight = fireMage(SETTINGS, 200f, 150f, 250f, 150f);
        var hero = fight.heroes().getFirst();
        int frames = SETTINGS.skillsFor(FIRE_MAGE).getFirst().stunFrames();

        assertTrue(untilStunned(fight.game(), hero, 150), "nothing it threw in five seconds stunned him");
        int stunned = 1;
        while (stunned <= frames) {
            fight.game().runHeadless(1);
            if (!hero.hasStatus(ObjectStatus.DISABLED)) {
                break;
            }
            stunned++;
        }
        assertEquals(frames, stunned, "stunned " + stunned + " frames against a StunFrames of " + frames);
    }

    /** And whoever its burst caught, on the same frame: two Rogues side by side, and both of them dazed. */
    @Test
    void andWhoeverItsBurstCaught() {
        var fight = fireMage(SETTINGS, 200f, 150f, 250f, 145f, 250f, 155f);

        assertTrue(untilStunned(fight.game(), fight.heroes().get(0), 150), "nothing it threw stunned either");
        assertTrue(fight.heroes().get(1).hasStatus(ObjectStatus.DISABLED),
                "the one beside him was not stunned the frame he was, so its burst stuns nobody");
    }

    /** The same fireball with no StunFrames hurts him, and leaves him free. */
    @Test
    void aSkillWithoutStunFramesLeavesItsVictimFree() {
        var data = Content.data().replace("      StunFrames = 30\n", "");
        assertNotEquals(Content.data(), data, "the premise: the fireball's StunFrames was taken out");
        var settings = DungeonSettings.parse(data);
        var fight = fireMage(settings, 200f, 150f, 250f, 150f);
        var hero = fight.heroes().getFirst();
        var fireball = settings.skillsFor(FIRE_MAGE).getFirst().projectile();

        boolean thrown = false;
        for (int frame = 0; frame < 150; frame++) {
            fight.game().runHeadless(1);
            thrown |= creature(fight.game(), fireball) != null;
            assertFalse(hero.hasStatus(ObjectStatus.DISABLED), "stunned, on frame " + frame);
        }
        assertTrue(thrown, "the premise: it threw its fireball at him");
        assertTrue(hero.getBody().getHealth() < hero.getBody().getMaxHealth(), "and he was hurt");
    }

    /** Only what a skillshot throws carries a stun: written on any other skill, the file is refused. */
    @Test
    void aStunOnAnythingButASkillshotIsRefused() {
        // The Mage's Frost Nova, which comes down on a spot rather than being thrown.
        var data = Content.data().replace("      SlowFrames = 90\n", "      SlowFrames = 90\n      StunFrames = 30\n");
        assertNotEquals(Content.data(), data, "the premise: the nova was given a stun");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("StunFrames"), refused.getMessage());
    }

    // ---- the stars ----

    /** Stars on whoever it stunned: the Combat block's look, played riding him on the frame it landed. */
    @Test
    void theStarsArePlayedOnWhoeverItStunned() {
        var fight = fireMage(SETTINGS, 200f, 150f, 250f, 150f);
        var hero = fight.heroes().getFirst();

        assertTrue(untilStunned(fight.game(), hero, 150), "nothing it threw stunned him");
        var played = fight.game().getSnapshot().events().stream()
                .filter(EffectPlayed.class::isInstance).map(EffectPlayed.class::cast).toList();
        assertTrue(played.stream().anyMatch(effect -> effect.name().equals(SETTINGS.combat().stunLook())
                        && hero.getId().equals(effect.riding())),
                "no " + SETTINGS.combat().stunLook() + " riding him the frame he was stunned: " + played);
    }

    /** They last as long as the longest stun any skill gives, and no layer of them says how long for itself. */
    @Test
    void theStarsLastAsLongAsTheLongestStun() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        int longest = SETTINGS.skills().stream().mapToInt(Skill::stunFrames).max().orElse(0);
        assertTrue(longest > 0, "the premise: something stuns");

        assertEquals(longest / (float) GameConstants.LOGICFRAMES_PER_SECOND,
                visuals.getEffectSeconds(SETTINGS.combat().stunLook()), 0.001f);
        var layers = SETTINGS.effectLayers().stream()
                .filter(art -> art.effect().equals(SETTINGS.combat().stunLook())).map(Main::layerOf).toList();
        assertFalse(layers.isEmpty(), "the look a stun is worn in is drawn by nothing");
        for (var layer : layers) {
            assertEquals(EffectLayer.AURA, layer.type(), "a stun is worn, and goes where he goes");
            assertEquals(0f, layer.seconds(), 0.001f, "a layer that says how long it lasts no longer follows the stun");
        }
    }
}
