package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Hero;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.module.StatusUpdate;

/**
 * A stun: the engine's own {@code DISABLED}, worn for as long as what stunned it says -- no step, no blow and no
 * cast while it lasts, and all three back once it is over.
 */
class StunTest {

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
}
