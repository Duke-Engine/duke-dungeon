# The boss's keep — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every floor of the descent ends in a keep — a square walled court one storey up, built in solid rock beside
the deepest chamber, its gate in the middle of the side a straight road and a stair come up to — drawn in worked
stone whatever the biome, with the owner's gate model standing in the doorway until a hero walks up to it, and then
swinging open.

**Architecture:** `gen/Keep` is the keep's geometry (walls, doorway, stair, court, road) and its placement rule,
searched over the carved `Cave` with a summed-area table of rock. `Cave.raise` builds it in and `Cave.levels` writes
its storey; `DungeonGenerator` appends it as the last room and the boss room, lists the gate among the props, and
`Relief` sets it level. The gate is an `Object` template with a `Box` and a `GateUpdate` module; the spawner turns it
across its doorway, and opening swaps it for an `OpenGate` with no shape, whose look plays the model's `open` clip
once (`Visuals.ClipMode.ONCE`). The Keep look is a masonry theme the floor's map record answers for the keep's
cells.

**Tech Stack:** Java 25, JUnit 5, Gradle (`./gradlew test`), `.duke` data read by the engine's record reader,
duke-engine 0.7.0 from the checkout beside this one.

**Spec:** `docs/plan/2026-09-30-boss-keep.md`

## Global Constraints

- Lock-step: all of it a pure function of seed, depth and data — whole numbers, fixed iteration order, no clock,
  no hash iteration; `Keep.site` and `Cave.raise` draw nothing from any `DeterministicRng`.
- No `Keep` block in `generation.duke` (`Sizes = []`, the default) → every floor exactly as today: the boss in the
  furthest chamber, open, storey 0 everywhere.
- Stages (`Layout.sized`, `mixesBiomes == false`) are cut without a keep; `first.map` and `deep.map` are not
  redrawn.
- Nothing needs the engine. `../duke-engine` is never edited from here.
- Names come from data (`Keep.Gate`, `Keep.Look`); no template or theme name is compiled in.
- Each task is committed on its own once its tests pass — the owner's word for this plan ("har bitta ishni alohida
  alohida commit qilib ketaver"). Commit messages end with the `Co-Authored-By` line the repository uses.
- The gate model is the owner's animated `gate.glb` (about 11.6 MB with its textures): two clips, `left_open` and
  `right_open`, joined into one `open` in the game's copy by `art/models/join_clips.pl`; nothing else in it changes.

## Files

| File | What it is |
|---|---|
| `src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java` | + `Keep(sizes, gate, look)` settings record |
| `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` | + `keep()`, its checks |
| `src/main/java/uz/dukeengine/dungeon/gen/Keep.java` | **new** — the keep's geometry and where it goes |
| `src/main/java/uz/dukeengine/dungeon/gen/Cave.java` | + `isStone`, `raise`, `keep`; `levels` writes the keep's storey |
| `src/main/java/uz/dukeengine/dungeon/gen/GeneratedDungeon.java` | + `keep` component |
| `src/main/java/uz/dukeengine/dungeon/gen/BiomeMap.java` | + `withRoom` |
| `src/main/java/uz/dukeengine/dungeon/gen/DungeonGenerator.java` | builds the keep in, the boss and gate with it |
| `src/main/java/uz/dukeengine/dungeon/gen/Scenery.java` | nothing strewn on the keep |
| `src/main/java/uz/dukeengine/dungeon/gen/Relief.java` | the keep set level |
| `src/main/java/uz/dukeengine/dungeon/stage/StageCheck.java` | the gate is a thing a floor may hold |
| `src/main/java/uz/dukeengine/dungeon/run/GateUpdate.java` | **new** — opens when a hero walks up |
| `src/main/java/uz/dukeengine/dungeon/run/Spawner.java` | turns the gate across its doorway |
| `src/main/java/uz/dukeengine/dungeon/run/FloorLooks.java` | + the keep's look |
| `src/main/java/uz/dukeengine/dungeon/run/DungeonRun.java` | hands the keep's look over |
| `src/main/java/uz/dukeengine/dungeon/content/Content.java`, `.../Dungeon.java` | register `GateUpdate` |
| `src/main/resources/data/world/generation.duke` | + `Keep` block |
| `src/main/resources/data/props/gate.duke` | **new** — the gate template |
| `src/main/resources/data/world/themes/keep.duke` | **new** — the masonry look |
| `src/main/resources/data/game.duke` | lists the two new files |
| `src/main/resources/models/props/gate/` | **new** — `gate.glb`, `License.txt` |
| `art/models/join_clips.pl` | **new** — joins a model's clips into one |
| `src/main/java/uz/dukeengine/dungeon/world/Theme.java` | + `ThemeMonster.playOnce` |
| `src/main/java/uz/dukeengine/dungeon/Main.java` | a themed thing's `PlayOnce` played once and held |
| `CREDITS.md`, `README.md` | the gate's author; a section on the keep |

---

### Task 1: The `Keep` block in the settings

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (accessor beside `biomes()`, checks in
  `validate()` after the two `propsPerRoom` checks, ~line 596)
- Modify: `src/main/resources/data/world/generation.duke` (a block after `PropsPerRoom = [0, 3]`)
- Test: `src/test/java/uz/dukeengine/dungeon/content/KeepSettingsTest.java`

**Interfaces:**
- Produces: `ProceduralMap.Keep(List<Integer> sizes, String gate, String look)` with `Keep.DEFAULTS` (no sizes,
  blank gate and look); `DungeonSettings.keep()` returning it.

- [ ] **Step 1: Write the failing test**

```java
package uz.dukeengine.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** What a map says about the boss's keep, and what it may not say. */
class KeepSettingsTest {

    /** The shipped descent builds one on every floor, in four sizes, with a gate in it. */
    @Test
    void theShippedDescentBuildsAKeepWithAGate() {
        var keep = DungeonSettings.load().keep();

        assertEquals(List.of(15, 13, 11, 9), keep.sizes());
        assertEquals("Gate", keep.gate());
    }

    /** A map that says nothing about a keep builds none. */
    @Test
    void aMapThatSaysNothingBuildsNone() {
        var data = Content.data().replaceFirst("(?s)  Keep = Keep\\n.*?\\n  End\\n", "");

        assertTrue(DungeonSettings.parse(data).keep().sizes().isEmpty());
    }

    /** Odd, so the boss and the gate each have a middle cell. */
    @Test
    void anEvenSizeIsRefusedByNumber() {
        var data = Content.data().replace("    Sizes = [15, 13, 11, 9]\n", "    Sizes = [15, 14]\n");

        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
        assertTrue(refused.getMessage().contains("14"), refused.getMessage());
    }

    /** Something has to stand in the doorway. */
    @Test
    void aKeepWithNoGateIsRefused() {
        var data = Content.data().replace("    Gate = Gate\n", "");

        assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data));
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.content.KeepSettingsTest"`
Expected: FAIL — compilation error, `keep()` is not a method of `DungeonSettings`.

- [ ] **Step 3: Add the record to `ProceduralMap`**

Add `keep` to the record's parameter documentation, make it the last component, give it a default, and nest the
record beside `Descent`:

```java
 * @param climatePerDepth how far the whole floor's climate drifts per floor down, wild then alive
 * @param keep          the boss's keep at the end of every floor — see {@link Keep}
 */
public record ProceduralMap(String name, Layout generation, @Link(Theme.class) List<String> themes,
        Themes.WhenExhausted whenExhausted,
        PerRoom propsPerRoom, Descent descent, @Link(Theme.class) List<String> biomes, int biomeSize,
        Theme.Climate climatePerDepth, Keep keep) {

    /** What a block leaves out. */
    public static final ProceduralMap DEFAULTS = new ProceduralMap("", Layout.DEFAULTS, List.of(),
            Themes.WhenExhausted.REPEAT, new PerRoom(0, 3), Descent.DEFAULTS, List.of(), 40,
            new Theme.Climate(0, 0), Keep.DEFAULTS);

    public ProceduralMap {
        themes = themes == null ? List.of() : List.copyOf(themes);
        biomes = biomes == null ? List.of() : List.copyOf(biomes);
        climatePerDepth = climatePerDepth == null ? new Theme.Climate(0, 0) : climatePerDepth;
        keep = keep == null ? Keep.DEFAULTS : keep;
    }
```

```java
    /**
     * The boss's keep: a walled court a storey up at the far end of each floor of the descent, its gate in the
     * middle of the side it is approached from — see {@code gen/Keep}.
     *
     * @param sizes how many cells across it may be, walls included, tried largest first; odd, so the boss and the
     *     gate each have a middle cell. None, and the boss waits in the furthest chamber, open, as it always did
     * @param gate  what stands in its doorway until it is opened: a template in {@code data/props/}
     * @param look  the theme it is drawn in whatever biome it stands in; blank leaves it the biome's
     */
    public record Keep(List<Integer> sizes, String gate, @Link(Theme.class) String look) {

        /** What a block leaves out: no keep at all. */
        public static final Keep DEFAULTS = new Keep(List.of(), "", "");

        public Keep {
            sizes = sizes == null ? List.of() : List.copyOf(sizes);
            gate = gate == null ? "" : gate;
            look = look == null ? "" : look;
        }
    }
```

- [ ] **Step 4: Add the accessor and the checks to `DungeonSettings`**

Beside `biomes()`:

```java
    /** The boss's keep, as the map asks for it — see {@link ProceduralMap.Keep}. */
    public ProceduralMap.Keep keep() {
        return map.keep();
    }
```

In `validate()`, right after the `MaxPerRoom must not be below MinPerRoom` check:

```java
        for (int size : map.keep().sizes()) {
            require(size >= 5 && size % 2 == 1, "a Keep is odd and at least 5 across, so its court and its doorway"
                    + " have middle cells: " + size);
            require(size + 4 <= Math.min(map.generation().mapWidth(), map.generation().mapHeight()),
                    "a Keep " + size + " across cannot stand on the map with rock round it");
        }
        require(map.keep().sizes().isEmpty() || !map.keep().gate().isBlank(),
                "a Keep needs a Gate: something has to stand in its doorway");
```

