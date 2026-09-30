# Monsters that grow like heroes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every monster stands at a level of its own — climbing from the way in to the chamber before the boss's, the
boss two above, tier after tier from where the last closed — and the level is what grows its health, its blow, its
skills, its mending, its worth and its mana; monsters cast several skills, opened by the heroes' own `LevelPerRank`,
paid from pools of their own; the fire mage calls a violet meteor down from its sixth level; and a monster's medallion
shows its own level.

**Architecture:** The rule is `DungeonSettings`' (`wayInLevel`, `beforeBossLevel`, `bossLevel`, `levelAlong`), read off
the `Descent` block and handed a tier. The steps along the way come from `StageCheck`'s own walk, now counting
(`StageCheck.walk` → `Walk.to(x, y)`); `Spawner.levelsOf` reads each placed monster's level off it. `Spawner.scale` is
the one step that makes a creature its level — health grown, a `LevelBonus` (which replaces `DepthBonus`) attached, its
worth set, its pool sized and filled, its `level:N` word set — and a rift's `rise` calls it with its caller's level.
`MonsterBrain` walks its book's skills in file order and casts the first that its level has opened, is ready, is
affordable and whose own condition holds. The meteor is a `Skill` block on the fire mage, its mark a `Projectile` of its
own drawn by an `Effect` of its own.

**Tech Stack:** Java 25, JUnit 5, Gradle (`./gradlew test`), `.duke` data read by the engine's record reader,
duke-engine 0.7.0 from the checkout beside this one, with E7 (`UnitBarLook.withLevelWord`) in it.

**Spec:** `docs/plan/2026-09-30-monsters-grow.md`

**Verified:** every task below was carried out in a worktree at master `7d41ada`, against an engine snapshot carrying
E7: each task's new tests seen failing as written here, then passing, then the whole suite. The code in the blocks is
the code that ran. Whole suite: 843 before, then **849, 853, 862, 865, 871, 877, 883**, all passing.

## Global Constraints

- Lock-step: the walk is a breadth-first count of whole steps, four ways from a cell in a fixed order, by the engine's
  own `PathGrid.canStep`; a tier's end is `StrictMath.pow` rounded once; a monster's place along the way is rounded
  once; mana is whole points and tenths grown in integer arithmetic; the brain walks a `List` in file order. No dice, no
  clock, no hash iteration; everything happens on the simulation thread when a floor is placed or a rift rises.
- Names and numbers come from data: the level lines and per-level percents in the `Descent` block, `MaxMana` and
  `ManaRegen` on a `Monster` block, `LevelPerRank` and `ManaCost` on its skills, `LevelWord` on the `UnitBar`, the
  meteor on the fire mage's block, its mark and its look beside each other in `data/projectiles/`.
- Every combat feature is a skill of the one skill system: the meteor is a `Skill`, mana is the `SkillBook`'s own pool,
  a skill opens by `LevelPerRank`.
- `../duke-engine` is never edited. No engine request is needed: E7 is used as it landed.
- Each task is committed on its own once its tests pass: the subject, a blank line, and
  `Co-Authored-By: <the model that did the work> <noreply@anthropic.com>`.
- **Piece 3 (`key-to-the-keep`) is built beside this** and changes `gen/Keep`, `DungeonGenerator.guard`,
  `DungeonSettings.bossGuards()`, `run/GateUpdate`, `Spawner.Placed` and its props loop, `DungeonRun`, a new
  `run/Mission`, `loot/*`, `BagScreen`, `PartyOrders`, `Dungeon.obey`, and `world.duke`, `gate.duke`, `key.duke`,
  `hud.duke` (a `Cursor`). This plan never touches `Keep`, the generator, `Placed`, the props loop, `DungeonRun`,
  `GateUpdate` or `Mission`; it only reads `GeneratedDungeon.keep()`, `Keep.chamber()` and `Keep.isCourt(x, y)`, which
  piece 3 keeps. Where a file is shared, each task says where the edit sits and how far it is from piece 3's. Checked:
  this plan's last commit and `key-to-the-keep` at `85ca4d5` merge with no conflict (`git merge-tree`), and the merged
  tree's whole suite passes, 939 tests.

## Files

| File | What it is |
|---|---|
| `src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java` | `Descent`: the level lines; the per-level percents in place of the per-depth ones |
| `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` | the level rule; `healthAtLevel`, `damageAtLevel`, `experienceAtLevel`, `manaAtLevel`; the per-depth accessors gone; checks per skill, pool and word |
| `src/main/java/uz/dukeengine/dungeon/stage/StageCheck.java` | its walk counts steps: `Walk`, `walk(GeneratedDungeon)` |
| `src/main/java/uz/dukeengine/dungeon/run/Spawner.java` | `levelsOf`; `scale(GameObject, int level, DungeonSettings)` public, the one step |
| `src/main/java/uz/dukeengine/dungeon/combat/LevelBonus.java` | **new** (from `DepthBonus.java`, `git mv`) — a creature's level and its two multipliers |
| `src/main/java/uz/dukeengine/dungeon/skill/SummoningUpdate.java` | reads its caller's level; `rise` calls `Spawner.scale` |
| `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` | reads `LevelBonus`; `usesMana`'s note |
| `src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java` | `SUMMON`'s and `isPassive`'s notes |
| `src/main/java/uz/dukeengine/dungeon/run/HeroStatus.java` | the card's blow is the creature's own; its notes on the level and mana |
| `src/main/java/uz/dukeengine/dungeon/world/UnitBar.java` | + `levelWord` |
| `src/main/java/uz/dukeengine/dungeon/Main.java` | `unitBars` hands the word on |
| `src/main/java/uz/dukeengine/dungeon/Dungeon.java` | `SummoningUpdate` built with the settings; a comment |
| `src/main/java/uz/dukeengine/dungeon/content/Monster.java`, `MonsterKind.java` | + `maxMana`, `manaRegen`; `skillKey`, `hasSkill`, `casting` gone |
| `src/main/java/uz/dukeengine/dungeon/ai/MonsterBrain.java` | chooses among its skills; `ITS_ONLY_RANK` gone |
| `src/main/java/uz/dukeengine/dungeon/loot/LootDrop.java` | one word of its note |
| `src/main/resources/data/world/generation.duke` | the level lines, the per-level percents |
| `src/main/resources/data/world/hud.duke` | `Segments = [*:120]`; `LevelWord = level:`; the medallion's note |
| `src/main/resources/data/units/skeleton.duke` | `GrowableBody`, as every monster's |
| `src/main/resources/data/units/warden.duke`, `data/game.duke` | notes |
| `src/main/resources/data/units/skeleton_mage.duke`, `skeleton_healer.duke`, `skeleton_summoner.duke` | pools and costs; the fire mage's meteor, written first |
| `src/main/resources/data/projectiles/skull_meteor_mark.duke` | **new** — `SkullMeteorMark` and its `SkullMeteor` |
| `src/main/resources/data/sounds/sfx.duke` | `spawned.SkullMeteorMark` |
| `src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java` | **new** — the rule, the way, what a level gives, the bar |
| `src/test/java/uz/dukeengine/dungeon/ai/MonsterManaTest.java` | **new** — pools |
| `src/test/java/uz/dukeengine/dungeon/SkullMeteorTest.java` | **new** — the meteor |
| `src/test/java/uz/dukeengine/dungeon/ai/MonsterSkillTest.java` | which of its skills; the level's bonus; the fireball found by its key |
| `src/test/java/uz/dukeengine/dungeon/ai/MonsterSummoningTest.java`, `DungeonUnitBarTest.java`, `stage/StagePlayTest.java`, `run/HeroStatusTest.java`, `DungeonMonsterArtTest.java`, `LifestealTest.java`, `StunTest.java` | follow the change |

---

### Task 1: A monster's level by the tier of the place it is met in

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java` (`Descent`, ~lines 67–87)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate()` after the `BossGuardRing`
  check, ~line 612; the end of the class, after `scaled`, ~line 1250)
- Modify: `src/main/resources/data/world/generation.duke` (the end of the `Descent` block, ~line 172)
- Test: `src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java` (**new**)

**Interfaces:**
- Consumes: `StrictMath.pow`; `Stages.load(String, DungeonSettings)` (the shipped `deep` stage, Difficulty 8).
- Produces: `ProceduralMap.Descent` gains `int wayInLevel, int beforeBossLevel, int tierGrowthPercent,
  int bossLevelsAbove, int maxMonsterLevel` after `experiencePercentPerDepth`; `DungeonSettings.maxMonsterLevel()`,
  `beforeBossLevel(int tier)`, `wayInLevel(int tier)`, `bossLevel(int tier)`,
  `levelAlong(int tier, int walked, int way)`. `MonsterLevelTest` with `SETTINGS` and `dataWith(String, String)`.

**Piece 3:** it rewrites the `@param bossGuardRing` line of `Descent`'s Javadoc and the `BossGuards` comment in
`generation.duke`; the paragraph below goes before `@param bosses`, the record's new components three lines under that
Javadoc, and the new lines after `ExperiencePercentPerDepth` — none touches or abuts those lines. In `validate()` piece 3
changes the keep's size check five lines further down.

- [ ] **Step 1: Write the failing test** — `src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java`:

```java
package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.Stages;

/**
 * A monster's level: where it is met, and what the level makes of it.
 *
 * <p>A place has a tier -- today a floor's depth, which is what a stage's {@code Difficulty} already means -- and the
 * level rule is handed the tier and nothing else: the way in at one end, the chamber before the boss's at the other,
 * the boss two above, and nothing above the cap.
 */
class MonsterLevelTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The shipped files with one line of them saying something else. */
    private static String dataWith(String line, String instead) {
        var data = Content.data().replace(line, instead);
        assertNotEquals(Content.data(), data, "the premise: the shipped files say " + line.strip());
        return data;
    }

    // ---- the rule ----

    /** Each tier runs from where the one before closed: 1 to 8, 8 to 13, 13 to 20, 20 to 33, the boss two above. */
    @Test
    void eachTierRunsFromWhereTheOneBeforeClosed() {
        int[][] tiers = {{1, 1, 8, 10}, {2, 8, 13, 15}, {3, 13, 20, 22}, {4, 20, 33, 35}};
        for (var tier : tiers) {
            assertEquals(tier[1], SETTINGS.wayInLevel(tier[0]), "tier " + tier[0] + "'s way in");
            assertEquals(tier[2], SETTINGS.beforeBossLevel(tier[0]), "tier " + tier[0] + " before its boss");
            assertEquals(tier[3], SETTINGS.bossLevel(tier[0]), "tier " + tier[0] + "'s boss");
        }
    }

    /** Asked straight for the fourth tier, it is the fourth tier: worked out in one step, never tier by tier. */
    @Test
    void theFourthTierAskedDirectlyIsTheFourthTier() {
        var fresh = DungeonSettings.load();

        assertEquals(33, fresh.beforeBossLevel(4));
        assertEquals(20, fresh.wayInLevel(4));
        assertEquals(35, fresh.bossLevel(4));
    }

    /** With no growth, every tier after the first stays where the first closed: 8 to 8. */
    @Test
    void withNoGrowthEveryTierAfterTheFirstStaysWhereTheFirstClosed() {
        var flat = DungeonSettings.parse(dataWith("    TierGrowthPercent = 60\n", "    TierGrowthPercent = 0\n"));

        assertEquals(1, flat.wayInLevel(1));
        for (int tier = 2; tier <= 6; tier++) {
            assertEquals(8, flat.wayInLevel(tier), "tier " + tier + "'s way in");
            assertEquals(8, flat.beforeBossLevel(tier), "tier " + tier + " before its boss");
        }
    }

    /** Nothing stands above the cap, the boss included: the deep stage plays at it throughout. */
    @Test
    void nothingStandsAboveTheCap() {
        assertEquals(50, SETTINGS.maxMonsterLevel());
        for (int tier = 1; tier <= 20; tier++) {
            assertTrue(SETTINGS.bossLevel(tier) <= SETTINGS.maxMonsterLevel(),
                    "tier " + tier + "'s boss stands at " + SETTINGS.bossLevel(tier));
        }
        int deep = Stages.load("deep", SETTINGS).difficulty();
        assertEquals(50, SETTINGS.wayInLevel(deep), "the deep stage's way in");
        assertEquals(50, SETTINGS.beforeBossLevel(deep), "and before its boss");
        assertEquals(50, SETTINGS.bossLevel(deep), "and its boss");
    }

    /**
     * Along the way a level is the way in plus its share of the climb, the share the steps walked over the steps to
     * the chamber -- rounded once, and never past that chamber's.
     */
    @Test
    void alongTheWayALevelIsItsShareOfTheClimb() {
        assertEquals(1, SETTINGS.levelAlong(1, 0, 70), "at the way in");
        assertEquals(5, SETTINGS.levelAlong(1, 35, 70), "half way: 1 + 7 x 0.5 is 4.5, rounded once");
        assertEquals(7, SETTINGS.levelAlong(1, 60, 70), "most of the way: 1 + 7 x 6/7 is 7");
        assertEquals(8, SETTINGS.levelAlong(1, 70, 70), "in the middle of the chamber");
        assertEquals(8, SETTINGS.levelAlong(1, 140, 70), "and past it, the chamber's");
        assertEquals(8, SETTINGS.levelAlong(1, -1, 70), "and where no step reaches, the whole way");
        assertEquals(11, SETTINGS.levelAlong(2, 35, 70), "the second tier half way: 8 + 5 x 0.5, rounded once");
    }

    /** A rule that steps back is refused when the file is read: no way in below 1, no chamber below it, no shrinking. */
    @Test
    void aRuleThatStepsBackIsRefused() {
        for (var wrong : new String[][] {
                {"    WayInLevel = 1\n", "    WayInLevel = 0\n"},
                {"    BeforeBossLevel = 8\n", "    BeforeBossLevel = 0\n"},
                {"    TierGrowthPercent = 60\n", "    TierGrowthPercent = -10\n"},
                {"    BossLevelsAbove = 2\n", "    BossLevelsAbove = -1\n"},
                {"    MaxMonsterLevel = 50\n", "    MaxMonsterLevel = 0\n"}}) {
            var data = dataWith(wrong[0], wrong[1]);
            assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data), wrong[1].strip());
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MonsterLevelTest"`
Expected: FAIL — compilation error, `cannot find symbol: method wayInLevel(int)` (and `beforeBossLevel`, `bossLevel`,
`maxMonsterLevel`, `levelAlong`) in `DungeonSettings`.

- [ ] **Step 3: The `Descent` block's level lines** — `ProceduralMap.java`, a paragraph in `Descent`'s Javadoc before
  `@param bosses`:

```java
    /**
     * How the descent grows harder, floor by floor.
     *
     * <p>And what level a monster stands at, by the tier of the place it is met in -- today a floor's depth: from
     * {@code wayInLevel} at the way in of the first tier to {@code beforeBossLevel} in the chamber before its boss's,
     * that end grown by {@code tierGrowthPercent} a tier past the first, and every later tier's way in where the tier
     * before closed; its boss {@code bossLevelsAbove} over that chamber's, and nothing above {@code maxMonsterLevel}.
     * See {@code DungeonSettings.levelAlong}.
     *
     * @param bosses        one per floor, in order, and the list is also how many floors there are:
```

and the record's components and defaults:

```java
            int bossHealthPercentPerDepth, int bossDamagePercentPerDepth, int experiencePercentPerDepth,
            int wayInLevel, int beforeBossLevel, int tierGrowthPercent, int bossLevelsAbove, int maxMonsterLevel) {

        /** What a block leaves out. */
        public static final Descent DEFAULTS = new Descent(List.of(), Map.of(), 2, 25, 15, 20, 40, 25, 30,
                1, 8, 60, 2, 50);
```

- [ ] **Step 4: The rule** — `DungeonSettings.java`, at the end of the class after `scaled(...)`:

```java
    // ---- a monster's level ----

    /** The highest level anything down here stands at, its boss included: {@code MaxMonsterLevel}. */
    public int maxMonsterLevel() {
        return map.descent().maxMonsterLevel();
    }

    /**
     * The level of the chamber before the boss's, in a place of this tier: {@code BeforeBossLevel} on the first, grown
     * by {@code TierGrowthPercent} a tier past it -- worked out in one step, never compounded tier by tier, so the
     * fourth is the same reached by playing or asked for -- rounded once, and never above the cap.
     *
     * <p>A tier is how hard a place is: today a floor's depth, which is what a stage's {@code Difficulty} already
     * means. The rule is handed the tier and knows nothing else of floors.
     */
    public int beforeBossLevel(int tier) {
        var descent = map.descent();
        double grown = descent.beforeBossLevel()
                * StrictMath.pow(1 + descent.tierGrowthPercent() / 100.0, Math.max(0, tier - 1));
        return (int) Math.min(descent.maxMonsterLevel(), Math.round(grown));
    }

    /**
     * The level at the way in: {@code WayInLevel} on the first tier, and on every later one where the tier before
     * closed -- so a descent never steps back down, as an open world's regions run 1-8, 8-13, 13-20.
     */
    public int wayInLevel(int tier) {
        return tier <= 1 ? Math.min(map.descent().wayInLevel(), map.descent().maxMonsterLevel())
                : beforeBossLevel(tier - 1);
    }

    /** The boss's: {@code BossLevelsAbove} over the chamber before its own, and never above the cap either. */
    public int bossLevel(int tier) {
        return Math.min(beforeBossLevel(tier) + map.descent().bossLevelsAbove(), map.descent().maxMonsterLevel());
    }

    /**
     * A monster's level {@code walked} steps along a way {@code way} steps long, from the way in to the middle of the
     * chamber before the boss's: the way in's level and its share of the climb to that chamber's -- the share never
     * above the whole of it, so everything past that chamber stands at its level -- rounded once. A monster no step
     * reaches, and a way of no steps, count as the whole way.
     */
    public int levelAlong(int tier, int walked, int way) {
        int in = wayInLevel(tier);
        int end = beforeBossLevel(tier);
        if (walked < 0 || way <= 0 || walked >= way) {
            return end;
        }
        return in + (int) Math.round((end - in) * (double) walked / way);
    }
```

and in `validate()`, right after `require(map.descent().bossGuardRing() >= 1, ...)`:

```java
        var descent = map.descent();
        require(descent.wayInLevel() >= 1 && descent.beforeBossLevel() >= descent.wayInLevel(),
                "a monster's level starts at WayInLevel, at least 1, and climbs to BeforeBossLevel, no lower");
        require(descent.tierGrowthPercent() >= 0 && descent.bossLevelsAbove() >= 0,
                "TierGrowthPercent and BossLevelsAbove cannot step a monster's level back down");
        require(descent.maxMonsterLevel() >= 1, "MaxMonsterLevel is at least the first level");
```

