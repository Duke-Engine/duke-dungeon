package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/** "Take this": a player sells what is in a slot of his bag to a merchant. See {@code DungeonRun.sell}. */
public record Sell(int playerIndex, ObjectId merchant, int slot) implements Command {
}
