package uz.dukeengine.dungeon.mount;

import java.util.List;

/**
 * A horse on the open world: the one {@code Mount} block of {@code data/world/mounts.duke}. See
 * docs/plan/2026-10-15-mounts.md; how it is played is {@code run/Stable}.
 *
 * @param whistle      the Potion that is owning a horse: carried, he may call it; blank for no riding at all
 * @param horse        the template of the horse standing about when he is off it
 * @param looks        each hero's riding look, written {@code Knight = KnightRiding}: the template he is drawn as
 * @param speedPercent how much faster than his walk he rides, a percentage of it
 * @param key          the key that calls the horse, mounts it, and gets off it
 * @param keyWord      what that key is called on the Settings screen's list of keys
 * @param mountFrames  how long getting up onto it takes
 * @param mountClip    the clip he plays meanwhile, from his own set; blank for none
 * @param reach        how near the horse's middle he gets up onto it
 * @param callDistance how far from him the horse he calls starts its run in
 * @param maps         the maps he may ride on: out of doors
 * @param noRideZones  the zones of those maps he may not: the city and the villages
 * @param gallopFrames how often a stride's hooves are heard while he rides and moves
 * @param noHorseWord  said when he calls and owns no horse
 * @param indoorsWord  said when he calls where he may not ride
 * @param call         the sound of the horse answering his call
 * @param gallop       the sound of a stride while he rides
 * @param puff         the effect where the horse appears from nowhere, and where he gets on and off
 */
public record Mount(String whistle, String horse, List<String> looks, int speedPercent, String key, String keyWord,
        int mountFrames, String mountClip, float reach, float callDistance, List<String> maps,
        List<String> noRideZones, int gallopFrames, String noHorseWord, String indoorsWord, String call,
        String gallop, String puff) {

    public static final Mount DEFAULTS = new Mount("", "", List.of(), 100, "T", "", 18, "", 14f, 70f, List.of(),
            List.of(), 14, "", "", "", "", "");

    /** The word a right-click on the horse gives: its cursor's name, and the order's word. */
    public static final String RIDE = "Ride";

    public Mount {
        looks = looks == null ? List.of() : List.copyOf(looks);
        maps = maps == null ? List.of() : List.copyOf(maps);
        noRideZones = noRideZones == null ? List.of() : List.copyOf(noRideZones);
        mountClip = mountClip == null ? "" : mountClip;
        call = call == null ? "" : call;
        gallop = gallop == null ? "" : gallop;
        puff = puff == null ? "" : puff;
    }

    /** Whether anybody rides at all: a whistle is named. */
    public boolean on() {
        return !whistle.isBlank();
    }

    /** The template {@code hero} is drawn as while he rides, or null for none named (he rides drawn as himself). */
    public String lookOf(String hero) {
        for (var line : looks) {
            var sides = line.split("=");
            if (sides.length == 2 && sides[0].strip().equals(hero)) {
                return sides[1].strip();
            }
        }
        return null;
    }

    /** Whether {@code template} is riding's own -- the horse or a riding look -- and no creature that fights. */
    public boolean isMount(String template) {
        return template.equals(horse) || looks.stream().anyMatch(line -> line.split("=").length == 2
                && line.split("=")[1].strip().equals(template));
    }

    /** His riding speed: {@code walking} and SpeedPercent of it. */
    public float riding(float walking) {
        return walking * (100 + speedPercent) / 100f;
    }
}