- [ ] **Step 5: Write the block into `generation.duke`**

After `PropsPerRoom = [0, 3]` and before `Descent = Descent`:

```
  ; ---- the boss's keep ----
  ;
  ; Every floor of the descent ends in one: a square court a storey above the
  ; ground, walled round, its gate in the middle of the side a straight road and
  ; a stair come up to. Built in solid rock beside the deepest chamber with room
  ; for it, so it cuts nothing off -- see gen/Keep and
  ; docs/plan/2026-09-30-boss-keep.md. The boss waits in its middle and its
  ; guard round it. Stages are cut without one. Leave the block out and the boss
  ; waits in the furthest chamber, open, as it always did.
  Keep = Keep
    ; Across, walls included, largest first: the larger ones are tried only
    ; beside the deepest chambers, the last beside any. Odd, so the boss and the
    ; gate each have a middle cell.
    Sizes = [15, 13, 11, 9]
    ; What stands in its doorway until it is opened: data/props/gate.duke.
    Gate = Gate
  End
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.content.*"`
Expected: PASS, `KeepSettingsTest` included.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/map/ProceduralMap.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/resources/data/world/generation.duke src/test/java/uz/dukeengine/dungeon/content/KeepSettingsTest.java
git commit -m "A map may ask for a keep at the end of every floor" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The keep's shape, and where it goes

**Files:**
- Create: `src/main/java/uz/dukeengine/dungeon/gen/Keep.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/Cave.java` (+ `isStone`, beside `isFloor`)
- Test: `src/test/java/uz/dukeengine/dungeon/gen/KeepShapeTest.java`

**Interfaces:**
- Consumes: `GeneratedDungeon.Room`, `GeneratedDungeon.Link`, `Cave.carve(...)`, `Cave.width()`, `Cave.height()`.
- Produces: `public record Keep(Room walls, Keep.Side side, int chamber)`; `public enum Keep.Side {NORTH, EAST,
  SOUTH, WEST}`; public `int size()`, `int[] gate()`, `float facing()`, `boolean isDoorway(int, int)`,
  `boolean isCourt(int, int)`, `boolean isStair(int, int)`, `boolean holds(int, int)`; package-private
  `char storeyAt(int, int)`, `int[] roadStart()`, `static Keep site(Cave, List<Room>, List<Link>, List<Integer>,
  int)`; `boolean Cave.isStone(int, int)`.

- [ ] **Step 1: Write the failing test**

```java
package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;
import uz.dukeengine.dungeon.world.Theme;

/** The keep's cells — wall, doorway, stair, court — and the rule for where one goes. */
class KeepShapeTest {

    /** Nine across at (10, 10), its gate in the north wall. */
    private static final Keep NORTH = new Keep(new Room(10, 10, 9, 9), Keep.Side.NORTH, 0);

    @Test
    void itsGateStandsInTheMiddleOfItsSideInADoorwayThreeWide() {
        assertArrayEquals(new int[] {14, 10}, NORTH.gate());
        for (int x = 10; x < 19; x++) {
            assertEquals(x >= 13 && x <= 15, NORTH.isDoorway(x, 10), "cell " + x + ",10");
        }
        assertFalse(NORTH.isDoorway(14, 11), "the doorway is in the wall, not the court");
    }

    @Test
    void itsStairIsTheStepOutsideItsDoorwayAndItsRoadLeavesBeyondIt() {
        for (int x = 13; x <= 15; x++) {
            assertTrue(NORTH.isStair(x, 9), "cell " + x + ",9");
        }
        assertFalse(NORTH.isStair(12, 9), "beside the stair is the ground outside");
        assertFalse(NORTH.isStair(14, 11), "inside the doorway is the court");
        assertArrayEquals(new int[] {14, 8}, NORTH.roadStart());
    }

    @Test
    void itsCourtIsInsideItsRingAStoreyUp() {
        assertTrue(NORTH.isCourt(11, 11));
        assertTrue(NORTH.isCourt(17, 17));
        assertFalse(NORTH.isCourt(10, 14), "the ring is wall");
        assertFalse(NORTH.isCourt(18, 14));
        assertEquals('1', NORTH.storeyAt(14, 14));
        assertEquals('1', NORTH.storeyAt(14, 10), "the doorway is level with the court");
        assertEquals('/', NORTH.storeyAt(14, 9));
        assertEquals('0', NORTH.storeyAt(14, 8));
    }

    @Test
    void itHoldsItsSquareAndItsStairAndNothingElse() {
        assertTrue(NORTH.holds(10, 10), "a corner of its wall");
        assertTrue(NORTH.holds(18, 18));
        assertTrue(NORTH.holds(14, 9), "its stair");
        assertFalse(NORTH.holds(12, 9));
        assertFalse(NORTH.holds(14, 8), "the road is the floor's");
        assertFalse(NORTH.holds(19, 14));
    }

    @Test
    void aGateInAWallRunningDownTheMapFacesAQuarterTurn() {
        var west = new Keep(new Room(10, 10, 9, 9), Keep.Side.WEST, 0);

        assertArrayEquals(new int[] {10, 14}, west.gate());
        assertTrue(west.isDoorway(10, 13) && west.isDoorway(10, 15));
        assertTrue(west.isStair(9, 14));
        assertArrayEquals(new int[] {8, 14}, west.roadStart());
        assertEquals((float) (StrictMath.PI / 2), west.facing());
        assertEquals(0f, NORTH.facing());
    }

    /** In solid rock beside the deeper of two chambers, its gate toward it, its road no longer than it may be. */
    @Test
    void itGoesInSolidRockBesideTheDeepestChamberFacingIt() {
        var rooms = List.of(new Room(3, 10, 9, 9), new Room(20, 10, 9, 9));
        var links = List.of(new Link(0, 1));
        var cave = Cave.carve(new DeterministicRng(1), 60, 30, rooms, links, 1, 2, 30, Theme.Terrain.DEFAULTS);

        var keep = Keep.site(cave, rooms, links, List.of(9), 30);

        assertNotNull(keep, "a floor that is mostly rock has room for a keep");
        assertEquals(1, keep.chamber(), "beside the deeper of the two");
        var walls = keep.walls();
        for (int y = walls.y() - 1; y <= walls.y() + walls.h(); y++) {
            for (int x = walls.x() - 1; x <= walls.x() + walls.w(); x++) {
                assertTrue(cave.isStone(x, y), "rock at " + x + "," + y + " before it is built");
            }
        }
        var chamber = rooms.get(1);
        int road = Math.abs(chamber.centerCellX() - walls.centerCellX())
                + Math.abs(chamber.centerCellY() - walls.centerCellY()) - walls.w() / 2;
        assertTrue(road <= 30, "its road is no longer than a chamber may stand from another: " + road);
        var gate = keep.gate();
        var out = keep.roadStart();
        assertTrue(Math.abs(out[0] - chamber.centerCellX()) + Math.abs(out[1] - chamber.centerCellY())
                        < Math.abs(gate[0] - chamber.centerCellX()) + Math.abs(gate[1] - chamber.centerCellY()),
                "its gate faces away from its chamber: " + keep);
    }

    /** And none where the rock has no square big enough. */
    @Test
    void thereIsNoneWhereTheRockHasNoRoom() {
        var rooms = List.of(new Room(2, 2, 11, 11), new Room(14, 2, 11, 11));
        var links = List.of(new Link(0, 1));
        var cave = Cave.carve(new DeterministicRng(1), 27, 15, rooms, links, 1, 2, 30, Theme.Terrain.DEFAULTS);

        assertNull(Keep.site(cave, rooms, links, List.of(15, 9), 30));
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.KeepShapeTest"`
Expected: FAIL — compilation error, no class `Keep`.

- [ ] **Step 3: Add `isStone` to `Cave`**, right after `isFloor`:

```java
    /** Whether a cell is rock: not floor, and not a grove's ground either. */
    boolean isStone(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && cells[y][x] == STONE;
    }
```

- [ ] **Step 4: Write `Keep.java`**

