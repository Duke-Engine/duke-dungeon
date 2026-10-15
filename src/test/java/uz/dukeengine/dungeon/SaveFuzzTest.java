package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Floors;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.run.Saves;
import uz.dukeengine.dungeon.stage.Stages;

/**
 * Saves broken every way a disk or a hand can break them -- cut short, bytes flipped, lines dropped or doubled, numbers
 * out of range, garbage, another version -- read the way the front menu reads them. None may throw, none may touch
 * the good saves beside it, and every one the menu offers must load and play. A fixed seed and a bounded count, so
 * the same cases every run.
 */
class SaveFuzzTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int CASES = 400;
    /** How many of the saves the menu offers are played, each a session laid: the slow part. */
    private static final int PLAYED = 40;

    /** Real saves, made headless: a descent two floors down with a bag, and a stage with its quests and triggers. */
    private static List<String> realSaves() {
        var descent = Dungeon.newSession(11L, SETTINGS);
        descent.game().runHeadless(1);
        var logic = descent.game().getLogic();
        logic.getObjects().stream().filter(o -> o.getTemplate().name().equals(SETTINGS.bossKindAt(1))).findFirst()
                .ifPresent(logic::destroyObject);
        descent.game().runHeadless(SETTINGS.run().descendDelayFrames() + 4);
        var boots = SETTINGS.loot().stream().filter(loot -> loot.id().equals("Boots")).findFirst().orElseThrow();
        descent.progress().getLoot().take(boots, 0, 0);
        var one = descent.run().saveNow();
        var talk = Dungeon.newStageSession(Stages.load("talk", SETTINGS), SETTINGS);
        talk.game().runHeadless(2);
        var two = talk.run().saveNow();
        assertNotNull(one);
        assertNotNull(two);
        return List.of(one.text(), two.text());
    }

    private static final String[] NUMBERS = {"-1", "0", "-2147483648", "2147483647", "2147483648", "99999999999999999999",
        "1e9", "", "NaN", "-0", "7"};

    private static byte[] broken(Random random, String good) {
        var bytes = good.getBytes(StandardCharsets.UTF_8);
        var lines = new ArrayList<>(Arrays.asList(good.split("\n", -1)));
        switch (random.nextInt(12)) {
            case 0 -> {
                return Arrays.copyOf(bytes, random.nextInt(bytes.length)); // cut short
            }
            case 1 -> {
                for (int flips = 1 + random.nextInt(4); flips > 0; flips--) {
                    bytes[random.nextInt(bytes.length)] ^= (byte) (1 << random.nextInt(8));
                }
                return bytes;
            }
            case 2 -> lines.remove(random.nextInt(lines.size()));
            case 3 -> {
                int at = random.nextInt(lines.size());
                lines.add(at, lines.get(at));
            }
            case 4 -> {
                // A number, any of them, made one no game writes.
                var matcher = java.util.regex.Pattern.compile("-?\\d+").matcher(good);
                var spans = new ArrayList<int[]>();
                while (matcher.find()) {
                    spans.add(new int[] {matcher.start(), matcher.end()});
                }
                var span = spans.get(random.nextInt(spans.size()));
                return (good.substring(0, span[0]) + NUMBERS[random.nextInt(NUMBERS.length)]
                        + good.substring(span[1])).getBytes(StandardCharsets.UTF_8);
            }
            case 5 -> {
                var garbage = new byte[random.nextInt(600)];
                random.nextBytes(garbage);
                return garbage;
            }
            case 6 -> {
                return new byte[0];
            }
            case 7 -> {
                return good.replaceFirst("Version = \\d+", "Version = " + (random.nextBoolean() ? 3 : 0))
                        .getBytes(StandardCharsets.UTF_8);
            }
            case 8 -> {
                // A line's value swapped for another line's: a list where a word goes, a word where a list.
                int a = random.nextInt(lines.size());
                int b = random.nextInt(lines.size());
                if (lines.get(a).contains("=") && lines.get(b).contains("=")) {
                    lines.set(a, lines.get(a).substring(0, lines.get(a).indexOf('=') + 1)
                            + lines.get(b).substring(lines.get(b).indexOf('=') + 1));
                }
            }
            case 9 -> {
                // A word made one the data has never heard of: a hero, an item, a skill key, a quest, a stage.
                int at = random.nextInt(lines.size());
                if (lines.get(at).contains("=")) {
                    lines.set(at, lines.get(at).substring(0, lines.get(at).indexOf('=') + 1)
                            + (random.nextBoolean() ? " Nobody" : " [x, \"y\", ,]"));
                }
            }
            case 10 -> {
                return (good + good).getBytes(StandardCharsets.UTF_8); // two Save blocks
            }
            default -> {
                // Several lines lost at once, as a crash mid-write of a file not written atomically would.
                int from = random.nextInt(lines.size());
                lines.subList(from, Math.min(lines.size(), from + 1 + random.nextInt(6))).clear();
            }
        }
        return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void aBrokenSaveIsRefusedCleanlyAndTheOthersStandAndWhatTheMenuOffersPlays(@TempDir Path home) throws Exception {
        var real = realSaves();
        var saves = new Saves(home);
        saves.write(Saves.AUTOSAVE, SaveGame.read(real.get(0), "autosave"));
        saves.write("slot1", SaveGame.read(real.get(1), "slot1"));
        var autosave = Files.readAllBytes(home.resolve("autosave.duke"));
        var slot1 = Files.readAllBytes(home.resolve("slot1.duke"));
        var random = new Random(20261015L);
        int played = 0;
        int refused = 0;
        for (int at = 0; at < CASES; at++) {
            var good = real.get(random.nextInt(real.size()));
            var bad = broken(random, good);
            Files.write(home.resolve("slot2.duke"), bad);
            var label = "case " + at + ":\n" + new String(bad, StandardCharsets.UTF_8);

            saves.read("slot2"); // null or a save; never a throw
            var offered = Main.loadable(saves.list(), SETTINGS);
            assertEquals(List.of(), offered.stream().map(Saves.Saved::slot)
                    .filter(slot -> !List.of(Saves.AUTOSAVE, "slot1", "slot2").contains(slot)).toList(), label);
            assertEquals(true, offered.stream().anyMatch(saved -> saved.slot().equals("slot1")), label);
            assertEquals(true, offered.stream().anyMatch(saved -> saved.slot().equals(Saves.AUTOSAVE)), label);
            var mine = offered.stream().filter(saved -> saved.slot().equals("slot2")).findFirst().orElse(null);
            if (mine == null) {
                refused++;
            } else if (played < PLAYED && !mine.game().text().equals(good)) {
                played++;
                var session = Dungeon.newSession(99L, SETTINGS);
                session.run().resume(mine.game(), Floors.resumed(mine.game(), SETTINGS));
                try {
                    session.game().runHeadless(3);
                } catch (RuntimeException e) {
                    throw new AssertionError("the menu offered a save that crashes the game -- " + label, e);
                }
            }
        }
        // The good ones beside it are as they were, byte for byte.
        assertEquals(true, Arrays.equals(autosave, Files.readAllBytes(home.resolve("autosave.duke"))));
        assertEquals(true, Arrays.equals(slot1, Files.readAllBytes(home.resolve("slot1.duke"))));
        System.out.println("SaveFuzz: " + CASES + " cases, " + refused + " refused, " + played + " offered and played");
    }
}
