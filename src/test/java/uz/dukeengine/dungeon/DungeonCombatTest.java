package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.combat.message.CombatOrder;

/**
 * Combat as the player meets it: what he clicks on, what comes for him unbidden,
 * and what the skeletons do about it.
 *
 * <p>Fought in a purpose-built open room rather than a generated dungeon, so the
 * distances are the test's own and nothing depends on where a seed happened to put
 * a wall. The creatures and behaviour are the game's, through the same seam the
 * real dungeon is built on — a test that wired up its own would be proving
 * something about a world nobody plays.
 *
 * <p>Nothing here asserts a balance figure. Where a distance has to be named it is
 * taken from the game's own settings, so re-tuning the game moves the test with it
 * instead of breaking it.
 */
class DungeonCombatTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** A big empty room: 40×30 cells of floor inside a stone border. */
    private static final String ARENA = arena();

    private static String arena() {
        int width = 40;
        int height = 30;
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record Fight(DukeGame game, GameObject hero, GameObject skeleton) {
    }

    /** A hero and one skeleton, placed exactly where the test wants them. */
    private static Fight fight(float heroX, float heroY, float skeletonX, float skeletonY) {
        return fight(heroX, heroY, skeletonX, skeletonY, "Skeleton");
    }

    /** The same, against a named kind — for a fight the hero must not end at once. */
    private static Fight fight(float heroX, float heroY, float foeX, float foeY, String kind) {
        var world = Dungeon.world(ARENA, SETTINGS);
        var game = world.game();
        game.spawn("Rogue", world.hero(), heroX, heroY);
        game.spawn(kind, world.dungeon(), foeX, foeY);
        game.runHeadless(1);
        return new Fight(game, creature(game, "Rogue"), creature(game, kind));
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(template))
                .findFirst()
                .orElse(null);
    }

    /**
     * The skeletons are no longer furniture: one that notices the hero comes to
     * him. The hero is given no orders at all, so any closing is the skeleton's
     * own doing.
     */
    @Test
    void aSkeletonThatSensesTheHeroAdvancesOnHim() {
        float gap = SETTINGS.combat().skeletonSenseRadius() * 0.7f; // comfortably within notice
        var fight = fight(150f, 150f, 150f + gap, 150f);
        float before = fight.hero().getPosition().distance(fight.skeleton().getPosition());

        fight.game().runHeadless(90);

        float after = fight.hero().getPosition().distance(fight.skeleton().getPosition());
        assertTrue(after < before,
                "the skeleton should have closed on the hero, but went from " + before + " to " + after);
    }

    /** A skeleton far beyond its notice keeps to itself — aggro spreads room by room. */
    @Test
    void aSkeletonOutOfRangeStaysPut() {
        float gap = SETTINGS.combat().skeletonChaseRadius() * 2f; // well outside anything it reacts to
        var fight = fight(60f, 150f, 60f + gap, 150f);
        var startedAt = fight.skeleton().getPosition();

        fight.game().runHeadless(90);

        assertTrue(fight.skeleton().getPosition().distance(startedAt) < 1f,
                "a skeleton that cannot sense the hero should not have moved");
    }

    /**
     * Sent back the way he came, the hero turns round rather than driving a
     * circle. {@code TurnRate} is SAGE's vehicle parameter, and with one set the
     * hero banked like a tank; a man pivots.
     *
     * <p>Checked by the shape of the path, not by any number in the data: an arc
     * swings wide of the line it is travelling along, so if he never leaves that
     * line he never arced.
     */
    @Test
    void reversingDirectionTurnsOnTheSpotInsteadOfDrivingACircle() {
        var world = Dungeon.world(ARENA, SETTINGS);
        var game = world.game();
        game.spawn("Rogue", world.hero(), 120f, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Rogue");
        int player = game.getLocalPlayerIndex();

        // Get him walking east, so he has a heading to reverse.
        game.postCommand(new CombatOrder.MoveTo(player, List.of(hero.getId()),
                new Coord3D(260f, 150f, 0f)));
        game.runHeadless(40);
        float turnedAtX = hero.getPosition().x();

        // Now straight back the way he came: a 180° reversal.
        game.postCommand(new CombatOrder.MoveTo(player, List.of(hero.getId()),
                new Coord3D(120f, 150f, 0f)));

        float widestSwing = 0f;
        float furthestEast = turnedAtX;
        for (int frame = 0; frame < 120; frame++) {
            game.runHeadless(1);
            widestSwing = Math.max(widestSwing, Math.abs(hero.getPosition().y() - 150f));
            furthestEast = Math.max(furthestEast, hero.getPosition().x());
        }

        assertTrue(widestSwing < 5f,
                "turning round should not swing him off the line, but he strayed "
                        + widestSwing + " units sideways");
        assertTrue(furthestEast - turnedAtX < 10f,
                "nor should he coast onward while turning, but he ran "
                        + (furthestEast - turnedAtX) + " units past the turn");
        assertTrue(hero.getPosition().x() < turnedAtX,
                "and he should be heading back west");
    }

    /** And so does a skeleton: it looks at the hero it is hitting. */
    @Test
    void aSkeletonTurnsToFaceTheHero() {
        var fight = fight(200f, 150f, 182f, 150f, "Champion");
        var skeleton = fight.skeleton();
        skeleton.setOrientation((float) StrictMath.PI); // looking away from the hero

        fight.game().runHeadless(30);

        assertTrue(offBy(skeleton, fight.hero()) < 0.2f,
                "the skeleton should be looking at what it is hitting");
    }

    /** How far {@code fighter}'s heading is from pointing at {@code target}, in radians. */
    private static float offBy(GameObject fighter, GameObject target) {
        float wanted = (float) StrictMath.atan2(
                target.getPosition().y() - fighter.getPosition().y(),
                target.getPosition().x() - fighter.getPosition().x());
        float difference = fighter.getOrientation() - wanted;
        // Shortest way round, so 359° counts as 1° rather than as nearly a full turn.
        return (float) Math.abs(StrictMath.atan2(
                StrictMath.sin(difference), StrictMath.cos(difference)));
    }

    /** Skeletons fight back once they arrive — the advance is not a harmless parade. */
    @Test
    void anAdvancingSkeletonDrawsBlood() {
        float gap = SETTINGS.combat().skeletonSenseRadius() * 0.7f;
        var fight = fight(150f, 150f, 150f + gap, 150f);
        var hero = fight.hero();
        float before = hero.getBody().getHealth();

        fight.game().runHeadless(400);

        assertNotNull(fight.game().getLogic().findObject(hero.getId()));
        assertTrue(hero.getBody().getHealth() < before,
                "a skeleton that walked over should have hit the hero");
    }
}
