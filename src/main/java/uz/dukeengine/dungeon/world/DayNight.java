package uz.dukeengine.dungeon.world;

import java.util.List;
import java.util.SplittableRandom;

/**
 * The open world's day and night, and its weather -- the one {@code DayNight} block of {@code data/world/daynight.duke}.
 * See {@code docs/plan/2026-10-15-day-and-night.md}.
 *
 * <p>The hour is the run's clock (frames of play, saved with the game) read round a day of {@code cycleFrames}: the
 * same on every machine, so the monsters that only roam at night wake together everywhere. What the hour looks like
 * -- the light, the weather falling, its loop -- is drawing, and only the open world (a wide map) is drawn by it;
 * interiors and the descent keep their own fixed light.
 *
 * @param cycleFrames   frames in a whole day (30 a second)
 * @param startHour     the hour a new game begins at: the clock's 0
 * @param nightFrom     the hour night begins
 * @param nightTo       and ends
 * @param light         the colour laid over the world at an hour, eased between the hours written
 * @param nightRoamers  the monsters that only roam at night: by day they keep to where they stand, unless struck
 * @param glows         what glows by night, a halo the night's share strong
 * @param glow          the halo's picture
 * @param nightLoop     the loop heard at night in the open world; blank for none
 * @param weatherFrames how long one weather lasts before the next is drawn
 * @param weathers      what weather each ground has, by chance; {@code *} for any other
 * @param looks         each weather's look and sound
 * @param clockWords    the HUD clock's names for the four parts of the day: night, dawn, day, dusk
 */
