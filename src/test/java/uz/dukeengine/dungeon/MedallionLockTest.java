package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.item.DropItem;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.item.ShiftItem;

/**
 * The owner after MSI 1.0.39: in the campaign the medallion cannot be removed -- not taken off, dropped or swapped out
 * for another thing of its slot -- and a click that tries says why. Outside the campaign it comes off as anything does.
 */
class MedallionLockTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String MEDALLION = SETTINGS.medallion().item();

    private static GameObject hero(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(o -> SETTINGS.heroes().stream().anyMatch(h -> h.name().equals(o.getTemplate().name())))
                .findFirst().orElseThrow();
    }

    private static LootBag bag(Dungeon.Session session) {
        return session.run().progressOf(hero(session.game()).getPlayerIndex()).getLoot();
    }

    private static int worn(LootBag bag) {
        return bag.wornPlace(SETTINGS.loot().stream().filter(i -> i.id().equals(MEDALLION)).findFirst().orElseThrow()
                .slot());
    }

    private static void order(Dungeon.Session session, uz.dukeengine.core.message.Command command) {
        session.game().postCommand(PartyOrders.of(command));
        session.game().runHeadless(3);
    }

    /** The campaign's third joint, the training: the medallion worn. */
    private static Dungeon.Session campaign() {
        var session = Dungeon.newSession(3L, SETTINGS);
        session.run().skipTo(3);
        session.game().runHeadless(3);
        var bag = bag(session);
        assertEquals(MEDALLION, bag.at(worn(bag)).id(), "the premise: he wears it");
        return session;
    }

    @Test
    void inTheCampaignItIsNotTakenOffAndHeSaysWhy() {
        var session = campaign();
        var bag = bag(session);
        int player = hero(session.game()).getPlayerIndex();

        order(session, new ShiftItem(player, worn(bag)));

        assertEquals(MEDALLION, bag.at(worn(bag)).id(), "taken off");
        assertEquals(SETTINGS.medallion().lockedWord(), bag.noteAt(session.game().getLogic().getFrame()));
    }

    @Test
    void inTheCampaignItIsNotDropped() {
        var session = campaign();
        var bag = bag(session);
        var hero = hero(session.game());

        order(session, new DropItem(hero.getPlayerIndex(), worn(bag), hero.getPosition()));
        session.game().runHeadless(30);

        assertEquals(MEDALLION, bag.at(worn(bag)).id(), "dropped");
    }

    @Test
    void inTheCampaignNothingElseIsPutOnInItsPlace() {
        var session = campaign();
        var bag = bag(session);
        var slot = bag.at(worn(bag)).slot();
        var other = SETTINGS.loot().stream().filter(i -> slot.equals(i.slot()) && !i.id().equals(MEDALLION))
                .findFirst().orElseThrow();
        assertTrue(bag.take(other, 0, 0), "the premise: another of its slot in the backpack");
        int place = -1;
        for (int i = 0; i < bag.backpackSize(); i++) {
            if (bag.at(i) == other) {
                place = i;
            }
        }

        order(session, new ShiftItem(hero(session.game()).getPlayerIndex(), place));

        assertEquals(MEDALLION, bag.at(worn(bag)).id(), "swapped out");
        assertEquals(other, bag.at(place), "and the other stays in the backpack");
    }

    /** Off the campaign -- the medallion's demo map -- it comes off as anything worn does. */
    @Test
    void offTheCampaignItComesOff() {
        var session = Dungeon.newStageSession(Stages.load("medalyon", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        assertTrue(!SETTINGS.inCampaign("medalyon"), "the premise: the demo map is no campaign's");
        var bag = bag(session);
        var medallion = SETTINGS.loot().stream().filter(i -> i.id().equals(MEDALLION)).findFirst().orElseThrow();
        bag.take(medallion, 0, 0);
        order(session, new ShiftItem(hero(session.game()).getPlayerIndex(), 0));
        assertEquals(MEDALLION, bag.at(worn(bag)).id(), "the premise: put on");

        order(session, new ShiftItem(hero(session.game()).getPlayerIndex(), worn(bag)));

        assertNotEquals(MEDALLION, bag.at(worn(bag)) == null ? "" : bag.at(worn(bag)).id(), "it came off");
    }
}