(`WayInLevel` at least 1 and no step back is what keeps every level at least 1: the rule needs no floor of its own.)

- [ ] **Step 5: The shipped lines** — `generation.duke`, after `ExperiencePercentPerDepth = 30`, before the `Descent`
  block's `End`:

```
    ExperiencePercentPerDepth = 30

    ; ---- the level of what lives here ----
    ;
    ; Every monster stands at a level of its own. A place has a tier -- how hard
    ; it is: today a floor's depth, which is what a stage's Difficulty means --
    ; and the tier gives two ends: the way in, and the chamber before the boss's
    ; (the one the keep's road leaves from; the boss itself where there is no
    ; keep). A monster between them has the way in's level and its share of the
    ; climb -- the steps walked from the way in to it over the steps to that
    ; chamber's middle -- rounded once. Past that chamber, its far side, the
    ; road, the court and the guard in it all stand at its level; the boss
    ; BossLevelsAbove over it; and what rises from a rift at its caller's.
    ;
    ; The first tier runs from WayInLevel to BeforeBossLevel. Each tier past it
    ; starts where the one before closed and ends TierGrowthPercent higher, worked
    ; out in one step: 1-8, 8-13, 13-20, 20-33, the boss two above each. Nothing
    ; stands above MaxMonsterLevel: the deep stage, at Difficulty 8, would play
    ; 134 to 215, and plays 50 throughout.
    WayInLevel = 1
    BeforeBossLevel = 8
    TierGrowthPercent = 60
    BossLevelsAbove = 2
    MaxMonsterLevel = 50
  End
End
```

- [ ] **Step 6: Run the test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MonsterLevelTest"`
Expected: PASS, 6 tests.

- [ ] **Step 7: Run the whole suite** — nothing reads the rule yet.

Run: `./gradlew test`
Expected: PASS, 849 tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/resources/data/world/generation.duke src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java
git commit -m "A monster's level by the tier of the place it is met in: the rule and its lines" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 2: The walk that finds what is walled off counts its steps, and every monster's level is read off it

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/stage/StageCheck.java` (the grid in `problems`, ~line 52; `unreachable`,
  ~line 148; `reachable` and `at`, ~lines 172–200)
- Modify: `src/main/java/uz/dukeengine/dungeon/run/Spawner.java` (an import; `levelsOf` after `place`, before
  `ROOM_SEARCH_STEPS`, ~line 110)
- Test: `src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java`

**Interfaces:**
- Consumes: `PathGrid.canStep`, `isBlocked`, `inBounds`; `MapLoader.fromText`, `levels`; `GeneratedDungeon.hero()`,
  `keep()`, `rooms()`, `boss()`, `monsters()`; `Keep.chamber()`, `Keep.isCourt(int, int)`; Task 1's `levelAlong`.
- Produces: `public record StageCheck.Walk(int width, int[] steps)` with `int to(int x, int y)` (−1 where no step
  reaches, and off the map); `public static StageCheck.Walk StageCheck.walk(GeneratedDungeon)`; package-private
  `static int[] Spawner.levelsOf(GeneratedDungeon, DungeonSettings, int tier)`, one level a monster in the floor's order.

**Piece 3:** `StageCheck` is not piece 3's. In `Spawner` piece 3 changes `Placed` and `place`'s last line (`return new
Placed(...)`); `levelsOf` goes after `place`'s closing brace and the blank line under it, and `place` itself is not
touched here.

- [ ] **Step 1: Write the failing tests** — `MonsterLevelTest`, the imports become:

```java
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.stage.StageCheck;
import uz.dukeengine.dungeon.stage.Stages;
```

and at the end of the class:

```java
    // ---- along the way ----

    /** The first floor of the descent drawn from {@code seed}. */
    private static GeneratedDungeon firstFloor(long seed) {
        return DungeonGenerator.generate(seed, SETTINGS, 1);
    }

    /** How many steps from the way in a monster stands. */
    private static int stepsTo(StageCheck.Walk walk, GeneratedDungeon.Monster monster) {
        return walk.to(monster.at().cellX(), monster.at().cellY());
    }

    /** Whether a monster stands inside a chamber's footprint. */
    private static boolean inside(GeneratedDungeon.Room room, GeneratedDungeon.Monster monster) {
        int x = monster.at().cellX();
        int y = monster.at().cellY();
        return x >= room.x() && y >= room.y() && x < room.x() + room.w() && y < room.y() + room.h();
    }

    /** Further along is never lower: of two monsters, the one more steps from the way in stands at least as high. */
    @Test
    void aMonsterFurtherAlongIsNeverBelowOneNearer() {
        for (long seed = 1; seed <= 6; seed++) {
            var floor = firstFloor(seed);
            var walk = StageCheck.walk(floor);
            var levels = Spawner.levelsOf(floor, SETTINGS, 1);
            var monsters = floor.monsters();
            for (int a = 0; a < levels.length; a++) {
                assertTrue(stepsTo(walk, monsters.get(a)) >= 0, "the premise: every monster can be walked to");
                for (int b = 0; b < levels.length; b++) {
                    if (stepsTo(walk, monsters.get(a)) > stepsTo(walk, monsters.get(b))) {
                        assertTrue(levels[a] >= levels[b], "seed " + seed + ": " + stepsTo(walk, monsters.get(a))
                                + " steps in at " + levels[a] + ", " + stepsTo(walk, monsters.get(b)) + " at "
                                + levels[b]);
                    }
                }
            }
        }
    }

    /**
     * The climb ends in the chamber the keep's road leaves from: every monster in it stands at 7 or 8, and everything
     * past it -- the guard in the court -- at 8.
     */
    @Test
    void theChamberBeforeTheKeepIsNearlyAtTheTopAndItsCourtAtIt() {
        int inChamber = 0;
        int inCourt = 0;
        for (long seed = 1; seed <= 6; seed++) {
            var floor = firstFloor(seed);
            var keep = floor.keep();
            assertNotNull(keep, "the premise: seed " + seed + "'s first floor has its keep");
            var chamber = floor.rooms().get(keep.chamber());
            var levels = Spawner.levelsOf(floor, SETTINGS, 1);
            for (int i = 0; i < levels.length; i++) {
                var monster = floor.monsters().get(i);
                if (keep.isCourt(monster.at().cellX(), monster.at().cellY())) {
                    inCourt++;
                    assertEquals(8, levels[i], "seed " + seed + ": a " + monster.kind() + " in the court");
                } else if (inside(chamber, monster)) {
                    inChamber++;
                    assertTrue(levels[i] == 7 || levels[i] == 8,
                            "seed " + seed + ": a " + monster.kind() + " before the keep at " + levels[i]);
                }
            }
        }
        assertTrue(inChamber > 0, "the premise: some chamber before a keep held a monster");
        assertTrue(inCourt > 0, "the premise: some court held its guard");
    }

    /** One seed gives every monster the same level twice: the walk and the rule know no dice and no clock. */
    @Test
    void oneSeedGivesEveryMonsterTheSameLevelTwice() {
        assertArrayEquals(Spawner.levelsOf(firstFloor(7L), SETTINGS, 1), Spawner.levelsOf(firstFloor(7L), SETTINGS, 1));
    }

    /**
     * A stage has no keep: its way ends at its boss, and it is played at its Difficulty -- the first stage climbing
     * toward its boss from the way in, and the deep one at the cap throughout.
     */
    @Test
    void aStagesWayEndsAtItsBoss() {
        var stage = Stages.load("first", SETTINGS);
        var floor = stage.floor();
        var walk = StageCheck.walk(floor);
        int toTheBoss = walk.to(floor.boss().at().cellX(), floor.boss().at().cellY());
        var levels = Spawner.levelsOf(floor, SETTINGS, stage.difficulty());

        assertTrue(toTheBoss > 0, "the premise: the boss can be walked to");
        for (int i = 0; i < levels.length; i++) {
            int steps = stepsTo(walk, floor.monsters().get(i));
            assertEquals(SETTINGS.levelAlong(stage.difficulty(), steps, toTheBoss), levels[i],
                    "a " + floor.monsters().get(i).kind() + " " + steps + " steps in, of " + toTheBoss);
        }
        var deep = Stages.load("deep", SETTINGS);
        for (int level : Spawner.levelsOf(deep.floor(), SETTINGS, deep.difficulty())) {
            assertEquals(SETTINGS.maxMonsterLevel(), level, "the deep stage plays at the cap throughout");
        }
    }
```

(What these stand on, as probed when the plan was verified: the way to a keep's chamber ran 84 to 169 steps, the
monsters in that chamber and the guards in its court all stood at 8, and the rest spread from 2 to 8; the first stage's
boss is 69 steps in and its monsters climb 2 → 7 toward it; the deep stage's 195 are all at 50. The tests hold the
chamber to 7 or 8, not 8, so a monster standing just inside its door is not a failure.)

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MonsterLevelTest"`
Expected: FAIL — compilation error: `cannot find symbol: class Walk` (location: class `StageCheck`), `method
walk(GeneratedDungeon)`, `method levelsOf(GeneratedDungeon,DungeonSettings,int)`.

- [ ] **Step 3: The walk counts** — `StageCheck.java`. An import after `DungeonSettings`'s:

```java
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement;
```

in `problems`, the grid's three lines become one:

```java
        PathGrid grid;
        try {
            grid = groundOf(floor);
        } catch (RuntimeException e) {
```

in `unreachable`:

```java
        var reached = steps(grid, entrance.cellX(), entrance.cellY());
```

and `reachable` and `at` give way to:

```java
    /**
     * A floor walked from its way in, counting the steps: how far along the way each cell is. The walk
     * {@link #problems} makes to find what is walled off, and the one a monster's level is read off -- see
     * {@code Spawner.levelsOf}.
     *
     * @param width how many cells a row of {@code steps} is: the map's width
     * @param steps how many steps each cell is from the way in, row by row; {@code -1} where no step reaches
     */
    public record Walk(int width, int[] steps) {

        /** How many steps the cell is from the way in: {@code -1} where no step reaches it, and off the map. */
        public int to(int x, int y) {
            int at = y * width + x;
            return x < 0 || y < 0 || x >= width || at >= steps.length ? -1 : steps[at];
        }
    }

    /**
     * {@code floor} walked from its way in over its own ground -- its stone, its storeys and its relief, and nothing
     * that stands on it, so a shut gate hides nothing behind it. Nothing is reached on a floor with no way in.
     */
    public static Walk walk(GeneratedDungeon floor) {
        var grid = groundOf(floor);
        var in = floor.hero();
        return new Walk(grid.getWidth(), in == null ? steps(grid, -1, -1) : steps(grid, in.cellX(), in.cellY()));
    }

    /** The floor's own ground as the engine walks it: its stone, its storeys and its relief. */
    private static PathGrid groundOf(GeneratedDungeon floor) {
        var grid = MapLoader.fromText(floor.asciiMap());
        MapLoader.levels(grid, floor.levelMap());
        grid.setRelief(floor.relief());
        return grid;
    }

    /**
     * How many steps from the way in the engine would let the hero walk to each cell, by its own rule for a step and
     * four ways from a cell in a fixed order; {@code -1} where no step reaches. Whole numbers, no dice.
     */
    private static int[] steps(PathGrid grid, int fromX, int fromY) {
        var steps = new int[grid.getWidth() * grid.getHeight()];
        java.util.Arrays.fill(steps, -1);
        if (grid.isBlocked(fromX, fromY)) {
            return steps;
        }
        var queue = new ArrayDeque<int[]>();
        steps[fromY * grid.getWidth() + fromX] = 0;
        queue.add(new int[] {fromX, fromY});
        int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            var here = queue.poll();
            int next = steps[here[1] * grid.getWidth() + here[0]] + 1;
            for (var way : ways) {
                int x = here[0] + way[0];
                int y = here[1] + way[1];
                if (!grid.inBounds(x, y) || steps[y * grid.getWidth() + x] >= 0
                        || !grid.canStep(here[0], here[1], x, y)) {
                    continue;
                }
                steps[y * grid.getWidth() + x] = next;
                queue.add(new int[] {x, y});
            }
        }
        return steps;
    }

    private static boolean at(PathGrid grid, int[] reached, int cx, int cy) {
        return grid.inBounds(cx, cy) && reached[cy * grid.getWidth() + cx] >= 0;
    }
```

(`isBlocked` is true off the map, so a way in off it, or none — `-1, -1` — reaches nothing.)

- [ ] **Step 4: Each monster's level off the walk** — `Spawner.java`, the import after `LootTable`'s:

```java
import uz.dukeengine.dungeon.loot.LootTable;
import uz.dukeengine.dungeon.stage.StageCheck;
```

and after `place`'s closing brace, before `/** How many steps from the way in a fountain may stand, ... */`:

```java
    /**
     * The level each of a floor's monsters stands at, in the order the floor lists them, in a place of {@code tier}:
     * its share of the way from the way in to the middle of the chamber before the boss's -- the one the keep's road
     * leaves from, or the boss's own place where there is no keep -- by the steps the floor is walked in (see
     * {@link StageCheck#walk}). Everything past that chamber, the keep's court and its guard included, stands at its
     * level.
     */
    static int[] levelsOf(GeneratedDungeon dungeon, DungeonSettings settings, int tier) {
        var walk = StageCheck.walk(dungeon);
        int way = -1;
        if (dungeon.keep() != null) {
            var chamber = dungeon.rooms().get(dungeon.keep().chamber());
            way = walk.to(chamber.centerCellX(), chamber.centerCellY());
        } else if (dungeon.boss() != null && dungeon.boss().at() != null) {
            way = walk.to(dungeon.boss().at().cellX(), dungeon.boss().at().cellY());
        }
        var levels = new int[dungeon.monsters().size()];
        for (int i = 0; i < levels.length; i++) {
            var at = dungeon.monsters().get(i).at();
            levels[i] = settings.levelAlong(tier, walk.to(at.cellX(), at.cellY()), way);
        }
        return levels;
    }
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MonsterLevelTest" --tests "uz.dukeengine.dungeon.stage.*"`
Expected: PASS — `MonsterLevelTest` 10 tests, and the stage tests as before (the walk still finds what is walled off).

- [ ] **Step 6: Run the whole suite**

Run: `./gradlew test`
Expected: PASS, 853 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/stage/StageCheck.java src/main/java/uz/dukeengine/dungeon/run/Spawner.java src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java
git commit -m "The walk that finds what is walled off counts its steps, and every monster's level is read off it" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 3: What a level gives — health, blow, skills, mending and worth grown by a monster's own level