```java
package uz.dukeengine.dungeon.gen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;

/**
 * The boss's keep: a square court a storey above the floor, walled round, at the far end of it — a gate in the middle
 * of the side it is approached from, a stair up to the gate, and a straight road from the stair to the chamber it
 * hangs off.
 *
 * <p>Built in solid rock once the cave is carved (see {@link #site}), so it takes no floor from anything and cuts no
 * tunnel: whatever was connected stays connected, and the only way into the court is up the stair and through the
 * gate. Whole numbers and a fixed order throughout, and no dice: a seed names one keep.
 *
 * @param walls   the square it stands in, its ring of wall included — odd across, so the court and the doorway each
 *     have a middle cell
 * @param side    which of its sides the gate is in
 * @param chamber the chamber its road runs to, by its place in the floor's rooms
 */
public record Keep(Room walls, Side side, int chamber) {

    /** The four sides of a keep, each with the way out of it. North is toward row 0, as the map is drawn. */
    public enum Side {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        final int dx;
        final int dy;

        Side(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    /** How far the doorway runs along its wall: the gate's cell and one either side, wider than any tunnel. */
    static final int DOORWAY = 3;

    /** Cells across, walls included. */
    public int size() {
        return walls.w();
    }

    /** The doorway's middle cell, in the ring on the gate's side: where the gate stands. */
    public int[] gate() {
        int middle = size() / 2;
        return switch (side) {
            case NORTH -> new int[] {walls.x() + middle, walls.y()};
            case SOUTH -> new int[] {walls.x() + middle, walls.y() + size() - 1};
            case WEST -> new int[] {walls.x(), walls.y() + middle};
            case EAST -> new int[] {walls.x() + size() - 1, walls.y() + middle};
        };
    }

    /**
     * Which way the gate's wall runs, as a facing in radians: none along the map's x, a quarter turn along its y. A
     * gate turned so stands across its doorway.
     */
    public float facing() {
        return side.dx == 0 ? 0f : (float) (StrictMath.PI / 2);
    }

    /** Whether a cell is the doorway: the gate's own and one either side of it, along the wall. */
    public boolean isDoorway(int x, int y) {
        var gate = gate();
        return side.dx == 0
                ? y == gate[1] && Math.abs(x - gate[0]) <= DOORWAY / 2
                : x == gate[0] && Math.abs(y - gate[1]) <= DOORWAY / 2;
    }

    /** Whether a cell is the court: inside the ring. */
    public boolean isCourt(int x, int y) {
        return x > walls.x() && y > walls.y() && x < walls.x() + size() - 1 && y < walls.y() + size() - 1;
    }

    /** Whether a cell is the stair: a step out from the doorway. */
    public boolean isStair(int x, int y) {
        return isDoorway(x - side.dx, y - side.dy);
    }

    /** Whether a cell belongs to the keep — its square or its stair: what is drawn as the keep, and strewn with nothing. */
    public boolean holds(int x, int y) {
        return x >= walls.x() && y >= walls.y() && x < walls.x() + size() && y < walls.y() + size() || isStair(x, y);
    }

    /** The storey a floor cell stands on, as the level map writes it: court and doorway one up, the stair a stair. */
    char storeyAt(int x, int y) {
        return isCourt(x, y) || isDoorway(x, y) ? '1' : isStair(x, y) ? '/' : '0';
    }

    /** Where the road leaves from: beyond the stair's middle, two cells out from the gate. */
    int[] roadStart() {
        var gate = gate();
        return new int[] {gate[0] + 2 * side.dx, gate[1] + 2 * side.dy};
    }

    /**
     * Where a keep goes on a carved floor, or null where none fits: in solid rock, beside the deepest chamber that has
     * room for one, as large as it can be there.
     *
     * <p>Every size but the last is tried only beside the deepest chambers — at most one tunnel short of the deepest
     * — largest first; failing that, the deepest chamber any size fits beside, largest first. Beside a chamber the
     * square with the shortest road wins, and of two as short the first in reading order.
     *
     * @param rooms      the chambers, the first where the heroes come in
     * @param links      the tunnels joining them, a tree grown out of the first
     * @param sizes      how many cells across, largest first
     * @param maxSpacing the longest a road may be
     */
    static Keep site(Cave cave, List<Room> rooms, List<Link> links, List<Integer> sizes, int maxSpacing) {
        if (sizes.isEmpty() || rooms.isEmpty()) {
            return null;
        }
        var depth = new int[rooms.size()];
        int deepest = 0;
        for (var link : links) {
            depth[link.to()] = depth[link.from()] + 1;
            deepest = Math.max(deepest, depth[link.to()]);
        }
        var order = new ArrayList<Integer>();
        for (int room = 0; room < rooms.size(); room++) {
            order.add(room);
        }
        order.sort(Comparator.comparingInt((Integer room) -> -depth[room]).thenComparingInt(room -> room));
        var open = openCells(cave);
        for (int s = 0; s < sizes.size() - 1; s++) {
            for (int room : order) {
                if (depth[room] < deepest - 1) {
                    break;
                }
                var keep = beside(cave, open, rooms, room, sizes.get(s), maxSpacing);
                if (keep != null) {
                    return keep;
                }
            }
        }
        for (int room : order) {
            for (int size : sizes) {
                var keep = beside(cave, open, rooms, room, size, maxSpacing);
                if (keep != null) {
                    return keep;
                }
            }
        }
        return null;
    }

    /**
     * The keep {@code size} across beside chamber {@code room} with the shortest road, or null: its square and a cell
     * round it all rock and inside the map's border, its road no longer than {@code maxSpacing} and leaving from
     * inside the border too.
     */
    private static Keep beside(Cave cave, int[] open, List<Room> rooms, int room, int size, int maxSpacing) {
        var chamber = rooms.get(room);
        Keep best = null;
        int shortest = Integer.MAX_VALUE;
        for (int y = 2; y + size + 2 <= cave.height(); y++) {
            for (int x = 2; x + size + 2 <= cave.width(); x++) {
                int road = Math.abs(chamber.centerCellX() - (x + size / 2))
                        + Math.abs(chamber.centerCellY() - (y + size / 2)) - size / 2;
                if (road > maxSpacing || road >= shortest
                        || openWithin(open, cave.width(), x - 1, y - 1, size + 2) > 0) {
                    continue;
                }
                var keep = new Keep(new Room(x, y, size, size), facing(chamber, x + size / 2, y + size / 2), room);
                var start = keep.roadStart();
                if (start[0] < 1 || start[1] < 1 || start[0] > cave.width() - 2 || start[1] > cave.height() - 2) {
                    continue;
                }
                best = keep;
                shortest = road;
            }
        }
        return best;
    }

    /** The side whose way out points most nearly at the chamber's middle; ties go north, east, south, west. */
    private static Side facing(Room chamber, int middleX, int middleY) {
        int dx = chamber.centerCellX() - middleX;
        int dy = chamber.centerCellY() - middleY;
        var best = Side.NORTH;
        int most = Integer.MIN_VALUE;
        for (var side : Side.values()) {
            int toward = side.dx * dx + side.dy * dy;
            if (toward > most) {
                most = toward;
                best = side;
            }
        }
        return best;
    }

    /** How many cells are not rock in every rectangle from the corner: {@code (width + 1)} by {@code (height + 1)}. */
    private static int[] openCells(Cave cave) {
        int across = cave.width() + 1;
        var sums = new int[across * (cave.height() + 1)];
        for (int y = 0; y < cave.height(); y++) {
            for (int x = 0; x < cave.width(); x++) {
                sums[(y + 1) * across + x + 1] = sums[y * across + x + 1] + sums[(y + 1) * across + x]
                        - sums[y * across + x] + (cave.isStone(x, y) ? 0 : 1);
            }
        }
        return sums;
    }

    /** How many cells are not rock in the square {@code side} across from {@code (left, top)}. */
    private static int openWithin(int[] sums, int width, int left, int top, int side) {
        int across = width + 1;
        int right = left + side;
        int bottom = top + side;
        return sums[bottom * across + right] - sums[top * across + right] - sums[bottom * across + left]
                + sums[top * across + left];
    }
}
```

- [ ] **Step 5: Run the test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.KeepShapeTest"`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/gen/Keep.java src/main/java/uz/dukeengine/dungeon/gen/Cave.java src/test/java/uz/dukeengine/dungeon/gen/KeepShapeTest.java
git commit -m "The shape of the boss's keep, and the rock it may be built in" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Every floor of the descent ends in a keep

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/Cave.java` (+ `keep` field, `keep()`, `raise`; `levels`)
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/GeneratedDungeon.java` (+ `keep` component)
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/BiomeMap.java` (+ `withRoom`)
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/DungeonGenerator.java` (`generate`, lines 76–111)
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/Scenery.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/stage/StageCheck.java` (~line 64)
- Modify tests: `gen/DungeonGeneratorTest.java`, `gen/BiomeFloorTest.java`, `gen/PropsTest.java`
- Test: `src/test/java/uz/dukeengine/dungeon/gen/KeepFloorTest.java`

**Interfaces:**
- Consumes: everything Task 2 produces; `DungeonSettings.keep()` (Task 1).
- Produces: `GeneratedDungeon.keep()` (null where there is none); `Cave.keep()`; `Cave.raise(Keep, List<Room>)`;
  `BiomeMap.withRoom(Room)`. On a floor with a keep: `bossRoom() == rooms().size() - 1`,
  `rooms().get(bossRoom()) == keep().walls()`, one link `Link(keep().chamber(), bossRoom())`, a prop
  `Prop(settings.keep().gate(), Placement.atCell(keep().gate()[0], keep().gate()[1]))`,
  `roomStoreys().get(bossRoom()) == 1`.

- [ ] **Step 1: Write the failing test**

```java
package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Link;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Placement;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Prop;

