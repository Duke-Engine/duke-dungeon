package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/**
 * A single-hero game: the bar is always the hero's, and what he picks out to look at goes to the target frame.
 *
 * <p>Two halves that cannot see each other: the client knows <em>which</em> creature, because selection is a fact
 * about a screen, and the simulation knows <em>what it is worth</em>, because damage is a template times whatever its
 * level multiplies by. They meet over one command — see {@link Watching} — and this holds the meeting still.
 */
class WatchingTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private record Watched(DukeGame game, GameObject hero, GameObject skeleton, GameObject another,
            uz.dukeengine.dungeon.ai.Orders orders, DungeonRun run) {

        String line() {
            game.runHeadless(1);
            return game.getSnapshot().status();
        }

        Target target() {
            game.runHeadless(1);
            return run.getTarget();
        }

        void pickOut(GameObject creature) {
            orders.watch(hero.getPlayerIndex(), creature == null ? null : creature.getId());
        }
    }

    /** A real dungeon, with the hero and the first two creatures in it that are not his and carry a weapon. */
    private static Watched standoff() {
        var session = Dungeon.newSession(7L, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Rogue"))
                .findFirst().orElseThrow();
        var armed = game.getLogic().getObjects().stream()
                .filter(o -> o.getPlayerIndex() != hero.getPlayerIndex())
                .filter(o -> o.getBody() != null && !o.isEffectivelyDead())
                .filter(o -> o.findModule(uz.dukeengine.combat.module.WeaponUpdate.class) != null)
                .limit(2).toList();
        return new Watched(game, hero, armed.get(0), armed.get(1), session.orders(), session.run());
    }

    /** With nothing picked out the bar is his, entire, and there is no target frame. */
    @Test
    void withNothingPickedOutTheBarIsHis() {
        var watched = standoff();

        var line = watched.line();

        assertTrue(line.startsWith("name=Erika"), line);
        assertTrue(line.contains("|skill="), "his own card carries his skills: " + line);
        assertNull(watched.run().getTarget(), "nothing picked, no frame");
    }

    /** Picking a skeleton leaves the bar his and puts the skeleton in the target frame. */
    @Test
    void pickingOutASkeletonLeavesTheBarHisAndFramesTheSkeleton() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());

        var line = watched.line();
        var target = watched.run().getTarget();

        assertTrue(line.startsWith("name=Erika"), "the bar never stops being about him: " + line);
        assertTrue(line.contains("|skill=") && line.contains("|xp="), "his skills and his experience: " + line);
        assertNotNull(target, "the skeleton is framed");
        assertEquals(watched.skeleton().getId().value(), target.id());
        assertEquals(uz.dukeengine.core.thing.Titled.of(watched.skeleton().getTemplate()), target.name());
        assertEquals(Math.round(watched.skeleton().getBody().getMaxHealth()), target.maxHealth());
        assertTrue(target.health() > 0, "how much of it there is: " + target);
        assertTrue(target.attack() > 0, "what it hits for: " + target);
        assertTrue(target.level() >= 1, "its level: " + target);
        assertEquals("", target.role(), "a monster has no role");
        assertEquals(0, target.armour(), "a monster shrugs nothing off");
    }

    /** Picking himself out is no target at all: his own panel already says it all. */
    @Test
    void pickingHimOutFramesNothing() {
        var watched = standoff();
        watched.pickOut(watched.hero());

        assertNull(watched.target());
        assertTrue(watched.line().startsWith("name=Erika"));
    }

    /** The frame follows the pick, and its health follows the creature. */
    @Test
    void theFrameFollowsThePick() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());
        assertEquals(watched.skeleton().getId().value(), watched.target().id());

        watched.pickOut(watched.another());
        assertEquals(watched.another().getId().value(), watched.target().id(), "it follows a new pick");

        int before = watched.target().health();
        watched.another().getBody().damage(1f, uz.dukeengine.core.module.DamageType.EXPLOSION);
        assertTrue(watched.target().health() < before, "and what is left of it");
    }

    /** Letting go puts the frame away. */
    @Test
    void lettingGoPutsTheFrameAway() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());
        assertNotNull(watched.target());

        watched.pickOut(null);

        assertNull(watched.target());
    }

    /** And a creature that dies takes its frame with it rather than leaving a corpse in it. */
    @Test
    void aDeadCreatureClearsTheFrame() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());
        assertNotNull(watched.target());

        watched.skeleton().getBody().damage(100000f, uz.dukeengine.core.module.DamageType.EXPLOSION);

        assertNull(watched.target(), "a frame describing a corpse looks broken");
    }

    /**
     * His orders always go to his own hero: with a skeleton picked out, the buttons and their keys are his to press
     * -- the client sends them to the hero the bars name -- and the bars name him.
     */
    @Test
    void withASkeletonPickedOutTheOrdersAreStillHis() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());

        var line = watched.line();

        assertTrue(line.contains("|cmds=mine"), "his orders go to his hero whatever he looks at: " + line);
        assertTrue(line.contains("|hero=" + watched.hero().getId().value() + ","),
                "and the bars name the hero they go to: " + line);
    }

    /**
     * With no hero of his standing there is nobody to order, and the buttons go dim: a party's player whose hero is
     * down, while the others' still stand. Asked of the card itself.
     */
    @Test
    void withNoHeroStandingTheOrdersAreNobodys() {
        var nothing = HeroStatus.nothing(1, 4, SETTINGS, "", "", false);

        assertTrue(nothing.contains("|cmds=theirs"), "nobody to order: " + nothing);
    }
}
