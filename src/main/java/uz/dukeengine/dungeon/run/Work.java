package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * "Work it": a player's hero sent to a thing -- a right-click on a tree, a bush, a well or a fire -- to chop, pick, fill
 * or cook there; at a place, to fell the piece of the map's scenery there (a tree); or, with neither, to build a fire
 * at his feet (the build key). See {@link Camp}.
 *
 * @param thing what he works, or null
 * @param place where the scenery he fells stands, or null
 */
public record Work(int playerIndex, ObjectId thing, uz.dukeengine.core.math.Coord3D place) implements Command {

    /** A thing worked, or with none a fire built. */
    public Work(int playerIndex, ObjectId thing) {
        this(playerIndex, thing, null);
    }
}
