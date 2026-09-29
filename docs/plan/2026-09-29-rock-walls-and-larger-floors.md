# Rock walls and larger floors — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The dungeon theme's rooms are bounded by cliff rocks instead of masonry slabs (masonry only faces raised ground), and the endless descent's floors grow from 50×36 to 96×72.

**Architecture:** Data and models only. The engine already draws a "thing" kit — `WallFillsRock` puts a clump of the wall model in every rock cell that borders open ground, on a lid laid at ground level, and draws the theme's `RockFace` only where rock stands above the foot of its face (TerrainScene `plan` ~685-740, `addKitPiece` ~794-855). The forest uses it for trees; the dungeon uses it for rocks.

**Tech Stack:** Java 25, Gradle, JUnit 5, jME 3.7 asset loading in tests, `.duke` data files, KayKit glTF models.

**Spec:** `docs/plan/2026-09-29-world-generation.md` (Stage 1 and the first half of Stage 4).

## Global Constraints

- The engine is not touched (`../duke-engine` belongs to its own session); this plan needs nothing from E1–E6.
- Models only from the KayKit packs in the owner's library (CC0), each credited in `CREDITS.md`.
- `.duke` numbers carry the comment that says why, as every block in those files does.
- Commits only when the owner says "commit qil"; never push.
- `deep.map` stays larger than any floor of the descent (`StageDifficultyTest.theLargeShippedStageIsLargeAndDeep`): 100×76 and 28 rooms, so the descent stays under both.

