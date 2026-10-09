package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;

/**
 * "I have read them": a player's machine has shown to the end — or had skipped — the first {@code count} conversations
 * said. A cutscene waits on it, so it never starts while a line is on somebody's screen. See {@code DungeonRun.cue}.
 */
public record Read(int playerIndex, int count) implements Command {
}
