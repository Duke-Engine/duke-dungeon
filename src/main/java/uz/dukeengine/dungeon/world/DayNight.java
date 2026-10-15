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
 * @param sky           the sun, the ambient and the haze at an hour, eased between the hours written
 * @param nightLights   what every fire's and lamp's light is multiplied by at the darkest hour (1 by day)
 * @param nightRoamers  the monsters that only roam at night: by day they keep to where they stand, unless struck
 * @param nightLoop     the loop heard at night in the open world; blank for none
 * @param weatherFrames how long one weather lasts before the next is drawn
 * @param weathers      what weather each ground has, by chance; {@code *} for any other
 * @param looks         each weather's look and sound
 * @param clockWords    the HUD clock's names for the four parts of the day: night, dawn, day, dusk
 */
public record DayNight(int cycleFrames, float startHour, float nightFrom, float nightTo, List<Sky> sky,
        float nightLights, List<String> nightRoamers, String nightLoop, int weatherFrames,
        List<Table> weathers, List<Weather> looks, List<String> clockWords) {

    /** What a game with no DayNight block has: noon, always, and no weather. */
    public static final DayNight DEFAULTS = new DayNight(36000, 12f, 20f, 5f, List.of(), 1f, List.of(),
            "", 0, List.of(), List.of(), List.of());

    public DayNight {
        sky = sky == null ? List.of() : List.copyOf(sky);
        nightLights = nightLights <= 0f ? 1f : nightLights;
        nightRoamers = nightRoamers == null ? List.of() : List.copyOf(nightRoamers);
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

    /** The sky at {@code hour}: eased between the two Sky keys round it, round midnight too; null if none written. */
    public Sky skyAt(float hour) {
        if (sky.isEmpty()) {
            return null;
        }
        Sky before = sky.getLast();
        Sky after = sky.getFirst();
        for (var key : sky) {
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
        return before.toward(after, t, hour);
    }

    /** How dark it is at {@code hour}, 0 (the brightest key's sun) to 1 (the darkest's): what the fires go by. */
    public float nightShare(float hour) {
        var now = skyAt(hour);
        if (now == null) {
            return 0f;
        }
        float most = (float) sky.stream().mapToDouble(Sky::strength).max().orElse(0);
        float least = (float) sky.stream().mapToDouble(Sky::strength).min().orElse(0);
        return most <= least ? 0f : Math.clamp((most - now.strength()) / (most - least), 0f, 1f);
    }

    /** What every light layer is multiplied by at {@code hour}: 1 by day, NightLights at the darkest. */
    public float lightScaleAt(float hour) {
        return 1f + (nightLights - 1f) * nightShare(hour);
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

    /**
     * A Sky key -- written {@code 6.5 14 70 45 0xFFB070 0x8070A0 0xE0A080}: the hour; the sun's height in degrees; its
     * strength and the ambient's, in percent; and three colours: the sun's, the shade's and the haze's.
     */
    public record Sky(float hour, float pitch, float strength, float ambient, int colour, int ambientTint, int haze) {

        public static Sky of(String written) {
            var w = written.strip().split("\\s+");
            if (w.length != 7) {
                throw new IllegalArgumentException("'" + written + "' is an hour, a pitch, a strength, an ambient"
                        + " and three colours (sun, shade, haze)");
            }
            return new Sky(Float.parseFloat(w[0]), Float.parseFloat(w[1]), Float.parseFloat(w[2]),
                    Float.parseFloat(w[3]), Integer.decode(w[4]), Integer.decode(w[5]), Integer.decode(w[6]));
        }

        /** This key a share {@code t} of the way to {@code next}, standing at {@code hour}. */
        Sky toward(Sky next, float t, float hour) {
            return new Sky(hour, pitch + (next.pitch - pitch) * t, strength + (next.strength - strength) * t,
                    ambient + (next.ambient - ambient) * t, mix(colour, next.colour, t),
                    mix(ambientTint, next.ambientTint, t), mix(haze, next.haze, t));
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
     * @param tint        the colour laid over the world (under the panel and the bars) besides the hour's, ARGB-less
     * @param tintPercent how much of it
     * @param falls       the picture that falls round the camera (rain's streak, snow's flake); blank for none
     * @param fallColour  rain's: the colour (RGB) each is multiplied by, drawn as a streak; 0 for the picture as it is
     * @param stretch     a streak's length along its fall, in its widths
     * @param fallSpeed   how fast, in units a second
     * @param fallSize    the side of each, in units
     * @param fallSpacing how far apart the columns are: the cap on how many fall (a box of FallBox each way)
     * @param fallBox     the side of the box round the camera they fall in
     * @param sway        how far each sways
     * @param loop        the loop heard under it; blank for none
     * @param thunderFrames a flash and a clap once in so many frames; 0 for none
     * @param thunder     the clap's cue
     */
    public record Weather(String name, int tint, int tintPercent, String falls, int fallColour, float stretch,
            float fallSpeed, float fallSize,
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