/** Every floor of the descent ends in the boss's keep, and the keep keeps every promise a floor makes. */
class KeepFloorTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int SEEDS = 200;

    private static GeneratedDungeon floor(long seed) {
        return DungeonGenerator.generate(seed, SETTINGS, 1 + (int) (seed % 4));
    }

    @Test
    void everyFloorEndsInAKeepWithItsBossInTheMiddleAndItsGateInTheDoorway() {
        int shortOfTheEnd = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            var floor = floor(seed);
            var keep = floor.keep();
            assertNotNull(keep, "seed " + seed + " has no keep");
            assertTrue(SETTINGS.keep().sizes().contains(keep.size()), "seed " + seed + ": " + keep.size() + " across");
            assertEquals(floor.rooms().size() - 1, floor.bossRoom(), "seed " + seed + ": the keep is the last room");
            assertEquals(keep.walls(), floor.rooms().get(floor.bossRoom()));
            assertEquals(Placement.atCell(keep.walls().centerCellX(), keep.walls().centerCellY()), floor.boss().at(),
                    "seed " + seed + ": the boss waits in the middle of the court");
            int boss = floor.bossRoom();
            assertEquals(List.of(new Link(keep.chamber(), boss)),
                    floor.links().stream().filter(link -> link.from() == boss || link.to() == boss).toList(),
                    "seed " + seed + ": one road, to its own chamber");
            var gate = keep.gate();
            assertTrue(floor.props().contains(new Prop(SETTINGS.keep().gate(), Placement.atCell(gate[0], gate[1]))),
                    "seed " + seed + ": no gate in the doorway");
            // The gate is in the side facing its chamber: stepping out of it goes toward the chamber's middle.
            var chamber = floor.rooms().get(keep.chamber());
            var out = keep.roadStart();
            assertTrue(Math.abs(out[0] - chamber.centerCellX()) + Math.abs(out[1] - chamber.centerCellY())
                            < Math.abs(gate[0] - chamber.centerCellX()) + Math.abs(gate[1] - chamber.centerCellY()),
                    "seed " + seed + ": its gate faces away from its chamber");
            var depth = new int[floor.rooms().size()];
            int deepest = 0;
            for (var link : floor.links()) {
                depth[link.to()] = depth[link.from()] + 1;
                if (link.to() != boss) {
                    deepest = Math.max(deepest, depth[link.to()]);
                }
            }
            if (depth[keep.chamber()] < deepest - 1) {
                shortOfTheEnd++;
            }
        }
        assertTrue(shortOfTheEnd <= SEEDS / 50, shortOfTheEnd + " keeps stood two or more tunnels short of the end");
    }

    /** The last floor's guard stands inside the court, never on its wall or in its doorway. */
    @Test
    void theGuardStandsInTheCourt() {
        int depth = SETTINGS.finalDepth();
        int guards = SETTINGS.bossGuardsAt(depth).stream().mapToInt(guard -> guard.count()).sum();
        for (long seed = 0; seed < 40; seed++) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, depth);
            var keep = floor.keep();
            assertEquals(guards, floor.monsters().stream()
                    .filter(monster -> keep.isCourt(monster.at().cellX(), monster.at().cellY())).count(),
                    "seed " + seed);
        }
    }

    /** Up its stair and through its doorway, and no other way: the engine's own step rule over map and storeys. */
    @Test
    void theCourtIsReachedOnlyThroughItsDoorway() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var floor = floor(seed);
            var keep = floor.keep();
            var open = reached(floor, false);
            var shut = reached(floor, true);
            var walls = keep.walls();
            for (int y = walls.y(); y < walls.y() + walls.h(); y++) {
                for (int x = walls.x(); x < walls.x() + walls.w(); x++) {
                    if (keep.isCourt(x, y)) {
                        assertTrue(open[y][x], "seed " + seed + ": court cell " + x + "," + y + " is out of reach");
                        assertFalse(shut[y][x], "seed " + seed + ": court cell " + x + "," + y
                                + " is reached round the gate");
                    }
                }
            }
            for (int room = 0; room < floor.bossRoom(); room++) {
                var middle = floor.rooms().get(room);
                assertTrue(shut[middle.centerCellY()][middle.centerCellX()],
                        "seed " + seed + ": chamber " + room + " is cut off");
            }
        }
    }

    /** Court and doorway a storey up, a stair before the doorway, a ring of wall, and everything else on the ground. */
    @Test
    void itStandsAStoreyUpWithAStairToItsDoorway() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var floor = floor(seed);
            var keep = floor.keep();
            var levels = floor.levelMap().strip().split("\n");
            var cells = floor.asciiMap().strip().split("\n");
            for (int y = 0; y < levels.length; y++) {
                for (int x = 0; x < levels[y].length(); x++) {
                    boolean wall = keep.holds(x, y) && !keep.isCourt(x, y) && !keep.isDoorway(x, y)
                            && !keep.isStair(x, y);
                    if (wall) {
                        assertEquals('#', cells[y].charAt(x), "seed " + seed + ": the ring is open at " + x + "," + y);
                    }
                    char wanted = cells[y].charAt(x) == '#' ? '#'
                            : keep.isCourt(x, y) || keep.isDoorway(x, y) ? '1' : keep.isStair(x, y) ? '/' : '0';
                    assertEquals(wanted, levels[y].charAt(x), "seed " + seed + " at " + x + "," + y);
                }
            }
            assertEquals(1, floor.roomStoreys().get(floor.bossRoom()));
        }
    }

    /** No grass, pebble or ore on it: it is built. */
    @Test
    void nothingIsStrewnOnIt() {
        for (long seed = 0; seed < 60; seed++) {
            var floor = floor(seed);
            for (var piece : floor.scenery()) {
                assertFalse(floor.keep().holds((int) piece.x(), (int) piece.y()), "seed " + seed + ": " + piece);
            }
        }
    }

    /** A stage keeps no look for one, as it keeps no biome for a cell: it is cut without. */
    @Test
    void aStageIsCutWithoutOne() {
        var stage = DungeonGenerator.generate(3L, SETTINGS, 1,
                Layout.sized(SETTINGS, SETTINGS.mapWidth(), SETTINGS.mapHeight(), SETTINGS.maxRooms()));

        assertNull(stage.keep());
        assertTrue(stage.props().stream().noneMatch(prop -> prop.kind().equals(SETTINGS.keep().gate())));
    }

    @Test
    void theSameSeedBuildsTheSameKeep() {
        assertEquals(floor(7L).keep(), floor(7L).keep());
    }

    /** Every cell the way in walks to by the engine's step rule, with the doorway shut or not. */
    private static boolean[][] reached(GeneratedDungeon floor, boolean shut) {
        var grid = MapLoader.fromText(floor.asciiMap());
        MapLoader.levels(grid, floor.levelMap());
        var keep = floor.keep();
        if (shut) {
            var walls = keep.walls();
            for (int y = walls.y(); y < walls.y() + walls.h(); y++) {
                for (int x = walls.x(); x < walls.x() + walls.w(); x++) {
                    if (keep.isDoorway(x, y)) {
                        grid.setBlocked(x, y, true);
                    }
                }
            }
        }
        var reached = new boolean[grid.getHeight()][grid.getWidth()];
        var queue = new ArrayDeque<int[]>();
        int startX = floor.hero().cellX();
        int startY = floor.hero().cellY();
        reached[startY][startX] = true;
        queue.add(new int[] {startX, startY});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            var at = queue.poll();
            for (var step : steps) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                if (grid.inBounds(x, y) && !reached[y][x] && grid.canStep(at[0], at[1], x, y)) {
                    reached[y][x] = true;
                    queue.add(new int[] {x, y});
                }
            }
        }
        return reached;
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.KeepFloorTest"`
Expected: FAIL — compilation error, `keep()` is not a component of `GeneratedDungeon`.

- [ ] **Step 3: Give `GeneratedDungeon` its keep**

Document it after `scenery` in the class comment:

```java
 * @param keep        the boss's keep — a walled court a storey up, the last of {@code rooms} and the boss's — or
 *                  {@code null} for a floor with none: a stage, a map that asks for none, or one where none fitted
```

Make it the last component, and keep both old constructors, each passing `null` for it:

```java
        BiomeMap biomes,
        List<Piece> scenery,
        Keep keep) {

    /** A floor that wears one theme whole — a stage, or the descent when its map mixes no biomes. */
    public GeneratedDungeon(String asciiMap, String levelMap, Placement hero, List<Monster> monsters, Monster boss,
            int bossRoom, List<Room> rooms, List<Link> links, List<Integer> roomStoreys, List<Prop> props,
            HeightMap relief, float levelHeight) {
        this(asciiMap, levelMap, hero, monsters, boss, bossRoom, rooms, links, roomStoreys, props, relief,
                levelHeight, null, List.of(), null);
    }

    /** A floor with no keep. */
    public GeneratedDungeon(String asciiMap, String levelMap, Placement hero, List<Monster> monsters, Monster boss,
            int bossRoom, List<Room> rooms, List<Link> links, List<Integer> roomStoreys, List<Prop> props,
            HeightMap relief, float levelHeight, BiomeMap biomes, List<Piece> scenery) {
        this(asciiMap, levelMap, hero, monsters, boss, bossRoom, rooms, links, roomStoreys, props, relief,
                levelHeight, biomes, scenery, null);
    }
```

- [ ] **Step 4: Build the keep into `Cave`**

A field beside `groves`:

```java
    /** The boss's keep once it is built in — see {@link #raise} — and null before, or on a floor with none. */
    private Keep keep;
```

Replace `levels()`:

```java
    /**
     * The same map in storeys: all of the floor on the one, because the ground's height is the relief's now — but for
     * a keep's court and doorway, a storey up, and the stair to them.
     */
    String levels() {
        var text = new StringBuilder(height * (width + 1));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                text.append(cells[y][x] == STONE ? STONE : keep == null ? '0' : keep.storeyAt(x, y));
            }
            text.append('\n');
        }
        return text.toString();
    }
```

And after `groves()`:

```java
    /** The keep built into this floor, or null. */
    Keep keep() {
        return keep;
    }

    /**
     * Build {@code keep} into the carved floor: its ring of wall, its court and doorway, the stair before the doorway,
     * and a straight road from beyond the stair to the middle of the chamber it hangs off in {@code rooms}. All of it
     * kept, the ring rock: a built thing is not worn. The road never touches the ring — it leaves the gate's side
     * outward, the side having been chosen for that, and a straight line leaving a square outward never meets it
     * again.
     */
    void raise(Keep keep, List<Room> rooms) {
        this.keep = keep;
        var walls = keep.walls();
        for (int y = walls.y() - 1; y <= walls.y() + walls.h(); y++) {
            for (int x = walls.x() - 1; x <= walls.x() + walls.w(); x++) {
                if (keep.isCourt(x, y) || keep.isDoorway(x, y) || keep.isStair(x, y)) {
                    keep(x, y);
                } else if (keep.holds(x, y)) {
                    cells[y][x] = STONE;
                }
            }
        }
        var to = rooms.get(keep.chamber());
        line(keep.roadStart(), new int[] {to.centerCellX(), to.centerCellY()});
    }
```

- [ ] **Step 5: Let `BiomeMap` take one more chamber**, after `ofRoom`:

```java
    /**
     * The same map with one more chamber, whole in the biome under its middle, numbered after the rest: a keep, built
     * once the floor was carved.
     */
    BiomeMap withRoom(Room room) {
        var painted = cells.clone();
        int biome = painted[room.centerCellY() * width + room.centerCellX()];
        for (int y = room.y(); y < room.y() + room.h(); y++) {
            for (int x = room.x(); x < room.x() + room.w(); x++) {
                painted[y * width + x] = biome;
            }
        }
        var more = Arrays.copyOf(rooms, rooms.length + 1);
        more[rooms.length] = biome;
        return new BiomeMap(width, biomes, painted, more);
    }
```

