# Stun, lifesteal, and a summoner's four — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The fire mage's fireball stuns whoever it hurts for a second, stars over his head while it lasts; every
boss gets back a quarter of what its blows deal; the summoner raises two swordsmen and two archers a cast.

**Architecture:** A stun is the engine's `DISABLED`, set by `ArrowUpdate` on the victim's own `StatusUpdate` for the
`StunFrames` the skill's shot carries (`SkillBook` → `Shot.looseAlong` → `ArrowUpdate`); the heroes gain the
`StatusUpdate` every monster has, `SkillBook.cast` refuses while its owner is `DISABLED`, and the stars are an
`Effect` named by the Combat block's `StunLook`, played riding the victim and renewed with a second stun by a game rule
in `Main.layerOf`, given their seconds by the longest `StunFrames` any skill has (as `Main.measureLooks` sets them).
Lifesteal is a passive skill, `SkillEffect.LIFESTEAL`, in the skill system and never cast; the two places a boss's blow
lands tell `SkillBook.drink` — `Swing.launch` for a swing, `ArrowUpdate.strike`/`splash` for a shot. `Skill.summons`
becomes an ordered `Map<String, Integer>` read as `BossGuards` is, and `SkillBook.summon` hands the kinds to its rifts
in the order written.

**Tech Stack:** Java 25, JUnit 5, Gradle (`./gradlew test`), `.duke` data read by the engine's record reader,
duke-engine 0.7.0 from the checkout beside this one.

**Spec:** `docs/plan/2026-09-30-stun-lifesteal-summons.md`

## Global Constraints

- Lock-step: whole-number frames and percentages, a fixed order, no clock, no hash iteration, and every change of
  state on the simulation thread (a module's update, a launcher's callback). A lifesteal is one multiplication of the
  blow's own figure by a whole percentage; `Summons` is walked in the order the file wrote it — the reader's map
  keeps it — never through a `Map.of` of several entries, whose order is not fixed.
- Names and numbers come from data: `StunFrames` on the skill, `StunLook` on the Combat block, `BoostPercent` on the
  passive `LIFESTEAL` skill, `Summons` and `MaxSummoned` on the skill. Nothing is compiled in — a blank `StunLook` plays
  nothing.
- `../duke-engine` is never edited. Everything here is game code and data.
- Each task is committed on its own once its tests pass: the message is the subject, a blank line, and
  `Co-Authored-By: <the model that did the work> <noreply@anthropic.com>`.
- Stay out of the keep, the gate, the loot bag and the HUD: another piece is being planned over them.

## Files

| File | What it is |
|---|---|
| `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` | refuses a cast while stunned; the skillshot carries its stun; summons several kinds; `drink` tells a passive skill of blows |
| `src/main/java/uz/dukeengine/dungeon/skill/Skill.java` | + `stunFrames`; `summons` a map, `summonCount` gone |
| `src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java` | `SUMMON`'s words; `LIFESTEAL`, passive, never cast |
| `src/main/java/uz/dukeengine/dungeon/combat/Shot.java` | `looseAlong` carries a stun |
| `src/main/java/uz/dukeengine/dungeon/combat/ArrowUpdate.java` | stuns what it hurts, plays the stars, tells `SkillBook.drink` of shots and bursts |
| `src/main/java/uz/dukeengine/dungeon/combat/Swing.java` | tells `SkillBook.drink` of a swing |
| `src/main/java/uz/dukeengine/dungeon/world/Combat.java` | + `stunLook` |
| `src/main/java/uz/dukeengine/dungeon/content/Monster.java` | `skillKey` skips passives |
| `src/main/java/uz/dukeengine/dungeon/content/MonsterKind.java` | `@param skillKey` reworded for passives |
| `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` | checks `StunFrames` and `Summons`; requires a `SkillBook` if `Skills` are listed; validates passives and `LIFESTEAL` |
| `src/main/java/uz/dukeengine/dungeon/content/Content.java`, `.../Dungeon.java` | `Lifesteal` module gone; `ArrowUpdate` built with the stun's look |
| `src/main/java/uz/dukeengine/dungeon/Main.java` | `measureLooks` gives the stars their seconds; `layerOf` sets renewal on the stun's layers |
| `src/main/resources/data/units/rogue.duke`, `knight.duke`, `mage.duke` | + `StatusUpdate`; the Rogue's field notes on effects |
| `src/main/resources/data/units/skeleton_mage.duke` | `StunFrames = 30` |
| `src/main/resources/data/units/warden.duke`, `reaper.duke`, `necromancer.duke`, `champion.duke` | + `SkillBook`; a `LIFESTEAL` skill |
| `src/main/resources/data/units/skeleton_summoner.duke` | `Summons = [Skeleton = 2, Stalker = 2]` |
| `src/main/resources/data/world/world.duke` | `StunLook = Stunned`, and the `Stunned` effect with renewal |
| `src/test/java/uz/dukeengine/dungeon/StunTest.java` | **new** |
| `src/test/java/uz/dukeengine/dungeon/LifestealTest.java` | tests the passive skill |
| `src/test/java/uz/dukeengine/dungeon/DungeonEffectLayerTest.java` | validates effect layers including stun renewal |
| `src/test/java/uz/dukeengine/dungeon/ai/MonsterSummoningTest.java` | the summoner's four |
| `src/test/java/uz/dukeengine/dungeon/skill/ManaTest.java`, `SkillRanksTest.java`, `SkillTest.java` | the `Skill` record's new shape |

---

### Task 1: A stunned creature casts nothing, and the heroes carry the timers a stun is counted on

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` (`cast(char, int)`'s Javadoc ~line 390;
  `cast(char, int, ObjectId, Coord3D)` right after the cooldown check, ~line 414)
- Modify: `src/main/resources/data/units/rogue.duke`, `knight.duke`, `mage.duke` (a module right after
  `HeroBrain`)
- Test: `src/test/java/uz/dukeengine/dungeon/StunTest.java` (**new**)

**Interfaces:**
- Consumes: the engine's `StatusUpdate.apply(ObjectStatus, int)` (refreshes, never stacks) and
  `GameObject.hasStatus(ObjectStatus.DISABLED)`, under which `MoveUpdate` and `WeaponUpdate` already stand still.
- Produces: `SkillBook.cast(...)` returns `false`, spending nothing, while its owner is `DISABLED`; every hero
  template carries a `StatusUpdate`. `StunTest` with the private helpers `room()`, `creature(DukeGame, String)`,
  `modulesOf(String)` that Tasks 2 and 3 add to.

- [ ] **Step 1: Write the failing test**

```java
package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Hero;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.module.StatusUpdate;

/**
 * A stun: the engine's own {@code DISABLED}, worn for as long as what stunned it says -- no step, no blow and no
 * cast while it lasts, and all three back once it is over.
 */
class StunTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

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

    /** What a unit's block is built from, as the shipped files write it. */
    private static List<ModuleData> modulesOf(String unit) {
        for (var record : Content.records(Content.units(), "units")) {
            if (record instanceof Hero hero && hero.name().equals(unit)) {
                return hero.modules();
            }
            if (record instanceof Monster monster && monster.name().equals(unit)) {
                return monster.modules();
            }
        }
        throw new AssertionError("the shipped files have no unit called " + unit);
    }

    // ---- who can be stunned, and what it stops ----

    /** Every hero carries the timers a stun is counted down on, as every monster already does. */
    @Test
    void everyHeroCarriesTheTimersForWhatHeWears() {
        assertFalse(SETTINGS.heroes().isEmpty(), "the shipped files name no hero");
        for (var hero : SETTINGS.heroes()) {
            assertTrue(modulesOf(hero.name()).stream().anyMatch(StatusUpdate.Data.class::isInstance),
                    hero.name() + " carries no StatusUpdate, so nothing can stun him");
        }
    }

    /**
     * Stunned, he casts nothing and spends nothing -- the cooldown stands as it stood, as it does for a cast he
     * cannot pay for -- and the frame the stun is over, the same key goes off.
     */
    @Test
    void aStunnedHeroCastsNothingAndCastsAgainOnceItIsOver() {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Rogue");
        var book = hero.findModule(SkillBook.class);

        hero.findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 30);

        assertFalse(book.cast('W', 1), "he cast while stunned");
        assertTrue(book.isReady('W'), "and the cast that never went off spent its cooldown");
        game.runHeadless(30);
        assertFalse(hero.hasStatus(ObjectStatus.DISABLED), "thirty frames on, he is still stunned");
        assertTrue(book.cast('W', 1), "and the stun over, he still cannot cast");
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.StunTest"`
Expected: FAIL, 2 tests — `everyHeroCarriesTheTimersForWhatHeWears` with "Rogue carries no StatusUpdate, so nothing
can stun him", and `aStunnedHeroCastsNothingAndCastsAgainOnceItIsOver` with a `NullPointerException`
(`findModule(StatusUpdate.class)` is null).

- [ ] **Step 3: Give the heroes their timers**

`rogue.duke`, after the `HeroBrain` module:

```
    ; Walks to whatever he is told to attack — see HeroBrain.
    HeroBrain
    End,
    ; Timers for the statuses he wears, as every monster carries -- a stun, so
    ; far: the engine's DISABLED, for as long as whatever stunned him says. His
    ; legs and his bow stand still under it and his skills refuse (SkillBook.cast);
    ; this counts it down and takes it off.
    StatusUpdate
    End,
```

`knight.duke` and `mage.duke`, each right after its own `HeroBrain` / `End,` pair:

```
    HeroBrain
    End,
    ; Timers for the statuses he wears, as the archer's -- a stun, so far.
    StatusUpdate
    End,
```

- [ ] **Step 4: Refuse a cast while stunned** — in `SkillBook`, the Javadoc of `cast(char key, int level)`:

```java
    /**
     * Cast the skill on {@code key} at a hero of {@code level}. Returns false and
     * does nothing if there is no such skill, it is still recharging, he is
     * stunned, or the level has not unlocked it — an ultimate refuses rather than
     * fires weakly.
     */
```

and in `cast(char key, int level, ObjectId at, Coord3D towards)`, between the cooldown check and
`var skill = skills.get(slot);`:

```java
        int slot = slotOf(key);
        if (slot < 0 || cooldowns[slot] > 0) {
            return false;
        }
        if (getOwner().hasStatus(ObjectStatus.DISABLED)) {
            // Stunned -- the engine's DISABLED, which his legs and his weapon
            // already stand still under. Refused before anything happens, as a
            // cast he cannot pay for is: no cooldown started, nothing taken. What
            // he cast before it goes on (a drawn shot still leaves, a whirlwind
            // still turns), and whatever chooses for him goes on choosing; he
            // only cannot act.
            return false;
        }
        var skill = skills.get(slot);
```

(`ObjectStatus` is already imported: `chill` uses it.)

- [ ] **Step 5: Run the test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.StunTest"`
Expected: PASS, 2 tests.