Library folder (the owner's): `C:\Users\abdur\OneDrive\Рабочий стол\Game assets\world\KayKit_Forest_Nature_Pack_1.0_FREE\KayKit_Forest_Nature_Pack_1.0_FREE\Assets\gltf\` — call it `$FOREST`.

Measured off the models (glTF accessor bounds, base at y = 0, no node transforms):

| Model | W (x) | H (y) | D (z) | tris | becomes |
|---|---|---|---|---|---|
| `Rock_1_K_Color1` | 3.69 | 3.65 | 3.13 | 529 | `models/tiles/dungeon/cliff_a.gltf` (tone Stone) |
| `Rock_1_J_Color1` | 3.39 | 3.36 | 3.76 | 521 | `models/tiles/dungeon/cliff_b.gltf` (tone Cracked) |

`WallTileSize = 3.69` scales a rock to one 10-unit cell across and about one storey (10) tall.

---

### Task 1: The dungeon's rooms are bounded by rock

**Files:**
- Create: `src/main/resources/models/tiles/dungeon/cliff_a.gltf`, `cliff_a.bin`, `cliff_b.gltf`, `cliff_b.bin`, `forest_texture.png`
- Delete: `src/main/resources/models/tiles/dungeon/wall_cracked.gltf`, `wall_cracked.bin`
- Modify: `src/main/resources/data/world/themes/dungeon.duke` (the `Theme` block's header comment, placement numbers and `Tones`)
- Modify: `src/test/java/uz/dukeengine/dungeon/DungeonTilesTest.java` (new test; `everyThemeWhoseWallsStandUpTellsTheirTopsFromTheFloor`)
- Modify: `CREDITS.md`

**Interfaces:**
- Consumes: `DungeonSettings.load().themes().themeNamed(String)`, `Theme.standing().fillsRock()`, `Theme.rockFace()`, `Theme.tones()`, `Tone.wall()` — all existing.
- Produces: nothing new in code; the theme's data changes.

- [ ] **Step 1: Write the failing test** — in `DungeonTilesTest`, under "what the side of a raised block of rock is drawn with":

```java
    /**
     * The dungeon's rooms are bounded by rock, and its masonry only holds up higher ground.
     *
     * <p>The owner's call, pinned: a cave is walled by the cliff it was cut from, and a
     * worked slab belongs only where something was built — the retaining wall of a
     * raised floor. So the wall is a thing standing in the rock, and the slab is the
     * RockFace the client draws only where rock stands above the foot of its face.
     */
    @Test
    void theDungeonIsBoundedByRockAndItsMasonryOnlyHoldsUpRaisedGround() {
        var dungeon = uz.dukeengine.dungeon.content.DungeonSettings.load().themes().themeNamed("Dungeon");

        assertTrue(dungeon.standing().fillsRock(),
                "the dungeon's boundary is rock standing in the rock, not a wall along it");
        assertEquals("models/tiles/dungeon/wall.gltf", dungeon.rockFace(),
                "and the masonry slab only faces raised ground");
        for (var tone : dungeon.tones()) {
            assertNotEquals("models/tiles/dungeon/wall.gltf", tone.wall(),
                    tone.name() + " still walls its rooms with masonry");
        }
    }
```

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew test --tests uz.dukeengine.dungeon.DungeonTilesTest`
Expected: FAIL in `theDungeonIsBoundedByRockAndItsMasonryOnlyHoldsUpRaisedGround` ("the dungeon's boundary is rock standing in the rock…").

- [ ] **Step 3: Ship the rocks** — copy, rename, and point each glTF at its renamed buffer:

```bash
cp "$FOREST/Rock_1_K_Color1.gltf" src/main/resources/models/tiles/dungeon/cliff_a.gltf
cp "$FOREST/Rock_1_K_Color1.bin"  src/main/resources/models/tiles/dungeon/cliff_a.bin
cp "$FOREST/Rock_1_J_Color1.gltf" src/main/resources/models/tiles/dungeon/cliff_b.gltf
cp "$FOREST/Rock_1_J_Color1.bin"  src/main/resources/models/tiles/dungeon/cliff_b.bin
cp src/main/resources/models/tiles/forest/forest_texture.png src/main/resources/models/tiles/dungeon/
sed -i 's/"Rock_1_K_Color1.bin"/"cliff_a.bin"/' src/main/resources/models/tiles/dungeon/cliff_a.gltf
sed -i 's/"Rock_1_J_Color1.bin"/"cliff_b.bin"/' src/main/resources/models/tiles/dungeon/cliff_b.gltf
grep -o '"uri"[^,}]*' src/main/resources/models/tiles/dungeon/cliff_*.gltf
git rm -q src/main/resources/models/tiles/dungeon/wall_cracked.gltf src/main/resources/models/tiles/dungeon/wall_cracked.bin
```

Expected from the grep: each file names its own `.bin` and `forest_texture.png`.

- [ ] **Step 4: Make the dungeon a thing kit** — in `dungeon.duke`, replace the masonry placement lines (`WallHeight = 4`, `WallShift = -0.5`, the `CapTint` note and value) and the two tones with:

```
  TileSize = 4
  ; The rocks are modelled on their own scale -- 3.69 across, where the floor is
  ; four -- so they are sized by their own module: one rock is one cell across
  ; and about a storey tall. Measured off the model, as every number here is.
  WallTileSize = 3.69
  ; The lid over the rock lies on the ground, as the wood's does: what you cannot
  ; walk into is the cliff standing on it, not a roof at wall height.
  WallHeight = 0
  ; No WallShift: a rock stands in the middle of its own cell and takes none, and
  ; the masonry face sets itself back by its own measured depth.
  WallFillsRock = Yes
  ; Two rocks to a cell, close about its middle, each a little bigger or smaller
  ; and turned its own way: a cliff line rather than a row of one boulder.
  WallClump = 2
  WallSpread = 0.18
  WallVariety = 0.35
  ; Masonry is for what was built: the retaining wall under a raised floor, which
  ; the client draws only where rock stands above the foot of its face.
  RockFace = models/tiles/dungeon/wall.gltf
  ; The lid is the floor tile, and deeper in the rock nothing stands on it -- so
  ; it is darkened to read as rock in shadow rather than as more floor. Under the
  ; rocks themselves it barely shows: they cover their cell.
  CapTint = 0x5A5660
```

and the tones:

```
  Tones = [
    Tone
      Name = Stone
      Floor = models/tiles/dungeon/floor.gltf
      Wall = models/tiles/dungeon/cliff_a.gltf
    End,
    Tone
      Name = Cracked
      Floor = models/tiles/dungeon/floor_rocks.gltf
      Wall = models/tiles/dungeon/cliff_b.gltf
    End
  ]
```

Also rewrite the block's lead comment ("Worked stone, four-unit tiles…", "No corner post…") to say: caverns of worked-stone floor bounded by KayKit cliff rocks; a corner post has no notch to plug in a cliff line; the slab survives only as the RockFace.

- [ ] **Step 5: The cap-tint check no longer demands a standing wall** — in `everyThemeWhoseWallsStandUpTellsTheirTopsFromTheFloor`, delete the `checked` counter and the final `assertTrue(checked > 0, …)`, and add to its Javadoc: "No shipped theme stands its walls up now — both lay their lids on the ground — so this guards the next one that does (a hall of worked stone), and passes empty until then."

- [ ] **Step 6: Run the kit and theme tests**

Run: `./gradlew test --tests uz.dukeengine.dungeon.DungeonTilesTest --tests uz.dukeengine.dungeon.DungeonThemeTest`
Expected: PASS — including `everyThemeIsTheSizeItSaysItIs` (3.69 vs the model's 3.69), `everyThemeLiftsItsWallsOntoTheFloor` (base at 0), `andWhatFacesItIsShippedAndIsShapedLikeAWall` (the slab).

- [ ] **Step 7: Credit the rocks** — in `CREDITS.md`, after the "…and its rocks and bare trees" row:

```
| …and the cliff rocks the caverns are bounded by | KayKit | CC0 | `models/tiles/dungeon/cliff_*.gltf` |
```

- [ ] **Step 8: Commit** — only on the owner's word:

```bash
git add -A src/main/resources/models/tiles/dungeon src/main/resources/data/world/themes/dungeon.duke src/test/java/uz/dukeengine/dungeon/DungeonTilesTest.java CREDITS.md
git commit -m "The caverns are bounded by rock, and masonry only holds up raised ground"
```

### Task 2: The descent at 96×72

**Files:**
- Modify: `src/main/resources/data/world/generation.duke` (the `Layout` block)
- Modify: `src/main/java/uz/dukeengine/dungeon/stage/MapWriter.java:43-49` (the comment on `DEEP`)

**Interfaces:**
- Consumes: `DungeonSettings.mapWidth()/mapHeight()/minRooms()/maxRooms()/placementAttempts()` — existing.
- Produces: nothing new in code.

- [ ] **Step 1: The guards are already written** — `DungeonGeneratorTest.roomCountStaysInRange` (201 seeds hold `MinRooms..MaxRooms`), `DungeonGeneratorTest`'s corridor-length test (average under 20 cells, longest under the map's width), `LayoutTest.aBiggerLayoutDrawsABiggerDungeon` (160×120 × 40 rooms beats `MaxRooms`), `StageDifficultyTest.theLargeShippedStageIsLargeAndDeep` (deep beats the descent). No new test: the change is a number, and these say whether the number holds.

- [ ] **Step 2: Grow the floor** — in `generation.duke`'s `Layout`:

```
    ; Cells across and down. Four times the floor this used to be: a floor is now a
    ; place to go through rather than a room to clear, and the biomes coming next
    ; need room to be more than one of. Kept inside the 100 by 76 the 3D client
    ; carries today (deep.map), which also stays the larger of the two.
    MapWidth = 96
    MapHeight = 72
```

and the room counts and attempts:

```
    MinRooms = 16
    MaxRooms = 26
```

```
    PlacementAttempts = 8000
```

Rewrite the `MinRooms` comment's numbers to match ("sixteen is what always fits at these sizes") and keep `MaxRooms` under deep.map's 28 rooms, saying so.

- [ ] **Step 3: Run the generator and stage tests**

Run: `./gradlew test --tests "uz.dukeengine.dungeon.gen.*" --tests uz.dukeengine.dungeon.stage.StageDifficultyTest`
Expected: PASS. If `roomCountStaysInRange` names a seed under 16 rooms, lower `MinRooms` to the smallest count it reports and say so in the comment; if the corridor test fails, lower `MaxRoomSpacing` from 30 to 24.

- [ ] **Step 4: MapWriter's comment** — `DEEP` is no longer "four times the floor"; say it is kept at the size the 3D client carries today (one Geometry per stone cell, none batched) and larger than any floor of the descent.

- [ ] **Step 5: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; update the test count in `README.md` if it states one.

- [ ] **Step 6: Commit** — only on the owner's word:

```bash
git add src/main/resources/data/world/generation.duke src/main/java/uz/dukeengine/dungeon/stage/MapWriter.java README.md
git commit -m "The descent's floors are four times the size they were"
```

### Task 3: The owner looks

- [ ] `./gradlew run --args=deep` — `deep` is depth 8, which wears the Dungeon theme: the rooms should be ringed by cliffs, slabs only under raised ground, the rock beyond the ring dark.
- [ ] `./gradlew run`, Enter the dungeon — a forest floor at 96×72: frame rate, and how long the floor takes to appear.
- [ ] Tune `WallClump`, `WallSpread`, `WallVariety` and `CapTint` from what the owner reports; each change re-runs Task 1 Step 6.

## Next, in their own plans

- When E2 lands: several cliffs and spires per tone (`Rock_1_L/M`, `Rock_1_N–Q`), and bushes among the forest's trees so its edge reads closed where the grid is.
- Stage 3's biome generator, headless, behind `Biomes`.