- [ ] **Step 6: Build it in `DungeonGenerator.generate`**

Replace the body of `generate(long seed, DungeonSettings settings, int depth, Layout layout)`:

```java
        var rng = new DeterministicRng(seed);
        var terrain = settings.themes().terrainAt(depth);

        var rooms = placeRooms(rng, settings, layout);
        var links = spanningTree(rooms);
        int furthest = furthestRoomFromStart(rooms.size(), links);
        // A map that mixes biomes lays them out now, from a stream of its own, so each region can be cut to its
        // own ground; one that does not cuts the whole floor to its depth's theme, exactly as it always has.
        var drawn = settings.biomes().isEmpty() || !layout.mixesBiomes() ? null
                : BiomeMap.draw(seed, depth, layout.width(), layout.height(), rooms, settings.biomes());
        var cave = drawn == null
                ? Cave.carve(rng, layout.width(), layout.height(), rooms, links, furthest,
                        settings.corridorWidth(), settings.maxRoomSpacing(), terrain)
                : Cave.carve(rng, layout.width(), layout.height(), rooms, links, furthest,
                        settings.corridorWidth(), settings.maxRoomSpacing(), terrainOfRooms(drawn, rooms.size()),
                        (x, y) -> drawn.at(x, y).terrain().ragged(),
                        // A wood's islands are groves of trees a body can go between; a cavern's are rock.
                        room -> drawn.ofRoom(room).grove() != null);

        // The boss's keep, in solid rock beside the deepest chamber with room for it — see Keep. A map drawn once
        // gets none: its file keeps no look for one, as it keeps no biome for a cell (see Layout). Where none fits,
        // the boss waits in the furthest chamber as it always did, and the floor is today's.
        var keep = layout.mixesBiomes()
                ? Keep.site(cave, rooms, links, settings.keep().sizes(), settings.maxRoomSpacing()) : null;
        var chambers = new ArrayList<>(rooms);
        var joined = new ArrayList<>(links);
        int bossRoom = furthest;
        if (keep != null) {
            cave.raise(keep, rooms);
            chambers.add(keep.walls());
            joined.add(new Link(keep.chamber(), chambers.size() - 1));
            bossRoom = chambers.size() - 1;
        }
        var biomes = drawn == null || keep == null ? drawn : drawn.withRoom(keep.walls());

        var hero = middleOf(rooms.get(0));
        var monsters = populate(rng, cave, chambers, settings, depth, bossRoom);
        var boss = new Monster(settings.bossKindAt(depth), middleOf(chambers.get(bossRoom)));
        monsters.addAll(guard(cave, chambers.get(bossRoom), settings, depth));
        var props = new ArrayList<>(scatter(rng, cave, chambers, settings, monsters, hero, boss.at()));
        if (keep != null) {
            // In the doorway until it is opened. Spawned as a prop is, and turned across the doorway: see Spawner.
            var gate = keep.gate();
            props.add(new Prop(settings.keep().gate(), Placement.atCell(gate[0], gate[1])));
        }

        var relief = biomes == null ? Relief.of(seed, cave, chambers, terrain)
                : Relief.of(seed, cave, chambers, biomes.hills(), biomes::riseAt,
                        terrainOfRooms(biomes, chambers.size()).stream().map(Theme.Terrain::level).toList());
        var scenery = biomes == null ? List.<GeneratedDungeon.Piece>of()
                : Scenery.scatter(seed, cave, biomes, rooms.getFirst());
        var storeys = new ArrayList<>(Collections.nCopies(chambers.size(), 0));
        if (keep != null) {
            storeys.set(bossRoom, 1);
        }
        return new GeneratedDungeon(cave.walls(), cave.levels(), hero, monsters, boss, bossRoom,
                List.copyOf(chambers), List.copyOf(joined), List.copyOf(storeys), List.copyOf(props),
                relief, 0f, biomes, scenery, keep);
```

And add to the class comment, after its first paragraph:

```java
 * <p>The floor ends in the boss's {@link Keep}: a walled court a storey up, built in solid rock once the rest is
 * carved, so it cuts nothing off; the last of the rooms, and the boss's.
```

- [ ] **Step 7: Strew nothing on the keep** — in `Scenery.scatter`, read the keep before the second loop and skip
  its cells:

```java
        var keep = cave.keep();
        for (int y = 0; y < cave.height(); y++) {
            for (int x = 0; x < cave.width(); x++) {
                if (!cave.isFloor(x, y) || Math.abs(x - wayIn.centerCellX()) <= CLEAR
                        && Math.abs(y - wayIn.centerCellY()) <= CLEAR || keep != null && keep.holds(x, y)) {
                    continue;
                }
```

- [ ] **Step 8: Let `StageCheck` know the gate** — after the loop filling `propKinds` (~line 64):

```java
        // And the keep's gate, which stands in its doorway rather than being scattered — see gen/Keep.
        if (!settings.keep().gate().isBlank()) {
            propKinds.add(settings.keep().gate());
        }
```

- [ ] **Step 9: Run the new test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.KeepFloorTest"`
Expected: PASS, 7 tests.

- [ ] **Step 10: Mend the three tests that took the boss room for a chamber**

`DungeonGeneratorTest.roomCountStaysInRange` — the keep is not one of the rooms the file counts:

```java
        for (long seed = 0; seed <= 200; seed++) {
            var floor = generate(seed);
            // The keep is built after the chambers are placed, and is not one of the rooms the file counts.
            int rooms = floor.rooms().size() - (floor.keep() == null ? 0 : 1);
            assertTrue(rooms >= SETTINGS.minRooms() && rooms <= SETTINGS.maxRooms(),
                    "seed " + seed + " produced " + rooms + " rooms");
        }
```

`BiomeFloorTest.aMixedFloorKeepsEveryPromiseAFloorMakes` — the same count:

```java
            int rooms = floor.rooms().size() - (floor.keep() == null ? 0 : 1);
```

`BiomeFloorTest.eachChamberIsCutToItsOwnBiome` — the keep is built, not cut, so first thing in the loop over rooms:

```java
            for (int i = 0; i < floor.rooms().size(); i++) {
                if (floor.keep() != null && i == floor.bossRoom()) {
                    continue; // built, not cut to its biome
                }
```

`PropsTest.everythingStandsInTheOpen` and `PropsTest.everyRoomIsStillReachableWithThemAllInPlace` — the gate is not
furniture: it stands in its doorway and it opens (see `KeepFloorTest`). First thing in each loop over props:

```java
            for (var prop : dungeon.props()) {
                if (prop.kind().equals(SETTINGS.keep().gate())) {
                    continue; // the keep's gate, in its doorway until it opens — see KeepFloorTest
                }
```

- [ ] **Step 11: Run the whole suite**

Run: `./gradlew test`
Expected: PASS. A failure anywhere else is a test assuming the boss's room is a carved chamber or that every floor
cell is storey 0 — stop and report it rather than loosening it.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/gen src/main/java/uz/dukeengine/dungeon/stage/StageCheck.java src/test/java/uz/dukeengine/dungeon/gen
git commit -m "Every floor of the descent ends in the boss's keep, a storey up behind one doorway" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The keep lies level

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/gen/Relief.java`
- Test: `src/test/java/uz/dukeengine/dungeon/gen/KeepFloorTest.java` (one more test)

**Interfaces:**
- Consumes: `Cave.keep()`, `Keep.walls()`.

- [ ] **Step 1: Write the failing test** — add to `KeepFloorTest`:

```java
    /** Its square and the ring round it — its stair among them — at one height, and no cliff anywhere. */
    @Test
    void itStandsLevelAndNoGroundRoundItIsACliff() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var floor = floor(seed);
            var relief = floor.relief();
            if (relief == null) {
                continue;
            }
            var walls = floor.keep().walls();
            int level = relief.at(walls.x() - 1, walls.y() - 1);
            for (int y = walls.y() - 1; y <= walls.y() + walls.h() + 1; y++) {
                for (int x = walls.x() - 1; x <= walls.x() + walls.w() + 1; x++) {
                    assertEquals(level, relief.at(x, y), "seed " + seed + ": its ground at corner " + x + "," + y);
                }
            }
            for (int y = 0; y < relief.rows() - 1; y++) {
                for (int x = 0; x < relief.columns() - 1; x++) {
                    assertFalse(relief.isCliff(x, y), "seed " + seed + " has a cliff at " + x + "," + y);
                }
            }
        }
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.KeepFloorTest.itStandsLevelAndNoGroundRoundItIsACliff"`
Expected: FAIL — "its ground at corner …" differs.

- [ ] **Step 3: Set it level in `Relief.of`** (the overload taking `riseAt`), between `limit(...)` and the loop that
  subtracts `lowest`:

```java
        limit(steps, columns, rows, floor.slope());
        var keep = cave.keep();
        if (keep != null) {
            // Built level: set down at the lowest ground within two cells of it, and the ground round it eased down
            // to meet it by the same slope. Nothing near it is lower, so it is never lowered again.
            flatten(steps, columns, rows, keep.walls());
            limit(steps, columns, rows, floor.slope());
        }
```

and the method, beside `level`:

```java
    /**
     * Every corner of {@code square} and of the ring of cells round it — the keep, and its stair — set to the lowest
     * corner within two cells of that ring.
     */
    private static void flatten(int[] steps, int columns, int rows, Room square) {
        int left = square.x() - 1;
        int top = square.y() - 1;
        int right = square.x() + square.w() + 1;
        int bottom = square.y() + square.h() + 1;
        int lowest = Integer.MAX_VALUE;
        for (int y = Math.max(0, top - 2); y <= Math.min(rows - 1, bottom + 2); y++) {
            for (int x = Math.max(0, left - 2); x <= Math.min(columns - 1, right + 2); x++) {
                lowest = Math.min(lowest, steps[y * columns + x]);
            }
        }
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                steps[y * columns + x] = lowest;
            }
        }
    }
```

