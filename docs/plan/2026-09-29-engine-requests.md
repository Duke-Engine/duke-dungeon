# Engine requests from duke-dungeon — world generation and display

duke-dungeon is growing its endless descent into much larger floors that mix several biomes (Minecraft-style:
a green wood, a pine wood, caves, ancient halls, a mine, … on ONE floor), with natural rock instead of wall
slabs, and with blocking that matches what is drawn. The game's side is in
`docs/plan/2026-09-29-world-generation.md`. This file is only what the engine is asked for, most urgent first.
Line references are from a read-only survey of duke-engine on 2026-09-29 and may have moved.

Constraints that hold for all of them:

- **One-kit maps draw exactly as today.** duke-generals and the dungeon's stages use the current paths.
- **Lock-step.** Anything that changes the simulation (grid, obstacles) must be a pure function of data every
  client has. The dungeon derives all of it from the floor's seed on every machine.
- **Data in, no game knowledge.** The engine is told cells, models, positions and names; biomes are the game's word.

## Status (2026-10-01)

Every engine request the game makes is kept in this one file, and only here. The owner pushes the engine to GitHub
and runs them himself in a cloud session; each section below stands on its own for that.

| Request | What | Status |
|---|---|---|
| E1–E7 | resolution, wall lists, large maps, per-cell looks, scenery, finer blocking, a creature's own level | landed in 0.7.0 (unreleased) |
| E8 | the engine split into `combat`, `rts`, `rpg` and their clients | steps 1–2 landed in 0.8.0 (the `combat` module, the runtime of no kind; the game follows at 1b82edf3); step 3 (`rpg`) and step 4 (the clients) waiting |
| E9 | an aura's picture follows its status | landed, 1bb64fa8 |
| E10 | one seamless world, ~900 × 900 cells, any size by design | landed in 0.8.0 (4236489b); the game takes its parts when it builds the open world |
| E11 | an untextured model keeps its own colour | landed, d7204414; the key's `Tint` is gone |
| E12 | a Layer block may say `Renews` (E9's option reachable from data) | waiting (small) |
| E13 | an armed thing-aim's press marked as the game's own order, not an attack | waiting (small) |
| E14 | a walk that stops short says so, and a way shut by bodies still goes as near as they let it | waiting: (1), (2) and (5) small, (3) a change to how a held mover plans |
| E15 | where the bottom panel stands, told to a game's painter | waiting (small) |

Versions: 0.7.0 is never released; the split ships as 0.8.0, the next and only Maven Central release. Until then the
game builds against the local engine.

**For a session given this whole file** (a cloud session working on github.com/Duke-Engine/duke-engine):

1. **E1–E7, E9, E10, E11 and E8's steps 1–2 are done** — they are here for their history. Do not redo them.
2. **E12, E13, E14's (1), (2) and (5), and E15 are small** and can be done now, each on its own; E14's (3) changes what
   a held mover remembers between its routes, and wants the reference's stuck handling read first.
3. **E8's step 3 (rpg)** lifts the game's own packages, and the game's newest code is not on GitHub yet: its
   stun-lifesteal-summons, key-to-the-keep and monsters-grow pieces are on the owner's master only, and the keep's seal
   and the auras are still being built. That step waits for them — ask him when. Step 4 (the clients) comes after it.
   The class-by-class proposal is the owner's page https://claude.ai/artifact/Sx1NpP81xmiqmLUny7Qakt (version 2).
4. Every change is announced to the game as the landings above were: the engine version, the API, what the game must
   change (for E8, the old-to-new package map of each step).

---

## E1 — Resolution: apply the Size setting live, open at the screen's own size (small, first)

A player's `~/.duke-engine/settings.properties` said `width=800 height=600 fullscreen=true`, so the game opened
at 800×600 stretched over a 2560×1600 panel and looked blurred. The Settings "Size" row already lists the
monitor's real modes but only saves them for the next launch (DukeRtsApp ~2443-2452, 2541-2565, 2741-2756),
while `Duke3D.display(w, h, fullscreen, told)` already resizes the live window (DukeRtsApp ~2671-2721).

- Choosing a Size (and switching Fullscreen) takes effect immediately, through the existing live path, and is saved.
- With no saved size, the first launch opens at the primary monitor's native mode rather than 1280×720.
- *Optional:* an anti-aliasing row (Off / 2× / 4×) saved and applied at the next launch — `setSamples` is never
  called today (Duke3D.launch ~166-196), so edges are fully aliased.

Done when: picking 2560×1600 in Settings changes the window at once, and a fresh profile opens at native size.

## E2 — A kit may have several wall models (small)

`Tileset.wall(path)` takes one model. The dungeon is replacing its wall slab with KayKit cliff rocks as a
"thing" kit (`WallFillsRock`, like the forest's trees), and seventeen rocks of one shape in a row read as a
pattern — the same reason `WallClump`/`WallSpread`/`WallVariety` exist.

- `wall(String...)` (or `walls(List<String>)`): each placement picks one model by the same deterministic
  position hash that already sizes and turns clump members (TerrainScene ~685-740, 794-870); with
  `WallFillsRock` each clump member picks its own.
- One path behaves exactly as today. `corner` could take a list the same way, if it is free.

Done when: a kit given three rock models shows all three, the same ones in the same places on every run.

## E3 — Large maps: stop paying per cell (biggest, blocks the map size)

The descent goes from 50×36 cells to about 128×96 (and should stay playable at 160×120). Today every floor
tile, lid, wall and tree is its own Geometry with no batching, instancing or chunking (TerrainScene
~1114-1120; nothing uses GeometryBatchFactory/BatchNode/InstancedNode); `drape` deep-clones every piece's mesh
(~514-539); the minimap draws one GUI quad per cell and re-materials every cell every frame (DukeRtsApp
~1226-1245, 1291-1336); fog softening is O(cells × 25) a frame and the fog texture is a fixed 256² stretched
over the map (Discovery ~315-355, FogMap ~111-133, Fog ~44); each A* search allocates four grid-sized arrays
(Pathfinder ~917-935). The dungeon's `MapWriter` already caps its big stage at 100×76 for this reason.

- Static terrain (kit pieces after draping) batched per chunk of cells and per material; fog culling per chunk.
- The minimap drawn from one texture, updated where discovery changes.
- Fog: texture sized to the map (or set per map), softening incremental rather than whole-map per frame.
- Pathfinder: per-search scratch reused (generation stamps), not allocated.

Done when: a generated 128×96 dungeon floor (kit, relief, fog, minimap on) holds 60 fps on an RTX 3070 laptop
at 2560×1600 and builds in under ~2 s, and 160×120 is playable.

## E4 — Per-cell looks: several kits on one map (biomes)

Today a map has one kit, chosen by `|look=Theme,Tone` on the status line (DukeRtsApp ~2943-3004). The dungeon
will register one look per biome exactly as it registers themes now (`Visuals.theme(name, …)`), and needs each
cell drawn with its own.

- The map record handed to `DukeGame.applyMapTerrain(grid, record)` may name a look per cell (for example a
  `lookAt(cx, cy)` returning a registered look's name, or a small palette plus a byte per cell). A cell with none
  uses the status line's look, so today's maps are unchanged.
- Each cell's floor, lid, clump and RockFace come from its own look. A boundary between an open cell and a rock
  cell is drawn by the **rock cell's** look (the rock decides whether it is a cliff or a wood).
- Fog tint follows the look under the camera's focus, eased over about a second.
- Per-look tint through the existing `tintFor` path is enough; no per-cell materials beyond one per (look, tint).
- If per-look placement numbers (TileSize, WallHeight, …) are costly to vary within one map, say so: the game can
  keep them equal across the looks it mixes.
- *Nice to have:* ground painted per cell under a kit (the survey's "skip FLOOR pieces when paint exists", with
  storey height added to `groundAt`, TerrainScene ~146-154, 436-438), which gives soft biome borders and one mesh
  per palette entry instead of a tile per cell.

Done when: a map whose cells name two looks draws a wood beside a cave with the right trees, rocks and floors,
and a map naming none draws as today.

## E5 — Scenery: static placed models outside the simulation

Grass, bushes, ore, banners — and later the boundary rocks and trees themselves — placed by the game at exact
positions. Today the only ways are simulation things (one node, fresh materials, per-frame sync, a minimap dot
and a snapshot entry each: DukeRtsApp ~6098-6365, GameLogic ~864-905) or kit pieces (only on rock cells).

- The map record may list scenery: model path, x, y (world), facing, scale, optional tint, and an optional
  footprint radius.
- Drawn batched per chunk with the terrain (E3), following the relief, hidden by fog like terrain.
- No simulation object, no minimap dot, no snapshot entry.
- With a footprint, it blocks the path grid the way a still thing's shape does (GameLogic ~864-905), and walking
  collides with it by shape. Without one, it is only drawn.

Done when: 5,000 scenery pieces cost no per-frame simulation work, and one with a footprint is walked round.

## E6 — Blocking that matches the drawing (after E3/E5)

A cell is 10 units and a rock or tree cell closes all of it, so two trees on neighbouring cells stop a hero even
where their models leave a wide gap. One `PathGrid` serves both simulation and drawing (DukeGame ~643-649,
DukeRtsApp ~1205, 3078-3104); props close whole cells (GameLogic ~887-901); clearance is a square of the mover's
radius about each cell's centre, with a zero-width retry when that fails (Pathfinder ~258-295, 1149-1175);
terrain is checked only at a walker's centre cell (GameLogic ~510-525).

- A navigation grid finer than the drawn tiles (5 units, or whatever the engine finds right), with drawing still
  one tile per 10-unit cell. Every threshold now counted in cells (standing beside, MoveUpdate tolerances, the
  5×5 mover block, search budgets, SoftenCells, …) reviewed for the new size.
- Scenery footprints (E5) close only the navigation cells they cover.
- Clearance close to the true gap: a gap between two footprints at least a mover's diameter (plus a small margin)
  is walkable for it, a narrower one is not; wide bodies do not visibly overlap rock.

Done when: a hero (radius 4-5) walks between two trees whose trunks stand 12+ units apart, and a boss (radius 8)
goes round.

## E7 — A creature's own level on its bar (2026-09-30, small)

The game is giving every monster a level of its own — rising with the floor's depth and, within a floor, with the
way to the boss (roadmap piece 4, `2026-09-30-roadmap.md`). The bar's medallion shows the floor's depth for anybody
but the hero (`UnitBarReading.levelOn`: `isHero ? heroLevel : depth`), so every monster on a floor shows one number.

- The game names a word prefix in its unit-bar look (e.g. `LevelWord = level:` in the `UnitBar` block → a field on
  `UnitBarLook`), and sets a condition on each creature, `level:7` (`GameObject.setCondition`, simulation state,
  already in the snapshot as `UnitView.conditions`).
- The medallion shows the number after that prefix for a creature carrying such a word; the hero keeps his own
  (`heroLevel`); anybody else falls back to the depth, as now. A game that names no prefix changes nothing.

Done when: two skeletons on one floor, one with `level:3` and one with `level:8`, show 3 and 8; a floor with no such
words looks exactly as today.

**Landed** in the engine as 1e1c71a4 (0.7.0, unreleased): `UnitBarLook` has a last component `levelWord`, and
`withLevelWord(String)` on any look; the game adds `LevelWord = level:` to its own `UnitBar` record and hands it
through `withLevelWord`, and sets `GameObject.setCondition("level:7")` on each creature (one such word per creature:
the words are sorted and the first that starts with the prefix wins). The hero keeps `heroLevel`; a creature with no
such word, or one that is not a number, shows the depth; a game naming no prefix changes nothing. The prefix matches
exactly, case included.

## E8 — RTS and RPG as two modules over one shared combat layer (2026-09-30, architecture: boundary now, moves later)

The owner's decision: duke-dungeon becomes **Last Hero**, a single-hero RPG in one open world, while duke-generals stays
an RTS. RPG systems (skills with mana and ranks, stuns, auras, lifesteal, items, missions) should not weigh on the RTS,
nor production and economy on the RPG. What both use lives once, beneath them — as the reference does it: Generals
sends a rank, an upgrade and a battle plan through one `WeaponBonus` table, and its stun is an expiring status (EMP,
hacking), not a hero's skill. The owner's boundary:

```
core   — world, things, modules, pathfinding, map, network, replay (as now)
combat — weapons and their bonuses, statuses (stun, slow, root), experience   ← shared by both
───────────────────────────┬──────────────────────────────────────────────
rts                        │ rpg
production, economy,       │ hero levels, skills, auras, lifesteal,
construction, command card │ inventory, missions, dialogue,
                           │ single-hero control
client: one 3D client + the RTS panels │ the RPG panels (bag, skill cards, mission tracker, bubbles)
```

- **combat, shared:** `Weapon*`, `WeaponBonus`, `RateOfFireModifier`, `DamageModifier`, `StatusUpdate` (DISABLED is the
  stun, HELD the root), `ExperienceModule`'s data ladders, `AutoHealUpdate`, shots, and the aura as a mechanism (BFME's
  leadership is an RTS aura too). Effects live here once; what grants them differs by side.
- **rts:** production, construction, supply and power, docking, harvesting, turrets, crushing, special powers a player
  buys, the command card.
- **rpg:** hero progression; the skill book (mana, cooldowns, ranks opened by level, the ways a skill lands — strike,
  skillshot, area, summon, …); passives and auras granted by skills and items; lifesteal; the bag and items (kinds,
  joining, uses such as unlocking a gate); mission steps; lines said in a bubble; and single-hero control — selecting
  anything shows it, orders always go to one's own hero, with no reselecting.
- **client:** one 3D client, with each side's panels shipped beside its module (today `HeroPanel`, `HeroPortrait` and
  the skill card sit in `client3d`).
- **What the game offers:** the RPG half already runs in duke-dungeon's own code — `skill/` (SkillBook, SkillEffect),
  `loot/` (LootBag, items, errands), `level/` (hero progress, attack speed), `combat/` (Swing, ArrowUpdate, Lifesteal),
  `run/Mission` (arriving with the key-to-the-keep piece) — a working seed to lift into the rpg module, generalised
  away from the dungeon's settings.
- **Order:** the boundary first — which packages and modules, what moves where — agreed with the owner; then the moves
  when the engine is ready, each announced with the engine version and the new packages, so the game follows in one
  step. Until then the game keeps building against today's packages and writes new RPG code along this boundary.

Done when: duke-generals builds and plays without the rpg module; duke-dungeon's RPG systems live in the engine's rpg
module and the game keeps only data and its own content (generation, biomes, the world, the story).

**Decided** (the engine session's proposal, the owner's answers, 2026-09-30): modules `combat`, `rpg`, `rts-client-3d`
and `rpg-client-3d`; the snapshot's view records move into `core`; a small shared move/attack/stop set in `combat`;
pursuit and engaging (`PursueUpdate`, `Engaging`, `GuardRules`) move into `combat` too; no 0.7.0 release — each step
lands on master with an old-to-new package map, and 0.8.0 ships the whole split; the engine's CLAUDE.md module rules
are rewritten for the new layering. Order: combat → a neutral runtime → rpg (after the dungeon's stun and keep branches
land) → the clients. Open: where the loot drop tables live (combat, or a `loot` module of their own) and whether they
move to core's `LogicRandom`.

**Landed**, steps 1–2 (0.8.0, merged as ca0f9bd4 and 65f86e89; the old-to-new map is the CHANGELOG's 0.8.0 "What to
change"): the `combat` module with the weapons, statuses, experience, pursuit and errands; move, attack and stop as
`CombatOrder`, a word order as `combat.message.GameOrder`; the views in `core.view`; the runtime of no kind, the RTS's
own API on `RtsFlavour` and a side as `RtsPlayer.of(logic, index)`. The game followed with imports and one call.

## E9 — An aura's picture follows its status (2026-09-30, small)

The dungeon's stun plays an AURA effect (`Stunned`, two layers) on whoever it stuns, its seconds set to the stun's.
Two ways the picture and the status part (`LayeredEffects`, lines from 8531470e):

- A second cast of an aura already burning on the same unit is dropped (~413-421, `alreadyBurning` ~950-957, pinned by
  `LayeredEffectsTest` ~308-323), while `StatusUpdate.apply` restarts the status: a hero stunned again within the
  stars' life loses them before the second stun ends. Asked: a re-cast of a burning aura extends it to the new end.
- A continuous layer riding a unit keeps feeding until span + its longest life (`untilFor` ~495-510; `feed` stops at
  span only when nobody is followed, ~819-826), so the stars go on being made ~0.6 s after the stun is over. Asked: a
  followed continuous layer stops feeding at its span, as an unfollowed one does. (The shipped frost's `Caught` layer
  has the same overrun.)

Done when: a hero stunned twice 0.5 s apart wears the stars until the second stun ends, and none are made after it.

**Landed** as 1bb64fa8 (0.7.0, unreleased): `EffectLayer.Builder.renews(boolean)`, AURA layers only, default false (the
knight's Whirlwind keeps the drop). A renewing aura cast again on a unit it burns on moves its end to the new cast's
end and feeds again. A continuous layer riding a unit now stops being made at its span, no opt-in. The game adds
`case "renews"` to `Main.layerOf` and `Renews = true` on the `Stunned` layers.

## E10 — One seamless world (2026-09-30, big — after E8)

A request from the game duke-dungeon, which is becoming **Last Hero**: a single-hero RPG whose whole campaign happens in
one open world, with no loading between its regions. This section stands on its own: what the game needs, what the
engine already has, the rules the engine keeps, and when the work is done. The engine decides how.

### Before starting

- **Work from the engine's newest commits.** The local checkout (`C:\Users\abdur\IdeaProjects\duke-engine`) was 27
  commits ahead of `origin/master` on 2026-09-30 — all of 0.7.0's large-map work, E7 and E9 among them. A session
  started from GitHub without them starts from an older engine.
- **E10 comes after E8,** the split of the engine into `combat`, `rts` and `rpg`: start from the engine as E8 left it.
  The world's work belongs in `core` (map, partition, pathfinding, things) and the client's drawing, beneath both sides.

### What the game wants

- **One world of about 900 × 900 cells** (a cell is 10 world units, so about 9,000 × 9,000 units). A hero walks about
  2.4 cells a second: some six minutes straight across, much longer along its roads. It holds the whole campaign — the
  last free kingdom (a city with castles), a desert, mountains, several conquered kingdoms and the villain's seat.
- **Size is only a number.** The owner wants the engine to carry a world of any size the game draws, so the design is
  chunks all the way down, and a frame's cost follows what is near the hero, never the world's area. The stress cases
  are 1,024 × 1,024 and 4,096 × 4,096 (about 41,000 units across, where a float position still has a few thousandths of
  a unit to spare). Up to that the whole cell grid may stay in memory. Beyond it — cells streamed from disk, positions
  past about 100,000 units needing a moving origin, a world generated as it is walked — is not asked now, but nothing
  in the design should rule it out: say where its next limit lies and what lifting it would take.
- **Seamless:** after the first seconds of a load, walking anywhere never shows a loading screen or a stall.
- **The game hands the world over as data,** as it hands a floor today (`DukeGame.applyMapTerrain`, a `Looked` record
  for per-cell looks, a `Dressed` record for scenery). The regions, biomes and kingdoms are the game's words; the engine
  is told cells, looks, heights, models and positions.

### What the engine already has (0.7.0, unreleased)

From the changelog: a route search reuses its arrays; the fog recomputes only where the open cells changed and redraws
only near them, `Fog.texelsPerCell` sizes its picture to the map; the minimap is one texture repainted where knowledge
changed; a kit's floor is gathered into chunks of 16 × 16 cells, one geometry per material per chunk, bent by the relief
in place, and dark chunks are left out; per-cell looks (`Looked`, `lookAt`); scenery outside the simulation
(`Dressed`, `MapScenery`, footprints kept by true distance, `PathGrid.clearOfCircles`). That work targeted 160 × 120.
A world thirty times its area needs the next step.

### What is asked

1. **Drawing streamed by chunk.** Only chunks within the view of the camera are built and kept: floor, lids, rock
   faces, scenery, their materials. A chunk's geometry is made when it comes near and dropped when it is far, built
   off the frame's budget (another thread, or a few chunks a frame), so crossing a chunk's edge never costs a frame.
   What lies beyond the drawn distance is hidden well (haze, fog, or a cheap stand-in — the engine's choice). Shadows,
   streams, weather and effects keep working.
2. **A world's cells kept compactly.** The whole grid may stay in memory up to the stress sizes — 810,000 cells is a
   few megabytes as bytes — but no object per cell, and no per-cell work per frame anywhere (fog, minimap, looks,
   relief).
3. **Things far from every hero asleep.** A thing no hero is near does no work a frame — no brain, no step, no weapon,
   no status timer — and keeps its state. It wakes when a hero comes near, on the same frame on every machine: sleeping
   and waking are decided from simulation state alone (for example distance in cells to the nearest hero, checked at a
   fixed cadence in a fixed order). A frame's cost follows what is awake, not what exists; the spatial index is sized
   for the world.
4. **Routes across the world.** A route between any two points is found in bounded time (a hierarchy — routes between
   chunk portals, then local search — or whatever the engine prefers), the same on every machine. The small maps of
   today keep their routes to the bit, or the change says where they differ and why.
5. **Fog and maps for the whole world.** What the player knows is kept for every cell; the fog's picture is made only
   near the camera. The minimap shows the part of the world around the hero; a whole-world view, zoomed out, can be
   drawn from the same knowledge (a picture the game can show, at least).
6. **Precision:** positions up to about 41,000 units draw and move without visible jitter (camera-relative drawing if
   the float range makes it necessary).
7. **Room for saving.** The world's changes — things killed, opened, taken, what the player knows, the heroes — must be
   possible to write and read back. Saving itself is a later request; this one only must not make it impossible (no
   simulation state that lives only in the client).

### Rules the engine keeps

The three at the top of this file: maps that do not use this draw and play exactly as today (duke-generals, the
dungeon's floors and its fixed stages); lock-step (drawing may use threads, the simulation may not depend on them);
data in, no game knowledge.

### Done when

- A generated 900 × 900 world with several looks, relief, scenery, fog and the minimap — and a 1,024 × 1,024 stress
  world — lets a hero walk from one corner to the other at 60 fps on an RTX 3070 laptop at 2560 × 1600, with no frame
  over 33 ms at chunk edges and no loading pause after the first seconds.
- A 4,096 × 4,096 world loads in about the same time and walks at about the same frame cost as the 1,024 one: nothing
  a frame grows with the world's area.
- 5,000 monsters spread over the world cost no work a frame except those near a hero, and a sleeping one wakes when a
  hero comes near, on the same frame on two machines in lock-step.
- A route from one corner of the world to the other is found in bounded time, the same on every machine.
- duke-generals, the dungeon's floors and its stages look and play as before.

### Tell the game

When it lands: the engine version, the records and entry points a game uses to hand over a world, what it must
provide, and any limit that remains — as the E7 and E9 landings were told.

**Landed** in 0.8.0 (merged as 4236489b; its CHANGELOG's "One seamless world" is the full account). Each part is the
game's to take, and a game that takes none plays as before: things asleep far from every waker (`Sleep`,
`GameLogic.setSleep`, `DukeGame.sleep`); routes by sectors (`Sectored`, `GameLogic.setRouteSectors`); sight kept in
chunks, stone that stops it (`GameLogic.setSightHiddenByStone`) and a side's memory saved with the game
(`getSightMemory`, `setSightMemory`); the ground built round the camera (`Visuals.streamGround`), the minimap round the
hero (`Visuals.minimapSpan`), a haze at the ground's edge (`Visuals.haze`) and the whole world as a picture
(`Duke3D.worldPicture`). The next limits it names: the grid kept whole in memory (~11 bytes a cell), a world handed
over whole, positions past ~100,000 units. The dungeon's floors take none of it yet; Last Hero's open world will.

## E11 — An untextured model keeps its own colour (2026-09-30, small)

The keep's key (`key_crown.glb`) is one material, "silver mat": a `baseColorFactor` of about 0.24 grey, rough 0.16,
and no texture. The client dresses every creature's and thing's model anew (`DukeRtsApp.dressModel` → 
`creatureMaterial(colours, tint)`, lines from 8531470e ~6414-6438, 6647-6658): `colours` is the look's texture or the
loader's first texture (`skinOf` ~6560-6570), and with neither the diffuse is the look's `Tint`, white by default — so
the key lies near-white, and its silver is lost. Textured models (the gate, the fountain) are not affected.

- With no texture from the look or the model, keep the loader's own base colour (glTF `baseColorFactor`) as the
  colour the tint multiplies, as a texture is kept today.
- A look that names a `Tint` still tints it; a model with a texture draws exactly as now.

Done when: a glb with only a base colour and no texture draws in that colour under a white tint, and every textured
model draws as before. Until then the game writes the colour as the look's `Tint` (key.duke).

**Landed** as d7204414 (0.8.0): an untextured glTF piece is drawn in its `BaseColor` (or `Color` when unlit), the tint
multiplying it; a thing carried in a hand the same. The game dropped the key's `Tint`.

## E12 — A Layer block may say `Renews` (2026-09-30, small)

E9 gave `EffectLayer.Builder.renews(boolean)` (AURA layers: cast again on a unit it still burns on, the aura lasts to
the new end instead of being dropped), but the data record a game's Effect blocks are read into,
`uz.dukeengine.core.content.Layer`, has `Follows` and no `Renews`: a file that writes `Renews = true` in a Layer block
is refused at load (`DataException`), so no game can ask for it in data.

- `Layer` gains `Boolean renews`, read from `Renews = true|false`, and its `fields()` hands it on as `"renews"`, as it
  does `"follows"`. Unsaid, nothing changes.

Done when: a Layer block with `Renews = true` loads and reaches the builder as renewing; one without it does not.
Until then the game asks the client itself to renew the one look a status wears — the Combat block's `StunLook`
(`Main.layerOf(art, settings)`); with this landed, that becomes `Renews = true` on the `Stunned` layers in data.

## E13 — A thing-aim's press marked as the game's own order (2026-10-01, small)

The dungeon's key is used by a left click on it in the bag (arming a `CommandButton` with `Aim.UNIT`, id `use:<slot>`)
and then a click on the keep's gate. The client answers that press (`DukeRtsApp.aimArmedButton`, ~4901-4915 at
1bb64fa8) with an ATTACK-kind order mark on the gate and the attack acknowledgement — red, as if the hero were sent to
fight it — where a right click on the same gate (a context order, `game.contextOrder`) flashes the order colour
(`OrderMark.ContextColour`, yellow).

- An armed button whose press is the game's own order on a thing is marked as a context order is (its colour, its
  acknowledgement), not as an attack — for example a `CommandButton` saying which mark its press wears, the attack's
  staying the default so an ability aimed at an enemy is marked as today.

Done when: a use aim pressed on the gate flashes the context colour; an attack ability aimed at an enemy looks as now.

## E14 — A walk that stops short says so, and a way shut by bodies still goes as near as they let it (2026-10-01)

A hero sent from the way in to the keep's gate with one order, on the first floor of 40 seeds, did not get there on
13 (the game's probe, 2026-10-01; the same 13 on 0.8.0). Three of the causes were the game's and are fixed there;
the rest are the locomotor's and the pathfinder's, and any caller that waits on a walk meets them. Line numbers are
at 1bb64fa8; each place is also named by a comment in it, which the 0.8.0 code still carries.

1. **A goal in no zone ignores what the bodies leave open** (small). `Pathfinder.findPathOrNearest`, the branch for
   a goal the mover's zone does not hold (~318-335; the one whose early return says "nowhere nearer than where it
   stands"): a goal whose cell is in another zone, or in none — a gate, a building, the usual end of a walk *up to* a
   thing — sends the search to the mover's zone's nearest cell to it with `orNearest = false`, costed by the traffic.
   Where a still enemy stands on that cell, or across every way to it (the traffic closes both), both searches come
   back empty and the route is `Path.partial(List.of())` — nowhere nearer — though open ground much nearer the goal
   can be walked to. On seed 0 a hero 660 from the gate was told so at once, because a healer stood on its threshold.
   In 0.8.0 the branch is the same (~775-790), now through the `Searcher` E10 added; the game's floors are not
   `Sectored`, so they take the whole-grid one.
   - Asked: the search there finds the nearest it can (`orNearest = true`, or the nearest cell it reached), as the
     connected branch does.
2. **A re-plan round a body that finds nowhere leaves the mover neither arrived nor short** (small). `MoveUpdate.update`
   → `giveWay` → `sortOutTheHold` → `planAgainRound` → `planRoute` (~1039-1049), and `planRoundWhatStopsIt` (~1022):
   when the route round the movers it is stuck behind has no waypoints, `update` returns (~752, "it stepped aside, or
   planned again and has nowhere to go") with the mover not moving, its goal kept, `stoppedShort()` false and
   `isGoalReachable()` false. Nothing calls `routeWalked` or `nowhereNearer`, so whoever waits on `stoppedShort()` waits
   for ever — and the goal is lost later to `standStill`'s `moveTo(position)` or to new legs. Seeds 4, 9 and 24 stood
   like that until the end.
   - Asked: a re-plan that leaves it standing short of its goal is stopped short, as a route given that leads
     nowhere nearer already is.
3. **Held between two still bodies, it re-plans between them for ever** (a change to its planning state, not a
   line). `planAgainRound` sets `round` to this frame's holders only: the route round A runs into B, the route round B
   into A, and `round` goes {A}, {B}, {A} — so the pass-through rule, which wants the same set twice and no step further
   (`round.equals(roundLast)`, ~487), never fires; and each re-plan resets the progress count (`planRoute` →
   `resetProgress`), so the stuck check never fires either. On seed 8: 79 routes in 4700 frames, walking on the spot.
   - Asked: what a held mover remembers between its routes covers the movers it has planned round while it made no
     headway — added up, not replaced — so that it passes through them or stops short within the stuck limit.
4. **Also seen:** a route through the gap between two round still things that the body does not fit: a statue and a
   pillar whose centres are 14.1 apart, radii 3.5 and 3, so 7.6 between their edges, for a hero 8 wide. The cells on
   either side have room at their centres, so the route goes through; the locomotor refuses the step, circles, gives
   up after two seconds, and a fresh route from there plans the same gap. Worth a look when (1)–(3) are done.
5. **A step aside drops a walk exactly to a point, yet goes on naming it, arrived** (small). `MoveUpdate.stepAsideFor`
   (~1180-1208; the one that goes "on to where it was sent afterwards if it was on its way somewhere"): what it goes on
   to (`goOnTo`, ~1199) is kept only for a walk to a place, so a walk exactly to a point — `moveExactlyTo`, a walk onto
   a thing to take or use it — is dropped; but the step sets `toPlace = true` and keeps `sentTo`, so `getGoal()`
   (~666-671) goes on answering that point, and once the step ends the mover reads as having got there as near as it
   could: not moving, not `stoppedShort()`, `isGoalReachable()`. In co-op a hero sent for a chest 300 away, met head on
   by a friend, stepped aside 155 short of it and was told he had arrived. The same at 0.8.0.
   - Asked: the step goes on afterwards to a point it was walking exactly to, as it does to a place — or, dropping
     the walk, reports no goal.

Done when: sent at a building whose nearest open cell a still enemy stands on, a mover walks up beside that enemy;
after a re-plan round a body that finds no way, `stoppedShort()` is true; a mover held between two still movers
passes through them or stops short within the stuck limit; a mover sent exactly to a point and asked aside on the
way goes on to it afterwards, or has no goal.

Until then the game copes in `ItemErrand`: legs that stopped short of a thing, stand as (2) leaves them, or stopped
in any way at all — a step aside included, whatever they report — are taken up again from where he stands by a walk
to the place (which goes through the connected branch), and only a walk that finds nowhere nearer than where he
stands ends it there; and an errand is given up — his legs stopped, the hero saying `NoWayWord` — when he has stood,
neither walking nor fighting, for `StuckFrames` without getting a cell nearer, or gone `StuckFightingFrames` without
getting nearer however he spent them (what ends (3) for him).

## E15 — Where the bottom panel stands, told to a game's painter (2026-10-01, small)

The dungeon draws a row of small pictures over the bar of the creature the player picks — the auras on it and a
haste — through its own `Painter`, which draws over the client's HUD. A row over a bar the panel at the window's foot
covers would be drawn over the panel, and the pointer over the panel still names the creature behind it
(`DukeGame.getPointedAt`: the pick goes through the slab, the world region being the whole window), so the game keeps
both off the panel. To know where the panel stands it copies the engine's figure: `HeroPanel.SLAB_HEIGHT` (the band of
172 and a pad of 10 over and under it, 192 design pixels) times the panel's legible scale,
`clamp(width / PanelLook.designWidth, minScale, maxScale)`. That misses the squeeze `HeroPanel.scaleFor` gives a window
too narrow for the bar's blocks (its `fits`), and goes stale the day the slab changes.

- Asked: the `Canvas` handed to a painter says where the bottom panel stands this frame — its rectangle, or its top
  edge, in canvas pixels, and nothing while it is hidden — as `barOf` says where a thing's bar is.

Done when: a game's painter reads where the bottom panel stands at any window size, squeezed or not, and nothing while
it is hidden. Until then the game copies the height, with a `ponytail:` note on it (`BuffScreen.PANEL_SLAB`).

---

**Order the game can use them in:** E1 and E2 at once (small). E3 before the descent grows past ~100×76. E4
and E5 unlock biomes on screen (the game's biome generator is written and tested headless meanwhile). E6 last.
Please tell the duke-dungeon session as each one lands, with the API it ended up as and the engine version it is
in — this repo is the game's, so the engine side is best not written into it.
