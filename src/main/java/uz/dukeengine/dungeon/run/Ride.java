package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * "My horse": the mount key -- off it if he rides, up onto it if it stands near, else called -- or, with a horse, a
 * right-click on it: up onto it, walked over to first. See {@link Stable}.
 *
 * @param horse the horse clicked, or null for the key
 */
public record Ride(int playerIndex, ObjectId horse) implements Command {
}