- [ ] **Step 4: Run the gen tests to see them pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.*"`
Expected: PASS — `KeepFloorTest` (8), `ReliefTest`, `BiomeFloorTest` and the rest.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/uz/dukeengine/dungeon/gen/Relief.java src/test/java/uz/dukeengine/dungeon/gen/KeepFloorTest.java
git commit -m "The keep lies level, and the ground round it comes down to meet it" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The gate

**Files:**
- Create: `src/main/resources/data/props/gate.duke`
- Modify: `src/main/resources/data/game.duke` (after `data/props/fountain.duke,`)
- Create: `src/main/java/uz/dukeengine/dungeon/run/GateUpdate.java`
- Modify: `src/main/java/uz/dukeengine/dungeon/content/Content.java` (`MODULES`, after `FountainUpdate.Data.class`)
- Modify: `src/main/java/uz/dukeengine/dungeon/Dungeon.java` (after the `FountainUpdate` registration, ~line 184)
- Modify: `src/main/java/uz/dukeengine/dungeon/run/Spawner.java` (the props loop, lines 87–91)
- Test: `src/test/java/uz/dukeengine/dungeon/run/GateTest.java`

**Interfaces:**
- Consumes: `GeneratedDungeon.keep()`, `Keep.facing()`, `Keep.gate()`, `Keep.isDoorway`.
- Produces: templates `Gate` and `OpenGate`; `public final class GateUpdate` with `record Data(float reach,
  int everyFrames, String opens)`, `GateUpdate(GameObject, Data, Set<String> heroes)`, `void open()` (the part-2 key
  will call it); `Spawner.acrossTheDoorway(GeneratedDungeon, Placement)` (package-private, static).

- [ ] **Step 1: Write the failing test**

```java
package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.game.DukeGame;

/** The keep's gate: across its doorway nothing passes, a hero walking up opens it, and the floor turns it true. */
class GateTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Where the gate stands: the middle of a doorway three cells wide, in a wall down the middle of a hall. */
    private static final Coord3D DOORWAY = new Coord3D(205f, 155f, 0f);

    /** A hall forty by thirty with a wall down x = 20, open at y 14 to 16. */
    private static String hall() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean border = x == 0 || y == 0 || x == 39 || y == 29;
                boolean wall = x == 20 && (y < 14 || y > 16);
                text.append(border || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /**
     * The hall with the gate in its doorway, turned along the wall — which runs down the map's y. A frame first:
     * before the world starts, a spawn is only booked for its first frame.
     */
    private static Dungeon.Arena withAGate() {
        var arena = Dungeon.world(hall(), SETTINGS);
        arena.game().spawn("Gate", arena.dungeon(), DOORWAY.x(), DOORWAY.y());
        arena.game().runHeadless(1);
        find(arena.game(), "Gate").setOrientation((float) (StrictMath.PI / 2));
        return arena;
    }

    private static boolean shut(DukeGame game, float x, float y) {
        var grid = game.getLogic().getPathGrid();
        var at = new Coord3D(x, y, 0f);
        return grid.isBlocked(grid.toCellX(at), grid.toCellY(at));
    }

    @Test
    void itShutsTheWholeDoorway() {
        var arena = withAGate();
        arena.game().runHeadless(2);

        for (float y : new float[] {145f, 155f, 165f}) {
            assertTrue(shut(arena.game(), DOORWAY.x(), y), "the doorway is open at y " + y);
        }
    }

    @Test
    void theDungeonsOwnDoNotOpenIt() {
        var arena = withAGate();
        arena.game().spawn("Skeleton", arena.dungeon(), 185f, 155f);
        arena.game().runHeadless(60);

        assertNotNull(find(arena.game(), "Gate"), "it opened for a skeleton");
    }

    @Test
    void aHeroWalkingUpToItOpensItAndWalksOnThrough() {
        var arena = withAGate();
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 120f, 155f);
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        hero.getLocomotor().moveTo(new Coord3D(300f, 155f, 0f));
        game.runHeadless(150);

        assertNull(find(game, "Gate"), "he stood at it and it stayed shut");
        var open = find(game, "OpenGate");
        assertNotNull(open, "nothing stands where it stood");
        assertEquals(DOORWAY.x(), open.getPosition().x(), 0.01f);
        assertEquals(DOORWAY.y(), open.getPosition().y(), 0.01f);
        assertEquals((float) (StrictMath.PI / 2), open.getOrientation(), 1e-6f, "turned as the gate was");
        assertFalse(shut(game, DOORWAY.x(), DOORWAY.y()), "and the doorway open, the open gate in nobody's way");
        hero.getLocomotor().moveTo(new Coord3D(300f, 155f, 0f));
        game.runHeadless(200);
        assertTrue(hero.getPosition().x() > 260f, "and he walked on through: " + hero.getPosition());
    }

    /** On a floor of the descent, it stands across its keep's doorway, every cell of it shut. */
    @Test
    void theFloorTurnsItAcrossItsDoorway() {
        for (long seed : new long[] {3L, 11L, 42L}) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 1);
            var game = Dungeon.newSession(seed, SETTINGS).game();
            game.runHeadless(2);

            var gate = find(game, "Gate");
            assertNotNull(gate, "seed " + seed + ": the keep has no gate");
            var keep = floor.keep();
            assertEquals(keep.facing(), gate.getOrientation(), 1e-6f, "seed " + seed);
            var walls = keep.walls();
            for (int y = walls.y(); y < walls.y() + walls.h(); y++) {
                for (int x = walls.x(); x < walls.x() + walls.w(); x++) {
                    if (keep.isDoorway(x, y)) {
                        assertTrue(shut(game, x * 10f + 5f, y * 10f + 5f), "seed " + seed + ": open at " + x + "," + y);
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.GateTest"`
Expected: FAIL — no `Gate` template (`find` returns null: NullPointerException in `withAGate`).

- [ ] **Step 3: Write the template `data/props/gate.duke`**

```
; The gate of the boss's keep: it stands across the keep's doorway, at the top
; of the stair, and nothing walks through it until it is opened -- see gen/Keep
; and run/GateUpdate.
;
;   - no Body, so nothing ever shoots at it
;   - a Box as long as the doorway (three cells, 30 units) and thin across it;
;     the spawner turns it along the wall it stands in
;   - STRUCTURE, so it is drawn as a thing standing on the floor; SELECTABLE, so
;     the pointer finds it -- the key will be aimed at it
;
; For now a hero walking up to it opens it: the key that will open it instead is
; the next piece of work. What it looks like is the Keep theme's
; (data/world/themes/keep.duke).
Object
  Name = Gate
  DisplayName = Darvoza
  KindOf = [STRUCTURE, SELECTABLE]
  VisionRange = 0
  Geometry = Box
    MajorRadius = 15
    MinorRadius = 2
    Height = 26
  End
  Modules = [
    GateUpdate
      ; From its middle, in world units: a hero on the stair, or on the road
      ; just below it.
      Reach = 25
      ; A third of a second.
      EveryFrames = 10
      ; What stands in its place once it is open, turned as it was.
      Opens = OpenGate
    End
  ]
End

; The gate once it is open: the same model, its leaves swinging open as it
; appears and staying so (the Keep theme's PlayOnce), with no shape, so it is in
; nobody's way -- and not SELECTABLE, since there is nothing left to do with it.
Object
  Name = OpenGate
  DisplayName = Darvoza
  KindOf = [STRUCTURE]
  VisionRange = 0
End
```

and list it in `data/game.duke`, after `data/props/fountain.duke,`:

```
    data/props/gate.duke,
```

- [ ] **Step 4: Write `run/GateUpdate.java`**

```java
package uz.dukeengine.dungeon.run;

import java.util.Set;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;

/**
 * The gate of the boss's keep: it stands across the doorway, and nothing walks through it until it is opened.
 *
 * <p>For now a hero walking up to it opens it — the key that will open it instead is the next piece of work, and it
 * will call {@link #open} where this does. Opening swaps it for the template its block names: the gate with a shape
 * goes, so the engine lays the navigation grid again without it and the doorway is walked at once, and the same gate
 * with no shape stands in its place, turned as it was, swinging open as it appears.
 *
 * <p>Deterministic: looked at on the world's own frame, by whether a living hero stands within its reach.
 */
@ModuleGroup(ModuleGroups.EFFECT)
public final class GateUpdate extends UpdateModule {

    /**
     * @param reach       how near a hero must come to open it, in world units, from its middle
     * @param everyFrames how often it looks, in logic frames (30 = once a second)
     * @param opens       the template that stands in its place once it is open; blank, and nothing does
     */
    public record Data(float reach, int everyFrames, String opens) implements ModuleData {

        /** What a block leaves out. */
        static final Data DEFAULTS = new Data(25f, 10, "");

        public Data {
            opens = opens == null ? "" : opens;
        }
    }

    private final Data data;
    /** The templates that are heroes: the ones that open it. */
    private final Set<String> heroes;
    /** Opened already: a second opening, the same frame, would stand a second open gate in the first. */
    private boolean opened;

    public GateUpdate(GameObject owner, Data data, Set<String> heroes) {
        super(owner);
        this.data = data;
        this.heroes = Set.copyOf(heroes);
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (opened || world == null || owner.isEffectivelyDead() || data.everyFrames() <= 0
                || world.getFrame() % data.everyFrames() != 0) {
            return;
        }
        var near = world.objectsInRange(owner.getPosition(), data.reach(), thing -> thing.getBody() != null
                && !thing.isEffectivelyDead() && heroes.contains(thing.getTemplate().name()));
        if (!near.isEmpty()) {
            open();
        }
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

- [ ] **Step 5: Register it**

`Content.MODULES`, after `uz.dukeengine.dungeon.level.FountainUpdate.Data.class`:

```java
                    uz.dukeengine.dungeon.level.FountainUpdate.Data.class,
                    uz.dukeengine.dungeon.run.GateUpdate.Data.class))
