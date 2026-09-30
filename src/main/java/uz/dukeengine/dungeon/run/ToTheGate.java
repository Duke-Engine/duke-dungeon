package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * "Go up to the gate": a player's hero sent to the keep's gate — a click on it, as a click on a chest sends him for
 * the chest — to say, when he is there, whether he has the key. See {@code ItemErrand}.
 *
 * @param gate the gate he is sent up to
 */
public record ToTheGate(int playerIndex, ObjectId gate) implements Command {
}