**Files:**
- Rename: `src/main/java/uz/dukeengine/dungeon/combat/DepthBonus.java` → `LevelBonus.java` (`git mv`, then rewritten)
- Modify: `src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java` (`Descent`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate()` after Task 1's lines; the
  per-depth accessors, ~lines 1217–1250)
- Modify: `src/main/java/uz/dukeengine/dungeon/run/Spawner.java` (class Javadoc; `place`'s Javadoc, its monsters loop
  ~line 73 and its boss ~line 100; `scale` ~line 306)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SummoningUpdate.java`, `.../skill/SkillBook.java` (`damageOf`,
  `depthOf`, `mend`), `.../skill/SkillEffect.java` (`SUMMON`'s note), `.../run/HeroStatus.java` (`creature`),
  `.../Dungeon.java` (~lines 162, 211), `.../loot/LootDrop.java` (one word)
- Modify: `src/main/resources/data/world/generation.duke`, `data/world/hud.duke` (`Segments`, ~line 241),
  `data/units/skeleton.duke`, `data/units/warden.duke`, `data/game.duke` (notes)
- Modify tests: `src/test/java/uz/dukeengine/dungeon/DungeonUnitBarTest.java`, `.../ai/MonsterSkillTest.java`,
  `.../ai/MonsterSummoningTest.java`, `.../stage/StagePlayTest.java`
- Test: `src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java`

**Interfaces:**
- Consumes: `GrowableBody.growMaxHealth(float)`; the engine's `DamageModifier` (the weapon multiplies by it);
  `ExperienceModule`; Task 1's `bossLevel`, `maxMonsterLevel`; Task 2's `levelsOf`.
- Produces: `public final class LevelBonus` — `LevelBonus(GameObject owner, int level, float multiplier, float
  healthMultiplier)`, `static int levelOf(GameObject)` (1 for a creature carrying none), `level()`,
  `damageMultiplier()`, `healthMultiplier()`; `public static void Spawner.scale(GameObject, int level,
  DungeonSettings)`; `DungeonSettings.healthAtLevel(int)`, `damageAtLevel(int)`, `experienceAtLevel(int)`;
  `SummoningUpdate(GameObject, DungeonSettings)`; `Descent` components `…, bossGuardRing, monsterCountPercentPerDepth,
  wayInLevel, beforeBossLevel, tierGrowthPercent, bossLevelsAbove, maxMonsterLevel, healthPercentPerLevel,
  damagePercentPerLevel, experiencePercentPerLevel`. Gone: `monsterHealthAt`, `monsterDamageAt`, `bossHealthAt`,
  `bossDamageAt`, `experienceAt`, `DepthBonus`. In `MonsterLevelTest`: `MAGE`, `HEALER`, `SUMMONER`, `room()`,
  `creature(DukeGame, String)`, `alone(String)`, `worthOf(GameObject)`, `shipped(String, String)`, `unarmedRogue()`,
  `unseeing(String)`.

**Piece 3:** in `Spawner.place` the monsters loop is above the comment over the props loop, which piece 3 changes, and
the boss's `scale` two lines under the boss's own spawn line, which it does not; `Placed` is not touched. In
`Dungeon.java` piece 3 rewrites the `GateUpdate` registration (~line 185); the edits here are at ~162 and ~211. In
`hud.duke` piece 3 adds a `Cursor` near line 703; `Segments` is at 241. `LootDrop.java` is in `loot/` but not among the
files piece 3 changes; the edit is one word of its class note.

- [ ] **Step 1: Write the failing tests** — `MonsterLevelTest`, the imports become:

```java
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.DataException;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.combat.LevelBonus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.Summoned;
import uz.dukeengine.dungeon.stage.StageCheck;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.module.ExperienceModule;
```

and at the end of the class:

```java
    // ---- what a level gives ----

    private static final String MAGE = "SkeletonMage";
    private static final String HEALER = "SkeletonHealer";
    private static final String SUMMONER = "SkeletonSummoner";

    /** An open room forty cells by thirty, stone only round its edge. */
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

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /** One {@code kind}, alone in a room with nobody to notice: as its block builds it, and no level put on it. */
    private static GameObject alone(String kind) {
        var arena = Dungeon.world(room(), SETTINGS);
        arena.game().spawn(kind, arena.dungeon(), 150f, 150f);
        arena.game().runHeadless(1);
        return creature(arena.game(), kind);
    }

    private static int worthOf(GameObject creature) {
        return creature.findModule(ExperienceModule.class).getExperienceValue();
    }

    /** A number the shipped block of {@code unit} writes once. */
    private static float shipped(String unit, String key) {
        return Float.parseFloat(ShippedBlock.of(unit).value(key));
    }

    /** The shipped units with the Rogue's bow reaching nothing: whatever he loses, the monster took. */
    private static String unarmedRogue() {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text());
    }

    /** The shipped files with {@code kind} noticing nobody: it does only what a test does for it. */
    private static DungeonSettings unseeing(String kind) {
        return DungeonSettings.parse(ShippedBlock.of(kind).with("SenseRadius", 1).with("ChaseRadius", 1)
                .with("AlertRadius", 0).text());
    }

    /** A level-1 monster is its block exactly: its own health, its own blow, its own worth. */
    @Test
    void aLevelOneMonsterIsItsBlockExactly() {
        var mage = alone(MAGE);
        float health = mage.getBody().getMaxHealth();
        int worth = worthOf(mage);

        Spawner.scale(mage, 1, SETTINGS);

        assertEquals(health, mage.getBody().getMaxHealth(), 0.001f, "its health");
        assertEquals(worth, worthOf(mage), "its worth");
        var bonus = mage.findModule(LevelBonus.class);
        assertEquals(1, bonus.level(), "and it carries its level, the first");
        assertEquals(1f, bonus.damageMultiplier(), 0.001f, "its blow");
    }

    /** At level 8 it has 1.7 times its health, all of it standing, hits 1.35 times as hard, and is worth 1.35 times. */
    @Test
    void atLevelEightItIsTougherHitsHarderAndIsWorthMore() {
        var mage = alone(MAGE);
        float health = mage.getBody().getMaxHealth();
        int worth = worthOf(mage);

        Spawner.scale(mage, 8, SETTINGS);

        assertEquals(health * 1.7f, mage.getBody().getMaxHealth(), 0.01f, "its health");
        assertEquals(mage.getBody().getMaxHealth(), mage.getBody().getHealth(), 0.01f, "all of it standing");
        assertEquals(Math.round(worth * 1.35f), worthOf(mage), "its worth");
        assertEquals(8, mage.findModule(LevelBonus.class).level());
        assertEquals(1.35f, mage.findModule(LevelBonus.class).damageMultiplier(), 0.001f, "its blow");
    }

    /** Its blow, landed for real: a level-8 skeleton's takes 1.35 times its block's off a Rogue who does not answer. */
    @Test
    void itsBlowLandsItsLevelsShareHarder() {
        var arena = Dungeon.world(room(), SETTINGS, unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 162f, 150f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = creature(game, "Rogue");
        Spawner.scale(creature(game, "Skeleton"), 8, SETTINGS);
        float before = hero.getBody().getHealth();

        for (int frame = 0; frame < 90 && hero.getBody().getHealth() == before; frame++) {
            game.runHeadless(1);
        }

        assertEquals(shipped("Skeleton", "Damage") * 1.35f, before - hero.getBody().getHealth(), 0.01f);
    }

    /** Its skills' damage the same share: the fire mage's fireball, thrown by hand at level 1 and at level 8. */
    @Test
    void itsSkillsHitItsLevelsShareHarder() {
        float plain = aFireballFrom(1);

        assertTrue(plain > 0f, "the premise: the fireball reached him");
        assertEquals(plain * 1.35f, aFireballFrom(8), 0.01f);
    }

    /** What the fire mage's fireball, thrown by hand from {@code level}, takes off a Rogue it never noticed. */
    private static float aFireballFrom(int level) {
        var arena = Dungeon.world(room(), unseeing(MAGE), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(MAGE, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var mage = creature(game, MAGE);
        var hero = creature(game, "Rogue");
        Spawner.scale(mage, level, SETTINGS);
        float before = hero.getBody().getHealth();

        assertTrue(mage.findModule(SkillBook.class).cast('Q', 1, null, hero.getPosition()), "the premise: it threw");
        game.runHeadless(30);
        return before - hero.getBody().getHealth();
    }

    /** And a mending's: the healer's light, called down by hand at level 1 and at level 8. */
    @Test
    void itsMendingMendsItsLevelsShareMore() {
        float plain = aMendingFrom(1);

        assertTrue(plain > 0f, "the premise: the light landed");
        assertEquals(plain * 1.35f, aMendingFrom(8), 0.01f);
    }

    /** What the healer's light, called down by hand from {@code level}, gives back to a skeleton left at 10. */
    private static float aMendingFrom(int level) {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn(HEALER, arena.dungeon(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 180f, 150f);
        game.runHeadless(1);
        var healer = creature(game, HEALER);
        var patient = creature(game, "Skeleton");
        Spawner.scale(healer, level, SETTINGS);
        patient.getBody().setHealth(10f);

        assertTrue(healer.findModule(SkillBook.class).cast('Q', 1, patient.getId(), null), "the premise: it mended");
        game.runHeadless(40);
        return patient.getBody().getHealth() - 10f;
    }

    /**
     * What rises from a rift stands at its caller's level, read when the rift opens: its figures, and its share of
     * what its kind is worth at that level.
     */
    @Test
    void whatRisesStandsAtItsCallersLevel() {
        var settings = DungeonSettings.parse(ShippedBlock.of(SUMMONER).with("SummonExperiencePercent", 50).text());
        var arena = Dungeon.world(room(), settings);
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        var summoner = creature(game, SUMMONER);
        Spawner.scale(summoner, 8, SETTINGS);

        assertTrue(summoner.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(150f, 150f, 0f)),
                "the premise: it opened its rifts");
        game.runHeadless(30);

        var risen = game.getLogic().getObjects().stream()
                .filter(object -> object.findModule(Summoned.class) != null).toList();
        assertEquals(4, risen.size(), "the premise: two swordsmen and two archers rose");
        for (var one : risen) {
            var kind = one.getTemplate().name();
            assertEquals(8, one.findModule(LevelBonus.class).level(), kind + "'s level");
            assertEquals(1.35f, one.findModule(LevelBonus.class).damageMultiplier(), 0.001f, kind + "'s blow");
            assertEquals(shipped(kind, "MaxHealth") * 1.7f, one.getBody().getMaxHealth(), 0.01f, kind + "'s health");
            assertEquals(Math.round(shipped(kind, "ExperienceValue") * 1.35f) * 50 / 100, worthOf(one),
                    kind + "'s worth: half of what its kind is worth at 8");
        }
    }

    /** The card shows its own blow: its weapon's figure times its own level's share -- not the floor's. */
    @Test
    void theCardShowsItsOwnBlow() {
        var skeleton = alone("Skeleton");
        Spawner.scale(skeleton, 8, SETTINGS);

        var line = HeroStatus.creature(skeleton, 4, 4, SETTINGS, "", false);

        assertTrue(line.contains("|stat=" + SETTINGS.hud().attackWord() + ","
                + Math.round(shipped("Skeleton", "Damage") * 1.35f) + ","), line);
    }

    /** Placed for real: the first floor's boss stands at 10 with 1.9 times its health, and every monster at 1 to 8. */
    @Test
    void aPlacedFloorStandsAtItsLevels() {
        var session = Dungeon.newSession(11L);
        session.game().runHeadless(1);
        var boss = SETTINGS.bossKindAt(1);

        int monsters = 0;
        for (var object : session.game().getLogic().getObjects()) {
            var name = object.getTemplate().name();
            if (SETTINGS.monster(name) == null) {
                continue; // a hero, a prop, a shot
            }
            var bonus = object.findModule(LevelBonus.class);
            assertNotNull(bonus, name + " was placed without its level");
            if (name.equals(boss)) {
                assertEquals(10, bonus.level(), "the boss");
                assertEquals(shipped(boss, "MaxHealth") * 1.9f, object.getBody().getMaxHealth(), 0.01f);
            } else {
                monsters++;
                assertTrue(bonus.level() >= 1 && bonus.level() <= 8, name + " at " + bonus.level());
            }
        }
        assertTrue(monsters > 0, "the premise: the floor holds monsters");
    }

    /** What a level gives replaces what a depth gave: a file still writing a per-depth line of it is refused. */
    @Test
    void aPerDepthLineOfHealthDamageOrExperienceIsRefused() {
        for (var gone : new String[] {"MonsterHealthPercentPerDepth", "MonsterDamagePercentPerDepth",
                "BossHealthPercentPerDepth", "BossDamagePercentPerDepth", "ExperiencePercentPerDepth"}) {
            var data = dataWith("    MonsterCountPercentPerDepth = 20\n",
                    "    MonsterCountPercentPerDepth = 20\n    " + gone + " = 25\n");

            var refused = assertThrows(DataException.class, () -> DungeonSettings.parse(data));
            assertTrue(refused.getMessage().contains(gone), refused.getMessage());
        }
    }
```

(Monsters spawned by hand here are bare — no `LevelBonus`, no pool, no word — until the test itself makes them their
level with `Spawner.scale`, as the spawner does everything it places. The fireball and the mending are thrown by hand
at monsters that notice nobody, so nothing the brain does moves the figure read.)

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MonsterLevelTest"`
Expected: FAIL — compilation error: `cannot find symbol: class LevelBonus` (package `uz.dukeengine.dungeon.combat`),
and `method scale in class Spawner cannot be applied to given types`.

- [ ] **Step 3: `DepthBonus` becomes `LevelBonus`**

```bash
git mv src/main/java/uz/dukeengine/dungeon/combat/DepthBonus.java src/main/java/uz/dukeengine/dungeon/combat/LevelBonus.java
```

and its whole text:

```java
package uz.dukeengine.dungeon.combat;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rts.module.DamageModifier;
import uz.dukeengine.rts.module.RtsModuleGroups;

/**
 * A monster's level, and the two multipliers it gives: how much harder it hits, and what its health was grown by.
 *
 * <p>Attached where the monster is placed, or rises, rather than written into its template, because the same Runner
 * stands at every level and only where it was met is different: see {@code Spawner.scale}, the one step that makes a
 * creature its level. A template is what a thing is; this is where it was found. A creature that carries none -- a
 * test's bare skeleton -- is level 1, its block exactly.
 *
 * <p>Rides the engine's {@link DamageModifier} seam for its weapon's blow, which is exactly the case that seam was
 * opened for: a bonus belonging to one unit rather than to its whole side, from a module the engine has never heard
 * of. Read by its skills too, which deal their own damage rather than going through the weapon (see
 * {@code SkillBook}); by the rifts it opens, so what climbs out stands at its level (see {@code SummoningUpdate}); and
 * by the card that shows what it hits for.
 */
@ModuleGroup({ModuleGroups.COMBAT, RtsModuleGroups.PROGRESSION})
public final class LevelBonus extends Module implements DamageModifier {

    private final int level;
    private final float multiplier;
    private final float healthMultiplier;

    public LevelBonus(GameObject owner, int level, float multiplier, float healthMultiplier) {
        super(owner);
        this.level = level;
        this.multiplier = multiplier;
        this.healthMultiplier = healthMultiplier;
    }

    /** The level of {@code creature}: the one its bonus says, or 1 for a creature that carries none. */
    public static int levelOf(GameObject creature) {
        var bonus = creature == null ? null : creature.findModule(LevelBonus.class);
        return bonus == null ? 1 : bonus.level;
    }

    public int level() {
        return level;
    }

    @Override
    public float damageMultiplier() {
        return multiplier;
    }

    /** What its health was grown by for its level. */
    public float healthMultiplier() {
        return healthMultiplier;
    }
}
```

- [ ] **Step 4: The per-level lines in place of the per-depth ones** — `ProceduralMap.java`, the paragraph added in
  Task 1 gains a sentence:

```java
     * before closed; its boss {@code bossLevelsAbove} over that chamber's, and nothing above {@code maxMonsterLevel}.
     * See {@code DungeonSettings.levelAlong}. What a level past the first is worth, to monster and boss alike:
     * {@code healthPercentPerLevel} more health, {@code damagePercentPerLevel} more to its blow, its skills and a
     * mending, and {@code experiencePercentPerLevel} more for killing it. How many monsters a place holds is still its
     * depth's: {@code monsterCountPercentPerDepth}.
```

and the components and defaults become:

```java
    public record Descent(@Link(Monster.class) List<String> bosses,
            @Link(Monster.class) Map<String, Integer> bossGuards, int bossGuardRing,
            int monsterCountPercentPerDepth,
            int wayInLevel, int beforeBossLevel, int tierGrowthPercent, int bossLevelsAbove, int maxMonsterLevel,
            int healthPercentPerLevel, int damagePercentPerLevel, int experiencePercentPerLevel) {

        /** What a block leaves out. */
        public static final Descent DEFAULTS = new Descent(List.of(), Map.of(), 2, 20,
                1, 8, 60, 2, 50, 10, 5, 5);
```

`DungeonSettings.java` — `monsterHealthAt`, `monsterDamageAt`, `bossHealthAt`, `bossDamageAt` and `experienceAt` go;
from `monsterCountAt` to the end of `scaled` reads:

```java
    /** What a room's count of monsters is multiplied by at this depth: who lives in a place, and how many, is its own. */
    public float monsterCountAt(int depth) {
        return scaled(map.descent().monsterCountPercentPerDepth(), depth);
    }

    /**
     * What a monster's health is multiplied by at this level -- and its blow, its skills' damage and a mending's heal
     * by {@link #damageAtLevel}, and what killing it is worth by {@link #experienceAtLevel}: alike for every monster and
     * boss, and a level-1 monster is its block exactly.
     */
    public float healthAtLevel(int level) {
        return scaled(map.descent().healthPercentPerLevel(), level);
    }

    public float damageAtLevel(int level) {
        return scaled(map.descent().damagePercentPerLevel(), level);
    }

    public float experienceAtLevel(int level) {
        return scaled(map.descent().experiencePercentPerLevel(), level);
    }

    /**
     * Linear growth from the first depth or level: {@code 1 + (n - 1) * percent / 100}.
     *
     * <p>Computed from the number in one step rather than compounded, so the tenth
     * floor is the same whether you arrived by playing or by asking.
     */
    private static float scaled(int percentPerStep, int n) {
        return 1f + Math.max(0, n - 1) * percentPerStep / 100f;
    }
```

and in `validate()`, after Task 1's `MaxMonsterLevel` check:

```java
        require(descent.healthPercentPerLevel() >= 0 && descent.damagePercentPerLevel() >= 0
                        && descent.experiencePercentPerLevel() >= 0,
                "a level cannot take a monster's health, its blow or its worth away");
```

`generation.duke` — the per-depth lines and their comments go, `MonsterCountPercentPerDepth` stays with a note of its
own:

```
    BossGuards = [SkeletonHealer = 2, SkeletonSummoner = 2]
    BossGuardRing = 2

    ; How many more monsters a room holds per depth beyond the first: at depth 3,
    ; with 20%, 1 + 2 x 0.2 = 1.4 times as many. Who lives in a place, and how
    ; many, is the place's; how strong each one is, its level's -- below.
    MonsterCountPercentPerDepth = 20

    ; ---- the level of what lives here ----
```

and after `MaxMonsterLevel = 50`:

```
    MaxMonsterLevel = 50

    ; What a level past the first is worth, to monster and boss alike: a tenth
    ; more health; a twentieth more to its blow, its skills' damage and a
    ; mending's heal; and a twentieth more for killing it. A level-1 monster is
    ; its block exactly; at 8 it has 1.7 times its health and hits 1.35 times as
    ; hard, and the boss at 35, 4.4 and 2.7 times. Experience climbs slowly on
    ; purpose: a level-8 monster is worth 1.35 times its kind, so the hero levels
    ; a little faster than he used to while what he fights climbs faster still.
    HealthPercentPerLevel = 10
    DamagePercentPerLevel = 5
    ExperiencePercentPerLevel = 5
  End
End
```

- [ ] **Step 5: The one step** — `Spawner.java`. The import `DepthBonus` → `LevelBonus`:

```java
import uz.dukeengine.dungeon.combat.LevelBonus;
```

the class note:

```java
/**
 * Puts a generated floor into the world, and makes each of its inhabitants as
 * dangerous as its level says it should be.
 *
 * <p>Shared by the first floor and every one after it, because they are the same
 * act: the only difference between the dungeon a run opens on and the one that
 * follows a dead boss is the number. Two copies of this would drift, and the one
 * that drifted would be the deeper floors nobody tests as often.
 *
 * <p>A level is applied to the individual, not to the template. The same Runner
 * appears on every floor and at every level — a template says what a thing is,
 * and this says where it was found. All three effects use seams the engine already
 * offers rather than new engine features: a growable body for health, a damage
 * modifier module for damage, and a replaced experience module for what killing it
 * is worth.
 */
```

`place`'s note — its first sentence, and in its last paragraph `beside the depth bonus` becomes `beside its level`:

```java
     * Lay out a floor: each player's hero at the way in, each monster at its level along the way -- {@code depth} is
     * the place's tier -- the boss at its own in its keep — or the furthest room, where none fits — and, from
     * {@code drops}, what the inhabitants leave behind.
```

```java
     * block, beside its level and for the same reason: a template says what a thing is, and what it leaves
```

its monsters loop:

```java
        var monsters = new ArrayList<GameObject>();
        var levels = levelsOf(dungeon, settings, depth);
        for (int i = 0; i < levels.length; i++) {
            var monster = dungeon.monsters().get(i);
            var spawned = spawn(game, dungeonPlayer, monster.kind(), at(logic, monster.at()));
            if (spawned != null) {
                scale(spawned, levels[i], settings);
                dropsFrom(spawned, drops, settings, depth, false);
                monsters.add(spawned);
            }
        }
```

its boss:

```java
        if (boss != null) {
            scale(boss, settings.bossLevel(depth), settings);
            dropsFrom(boss, drops, settings, depth, true);
        }
```

and `scale`, from its Javadoc to the experience comment's first lines (the rest of it stays):

```java
    /**
     * Make one creature its level -- a monster placed on the floor, its boss, or whatever rises from a rift: its
     * health grown, a {@link LevelBonus} saying the level and what it gives, and its worth set. The one step for all
     * of them, so a creature that rises is made exactly as one placed there would be.
     *
     * <p>Each multiplier is computed from the level in one step rather than
     * compounded level by level, so a level is the same however it was reached.
     */
    public static void scale(GameObject monster, int level, DungeonSettings settings) {
        float health = settings.healthAtLevel(level);
        if (monster.getBody() instanceof GrowableBody body && health > 1f) {
            body.growMaxHealth(body.getMaxHealth() * (health - 1f));
        }
        monster.addModule(new LevelBonus(monster, level, settings.damageAtLevel(level), health));
        float experience = settings.experienceAtLevel(level);
        // What killing it is worth is fixed by its template, and the template is
        // the same at every level — so the module is swapped for one that says a
        // bigger number, in the place the old one held.
```

- [ ] **Step 6: What rises is made by the same step** — `SummoningUpdate.java`. The imports:

```java
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.combat.LevelBonus;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.rts.module.ExperienceModule;
```

(`DepthBonus` and `GrowableBody` go.) The class note's last paragraph:

```java
 * <p>What climbs out is marked as called up ({@link Summoned}): it falls down again when
 * its time is out, it stands at its caller's level, it is worth the share of experience
 * the skill says of what its kind is worth there, and it counts against its caller's
 * {@code MaxSummoned} for as long as it stands.
 */
```

the fields and constructor:

```java
    /** What a level is worth, for the one step that makes what rises its caller's level. */
    private final DungeonSettings settings;
    private ObjectId caller;
    private String creature;
    private int lasts;
    private int experiencePercent;
    private int level = 1;
    private int opensIn;
    private boolean opened;

    public SummoningUpdate(GameObject owner, DungeonSettings settings) {
        super(owner);
        this.settings = settings;
    }
```

`open`:

```java
    /**
     * Open it: in {@code frames} a {@code creature} climbs out, for {@code lasts} frames,
     * worth {@code experiencePercent} of its own kind.
     *
     * <p>Its caller's level is read now rather than when it rises, as every shot here is
     * settled when it is thrown: the caller may be dead by then.
     */
    public void open(GameObject from, String creature, int lasts, int experiencePercent,
            int frames) {
        this.caller = from.getId();
        this.creature = creature;
        this.lasts = lasts;
        this.experiencePercent = experiencePercent;
        this.level = LevelBonus.levelOf(from);
        this.opensIn = Math.max(1, frames);
        this.opened = true;
    }
```

and in `rise`, the copying gives way to the step:

```java
        var risen = world.spawn(template, rift.getPosition(), rift.getPlayerIndex());
        risen.addModule(new Summoned(risen, lasts));
        // Its caller's level, by the one step that makes anything placed on the floor its
        // level -- and then its share of what that level makes it worth.
        Spawner.scale(risen, level, settings);
        var worth = risen.findModule(ExperienceModule.class);
```

`Dungeon.java` builds it with the settings:

```java
                    // And a rift, which something of the dungeon's own climbs out of -- at its
                    // caller's level, which is what the settings say a level is worth.
                    factory.register(SummoningUpdate.Data.class,
                            (owner, data) -> new SummoningUpdate(owner, settings));
```

and the comment over the `GrowableBody` registration:

```java
                    // Hero and monsters alike need a body that can grow: levels
                    // raise his and theirs, and the engine's fixes its maximum
                    // when the unit is built.
```

- [ ] **Step 7: Skills, mendings and the card read the level** — `SkillBook.java`: the import
  `uz.dukeengine.dungeon.combat.DepthBonus` becomes `uz.dukeengine.dungeon.combat.LevelBonus` (after `FallingUpdate`'s),
  and:

```java
    /**
     * What a skill hits for: its own figure at this rank, the ultimate's window if
     * one is open, and how much harder its caster's level makes it. Only a monster
     * carries a level of its own, so a hero's figure is untouched by that last.
     */
    private float damageOf(Skill skill, int level) {
        return skill.damageAt(level) * damageMultiplier() * bonusOf(getOwner());
    }

    /**
     * Its level's bonus, read here as well as by the weapon. A skill deals its own
     * damage rather than going through a weapon, so without asking it would hit as
     * hard at the thirtieth level as at the first.
     */
    private static float bonusOf(GameObject owner) {
        var bonus = owner.findModule(LevelBonus.class);
        return bonus == null ? 1f : bonus.damageMultiplier();
    }
```

in `mend`, `skill.heal() * depthOf(owner)` becomes `skill.heal() * bonusOf(owner)`, and its note's
`grown by the depth as the healer's blows are.` becomes `grown by its level as the healer's blows are.`

`SkillEffect.java`, `SUMMON`'s note:

```java
     * {@code DurationFrames} and then falls down, stands at its caller's level, and is
     * worth {@code SummonExperiencePercent} of what its own kind is worth there.
```

`HeroStatus.java`, the import after `Doing`'s, the card's blow, and a word of its note:

```java
import uz.dukeengine.dungeon.ai.Doing;
import uz.dukeengine.dungeon.combat.LevelBonus;
```

```java
        var pictures = settings.hud().statIcons();
        // Its weapon's figure and what its own level makes of it -- a boss's as a skeleton's.
        var level = creature.findModule(LevelBonus.class);
        float damage = weaponDamage(creature.getTemplate()) * (level == null ? 1f : level.damageMultiplier());
```

```java
     * hero's three are worked out — and because what its level multiplies a monster
     * by is this game's arithmetic. See {@code Spawner.scale}.
```

`LootDrop.java`, its class note: `block, beside the depth bonus and for the same reason` becomes
`block, beside its level and for the same reason`.

- [ ] **Step 8: The Skeleton's body can grow** — `skeleton.duke`. It is the one monster built on the engine's
  `ActiveBody`, whose maximum is fixed when it is built — so the depth never grew it either, and without this
  `whatRisesStandsAtItsCallersLevel` fails with `Skeleton's health ==> expected: <102.0> but was: <60.0>`:

```
  Modules = [
    ; Grown by its level where it is placed, as every monster's body is -- see
    ; the level lines in the Descent block of generation.duke.
    GrowableBody
      MaxHealth = 60
    End,
```

- [ ] **Step 9: The bar's marks over the new range** — the Champion at the cap (the deep stage's boss, 3540 health)
  wears 70 marks about 2.2 pixels apart at fifty a mark; measured over every monster at every level 1–50 and both ends of
  each hero, the least lot that keeps every bar at `CLOSEST` (4.5) is 105, and 120 gives the widest 29 marks 5.19 apart —
  the spacing the file was first tuned to. `hud.duke`:

```
  ; ★ A HUNDRED AND TWENTY, MEASURED AGAINST WHAT THE GAME ACTUALLY MAKES. The
  ; most health anything has is a boss at the level cap -- the Champion at 50 on
  ; the deep stage, 3540 -- and it wears twenty-nine marks on the longest bar,
  ; about five of the design's pixels apart: seven and a half on a big screen,
  ; where the bar is drawn half as big again. A hero wears three to eight at his
  ; first level and up to twelve at his fifteenth, a skeleton one. A hundred a
  ; mark would put that boss's under four and a half apart, where marks read as
  ; texture -- and fifty, before monsters had levels, under two and a half.
  ; DungeonUnitBarTest loads this file and holds all of that.
  Segments = [*:120]
```

`DungeonUnitBarTest.java` measures that range — in `everySizeTheGameMakes`, `int floors = ...` and the two depth loops
give way to:

```java
        var sizes = new ArrayList<float[]>();

        // Every monster at every level one can stand at, up to the cap -- the bosses among them, on the one rule:
        // the fourth floor's boss at 35, and the deep stage's at 50, the widest bar the game makes.
        for (var kind : SETTINGS.monsters()) {
            float base = baseHealth(factory.findTemplate(kind.name()));
            assertTrue(base > 0f, kind.name() + " has no body in the shipped files");
            for (int level = 1; level <= SETTINGS.maxMonsterLevel(); level++) {
                sizes.add(new float[] {base * SETTINGS.healthAtLevel(level), level});
            }
        }
```

the hero's entry carries his level, `...Found.NOTHING).maxHealth(), level});` in place of `...maxHealth(), 1});`; the
two messages say `" health at level "` for `" health at depth "`; and the notes follow:

```java
     * What a template's body is worth before any level has been applied to it.
```

```java
     * his levels, a monster's with the level it was placed at — and the engine
```

```java
     * <p>Asked of every creature the game makes — every monster and boss at every level one can stand at, and both
     * ends of what a hero grows into — and of the whole range past them, so a rung added
     * to the table later fails here before anybody has looked at a bar.
```

- [ ] **Step 10: The tests that spoke of depth** — `MonsterSkillTest.java`: the import `DepthBonus` → `LevelBonus`, and:

```java
    /** With a level's bonus it hits harder: the bonus reaches its skill as well as its weapon. */
    @Test
    void aCasterWithALevelsBonusHitsHarder() {
        float plain = aBlowFrom(1f);
        float twice = aBlowFrom(2f);

        assertTrue(plain > 0f, "the fireball never reached him");
        assertEquals(plain * 2f, twice, 0.05f, "twice the bonus should be twice the blow");
    }

    /**
     * What one fireball takes off the hero, from a caster carrying this bonus -- at the first
     * level, so nothing a higher one would open is open. The one it throws the moment it sees
     * him leaves before the bonus is put on, so it is the next one, a cooldown later, that is
     * measured.
     */
    private static float aBlowFrom(float bonus) {
        var fight = fight(standingStill(), room(NO_WALL), 240f, 200f);
        if (bonus != 1f) {
            fight.mage().addModule(new LevelBonus(fight.mage(), 1, bonus, 1f));
        }
```

`MonsterSummoningTest.java`: the import `DepthBonus` → `LevelBonus`, `import uz.dukeengine.dungeon.run.Spawner;` after
`ShippedBlock`'s, and `whatItCallsUpWasFoundAsDeepAsItsCaller` becomes:

```java
    /** Called up at its caller's level, read as the rift opens, so it is what one placed there would be. */
    @Test
    void whatItCallsUpStandsAtItsCallersLevel() {
        var circle = circle(summoningWith("[Skeleton = 2]", 4, 3000, 0, 60));
        var before = new HashSet<Integer>();
        risings(circle.game(), oneSummoning()).forEach(one -> before.add(one.id()));
        assertEquals(2, before.size(), "the first casting, before it stood any higher");
        Spawner.scale(circle.summoner(), 8, SETTINGS);

        var after = risings(circle.game(), 60 + oneSummoning()).stream()
                .filter(one -> !before.contains(one.id())).toList();

        assertEquals(2, after.size(), "a second casting, once it did: " + after);
        var world = circle.game().getLogic();
        for (var one : after) {
            var bonus = world.findObject(new uz.dukeengine.core.thing.ObjectId(one.id()))
                    .findModule(LevelBonus.class);
            assertEquals(8, bonus.level(), "called up at another level than its caller's");
            assertEquals(SETTINGS.damageAtLevel(8), bonus.damageMultiplier(), 0.001f);
            assertEquals(SETTINGS.healthAtLevel(8), bonus.healthMultiplier(), 0.001f);
        }
        for (int id : before) {
            assertEquals(1, LevelBonus.levelOf(world.findObject(new uz.dukeengine.core.thing.ObjectId(id))),
                    "the first two were called up before it stood any higher");
        }
    }
```

- [ ] **Step 11: A stage is held against a floor drawn as a stage is** — a stage keeps no word of a keep (its file has
  none: `StageFile` builds its floor with `keep` null), and a keep is where a floor's way ends; so a stage cut from a
  floor with a keep would end its way at its boss where the floor ended it at the keep's chamber, and
  `aStagePlaysExactlyLikeTheDungeonItWasCutFrom` fails on the checksum (the same creatures in the same places, at other
  levels). A stage is always cut without one (`MapWriter`); `StagePlayTest.java` draws the floor it compares that way —
  the import `uz.dukeengine.dungeon.content.Content` after `Dungeon`'s, and:

```java
    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /**
     * The shipped files with no keep. A stage is cut without one (see {@code MapWriter}) and keeps no word of one,
     * and a keep is where a floor's way ends -- so what each monster on it stands at: the floor a stage is held
     * against is drawn without one too.
     */
    private static final DungeonSettings KEEPLESS =
            DungeonSettings.parse(Content.data().replace("    Sizes = [15, 13, 11, 9]\n", ""));

    private static Stage frozen(long seed) {
        return frozen(seed, SETTINGS);
    }

    private static Stage frozen(long seed, DungeonSettings settings) {
        var stage = new Stage("test", "Test", "", 1, 1, seed,
                DungeonGenerator.generate(seed, settings, 1));
```

```java
    @Test
    void aStagePlaysExactlyLikeTheDungeonItWasCutFrom() {
        long seed = 4242L;
        assertTrue(DungeonGenerator.generate(seed, KEEPLESS, 1).keep() == null, "the premise: no keep was drawn");
        var generated = Dungeon.newSession(seed, KEEPLESS).game();
        var staged = Dungeon.newStageSession(frozen(seed, KEEPLESS), KEEPLESS).game();
```

- [ ] **Step 12: The notes that said depth** — `warden.duke`:

```
; They climb, and their level climbs under them -- two above the chamber before
; each one's keep, see the level lines in the Descent block of generation.duke --
; so what is written here is the shape of the fight rather than its difficulty:
; the Warden is met at level 10, with 1.9 times the health written here, and the
; Champion at 35, with 4.4 times.
```

`game.duke`:

```
; data/world/world.duke. Monsters carry GrowableBody, because their level raises their
; health after they are built; props carry no Body at all, which is why nothing targets them.
```

- [ ] **Step 13: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MonsterLevelTest" --tests "uz.dukeengine.dungeon.DungeonUnitBarTest" --tests "uz.dukeengine.dungeon.ai.MonsterSkillTest" --tests "uz.dukeengine.dungeon.ai.MonsterSummoningTest" --tests "uz.dukeengine.dungeon.stage.StagePlayTest"`
Expected: PASS — `MonsterLevelTest` 19 tests, `DungeonUnitBarTest` 4, the rest as before.

- [ ] **Step 14: Run the whole suite** — every placed monster now carries its level.

Run: `./gradlew test`
Expected: PASS, 862 tests.

- [ ] **Step 15: Commit** — Step 3's `git mv` has already staged `DepthBonus.java`'s going; naming it to `git add`
  again would stop the command with `pathspec ... did not match any files`.

```bash
git add src/main/java/uz/dukeengine/dungeon/combat/LevelBonus.java src/main/java/uz/dukeengine/dungeon/Dungeon.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/loot/LootDrop.java src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java src/main/java/uz/dukeengine/dungeon/run/HeroStatus.java src/main/java/uz/dukeengine/dungeon/run/Spawner.java src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java src/main/java/uz/dukeengine/dungeon/skill/SummoningUpdate.java src/main/resources/data/game.duke src/main/resources/data/units/skeleton.duke src/main/resources/data/units/warden.duke src/main/resources/data/world/generation.duke src/main/resources/data/world/hud.duke src/test/java/uz/dukeengine/dungeon/DungeonUnitBarTest.java src/test/java/uz/dukeengine/dungeon/ai/MonsterSkillTest.java src/test/java/uz/dukeengine/dungeon/ai/MonsterSummoningTest.java src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java src/test/java/uz/dukeengine/dungeon/stage/StagePlayTest.java
git commit -m "What a level gives: a monster's health, blow, skills, mending and worth grown by its own level, and what rises at its caller's" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 4: A monster's medallion shows its own level, held in a word on it

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/world/UnitBar.java` (+ `levelWord`, the last component)
- Modify: `src/main/java/uz/dukeengine/dungeon/Main.java` (`unitBars`, ~line 298)
- Modify: `src/main/java/uz/dukeengine/dungeon/run/Spawner.java` (`scale`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate()`, after Task 3's line)
- Modify: `src/main/java/uz/dukeengine/dungeon/run/HeroStatus.java` (`world`'s note, ~line 213)
- Modify: `src/main/resources/data/world/hud.duke` (the medallion, ~lines 295–309)
- Modify tests: `src/test/java/uz/dukeengine/dungeon/DungeonUnitBarTest.java`, `.../run/HeroStatusTest.java` (a note)
- Test: `src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java`

**Interfaces:**
- Consumes: E7 — `UnitBarLook.withLevelWord(String)`, `UnitBarLook.levelWord()` (the client reads the number after the
  word in a creature's first word that begins with it; the hero's own and, with none, the depth, as before);
  `GameObject.setCondition(String)`, `getConditions()`.
- Produces: `String UnitBar.levelWord()` (blank in `DEFAULTS`); `Spawner.scale` sets `levelWord + level` on the creature;
  `Main.unitBars` hands the word on. In `MonsterLevelTest`: `levelWords(GameObject)`.

**Piece 3:** it adds a `Cursor` at the end of `hud.duke`'s cursors (~line 703); the medallion is ~line 300. In
`Main.java` it changes `themedCreature`/`dressed` (~line 362) and `looks` (~line 917); `unitBars` is ~line 298.

- [ ] **Step 1: Write the failing tests** — `MonsterLevelTest`, `assertFalse` among the static imports and
  `java.util.List` before `org.junit.jupiter.api.Test`:

```java
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
```

and at the end of the class:

```java
    // ---- on the bar ----

    /** The words a creature holds that begin as the bar's level word does. */
    private static List<String> levelWords(GameObject creature) {
        return creature.getConditions().stream()
                .filter(word -> word.startsWith(SETTINGS.unitBar().levelWord())).toList();
    }

    /** Every monster placed on a floor, and its boss, carries exactly one level word: its own. */
    @Test
    void everyPlacedMonsterWearsItsOwnLevel() {
        var session = Dungeon.newSession(11L);
        session.game().runHeadless(1);

        int worn = 0;
        for (var object : session.game().getLogic().getObjects()) {
            if (SETTINGS.monster(object.getTemplate().name()) != null) {
                worn++;
                assertEquals(List.of(SETTINGS.unitBar().levelWord() + LevelBonus.levelOf(object)), levelWords(object),
                        object.getTemplate().name());
            }
        }
        assertTrue(worn > 1, "the premise: the floor holds its monsters and its boss");
    }

    /** And whatever rises from a rift wears its caller's. */
    @Test
    void whatRisesWearsItsCallersLevel() {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        var summoner = creature(game, SUMMONER);
        Spawner.scale(summoner, 8, SETTINGS);

        assertTrue(summoner.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(150f, 150f, 0f)),
                "the premise: it opened its rifts");
        game.runHeadless(30);

        var risen = game.getLogic().getObjects().stream()
                .filter(object -> object.findModule(Summoned.class) != null).toList();
        assertFalse(risen.isEmpty(), "the premise: something rose");
        for (var one : risen) {
            assertEquals(List.of(SETTINGS.unitBar().levelWord() + 8), levelWords(one), one.getTemplate().name());
        }
    }
```

and `DungeonUnitBarTest`, before `theFileSaysEnoughToDrawWith`:

```java
    /**
     * A monster's medallion shows its own level: the file names the word it is held in -- {@code level:8} -- and the
     * look the client is handed reads it. The hero keeps his own, and a creature holding none shows the depth.
     */
    @Test
    void theMedallionReadsACreaturesOwnLevel() {
        assertEquals("level:", SETTINGS.unitBar().levelWord());
        assertEquals(SETTINGS.unitBar().levelWord(), look().levelWord());
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MonsterLevelTest" --tests "uz.dukeengine.dungeon.DungeonUnitBarTest"`
Expected: FAIL — compilation error, `cannot find symbol: method levelWord()` in `UnitBar`.

- [ ] **Step 3: The word** — `UnitBar.java`, documented and last:

```java
 * @param segments the segment table, coarsest last, or empty for a game that draws no bars. Empty
 *     is the meaningful default and the rest are not: without a table there is nothing to divide
 *     a bar into, so the client draws none at all rather than inventing lots of its own
 * @param levelWord what a creature's own level is held in, a word on it -- {@code level:} for
 *     {@code level:8}, set where it is placed (see {@code Spawner.scale}) -- and shown in its
 *     medallion. Blank, and every creature but the hero shows the floor's depth
 */
public record UnitBar(List<BarStep> segments, int shortestAt, int longestAt, float shortest, float longest,
        float height, float manaHeight, float gap, float lift, float ring, float ringEdge, float ringGap,
        float arc, int enemy, int friend, int mana, int trough, int tick, int ringFace, int ringRim,
        int bossRim, int lettering, float nameSize, float bossNameSize, float countSize, float levelSize,
        String levelWord) {

    /** What a block leaves out. */
    public static final UnitBar DEFAULTS = new UnitBar(List.of(), 30, 1400, 80f, 220f, 13f, 6f, 2f, 1.4f, 26f,
            2f, 4f, 3f, 0xA8322B, 0x8FC4AE, 0x3E6FA8, 0x16130F, 0x0A0806, 0x16130F, 0x8FC4AE, 0xE8A33D,
            0xD9CFBA, 11f, 15f, 10f, 12f, "");
```

`Main.unitBars`, its last lines:

```java
                settings.unitBar().countSize(), settings.unitBar().levelSize())
                // A monster's medallion shows its own level, held in a word on it; see Spawner.scale.
                .withLevelWord(settings.unitBar().levelWord());
    }
```

`DungeonSettings.validate()`, after Task 3's per-level check:

```java
        // A creature's words cross to the client joined by ',', inside a line split on '|'.
        require(sayable(unitBar.levelWord()), "the UnitBar's LevelWord may not contain ',' or '|'");
```

- [ ] **Step 4: The step sets it** — `Spawner.scale`, its note's first sentence:

```java
     * Make one creature its level -- a monster placed on the floor, its boss, or whatever rises from a rift: its
     * health grown, a {@link LevelBonus} saying the level and what it gives, its worth set, and the word its bar reads
     * the level from ({@code level:8}; the {@code UnitBar}'s {@code LevelWord}). The one step for all of them, so a
     * creature that rises is made exactly as one placed there would be.
```

and after the `LevelBonus` is added:

```java
        monster.addModule(new LevelBonus(monster, level, settings.damageAtLevel(level), health));
        if (!settings.unitBar().levelWord().isBlank()) {
            monster.setCondition(settings.unitBar().levelWord() + level);
        }
        float experience = settings.experienceAtLevel(level);
```

- [ ] **Step 5: The file says it** — `hud.duke`, the medallion's note, and the word after `Arc`:

```
  ; The level, in a disc, with the experience ring around it. The number is on
  ; EVERYBODY: the hero's is his own, and a monster's is the level it was placed
  ; at -- exactly what its health, its blow and its worth were grown by, see the
  ; level lines in the Descent block of generation.duke -- so the disc answers
  ; "how hard is this one" with the same number the simulation used to make it
  ; hard. The monster holds it in a word, LevelWord and the number -- level:8 --
  ; set where it is placed or rises. A creature holding none shows the floor's
  ; depth.
  ;
  ; The RING is only on the hero, because he is the only thing in the game that
  ; earns experience. An empty ring on a skeleton would be a promise that it
  ; could fill.
  Ring = 34
  RingEdge = 2
  RingGap = 5
  Arc = 3
  LevelWord = level:
```

`HeroStatus.world`'s note, its first bullet:

```java
     * <li>a monster's level is its own, and rides the creature rather than this
     *     line: a word on it, {@code level:8}, set where it is placed (see
     *     {@code Spawner.scale}) and carried with its other words. What is sent
     *     here is the DEPTH, one number for the whole floor, which the medallion
     *     shows on a creature holding no level of its own
```

and `HeroStatusTest.theFloorIsSentAsAFigureAsWellAsAsWords`' note:

```java
     * draws as lettering. The medallion over a creature holding no level of its
     * own needs it as a figure it can count with.
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MonsterLevelTest" --tests "uz.dukeengine.dungeon.DungeonUnitBarTest"`
Expected: PASS — `MonsterLevelTest` 21 tests, `DungeonUnitBarTest` 5.

- [ ] **Step 7: Run the whole suite**

Run: `./gradlew test`
Expected: PASS, 865 tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/Main.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/run/HeroStatus.java src/main/java/uz/dukeengine/dungeon/run/Spawner.java src/main/java/uz/dukeengine/dungeon/world/UnitBar.java src/main/resources/data/world/hud.duke src/test/java/uz/dukeengine/dungeon/DungeonUnitBarTest.java src/test/java/uz/dukeengine/dungeon/run/HeroStatusTest.java src/test/java/uz/dukeengine/dungeon/run/MonsterLevelTest.java
git commit -m "A monster's medallion shows its own level, held in a word on it" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 5: The three casting mages pay from pools of their own, grown by their level and full where they stand

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/content/Monster.java` (+ `maxMana`, `manaRegen`, the last two components)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/MonsterKind.java` (+ the same, last)
- Modify: `src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java` (`Descent` + `manaPercentPerLevel`, last)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`read`'s `case Monster`, ~line 323;
  `requirePool` after `requireBook`, ~line 405; `validate()`'s monsters loop ~line 596 and Task 3's per-level check;
  `manaAtLevel` after `experienceAtLevel`)
- Modify: `src/main/java/uz/dukeengine/dungeon/run/Spawner.java` (`scale`), `.../skill/SkillBook.java` (`usesMana`'s
  note, ~line 201), `.../run/HeroStatus.java` (`world`'s mana bullet)
- Modify: `src/main/resources/data/units/skeleton_mage.duke`, `skeleton_healer.duke`, `skeleton_summoner.duke`,
  `data/world/generation.duke`
- Test: `src/test/java/uz/dukeengine/dungeon/ai/MonsterManaTest.java` (**new**)

**Interfaces:**
- Consumes: `SkillBook.poolOf(int max, int tenthsPerSecond)` (a pool from nothing gains nothing), `fillMana()`,
  `resize(int, int)`, `canAfford(char, int)`, `cast`'s refusal of what it cannot pay for, `getMana()`, `getMaxMana()`,
  `getManaRegen()`, `skillOn(char)`; `Skill.manaAt(int)`.
- Produces: `int Monster.maxMana()`, `manaRegen()`; `int MonsterKind.maxMana()`, `manaRegen()` (the kind's level-1
  figures); `int Descent.manaPercentPerLevel()`; `int DungeonSettings.manaAtLevel(int base, int level)`; `Spawner.scale`
  sizes and fills the pool of a kind that names one.

**Piece 3:** in `DungeonSettings.read` it adds `case Theme.ThemeMonster look -> ...` after `case Theme theme`, twenty
lines under `case Monster`; the rest is not piece 3's.

- [ ] **Step 1: Write the failing test** — `src/test/java/uz/dukeengine/dungeon/ai/MonsterManaTest.java`:

```java
package uz.dukeengine.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.game.DukeGame;

/**
 * A monster's mana: the {@code SkillBook}'s own pool, as a hero's is -- grown by its level where it is placed, full
 * then, trickling back, and paid from at its skills' costs. A kind that names no pool casts free.
 *
 * <p>Asked of a caster alone in a room with nobody to notice, and cast by hand, so nothing its brain does moves a
 * figure the test is reading.
 */
class MonsterManaTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String MAGE = "SkeletonMage";

    /** Somewhere on the floor to throw at. */
    private static final Coord3D AWAY = new Coord3D(100f, 150f, 0f);

    /** An open room forty cells by thirty, stone only round its edge. */
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

    private record Alone(DukeGame game, GameObject mage) {

        SkillBook book() {
            return mage.findModule(SkillBook.class);
        }
    }

    /** The fire mage alone in a room, as {@code settings} build it, with nobody to notice. */
    private static Alone aloneUnder(DungeonSettings settings) {
        var arena = Dungeon.world(room(), settings);
        var game = arena.game();
        game.spawn(MAGE, arena.dungeon(), 150f, 150f);
        game.runHeadless(1);
        return new Alone(game, game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(MAGE)).findFirst().orElseThrow());
    }

    /** A whole number the fire mage's shipped block writes once. */
    private static int shipped(String key) {
        return Integer.parseInt(ShippedBlock.of(MAGE).value(key));
    }

    /** Placed at level 8: its pool and its trickle grown a tenth a level, in whole points and tenths -- and full. */
    @Test
    void itsPoolIsGrownByItsLevelAndFullWhereItIsPlaced() {
        var alone = aloneUnder(SETTINGS);
        Spawner.scale(alone.mage(), 8, SETTINGS);

        assertEquals(shipped("MaxMana") + shipped("MaxMana") * 7 * 10 / 100, alone.book().getMaxMana(), "its pool");
        assertEquals(alone.book().getMaxMana(), alone.book().getMana(), "full");
        assertEquals(shipped("ManaRegen") + shipped("ManaRegen") * 7 * 10 / 100, alone.book().getManaRegen(),
                "its trickle, in tenths of a point a second");
    }

    /** What a cast spends -- its skill's cost at its first rank -- comes back at the trickle, in whole points. */
    @Test
    void whatItSpendsTricklesBack() {
        var alone = aloneUnder(SETTINGS);
        Spawner.scale(alone.mage(), 8, SETTINGS);
        var book = alone.book();
        int full = book.getMana();

        assertTrue(book.cast('Q', 1, null, AWAY), "the premise: it threw");
        int spent = full - book.getMana();
        assertTrue(spent > 0, "the premise: its fireball costs something");
        assertEquals(book.skillOn('Q').manaAt(1), spent, "it paid the fireball's cost");
        alone.game().runHeadless(30);

        assertEquals(full - spent + book.getManaRegen() / 10, book.getMana(), "a second's trickle back");
    }

    /** A cast it cannot pay for is refused before anything happens: no mana taken, and its cooldown not started. */
    @Test
    void aCastItCannotPayForIsRefusedWithNothingSpent() {
        var alone = aloneUnder(SETTINGS);
        Spawner.scale(alone.mage(), 1, SETTINGS);
        var book = alone.book();
        book.resize(10, 0);

        assertFalse(book.cast('Q', 1, null, AWAY), "it threw what it could not pay for");
        assertEquals(10, book.getMana(), "and something was taken");
        assertTrue(book.isReady('Q'), "and its cooldown was started");
    }

    /** A kind that names no pool casts free, as every monster did: the fire mage re-tuned without one. */
    @Test
    void aKindThatNamesNoPoolCastsFree() {
        var poolless = DungeonSettings.parse("""
                Monster
                  Name = SkeletonMage
                  SkillDistance = [20, 60]
                End
                """);
        var alone = aloneUnder(poolless);
        Spawner.scale(alone.mage(), 8, poolless);

        assertEquals(0, alone.book().getMaxMana(), "it was given a pool");
        assertTrue(alone.book().cast('Q', 1, null, AWAY), "and without one it could not cast");
    }

    /** A monster whose own skills cost mana and that names no pool is refused when the file is read. */
    @Test
    void aCostWithNoPoolIsRefusedWhenTheFileIsRead() {
        var block = ShippedBlock.of(MAGE).text();
        var poolless = block.replace("  MaxMana = " + shipped("MaxMana") + "\n", "");
        assertNotEquals(block, poolless, "the premise: its pool was taken out");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(poolless));
        assertTrue(refused.getMessage().contains("MaxMana"), refused.getMessage());
    }

    /**
     * As shipped: the three casting mages' pools, their trickles and what their skills cost -- the fire mage's 60 and
     * 30 for a fireball of 20, the healer's 50 and 25 for a mending of 25, the summoner's 80 and 25 for a calling of
     * 40 -- and everything else down here casts free.
     */
    @Test
    void asShippedTheThreeCastingMagesPayFromPoolsAndNothingElseDoes() {
        var pools = Map.of("SkeletonMage", List.of(60, 30, 20), "SkeletonHealer", List.of(50, 25, 25),
                "SkeletonSummoner", List.of(80, 25, 40));
        for (var kind : SETTINGS.monsters()) {
            var skills = SETTINGS.skillsFor(kind.name());
            var pool = pools.get(kind.name());
            if (pool == null) {
                assertEquals(0, kind.maxMana(), kind.name() + " names a pool");
                assertTrue(skills.stream().allMatch(skill -> skill.manaAt(1) == 0), kind.name() + "'s skills cost mana");
                continue;
            }
            var q = skills.stream().filter(skill -> skill.key() == 'Q').findFirst().orElseThrow();
            assertEquals(pool, List.of(kind.maxMana(), kind.manaRegen(), q.manaAt(1)), kind.name());
        }
    }
}
```

(The trickle: at level 8 the fire mage's 30 tenths a second become 51, so the 30 frames of one second give back five
whole points, the odd tenth carried by the book.)

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.ai.MonsterManaTest"`
Expected: FAIL — compilation error, `cannot find symbol: method maxMana()` and `method manaRegen()` in `MonsterKind`.

- [ ] **Step 3: A monster names its pool** — `Monster.java`, documented after `@param skills`, last in the record,
  and in `DEFAULTS` and `kind()`:

```java
 * @param skills        what it casts, each a {@code Skill} block in its {@code Skills = [ … ]}
 * @param maxMana       the pool its skills are paid from, in whole points, as a hero's {@code MaxMana} -- grown by
 *                      its level where it is placed, and full then. None, and it casts free
 * @param manaRegen     how fast the pool comes back, in tenths of a point a second, as a hero's {@code ManaRegen}
 */
```

```java
        Held held, @Group("Skills") PortraitArt portrait, List<Skill> skills, int maxMana, int manaRegen)
        implements Solid, Sighted, Classified, Titled, Drawn {
```

```java
            null, List.of(), 0, 0);
```

```java
        return new MonsterKind(name, senseRadius, chaseRadius, closeDistance, alertRadius, repathFrames,
                swingFrames, minDepth, weight, colour, scale, look(), skillKey(), skillDistance.nearest(),
                skillDistance.furthest(), keepDistance.nearest(), keepDistance.furthest(), maxPerRoom,
                maxMana, manaRegen);
```

`MonsterKind.java` — documented after `@param maxPerRoom`, last in the record, and passed on by `casting`:

```java
 * @param maxPerRoom   how many of it one room may hold, or zero for no limit
 * @param maxMana      the pool its skills are paid from at its first level, in whole points; zero casts free
 * @param manaRegen    how fast that comes back at its first level, in tenths of a point a second
 */
```

```java
        int maxPerRoom,
        int maxMana,
        int manaRegen) {
```

```java
                swingFrames, minDepth, weight, colour, scale, look, key, skillNearest, skillFurthest,
                keepNearest, keepFurthest, maxPerRoom, maxMana, manaRegen);
```

- [ ] **Step 4: What a level adds to it** — `ProceduralMap.java`, the paragraph's last sentences:

```java
     * mending, {@code experiencePercentPerLevel} more for killing it, and {@code manaPercentPerLevel} more to the pool
     * a caster pays from and to its trickle. How many monsters a place holds is still its depth's:
     * {@code monsterCountPercentPerDepth}.
```

```java
            int healthPercentPerLevel, int damagePercentPerLevel, int experiencePercentPerLevel,
            int manaPercentPerLevel) {

        /** What a block leaves out. */
        public static final Descent DEFAULTS = new Descent(List.of(), Map.of(), 2, 20,
                1, 8, 60, 2, 50, 10, 5, 5, 10);
```

`DungeonSettings.java`, after `experienceAtLevel`:

```java
    /**
     * A monster's pool at this level, or its trickle in tenths of a point a second: {@code base} and
     * {@code ManaPercentPerLevel} of it a level past the first, in whole numbers, as everything mana is.
     */
    public int manaAtLevel(int base, int level) {
        return base + base * Math.max(0, level - 1) * map.descent().manaPercentPerLevel() / 100;
    }
```

Task 3's per-level check takes the mana in:

```java
        require(descent.healthPercentPerLevel() >= 0 && descent.damagePercentPerLevel() >= 0
                        && descent.experiencePercentPerLevel() >= 0 && descent.manaPercentPerLevel() >= 0,
                "a level cannot take a monster's health, its blow, its worth or its mana away");
```

`generation.duke`, after `ExperiencePercentPerLevel = 5`:

```
    ExperiencePercentPerLevel = 5
    ; And a tenth more to a caster's pool and to how fast it comes back, in whole
    ; points and tenths -- see MaxMana and ManaRegen on the three mages.
    ManaPercentPerLevel = 10
```

- [ ] **Step 5: A cost with no pool is refused** — `DungeonSettings.read`, in `case Monster`:

```java
                case Monster monster -> {
                    monsters.add(monster.kind());
                    own(monster.name(), monster.portrait(), monster.skills());
                    requireBook("Monster " + monster.name(), monster.modules(), monster.skills());
                    requirePool(monster);
                }
```

after `requireBook`:

```java
    /**
     * A monster whose skills cost mana names the pool it pays from: without one it casts free, and the cost would be a
     * number nothing read. Asked of the skills its own block writes, where the record is read, as a book is: a block
     * that re-tunes a monster and writes no skills says nothing of what they cost.
     */
    private static void requirePool(Monster monster) {
        require(monster.maxMana() > 0 || monster.skills().stream().allMatch(skill -> skill.manaAt(1) == 0),
                "Monster " + monster.name() + " has a skill that costs mana and names no pool to pay it from:"
                        + " give it a MaxMana");
    }
```

and in `validate()`'s monsters loop, after the `MaxPerRoom` check:

```java
            require(kind.maxMana() >= 0 && kind.manaRegen() >= 0, name + "'s MaxMana and ManaRegen cannot be negative");
```

- [ ] **Step 6: The step sizes and fills it** — `Spawner.java`, the import after `LootTable`'s:

```java
import uz.dukeengine.dungeon.loot.LootTable;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.stage.StageCheck;
```

`scale`'s note's first sentence:

```java
     * Make one creature its level -- a monster placed on the floor, its boss, or whatever rises from a rift: its
     * health grown, a {@link LevelBonus} saying the level and what it gives, its worth set, its pool -- if its kind
     * names one -- sized and filled, and the word its bar reads the level from ({@code level:8}; the {@code UnitBar}'s
     * {@code LevelWord}). The one step for all of them, so a creature that rises is made exactly as one placed there
     * would be.
```

and after the word is set:

```java
        if (!settings.unitBar().levelWord().isBlank()) {
            monster.setCondition(settings.unitBar().levelWord() + level);
        }
        // What it casts out of, grown by its level and full: a monster is met rested. A kind that names no pool is
        // given none, and casts free.
        var book = monster.findModule(SkillBook.class);
        var kind = settings.monster(monster.getTemplate().name());
        if (book != null && kind != null && kind.maxMana() > 0) {
            book.poolOf(settings.manaAtLevel(kind.maxMana(), level), settings.manaAtLevel(kind.manaRegen(), level));
            book.fillMana();
        }
        float experience = settings.experienceAtLevel(level);
```

`SkillBook.usesMana`'s note:

```java
    /**
     * Whether this creature pays for what it casts.
     *
     * <p>Off unless it is given a pool: a hero by what he is made of, and a monster
     * whose kind names one where it is placed -- the three casting mages, see
     * {@code Spawner.scale}. Every other monster casts free.
     */
    private boolean usesMana;
```

`HeroStatus.world`'s last bullet:

```java
     * <li>and the bar's mana is the hero's alone — the casting mages pay from pools
     *     of their own, and nobody is shown them — so it is one entry
```

- [ ] **Step 7: The three pools and their costs** — `skeleton_mage.duke`, the note over its skills and the pool after it:

```
  ; Headed by the creature's name exactly as a hero's are, and cast by its brain
  ; rather than a key -- see Skill, SkillDistance and KeepDistance on its
  ; Monster block. It has one rank: what makes it hit harder further down is its
  ; level, as for its weapon. ProjectileSpeed is how fast what it throws travels;
  ; unsaid, it would be the hero's HeavySpeed. It says 120, which is what
  ; HeavySpeed is today, and stays 120 if that moves.
  ;
  ; And it pays for it, as a hero does, out of a pool in the same words and
  ; units: MaxMana in points, ManaRegen in tenths of a point a second -- each a
  ; tenth more a level past the first (ManaPercentPerLevel in generation.duke),
  ; and full when it is placed. Enough to throw at its cooldown through a fight
  ; of a minute or more, and longer the higher it stands. Nobody sees the pool:
  ; the bar's mana is the hero's alone.
  ; ---------------------------------------------------------------------------
  MaxMana = 60
  ManaRegen = 30

  Skills = [
```

and in its `Q`, after `CooldownFrames = 180`:

```
      CooldownFrames = 180
      ManaCost = 20
      MaxRank = 1
```

`skeleton_healer.duke`, before `Skills = [`, and in its `Q` after `CooldownFrames = 240`:

```
  ; What it mends out of, in the hero's words and units, as the Skeleton Mage's:
  ; enough to mend at its cooldown through a fight of a minute or more.
  MaxMana = 50
  ManaRegen = 25

  Skills = [
```

```
      CooldownFrames = 240
      ManaCost = 25
      MaxRank = 1
```

`skeleton_summoner.duke`, before `Skills = [`, and in its `Q` after `CooldownFrames = 360`:

```
  ; What it opens its rifts out of, in the hero's words and units, as the Skeleton
  ; Mage's: enough to call up its four at its cooldown through a fight of a minute
  ; or more.
  MaxMana = 80
  ManaRegen = 25

  Skills = [
```

```
      CooldownFrames = 360
      ManaCost = 40
      MaxRank = 1
```

- [ ] **Step 8: Run the test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.ai.MonsterManaTest"`
Expected: PASS, 6 tests.

- [ ] **Step 9: Run the whole suite** — the mages spawned by hand in the other tests are bare and cast free, as before;
  those on real floors now pay.

Run: `./gradlew test`
Expected: PASS, 871 tests.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/content/Monster.java src/main/java/uz/dukeengine/dungeon/content/MonsterKind.java src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java src/main/java/uz/dukeengine/dungeon/run/HeroStatus.java src/main/java/uz/dukeengine/dungeon/run/Spawner.java src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/resources/data/units/skeleton_healer.duke src/main/resources/data/units/skeleton_mage.duke src/main/resources/data/units/skeleton_summoner.duke src/main/resources/data/world/generation.duke src/test/java/uz/dukeengine/dungeon/ai/MonsterManaTest.java
git commit -m "The three casting mages pay from pools of their own, grown by their level and full where they stand" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 6: A monster casts the first of its skills, in file order, that its level has opened and it can pay for

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/ai/MonsterBrain.java` (class note; `ITS_ONLY_RANK`, ~line 59;
  `castAt` and `mend`, ~lines 204–251)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/Monster.java` (`skillKey()` gone; `kind()`; `@param skillDistance`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/MonsterKind.java` (`skillKey`, `hasSkill()`, `casting` gone)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`parse`, ~line 305; `validate()`'s
  monsters loop, ~line 583)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java` (`isPassive`'s note)
- Modify tests: `src/test/java/uz/dukeengine/dungeon/DungeonMonsterArtTest.java` (~line 316),
  `.../LifestealTest.java` (~line 236)
- Test: `src/test/java/uz/dukeengine/dungeon/ai/MonsterSkillTest.java`

**Interfaces:**
- Consumes: `Skill.levelForRank(int)` (1 for a skill that waits for nothing), `Skill.effect().aim()`,
  `SkillEffect.isPassive()`; `SkillBook.getSkills()`, `isReady`, `canAfford`, `cast` (false for rank 0, a passive, one
  it cannot pay for, and one its effect declines); Task 3's `LevelBonus.levelOf`; `SkillBook.resize` (in the tests).
- Produces: the brain's choice; `MonsterKind` without `skillKey`, `hasSkill()`, `casting(char)`; `Monster` without
  `skillKey()`; the settings' checks asked of each skill. In `MonsterSkillTest`: `SPARK`,
  `withSkillsAfterItsFireball(String...)`, `spark(char key, int cost, int opensAt)`, `atLevel(DungeonSettings, int)`.

**Piece 3:** none of these files is piece 3's but `DungeonSettings`; its edits there (the keep's size check, `looks`,
the key's checks) are below and above the monsters loop without touching it.

- [ ] **Step 1: Write the failing tests** — `MonsterSkillTest`. Three static imports and three imports more:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

```java
import uz.dukeengine.dungeon.combat.LevelBonus;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.game.DukeGame;
```

the band test casts from no further than its fireball reaches (a `SkillDistance` of `[20, 120]` over a `Range` of 70 is
exactly what Step 5 refuses — without this, `aWiderBandInTheFileIsAWiderBandOnTheFloor` fails with `Monster
SkeletonMage's Skill Q reaches 70.0, short of the 120.0 its SkillDistance casts from`):

```java
    /** Where it settles holding {@code band}, casting at him from anywhere its fireball reaches. */
    private static float settledGap(String band) {
        var fight = fight(keeping(band, "20, 70"), room(NO_WALL), 215f, 200f);
```

and at the end of the class, after `checksums()`:

```java
    // ---- which of its skills ----

    /** What the fire mage's added skill throws in these fights: a shadow spark, which nothing else in the room does. */
    private static final String SPARK = "ShadowSpark";

    /**
     * The shipped files with the fire mage given more skills, written after its fireball -- where a block that re-tunes
     * a unit puts the skills it adds, behind the ones its shipped block writes.
     */
    private static DungeonSettings withSkillsAfterItsFireball(String... skills) {
        var mage = ShippedBlock.of(MAGE);
        var text = mage.text().replace("    End\n  ]\nEnd\n", "    End,\n" + String.join(",\n", skills) + "\n  ]\nEnd\n");
        assertNotEquals(mage.text(), text, "the premise: the fire mage was given more skills");
        return DungeonSettings.parse(text);
    }

    /** A skill on {@code key} throwing a shadow spark, costing {@code cost} and waiting for level {@code opensAt}. */
    private static String spark(char key, int cost, int opensAt) {
        return """
                    Skill
                      Key = %s
                      Effect = SKILLSHOT
                      Damage = 5
                      Range = 70
                      ProjectileSpeed = 120
                      Projectile = %s
                      CooldownFrames = 300
                      ManaCost = %d
                      LevelPerRank = %d
                      MaxRank = 1
                    End""".formatted(key, SPARK, cost, opensAt);
    }

    /**
     * The fire mage made {@code level} where it stands -- by the spawner's own step, before anybody is there to cast
     * at -- and then a Rogue holding his ground inside its band.
     */
    private static Fight atLevel(DungeonSettings settings, int level) {
        var arena = Dungeon.world(room(NO_WALL), settings);
        var game = arena.game();
        game.spawn(MAGE, arena.dungeon(), 200f, ROW);
        game.runHeadless(1);
        var mage = creature(game, MAGE);
        Spawner.scale(mage, level, settings);
        arena.orders().hold(arena.hero().getIndex(), true);
        game.spawn("Rogue", arena.hero(), 240f, ROW);
        return new Fight(game, creature(game, "Rogue"), mage);
    }

    /**
     * Of its skills it casts the first, in the order its block writes them, that is open, ready and affordable -- and
     * then nothing at all until the gesture is over: the next skill, and its ordinary fire, a swing's frames later.
     */
    @Test
    void itCastsTheFirstOpenReadySkillInFileOrderAndNothingUntilTheGestureEnds() {
        var settings = withSkillsAfterItsFireball(spark('W', 0, 0));
        int swing = settings.monster(MAGE).swingFrames();

        var thrown = leaving(atLevel(settings, 1).game(), 90, SPARK, fireball(), ORDINARY_FIRE);

        assertFalse(thrown.get(fireball()).isEmpty(), "the skill written first was never cast: " + thrown);
        int first = thrown.get(fireball()).getFirst();
        assertFalse(thrown.get(SPARK).isEmpty(), "and once its gesture was over, the next never went: " + thrown);
        assertTrue(thrown.get(SPARK).getFirst() > first, "the one written second went first: " + thrown);
        for (var shot : new String[] {SPARK, ORDINARY_FIRE}) {
            assertTrue(thrown.get(shot).stream().allMatch(frame -> frame < first || frame >= first + swing),
                    shot + " left inside the gesture of the cast on frame " + first + ": " + thrown);
        }
    }

    /** A skill waits for the level its first rank does, as a hero's does: below it never cast, from it cast. */
    @Test
    void aSkillOpensAtTheLevelItsFirstRankWaitsFor() {
        var settings = withSkillsAfterItsFireball(spark('W', 0, 6));

        var below = leaving(atLevel(settings, 5).game(), 400, SPARK, fireball());
        assertTrue(below.get(SPARK).isEmpty(), "cast below the level it waits for: " + below);
        assertFalse(below.get(fireball()).isEmpty(), "the premise: it cast its fireball: " + below);

        var from = leaving(atLevel(settings, 6).game(), 90, SPARK);
        assertFalse(from.get(SPARK).isEmpty(), "never cast at the level it waits for: " + from);
    }

    /**
     * A skill it cannot pay for is refused before anything is spent, and the next one is cast: with less in its pool
     * than its fireball costs, it throws the spark written after it.
     */
    @Test
    void aSkillItCannotPayForPassesToTheNext() {
        var fight = atLevel(withSkillsAfterItsFireball(spark('W', 5, 0)), 1);
        var book = fight.mage().findModule(SkillBook.class);
        book.resize(book.skillOn('Q').manaAt(1) - 1, 0);

        var thrown = leaving(fight.game(), 60, SPARK, fireball());

        assertTrue(thrown.get(fireball()).isEmpty(), "it threw what it could not pay for: " + thrown);
        assertTrue(book.isReady('Q'), "and what it could not pay for spent its cooldown");
        assertFalse(thrown.get(SPARK).isEmpty(), "and nothing else was cast in its place: " + thrown);
    }

    /** A passive is never cast, wherever it is written: the brain passes over it to the next. */
    @Test
    void aPassiveIsNeverCastAndTheNextIs() {
        var fight = atLevel(withSkillsAfterItsFireball(
                "    Skill\n      Key = W\n      Effect = LIFESTEAL\n      BoostPercent = 25\n    End",
                spark('E', 0, 0)), 1);

        var thrown = leaving(fight.game(), 60, fireball(), SPARK);

        assertFalse(thrown.get(fireball()).isEmpty() || thrown.get(SPARK).isEmpty(),
                "the passive between the fireball and the spark stopped its casting: " + thrown);
        assertEquals(0, fight.mage().findModule(SkillBook.class).cooldownOf('W'), "and the passive was cast");
    }

    /**
     * A cast its book declines passes to its next skill, that frame: a W whose shot does not fly -- a mending light
     * -- is refused every time with its cooldown unspent, and the spark written after it is thrown in its place.
     */
    @Test
    void aCastItsBookDeclinesPassesToTheNextSkill() {
        var fight = atLevel(withSkillsAfterItsFireball(
                spark('W', 0, 0).replace("Projectile = " + SPARK, "Projectile = MendingLight"), spark('E', 0, 0)), 1);

        var thrown = leaving(fight.game(), 60, SPARK);

        assertTrue(fight.mage().findModule(SkillBook.class).isReady('W'), "the premise: its book never let the W go");
        assertFalse(thrown.get(SPARK).isEmpty(), "and nothing went in its place: " + thrown);
    }

    /** A skill aimed at him has to reach the far end of its band: a Range short of it is refused when the file is read. */
    @Test
    void anAimedSkillThatFallsShortOfItsBandIsRefused() {
        var data = ShippedBlock.of(MAGE).with("SkillDistance", "[20, 80]").text();

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("Range"), refused.getMessage());
    }
```

(The added skills go after the fireball because that is where a re-tuning block's own skills land: the settings keep
the shipped skills in their shipped order and put the ones a file adds after them — `fillInMissingSkills`. The shipped
file itself keeps its own order, which is what Task 7's meteor, written first, stands on. `atLevel` scales the mage
before the Rogue arrives, so its first cast is made at its level; `MendingLight` is a projectile with no
`ArrowUpdate`, so `Shot.looseAlong` spawns and drops it and the book declines the cast, cooldown unspent.)

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.ai.MonsterSkillTest"`
Expected: FAIL, 6 of 19, because the brain still casts only its one key:
`itCastsTheFirstOpenReadySkillInFileOrderAndNothingUntilTheGestureEnds` — `and once its gesture was over, the next
never went: {SkullFireball=[2], Fireball=[30, 84], ShadowSpark=[]}`;
`aSkillOpensAtTheLevelItsFirstRankWaitsFor` — `never cast at the level it waits for: {ShadowSpark=[]}`;
`aSkillItCannotPayForPassesToTheNext` — `and nothing else was cast in its place: {SkullFireball=[], ShadowSpark=[]}`;
`aPassiveIsNeverCastAndTheNextIs` — `the passive between the fireball and the spark stopped its casting:
{SkullFireball=[2], ShadowSpark=[]}`;
`aCastItsBookDeclinesPassesToTheNextSkill` — `and nothing went in its place: {ShadowSpark=[]}`;
`anAimedSkillThatFallsShortOfItsBandIsRefused` — `Expected java.lang.IllegalArgumentException to be thrown, but nothing
was thrown.`

- [ ] **Step 3: The brain chooses** — `MonsterBrain.java`. The import after `World`'s:

```java
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.combat.LevelBonus;
import uz.dukeengine.dungeon.combat.Swing;
```

the class note's paragraph on skills:

```java
 * <p>A kind the file gives skills decides for itself which to cast and when: the
 * first, in the order its block writes them, that its level has opened, that is
 * ready, that it can pay for and whose own condition holds. And a kind given a band
 * of distance holds that band instead of closing: it backs away from him when he
 * comes too near and follows when he gets too far. Both are numbers on its block,
 * so a caster is still this one mind. A mending is the one skill not cast at him:
 * it goes to whichever of its own needs it most -- see {@link Mending}.
```

`ITS_ONLY_RANK` and its note go, and `castAt` and `mend` become:

```java
    /**
     * One of its skills, when everything a player checks before casting holds: the
     * first, in the order its block writes them, that its level has opened, that is
     * ready, that it can pay for and that is cast at all -- and whose own condition
     * holds: for a mending, one of its own hurt enough in reach and sight; for anything
     * else, him inside the distance it casts across with nothing but air between them.
     * A cast the book declines -- no room for a rift -- passes to the next skill.
     *
     * <p>Thrown at where he stands now, so a player who keeps moving can walk out of
     * its way -- which is the whole of what makes it fair. Not while its ordinary shot
     * is still leaving it, nor while its last cast is, and nothing more leaves its
     * weapon until the cast is done: one throw at a time, so each is seen.
     *
     * <p>A monster holds rank 1 of every skill its level has opened -- the level its
     * first rank waits for, as a hero's does (see {@link Skill#levelForRank}) -- and
     * rank 0, which the book refuses, of the rest. It never ranks past 1: what makes it
     * hit harder is its level's bonus, as for its weapon.
     */
    private void castAt(GameObject hero) {
        var book = unit().findModule(SkillBook.class);
        if (book == null || midBlow() || midCast()) {
            return;
        }
        int level = LevelBonus.levelOf(unit());
        for (var skill : book.getSkills()) {
            int rank = level >= skill.levelForRank(1) ? 1 : 0;
            if (rank == 0 || skill.effect().isPassive() || !book.isReady(skill.key())
                    || !book.canAfford(skill.key(), rank)) {
                continue;
            }
            boolean cast = skill.effect() == SkillEffect.HEAL ? mend(book, skill, rank)
                    : throwAt(book, skill, rank, hero);
            if (cast) {
                holdTheWeapon();
                return;
            }
        }
    }

    /** At him, if he stands inside the distance it casts across and in plain sight: whether it went. */
    private boolean throwAt(SkillBook book, Skill skill, int rank, GameObject hero) {
        float gap = World.reachBetween(unit(), hero);
        return gap >= kind.skillNearest() && gap <= kind.skillFurthest() && SightLine.clear(unit(), hero)
                && book.cast(skill.key(), rank, null, hero.getPosition());
    }

    /**
     * Mend whichever of its own is worst hurt, if anyone is hurt enough: whether it did.
     *
     * <p>Looked for on one frame in every few, staggered by its id as a route is: the
     * search is everything round it, and nobody bleeds out in the wait.
     */
    private boolean mend(SkillBook book, Skill skill, int rank) {
        int every = kind.repathFrames();
        if (frame() % every != Math.floorMod(unit().getId().value(), every)) {
            return false;
        }
        var patient = Mending.worstHurt(world(), unit(), skill.range(), skill.healBelowPercent());
        return patient != null && book.cast(skill.key(), rank, patient.getId(), null);
    }
```

- [ ] **Step 4: The one key goes** — `Monster.java`: `skillKey()` and its note go; `kind()` passes none:

```java
        return new MonsterKind(name, senseRadius, chaseRadius, closeDistance, alertRadius, repathFrames,
                swingFrames, minDepth, weight, colour, scale, look(), skillDistance.nearest(),
                skillDistance.furthest(), keepDistance.nearest(), keepDistance.furthest(), maxPerRoom,
                maxMana, manaRegen);
```

and its band's note:

```java
 * @param skillDistance the nearest and the furthest it casts any skill at him from, surface to surface: one band,
 *                      whichever skill, and every skill aimed at him reaches its far end
```

`MonsterKind.java`: the `char skillKey` component, `hasSkill()` and `casting(char)` go, and the notes:

```java
 * @param skillNearest the nearest it casts any skill at him from, surface to surface
 * @param skillFurthest and the furthest. Which skill it casts is its brain's to choose,
 *                     among its {@code SkillBook}'s in the order written -- see
 *                     {@code MonsterBrain}
```

`SkillEffect.isPassive`'s note:

```java
     * <p>{@link SkillBook#cast} refuses one with its cooldown untouched, and a monster's
     * brain passes over one wherever it is written (see {@code MonsterBrain}), so a
     * creature whose only skill is a passive casts nothing. What a passive does is
```

- [ ] **Step 5: The settings ask of each skill** — `DungeonSettings.parse`: the re-keying goes (a re-tuning block that
  writes no skills keeps the shipped ones, which the brain reads from the book):

```java
            settings.fillInMissingAttributes();
            settings.fillInMissingAnimationSets();
        }
        settings.validate();
```

and in `validate()`'s monsters loop, the `if (kind.hasSkill()) { ... }` block becomes:

```java
            for (var skill : skillsFor(kind.name())) {
                // A passive is never cast, and a mending is cast on its own side, so how far off HE
                // is means nothing to either. Everything else is cast at him, from one band.
                if (skill.effect().isPassive() || skill.effect() == SkillEffect.HEAL) {
                    continue;
                }
                require(kind.skillNearest() >= 0f && kind.skillFurthest() > kind.skillNearest(),
                        name + " has to cast its Skill " + skill.key()
                                + " across some distance: SkillDistance nearest furthest");
                // And what is aimed at him has to reach the band's far end, or it falls short from there.
                // What goes off round the caster itself -- a summoning's rifts -- is aimed at nothing.
                require(skill.effect().aim() == SkillEffect.Aim.SELF || skill.range() >= kind.skillFurthest(),
                        name + "'s Skill " + skill.key() + " reaches " + skill.range() + ", short of the "
                                + kind.skillFurthest() + " its SkillDistance casts from: give it a Range of at"
                                + " least that");
            }
```

- [ ] **Step 6: The two tests that asked the kind for its key** — `DungeonMonsterArtTest`:

```java
    /** Whether a skill of its own that is cast at all throws something across the room. */
    private static boolean castsSomethingThatFlies(uz.dukeengine.dungeon.content.MonsterKind kind) {
        return SETTINGS.skillsFor(kind.name()).stream()
                .anyMatch(skill -> !skill.effect().isPassive() && skill.hasProjectile());
    }
```

`LifestealTest.itIsNeverCast`, its loop over the bosses:

```java
        for (var boss : bosses()) {
            assertTrue(SETTINGS.skillsFor(boss).stream().allMatch(skill -> skill.effect().isPassive()),
                    boss + "'s brain has a skill to cast");
        }
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.ai.*" --tests "uz.dukeengine.dungeon.LifestealTest" --tests "uz.dukeengine.dungeon.DungeonMonsterArtTest"`
Expected: PASS — `MonsterSkillTest` 19 tests, the healer's, the summoner's and the rest as before.

- [ ] **Step 8: Run the whole suite**

Run: `./gradlew test`
Expected: PASS, 877 tests.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/ai/MonsterBrain.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/content/Monster.java src/main/java/uz/dukeengine/dungeon/content/MonsterKind.java src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java src/test/java/uz/dukeengine/dungeon/DungeonMonsterArtTest.java src/test/java/uz/dukeengine/dungeon/LifestealTest.java src/test/java/uz/dukeengine/dungeon/ai/MonsterSkillTest.java
git commit -m "A monster casts the first of its skills, in file order, that its level has opened and it can pay for" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

### Task 7: The fire mage calls a violet meteor down on him from its sixth level, its fireball after it

**Files:**
- Create: `src/main/resources/data/projectiles/skull_meteor_mark.duke`
- Modify: `src/main/resources/data/game.duke` (the projectiles list, after `meteor_mark.duke`, ~line 80)
- Modify: `src/main/resources/data/sounds/sfx.duke` (after `spawned.SummoningRift`, ~line 109)
- Modify: `src/main/resources/data/units/skeleton_mage.duke` (its first note; the note over its skills; the meteor
  written first in `Skills`)
- Modify tests: `src/test/java/uz/dukeengine/dungeon/ai/MonsterSkillTest.java`, `.../StunTest.java`,
  `.../LifestealTest.java` (the fireball found by its key, not by being first)
- Test: `src/test/java/uz/dukeengine/dungeon/SkullMeteorTest.java` (**new**)

**Interfaces:**
- Consumes: `SkillEffect.METEOR` → `SkillBook.callDown` → `FallingUpdate`; `Main.measureLooks` (an effect's seconds and
  reach from the skill that throws it); `Visuals.getEffectReach`, `getEffectSeconds`; Task 6's brain; Task 5's pool;
  Task 3's `Spawner.scale`.
- Produces: `Projectile SkullMeteorMark`, `Effect SkullMeteor`, `Sound spawned.SkullMeteorMark`, the fire mage's `R`.
  In `MonsterSkillTest`: `theFireball()`; in `StunTest`: `fireballOf(DungeonSettings)`.

**Piece 3:** it adds `data/props/key.duke` to `game.duke`'s props list, thirty lines below the projectiles list.

- [ ] **Step 1: Write the failing test** — `src/test/java/uz/dukeengine/dungeon/SkullMeteorTest.java`:

```java
package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.game.DukeGame;

/**
 * The fire mage's ultimate: the Mage's own meteor from the other side. Open from its sixth level, cast at where he
 * stands whenever it is open, ready and paid for, its mark a thing of its own -- and its fireball after it while it
 * recharges.
 *
 * <p>Fought in an open room against a Rogue holding his ground inside its band, the mage made its level by the
 * spawner's own step before he is there to cast at.
 */
class SkullMeteorTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String MAGE = "SkeletonMage";
    private static final String MARK = "SkullMeteorMark";

    private static Skill meteor() {
        return SETTINGS.skillsFor(MAGE).getFirst();
    }

    private static Skill fireball() {
        return SETTINGS.skillsFor(MAGE).stream().filter(skill -> skill.key() == 'Q').findFirst().orElseThrow();
    }

    /** An open room forty cells by thirty, stone only round its edge. */
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

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private record Fight(DukeGame game, GameObject hero, GameObject mage) {
    }

    /** The fire mage made {@code level} where it stands, and then a Rogue holding his ground inside its band. */
    private static Fight atLevel(int level) {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn(MAGE, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        var mage = creature(game, MAGE);
        Spawner.scale(mage, level, SETTINGS);
        arena.orders().hold(arena.hero().getIndex(), true);
        game.spawn("Rogue", arena.hero(), 240f, 150f);
        return new Fight(game, creature(game, "Rogue"), mage);
    }

    /** The frame each new one of {@code template} appeared on, over the next frames. */
    private static List<Integer> appearing(DukeGame game, int frames, String template) {
        var seen = new HashSet<Integer>();
        var when = new ArrayList<Integer>();
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var object : game.getLogic().getObjects()) {
                if (object.getTemplate().name().equals(template) && seen.add(object.getId().value())) {
                    when.add(game.getLogic().getFrame());
                }
            }
        }
        return when;
    }

    // ---- as shipped ----

    /** Written first in the fire mage's block, with its figures: an ultimate of one rank, open from level 6. */
    @Test
    void theFireMagesMeteorIsWrittenFirst() {
        var meteor = meteor();

        assertEquals(SkillEffect.METEOR, meteor.effect());
        assertEquals('R', meteor.key());
        assertEquals(6, meteor.levelForRank(1), "open from its sixth level");
        assertTrue(meteor.isUltimate(), "an ultimate, by the rule that makes one");
        assertEquals(1, meteor.maxRank());
        assertEquals(90f, meteor.damage(), 0.001f);
        assertEquals(30f, meteor.radius(), 0.001f);
        assertEquals(70f, meteor.range(), 0.001f);
        assertEquals(45, meteor.windUpFrames());
        assertEquals(600, meteor.cooldownFrames());
        assertEquals(50, meteor.manaCost());
        assertEquals("Meteor", meteor.name());
        assertEquals(MARK, meteor.projectile());
        assertFalse(meteor.hasLook(), "a monster's cast is drawn by what it throws");
        assertEquals(SkillEffect.SKILLSHOT, SETTINGS.skillsFor(MAGE).get(1).effect(), "and its fireball second");
    }

    /** Its mark is its own, drawn violet as its fireball, and measured by its own skill: the Mage's keeps its own. */
    @Test
    void itsMarkIsItsOwnAndMeasuredByItsOwnSkill() {
        var mark = SETTINGS.projectile(MARK);
        assertEquals("SkullMeteor", mark.effect());
        assertEquals(SETTINGS.projectile(fireball().projectile()).tint(), mark.tint(), "its fireball's violet");

        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);

        assertEquals(meteor().radius(), visuals.getEffectReach(mark.effect()), 0.001f, "as wide as its blast");
        assertEquals(meteor().windUpFrames() / (float) GameConstants.LOGICFRAMES_PER_SECOND,
                visuals.getEffectSeconds(mark.effect()), 0.001f, "and lying there as long as it falls");
        var his = SETTINGS.skillsFor("Mage").stream().filter(skill -> skill.effect() == SkillEffect.METEOR)
                .findFirst().orElseThrow();
        assertEquals(his.radius(), visuals.getEffectReach(SETTINGS.projectile(his.projectile()).effect()), 0.001f,
                "and the Mage's own meteor is as wide as his");
    }

    /** It is heard as it appears, as the rift is. */
    @Test
    void itIsHeardAsItAppears() {
        assertTrue(SETTINGS.sounds().stream().anyMatch(sound -> sound.name().equals("spawned." + MARK)));
    }

    // ---- in a fight ----

    /** Below its sixth level it is never cast: full, and with him inside its band for as long as its cooldown. */
    @Test
    void belowItsSixthLevelItIsNeverCast() {
        var fight = atLevel(5);

        assertTrue(fight.mage().findModule(uz.dukeengine.dungeon.skill.SkillBook.class).getMana()
                >= meteor().manaCost(), "the premise: it could pay for one");
        assertEquals(List.of(), appearing(fight.game(), meteor().cooldownFrames(), MARK));
    }

    /** From its sixth level it is cast at where he stands, and what marks the floor is its own mark. */
    @Test
    void fromItsSixthLevelItIsCastAtWhereHeStands() {
        var fight = atLevel(6);

        GameObject mark = null;
        for (int frame = 0; frame < 60 && mark == null; frame++) {
            fight.game().runHeadless(1);
            mark = creature(fight.game(), MARK);
        }

        assertNotNull(mark, "no meteor was called down on him");
        assertEquals(fight.hero().getPosition().x(), mark.getPosition().x(), 0.5f, "where he stands");
        assertEquals(fight.hero().getPosition().y(), mark.getPosition().y(), 0.5f, "where he stands");
    }

    /** While it recharges, its fireball follows: a gesture after the meteor, and no second meteor. */
    @Test
    void whileItRechargesItsFireballFollows() {
        var fight = atLevel(6);
        int swing = SETTINGS.monster(MAGE).swingFrames();
        var game = fight.game();
        var marks = new ArrayList<Integer>();
        var fireballs = new ArrayList<Integer>();
        var seen = new HashSet<Integer>();

        for (int frame = 0; frame < 300; frame++) {
            game.runHeadless(1);
            for (var object : game.getLogic().getObjects()) {
                var name = object.getTemplate().name();
                if (seen.add(object.getId().value())) {
                    if (name.equals(MARK)) {
                        marks.add(game.getLogic().getFrame());
                    } else if (name.equals(fireball().projectile())) {
                        fireballs.add(game.getLogic().getFrame());
                    }
                }
            }
        }

        assertEquals(1, marks.size(), "one meteor in ten seconds of a twenty-second cooldown: " + marks);
        assertFalse(fireballs.isEmpty(), "and no fireball while it recharged");
        assertTrue(fireballs.getFirst() >= marks.getFirst() + swing,
                "the fireball left inside the meteor's gesture: " + marks + " then " + fireballs);
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.SkullMeteorTest"`
Expected: FAIL, 5 of 6 tests — `theFireMagesMeteorIsWrittenFirst` (`expected: <METEOR> but was: <SKILLSHOT>`),
`itsMarkIsItsOwnAndMeasuredByItsOwnSkill` (`expected: <SkullMeteor> but was: <null>`), `itIsHeardAsItAppears`
(`expected: <true> but was: <false>`), `fromItsSixthLevelItIsCastAtWhereHeStands` (`no meteor was called down on him
==> expected: not <null>`), `whileItRechargesItsFireballFollows` (`one meteor in ten seconds of a twenty-second
cooldown: [] ==> expected: <1> but was: <0>`). `belowItsSixthLevelItIsNeverCast` passes, and must go on passing.

- [ ] **Step 3: Its mark, and how the mark is drawn** — `src/main/resources/data/projectiles/skull_meteor_mark.duke`.
  The kit's `Meteor` layer for layer in the fire mage's violet — each colour its `DarkFireball`'s for the same part —
  and, since its blast is 30 across where the Mage's is 44, what it throws, how big it flashes and how far it lights at
  30/44 of the kit's, as `DarkFireball` is drawn to its own smaller blast (at the kit's own speeds its fire, smoke and
  sparks fly past 30, and `DungeonEffectLayerTest.nothingABlastThrowsFliesPastWhereItsDamageStops` fails with
  `SkullMeteor Smoke reaches 33.516174 past a blast of 30.0`); what is measured in reach is left as the kit's:

```
; The mark the Skeleton Mage's meteor leaves on the floor while it falls -- the
; Mage's MeteorMark again, from the other side, and a thing in the world for the
; same reasons. How long it marks the ground and how hard it lands are the
; skill's: see the R skill of the SkeletonMage.
;
; Its own and not the Mage's, because the client measures a mark by the skill
; that throws it (Main.measureLooks): his blast is 44 across and this one 30, and
; one mark shared by the two would be drawn the width of whichever was read last.
Projectile
  Name = SkullMeteorMark
  DisplayName = Meteor
  VisionRange = 0
  Modules = [
    FallingUpdate
    End
  ]


; On the floor rather than at shot height, violet as its fireball is.
  Height = 1
  Tint = 0xB04AFF
  Effect = SkullMeteor
  EffectOffset = 0
End

; ---------------------------------------------------------------------------
; The kit's Meteor, layer for layer, burning violet instead of gold -- the colours
; of the Skeleton Mage's fireball (the kit's DarkFireball), so a player can tell
; at a glance whose sky is falling on him. The mark glows on the floor for the
; second and a half the rock falls, and bursts where it lands; how long and how
; wide are the skill's, handed over by Main.measureLooks. Its blast is 30 across
; where his is 44, so what it throws, how big it flashes and how far it lights
; are drawn to that smaller blast -- 30/44 of his, as its fireball's are drawn
; to its own -- and nothing it throws flies past where its damage stops.
; ---------------------------------------------------------------------------
Effect
  Name = SkullMeteor
  ShakeSeconds = 0.26
  ShakePower = 1.8

  Layers = [
    Layer
      Name = Rock
      Type = AURA
      Texture = kit/effects/particles/fire_02.png
      Blend = Additive
      Cover = 0.55
      Count = 24
      Rate = 50
      Life = [0.15, 0.25]
      Size = [10.91, 6.82]
      Colour = [0xF040FF, 0x8B00E0]
      Alpha = [1, 0]
      Radius = 1.36
      Spin = 120
      Fall = 160
    End,

    Layer
      Name = Heart
      Type = AURA
      Texture = kit/effects/particles/circle_05.png
      Blend = Additive
      Cover = 0.3
      Count = 6
      Rate = 30
      Life = [0.12, 0.2]
      Size = [9.55, 6.82]
      Colour = [0xFFB8FB, 0xDF30FF]
      Alpha = [1, 0.6]
      Fall = 160
    End,

    Layer
      Name = Flames
      Type = TRAIL
      Texture = kit/effects/particles/fire_01.png
      Blend = Additive
      Cover = 0.45
      Count = 56
      Rate = 80
      Life = [0.3, 0.5]
      Size = [9.55, 3.41]
      SizeEase = 2
      SizeJitter = 0.3
      Colour = [0xF047FF, 0x43008A]
      ColourEase = 1.5
      Alpha = [1, 0]
      Speed = [1.36, 4.09]
      Direction = UP
      Spread = 30
      Drag = 2
      Spin = 80
      Fall = 160
    End,

    Layer
      Name = Wake
      Type = TRAIL
      Texture = kit/effects/particles/smoke_05.png
      Blend = Alpha
      Count = 28
      Rate = 22
      Life = [0.8, 1.2]
      Size = [5.45, 12.27]
      Colour = [0x554A5A, 0x37303A]
      Alpha = [0.55, 0]
      Speed = [0.68, 2.73]
      Direction = UP
      Spin = 20
      Fall = 160
    End,

    Layer
      Name = Glow
      Type = LIGHT
      LightColour = 0xC228FF
      LightPower = 3.5
      LightRadius = 68.18
      Fall = 160
    End,

    Layer
      Name = Scorch
      Type = MARK
      Measure = Reach
      Texture = kit/effects/particles/scorch_03.png
      Blend = Alpha
      Count = 1
      Seconds = 4
      Size = [3.4, 3.4]
      Colour = [0x17101A, 0x17101A]
      Alpha = [0.6, 0.6]
      FadeOut = 0.35
      Height = 0.3
    End,

    Layer
      Name = Dust
      Type = RING
      Measure = Reach
      Texture = kit/effects/particles/smoke_10.png
      Blend = Alpha
      Delay = 0.03
      Count = 1
      Life = [0.9, 0.9]
      Size = [0.45, 4.324]
      SizeEase = 2.4
      Colour = [0x85688A, 0x55445A]
      Alpha = [0.6, 0]
      FadeOut = 0.6
      Spin = 15
      Height = 0.6
    End,

    Layer
      Name = Wave
      Type = RING
      Measure = Reach
      Texture = kit/effects/particles/circle_03.png
      Blend = Additive
      Cover = 0.2
      Count = 1
      Life = [0.5, 0.5]
      Size = [0.25, 2.807]
      SizeEase = 3.2
      Colour = [0xF560FF, 0x9A10FF]
      Alpha = [1, 0]
      FadeOut = 0.55
      Height = 0.5
      TurnJitter = 0
    End,

    Layer
      Name = Smoke
      Type = BURST
      Measure = Reach
      Texture = kit/effects/particles/smoke_08.png
      Blend = Alpha
      Delay = 0.12
      Count = 16
      Radius = 0.4
      Life = [1.0, 1.6]
      Size = [0.35, 0.7]
      SizeEase = 2
      SizeJitter = 0.35
      Colour = [0x756A7A, 0x47404A]
      Alpha = [0.4, 0]
      FadeIn = 0.1
      Speed = [4.09, 9.55]
      Direction = OUT
      Spread = 55
      Drag = 1.8
      Gravity = -4
      Spin = 18
      Height = 4
    End,

    Layer
      Name = Debris
      Type = BURST
      Texture = kit/effects/particles/dirt_02.png
      Blend = Alpha
      Count = 20
      Life = [0.7, 1.1]
      Size = [3.41, 2.05]
      SizeJitter = 0.5
      Colour = [0x5A4A3C, 0x3A2E24]
      Alpha = [1, 0.9]
      FadeOut = 0.25
      Speed = [17.05, 34.09]
      Direction = UP
      Spread = 60
      Gravity = 120
      Drag = 0.5
      Spin = 240
      Height = 2
    End,

    Layer
      Name = Fire
      Type = BURST
      Measure = Reach
      Texture = kit/effects/particles/fire_02.png
      Blend = Additive
      Cover = 0.4
      Count = 36
      Radius = 0.42
      Life = [0.45, 0.8]
      Size = [0.3, 0.42]
      SizeEase = 2.5
      SizeJitter = 0.3
      Colour = [0xFC4AFF, 0x9A10FF]
      Alpha = [0.8, 0]
      FadeOut = 0.5
      Speed = [20.45, 34.09]
      Direction = OUT
      Spread = 40
      Drag = 3
      Gravity = -8
      Spin = 50
      Height = 3
    End,

    Layer
      Name = Core
      Type = IMPACT
      Measure = Reach
      Texture = kit/effects/particles/circle_05.png
      Blend = Additive
      Count = 1
      Life = [0.45, 0.45]
      Size = [0.6, 2.8]
      SizeEase = 2.5
      Colour = [0xF070FF, 0xAA10FF]
      Alpha = [0.7, 0]
      FadeOut = 0.7
      Height = 3
    End,

    Layer
      Name = Sparks
      Type = BURST
      Texture = kit/effects/particles/star_04.png
      Blend = Additive
      Count = 44
      Life = [0.4, 0.8]
      Size = [1.5, 0.2]
      Stretch = 0.05
      Colour = [0xFFB0F4, 0xBA10FF]
      Speed = [34.09, 75]
      Direction = OUT
      Spread = 55
      Drag = 2.5
      Gravity = 60
      Height = 3
    End,

    Layer
      Name = Flash
      Type = IMPACT
      Texture = kit/effects/particles/star_09.png
      Blend = Additive
      Count = 1
      Life = [0.25, 0.25]
      Size = [16.36, 47.73]
      SizeEase = 3
      Colour = [0xFFD8FB, 0xEA50FF]
      Alpha = [1, 0]
      FadeOut = 0.6
      Height = 4
      TurnJitter = 0
    End,

    Layer
      Name = Blast
      Type = LIGHT
      Seconds = 0.7
      LightColour = 0xCF30FF
      LightPower = 3
      LightRadius = 88.64
      FadeOut = 0.9
    End
  ]
End
```

`game.duke`, after `meteor_mark.duke` in the projectiles:

```
    data/projectiles/meteor_mark.duke,
    data/projectiles/skull_meteor_mark.duke,
```

- [ ] **Step 4: Heard as the rift is** — `sfx.duke`, after `spawned.SummoningRift`:

```
; A Skeleton Mage calling its meteor down: the Mage's own call (skill.R), softer,
; because it is the same thing happening to the other side -- heard as its mark
; lands on the floor, a second and a half before the rock does.
Sound
  Name = spawned.SkullMeteorMark
  Channel = Effects
  Positional = Yes
  Gain = 0.6
  Files = [audio/sfx/skill_empower.ogg]
End
```

- [ ] **Step 5: The meteor, written first** — `skeleton_mage.duke`. Its first note:

```
; The one that casts. Frail, slow, and dangerous only at a distance: between its
; fireballs it throws the Revenant's small fire, and the fireball is a skill its
; brain decides to throw -- see Monster SkeletonMage and Skill
; SkeletonMage Q below. From its sixth level it calls a meteor down on him too,
; its R.
```

the note over its skills, from `; A monster's own skill.` to the pool:

```
  ; A monster's own skills.
  ;
  ; Headed by the creature's name exactly as a hero's are, and cast by its brain
  ; rather than a key -- the first, in the order written, that its level has
  ; opened, that is ready and that it can pay for: see SkillDistance and
  ; KeepDistance on its Monster block. Each has one rank: what makes it hit
  ; harder further down is its level, as for its weapon. ProjectileSpeed is how
  ; fast what it throws travels; unsaid, it would be the hero's HeavySpeed. It
  ; says 120, which is what HeavySpeed is today, and stays 120 if that moves.
  ;
  ; And it pays for them, as a hero does, out of a pool in the same words and
  ; units: MaxMana in points, ManaRegen in tenths of a point a second -- each a
  ; tenth more a level past the first (ManaPercentPerLevel in generation.duke),
  ; and full when it is placed. Enough to throw at its cooldowns through a fight
  ; of a minute or more, its meteor included, and longer the higher it stands.
  ; Nobody sees the pool: the bar's mana is the hero's alone.
  ; ---------------------------------------------------------------------------
  MaxMana = 60
  ManaRegen = 30
```

and first in `Skills = [`, before the `Q`:

```
  Skills = [
    Skill
      ; Its ultimate: the Mage's own meteor, from the other side -- a mark on the
      ; floor where he stands, and the blast a second and a half later. It waits
      ; for its sixth level as a hero's ultimate waits for his (LevelPerRank is the
      ; level its one rank opens at), so on the first floor only the fire mages
      ; nearest the keep call it down, and deeper every one does. Written first,
      ; so it is the one its brain casts whenever it is open, ready and paid for;
      ; while it recharges, or its pool is short of it, the fireball follows.
      Key = R
      Effect = METEOR
      ; A hero at the middle of the mark walks out of its 30 in the second and a
      ; half it lies there. The Mage's own is 160 across 44: this is the
      ; monster's, and its level is what makes it hit harder.
      Damage = 90
      Radius = 30
      Range = 70
      WindUpFrames = 45
      ; Its own mark, violet as its fireball is -- data/projectiles/
      ; skull_meteor_mark.duke. Not the Mage's MeteorMark: a mark is drawn as wide
      ; as the skill that throws it, and his is wider.
      Projectile = SkullMeteorMark
      ; Twenty seconds between meteors, and most of its pool: a stunning fireball
      ; under a falling meteor is a hard pair, and its cooldown and its cost keep
      ; it rare.
      CooldownFrames = 600
      ManaCost = 50
      MaxRank = 1
      LevelPerRank = 6
      Name = Meteor
    End,
    Skill
      Key = Q
```

- [ ] **Step 6: The tests that took its first skill for its fireball** — `MonsterSkillTest`:
  `import uz.dukeengine.dungeon.skill.Skill;` after `Spawner`'s, and:

```java
    /** Its fireball, its Q: its meteor is written before it, and is not open at the first level these fight at. */
    private static Skill theFireball() {
        return SETTINGS.skillsFor(MAGE).stream().filter(skill -> skill.key() == 'Q').findFirst().orElseThrow();
    }

    private static String fireball() {
        return theFireball().projectile();
    }
```

and the three `SETTINGS.skillsFor(MAGE).get(0).cooldownFrames()` — in `itWaitsForItsCooldownBetweenThrows`,
`betweenItsFireballsItThrowsItsOrdinaryFire` and `aBlowFrom` — become `theFireball().cooldownFrames()`.

`StunTest`, after `FIRE_MAGE`:

```java
    /** The fire mage's fireball under {@code settings}: its Q, written after its meteor. */
    private static Skill fireballOf(DungeonSettings settings) {
        return settings.skillsFor(FIRE_MAGE).stream().filter(skill -> skill.key() == 'Q').findFirst().orElseThrow();
    }
```

and `SETTINGS.skillsFor(FIRE_MAGE).getFirst().stunFrames()` (twice) becomes `fireballOf(SETTINGS).stunFrames()`,
`settings.skillsFor(FIRE_MAGE).getFirst().projectile()` becomes `fireballOf(settings).projectile()`.

`LifestealTest.anOrdinaryShotIsDrunkFromOnceWhereItLands`:

```java
        float fireball = SETTINGS.skillsFor("SkeletonMage").stream().filter(skill -> skill.key() == 'Q')
                .findFirst().orElseThrow().damage();
```

(Every fire mage these tests fight is spawned by hand, so at level 1, where the meteor is not open: what they measured
is what they measure.)

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.SkullMeteorTest" --tests "uz.dukeengine.dungeon.StunTest" --tests "uz.dukeengine.dungeon.LifestealTest" --tests "uz.dukeengine.dungeon.ai.MonsterSkillTest" --tests "uz.dukeengine.dungeon.DungeonEffectLayerTest" --tests "uz.dukeengine.dungeon.DungeonSoundTest" --tests "uz.dukeengine.dungeon.ProjectileEffectTest"`
Expected: PASS — `SkullMeteorTest` 6 tests; `DungeonEffectLayerTest` finds `SkullMeteor`'s layers drawable, from
textures that are there, as wide as its blast and nothing thrown past it; `DungeonSoundTest` its sound; the rest as
before.

- [ ] **Step 8: Run the whole suite**

Run: `./gradlew test`
Expected: PASS, 883 tests.

- [ ] **Step 9: Look at it** — the owner looks, in the game (`./gradlew run`), at a fire mage in the chamber before the
  first floor's keep, or on the second floor: the violet mark on the floor under him, the rock falling onto it for a
  second and a half, the burst; and its medallion saying its level. The `SkullMeteor` block's `Size`, `Speed` and
  `LightRadius` and the meteor's `Damage`, `Radius`, `CooldownFrames` and `ManaCost` are the numbers to tune. Nothing
  to run here beyond the suite: report what to look at.

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/data/game.duke src/main/resources/data/projectiles/skull_meteor_mark.duke src/main/resources/data/sounds/sfx.duke src/main/resources/data/units/skeleton_mage.duke src/test/java/uz/dukeengine/dungeon/LifestealTest.java src/test/java/uz/dukeengine/dungeon/SkullMeteorTest.java src/test/java/uz/dukeengine/dungeon/StunTest.java src/test/java/uz/dukeengine/dungeon/ai/MonsterSkillTest.java
git commit -m "The fire mage calls a violet meteor down on him from its sixth level, its fireball after it" -m "Co-Authored-By: <the model that did the work> <noreply@anthropic.com>"
```

---

## Where the spec and the code part

- **A monster nobody placed has no level, no word and no pool.** `Spawner.scale` is the one step, and only what the
  spawner places or a rift raises goes through it. A creature spawned by hand — every arena test's — carries no
  `LevelBonus` (`LevelBonus.levelOf` says 1), no `level:` word, and no pool: it casts free, whatever its kind names.
  The spec says this of the level ("a test's bare skeleton is level 1"); it is read the same way for the pool, which
  keeps every fight the existing tests stage exactly as it was. A test that wants a level calls `Spawner.scale` itself.
- **Where there is no keep, the way ends at the boss's own place** — its cell. On a generated floor with no keep that is
  the middle of its chamber, as the spec says; on a stage it is wherever the author stood the boss, which is what the
  spec's test asks ("a stage's way ends at its boss").
- **A stage keeps no word of a keep.** `StageFile` builds a stage's floor with `keep` null, and a keep is where a floor's
  way ends — so a stage cut from a keep floor would stand its monsters at other levels than the floor it was cut from.
  Stages are always cut keepless (`MapWriter`); `StagePlayTest.aStagePlaysExactlyLikeTheDungeonItWasCutFrom` now draws
  the compared floor keepless too (its promise is unchanged). Writing the keep into stage files is not needed for any
  shipped map.
- **Where no step reaches, the whole way.** A monster the walk cannot reach, or a floor whose way has no steps, stands at
  the chamber's level. None does on a generated floor (the tree of tunnels reaches everything) or a stage (`StageCheck`
  refuses one that does not).
- **"A skill aimed at him must reach the band's far end"** is asked of skills that reach him: those whose effect is aimed
  at something (`SkillEffect.aim()` other than `SELF`), mendings aside. A `SUMMON` is cast toward him but opens its
  rifts round the caster (`Aim.SELF`, `Range` 0): read literally the rule would refuse the shipped summoner. The band
  itself is still asked of every skill cast at him, the summoning's included.
- **File order is the settings' order.** For the shipped file it is the block's own order — which is what puts the meteor
  first. A block that re-tunes a unit keeps the shipped skills in their shipped order and puts any it adds after them
  (`fillInMissingSkills`, as for every unit), so the brain tests add their skills after the fireball.
- **The Skeleton was on the engine's `ActiveBody`,** whose maximum is fixed when it is built: the depth never grew it, and
  a level could not. It carries `GrowableBody` now, as the game's manifest says every monster does.
- **`Segments` 50 → 120.** The spec asks for the marks to be measured again over the new range; the widest bar is the
  deep stage's Champion at 50 (3540 health). The least lot that holds every creature at `CLOSEST` is 105; 120 gives it
  29 marks about five pixels apart, the spacing the file was tuned to — and a hero three to eight marks at his first
  level.
- **The meteor's look is the kit's `Meteor` recoloured and drawn to its blast.** Colours are the `DarkFireball`'s part
  for part; sizes, speeds and light radii not measured in reach are 30/44 of the kit's, because at the kit's own figures
  its fire, smoke and sparks fly past a 30 blast and `DungeonEffectLayerTest` refuses it. The camera's knock is the
  kit's.
- **The shipped sound** is `audio/sfx/skill_empower.ogg` — the Mage's own meteor call (`skill.R`) — at 0.6: the spec
  names none.
- **`LevelBonus` keeps the health multiplier** the spec lists ("the level and the two multipliers"); since what rises is
  made by `Spawner.scale` from the level, nothing but tests reads it now.
- **A skill a monster cannot pay for is passed over by the brain, not tried**: it asks `canAfford` first, so the book's
  refusal (tested in `MonsterManaTest`) never stamps `refusedForManaFrame` on a monster — that stamp is the hero's
  status line's.
- **The pool check is asked of the block's own skills where the record is read**, as `requireBook` is: a re-tuning block
  that writes no skills and no `MaxMana` is let be, and its kind casts free.
- **One cast per gesture:** `castAt` now waits out `midCast()` as well as `midBlow()` (with one skill the cooldown did
  that; with several it would not).
- **`aCasterWithALevelsBonusHitsHarder`** (was `aCasterFoundDeeperHitsHarder`) hangs a hand-made `LevelBonus` of ×2 at
  level 1 on the mage — the old test's contrivance with the new module — so the meteor stays closed while the fireball's
  doubling is measured; and `aWiderBandInTheFileIsAWiderBandOnTheFloor` casts from `[20, 70]` rather than `[20, 120]`,
  which the new rule refuses.
- **Mana grows in whole numbers:** `base + base × (level − 1) × ManaPercentPerLevel / 100`, integer division (the fire
  mage at 8: 60 → 102, 30 → 51 tenths). Worth is `Math.round(value × multiplier)` once; what rises gets its share of
  that by integer division.
- **`LevelWord` may not hold ',' or '|'**, checked when the file is read: a creature's words cross to the client joined
  by ',' in a line split on '|'.
- **No engine request.** E7 is used as landed. A monster's mana drawn on its bar stays out, as the spec leaves it (an
  engine request, if it is ever wanted).
- **Beyond the spec's list, touched for its words only:** `LootDrop`'s class note (`beside the depth bonus`), `Dungeon`'s
  comment over `GrowableBody`, `game.duke`'s and `warden.duke`'s notes, `HeroStatusTest`'s note on `|deep=`.

## Whole-suite counts

| After | Tests | New |
|---|---|---|
| master `7d41ada` | 843 | — |
| Task 1 | 849 | `MonsterLevelTest` 6 |
| Task 2 | 853 | `MonsterLevelTest` +4 |
| Task 3 | 862 | `MonsterLevelTest` +9 |
| Task 4 | 865 | `MonsterLevelTest` +2, `DungeonUnitBarTest` +1 |
| Task 5 | 871 | `MonsterManaTest` 6 |
| Task 6 | 877 | `MonsterSkillTest` +6 |
| Task 7 | 883 | `SkullMeteorTest` 6 |
