package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Hotkeys;
import uz.dukeengine.client3d.KeyMap;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.world.Controls.Scheme;

/**
 * The two control schemes' keys (docs/plan/2026-10-15-modern-controls.md): modern casts a hero's four on Q E R F and
 * leaves W A S D to his feet; classic is the keys as they always were. A skill keeps its own key as its name.
 */
class ModernControlsTest {

    /** The shipped settings played the classic way. */
    static DungeonSettings classic() {
        return played(Scheme.CLASSIC);
    }

    private static DungeonSettings played(Scheme scheme) {
        var settings = DungeonSettings.load();
        settings.useScheme(scheme);
        return settings;
    }

    @Test
    void theFileIsModernAndTheMigrationIsQErf() {
        var shipped = DungeonSettings.load();
        assertEquals(Scheme.MODERN, shipped.scheme(), "the owner's default for a new player, since E74-E80");
        assertEquals(Map.of('Q', 'Q', 'W', 'E', 'E', 'R', 'R', 'F'), shipped.controls().modernKeys());
        assertEquals('E', shipped.letterOf('W'));
        assertEquals('W', classic().letterOf('W'), "classic presses a skill on its own key");
    }

    /** Every hero's four, pressed on the modern letters, each asking for what its own key asked for in classic. */
    @Test
    void everyHeroCastsHisFourOnQErfInTheModernScheme() {
        var classic = played(Scheme.CLASSIC);
        var modern = played(Scheme.MODERN);
        for (var hero : modern.heroes()) {
            var before = Hotkeys.create();
            Main.aimsFor(before, classic, hero.name());
            var after = Hotkeys.create();
            Main.aimsFor(after, modern, hero.name());
            for (var skill : modern.skillsFor(hero.name())) {
                char letter = modern.letterOf(skill.key());
                assertEquals("QERF".charAt("QWER".indexOf(skill.key())), letter, hero.name() + "'s " + skill.key());
                assertEquals(before.aimOf(skill.key()), after.aimOf(letter), hero.name() + "'s " + skill.key());
                assertEquals(skill.name(), after.wordOf(letter));
            }
            for (char feet : "WASD".toCharArray()) {
                assertFalse(after.claimedKeys().contains(feet), hero.name() + ": " + feet + " is a step");
            }
        }
    }

    /** Modern takes the orders' letters off and W A S D off the camera; classic keeps both. */
    @Test
    void modernLeavesWasdToHisFeetAndClassicIsAsItWas() {
        var modern = Main.controls(played(Scheme.MODERN));
        for (char feet : "WASD".toCharArray()) {
            assertFalse(modern.claimedKeys().contains(feet), feet + " is a step, not an order or a skill");
            for (var pan : new KeyMap.Control[] {KeyMap.Control.PAN_UP, KeyMap.Control.PAN_LEFT,
                    KeyMap.Control.PAN_DOWN, KeyMap.Control.PAN_RIGHT}) {
                assertTrue(modern.keyMap().keysOf(pan).stream().noneMatch(key -> key.code() == Hotkeys.codeOf(feet)),
                        feet + " pans no camera in the modern scheme");
                assertFalse(modern.keyMap().keysOf(pan).isEmpty(), "the arrows still pan");
            }
        }
        var classic = Main.controls(played(Scheme.CLASSIC));
        assertEquals(Hotkeys.Aim.UNIT_OR_GROUND, classic.aimOf('A'));
        assertEquals(Hotkeys.Aim.OPEN_GROUND, classic.aimOf('F'), "classic F is still the walk order");
        assertTrue(classic.claimedKeys().contains('W'));
    }

