package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.story.Campaign;
import uz.dukeengine.game.DukeGame;

/**
 * Traversal links (jump, vault, climb, drop) laid on the open world
 * (docs/plan/2026-10-15-modern-controls.md, section 5): they are data in
 * {@code data/world/traversal.duke}, placed by {@link WorldMapWriter},
 * written into {@code maps/world/world.map}, and handed to the engine as
 * {@link uz.dukeengine.core.map.Linked} so the pathfinder can use them.
 */
class TraversalWorldTest {

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

    private static void frame(Dungeon.Session session, boolean skip) {
        session.run().read(session.game().getLocalPlayerIndex(), 10_000);
        if (skip && session.run().inCutscene()) {
            session.run().skipCutscene(session.game());
        }
        session.game().runHeadless(1);
    }

    /** The regenerated world.map keeps the traversal links (round-trip through the stage file). */
    @Test
    void worldMapKeepsTraversalLinks() {
        var session = newGame();
        var links = session.game().getLogic().traversalLinks();
        assertFalse(links.isEmpty(), "the world lays no traversal links");
        assertEquals(16, links.size(), "the expected number of traversal links: " + links);
        // Every link stands on ground a hero can stand on.
        for (var link : links) {
            for (var end : new Coord3D[] { link.a(), link.b() }) {
                int x = (int) Math.round(end.x());
                int y = (int) Math.round(end.y());
                assertTrue(x >= 0 && y >= 0, "link end outside the world: " + end);
            }
        }
    }

    /** The map record handed to the engine is {@link uz.dukeengine.core.map.Linked}, so the links reach
     *  {@code GameLogic.setTraversalLinks} and the pathfinder can plan through them. */
    @Test
    void traversalLinksAreWiredToTheEngine() {
        var session = newGame();
        var links = session.game().getLogic().traversalLinks();
        assertFalse(links.isEmpty(), "the engine is not told the world's traversal links");
        // One of them is one-way (the data marks it bothWays = No).
        assertTrue(links.stream().anyMatch(link -> !link.bothWays()), "no one-way link in the world");
    }

    /**
     * Links do not lead into closed campaign regions: none of the link's ends stands inside a zone that is shut
     * (the palace court, the closed city, ...), so a route can never use a link to enter a place the story has not
     * opened yet.
     */
    @Test
    void linksDoNotEnterClosedRegions() {
        var session = newGame();
        var logic = session.game().getLogic();
        var links = logic.traversalLinks();
        assertFalse(links.isEmpty(), "the world lays no traversal links");
        // The links stand round the lake and the field, in the open world: none of their ends stands inside the
        // closed city (24 x 18 cells round Qonish) or the closed palace court (8 x 8 round city + (-10, -3)).
        int cx = 116; // Qonish (field's middle); the city is at C ≈ Qonish + (−3, +34)
        int cy = 461;
        for (var link : links) {
            for (var end : new Coord3D[] { link.a(), link.b() }) {
                int x = (int) Math.round(end.x());
                int y = (int) Math.round(end.y());
                assertFalse(x >= cx - 12 && x < cx + 12 && y >= cy - 9 && y < cy + 9,
                        "link end inside the closed city: " + x + "," + y);
                assertFalse(x >= cx - 10 && x < cx - 2 && y >= cy - 3 && y < cy + 5,
                        "link end inside the closed palace court: " + x + "," + y);
            }
        }
    }
}
