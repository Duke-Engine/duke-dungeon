package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * "Talk to them": a player's hero sent to a person — a right-click on them, as a click on the gate sends him up to
 * the gate — to talk when he is near enough. See {@code DungeonRun.talkTo}.
 *
 * @param person the person he goes to talk to
 */
public record TalkTo(int playerIndex, ObjectId person) implements Command {
}
