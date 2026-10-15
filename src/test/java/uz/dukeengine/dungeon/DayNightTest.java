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

    @Test
    void theLightAtTheKeyHours() {
        assertEquals(0, DAY.lightAt(12f) >>> 24, "noon lays nothing over the world");
        int midnight = DAY.lightAt(0f);
        assertEquals(128, midnight >>> 24, "night is half dark");
        assertEquals(0x0A1838, midnight & 0xFFFFFF, "and dark blue");
        assertEquals(midnight, DAY.lightAt(23f), "and the same either side of midnight");
        int dawn = DAY.lightAt(6.5f);
        assertTrue((dawn >> 16 & 0xFF) > (dawn & 0xFF), "dawn is warm");
        int dusk = DAY.lightAt(18.5f);
        assertTrue((dusk >> 16 & 0xFF) > (dusk & 0xFF), "dusk is warm");
        assertTrue((dawn >>> 24) < 128 && (dusk >>> 24) < 128, "and neither as dark as night");
        int between = DAY.lightAt(7.25f);
        assertTrue((between >>> 24) > 0 && (between >>> 24) < (dawn >>> 24), "eased between the keys");
        assertEquals(1f, DAY.nightShare(0f), 1e-4);
        assertEquals(0f, DAY.nightShare(12f), 1e-4);
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