public record DayNight(int cycleFrames, float startHour, float nightFrom, float nightTo, List<Key> light,
        List<String> nightRoamers, List<Glow> glows, String glow, String nightLoop, int weatherFrames,
        List<Table> weathers, List<Weather> looks, List<String> clockWords) {

    /** What a game with no DayNight block has: noon, always, and no weather. */
    public static final DayNight DEFAULTS = new DayNight(36000, 12f, 20f, 5f, List.of(), List.of(), List.of(), "",
            "", 0, List.of(), List.of(), List.of());

    public DayNight {
        light = light == null ? List.of() : List.copyOf(light);
        nightRoamers = nightRoamers == null ? List.of() : List.copyOf(nightRoamers);
        glows = glows == null ? List.of() : List.copyOf(glows);
        glow = glow == null ? "" : glow;
        nightLoop = nightLoop == null ? "" : nightLoop;
        weathers = weathers == null ? List.of() : List.copyOf(weathers);
        looks = looks == null ? List.of() : List.copyOf(looks);
        clockWords = clockWords == null ? List.of() : List.copyOf(clockWords);
    }

    /** The hour, 0 to 24, {@code clock} frames into the run. */
    public float hourOf(long clock) {
        long into = Math.floorMod(clock, (long) cycleFrames);
        return (startHour + into * 24f / cycleFrames) % 24f;
    }

    /** Whether {@code hour} is night: from NightFrom round to NightTo. */
    public boolean night(float hour) {
        return nightFrom > nightTo ? hour >= nightFrom || hour < nightTo : hour >= nightFrom && hour < nightTo;
    }

    /** Whether {@code template} sets out now: anything does by day but a night roamer. */
    public boolean roams(String template, long clock) {
        return !nightRoamers.contains(template) || night(hourOf(clock));
    }

    /** The colour laid over the world at {@code hour}, ARGB: eased between the two Light keys round it. */
    public int lightAt(float hour) {
        if (light.isEmpty()) {
            return 0;
        }
        Key before = light.getLast();
        Key after = light.getFirst();
        for (var key : light) {
            if (key.hour() <= hour) {
                before = key;
            } else {
                after = key;
                break;
            }
        }
        // Before the first key or past the last, it is the last eased round midnight to the first.
        float span = Math.floorMod((int) ((after.hour() - before.hour()) * 1000f), 24000) / 1000f;
        float gone = Math.floorMod((int) ((hour - before.hour()) * 1000f), 24000) / 1000f;
        float t = span <= 0f ? 0f : Math.clamp(gone / span, 0f, 1f);
        return mix(before.argb(), after.argb(), t);
    }

    /** How dark it is at {@code hour}, 0 to 1: the light's strength against the strongest key's -- what glows go by. */
    public float nightShare(float hour) {
        int darkest = light.stream().mapToInt(key -> key.argb() >>> 24).max().orElse(0);
        return darkest == 0 ? 0f : (lightAt(hour) >>> 24) / (float) darkest;
    }

    /**
     * The weather on ground {@code biome} (a paint character) {@code clock} frames into a run of {@code seed}: drawn
     * from the ground's table once every WeatherFrames, from the seed, the window and the ground alone -- the same
     * on every machine and every load. Null for clear, or none written.
     */
    public Weather weatherAt(long seed, long clock, char biome) {
        if (weatherFrames <= 0) {
            return null;
        }
        Table table = null;
        for (var each : weathers) {
            if (each.biome() == biome || each.biome() == '*' && table == null) {
                table = each;
            }
        }
        if (table == null || table.chances().isEmpty()) {
            return null;
        }
        long window = Math.floorDiv(clock, (long) weatherFrames);
        int total = table.chances().stream().mapToInt(Chance::weight).sum();
        int roll = new SplittableRandom(seed ^ window * 0x9E3779B97F4A7C15L ^ biome * 0xC2B2AE3D27D4EB4FL)
                .nextInt(Math.max(1, total));
        for (var chance : table.chances()) {
            roll -= chance.weight();
            if (roll < 0) {
                return look(chance.weather());
            }
        }
        return null;
    }

    /** The weather named {@code name}, or null: Clear, or a name with no look. */
    public Weather look(String name) {
        return looks.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    /**
     * The frame of the thunder in the storm window {@code clock} falls in, one every {@code every} frames at a place in
     * it drawn from the seed: so every machine flashes on the same frame.
     */
    public static long thunderIn(long seed, long clock, int every) {
        long window = Math.floorDiv(clock, (long) every);
        return window * every + new SplittableRandom(seed * 31 + window).nextInt(every);
    }

    /** {@code a} to {@code b}, a share {@code t} of the way, each of ARGB's four. */
    public static int mix(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int x = a >>> shift & 0xFF;
            int y = b >>> shift & 0xFF;
            out |= Math.round(x + (y - x) * t) << shift;
        }
        return out;
    }

    /** A Light key -- written {@code 6 0xFF9A50 18}: the hour, the colour and how much of it, in percent. */
    public record Key(float hour, int colour, int percent) {

        public static Key of(String written) {
            var w = written.strip().split("\\s+");
            if (w.length != 3) {
                throw new IllegalArgumentException("'" + written + "' is an hour, a colour and a percent");
            }
            return new Key(Float.parseFloat(w[0]), Integer.decode(w[1]), Integer.parseInt(w[2]));
        }

        int argb() {
            return Math.round(Math.clamp(percent, 0, 100) * 2.55f) << 24 | colour & 0xFFFFFF;
        }
    }

    /** A thing that glows by night -- written {@code Campfire 0xFF9A3A 40}: its template, colour and reach. */
    public record Glow(String template, int colour, float radius) {

        public static Glow of(String written) {
            var w = written.strip().split("\\s+");
            if (w.length != 3) {
                throw new IllegalArgumentException("'" + written + "' is a template, a colour and a reach");
            }
            return new Glow(w[0], Integer.decode(w[1]), Float.parseFloat(w[2]));
        }
    }

    /** A ground's weathers by chance -- written {@code m = Fog 45, Rain 25, Clear 30}. */
    public record Table(char biome, List<Chance> chances) {

        public static Table of(String written) {
            var sides = written.split("=");
            if (sides.length != 2 || sides[0].strip().length() != 1) {
                throw new IllegalArgumentException("'" + written + "' is a ground's character = weathers by chance");
            }
            var chances = new java.util.ArrayList<Chance>();
            for (var one : sides[1].split(",")) {
                var w = one.strip().split("\\s+");
                if (w.length != 2) {
                    throw new IllegalArgumentException("'" + one + "' is a weather and its chance");
                }
                chances.add(new Chance(w[0], Integer.parseInt(w[1])));
            }
            return new Table(sides[0].strip().charAt(0), List.copyOf(chances));
        }
    }

    /** One weather of a table and how likely it is. */
    public record Chance(String weather, int weight) {
    }

    /**
     * How a weather looks and sounds.
     *
     * @param name        what a table calls it
     * @param tint        the colour laid over the world besides the hour's, ARGB-less
     * @param tintPercent how much of it
     * @param falls       the picture that falls round the camera (rain's streak, snow's flake); blank for none
     * @param fallSpeed   how fast, in units a second
     * @param fallSize    the side of each, in units
     * @param fallSpacing how far apart the columns are: the cap on how many fall (a box of FallBox each way)
     * @param fallBox     the side of the box round the camera they fall in
     * @param sway        how far each sways
     * @param loop        the loop heard under it; blank for none
     * @param thunderFrames a flash and a clap once in so many frames; 0 for none
     * @param thunder     the clap's cue
     */
    public record Weather(String name, int tint, int tintPercent, String falls, float fallSpeed, float fallSize,
            float fallSpacing, float fallBox, float sway, String loop, int thunderFrames, String thunder) {

        public Weather {
            falls = falls == null ? "" : falls;
            loop = loop == null ? "" : loop;
            thunder = thunder == null ? "" : thunder;
        }

        /** The colour it lays over the world, ARGB. */
        public int argb() {
            return Math.round(Math.clamp(tintPercent, 0, 100) * 2.55f) << 24 | tint & 0xFFFFFF;
        }

        /** How many flakes fall round the camera: a column every FallSpacing across the box, each way. */
        public int flakes() {
            int half = (int) Math.floor(fallBox / Math.max(0.01f, fallSpacing) * 0.5f);
            return falls.isBlank() ? 0 : 4 * half * half;
        }
    }
}
