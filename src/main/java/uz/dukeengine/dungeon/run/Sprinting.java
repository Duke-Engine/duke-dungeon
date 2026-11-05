package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;

/** Shift held or let go: the player's hero runs fast (see {@link Sprint}) or at his own pace. */
public record Sprinting(int playerIndex, boolean on) implements Command {
}
