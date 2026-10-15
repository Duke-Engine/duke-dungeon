package uz.dukeengine.dungeon.ai;

import uz.dukeengine.core.message.Command;

/**
 * His player plays the modern way (docs/plan/2026-10-15-modern-controls.md): his hero picks no fight of his own -- no
 * enemy acquired by his brain or his weapon, none chased -- and fights only what the left button gives him (a lock-on, a
 * swing). An order, sent once as the match begins, so every machine of a party agrees which heroes keep to it; a
 * player of the classic scheme never sends it. See {@link HeroBrain#holdingFire}.
 */
public record FightOnlyWhenTold(int playerIndex, boolean on) implements Command {
}
