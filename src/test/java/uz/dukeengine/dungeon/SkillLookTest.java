package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.skill.SkillBook;

/**
 * What every skill looks like going off, checked against what the client can
 * actually draw.
 *
 * <p>Two files that have to agree and that nothing makes agree. A skill names a
 * {@code Effect} block by a string; a block that is not there is a skill
 * with no effect, and a block that is there but describes nothing is a skill with
 * an effect that draws nothing. Neither refuses at run time — both are a skill
 * that goes off in silence, which reads as a bug in the skill rather than as a
 * missing line in a data file, and so is looked for in the wrong place.
 *
 * <p>Nothing here says anything looks good. What it pins is the handful of ways
 * this arrangement fails quietly, plus two shapes worth holding still: an effect
 * is SHORT, and the knock is SMALL.
 */
class SkillLookTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The skills a player casts: a hero's. A monster's has no key, no card and no look. */
    private static java.util.List<uz.dukeengine.rpg.skill.Skill> playersSkills() {
        var heroes = SETTINGS.heroes().stream().map(uz.dukeengine.dungeon.content.HeroLook::name)
                .collect(java.util.stream.Collectors.toSet());
        // A passive is never cast and draws nothing -- the knight's dragon blood -- so it names no Look.
        return SETTINGS.skills().stream().filter(skill -> heroes.contains(skill.heroTemplate()))
                // Nor his horse: what it looks like is his riding, no Look.
                .filter(skill -> !skill.effect().isPassive()
                        && skill.effect() != uz.dukeengine.rpg.skill.SkillEffect.MODULE // nor his dodge
                        && skill.effect() != uz.dukeengine.rpg.skill.SkillEffect.MOUNT)
                .toList();
    }

    /**
     * How long an effect may run.
     *
     * <p>A ceiling rather than a target, and a low one: the whole of a cast is
     * over before the next thing the player does. An effect he has to wait out is
     * one he learns to stop looking at, and the way this goes wrong is somebody
     * being pleased with a ring and slowing it down until it is furniture.
     *
     * <p>A standing MARK is exempt — it is not an effect so much as a promise, and
     * its whole point is the wait.
     */
    private static final float LONGEST_SECONDS = 0.8f;

    /** And how hard the camera may be knocked. See {@link #theKnockIsSmall}. */
    private static final float HARDEST_KNOCK = 2f;

    // ---- the two files agree ----

    /** Every skill in the shipped file says what it looks like. */
    @Test
    void everySkillSaysWhatItLooksLike() {
        assertFalse(SETTINGS.skills().isEmpty(), "the shipped file describes no skills at all");
        for (var skill : playersSkills()) {
            assertTrue(skill.hasLook(), skill.heroTemplate() + "'s " + skill.key()
                    + " goes off in silence: no Look line, so nothing is drawn for it");
        }
    }

    /** And what it names is a block that is really there. */
    @Test
    void everyLookNamedIsABlockThatExists() {
        var described = SETTINGS.effects().stream().map(look -> look.name()).toList();
        for (var skill : playersSkills()) {
            assertTrue(described.contains(skill.look()), skill.heroTemplate() + "'s "
                    + skill.key() + " looks like " + skill.look() + ", which nothing describes");
        }
    }

    /** And that block draws something, rather than being a name with nothing behind it. */
    @Test
    void everyLookNamedDrawsSomething() {
        var layered = SETTINGS.effectLayers().stream().map(DungeonSettings.EffectLayerArt::effect)
                .collect(java.util.stream.Collectors.toSet());
        for (var skill : playersSkills()) {
            assertTrue(layered.contains(skill.look()), skill.heroTemplate() + "'s " + skill.key() + " names "
                    + skill.look() + ", which has no layers and so draws nothing");
        }
    }

    // ---- and the two shapes worth holding still ----

    /**
     * The looks only a cutscene plays: what a story's STREAK lands with (the intro's meteor), under the letterbox, with
     * the player's hands off the keys. The day a meteor really landed (docs/plan/2026-10-15-seamless-campaign.md): it may
     * knock longer and harder than a fight's -- to CUTSCENE_SECONDS and CUTSCENE_KNOCK, the engine applying it whole
     * (SkillEffects.knock, scaled only by the player's own Silkinish setting).
     */
    private static java.util.Set<String> cutsceneLooks() {
        var looks = new java.util.HashSet<String>();
        for (var trigger : SETTINGS.story().triggers()) {
            for (var action : uz.dukeengine.dungeon.story.Action.of(trigger)) {
                if (action.act() == uz.dukeengine.dungeon.story.Action.Act.STREAK && !action.landed().isBlank()) {
                    looks.add(action.landed());
                }
            }
        }
        return looks;
    }

    private static final float CUTSCENE_SECONDS = 1.5f;
    private static final float CUTSCENE_KNOCK = 5f;

    /** The knock is over before the next thing the player does. */
    @Test
    void everyKnockIsOverQuickly() {
        var cinematic = cutsceneLooks();
        for (var look : SETTINGS.effects()) {
            assertTrue(look.shakeSeconds() <= (cinematic.contains(look.name()) ? CUTSCENE_SECONDS : LONGEST_SECONDS),
                    look.name() + " shakes the camera for " + look.shakeSeconds() + "s");
        }
    }

    /**
     * The knock is felt rather than seen.
     *
     * <p>A ceiling because this is the one number on the page with a real cost if
     * it is got wrong: a shake that can be SEEN is a shake a player asks you to
     * turn off, and past a point it is one that cannot be played through at all.
     * Every block in the shipped file is well under this; what the test is for is
     * the day somebody decides a meteor should really land.
     */
    @Test
    void theKnockIsSmall() {
        var cinematic = cutsceneLooks();
        for (var look : SETTINGS.effects()) {
            assertTrue(look.shakePower() <= (cinematic.contains(look.name()) ? CUTSCENE_KNOCK : HARDEST_KNOCK),
                    look.name() + " knocks the camera "
                    + look.shakePower() + " units, and past " + HARDEST_KNOCK
                    + " it is an earthquake rather than an impact");
        }
    }

    /** Nothing that opens a ring draws it in black. */
    @Test
    void everyRingIsSomeColour() {
        var looks = playersSkills().stream().map(uz.dukeengine.rpg.skill.Skill::look).collect(java.util.stream.Collectors.toSet());
        for (var layer : SETTINGS.effectLayers()) {
            if (!looks.contains(layer.effect()) || !"RING".equals(layer.fields().get("type"))) {
                continue;
            }
            assertFalse("0".equals(layer.fields().get("colourStart")), layer.effect() + "'s " + layer.name()
                    + " is drawn in black, which in a dark room is drawn not at all");
        }
    }

    // ---- what a mark is drawn ON ----

    private static GameObject knightIn(uz.dukeengine.game.DukeGame arena) {
        return arena.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Knight"))
                .findFirst().orElseThrow();
    }

    private static uz.dukeengine.game.DukeGame knightInARoom() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean edge = x == 0 || y == 0 || x == 39 || y == 29;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        var world = Dungeon.world(text.toString(), SETTINGS, Content.units());
        world.game().spawn("Knight", world.hero(), 150f, 150f);
        world.game().runHeadless(1);
        return world.game();
    }

    private static uz.dukeengine.rpg.skill.Skill skillOf(String hero, char key) {
        return SETTINGS.skillsFor(hero).stream().filter(skill -> skill.key() == key)
                .findFirst().orElseThrow();
    }

    // ---- and none of it reaches the fight ----

    /**
     * The look a skill names cannot change what the skill does.
     *
     * <p>The same claim {@code ProjectileEffectTest} makes about the burning, and
     * it has to be made again here because the {@code Look} line is on the SKILL
     * block rather than on an effect block — it travels in the same record as the
     * damage and the cooldown, and a record the simulation reads is a record that
     * can change what happens, with no way to tell by looking.
     */
    @Test
    void theLookCannotReachTheSimulation() {
        var with = DungeonSettings.load();
        var without = DungeonSettings.parse(withoutTheLooks(Content.data()));

        assertTrue(playersSkills().stream().allMatch(skill -> skill.hasLook()));
        assertFalse(with.combat().auraMarkLooks().isEmpty(), "the premise: the marks under an aura's reach are named");
        assertTrue(without.skills().stream().noneMatch(skill -> skill.hasLook()),
                "the stripped file still names looks");
        assertTrue(without.combat().auraMarkLooks().isEmpty(), "the stripped file still names marks");
        assertEquals(signature(with), signature(without),
                "a floor played differently once the skills were drawn with nothing");
    }

    /**
     * The same file with every skill's Look line cut out of it -- and an aura's TickFrames, written under its Look: the
     * beat it is worn at, which nothing else reads, and which a file may not name without the look it beats -- and the
     * Combat block's AuraMarkLooks, the marks laid under what an aura reaches. Its AuraMarkTickFrames stays: a block
     * that names no marks may still give the beat they would be laid at.
     */
    private static String withoutTheLooks(String file) {
        var kept = new StringBuilder();
        boolean underALook = false;
        for (var line : file.split("\n", -1)) {
            boolean look = line.trim().startsWith("Look = ");
            if (!look && !(underALook && line.trim().startsWith("TickFrames = "))
                    && !line.trim().startsWith("AuraMarkLooks = ")) {
                kept.append(line).append('\n');
            }
            underALook = look;
        }
        return kept.toString();
    }

    /** Two floors of one seed, as the simulation itself counts them. */
    private static String signature(DungeonSettings settings) {
        var session = Dungeon.newSession(31L, settings);
        var game = session.game();
        game.runHeadless(1);
        var line = new StringBuilder();
        for (int frame = 0; frame < 90; frame++) {
            game.runHeadless(1);
            line.append(game.getLogic().checksum()).append('|');
        }
        return line.toString();
    }
}
