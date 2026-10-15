package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.world.DayNight;

/** The open world's day and night and its weather: see docs/plan/2026-10-15-day-and-night.md. */
class DayNightTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final DayNight DAY = SHIPPED.dayNight();
    private static final int HOUR = DAY.cycleFrames() / 24;

    /** The frame of the run's clock at {@code hour}: the clock's 0 is StartHour. */
    private static int at(float hour) {
        return Math.round((hour - DAY.startHour() + 24f) % 24f * HOUR);
    }

    @Test
    void theHourIsTheRunsClockRoundADayOfTwentyMinutes() {
        assertEquals(36000, DAY.cycleFrames(), "20 minutes at 30 frames a second");
        assertEquals(8f, DAY.hourOf(0), 1e-4, "a new game begins in the morning");
        assertEquals(20f, DAY.hourOf(DAY.cycleFrames() / 2), 1e-4);
        assertEquals(DAY.hourOf(123), DAY.hourOf(123 + 5L * DAY.cycleFrames()), 1e-4, "and goes round");
        assertFalse(DAY.night(DAY.hourOf(0)));
        assertTrue(DAY.night(DAY.hourOf(at(23))));
        assertTrue(DAY.night(DAY.hourOf(at(3))));
    }

    @Test
    void theClockIsSavedAndReadBackAndAnOlderSaveIsMorning() {
        var save = new SaveGame(5L, "world", 1, "Rogue", 0, List.of(), List.of()).withClock(at(22));
        var read = SaveGame.read(save.text(), "test");
        assertEquals(at(22), read.clock());
        assertTrue(DAY.night(DAY.hourOf(read.clock())));
        var older = save.text().replaceAll("  Clock = \\d+\\R", "");
        assertEquals(0, SaveGame.read(older, "test").clock(), "a save from before the day turned: morning");
    }

    @Test
    void theRunsClockTicksWithItsFramesAndIsInItsSave() {
        var session = Dungeon.newSession(11L, SHIPPED);
        session.game().runHeadless(40);
        assertEquals(40, session.run().clock());
        assertEquals(40, session.run().saveNow().clock());
    }

    /** The hour's sun (E86): scene.duke's at noon, low and warm at dawn and dusk, a cold low moon at night. */
    @Test
    void theSunAtTheKeyHours() {
        float yaw = SHIPPED.sun().yaw();
        var noon = Atmosphere.sunOf(DAY.skyAt(12f), yaw);
        var scene = SHIPPED.sun();
        assertEquals(scene.pitch(), noon.pitch(), 1e-3, "noon is scene.duke's own sun");
        assertEquals(scene.strengthPercent() / 100f, noon.strength(), 1e-3);
        assertEquals(scene.ambientPercent() / 100f, noon.ambient(), 1e-3);
        assertEquals(scene.colour(), noon.colour());
        assertEquals(yaw, noon.yaw(), 1e-3, "the walls are lit on the same side all day");

        var midnight = Atmosphere.sunOf(DAY.skyAt(0f), yaw);
        assertTrue(midnight.strength() < 0.4f, "the night is dark");
        assertTrue((midnight.colour() & 0xFF) > (midnight.colour() >> 16 & 0xFF), "and its moon cold");
        assertEquals(midnight, Atmosphere.sunOf(DAY.skyAt(23f), yaw), "the same either side of midnight");
        for (float hour : new float[] {6.5f, 18.5f}) {
            var low = Atmosphere.sunOf(DAY.skyAt(hour), yaw);
            assertTrue(low.pitch() < 20f, "dawn's and dusk's sun is low");
            assertTrue((low.colour() >> 16 & 0xFF) > (low.colour() & 0xFF), "and warm");
            assertTrue(low.strength() > midnight.strength() && low.strength() < noon.strength());
        }
        var between = DAY.skyAt(7.25f);
        assertTrue(between.pitch() > DAY.skyAt(6.5f).pitch() && between.pitch() < DAY.skyAt(8f).pitch(),
                "eased between the keys");
        assertEquals(1f, DAY.nightShare(0f), 1e-4);
        assertEquals(0f, DAY.nightShare(12f), 1e-4);
        assertEquals(DAY.nightLights(), DAY.lightScaleAt(0f), 1e-4, "the fires reach furthest at night");
        assertEquals(1f, DAY.lightScaleAt(12f), 1e-4, "and as written by day");
        assertTrue((DAY.skyAt(0f).haze() & 0xFF) > (DAY.skyAt(0f).haze() >> 16 & 0xFF), "the night's haze is blue");
    }

    @Test
    void theWeatherIsDrawnFromTheSeedTheWindowAndTheGround() {
        int window = DAY.weatherFrames();
        var seen = new HashSet<String>();
        for (int w = 0; w < 200; w++) {
            int clock = w * window + 17;
            var weather = DAY.weatherAt(42L, clock, 'm');
            assertEquals(weather, DAY.weatherAt(42L, clock, 'm'), "the same seed, window and ground: the same");
            assertEquals(weather, DAY.weatherAt(42L, clock + window - 20, 'm'), "the whole window long");
            seen.add(weather == null ? "Clear" : weather.name());
            var snow = DAY.weatherAt(42L, clock, 'n');
            assertTrue(snow == null || snow.name().equals("Snow"), "the highlands snow or are clear");
        }
        assertEquals(new HashSet<>(List.of("Fog", "Rain", "Clear")), seen, "the marsh's table, every one drawn");
        int differ = 0;
        for (int w = 0; w < 50; w++) {
            differ += java.util.Objects.equals(DAY.weatherAt(1L, w * window, '*'), DAY.weatherAt(2L, w * window, '*'))
                    ? 0 : 1;
        }
        assertTrue(differ > 0, "another seed, another sky");
    }

    @Test
    void aStormThundersOnAFrameTheSeedSays() {
        var storm = DAY.look("Storm");
        assertNotNull(storm);
        long at = DayNight.thunderIn(7L, 1000, storm.thunderFrames());
        assertEquals(at, DayNight.thunderIn(7L, 1000, storm.thunderFrames()));
        assertTrue(at / storm.thunderFrames() == 1000 / storm.thunderFrames(), "inside its window");
        assertTrue(DAY.looks().stream().allMatch(look -> look.flakes() <= 4096), "rain is capped");
    }

    @Test
    void theGroundIsWhatMostOfItRoundHimIs() {
        var rows = List.of("mmmmmmmmmmmmm", "mmmmmmmmmmmmm", "mmmmmmmmmmmmm", "mmmmmmdmmmmmm", "mmmmmmdmmmmmm",
                "mmmmmmdmmmmmm", "mmmmmmdmmmmmm", "mmmmmmdmmmmmm", "mmmmmmdmmmmmm", "mmmmmmdmmmmmm", "mmmmmmdmmmmmm",
                "mmmmmmdmmmmmm", "mmmmmmdmmmmmm");
        assertEquals('m', Atmosphere.biomeAt(rows, 65f, 65f, 10f), "a road through the marsh is the marsh");
        assertEquals('*', Atmosphere.biomeAt(List.of(), 0f, 0f, 10f));
    }

    /** The marsh's Arvoh keeps still by day and comes for him at night. */
    @Test
    void aNightRoamerKeepsStillByDayAndHuntsAtNight() {
        assertTrue(DAY.nightRoamers().contains("Wisp"));
        assertFalse(DAY.roams("Wisp", at(12)));
        assertTrue(DAY.roams("Wisp", at(23)));
        assertTrue(DAY.roams("Skeleton", at(12)), "anything else roams by day");

        assertTrue(closed(12f) < 2f, "by day it stays where it stood");
        assertTrue(closed(23f) > 20f, "by night it comes");
    }

    /** How far a night-roaming skeleton 180 away closes on a rogue in 120 frames at {@code hour}. */
    private static float closed(float hour) {
        var settings = DungeonSettings.parse("""
                Monster
                  Name = Skeleton
                  SenseRadius = 300
                  ChaseRadius = 500
                  CloseDistance = 4
                End
                DayNight
                  CycleFrames = 36000
                  StartHour = 8
                  NightFrom = 20
                  NightTo = 5
                  NightRoamers = [Skeleton]
                End
                """);
        var arena = Dungeon.world(room(), settings);
        arena.clock().set(at(hour));
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 330f, 150f); // well inside its sense, outside his reach
        game.runHeadless(1);
        var skeleton = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Skeleton")).findFirst().orElseThrow();
        var start = skeleton.getPosition();
        game.runHeadless(120);
        assertNotEquals(null, skeleton.getPosition());
        return skeleton.getPosition().distance(start);
    }

    private static String room() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                text.append(x == 0 || y == 0 || x == 39 || y == 29 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }
}
