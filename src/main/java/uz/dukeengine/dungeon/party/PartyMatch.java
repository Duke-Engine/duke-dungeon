package uz.dukeengine.dungeon.party;

/**
 * What the host settled for the party, said to every guest in one line: the endless descent from a seed, or a stage
 * by its name.
 *
 * <p>The seed is the host's, drawn once, so every machine lays the same floors; a guest drawing its own would be
 * playing another dungeon from the first frame. Heroes are not in it — each player says his own, as an order, once
 * the match runs.
 *
 * <p>Headed with the game and a version, so a guest on a build that writes this line differently is refused with
 * a reason, rather than let in to a match that comes apart a minute later.
 *
 * @param stage the stage's name, as its folder under {@code maps/} is called, or {@code null} for the descent
 * @param seed  what the descent is drawn from; nothing, for a stage, which carries its own
 */
public record PartyMatch(String stage, long seed) {

    static final String HEADER = "duke-dungeon party 1";

    /** The endless descent, from this seed. */
    public static PartyMatch endless(long seed) {
        return new PartyMatch(null, seed);
    }

    /** One stage, by its name. */
    public static PartyMatch stage(String name) {
        return new PartyMatch(name, 0L);
    }

    public boolean isStage() {
        return stage != null;
    }

    /** The line the host sends. */
    public String written() {
        return HEADER + "\n" + (isStage() ? "stage " + stage : "endless " + seed);
    }

    /** What a host's line says, or an error saying why it cannot be played here. */
    public static PartyMatch read(String written) {
        var lines = written == null ? new String[0] : written.strip().split("\n");
        if (lines.length != 2 || !lines[0].equals(HEADER)) {
            throw new IllegalArgumentException("the host is running a different build of the game ("
                    + (lines.length == 0 ? "it said nothing" : lines[0]) + ")");
        }
        var words = lines[1].strip().split(" ", 2);
        if (words.length == 2 && words[0].equals("stage") && !words[1].isBlank()) {
            return stage(words[1]);
        }
        if (words.length == 2 && words[0].equals("endless")) {
            try {
                return endless(Long.parseLong(words[1]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("the host's seed is not a number: " + words[1], e);
            }
        }
        throw new IllegalArgumentException("the host asked for a game this build does not know: " + lines[1]);
    }
}
