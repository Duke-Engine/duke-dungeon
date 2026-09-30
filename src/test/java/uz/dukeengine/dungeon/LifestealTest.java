package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.game.DukeGame;

/**
 * Every boss drinks from its blows: a quarter of what each deals comes back to it as health, never above its
 * maximum -- a swing where it lands, a shot where it arrives -- and nothing else down here does. It is a skill of
 * theirs, a {@code LIFESTEAL}, and one that is never cast.
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

    /**
     * The shipped units, with the Rogue's bow reaching nothing, the Necromancer mending nothing on its own, and the
     * fire mage throwing nothing but its fireball.
     */
    private static String units() {
        var rogue = ShippedBlock.of("Rogue");
        var necromancer = ShippedBlock.of("Necromancer");
        var mage = ShippedBlock.of("SkeletonMage");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text())
                .replace(necromancer.text(), necromancer.with("HealPerSecond", 0).text())
                .replace(mage.text(), mage.with("AttackRange", 0).text());
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

    /** What the hero lost and what the monster gained, over {@code frames} of the fight. */
    private static float[] lostAndGained(Duel duel, int frames) {
        float hero = duel.hero().getBody().getHealth();
        float monster = duel.monster().getBody().getHealth();
        duel.game().runHeadless(frames);
        return new float[] {hero - duel.hero().getBody().getHealth(), duel.monster().getBody().getHealth() - monster};
    }

    /** The bosses of the descent, one a floor. */
    private static TreeSet<String> bosses() {
        var bosses = new TreeSet<String>();
        for (int depth = 1; depth <= SETTINGS.finalDepth(); depth++) {
            bosses.add(SETTINGS.bossKindAt(depth));
        }
        return bosses;
    }

    /** What share of every blow each LIFESTEAL of {@code unit}'s drinks, as shipped. */
    private static List<Integer> sharesOf(String unit) {
        return SETTINGS.skillsFor(unit).stream().filter(skill -> skill.effect() == SkillEffect.LIFESTEAL)
                .map(Skill::boostPercent).toList();
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

    /**
     * And from each its burst catches: the fire mage given the skill, two Rogues side by side in its band, and
     * whatever its fireball took from the pair -- the one it struck and the one its burst caught -- it has a quarter
     * of back. Its ordinary fire reaches nothing here, so what they lose is the fireball's alone: its Swing stands
     * before its Bow, and a shot of that is heard as it leaves as well as where it lands -- see Swing.launch.
     */
    @Test
    void itsBurstDrinksFromEachItCatches() {
        var data = Content.data().replace("      Name = Olov shari\n    End\n", "      Name = Olov shari\n    End,\n"
                + "    Skill\n      Key = W\n      Effect = LIFESTEAL\n      BoostPercent = 25\n    End\n");
        assertNotEquals(Content.data(), data, "the premise: the fire mage was given the skill");
        var arena = Dungeon.world(room(), DungeonSettings.parse(data), units());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 250f, 145f);
        game.spawn("Rogue", arena.hero(), 250f, 155f);
        game.spawn("SkeletonMage", arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var heroes = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Rogue")).toList();
        var mage = creature(game, "SkeletonMage");
        mage.getBody().setHealth(1f);
        float before = heroes.get(0).getBody().getHealth() + heroes.get(1).getBody().getHealth();

        // Its fireball is the one thing it throws that stuns, so the frame both stand dazed is the frame it burst.
        boolean burst = false;
        for (int frame = 0; frame < 150 && !burst; frame++) {
            game.runHeadless(1);
            burst = heroes.stream().allMatch(hero -> hero.hasStatus(ObjectStatus.DISABLED));
        }

        assertTrue(burst, "the premise: in five seconds its fireball struck one and its burst caught the other");
        float lost = before - heroes.get(0).getBody().getHealth() - heroes.get(1).getBody().getHealth();
        float gained = mage.getBody().getHealth() - 1f;
        assertEquals(lost / 4f, gained, 0.001f, "they lost " + lost + " and it got back " + gained);
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

    /**
     * Never cast: its key does nothing and spends nothing, and a boss whose one skill it is has nothing its brain
     * casts -- it holds for as long as the boss stands.
     */
    @Test
    void itIsNeverCast() {
        var duel = duel("Warden", 20f, 1f);
        var book = duel.monster().findModule(SkillBook.class);
        char key = book.getSkills().getFirst().key();

        assertFalse(book.cast(key, 1, null, duel.hero().getPosition()), "it was cast");
        assertEquals(0, book.cooldownOf(key), "and the cast that never went off started its cooldown");
        for (var boss : bosses()) {
            assertFalse(SETTINGS.monster(boss).hasSkill(), boss + "'s brain has a skill to cast");
        }
    }

    /** As shipped: every boss of the descent drinks a quarter of every blow, by a skill of its own. */
    @Test
    void everyBossDrinksAQuarter() {
        assertTrue(SETTINGS.finalDepth() > 0, "the premise: the descent has its bosses");
        for (var boss : bosses()) {
            assertEquals(List.of(25), sharesOf(boss), boss + " does not drink a quarter");
        }
    }

    /** And nothing else down here drinks: every shipped unit with a LIFESTEAL is a boss, and every boss has one. */
    @Test
    void nothingButTheBossesDrinks() {
        var drinkers = new TreeSet<String>();
        for (var skill : SETTINGS.skills()) {
            if (skill.effect() == SkillEffect.LIFESTEAL) {
                drinkers.add(skill.heroTemplate());
            }
        }
        assertEquals(bosses(), drinkers);
    }

    /** A share is some of the blow and no more than all of it: anything else is refused at load. */
    @Test
    void aShareOutOfRangeIsRefused() {
        for (int share : new int[] {0, 101}) {
            var refused = assertThrows(IllegalArgumentException.class,
                    () -> DungeonSettings.parse(ShippedBlock.dataWith("Warden", "BoostPercent", share)));
            assertTrue(refused.getMessage().contains("BoostPercent"), refused.getMessage());
        }
    }

    /** And what only a cast would read is refused on it -- a Damage, say: it is never cast. */
    @Test
    void whatOnlyACastWouldReadIsRefusedOnIt() {
        var data = Content.data()
                .replace("      Effect = LIFESTEAL\n", "      Effect = LIFESTEAL\n      Damage = 10\n");
        assertNotEquals(Content.data(), data, "the premise: the drink was given a Damage");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("never cast"), refused.getMessage());
    }
}
