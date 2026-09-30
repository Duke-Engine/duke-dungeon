package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.combat.Lifesteal;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.game.DukeGame;

/**
 * Every boss drinks from its blows: a quarter of what each deals comes back to it as health, never above its
 * maximum -- a swing where it lands, a shot where it arrives -- and nothing else down here does.
 *
 * <p>Fought for real, against a Rogue told to pick no fights and with a bow that reaches nothing, so what the boss
 * gains is only ever what it drank; the Necromancer's own mending is stilled for the same reason.
 */
class LifestealTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

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

    /** The shipped units, with the Rogue's bow reaching nothing and the Necromancer mending nothing on its own. */
    private static String units() {
        var rogue = ShippedBlock.of("Rogue");
        var necromancer = ShippedBlock.of("Necromancer");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text())
                .replace(necromancer.text(), necromancer.with("HealPerSecond", 0).text());
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private record Duel(DukeGame game, GameObject hero, GameObject monster) {
    }

    /**
     * The Rogue, and {@code monster} {@code gap} further along the row, inside its own reach -- left with
     * {@code share} of its health, so what it drinks has room to show.
     */
    private static Duel duel(String monster, float gap, float share) {
        var arena = Dungeon.world(room(), SETTINGS, units());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(monster, arena.dungeon(), 150f + gap, 150f);
        game.runHeadless(1);
        // A player has an index once the game has started, and not before.
        arena.orders().hold(arena.hero().getIndex(), true);
        var it = creature(game, monster);
        it.getBody().setHealth(it.getBody().getMaxHealth() * share);
        return new Duel(game, creature(game, "Rogue"), it);
    }

    /** What a unit's block is built from, as the shipped files write it. */
    private static List<ModuleData> modulesOf(String unit) {
        for (var record : Content.records(Content.units(), "units")) {
            if (record instanceof Monster monster && monster.name().equals(unit)) {
                return monster.modules();
            }
        }
        throw new AssertionError("the shipped files have no monster called " + unit);
    }

    /** What the hero lost and what the monster gained, over {@code frames} of the fight. */
    private static float[] lostAndGained(Duel duel, int frames) {
        float hero = duel.hero().getBody().getHealth();
        float monster = duel.monster().getBody().getHealth();
        duel.game().runHeadless(frames);
        return new float[] {hero - duel.hero().getBody().getHealth(), duel.monster().getBody().getHealth() - monster};
    }

    /** The Warden's swing: whatever it took from him, it has a quarter of back. */
    @Test
    void aBossGetsBackAQuarterOfWhatItsBlowsDeal() {
        var change = lostAndGained(duel("Warden", 20f, 0.5f), 90);

        assertTrue(change[0] > 0f, "the premise: in three seconds at arm's length it struck him");
        assertEquals(change[0] / 4f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** The Necromancer's shot does the same, where it arrives. */
    @Test
    void itsShotDoesTheSame() {
        var change = lostAndGained(duel("Necromancer", 45f, 0.5f), 150);

        assertTrue(change[0] > 0f, "the premise: in five seconds in its reach, something it threw reached him");
        assertEquals(change[0] / 4f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** Never above its maximum: whole, it stays whole, and no more. */
    @Test
    void neverAboveItsMaximum() {
        var duel = duel("Warden", 20f, 1f);
        var change = lostAndGained(duel, 90);

        assertTrue(change[0] > 0f, "the premise: it struck him");
        assertEquals(duel.monster().getBody().getMaxHealth(), duel.monster().getBody().getHealth(), 0.001f);
    }

    /** A creature without it gets nothing back, however hard it hits. */
    @Test
    void aCreatureWithoutItGetsNothing() {
        var change = lostAndGained(duel("Skeleton", 12f, 0.5f), 90);

        assertTrue(change[0] > 0f, "the premise: it struck him");
        assertEquals(0f, change[1], 0.001f, "a skeleton drank from its blows");
    }

    /** As shipped: every boss of the descent drinks a quarter of every blow. */
    @Test
    void everyBossDrinksAQuarter() {
        assertTrue(SETTINGS.finalDepth() > 0, "the premise: the descent has its bosses");
        for (int depth = 1; depth <= SETTINGS.finalDepth(); depth++) {
            var boss = SETTINGS.bossKindAt(depth);
            assertTrue(modulesOf(boss).contains(new Lifesteal.Data(25)), boss + " does not drink a quarter");
        }
    }
}
