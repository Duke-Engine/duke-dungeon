package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.SoundBank;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;

/**
 * What is heard where and when: the music by place and moment, the footstep by ground, and that every moment the game
 * raises by name has a sound to raise — see {@link Soundscape} and the Audio block.
 */
class SoundscapeTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final SoundBank BANK = bank();

    private static SoundBank bank() {
        var bank = SoundBank.create();
        for (var cue : SETTINGS.sounds()) {
            bank.cue(cue.name(), Main.channelOf(cue.channel()), cue.positional(), cue.gain(), cue.gapSeconds(),
                    cue.files());
        }
        return bank.build();
    }

    private static final Map<String, String> MUSIC = Map.of("menu", "m.menu", "battle", "m.battle", "boss", "m.boss",
            "won", "m.won", "won.castle", "m.sad", "cutscene.Turn", "m.sad", "Square", "m.square", "town", "m.town",
            "default", "m.any");

    // ---- the music ----

    @Test
    void theMenuHasItsOwn() {
        assertEquals("m.menu", Soundscape.musicFor(MUSIC, false, null, null, List.of(), null, false, false));
    }

    @Test
    void aZoneHeStandsInBeatsHisMapAndHisMapBeatsAnywhere() {
        assertEquals("m.square", Soundscape.musicFor(MUSIC, true, DungeonRun.State.RUNNING, "town", List.of("Square"),
                null, false, false));
        assertEquals("m.town", Soundscape.musicFor(MUSIC, true, DungeonRun.State.RUNNING, "town", List.of("Lane"),
                null, false, false));
        assertEquals("m.any", Soundscape.musicFor(MUSIC, true, DungeonRun.State.RUNNING, "elsewhere", List.of(),
                null, false, false));
    }

    @Test
    void aFightBeatsThePlaceAndABossBeatsAFight() {
        assertEquals("m.battle", Soundscape.musicFor(MUSIC, true, DungeonRun.State.RUNNING, "town",
                List.of("Square"), null, false, true));
        assertEquals("m.boss", Soundscape.musicFor(MUSIC, true, DungeonRun.State.RUNNING, "town", List.of(), null,
                true, true));
    }

    @Test
    void aCutsceneOfItsOwnAndAWinBeatEverything() {
        assertEquals("m.sad", Soundscape.musicFor(MUSIC, true, DungeonRun.State.RUNNING, "town", List.of(), "Turn",
                true, true));
        assertEquals("m.sad", Soundscape.musicFor(MUSIC, true, DungeonRun.State.WON, "castle", List.of(), null,
                false, false), "won on this map");
        assertEquals("m.won", Soundscape.musicFor(MUSIC, true, DungeonRun.State.WON, "town", List.of(), "Other",
                true, true), "won anywhere else; a cutscene with no music of its own is no moment");
    }

    @Test
    void aMomentNotNamedIsNotOne() {
        assertNull(Soundscape.musicFor(Map.of(), true, DungeonRun.State.DEAD, "town", List.of(), null, true, true),
                "nothing named: whatever plays goes on");
    }

    /** Every cue the Audio block names is a track of the bank, on the music's knob; every place's loop is a sound. */
    @Test
    void everyTrackTheAudioBlockNamesIsMusic() {
        assertFalse(SETTINGS.audio().musicByPlace().isEmpty(), "the game has music by place");
        for (var where : SETTINGS.audio().musicByPlace().entrySet()) {
            var cue = BANK.find(where.getValue());
            assertNotNull(cue, where.getKey() + " plays " + where.getValue() + ", which is no sound");
            assertEquals(where.getValue(), cue.name(), where.getKey() + "'s track is named exactly");
            assertEquals(SoundBank.Channel.MUSIC, cue.channel(), where.getValue() + " is on the music's knob");
        }
        for (var where : SETTINGS.audio().ambienceByPlace().entrySet()) {
            var cue = BANK.find(where.getValue());
            assertNotNull(cue, where.getKey() + "'s loop " + where.getValue() + " is no sound");
            assertTrue(cue.positional(), "a loop rides him, so it is placed (and mono): " + where.getValue());
        }
        for (var moment : List.of("menu", "battle", "boss", "won", "descent", "default")) {
            assertTrue(SETTINGS.audio().musicByPlace().containsKey(moment), moment + " has music");
        }
    }

    // ---- what the game raises by name ----

    /** Each hero is heard as himself: his weapon, his pain, his death, and each of his four skills. */
    @Test
    void everyHeroIsHeardAsHimself() {
        for (var hero : SETTINGS.heroes()) {
            for (var moment : List.of("fired.", "hurt.", "died.")) {
                var cue = BANK.find(moment + hero.name());
                assertNotNull(cue, moment + hero.name());
                assertEquals(moment + hero.name(), cue.name(), moment + hero.name() + " is his own, not a fallback");
            }
            var skills = SETTINGS.skillsFor(hero.name());
            assertFalse(skills.isEmpty(), hero.name() + " has skills");
            for (var skill : skills) {
                var name = "cast." + hero.name() + "." + skill.key();
                assertNotNull(BANK.find(name), name);
                assertEquals(name, BANK.find(name).name(), name + " is its own");
            }
        }
    }

    /** Every creature that fights sounds when it strikes, is hit and dies -- its own, or the general one. */
    @Test
    void everyCreatureStrikesIsHitAndDies() {
        for (var kind : SETTINGS.monsters()) {
            assertNotNull(BANK.find("fired." + kind.name()), "fired." + kind.name());
            assertNotNull(BANK.find("died." + kind.name() + ".normal"), "died." + kind.name());
        }
    }

    /** The names written in the code, every one a sound of the bank. */
    @Test
    void everyMomentTheCodeNamesHasASound() {
        for (var name : List.of("fired", "equip", "drink", "coins", "screen_open", "screen_close", "quest_taken",
                "quest_done", "level_up", "loot", "step", "struck.Chest", "spawned.OpenGate")) {
            var cue = BANK.find(name);
            assertNotNull(cue, name);
            assertEquals(name, cue.name(), name + " is its own");
        }
        assertNull(BANK.find("skill.Q"), "a skill is heard by whose it is, not by its key: cast.<Hero>.<Key>");
    }

    // ---- the footstep ----

    @Test
    void theFootstepIsTheGroundPaintedUnderHim() {
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        var paint = new uz.dukeengine.dungeon.stage.StagePaint("field", List.of("gs", "r#"),
                Map.of("g", "ground/grass.png", "s", "ground/sand.png", "r", "ground/rock.png", "#", "#202020"),
                Map.of());

        assertEquals("step.grass", Soundscape.stepOn(paint, cell * 0.5f, cell * 0.5f));
        assertEquals("step.sand", Soundscape.stepOn(paint, cell * 1.5f, cell * 0.5f), "x is the column");
        assertEquals("step.rock", Soundscape.stepOn(paint, cell * 0.5f, cell * 1.5f), "y is the row");
        assertEquals("step", Soundscape.stepOn(paint, cell * 1.5f, cell * 1.5f), "a colour is no surface");
        assertEquals("step", Soundscape.stepOn(paint, -cell, cell * 9f), "off the map");
        assertEquals("step", Soundscape.stepOn(null, 0f, 0f), "and a floor nobody painted is the descent's stone");
        assertNotNull(BANK.find("step.rock"), "every surface falls back to a footstep");
    }

    // ---- the weapon's name ----

    @Test
    void aWeaponIsNamedAfterItsHolderUnlessItHasAName() {
        var arena = Dungeon.world("""
                ######
                #....#
                #....#
                ######
                """, SETTINGS);
        var game = arena.game();
        game.spawn("Skeleton", arena.dungeon(), 20f, 20f);
        game.runHeadless(1);
        var skeleton = game.getLogic().getObjects().stream()
                .filter(one -> one.getTemplate() != null && one.getTemplate().name().equals("Skeleton"))
                .findFirst().orElseThrow();

        var plain = new uz.dukeengine.combat.module.WeaponUpdate.Data(5f, 10f, 20);
        assertEquals("Skeleton", Dungeon.namedFor(skeleton, plain).name());
        assertEquals(10f, Dungeon.namedFor(skeleton, plain).attackRange(), 0.001f, "and nothing else changed");
        var named = new uz.dukeengine.combat.module.WeaponUpdate.Data(5f, 10f, 20,
                uz.dukeengine.core.module.DamageType.NORMAL, 0f, true, List.of(), 0, 0, 0, true, "Bow", List.of());
        assertEquals("Bow", Dungeon.namedFor(skeleton, named).name(), "a name its block wrote stays");
    }

    // ---- what was taken out ----

    /**
     * The three pieces of music and the level-up whose licences nobody could confirm are gone, and the credits say
     * nothing of audio left to confirm.
     */
    @Test
    void noAudioOfUnconfirmedLicenceIsShipped() {
        for (var gone : List.of("audio/music/dungeon_loop.ogg", "audio/music/sentinel.ogg",
                "audio/music/uncertainty.ogg")) {
            assertNull(SoundscapeTest.class.getClassLoader().getResource(gone), gone + " is gone");
        }
        var credits = Content.read("CREDITS.md");
        for (var line : credits.split("\n")) {
            if (line.contains("`audio/")) {
                assertFalse(line.toLowerCase(java.util.Locale.ROOT).contains("unconfirmed"),
                        "an audio row left unconfirmed: " + line);
            }
        }
    }
}
