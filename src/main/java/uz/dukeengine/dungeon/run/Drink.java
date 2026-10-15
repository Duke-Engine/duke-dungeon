package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;

/** "Drink": a player's hero drinks from his belt's {@code belt}th stack, keys 1-4 from 0. See {@link Belt}. */
public record Drink(int playerIndex, int belt) implements Command {
}
