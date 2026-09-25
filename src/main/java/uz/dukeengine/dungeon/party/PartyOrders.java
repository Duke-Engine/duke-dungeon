package uz.dukeengine.dungeon.party;

import java.util.List;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.dungeon.ai.AttackMove;
import uz.dukeengine.dungeon.ai.HoldGround;
import uz.dukeengine.dungeon.run.Watching;
import uz.dukeengine.dungeon.skill.CastSkill;
import uz.dukeengine.dungeon.skill.UpgradeSkill;
import uz.dukeengine.rts.message.GameMessage.GameOrder;

/**
 * This game's own orders in the one form every machine hears: a {@link GameOrder}.
 *
 * <p>A command of the game's own class stays on the machine it was posted on — the wire and the replay speak the
 * RTS set — so in a party a cast posted as a {@code CastSkill} would happen on one machine and nowhere else. The
 * engine's answer is the game order: a word, a place, a target and a number, which it carries without reading.
 * This is the one place the words are written and read, so the two cannot drift.
 *
 * <p>Posted alone as well as in a party, so the two games are one game and a replay records what was ordered.
 */
public final class PartyOrders {

    static final String CAST = "cast";
    static final String RAISE = "raise";
    static final String HOLD = "hold";
    static final String MARCH = "attack-move";
    static final String WATCH = "watch";
    /** Followed by the hero's template: the one order whose meaning is a name. */
    static final String HERO = "hero:";

    private PartyOrders() {
    }

    /** The order that carries {@code command} to every machine. */
    public static GameOrder of(Command command) {
        return switch (command) {
            case CastSkill cast -> new GameOrder(cast.playerIndex(), CAST, List.of(), cast.point(), cast.target(),
                    cast.key());
            case UpgradeSkill raise -> new GameOrder(raise.playerIndex(), RAISE, List.of(), null, null, raise.key());
            case HoldGround hold -> new GameOrder(hold.playerIndex(), HOLD, List.of(), null, null,
                    hold.stand() ? 1 : 0);
            case AttackMove march -> new GameOrder(march.playerIndex(), MARCH, List.of(), march.spot(), null, 0);
            case Watching watching -> new GameOrder(watching.playerIndex(), WATCH, List.of(), null,
                    watching.unit(), 0);
            case ChooseHero pick -> new GameOrder(pick.playerIndex(), HERO + pick.hero(), List.of(), null, null, 0);
            default -> throw new IllegalArgumentException("not one of this game's orders: " + command);
        };
    }

    /**
     * The command an order carries, or {@code null} for an order this game never sends — which arrives from
     * another machine as data, and is not obeyed because it cannot be read.
     */
    public static Command commandOf(GameOrder order) {
        var word = order.word();
        if (word.startsWith(HERO)) {
            return new ChooseHero(order.playerIndex(), word.substring(HERO.length()));
        }
        return switch (word) {
            case CAST -> new CastSkill(order.playerIndex(), (char) order.number(), order.target(), order.place());
            case RAISE -> new UpgradeSkill(order.playerIndex(), (char) order.number());
            case HOLD -> new HoldGround(order.playerIndex(), order.number() != 0);
            case MARCH -> order.place() == null ? null : new AttackMove(order.playerIndex(), order.place());
            case WATCH -> new Watching(order.playerIndex(), order.target());
            default -> null;
        };
    }
}
