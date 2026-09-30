# The key to the keep — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The boss's keep stands on the ground behind a gate that never opens by itself; every floor with a keep sets
the party a mission — kill everything it put outside the keep, take the key the last of them leaves, give it to the
gate, kill the boss — told at the top of the screen, with each hero's lines in a bubble over his head; the key is the
first item with a use, the owner's crown key in every biome; and the boss's four mages stand at the court's corners
on every floor.

**Architecture:** `gen/Keep` loses its storey and its stair (a threshold of its stone takes the stair's cells) and
names the court's corners, where `DungeonGenerator.guard` stands the first four of `DungeonSettings.bossGuards()`.
`run/GateUpdate` keeps only `open()` and the two lines a hero says at it; `loot/ItemErrand` gains the walk up to the
gate and the use of a thing on a thing, reached through two new orders every machine hears as `GameOrder`s
(`PartyOrders.TO_THE_GATE`, `USE`). `run/Mission`, kept per floor by `DungeonRun`, counts the floor's own outside the
keep through a die module, lays the key and names the step; `MissionScreen` draws the step and the heroes' bag notes
on the game's canvas with the bag.

**Tech Stack:** Java 25, JUnit 5, Gradle (`./gradlew test`), `.duke` data read by the engine's record reader,
duke-engine 0.7.0 from the checkout beside this one, Blender 5.2 run headless once for the key's art.

**Spec:** `docs/plan/2026-09-30-key-to-the-keep.md` (piece 3 of `docs/plan/2026-09-30-roadmap.md`; builds on
`docs/plan/2026-09-30-boss-keep.md`)

## Global Constraints

- Lock-step: the simulation's state changes only on the simulation thread (orders, errands, die modules, the run's
  tick), in whole numbers, in a fixed order, with no clock and no hash iteration; `MissionScreen` and `BagScreen`
  only read, on the window's thread — the tracker as one volatile string, a bag's note as one volatile record.
- Names, numbers and words from data, nothing compiled in: every line the player reads is in `world.duke` (`Run`,
  `LootDrops`, the Key's `LootItem`) or `gate.duke` (its `GateUpdate` block), the spec's Uzbek verbatim; the key's
  template and its one look are named in `data/props/key.duke`.
- `../duke-engine` is never edited from here.
- Stay inside this piece: a parallel piece is changing skills, projectiles and `data/units/` — nothing here touches
  `Skill`, `SkillBook`, `ArrowUpdate`, `Swing` or a unit file; the guard's tests hold whatever a unit's `MinDepth` is;
  no edit quotes `world.duke`'s `DropPercent` line, which that piece is changing.
- Each task is committed on its own once its tests pass: message = subject, blank line,
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (two `-m`).
- The owner's `key_crown.glb` is copied, never modified: its copy in `art/models/` is the source, and the game's copy
  is written from that by `art/icons/render_item.py`.

## Files

| File | What it is |
|---|---|
| `src/main/java/uz/dukeengine/dungeon/gen/Keep.java` | `isThreshold` (was `isStair`), no `storeyAt`; + `corners()` |
| `src/main/java/uz/dukeengine/dungeon/gen/Cave.java` | the keep's floor on storey 0; built over its threshold |
| `src/main/java/uz/dukeengine/dungeon/gen/DungeonGenerator.java` | every room on storey 0; the guard at the court's corners |
| `src/main/java/uz/dukeengine/dungeon/gen/GeneratedDungeon.java`, `.../gen/Relief.java` | prose |
| `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` | `bossGuards()` (was `bossGuardsAt`); a keep 7 across at least; `looks()`; the key's checks; `NoUseWord` check |
| `src/main/java/uz/dukeengine/dungeon/content/Content.java` | a `ThemeMonster` block readable outside a theme |
| `src/main/java/uz/dukeengine/dungeon/run/GateUpdate.java` | no reach, no timer: `open()` and its two lines |
| `src/main/java/uz/dukeengine/dungeon/run/ToTheGate.java` | **new** — the order that sends a hero up to the gate |
| `src/main/java/uz/dukeengine/dungeon/run/Mission.java` | **new** — a floor's mission, and its count of the dead |
| `src/main/java/uz/dukeengine/dungeon/run/Spawner.java` | `Placed.gate` |
| `src/main/java/uz/dukeengine/dungeon/run/DungeonRun.java` | the mission, the tracker, the key left where a hero falls |
| `src/main/java/uz/dukeengine/dungeon/loot/ItemUse.java` | **new** — what a thing does when it is used |
| `src/main/java/uz/dukeengine/dungeon/loot/LootKind.java` | + `KEY` |
| `src/main/java/uz/dukeengine/dungeon/loot/Loot.java` | + `use`, `liesAs`; `joins()`, `liesAs(String)` |
| `src/main/java/uz/dukeengine/dungeon/loot/LootBag.java` | a key never joins; `holds(kind)`; the note one volatile value |
| `src/main/java/uz/dukeengine/dungeon/loot/ItemErrand.java` | up to the gate; a thing used on a thing; a thing lies as its own |
| `src/main/java/uz/dukeengine/dungeon/loot/UseItem.java` | **new** — the order that uses a thing on a thing |
| `src/main/java/uz/dukeengine/dungeon/loot/KeyDrop.java` | **new** — the key left where a hero falls |
| `src/main/java/uz/dukeengine/dungeon/loot/GroundItem.java` | `lay` public |
| `src/main/java/uz/dukeengine/dungeon/world/LootItem.java` | + `Use`, `LiesAs` |
| `src/main/java/uz/dukeengine/dungeon/world/LootDrops.java` | + `NoUseWord` |
| `src/main/java/uz/dukeengine/dungeon/world/Run.java` | + the tracker's four words |
| `src/main/java/uz/dukeengine/dungeon/party/PartyOrders.java` | + `TO_THE_GATE`, `USE` |
| `src/main/java/uz/dukeengine/dungeon/Dungeon.java` | the gate registered plainly; the two orders obeyed |
| `src/main/java/uz/dukeengine/dungeon/BagScreen.java` | the gate's click; a left click uses a thing; paints `MissionScreen` |
| `src/main/java/uz/dukeengine/dungeon/MissionScreen.java` | **new** — the tracker, and the bubbles over the heroes |
| `src/main/java/uz/dukeengine/dungeon/Main.java` | a `ThemeMonster` outside any theme is a base look; `looks` package-private |
| `src/main/resources/data/props/gate.duke` | no `Reach`, no `EveryFrames`; the gate's two lines |
| `src/main/resources/data/props/key.duke` | **new** — the `Key` template and its one look |
| `src/main/resources/data/game.duke` | lists `key.duke` |
| `src/main/resources/data/world/world.duke` | the Key item; `NoUseWord`; the tracker's words |
| `src/main/resources/data/world/generation.duke`, `.../themes/keep.duke` | the keep's and the guard's prose |
| `src/main/resources/data/world/hud.duke` | the pointer over the gate |
| `art/models/key_crown.glb` | **new** — the owner's model, copied untouched |
| `art/icons/render_item.py` | **new** — sets a model on its origin and renders its bag picture |
| `src/main/resources/models/props/key/key_crown.glb` | **new** — the game's copy, written by `render_item.py` |
| `src/main/resources/icons/items/key_crown.png` | **new** — the bag picture, written by `render_item.py` |
| `CREDITS.md`, `README.md` | the key's row; the keep's section |

Tests: `gen/KeepShapeTest`, `gen/KeepFloorTest`, `DungeonThemeTest`, `gen/MonsterPlacementTest`,
`content/KeepSettingsTest`, `run/GateTest`,
`KeyArtTest` (new), `DungeonTilesTest`, `loot/LootTest`, `BagScreenTest`, `loot/ItemErrandTest`,
`party/PartyOrdersTest`, `run/MissionTest` (new), `MissionScreenTest` (new).

---

### Task 1: The keep on the ground

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/Keep.java` (class Javadoc lines 9–22; `isStair` 78–81; `holds`
  83–86; `storeyAt` 88–91 deleted; `roadStart` Javadoc 93)
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/Cave.java` (`levels` 134–147; `raise` 510–531)
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/DungeonGenerator.java` (class Javadoc 19–20; storeys 139–145)
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/GeneratedDungeon.java` (`@param keep`, 45–46)
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/Relief.java` (`flatten` Javadoc, 201–204)
- Modify: `src/main/resources/data/world/generation.duke` (the `Keep` block's comment, 115–121)
- Modify: `src/main/resources/data/world/themes/keep.duke` (header comment, 5–9)
- Test: `src/test/java/uz/dukeengine/dungeon/gen/KeepShapeTest.java`, `.../gen/KeepFloorTest.java`,
  `src/test/java/uz/dukeengine/dungeon/DungeonThemeTest.java`

**Interfaces:**
- Produces: `public boolean Keep.isThreshold(int x, int y)` — the three cells a step out from the doorway (what
  `isStair` was); `Keep.holds` covers them. `Keep.isStair` and `Keep.storeyAt` are gone. Every floor cell of a
  generated floor is `0` in `levelMap`; `roomStoreys` is all 0.

- [ ] **Step 1: Write the failing tests**

In `KeepShapeTest`, replace `itsStairIsTheStepOutsideItsDoorwayAndItsRoadLeavesBeyondIt` and
`itsCourtIsInsideItsRingAStoreyUp` with:

```java
    @Test
    void itsThresholdIsTheStepOutsideItsDoorwayAndItsRoadLeavesBeyondIt() {
        for (int x = 13; x <= 15; x++) {
            assertTrue(NORTH.isThreshold(x, 9), "cell " + x + ",9");
        }
        assertFalse(NORTH.isThreshold(12, 9), "beside the threshold is the ground outside");
        assertFalse(NORTH.isThreshold(14, 11), "inside the doorway is the court");
        assertArrayEquals(new int[] {14, 8}, NORTH.roadStart());
    }

    @Test
    void itsCourtIsInsideItsRing() {
        assertTrue(NORTH.isCourt(11, 11));
        assertTrue(NORTH.isCourt(17, 17));
        assertFalse(NORTH.isCourt(10, 14), "the ring is wall");
        assertFalse(NORTH.isCourt(18, 14));
        assertFalse(NORTH.isCourt(14, 10), "the doorway is in the ring, not the court");
    }
```

rename `itHoldsItsSquareAndItsStairAndNothingElse` to `itHoldsItsSquareAndItsThresholdAndNothingElse` and its message
`"its stair"` to `"its threshold"`, and in the last two tests:

```java
        assertTrue(west.isThreshold(9, 14));
```

```java
        assertTrue(east.isThreshold(19, 14), "EAST threshold is x=19");
        assertArrayEquals(new int[] {20, 14}, east.roadStart(), "EAST road starts beyond the threshold");
```

```java
        assertTrue(south.isThreshold(14, 19), "SOUTH threshold is y=19");
        assertArrayEquals(new int[] {14, 20}, south.roadStart(), "SOUTH road starts beyond the threshold");
```

(each replacing the `isStair` line and the `roadStart` line of the same side).

In `KeepFloorTest.everyFloorEndsInAKeepWithItsBossInTheMiddleAndItsGateInTheDoorway`, the line
`|| keep.isStair(m.at().cellX(), m.at().cellY())),` becomes `|| keep.isThreshold(m.at().cellX(), m.at().cellY())),`.
Replace `itStandsAStoreyUpWithAStairToItsDoorway` with:

```java
    /**
     * On the ground: every cell of it on storey 0, as every other floor cell is, and no stair anywhere; its ring of
     * wall whole; and the threshold before its doorway floor, three cells, drawn as the keep.
     */
    @Test
    void itStandsOnTheGroundWithAThresholdBeforeItsDoorway() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var floor = floor(seed);
            var keep = floor.keep();
            var levels = floor.levelMap().strip().split("\n");
            var cells = floor.asciiMap().strip().split("\n");
            int threshold = 0;
            for (int y = 0; y < levels.length; y++) {
                for (int x = 0; x < levels[y].length(); x++) {
                    boolean wall = keep.holds(x, y) && !keep.isCourt(x, y) && !keep.isDoorway(x, y)
                            && !keep.isThreshold(x, y);
                    if (wall) {
                        assertEquals('#', cells[y].charAt(x), "seed " + seed + ": the ring is open at " + x + "," + y);
                    }
                    if (keep.isThreshold(x, y)) {
                        threshold++;
                        assertEquals('.', cells[y].charAt(x), "seed " + seed + ": its threshold is rock at " + x + ","
                                + y);
                        assertTrue(keep.holds(x, y), "seed " + seed + ": its threshold is not drawn as the keep");
                    }
                    assertEquals(cells[y].charAt(x) == '#' ? '#' : '0', levels[y].charAt(x),
                            "seed " + seed + " at " + x + "," + y);
                }
            }
            assertEquals(3, threshold, "seed " + seed + ": a threshold as wide as its doorway");
            assertEquals(0, floor.roomStoreys().get(floor.bossRoom()), "seed " + seed);
        }
    }
```

and in the Javadoc of `itStandsLevelAndNoGroundRoundItIsACliff`, `its stair among them` becomes
`its threshold among them`.

In `DungeonThemeTest.theKeepIsDrawnInStoneWhateverBiomeItStandsIn`, the Javadoc's `its wall, its court, its stair`
becomes `its wall, its court, its threshold`, and the loop body becomes:

```java
            if (keep.isThreshold(x, y)) {
                assertEquals(stone, looked.lookAt(x, y), "its threshold");
                int roadX = x + step[0];
                int roadY = y + step[1];
                assertEquals(SHIPPED.themes().dressedAs(floor.biomes().at(roadX, roadY), 11L, 1).asStatus(),
                        looked.lookAt(roadX, roadY), "the road beyond the threshold is the ground's");
            }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.KeepShapeTest" --tests "uz.dukeengine.dungeon.gen.KeepFloorTest" --tests "uz.dukeengine.dungeon.DungeonThemeTest"`
Expected: FAIL — compilation error, `cannot find symbol: method isThreshold(int,int)` in `Keep`.

- [ ] **Step 3: The keep's shape — `Keep.java`**

The class Javadoc's first two paragraphs become:

```java
/**
 * The boss's keep: a square court on the ground, walled round, at the far end of the floor — a gate in the middle of
 * the side it is approached from, a threshold of the keep's stone before the gate, and a straight road from the
 * threshold to the chamber it hangs off.
 *
 * <p>Built in solid rock once the cave is carved (see {@link #site}), so it takes no floor from anything and cuts no
 * tunnel: whatever was connected stays connected, and the only way into the court is over the threshold and through
 * the gate. Whole numbers and a fixed order throughout, and no dice: a seed names one keep.
```

Replace `isStair`, `holds` and `storeyAt` (lines 78–91) with:

```java
    /**
     * Whether a cell is the threshold: floor a step out from the doorway and as wide as it, drawn in the keep's
     * stone — where the stair stood while the court was a storey up.
     */
    public boolean isThreshold(int x, int y) {
        return isDoorway(x - side.dx, y - side.dy);
    }

    /**
     * Whether a cell belongs to the keep — its square or its threshold: what is drawn as the keep, and strewn with
     * nothing.
     */
    public boolean holds(int x, int y) {
        return x >= walls.x() && y >= walls.y() && x < walls.x() + size() && y < walls.y() + size()
                || isThreshold(x, y);
    }
```

and `roadStart`'s Javadoc becomes `/** Where the road leaves from: beyond the threshold's middle, two cells out from
the gate. */` (one line).

- [ ] **Step 4: Built on the ground — `Cave.java`**

Replace `levels()` with:

```java
    /**
     * The same map in storeys: all of the floor on the one, the keep's court among it, because the ground's height is
     * the relief's now.
     */
    String levels() {
        var text = new StringBuilder(height * (width + 1));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                text.append(cells[y][x] == STONE ? STONE : '0');
            }
            text.append('\n');
        }
        return text.toString();
    }
```

In `raise`'s Javadoc, replace

```java
     * Build {@code keep} into the carved floor: its ring of wall, its court and doorway, the stair before the doorway,
     * and a straight road from beyond the stair to the middle of the chamber it hangs off in {@code rooms}. All of it
```

with

```java
     * Build {@code keep} into the carved floor: its ring of wall, its court and doorway, the threshold before the
     * doorway, and a straight road from beyond the threshold to the middle of the chamber it hangs off in
     * {@code rooms}. All of it
