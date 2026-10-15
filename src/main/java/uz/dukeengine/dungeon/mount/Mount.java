package uz.dukeengine.dungeon.mount;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.rpg.skill.Skill;

/**
 * A horse on the open world: the one {@code Mount} block of {@code data/world/mounts.duke}. See
 * docs/plan/2026-10-15-mounts.md; how it is played is {@code run/Stable}, and riding itself is each hero's MOUNT skill.
 *
 * @param whistle      the Potion that is owning a horse: carried, he may call it; blank for no riding at all
 * @param horse        the template of the horse: standing about while he is off it, carrying him while he rides
 * @param key          the key that calls the horse and casts his MOUNT skill on it, and gets him off it
 * @param keyWord      what that key is called on the Settings screen's list of keys
 * @param reach        how near the horse's middle he gets up onto it
 * @param callDistance how far from him the horse he calls starts its run in
 * @param maps         the maps out of doors: on any other every point is in the zone {@link #INDOORS}
 * @param bone         the horse's bone a rider sits at
 * @param seat         where on it, in the horse's units: written {@code "x y z"}, z up
 * @param noHorseWord  said when he calls and owns no horse
 * @param indoorsWord  said when he calls where he may not ride
 * @param call         the sound of the horse answering his call
 * @param puff         the effect where the horse appears from nowhere, and where he gets on and off
 * @param skills       the MOVE_SPEED skill that pictures his quicker legs on the buff row
 */
public record Mount(String whistle, String horse, String key, String keyWord, float reach, float callDistance,
        List<String> maps, String bone, String seat, String noHorseWord, String indoorsWord, String call,
        String puff, List<Skill> skills) {

    public static final Mount DEFAULTS = new Mount("", "", "T", "", 14f, 70f, List.of(), "", "", "", "", "", "",
            List.of());

    /** The word a right-click on the horse gives: its cursor's name, and the order's word. */
    public static final String RIDE = "Ride";

    /** The zone every point of a map not out of doors is in, for a skill's NotIn. */
    public static final String INDOORS = "Ichkari";

    public Mount {
        maps = maps == null ? List.of() : List.copyOf(maps);
        bone = bone == null ? "" : bone;
        seat = seat == null ? "" : seat;
        call = call == null ? "" : call;
        puff = puff == null ? "" : puff;
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    /** Whether anybody rides at all: a whistle is named. */
    public boolean on() {
        return !whistle.isBlank();
    }

    /** Where on the bone he sits. */
    public Coord3D seatOffset() {
        var w = seat.isBlank() ? new String[0] : seat.strip().split("\\s+");
        return w.length != 3 ? Coord3D.ZERO
                : new Coord3D(Float.parseFloat(w[0]), Float.parseFloat(w[1]), Float.parseFloat(w[2]));
    }
}
