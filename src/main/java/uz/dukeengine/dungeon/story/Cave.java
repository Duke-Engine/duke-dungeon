package uz.dukeengine.dungeon.story;

/**
 * A cave of the open world (docs/plan/2026-10-19-open-world-act1.md): a mouth on {@code map} in the zone {@code zone},
 * and under it a floor of its own -- the campaign's Descent of the cave's name, laid at {@code depth} -- full of
 * monsters. Stepping into the mouth takes the heroes down; the last monster of the floor dead, the cave is gone (its
 * {@code gone} flag, saved), and they come back up at its mouth. A cave gone is never gone into again.
 *
 * @param zone  the mouth: the map's zone a hero steps into to go down
 * @param wakes a flag it waits for before it takes anybody in; blank for from the start
 * @param gone  the flag set when its floor is cleared; blank for {@code Gone} and its name
 * @param seal  the Monster laid in its mouth once it is gone, as a map is laid: a cleared cave is seen sealed; blank for
 *              none
 */
public record Cave(String name, String map, String zone, String wakes, String gone, String seal) {

    public static final Cave DEFAULTS = new Cave("", "world", "", "", "", "CaveSeal");

    /** The flag its floor cleared sets. */
    public String goneFlag() {
        return gone == null || gone.isBlank() ? "Gone" + name : gone;
    }
}
