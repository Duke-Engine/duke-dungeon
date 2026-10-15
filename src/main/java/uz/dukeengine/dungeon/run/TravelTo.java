package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;

/** "Take me there": a player travels to a watchtower, by its place among the map's towers. See DungeonRun.travel. */
public record TravelTo(int playerIndex, int tower) implements Command {
}
