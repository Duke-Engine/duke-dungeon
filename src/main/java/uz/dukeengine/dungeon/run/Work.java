package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * "Work it": a player's hero sent to a thing -- a right-click on a tree, a bush, a well or a fire -- to chop, pick, fill
 * or cook there; or, with no thing, to build a fire at his feet (the build key). See {@link Camp}.
 *
 * @param thing what he works, or null to build a fire
 */
public record Work(int playerIndex, ObjectId thing) implements Command {
}
