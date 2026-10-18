package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;

/**
 * A player's answer to an offer: the {@code count}th conversation said (the same count {@link Read} uses) was an offer,
 * and he accepted it or turned it down. Sent just before the {@link Read} that says he has finished with it.
 */
public record Answer(int playerIndex, int count, boolean accept) implements Command {
}
