package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.ai.FightOnlyWhenTold;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.game.DukeGame;

/**
 * The modern way, in the simulation: a hero whose player sent {@link FightOnlyWhenTold} picks no fight of his own --
 * neither his brain nor his idle weapon acquires an enemy -- and fights what he is given; the classic hero fights what
 * walks into his reach, as ever. And an item's "+1 to a skill" names the letter pressed.
 */
class ModernFightingTest {

    private static final DungeonSettings SETTINGS = ModernControlsTest.classic();

    private static String arena() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                text.append(x == 0 || y == 0 || x == 39 || y == 29 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record Field(DukeGame game, GameObject hero, List<GameObject> skeletons) {
    }

    /** The rogue at 150 with skeletons at the given x, inside his bow's 60; modern if his player said so. */
    private static Field field(boolean modern, float... skeletonsAt) {
        var arena = Dungeon.world(arena(), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        for (float x : skeletonsAt) {
            game.spawn("Skeleton", arena.dungeon(), x, 150f);
        }
        game.runHeadless(1);
        var all = game.getLogic().getObjects();
        var hero = all.stream().filter(o -> o.getTemplate().name().equals("Rogue")).findFirst().orElseThrow();
        if (modern) {
            // A bare arena obeys no orders, so what the match does on FightOnlyWhenTold is done here: the flag, and
            // whatever he picked in the frame before it let go.
            arena.orders().fightOnlyWhenTold(arena.hero().getIndex(), true);
            hero.findModule(WeaponUpdate.class).holdFire();
        }
        var skeletons = all.stream().filter(o -> o.getTemplate().name().equals("Skeleton")).toList();
        assertTrue(hero.anyModule(m -> m instanceof uz.dukeengine.dungeon.ai.HeroBrain.Module), "his brain holds");
        return new Field(game, hero, skeletons);
    }

    private static boolean hurt(GameObject thing) {
        return thing.isEffectivelyDead() || thing.getBody().getHealth() < thing.getBody().getMaxHealth();
    }

    /** The order travels: on the wire and back, and a played match obeys it on every machine. */
    @Test
    void theOrderIsHeardByTheMatch() {
        var told = new FightOnlyWhenTold(0, true);
        assertEquals(told, PartyOrders.commandOf(PartyOrders.of(told)));
        var session = Dungeon.newSession(7L, DungeonSettings.load());
        var game = session.game();
        game.runHeadless(2);
        game.postCommand(PartyOrders.of(new FightOnlyWhenTold(game.getLocalPlayerIndex(), true)));
        game.runHeadless(3);
        assertTrue(session.orders().fightsOnlyWhenTold(game.getLocalPlayerIndex()));
    }

    /** The client says it as his hero is chosen -- on the menu, before the match runs -- in modern and action only. */
    @Test
    void choosingHisHeroSaysItInModernOnly() {
        for (var scheme : uz.dukeengine.dungeon.world.Controls.Scheme.values()) {
            var settings = DungeonSettings.load();
            settings.useScheme(scheme);
            var session = Dungeon.newSession(7L, settings);
            Main.heroChosen(session, settings, Main.looks(settings), Main.controls(settings),
                    settings.run().defaultHero());
            session.game().runHeadless(4);
            // Action plays as modern here: he fights only when told (docs/plan/2026-10-15-action-controls.md).
            assertEquals(uz.dukeengine.dungeon.world.Controls.walksOnKeys(scheme),
                    session.orders().fightsOnlyWhenTold(session.game().getLocalPlayerIndex()), scheme.name());
        }
    }

    @Test
    void theClassicHeroShootsWhatComesIntoHisReach() {
        var field = field(false, 190f);
        field.game().runHeadless(150);
        assertTrue(hurt(field.skeletons().getFirst()), "classic: he fights what walks into his reach");
    }

    @Test
    void theModernHeroPicksNoFightOfHisOwn() {
        var field = field(true, 190f);
        field.game().runHeadless(150);
        assertNull(field.hero().findModule(WeaponUpdate.class).getTarget(), "nothing acquired, by brain or weapon");
        assertTrue(!hurt(field.skeletons().getFirst()), "modern: nobody told him to");
    }

    /** A lock-on fights until its target dies, then he stands: no hunting the next one. */
    @Test
    void aLockOnEndsWithItsTargetAndHeHuntsNoOther() {
        var field = field(true, 175f, 205f);
        var first = field.skeletons().getFirst();
        var second = field.skeletons().get(1);
        field.game().postCommand(new CombatOrder.AttackObject(field.game().getLocalPlayerIndex(),
                List.of(field.hero().getId()), first.getId()));
        for (int frame = 0; frame < 900 && !first.isEffectivelyDead(); frame++) {
            field.game().runHeadless(1);
        }
        assertTrue(first.isEffectivelyDead(), "the lock-on killed it");
        float health = second.getBody().getHealth();
        field.game().runHeadless(120);
        assertEquals(health, second.getBody().getHealth(), "the next one is not his business");
        assertNull(field.hero().findModule(WeaponUpdate.class).getTarget());
    }

    /** The card names the key pressed: the W skill's rank is "E mahorati" in the modern scheme, "W" in the classic. */
    @Test
    void anItemsSkillRankNamesTheLetterPressed() {
        var modern = DungeonSettings.load();
        assertEquals(uz.dukeengine.dungeon.world.Controls.Scheme.MODERN, modern.scheme());
        assertEquals("+1 E mahorati", modern.items().say("SKILL_RANK:W", "", 1));
        assertEquals("+1 F mahorati", modern.items().say("SKILL_RANK:R", "", 1));
        assertEquals("+1 W mahorati", ModernControlsTest.classic().items().say("SKILL_RANK:W", "", 1));
    }
}
