package uz.dukeengine.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.client3d.WeatherLook;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.StagePaint;
import uz.dukeengine.dungeon.world.DayNight;

/**
 * The open world's hour and weather, as this machine draws them: the hour's colour over the world (dawn, day, dusk, a
 * dark-blue night), the fires' halos by night, the weather falling round the camera with its loop and a storm's
 * flash, and a small clock by the minimap. Only on a wide map: the castles, halls and the descent keep their own light.
 *
 * <p>The hour and the weather are the run's (its clock, saved; its seed) and so the same on every machine; how they are
 * drawn is this client's. Until the engine can relight a world while it is drawn (E82) the hour is a colour laid over
 * the world under the panel, as the game's own canvas can: a fill, the halos added, nothing more a frame. Drawn under
 * the game's other screens, whose painter it wraps.
 */
final class Atmosphere implements Painter {

    /** How fast a weather's colour comes and goes, a share of what is left each second. */
    private static final float EASE_PER_SECOND = 0.6f;
    /** A storm's flash: so many frames, from so strong to nothing. */
    private static final int FLASH_FRAMES = 12;
    private static final float FLASH_ALPHA = 0.5f;
    /** How far apart the ground is sampled round him, in cells, for the ground's weather. */
    private static final int SAMPLE = 6;

    private final Painter under;
    private final Duke3D duke;
    private final DungeonSettings settings;
    private final DayNight day;
    private final java.util.Map<String, DayNight.Glow> glows = new HashMap<>();

    private DayNight.Weather falling;
    private boolean fallingSet;
    private final Set<String> looping = new HashSet<>();
    private int weatherShown;
    private long lastThunder = -1;
    private long lastNanos;
    private char biome = '*';
    /** The {@code --weather=Name} test arg's weather, drawn whatever the sky says; null for the sky's. */
    private final DayNight.Weather forced;

    Atmosphere(Painter under, Duke3D duke, DungeonSettings settings, String forced) {
        this.under = under;
        this.forced = forced == null ? null : settings.dayNight().look(forced);
        this.duke = duke;
        this.settings = settings;
        this.day = settings.dayNight();
        for (var glow : day.glows()) {
            glows.put(glow.template(), glow);
        }
    }

    @Override
    public void paint(Canvas canvas) {
        var match = DialogueScreen.match();
        boolean open = match != null && match.game().getLogic() != null
                && StagePaint.isWide(match.game().getMapRecord());
        if (!open) {
            fall(null);
            loops(Set.of());
            weatherShown = 0;
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
        cover(canvas, panel, day.lightAt(hour));
        int target = weather == null ? weatherShown & 0xFFFFFF : weather.argb();
        weatherShown = DayNight.mix(weatherShown, target, 1f - (float) Math.exp(-seconds * EASE_PER_SECOND));
        cover(canvas, panel, weatherShown);
        glows(canvas, units, night, bottom);
        thunder(canvas, run.seed(), clock, weather, panel);
        clock(canvas, hour, bottom);
        under.paint(canvas);
    }

    @Override
    public void resized(int width, int height) {
        under.resized(width, height);
    }

    /** The world, all of the window but the panel, washed with {@code argb}. */
    private static void cover(Canvas canvas, Canvas.Box panel, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        float bottom = panel == null ? canvas.height() : panel.y();
        canvas.fillRect(0f, 0f, canvas.width(), bottom, argb);
        if (panel != null) { // beside a panel narrower than the window
            canvas.fillRect(0f, bottom, panel.x(), canvas.height() - bottom, argb);
            float right = panel.x() + panel.width();
            canvas.fillRect(right, bottom, canvas.width() - right, canvas.height() - bottom, argb);
        }
    }

    /** The fires' halos, as strong as the night is dark: added over the dark, so a fire reads further at night. */
    private void glows(Canvas canvas, List<UnitView> units, float night, float bottom) {
        if (night < 0.05f || day.glow().isBlank() || glows.isEmpty()) {
            return;
        }
        var picture = Canvas.Image.of(day.glow());
        for (var unit : units) {
            var glow = glows.get(unit.templateName());
            if (glow == null) {
                continue;
            }
            var middle = canvas.screenOf(unit.x(), unit.y(), 4f);
            var edge = canvas.screenOf(unit.x() + glow.radius(), unit.y(), 4f);
            if (middle == null || edge == null || middle.y() > bottom) {
                continue;
            }
            float r = (float) Math.hypot(edge.x() - middle.x(), edge.y() - middle.y());
            int alpha = Math.round(Math.clamp(night * 0.8f, 0f, 1f) * 255f);
            canvas.drawImage(picture, middle.x() - r, middle.y() - r, middle.x() + r, middle.y() + r,
                    alpha << 24 | glow.colour() & 0xFFFFFF, Canvas.Blend.ADDITIVE);
        }
    }

    /** A storm's flash, white and gone in a few frames, and its clap: on the same frame on every machine. */
    private void thunder(Canvas canvas, long seed, int clock, DayNight.Weather weather, Canvas.Box panel) {
        if (weather == null || weather.thunderFrames() <= 0) {
            return;
        }
        long at = DayNight.thunderIn(seed, clock, weather.thunderFrames());
        long since = clock - at;
        if (since < 0 || since >= FLASH_FRAMES) {
            return;
        }
        if (at != lastThunder && !weather.thunder().isBlank()) {
            duke.sound(weather.thunder());
        }
        lastThunder = at;
        int alpha = Math.round(FLASH_ALPHA * (1f - since / (float) FLASH_FRAMES) * 255f);
        cover(canvas, panel, alpha << 24 | 0xE8F0FF);
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
        duke.weather(look == null ? null
                : new WeatherLook(look.falls(), look.fallBox(), look.fallSpacing(), look.fallSpeed(), look.sway(),
                        0.0533f, 0.0275f, 0f, 0f, 256f, look.fallSize()));
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