- [ ] **Step 6: Run the whole suite** — every hero is built with one more module.

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/resources/data/units/rogue.duke src/main/resources/data/units/knight.duke src/main/resources/data/units/mage.duke src/test/java/uz/dukeengine/dungeon/StunTest.java
git commit -m "A stunned hero casts nothing, and every hero carries the timers a stun is counted on" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The fire mage's fireball stuns whoever it hurts, for a second

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/Skill.java` (+ `stunFrames`, the last component)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` (the `SKILLSHOT` case of `apply`, ~line 583)
- Modify: `src/main/java/uz/dukeengine/dungeon/combat/Shot.java` (`looseAlong`)
- Modify: `src/main/java/uz/dukeengine/dungeon/combat/ArrowUpdate.java` (`looseAlong`, `strike`, `splash`, + `stun`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate()`, the loop over skills,
  ~line 674)
- Modify: `src/main/resources/data/units/skeleton_mage.duke` (its `Q`), `rogue.duke` (the notes on `SKILLSHOT`)
- Modify tests: `src/test/java/uz/dukeengine/dungeon/skill/ManaTest.java` (~line 284),
  `.../skill/SkillRanksTest.java` (~lines 31, 36), `.../skill/SkillTest.java` (~lines 20, 94)
- Test: `src/test/java/uz/dukeengine/dungeon/StunTest.java`

**Interfaces:**
- Consumes: Task 1's refusal and hero timers; `StatusUpdate.apply`.
- Produces: `int Skill.stunFrames()` (last component); `Shot.looseAlong(GameObject shooter, Coord3D towards,
  float damage, DamageType type, String template, float speed, float muzzleOffset, float distance, float blast,
  int stun)`; package-private `ArrowUpdate.looseAlong(GameObject, Coord3D, float, DamageType, float, float, float,
  int stun)`; private `ArrowUpdate.stun(GameObject hurt)` (Task 3 gives it the world). In `StunTest`: `FIRE_MAGE`,
  `unarmedRogue()`, `record Fight(DukeGame game, List<GameObject> heroes)`, `fireMage(DungeonSettings, float, float,
  float...)`, `untilStunned(DukeGame, GameObject, int)`.

- [ ] **Step 1: Write the failing tests** — in `StunTest`, replace the imports with:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Hero;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.module.StatusUpdate;
```

add a paragraph to the class comment and the fire mage's name beside `SETTINGS`:

```java
/**
 * A stun: the engine's own {@code DISABLED}, worn for as long as what stunned it says -- no step, no blow and no
 * cast while it lasts, and all three back once it is over.
 *
 * <p>The fire mage's fireball is the one thing that stuns, so it is fought for real: its own brain throws at a Rogue
 * standing in its band, told to pick no fights and with a bow that reaches nothing, so nothing he does ends the fight
 * before it has been had.
 */
class StunTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String FIRE_MAGE = "SkeletonMage";
```

after `modulesOf`:

```java
    /** The shipped units with the Rogue's bow reaching nothing: whatever is hurt here, the fire mage hurt. */
    private static String unarmedRogue() {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text());
    }

    private record Fight(DukeGame game, List<GameObject> heroes) {
    }

    /** The fire mage at {@code (mageX, mageY)}, and a Rogue at each pair of {@code heroXy}, holding his ground. */
    private static Fight fireMage(DungeonSettings settings, float mageX, float mageY, float... heroXy) {
        var arena = Dungeon.world(room(), settings, unarmedRogue());
        var game = arena.game();
        for (int i = 0; i + 1 < heroXy.length; i += 2) {
            game.spawn("Rogue", arena.hero(), heroXy[i], heroXy[i + 1]);
        }
        game.spawn(FIRE_MAGE, arena.dungeon(), mageX, mageY);
        game.runHeadless(1);
        // A player has an index once the game has started, and not before.
        arena.orders().hold(arena.hero().getIndex(), true);
        var heroes = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Rogue")).toList();
        return new Fight(game, heroes);
    }

    /** Runs the fight a frame at a time until {@code hero} is stunned: whether he was, within {@code frames}. */
    private static boolean untilStunned(DukeGame game, GameObject hero, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            if (hero.hasStatus(ObjectStatus.DISABLED)) {
                return true;
            }
        }
        return false;
    }
```

and at the end of the class:

```java
    // ---- the fire mage's fireball ----

    /** As shipped: its fireball stuns for a second. */
    @Test
    void theFireMagesFireballStunsForASecond() {
        assertEquals(30, SETTINGS.skillsFor(FIRE_MAGE).getFirst().stunFrames());
    }

    /**
     * What it strikes stands dazed for exactly its StunFrames: {@code DISABLED} on the frame it lands and for that many
     * frames in all, and free on the next.
     */
    @Test
    void itStunsWhatItStrikesForExactlyItsFrames() {
        var fight = fireMage(SETTINGS, 200f, 150f, 250f, 150f);
        var hero = fight.heroes().getFirst();
        int frames = SETTINGS.skillsFor(FIRE_MAGE).getFirst().stunFrames();

        assertTrue(untilStunned(fight.game(), hero, 150), "nothing it threw in five seconds stunned him");
        int stunned = 1;
        while (stunned <= frames) {
            fight.game().runHeadless(1);
            if (!hero.hasStatus(ObjectStatus.DISABLED)) {
                break;
            }
            stunned++;
        }
        assertEquals(frames, stunned, "stunned " + stunned + " frames against a StunFrames of " + frames);
    }

    /** And whoever its burst caught, on the same frame: two Rogues side by side, and both of them dazed. */
    @Test
    void andWhoeverItsBurstCaught() {
        var fight = fireMage(SETTINGS, 200f, 150f, 250f, 145f, 250f, 155f);

        assertTrue(untilStunned(fight.game(), fight.heroes().get(0), 150), "nothing it threw stunned either");
        assertTrue(fight.heroes().get(1).hasStatus(ObjectStatus.DISABLED),
                "the one beside him was not stunned the frame he was, so its burst stuns nobody");
    }

    /** The same fireball with no StunFrames hurts him, and leaves him free. */
    @Test
    void aSkillWithoutStunFramesLeavesItsVictimFree() {
        var data = Content.data().replace("      StunFrames = 30\n", "");
        assertNotEquals(Content.data(), data, "the premise: the fireball's StunFrames was taken out");
        var settings = DungeonSettings.parse(data);
        var fight = fireMage(settings, 200f, 150f, 250f, 150f);
        var hero = fight.heroes().getFirst();
        var fireball = settings.skillsFor(FIRE_MAGE).getFirst().projectile();

        boolean thrown = false;
        for (int frame = 0; frame < 150; frame++) {
            fight.game().runHeadless(1);
            thrown |= creature(fight.game(), fireball) != null;
            assertFalse(hero.hasStatus(ObjectStatus.DISABLED), "stunned, on frame " + frame);
        }
        assertTrue(thrown, "the premise: it threw its fireball at him");
        assertTrue(hero.getBody().getHealth() < hero.getBody().getMaxHealth(), "and he was hurt");
    }

    /** Only what a skillshot throws carries a stun: written on any other skill, the file is refused. */
    @Test
    void aStunOnAnythingButASkillshotIsRefused() {
        // The Mage's Frost Nova, which comes down on a spot rather than being thrown.
        var data = Content.data().replace("      SlowFrames = 90\n", "      SlowFrames = 90\n      StunFrames = 30\n");
        assertNotEquals(Content.data(), data, "the premise: the nova was given a stun");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("StunFrames"), refused.getMessage());
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.StunTest"`
Expected: FAIL — compilation error, `stunFrames()` is not a method of `Skill`.

- [ ] **Step 3: Give the `Skill` its `StunFrames`** — in `Skill.java`, document it after `summonExperiencePercent`:

```java
 * @param summonExperiencePercent what killing one is worth, as a share of its own kind:
 *     a thing that was never placed on the floor should not be a well to draw from
 * @param stunFrames    how long whoever a {@code SKILLSHOT} hurts stands dazed after —
 *     the one it struck and everyone its burst caught — or zero for a shot that only
 *     hurts. The engine's own {@code DISABLED}, worn on the victim's own timers: its
 *     legs and its weapon stand still under it and its skills refuse. The fire mage's
 *     fireball has one and its ordinary fire does not
 */
```

make it the last component:

```java
        int maxSummoned,
        int summonExperiencePercent,
        int stunFrames) {
```

and carry it in `DEFAULTS` and `ownedBy`:

```java
    static final Skill DEFAULTS = new Skill(null, '\0', SkillEffect.STRIKE, 0f, 0f, 0f, 0f, 0f, 0f,
            0, 0, 0, 0, 0, 90, 0, 4, 0, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, "", 0, 0, 0, 0);
```

```java
                projectileSpeed, heal, healBelowPercent, summons, summonCount, maxSummoned,
                summonExperiencePercent, stunFrames);
```

- [ ] **Step 4: Mend the five skills the tests build by hand** — each gains a last `0`, no stun:

`ManaTest.andACostNeverFallsBelowNothing`:

```java
        var free = new Skill("Mage", 'Q', SkillEffect.STRIKE, 0f, 0f, 0f, 0f, 0f, 0f,
                0, 0, 0, 0, 0, 60, 0, 9, 0, 0, 5, -50, "", "", "", "", 0f, "", "", 0f, 0f, 0, "", 0, 0, 0, 0);
```

`SkillRanksTest.ordinary` and `SkillRanksTest.ultimate`:

```java
        return new Skill("Hero", key, SkillEffect.AREA_DAMAGE, 10f, 1f, 10f, 10f, 0f, 0f,
                0, 0, 0, 0, 0, 60, 0, 4, 0, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, "", 0, 0, 0, 0);
```

```java
        return new Skill("Hero", key, SkillEffect.EMPOWER, 0f, 0f, 0f, 0f, 0f, 0f,
                50, 5, 120, 0, 0, 600, 0, 3, 4, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, "", 0, 0, 0, 0);
```

`SkillTest.skill` and `SkillTest.anUltimateGrowsIntoItsRanks`:

```java
        return new Skill("Rogue", 'Q', SkillEffect.STRIKE, damage, perLevel, 0f, 40f, 0f, 0f,
                0, 0, 0, 0, 0, cooldown, cooldownPerLevel, 4, 0, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, "", 0, 0, 0, 0);
```

```java
        var r = new Skill("Rogue", 'R', SkillEffect.EMPOWER, 0f, 0f, 0f, 0f, 0f, 0f,
                80, 12, 180, 0, 0, 900, -30, 3, 4, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, "", 0, 0, 0, 0);
```

- [ ] **Step 5: The skillshot hands its stun to what it throws** — `SkillBook.apply`, `case SKILLSHOT`:

```java
                // Radius is the burst where it lands, and zero leaves it an arrow:
                // both fly the same way and stop at the first body, and one of them
                // takes the rest of the room with it. StunFrames is how long whoever
                // it hurts stands dazed after, and zero leaves them free.
                if (!Shot.looseAlong(owner, towards, damageOf(skill, level), DamageType.NORMAL,
                        skill.projectile(), speedOf(skill),
                        settings.combat().arrowMuzzleOffset(), skill.range(), skill.radius(),
                        skill.stunFrames())) {
                    return false; // no arrow to throw; the cooldown is not spent
                }
```

`Shot.looseAlong` takes it and passes it on — its documentation, signature and last line:

```java
     * @param blast    how far the burst reaches where it lands; zero for a shot
     *                 that only hurts what it hit
     * @param stun     how long whoever it hurts stands dazed after, in frames; zero
     *                 for a shot that only hurts
     * @return whether one actually left
     */
    public static boolean looseAlong(GameObject shooter, Coord3D towards, float damage,
            DamageType type, String template, float speed, float muzzleOffset, float distance,
            float blast, int stun) {
```

```java
        flight.looseAlong(shooter, towards, damage, type, speed, distance, blast, stun);
```

(`Bow` goes on calling `Shot.loose`, which carries no stun: a creature's ordinary shot never stuns.)

- [ ] **Step 6: The shot stuns what it hurts** — `ArrowUpdate`. Imports:

```java
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rts.module.ExperienceModule;
import uz.dukeengine.rts.module.StatusUpdate;
```

a field after `blastRadius`:

```java
    /**
     * How long whoever this hurts stands dazed after, in frames, or zero for a shot that only hurts: the one it
     * struck and everyone its burst caught alike. Carried from the skill that threw it, as the damage is -- the fire
     * mage's fireball stuns, and its ordinary fire does not.
     */
    private int stunFrames;
```

`looseAlong` takes it:

```java
    void looseAlong(GameObject from, Coord3D towards, float carrying, DamageType type,
            float speed, float distance, float blast, int stun) {
        this.blastRadius = blast;
        this.stunFrames = stun;
```

`strike` stuns what it struck, before the burst:

```java
        victim.getBody().damage(damage, damageType);
        stun(victim);
        splash(world, victim);
```

`splash` stuns each it caught — the end of its loop, and the new method after it:

```java
            caught.getBody().damage(damage, damageType);
            stun(caught);
        }
    }

    /**
     * Leave whoever this hurt standing dazed, if it was thrown to: the engine's own {@code DISABLED} for
     * {@link #stunFrames}, set on the creature's own timers -- so its legs and its weapon stand still under it, its
     * skills refuse (see {@code SkillBook.cast}), it wears off by itself whoever threw it, and a second stun starts
     * the count again rather than adding to it.
     *
     * <p>A creature whose file never asked for a {@code StatusUpdate} is not stunned, as it is not slowed: better
     * that than the game deciding what a creature is made of behind its own file's back.
     */
    private void stun(GameObject hurt) {
        var timers = stunFrames <= 0 || hurt.isEffectivelyDead() ? null : hurt.findModule(StatusUpdate.class);
        if (timers != null) {
            timers.apply(ObjectStatus.DISABLED, stunFrames);
        }
    }
```

- [ ] **Step 7: Refuse a stun anything but a skillshot would carry** — `DungeonSettings.validate()`, in the loop
  over `skills`, right after the `Icon` check:

```java
            require(sayable(skill.icon()),
                    "a skill's Icon may not contain ',' or '|': " + skill.key());
            // A stun rides what a skillshot throws, and nothing else a skill does carries
            // one: written on any other, it would be a number nothing read.
            require(skill.stunFrames() == 0
                            || skill.stunFrames() > 0 && skill.effect() == SkillEffect.SKILLSHOT,
                    skill.heroTemplate() + "'s Skill " + skill.key() + " has StunFrames = " + skill.stunFrames()
                            + ": only a SKILLSHOT stuns, and for no fewer than no frames");
```

- [ ] **Step 8: The fire mage's fireball stuns for a second** — `skeleton_mage.duke`, in its `Q`, after
  `Projectile = SkullFireball`:

```
      Projectile = SkullFireball
      ; And whoever it hurts -- the one it struck and everyone its burst caught --
      ; stands dazed for a second: no step, no blow, no cast. Its ordinary fire
      ; stuns nothing: a stun on every shot of something that throws every two
      ; seconds would hold him still for most of a fight.
      StunFrames = 30
      ; Six seconds between fireballs, with its ordinary fire in between.
```

and the field notes in `rogue.duke` say so, at the end of the `SKILLSHOT` entry:

```
  ;                 instead of clicking on it. Radius is how wide the shot is.
  ;                 Name a StunFrames and whoever it hurts -- the one it struck and
  ;                 everyone its burst caught -- stands dazed that long after.
```

- [ ] **Step 9: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.StunTest" --tests "uz.dukeengine.dungeon.skill.*"`
Expected: PASS — `StunTest` 7 tests, and `ManaTest`, `SkillRanksTest`, `SkillTest` as before.

- [ ] **Step 10: Run the whole suite** — the `Skill` record changed shape and a shot's signature with it.

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/skill/Skill.java src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/java/uz/dukeengine/dungeon/combat/Shot.java src/main/java/uz/dukeengine/dungeon/combat/ArrowUpdate.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/resources/data/units/skeleton_mage.duke src/main/resources/data/units/rogue.duke src/test/java/uz/dukeengine/dungeon/StunTest.java src/test/java/uz/dukeengine/dungeon/skill/ManaTest.java src/test/java/uz/dukeengine/dungeon/skill/SkillRanksTest.java src/test/java/uz/dukeengine/dungeon/skill/SkillTest.java
git commit -m "The fire mage's fireball stuns whoever it hurts, for a second" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Stars over a stunned head, for as long as the stun

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/world/Combat.java` (+ `stunLook`, the last component)
- Modify: `src/main/resources/data/world/world.duke` (`StunLook` at the end of the `Combat` block; the `Stunned`
  effect right after it)
- Modify: `src/main/java/uz/dukeengine/dungeon/combat/ArrowUpdate.java` (constructor; `stun` plays the look)
- Modify: `src/main/java/uz/dukeengine/dungeon/Dungeon.java` (the `ArrowUpdate` registration, ~line 200)
- Modify: `src/main/java/uz/dukeengine/dungeon/Main.java` (`measureLooks`, ~line 157)
- Test: `src/test/java/uz/dukeengine/dungeon/StunTest.java`

**Interfaces:**
- Consumes: `World.effect(String name, GameObject thing)` (engine: an `EffectPlayed` riding the thing, drawing only);
  `Visuals.effectSeconds(String, float)`; Task 2's `ArrowUpdate.stun`, `Skill.stunFrames()`, and `StunTest`'s
  `fireMage`, `untilStunned`.
- Produces: `String Combat.stunLook()` (`@Link(Effect.class)`, blank in `DEFAULTS`); the shipped `StunLook =
  Stunned` and `Effect Stunned`; `public ArrowUpdate(GameObject owner, String stunLook)` in place of
  `ArrowUpdate(GameObject, ModuleData)`; `ArrowUpdate.stun(World world, GameObject hurt)`.

- [ ] **Step 1: Write the failing tests** — `StunTest`'s imports gain five:

```java
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.EffectLayer;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.module.ModuleData;
```

```java
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
```

and at the end of the class:

```java
    // ---- the stars ----

    /** Stars on whoever it stunned: the Combat block's look, played riding him on the frame it landed. */
    @Test
    void theStarsArePlayedOnWhoeverItStunned() {
        var fight = fireMage(SETTINGS, 200f, 150f, 250f, 150f);
        var hero = fight.heroes().getFirst();

        assertTrue(untilStunned(fight.game(), hero, 150), "nothing it threw stunned him");
        var played = fight.game().getSnapshot().events().stream()
                .filter(EffectPlayed.class::isInstance).map(EffectPlayed.class::cast).toList();
        assertTrue(played.stream().anyMatch(effect -> effect.name().equals(SETTINGS.combat().stunLook())
                        && hero.getId().equals(effect.riding())),
                "no " + SETTINGS.combat().stunLook() + " riding him the frame he was stunned: " + played);
    }

    /** They last as long as the longest stun any skill gives, and no layer of them says how long for itself. */
    @Test
    void theStarsLastAsLongAsTheLongestStun() {
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        int longest = SETTINGS.skills().stream().mapToInt(Skill::stunFrames).max().orElse(0);
        assertTrue(longest > 0, "the premise: something stuns");

        assertEquals(longest / (float) GameConstants.LOGICFRAMES_PER_SECOND,
                visuals.getEffectSeconds(SETTINGS.combat().stunLook()), 0.001f);
        var layers = SETTINGS.effectLayers().stream()
                .filter(art -> art.effect().equals(SETTINGS.combat().stunLook())).map(Main::layerOf).toList();
        assertFalse(layers.isEmpty(), "the look a stun is worn in is drawn by nothing");
        for (var layer : layers) {
            assertEquals(EffectLayer.AURA, layer.type(), "a stun is worn, and goes where he goes");
            assertEquals(0f, layer.seconds(), 0.001f, "a layer that says how long it lasts no longer follows the stun");
        }
    }
```

(`getSnapshot().events()` is the frame's own: the client drains the world's events every frame it draws, and shows
what the local hero's side can see — he sees himself.)

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.StunTest"`
Expected: FAIL — compilation error, `stunLook()` is not a method of `Combat`.

- [ ] **Step 3: The Combat block names the look** — `Combat.java`, imports after the package line:

```java
package uz.dukeengine.dungeon.world;

import uz.dukeengine.core.content.Effect;
import uz.dukeengine.core.data.Link;
```

and the record's last component, documented, with its default:

```java
 * @param arrowMuzzleOffset  how far in front of an archer his arrow appears — the bow, not his chest
 * @param stunLook           what a stunned creature wears while it stands dazed: an {@code Effect} played on it
 *     by whatever stunned it, lasting as long as the longest {@code StunFrames} any skill has. Blank for nothing
 */
public record Combat(float skeletonSenseRadius, float skeletonChaseRadius, int skeletonRepathFrames,
        float closeDistance, int heroRepathFrames, float wayAheadProbe, float retreatTurnDegrees,
        int retreatTurns, float summonTurnDegrees, int summonTurns, String arrowTemplate, float arrowSpeed,
        float arrowMuzzleOffset, @Link(Effect.class) String stunLook) {

    /** What a block leaves out. */
    public static final Combat DEFAULTS = new Combat(90f, 150f, 10, 4f, 10, 5f, 30f, 3, 45f, 4, "Arrow",
            260f, 5f, "");
}
```

- [ ] **Step 4: Name it, and draw it** — `world.duke`, the end of the `Combat` block and a new block after it:

```
  ; he turns to shoot, so his heading is the line of the shot.
  ArrowMuzzleOffset = 5

  ; What a stunned creature wears while it stands dazed -- see the Effect below,
  ; and StunFrames on the fire mage's fireball. Blank, and a stun is not drawn.
  StunLook = Stunned
End

; ---------------------------------------------------------------------------
; Stars over a stunned head, for as long as the stun: whatever stuns plays it on
; whoever it stunned (see ArrowUpdate), and Combat's StunLook above names it.
;
; No layer says how long it lasts. The client is told the longest StunFrames any
; skill has (Main.measureLooks), so the number is written once, on the skill, and
; the picture follows it -- as the frost on whoever a nova caught follows its
; SlowFrames.
; ---------------------------------------------------------------------------
Effect
  Name = Stunned

  Layers = [
    ; A ring of light turning over his head, as a dazed man's world turns: above a
    ; hero's twelve units and clear of the mage's hat, and blinking through the
    ; last sixth of its run, so the player sees the stun about to lift.
    Layer
      Name = Halo
      Type = AURA
      Texture = kit/effects/particles/twirl_02.png
      Blend = Additive
      Count = 1
      Size = [8, 8]
      Colour = [0xFFF4B0, 0xFFD24A]
      Alpha = [0.6, 0.6]
      FadeIn = 0.05
      FadeOut = 0.15
      Spin = 240
      Height = 15
      TurnJitter = 0
    End,

    ; And the stars on it: a few at a time, each gone in half a second, so the
    ; ring is always being drawn again round wherever he stands.
    Layer
      Name = Stars
      Type = AURA
      Texture = kit/effects/particles/star_04.png
      Blend = Additive
      Count = 6
      Rate = 10
      Radius = 4
      Life = [0.4, 0.6]
      Size = [2.4, 1.2]
      Colour = [0xFFFBE0, 0xFFC830]
      Speed = [0, 0]
      Direction = NONE
      Spin = 180
      Height = 15
    End
  ]
End
```

- [ ] **Step 5: The shot plays it on whoever it stunned** — `ArrowUpdate`: a field after `stunFrames`,

```java
    /** What whoever this stuns wears while dazed: the Combat block's {@code StunLook}; blank for nothing. */
    private final String stunLook;
```

the constructor takes it in place of the data it ignored (`ModuleData` stays imported: `Data` implements it),

```java
    public ArrowUpdate(GameObject owner, String stunLook) {
        super(owner);
        this.stunLook = stunLook == null ? "" : stunLook;
    }
```

the two calls pass the world — in `strike`, `stun(world, victim);` and in `splash`, `stun(world, caught);` — and
`stun` plays the look after setting the status:

```java
    /**
     * Leave whoever this hurt standing dazed, if it was thrown to: the engine's own {@code DISABLED} for
     * {@link #stunFrames}, set on the creature's own timers -- so its legs and its weapon stand still under it, its
     * skills refuse (see {@code SkillBook.cast}), it wears off by itself whoever threw it, and a second stun starts
     * the count again rather than adding to it. And the stars over its head, riding it for as long: the client is
     * told how long by {@code Main.measureLooks}.
     *
     * <p>A creature whose file never asked for a {@code StatusUpdate} is not stunned, as it is not slowed: better
     * that than the game deciding what a creature is made of behind its own file's back.
     */
    private void stun(World world, GameObject hurt) {
        var timers = stunFrames <= 0 || hurt.isEffectivelyDead() ? null : hurt.findModule(StatusUpdate.class);
        if (timers == null) {
            return;
        }
        timers.apply(ObjectStatus.DISABLED, stunFrames);
        if (!stunLook.isBlank()) {
            world.effect(stunLook, hurt);
        }
    }
```

`Dungeon.java` builds it with the settings' look — replace `factory.register(ArrowUpdate.Data.class,
ArrowUpdate::new);` with:

```java
                    // And what a creature a shot stuns wears while it stands dazed -- see ArrowUpdate.stun.
                    factory.register(ArrowUpdate.Data.class,
                            (owner, data) -> new ArrowUpdate(owner, settings.combat().stunLook()));
```

- [ ] **Step 6: Give the stars their seconds** — `Main.measureLooks`: a paragraph at the end of its Javadoc,

```java
     * too -- a meteor's falling mark takes the same wind-up to come down, and a
     * fireball's blast is as wide as the skill that threw it.
     *
     * <p>And the stars over a stunned head last as long as the longest stun any skill
     * gives: the look is the Combat block's, one for every stun, played on whoever
     * was stunned rather than where a skill went off.
     */
```

and after its loop over the skills, before the method's closing brace:

```java
                if (skill.effect() == uz.dukeengine.dungeon.skill.SkillEffect.METEOR
                        && skill.windUpFrames() > 0) {
                    visuals.effectSeconds(carried, skill.windUpFrames() / perSecond);
                }
            }
        }
        int longestStun = 0;
        for (var skill : settings.skills()) {
            longestStun = Math.max(longestStun, skill.stunFrames());
        }
        if (longestStun > 0 && !settings.combat().stunLook().isBlank()) {
            visuals.effectSeconds(settings.combat().stunLook(), longestStun / perSecond);
        }
    }
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.StunTest" --tests "uz.dukeengine.dungeon.DungeonEffectLayerTest"`
Expected: PASS — `StunTest` 9 tests; `DungeonEffectLayerTest` finds the `Stunned` layers of a kind the client
draws, from textures that are there.

