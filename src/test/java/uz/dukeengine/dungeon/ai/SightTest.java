package uz.dukeengine.dungeon.ai;

import uz.dukeengine.rpg.SightLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.skill.CastSkill;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * What the hero shoots at, and what he stops doing when he is told something new.
 *
 * <p>Two rules, and both are about him doing what the player meant rather than
 * what the engine's defaults would have him do:
 *
 * <ul>
 *   <li>He shoots what he can see. The engine's fog is a circle and its target
 *       acquisition knows nothing about walls, so left alone he kills things
 *       through them.
 *   <li>The last order wins. Ordering an attack while he is walking stops the
 *       walk; casting a skill stops it too and abandons what he was sent at.
 * </ul>
 *
 * <p>Fought in an open arena rather than a generated floor so the distances are
 * the test's own — a generated dungeon puts its wall wherever it likes.
 */
class SightTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final int WIDE = 40;
    private static final int HIGH = 30;
    private static final float CELL = 10f;

    /**
     * A room, optionally divided by a wall with a doorway at the far end.
     *
     * <p>The doorway matters: a wall with no way round it is not a wall, it is two
     * rooms, and "he walks round to get a look at it" would have nowhere to go.
     * Nothing can be seen through it either way — the line these tests draw runs
     * across the middle, well away from the gap.
     */
    private static final int DOORWAY = HIGH - 2;

    private static String arena(int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < HIGH; y++) {
            for (int x = 0; x < WIDE; x++) {
                boolean edge = x == 0 || y == 0 || x == WIDE - 1 || y == HIGH - 1;
                text.append(edge || (x == wallColumn && y != DOORWAY) ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static final int NO_WALL = -1;

    /** The middle of a cell, which is where a creature is put. */
    private static float at(int cell) {
        return (cell + 0.5f) * CELL;
    }

    private record Arena(DukeGame game, GameObject hero, GameObject skeleton) {
    }

    private static Arena arena(int wallColumn, int heroCell, int skeletonCell) {
        return arena(Dungeon.world(arena(wallColumn), SETTINGS), heroCell, skeletonCell);
    }

    private static Arena arena(Dungeon.Arena world, int heroCell, int skeletonCell) {
        var game = world.game();
        game.spawn("Rogue", world.hero(), at(heroCell), at(15));
        game.spawn("Skeleton", world.dungeon(), at(skeletonCell), at(15));
        game.runHeadless(1);
        return new Arena(game, creature(game, "Rogue"), creature(game, "Skeleton"));
    }

    /** The shipped hero, re-tuned: his bow drawn further than his eyes reach. */
    private static String longBow() {
        var creatures = uz.dukeengine.dungeon.content.Content.units();
        assertTrue(creatures.contains("AttackRange = 60"), "the hero's bow should still be 60");
        return creatures.replace("AttackRange = 60", "AttackRange = 200");
    }

    private static Dungeon.Arena flatWorld(String creaturesIni) {
        return Dungeon.world(arena(NO_WALL), null, SETTINGS, creaturesIni,
                new uz.dukeengine.rpg.item.LootBag());
    }

    /**
     * The same room with its far half a storey up, and a flight of stairs at the
     * column where it rises — or a sheer edge, which is the case that matters.
     */
    private static String storeys(int raisedFrom, boolean withStair) {
        var text = new StringBuilder();
        for (int y = 0; y < HIGH; y++) {
            for (int x = 0; x < WIDE; x++) {
                boolean edge = x == 0 || y == 0 || x == WIDE - 1 || y == HIGH - 1;
                text.append(edge ? '#'
                        : withStair && x == raisedFrom - 1 ? '/'
                        : x >= raisedFrom ? '1' : '0');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static Arena raisedArena(int raisedFrom, boolean withStair, int heroCell,
            int skeletonCell) {
        return arena(Dungeon.world(arena(NO_WALL), storeys(raisedFrom, withStair), SETTINGS,
                uz.dukeengine.dungeon.content.Content.units(),
                new uz.dukeengine.rpg.item.LootBag()), heroCell, skeletonCell);
    }

    private static boolean sees(Arena arena) {
        return SightLine.sees(arena.hero(), arena.skeleton(), SETTINGS.storeyHeight());
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    // ---- the line itself ----

    @Test
    void aClearRoomIsClear() {
        var arena = arena(NO_WALL, 10, 15);
        assertTrue(SightLine.clear(arena.hero(), arena.skeleton()));
    }

    @Test
    void stoneBetweenThemIsNot() {
        var arena = arena(13, 10, 15);
        assertFalse(SightLine.clear(arena.hero(), arena.skeleton()));
    }

    /** Standing against a wall does not hide you: it is what is behind one that does. */
    @Test
    void aCreatureAgainstTheWallIsStillSeen() {
        var arena = arena(13, 10, 12);
        assertTrue(SightLine.clear(arena.hero(), arena.skeleton()),
                "the cell in front of the wall is in plain view");
    }

    @Test
    void sightIsTheSameFromEitherEnd() {
        var arena = arena(13, 10, 15);
        assertFalse(SightLine.clear(arena.skeleton(), arena.hero()));
    }

    // ---- and the two things besides stone that hide a creature ----

    /**
     * The dark hides as well as stone does.
     *
     * <p>His eyes reach 70 and this is 250 away down an empty room: on screen
     * there is nothing there at all, because the fog is drawn to the same number.
     */
    @Test
    void aCreatureBeyondHisEyesIsHiddenHoweverClearTheLineIs() {
        var arena = arena(NO_WALL, 5, 30);

        assertTrue(SightLine.clear(arena.hero(), arena.skeleton()),
                "there is nothing whatever in the way");
        assertFalse(sees(arena), "but it is standing in the dark, and the dark is where he stops");
    }

    /** And inside them it is not hidden, so the test above is about the distance. */
    @Test
    void aCreatureInsideThemIsSeen() {
        assertTrue(sees(arena(NO_WALL, 10, 15)));
    }

    /**
     * A floor above his own hides whoever is standing on it.
     *
     * <p>It is behind its own edge: from the corridor beneath you see the wall
     * holding it up and not the room on top, which is exactly what the client
     * draws. Nothing here knew that until floors had storeys in them, so he shot
     * at monsters the player was never shown.
     */
    @Test
    void aCreatureOnTheFloorAboveIsHiddenFromBelow() {
        var arena = raisedArena(12, false, 10, 14);

        assertTrue(SightLine.clear(arena.hero(), arena.skeleton()),
                "there is no stone between them; the floor is simply higher");
        assertFalse(sees(arena), "and you cannot see onto a floor above your own");
    }

    /** The same room without the step in it: the same two cells, in plain view. */
    @Test
    void andOnTheSameFloorItIsNotHidden() {
        assertTrue(sees(raisedArena(20, false, 10, 14)));
    }

    /**
     * A staircase is something you can see up.
     *
     * <p>A stair cell's floor climbs across it, so its middle stands half a storey
     * above the room it starts from. Rounded to the nearest that is the upper
     * storey and the stair hides itself — and with it whoever is halfway up. It
     * belongs to the floor it starts from, which is what the client says too.
     */
    @Test
    void aCreatureOnTheStairsIsSeenFromTheFootOfThem() {
        assertTrue(sees(raisedArena(12, true, 10, 11)),
                "he is looking straight up the steps at it");
    }
}
