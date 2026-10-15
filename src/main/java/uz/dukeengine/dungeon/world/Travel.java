package uz.dukeengine.dungeon.world;

/**
 * Fast travel between the watchtowers of a map: a tower is a zone whose name begins {@code towers}; it is found as
 * every place is, by the hero standing in it (saved with the places found), and from the world map -- or from its
 * keeper, who opens the map -- he goes to any found tower for {@code cost} gold, under the title card {@code card}.
 *
 * @param towers      how a tower's zone is named: the start of its name
 * @param cost        the gold one journey costs
 * @param keeper      the Npc who keeps a tower; talked to, he opens the world map to travel from
 * @param card        the Title shown as he goes, or blank for none
 * @param noGoldWord  what the purse says when he has too little
 * @param unknownWord what is said of a tower he has not found
 * @param travelHint  the world map's line for it: the cost and how to go
 */
public record Travel(String towers, int cost, String keeper, String card, String noGoldWord, String unknownWord,
        String travelHint) {

    /** What a block leaves out: no towers anywhere. */
    public static final Travel DEFAULTS = new Travel("", 0, "", "", "Not enough gold", "Not found yet",
            "Click a tower to travel");
}
