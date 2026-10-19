package uz.dukeengine.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.client3d.Sunlight;
import uz.dukeengine.client3d.WeatherLook;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.StagePaint;
import uz.dukeengine.dungeon.world.DayNight;

/**
 * The open world's hour and weather, as this machine draws them: the hour's sun, shade and haze (dawn, day, dusk, a
 * cold low moon), the fires reaching further by night, the weather falling round the camera with its loop, its tint and
 * a storm's flash over the world alone, and a small clock by the minimap. Only on a wide map: the castles, halls and
 * the descent keep scene.duke's Sun, given back the moment he is off the open world.
 *
 * <p>The hour and the weather are the run's (its clock, saved; its seed) and so the same on every machine; how they are
 * drawn is this client's: the engine's light (E86) -- {@code Duke3D.sunlight}, {@code hazeColour}, {@code lightScale}
 * and {@code worldTint} -- told a few times a second and eased by the client between. Drawn under the game's other
 * screens, whose painter it wraps.
 */
final class Atmosphere implements Painter {

    /** How fast a weather's colour comes and goes, a share of what is left each second. */
    private static final float EASE_PER_SECOND = 0.6f;
    /** A storm's flash: so many frames, from so strong to nothing. */
    private static final int FLASH_FRAMES = 12;
    private static final float FLASH_ALPHA = 0.5f;
    /** How often the hour's light is told the client, in seconds: it eases between. */
    private static final float RELIGHT_SECONDS = 0.5f;
    /** How far apart the ground is sampled round him, in cells, for the ground's weather. */
    private static final int SAMPLE = 6;

    private final Painter under;
    private final Duke3D duke;
    private final DungeonSettings settings;
    private final DayNight day;

    private DayNight.Weather falling;
    private boolean fallingSet;
    private final Set<String> looping = new HashSet<>();
    private int weatherShown;
    /** The tint last told the client; whether the hour's light is on, and how long since it was last told. */
    private int tinted;
    private boolean lit;
    private float sinceLit;
    private long lastThunder = -1;
    private long lastNanos;
    private char biome = '*';
    private uz.dukeengine.client3d.GroundCover coverShown;
    /** The {@code --weather=Name} test arg's weather, drawn whatever the sky says; null for the sky's. */
    private final DayNight.Weather forced;

    Atmosphere(Painter under, Duke3D duke, DungeonSettings settings, String forced) {
        this.under = under;
        this.forced = forced == null ? null : settings.dayNight().look(forced);
        this.duke = duke;
        this.settings = settings;
        this.day = settings.dayNight();
    }