```

`Dungeon.java`, right after the `FountainUpdate` registration:

```java
                    // The keep's gate, opened by a hero walking up to it. See GateUpdate.
                    var heroNames = settings.heroes().stream().map(hero -> hero.name())
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    factory.register(uz.dukeengine.dungeon.run.GateUpdate.Data.class,
                            (owner, data) -> new uz.dukeengine.dungeon.run.GateUpdate(owner, data, heroNames));
```

- [ ] **Step 6: Turn it across its doorway in `Spawner.place`** — replace the props loop:

```java
        for (var prop : dungeon.props()) {
            if (!underIt.contains(key(prop.at().cellX(), prop.at().cellY()))) {
                var thing = spawn(game, dungeonPlayer, prop.kind(), at(logic, prop.at()));
                if (thing != null && thing.findModule(GateUpdate.class) != null) {
                    thing.setOrientation(acrossTheDoorway(dungeon, prop.at()));
                }
            }
        }
```

and beside `storeyAt`:

```java
    /**
     * Which way a gate faces to stand across the doorway it was put in: along the wall, which is the way the rock lies
     * two cells off either side of it — the cell either side of it is the doorway's own.
     */
    static float acrossTheDoorway(GeneratedDungeon dungeon, GeneratedDungeon.Placement at) {
        var rows = dungeon.asciiMap().strip().split("\n");
        int x = at.cellX();
        int y = at.cellY();
        return rock(rows, x - 2, y) && rock(rows, x + 2, y) ? 0f : (float) (StrictMath.PI / 2);
    }

    /** Whether a cell is rock as the map writes it, off the map included. */
    private static boolean rock(String[] rows, int x, int y) {
        return y < 0 || y >= rows.length || x < 0 || x >= rows[y].length() || rows[y].charAt(x) == '#';
    }
```

- [ ] **Step 7: Run the gate test to see it pass**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.run.GateTest"`
Expected: PASS, 4 tests.

- [ ] **Step 8: Run the whole suite**

Run: `./gradlew test`
Expected: PASS — `EveryModuleIsGroupedTest` finds `GateUpdate` in `Effect`.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/data/props/gate.duke src/main/resources/data/game.duke src/main/java/uz/dukeengine/dungeon/run/GateUpdate.java src/main/java/uz/dukeengine/dungeon/run/Spawner.java src/main/java/uz/dukeengine/dungeon/content/Content.java src/main/java/uz/dukeengine/dungeon/Dungeon.java src/test/java/uz/dukeengine/dungeon/run/GateTest.java
git commit -m "A gate stands across the keep's doorway until a hero walks up to it, and opens" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The keep drawn in stone, and its gate the owner's model

**Files:**
- Create: `art/models/join_clips.pl`
- Create: `src/main/resources/models/props/gate/gate.glb` (copied, clips joined), `.../models/props/gate/License.txt`
- Create: `src/main/resources/data/world/themes/keep.duke`
- Modify: `src/main/resources/data/game.duke` (after `data/world/themes/mine.duke,`)
- Modify: `src/main/resources/data/world/generation.duke` (`Look` in the `Keep` block)
- Modify: `src/main/java/uz/dukeengine/dungeon/world/Theme.java` (`ThemeMonster.playOnce`)
- Modify: `src/main/java/uz/dukeengine/dungeon/Main.java` (`themedCreature`)
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (one more check)
- Modify: `src/main/java/uz/dukeengine/dungeon/run/FloorLooks.java`, `src/main/java/uz/dukeengine/dungeon/run/DungeonRun.java`
  (`looksOf`)
- Modify: `CREDITS.md`, `README.md`
- Tests: `src/test/java/uz/dukeengine/dungeon/DungeonThemeTest.java`, `.../DungeonTilesTest.java`,
  `.../content/KeepSettingsTest.java`

**Interfaces:**
- Consumes: `GeneratedDungeon.keep()`, `Keep.holds`, `Keep.gate`, `Keep.isStair`; `Themes.themeNamed`,
  `Themes.dressedAs`; templates `Gate`, `OpenGate` (Task 5).
- Produces: `FloorLooks(name, biomes, looks, scenery, navigationCellsPerCell, Keep keep, String keepLook)`;
  `Theme.ThemeMonster.playOnce()` — a clip played once from its first frame and held on its last, in place of any
  idle, via `Visuals.UnitVisual.clip(Set.of(), clip, ClipMode.ONCE, ClipStart.FIRST, null)`.

- [ ] **Step 1: Write the failing tests**

In `DungeonTilesTest.everyThemedCreatureCarriesTheClipsItIsGiven`, the clip a themed thing plays once is one it has
to carry too — add it to the clips asked for:

```java
                for (var wanted : new String[] {art.look().idle(), art.look().walk(),
                    art.look().attack(), art.death(), art.playOnce()}) {
```

In `KeepSettingsTest`:

```java
    /** Drawn in the Keep theme, and a Look naming no theme is refused — by the link or by the check, either way. */
    @Test
    void itIsDrawnInATheme() {
        assertEquals("Keep", DungeonSettings.load().keep().look());
        var data = Content.data().replace("    Look = Keep\n", "    Look = Castle\n");

        assertThrows(RuntimeException.class, () -> DungeonSettings.parse(data));
    }
```

In `DungeonThemeTest`, in `aMixedFloorTellsTheClientWhatEveryCellWears`, the keep's middle wears its own look —
replace the loop over rooms:

```java
        var stone = SHIPPED.themes().dressedAs(SHIPPED.themes().themeNamed(SHIPPED.keep().look()), 11L, 1).asStatus();
        for (int i = 0; i < floor.rooms().size(); i++) {
            var middle = floor.rooms().get(i);
            var wears = floor.keep() != null && i == floor.bossRoom() ? stone
                    : SHIPPED.themes().dressedAs(floor.biomes().ofRoom(i), 11L, 1).asStatus();
            assertEquals(wears, looked.lookAt(middle.centerCellX(), middle.centerCellY()), "chamber " + i);
        }
```

and a new test beside it:

```java
    /** The keep is worked stone whatever biome it stands in — its wall, its court, its stair — and the road is not. */
    @Test
    void theKeepIsDrawnInStoneWhateverBiomeItStandsIn() {
        var session = Dungeon.newSession(11L);
        session.game().runHeadless(2);
        var floor = DungeonGenerator.generate(11L, SHIPPED, 1);
        var looked = (uz.dukeengine.core.map.Looked) session.game().getMapRecord();
        var keep = floor.keep();
        var stone = SHIPPED.themes().dressedAs(SHIPPED.themes().themeNamed(SHIPPED.keep().look()), 11L, 1).asStatus();

        var gate = keep.gate();
        assertEquals(stone, looked.lookAt(gate[0], gate[1]), "the wall its gate is in");
        assertEquals(stone, looked.lookAt(keep.walls().centerCellX(), keep.walls().centerCellY()), "its court");
        for (var step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int x = gate[0] + step[0];
            int y = gate[1] + step[1];
            if (keep.isStair(x, y)) {
                assertEquals(stone, looked.lookAt(x, y), "its stair");
                int roadX = x + step[0];
                int roadY = y + step[1];
                assertEquals(SHIPPED.themes().dressedAs(floor.biomes().at(roadX, roadY), 11L, 1).asStatus(),
                        looked.lookAt(roadX, roadY), "the road below the stair is the ground's");
            }
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.DungeonThemeTest" --tests "uz.dukeengine.dungeon.content.KeepSettingsTest" --tests "uz.dukeengine.dungeon.DungeonTilesTest"`
Expected: FAIL — compilation error, `playOnce()` is not a method of `ThemeMonster`.

- [ ] **Step 3: Write `art/models/join_clips.pl`**

```perl
# Joins every clip of a .glb into one, named by the second argument: each clip's channels and samplers, the samplers
# renumbered, and nothing else in the file touched -- the binary chunk goes back exactly as it came. For a model whose
# parts were animated one clip each in Blender (the keep's gate: a clip for each leaf), so that one clip plays them
# all.
#
#   perl art/models/join_clips.pl src/main/resources/models/props/gate/gate.glb open
use strict;
use warnings;
use JSON::PP;

my ($path, $name) = @ARGV;
die "usage: join_clips.pl model.glb clip-name\n" unless defined $name;
open(my $in, "<:raw", $path) or die "cannot read $path\n";
my $all = do { local $/; <$in> };
close $in;
my ($magic, $version) = unpack("A4V", substr($all, 0, 8));
die "$path is not a glb\n" unless $magic eq "glTF";
my ($length) = unpack("V", substr($all, 12, 4));
my $gltf = JSON::PP->new->decode(substr($all, 20, $length));
my $rest = substr($all, 20 + $length);
my %joined = (name => $name, channels => [], samplers => []);
for my $clip (@{$gltf->{animations} // []}) {
    my $offset = scalar @{$joined{samplers}};
    push @{$joined{samplers}}, @{$clip->{samplers}};
    push @{$joined{channels}}, map { { %$_, sampler => $_->{sampler} + $offset } } @{$clip->{channels}};
}
$gltf->{animations} = [\%joined];
my $json = JSON::PP->new->canonical->encode($gltf);
$json .= " " x ((4 - length($json) % 4) % 4);
my $glb = pack("A4VV", "glTF", $version, 20 + length($json) + length($rest)) . pack("VA4", length($json), "JSON")
        . $json . $rest;
open(my $out, ">:raw", $path) or die "cannot write $path\n";
print $out $glb;
close $out;
```

- [ ] **Step 4: Bring the model in, its two clips joined into `open`**

```bash
mkdir -p src/main/resources/models/props/gate
cp "/c/Users/abdur/OneDrive/Рабочий стол/Game assets/buildings/Gate/gate.glb" src/main/resources/models/props/gate/gate.glb
perl art/models/join_clips.pl src/main/resources/models/props/gate/gate.glb open
```

`src/main/resources/models/props/gate/License.txt`:

