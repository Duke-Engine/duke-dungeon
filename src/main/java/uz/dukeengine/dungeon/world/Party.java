package uz.dukeengine.dungeon.world;

import java.util.List;

/**
 * Going down together: how big a party may be, where its host listens, how each hero's side is told apart, and
 * every word on the way in.
 *
 * @param maxPlayers the most heroes a party may be, the host counted; a party of one is a game alone
 * @param port       where a host listens for its guests, and what a guest calls when an address names no port
 * @param colours    each seat's colour, packed {@code 0xRRGGBB}, in seat order — the host's first. A seat past the
 *     end of the list wears the last
 * @param playersWord  the row for a party of so many, {@code %d} standing for how many
 * @param waitingHint  under the host's waiting room: {@code %s} is the address the others type, {@code %d} how many
 *     the party is
 * @param gatheringWord the banner while the party's heroes are still being said, before the first floor is laid
 */
public record Party(int maxPlayers, int port, List<Integer> colours, String whoWord, String whoHint,
        String aloneWord, String aloneBlurb, String hostWord, String hostBlurb, String joinWord, String joinBlurb,
        String howManyWord, String playersWord, String waitingWord, String waitingHint, String joiningWord,
        String joiningHint, String addressPrompt, String cancelWord, String failedWord, String gatheringWord) {

    /** What a block leaves out. */
    public static final Party DEFAULTS = new Party(4, 7777, List.of(0x5AAAFF, 0x7CD66A, 0xF2C94C, 0xC98BFF),
            "WHO GOES IN", "UP DOWN choose    ENTER take    ESC back", "Alone", "", "Host a party", "",
            "Join a party", "", "HOW MANY HEROES", "%d heroes", "WAITING FOR THE PARTY",
            "Address: %s    it starts when all %d are in", "JOINING THE PARTY", "Waiting for the host to start",
            "The host's address:", "Cancel", "The party could not meet", "The party is gathering");

    public Party {
        colours = colours == null || colours.isEmpty() ? List.of(0x5AAAFF) : List.copyOf(colours);
    }

    /** The colour seat {@code seat} (1 for the host) wears. */
    public java.awt.Color colourOf(int seat) {
        return new java.awt.Color(colours.get(Math.clamp(seat - 1, 0, colours.size() - 1)));
    }
}