    @Override
    public void paint(Canvas canvas) {
        // The ground cover of the stage laid last, handed to the window once each time a new one is laid (E98).
        var cover = uz.dukeengine.dungeon.run.DungeonRun.coverLaid();
        if (cover != coverShown) {
            coverShown = cover;
            // ponytail: held back until E100 -- the engine instances cover drawn in FoggedTerrain, which has no UseInstancing,
            // and the window dies on the first chunk. Hand it `cover` again once E100 lands.
            duke.groundCover(uz.dukeengine.client3d.GroundCover.NONE);
        }
        var match = DialogueScreen.match();
        // Not while an intro has the screen: the backdrop made the match shows the hour once the seat is taken and the
        // act's opening is over (the seamless campaign).
        boolean open = match != null && match.game().getLogic() != null
                && StagePaint.isWide(match.game().getMapRecord()) && !match.run().inIntro();
        if (!open) {
            fall(null);
            loops(Set.of());
            weatherShown = 0;
            unlit();
            under.paint(canvas);
            return;
        }
        long now = System.nanoTime();
        float seconds = lastNanos == 0 ? 0f : Math.min(0.25f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        var run = match.run();
        int clock = run.clock();
        float hour = day.hourOf(clock);
        var units = match.game().getSnapshot().units();
        var hero = heroOf(match, units);
        if (hero != null && match.game().getMapRecord() instanceof StagePaint paint) {
            biome = biomeAt(paint.rows(), hero.x(), hero.y(), uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE);
        }
        var weather = forced != null ? forced : day.weatherAt(run.seed(), clock, biome);
        fall(weather);
        float night = day.nightShare(hour);
        var wanted = new HashSet<String>();
        if (weather != null && !weather.loop().isBlank()) {
            wanted.add(weather.loop());
        }
        if (night > 0.5f && !day.nightLoop().isBlank() && (weather == null || weather.loop().isBlank())) {
            wanted.add(day.nightLoop()); // the crickets go quiet under rain
        }
        loops(wanted);

        var panel = canvas.bottomPanel();
        float bottom = panel == null ? canvas.height() : panel.y();
        relight(hour, seconds);
        int target = weather == null ? weatherShown & 0xFFFFFF : weather.argb();
        weatherShown = DayNight.mix(weatherShown, target, 1f - (float) Math.exp(-seconds * EASE_PER_SECOND));
        int flash = thunder(run.seed(), clock, weather);
        tint(flash != 0 ? flash : weatherShown);
        clock(canvas, hour, bottom);
        under.paint(canvas);
    }

    @Override
    public void resized(int width, int height) {
        under.resized(width, height);
    }

    /** The hour's sun, shade, haze and lights, told the client every RELIGHT_SECONDS and eased by it between. */
    private void relight(float hour, float seconds) {
        sinceLit += seconds;
        if (lit && sinceLit < RELIGHT_SECONDS) {
            return;
        }
        var sky = day.skyAt(hour);
        if (sky == null) {
            return;
        }
        float ease = lit ? RELIGHT_SECONDS : 0f; // straight to the hour as he comes out under it
        duke.sunlight(sunOf(sky, settings.sun().yaw()), ease);
        duke.hazeColour(sky.haze() & 0xFFFFFF, ease);
        duke.lightScale(day.lightScaleAt(hour));
        lit = true;
        sinceLit = 0f;
    }

    /** Off the open world: scene.duke's Sun and the fog's own haze back, the lights as written, no tint. */
    private void unlit() {
        if (lit) {
            var sun = settings.sun();
            duke.sunlight(new Sunlight(sun.pitch(), sun.yaw(), sun.strengthPercent() / 100f,
                    sun.ambientPercent() / 100f, sun.colour(), sun.ambientTint()), 0f);
            duke.hazeColour(-1, 0f);
            duke.lightScale(1f);
            lit = false;
        }
        tint(0);
    }

    /** The colour over the world alone (under the panel and the bars), told the client as it changes. */
    private void tint(int argb) {
        if ((argb >>> 24) == 0) {
            argb = 0;
        }
        if (argb != tinted) {
            duke.worldTint(argb);
            tinted = argb;
        }
    }

    /** The sun a Sky key stands for, from {@code yaw} round the compass (scene.duke's: the side walls are lit on). */
    static Sunlight sunOf(DayNight.Sky sky, float yaw) {
        return new Sunlight(sky.pitch(), yaw, sky.strength() / 100f, sky.ambient() / 100f, sky.colour() & 0xFFFFFF,
                sky.ambientTint() & 0xFFFFFF);
    }

    /**
     * A storm's flash, white and gone in a few frames, and its clap: on the same frame on every machine. The flash's
     * colour over the world now, or 0 for none.
     */
    private int thunder(long seed, int clock, DayNight.Weather weather) {
        if (weather == null || weather.thunderFrames() <= 0) {
            return 0;
        }
        long at = DayNight.thunderIn(seed, clock, weather.thunderFrames());
        long since = clock - at;
        if (since < 0 || since >= FLASH_FRAMES) {
            return 0;
        }
        if (at != lastThunder && !weather.thunder().isBlank()) {
            duke.sound(weather.thunder());
        }
        lastThunder = at;
        int alpha = Math.round(FLASH_ALPHA * (1f - since / (float) FLASH_FRAMES) * 255f);
        return alpha << 24 | 0xE8F0FF;
    }

    /** A sun or a moon and the hour, over the minimap's corner, above the key hints. */
    private void clock(Canvas canvas, float hour, float bottom) {
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.014f), 10, 16));
        float line = canvas.measure(font, "Ag").lineHeight() + 4f;
        float y = bottom - 2f * (line + 8f);
        float x = 12f;
        boolean night = day.night(hour);
        canvas.drawImage(Canvas.Image.of("kit/effects/particles/circle_05.png"), x, y, x + line, y + line,
                night ? 0xFFC8D8FF : 0xFFFFD060, Canvas.Blend.ALPHA);
        int h = (int) hour;
        int m = (int) ((hour - h) * 60f);
        var words = day.clockWords();
        String part = words.size() < 4 ? "" : " " + words.get(night ? 0 : hour < 8f ? 1 : hour < 17f ? 2 : 3);
        var text = String.format("%02d:%02d%s", h, m, part);
        float ty = y + (line - canvas.measure(font, text).lineHeight()) / 2f;
        canvas.drawText(font, text, x + line + 7f, ty + 1f, 0xC0000000);
        canvas.drawText(font, text, x + line + 6f, ty, 0xE0F0E6D2);
    }

    /** The weather falling round the camera: told the client only as it changes. */
    private void fall(DayNight.Weather weather) {
        var look = weather == null || weather.falls().isBlank() ? null : weather;
        if (fallingSet && java.util.Objects.equals(look, falling)) {
            return;
        }
        var drawn = look == null ? null
                : new WeatherLook(look.falls(), look.fallBox(), look.fallSpacing(), look.fallSpeed(), look.sway(),
                        0.0533f, 0.0275f, 0f, 0f, 256f, look.fallSize());
        duke.weather(drawn == null || look.fallColour() == 0 ? drawn
                : drawn.rain(0xFF000000 | look.fallColour(), look.stretch()));
        falling = look;
        fallingSet = true;
    }

    /** The loops heard: those wanted started, faded in, and the rest faded out. */
    private void loops(Set<String> wanted) {
        float fade = settings.audio().fadeSeconds();
        for (var it = looping.iterator(); it.hasNext(); ) {
            var cue = it.next();
            if (!wanted.contains(cue)) {
                duke.stopLoop(cue, fade);
                it.remove();
            }
        }
        for (var cue : wanted) {
            if (looping.add(cue)) {
                duke.loop(cue, fade);
            }
        }
    }

    /** This machine's hero as the snapshot last saw him, or null. */
    private UnitView heroOf(Dungeon.Session match, List<UnitView> units) {
        int him = match.game().getLocalPlayerIndex();
        for (var unit : units) {
            if (unit.playerIndex() == him
                    && settings.heroes().stream().anyMatch(each -> each.name().equals(unit.templateName()))) {
                return unit;
            }
        }
        return null;
    }

    /**
     * The ground round {@code (x, y)}: the paint character most of nine cells round it stand on, {@code SAMPLE} cells
     * apart -- so a road through the marsh is still the marsh. Ties go to the middle's.
     */
    static char biomeAt(List<String> rows, float x, float y, float cell) {
        if (rows.isEmpty()) {
            return '*';
        }
        int cx = (int) Math.floor(x / cell);
        int cy = (int) Math.floor(y / cell);
        var counts = new HashMap<Character, Integer>();
        for (int dy = -SAMPLE; dy <= SAMPLE; dy += SAMPLE) {
            for (int dx = -SAMPLE; dx <= SAMPLE; dx += SAMPLE) {
                counts.merge(at(rows, cx + dx, cy + dy), 1, Integer::sum);
            }
        }
        char best = at(rows, cx, cy);
        for (var each : counts.entrySet()) {
            if (each.getValue() > counts.get(best)) {
                best = each.getKey();
            }
        }
        return best;
    }

    private static char at(List<String> rows, int x, int y) {
        var row = rows.get(Math.clamp(y, 0, rows.size() - 1));
        return row.isEmpty() ? '*' : row.charAt(Math.clamp(x, 0, row.length() - 1));
    }

}