```

and its test line becomes:

```java
                if (keep.isCourt(x, y) || keep.isDoorway(x, y) || keep.isThreshold(x, y)) {
```

- [ ] **Step 5: Every room on the ground — `DungeonGenerator.java`, and the prose**

In the class Javadoc, `a walled court a storey up, built in solid rock` becomes `a walled court on the ground, built in
solid rock`. Replace

```java
        var storeys = new ArrayList<>(Collections.nCopies(chambers.size(), 0));
        if (keep != null) {
            storeys.set(bossRoom, 1);
        }
```

with

```java
        // Every chamber on the ground, the keep's court among them: how high each stands is the relief's.
        var storeys = Collections.nCopies(chambers.size(), 0);
```

(the `return new GeneratedDungeon(...)` below it is unchanged: `List.copyOf(storeys)` takes the new list as it
took the old).

`GeneratedDungeon`, `@param keep`: `the boss's keep — a walled court a storey up, the last of {@code rooms} and the
boss's — or` becomes `the boss's keep — a walled court on the ground, the last of {@code rooms} and the boss's — or`.

`Relief.flatten`'s Javadoc: `— the keep, and its stair —` becomes `— the keep, and its threshold —`.

`generation.duke`, the `Keep` block's comment — replace

```
  ; Every floor of the descent ends in one: a square court a storey above the
  ; ground, walled round, its gate in the middle of the side a straight road and
  ; a stair come up to. Built in solid rock beside the deepest chamber with room
  ; for it, so it cuts nothing off -- see gen/Keep and
  ; docs/plan/2026-09-30-boss-keep.md. The boss waits in its middle and its
  ; guard round it. Stages are cut without one. Leave the block out and the boss
  ; waits in the furthest chamber, open, as it always did.
```

with

```
  ; Every floor of the descent ends in one: a square court on the ground, walled
  ; round a storey high, its gate in the middle of the side a straight road comes
  ; up to, over a threshold of the keep's stone. Built in solid rock beside the
  ; deepest chamber with room for it, so it cuts nothing off -- see gen/Keep,
  ; docs/plan/2026-09-30-boss-keep.md and docs/plan/2026-09-30-key-to-the-keep.md.
  ; The boss waits in its middle and its guard round it. Stages are cut without
  ; one. Leave the block out and the boss waits in the furthest chamber, open, as
  ; it always did.
```

`themes/keep.duke`, the header — replace

```
; caverns were bounded by rock. The wall is a surface, one course a storey, so
; the ring round the court is a wall from both sides and the stair climbs to a
; doorway in it. The numbers are that kit's, measured once and held by
; DungeonTilesTest.
```

with

```
; caverns were bounded by rock. The wall is a surface, one course a storey, so
; the ring round the court is a wall a storey high on both of its faces, the
; doorway in it and a threshold of this stone before the doorway. The numbers
; are that kit's, measured once and held by DungeonTilesTest.
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.KeepShapeTest" --tests "uz.dukeengine.dungeon.gen.KeepFloorTest" --tests "uz.dukeengine.dungeon.DungeonThemeTest"`
Expected: PASS. `theCourtIsReachedOnlyThroughItsDoorway` passes unchanged: the ring is rock and the doorway the one
gap in it, whatever storey the court is on.

- [ ] **Step 7: Run the whole suite**

Run: `./gradlew test`
Expected: PASS — the level map reaches the stage writer, the spawner and every floor test.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/gen/Keep.java src/main/java/uz/dukeengine/dungeon/gen/Cave.java src/main/java/uz/dukeengine/dungeon/gen/DungeonGenerator.java src/main/java/uz/dukeengine/dungeon/gen/GeneratedDungeon.java src/main/java/uz/dukeengine/dungeon/gen/Relief.java src/main/resources/data/world/generation.duke src/main/resources/data/world/themes/keep.duke src/test/java/uz/dukeengine/dungeon/gen/KeepShapeTest.java src/test/java/uz/dukeengine/dungeon/gen/KeepFloorTest.java src/test/java/uz/dukeengine/dungeon/DungeonThemeTest.java
git commit -m "The keep stands on the ground, a threshold of its stone where its stair was" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Four mages at the court's corners, on every floor

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/Keep.java` (+ `corners()`, after `isCourt`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`bossGuardsAt`, ~236–247; the keep's
  size check in `validate`, ~598–600)
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/DungeonGenerator.java` (the guard's call ~119–126; `guard`
  ~344–376)
- Modify: `src/main/resources/data/world/generation.duke` (the `BossGuards` comment, ~148–156)
- Test: `src/test/java/uz/dukeengine/dungeon/gen/KeepShapeTest.java`, `.../gen/KeepFloorTest.java`,
  `.../gen/MonsterPlacementTest.java`, `src/test/java/uz/dukeengine/dungeon/content/KeepSettingsTest.java`

**Interfaces:**
- Consumes: `Keep.isCourt`, `DungeonSettings.bossGuardRing()`.
- Produces: `public List<int[]> Keep.corners()` — the court's corners a cell in from each wall, in the order NW, SE,
  NE, SW (one diagonal, then the other); `public List<DungeonSettings.BossGuard> DungeonSettings.bossGuards()` — the
  file's guard in file order, no `MinDepth` filter (replaces `bossGuardsAt(int)`); a keep is at least 7 across (at 5
  its four corners would be the boss's own cell).

- [ ] **Step 1: Write the failing tests**

`KeepShapeTest`, a new test after `itHoldsItsSquareAndItsThresholdAndNothingElse`:

```java
    /** The boss's first four guards: the court's corners, a cell in from each wall, one diagonal and then the other. */
    @Test
    void itsGuardStandsAtTheCourtsCornersACellInFromEachWall() {
        var corners = NORTH.corners();

        assertArrayEquals(new int[] {12, 12}, corners.get(0));
        assertArrayEquals(new int[] {16, 16}, corners.get(1), "the first two on one diagonal");
        assertArrayEquals(new int[] {16, 12}, corners.get(2));
        assertArrayEquals(new int[] {12, 16}, corners.get(3), "the other two on the other");
        for (var corner : corners) {
            assertTrue(NORTH.isCourt(corner[0] - 1, corner[1] - 1) && NORTH.isCourt(corner[0] + 1, corner[1] + 1),
                    "a cell in from the wall, not against it: " + corner[0] + "," + corner[1]);
        }
    }
```

`KeepFloorTest`: in `theGuardStandsInTheCourt`, `SETTINGS.bossGuardsAt(depth)` becomes `SETTINGS.bossGuards()`; and
a new test after it:

```java
    /**
     * The boss's four mages stand at the court's corners, a cell in from each wall, on every floor: the guard in the
     * file's order, so the healers stand at one diagonal and the summoners at the other.
     */
    @Test
    void theGuardStandsAtTheCourtsCornersOnEveryFloor() {
        var named = new java.util.ArrayList<String>();
        for (var guard : SETTINGS.bossGuards()) {
            for (int n = 0; n < guard.count(); n++) {
                named.add(guard.kind());
            }
        }
        assertEquals(List.of("SkeletonHealer", "SkeletonHealer", "SkeletonSummoner", "SkeletonSummoner"), named,
                "the shipped file's guard, in its order");
        for (int depth = 1; depth <= SETTINGS.finalDepth(); depth++) {
            for (long seed = 0; seed < 20; seed++) {
                var floor = DungeonGenerator.generate(seed, SETTINGS, depth);
                var keep = floor.keep();
                var wanted = new java.util.ArrayList<GeneratedDungeon.Monster>();
                for (int i = 0; i < named.size(); i++) {
                    var corner = keep.corners().get(i);
                    wanted.add(new GeneratedDungeon.Monster(named.get(i), Placement.atCell(corner[0], corner[1])));
                }
                assertEquals(wanted, floor.monsters().stream()
                                .filter(monster -> keep.isCourt(monster.at().cellX(), monster.at().cellY()))
                                .toList(),
                        "seed " + seed + " at depth " + depth);
            }
        }
    }
```

`KeepSettingsTest`, a new test after `anEvenSizeIsRefusedByNumber`:

```java
    /** The boss in the middle and a mage at each corner, a cell in from each wall: seven across at least. */
    @Test
    void aKeepTooSmallForItsGuardIsRefused() {
        var data = Content.data().replace("    Sizes = [15, 13, 11, 9]\n", "    Sizes = [15, 5]\n");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().endsWith(": 5"), refused.getMessage());
    }
```

`MonsterPlacementTest`: in `deeperDownTheBossRoomHoldsTheGuardTheFileNames`, `SETTINGS.bossGuardsAt(depth)` becomes
`SETTINGS.bossGuards()`; and two new tests after `aGuardLeavesTheRestOfTheFloorAsItWas`:

```java
    /**
     * MinDepth is for the rooms the draw fills: the file's guard stands with every boss, however shallow its floor —
     * asked of the deepest kind the rooms draw, whatever the unit files say that is today.
     */
    @Test
    void aGuardStandsWithTheBossWhateverItsMinDepth() {
        var deepest = SETTINGS.monsters().stream().filter(kind -> kind.weight() > 0)
                .max(java.util.Comparator.comparingInt(kind -> kind.minDepth())).orElseThrow();
        assertTrue(deepest.minDepth() > 1, "every kind is met on the first floor, so this proves nothing");
        var guarded = guardedBy(deepest.name() + " = 4");
        for (long seed = 0; seed <= 10; seed++) {
            var floor = DungeonGenerator.generate(seed, guarded, 1);
            var room = floor.rooms().get(floor.bossRoom());

            assertEquals(4, floor.monsters().stream()
                    .filter(monster -> inRoom(monster.at(), room) && monster.kind().equals(deepest.name())).count(),
                    "seed " + seed + ": " + deepest.name() + " is not a floor deep enough to stand with the boss");
        }
    }

    /** A guard beyond the fourth stands on the ring round the boss, inside the court, where every guard used to. */
    @Test
    void aGuardBeyondTheFourthStandsOnTheRingRoundTheBoss() {
        var five = guardedBy("SkeletonHealer = 2, SkeletonSummoner = 2, Runner = 1");
        for (long seed = 0; seed <= 20; seed++) {
            var floor = DungeonGenerator.generate(seed, five, 1);
            var keep = floor.keep();
            var runner = floor.monsters().stream()
                    .filter(monster -> keep.isCourt(monster.at().cellX(), monster.at().cellY())
                            && monster.kind().equals("Runner"))
                    .findFirst().orElseThrow();
            int out = Math.max(Math.abs(runner.at().cellX() - floor.boss().at().cellX()),
                    Math.abs(runner.at().cellY() - floor.boss().at().cellY()));

            assertEquals(five.bossGuardRing(), out, "seed " + seed + ": " + runner.at());
            for (var corner : keep.corners()) {
                assertFalse(runner.at().equals(GeneratedDungeon.Placement.atCell(corner[0], corner[1])),
                        "seed " + seed + ": it stands on a corner a mage already has");
            }
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.KeepShapeTest" --tests "uz.dukeengine.dungeon.gen.KeepFloorTest" --tests "uz.dukeengine.dungeon.gen.MonsterPlacementTest" --tests "uz.dukeengine.dungeon.content.KeepSettingsTest"`
Expected: FAIL — compilation errors: `cannot find symbol: method corners()` in `Keep`, `method bossGuards()` in
`DungeonSettings`.

- [ ] **Step 3: The corners — `Keep.java`, after `isCourt`**

```java
    /**
     * Where the boss's first four guards stand: the court's corners, a cell in from each wall — one diagonal and then
     * the other, the order the ring round the boss takes its own corners in, so the first two named share a diagonal.
     */
    public List<int[]> corners() {
        int near = 2;
        int far = size() - 3;
        return List.of(new int[] {walls.x() + near, walls.y() + near}, new int[] {walls.x() + far, walls.y() + far},
                new int[] {walls.x() + far, walls.y() + near}, new int[] {walls.x() + near, walls.y() + far});
    }
```

- [ ] **Step 4: The guard whatever its MinDepth — `DungeonSettings.java`**

Replace `bossGuardsAt` and its Javadoc with:

```java
    /**
     * Who stands with the boss, on every floor: each guard the file names, in the order it names them. MinDepth is
     * for the rooms the draw fills, and keeps no guard away.
     */
    public java.util.List<BossGuard> bossGuards() {
        var here = new java.util.ArrayList<BossGuard>();
        for (var guard : map.descent().bossGuards().entrySet()) {
            here.add(new BossGuard(guard.getKey(), guard.getValue()));
        }
        return here;
    }
```

(the map is the reader's `LinkedHashMap`, so this is the order the file writes). In `validate`, the keep's size check

```java
            require(size >= 5 && size % 2 == 1, "a Keep is odd and at least 5 across, so its court and its doorway"
                    + " have middle cells: " + size);
```

becomes

```java
            require(size >= 7 && size % 2 == 1, "a Keep is odd and at least 7 across, so its court and its doorway"
                    + " have middle cells and its guard corners apart from the boss's: " + size);
```

- [ ] **Step 5: Standing them — `DungeonGenerator.java`**

Replace

```java
        var guarded = chambers.get(bossRoom);
        if (keep != null) {
            // The guard is kept to the court: the square the keep is walled in takes in its ring of wall and its
            // doorway too, and no guard is stood in either. Its middle cell is the same, so it stands where it did.
            var w = keep.walls();
            guarded = new Room(w.x() + 1, w.y() + 1, w.w() - 2, w.h() - 2);
        }
        monsters.addAll(guard(cave, guarded, settings, depth));
```

with

```java
        var guarded = chambers.get(bossRoom);
        if (keep != null) {
            // The guard is kept to the court: the square the keep is walled in takes in its ring of wall and its
            // doorway too, and no guard is stood in either. Its middle cell is the same, so the ring round the boss is.
            var w = keep.walls();
            guarded = new Room(w.x() + 1, w.y() + 1, w.w() - 2, w.h() - 2);
        }
        monsters.addAll(guard(cave, guarded, keep, settings));
```

and replace `guard` (its Javadoc and body; `ringAround` and `inside` stay) with:

```java
    /**
     * Stand the boss's guard with it: the kinds the file names, in its order, on every floor.
     *
     * <p>In a keep the first four stand at the court's corners, a cell in from each wall — so with two healers named
     * before two summoners, the healers hold one diagonal and the summoners the other. Any more, and every guard on a
     * floor with no keep, stand on the next free cell of a square ring round the boss's own.
     *
     * <p>No dice, so a floor's chambers, fillers and furniture are drawn exactly as they were before its boss had a
     * guard. The ring is {@code BossGuardRing} cells out -- a boss is wide -- with its corners taken first and then the
     * middles of its sides. A cell that is rock, outside the chamber's footprint or stood on already is passed over, and
     * when one ring has no floor left the next ring out is tried.
     */
    private static List<Monster> guard(Cave cave, Room room, Keep keep, DungeonSettings settings) {
        var wanted = new ArrayList<String>();
        for (var guard : settings.bossGuards()) {
            for (int n = 0; n < guard.count(); n++) {
                wanted.add(guard.kind());
            }
        }
        var guards = new ArrayList<Monster>();
        if (keep != null) {
            for (var corner : keep.corners()) {
                if (guards.size() < wanted.size()) {
                    guards.add(new Monster(wanted.get(guards.size()), Placement.atCell(corner[0], corner[1])));
                }
            }
        }
        int bx = room.centerCellX();
        int by = room.centerCellY();
        int widest = Math.max(room.w(), room.h());
        for (int ring = settings.bossGuardRing();
                ring <= widest && guards.size() < wanted.size(); ring++) {
            for (var cell : ringAround(ring)) {
                int cx = bx + cell[0];
                int cy = by + cell[1];
                if (guards.size() < wanted.size() && inside(room, cx, cy) && cave.isFloor(cx, cy)
                        && nobodyOn(guards, cx, cy)) {
                    guards.add(new Monster(wanted.get(guards.size()), Placement.atCell(cx, cy)));
                }
            }
        }
        return guards;
    }

    /** Whether no guard stands on the cell yet: in a small keep the ring's corners are the court's. */
    private static boolean nobodyOn(List<Monster> guards, int cx, int cy) {
        var cell = Placement.atCell(cx, cy);
        return guards.stream().noneMatch(guard -> guard.at().equals(cell));
    }
```

- [ ] **Step 6: The file says so — `generation.duke`**

In the `Descent` block, replace

```
    ; A kind and how many of it. A kind not yet at its MinDepth stays away. The healer
    ; and the summoner are both at MinDepth 1, so no floor is without them: their guard
    ; stands with every boss, the first floor's included, and that is meant -- the green
    ; and the purple mages, two to mend the boss and two to fill the room while they do.
    ; They stand on a square ring BossGuardRing cells out from the boss, corners first,
    ; and further out if the room has no floor there. MaxPerRoom is for rooms the draw
    ; fills and does not apply here. Leave BossGuards out and every boss waits alone.
```

with

```
    ; A kind and how many of it, and it stands with every boss, the first floor's
    ; included, whatever its MinDepth -- MinDepth and MaxPerRoom are for the rooms
    ; the draw fills. The green and the purple mages: two to mend the boss and two
    ; to fill the court while they do. The first four stand at the keep's court
    ; corners, a cell in from each wall, in the order written -- so the healers
    ; hold one diagonal and the summoners the other. Any more, and every guard on a
    ; floor with no keep, stand on a square ring BossGuardRing cells out from the
    ; boss, corners first, and further out if the room has no floor there. Leave
    ; BossGuards out and every boss waits alone.
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.KeepShapeTest" --tests "uz.dukeengine.dungeon.gen.KeepFloorTest" --tests "uz.dukeengine.dungeon.gen.MonsterPlacementTest" --tests "uz.dukeengine.dungeon.content.KeepSettingsTest"`
Expected: PASS.

- [ ] **Step 8: Run the whole suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/gen/Keep.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/gen/DungeonGenerator.java src/main/resources/data/world/generation.duke src/test/java/uz/dukeengine/dungeon/gen/KeepShapeTest.java src/test/java/uz/dukeengine/dungeon/gen/KeepFloorTest.java src/test/java/uz/dukeengine/dungeon/gen/MonsterPlacementTest.java src/test/java/uz/dukeengine/dungeon/content/KeepSettingsTest.java
git commit -m "The boss's four mages stand at the court's corners on every floor, whatever their MinDepth" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The gate stays shut

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/run/GateUpdate.java` (whole file)
- Modify: `src/main/resources/data/props/gate.duke` (header and the `GateUpdate` block)
- Modify: `src/main/java/uz/dukeengine/dungeon/Dungeon.java` (the `GateUpdate` registration, ~185–189)
- Test: `src/test/java/uz/dukeengine/dungeon/run/GateTest.java`

**Interfaces:**
- Produces: `public final class GateUpdate extends Module` with `record Data(String opens)` and `void open()` —
  nothing opens the gate but a call to `open()` (Tasks 6 and 7 are its callers).

- [ ] **Step 1: Write the failing tests**

In `GateTest`, the class Javadoc becomes

```java
/**
 * The keep's gate: across its doorway nothing passes, a hero walking up leaves it shut, only its open() opens it,
 * and the floor turns it true.
 */
```

and the two tests `aHeroWalkingUpToItOpensItAndWalksOnThrough` and `aHeroFarFromItLeavesItShut` are replaced by:

```java
    /** A hero walking up to it, and standing at it, leaves it shut: only the key opens it now. */
    @Test
    void aHeroWalkingUpToItLeavesItShut() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        hero.getLocomotor().moveTo(new Coord3D(300f, 155f, 0f));
        game.runHeadless(150);

        assertNotNull(find(game, "Gate"), "it opened for him");
        assertNull(find(game, "OpenGate"), "and something stands where it stood");
        assertTrue(shut(game, DOORWAY.x(), DOORWAY.y()), "the doorway is open");
        assertTrue(hero.getPosition().x() < DOORWAY.x(), "and he is still on his side of it: " + hero.getPosition());
    }

    /** Opened, the same gate stands open where it stood, turned as it was, and he walks on through. */
    @Test
    void openedItStandsOpenWhereItStoodAndHeWalksOnThrough() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");

        find(game, "Gate").findModule(GateUpdate.class).open();
        game.runHeadless(2);

        assertNull(find(game, "Gate"), "it stayed shut");
        var open = find(game, "OpenGate");
        assertNotNull(open, "nothing stands where it stood");
        assertEquals(DOORWAY.x(), open.getPosition().x(), 0.01f);
        assertEquals(DOORWAY.y(), open.getPosition().y(), 0.01f);
        assertEquals((float) (StrictMath.PI / 2), open.getOrientation(), 1e-6f, "turned as the gate was");
        assertFalse(shut(game, DOORWAY.x(), DOORWAY.y()), "and the doorway open, the open gate in nobody's way");
        hero.getLocomotor().moveTo(new Coord3D(300f, 155f, 0f));
        game.runHeadless(250);
        assertTrue(hero.getPosition().x() > 260f, "and he walked on through: " + hero.getPosition());
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.GateTest"`
Expected: FAIL — `aHeroWalkingUpToItLeavesItShut`: "it opened for him" (today's gate opens for a hero in reach).

- [ ] **Step 3: Write `run/GateUpdate.java`**

```java
package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;

/**
 * The gate of the boss's keep: it stands across the doorway, and nothing walks through it until it is opened — and
 * nothing opens it but the key, given to it. A hero walking up to it leaves it shut.
 *
 * <p>Opening swaps it for the template its block names: the gate with a shape goes, so the engine lays the navigation
 * grid again without it and the doorway is walked at once, and the same gate with no shape stands in its place,
 * turned as it was, swinging open as it appears.
 *
 * <p>Deterministic: opened by an errand an order started, on every machine on the same frame.
 */
@ModuleGroup(ModuleGroups.EFFECT)
public final class GateUpdate extends Module {

    /**
     * @param opens the template that stands in its place once it is open; blank, and nothing does
     */
    public record Data(String opens) implements ModuleData {

        /** What a block leaves out. */
        static final Data DEFAULTS = new Data("");

        public Data {
            opens = opens == null ? "" : opens;
        }
    }

    private final Data data;
    /** Opened already: a second opening, the same frame, would stand a second open gate in the first. */
    private boolean opened;

    public GateUpdate(GameObject owner, Data data) {
        super(owner);
        this.data = data;
    }

    /**
     * Open it: this gate goes, its shape with it, and in its place, turned as it was, stands the template
     * {@code Opens} names — the same gate swung open, in nobody's way.
     */
    public void open() {
        if (opened) {
            return;
        }
        opened = true;
        var owner = getOwner();
        var world = owner.getWorld();
        var open = world == null || data.opens().isBlank() ? null : world.findTemplate(data.opens());
        if (open != null) {
            world.spawn(open, owner.getPosition(), owner.getPlayerIndex()).setOrientation(owner.getOrientation());
        }
        owner.markDestroyed();
    }
}
```

- [ ] **Step 4: The template — `data/props/gate.duke`**

Replace the header comment and the `Gate` block's `Modules` with:

```
; The gate of the boss's keep: it stands across the keep's doorway, and nothing
; walks through it until it is opened -- see gen/Keep and run/GateUpdate.
;
;   - no Body, so nothing ever shoots at it
;   - a Box as long as the doorway (three cells, 30 units) and thin across it;
;     the spawner turns it along the wall it stands in
;   - STRUCTURE, so it is drawn as a thing standing on the floor; SELECTABLE, so
;     the pointer finds it -- a click on it sends a hero up to it
;
; Nothing opens it but the key given to it: a hero walking up to it leaves it
; shut. What it looks like is the Keep theme's (data/world/themes/keep.duke).
```

```
  Modules = [
    GateUpdate
      ; What stands in its place once it is open, turned as it was.
      Opens = OpenGate
    End
  ]
```

(the `Object` lines between them, and the `OpenGate` block, are unchanged).

- [ ] **Step 5: Register it plainly — `Dungeon.java`**

Replace

```java
                    // The keep's gate, opened by a hero walking up to it. See GateUpdate.
                    var heroNames = settings.heroes().stream().map(hero -> hero.name())
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    factory.register(uz.dukeengine.dungeon.run.GateUpdate.Data.class,
                            (owner, data) -> new uz.dukeengine.dungeon.run.GateUpdate(owner, data, heroNames));
```

with

```java
                    // The keep's gate, which only the key given to it opens. See GateUpdate.
                    factory.register(uz.dukeengine.dungeon.run.GateUpdate.Data.class,
                            uz.dukeengine.dungeon.run.GateUpdate::new);
```

- [ ] **Step 6: Run the gate test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.GateTest"`
Expected: PASS, 5 tests.

- [ ] **Step 7: Run the whole suite**

Run: `./gradlew test`
Expected: PASS — `EveryModuleIsGroupedTest` still finds `GateUpdate` in `Effect`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/run/GateUpdate.java src/main/resources/data/props/gate.duke src/main/java/uz/dukeengine/dungeon/Dungeon.java src/test/java/uz/dukeengine/dungeon/run/GateTest.java
git commit -m "The keep's gate stays shut when a hero walks up to it: only its open() opens it" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The key's model, its picture, and one look in every biome

**Files:**
- Create: `art/models/key_crown.glb` (the owner's, copied untouched)
- Create: `art/icons/render_item.py`
- Create (written by the script): `src/main/resources/models/props/key/key_crown.glb`,
  `src/main/resources/icons/items/key_crown.png`
- Create: `src/main/resources/data/props/key.duke`
- Modify: `src/main/resources/data/game.duke` (after `data/props/gate.duke,`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/Content.java` (`TYPES`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (a list beside `themes`, `read`, an
  accessor, `validate`)
- Modify: `src/main/java/uz/dukeengine/dungeon/Main.java` (`themes`, `themedCreature` → `dressed`, `looks`)
- Modify: `CREDITS.md` (a row after the gate's)
- Test: `src/test/java/uz/dukeengine/dungeon/KeyArtTest.java` (new), `.../DungeonTilesTest.java`

**Interfaces:**
- Produces: template `Key` (an `Object` with a `GroundItem`, no body, no shape); `public List<Theme.ThemeMonster>
  DungeonSettings.looks()` — `ThemeMonster` blocks written outside any theme, each a thing's look in every theme
  that does not dress it; `static Visuals Main.looks(DungeonSettings)` (package-private); the picture
  `icons/items/key_crown.png` (64 × 64, clear round the key).
- The client falls back to the base look by itself: `DukeRtsApp.lookOf` asks the cell's or floor's theme
  (`Visuals.Theme.of`, null where it has no opinion) and then `Visuals.of(template)` — so the one base look
  registered under `Key` draws the key in every biome and in the keep, and no theme names it.

- [ ] **Step 1: Write the failing tests**

`src/test/java/uz/dukeengine/dungeon/KeyArtTest.java`:

```java
package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.world.Theme;

/**
 * The keep's key as the game draws it: the owner's model lying on the floor where it fell, one look in every biome,
 * and its picture in the bag the size of every other.
 */
class KeyArtTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static Theme.ThemeMonster look() {
        var look = SETTINGS.looks().stream().filter(one -> one.name().equals("Key")).findFirst().orElse(null);
        assertNotNull(look, "nothing says what the key looks like");
        return look;
    }

    @Test
    void itLiesFlatOnTheFloorWhereItFellAboutHalfACellLong() {
        var model = new DesktopAssetManager(true).loadModel(look().model());
        model.updateGeometricState();
        var box = (BoundingBox) model.getWorldBound();

        assertEquals(0f, box.getCenter().y - box.getYExtent(), 0.01f, "on the floor rather than sunk into it");
        assertEquals(0f, box.getCenter().x, 0.05f, "where it fell, not beside it");
        assertEquals(0f, box.getCenter().z, 0.05f, "where it fell, not beside it");
        assertTrue(box.getYExtent() < Math.max(box.getXExtent(), box.getZExtent()) / 4f, "lying down, as a key does");
        float length = 2f * Math.max(box.getXExtent(), box.getZExtent()) * look().modelScale();
        assertTrue(length > 5f && length < 9f, "a little over half a cell long: " + length);
    }

    /** One look, named once: no theme dresses it, and outside every theme it is the owner's key. */
    @Test
    void everyBiomeDrawsItTheOneWay() {
        for (var theme : SETTINGS.themes().all()) {
            assertTrue(theme.monsters().stream().noneMatch(themed -> themed.name().equals("Key")),
                    theme.name() + " dresses the key its own way");
        }
        assertEquals(look().model(), Main.looks(SETTINGS).of("Key").modelFor(1f, java.util.Set.of()));
    }

    @Test
    void itsPictureIsTheSizeOfEveryOtherAndClearRoundTheKey() throws IOException {
        var image = ImageIO.read(KeyArtTest.class.getClassLoader().getResource("icons/items/key_crown.png"));

        assertNotNull(image, "the bag has no picture of it");
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        for (int[] corner : new int[][] {{0, 0}, {63, 0}, {0, 63}, {63, 63}}) {
            assertEquals(0, image.getRGB(corner[0], corner[1]) >>> 24, "clear in its corners, so the slot shows");
        }
        int drawn = 0;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                drawn += (image.getRGB(x, y) >>> 24) > 0 ? 1 : 0;
            }
        }
        assertTrue(drawn > 200, "and a key in the middle: " + drawn + " pixels of it");
    }
}
```

`DungeonTilesTest.everyPictureIsSquareAndTheSizeItsSheetWasCutAt` — the folder list becomes:

```java
        for (var folder : new String[][] {{"icons/skills/", "256"}, {"icons/commands/", "128"},
            {"icons/stats/", "64"}, {"icons/items/", "64"}}) {
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.KeyArtTest" --tests "uz.dukeengine.dungeon.DungeonTilesTest"`
Expected: FAIL — compilation errors: `cannot find symbol: method looks()` in `DungeonSettings`; `looks(DungeonSettings)
has private access in Main`.

- [ ] **Step 3: Copy the owner's model, untouched**

```bash
cp "C:/Users/abdur/OneDrive/Рабочий стол/Game assets/items/keys/key_crown.glb" art/models/key_crown.glb
cmp "C:/Users/abdur/OneDrive/Рабочий стол/Game assets/items/keys/key_crown.glb" art/models/key_crown.glb && echo SAME
```

Expected: `SAME` (213 036 bytes). The original is never written to.

- [ ] **Step 4: Write `art/icons/render_item.py`**

```python
"""The picture a bag draws a thing with, rendered from the thing's own model -- and the game's copy of that model,
set on its own origin and lying on it.

    "C:/Program Files/Blender Foundation/Blender 5.2/blender.exe" -b --factory-startup \
        -P art/icons/render_item.py -- art/models/key_crown.glb \
        src/main/resources/models/props/key/key_crown.glb src/main/resources/icons/items/key_crown.png

Run by hand when an item's model arrives, as art/anim/fbx_to_glb.py is, and the build does not depend on it: what
ships is what it wrote, which is committed. Kept here rather than done in Blender's window for the reason the icon
cutter is: a picture nobody can make again is a picture nobody dares change.

THE MODEL IS SET ON ITS ORIGIN. The owner's key was exported where it lay in his scene, two units off the origin; the
game draws a thing at its origin, so as it came the key would lie two cells from where it fell. Its middle is moved
over the origin and its underside onto it, and nothing else in it changes.

THE PICTURE IS THE SIZE OF THE OTHERS. 64 by 64, as every picture in icons/stats is, and clear round the thing, so a
bag's slot shows its own stone there. Looked at from straight above and turned an eighth, so a long thing runs corner
to corner; lit by a sun and by a pale sky it shines in; rendered by Cycles on the processor, so a machine with no
graphics card makes the same picture.
"""

import math
import os
import sys

import bpy
from mathutils import Vector

SIZE = 64


def bounds(meshes):
    """The box round every mesh, in the scene's own units: its low corner and its high one."""
    corners = [thing.matrix_world @ Vector(corner) for thing in meshes for corner in thing.bound_box]
    low = Vector((min(c.x for c in corners), min(c.y for c in corners), min(c.z for c in corners)))
    high = Vector((max(c.x for c in corners), max(c.y for c in corners), max(c.z for c in corners)))
    return low, high


def render(source, model, picture):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=source)
    scene = bpy.context.scene
    meshes = [thing for thing in scene.objects if thing.type == "MESH"]
    if not meshes:
        raise SystemExit(f"{source}: no mesh in it to draw")

    # On its own origin, lying on it.
    low, high = bounds(meshes)
    shift = Vector(((low.x + high.x) / 2, (low.y + high.y) / 2, low.z))
    for thing in scene.objects:
        if thing.parent is None:
            thing.location -= shift
    bpy.context.view_layer.update()
    bpy.ops.export_scene.gltf(filepath=model, export_format="GLB")

    # From straight above, turned an eighth: a rectangle across by down fills (across + down) / sqrt 2 each way.
    low, high = bounds(meshes)
    across = high.x - low.x
    down = high.y - low.y
    camera = bpy.data.objects.new("Camera", bpy.data.cameras.new("Camera"))
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = (across + down) / math.sqrt(2) * 1.1
    camera.location = (0, 0, high.z + 10)
    camera.rotation_euler = (0, 0, math.radians(45))
    scene.collection.objects.link(camera)
    scene.camera = camera

    sun = bpy.data.objects.new("Sun", bpy.data.lights.new("Sun", "SUN"))
    sun.data.energy = 4
    sun.rotation_euler = (math.radians(40), 0, math.radians(30))
    scene.collection.objects.link(sun)
    # The sky is what silver shows: a world's colour is its Background node's, not World.color.
    sky = bpy.data.worlds.new("Sky")
    background = sky.node_tree.nodes["Background"]
    background.inputs["Color"].default_value = (0.8, 0.8, 0.85, 1.0)
    background.inputs["Strength"].default_value = 1.0
    scene.world = sky

    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 64
    scene.render.resolution_x = SIZE
    scene.render.resolution_y = SIZE
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.view_settings.view_transform = "Standard"
    scene.render.filepath = picture
    bpy.ops.render.render(write_still=True)
    print(f"RENDERED {picture}, and {model} set on its origin, from {source}")


argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(argv) != 3:
    raise SystemExit("usage: blender -b --factory-startup -P render_item.py -- model.glb out.glb out.png")
# Whole paths: Blender reads a relative render path against the .blend it has not got, and wrote to C:\out.
render(*(os.path.abspath(path) for path in argv))
```

- [ ] **Step 5: Run it**

```bash
mkdir -p src/main/resources/models/props/key src/main/resources/icons/items
"C:/Program Files/Blender Foundation/Blender 5.2/blender.exe" -b --factory-startup -P art/icons/render_item.py -- art/models/key_crown.glb src/main/resources/models/props/key/key_crown.glb src/main/resources/icons/items/key_crown.png
```

Expected, in about ten seconds: a line `RENDERED <whole path>\key_crown.png, and <whole path>\key_crown.glb set on
its origin, from <whole path>\key_crown.glb`, and the two files written (the model about 213 KB, its root node now
at about `(0.011, 0.039, 0.020)` so the key's middle is over its origin and its underside on it; the picture 64 × 64
RGBA, about 5 KB). Open the PNG: a silver key corner to corner on a clear ground — measured while this plan was
written, 677 of its pixels drawn, its four corners clear.

- [ ] **Step 6: The template and its look — `data/props/key.duke`**

```
; The key to the boss's keep, lying on the floor: where the last monster the
; floor put outside the keep fell, where a hero carrying it fell, or wherever
; one put it down -- see run/Mission. It lies as a chest lies and is taken up the
; same way: a right click on it sends a hero for it.
;
;   - no Body, so nothing shoots at it; no Geometry, so it is in nobody's way
;   - STRUCTURE, so it is drawn as a thing on the floor; SELECTABLE, so the
;     pointer finds it and says what it is
;
; What it holds is the Key among the LootItem blocks of data/world/world.duke.
Object
  Name = Key
  DisplayName = Kalit
  KindOf = [STRUCTURE, SELECTABLE]
  VisionRange = 0
  Modules = [
    GroundItem
    End
  ]
End

; How it is drawn, in every biome and in the keep alike: one look, named once. A
; ThemeMonster written outside any theme is what a thing looks like wherever the
; theme it lies in does not dress it, and no theme dresses the Key. The owner's
; crown key (art/models/key_crown.glb), set on its own origin and lying on it by
; art/icons/render_item.py: 1.1 long as modelled, so 6.6 at this size -- a
; little over half a cell.
ThemeMonster
  Name = Key
  Model = models/props/key/key_crown.glb
  ModelScale = 6
End
```

and in `data/game.duke`, after `data/props/gate.duke,`:

```
    data/props/key.duke,
```

- [ ] **Step 7: A ThemeMonster outside a theme is read — `Content.java`, `DungeonSettings.java`**

`Content.TYPES`: after `named(Theme.class),` add `named(Theme.ThemeMonster.class),`.

`DungeonSettings`, beside `private final List<Theme> themes = new java.util.ArrayList<>();`:

```java
    /** How things look outside any theme — in every theme that does not dress them. */
    private final List<Theme.ThemeMonster> looks = new java.util.ArrayList<>();
```

in `read`, after `case Theme theme -> themes.add(theme);`:

```java
                case Theme.ThemeMonster look -> looks.add(look);
```

after the `biomes()` accessor:

```java
    /**
     * How things look outside any theme, and so in every theme that does not dress them: the keep's key, one look
     * named once rather than a copy in every biome. See the ThemeMonster in {@code data/props/key.duke}.
     */
    public List<Theme.ThemeMonster> looks() {
        return List.copyOf(looks);
    }
```

and in `validate`, after the loop that checks every theme's monsters' animations:

```java
        for (var look : looks) {
            requireLinked(look.animations(), "ThemeMonster " + look.name());
        }
```

- [ ] **Step 8: Drawn so — `Main.java`**

Replace `themedCreature` with a method that returns the dressing rather than applying it:

```java
    /**
     * What one creature is drawn as, from its ThemeMonster block — in a theme, or outside any — described from
     * nothing.
     */
    private static java.util.function.Consumer<Visuals.UnitVisual> dressed(Theme.ThemeMonster themed,
            DungeonSettings settings) {
        var art = settings.animated(themed.look());
        return unit -> {
            unit.colour(art.awtTint());
            if (!art.hasModel()) {
                return;
            }
            unit.model(art.model())
                    .texture(art.texture())
                    .tint(art.awtTint())
                    .scale(art.modelScale())
                    .facing(art.facing())
                    .idle(art.idle())
                    .walk(art.walk())
                    .attack(art.attack())
                    .hurt(art.hurt())
                    .effect(art.effect())
                    .die(art.death());
            carry(unit, art.held());
            if (themed.playOnce() != null) {
                // Once, from its first frame, and held on its last: a gate swinging open as it appears.
                unit.clip(java.util.Set.of(), themed.playOnce(), Visuals.ClipMode.ONCE, Visuals.ClipStart.FIRST, null);
            }
            // Borrowed only when the file says so. A themed creature usually comes
            // with a model of its own, and a model of its own carries its own
            // clips -- copying them onto it from a second copy of the same file
            // rebinds the tracks to the wrong skeleton and the thing collapses.
            // Linking animations here is for a theme that re-skins a creature with
            // another model from the same kit.
            for (var library : art.libraries()) {
                unit.animationsFrom(library);
            }
        };
    }
```

in `themes`, `themedCreature(look, themed, settings);` becomes:

```java
                        look.unit(themed.name(), dressed(themed, settings));
```

`looks` loses `private` (`static Visuals looks(DungeonSettings settings) {`), its Javadoc gains a last line
`<p>Package-private so a test can ask what a thing is drawn as.`, and after
`visuals.unit("Chest", unit -> unit.colour(new java.awt.Color(0xE8A33D)).scale(0.5f));` it gains:

```java

        // And what the files dress outside any theme: drawn so in every theme that does not dress it -- the key,
        // one look named once rather than a copy in every biome. See the ThemeMonster in data/props/key.duke.
        for (var look : settings.looks()) {
            visuals.unit(look.name(), dressed(look, settings));
        }
```

- [ ] **Step 9: Its credit — `CREDITS.md`**

After the row of the keep's gate:

```
| The keep's key — a crown key, lying where the last monster outside the keep fell, and its picture in the bag rendered from it | from the owner's own asset folder, maker not named | **unconfirmed** — to be asked of the owner before a release | `models/props/key/`, `icons/items/key_crown.png` |
```

- [ ] **Step 10: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.KeyArtTest" --tests "uz.dukeengine.dungeon.DungeonTilesTest"`
Expected: PASS.

- [ ] **Step 11: Run the whole suite**

Run: `./gradlew test`
Expected: PASS — a new file in `game.duke` reaches every test that reads the shipped data.

- [ ] **Step 12: Commit**

```bash
git add art/models/key_crown.glb art/icons/render_item.py src/main/resources/models/props/key/key_crown.glb src/main/resources/icons/items/key_crown.png src/main/resources/data/props/key.duke src/main/resources/data/game.duke src/main/java/uz/dukeengine/dungeon/content/Content.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/Main.java CREDITS.md src/test/java/uz/dukeengine/dungeon/KeyArtTest.java src/test/java/uz/dukeengine/dungeon/DungeonTilesTest.java
git commit -m "The keep's key: the owner's crown key lying on the floor in every biome, its bag picture rendered from it" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The key — a thing that gives nothing, never joins, never drops, and may be used

**Files:**
- Create: `src/main/java/uz/dukeengine/dungeon/loot/ItemUse.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/loot/LootKind.java` (+ `KEY`)
- Modify: `src/main/java/uz/dukeengine/dungeon/loot/Loot.java` (components, constructors, `joined`, `worth`, two
  methods)
- Modify: `src/main/java/uz/dukeengine/dungeon/world/LootItem.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/loot/LootBag.java` (`take`; + `holds`)
- Modify: `src/main/java/uz/dukeengine/dungeon/loot/ItemErrand.java` (the put-down line, ~144)
- Modify: `src/main/java/uz/dukeengine/dungeon/BagScreen.java` (`bonusOf` ~134–148; `lines` ~205–216)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate`, after the items' `sayable`
  loop ~672)
- Modify: `src/main/resources/data/world/world.duke` (the kinds paragraph ~315–321; a `LootItem` after `Tome`)
- Test: `src/test/java/uz/dukeengine/dungeon/loot/LootTest.java`, `.../BagScreenTest.java`,
  `.../loot/ItemErrandTest.java`

**Interfaces:**
- Consumes: template `Key` (Task 4).
- Produces: `enum ItemUse { NONE, UNLOCK }`; `LootKind.KEY`; `Loot(String id, String name, String icon, LootKind kind,
  int value, int weight, int minDepth, String attribute, LootExtra extra, int extraValue, int extraStep, int level,
  ItemUse use, String liesAs)` (the 7- and 8-argument constructors stay), `boolean Loot.joins()`,
  `String Loot.liesAs(String chest)`; `LootItem(String name, String displayName, String icon, LootKind kind,
  int value, int weight, int minDepth, String attribute, LootExtra extra, int extraValue, ItemUse use,
  String liesAs)`;
  `boolean LootBag.holds(LootKind kind)`; the shipped item `Key` (`Kind = KEY`, `Use = UNLOCK`, `LiesAs = Key`,
  `Weight = 0`).

- [ ] **Step 1: Write the failing tests**

`LootTest` — `theDungeonLeavesTheThreeAttributes` and `theFileDecidesWhatCanBeFound` become:

```java
    /** What the dungeon leaves at random: the hero's three attributes, three points each, each with an extra to come. */
    @Test
    void theDungeonLeavesTheThreeAttributes() {
        var found = SHIPPED.loot().stream().filter(item -> item.weight() > 0).toList();
        assertEquals(List.of("Gauntlet", "Boots", "Tome"), found.stream().map(Loot::id).toList());
        for (var item : found) {
            assertEquals(LootKind.ATTRIBUTE, item.kind(), item.id());
            assertEquals(3, item.value(), item.id());
            assertTrue(item.extra() != LootExtra.NONE && item.extraStep() > 0, item.id() + " has an extra to come");
        }
        assertEquals(0, SHIPPED.lootDrops().valuePercentPerDepth(), "alike things have to stay alike to join");
    }

    @Test
    void theFileDecidesWhatCanBeFound() {
        assertFalse(SHIPPED.loot().isEmpty(), "the shipped game leaves something behind");
        assertEquals("Chest", SHIPPED.lootDrops().template());
        for (var item : SHIPPED.loot()) {
            assertFalse(item.name().isBlank(), item.id() + " has nothing to say for itself");
            assertTrue(item.value() > 0 || item.kind() == LootKind.KEY, item.id() + " is worth nothing");
        }
    }
```

and these are added after them:

```java
    private static Loot key() {
        return SHIPPED.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
    }

    /** The shipped key: given, never found, used on a gate, lying as a key rather than in a chest. */
    @Test
    void theKeyIsAThingThatMayBeUsed() {
        var key = key();
        assertEquals("Key", key.id());
        assertEquals(ItemUse.UNLOCK, key.use());
        assertEquals("Key", key.liesAs("Chest"), "it lies as itself");
        assertEquals("Chest", SHIPPED.loot().getFirst().liesAs("Chest"), "and everything else in a chest");
        assertEquals(ItemUse.NONE, SHIPPED.loot().getFirst().use(), "which only counts while it is carried");
    }

    /** Carrying it changes nothing about him: the same health, the same blow, nothing coming back quicker. */
    @Test
    void theKeyGivesNothing() {
        var bag = new LootBag();
        bag.take(key(), 0, 0);
        assertEquals(0, bag.attackPercent() + bag.health() + bag.mana() + bag.armourPercent() + bag.healthRegen()
                + bag.manaRegen() + bag.attackSpeedPercent(), "a figure from a key");
        assertTrue(bag.holds(LootKind.KEY));

        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var player = game.getLogic().getRtsPlayer(game.getLocalPlayerIndex());
        float health = hero.getBody().getMaxHealth();
        float blow = player.getWeaponDamageBonus();
        session.progress().getLoot().take(key(), 0, 30);
        game.runHeadless(2);
        assertEquals(health, hero.getBody().getMaxHealth(), 0.0001f, "his health");
        assertEquals(blow, player.getWeaponDamageBonus(), 0.0001f, "his blow");
    }

    /** Three keys are three keys: a key never joins into a higher one. */
    @Test
    void keysNeverJoin() {
        var bag = new LootBag();
        for (int n = 0; n < LootBag.JOIN; n++) {
            assertTrue(bag.take(key(), 0, 0));
        }
        assertEquals(List.of(key(), key(), key()), bag.getFound());
    }

    /** Nothing leaves one at random, however often things drop. */
    @Test
    void theKeyIsNeverDrawnAsADrop() {
        assertEquals(0, key().weight());
        var table = new LootTable(SHIPPED.loot(), 7L, 100, 100, 0);
        for (int id = 0; id < 2000; id++) {
            var drop = table.dropFor(id, 1 + id % 4, id % 10 == 0);
            assertNotNull(drop);
            assertTrue(drop.kind() != LootKind.KEY, "monster " + id + " dropped the key");
        }
    }

    /** And a file that would let one drop at random is refused: a key is given, never found. */
    @Test
    void aKeyThatWouldDropIsRefused() {
        var shipped = "  Kind = KEY\n  Use = UNLOCK\n  LiesAs = Key\n  Value = 0\n  Weight = 0\n";
        var data = uz.dukeengine.dungeon.content.Content.data();
        assertTrue(data.contains(shipped), "the shipped key is no longer written this way");

        assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace(shipped, shipped.replace("Weight = 0", "Weight = 5"))));
    }
```

(`LootTest` gains `import static org.junit.jupiter.api.Assertions.assertThrows;`; `find` is the test's own helper.)

`BagScreenTest.everyShippedThingCanBeDrawnAndSaid`, the loop becomes:

```java
        for (var item : SETTINGS.loot()) {
            assertNotNull(BagScreen.class.getResource("/" + item.icon()), item.id() + " draws " + item.icon());
            var said = BagScreen.bonusOf(item, SETTINGS);
            if (item.kind() == LootKind.KEY) {
                assertEquals("", said, "a key gives nothing, and says nothing of it");
                continue;
            }
            assertTrue(said.startsWith("+" + item.value()) && !said.contains("null"), item.id() + ": " + said);
        }
```

`ItemErrandTest`, a new test after `sentToPutAThingDownHeWalksThereAndLeavesIt`:

```java
    /** A thing that names what it lies as lies as that: the key goes down as a key, not into a chest. */
    @Test
    void theKeyPutDownLiesAsAKey() {
        var room = room(100f);
        var bag = new LootBag();
        var key = SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
        bag.take(key, 0, 0);

        assertTrue(ItemErrand.drop(room.hero(), 0, new Coord3D(250f, 150f, 0f), bag, room.rules()));
        room.game().runHeadless(300);

        assertTrue(room.chests().isEmpty(), "not in a chest");
        var lying = room.game().getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Key")).findFirst().orElse(null);
        assertNotNull(lying, "it does not lie as a key");
        assertEquals(key, lying.findModule(GroundItem.class).getHolding());
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.loot.LootTest" --tests "uz.dukeengine.dungeon.BagScreenTest" --tests "uz.dukeengine.dungeon.loot.ItemErrandTest"`
Expected: FAIL — compilation errors: `cannot find symbol: variable KEY` in `LootKind`, `class ItemUse`, `method
holds(LootKind)`.

- [ ] **Step 3: `loot/ItemUse.java`**

```java
package uz.dukeengine.dungeon.loot;

/**
 * What a thing in the bag does when it is used: a left click on it there, and then a click on what it is used on.
 *
 * <p>A thing that does nothing only counts while it is carried, which is all the dungeon leaves at random. The key is
 * the first that does something; a blink and illusions are to come on the same line of a {@code LootItem} block.
 */
public enum ItemUse {

    /** Nothing: it counts while it is carried, and a left click on it does nothing. */
    NONE,

    /** It opens the keep's gate, given to it, and is gone; on anything else it does nothing, and he keeps it. */
    UNLOCK
}
```

- [ ] **Step 4: `LootKind.KEY`**

`ATTRIBUTE` gains a comma, and after it:

```java

    /**
     * None of his figures: the key to the boss's keep, which opens its gate and gives him nothing. It never joins
     * with another, and it is given rather than found — see {@code run/Mission}.
     */
    KEY
```

- [ ] **Step 5: `Loot.java`**

The Javadoc gains, after `@param level`:

```java
 * @param use        what a left click on it in the bag does
 * @param liesAs     the template it lies on the floor as; blank for the chest everything else lies in
```

and the record, its compact constructor and the four methods below it become:

```java
public record Loot(String id, String name, String icon, LootKind kind, int value, int weight, int minDepth,
        String attribute, LootExtra extra, int extraValue, int extraStep, int level, ItemUse use, String liesAs) {

    public Loot {
        attribute = attribute == null ? "" : attribute;
        extra = extra == null ? LootExtra.NONE : extra;
        level = Math.max(1, level);
        use = use == null ? ItemUse.NONE : use;
        liesAs = liesAs == null ? "" : liesAs;
    }

    /** An item that gives no attribute, which is every kind but {@code ATTRIBUTE}. */
    public Loot(String id, String name, String icon, LootKind kind, int value, int weight,
            int minDepth) {
        this(id, name, icon, kind, value, weight, minDepth, "");
    }

    /** An item found as it is, with nothing beside its figure. */
    public Loot(String id, String name, String icon, LootKind kind, int value, int weight,
            int minDepth, String attribute) {
        this(id, name, icon, kind, value, weight, minDepth, attribute, LootExtra.NONE, 0, 0, 1, ItemUse.NONE, "");
    }

    /** Whether it is the same thing as {@code other} at the same level: what joins with it. */
    public boolean sameAs(Loot other) {
        return other != null && id.equals(other.id) && level == other.level;
    }

    /** Whether alike ones join into one of the next level: everything but a key, which is one key however many. */
    public boolean joins() {
        return kind != LootKind.KEY;
    }

    /** What it lies on the floor as: its own template, or {@code chest} for a thing that names none. */
    public String liesAs(String chest) {
        return liesAs.isBlank() ? chest : liesAs;
    }

    /**
     * {@code count} of this joined into one of the next level: worth all of them together, figure and extra — and
     * at the second level its extra begins.
     */
    public Loot joined(int count) {
        return new Loot(id, name, icon, kind, value * count, weight, minDepth, attribute, extra,
                extraValue * count + (level == 1 ? extraStep : 0), extraStep, level + 1, use, liesAs);
    }

    /** The same thing, worth {@code value} instead. */
    public Loot worth(int value) {
        return new Loot(id, name, icon, kind, value, weight, minDepth, attribute, extra, extraValue, extraStep, level,
                use, liesAs);
    }
```

- [ ] **Step 6: `world/LootItem.java`**

```java
package uz.dukeengine.dungeon.world;

import uz.dukeengine.dungeon.loot.ItemUse;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootExtra;
import uz.dukeengine.dungeon.loot.LootKind;

/**
 * One thing that can be found on a floor. A new one is a block and no Java.
 *
 * @param name        its id, which nothing but the file and the game read
 * @param displayName what the panel calls it; its name when the block gives none
 * @param attribute   which attribute a {@code Kind = ATTRIBUTE} item gives, by the name a hero's
 *     block uses for it
 * @param extra       what it gives beside its figure once three of it are joined into the second level
 * @param extraValue  how much of that the second level brings; every level after is three of the one before
 * @param use         what a left click on it in the bag does; {@code NONE}, the default, for a thing that only
 *     counts while it is carried
 * @param liesAs      the template it lies on the floor as; blank for the chest everything else lies in
 */
public record LootItem(String name, String displayName, String icon, LootKind kind, int value, int weight,
        int minDepth, String attribute, LootExtra extra, int extraValue, ItemUse use, String liesAs) {

    /** What a block leaves out. */
    public static final LootItem DEFAULTS = new LootItem("", "", "", LootKind.ATTACK, 0, 10, 1, "", LootExtra.NONE,
            0, ItemUse.NONE, "");

    /** The thing as it is found: at the first level, with its extra still to come. */
    public Loot loot() {
        return new Loot(name, displayName == null || displayName.isBlank() ? name : displayName, icon, kind, value,
                weight, minDepth, attribute, extra, 0, extraValue, 1, use, liesAs);
    }
}
```

- [ ] **Step 7: A key never joins — `LootBag.java`**

In `take`, `while (join >= 2 && coming.level() < topLevel) {` becomes
`while (join >= 2 && coming.level() < topLevel && coming.joins()) {`. After `isFull()`:

```java
    /** Whether he carries anything of {@code kind}: the key, say. */
    public boolean holds(LootKind kind) {
        return Arrays.stream(slots).anyMatch(item -> item != null && item.kind() == kind);
    }
```

- [ ] **Step 8: A thing lies as its own — `ItemErrand.java`**

```java
            GroundItem.lay(world, rules.template(), put, hero.getPosition(), rules.floorOwner());
```

becomes

```java
            GroundItem.lay(world, put.liesAs(rules.template()), put, hero.getPosition(), rules.floorOwner());
```

- [ ] **Step 9: What the pointer says of it — `BagScreen.java`**

`bonusOf` becomes:

```java
    /** What a thing gives, as the pointer says it: {@code +8% Zarba}, {@code +3 Kuch} — and of a key, nothing. */
    static String bonusOf(Loot item, DungeonSettings settings) {
        var hud = settings.hud();
        return switch (item.kind()) {
            case ATTACK -> "+" + item.value() + "% " + hud.attackWord();
            case ARMOUR -> "+" + item.value() + "% " + hud.armourWord();
            case HEALTH -> "+" + item.value() + " " + hud.healthWord();
            case MANA -> "+" + item.value() + " " + hud.manaWord();
            case ATTRIBUTE -> {
                var rules = settings.attributeRules();
                int at = rules.indexOf(item.attribute());
                yield "+" + item.value() + " " + (at < 0 ? item.attribute() : rules.attributes().get(at).word());
            }
            case KEY -> ""; // it opens a gate, and gives him nothing
        };
    }
```

and in `lines`, `said.add(bonusOf(item, settings));` becomes:

```java
        var bonus = bonusOf(item, settings);
        if (!bonus.isEmpty()) {
            said.add(bonus);
        }
```

- [ ] **Step 10: The file may not let one drop — `DungeonSettings.java`**

In `validate`, after

```java
        for (var item : loot) {
            require(sayable(item.name()),
                    "an item's DisplayName may not contain ',' or '|': " + item.id());
        }
```

add:

```java
        for (var item : loot) {
            require(item.kind() != LootKind.KEY || item.weight() == 0,
                    "LootItem " + item.id() + " is a KEY, which is given and never found: its Weight is 0");
        }
        require(map.keep().sizes().isEmpty()
                        || loot.stream().anyMatch(item -> item.use() == uz.dukeengine.dungeon.loot.ItemUse.UNLOCK),
                "a Keep's gate opens only to a key, and no LootItem has Use = UNLOCK");
```

- [ ] **Step 11: The key in the data — `world.duke`**

In the paragraph above `LootDrops`, replace

```
; The kinds a thing may be are what a hero is made of: ATTACK (percent of his
; blow), HEALTH (flat maximum health), ARMOUR (percent taken off what reaches
; him), MANA (flat maximum mana) and ATTRIBUTE (whole points of the attribute its
; Attribute line names -- see Attribute). What the dungeon leaves today is
; attributes alone. An Extra is HEALTH_REGEN or MANA_REGEN (whole points a
; second, on top of what comes back on its own) or ATTACK_SPEED (percent quicker
; between blows).
```

with

```
; The kinds a thing may be are what a hero is made of: ATTACK (percent of his
; blow), HEALTH (flat maximum health), ARMOUR (percent taken off what reaches
; him), MANA (flat maximum mana) and ATTRIBUTE (whole points of the attribute its
; Attribute line names -- see Attribute) -- and KEY, which is none of them: the
; key to the boss's keep, last below, given rather than found. What the dungeon
; leaves at random is attributes alone. An Extra is HEALTH_REGEN or MANA_REGEN
; (whole points a second, on top of what comes back on its own) or ATTACK_SPEED
; (percent quicker between blows).
```

and after the `Tome` block, at the end of the file:

```

; The key to the boss's keep: given, never found. Nothing drops it at random --
; its Weight is 0 -- and the last monster a floor put outside its keep leaves it
; where it fell (see run/Mission). It gives nothing and never joins with
; another. Its Use is what a left click on it in the bag does: UNLOCK, and the
; next click is on the thing he takes it to -- given to the keep's gate it opens
; it and is gone. It lies on the floor as its own thing, the Key in
; data/props/key.duke, rather than in a chest: LiesAs.
LootItem
  Name = Key
  DisplayName = Kalit
  Icon = icons/items/key_crown.png
  Kind = KEY
  Use = UNLOCK
  LiesAs = Key
  Value = 0
  Weight = 0
End
```

- [ ] **Step 12: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.loot.LootTest" --tests "uz.dukeengine.dungeon.BagScreenTest" --tests "uz.dukeengine.dungeon.loot.ItemErrandTest"`
Expected: PASS.

- [ ] **Step 13: Run the whole suite**

Run: `./gradlew test`
Expected: PASS — `HeroChoiceTest` still takes the Gauntlet (`loot().getFirst()`: the key is written last);
`HeroStatusTest` finds an `ATTRIBUTE` item by kind.

- [ ] **Step 14: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/loot/ItemUse.java src/main/java/uz/dukeengine/dungeon/loot/LootKind.java src/main/java/uz/dukeengine/dungeon/loot/Loot.java src/main/java/uz/dukeengine/dungeon/world/LootItem.java src/main/java/uz/dukeengine/dungeon/loot/LootBag.java src/main/java/uz/dukeengine/dungeon/loot/ItemErrand.java src/main/java/uz/dukeengine/dungeon/BagScreen.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/resources/data/world/world.duke src/test/java/uz/dukeengine/dungeon/loot/LootTest.java src/test/java/uz/dukeengine/dungeon/BagScreenTest.java src/test/java/uz/dukeengine/dungeon/loot/ItemErrandTest.java
git commit -m "The key: the first thing that may be used, which gives nothing, never joins and never drops" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: A right click on the gate sends the hero up to it

**Files:**
- Create: `src/main/java/uz/dukeengine/dungeon/run/ToTheGate.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/run/GateUpdate.java` (`Data`; + `lineFor`)
- Modify: `src/main/resources/data/props/gate.duke` (the `GateUpdate` block)
- Modify: `src/main/java/uz/dukeengine/dungeon/loot/ItemErrand.java` (whole file)
- Modify: `src/main/java/uz/dukeengine/dungeon/party/PartyOrders.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/Dungeon.java` (`obey`, after the `DropItem` case ~447)
- Modify: `src/main/java/uz/dukeengine/dungeon/BagScreen.java` (class Javadoc; `show`'s context rule)
- Modify: `src/main/resources/data/world/hud.duke` (a `Cursor` after `Drop`'s)
- Test: `src/test/java/uz/dukeengine/dungeon/run/GateTest.java`, `.../party/PartyOrdersTest.java`,
  `.../BagScreenTest.java`

**Interfaces:**
- Consumes: `LootKind.KEY`, `LootBag.holds` (Task 5); `GateUpdate.open` (Task 3).
- Produces: `record ToTheGate(int playerIndex, ObjectId gate) implements Command`; `PartyOrders.TO_THE_GATE =
  "ToTheGate"`; `GateUpdate.Data(String opens, String withoutKeyWord, String withKeyWord)`,
  `String GateUpdate.lineFor(boolean withTheKey)`; `static boolean ItemErrand.toTheGate(GameObject hero,
  GameObject gate, LootBag bag, Rules rules)`; `ItemErrand` restructured round a private `Act` (`TAKE`, `PUT`,
  `LOOK`) and a `thing` id — Task 7 adds `USE`.

- [ ] **Step 1: Write the failing tests**

`GateTest` gains imports `uz.dukeengine.dungeon.loot.ItemErrand`, `uz.dukeengine.dungeon.loot.Loot`,
`uz.dukeengine.dungeon.loot.LootBag`, `uz.dukeengine.dungeon.loot.LootKind`, two helpers after `shut`:

```java
    /** What an errand here shares: the shipped reach, and a note that lasts, so what he says can be read. */
    private static ItemErrand.Rules rules(Dungeon.Arena arena) {
        var drops = SETTINGS.lootDrops();
        return new ItemErrand.Rules(drops.pickupRange(), 100_000, drops.template(), arena.dungeon().getIndex(),
                drops.fullWord());
    }

    private static Loot key() {
        return SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
    }
```

and two tests:

```java
    /** Sent up to it without the key, he walks there and says he has to find one — and it stays shut. */
    @Test
    void sentUpToItWithoutTheKeyHeSaysHeMustFindIt() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var gate = find(game, "Gate");
        var bag = new LootBag();

        assertTrue(ItemErrand.toTheGate(hero, gate, bag, rules(arena)));
        game.runHeadless(150);

        assertEquals("Boss xonasi uchun kalit topishim kerak", bag.noteAt(game.getLogic().getFrame()));
        assertTrue(hero.getPosition().x() > 180f, "he said it from where he stood: " + hero.getPosition());
        assertNotNull(find(game, "Gate"), "and it is still shut");
    }

    /** With the key in his bag he says he has to give it to the gate — which still does not open by itself. */
    @Test
    void sentUpToItWithTheKeyHeSaysHeMustGiveItToTheGate() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var bag = new LootBag();
        bag.take(key(), 0, 0);

        assertTrue(ItemErrand.toTheGate(find(game, "Rogue"), find(game, "Gate"), bag, rules(arena)));
        game.runHeadless(150);

        assertEquals("Kalit menda — uni darvozaga berishim kerak", bag.noteAt(game.getLogic().getFrame()));
        assertNotNull(find(game, "Gate"), "it opened without being given the key");
        assertTrue(bag.holds(LootKind.KEY), "and the key is still his");
    }
```

`PartyOrdersTest`: the class imports `uz.dukeengine.dungeon.run.ToTheGate`, and `EVERY_ORDER`'s last line
`            new DropItem(2, 5, new Coord3D(31.75f, 402.5f, 0f)));` becomes:

```java
            new DropItem(2, 5, new Coord3D(31.75f, 402.5f, 0f)),
            new ToTheGate(1, new ObjectId(64)));
```

and `anOrderThisGameNeverSendsMeansNothing` gains:

```java
        assertNull(PartyOrders.commandOf(new GameOrder(2, PartyOrders.TO_THE_GATE, List.of(), null, null, 0)),
                "a walk up to no gate");
```

`BagScreenTest`, a new test after `aThingOnTheFloorSaysWhatItGivesWhateverIsSelected`:

```java
    /**
     * A click on the keep's gate is an order of the game's own, as a click on a chest is — so the ring round it
     * flashes yellow — and the order sends the hero up to it.
     */
    @Test
    void aClickOnTheGateSendsHimUpToIt() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        var bag = bagOver(game);
        bag.show(session);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var gate = find(game, "Gate");
        assertNotNull(gate, "the floor's keep has no gate");

        game.setSelection(List.of(hero.getId().value()));
        game.setPointedAt(gate.getId().value());
        game.runHeadless(2);
        assertEquals(uz.dukeengine.dungeon.party.PartyOrders.TO_THE_GATE, game.getSnapshot().contextOrder());

        // What the client sends for that click.
        game.postCommand(new uz.dukeengine.rts.message.GameMessage.GameOrder(hero.getPlayerIndex(),
                game.getSnapshot().contextOrder(), List.of(hero.getId()), gate.getPosition(), gate.getId(), 0));
        game.runHeadless(2);
        assertNotNull(hero.findModule(uz.dukeengine.dungeon.loot.ItemErrand.class), "he is not on his way to it");
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.GateTest" --tests "uz.dukeengine.dungeon.party.PartyOrdersTest" --tests "uz.dukeengine.dungeon.BagScreenTest"`
Expected: FAIL — compilation errors: `cannot find symbol: class ToTheGate`,
`method toTheGate(GameObject,GameObject,LootBag,Rules)` in `ItemErrand`, `variable TO_THE_GATE` in `PartyOrders`.

- [ ] **Step 3: `run/ToTheGate.java`**

```java
package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * "Go up to the gate": a player's hero sent to the keep's gate — a click on it, as a click on a chest sends him for
 * the chest — to say, when he is there, whether he has the key. See {@code ItemErrand}.
 *
 * @param gate the gate he is sent up to
 */
public record ToTheGate(int playerIndex, ObjectId gate) implements Command {
}
```

- [ ] **Step 4: What a hero says at the gate — `GateUpdate.java`, `gate.duke`**

`GateUpdate.Data` becomes:

```java
    /**
     * @param opens          the template that stands in its place once it is open; blank, and nothing does
     * @param withoutKeyWord what a hero sent up to it says there without the key
     * @param withKeyWord    and with the key in his bag, which is not yet the key given to it
     */
    public record Data(String opens, String withoutKeyWord, String withKeyWord) implements ModuleData {

        /** What a block leaves out. */
        static final Data DEFAULTS = new Data("", "", "");

        public Data {
            opens = opens == null ? "" : opens;
            withoutKeyWord = withoutKeyWord == null ? "" : withoutKeyWord;
            withKeyWord = withKeyWord == null ? "" : withKeyWord;
        }
    }
```

and after the constructor:

```java
    /** What a hero sent up to it says when he gets there: with the key in his bag, or without it. */
    public String lineFor(boolean withTheKey) {
        return withTheKey ? data.withKeyWord() : data.withoutKeyWord();
    }
```

`gate.duke`, the `GateUpdate` block becomes:

```
    GateUpdate
      ; What stands in its place once it is open, turned as it was.
      Opens = OpenGate
      ; What a hero sent up to it says when he gets there, without the key and
      ; with it in his bag -- see loot/ItemErrand. Having it is not giving it:
      ; only the key given to it opens it.
      WithoutKeyWord = Boss xonasi uchun kalit topishim kerak
      WithKeyWord = Kalit menda — uni darvozaga berishim kerak
    End
```

- [ ] **Step 5: The errand up to the gate — `loot/ItemErrand.java`**

```java
package uz.dukeengine.dungeon.loot;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.run.GateUpdate;
import uz.dukeengine.rts.module.Errand;
import uz.dukeengine.rts.module.RtsModuleGroups;
import uz.dukeengine.rts.module.WeaponUpdate;

/**
 * A hero sent to pick a thing up off the floor, to put one of his down, or up to the keep's gate: he walks there, and
 * when he is near enough it is done — the thing changes hands, or he says what he makes of the gate.
 *
 * <p>An errand in the engine's sense — a module on him while it lasts — so any order the player gives him after it,
 * a walk, an attack, a stop, gives it up as it gives up every errand. A walk somewhere else it notices for itself,
 * since a skill that moves him does not go through the engine's door: his legs going anywhere but here is the
 * errand over. Legs that got as near as they could without getting here leave a thing he was sent for lying, and
 * put a thing he was sending down where they stopped. A thing with a shape — the gate — is walked up to rather than
 * onto: as near as he can get to it, its edge is there.
 *
 * <p>Deterministic: sent by an order, on every machine on the same frame; checked on a frame boundary; near enough
 * is a sum of squares, and how far a shape reaches is the engine's own figure, the same on every machine.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class ItemErrand extends UpdateModule implements Errand {

    /**
     * What every errand of a game shares.
     *
     * @param reach      how near he has to be for a thing to change hands
     * @param noteFrames how long what he says is said, in logic frames
     * @param template   what a thing he puts down lies in, when it names nothing of its own
     * @param floorOwner whose a thing on the floor is: the dungeon's, so it is nobody's hero's to select
     * @param fullWord   what he says when his bag has no room for what he was sent for
     */
    public record Rules(float reach, int noteFrames, String template, int floorOwner, String fullWord) {
    }

    /** What he was sent to do when he gets there. */
    private enum Act {
        /** Take what a thing lying there holds. */
        TAKE,
        /** Put what is in a slot of his down. */
        PUT,
        /** Say what he makes of the keep's gate. */
        LOOK
    }

    private final LootBag bag;
    private final Rules rules;
    private final Act act;
    private final Coord3D goal;
    /** What he was sent to: the thing to take, or the gate to look at; null when he was sent to put one down. */
    private final ObjectId thing;
    /** The slot he is putting down; -1 otherwise. */
    private final int slot;
    /** Done or given up: nothing more to do until the next order takes it off him. */
    private boolean over;

    private ItemErrand(GameObject hero, LootBag bag, Rules rules, Act act, Coord3D goal, ObjectId thing, int slot) {
        super(hero);
        this.bag = bag;
        this.rules = rules;
        this.act = act;
        this.goal = new Coord3D(goal.x(), goal.y(), 0f);
        this.thing = thing;
        this.slot = slot;
    }

    /** Send {@code hero} for what {@code thing} holds, into {@code bag}; false where there is nothing there. */
    public static boolean pickUp(GameObject hero, GameObject thing, LootBag bag, Rules rules) {
        var lying = thing == null ? null : thing.findModule(GroundItem.class);
        if (hero == null || bag == null || lying == null || lying.getHolding() == null) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, Act.TAKE, thing.getPosition(), thing.getId(), -1));
        return true;
    }

    /** Send {@code hero} to put what is in {@code slot} of {@code bag} down at {@code place}; false for an empty slot. */
    public static boolean drop(GameObject hero, int slot, Coord3D place, LootBag bag, Rules rules) {
        if (hero == null || bag == null || place == null || bag.at(slot) == null) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, Act.PUT, place, null, slot));
        return true;
    }

    /**
     * Send {@code hero} up to the keep's gate, to say when he is there whether he has the key — into {@code bag}'s
     * note; false for a thing that is not a gate.
     */
    public static boolean toTheGate(GameObject hero, GameObject gate, LootBag bag, Rules rules) {
        if (hero == null || bag == null || gate == null || gate.findModule(GateUpdate.class) == null) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, Act.LOOK, gate.getPosition(), gate.getId(), -1));
        return true;
    }

    /** What the player said last is what he does: whatever he was at is given up, and off he goes. */
    private static void send(GameObject hero, ItemErrand errand) {
        Errand.giveUpAll(hero);
        var weapon = hero.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.holdFire(); // as a walk does: the fight he was in is not what he was sent to do
        }
        var legs = hero.getLocomotor();
        if (legs != null) {
            if (errand.near(hero, errand.rules.reach())) {
                legs.stop();
            } else if (errand.thing != null) {
                legs.moveExactlyTo(errand.goal); // onto the thing, or as near it as he can get
            } else {
                legs.moveTo(errand.goal);
            }
        }
        hero.addModule(errand);
    }

    @Override
    public void update() {
        var hero = getOwner();
        var world = hero.getWorld();
        if (over || world == null || hero.isEffectivelyDead()) {
            return;
        }
        var legs = hero.findModule(MoveUpdate.class);
        var going = legs == null ? null : legs.getGoal();
        if (going != null && !going.equals(goal)) {
            over = true; // sent somewhere else
            return;
        }
        var there = thing == null ? null : world.findObject(thing);
        var lying = there == null ? null : there.findModule(GroundItem.class);
        if (thing != null && (there == null || act == Act.TAKE && (lying == null || lying.getHolding() == null))) {
            over = true; // gone, or somebody got there first
            return;
        }
        if (!near(hero, rules.reach())) {
            boolean asNearAsHeCan = legs != null && !legs.isMoving() && legs.stoppedShort();
            if (!asNearAsHeCan) {
                return;
            }
            // As near as he could get. A place he could not reach is where he got to, and a thing lying there that
            // he could not reach stays where it is -- but a thing with a shape is walked up to rather than onto, and
            // its edge is there.
            if (there != null && !near(hero, rules.reach() + there.getGeometry().footprintRadius())) {
                over = true;
                return;
            }
        }
        over = true;
        if (legs != null && legs.isMoving()) {
            legs.stop();
        }
        int frame = world.getFrame();
        switch (act) {
            case TAKE -> {
                // Into the bag if there is room for it, or if it makes up a set that joins into less room than it
                // takes.
                if (bag.take(lying.getHolding(), frame, rules.noteFrames())) {
                    lying.take();
                } else {
                    bag.say(rules.fullWord(), frame, rules.noteFrames());
                }
            }
            case PUT -> {
                var put = bag.remove(slot);
                if (put != null) {
                    GroundItem.lay(world, put.liesAs(rules.template()), put, hero.getPosition(), rules.floorOwner());
                }
            }
            case LOOK -> {
                var gate = there.findModule(GateUpdate.class);
                if (gate != null) {
                    bag.say(gate.lineFor(bag.holds(LootKind.KEY)), frame, rules.noteFrames());
                }
            }
        }
    }

    /** Whether he stands within {@code reach} of it, across the floor: how high either is does not come into it. */
    private boolean near(GameObject hero, float reach) {
        float dx = hero.getPosition().x() - goal.x();
        float dy = hero.getPosition().y() - goal.y();
        return dx * dx + dy * dy <= reach * reach;
    }

    /** Whether it has been done or given up. */
    public boolean isOver() {
        return over;
    }
}
```

- [ ] **Step 6: The order — `PartyOrders.java`**

Import `uz.dukeengine.dungeon.run.ToTheGate`. After `DROP`:

```java
    /**
     * What a click on the keep's gate gives the hero, as a click on a thing lying on the floor gives a pickup: sent
     * by the engine as the word the game named, and the pointer over the gate is the cursor of that name.
     */
    public static final String TO_THE_GATE = "ToTheGate";
```

in `of`, after the `DropItem` case:

```java
            case ToTheGate gate -> new GameOrder(gate.playerIndex(), TO_THE_GATE, List.of(), null, gate.gate(), 0);
```

in `commandOf`, after the `DROP` case:

```java
            case TO_THE_GATE -> order.target() == null ? null : new ToTheGate(order.playerIndex(), order.target());
```

- [ ] **Step 7: Obeyed — `Dungeon.java`**

In `obey`, after the `DropItem` case:

```java
                // Sent up to the keep's gate: he walks there and says whether he has the key. See ItemErrand.
                case uz.dukeengine.dungeon.run.ToTheGate gate -> {
                    var progress = run.progressOf(gate.playerIndex());
                    if (progress != null && uz.dukeengine.dungeon.loot.ItemErrand.toTheGate(
                            Skills.heroOf(game.getLogic(), gate.playerIndex()),
                            game.getLogic().findObject(gate.gate()), progress.getLoot(),
                            errandRules(settings, arena))) {
                        arena.orders().attackMove(gate.playerIndex(), null);
                    }
                }
```

- [ ] **Step 8: The click on the gate — `BagScreen.java`, `hud.duke`**

Import `uz.dukeengine.dungeon.run.GateUpdate`. The class Javadoc's second paragraph becomes:

```java
 * <p>Nothing here touches the world. Picking up is the click the client already sends on a thing the game names a
 * word for ({@link PartyOrders#PICK_UP}), and so is going up to the keep's gate ({@link PartyOrders#TO_THE_GATE});
 * putting down is the client's aim at the ground, whose place comes back as a {@link DropItem} order — so both go
 * down the road every order goes, to every machine of a party.
```

In `show`, the context rule becomes:

```java
        game.contextOrder((selection, target) -> {
            var item = target.findModule(GroundItem.class);
            if (item != null && item.getHolding() != null) {
                return PartyOrders.PICK_UP;
            }
            // And the keep's gate, which he is sent up to: see ItemErrand.
            return target.findModule(GateUpdate.class) == null ? null : PartyOrders.TO_THE_GATE;
        });
```

`hud.duke`, after the `Drop` cursor's block:

```

; And the keep's gate, which a click sends him up to: a lock, until he has
; given it the key. Held at its middle, as the hands are.
Cursor
  Name = ToTheGate
  Image = ui/cursors/default/lock.png
  HotX = 15
  HotY = 15
  Tint = 0xE8A33D
End
```

- [ ] **Step 9: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.GateTest" --tests "uz.dukeengine.dungeon.party.PartyOrdersTest" --tests "uz.dukeengine.dungeon.BagScreenTest" --tests "uz.dukeengine.dungeon.loot.ItemErrandTest"`
Expected: PASS — `ItemErrandTest` holds the pick-up and put-down as they were.

- [ ] **Step 10: Run the whole suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/run/ToTheGate.java src/main/java/uz/dukeengine/dungeon/run/GateUpdate.java src/main/resources/data/props/gate.duke src/main/java/uz/dukeengine/dungeon/loot/ItemErrand.java src/main/java/uz/dukeengine/dungeon/party/PartyOrders.java src/main/java/uz/dukeengine/dungeon/Dungeon.java src/main/java/uz/dukeengine/dungeon/BagScreen.java src/main/resources/data/world/hud.duke src/test/java/uz/dukeengine/dungeon/run/GateTest.java src/test/java/uz/dukeengine/dungeon/party/PartyOrdersTest.java src/test/java/uz/dukeengine/dungeon/BagScreenTest.java
git commit -m "A right click on the keep's gate sends the hero up to it, to say whether he has the key" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The key given to the gate

**Files:**
- Create: `src/main/java/uz/dukeengine/dungeon/loot/UseItem.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/loot/ItemErrand.java` (`Rules`, `Act`, fields' Javadoc, + `use`, the
  switch)
- Modify: `src/main/java/uz/dukeengine/dungeon/world/LootDrops.java` (+ `noUseWord`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (`validate`, after the `FullWord` check)
- Modify: `src/main/resources/data/world/world.duke` (`LootDrops`, after `AttackSpeedWord`)
- Modify: `src/main/java/uz/dukeengine/dungeon/party/PartyOrders.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/Dungeon.java` (`obey`; `errandRules`)
- Modify: `src/main/java/uz/dukeengine/dungeon/BagScreen.java` (constants, a field, `show`, `slotOf`, `paint`,
  `click`, + `hold`)
- Test: `src/test/java/uz/dukeengine/dungeon/run/GateTest.java`, `.../loot/ItemErrandTest.java`,
  `.../party/PartyOrdersTest.java`, `.../BagScreenTest.java`

**Interfaces:**
- Consumes: `ItemUse.UNLOCK`, `Loot.use()` (Task 5); `GateUpdate.open()` (Task 3); `ItemErrand` of Task 6.
- Produces: `record UseItem(int playerIndex, int slot, ObjectId target) implements Command`; `PartyOrders.USE =
  "use"`; `ItemErrand.Rules(float reach, int noteFrames, String template, int floorOwner, String fullWord, String
  noUseWord)`; `static boolean ItemErrand.use(GameObject hero, int slot, GameObject thing, LootBag bag, Rules rules)`;
  `LootDrops.noUseWord()`; `BagScreen`'s aim ids `use:<slot>` beside `drop:<slot>`.

- [ ] **Step 1: Write the failing tests**

`GateTest.rules` gains the new word:

```java
        return new ItemErrand.Rules(drops.pickupRange(), 100_000, drops.template(), arena.dungeon().getIndex(),
                drops.fullWord(), drops.noUseWord());
```

and two tests:

```java
    /** Given the key, the gate opens — the owner's swing — and the key is gone from his bag. */
    @Test
    void usingTheKeyOnItOpensItAndTheKeyIsGone() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var bag = new LootBag();
        bag.take(key(), 0, 0);

        assertTrue(ItemErrand.use(find(game, "Rogue"), 0, find(game, "Gate"), bag, rules(arena)));
        game.runHeadless(150);

        assertNull(find(game, "Gate"), "the key did not open it");
        assertNotNull(find(game, "OpenGate"), "and nothing stands where it stood");
        assertNull(bag.at(0), "the key stayed in his bag");
        assertFalse(bag.holds(LootKind.KEY));
    }

    /** Used on anything else it does nothing: he says so, and keeps it. */
    @Test
    void usingTheKeyOnAnythingElseKeepsItAndSaysSo() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.spawn("Pillar", arena.dungeon(), 120f, 100f);
        game.runHeadless(2);
        var bag = new LootBag();
        bag.take(key(), 0, 0);

        assertTrue(ItemErrand.use(find(game, "Rogue"), 0, find(game, "Pillar"), bag, rules(arena)));
        game.runHeadless(150);

        assertEquals(key(), bag.at(0), "he gave it to a pillar");
        assertEquals("Bu kalit faqat boss darvozasini ochadi", bag.noteAt(game.getLogic().getFrame()));
        assertNotNull(find(game, "Gate"), "and the gate is as it was");
    }
```

`ItemErrandTest.Room.rules()` becomes:

```java
        ItemErrand.Rules rules() {
            return new ItemErrand.Rules(SETTINGS.lootDrops().pickupRange(), 100_000, "Chest", floorOwner, "Full",
                    "No use");
        }
```

`PartyOrdersTest`: import `uz.dukeengine.dungeon.loot.UseItem`; `EVERY_ORDER`'s last lines become:

```java
            new ToTheGate(1, new ObjectId(64)),
            new UseItem(2, 3, new ObjectId(64)));
```

and `anOrderThisGameNeverSendsMeansNothing` gains:

```java
        assertNull(PartyOrders.commandOf(new GameOrder(2, PartyOrders.USE, List.of(), null, null, 3)),
                "a thing used on nothing");
```

`BagScreenTest`, a new test after `aThingInTheBagIsShownTakenInHandAndPutDown`:

```java
    /**
     * A thing that does something is used, not only put down: a left click on it in the bag takes it in hand, and
     * the thing the next click is on is where he takes it — here the keep's gate.
     */
    @Test
    void aLeftClickOnTheKeyTakesItToTheThingClickedNext() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var key = SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
        session.progress().getLoot().take(key, 0, 30);
        var bag = bagOver(game);
        bag.show(session);
        var drawn = new Drawn();
        bag.paint(drawn);
        var picture = drawn.picture(key.icon());
        assertNotNull(picture, "the key is drawn in its slot: " + drawn.pictures);

        assertTrue(bag.take(new uz.dukeengine.client3d.CanvasInput.Button(picture.middleX(), picture.middleY(),
                uz.dukeengine.client3d.CanvasInput.Mouse.LEFT, true, false)), "a left click on it is the bag's");
        drawn.clear();
        bag.paint(drawn);
        assertFalse(drawn.text.contains(SETTINGS.lootDrops().dropHint()), "in hand to be used, not put down");

        // Where the client's aim was pressed: on the gate.
        var gate = find(game, "Gate");
        game.pressCommand("use:0", null, 0f, gate.getId().value());
        game.runHeadless(2);
        assertNotNull(find(game, "Rogue").findModule(uz.dukeengine.dungeon.loot.ItemErrand.class),
                "he is not on his way to the gate with it");
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.GateTest" --tests "uz.dukeengine.dungeon.loot.ItemErrandTest" --tests "uz.dukeengine.dungeon.party.PartyOrdersTest" --tests "uz.dukeengine.dungeon.BagScreenTest"`
Expected: FAIL — compilation errors: `cannot find symbol: class UseItem`,
`method use(GameObject,int,GameObject,LootBag,Rules)` in `ItemErrand`, `method noUseWord()` in `LootDrops`; the
constructor of `Rules` takes five arguments, not six.

- [ ] **Step 3: `loot/UseItem.java`**

```java
package uz.dukeengine.dungeon.loot;

import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.thing.ObjectId;

/**
 * "Use that on this": a player's hero sent to take what is in one slot of his bag to a thing, and use it there — the
 * key on the keep's gate. See {@link ItemErrand}.
 *
 * @param slot   which of his bag's slots, from 0
 * @param target what he takes it to
 */
public record UseItem(int playerIndex, int slot, ObjectId target) implements Command {
}
```

- [ ] **Step 4: What he says of a thing that does nothing there — `LootDrops.java`, `world.duke`, `DungeonSettings`**

`LootDrops` gains, after `@param attackSpeedWord`:

```java
 * @param noUseWord            what he says when he was sent to use a thing on something it does nothing to — the key
 *     on anything but the keep's gate
```

and becomes:

```java
public record LootDrops(String template, int dropPercent, int bossDropPercent, float pickupRange,
        int valuePercentPerDepth, int noteFrames, int slots, String fullWord, String takeHint, String dropHint,
        int joinCount, int topLevel, String healthRegenWord, String manaRegenWord, String attackSpeedWord,
        String noUseWord) {

    /** What a block leaves out. */
    public static final LootDrops DEFAULTS = new LootDrops("", 20, 100, 14f, 20, 90, 6, "The bag is full",
            "Right button: take", "Left button: put it down", 3, 3, "Health/s", "Mana/s", "Attack speed",
            "It does nothing here");
}
```

`world.duke`, in `LootDrops`, after `AttackSpeedWord = Hujum tezligi`:

```
  ; What he says when he is sent to use a thing on something it does nothing
  ; to: the key on anything but the keep's gate. He keeps it.
  NoUseWord = Bu kalit faqat boss darvozasini ochadi
```

`DungeonSettings.validate`, after `require(sayable(lootDrops.fullWord()), "FullWord may not contain ',' or '|'");`:

```java
        require(sayable(lootDrops.noUseWord()), "NoUseWord may not contain ',' or '|'");
```

- [ ] **Step 5: The errand with a thing in hand — `ItemErrand.java`**

The class Javadoc's first paragraph becomes:

```java
 * A hero sent to pick a thing up off the floor, to put one of his down, to use one of his on a thing, or up to the
 * keep's gate: he walks there, and when he is near enough it is done — the thing changes hands, is used, or he says
 * what he makes of the gate.
```

`Rules` becomes:

```java
    /**
     * What every errand of a game shares.
     *
     * @param reach      how near he has to be for a thing to change hands
     * @param noteFrames how long what he says is said, in logic frames
     * @param template   what a thing he puts down lies in, when it names nothing of its own
     * @param floorOwner whose a thing on the floor is: the dungeon's, so it is nobody's hero's to select
     * @param fullWord   what he says when his bag has no room for what he was sent for
     * @param noUseWord  what he says when he was sent to use a thing on something it does nothing to
     */
    public record Rules(float reach, int noteFrames, String template, int floorOwner, String fullWord,
            String noUseWord) {
    }
```

`Act` gains, after `PUT`:

```java
        /** Use what is in a slot of his on a thing. */
        USE,
```

the two fields' Javadoc become:

```java
    /**
     * What he was sent to: the thing to take, to use one of his on, or the gate to look at; null when he was sent to
     * put one down.
     */
    private final ObjectId thing;
    /** The slot he is putting down or using; -1 otherwise. */
    private final int slot;
```

and after `toTheGate`:

```java
    /**
     * Send {@code hero} to take what is in {@code slot} of {@code bag} to {@code thing} and use it there; false for a
     * slot with nothing in it that can be used, or nothing to use it on.
     */
    public static boolean use(GameObject hero, int slot, GameObject thing, LootBag bag, Rules rules) {
        var item = bag == null ? null : bag.at(slot);
        if (hero == null || thing == null || item == null || item.use() == ItemUse.NONE) {
            return false;
        }
        send(hero, new ItemErrand(hero, bag, rules, Act.USE, thing.getPosition(), thing.getId(), slot));
        return true;
    }
```

and the switch in `update` gains, after the `PUT` case:

```java
            case USE -> {
                var item = bag.at(slot);
                if (item == null || item.use() != ItemUse.UNLOCK) {
                    return; // nothing in hand that opens anything, any more
                }
                var gate = there.findModule(GateUpdate.class);
                if (gate == null) {
                    bag.say(rules.noUseWord(), frame, rules.noteFrames()); // it opens the gate and nothing else
                } else {
                    bag.remove(slot);
                    gate.open();
                }
            }
```

- [ ] **Step 6: The order — `PartyOrders.java`, `Dungeon.java`**

`PartyOrders`: import `uz.dukeengine.dungeon.loot.UseItem`; after `TO_THE_GATE`:

```java
    static final String USE = "use";
```

in `of`, after the `ToTheGate` case:

```java
            case UseItem use -> new GameOrder(use.playerIndex(), USE, List.of(), null, use.target(), use.slot());
```

in `commandOf`, after the `TO_THE_GATE` case:

```java
            case USE -> order.target() == null ? null
                    : new UseItem(order.playerIndex(), (int) order.number(), order.target());
```

`Dungeon.obey`, after the `ToTheGate` case:

```java
                // Sent to use a thing of his on another: the key on the gate. See ItemErrand.
                case uz.dukeengine.dungeon.loot.UseItem use -> {
                    var progress = run.progressOf(use.playerIndex());
                    if (progress != null && uz.dukeengine.dungeon.loot.ItemErrand.use(
                            Skills.heroOf(game.getLogic(), use.playerIndex()), use.slot(),
                            game.getLogic().findObject(use.target()), progress.getLoot(),
                            errandRules(settings, arena))) {
                        arena.orders().attackMove(use.playerIndex(), null);
                    }
                }
```

and `errandRules` returns:

```java
        return new uz.dukeengine.dungeon.loot.ItemErrand.Rules(drops.pickupRange(), drops.noteFrames(),
                drops.template(), arena.dungeon().getIndex(), drops.fullWord(), drops.noUseWord());
```

- [ ] **Step 7: A left click uses — `BagScreen.java`**

Imports `uz.dukeengine.core.thing.ObjectId`, `uz.dukeengine.dungeon.loot.ItemUse`, `uz.dukeengine.dungeon.loot.UseItem`.
The class Javadoc's first paragraph becomes:

```java
/**
 * The hero's bag as the player handles it: its slots on a slab of stone at the right of the window, what a thing
 * gives when the pointer rests on it — in the bag or lying on the floor — and a thing taken in hand with the right
 * button and put down on the floor with the left; or, a thing that does something, taken in hand with the left and
 * used on the thing the next click is on.
```

and its second paragraph becomes:

```java
 * <p>Nothing here touches the world. Picking up is the click the client already sends on a thing the game names a
 * word for ({@link PartyOrders#PICK_UP}), and so is going up to the keep's gate ({@link PartyOrders#TO_THE_GATE});
 * putting down is the client's aim at the ground, whose place comes back as a {@link DropItem} order, and using a
 * thing is its aim at a thing, whose id comes back as a {@link UseItem} order — so all of them go down the road
 * every order goes, to every machine of a party.
```

After `DROP`:

```java
    /** The id of the aim that takes {@code slot} to a thing, to be used on it. */
    private static final String USE = "use:";
```

after `heldIn`:

```java
    /** Whether the thing in hand is to be used rather than put down: no word beside it about the floor. */
    private boolean using;
```

in `show`, the press handler becomes:

```java
        game.onCommandPressed(press -> {
            int put = slotOf(DROP, press.id());
            if (put >= 0 && press.place() != null) {
                game.postCommand(PartyOrders.of(new DropItem(game.getLocalPlayerIndex(), put, press.place())));
            }
            int used = slotOf(USE, press.id());
            if (used >= 0 && press.target() >= 0) {
                game.postCommand(PartyOrders.of(new UseItem(game.getLocalPlayerIndex(), used,
                        new ObjectId(press.target()))));
            }
        });
```

`slotOf` becomes:

```java
    /** The slot a drop aim's id names, or -1 for an id that is not one. */
    static int slotOf(String id) {
        return slotOf(DROP, id);
    }

    /** The slot an aim's id names after {@code kind} — a drop's or a use's — or -1 for an id that is not one. */
    private static int slotOf(String kind, String id) {
        if (id == null || !id.startsWith(kind)) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(kind.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
```

in `paint`, the tip of a thing in hand is only for putting down:

```java
            if (!using) {
                tip(canvas, List.of(settings.lootDrops().dropHint()), mouseX + 14, mouseY + 14 + size, false);
            }
```

and `click` becomes, with `hold` after it:

```java
    /**
     * A button on the bag stays on the bag; the right one on a thing in it takes that thing in hand to put down, and
     * the left one on a thing that does something takes it in hand to use.
     */
    private boolean click(Button button) {
        if (button.button() == Mouse.LEFT && !button.down()) {
            boolean mine = pressedHere;
            pressedHere = false;
            return mine;
        }
        if (!onTheBag(button.x(), button.y())) {
            return false;
        }
        var match = session;
        int at = slotAt(button.x(), button.y());
        var bag = match == null ? null : bagOf(match);
        var item = bag == null || at < 0 ? null : bag.slots().get(at);
        if (button.button() == Mouse.LEFT) {
            pressedHere = true;
            if (item != null && item.use() != ItemUse.NONE) {
                hold(match, at, item, USE, CommandButton.Aim.UNIT);
            }
            return true;
        }
        if (button.button() != Mouse.RIGHT || !button.down()) {
            return button.button() != Mouse.RIGHT;
        }
        if (item == null) {
            // Nothing to take. With a thing in hand, the client's own second thoughts let go of it.
            return held < 0;
        }
        hold(match, at, item, DROP, CommandButton.Aim.GROUND);
        return true;
    }

    /**
     * Take the thing in {@code at} in hand: to be put down where the next click on the ground is, or — a thing that
     * does something — taken to the thing the next click is on. The client arms the aim, and says when it is over.
     */
    private void hold(Dungeon.Session match, int at, Loot item, String kind, CommandButton.Aim aim) {
        int armed = ++aims;
        held = at;
        heldIn = match;
        using = aim == CommandButton.Aim.UNIT;
        duke.aim(new CommandButton(kind + at, item.icon(), item.name(), null, true, aim, null), 0f, HOLDING,
                outcome -> {
                    if (aims == armed) {
                        held = -1;
                    }
                });
    }
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.GateTest" --tests "uz.dukeengine.dungeon.loot.ItemErrandTest" --tests "uz.dukeengine.dungeon.party.PartyOrdersTest" --tests "uz.dukeengine.dungeon.BagScreenTest"`
Expected: PASS.

- [ ] **Step 9: Run the whole suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/loot/UseItem.java src/main/java/uz/dukeengine/dungeon/loot/ItemErrand.java src/main/java/uz/dukeengine/dungeon/world/LootDrops.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/resources/data/world/world.duke src/main/java/uz/dukeengine/dungeon/party/PartyOrders.java src/main/java/uz/dukeengine/dungeon/Dungeon.java src/main/java/uz/dukeengine/dungeon/BagScreen.java src/test/java/uz/dukeengine/dungeon/run/GateTest.java src/test/java/uz/dukeengine/dungeon/loot/ItemErrandTest.java src/test/java/uz/dukeengine/dungeon/party/PartyOrdersTest.java src/test/java/uz/dukeengine/dungeon/BagScreenTest.java
git commit -m "A left click on the key in the bag, then on the gate: he takes it there and the gate opens" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The mission

**Files:**
- Create: `src/main/java/uz/dukeengine/dungeon/run/Mission.java`
- Create: `src/main/java/uz/dukeengine/dungeon/loot/KeyDrop.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/loot/GroundItem.java` (`lay` public, ~53)
- Modify: `src/main/java/uz/dukeengine/dungeon/run/Spawner.java` (`Placed` ~37–39; the props loop and the return
  ~89–107)
- Modify: `src/main/java/uz/dukeengine/dungeon/world/Run.java`
- Modify: `src/main/resources/data/world/world.duke` (`Run`, after `WayIn = Fountain`)
- Modify: `src/main/java/uz/dukeengine/dungeon/run/DungeonRun.java` (fields after `gathering`; `lay`; `whileRunning`;
  `descend`; the observation section)
- Test: `src/test/java/uz/dukeengine/dungeon/run/MissionTest.java` (new)

**Interfaces:**
- Consumes: `Keep.holds` (Task 1); `LootKind.KEY`, `Loot.liesAs`, `LootBag.holds` (Task 5); `GateUpdate` (Task 3).
- Produces: `public final class Mission` with `enum Step { CLEAR, TAKE, GIVE, KILL }`, `static Mission of(World,
  GeneratedDungeon, Spawner.Placed, DungeonSettings, int floorOwner)` (null for a floor with no keep),
  `Step step(World, List<LootBag>)`, `String words(Run, Step)`, `int outside()`, `int killed()`;
  `Spawner.Placed(List<GameObject> heroes, GameObject boss, List<GameObject> monsters, GameObject gate)`;
  `KeyDrop(GameObject hero, LootBag bag, String chest, int floorOwner)`; `Run.clearWord(int killed, int of)`,
  `takeKeyWord()`, `giveKeyWord()`, `killBossWord()`; `DungeonRun.getMission()`, `getStep()`, `getTracker()` (the
  last from any thread) — Task 9 draws `getTracker()`.

- [ ] **Step 1: Write the failing test — `run/MissionTest.java`**

```java
package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.Layout;
import uz.dukeengine.dungeon.loot.GroundItem;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootKind;
import uz.dukeengine.dungeon.loot.PickUp;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.stage.Stage;
import uz.dukeengine.game.DukeGame;

/**
 * A floor's mission, as the run keeps it: every monster the floor put outside the keep killed, the key the last of
 * them leaves taken, given to the gate, and the boss — each step in order, said by the tracker.
 */
class MissionTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final long SEED = 21L;

    private static Dungeon.Session opened(DungeonSettings settings) {
        var session = Dungeon.newSession(SEED, settings);
        session.game().runHeadless(2);
        return session;
    }

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static Loot key() {
        return SETTINGS.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
    }

    private static int cellOf(float at) {
        return (int) Math.floor(at / 10f);
    }

    /** Everything of the dungeon's with a body that stands outside the keep: the floor's own, and nothing it raised. */
    private static List<GameObject> outside(Dungeon.Session session) {
        var game = session.game();
        var keep = DungeonGenerator.generate(SEED, SETTINGS, 1).keep();
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() != game.getLocalPlayerIndex() && object.getBody() != null)
                .filter(object -> !keep.holds(cellOf(object.getPosition().x()), cellOf(object.getPosition().y())))
                .toList();
    }

    /** The shipped descent with no monster in any chamber: only the boss and its guard, in the keep. */
    private static DungeonSettings nobodyOutside() {
        var data = Content.data();
        var shipped = "    MinSkeletonsPerRoom = 2\n    MaxSkeletonsPerRoom = 6\n";
        assertTrue(data.contains(shipped), "the shipped map no longer fills its chambers this way");
        return DungeonSettings.parse(data.replace(shipped,
                "    MinSkeletonsPerRoom = 0\n    MaxSkeletonsPerRoom = 0\n"));
    }

    @Test
    void itCountsTheMonstersTheFloorPutOutsideTheKeep() {
        var floor = DungeonGenerator.generate(SEED, SETTINGS, 1);
        var keep = floor.keep();
        int placed = (int) floor.monsters().stream()
                .filter(monster -> !keep.holds(monster.at().cellX(), monster.at().cellY())).count();
        var session = opened(SETTINGS);
        var mission = session.run().getMission();

        assertTrue(placed > 0, "a floor with nobody outside proves nothing here");
        assertEquals(placed, mission.outside(), "the floor's own outside the keep, the guard not among them");
        assertEquals(SETTINGS.run().clearWord(0, placed), session.run().getTracker());

        session.game().getLogic().destroyObject(outside(session).getFirst());
        session.game().runHeadless(1);
        assertEquals(1, mission.killed());
        assertEquals(SETTINGS.run().clearWord(1, placed), session.run().getTracker(), "killed, of all");
    }

    @Test
    void theLastOfThemLeavesTheKeyWhereItFell() {
        var session = opened(SETTINGS);
        var game = session.game();
        Coord3D last = null;
        for (var monster : outside(session)) {
            assertNull(find(game, "Key"), "a key while one of them stands");
            last = monster.getPosition();
            game.getLogic().destroyObject(monster);
            game.runHeadless(1);
        }

        var key = find(game, "Key");
        assertNotNull(key, "the last of them left no key");
        assertTrue(key.getPosition().distance(last) < 2f, "where it fell: " + key.getPosition() + ", not " + last);
        assertEquals(key(), key.findModule(GroundItem.class).getHolding(), "lying as a chest lies, holding the key");
    }

    @Test
    void aFloorThatPutNobodyOutsideLeavesTheKeyAtTheWayInFromTheStart() {
        var session = opened(nobodyOutside());
        var game = session.game();

        assertEquals(0, session.run().getMission().outside());
        var key = find(game, "Key");
        assertNotNull(key, "no key lies anywhere");
        assertTrue(key.getPosition().distance(find(game, "Rogue").getPosition()) < 10f,
                "where the heroes came in: " + key.getPosition());
        assertEquals(Mission.Step.TAKE, session.run().getStep());
    }

    @Test
    void theStepsComeInOrderAndTheTrackerSaysEach() {
        var session = opened(SETTINGS);
        var game = session.game();
        var run = session.run();
        var words = SETTINGS.run();
        assertEquals(Mission.Step.CLEAR, run.getStep());

        for (var monster : outside(session)) {
            game.getLogic().destroyObject(monster);
            game.runHeadless(1);
        }
        assertEquals(Mission.Step.TAKE, run.getStep(), "every one of them dead, and the key lying");
        assertEquals(words.takeKeyWord(), run.getTracker());

        var key = find(game, "Key");
        find(game, "Rogue").setPosition(key.getPosition());
        game.postCommand(PartyOrders.of(new PickUp(game.getLocalPlayerIndex(), key.getId())));
        game.runHeadless(3);
        assertEquals(Mission.Step.GIVE, run.getStep(), "the key in his bag");
        assertEquals(words.giveKeyWord(), run.getTracker());

        find(game, "Gate").findModule(GateUpdate.class).open();
        game.runHeadless(2);
        assertEquals(Mission.Step.KILL, run.getStep(), "the gate open");
        assertEquals(words.killBossWord(), run.getTracker());
    }

    @Test
    void aHeroWhoFallsWithTheKeyLeavesItWhereHeFell() {
        var session = opened(SETTINGS);
        var game = session.game();
        var bag = session.progress().getLoot();
        bag.take(key(), 0, 0);
        var hero = find(game, "Rogue");
        var where = hero.getPosition();

        game.getLogic().destroyObject(hero);
        game.runHeadless(1);

        assertFalse(bag.holds(LootKind.KEY), "he took it with him");
        var key = find(game, "Key");
        assertNotNull(key, "it is nowhere");
        assertTrue(key.getPosition().distance(where) < 2f, "where he fell: " + key.getPosition());
        assertEquals(key(), key.findModule(GroundItem.class).getHolding());
    }

    /** The tracker's words are the owner's, as the spec writes them. */
    @Test
    void theTrackerSaysItInTheOwnersWords() {
        var words = SETTINGS.run();
        assertEquals("Qal'adan tashqaridagi barcha monstrlarni o'ldiring — 12/40", words.clearWord(12, 40));
        assertEquals("Kalitni oling", words.takeKeyWord());
        assertEquals("Kalitni boss darvozasiga bering", words.giveKeyWord());
        assertEquals("Bossni o'ldiring", words.killBossWord());
    }

    /** A stage is cut without a keep, and so has no mission: its tracker says only the last line. */
    @Test
    void aStageHasNoMission() {
        var floor = DungeonGenerator.generate(SEED, SETTINGS, 1,
                Layout.sized(SETTINGS, SETTINGS.mapWidth(), SETTINGS.mapHeight(), SETTINGS.maxRooms()));
        var session = Dungeon.newStageSession(new Stage("test", "Test", "", 1, 1, SEED, floor), SETTINGS);
        session.game().runHeadless(2);

        assertNull(session.run().getMission());
        assertEquals(Mission.Step.KILL, session.run().getStep());
        assertEquals(SETTINGS.run().killBossWord(), session.run().getTracker());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MissionTest"`
Expected: FAIL — compilation errors: `cannot find symbol: class Mission`, `method getMission()` in `DungeonRun`,
`method clearWord(int,int)` in `Run`.

- [ ] **Step 3: A thing laid from elsewhere — `GroundItem.java`**

`static GameObject lay(uz.dukeengine.core.thing.World world, String template, Loot item,` becomes
`public static GameObject lay(uz.dukeengine.core.thing.World world, String template, Loot item,`.

- [ ] **Step 4: `loot/KeyDrop.java`**

```java
package uz.dukeengine.dungeon.loot;

import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rts.module.RtsModuleGroups;

/**
 * What a hero leaves where he falls: the key, if he carries it — so a party never loses the way on with him, and the
 * others can take it up where it lies.
 *
 * <p>Hung on the engine's {@link DieModule} seam, as a monster's {@link LootDrop} is and for the same reason: it runs
 * once he has left the world, and what it lays lies where he fell. Everything else in his bag stays his, for when he
 * stands again.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class KeyDrop extends Module implements DieModule {

    private final LootBag bag;
    private final String chest;
    private final int floorOwner;

    /**
     * @param chest      what a thing lies in when it names nothing of its own
     * @param floorOwner whose a thing on the floor is: the dungeon's
     */
    public KeyDrop(GameObject hero, LootBag bag, String chest, int floorOwner) {
        super(hero);
        this.bag = bag;
        this.chest = chest;
        this.floorOwner = floorOwner;
    }

    @Override
    public void onDie() {
        var hero = getOwner();
        var world = hero.getWorld();
        if (world == null) {
            return;
        }
        for (int slot = 0; slot < bag.slots().size(); slot++) {
            var item = bag.at(slot);
            if (item != null && item.kind() == LootKind.KEY) {
                bag.remove(slot);
                GroundItem.lay(world, item.liesAs(chest), item, hero.getPosition(), floorOwner);
            }
        }
    }
}
```

- [ ] **Step 5: The gate the floor stood — `Spawner.java`**

`Placed` becomes:

```java
    /**
     * Everything a floor puts in the world — the heroes in the order they were given — the boss it hangs its exit
     * on, and the gate across its keep's doorway, or null for a floor with none.
     */
    public record Placed(List<GameObject> heroes, GameObject boss, List<GameObject> monsters, GameObject gate) {
    }
```

the props loop becomes:

```java
        GameObject gate = null;
        for (var prop : dungeon.props()) {
            if (!underIt.contains(key(prop.at().cellX(), prop.at().cellY()))) {
                var thing = spawn(game, dungeonPlayer, prop.kind(), at(logic, prop.at()));
                if (thing != null && thing.findModule(GateUpdate.class) != null) {
                    thing.setOrientation(acrossTheDoorway(dungeon, prop.at()));
                    gate = thing;
                }
            }
        }
```

and the return:

```java
        return new Placed(java.util.Collections.unmodifiableList(heroes), boss, List.copyOf(monsters), gate);
```

- [ ] **Step 6: The tracker's words — `Run.java`, `world.duke`**

`Run`'s Javadoc gains, after `@param wayIn`:

```java
 * @param clearWord          what the tracker says while the floor's own outside its keep still stand: the first
 *     {@code %d} how many of them are dead, the second how many there were
 * @param takeKeyWord        and while the key lies on the floor
 * @param giveKeyWord        and while a hero carries it
 * @param killBossWord       and once the gate is open — or on a floor with no keep, all it ever says
```

and the record becomes:

```java
public record Run(int respawnDelayFrames, int descendDelayFrames, int victoryFrames, String defaultHero,
        String diedWord, String wonWord, String nextDepthWord, String wayIn, String clearWord, String takeKeyWord,
        String giveKeyWord, String killBossWord) {

    /** What a block leaves out. */
    public static final Run DEFAULTS = new Run(60, 75, 150, "Rogue", "You died", "You won", "Depth %d", "",
            "Kill everything outside the keep — %d/%d", "Take the key", "Give the key to the gate", "Kill the boss");

    /** What the banner says as he goes down to {@code depth}. */
    public String nextDepthWord(int depth) {
        return nextDepthWord.replace("%d", Integer.toString(depth));
    }

    /** What the tracker says with {@code killed} of the floor's own outside its keep dead, of {@code of}. */
    public String clearWord(int killed, int of) {
        return clearWord.replaceFirst("%d", Integer.toString(killed)).replaceFirst("%d", Integer.toString(of));
    }
}
```

`world.duke`, in `Run`, after `WayIn = Fountain`:

```

  ; What the tracker at the top of the screen says of a floor's mission, a step
  ; at a time -- see run/Mission. The first counts: %d the monsters the floor
  ; put outside its keep that are dead, then %d how many it put there. A floor
  ; with no keep -- a stage, or one where none fitted -- has no mission, and its
  ; tracker says only the last.
  ClearWord = Qal'adan tashqaridagi barcha monstrlarni o'ldiring — %d/%d
  TakeKeyWord = Kalitni oling
  GiveKeyWord = Kalitni boss darvozasiga bering
  KillBossWord = Bossni o'ldiring
```

- [ ] **Step 7: `run/Mission.java`**

```java
package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.loot.GroundItem;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootBag;
import uz.dukeengine.dungeon.loot.LootKind;
import uz.dukeengine.dungeon.world.Run;
import uz.dukeengine.rts.module.RtsModuleGroups;

/**
 * What a floor with a keep asks of the party, in four steps: kill every monster it put outside the keep, take the
 * key the last of them leaves where it fell, give the key to the gate, and kill the boss behind it.
 *
 * <p>Kept by the run, one to a floor, and the same on every machine: it counts the deaths the engine tells it of, on
 * the frame they happen, lays the key with the world's own spawn, and reads the rest off the world as it stands —
 * whether anyone carries the key, whether the gate still stands. Nothing here reads a clock or a hash.
 *
 * <p>Only the floor's own are waited for: what a summoner calls up later was never put there by the floor. A floor
 * that put none outside lays the key at once, where the heroes came in.
 */
public final class Mission {

    /** The four steps, in the order a floor asks them. */
    public enum Step {
        /** Kill everything the floor put outside the keep. */
        CLEAR,
        /** Take the key the last of them left. */
        TAKE,
        /** Give it to the gate. */
        GIVE,
        /** Kill the boss: the gate is open, or there was no keep. */
        KILL
    }

    private final Loot key;
    /** What a thing lies in when it names nothing of its own; the key names itself. */
    private final String chest;
    private final int floorOwner;
    /** The gate across the keep's doorway, or null where the floor stood none. */
    private final ObjectId gate;
    /** How many the floor put outside the keep, and how many of them have died. */
    private final int outside;
    private int killed;
    private boolean keyLaid;

    private Mission(Loot key, String chest, int floorOwner, ObjectId gate, int outside) {
        this.key = key;
        this.chest = chest;
        this.floorOwner = floorOwner;
        this.gate = gate;
        this.outside = outside;
    }

    /**
     * The mission of a floor just laid, or null for one with no keep — a stage, or a floor where none fitted — or a
     * game with no key to give. Every monster placed outside the keep is told to report its death; a floor that put
     * none there lays the key now, where the first hero came in.
     */
    static Mission of(World world, GeneratedDungeon floor, Spawner.Placed placed, DungeonSettings settings,
            int floorOwner) {
        var keep = floor.keep();
        var key = settings.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElse(null);
        if (keep == null || key == null) {
            return null;
        }
        var theirs = new ArrayList<GameObject>();
        for (var monster : placed.monsters()) {
            var at = new GeneratedDungeon.Placement(monster.getPosition().x(), monster.getPosition().y());
            if (!keep.holds(at.cellX(), at.cellY())) {
                theirs.add(monster);
            }
        }
        var mission = new Mission(key, settings.lootDrops().template(), floorOwner,
                placed.gate() == null ? null : placed.gate().getId(), theirs.size());
        for (var monster : theirs) {
            monster.addModule(new Counted(monster, mission));
        }
        if (theirs.isEmpty()) {
            mission.lay(world, wayIn(floor, placed));
        }
        return mission;
    }

    /** Where the heroes came in: where the first of them stands, or the floor's own way in. */
    private static Coord3D wayIn(GeneratedDungeon floor, Spawner.Placed placed) {
        for (var hero : placed.heroes()) {
            if (hero != null) {
                return hero.getPosition();
            }
        }
        return new Coord3D(floor.hero().x(), floor.hero().y(), 0f);
    }

    /** One of the floor's own outside the keep has died: the last of them leaves the key where it fell. */
    private void fell(GameObject monster) {
        killed++;
        if (killed == outside && !keyLaid) {
            lay(monster.getWorld(), monster.getPosition());
        }
    }

    private void lay(World world, Coord3D at) {
        keyLaid = true;
        GroundItem.lay(world, key.liesAs(chest), key, at, floorOwner);
    }

    /** Where the party stands in it now: the key not laid yet, lying, carried, or given and the gate open. */
    public Step step(World world, List<LootBag> bags) {
        if (!keyLaid) {
            return Step.CLEAR;
        }
        if (gate != null && world.findObject(gate) != null) {
            return bags.stream().anyMatch(bag -> bag.holds(LootKind.KEY)) ? Step.GIVE : Step.TAKE;
        }
        return Step.KILL;
    }

    /** What the tracker says at {@code step}, in the run's words: the count while there is one. */
    public String words(Run run, Step step) {
        return switch (step) {
            case CLEAR -> run.clearWord(killed, outside);
            case TAKE -> run.takeKeyWord();
            case GIVE -> run.giveKeyWord();
            case KILL -> run.killBossWord();
        };
    }

    /** How many monsters the floor put outside its keep. */
    public int outside() {
        return outside;
    }

    /** How many of them have died. */
    public int killed() {
        return killed;
    }

    /** On each monster the floor put outside the keep: its death, told to the mission it belongs to. */
    @ModuleGroup(RtsModuleGroups.ECONOMY)
    static final class Counted extends Module implements DieModule {

        private final Mission mission;

        Counted(GameObject monster, Mission mission) {
            super(monster);
            this.mission = mission;
        }

        @Override
        public void onDie() {
            mission.fell(getOwner());
        }
    }
}
```

- [ ] **Step 8: Kept by the run — `DungeonRun.java`**

Import `uz.dukeengine.dungeon.loot.KeyDrop`. After `private GeneratedDungeon gathering;`:

```java

    /** What this floor asks of the party, or {@code null} for a floor with no keep — see {@link Mission}. */
    private Mission mission;
    /** Where it stands, worked out every frame the floor is played: the last step on a floor with none. */
    private Mission.Step step = Mission.Step.KILL;
    /** The tracker's words for it, read by the window: written whole, on the simulation's thread, every frame. */
    private volatile String tracker = "";
```

In `lay`, after `bossId = placed.boss() == null ? null : placed.boss().getId();`, and in `descend`, between
`bossId = placed.boss() == null ? null : placed.boss().getId();` and `look = lookOf(floor);`:

```java
        onTheFloor(game, floor, placed);
```

after `lay`:

```java
    /**
     * What a floor just laid asks of the party: its mission, if it has a keep, and of every hero that he leave the
     * key where he falls, so a party never loses the way on with him.
     */
    private void onTheFloor(DukeGame game, GeneratedDungeon floor, Spawner.Placed placed) {
        for (int i = 0; i < seats.size(); i++) {
            var hero = placed.heroes().get(i);
            if (hero != null) {
                hero.addModule(new KeyDrop(hero, seats.get(i).progress.getLoot(), settings.lootDrops().template(),
                        dungeonPlayer.getIndex()));
            }
        }
        mission = Mission.of(game.getLogic(), floor, placed, settings, dungeonPlayer.getIndex());
        track(game);
    }

    /** Where the mission stands this frame, and the tracker's words for it. */
    private void track(DukeGame game) {
        step = mission == null ? Mission.Step.KILL
                : mission.step(game.getLogic(), seats.stream().map(seat -> seat.progress.getLoot()).toList());
        tracker = mission == null ? settings.run().killBossWord() : mission.words(settings.run(), step);
    }
```

at the end of `whileRunning`, `showStatus(game);` becomes:

```java
        track(game);
        showStatus(game);
```

and after `getDepth()`:

```java

    /** What this floor asks of the party, or {@code null} for a floor with no keep. */
    public Mission getMission() {
        return mission;
    }

    /** Where this floor's mission stands: the last step on a floor with none. */
    public Mission.Step getStep() {
        return step;
    }

    /** The mission's step in words, as the tracker at the top of the window says it — from any thread. */
    public String getTracker() {
        return tracker;
    }
```

- [ ] **Step 9: Run the test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.MissionTest"`
Expected: PASS, 7 tests.

- [ ] **Step 10: Run the whole suite**

Run: `./gradlew test`
Expected: PASS — `EveryModuleIsGroupedTest` finds `KeyDrop` and `Mission.Counted` in `Economy`; the run, the party
and the depth tests lay floors through the new `onTheFloor`.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/run/Mission.java src/main/java/uz/dukeengine/dungeon/loot/KeyDrop.java src/main/java/uz/dukeengine/dungeon/loot/GroundItem.java src/main/java/uz/dukeengine/dungeon/run/Spawner.java src/main/java/uz/dukeengine/dungeon/world/Run.java src/main/resources/data/world/world.duke src/main/java/uz/dukeengine/dungeon/run/DungeonRun.java src/test/java/uz/dukeengine/dungeon/run/MissionTest.java
git commit -m "A floor with a keep sets its mission: clear outside, take the key, give it to the gate, kill the boss" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: On the screen — the tracker, and the hero's lines in a bubble

**Files:**
- Create: `src/main/java/uz/dukeengine/dungeon/MissionScreen.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/loot/LootBag.java` (class Javadoc; the note's fields, `say`, `clear`,
  `noteAt`)
- Modify: `src/main/java/uz/dukeengine/dungeon/BagScreen.java` (`paint`, after `if (!shown) { return; }`)
- Modify: `README.md` (`### Boss qal'asi`)
- Test: `src/test/java/uz/dukeengine/dungeon/MissionScreenTest.java` (new)

**Interfaces:**
- Consumes: `DungeonRun.getTracker()`, `DungeonRun.progressOf(int)` (Task 8); `Canvas.barOf(int)`,
  `WorldSnapshot.units()` from the engine.
- Produces: `static void MissionScreen.paint(Canvas, Dungeon.Session, DungeonSettings)`; `LootBag.noteAt(int)` safe
  from any thread.

- [ ] **Step 1: Write the failing test — `MissionScreenTest.java`**

```java
package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** What the game says over the world: the mission's step at the top, and what a hero says over his head. */
class MissionScreenTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Every line drawn and where, on a window 1600 by 900 where every thing's bar stands at one place. */
    private static final class Drawn implements Canvas {

        record Line(String text, float x, float y) {
        }

        final List<Line> lines = new ArrayList<>();
        final Box bar;

        Drawn(Box bar) {
            this.bar = bar;
        }

        Line line(String text) {
            return lines.stream().filter(line -> line.text().equals(text)).findFirst().orElse(null);
        }

        @Override
        public int width() {
            return 1600;
        }

        @Override
        public int height() {
            return 900;
        }

        @Override
        public void drawImage(Image image, float x0, float y0, float x1, float y1, int argb, Blend blend) {
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
        }

        @Override
        public void openRect(float x, float y, float w, float h, float lineWidth, int argb) {
        }

        @Override
        public void fillTriangle(float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        }

        @Override
        public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        }

        @Override
        public void line(float x0, float y0, float x1, float y1, float width, int argb, int argbEnd) {
        }

        @Override
        public void clip(float x0, float y0, float x1, float y1) {
        }

        @Override
        public void noClip() {
        }

        @Override
        public void drawText(Font font, String text, float x, float y, int argb) {
            lines.add(new Line(text, x, y));
        }

        @Override
        public Measure measure(Font font, String text) {
            return new Measure(text.length() * 8, font.pixelHeight() + 4);
        }

        @Override
        public Box barOf(int id) {
            return bar;
        }
    }

    @Test
    void theTrackerSaysTheStepAtTheTopOfTheScreen() {
        var session = Dungeon.newSession(21L);
        session.game().runHeadless(2);
        var drawn = new Drawn(null);

        MissionScreen.paint(drawn, session, SETTINGS);

        var tracker = drawn.line(session.run().getTracker());
        assertNotNull(tracker, "the step is not drawn: " + drawn.lines);
        assertTrue(tracker.y() < 90f, "and it is not at the top: " + tracker);
        assertTrue(session.run().getTracker().endsWith("0/" + session.run().getMission().outside()),
                "with its count: " + session.run().getTracker());
    }

    @Test
    void whatAHeroSaysIsSaidOverHisHeadAboveHisBar() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var words = SETTINGS.lootDrops().fullWord();
        session.progress().getLoot().say(words, game.getLogic().getFrame(), 90);
        game.runHeadless(1);
        var drawn = new Drawn(new Canvas.Box(700f, 400f, 60f, 8f));

        MissionScreen.paint(drawn, session, SETTINGS);

        var said = drawn.line(words);
        assertNotNull(said, "he says nothing: " + drawn.lines);
        assertTrue(said.y() < 400f, "above his bar: " + said);
        assertTrue(said.x() < 730f && said.x() + words.length() * 8 > 730f, "and over it: " + said);
    }

    @Test
    void onceItHasBeenSaidTheBubbleIsGone() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var words = SETTINGS.lootDrops().fullWord();
        session.progress().getLoot().say(words, game.getLogic().getFrame(), 1);
        game.runHeadless(3);
        var drawn = new Drawn(new Canvas.Box(700f, 400f, 60f, 8f));

        MissionScreen.paint(drawn, session, SETTINGS);

        assertNull(drawn.line(words), "it is still being said: " + drawn.lines);
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.MissionScreenTest"`
Expected: FAIL — compilation error: `cannot find symbol: variable MissionScreen`.

- [ ] **Step 3: A note the window may read — `LootBag.java`**

In the class Javadoc, replace

```java
 * <p>Written on the simulation thread only. The window reads {@link #slots()}, a copy taken each time the bag
 * changes, so it never sees one half-changed.
```

with

```java
 * <p>Written on the simulation thread only. The window reads {@link #slots()}, a copy taken each time the bag
 * changes, and {@link #noteAt}, whose words and frame are one value — so it never sees either half-changed.
```

Replace

```java
    /** The words for what he just picked up, and the frame they stop being said. */
    private String note = "";
    private int noteUntilFrame;
```

with

```java
    /** What he is saying, and the frame it stops being said: one value, so both are always read together. */
    private record Note(String words, int untilFrame) {
    }

    /** What he just picked up, why he left it lying, what he makes of the gate: the window reads it too. */
    private volatile Note note = new Note("", 0);
```

`say`'s body becomes `note = new Note(words, frame + noteFrames);`; in `clear`, `note = "";` and
`noteUntilFrame = 0;` become `note = new Note("", 0);`; and `noteAt` becomes:

```java
    /** What he is saying at {@code frame}, or "" once it has been said — from any thread. */
    public String noteAt(int frame) {
        var now = note;
        return frame < now.untilFrame() ? now.words() : "";
    }
```

- [ ] **Step 4: `MissionScreen.java`**

```java
package uz.dukeengine.dungeon;

import java.util.stream.Collectors;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * What the game says over the world: the floor's mission at the top of the window, a step at a time, and what each
 * hero says in a bubble over his head, above his bar.
 *
 * <p>Drawn on the game's own canvas with the bag, on the window's thread, and only reading: the tracker's words are
 * the run's, written whole on the simulation's thread every frame ({@code DungeonRun.getTracker}); a hero's are his
 * bag's note ({@code LootBag.say}) — what he makes of the gate, why he kept the key, that the bag is full, what he
 * just picked up — and where he is on the screen is where the client put his bar this frame ({@link Canvas#barOf}).
 */
final class MissionScreen {

    private MissionScreen() {
    }

    /** The tracker, and a bubble over every hero with something to say. */
    static void paint(Canvas canvas, Dungeon.Session match, DungeonSettings settings) {
        var look = settings.menu();
        var words = match.run().getTracker();
        if (!words.isEmpty()) {
            tracker(canvas, words, look);
        }
        var heroes = settings.heroes().stream().map(hero -> hero.name()).collect(Collectors.toUnmodifiableSet());
        var snapshot = match.game().getSnapshot();
        for (var unit : snapshot.units()) {
            var progress = heroes.contains(unit.templateName()) ? match.run().progressOf(unit.playerIndex()) : null;
            var said = progress == null ? "" : progress.getLoot().noteAt(snapshot.frame());
            var bar = said.isEmpty() ? null : canvas.barOf(unit.id());
            if (bar != null) {
                bubble(canvas, said, bar, look);
            }
        }
    }

    /** The step, on a slab of stone at the top of the window, in the middle. */
    private static void tracker(Canvas canvas, String words, MenuStyle look) {
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.024f), 14, 30)).bold(true);
        var size = canvas.measure(font, words);
        float pad = font.pixelHeight() * 0.6f;
        float wide = size.width() + pad * 2f;
        float high = size.lineHeight() + pad;
        float left = (canvas.width() - wide) / 2f;
        float top = canvas.height() * 0.02f;
        canvas.fillRect(left, top, wide, high, 0xE0000000 | look.stoneColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());
        canvas.drawText(font, words, left + pad, top + pad / 2f, 0xFF000000 | look.torchColour());
    }

    /** His words over his bar, the bubble's tail pointing down at it, kept on the screen. */
    private static void bubble(Canvas canvas, String words, Canvas.Box bar, MenuStyle look) {
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.018f), 12, 22));
        var size = canvas.measure(font, words);
        float pad = font.pixelHeight() * 0.5f;
        float wide = size.width() + pad * 2f;
        float high = size.lineHeight() + pad;
        float middle = bar.x() + bar.width() / 2f;
        float left = Math.clamp(middle - wide / 2f, 4f, Math.max(4f, canvas.width() - wide - 4f));
        float top = bar.y() - high - pad;
        canvas.fillRect(left, top, wide, high, 0xF0000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 1.5f, 0xFF000000 | look.stoneEdgeColour());
        canvas.fillTriangle(middle - pad, top + high, middle + pad, top + high, middle, top + high + pad,
                0xF0000000 | look.stoneDeepColour());
        canvas.drawText(font, words, left + pad, top + pad / 2f, 0xFF000000 | look.boneColour());
    }
}
```

- [ ] **Step 5: Painted with the bag — `BagScreen.java`**

In `paint`, after

```java
        if (!shown) {
            return;
        }
```

add

```java
        // The floor's mission at the top, and what the heroes say over their heads -- under the bag and its cards.
        MissionScreen.paint(canvas, match, settings);
```

- [ ] **Step 6: The README says what the keep is now — `README.md`, `### Boss qal'asi`**

Replace the first paragraph's

```
Har qavat oxirida boss **qal'ada** turadi (`gen/Keep`): toshning ichiga
qurilgan kvadrat hovli, bir qavat baland (`levelMap` da `1`), atrofi devor —
qaysi biomda bo'lmasin, tosh plitadan (`data/world/themes/keep.duke`).
Darvoza yaqinlashish tomonidagi devorning o'rtasida (3 katak), oldida zina
(`/`), zinadan kameragacha to'g'ri yo'l. Qal'a eng chuqur kamera yonidagi
```

with

```
Har qavat oxirida boss **qal'ada** turadi (`gen/Keep`): toshning ichiga
qurilgan kvadrat hovli, yer sathida (`levelMap` da `0`), atrofi bir qavat
baland devor — qaysi biomda bo'lmasin, tosh plitadan
(`data/world/themes/keep.duke`). Darvoza yaqinlashish tomonidagi devorning
o'rtasida (3 katak), oldida qal'a toshidan ostona (3 katak), ostonadan
kameragacha to'g'ri yo'l. To'rt mag hovlining burchaklarida, har devordan bir
katak ichkarida turadi. Qal'a eng chuqur kamera yonidagi
```

replace

```
`ClipMode.ONCE`). Hozircha qahramon yaqinlashganda ochiladi; keyingi qadamda
kalit bilan ochiladi. Stage'lar qal'asiz kesiladi — stage fayli qal'aning
ko'rinishini saqlamaydi.
```

with

```
`ClipMode.ONCE`). Darvoza o'zi ochilmaydi: uni faqat kalit ochadi. Stage'lar
qal'asiz kesiladi — stage fayli qal'aning ko'rinishini saqlamaydi.

Qal'ali qavatning **vazifasi** bor (`run/Mission`), ekranning tepasida
yoziladi: qal'adan tashqaridagi barcha monstrlarni o'ldirish (hisobi bilan),
oxirgisi yiqilgan joyda qolgan kalitni olish, uni darvozaga berish, bossni
o'ldirish. Kalit (`world.duke` dagi `Kind = KEY`, `Use = UNLOCK` li
`LootItem`) hech narsa bermaydi, birlashmaydi va tasodifan tushmaydi. Sumkada
kalitga chap tugma, keyin darvozaga bosilsa, qahramon borib kalitni darvozaga
beradi. Darvozaga o'ng tugma bosilsa, qahramon borib, kaliti bor-yo'qligini
boshi ustidagi pufakchada aytadi. Kalitni ko'targan qahramon yiqilsa, kalit
o'sha joyda qoladi.
```

- [ ] **Step 7: Run the test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.MissionScreenTest" --tests "uz.dukeengine.dungeon.BagScreenTest"`
Expected: PASS — `BagScreenTest`'s canvas places no bar, so it gains the tracker's line and no bubble.

- [ ] **Step 8: Run the whole suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/MissionScreen.java src/main/java/uz/dukeengine/dungeon/loot/LootBag.java src/main/java/uz/dukeengine/dungeon/BagScreen.java README.md src/test/java/uz/dukeengine/dungeon/MissionScreenTest.java
git commit -m "The mission's step at the top of the screen, and what a hero says in a bubble over his head" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