- [ ] **Step 8: Run the whole suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 9: Look at it** — the owner looks, in the game (`./gradlew run`), at a hero the fire mage's fireball
  catches: the halo and stars over his head for the second he stands still, and gone as he moves again. `Height`,
  `Size` and `Spin` in the `Stunned` block are the numbers to tune. Nothing to run here beyond the suite: report what
  to look at.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/world/Combat.java src/main/resources/data/world/world.duke src/main/java/uz/dukeengine/dungeon/combat/ArrowUpdate.java src/main/java/uz/dukeengine/dungeon/Dungeon.java src/main/java/uz/dukeengine/dungeon/Main.java src/test/java/uz/dukeengine/dungeon/StunTest.java
git commit -m "Stars over a stunned head, for as long as the stun" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Every boss gets back a quarter of what its blows deal

**Superseded by Task 6:** This task built lifesteal as a module; at the owner's word, Task 6 replaced it with a passive
skill in the skill system — `SkillEffect.LIFESTEAL`, never cast, its share the skill's `BoostPercent`, heard the same
way. The module and its tests stand below as history. Task 6's implementation applies instead.

**Files:**
- Create: `src/main/java/uz/dukeengine/dungeon/combat/Lifesteal.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/combat/Swing.java` (class comment; `launch`)
- Modify: `src/main/java/uz/dukeengine/dungeon/combat/ArrowUpdate.java` (`strike`, `splash`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/Content.java` (import; `MODULES`, ~line 98)
- Modify: `src/main/java/uz/dukeengine/dungeon/Dungeon.java` (import; after the `Swing` registration, ~line 212)
- Modify: `src/main/resources/data/units/warden.duke`, `reaper.duke`, `necromancer.duke`, `champion.duke` (a module
  right after `Swing`)
- Test: `src/test/java/uz/dukeengine/dungeon/LifestealTest.java` (**new**)

**Interfaces:**
- Consumes: `ProjectileLauncher.launch(GameObject, GameObject, float damage, DamageType)` — the engine lands the
  declined blow's `damage` on the victim, scaled by its armour; `BodyModule.heal(float)`, clamped to the maximum;
  Task 3's `ArrowUpdate.strike` and `splash`.
- Produces: `public final class Lifesteal` with `public record Data(int percent)`, `Lifesteal(GameObject, Data)`,
  `public static void drink(GameObject striker, float dealt)`; `ArrowUpdate.splash(World, GameObject struck,
  GameObject archer)`.

- [ ] **Step 1: Write the failing test**

```java
package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.combat.Lifesteal;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.game.DukeGame;

/**
 * Every boss drinks from its blows: a quarter of what each deals comes back to it as health, never above its
 * maximum -- a swing where it lands, a shot where it arrives -- and nothing else down here does.
 *
 * <p>Fought for real, against a Rogue told to pick no fights and with a bow that reaches nothing, so what the boss
 * gains is only ever what it drank; the Necromancer's own mending is stilled for the same reason.
 */
class LifestealTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

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

    /** The shipped units, with the Rogue's bow reaching nothing and the Necromancer mending nothing on its own. */
    private static String units() {
        var rogue = ShippedBlock.of("Rogue");
        var necromancer = ShippedBlock.of("Necromancer");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text())
                .replace(necromancer.text(), necromancer.with("HealPerSecond", 0).text());
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private record Duel(DukeGame game, GameObject hero, GameObject monster) {
    }

    /**
     * The Rogue, and {@code monster} {@code gap} further along the row, inside its own reach -- left with
     * {@code share} of its health, so what it drinks has room to show.
     */
    private static Duel duel(String monster, float gap, float share) {
        var arena = Dungeon.world(room(), SETTINGS, units());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(monster, arena.dungeon(), 150f + gap, 150f);
        game.runHeadless(1);
        // A player has an index once the game has started, and not before.
        arena.orders().hold(arena.hero().getIndex(), true);
        var it = creature(game, monster);
        it.getBody().setHealth(it.getBody().getMaxHealth() * share);
        return new Duel(game, creature(game, "Rogue"), it);
    }

    /** What a unit's block is built from, as the shipped files write it. */
    private static List<ModuleData> modulesOf(String unit) {
        for (var record : Content.records(Content.units(), "units")) {
            if (record instanceof Monster monster && monster.name().equals(unit)) {
                return monster.modules();
            }
        }
        throw new AssertionError("the shipped files have no monster called " + unit);
    }

    /** What the hero lost and what the monster gained, over {@code frames} of the fight. */
    private static float[] lostAndGained(Duel duel, int frames) {
        float hero = duel.hero().getBody().getHealth();
        float monster = duel.monster().getBody().getHealth();
        duel.game().runHeadless(frames);
        return new float[] {hero - duel.hero().getBody().getHealth(), duel.monster().getBody().getHealth() - monster};
    }

    /** The Warden's swing: whatever it took from him, it has a quarter of back. */
    @Test
    void aBossGetsBackAQuarterOfWhatItsBlowsDeal() {
        var change = lostAndGained(duel("Warden", 20f, 0.5f), 90);

        assertTrue(change[0] > 0f, "the premise: in three seconds at arm's length it struck him");
        assertEquals(change[0] / 4f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** The Necromancer's shot does the same, where it arrives. */
    @Test
    void itsShotDoesTheSame() {
        var change = lostAndGained(duel("Necromancer", 45f, 0.5f), 150);

        assertTrue(change[0] > 0f, "the premise: in five seconds in its reach, something it threw reached him");
        assertEquals(change[0] / 4f, change[1], 0.001f, "it took " + change[0] + " and got back " + change[1]);
    }

    /** Never above its maximum: whole, it stays whole, and no more. */
    @Test
    void neverAboveItsMaximum() {
        var duel = duel("Warden", 20f, 1f);
        var change = lostAndGained(duel, 90);

        assertTrue(change[0] > 0f, "the premise: it struck him");
        assertEquals(duel.monster().getBody().getMaxHealth(), duel.monster().getBody().getHealth(), 0.001f);
    }

    /** A creature without it gets nothing back, however hard it hits. */
    @Test
    void aCreatureWithoutItGetsNothing() {
        var change = lostAndGained(duel("Skeleton", 12f, 0.5f), 90);

        assertTrue(change[0] > 0f, "the premise: it struck him");
        assertEquals(0f, change[1], 0.001f, "a skeleton drank from its blows");
    }

    /** As shipped: every boss of the descent drinks a quarter of every blow. */
    @Test
    void everyBossDrinksAQuarter() {
        assertTrue(SETTINGS.finalDepth() > 0, "the premise: the descent has its bosses");
        for (int depth = 1; depth <= SETTINGS.finalDepth(); depth++) {
            var boss = SETTINGS.bossKindAt(depth);
            assertTrue(modulesOf(boss).contains(new Lifesteal.Data(25)), boss + " does not drink a quarter");
        }
    }
}
```

(What the hero loses is the blow's own figure: a Rogue spawned into an arena has no `HeroProgress` behind him, so
no armour and no mending. The Warden hits 16 and gets 4; the Necromancer's fire 20, and 5.)

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.LifestealTest"`
Expected: FAIL — compilation error, no class `uz.dukeengine.dungeon.combat.Lifesteal`.

- [ ] **Step 3: Write `combat/Lifesteal.java`**

```java
package uz.dukeengine.dungeon.combat;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;

/**
 * A creature that drinks from its blows: every blow it lands gives it back {@code Percent} of what the blow dealt, as
 * health, never above its maximum. Every boss carries it, so a fight with one that goes on is lost faster than its
 * bar says.
 *
 * <p>It is told rather than listening. A blow lands in two places in this game -- a swing where the striker stands,
 * which {@link Swing} hears the moment before the weapon lands it, and a shot when it arrives, in
 * {@link ArrowUpdate} -- and each hands the blow's figure here. The figure is what the blow dealt, not what the victim
 * had left: a kill is no special case.
 *
 * <p>Deterministic: one multiplication of the blow's own figure by a whole percentage, on the simulation's frame, and
 * the body's own {@code heal}, which the client shows as it shows every other.
 */
@ModuleGroup({ModuleGroups.COMBAT, ModuleGroups.BODY})
public final class Lifesteal extends Module {

    /** @param percent what share of each blow's damage comes back to it as health */
    public record Data(int percent) implements ModuleData {
    }

    private final Data data;

    public Lifesteal(GameObject owner, Data data) {
        super(owner);
        this.data = data;
    }

    /**
     * {@code striker} landed a blow worth {@code dealt}: if it drinks from its blows, and is still standing to, it
     * gets its share back. Nobody, the dead, and a creature without the module get nothing.
     */
    public static void drink(GameObject striker, float dealt) {
        var thirst = striker == null ? null : striker.findModule(Lifesteal.class);
        if (thirst == null || dealt <= 0f || striker.isEffectivelyDead() || striker.getBody() == null) {
            return;
        }
        striker.getBody().heal(dealt * thirst.data.percent() / 100f);
    }
}
```

- [ ] **Step 4: Tell it of a swing** — `Swing.java`, the first line of its class comment:

```java
/**
 * Remembers the frame this creature last struck a blow, and lets it drink from the blow if it does -- see
 * {@link Lifesteal}.
 *
```

and `launch`:

```java
    @Override
    public boolean launch(GameObject striker, GameObject victim, float damage, DamageType type) {
        var world = striker.getWorld();
        if (world != null) {
            struckOn = world.getFrame();
        }
        // Declined, the weapon lands it where it stands, this frame: a blow landed, and its
        // striker drinks from it if it does.
        // ponytail: on a creature whose Swing stands before its Bow this is heard as the shot
        // leaves as well as where it lands (ArrowUpdate) -- no boss is built so; skip it here
        // for a striker with a Bow when one is.
        Lifesteal.drink(striker, damage);
        return false; // nothing flies; the weapon lands it where it stands
    }
```

- [ ] **Step 5: And of a shot** — `ArrowUpdate.strike` finds the archer before the burst and hands him to it:

```java
    private void strike(World world, GameObject victim) {
        victim.getBody().damage(damage, damageType);
        stun(world, victim);
        // Every blow it lands, this one and each its burst deals, its archer drinks from if he
        // does -- see Lifesteal.
        var archer = world.findObject(shooter);
        Lifesteal.drink(archer, damage);
        splash(world, victim, archer);
        if (victim.isEffectivelyDead()) {
            var earned = victim.findModule(ExperienceModule.class);
            var his = archer == null ? null : archer.findModule(ExperienceModule.class);
            if (his != null && earned != null) {
                his.addExperience(earned.getExperienceValue());
            }
        }
        getOwner().markDestroyed();
    }
```

and `splash` takes him:

```java
    private void splash(World world, GameObject struck, GameObject archer) {
```

```java
            caught.getBody().damage(damage, damageType);
            Lifesteal.drink(archer, damage);
            stun(world, caught);
```

- [ ] **Step 6: Register it** — `Content.java`, the import beside `Swing`'s and the word in `MODULES`:

```java
import uz.dukeengine.dungeon.combat.FallingUpdate;
import uz.dukeengine.dungeon.combat.Lifesteal;
import uz.dukeengine.dungeon.combat.Swing;
```

```java
                    SummoningUpdate.Data.class, Swing.Data.class, Lifesteal.Data.class, GroundItem.Data.class,
```

`Dungeon.java`, the import beside `Swing`'s and, right after `factory.register(Swing.Data.class, Swing::new);`:

```java
import uz.dukeengine.dungeon.combat.EyesOnly;
import uz.dukeengine.dungeon.combat.Lifesteal;
import uz.dukeengine.dungeon.combat.Swing;
```

```java
                    factory.register(Swing.Data.class, Swing::new);
                    // And a boss drinks from the blows it lands. See Lifesteal.
                    factory.register(Lifesteal.Data.class, Lifesteal::new);
```

- [ ] **Step 7: Every boss carries it** — `warden.duke`, right after its `Swing` module:

```
    ; instead of walking off mid-blow. It launches nothing -- see Swing.
    Swing
    End,
    ; It drinks from its blows, as every boss does: a quarter of what each one
    ; deals comes back to it as health, never above its maximum -- a swing where
    ; it lands, a shot where it arrives. A long fight with a boss is lost faster
    ; than its bar says. See Lifesteal.
    Lifesteal
      Percent = 25
    End,
```

and `reaper.duke`, `necromancer.duke`, `champion.duke`, each right after its own `Swing` / `End,` pair (the
Necromancer's `Bow` stands before its `Swing` and takes every shot, so its blows are drunk where they arrive):

```
    Swing
    End,
    ; A quarter of every blow back, as every boss has -- see the Warden's.
    Lifesteal
      Percent = 25
    End,
```

- [ ] **Step 8: Run the test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.LifestealTest"`
Expected: PASS, 5 tests.

- [ ] **Step 9: Run the whole suite** — `EveryModuleIsGroupedTest` finds `Lifesteal` in `Combat` and `Body`.

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/combat/Lifesteal.java src/main/java/uz/dukeengine/dungeon/combat/Swing.java src/main/java/uz/dukeengine/dungeon/combat/ArrowUpdate.java src/main/java/uz/dukeengine/dungeon/content/Content.java src/main/java/uz/dukeengine/dungeon/Dungeon.java src/main/resources/data/units/warden.duke src/main/resources/data/units/reaper.duke src/main/resources/data/units/necromancer.duke src/main/resources/data/units/champion.duke src/test/java/uz/dukeengine/dungeon/LifestealTest.java
git commit -m "Every boss gets back a quarter of what its blows deal" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The summoner raises two swordsmen and two archers a cast

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/Skill.java` (`summons` a map, `summonCount` gone)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java` (`summon`, ~line 1025)
- Modify: `src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java` (`SUMMON`'s Javadoc, ~line 158)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate()`, `case SUMMON`, ~line 695)
- Modify: `src/main/resources/data/units/skeleton_summoner.duke`, `rogue.duke` (the notes on `SUMMON`)
- Modify tests: `.../skill/ManaTest.java`, `.../skill/SkillRanksTest.java`, `.../skill/SkillTest.java` (the five skills
  built by hand, and an import each)
- Test: `src/test/java/uz/dukeengine/dungeon/ai/MonsterSummoningTest.java`

**Interfaces:**
- Consumes: the record reader's maps, `[Key = value, ...]` read into an insertion-ordered map as `BossGuards` is,
  whose refusal of a single word names the form (`'Summons' is a map: write it Summons = [key = value, key =
  value]`); `Summoning.spots(World, GameObject, Coord3D, float distance, int count, float apart, float, int)`,
  unchanged; `SummoningUpdate.open(GameObject, String creature, int, int, int)`, unchanged.
- Produces: `Map<String, Integer> Skill.summons()` in place of `String summons()` and `int summonCount()`; the
  component order `..., heal, healBelowPercent, summons, maxSummoned, summonExperiencePercent, stunFrames`.

- [ ] **Step 1: Write the failing tests** — `MonsterSummoningTest`. The imports:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.DataException;
import uz.dukeengine.core.math.Coord3D;
```

the class comment's first sentence, and two names beside `SUMMONER`:

```java
/**
 * A monster that calls up more of them: which, how many, where, for how long, what they
 * are worth, and never past its ceiling.
```

```java
    private static final String SUMMONER = "SkeletonSummoner";
    /** What the shipped summoner calls up first, and second. */
    private static final String SWORDSMAN = "Skeleton";
    private static final String ARCHER = "Stalker";
```

`summoningWith` takes the kinds as the file writes them, in place of a count:

```java
    /** The shipped summoner, with its summoning's numbers changed: {@code summons} as the file writes it. */
    private static DungeonSettings summoningWith(String summons, int most, int lasts, int percent,
            int cooldown) {
        return DungeonSettings.parse(ShippedBlock.of(SUMMONER).with("Summons", summons)
                .with("MaxSummoned", most).with("DurationFrames", lasts)
                .with("SummonExperiencePercent", percent).with("CooldownFrames", cooldown).text());
    }
```

a helper before `standing`:

```java
    /** The kind of each thing it called up, in the order they rose. */
    private static List<String> kindsOf(DukeGame game, List<Rising> risen) {
        return risen.stream().map(one -> game.getLogic()
                .findObject(new uz.dukeengine.core.thing.ObjectId(one.id())).getTemplate().name()).toList();
    }
```

`itCallsUpItsCountRoundItselfTowardHimFirst` gives way to two tests:

```java
    /** As shipped: two swordsmen and then two archers a cast, and four of its own at most. */
    @Test
    void theShippedSummonerCallsUpTwoSwordsmenAndTwoArchers() {
        assertEquals(List.of(Map.entry(SWORDSMAN, 2), Map.entry(ARCHER, 2)),
                List.copyOf(summoning().summons().entrySet()));
        assertEquals(4, summoning().maxSummoned());
    }

    /** One cast calls up every kind it names, as many of each, the first kind first, round itself toward him. */
    @Test
    void itCallsUpEachKindItNamesRoundItselfTowardHimFirst() {
        var circle = circle(SETTINGS);
        var from = circle.summoner().getPosition();

        var risen = risings(circle.game(), oneSummoning());

        assertEquals(List.of(SWORDSMAN, SWORDSMAN, ARCHER, ARCHER), kindsOf(circle.game(), risen),
                "one casting: " + risen);
        for (var one : risen) {
            float away = (float) Math.hypot(one.at().x() - from.x(), one.at().y() - from.y());
            assertEquals(summoning().radius(), away, 0.5f, "each rises its Radius from it");
        }
        assertTrue(risen.get(0).at().x() < from.x(), "and the first on his side of it: " + risen);
    }
```

the counts the other tests passed become kinds — in `howManyRiseIsTheFiles`,
`nothingRisesInStoneOrOutOfItsSight`, `whatItCallsUpFallsDownWhenItsTimeIsOut`,
`whatItCallsUpWasFoundAsDeepAsItsCaller` and `playedOut`:

```java
        var three = circle(summoningWith("[Skeleton = 3]", 6, 600, 0, 3000));
```

```java
        var circle = circle(summoningWith("[Skeleton = 4]", 4, 3000, 0, 3000), corridor(), at(14), at(10));
```

```java
        var circle = circle(summoningWith("[Skeleton = 2]", 4, lasts, 0, 3000));
```

```java
        var circle = circle(summoningWith("[Skeleton = 2]", 4, 3000, 0, 60));
        var before = new HashSet<Integer>();
```

```java
        var circle = circle(summoningWith("[Skeleton = 2, Stalker = 2]", 4, 200, 0, 120));
```

the ceiling is met by a cast asking more than it may have:

```java
    /**
     * Quick to cast and slow to fall down, so the only thing that stops it is its ceiling -- which a cast asking
     * five reaches at once, calling up the first four of them.
     */
    @Test
    void itNeverHasMoreStandingThanItsCeiling() {
        var circle = circle(summoningWith("[Skeleton = 3, Stalker = 2]", 4, 3000, 0, 60));
```

(the rest of that test as it is), and `whatItCallsUpIsWorthTheShareTheFileSays` names its kind:

```java
            var arena = Dungeon.world(room(), summoningWith("[Skeleton = 2]", 4, 3000, percent, 3000));
            var game = arena.game();
            game.spawn("Rogue", arena.hero(), 150f, ROW);
            game.spawn(SUMMONER, arena.dungeon(), 200f, ROW);
            // One placed on the floor rather than called up, far off in a corner: what its
            // own kind is worth.
            game.spawn(SWORDSMAN, arena.dungeon(), at(55), at(35));
            game.runHeadless(1);
            arena.orders().hold(arena.hero().getIndex(), true);
            int whole = first(game, SWORDSMAN)
                    .findModule(ExperienceModule.class).getExperienceValue();
```

A test before `shutInRockThereIsNowhereToCallThemUp`:

```java
    /** Where the floor has room for fewer than a cast asks, as many rise as fit, the kinds written first. */
    @Test
    void whereFewerFitTheKindsWrittenFirstRise() {
        var circle = circle(SETTINGS, corridor(), at(14), at(10));

        var risen = risings(circle.game(), oneSummoning());

        assertEquals(List.of(SWORDSMAN, SWORDSMAN), kindsOf(circle.game(), risen),
                "the corridor has room for two, and the swordsmen are written first: " + risen);
    }
```

and one at the end of the class:

```java
    // ---- the old way of writing it ----

    /** One kind and a SummonCount beside it, as the file used to say: refused, and told the new way. */
    @Test
    void theOldSingleSummonsIsRefusedNamingTheNewForm() {
        var old = ShippedBlock.dataWith(SUMMONER, "Summons", SWORDSMAN);

        var refused = assertThrows(DataException.class, () -> DungeonSettings.parse(old));
        assertTrue(refused.getMessage().contains("Summons = ["), refused.getMessage());
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.ai.MonsterSummoningTest"`
Expected: FAIL — compilation error, `entrySet()` is not a method of `String` (`summoning().summons()`).

- [ ] **Step 3: `Summons` becomes kinds and counts** — `Skill.java`: `import java.util.Map;` after the package line,

```java
package uz.dukeengine.dungeon.skill;

import java.util.Map;
import uz.dukeengine.core.data.Clip;
```

its two lines of documentation become one entry,

```java
 * @param summons       what a {@code SUMMON} calls up, each kind and how many of it, in
 *     the order written: {@code [Skeleton = 2, Stalker = 2]} is two swordsmen and two
 *     archers a cast, and where the floor has room for fewer, the swordsmen are the
 *     ones that rise. One cast calls up the whole of it
```

its two components one,

```java
        @Link(Monster.class) Map<String, Integer> summons,
        int maxSummoned,
```

and `DEFAULTS` and `ownedBy` follow:

```java
    static final Skill DEFAULTS = new Skill(null, '\0', SkillEffect.STRIKE, 0f, 0f, 0f, 0f, 0f, 0f,
            0, 0, 0, 0, 0, 90, 0, 4, 0, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, Map.of(), 0, 0, 0);
```

```java
                projectileSpeed, heal, healBelowPercent, summons, maxSummoned,
                summonExperiencePercent, stunFrames);
```

- [ ] **Step 4: Mend the five skills the tests build by hand** — each `"", 0, 0, 0, 0)` tail (no summons, no count,
  no ceiling, no share, no stun) becomes `Map.of(), 0, 0, 0)`, and each file imports `java.util.Map`:

`ManaTest` — the import before `import org.junit.jupiter.api.Test;`, and `andACostNeverFallsBelowNothing`:

```java
import java.util.Map;
import org.junit.jupiter.api.Test;
```

```java
        var free = new Skill("Mage", 'Q', SkillEffect.STRIKE, 0f, 0f, 0f, 0f, 0f, 0f,
                0, 0, 0, 0, 0, 60, 0, 9, 0, 0, 5, -50, "", "", "", "", 0f, "", "", 0f, 0f, 0, Map.of(), 0, 0, 0);
```

`SkillRanksTest` — the import after `import java.util.List;`, and `ordinary` and `ultimate`:

```java
import java.util.List;
import java.util.Map;
```

```java
        return new Skill("Hero", key, SkillEffect.AREA_DAMAGE, 10f, 1f, 10f, 10f, 0f, 0f,
                0, 0, 0, 0, 0, 60, 0, 4, 0, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, Map.of(), 0, 0, 0);
```

```java
        return new Skill("Hero", key, SkillEffect.EMPOWER, 0f, 0f, 0f, 0f, 0f, 0f,
                50, 5, 120, 0, 0, 600, 0, 3, 4, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, Map.of(), 0, 0, 0);
```

`SkillTest` — the import before `import org.junit.jupiter.api.Test;`, and `skill` and `anUltimateGrowsIntoItsRanks`:

```java
import java.util.Map;
import org.junit.jupiter.api.Test;
```

```java
        return new Skill("Rogue", 'Q', SkillEffect.STRIKE, damage, perLevel, 0f, 40f, 0f, 0f,
                0, 0, 0, 0, 0, cooldown, cooldownPerLevel, 4, 0, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, Map.of(), 0, 0, 0);
```

```java
        var r = new Skill("Rogue", 'R', SkillEffect.EMPOWER, 0f, 0f, 0f, 0f, 0f, 0f,
                80, 12, 180, 0, 0, 900, -30, 3, 4, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0, Map.of(), 0, 0, 0);
```

- [ ] **Step 5: One cast calls up every kind** — replace `SkillBook.summon`, Javadoc and all, with:

```java
    /**
     * Open rifts for what it calls up: every kind its {@code Summons} names, as many of
     * each as it says -- as many as there is room for under its ceiling, and floor round
     * it to open them on. See {@link Summoning} for where.
     *
     * <p>The kinds take the rifts in the order the file wrote them, so where there is
     * room for fewer than the cast asks, the first kinds are the ones that rise: two
     * swordsmen before two archers.
     *
     * @return whether one opened at all; false leaves the cooldown unspent
     */
    private boolean summon(GameObject owner, World world, Skill skill, Coord3D towards) {
        var rising = new java.util.ArrayList<String>();
        float body = 0f;
        for (var kind : skill.summons().entrySet()) {
            var creature = world.findTemplate(kind.getKey());
            if (creature == null) {
                continue;
            }
            for (int one = 0; one < kind.getValue(); one++) {
                rising.add(kind.getKey());
            }
            body = Math.max(body, uz.dukeengine.core.thing.Solid.of(creature).footprintRadius());
        }
        int room = Math.min(rising.size(), skill.maxSummoned() - summonedStanding());
        var rift = skill.hasProjectile() ? world.findTemplate(skill.projectile()) : null;
        if (room <= 0 || rift == null) {
            return false;
        }
        // Two that rise together stand a body apart -- the widest body of what rises.
        var spots = Summoning.spots(world, owner, towards, skill.radius(), room, 2f * body,
                settings.combat().summonTurnDegrees(), settings.combat().summonTurns());
        int opened = 0;
        for (var spot : spots) {
            var opening = world.spawn(rift, spot, owner.getPlayerIndex());
            var summoning = opening.findModule(SummoningUpdate.class);
            if (summoning == null) {
                opening.markDestroyed(); // the template exists but is not a rift
                continue;
            }
            summoning.open(owner, rising.get(opened), skill.durationFrames(),
                    skill.summonExperiencePercent(), skill.windUpFrames());
            summoned.add(opening.getId());
            opened++;
        }
        if (opened == 0) {
            return false;
        }
        if (towards != null) {
            Facing.turnToward(owner, towards);
        }
        world.post(new WeaponFired(world.getFrame(), owner.getId(), null,
                owner.getPosition(), spots.get(0)));
        return true;
    }
```

- [ ] **Step 6: What a file may say** — `DungeonSettings.validate()`, the first check in `case SUMMON`:

```java
                case SUMMON -> {
                    var name = skill.heroTemplate() + "'s Skill " + skill.key();
                    require(!skill.summons().isEmpty() && skill.maxSummoned() >= 1
                                    && skill.summons().values().stream().allMatch(count -> count >= 1),
                            name + " calls up nothing: it needs Summons = [Kind = count, ...], each at least one,"
                                    + " and a MaxSummoned");
```

(the three checks after it stay as they are).

- [ ] **Step 7: Say so where the effect is described** — `SkillEffect.SUMMON`, the paragraph after its first line:

```java
     * <p>A rift opens for every creature {@code Summons} names -- two for
     * {@code [Skeleton = 2]}, four for {@code [Skeleton = 2, Stalker = 2]} --
     * {@code Radius} away, toward where it was aimed first and then turned aside in a
     * fixed order, on open floor the caster can see -- never in stone, on another
     * storey or on somebody; see {@link Summoning}. Each is a thing in the world, as a
     * meteor's mark is, and its creature climbs out of it {@code WindUpFrames} later,
     * the kinds taking the rifts in the order written. What climbs out lasts
     * {@code DurationFrames} and then falls down, is worth {@code SummonExperiencePercent}
     * of its own kind, and hits as hard as the depth made its caller hit.
```

and the field notes in `rogue.duke`, the `SUMMON` entry:

```
  ;   SUMMON        a monster's, so far: open a Projectile round you for every creature
  ;                 Summons names -- [Skeleton = 2, Stalker = 2] is four, the swordsmen
  ;                 first -- Radius away and toward where you point first, and each
  ;                 climbs out of its own WindUpFrames later. They last DurationFrames,
  ;                 are worth SummonExperiencePercent of their own kind, and no more
  ;                 than MaxSummoned of one caster's stand at once.
```

- [ ] **Step 8: The summoner's four** — `skeleton_summoner.duke`. Its first comment:

```
; The purple one, and it brings more of them. Frail and slow like the other two, with a
; mote of dark for its ordinary shot, and its skill opens rifts that swordsmen and
; archers climb out of -- see Monster SkeletonSummoner and the Q skill of the
; SkeletonSummoner below.
```

in the comment over its behaviour, `that ordinary skeletons climb out of` becomes:

```
  ; round itself that skeletons and stalkers climb out of -- toward him first. They last a
```

over `MaxPerRoom`:

```
  ; One to a room: with its MaxSummoned that is four called up a room at most.
```

and its `Q`, from its first comment to `MaxSummoned`:

```
      ; Rifts in the floor round it, and one of its own out of each: toward him first
      ; and then turned aside -- see SummonTurnDegrees in the Combat section -- and only
      ; on open floor it can see.
      Effect = SUMMON
      ; Two swordsmen and two archers a cast, in that order -- where the floor has
      ; room for fewer, the swordsmen are the ones that rise -- and never more than
      ; one cast's worth of its own standing at once: a room with a summoner in it is
      ; a fight that grows, with a ceiling on how far.
      Summons = [Skeleton = 2, Stalker = 2]
      MaxSummoned = 4
```

and over `SummonExperiencePercent`:

```
      ; Worth nothing: a creature that was never placed on the floor is not a well to
      ; draw experience from.
```

- [ ] **Step 9: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.ai.MonsterSummoningTest" --tests "uz.dukeengine.dungeon.skill.*"`
Expected: PASS — `MonsterSummoningTest` 13 tests, and the `skill` tests as before.

- [ ] **Step 10: Run the whole suite** — the `Skill` record changed shape again.

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/skill/Skill.java src/main/java/uz/dukeengine/dungeon/skill/SkillBook.java src/main/java/uz/dukeengine/dungeon/skill/SkillEffect.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/resources/data/units/skeleton_summoner.duke src/main/resources/data/units/rogue.duke src/test/java/uz/dukeengine/dungeon/ai/MonsterSummoningTest.java src/test/java/uz/dukeengine/dungeon/skill/ManaTest.java src/test/java/uz/dukeengine/dungeon/skill/SkillRanksTest.java src/test/java/uz/dukeengine/dungeon/skill/SkillTest.java
git commit -m "The summoner raises two swordsmen and two archers a cast" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The bosses' lifesteal is a skill of theirs, not a module

Task 4 built lifesteal as a module. Task 6 replaces it with a passive skill — `SkillEffect.LIFESTEAL`, never cast, its
share the skill's `BoostPercent`, heard at Task 4's two hooks (`Swing.launch`, `ArrowUpdate.strike` and `splash`), drunk
by `SkillBook.drink`. The module goes. Every boss carries a `SkillBook` and a Q skill "Qon so'rish" at 25. Two load
rules come with it: a unit whose file lists Modules and gives Skills must carry a `SkillBook`; a passive skill is
refused on a hero (monsters' only, for now).

Commits: f66bfc4, a77e40f.

---

### Task 7: The stars renew with a second stun

The engine's `EffectLayer` can renew an AURA (commit 1bb64fa8, engine 0.7.0). The game's data record `Layer` cannot say
`Renews` (engine request E12), so the game renews exactly the look the Combat block's `StunLook` names, in
`Main.layerOf(art, settings)`; other auras keep the engine's drop. The stars play on the victim as before; stunned
again, they last to the new stun's end.

Commits: f91dab1, c15c628.

---

## Where the spec and the code part

- **"A skill may stun what it hurts"** — only a `SKILLSHOT` carries a stun: it is the one effect whose blow lands
  in `ArrowUpdate`, where the stun is set, and the fire mage's fireball is the one skill that stuns. Any other
  skill with `StunFrames` is refused at load (Task 2) rather than silently ignored; a stunning strike or blast is a
  later piece's to add where that effect lands.
- **"A settings file with the old single `Summons` is refused with a message naming the new form"** — the engine's
  record reader already refuses a single word where a map is read, with `'Summons' is a map: write it Summons =
  [key = value, key = value]`; no game code is written for it. An old file's `SummonCount` line on its own would be
  refused as a field the `Skill` has not got, but in the shipped order `Summons` is read first.
- **Engine request E12 (layer renewal)** — the engine's `EffectLayer.Builder.renews(boolean)` cannot be set from data
  (the Layer record has no such field), so Task 7 sets it at runtime: `Main.layerOf(art, settings)` renews the stun's
  look and leaves others at the engine's default.
- **Lifesteal as a passive skill** — `SkillEffect.LIFESTEAL` is never cast; `SkillBook.cast` refuses it, and a
  monster's brain casts the first of its skills that is not a passive (Task 6's `Monster.skillKey`), so a creature
  whose only skill it is casts nothing. A unit whose file lists `Modules` and gives `Skills` must carry a `SkillBook`
  among them (Task 6 enforces at load); a hero may not carry a passive, for now.
- **Lifesteal "where the blows land"** — `Swing.launch` hears a blow as the weapon lets go of it. For a melee boss
  that is the landing; for a creature whose `Swing` stands before its `Bow` — the three skeleton mages, on purpose —
  it was the throw as well as the arrival, twice the share. Found at the whole-branch review and fixed: `Swing`
  drinks only for a creature without a `Bow`, whose arrows drink where they land (`LifestealTest`).
- **The heal is of the blow's figure before the victim's armour** — `Swing.launch` and `ArrowUpdate` carry what the
  blow was worth, and the victim's body scales it afterwards; the spec's "the blow's damage counts" is read as that
  figure.
- **The stars last the longest `StunFrames` any skill has** — one look for every stun, as the spec says, so a
  shorter stun (none ships) would wear its stars a little past its end.
- **The stars renew with a second stun** — stunned again while they turn, the picture lasts to the new stun's end
  rather than dropping, a game rule applied at load in `Main.layerOf(art, settings)` — the status refreshes, so the
  look must too. Other AURA layers (the knight's Whirlwind, the mages' projectiles) keep the engine's drop.
- **Where the look's name lives** — the spec names the effect `Stunned` but not what names it; it is the Combat
  block's `StunLook`, beside `ArrowTemplate`, so no name is compiled in and a blank one draws nothing.
