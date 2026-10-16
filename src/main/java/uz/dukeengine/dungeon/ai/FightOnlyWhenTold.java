package uz.dukeengine.dungeon.ai;

import uz.dukeengine.core.message.Command;

/**
 * His hero picks no fight of his own -- no enemy acquired by his brain or his weapon, none chased -- and fights only
 * what he is told to: an attack order on a creature (fought until it dies) or an attack-move. The owner's choice: every
 * player sends it once as his hero is chosen, so every machine of a party agrees. See {@link HeroBrain}.
 */
public record FightOnlyWhenTold(int playerIndex, boolean on) implements Command {
}
