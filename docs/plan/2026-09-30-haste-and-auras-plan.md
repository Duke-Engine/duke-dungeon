# The mages' haste and auras — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The summoner hastens the sturdiest of its own near it, or itself — every wait of its weapon divided by 1.75
for five seconds — and three passives lend everyone of their bearer's own side round it what they are worth: the
summoner's blow half as hard again, the healer's five points of mana a second, the Revenant's tenth of every blow back
as health. Each is a skill of the one skill system, received by the `SkillBook` every monster now carries, and drawn:
`Hasted` on the one hastened, a faint ring on each bearer as wide as its reach.

**Architecture:** `HASTE(Aim.SELF)` is a cast effect. The caster's book picks the one it is for (`sturdiestNear`: the
highest level, then the most health at its fullest, then the nearer, then the smaller id, of its own side, carrying a
book, in `Range` middle to middle and in plain sight, itself among them) and writes `hasteFrames`/`hastePercent` into
that one's book — which is now a `RateOfFireModifier`, so the engine divides each wait by it and cuts it to whole
frames. `DAMAGE_AURA`, `MANA_AURA` and `LIFESTEAL_AURA` are passives that `SkillEffect.isAura()` names. Nothing is
pushed to the ones they reach: `SkillBook.auraOn(creature, kind)` asks, at the moment a figure is used, for the
strongest of that kind among the living bearers of the creature's side whose level has opened it, whose `Radius` it
stands in and whose sight line to it is clear — from `damageMultiplier` (each blow and skill), `drink` (each blow
landed, the meteor's blast now among them) and the mana trickle (each frame the pool is not full). The book plays each
open aura's look on its bearer every `TickFrames`, staggered by its id; `Main.measureLooks` makes that look last two
ticks and `Main.layerOf` renews every look worn as a state.

**Tech Stack:** Java 25, JUnit 5, Gradle (`./gradlew test`), `.duke` data read by the engine's record reader,
duke-engine 0.8.0 from the checkout beside this one — nothing new asked of it. (Verified on 0.7.0 at 1bb64fa8; its
imports were then moved to 0.8.0's packages, `combat.module` and `combat.event`.)

**Spec:** `docs/plan/2026-09-30-haste-and-auras.md`

**Verified:** every task below was carried out in a scratch worktree at `a8570b6` — master `e907d9f` with
`key-to-the-keep` (`034b26e`) and `monsters-grow` (`8daf05e`) merged into it — against a snapshot of the engine: each
task's new tests seen failing as written here (each task's test files applied onto the task before it, run again on
the finished chain), then passing, then the whole suite at each task's commit. The code in the blocks is the code that
ran: every block was checked, by script, to stand word for word in its task's commit. Whole suite: 955 before, then
**967, 979, 985, 994, 1000**, all passing.

## Global Constraints

- Lock-step: a haste's choice is a total order ending in the object id, over `world.objectsInRange` (the objects in
  the order they were made); an aura is a maximum of whole percents and tenths, the same in any order, behind a sight
  line walked on integers (`SightLine.clear`); the haste's frames are counted down; mana comes by the book's carry in
  whole points; the waits are the engine's own `(int) (frames / rate)`. The looks are events (`world.effect`), out of
  the checksum. Nothing asks the clock or walks a hash; everything happens on the simulation thread.
- Names and numbers come from data: the haste's and each aura's figures, `ManaRegen` on a skill, each look and its
  beat (`TickFrames`), in the three mages' files; which creatures carry a book, in their own files.
- Every combat feature is a skill of the one skill system: one new cast effect and three new passives, the `SkillBook`
  the one thing that receives them. No module is attached for a haste — the book every monster carries is the
  `RateOfFireModifier` — and nothing is written into the creatures an aura reaches.
- `../duke-engine` is never edited, and no engine request is needed: `RateOfFireModifier`, `DamageModifier`,
  `world.effect(name, thing)`, a layer measured in `REACH` that `Follows`, and the renewing flag `Main.layerOf` already
  hands the client are all there (see *Where the spec and the code part* for the one thing a lying ring cannot do).
- Each task is committed on its own once its tests pass: the subject, a blank line, and
  `Co-Authored-By: <the model that did the work> <noreply@anthropic.com>`.
- **Pieces 3 and 4 first.** This plan is written against master once `key-to-the-keep` and `monsters-grow` are merged
  into it: it reads piece 4's `LevelBonus.levelOf`, `Spawner.scale`, the mages' pools, the brain's first open, ready,
  paid-for skill in file order and the fire mage's meteor, and piece 3's keep puts the two summoners and two healers on
  every floor that `SkillLookTest`'s real floor plays. Its line numbers (`~`) are from that merge.
- `MonsterBrain` is not touched: a haste is cast as anything the brain throws at him is, and a passive is skipped
  wherever it is written.

## Files

| File | What it is |
|---|---|
| `src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java` | + `HASTE`, `DAMAGE_AURA`, `MANA_AURA`, `LIFESTEAL_AURA`; `isAura()` |
| `src/main/java/uz/dukeengine/dungeon/skill/Skill.java` | + `manaRegen`, last; notes on `range`, `radius`, `boostPercent`, `durationFrames` |
| `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` | a `RateOfFireModifier`: the haste held and counted down, `sturdiestNear`; `auraReach`, `auraOn`; `damageMultiplier`, `drink` and the trickle ask it; `wearTheAuras` |
| `src/main/java/uz/dukeengine/dungeon/combat/FallingUpdate.java` | the meteor's blast drinks, for each it hurts |
| `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` | the file's rules for a haste and an aura; the passive-`Look` rule relaxed for an aura |
| `src/main/java/uz/dukeengine/dungeon/Main.java` | `rangeOf` learns the four; `measureLooks` gives an aura's look two ticks; `layerOf` renews every look worn as a state |
| `src/main/resources/data/units/skeleton_summoner.duke` | W (`HASTE`, *Shiddat*), E (`DAMAGE_AURA`, *Qudrat*); the `Hasted` and `MightAura` looks; its notes |
| `src/main/resources/data/units/skeleton_healer.duke` | W (`MANA_AURA`, *Sehr buloqi*); the `ManaAura` look; its note |
| `src/main/resources/data/units/revenant.duke` | a `SkillBook`; Q (`LIFESTEAL_AURA`, *Qon aurasi*); the `BloodAura` look; its notes |
| `src/main/resources/data/units/skeleton.duke`, `stalker.duke`, `brute.duke`, `runner.duke` | a `SkillBook` each; the Skeleton's note on what aims at nothing |
| `src/test/java/uz/dukeengine/dungeon/HasteTest.java` | **new** — whom, what it does, when, seen, the file |
| `src/test/java/uz/dukeengine/dungeon/AuraTest.java` | **new** — might, mana, lifesteal, worn, reach, the file, the shipped mages, two worlds alike |
| `src/test/java/uz/dukeengine/dungeon/DungeonEffectLayerTest.java` | + each aura's ring lies where its reach ends |
| `src/test/java/uz/dukeengine/dungeon/ai/MonsterSummoningTest.java` | the summoning's lines read off the summoner with its summoning alone |
| `src/test/java/uz/dukeengine/dungeon/content/DungeonSettingsTest.java` | the ring switch learns the four |
| `src/test/java/uz/dukeengine/dungeon/skill/ManaTest.java`, `SkillRanksTest.java`, `SkillTest.java` | the `Skill` records they write out whole gain `manaRegen` |
| `src/test/java/uz/dukeengine/dungeon/StunTest.java`, `SkillLookTest.java` | the stun no longer renews alone; stripping the looks strips an aura's beat under its look |

---

### Task 1: The summoner hastens the sturdiest of its own near it, or itself — and every monster carries a book

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java` (the class note, ~line 13; `HASTE` after
  `SUMMON`, ~line 172)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` (imports; class note; fields after
  `guardPercent`, ~line 73; `getHasteFrames` after `getBoostFrames`, ~line 360; `case HASTE` after `case SUMMON` in
  `apply`, ~line 655; `sturdiestNear` before `enemiesWithin`, ~line 852; `rateOfFireMultiplier` after
  `damageMultiplier`, ~line 1139; the countdown in `update`, ~line 1237)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/Skill.java` (the `range`, `boostPercent` and `durationFrames`
  notes)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate()`, the `switch` over each
  skill's effect, ~line 798)
- Modify: `src/main/java/uz/dukeengine/dungeon/Main.java` (`rangeOf`, ~lines 819–831)
- Modify: `src/main/resources/data/units/skeleton_summoner.duke`, `skeleton.duke`, `stalker.duke`, `brute.duke`,
  `runner.duke`, `revenant.duke`
- Modify: `src/test/java/uz/dukeengine/dungeon/content/DungeonSettingsTest.java` (the switch in
  `everySkillCarriesTheFigureItsRingIsDrawnFrom`, ~line 458)
- Modify: `src/test/java/uz/dukeengine/dungeon/ai/MonsterSummoningTest.java` (`summoningWith`, ~line 110)
- Test: `src/test/java/uz/dukeengine/dungeon/HasteTest.java` (**new**)

**Interfaces:**
- Consumes: piece 4's `LevelBonus.levelOf(GameObject)` and `Spawner.scale(GameObject, int, DungeonSettings)`;
  `SightLine.clear(GameObject, GameObject)`; `World.objectsInRange(Coord3D, float, Predicate)`;
  `Facing.turnToward`; `WeaponFired`; the engine's `RateOfFireModifier` (`uz.dukeengine.combat.module`), which
  `WeaponUpdate` multiplies into every wait; `ShippedBlock`, `Dungeon.world`, `DukeGame.getLogic().drainEvents()`.
- Produces: `SkillEffect.HASTE` (`Aim.SELF`, cast); `SkillBook implements RateOfFireModifier` —
  `float rateOfFireMultiplier()`, `int getHasteFrames()`; the summoner's W; a `SkillBook` on every monster;
  `HasteTest` with `room(DungeonSettings, int wallColumn, One...)` and its `Room` record.

- [ ] **Step 1: Write the failing test** — `src/test/java/uz/dukeengine/dungeon/HasteTest.java`:

