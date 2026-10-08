package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/** "I'll take one": a player buys one of a merchant's wares, by its place in his stock. See {@code DungeonRun.buy}. */
public record Buy(int playerIndex, ObjectId merchant, int ware) implements Command {
}