```
Old Driveway Gate: two old pillars, two gate lights and an iron gate.

By animatedheaven -- https://www.cgtrader.com/designers/animatedheaven
Licence: may be used commercially, with credit.

Supplied by the owner as gate.glb: converted from the author's OBJ with its
textures (2048 x 2048, diffuse and normal) packed inside, and the two leaves
animated opening in Blender, a clip each. The game's copy has the two clips
joined into one, "open", by art/models/join_clips.pl.
```

- [ ] **Step 5: Let a themed thing play a clip once** — `Theme.ThemeMonster` gains a last component, documented
  beside `death`:

```java
     * @param death      what it plays when it falls
     * @param playOnce   what it plays once from its first frame and holds on its last, in place of any idle: a gate
     *     swinging open as it appears. None for a thing that plays its roles
     */
    public record ThemeMonster(String name, String model, String texture, float modelScale, int tint, float facing,
            @Link(AnimationSet.class) String animations, @Clip String idle, @Clip String walk, @Clip String attack,
            @Clip String hurt, @Clip String death, @Link(Effect.class) String effect, Held held,
            @Clip String playOnce) {

        /** What a block leaves out. */
        public static final ThemeMonster DEFAULTS = new ThemeMonster("", null, null, 1f, 0xFFFFFF, 90f, null,
                null, null, null, null, null, null, Held.NOTHING, null);
```

and `Main.themedCreature`, right after `carry(unit, art.held());`:

```java
            if (themed.playOnce() != null) {
                // Once, from its first frame, and held on its last: a gate swinging open as it appears.
                unit.clip(java.util.Set.of(), themed.playOnce(), Visuals.ClipMode.ONCE, Visuals.ClipStart.FIRST, null);
            }
```

- [ ] **Step 6: Write the theme `data/world/themes/keep.duke`**

```
; The boss's keep: worked stone, whatever biome it was built in. Not a biome and
; no depth's theme -- neither Biomes nor Themes lists it -- only what the keep's
; own cells are drawn as, as generation.duke's Keep block says.
;
; A masonry kit: the dungeon's own pieces as the dungeon was built before its
; caverns were bounded by rock. The wall is a surface, one course a storey, so
; the ring round the court is a wall from both sides and the stair climbs to a
; doorway in it. The numbers are that kit's, measured once and held by
; DungeonTilesTest.
Theme
  Name = Keep
  TileSize = 4
  WallHeight = 4
  ; The slab is a unit thick and centred on its own origin: pushed back half a
  ; unit, its face lands on the boundary.
  WallShift = -0.5
  Stairs = models/tiles/dungeon/stairs.gltf
  ; The top of the wall is a floor tile: cooler and darker, stone in shade.
  CapTint = 0xA8A8B4
  StoreyShadePercent = 108
  FogTint = 0x07060A
  Tones = [
    Tone
      Name = Stone
      Floor = models/tiles/dungeon/floor.gltf
      Wall = models/tiles/dungeon/wall.gltf
    End
  ]
  Monsters = [
    ; What stands in the court is the caverns' stone.
    ThemeMonster
      Name = Pillar
      Model = models/props/dungeon/pillar.gltf
      ModelScale = 2.5
    End,
    ThemeMonster
      Name = Statue
      Model = models/props/dungeon/pillar_decorated.gltf
      ModelScale = 2.5
    End,
    ThemeMonster
      Name = Barrel
      Model = models/props/dungeon/barrel.gltf
      ModelScale = 3.0
    End,
    ; What the boss leaves behind lies in its court.
    ThemeMonster
      Name = Chest
      Model = models/props/dungeon/chest.gltf
      ModelScale = 5.0
    End,
    ThemeMonster
      Name = Fountain
      Model = models/props/fountain/fountain.glb
      ModelScale = 7.5
      Effect = FountainWater
    End,
    ; animatedheaven's Old Driveway Gate: two pillars with the iron between
    ; them, 106 across and 70 tall as modelled. At 0.37 the iron fills the
    ; doorway's 30 and the pillars stand in the wall either side of it. Its
    ; width runs along the model's own x, which is the way the gate faces --
    ; along its wall -- so it needs no turn of its own. Shut, it plays nothing
    ; and stands as the model rests: closed.
    ThemeMonster
      Name = Gate
      Model = models/props/gate/gate.glb
      ModelScale = 0.37
      Facing = 0
    End,
    ; And open: the same gate, its two leaves swinging open as it appears --
    ; the owner's clip, played once and held on its last frame.
    ThemeMonster
      Name = OpenGate
      Model = models/props/gate/gate.glb
      ModelScale = 0.37
      Facing = 0
      PlayOnce = open
    End
  ]
End
```

and list it in `data/game.duke`, after `data/world/themes/mine.duke,`:

```
    data/world/themes/keep.duke,
```

- [ ] **Step 7: Name it in the `Keep` block** of `generation.duke`, after `Gate = Gate`:

```
    ; What it is drawn as, whatever biome it stands in: data/world/themes/keep.duke.
    Look = Keep
```

and check it in `DungeonSettings.validate()`, after the Gate check:

```java
        require(map.keep().look().isBlank() || themes.stream().anyMatch(theme -> theme.name().equals(map.keep().look())),
                "the Keep's Look names no theme: " + map.keep().look());
```

- [ ] **Step 8: Answer the look for the keep's cells** — `FloorLooks`:

```java
 * @param navigationCellsPerCell how many cells a side the floor is walked at for each one it is drawn at
 * @param keep     the boss's keep, drawn in {@code keepLook} whatever biome it stands in; null for none
 * @param keepLook the look the keep is drawn in, by the name the client registered it under; null for the biome's
 */
record FloorLooks(String name, BiomeMap biomes, List<String> looks, List<GeneratedDungeon.Piece> scenery,
        int navigationCellsPerCell, Keep keep, String keepLook) implements Looked, Dressed, Subdivided {

    @Override
    public String lookAt(int cx, int cy) {
        if (keep != null && keepLook != null && keep.holds(cx, cy)) {
            return keepLook;
        }
        return looks.get(biomes.indexAt(cx, cy));
    }
}
```

(import `uz.dukeengine.dungeon.gen.Keep`), and `DungeonRun.looksOf` hands it over:

```java
        var stone = themes.dressedAs(themes.themeNamed(settings.keep().look()), floors.seed(), depth);
        return new FloorLooks("descent", floor.biomes(), looks, floor.scenery(),
                settings.world().navigationCellsPerCell(), floor.keep(), stone == null ? null : stone.asStatus());
```

- [ ] **Step 9: Credit the author** — a row in `CREDITS.md`'s Duke Dungeon table, after the Resource Bits row:

```
| The keep's gate — "Old Driveway Gate": two stone pillars with their lamps, and an iron gate | **animatedheaven** ([CGTrader](https://www.cgtrader.com/designers/animatedheaven)) | commercial use with credit | `models/props/gate/` |
```

- [ ] **Step 10: Say it in the README** — after the biomes section, before `## Stage rejimi`:

```markdown
### Boss qal'asi

Har qavat oxirida boss **qal'ada** turadi (`gen/Keep`): toshning ichiga
qurilgan kvadrat hovli, bir qavat baland (`levelMap` da `1`), atrofi devor —
qaysi biomda bo'lmasin, tosh plitadan (`data/world/themes/keep.duke`).
Darvoza yaqinlashish tomonidagi devorning o'rtasida (3 katak), oldida zina
(`/`), zinadan kameragacha to'g'ri yo'l. Qal'a eng chuqur kamera yonidagi
butunlay tosh joyga quriladi, shuning uchun hech qanday yo'lni kesmaydi; tekis
turadi, atrofidagi yer unga qiyalik bilan tushadi.

O'lchami `generation.duke` dagi `Keep` blokida: `Sizes = [15, 13, 11, 9]` —
kattalari faqat eng chuqur kameralar yonida sinab ko'riladi. Darvoza —
`data/props/gate.duke`, modeli animatedheaven'ning "Old Driveway Gate"i,
tabaqalarining ochilishi Blender'da animatsiya qilingan (ikki klip o'yin
nusxasida `art/models/join_clips.pl` bilan bitta `open` klipiga
birlashtirilgan). Ochilganda darvoza o'rniga shaklsiz `OpenGate` turadi va
`open` klipini bir marta o'ynab, ochiq qoladi (`PlayOnce`, engine
`ClipMode.ONCE`). Hozircha qahramon yaqinlashganda ochiladi; keyingi qadamda
kalit bilan ochiladi. Stage'lar qal'asiz kesiladi — stage fayli qal'aning
ko'rinishini saqlamaydi.
```

- [ ] **Step 11: Run the whole suite**

Run: `./gradlew test`
Expected: PASS — the tests above, and `DungeonTilesTest` (the Keep kit, the gate's model and its `open` clip) and
`FountainArtTest` now reading the Keep theme too.

- [ ] **Step 12: Look at it** — the owner looks, in the game (`./gradlew run`), at a floor's keep: the stone ring
  and its stair, the gate's size in its doorway (0.37 in `keep.duke` is the number to tune), and its leaves swinging
  open as a hero reaches the stair. Nothing to run here beyond the suite: report what to look at.

- [ ] **Step 13: Commit**

```bash
git add art/models/join_clips.pl src/main/resources/models/props/gate src/main/resources/data/world/themes/keep.duke src/main/resources/data/game.duke src/main/resources/data/world/generation.duke src/main/java/uz/dukeengine/dungeon/world/Theme.java src/main/java/uz/dukeengine/dungeon/Main.java src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java src/main/java/uz/dukeengine/dungeon/run/FloorLooks.java src/main/java/uz/dukeengine/dungeon/run/DungeonRun.java CREDITS.md README.md src/test/java/uz/dukeengine/dungeon/DungeonThemeTest.java src/test/java/uz/dukeengine/dungeon/DungeonTilesTest.java src/test/java/uz/dukeengine/dungeon/content/KeepSettingsTest.java
git commit -m "The keep in worked stone whatever the biome, behind animatedheaven's gate" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
