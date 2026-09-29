package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.level.GrowableBody;

/**
 * The marks on a creature's bar, as the settings file draws them.
 *
 * <p>One mark is worth the same health on every creature alive, as a MOBA's bars are: so a bar's marks say how
 * much it has, a hero's bar fills with marks as he levels, and more health never wears fewer. The one thing that
 * can go wrong unseen is a lot so small that the widest bar the game makes packs its marks closer than they can be
 * told apart — which looks like a texture rather than like a mistake.
 *
 * <p>The rules themselves live on the client and are tested there — see
 * {@code uz.dukeengine.client3d.UnitBarLookTest}. This is about the file.
 */
class DungeonUnitBarTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /**
     * Closer together than this, in the bar's own pixels, and marks read as texture rather than as a count. The bar is
     * drawn at its design size or bigger on any window at least the design's width, so there these are at least as
     * many real pixels; on a narrower one the bar shrinks, and its marks close up with it.
     */
    private static final float CLOSEST = 4.5f;

    private static uz.dukeengine.client3d.UnitBarLook look() {
        return Main.unitBars(SETTINGS);
    }

    /**
     * What a template's body is worth before any depth has been applied to it.
     *
     * <p>★ IT IS A {@code GrowableBody}, NOT THE ENGINE'S OWN. Every creature in
     * this game has one, because every creature's maximum moves — the hero's with
     * his levels, a monster's with the depth it was spawned at — and the engine
     * fixes its own body's maximum when the unit is built. Matching only
     * {@link uz.dukeengine.core.module.ActiveBody.Data} therefore found nothing, in
     * silence, and the whole of what this test measured was the two fixture
     * creatures: seven sizes across 60 to 280 health, against a table built for
     * 30 to 1320. It passed. It was caught by printing what it had looked at.
     *
     * <p>The engine's body is still read, for anything that has one.
     */
    private static float baseHealth(ThingTemplate template) {
        if (template == null) {
            return 0f;
        }
        for (var module : template.modules()) {
            switch (module) {
                case GrowableBody.Data body -> {
                    return body.maxHealth();
                }
                case uz.dukeengine.core.module.ActiveBody.Data body -> {
                    return body.maxHealth();
                }
                default -> { }
            }
        }
        return 0f;
    }

    /** Every creature in the game, at every size the game can present it at. */
    private static List<float[]> everySizeTheGameMakes() {
        var game = Dungeon.create(1234L);
        game.runHeadless(1); // boots the world, which is what loads the real files
        var factory = game.getLogic().getThingFactory();
        var sizes = new ArrayList<float[]>();
        int floors = Math.max(1, SETTINGS.finalDepth());

        for (var kind : SETTINGS.monsters()) {
            float base = baseHealth(factory.findTemplate(kind.name()));
            assertTrue(base > 0f, kind.name() + " has no body in the shipped files");
            for (int depth = Math.max(1, kind.minDepth()); depth <= floors; depth++) {
                sizes.add(new float[] {base * SETTINGS.monsterHealthAt(depth), depth});
            }
        }
        // The boss of a floor climbs faster than its underlings, and is the one
        // creature the table is most likely to be short at the top for.
        for (int depth = 1; depth <= floors; depth++) {
            float base = baseHealth(factory.findTemplate(SETTINGS.bossKindAt(depth)));
            assertTrue(base > 0f, "the boss of depth " + depth + " has no body");
            sizes.add(new float[] {base * SETTINGS.bossHealthAt(depth), depth});
        }
        // And the hero, who is the bar the player looks at most: at the bottom
        // and at the top of what levelling can add to him.
        int top = SETTINGS.levelling().maxLevel();
        for (var hero : SETTINGS.heroes()) {
            var template = factory.findTemplate(hero.name());
            assertTrue(baseHealth(template) > 0f, hero.name() + " has no body");
            // His block is what his strength is added to, so the bar is sized by the
            // two together -- at his first level and at the last.
            for (int level : new int[] {1, top}) {
                sizes.add(new float[] {uz.dukeengine.dungeon.level.HeroFigures.of(
                        uz.dukeengine.dungeon.level.HeroBase.of(template), hero.maxMana(),
                        hero.attributes(), SETTINGS.attributeRules(), level,
                        uz.dukeengine.dungeon.level.HeroFigures.Found.NOTHING).maxHealth(), 1});
            }
        }
        return sizes;
    }

    /**
     * One lot for everyone: a mark on a skeleton is worth what a mark on the Champion is.
     *
     * <p>Asked of every creature the game makes — monsters at every depth they can be met, each floor's boss on its
     * own steeper curve, and both ends of what a hero grows into — and of the whole range past them, so a rung added
     * to the table later fails here before anybody has looked at a bar.
     */
    @Test
    void aMarkIsWorthTheSameOnEveryCreature() {
        var look = look();
        int lot = look.valueFor(1f);
        for (var size : everySizeTheGameMakes()) {
            assertEquals(lot, look.valueFor(size[0]), Math.round(size[0]) + " health at depth " + (int) size[1]
                    + " is marked in lots of " + look.valueFor(size[0]) + ", where everything else is " + lot);
        }
        for (int health = 1; health <= 20_000; health += 13) {
            assertEquals(lot, look.valueFor(health), health + " health is marked in other lots");
        }
    }

    /** So more health never wears fewer marks — which a ladder of lots did, at every rung a hero levelled across. */
    @Test
    void moreHealthNeverWearsFewerMarks() {
        var look = look();
        int before = 0;
        for (int health = 1; health <= 20_000; health++) {
            int marks = look.segmentsFor(health);
            assertTrue(marks >= before, health + " health wears " + marks + " marks, where " + (health - 1)
                    + " wore " + before);
            before = marks;
        }
    }

    /**
     * And every bar the game draws keeps its marks far enough apart to be marks: the widest creature there is, on the
     * longest bar there is, still wears a count rather than a texture.
     */
    @Test
    void everyCreatureThisGameMakesWearsMarksThatReadAsMarks() {
        var look = look();
        var wrong = new ArrayList<String>();
        for (var size : everySizeTheGameMakes()) {
            float health = size[0];
            if (health <= 0f) {
                continue; // no body: a prop, and it is drawn no bar
            }
            float apart = look.widthFor(health) / look.segmentsFor(health);
            if (apart < CLOSEST) {
                wrong.add(Math.round(health) + " health at depth " + (int) size[1] + " wears "
                        + look.segmentsFor(health) + " marks " + apart + " apart");
            }
        }
        if (!wrong.isEmpty()) {
            fail("these bars read as texture:\n  " + String.join("\n  ", wrong));
        }
    }

    /** The file says enough for the client to draw anything at all. */
    @Test
    void theFileSaysEnoughToDrawWith() {
        var look = look();

        assertTrue(look.draws(), "no table, no bars");
        assertTrue(look.hasMana(), "this game has mana, so a creature with any gets a bar");
        assertTrue(look.longest() > look.shortest(), "a boss's bar has to be the longer one");
        assertTrue(look.widthFor(20_000f) <= look.longest(),
                "nothing draws a bar past the longest");
    }
}
