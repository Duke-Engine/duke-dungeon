package uz.dukeengine.dungeon.party;

import uz.dukeengine.core.message.Command;

/**
 * "I go in as this one" — a player's pick of hero, made on the menu and said once the match is running.
 *
 * <p>An order rather than a setting because a party's heroes are every machine's business: the pick is made on
 * one machine and has to be known to all of them on the same frame, and an order is the one thing that is.
 *
 * @param hero the creature template of the hero, as its {@code Hero} block names it
 */
public record ChooseHero(int playerIndex, String hero) implements Command {
}
