package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;

/** The left button (light) or the right (heavy) pressed: the player's hero swings a step of his {@link Combo}. */
public record Striking(int playerIndex, boolean heavy) implements Command {
}
