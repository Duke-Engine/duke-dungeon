package uz.dukeengine.dungeon.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.ai.AttackMove;
import uz.dukeengine.dungeon.ai.HoldGround;
import uz.dukeengine.dungeon.loot.DropItem;
import uz.dukeengine.dungeon.loot.PickUp;
import uz.dukeengine.dungeon.loot.UseItem;
import uz.dukeengine.dungeon.run.ToTheGate;
import uz.dukeengine.dungeon.run.Watching;
import uz.dukeengine.dungeon.skill.CastSkill;
import uz.dukeengine.dungeon.skill.UpgradeSkill;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.rts.network.CommandCodec;

/** Every order a player gives crosses the wire and comes out the order it went in as. */
class PartyOrdersTest {

    private static final List<Command> EVERY_ORDER = List.of(
            new CastSkill(2, 'Q', new ObjectId(41), null),
            new CastSkill(3, 'W', null, new Coord3D(123.456f, 78.9f, 0.1f)),
            new CastSkill(1, 'E'),
            new UpgradeSkill(4, 'R'),
            new HoldGround(2, true),
            new HoldGround(2, false),
            new AttackMove(3, new Coord3D(10.5f, 20.25f, 3f)),
            new Watching(1, new ObjectId(7)),
            new Watching(1, null),
            new ChooseHero(2, "Knight"),
            new PickUp(3, new ObjectId(88)),
            new DropItem(2, 5, new Coord3D(31.75f, 402.5f, 0f)),
            new ToTheGate(1, new ObjectId(64)),
            new UseItem(2, 3, new ObjectId(64)));

    @Test
    void anOrderIsTheCommandItCarries() {
        for (var command : EVERY_ORDER) {
            assertEquals(command, PartyOrders.commandOf(PartyOrders.of(command)));
        }
    }

    /** Through the engine's own wire format, as a party's orders travel: the same bits out as in. */
    @Test
    void anOrderSurvivesTheWire() {
        var packet = new CommandPacket(12, 2, EVERY_ORDER.stream().<Command>map(PartyOrders::of).toList());
        var back = CommandCodec.INSTANCE.decode(CommandCodec.INSTANCE.encode(packet));

        assertEquals(EVERY_ORDER, back.commands().stream()
                .map(order -> PartyOrders.commandOf((GameOrder) order)).toList());
    }

    /** An order this game never sends is data from somebody else's build, and is not obeyed. */
    @Test
    void anOrderThisGameNeverSendsMeansNothing() {
        assertNull(PartyOrders.commandOf(new GameOrder(2, "sell", List.of(), null, null, 0)));
        assertNull(PartyOrders.commandOf(new GameOrder(2, PartyOrders.MARCH, List.of(), null, null, 0)),
                "a march to nowhere");
        assertNull(PartyOrders.commandOf(new GameOrder(2, PartyOrders.PICK_UP, List.of(), null, null, 0)),
                "a pickup of nothing");
        assertNull(PartyOrders.commandOf(new GameOrder(2, PartyOrders.DROP, List.of(), null, null, 1)),
                "a thing put down nowhere");
        assertNull(PartyOrders.commandOf(new GameOrder(2, PartyOrders.TO_THE_GATE, List.of(), null, null, 0)),
                "a walk up to no gate");
        assertNull(PartyOrders.commandOf(new GameOrder(2, PartyOrders.USE, List.of(), null, null, 3)),
                "a thing used on nothing");
    }
}