    @Test
    void aModernLetterOnWalkingOrAnotherKeyIsRefused() {
        var data = Content.data();
        var onFoot = data.replace("\"W = E\"", "\"W = S\"");
        assertNotEquals(data, onFoot, "the premise: the shipped file presses W's skill on E");
        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(onFoot));
        assertTrue(refused.getMessage().contains("Controls"), refused.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("\"W = E\"", "\"W = C\"")), "C is the sheet's");
        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace("\"W = E\", ", "").replace("Scheme = CLASSIC", "Scheme = MODERN")),
                "a slot on a step's key needs a letter of its own where modern is the file's");
    }

    private static uz.dukeengine.core.thing.GameObject heroOf(uz.dukeengine.game.DukeGame game) {
        var name = DungeonSettings.load().run().defaultHero();
        return game.getLogic().getObjects().stream().filter(thing -> thing.getTemplate().name().equals(name))
                .findFirst().orElseThrow();
    }

    /** What the held keys send (E74/E75): MoveAlong for his hero, on the stream, so a replay walks him the same. */
    @Test
    void aDirectionChangeWalksHisHeroAndLettingGoStopsHimTheSameOnEveryRun() {
        float[][] ends = new float[2][];
        for (int run = 0; run < 2; run++) {
            var session = Dungeon.newSession(7L, DungeonSettings.load());
            var game = session.game();
            game.runHeadless(2);
            var hero = heroOf(game);
            var from = hero.getPosition();
            Main.walk(game, 0); // along +x
            game.runHeadless(20);
            Main.walk(game, uz.dukeengine.client3d.DirectionKeys.NONE);
            game.runHeadless(2);
            var stopped = hero.getPosition();
            game.runHeadless(10);
            assertTrue(Math.hypot(stopped.x() - from.x(), stopped.z() - from.z()) > 1.0,
                    "he walked: " + from + " -> " + stopped);
            assertEquals(stopped.x(), hero.getPosition().x(), 1e-4, "NONE stops him where he is");
            assertEquals(stopped.z(), hero.getPosition().z(), 1e-4);
            ends[run] = new float[] {hero.getPosition().x(), hero.getPosition().y(), hero.getPosition().z()};
        }
        assertEquals(java.util.Arrays.toString(ends[0]), java.util.Arrays.toString(ends[1]), "the same orders, the same walk");
    }

    @Test
    void theCameraFollowsHimInModernAndIsFreeInClassic() {
        assertEquals(uz.dukeengine.client3d.CameraFollow.of(null), Main.looks(played(Scheme.MODERN)).getCameraFollow());
        assertEquals(uz.dukeengine.client3d.CameraFollow.NONE, Main.looks(played(Scheme.CLASSIC)).getCameraFollow());
        assertEquals(uz.dukeengine.client3d.EdgeScroll.NONE, Main.looks(played(Scheme.MODERN)).getEdgeScroll());
    }

    /** The screens over the world hold his feet while they are up (E75): the client then sends NONE once. */
    @Test
    void theLaunchScreenAndHisOwnScreensHoldTheKeysAndTheWorldDoesNot() {
        var settings = DungeonSettings.load();
        var talk = new DialogueScreen(null, (uz.dukeengine.client3d.CanvasInput) event -> false, settings);
        var screens = new PlayerScreens(talk, settings);
        assertFalse(screens.holdsKeys(), "nothing open, nothing said: WASD walks");
        screens.toggle(PlayerScreens.Open.SHEET);
        assertTrue(screens.holdsKeys(), "the sheet is open");
        screens.toggle(PlayerScreens.Open.SHEET);
        assertFalse(screens.holdsKeys());

        var launch = new LaunchScreen(canvas -> { }, screens, settings.splash(), settings.mainMenu(), () -> { },
                cue -> { });
        assertTrue(launch.holdsKeys(), "the splash is up");
        for (float t = 0f; t < settings.splash().skipAfter() + 0.02f; t += 0.01f) {
            launch.step(0.01f);
        }
        launch.skip();
        launch.loaded(100);
        for (float t = 0f; t < 3f; t += 0.01f) {
            launch.step(0.01f);
        }
        assertEquals(LaunchScreen.Phase.DONE, launch.phase());
        assertFalse(launch.holdsKeys(), "gone: the screens under it say");
    }

    /** A conversation and the cutscene after it hold his feet; once both are done they are his again. */
    @Test
    void aConversationAndACutsceneHoldTheKeys() {
        var settings = DungeonSettings.load();
        var session = Dungeon.newStageSession(uz.dukeengine.dungeon.stage.Stages.load("talk", settings), settings);
        var game = session.game();
        game.runHeadless(1);
        var screen = new DialogueScreen(null, (uz.dukeengine.client3d.CanvasInput) event -> false, settings);
        DialogueScreen.show(session);
        assertFalse(screen.holdsKeys(), "nobody has talked yet");
        var hero = heroOf(game);
        var commander = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Commander")).findFirst().orElseThrow();
        hero.setPosition(new uz.dukeengine.core.math.Coord3D(commander.getPosition().x() + 12f,
                commander.getPosition().y(), commander.getPosition().z()));
        game.runHeadless(settings.story().dialogue().checkFrames() + 1);
        screen.paint(new PlayerScreensTest.Drawn());
        assertTrue(screen.holdsKeys(), "she is talking to him");
        screen.take(new uz.dukeengine.client3d.CanvasInput.Key(uz.dukeengine.client3d.CanvasInput.SPACE, true, false));
        screen.paint(new PlayerScreensTest.Drawn());
        screen.take(new uz.dukeengine.client3d.CanvasInput.Key(uz.dukeengine.client3d.CanvasInput.ESCAPE, true, false));
        screen.paint(new PlayerScreensTest.Drawn());
        game.runHeadless(2);
        assertTrue(session.run().inCutscene(), "the premise: the locket's cutscene");
        assertTrue(screen.holdsKeys(), "a cutscene holds his feet");
    }

    @Test
    void theMouseIsModernInTheModernSchemeAndClassicInTheClassic() {
        assertEquals(uz.dukeengine.client3d.MouseScheme.MODERN, Main.mouseOf(played(Scheme.MODERN)));
        assertEquals(uz.dukeengine.client3d.MouseScheme.CLASSIC, Main.mouseOf(classic()));
        assertEquals(60f, uz.dukeengine.client3d.MouseScheme.MODERN.lockReach(), "the spec's lock-on reach");
        assertTrue(uz.dukeengine.client3d.MouseScheme.MODERN.quickCast());
    }

    /** E76: a swing at the ground strikes the one enemy nearest the knight's front within 120 degrees. */
    @Test
    void theKnightSwingsInA120DegreeArc() {
        var game = Dungeon.world("""
                .....
                .....
                .....
                """, DungeonSettings.load()).game();
        game.runHeadless(1);
        for (var module : game.getLogic().getThingFactory().findTemplate("Knight").modules()) {
            if (module instanceof uz.dukeengine.combat.module.WeaponUpdate.Data weapon) {
                assertEquals(120f, weapon.swingArcDegrees());
                return;
            }
        }
        throw new AssertionError("the knight carries no weapon");
    }

    /** Every hero has his dodge: the spec's numbers, no slot of his four, nor on his bar's keys. */
    @Test
    void everyHeroHasHisDodgeOffHisFourAtTheSpecsNumbers() {
        var settings = DungeonSettings.load();
        for (var hero : settings.heroes()) {
            var dodges = settings.bookFor(hero.name()).stream()
                    .filter(skill -> skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.DODGE).toList();
            assertEquals(1, dodges.size(), hero.name() + " rolls on Space");
            var dodge = dodges.getFirst();
            assertEquals(16f, dodge.distance());
            assertEquals(9, dodge.durationFrames());
            assertEquals(7, dodge.tickFrames());
            assertEquals(75, dodge.cooldownFrames());
            assertEquals(0, dodge.manaCost());
            assertFalse(settings.skillsFor(hero.name()).contains(dodge), "no slot of the four");
            assertFalse(Main.controls(settings).claimedKeys().contains(dodge.key()), "pressed on Space, no letter");
        }
    }

    /** Space, with nothing over the world holding the keys, rolls him his 16 and he is out of harm's way at once. */
    @Test
    void spaceRollsHimTheWayHeWalksAndAScreenOverTheWorldKeepsIt() {
        var settings = DungeonSettings.load();
        var session = Dungeon.newSession(7L, settings);
        var game = session.game();
        game.runHeadless(2);
        var hero = heroOf(game);
        var from = hero.getPosition();
        var keys = Main.controls(settings);
        assertEquals(Hotkeys.Aim.NOW, keys.aimOf(' '), "Space is a key of the game's, the dodge's");
        assertFalse(Main.controls(classic()).claimedKeys().contains(' '), "classic: Space is the camera's");

        uz.dukeengine.client3d.CanvasInput held = new uz.dukeengine.client3d.CanvasInput() {
            @Override
            public boolean take(Event event) {
                return false;
            }

            @Override
            public boolean holdsKeys() {
                return true;
            }
        };
        Main.over = held;
        assertTrue(keys.pressNow(game, ' '));
        game.runHeadless(3);
        assertEquals(from.x(), hero.getPosition().x(), 1e-4, "a screen holds the keys: no roll");

        Main.over = null;
        assertTrue(keys.pressNow(game, ' '));
        game.runHeadless(3);
        assertTrue(hero.hasStatus(uz.dukeengine.core.thing.ObjectStatus.INVULNERABLE), "out of harm's way");
        game.runHeadless(12);
        assertFalse(hero.hasStatus(uz.dukeengine.core.thing.ObjectStatus.INVULNERABLE));
        double rolled = Math.hypot(hero.getPosition().x() - from.x(), hero.getPosition().y() - from.y());
        assertTrue(rolled > 10.0 && rolled < 16.5, "a roll of 16 or short of stone: " + rolled);
    }

    /** E79: his bar draws and presses each slot on the letter of the scheme, and modern has no order buttons. */
    @Test
    void theBarShowsTheLetterPressedAndModernHasNoOrderButtons() {
        var game = Dungeon.newSession(7L, DungeonSettings.load()).game();
        game.runHeadless(32);
        var line = game.getSnapshot().status();
        assertTrue(line.contains("|letter=W,E"), "W's skill on E: " + line);
        assertTrue(line.contains("|letter=R,F"), line);
        assertFalse(line.contains("|letter=V,"), "his dodge is no slot: " + line);
        assertFalse(line.contains("|cmd="), "no order buttons in modern: " + line);

        var classicGame = Dungeon.newSession(7L, classic()).game();
        classicGame.runHeadless(32);
        assertFalse(classicGame.getSnapshot().status().contains("|letter="), "classic: each on its own key");
    }

    /** E79's row: Boshqaruv, Zamonaviy first, the file's scheme its fallback. */
    @Test
    void theSettingsRowIsTheSchemeWithModernFirst() {
        var settings = DungeonSettings.load();
        var shell = FrontMenu.shell(settings, java.util.List.of(), java.util.List.of());
        var row = shell.settingsRows().stream()
                .filter(each -> each.key().equals(uz.dukeengine.dungeon.world.Controls.ROW)).findFirst().orElseThrow();
        assertEquals(java.util.List.of("Zamonaviy (WASD)", "Klassik (sichqoncha)", "Jangovar"), row.choices());
        assertTrue(row.word().startsWith("Boshqaruv"));
        assertEquals(0, row.fallback(), "a new player plays the modern way");
        assertEquals(Scheme.CLASSIC, uz.dukeengine.dungeon.world.Controls.ofChoice(1));
    }
}