```java
package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.StatusUpdate;

/**
 * The summoner's haste: the sturdiest of its own near it, or itself, fires three quarters again as fast for five
 * seconds -- every wait of its weapon divided by 1.75 and cut to whole frames, the engine's own rule.
 *
 * <p>Whom it hastens is the book's to pick, as a strike cast at nothing picks its victim, so it is asked of a haste
 * cast by hand in a room with nobody to fight; when the brain casts it is asked of a fight.
 */
class HasteTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String SUMMONER = "SkeletonSummoner";
    private static final int NO_WALL = -1;

    /** An open room forty cells by thirty; with a wall down one column, and a doorway at its far end. */
    private static String room(int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean edge = x == 0 || y == 0 || x == 39 || y == 29;
                boolean wall = x == wallColumn && y != 27;
                text.append(edge || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** The summoner's haste as shipped: its W. */
    private static Skill haste() {
        return SETTINGS.skillsFor(SUMMONER).stream().filter(skill -> skill.key() == 'W').findFirst().orElseThrow();
    }

    /** One creature to place: its kind and where. */
    private record One(String kind, float x, float y) {
    }

    private static One one(String kind, float x, float y) {
        return new One(kind, x, y);
    }

    /** A room, and the dungeon's own standing in it, each in the order it was made. */
    private record Room(DukeGame game, List<GameObject> ones) {

        GameObject get(int index) {
            return ones.get(index);
        }

        /** The first of them casts its haste by hand, at nothing: whether it went. */
        boolean castsItsHaste() {
            return bookOf(ones.getFirst()).cast('W', 1, null, null);
        }

        /** The one of them a haste is burning on: exactly one, or the test says who else. */
        GameObject hastened() {
            var burning = ones.stream().filter(one -> bookOf(one).getHasteFrames() > 0).toList();
            assertEquals(1, burning.size(), "hastened: " + burning);
            return burning.getFirst();
        }
    }

    /** A room holding these of the dungeon's own, in this order, and nobody to fight. */
    private static Room room(DungeonSettings settings, int wallColumn, One... ones) {
        var arena = Dungeon.world(room(wallColumn), settings);
        var game = arena.game();
        for (var one : ones) {
            game.spawn(one.kind(), arena.dungeon(), one.x(), one.y());
        }
        game.runHeadless(1);
        return new Room(game, game.getLogic().getObjects().stream().filter(object -> object.getBody() != null)
                .toList());
    }

    private static SkillBook bookOf(GameObject creature) {
        return creature.findModule(SkillBook.class);
    }

    // ---- whom ----

    /**
     * The sturdiest of its own near it: a Brute over two Skeletons of its level, by its health at its fullest -- and it
     * turns to the one it hastens and is seen casting.
     */
    @Test
    void itHastensTheSturdiestOfItsOwnNearIt() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f),
                one("Brute", 100f, 180f), one("Skeleton", 100f, 120f));
        var summoner = room.get(0);

        assertTrue(room.castsItsHaste(), "the premise: it cast its haste");
        var fired = room.game().getLogic().drainEvents();

        assertEquals(room.get(2), room.hastened(), "the Brute");
        assertEquals(haste().durationFrames(), bookOf(room.get(2)).getHasteFrames(), "for its DurationFrames");
        assertEquals((float) (Math.PI / 2), summoner.getOrientation(), 0.001f, "turned to the Brute, up the column");
        assertTrue(fired.stream().anyMatch(event -> event instanceof WeaponFired shot
                        && shot.shooter().equals(summoner.getId())),
                "and seen casting: " + fired);
    }

    /** A level outweighs a body: a Skeleton a level up is hastened over the Brute. */
    @Test
    void aLevelUpOutweighsTheBiggerBody() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Brute", 100f, 180f),
                one("Skeleton", 130f, 150f));
        Spawner.scale(room.get(2), 2, SETTINGS);

        assertTrue(room.castsItsHaste(), "the premise: it cast its haste");

        assertEquals(room.get(2), room.hastened(), "the Skeleton at level 2 over the Brute at 1");
    }

    /** Of two alike, the nearer; of two alike and as near, the one the world made first -- whichever side it is on. */
    @Test
    void ofTwoAlikeTheNearerAndOfTwoAsNearTheFirst() {
        var nearer = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 100f, 190f),
                one("Skeleton", 120f, 150f));
        assertTrue(nearer.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(nearer.get(2), nearer.hastened(), "the one 20 away over the one 40 away");

        for (boolean leftFirst : new boolean[] {true, false}) {
            var asNear = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f),
                    one("Skeleton", leftFirst ? 70f : 130f, 150f), one("Skeleton", leftFirst ? 130f : 70f, 150f));
            assertTrue(asNear.castsItsHaste(), "the premise: it cast its haste");
            assertEquals(asNear.get(1), asNear.hastened(), "the one made first, 30 away as the other is");
        }
    }

    /**
     * Only in its reach and its plain sight: a Brute at its Range, middle to middle, is hastened; one a step past it,
     * or behind stone, is not -- and with nobody else, it hastens itself.
     */
    @Test
    void nobodyBehindStoneOrPastItsRangeItselfInstead() {
        float range = haste().range();
        var atItsRange = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Brute", 100f - range, 150f));
        assertTrue(atItsRange.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(atItsRange.get(1), atItsRange.hastened(), "the Brute at " + range);

        var outOfIt = room(SETTINGS, 12, one(SUMMONER, 100f, 150f), one("Brute", 99f - range, 150f),
                one("Brute", 150f, 150f));
        assertTrue(outOfIt.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(outOfIt.get(0), outOfIt.hastened(), "one Brute a step past its Range, one behind stone");
    }

    /** Alone, it hastens itself -- and so it does with a Range of 0, a Brute beside it. */
    @Test
    void aloneOrWithNoRangeItHastensItself() {
        var alone = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        assertTrue(alone.castsItsHaste(), "never refused for want of someone to hasten");
        assertEquals(alone.get(0), alone.hastened());

        var noReach = DungeonSettings.parse(ShippedBlock.of(SUMMONER).with("Range", 0).text());
        var beside = room(noReach, NO_WALL, one(SUMMONER, 100f, 150f), one("Brute", 110f, 150f));
        assertTrue(beside.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(beside.get(0), beside.hastened(), "a Range of 0 hastens only its caster");
    }

    // ---- what it does ----

    /** The shipped units with the Rogue's bow reaching nothing: whatever he loses, a monster took. */
    private static String unarmedRogue() {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text());
    }

    /** The shipped files with the summoner noticing nobody: it does only what a test does for it. */
    private static DungeonSettings unseeingSummoner() {
        return DungeonSettings.parse(ShippedBlock.of(SUMMONER).with("SenseRadius", 1).with("ChaseRadius", 1)
                .with("AlertRadius", 0).text());
    }

    private static GameObject first(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    /** Runs a frame at a time until {@code hero} loses health: whether he did, within {@code frames}. */
    private static boolean untilStruck(DukeGame game, GameObject hero, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            float health = hero.getBody().getHealth();
            game.runHeadless(1);
            if (hero.getBody().getHealth() < health) {
                return true;
            }
        }
        return false;
    }

    /**
     * A hastened Skeleton waits 17 frames between blows for its 30 -- 30 / 1.75, cut to whole frames -- through the
     * haste's 150 frames, and 30 again after: a Rogue who cannot answer at its reach, and a summoner noticing nobody
     * that hastens it by hand once it is striking.
     */
    @Test
    void aHastenedSkeletonWaitsSeventeenFramesForItsThirtyThroughTheHaste() {
        var arena = Dungeon.world(room(NO_WALL), unseeingSummoner(), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 162f, 150f);
        game.spawn(SUMMONER, arena.dungeon(), 162f, 110f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = first(game, "Rogue");
        var skeleton = first(game, "Skeleton");
        int lasts = haste().durationFrames();

        assertTrue(untilStruck(game, hero, 150), "the premise: the Skeleton struck him");
        assertTrue(bookOf(first(game, SUMMONER)).cast('W', 1, null, null), "the premise: it cast its haste");
        assertEquals(lasts, bookOf(skeleton).getHasteFrames(), "the premise: on the Skeleton");
        int hastedAt = game.getLogic().getFrame();
        var blows = new ArrayList<Integer>();
        while (game.getLogic().getFrame() - hastedAt < lasts + 90) {
            if (untilStruck(game, hero, 1)) {
                blows.add(game.getLogic().getFrame() - hastedAt);
            }
        }

        int quick = 0;
        int plain = 0;
        for (int i = 0; i + 1 < blows.size(); i++) {
            boolean hasted = blows.get(i) <= lasts;
            assertEquals(hasted ? 17 : 30, blows.get(i + 1) - blows.get(i),
                    "the wait after its blow " + blows.get(i) + " frames in: " + blows);
            quick += hasted ? 1 : 0;
            plain += hasted ? 0 : 1;
        }
        assertTrue(quick >= 7 && plain >= 2, "the premise: blows through the haste and after it: " + blows);
    }

    /**
     * A second haste while one burns starts it again at the newer figures -- never stacked: two summoners hasten one
     * Brute two seconds apart, and it has the whole 150 frames again and fires 1.75 times as fast, not faster.
     */
    @Test
    void aSecondHasteStartsItAgainAndNeverStacks() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one(SUMMONER, 160f, 150f),
                one("Brute", 130f, 150f));
        var brute = bookOf(room.get(2));
        int lasts = haste().durationFrames();

        assertTrue(room.castsItsHaste(), "the premise: the first cast its haste");
        assertEquals(lasts, brute.getHasteFrames());
        room.game().runHeadless(60);
        assertEquals(lasts - 60, brute.getHasteFrames(), "the premise: two seconds of it gone");

        assertTrue(bookOf(room.get(1)).cast('W', 1, null, null), "the premise: the second cast its haste");
        assertEquals(lasts, brute.getHasteFrames(), "started again, not added to");
        assertEquals(1f + haste().boostPercent() / 100f, brute.rateOfFireMultiplier(), 0.0001f,
                "and as fast as one haste makes it");
    }

    /** It spends its ManaCost from the summoner's pool, and is refused, spending nothing, stunned or unpaid for. */
    @Test
    void itSpendsItsCostAndIsRefusedStunnedOrUnpaidFor() {
        var paid = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        var book = bookOf(paid.get(0));
        Spawner.scale(paid.get(0), 1, SETTINGS);
        int full = book.getMana();

        assertTrue(paid.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(25, haste().manaCost(), "the premise: it costs 25");
        assertEquals(full - 25, book.getMana(), "what it spent");

        var stunned = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        stunned.get(0).findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 30);
        assertFalse(stunned.castsItsHaste(), "cast stunned");
        assertTrue(bookOf(stunned.get(0)).isReady('W'), "and a cast that never went off spent its cooldown");
        assertEquals(0, bookOf(stunned.get(0)).getHasteFrames(), "and hastened it");

        var poor = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        Spawner.scale(poor.get(0), 1, SETTINGS);
        bookOf(poor.get(0)).resize(24, 0);
        assertFalse(poor.castsItsHaste(), "cast with 24 for a cost of 25");
        assertEquals(24, bookOf(poor.get(0)).getMana(), "and something was taken");
        assertTrue(bookOf(poor.get(0)).isReady('W'), "and its cooldown was started");
    }

    // ---- when ----

    /**
     * In a fight -- him inside its SkillDistance and in plain sight -- its haste follows its summoning; with nobody to
     * fight, never. The one it hastens is of its own, whoever stood sturdiest when it went.
     */
    @Test
    void inAFightItsHasteFollowsItsSummoningAndNeverInAnEmptyRoom() {
        var arena = Dungeon.world(room(NO_WALL), SETTINGS, unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(SUMMONER, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var book = bookOf(first(game, SUMMONER));

        game.runHeadless(90);

        assertFalse(book.isReady('Q'), "the premise: it summoned");
        assertFalse(book.isReady('W'), "and then hastened");
        assertTrue(game.getLogic().getObjects().stream().anyMatch(object -> object.getPlayerIndex()
                        == arena.dungeon().getIndex() && bookOf(object) != null && bookOf(object).getHasteFrames() > 0),
                "someone of its own is hastened");

        var empty = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        empty.game().runHeadless(400);
        assertTrue(bookOf(empty.get(0)).isReady('W'), "it hastened in an empty room");
    }

    // ---- the file ----

    /** Every monster carries a SkillBook: what a haste or an aura gives it is held there. */
    @Test
    void everyMonsterCarriesABook() {
        var bookless = new ArrayList<String>();
        for (var record : Content.records(Content.units(), "units")) {
            if (record instanceof Monster monster
                    && monster.modules().stream().noneMatch(SkillBook.Data.class::isInstance)) {
                bookless.add(monster.name());
            }
        }
        assertEquals(List.of(), bookless);
    }

    /** A haste needs a BoostPercent of at least 1 and DurationFrames, or the file is refused, saying so. */
    @Test
    void aHasteWithoutItsFiguresIsRefused() {
        var block = ShippedBlock.of(SUMMONER).text();
        for (var wrong : new String[][] {{"BoostPercent", "75"}, {"DurationFrames", "150"}}) {
            var text = block.replace("      " + wrong[0] + " = " + wrong[1] + "\n", "      " + wrong[0] + " = 0\n");
            assertNotEquals(block, text, "the premise: the haste's " + wrong[0] + " was made 0");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(text), wrong[0]);
            assertTrue(refused.getMessage().contains(wrong[0]), refused.getMessage());
        }
    }

    /**
     * As shipped: the summoner's W, written after its summoning -- three quarters again as fast for five seconds, one
     * of its own as far as 60 from it, 25 mana and the summoning's twelve seconds between -- and its ring drawn round
     * it as far as its Range.
     */
    @Test
    void asShippedTheSummonersHaste() {
        var haste = haste();

        assertEquals(List.of(SkillEffect.SUMMON, SkillEffect.HASTE),
                SETTINGS.skillsFor(SUMMONER).stream().map(Skill::effect).limit(2).toList(),
                "its summoning, then its haste");
        assertEquals(List.of(75, 150, 25, 360, 1), List.of(haste.boostPercent(), haste.durationFrames(),
                haste.manaCost(), haste.cooldownFrames(), haste.maxRank()));
        assertEquals(60f, haste.range(), 0.001f);
        assertEquals("Shiddat", haste.name());
        var ring = Main.rangeOf(haste, 5f);
        assertEquals(SkillRange.Shape.AROUND_HIM, ring.shape());
        assertEquals(haste.range(), ring.reach(), 0.001f);
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.HasteTest"`
Expected: FAIL — compilation error, `cannot find symbol`: `method getHasteFrames()` and `method rateOfFireMultiplier()`
in `SkillBook`, `variable HASTE` in `SkillEffect`.

- [ ] **Step 3: The effect** — `SkillEffect.java`. The class note's list of shapes, in place of its "and two that no
  hero has and a monster does: mend one of your own, and call up more of them.":

```java
 * somewhere else, be briefly stronger, be briefly harder to kill -- and three that
 * no hero has and a monster does: mend one of your own, call up more of them, and
 * make one of them strike faster.
```

and the constant, after `SUMMON(Aim.SELF),`:

```java
    /**
     * Make one of your own strike faster, for a while.
     *
     * <p>The sturdiest of the caster's own side near it -- the highest level, then the most health at its fullest,
     * then the nearer, then the one the world made first -- that lives, carries a {@code SkillBook}, and stands within
     * {@code Range} of the caster, middle to middle, and in its plain sight; the caster itself when nothing sturdier
     * stands near. Every wait of that one's weapon is divided by 1 + {@code BoostPercent}/100 and cut to whole frames,
     * the engine's rule for a rate of fire, for {@code DurationFrames}; a second haste while it burns starts it again
     * at the newer figures. Aimed at nothing, so never refused for want of someone to hasten.
     */
    HASTE(Aim.SELF),
```

- [ ] **Step 4: The book holds it, and is a rate of fire** — `SkillBook.java`. Two imports, `SightLine` after
  `Facing`'s and `RateOfFireModifier` after `DamageModifier`'s:

```java
import uz.dukeengine.dungeon.ai.Facing;
import uz.dukeengine.dungeon.ai.SightLine;
```

```java
import uz.dukeengine.combat.module.DamageModifier;
import uz.dukeengine.combat.module.RateOfFireModifier;
```

The class note's paragraph on `DamageModifier` gains a last sentence, its determinism paragraph names the haste's
choice, and the class implements the seam:

```java
 * that expires is the same thing without the trap. A {@code HASTE} is held the
 * same way, in the hastened's own book, which is a {@link RateOfFireModifier} for
 * it -- the seam a hero's boots ride through {@code AttackSpeed} -- and every
 * monster carries one from birth.
 *
 * <p>Determinism: cooldowns are frames counted down, never seconds; the victim of
 * a {@code STRIKE} is the nearest enemy with ties broken by object id, and the one
 * a {@code HASTE} is for the sturdiest near, down to the object id; a
 * {@code DASH} walks with {@link StrictMath}. Nothing here asks the clock.
 */
@ModuleGroup({ModuleGroups.COMBAT, ModuleGroups.MOVEMENT, ModuleGroups.EFFECT, ModuleGroups.BODY})
public final class SkillBook extends UpdateModule implements DamageModifier, RateOfFireModifier, WeaponHold {
```

The fields, after `private int guardPercent;`:

```java
    /**
     * Frames of a {@code HASTE} left on this one -- whoever cast it -- and how much faster its weapon fires while it
     * lasts. Counted down here as {@code EMPOWER}'s are; a second haste starts them again at its own figures.
     */
    private int hasteFrames;
    private int hastePercent;
```

Its reading, after `getBoostFrames()`:

```java
    /** Frames of haste left on it, whoever cast it; 0 when none burns. */
    public int getHasteFrames() {
        return hasteFrames;
    }
```

The cast, in `apply`, after `case SUMMON -> { ... }`:

```java
            case HASTE -> {
                // The sturdiest of its own near it, or itself -- never nobody. Held in the hastened's own book and
                // counted down there, as EMPOWER is in its caster's: a second haste starts it again, never stacks.
                var hastened = sturdiestNear(owner, world, skill.range());
                var its = hastened.findModule(SkillBook.class);
                its.hasteFrames = skill.durationFrames();
                its.hastePercent = skill.boostAt(level);
                // It turns to the one it hastens and is seen casting, as a mending is.
                Facing.turnToward(owner, hastened);
                world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                        owner.getPosition(), hastened.getPosition()));
            }
```

(`rememberTheCast` has no case for it and marks nothing: a monster's cast marks are drawn by nobody, and what the
hastened wears comes in Task 5.)

Whom, before `enemiesWithin(GameObject, World, float)`:

```java
    /**
     * Whom a haste is for: of {@code caster}'s own side, living, carrying a book, within {@code range} of it, middle to
     * middle, and in its plain sight -- itself always among them -- the one of the highest level, then the most health
     * at its fullest, then the nearer, then the one the world made first. Sturdiest rather than hardest-hitting:
     * health is on every body, and a blow's worth is not. A total order ending in the object id, so every machine
     * picks the same one.
     */
    private static GameObject sturdiestNear(GameObject caster, World world, float range) {
        var here = caster.getPosition();
        var sturdier = java.util.Comparator.<GameObject>comparingInt(LevelBonus::levelOf)
                .thenComparingDouble(one -> one.getBody().getMaxHealth())
                .thenComparingDouble(one -> -here.distance(one.getPosition()))
                .thenComparingInt(one -> -one.getId().value());
        return world.objectsInRange(here, range, one -> one.getPlayerIndex() == caster.getPlayerIndex()
                        && one.getBody() != null && !one.isEffectivelyDead()
                        && one.findModule(SkillBook.class) != null && SightLine.clear(caster, one))
                .stream().max(sturdier).orElse(caster);
    }
```

The seam, after `damageMultiplier()`:

```java
    /**
     * How much faster its weapon fires while a {@code HASTE} burns on it: the engine divides every wait by this and
     * cuts it to whole frames -- 30 frames at 1.75 are 17. Read at each shot; 1 when none burns.
     */
    @Override
    public float rateOfFireMultiplier() {
        return hasteFrames > 0 ? 1f + hastePercent / 100f : 1f;
    }
```

