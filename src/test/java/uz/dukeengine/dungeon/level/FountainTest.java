package uz.dukeengine.dungeon.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.game.DukeGame;

/**
 * The fountain at the way in: every floor lets the heroes in round one, it mends whatever lives near it — the
 * dungeon's own as well — and it is walked round, not through.
 */
class FountainTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** An open room, so nothing here turns on where a seed put a wall. */
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

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /** How far apart across the floor, whatever the ground's height under either. */
    private static float apart(GameObject one, GameObject other) {
        return (float) Math.hypot(one.getPosition().x() - other.getPosition().x(),
                one.getPosition().y() - other.getPosition().y());
    }

    @Test
    void everyFloorLetsTheHeroesInRoundTheFountain() {
        for (long seed : new long[] {3L, 7L, 21L, 42L, 99L}) {
            var game = Dungeon.newSession(seed, SETTINGS).game();
            game.runHeadless(2);
            var fountain = find(game, "Fountain");
            assertNotNull(fountain, "seed " + seed + ": the way in has its fountain");
            float off = apart(find(game, "Rogue"), fountain);
            assertTrue(off >= 19f && off <= 40f, "seed " + seed + ": he stands beside it, not in it: " + off);
        }
    }

    /** Everything living near it is mended — a monster as well as a hero — and nothing further off. */
    @Test
    void itMendsWhoeverStandsNearItMonstersToo() {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn("Fountain", arena.dungeon(), 200f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 225f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 330f, 150f);
        game.runHeadless(1);
        var skeletons = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Skeleton"))
                .sorted(Comparator.comparing(object -> object.getPosition().x())).toList();
        var near = skeletons.getFirst().getBody();
        var far = skeletons.getLast().getBody();
        near.setHealth(near.getMaxHealth() / 2f);
        far.setHealth(far.getMaxHealth() / 2f);
        float nearWas = near.getHealth();
        float farWas = far.getHealth();

        game.runHeadless(60); // two of its pulses

        int pulse = Math.max(1, Math.round(near.getMaxHealth() * 5 / 100f));
        assertEquals(nearWas + 2 * pulse, near.getHealth(), 0.01f, "twice a twentieth of him back");
        assertEquals(farWas, far.getHealth(), 0.01f, "and nothing for one out of its reach");
    }

    @Test
    void itGivesAHeroHisManaBack() {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn("Fountain", arena.dungeon(), 200f, 150f);
        game.spawn("Rogue", arena.hero(), 230f, 150f);
        game.runHeadless(1);
        var book = find(game, "Rogue").findModule(SkillBook.class);
        book.poolOf(100, 0); // room for a hundred and none of it filled

        game.runHeadless(30);

        assertEquals(5, book.getMana(), "a twentieth of his pool back in a second");
    }

    /** Sent past it, he walks round the basin rather than through it. */
    @Test
    void itIsWalkedRoundNotThrough() {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn("Fountain", arena.dungeon(), 200f, 150f);
        game.spawn("Rogue", arena.hero(), 120f, 150f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var fountain = find(game, "Fountain");
        hero.getLocomotor().moveTo(new Coord3D(280f, 150f, 0f));

        float nearest = Float.MAX_VALUE;
        for (int frame = 0; frame < 300; frame++) {
            game.runHeadless(1);
            nearest = Math.min(nearest, apart(hero, fountain));
        }

        assertTrue(hero.getPosition().x() > 260f, "he got past it: " + hero.getPosition());
        assertTrue(nearest >= 7f, "and never stood in its water: " + nearest);
    }
}
