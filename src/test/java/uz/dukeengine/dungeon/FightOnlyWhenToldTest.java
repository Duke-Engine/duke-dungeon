package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.ai.FightOnlyWhenTold;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.game.DukeGame;

/**
 * The owner's choice: his hero picks no fight of his own -- neither his brain nor his idle weapon acquires an enemy --
 * and fights what he is told to: an attack order (until its target dies) or an attack-move. And an item's "+1 to a
 * skill" names the skill's own key.
 */
class FightOnlyWhenToldTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

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

    private record Field(DukeGame game, Dungeon.Arena arena, GameObject hero, List<GameObject> skeletons) {
    }

    /** The rogue at 150 with skeletons at the given x, inside his bow's 60, told to fight only when told. */
    private static Field field(float... skeletonsAt) {
        var arena = Dungeon.world(arena(), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        for (float x : skeletonsAt) {
            game.spawn("Skeleton", arena.dungeon(), x, 150f);
        }
        game.runHeadless(1);
        var all = game.getLogic().getObjects();
        var hero = all.stream().filter(o -> o.getTemplate().name().equals("Rogue")).findFirst().orElseThrow();
        // A bare arena obeys no orders, so what the match does on FightOnlyWhenTold is done here: the flag, and
        // whatever he picked in the frame before it let go.
        arena.orders().fightOnlyWhenTold(arena.hero().getIndex(), true);
        hero.findModule(WeaponUpdate.class).holdFire();
        var skeletons = all.stream().filter(o -> o.getTemplate().name().equals("Skeleton")).toList();
        game.runHeadless(1);
        assertTrue(hero.findModule(WeaponUpdate.class).isAcquiringNothing(), "his weapon picks nothing by itself (E87)");
        return new Field(game, arena, hero, skeletons);
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

    /** The client says it as his hero is chosen -- on the menu, before the match runs. */
    @Test
    void choosingHisHeroSaysIt() {
        var settings = DungeonSettings.load();
        var session = Dungeon.newSession(7L, settings);
        Main.heroChosen(session, settings, Main.looks(settings), Main.controls(settings), settings.run().defaultHero());
        session.game().runHeadless(4);
        assertTrue(session.orders().fightsOnlyWhenTold(session.game().getLocalPlayerIndex()));
    }

    @Test
    void hePicksNoFightOfHisOwn() {
        var field = field(190f);
        field.game().runHeadless(150);
        assertNull(field.hero().findModule(WeaponUpdate.class).getTarget(), "nothing acquired, by brain or weapon");
        assertTrue(!hurt(field.skeletons().getFirst()), "nobody told him to");
    }

    /** An attack order fights until its target dies, then he stands: no hunting the next one. */
    @Test
    void anAttackOrderEndsWithItsTargetAndHeHuntsNoOther() {
        var field = field(175f, 205f);
        var first = field.skeletons().getFirst();
        var second = field.skeletons().get(1);
        field.game().postCommand(new CombatOrder.AttackObject(field.game().getLocalPlayerIndex(),
                List.of(field.hero().getId()), first.getId()));
        for (int frame = 0; frame < 900 && !first.isEffectivelyDead(); frame++) {
            field.game().runHeadless(1);
        }
        assertTrue(first.isEffectivelyDead(), "the attack order killed it");
        float health = second.getBody().getHealth();
        field.game().runHeadless(120);
        assertEquals(health, second.getBody().getHealth(), "the next one is not his business");
        assertNull(field.hero().findModule(WeaponUpdate.class).getTarget());
    }

    /** An attack-move is telling him: he fights what he meets on the way. */
    @Test
    void anAttackMoveFightsWhatItMeets() {
        var field = field(230f);
        field.arena().orders().attackMove(field.arena().hero().getIndex(), new Coord3D(300f, 150f, 0f));
        field.game().runHeadless(400);
        assertTrue(hurt(field.skeletons().getFirst()), "the skeleton in his path was fought");
    }

    /** The card names the skill's own key: Q W E R, as the bar and the keyboard do. */
    @Test
    void anItemsSkillRankNamesItsKey() {
        assertEquals("+1 W mahorati", SETTINGS.items().say("SKILL_RANK:W", "", 1));
        assertEquals("+1 R mahorati", SETTINGS.items().say("SKILL_RANK:R", "", 1));
    }
}