And the countdown, in `update()`, after the guard's:

```java
        if (guardFrames > 0) {
            guardFrames--;
        }
        if (hasteFrames > 0) {
            hasteFrames--;
        }
        turnTheWhirlwind();
```

- [ ] **Step 5: What the file must say of it** — `DungeonSettings.validate()`, in the `switch (skill.effect())` after
  `case SUMMON -> { ... }`:

```java
                case HASTE -> require(skill.boostPercent() >= 1 && skill.durationFrames() > 0,
                        skill.heroTemplate() + "'s Skill " + skill.key() + " hastens nobody: it needs a BoostPercent"
                                + " of at least 1 and DurationFrames for it to last");
```

(A `Range` of 0 is let be: it hastens only its caster. `Aim.SELF`, so piece 4's rule that an aimed skill's `Range`
reach the band's far end does not ask it; the band itself is asked of it, as of every skill cast at him.)

- [ ] **Step 6: Its ring** — `Main.rangeOf`. Round him, as far as its `Range`:

```java
            // A summoning calls them up round him, as far out as its Radius; a haste finds one of
            // his own round him, as far out as its Range.
            case AREA_DAMAGE, SUMMON, HASTE -> uz.dukeengine.client3d.SkillRange.Shape.AROUND_HIM;
```

```java
            case AREA_DAMAGE, SUMMON -> skill.radius();
            case HASTE -> skill.range();
```

and the test's own switch over every effect, in `DungeonSettingsTest.everySkillCarriesTheFigureItsRingIsDrawnFrom`,
learns it the same way (the switch is exhaustive and would not compile without it):

```java
                case AREA_DAMAGE, SUMMON -> skill.radius();
                case HASTE -> skill.range();
```

- [ ] **Step 7: Its notes on `Skill`** — `Skill.java`, the record's `@param` lines for `range`, `boostPercent` and
  `durationFrames`:

```java
 * @param range         how far {@code STRIKE} can find a victim, and a {@code HASTE}
 *     the one it hastens, middle to middle
```

```java
 * @param boostPercent  what this skill is worth in percent — damage added by
 *     {@code EMPOWER}, damage avoided by {@code GUARD}, the share of every blow
 *     a {@code LIFESTEAL} gives back as health, and how much faster a {@code HASTE}
 *     makes a weapon fire. One field because it is one question ("how much is it
 *     worth?") asked of mirrored effects and of a passive that is worth a share of
 *     what its bearer does
 * @param boostPerLevel that percentage's growth per level
 * @param durationFrames how long it lasts: {@code EMPOWER}'s extra damage,
 *     {@code GUARD}'s protection, a {@code HASTE}, or how long an
 *     {@code AREA_DAMAGE} goes on landing. Zero for a skill that happens and is over
```

- [ ] **Step 8: The summoner's W, and a book on every monster** — `skeleton_summoner.duke`. Its first note:

```
; The purple one, and it brings more of them. Frail and slow like the other two, with a
; mote of dark for its ordinary shot; its skills open rifts that swordsmen and archers
; climb out of, and make the sturdiest of its own strike faster -- see Monster
; SkeletonSummoner and the Q and W skills of the SkeletonSummoner below.
```

the last line of its Monster note, and its pool's note:

```
  ; calls up. Its rifts open, it makes the sturdiest of its own near it strike faster.
```

```
  ; What it opens its rifts and hastens out of, in the hero's words and units, as the
  ; Skeleton Mage's: enough to call up its four at its cooldown through a fight of a
  ; minute or more, and to hasten as its trickle allows.
```

and the W, after the summoning's `End` (which gains its comma):

```
      Name = Chaqiruv
    End,
    Skill
      Key = W
      ; One of its own made to strike faster: the sturdiest near it -- the highest level,
      ; then the most health, then the nearer -- or itself, when nothing sturdier stands
      ; near. Cast as its summoning is, whenever he is inside its SkillDistance and in
      ; plain sight: after the summoning in a fight, and never in an empty room.
      Effect = HASTE
      ; Three quarters again as fast -- every wait of its weapon divided by 1.75 -- for
      ; five seconds. A second haste while it burns starts the five seconds again.
      BoostPercent = 75
      DurationFrames = 150
      ; How far from it the one it hastens may stand: past its band, to the ones
      ; fighting him.
      Range = 60
      ; The summoning's twelve seconds between castings.
      CooldownFrames = 360
      ManaCost = 25
      MaxRank = 1
      Name = Shiddat
    End
  ]
End
```

`skeleton.duke`, `stalker.duke`, `brute.duke`, `runner.duke` and `revenant.duke`: the same three lines after each one's
`Swing` block — after the weapon, so the weapon reads a haste before the book counts it down in a frame:

```
    Swing
    End,
    ; Every monster carries one, casting or not: a summoner's haste is held in it.
    SkillBook
    End,
```

and `skeleton.duke`'s note on what aims at nothing (~line 78):

```
; needs no SkillDistance, and what goes off round the caster -- a summoning, a
; haste -- aims at nothing, so its Range is not held to the band.
```

- [ ] **Step 9: The summoning's tests read its lines off the summoning alone** — `MonsterSummoningTest.java`. The haste
  writes a `DurationFrames` and a `CooldownFrames` of its own, and `ShippedBlock.with` asks for a line written once,
  so `summoningWith` edits the summoner with its summoning the only skill it has — which is what those tests measure:

```java
    /** The shipped summoner, with its summoning's numbers changed: {@code summons} as the file writes it. */
    private static DungeonSettings summoningWith(String summons, int most, int lasts, int percent,
            int cooldown) {
        return DungeonSettings.parse(summoningAlone().with("Summons", summons)
                .with("MaxSummoned", most).with("DurationFrames", lasts)
                .with("SummonExperiencePercent", percent).with("CooldownFrames", cooldown).text());
    }

    /**
     * The shipped summoner with its summoning the only skill it has: the skills written after it -- its haste -- set
     * lines of the same names, and these tests are the summoning's.
     */
    private static ShippedBlock summoningAlone() {
        var block = ShippedBlock.of(SUMMONER).text();
        return new ShippedBlock(block.substring(0, block.indexOf("    End,\n    Skill\n")) + "    End\n  ]\nEnd\n");
    }
```

- [ ] **Step 10: Run the test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.HasteTest"`
Expected: PASS, 12 tests.

- [ ] **Step 11: Run the whole suite**

Run: `./gradlew test`
Expected: PASS, 967 tests. (Without Step 9, 9 of `MonsterSummoningTest`'s 15 fail: `the block sets DurationFrames 2
times, not once`.)

- [ ] **Step 12: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/Main.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/skill/Skill.java src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java src/main/resources/data/units/brute.duke src/main/resources/data/units/revenant.duke src/main/resources/data/units/runner.duke src/main/resources/data/units/skeleton.duke src/main/resources/data/units/skeleton_summoner.duke src/main/resources/data/units/stalker.duke src/test/java/uz/dukeengine/dungeon/HasteTest.java src/test/java/uz/dukeengine/dungeon/ai/MonsterSummoningTest.java src/test/java/uz/dukeengine/dungeon/content/DungeonSettingsTest.java
git commit -m "The summoner hastens the sturdiest of its own near it, or itself, and every monster carries a book" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 2: Everyone of the summoner's own round it hits half as hard again — an aura asked where each blow is struck

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java` (the class note; `DAMAGE_AURA` after
  `LIFESTEAL`; `isAura()` after `isPassive()`)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` (`auraReach` and the constructor, ~line 243;
  `damageMultiplier` and `auraOn`, ~line 1195)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/Skill.java` (the `radius` and `boostPercent` notes)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate()`: the aura's `Radius` before
  the `switch`, ~line 775; `case DAMAGE_AURA` after `case LIFESTEAL`)
- Modify: `src/main/java/uz/dukeengine/dungeon/Main.java` (`rangeOf`)
- Modify: `src/main/resources/data/units/skeleton_summoner.duke`
- Modify: `src/test/java/uz/dukeengine/dungeon/content/DungeonSettingsTest.java` (the ring switch)
- Test: `src/test/java/uz/dukeengine/dungeon/AuraTest.java` (**new**)

**Interfaces:**
- Consumes: Task 1's `SkillBook` on every monster; `LevelBonus.levelOf`; `Skill.levelForRank(1)` (piece 4's rule of
  when a monster's skill opens); `SightLine.clear`; `World.objectsInRange`; `DungeonSettings.monster(String)` and
  `skills()`; the engine's `WeaponUpdate`, which multiplies every `DamageModifier` into each blow.
- Produces: `SkillEffect.DAMAGE_AURA` (`Aim.SELF`, passive); `boolean SkillEffect.isAura()`;
  `public static int SkillBook.auraOn(GameObject creature, SkillEffect kind)`; `SkillBook.damageMultiplier()` ×
  (1 + the might / 100); the summoner's E; `AuraTest` with `room(...)`, `unseeing(String...)`, `untilStruck`,
  `might(GameObject)`.

- [ ] **Step 1: Write the failing test** — `src/test/java/uz/dukeengine/dungeon/AuraTest.java`:

```java
package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.dungeon.skill.Summoned;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.combat.module.StatusUpdate;

/**
 * The mages' auras: what each lends everyone of its own round it -- the living of its side that carry a book, within
 * its Radius, middle to middle, and in its plain sight, itself among them -- asked by the one it lends to at the moment
 * the figure is used.
 *
 * <p>Most of it is asked of creatures standing in a room with nobody to fight, so nothing moves the figure read; a blow
 * is fought for real, against a Rogue who cannot answer, where the figure is the blow itself.
 */
class AuraTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String SUMMONER = "SkeletonSummoner";
    private static final String MAGE = "SkeletonMage";
    private static final int NO_WALL = -1;

    /** An open room forty cells by thirty; with a wall down one column, and a doorway at its far end. */
    private static String room(int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean edge = x == 0 || y == 0 || x == 39 || y == 29;
                boolean wall = x == wallColumn && y != 27;
                text.append(edge || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** One creature to place: its kind and where. */
    private record One(String kind, float x, float y) {
    }

    private static One one(String kind, float x, float y) {
        return new One(kind, x, y);
    }

    /** A room, and the dungeon's own standing in it, each in the order it was made. */
    private record Room(DukeGame game, List<GameObject> ones) {

        GameObject get(int index) {
            return ones.get(index);
        }
    }

    /** A room holding these of the dungeon's own, in this order, and nobody to fight. */
    private static Room room(DungeonSettings settings, int wallColumn, One... ones) {
        return room(settings, Content.units(), wallColumn, ones);
    }

    /** The same, of creatures as {@code units} write them. */
    private static Room room(DungeonSettings settings, String units, int wallColumn, One... ones) {
        var arena = Dungeon.world(room(wallColumn), settings, units);
        var game = arena.game();
        for (var one : ones) {
            game.spawn(one.kind(), arena.dungeon(), one.x(), one.y());
        }
        game.runHeadless(1);
        return new Room(game, game.getLogic().getObjects().stream().filter(object -> object.getBody() != null)
                .toList());
    }

    private static SkillBook bookOf(GameObject creature) {
        return creature.findModule(SkillBook.class);
    }

    /** What its blows and its skills are multiplied by, as its weapon and its book read it. */
    private static float might(GameObject creature) {
        return bookOf(creature).damageMultiplier();
    }

    /** The shipped units with the Rogue's bow reaching nothing: whatever he loses, a monster took. */
    private static String unarmedRogue() {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text());
    }

    /** The shipped files with these kinds noticing nobody: they do only what a test does for them. */
    private static DungeonSettings unseeing(String... kinds) {
        var text = new StringBuilder();
        for (var kind : kinds) {
            text.append(ShippedBlock.of(kind).with("SenseRadius", 1).with("ChaseRadius", 1).with("AlertRadius", 0)
                    .text());
        }
        return DungeonSettings.parse(text.toString());
    }

    private static GameObject first(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    /** Runs a frame at a time until {@code hero} loses health: what he lost, within {@code frames}, or 0. */
    private static float untilStruck(DukeGame game, GameObject hero, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            float health = hero.getBody().getHealth();
            game.runHeadless(1);
            if (hero.getBody().getHealth() < health) {
                return health - hero.getBody().getHealth();
            }
        }
        return 0f;
    }

    // ---- might ----

    /**
     * A Skeleton 60 from a summoner, middle to middle, deals 10.5 for its 7: its first blow at a Rogue who cannot
     * answer, the summoner noticing nobody.
     */
    @Test
    void aSkeletonSixtyFromASummonerDealsTenAndAHalfForItsSeven() {
        var arena = Dungeon.world(room(NO_WALL), unseeing(SUMMONER), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 162f, 150f);
        game.spawn(SUMMONER, arena.dungeon(), 162f, 90f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);

        assertEquals(10.5f, untilStruck(game, first(game, "Rogue"), 150), 0.001f, "its first blow");
    }

    /** What the fire mage's fireball takes off a Rogue, thrown by hand -- with a summoner 50 from it, or without. */
    private static float aFireballFrom(boolean summonerBeside) {
        var arena = Dungeon.world(room(NO_WALL), unseeing(MAGE, SUMMONER), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 240f, 150f);
        game.spawn(MAGE, arena.dungeon(), 200f, 150f);
        if (summonerBeside) {
            game.spawn(SUMMONER, arena.dungeon(), 200f, 100f);
        }
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = first(game, "Rogue");
        float health = hero.getBody().getHealth();

        assertTrue(bookOf(first(game, MAGE)).cast('Q', 1, null, hero.getPosition()), "the premise: it threw");
        game.runHeadless(30);
        return health - hero.getBody().getHealth();
    }

    /** A skill's damage is raised alike: the fire mage's fireball, 45, lands 67.5 beside a summoner. */
    @Test
    void aFireMagesFireballIsRaisedAlike() {
        float plain = aFireballFrom(false);

        assertEquals(45f, plain, 0.001f, "the premise: its fireball, alone");
        assertEquals(plain * 1.5f, aFireballFrom(true), 0.001f, "beside a summoner");
    }

    /** At 60, middle to middle, half as hard again; a step further, or behind stone, its own. */
    @Test
    void atSixtyOneOrBehindStoneItsOwn() {
        var at60 = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 160f, 150f));
        assertEquals(1.5f, might(at60.get(1)), 0.0001f, "60 from it");

        var at61 = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 161f, 150f));
        assertEquals(1f, might(at61.get(1)), 0.0001f, "61 from it");

        var walled = room(SETTINGS, 13, one(SUMMONER, 100f, 150f), one("Skeleton", 150f, 150f));
        assertEquals(1f, might(walled.get(1)), 0.0001f, "50 from it, stone between");
    }

    /** Of several of one kind, the strongest -- never the sum: two summoners, and still half as hard again. */
    @Test
    void twoSummonersLendTheStrongerNotBoth() {
        var two = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one(SUMMONER, 160f, 150f),
                one("Skeleton", 130f, 150f));

        assertEquals(1.5f, might(two.get(2)), 0.0001f);
    }

    /** Its bearer is among those it reaches; a hero beside it never is. */
    @Test
    void itsBearerHitsHarderAndAHeroBesideItDoesNot() {
        var arena = Dungeon.world(room(NO_WALL), SETTINGS);
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), 100f, 150f);
        game.spawn("Rogue", arena.hero(), 115f, 150f);
        game.runHeadless(1);

        assertEquals(1.5f, might(first(game, SUMMONER)), 0.0001f, "the summoner itself");
        assertEquals(1f, might(first(game, "Rogue")), 0.0001f, "a hero, 15 from it");
        assertEquals(0, SkillBook.auraOn(first(game, "Rogue"), SkillEffect.DAMAGE_AURA));
    }

    // ---- reach ----

    /**
     * It holds while its bearer lives: the summoner killed, the Skeleton beside it is its own again -- from the moment
     * it falls, its body still lying there, and after.
     */
    @Test
    void itsBearerKilledItsBlowIsItsOwnAgain() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f));
        assertEquals(1.5f, might(room.get(1)), 0.0001f, "the premise: it lends");

        var body = room.get(0).getBody();
        body.damage(body.getHealth() + 1f);

        assertTrue(room.get(0).isEffectivelyDead(), "the premise: the summoner is dead");
        assertEquals(1f, might(room.get(1)), 0.0001f, "the moment it falls");
        room.game().runHeadless(1);
        assertEquals(1f, might(room.get(1)), 0.0001f, "and after");
    }

    /** It holds while its bearer lives, stunned or not: a summoner standing dazed still lends. */
    @Test
    void aStunnedBearerStillLends() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f));
        room.get(0).findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 60);
        room.game().runHeadless(1);

        assertTrue(room.get(0).hasStatus(ObjectStatus.DISABLED), "the premise: the summoner stands dazed");
        assertEquals(1.5f, might(room.get(1)), 0.0001f);
    }

    /** What a summoner calls up is of its own, and lent to as any of them is: two swordsmen and two archers. */
    @Test
    void whatASummonerCallsUpIsLentTo() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 200f, 150f));
        assertTrue(bookOf(room.get(0)).cast('Q', 1, null, new Coord3D(150f, 150f, 0f)),
                "the premise: it opened its rifts");
        room.game().runHeadless(30);

        var risen = room.game().getLogic().getObjects().stream()
                .filter(object -> object.findModule(Summoned.class) != null).toList();
        assertEquals(4, risen.size(), "the premise: its four rose");
        for (var one : risen) {
            assertEquals(1.5f, might(one), 0.0001f, one.getTemplate().name());
        }
    }

    /** A creature that carries no book is not reached, as one with no StatusUpdate is not stunned. */
    @Test
    void aCreatureWithNoBookIsNotReached() {
        var skeleton = ShippedBlock.of("Skeleton").text();
        var bookless = skeleton.replace(
                "    ; Every monster carries one, casting or not: a summoner's haste is held in it.\n"
                        + "    SkillBook\n    End,\n", "");
        assertNotEquals(skeleton, bookless, "the premise: the Skeleton's book was taken out");
        var room = room(SETTINGS, Content.units().replace(skeleton, bookless), NO_WALL,
                one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f));

        assertNull(bookOf(room.get(1)), "the premise: it carries none");
        assertEquals(0, SkillBook.auraOn(room.get(1), SkillEffect.DAMAGE_AURA));
    }

    /** An aura its bearer's level has not opened lends nothing, and lends from the level that opens it. */
    @Test
    void anAuraItsBearersLevelHasNotOpenedLendsNothing() {
        var data = Content.data().replace("      Effect = DAMAGE_AURA\n",
                "      Effect = DAMAGE_AURA\n      LevelPerRank = 6\n");
        assertNotEquals(Content.data(), data, "the premise: the summoner's might waits for its sixth level");
        var settings = DungeonSettings.parse(data);
        var room = room(settings, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f));

        assertEquals(1f, might(room.get(1)), 0.0001f, "at its first level");
        Spawner.scale(room.get(0), 6, settings);
        assertEquals(1.5f, might(room.get(1)), 0.0001f, "at its sixth");
    }

    // ---- the file ----

    /** An aura needs a Radius, and a might a BoostPercent of at least 1: without either, the file is refused. */
    @Test
    void aMightWithoutItsRadiusOrItsWorthIsRefused() {
        var block = ShippedBlock.of(SUMMONER).text();
        for (var wrong : new String[][] {{"      Radius = 60\n", "", "Radius"},
                {"      BoostPercent = 50\n", "      BoostPercent = 0\n", "BoostPercent"}}) {
            var text = block.replace(wrong[0], wrong[1]);
            assertNotEquals(block, text, "the premise: " + wrong[2] + " was changed");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(text), wrong[2]);
            assertTrue(refused.getMessage().contains(wrong[2]), refused.getMessage());
        }
    }

    /**
     * As shipped: the summoner's E, written after its haste -- half as hard again for everyone of its own within 60 of
     * it, never cast -- and its ring drawn on him alone, as a lifesteal's is.
     */
    @Test
    void asShippedTheSummonersMight() {
        var skills = SETTINGS.skillsFor(SUMMONER);
        var might = skills.get(2);

        assertEquals(List.of(SkillEffect.SUMMON, SkillEffect.HASTE, SkillEffect.DAMAGE_AURA),
                skills.stream().map(Skill::effect).toList());
        assertEquals('E', might.key());
        assertTrue(might.effect().isPassive() && might.effect().isAura());
        assertEquals(50, might.boostPercent());
        assertEquals(60f, might.radius(), 0.001f);
        assertEquals(1, might.maxRank());
        assertEquals("Qudrat", might.name());
        assertEquals(SkillRange.Shape.ON_HIMSELF, Main.rangeOf(might, 5f).shape());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.AuraTest"`
Expected: FAIL — compilation error, `cannot find symbol`: `variable DAMAGE_AURA` and `method isAura()` in
`SkillEffect`.

- [ ] **Step 3: The effect, and what makes one an aura** — `SkillEffect.java`. The class note's sentence on what is
  never cast:

```java
 * And some that are never cast at all: drink from your own blows, and lend
 * everyone of your own round you something -- see {@link #isPassive} and
 * {@link #isAura}. A new shape is
```

`LIFESTEAL(Aim.SELF, true);` becomes `LIFESTEAL(Aim.SELF, true),` and the constant follows it:

```java
    /**
     * Everyone of your own round you hits harder: {@code BoostPercent} more on every blow and every skill's damage. A
     * mending is not damage, and is not raised.
     *
     * <p>An aura: never cast, and lent to everyone it reaches rather than to its bearer alone -- see {@link #isAura}.
     */
    DAMAGE_AURA(Aim.SELF, true);
```

and after `isPassive()`:

```java
    /**
     * Whether it lends what it is worth to everyone of its bearer's own side round it: the living of that side that
     * carry a {@code SkillBook} -- the bearer itself, and what a summoner calls up, among them; heroes never -- within
     * its {@code Radius} of the bearer, middle to middle, and in its plain sight. Every aura is a passive.
     *
     * <p>It is asked by the one it lends to, at the moment the figure is used, never pushed to it -- see
     * {@link SkillBook#auraOn} -- so it holds exactly while that one stands in reach and ends the moment it steps out
     * or its bearer falls. Of several of one kind round a creature the strongest counts, never the sum; kinds add,
     * each where its own figure is used. Its figures are the skill's own and do not grow with its bearer's level.
     */
    public boolean isAura() {
        return this == DAMAGE_AURA;
    }
```

- [ ] **Step 4: The asking** — `SkillBook.java`. How far out a book asks, before the constructor, and the constructor
  working it out once:

```java
    /**
     * How far round it an aura may be lent from: the widest {@code Radius} any aura in the files has, and nothing for a
     * hero's book -- none of his side may bear one -- or where no file gives an aura. See {@link #auraOn}.
     */
    private final float auraReach;

    public SkillBook(GameObject owner, List<Skill> skills, DungeonSettings settings) {
        super(owner);
        this.skills = List.copyOf(skills);
        this.cooldowns = new int[skills.size()];
        this.settings = settings;
        this.auraReach = settings.monster(owner.getTemplate().name()) == null ? 0f
                : settings.skills().stream().filter(skill -> skill.effect().isAura())
                        .map(Skill::radius).reduce(0f, Math::max);
    }
```

`damageMultiplier()` in place of its one line, and `auraOn` after it:

```java
    /**
     * How much harder its blows and its skills land: an {@code EMPOWER} while it lasts, and the strongest
     * {@code DAMAGE_AURA} it stands in -- see {@link #auraOn}. The engine's weapon multiplies it into each blow, and
     * {@link #damageOf} into each skill; a mending is not damage and does not ask.
     */
    @Override
    public float damageMultiplier() {
        float empowered = boostFrames > 0 ? 1f + boostPercent / 100f : 1f;
        return empowered * (1f + auraOn(getOwner(), SkillEffect.DAMAGE_AURA) / 100f);
    }

    /**
     * The strongest aura of {@code kind} on {@code creature}, 0 for none: the largest worth among the bearers of that
     * kind on its side, living, whose level has opened it, within whose {@code Radius} it stands, middle to middle, and
     * in whose plain sight -- the bearer itself among them. Nothing for a creature that carries no book, as one without
     * a {@code StatusUpdate} is not stunned.
     *
     * <p>Asked where the figure is used, never pushed to the creature, so it holds exactly while the creature stands in
     * reach and ends the moment it steps out or its bearer falls: nothing kept, nothing to go stale. A maximum of whole
     * numbers, the same in any order, behind a sight line walked on integers.
     *
     * <p>ponytail: a pass over the floor's objects out to the widest aura, and a sight line for each bearer in reach,
     * at every asking; a cache by frame if a crowded floor ever shows it.
     */
    public static int auraOn(GameObject creature, SkillEffect kind) {
        var book = creature == null ? null : creature.findModule(SkillBook.class);
        var world = creature == null ? null : creature.getWorld();
        if (book == null || world == null || book.auraReach <= 0f) {
            return 0;
        }
        var here = creature.getPosition();
        int strongest = 0;
        for (var bearer : world.objectsInRange(here, book.auraReach,
                one -> one.getPlayerIndex() == creature.getPlayerIndex() && !one.isEffectivelyDead())) {
            var theirs = bearer.findModule(SkillBook.class);
            if (theirs == null) {
                continue;
            }
            for (var skill : theirs.skills) {
                int worth = skill.boostPercent();
                if (skill.effect() == kind && worth > strongest
                        && LevelBonus.levelOf(bearer) >= skill.levelForRank(1)
                        && here.distance(bearer.getPosition()) <= skill.radius()
                        && SightLine.clear(bearer, creature)) {
                    strongest = worth;
                }
            }
        }
        return strongest;
    }
```

- [ ] **Step 5: What the file must say of an aura** — `DungeonSettings.validate()`. Before the `switch`, after the
  passive's rule:

```java
            // An aura lends what it is worth as far as its Radius and no further: without one it reaches nobody.
            require(!skill.effect().isAura() || skill.radius() > 0f,
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect()
                            + " and reaches nobody: it needs a Radius");
```

and in it, after `case LIFESTEAL -> ...`:

```java
                case DAMAGE_AURA -> require(skill.boostPercent() >= 1,
                        skill.heroTemplate() + "'s Skill " + skill.key() + "'s BoostPercent is what it adds to every"
                                + " blow round it, at least 1");
```

- [ ] **Step 6: Its ring, where the lifesteal's stands** — `Main.rangeOf`:

```java
            // None of these is aimed past him: one sharpens his sword, one thickens
            // his skin, and the passives -- never cast -- drink from his blows or
            // lend round him.
            case EMPOWER, GUARD, LIFESTEAL, DAMAGE_AURA -> uz.dukeengine.client3d.SkillRange.Shape.ON_HIMSELF;
```

```java
            case EMPOWER, GUARD, LIFESTEAL, DAMAGE_AURA -> selfRadius;
```

and `DungeonSettingsTest`'s switch, the stand-in's note moved over it so the line stays short as it grows:

```java
                case EMPOWER, GUARD -> 1f; // his own width; the look says how wide
                // Never cast, so no ring is ever drawn: a stand-in to keep the switch whole.
                case LIFESTEAL, DAMAGE_AURA -> 1f;
```

- [ ] **Step 7: Its notes on `Skill`** — the `radius` and `boostPercent` `@param` lines:

```java
 * @param radius        how far {@code AREA_DAMAGE} reaches around the caster, and an
 *     aura round its bearer, middle to middle
```

```java
 * @param boostPercent  what this skill is worth in percent — damage added by
 *     {@code EMPOWER}, damage avoided by {@code GUARD}, the share of every blow
 *     a {@code LIFESTEAL} gives back as health, how much faster a {@code HASTE}
 *     makes a weapon fire, and what a {@code DAMAGE_AURA} adds to every blow round
 *     it. One field because it is one question ("how much is it worth?") asked of
 *     mirrored effects and of passives that are worth a share of what is done
```

- [ ] **Step 8: The summoner's E** — `skeleton_summoner.duke`. Its first note:

```
; The purple one, and it brings more of them. Frail and slow like the other two, with a
; mote of dark for its ordinary shot; its skills open rifts that swordsmen and archers
; climb out of, and make the sturdiest of its own strike faster, and everyone of its own
; round it hits harder -- see Monster SkeletonSummoner and the Q, W and E skills of the
; SkeletonSummoner below.
```

and the E, after the haste's `End` (which gains its comma):

```
      Name = Shiddat
    End,
    Skill
      Key = E
      ; Everyone of its own round it -- itself, and what it calls up -- hits half as hard
      ; again: every blow and every skill, within its Radius and in its plain sight.
      ; Never cast: it holds while it stands. Two summoners lend the stronger, never
      ; both.
      Effect = DAMAGE_AURA
      BoostPercent = 50
      ; A room round it, and the ones fighting him from its band.
      Radius = 60
      MaxRank = 1
      Name = Qudrat
    End
  ]
End
```

- [ ] **Step 9: Run the test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.AuraTest"`
Expected: PASS, 12 tests.

- [ ] **Step 10: Run the whole suite**

Run: `./gradlew test`
Expected: PASS, 979 tests.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/Main.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/skill/Skill.java src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java src/main/resources/data/units/skeleton_summoner.duke src/test/java/uz/dukeengine/dungeon/AuraTest.java src/test/java/uz/dukeengine/dungeon/content/DungeonSettingsTest.java
git commit -m "Everyone of the summoner's own round it hits half as hard again, asked where each blow is struck" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 3: Every pool of the healer's own round it fills five points a second faster, asked each frame it is not full

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java` (`MANA_AURA` after `DAMAGE_AURA`; `isAura()`)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/Skill.java` (+ `manaRegen`, last: its note, the record,
  `DEFAULTS`, `ownedBy`)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` (`auraOn`'s worth and note; `regenerate()`,
  ~line 1320)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate()`: `ManaRegen` elsewhere,
  after the aura's `Radius`; `case MANA_AURA`)
- Modify: `src/main/java/uz/dukeengine/dungeon/Main.java` (`rangeOf`)
- Modify: `src/main/resources/data/units/skeleton_healer.duke`, `skeleton_summoner.duke` (its pool's note)
- Modify: `src/test/java/uz/dukeengine/dungeon/content/DungeonSettingsTest.java`,
  `src/test/java/uz/dukeengine/dungeon/skill/ManaTest.java`, `SkillRanksTest.java`, `SkillTest.java`
- Test: `src/test/java/uz/dukeengine/dungeon/AuraTest.java`

**Interfaces:**
- Consumes: Task 2's `auraOn`; the book's pool (`poolOf`, `resize`, `getMana`, `getMaxMana`, `getManaRegen`) and its
  carry in whole points; piece 4's `Spawner.scale`, which sizes and fills a mage's pool.
- Produces: `SkillEffect.MANA_AURA` (passive, an aura); `int Skill.manaRegen()` — `ManaRegen` in a `Skill` block,
  tenths of a point a second; the trickle adds the strongest mana aura; the healer's W.

- [ ] **Step 1: Write the failing tests** — `AuraTest.java`. The healer beside the other kinds:

```java
    private static final String MAGE = "SkeletonMage";
    private static final String HEALER = "SkeletonHealer";
    private static final int NO_WALL = -1;
```

and, before `// ---- reach ----`:

```java
    // ---- mana ----

    /** Its pool at the first level, with nothing in it: what comes back over the next frames is only the trickle. */
    private static SkillBook emptied(GameObject caster) {
        Spawner.scale(caster, 1, SETTINGS);
        var book = bookOf(caster);
        int most = book.getMaxMana();
        int tenths = book.getManaRegen();
        book.resize(0, 0);
        book.resize(most, tenths);
        return book;
    }

    /** What an emptied pool of each of {@code refilling} holds two seconds on: the trickle, in whole points. */
    private static List<Integer> twoSecondsOf(Room room, int... refilling) {
        var books = java.util.Arrays.stream(refilling).mapToObj(at -> emptied(room.get(at))).toList();
        room.game().runHeadless(60);
        return books.stream().map(SkillBook::getMana).toList();
    }

    /** A healer alone refills seven and a half a second: its own two and a half, and its aura's five. */
    @Test
    void aHealerAloneRefillsSevenAndAHalfASecond() {
        var alone = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f));

        assertEquals(List.of(15), twoSecondsOf(alone, 0));
    }

    /**
     * A fire mage beside it refills eight a second, its own three and the aura's five; out of its reach, three; and
     * with no trickle of its own, beside it, the aura's five.
     */
    @Test
    void aFireMageBesideItRefillsEightAndOutOfItsReachThree() {
        var beside = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one(MAGE, 160f, 150f));
        assertEquals(List.of(16), twoSecondsOf(beside, 1), "60 from it");

        var apart = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one(MAGE, 161f, 150f));
        assertEquals(List.of(6), twoSecondsOf(apart, 1), "61 from it");

        var dry = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one(MAGE, 130f, 150f));
        var book = emptied(dry.get(1));
        book.resize(book.getMaxMana(), 0);
        dry.game().runHeadless(60);
        assertEquals(10, book.getMana(), "no trickle of its own, 30 from it");
    }

    /** Two healers, and still five more, not ten: the strongest of a kind, never the sum -- each of them as much. */
    @Test
    void twoHealersStillFiveMore() {
        var two = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one(HEALER, 160f, 150f), one(MAGE, 130f, 150f));

        assertEquals(List.of(15, 15, 16), twoSecondsOf(two, 0, 1, 2));
    }

    /** The aura fills pools, it makes none: a Skeleton beside a healer has no pool, and is given none. */
    @Test
    void aSkeletonBesideAHealerIsGivenNoPool() {
        var room = room(SETTINGS, NO_WALL, one(HEALER, 100f, 150f), one("Skeleton", 130f, 150f));
        room.game().runHeadless(60);

        assertEquals(List.of(0, 0), List.of(bookOf(room.get(1)).getMaxMana(), bookOf(room.get(1)).getMana()));
    }
```

and, before `asShippedTheSummonersMight`:

```java
    /**
     * A mana aura needs a ManaRegen of at least 1; and a ManaRegen on any other skill is read by nothing, and refused,
     * as StunFrames off a skillshot is -- each saying so.
     */
    @Test
    void aManaRegenOnlyAManaAuraReadsIsRefusedElsewhere() {
        var healer = ShippedBlock.of(HEALER).text();
        var none = healer.replace("      ManaRegen = 50\n", "      ManaRegen = 0\n");
        assertNotEquals(healer, none, "the premise: the healer's aura was given no ManaRegen");
        var summoner = ShippedBlock.of(SUMMONER).text();
        var misplaced = summoner.replace("      Effect = HASTE\n", "      Effect = HASTE\n      ManaRegen = 50\n");
        assertNotEquals(summoner, misplaced, "the premise: the summoner's haste was given a ManaRegen");

        for (var text : new String[] {none, misplaced}) {
            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(text));
            assertTrue(refused.getMessage().contains("ManaRegen"), refused.getMessage());
        }
    }

    /**
     * As shipped: the healer's W, written after its mending -- five points a second more to every pool of its own
     * within 60 of it, never cast.
     */
    @Test
    void asShippedTheHealersMana() {
        var skills = SETTINGS.skillsFor(HEALER);
        var mana = skills.get(1);

        assertEquals(List.of(SkillEffect.HEAL, SkillEffect.MANA_AURA), skills.stream().map(Skill::effect).toList());
        assertEquals('W', mana.key());
        assertTrue(mana.effect().isPassive() && mana.effect().isAura());
        assertEquals(50, mana.manaRegen(), "in tenths of a point a second");
        assertEquals(60f, mana.radius(), 0.001f);
        assertEquals(1, mana.maxRank());
        assertEquals("Sehr buloqi", mana.name());
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.AuraTest"`
Expected: FAIL — compilation error, `cannot find symbol`: `variable MANA_AURA` in `SkillEffect`, `method manaRegen()` in
`Skill`.

- [ ] **Step 3: The effect** — `SkillEffect.java`. `DAMAGE_AURA(Aim.SELF, true);` becomes
  `DAMAGE_AURA(Aim.SELF, true),`, and:

```java
    /**
     * Everyone of your own round you refills faster: {@code ManaRegen}, in tenths of a point a second as a pool's own
     * trickle is, added to it -- a pool with none of its own still fills at the aura's. A creature with no pool gets
     * nothing: the aura fills pools, it makes none. An aura -- see {@link #isAura}.
     */
    MANA_AURA(Aim.SELF, true);
```

```java
    public boolean isAura() {
        return this == DAMAGE_AURA || this == MANA_AURA;
    }
```

- [ ] **Step 4: `ManaRegen` on a skill** — `Skill.java`. The last `@param`, after `stunFrames`':

```java
 *     fireball has one and its ordinary fire does not
 * @param manaRegen     what a {@code MANA_AURA} adds to every pool of its own round it,
 *     in tenths of a point a second -- the heroes' word and unit for a trickle. Read by
 *     nothing else, and refused on anything else
 */
```

the record's last component:

```java
        int summonExperiencePercent,
        int stunFrames,
        int manaRegen) {
```

and a `0` more at the end of `DEFAULTS`, and `manaRegen` handed on by `ownedBy`:

```java
            0, 0, 0, 0, 0, 90, 0, 4, 0, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, Map.of(), 0, 0, 0, 0);
```

```java
                summonExperiencePercent, stunFrames, manaRegen);
```

The five records the tests write out whole take the same `0` at their ends — `ManaTest.andACostNeverFallsBelowNothing`,
`SkillRanksTest.ordinary` and `ultimate`, `SkillTest.skill` and `anUltimateGrowsIntoItsRanks`: each
`..., Map.of(), 0, 0, 0);` becomes `..., Map.of(), 0, 0, 0, 0);`.

- [ ] **Step 5: The trickle asks for it** — `SkillBook.java`. `auraOn`'s first sentence, and the worth it compares:

```java
     * The strongest aura of {@code kind} on {@code creature}, 0 for none: the largest worth -- a {@code BoostPercent},
     * or a {@code MANA_AURA}'s tenths of a point a second -- among the bearers of that kind on its side, living, whose
     * level has opened it, within whose {@code Radius} it stands, middle to middle, and in whose plain sight -- the
     * bearer itself among them. Nothing for a creature that carries no book, as one without a {@code StatusUpdate} is
     * not stunned.
```

```java
                int worth = kind == SkillEffect.MANA_AURA ? skill.manaRegen() : skill.boostPercent();
```

and `regenerate()`, its note's last paragraph and its opening:

```java
     * a second's worth of frames of the rate is exactly the rate, and no rounding
     * is carried from one second into the next.
     *
     * <p>The rate is its own and the strongest {@code MANA_AURA} it stands in, asked
     * each frame the pool is not full -- see {@link #auraOn}. A pool with no trickle
     * of its own still fills at the aura's; no pool at all is given nothing.
     */
    private void regenerate() {
        int tenths = !usesMana || mana >= maxMana ? 0
                : manaTenthsPerSecond + auraOn(getOwner(), SkillEffect.MANA_AURA);
        if (tenths <= 0) {
            manaCarry = 0;
            return;
        }
        int aSecond = TENTHS * uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;
        manaCarry += tenths;
```

- [ ] **Step 6: What the file must say of it** — `DungeonSettings.validate()`, after the aura's `Radius`:

```java
            // What a mana aura lends, and nothing else reads: written on any other skill, a number nothing read.
            require(skill.manaRegen() == 0 || skill.effect() == SkillEffect.MANA_AURA,
                    skill.heroTemplate() + "'s Skill " + skill.key() + " has ManaRegen = " + skill.manaRegen()
                            + ": only a MANA_AURA lends mana");
```

and after `case DAMAGE_AURA -> ...`:

```java
                case MANA_AURA -> require(skill.manaRegen() >= 1,
                        skill.heroTemplate() + "'s Skill " + skill.key() + "'s ManaRegen is what it adds to every"
                                + " pool round it, in tenths of a point a second, at least 1");
```

- [ ] **Step 7: Its ring** — `Main.rangeOf`:

```java
            case EMPOWER, GUARD, LIFESTEAL, DAMAGE_AURA, MANA_AURA ->
                    uz.dukeengine.client3d.SkillRange.Shape.ON_HIMSELF;
```

```java
            case EMPOWER, GUARD, LIFESTEAL, DAMAGE_AURA, MANA_AURA -> selfRadius;
```

and `DungeonSettingsTest`'s: `case LIFESTEAL, DAMAGE_AURA, MANA_AURA -> 1f;`.

- [ ] **Step 8: The healer's W** — `skeleton_healer.duke`. Its first note:

```
; The green one, and it fights for the others. Frail and slow like the Skeleton Mage,
; with a spark of light for its ordinary shot; its skills are holy light called down
; on whichever of its own is worst hurt, and every pool of its own round it filling
; faster -- see Monster SkeletonHealer and the Q and W skills of the SkeletonHealer
; below.
```

and the W, after the mending's `End` (which gains its comma):

```
      Name = Muqaddas nur
    End,
    Skill
      Key = W
      ; Every pool of its own round it -- its own, a summoner's, a fire mage's -- fills
      ; five points a second faster, within its Radius and in its plain sight: in tenths
      ; of a point a second, as a pool's own trickle. A creature with no pool is given
      ; none. Never cast: it holds while it stands. Two healers lend the stronger.
      Effect = MANA_AURA
      ManaRegen = 50
      ; A room round it, and the ones fighting him from its band.
      Radius = 60
      MaxRank = 1
      Name = Sehr buloqi
    End
  ]
End
```

`skeleton_summoner.duke`, its pool's note:

```
  ; What it opens its rifts and hastens out of, in the hero's words and units, as the
  ; Skeleton Mage's: enough to call up its four at its cooldown through a fight of a
  ; minute or more, and to hasten as its trickle allows -- or, beside a healer, whose
  ; aura fills it three times as fast, both at their cooldowns.
```

- [ ] **Step 9: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.AuraTest" --tests "uz.dukeengine.dungeon.skill.ManaTest" --tests "uz.dukeengine.dungeon.skill.SkillRanksTest" --tests "uz.dukeengine.dungeon.skill.SkillTest"`
Expected: PASS — `AuraTest` 18 tests; the other three as before.

- [ ] **Step 10: Run the whole suite**

Run: `./gradlew test`
Expected: PASS, 985 tests.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/Main.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/skill/Skill.java src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java src/main/resources/data/units/skeleton_healer.duke src/main/resources/data/units/skeleton_summoner.duke src/test/java/uz/dukeengine/dungeon/AuraTest.java src/test/java/uz/dukeengine/dungeon/content/DungeonSettingsTest.java src/test/java/uz/dukeengine/dungeon/skill/ManaTest.java src/test/java/uz/dukeengine/dungeon/skill/SkillRanksTest.java src/test/java/uz/dukeengine/dungeon/skill/SkillTest.java
git commit -m "Every pool of the healer's own round it fills five points a second faster, asked each frame it is not full" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 4: Everyone of the Revenant's own round it drinks a tenth of every blow, the meteor's blast among them

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java` (`LIFESTEAL`'s note; `LIFESTEAL_AURA`;
  `isAura()`)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` (`drink` and its note, ~line 1253)
- Modify: `src/main/java/uz/dukeengine/dungeon/combat/FallingUpdate.java` (an import; `land`, ~line 96)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`case LIFESTEAL`)
- Modify: `src/main/java/uz/dukeengine/dungeon/Main.java` (`rangeOf`)
- Modify: `src/main/resources/data/units/revenant.duke`
- Modify: `src/test/java/uz/dukeengine/dungeon/content/DungeonSettingsTest.java`
- Test: `src/test/java/uz/dukeengine/dungeon/AuraTest.java`

**Interfaces:**
- Consumes: Task 2's `auraOn`; `SkillBook.drink(GameObject, float)` as piece 2 left it, told by `Swing.launch` and
  `ArrowUpdate.strike`/`splash`; piece 4's meteor (`FallingUpdate.callDown`/`land`) and `Spawner.scale`;
  `GrowableBody.setMaxHealth` (a test's room to drink into).
- Produces: `SkillEffect.LIFESTEAL_AURA` (passive, an aura); `drink` adds the strongest lifesteal aura to the
  striker's own `LIFESTEAL` shares; `FallingUpdate.land` tells `drink` of each it hurts; the Revenant's Q.

- [ ] **Step 1: Write the failing tests** — `AuraTest.java`. An import, after `ShippedBlock`'s:

```java
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.level.GrowableBody;
```

the Revenant beside the other kinds:

```java
    private static final String HEALER = "SkeletonHealer";
    private static final String REVENANT = "Revenant";
    private static final int NO_WALL = -1;
```

before `// ---- reach ----`:

```java
    // ---- lifesteal ----

    /** The shipped units with the Rogue's bow reaching nothing and the Revenant mending nothing on its own. */
    private static String drinkingUnits() {
        var revenant = ShippedBlock.of(REVENANT);
        return unarmedRogue().replace(revenant.text(), revenant.with("HealPerSecond", 0).text());
    }

    /** What the Rogue lost and what {@code striker}, left at half, gained over {@code frames} of a fight. */
    private static float[] lostAndGained(DukeGame game, GameObject hero, GameObject striker, int frames) {
        striker.getBody().setHealth(striker.getBody().getMaxHealth() / 2f);
        float his = hero.getBody().getHealth();
        float its = striker.getBody().getHealth();
        game.runHeadless(frames);
        return new float[] {his - hero.getBody().getHealth(), striker.getBody().getHealth() - its};
    }

    /**
     * A fight: a Rogue who cannot answer at (150, 150), {@code striker} at {@code (x, 150)} and a Revenant noticing
     * nobody 50 from it -- or none, for {@code REVENANT} itself.
     */
    private static Room fight(String striker, float x) {
        var arena = Dungeon.world(room(NO_WALL), REVENANT.equals(striker) ? SETTINGS : unseeing(REVENANT),
                drinkingUnits());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(striker, arena.dungeon(), x, 150f);
        if (!REVENANT.equals(striker)) {
            game.spawn(REVENANT, arena.dungeon(), x, 100f);
        }
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        return new Room(game, List.of(first(game, "Rogue"), first(game, striker)));
    }

    /** A Skeleton beside a Revenant striking for 7 gets back 0.7, and a tenth of every blow after. */
    @Test
    void aSkeletonBesideARevenantGetsBackATenth() {
        var fight = fight("Skeleton", 162f);
        var skeleton = fight.get(1);
        skeleton.getBody().setHealth(30f);

        assertEquals(7f, untilStruck(fight.game(), fight.get(0), 150), 0.001f, "the premise: its blow");
        assertEquals(30.7f, skeleton.getBody().getHealth(), 0.001f, "0.7 back");
        var change = lostAndGained(fight.game(), fight.get(0), skeleton, 90);
        assertTrue(change[0] > 0f, "the premise: it went on striking");
        assertEquals(change[0] / 10f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** A Stalker's bolt, where it arrives: a tenth of it. */
    @Test
    void aStalkersBoltItsTenth() {
        var fight = fight("Stalker", 190f);
        var change = lostAndGained(fight.game(), fight.get(0), fight.get(1), 150);

        assertTrue(change[0] > 0f, "the premise: its bolts reached him");
        assertEquals(change[0] / 10f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** The Revenant is among those its aura reaches: its own fire, a tenth -- its own mending stilled. */
    @Test
    void theRevenantDrinksFromItsOwnFire() {
        var fight = fight(REVENANT, 190f);
        var change = lostAndGained(fight.game(), fight.get(0), fight.get(1), 150);

        assertTrue(change[0] > 0f, "the premise: its fire reached him");
        assertEquals(change[0] / 10f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** What {@code creature}, left at half, gains from a blow of 20 told to it by hand. */
    private static float aBlowOfTwentyTo(GameObject creature) {
        creature.getBody().setHealth(creature.getBody().getMaxHealth() / 2f);
        float before = creature.getBody().getHealth();
        SkillBook.drink(creature, 20f);
        return creature.getBody().getHealth() - before;
    }

    /** A creature's own lifesteal and the aura add: a Warden beside a Revenant drinks its quarter and the tenth. */
    @Test
    void aBossBesideARevenantDrinksItsOwnAndTheAura() {
        var room = room(SETTINGS, NO_WALL, one(REVENANT, 100f, 150f), one("Warden", 140f, 150f));

        assertEquals(7f, aBlowOfTwentyTo(room.get(1)), 0.001f, "35% of 20");
    }

    /** Two Revenants, and still a tenth; out of reach, nothing. */
    @Test
    void twoRevenantsStillATenthAndOutOfReachNothing() {
        var two = room(SETTINGS, NO_WALL, one(REVENANT, 100f, 150f), one(REVENANT, 160f, 150f),
                one("Skeleton", 130f, 150f));
        assertEquals(2f, aBlowOfTwentyTo(two.get(2)), 0.001f, "two Revenants");

        var apart = room(SETTINGS, NO_WALL, one(REVENANT, 100f, 150f), one("Skeleton", 161f, 150f));
        assertEquals(0f, aBlowOfTwentyTo(apart.get(1)), 0.001f, "61 from it");
    }

    /**
     * The meteor's blast drinks, for each it hurts: a level-6 fire mage beside a Revenant, its meteor cast by hand at a
     * Rogue who cannot answer, gets back a tenth of the 112.5 that landed on him. It is given room to drink into by its
     * ceiling raised, its health left where it was: lowered, its brain would take it for a wound and answer it.
     */
    @Test
    void theMeteorsBlastDrinks() {
        var arena = Dungeon.world(room(NO_WALL), unseeing(MAGE, REVENANT), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(MAGE, arena.dungeon(), 200f, 150f);
        game.spawn(REVENANT, arena.dungeon(), 200f, 100f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = first(game, "Rogue");
        var mage = first(game, MAGE);
        Spawner.scale(mage, 6, SETTINGS);
        ((GrowableBody) mage.getBody()).setMaxHealth(mage.getBody().getMaxHealth() * 2f);
        float his = hero.getBody().getHealth();
        float its = mage.getBody().getHealth();
        var meteor = SETTINGS.skillsFor(MAGE).getFirst();

        assertTrue(bookOf(mage).cast('R', 1, null, hero.getPosition()), "the premise: it called the meteor down");
        game.runHeadless(meteor.windUpFrames() + 5);

        assertEquals(112.5f, his - hero.getBody().getHealth(), 0.01f, "the premise: what landed on him");
        assertEquals(11.25f, mage.getBody().getHealth() - its, 0.01f, "a tenth of it back");
    }
```

and, before `asShippedTheHealersMana`:

```java
    /** A lifesteal aura's BoostPercent is a share of every blow, from 1 to 100, as a lifesteal's is: else refused. */
    @Test
    void aLifestealAurasShareOutOfRangeIsRefused() {
        for (int share : new int[] {0, 101}) {
            var refused = assertThrows(IllegalArgumentException.class,
                    () -> DungeonSettings.parse(ShippedBlock.dataWith(REVENANT, "BoostPercent", share)));
            assertTrue(refused.getMessage().contains("BoostPercent"), refused.getMessage());
        }
    }

    /**
     * As shipped: the Revenant's Q, the one skill it has -- a tenth of every blow back to everyone of its own within 60
     * of it, never cast -- carried by the SkillBook every monster has.
     */
    @Test
    void asShippedTheRevenantsDrink() {
        var skills = SETTINGS.skillsFor(REVENANT);
        var drink = skills.getFirst();

        assertEquals(List.of(SkillEffect.LIFESTEAL_AURA), skills.stream().map(Skill::effect).toList());
        assertEquals('Q', drink.key());
        assertTrue(drink.effect().isPassive() && drink.effect().isAura());
        assertEquals(10, drink.boostPercent());
        assertEquals(60f, drink.radius(), 0.001f);
        assertEquals(1, drink.maxRank());
        assertEquals("Qon aurasi", drink.name());
    }

    /** Two worlds fought through the same frames, the three mages and their own in them, read the same checksum. */
    @Test
    void twoWorldsFoughtAlikeReadTheSameChecksum() {
        assertEquals(checksums(), checksums());
    }

    private static String checksums() {
        var arena = Dungeon.world(room(NO_WALL), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        for (var one : new One[] {one(SUMMONER, 210f, 130f), one(HEALER, 210f, 170f), one(REVENANT, 200f, 150f),
                one(MAGE, 220f, 150f), one("Skeleton", 175f, 140f), one("Brute", 175f, 160f)}) {
            game.spawn(one.kind(), arena.dungeon(), one.x(), one.y());
        }
        var line = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            game.runHeadless(30);
            line.append(game.getLogic().checksum()).append('|');
        }
        return line.toString();
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.AuraTest"`
Expected: FAIL — compilation error, `cannot find symbol`: `variable LIFESTEAL_AURA` in `SkillEffect`.

- [ ] **Step 3: The effect** — `SkillEffect.java`. `LIFESTEAL`'s note, its last sentences:

```java
     * <p>Never cast: it holds for as long as its bearer lives -- see
     * {@link #isPassive}. It is told of each blow by the places a monster's blow
     * lands today, and a damaging skill that lands anywhere else has to tell it too;
     * see {@link SkillBook#drink}.
```

`MANA_AURA(Aim.SELF, true);` becomes `MANA_AURA(Aim.SELF, true),`, and:

```java
    /**
     * Everyone of your own round you drinks from its blows: {@code BoostPercent} of every blow it lands back as
     * health, added to a {@link #LIFESTEAL} of its own -- see {@link SkillBook#drink}. An aura -- see {@link #isAura}.
     */
    LIFESTEAL_AURA(Aim.SELF, true);
```

```java
    public boolean isAura() {
        return this == DAMAGE_AURA || this == MANA_AURA || this == LIFESTEAL_AURA;
    }
```

- [ ] **Step 4: `drink` adds it** — `SkillBook.java`, the whole of `drink` and its note:

```java
    /**
     * {@code striker} landed a blow worth {@code dealt}: every {@code LIFESTEAL} among
     * its skills, and the strongest {@code LIFESTEAL_AURA} it stands in -- see
     * {@link #auraOn} -- give it back their shares of it, added, as health, never above
     * its maximum. Nobody, the dead, and a creature with neither get nothing.
     *
     * <p>Told rather than listening, by the places a monster's blow lands today: a
     * swing where the striker stands, which {@code Swing} hears the moment before the
     * weapon lands it; a shot when it arrives and each its burst catches, in
     * {@code ArrowUpdate}; and a meteor's blast, for each it hurts, in
     * {@code FallingUpdate}. A damaging skill that lands anywhere else -- an area
     * blow, a strike with no shot -- has to call this where its damage lands, or that
     * blow is not drunk from. The figure is what the blow was worth, not what the
     * victim had left: a kill is no special case.
     *
     * <p>Deterministic: whole percentages added up, its skills in the order the file
     * wrote them, one multiplication of the blow's own figure, on the simulation's
     * frame, and the body's own {@code heal}.
     */
    public static void drink(GameObject striker, float dealt) {
        var book = striker == null ? null : striker.findModule(SkillBook.class);
        if (book == null || dealt <= 0f || striker.isEffectivelyDead() || striker.getBody() == null) {
            return;
        }
        int share = auraOn(striker, SkillEffect.LIFESTEAL_AURA);
        for (var skill : book.skills) {
            if (skill.effect() == SkillEffect.LIFESTEAL) {
                // ponytail: at its first rank, which is a monster's only one; a hero may not
                // drink yet (see DungeonSettings.validate), and one who does will read his rank
                // from SkillRanks, as a cast is handed it.
                share += skill.boostAt(1);
            }
        }
        if (share > 0) {
            striker.getBody().heal(dealt * share / 100f);
        }
    }
```

- [ ] **Step 5: The meteor's blast tells it** — `FallingUpdate.java`. The import, after `World`'s:

```java
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.skill.SkillBook;
```

and in `land`, after each victim's damage:

```java
            victim.getBody().damage(damage, DamageType.EXPLOSION);
            // A blow like any other, and its caller drinks from it if a skill of its, or an aura it stands in, says
            // so -- see SkillBook.drink.
            SkillBook.drink(caster, damage);
```

(The caller may be dead, or gone, by the time it lands: `drink` gives nothing then.)

- [ ] **Step 6: What the file must say of it, and its ring** — `DungeonSettings.validate()`, the lifesteal's case takes
  it in:

```java
                case LIFESTEAL, LIFESTEAL_AURA -> require(skill.boostPercent() >= 1 && skill.boostPercent() <= 100,
```

`Main.rangeOf`:

```java
            case EMPOWER, GUARD, LIFESTEAL, DAMAGE_AURA, MANA_AURA, LIFESTEAL_AURA ->
                    uz.dukeengine.client3d.SkillRange.Shape.ON_HIMSELF;
```

```java
            case EMPOWER, GUARD, LIFESTEAL, DAMAGE_AURA, MANA_AURA, LIFESTEAL_AURA -> selfRadius;
```

and `DungeonSettingsTest`'s: `case LIFESTEAL, DAMAGE_AURA, MANA_AURA, LIFESTEAL_AURA -> 1f;`.

- [ ] **Step 7: The Revenant's Q** — `revenant.duke`. Its book now carries a skill:

```
    ; What carries its aura, below -- and, as every monster's does, a summoner's haste.
    SkillBook
    End,
```

its note:

```
  ; Heals itself (AutoHealUpdate in its template above). Kill it quickly or it undoes
  ; the fight — a damage check rather than a health check. And everyone of its own round
  ; it drinks from their blows: see its Skill, below.
```

and its skills, after `Tint`:

```
  ; It heals itself; the pale wash says so before the health bar does.
  Tint = 0x9FC4FF

  Skills = [
    Skill
      Key = Q
      ; Everyone of its own round it -- itself too -- drinks a tenth of every blow it
      ; lands, as health: a swing where it lands, a shot where it arrives, a burst and a
      ; meteor's blast for each they hurt -- added to a boss's own quarter. Within its
      ; Radius and in its plain sight. Never cast: it holds while it stands. Two
      ; Revenants lend the stronger, never both.
      Effect = LIFESTEAL_AURA
      BoostPercent = 10
      ; A room round it, and the ones fighting beside it.
      Radius = 60
      MaxRank = 1
      Name = Qon aurasi
    End
  ]
End
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.AuraTest" --tests "uz.dukeengine.dungeon.LifestealTest"`
Expected: PASS — `AuraTest` 27 tests; `LifestealTest` as before (a lone `LIFESTEAL` heals `dealt * 25 / 100f`, the
figure it always did).

- [ ] **Step 9: Run the whole suite**

Run: `./gradlew test`
Expected: PASS, 994 tests.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/Main.java src/main/java/uz/dukeengine/dungeon/combat/FallingUpdate.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java src/main/resources/data/units/revenant.duke src/test/java/uz/dukeengine/dungeon/AuraTest.java src/test/java/uz/dukeengine/dungeon/content/DungeonSettingsTest.java
git commit -m "Everyone of the Revenant's own round it drinks a tenth of every blow, the meteor's blast among them" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 5: The hastened wear Hasted, and each aura's bearer a ring as wide as its reach, renewed at each beat

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` (`case HASTE`, ~line 687; `wearTheAuras` and its
  call in `update`, ~line 1370)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate()`: the passive's rule,
  ~line 769)
- Modify: `src/main/java/uz/dukeengine/dungeon/Main.java` (`layerOf`, ~line 70–105; `measureLooks`, ~line 150–190)
- Modify: `src/main/resources/data/units/skeleton_summoner.duke`, `skeleton_healer.duke`, `revenant.duke`
- Modify: `src/test/java/uz/dukeengine/dungeon/StunTest.java` (`theStarsLastAsLongAsTheLongestStun`),
  `src/test/java/uz/dukeengine/dungeon/SkillLookTest.java` (`withoutTheLooks`)
- Test: `src/test/java/uz/dukeengine/dungeon/HasteTest.java`, `AuraTest.java`, `DungeonEffectLayerTest.java`

**Interfaces:**
- Consumes: the engine's `World.effect(String, GameObject)` (an `EffectPlayed` riding the thing, out of the checksum)
  and `EffectLayer` (`MARK`, `AURA`, `REACH`, `follows`, `renews`, `fadeIn`/`fadeOut`); `Visuals.effectSeconds` and
  `effectReach`, set by `Main.measureLooks`; `DukeGame.watch()` (a test sees moments through nobody's fog);
  `DungeonEffectLayerTest.brightestRadius`.
- Produces: a haste plays its skill's `Look` on the one it hastens; the book plays each open aura's `Look` on its
  bearer every `TickFrames`, on the frames its id falls on; an aura's look measured two ticks; `Main.layerOf` renews
  the stun's look and every haste's and aura's; the passive-`Look` rule relaxed for an aura, and its `Look` and
  `TickFrames` together; the `Hasted`, `MightAura`, `ManaAura` and `BloodAura` looks.

- [ ] **Step 1: Write the failing tests** — `HasteTest.java`. Three imports:

```java
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.EffectLayer;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.thing.GameObject;
```

and before `// ---- when ----`:

```java
    // ---- seen ----

    /**
     * The one it hastens wears Hasted, played on it the moment it is hastened, for the haste's five seconds -- and a
     * second haste carries it on to the new end: its layers renew.
     */
    @Test
    void theHastenedWearsHastedForAsLongAsTheHaste() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Brute", 100f, 180f));

        assertTrue(room.castsItsHaste(), "the premise: it cast its haste");
        var worn = room.game().getLogic().drainEvents().stream().filter(EffectPlayed.class::isInstance)
                .map(EffectPlayed.class::cast).filter(played -> played.name().equals(haste().look()))
                .map(EffectPlayed::riding).toList();

        assertEquals("Hasted", haste().look());
        assertEquals(List.of(room.get(1).getId()), worn, "worn by the Brute, and by nobody else");
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        assertEquals(5f, visuals.getEffectSeconds("Hasted"), 0.001f, "for five seconds");
        var layers = SETTINGS.effectLayers().stream().filter(art -> art.effect().equals("Hasted"))
                .map(art -> Main.layerOf(art, SETTINGS)).toList();
        assertFalse(layers.isEmpty(), "the premise: Hasted is drawn in layers");
        for (var layer : layers) {
            assertEquals(EffectLayer.AURA, layer.type(), "worn, and going where it goes");
            assertTrue(layer.renews(), "a second haste carries it on to the new end");
        }
    }
```

`AuraTest.java`. The imports become:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.EffectLayer;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
```

before `// ---- reach ----`:

```java
    // ---- worn ----

    /** One aura's look played: which, on whom, on what frame. */
    private record Worn(String look, ObjectId on, int frame) {
    }

    /**
     * Every aura's look played over the next {@code frames}, in the order played -- watched through nobody's fog, since
     * a room with no hero in it is a room nobody sees.
     */
    private static List<Worn> worn(DukeGame game, int frames) {
        var looks = SETTINGS.skills().stream().filter(skill -> skill.effect().isAura()).map(Skill::look).toList();
        game.watch();
        var worn = new ArrayList<Worn>();
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var event : game.getSnapshot().events()) {
                if (event instanceof EffectPlayed played && looks.contains(played.name())) {
                    worn.add(new Worn(played.name(), played.riding(), played.frame()));
                }
            }
        }
        return worn;
    }

    /** Its aura, as shipped. */
    private static Skill auraOf(GameObject bearer) {
        return SETTINGS.skillsFor(bearer.getTemplate().name()).stream().filter(skill -> skill.effect().isAura())
                .findFirst().orElseThrow();
    }

    /**
     * Each bearer wears its aura's look every TickFrames, on the frames its object id falls on, as a brain's re-plans
     * are: a summoner its might, a healer its mana, a Revenant its drink -- and nothing on those they reach.
     */
    @Test
    void eachBearerWearsItsLookEveryTickOnTheFramesItsIdFallsOn() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 60f, 150f), one(HEALER, 180f, 150f),
                one(REVENANT, 300f, 150f), one("Skeleton", 90f, 150f));
        var worn = worn(room.game(), 90);

        assertEquals(List.of("MightAura", "ManaAura", "BloodAura"),
                room.ones().subList(0, 3).stream().map(bearer -> auraOf(bearer).look()).toList());
        for (var bearer : room.ones().subList(0, 3)) {
            var aura = auraOf(bearer);
            var its = worn.stream().filter(one -> bearer.getId().equals(one.on())).toList();
            assertEquals(List.of(aura.look(), aura.look(), aura.look()), its.stream().map(Worn::look).toList(),
                    "three beats in three seconds: " + its);
            for (var one : its) {
                assertEquals(Math.floorMod(bearer.getId().value(), aura.tickFrames()),
                        one.frame() % aura.tickFrames(), "on the frames its id falls on: " + its);
            }
        }
        assertEquals(9, worn.size(), "and on nobody else: " + worn);
    }

    /** A bearer fallen wears it no more; and an aura is worn from the level that opens it, never before. */
    @Test
    void aFallenOrUnopenedAuraIsNotWorn() {
        var fallen = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        assertFalse(worn(fallen.game(), 30).isEmpty(), "the premise: standing, it wears its might");
        var body = fallen.get(0).getBody();
        body.damage(body.getHealth() + 1f);
        assertEquals(List.of(), worn(fallen.game(), 60), "fallen");

        var data = Content.data().replace("      Effect = DAMAGE_AURA\n",
                "      Effect = DAMAGE_AURA\n      LevelPerRank = 6\n");
        assertNotEquals(Content.data(), data, "the premise: the summoner's might waits for its sixth level");
        var settings = DungeonSettings.parse(data);
        var waiting = room(settings, NO_WALL, one(SUMMONER, 100f, 150f));
        assertEquals(List.of(), worn(waiting.game(), 60), "at its first level");
        Spawner.scale(waiting.get(0), 6, settings);
        assertFalse(worn(waiting.game(), 30).isEmpty(), "at its sixth");
    }

    /**
     * Each look is measured two of its bearer's ticks, as wide as its Radius, and renews as a state does: a ring lying
     * on the floor that follows its bearer, fading in over its first half and out over its second -- so the one laid a
     * tick later takes over as it goes, and the two stand as one steady ring.
     */
    @Test
    void eachRingIsMeasuredTwoTicksAtItsRadiusAndTakenOverByTheNext() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        var auras = SETTINGS.skills().stream().filter(skill -> skill.effect().isAura()).toList();
        assertEquals(3, auras.size(), "the premise: three auras");

        for (var aura : auras) {
            assertEquals(2f * aura.tickFrames() / GameConstants.LOGICFRAMES_PER_SECOND,
                    visuals.getEffectSeconds(aura.look()), 0.001f, aura.look() + ": two ticks");
            assertEquals(aura.radius(), visuals.getEffectReach(aura.look()), 0.001f, aura.look() + ": its Radius");
            var layers = SETTINGS.effectLayers().stream().filter(art -> art.effect().equals(aura.look()))
                    .map(art -> Main.layerOf(art, SETTINGS)).toList();
            assertFalse(layers.isEmpty(), aura.look() + " is drawn in layers");
            for (var layer : layers) {
                assertEquals(EffectLayer.MARK, layer.type(), aura.look() + " lies on the floor");
                assertTrue(layer.follows(), aura.look() + " follows its bearer");
                assertEquals(EffectLayer.REACH, layer.measure(), aura.look() + " is measured in its reach");
                assertEquals(0.5f, layer.fadeIn(), 0.001f, aura.look());
                assertEquals(0.5f, layer.fadeOut(), 0.001f, aura.look());
                assertTrue(layer.renews(), aura.look() + " renews, worn as a state");
            }
        }
    }
```

and before `aLifestealAurasShareOutOfRangeIsRefused`:

```java
    /**
     * An aura is worn at the beat it names: its Look and its TickFrames come together or not at all, else the file is
     * refused. A lifesteal is not worn, and still may not say what it looks like.
     */
    @Test
    void anAurasLookAndItsBeatComeTogether() {
        var block = ShippedBlock.of(SUMMONER).text();
        for (var line : new String[] {"      Look = MightAura\n", "      TickFrames = 30\n"}) {
            var text = block.replace(line, "");
            assertNotEquals(block, text, "the premise: " + line.strip() + " was taken off its might");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(text), line);
            assertTrue(refused.getMessage().contains("TickFrames"), refused.getMessage());
        }

        var warden = ShippedBlock.of("Warden").text();
        var drawn = warden.replace("      Effect = LIFESTEAL\n",
                "      Effect = LIFESTEAL\n      Look = BloodAura\n      TickFrames = 30\n");
        assertNotEquals(warden, drawn, "the premise: the Warden's lifesteal was given a look");
        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(drawn));
        assertTrue(refused.getMessage().contains("never cast"), refused.getMessage());
    }
```

`DungeonEffectLayerTest.java`, before `thrownAtMost`:

```java
    /**
     * Each aura's ring lies where its reach ends: measured in the aura's reach, and its texture's brightest line on its
     * Radius -- so what stands inside the ring is what it lends to, as a blast's edge is where its damage stops.
     */
    @Test
    void eachAurasRingLiesWhereItsReachEnds() throws java.io.IOException {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        int rings = 0;
        for (var aura : SETTINGS.skills().stream().filter(skill -> skill.effect().isAura()).toList()) {
            assertEquals(aura.radius(), visuals.getEffectReach(aura.look()), 0.001f, aura.look());
            for (var art : SETTINGS.effectLayers()) {
                var layer = drawn(art);
                if (!art.effect().equals(aura.look()) || !EffectLayer.MARK.equals(layer.type())) {
                    continue;
                }
                assertEquals(EffectLayer.REACH, layer.measure(), art.name() + " is not measured in reach");
                float edge = layer.sizeEnd() * brightestRadius(layer.texture()) / 2f;
                assertEquals(1f, edge, 0.05f, aura.look() + " " + art.name() + " lies at " + edge + " of its Radius");
                rings++;
            }
        }
        assertEquals(3, rings, "a ring for each of the three auras");
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.AuraTest" --tests "uz.dukeengine.dungeon.HasteTest" --tests "uz.dukeengine.dungeon.DungeonEffectLayerTest"`
Expected: FAIL, 6 tests — `eachBearerWearsItsLookEveryTickOnTheFramesItsIdFallsOn` (`expected: <[MightAura, ManaAura,
BloodAura]> but was: <[, , ]>`), `aFallenOrUnopenedAuraIsNotWorn` (`the premise: standing, it wears its might`),
`eachRingIsMeasuredTwoTicksAtItsRadiusAndTakenOverByTheNext` (`: its Radius ==> expected: <60.0> but was: <0.0>`),
`anAurasLookAndItsBeatComeTogether` (`the premise: Look = MightAura was taken off its might`),
`theHastenedWearsHastedForAsLongAsTheHaste` (`expected: <Hasted> but was: <>`),
`eachAurasRingLiesWhereItsReachEnds` (`expected: <60.0> but was: <0.0>`).

- [ ] **Step 3: Worn** — `SkillBook.java`. The haste's comment and its look, in `case HASTE`:

```java
                // It turns to the one it hastens and is seen casting, as a mending is; and the one it hastens wears
                // the haste's look for as long as it lasts -- Main.measureLooks gives the look its DurationFrames,
                // and Main.layerOf carries it on to a second haste's end.
                Facing.turnToward(owner, hastened);
                world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                        owner.getPosition(), hastened.getPosition()));
                if (skill.hasLook()) {
                    world.effect(skill.look(), hastened);
                }
```

in `update()`, before the whirlwind:

```java
        if (hasteFrames > 0) {
            hasteFrames--;
        }
        wearTheAuras();
        turnTheWhirlwind();
```

and before `turnTheWhirlwind()`'s note:

```java
    /**
     * Its auras are worn: each its level has opened and that says what it looks like is played on it every
     * {@code TickFrames} -- on the frames its object id falls on, as a brain's re-plans are, so a roomful are not all
     * played on one frame -- for as long as it lives, stunned or not. {@code Main.measureLooks} makes each last two
     * ticks, so it rides its bearer while it stands and is gone within two of its fall. An event: out of the checksum.
     */
    private void wearTheAuras() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || owner.isEffectivelyDead()) {
            return;
        }
        for (var skill : skills) {
            int beat = skill.tickFrames();
            if (skill.effect().isAura() && skill.hasLook() && beat > 0
                    && world.getFrame() % beat == Math.floorMod(owner.getId().value(), beat)
                    && LevelBonus.levelOf(owner) >= skill.levelForRank(1)) {
                world.effect(skill.look(), owner);
            }
        }
    }
```

- [ ] **Step 4: What the file may say** — `DungeonSettings.validate()`, the passive's rule relaxed for an aura, and the
  beat with its look:

```java
            // A passive is never cast, so what only a cast reads would be a number nothing read -- but an aura is worn,
            // and may say what it looks like: its bearer plays it on itself.
            require(!skill.effect().isPassive() || skill.damage() == 0f && skill.manaCost() == 0
                            && skill.windUpFrames() == 0 && !skill.hasProjectile()
                            && (!skill.hasLook() || skill.effect().isAura()),
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect()
                            + ", never cast: a Damage, ManaCost, WindUpFrames, Projectile or Look on it is read by"
                            + " nothing -- only an aura is worn, and may name a Look");
            // And worn at the beat it names: an aura's Look and its TickFrames come together, or neither does.
            require(!skill.effect().isAura() || skill.hasLook() == (skill.tickFrames() > 0),
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect() + ", worn every"
                            + " TickFrames as its Look: the two come together, or neither does");
```

- [ ] **Step 5: How long, and what renews** — `Main.java`. `layerOf`'s paragraph on renewing:

```java
     * <p>Only what was said, because the defaults are the client's and are written down once,
     * in {@code EffectLayer.Builder}. A look worn as a state renews — the stun's count starts
     * again at each stun, a second haste starts the haste again, and an aura is played on its
     * bearer again at every beat — so it must last to the new end: see {@link #wornAsAState}.
     * Named by the files, so no name is compiled in. Other auras keep the engine's default
     * drop: the knight's Whirlwind is cast again at each landing and must not be stretched.
```

its last line, in place of the two that read the stun's look, and the rule after it:

```java
        return layer.renews(wornAsAState(art.effect(), settings)).build();
    }

    /** Whether a look is worn as a state: the Combat block's {@code StunLook}, and every haste's and aura's. */
    private static boolean wornAsAState(String look, DungeonSettings settings) {
        return look.equals(settings.combat().stunLook()) || settings.skills().stream().anyMatch(skill ->
                skill.look().equals(look) && (skill.effect() == uz.dukeengine.dungeon.skill.SkillEffect.HASTE
                        || skill.effect().isAura()));
    }
```

`measureLooks`, its note on how long and the aura's two ticks:

```java
     * <p>How long: a skill that lasts gives its duration; an aura, two of the beats its
     * bearer wears it at, so the one played at each beat takes over from the last; one
     * that is aimed and then lands gives its wind-up, which is how long the ground is
     * marked; one that slows what it caught gives the slow, which is how long they wear
     * the frost.
```

```java
                int frames = skill.durationFrames() > 0 ? skill.durationFrames()
                        : skill.effect().isAura() ? 2 * skill.tickFrames()
                        : skill.windUpFrames() > 0 ? skill.windUpFrames()
                        : skill.slowFrames();
```

(The haste's look needs nothing here: it lasts, and its `DurationFrames` already measure it — five seconds.)

- [ ] **Step 6: The four looks, in their mages' files** — `skeleton_summoner.duke`. The haste's look, after its
  `ManaCost`:

```
      ManaCost = 25
      ; And the one it hastens wears it for as long -- see the Effect Hasted, below.
      Look = Hasted
      MaxRank = 1
      Name = Shiddat
```

the might's look and beat, after its `Radius` — the beat written under the look (`SkillLookTest`, Step 7, reads it so):

```
      Radius = 60
      ; Worn: a faint ring as wide as its Radius, played on it every second -- see the
      ; Effect MightAura, below.
      Look = MightAura
      TickFrames = 30
      MaxRank = 1
      Name = Qudrat
```

and after the Monster block's `End`:

```
; ---------------------------------------------------------------------------
; What the one a summoner hastens wears, for as long as the haste: the kit's Focus -- a
; glow round the body, motes rising -- in a fury's red. No layer says how long: the client
; is told the haste's DurationFrames (Main.measureLooks), and a second haste while it
; burns carries it on to the new end (Main.layerOf) -- which is why both are let out as
; they go rather than laid once.
; ---------------------------------------------------------------------------
Effect
  Name = Hasted

  Layers = [
    Layer
      Name = Glow
      Type = AURA
      Texture = kit/effects/particles/light_02.png
      Blend = Additive
      Cover = 0.1
      Count = 5
      Rate = 6
      Life = [0.6, 0.8]
      Size = [20, 22]
      Colour = [0xFF5A3A, 0xD81E12]
      Alpha = [0.35, 0]
      Spin = 30
      Height = 6
    End,

    Layer
      Name = Motes
      Type = AURA
      Texture = kit/effects/particles/star_01.png
      Blend = Additive
      Count = 10
      Rate = 7
      Radius = 6
      Life = [0.8, 1.2]
      Size = [2.4, 0.4]
      Colour = [0xFFB080, 0xFF3020]
      Speed = [2, 5]
      Direction = UP
      Spread = 20
      Height = 1
    End
  ]
End

; ---------------------------------------------------------------------------
; A summoner's might, worn: a faint ring on the floor as wide as its Radius, in its own
; purple. It plays it on itself every TickFrames (see its E), and the client is told it
; lasts two of them (Main.measureLooks): each ring fades in over its first half and out
; over its second, so the one laid a beat later takes over as it goes and the two stand
; as one steady ring -- until the summoner falls, and the last is gone within two
; seconds. circle_02's brightest line lies at 0.5625 of its half-width, so a size of 3.56
; of the reach lays it on the Radius itself: what stands inside the ring is lent to.
; ---------------------------------------------------------------------------
Effect
  Name = MightAura

  Layers = [
    Layer
      Name = Ring
      Type = MARK
      Measure = Reach
      Follows = Yes
      Texture = kit/effects/particles/circle_02.png
      Blend = Additive
      Count = 1
      Size = [3.56, 3.56]
      Colour = [0xB06AE8, 0xB06AE8]
      Alpha = [0.3, 0.3]
      FadeIn = 0.5
      FadeOut = 0.5
      Height = 0.3
    End
  ]
End
```

`skeleton_healer.duke`, after its W's `Radius`:

```
      Radius = 60
      ; Worn: a faint ring as wide as its Radius, played on it every second -- see the
      ; Effect ManaAura, below.
      Look = ManaAura
      TickFrames = 30
      MaxRank = 1
      Name = Sehr buloqi
```

and after the Monster block's `End`:

```
; ---------------------------------------------------------------------------
; A healer's mana, worn: the summoner's MightAura in the healer's own green -- the same
; ring on the floor as wide as its Radius, played on it every TickFrames and lasting two.
; ---------------------------------------------------------------------------
Effect
  Name = ManaAura

  Layers = [
    Layer
      Name = Ring
      Type = MARK
      Measure = Reach
      Follows = Yes
      Texture = kit/effects/particles/circle_02.png
      Blend = Additive
      Count = 1
      Size = [3.56, 3.56]
      Colour = [0x8FDC6A, 0x8FDC6A]
      Alpha = [0.3, 0.3]
      FadeIn = 0.5
      FadeOut = 0.5
      Height = 0.3
    End
  ]
End
```

`revenant.duke`, after its Q's `Radius`:

```
      Radius = 60
      ; Worn: a faint ring as wide as its Radius, played on it every second -- see the
      ; Effect BloodAura, below.
      Look = BloodAura
      TickFrames = 30
      MaxRank = 1
      Name = Qon aurasi
```

and after the Monster block's `End`:

```
; ---------------------------------------------------------------------------
; A Revenant's drink, worn: the summoner's MightAura in the Revenant's own blue -- the same
; ring on the floor as wide as its Radius, played on it every TickFrames and lasting two.
; ---------------------------------------------------------------------------
Effect
  Name = BloodAura

  Layers = [
    Layer
      Name = Ring
      Type = MARK
      Measure = Reach
      Follows = Yes
      Texture = kit/effects/particles/circle_02.png
      Blend = Additive
      Count = 1
      Size = [3.56, 3.56]
      Colour = [0x6FA8FF, 0x6FA8FF]
      Alpha = [0.3, 0.3]
      FadeIn = 0.5
      FadeOut = 0.5
      Height = 0.3
    End
  ]
End
```

- [ ] **Step 7: The two tests that held the old rules** — `StunTest.theStarsLastAsLongAsTheLongestStun` said the stun
  alone renews; a haste's and an aura's looks do now. An import, after `SkillBook`'s:

```java
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
```

its note, and the other auras it asks of:

```java
    /** They last as long as the longest stun any skill gives, and no layer of them says how
     * long for itself. The stun's layers renew with each stun; other AURA layers do not, but
     * for those of a look worn as a state -- a haste's, an aura's: see HasteTest and AuraTest. */
```

```java
        // Other auras keep the engine's default: they do not renew
        var states = SETTINGS.skills().stream()
                .filter(skill -> skill.effect() == SkillEffect.HASTE || skill.effect().isAura())
                .map(Skill::look).toList();
        var otherAuras = SETTINGS.effectLayers().stream()
                .filter(art -> !art.effect().equals(SETTINGS.combat().stunLook()) && !states.contains(art.effect()))
```

`SkillLookTest.theLookCannotReachTheSimulation` strips every `Look` line from the files and plays a floor; an aura left
with its beat and no look is now refused, so the beat written under a look goes with it:

```java
    /**
     * The same file with every skill's Look line cut out of it -- and an aura's TickFrames, written under its Look: the
     * beat it is worn at, which nothing else reads, and which a file may not name without the look it beats.
     */
    private static String withoutTheLooks(String file) {
        var kept = new StringBuilder();
        boolean underALook = false;
        for (var line : file.split("\n", -1)) {
            boolean look = line.trim().startsWith("Look = ");
            if (!look && !(underALook && line.trim().startsWith("TickFrames = "))) {
                kept.append(line).append('\n');
            }
            underALook = look;
        }
        return kept.toString();
    }
```

(Its floor, seed 31, has the keep's two summoners and two healers on it: with the looks and without, it plays the same
ninety checksums — the rings and `Hasted` never reach the simulation.)

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.AuraTest" --tests "uz.dukeengine.dungeon.HasteTest" --tests "uz.dukeengine.dungeon.DungeonEffectLayerTest" --tests "uz.dukeengine.dungeon.StunTest" --tests "uz.dukeengine.dungeon.SkillLookTest"`
Expected: PASS — `AuraTest` 31, `HasteTest` 13, `DungeonEffectLayerTest` 25, `StunTest` 9, `SkillLookTest` 11. (Without
Step 7: `theLookCannotReachTheSimulation` is refused its stripped file — `Revenant's Skill Q is a LIFESTEAL_AURA, worn
every TickFrames as its Look: the two come together, or neither does` — and `theStarsLastAsLongAsTheLongestStun` fails
`other auras do not renew; the stun alone does`.)

- [ ] **Step 9: Run the whole suite**

Run: `./gradlew test`
Expected: PASS, 1000 tests.

- [ ] **Step 10: Look at it** — the owner looks, in the game (`./gradlew run`), at a keep's court on the first floor:
  a faint purple ring under each summoner and a green one under each healer, each as wide as its reach and steady while
  it stands, gone within two seconds of its fall; a red glow and rising motes on whoever a summoner hastens, for five
  seconds; and a blue ring under a Revenant met on the way. The rings' `Alpha` and `Colour`, `Hasted`'s `Size`,
  `Alpha` and colours are the numbers to tune. Nothing to run here beyond the suite: report what to look at.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/Main.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/resources/data/units/revenant.duke src/main/resources/data/units/skeleton_healer.duke src/main/resources/data/units/skeleton_summoner.duke src/test/java/uz/dukeengine/dungeon/AuraTest.java src/test/java/uz/dukeengine/dungeon/DungeonEffectLayerTest.java src/test/java/uz/dukeengine/dungeon/HasteTest.java src/test/java/uz/dukeengine/dungeon/SkillLookTest.java src/test/java/uz/dukeengine/dungeon/StunTest.java
git commit -m "The hastened wear Hasted, and each aura's bearer a ring as wide as its reach, renewed at each beat" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

## Where the spec and the code part

- **The ring is a lying `MARK`, and it is not renewed by the client: two lie under the bearer at once.** The spec asks
  for "a faint ring on the floor" that is "renewed at each" beat. The client lays only a `RING`, `MARK` or `ARC` flat on
  the floor, and it keys and renews only an `AURA` layer (`LayeredEffects.start`: the key is made for an aura riding
  somebody); an `AURA` stands facing the camera. So each ring is a `MARK` that `Follows` its bearer, laid at every beat
  and lasting two (`Main.measureLooks`, as the spec says), fading in over its first half and out over its second: the
  one laid a beat later fades in exactly as the one before fades out (the client's fades are smoothsteps, and
  `s(u) + 1 − s(u)` is 1), so the two stand as one ring of steady brightness. `Main.layerOf` still renews every aura's
  look, as the spec's rule says — the flag is read by the client for an `AURA` layer, and a look an aura adds one to
  (motes, say) is renewed; on the lying ring it changes nothing. A lying layer keyed and renewed as an `AURA` is would be
  an engine request; nothing here needs it, so none is made.
- **`Hasted` is let out as it goes** (`Rate`), not laid once: the client's renewal carries a layer that feeds on to the
  new end, where a single particle laid once keeps the life it was born with. It is the kit's `Focus` cut to the two
  layers the spec names — the glow round the body and the motes rising — in a fury's red.
- **An aura is asked more often than at each blow.** The engine's weapon weighs its target every frame it has one
  (`WeaponUpdate.choose` → `dealt` → every `DamageModifier`), so a monster in a fight asks for its might each frame, not
  only as it strikes. The figures are the same; the cost is one pass over the floor's objects per such monster per
  frame and a sight line per bearer in reach — small at the dungeon's sizes; the `ponytail:` note on `auraOn` names
  the cache for when a crowded floor shows it.
- **A hero's book never asks.** `auraReach` is 0 for a unit that is not a `Monster` (and wherever no file gives an
  aura): the spec's "none for a hero's book". His side could bear none anyway — a passive is refused on a hero.
- **Where the book stands in a monster's modules matters to the haste's frames.** The five new `SkillBook` blocks stand
  after the `Swing`, so after the weapon: in a frame the weapon reads the haste before the book counts it down, and the
  blows struck in the 150 frames after a cast wait 17 (`HasteTest` counts them). The three casting mages' books already
  stood after their weapons.
- **`EMPOWER` and the might multiply** — `(1 + e/100)(1 + a/100)` — rather than add. Only a hero empowers and no hero
  is lent might, so the two never meet.
- **`drink` adds the shares, then heals once** — `dealt * share / 100f`. For a lone `LIFESTEAL` that is the figure it
  always healed, bit for bit (`LifestealTest` unchanged).
- **The haste's candidates and an aura's receivers are those that carry a book**, which every monster now does; the
  Rogue carries one too, but is of the other side. `Range` and `Radius` are measured middle to middle by the engine's
  `objectsInRange`.
- **`isAura()` is a method over the three constants**, not a third constructor argument: the passive flag stays said
  once on each constant, and an aura is always a passive.
- **The tests that changed for the change:** `MonsterSummoningTest.summoningWith` edits the summoner with its summoning
  alone (the haste writes `DurationFrames` and `CooldownFrames` too); `DungeonSettingsTest`'s exhaustive switch learns
  the four, its stand-in's note moved above the line; the five `Skill` records written out whole gain `manaRegen`;
  `StunTest`'s "the stun alone renews" became "the stun, a haste's and an aura's"; `SkillLookTest.withoutTheLooks`
  strips an aura's beat written under its look (Task 5, Step 7).
- **Tests watch through nobody's fog** where no hero stands (`DukeGame.watch()`): the snapshot hands on only the moments
  its player can see, and a room with no hero in it is seen by nobody. A haste cast by hand is read off
  `GameLogic.drainEvents()` before the next frame drains it.
- **A second haste is tested with two summoners:** one summoner's cooldown (12 s) outlasts its haste (5 s), so the
  haste that refreshes another is another summoner's — as in the keep's court, where two stand.
- **`theMeteorsBlastDrinks` gives the fire mage room by raising its ceiling** (`GrowableBody.setMaxHealth`), its health
  left where it was: lowered, its brain takes it for a wound and fights, and its own fire would land on the Rogue too.
- **The Revenant's ring is blue.** The spec says each ring is "in its bearer's colour", and the Revenant's `Colour` is
  `0x6FA8FF`; a `BloodAura` in blood's red is one line in `revenant.duke` if the owner would rather.
- **Beyond the spec's list, touched for its words only:** `Skill`'s `@param` notes, `SkillEffect`'s class note and
  `LIFESTEAL`'s, `SkillBook`'s class note and `drink`'s, the three mages' notes, `skeleton.duke`'s note on what aims at
  nothing, `FallingUpdate`'s comment.

## Whole-suite counts

| After | Tests | New |
|---|---|---|
| master with pieces 3 and 4 merged (`a8570b6`) | 955 | — |
| Task 1 | 967 | `HasteTest` 12 |
| Task 2 | 979 | `AuraTest` 12 |
| Task 3 | 985 | `AuraTest` +6 |
| Task 4 | 994 | `AuraTest` +9 |
| Task 5 | 1000 | `AuraTest` +4, `HasteTest` +1, `DungeonEffectLayerTest` +1 |
