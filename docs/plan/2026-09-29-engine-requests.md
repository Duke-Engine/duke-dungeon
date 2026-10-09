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
| E8 | the engine split into `combat`, `rts`, `rpg` and their clients | steps 1–2 landed in 0.8.0 (the `combat` module, the runtime of no kind; the game follows at 1b82edf3). **Step 3 landed** in 0.8.0 on the engine's branch `e8-3-rpg` (812b0c36..2f13dc52): the `rpg` module, this game's `skill/`, `loot/`, `level/`, `combat/`, `run/Mission`, `GateUpdate`, `Seal`, `ai/SightLine` and `Facing` in `uz.dukeengine.rpg.*`. **Step 4 landed** in 0.8.0 on the branch `e8-4-clients`, made from `e8-3-rpg` (ebc07810..72686d21): `rts-client-3d` and `rpg-client-3d`, the hero bar and its portrait in `uz.dukeengine.client3d.rpg`, this game's `BagScreen` and `BuffScreen` there too, the hero card written by `HeroCard`, and single-hero control. Both merged to master, pushed, and released as 0.8.0 (tag `v0.8.0`); the game took them at 186fd82. The game: depends on `uz.duke-engine:rpg-client-3d` (and `rts`, for `RtsFlavour`); changes imports by the CHANGELOG's 0.8.0 tables ("A new module, `rpg`" and "The client is split"); deletes its `BagScreen` and `BuffScreen` and builds the engine's with its words, its `bonusOf`/`extraOf` as their lines, `PartyOrders::of` as the wire and `MissionScreen` under the bag; writes its `HeroStatus` through `HeroCard`; sets the bar's looks on `HeroLooks.of(visuals)`; and needs no selected hero for his orders |
| E9 | an aura's picture follows its status | landed, 1bb64fa8 |
| E10 | one seamless world, ~900 × 900 cells, any size by design | landed in 0.8.0 (4236489b); the game takes its parts when it builds the open world |
| E11 | an untextured model keeps its own colour | landed, d7204414; the key's `Tint` is gone |
| E12 | a Layer block may say `Renews` (E9's option reachable from data) | landed, 97e3c86c |
| E13 | an armed thing-aim's press marked as the game's own order, not an attack | landed, 308841ce |
| E14 | a walk that stops short says so, and a way shut by bodies still goes as near as they let it | landed, b4c291c3: (1), (2), (3) and (5); (4) was only noted |
| E15 | where the bottom panel stands, told to a game's painter | landed, 0adfc96c |
| E16 | a painted world drawn as Warcraft III, Dota and Generals draw theirs: streamed in chunks, blends from the paint, mips, tint, cliffs by slope | landed, 28fe3df5…822daabb (on e16-painted-world); the game draws its painted test map with it |
| E17 | a thing that hides what is behind it from sight while it stands (`BlocksSight`) — the keep's shut gate | landed, d5db75c8 + 36f49f8e (on e17-blocks-sight) |
| E18 | streaming the ground per map, not for the whole run | landed, 574011ae (on e16-painted-world); the game asks it per map |
| E19 | a game's rows on the pause menu, and a word to the game before the client stops (saving) | landed, b66f00a7: `Shell.pauseRows`, `Duke3D.onLeaving`; the game saves with both |
| E20 | a game adds its own right-click actions (talk to a person) without replacing the engine's pickup and gate rules | landed, 2a863ef9 (on e20-context-orders); the game adds its Talk rule beside the bag's |
| E21 | the move order's arrowheads lie on the ground's slope | landed, b5fc663d |
| E22 | the story half lifted into rpg: zones, dialogue, quests, triggers and cutscenes, people and trade | waiting (big) |
| E23 | a long list in a .duke file read in linear time (the world map's scenery) | landed, e05164d8 (on e23-linear-lists) |
| E24 | haze (and the world picture) chosen per map, as streaming is | landed, 60557fac (on e24-haze-per-map); the game hazes its wide worlds |
| E25 | a hero's worn slots by kind and a backpack that gives nothing | waiting (medium) |

Versions: 0.7.0 is never released; the split ships as 0.8.0, the next and only Maven Central release. Until then the
game builds against the local engine. E12–E15 reached it together at 668045f0 (their pull requests merged as
e4666f06, b03a781c, 8f6bbd87 and 668045f0), which is still 0.8.0.

**For a session given this whole file** (a cloud session working on github.com/Duke-Engine/duke-engine):

1. **E1–E7, E9–E15 and E8's steps 1–2 are done** — they are here for their history. Do not redo them.
2. **The small ones — E12, E13, E14 and E15 — are done too**, landed together by 668045f0 and taken up by the game:
   only E8's step 3 and step 4, below, remain — and E16.
3. **E8's step 3 (rpg)** lifts the game's own packages, and the game's master is on GitHub with every piece since
   the stun (the key to the keep, monsters that grow, the haste and auras, the keep's seal, the long walk, what an aura
   looks like). That step waits only for the owner's word — ask him when. Step 4 (the clients) comes after it.
   The class-by-class proposal is the owner's page https://claude.ai/artifact/Sx1NpP81xmiqmLUny7Qakt (version 2).
4. **E16 is the open world's ground** — big; its section stands on its own, its parts in the order it gives.
5. Every change is announced to the game as the landings above were: the engine version, the API, what the game must
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

**Landed** as 97e3c86c (0.8.0, merged as e4666f06): `Layer` gains `Boolean renews`, after `follows`, read from
`Renews = Yes|No` and handed on by `fields()` as `"renews"`, as `follows` is; a block that does not say it hands on
nothing, and is drawn as before. The game took it up: `Main.layerOf` has `case "renews"` beside `"follows"`, and its
code rule — `wornAsAState` and the trailing `.renews(...)`, with the settings argument only they asked for — is gone.
The AURA layers of the five looks worn as a state (the stun's `Stunned`, the haste's `Hasted`, and the riders of
`MightAura`, `ManaAura` and `BloodAura`) say `Renews = Yes`, and `DungeonSettings` refuses a file where an AURA layer
of such a look does not, by a message naming the look and the layer; a MARK ring says nothing, the client lays it anew
at each beat.

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

**Landed** as 308841ce (0.8.0, merged as b03a781c): `CommandButton` gains a last component, `answeredAs`, and
`answeredAs(String word)` gives the same button answered as that word: pressed on a thing, it is marked and voiced as a
click giving the word on a thing is (`Visuals.wordMark`, `Visuals.orderAnswer`) — here the ring of the game's own
orders, in `ContextColour`, and a move's voice, as a right click on the gate gets — where it was the attack's ring and
the button's own name voiced. A button naming none is answered as before, and every constructor the game called builds
one as before. The game's use aim, the key armed from the bag, says `.answeredAs(PartyOrders.USE)` (`"use"`, public now
as `PICK_UP` and `TO_THE_GATE` are); the drop aim, which aims at the floor, names none.

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

**Landed** as b4c291c3 (0.8.0, merged as 8f6bbd87), four of the five points: (1) a goal no zone holds is gone for to
the nearest cell it can reach where the bodies shut the zone's nearest, so a hero sent at the gate with a still enemy on
its threshold walks up beside him; (2) a route again round a body that finds nowhere nearer has the walk stopped short
(`stoppedShort()`); (3) a mover held between bodies plans round all those it has been held by while it makes no
headway, and so passes through them or stops short within its stuck limit; (5) a step aside goes on afterwards to a
point it was walking exactly onto. (4), the gap two round things leave that a body does not fit, was only noted as seen,
and is not among them. The game changed no code and removed no behaviour: `ItemErrand`'s look again after his legs stop,
its standing watch (`StuckFrames`) and its cap (`StuckFightingFrames`) stay as safety, and the notes in `ItemErrand`,
`world.duke` and the tests that said what the engine did before say what it does now. The look again still earns its
place: sent up to the gate from the way in, the hero kept alive, on the first floor of seeds 0 to 39 at 668045f0, 37 get
there with the guards and 32 without the look again (the 6th, 9th, 23rd, 34th and 36th are the five it takes up), and
the 2nd, 4th and 39th end saying `NoWayWord` either way; `GateTest` sends him from the way in on the 6th.

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

**Landed** as 0adfc96c (0.8.0, merged as 668045f0): `Canvas.bottomPanel()`, a default method answering null, says where
the client's bottom panel stands this frame as a `Canvas.Box` in the canvas's pixels — the bar across the foot of the
window, as tall as it is drawn, so a window too narrow for its blocks squeezes it below the `192 × scale` the game had
copied — and null while it is hidden. The game's `BuffScreen` asks it: a pointer inside the box names nothing for a row,
a row whose bar lies behind it is not drawn, and null keeps nothing off. `PANEL_SLAB` and its `ponytail:` note are gone;
the panel's legible scale stays, for the size of the pictures.

## E16 — A painted world, drawn as Warcraft III, Dota and Generals draw theirs (2026-10-01, big)

Last Hero's open world (the campaign: `docs/plan/2026-10-01-campaign.md`; the world E10 made possible, about 900 × 900
cells of 10 units) is to be painted ground — textures blended into each other, relief, no grid — not the dungeon's
KayKit floor tiles, whose cells show. The engine already paints ground (`GroundPaint`, `TerrainScene`: a map's `@Paint`
rows and palette, world-scaled texture repeat, `@Overlay`/`@Fade` blends, `@Relief`), and duke-generals draws its maps
with it. At an open world's size and look it falls short; measured with the engine's own `TerrainScene.rebuild` on a
synthetic 900 × 900 painted map (6 surfaces, 13.5% of cells blended), lines from 668045f0:

- It is built whole, never streamed: one mesh per palette entry for the world (TerrainScene ~266-274, 351-422), its
  scenery too (~315-318); E10's streaming is the kit's path only (~572-582, 669-672). At 900 × 900: 148 MB of buffers
  (197 MB with `groundLight`), 3.8 M vertices and 2.06 M triangles, every mesh's bound spanning the world, so all are
  sent every frame; 4,096 × 4,096 would be several GB. `applyDiscovery` loops over every cell each frame (~1750-1756).
- A blend is one cell wide, a straight ramp in one of 16 cell-shaped fades (FadeShape ~17-48), and nothing derives it
  from the paint: a game must author `@Overlay`/`@Fade` per cell (Generals' maps carry SAGE's baked blends). Borders
  follow the grid, never ragged as in Warcraft III or Dota.
- Ground textures are loaded without mipmaps or anisotropy (DukeRtsApp ~7825-7838; jME's default minification is
  bilinear with no mips), so ground shimmers at a distance under a tilted camera.
- One colour map per surface: no tint, no de-tiling, no normal or detail maps; per-cell flat normals unless
  `Visuals.groundLight` is set; a cliff's texture stretches unless the game writes `@PictureCorners` for every steep
  cell; `#` cells on a painted map draw as 6-unit grey boxes at the floor, not on the relief (~286-314).
- A kit beats paint, and a game's default kit counts (~251, 204-209, 258-261), so a game whose scene names a default
  `Tiles` floor cannot paint a map without dropping it.

Asked, in the order the game can use them:

1. **A map may choose paint over a game's default kit** (a `Painted` map with rows wins), and **ground textures get
   mipmaps and anisotropic filtering**, as a model's skin already does. (Small.)
2. **Paint inside E10's chunks.** Painted ground — its surfaces, overlays and a painted map's scenery — built a chunk at
   a time round the camera, culled, and let go far off, as the kit's floor is; each chunk, corner for corner, what the
   whole build makes; per-cell work every frame only for cells near the camera.
3. **Blends from the paint alone, Warcraft III style.** Per-cell corner weights of up to four surfaces derived from
   `@Paint`, the border between two broken by a noise (or each surface's own height) texture thresholded in the shader,
   so borders are soft and ragged rather than cell-shaped; a map's own `@Overlay`/`@Fade` drawn as today, so Generals'
   maps keep their look.
4. **A surface's tint and de-tiling.** A palette entry may be a texture times a colour; a surface may vary per cell
   (rotation or offset of its picture) or carry a large variation map, so one texture over a valley does not repeat
   visibly.
5. **Cliffs by slope.** A steep cell drawn with a cliff surface mapped without stretching (triplanar, or slope to a
   named palette entry), so a game need not write `@PictureCorners`; `#` cells on a painted map draw nothing.
6. **Smooth relief by default:** smooth normals without `groundLight`; optionally each cell cut 2 × 2.

Done when: a generated 900 × 900 painted world with at least six surfaces and relief walks with E10's frame budget (no
frame over 33 ms at a chunk's edge) and its memory in chunks round the camera; the borders between its surfaces are
soft and ragged, its ground does not shimmer at a distance, its cliffs are textured without per-cell picture corners;
and every duke-generals map looks as it did.

Until then the game tries painted ground on small maps — its palette KayKit's own colours as `#RRGGBB` entries, with
`Visuals.groundShade` for variation — and keeps the descent on its kit. Textures to paint with are the game's to choose
(hand-painted, as Warcraft III's; licensed so the PNGs may sit in a public repository).

---

**Order the game can use them in:** E1 and E2 at once (small). E3 before the descent grows past ~100×76. E4
and E5 unlock biomes on screen (the game's biome generator is written and tested headless meanwhile). E6 last.
Please tell the duke-dungeon session as each one lands, with the API it ended up as and the engine version it is
in — this repo is the game's, so the engine side is best not written into it.

## E17 — A thing that hides what is behind it from sight (2026-10-08, small)

The owner, 2026-10-03: while the keep's gate is shut, nothing on its far side is seen — the player does not see into
the court. The fog already does most of it: the court's walls are stone, which a line of sight stops at
(`SightCells.Ground.stone`, read from `PathGrid.isTerrainBlocked`). The gate is not stone: it is an object standing in
the doorway (`data/props/gate.duke`, a Box 30 × 4, no Body), so a hero at the threshold sees through it into the court.

Asked:

- An object template may say `BlocksSight = Yes` (default No). While such a thing stands, the sight cells its
  footprint covers are stone to every looker's line of sight — the cells themselves seen, what is behind them not —
  exactly as the map's stone is. The same rule for every player, so lock-step holds; nothing about pathing changes.
- When it goes (destroyed, or swapped for another template — the gate opening swaps it for `OpenGate`, which will not
  say it), the cells are open again on the next look.
- Generic on purpose: a door, a curtain, a thick hedge can say it later.

The game then adds `BlocksSight = Yes` to `Gate` and nothing else.

Done when: a hero at the shut gate's threshold has the court's cells not in sight (its creatures not drawn); the gate
opened, the next look shows them; a test in core pins a `BlocksSight` box hiding a cell behind it and unhiding it when
removed. Announce the version and the key's name to the game.

**E17, part 2 (2026-10-08).** Part 1 landed (d5db75c8) in core's `SightCells`, but the dungeon does not keep sight
cells: its fog is client3d's `Discovery` (three states, line of sight from `grid.isTerrainBlocked` in `inSight`, ~line
466–513), set through `visuals.fog(new Fog(lineOfSight, …))`. Asked: `Discovery`'s line of sight stops at the cells a
standing `BlocksSight` thing covers, as it stops at stone — the cells seen, what is behind them not, and whoever stands
there not drawn — read from the snapshot each frame, so an opened gate uncovers the court on the next frame. The game
already says `BlocksSight = Yes` on its Gate (now a `Prop` block, since `Object`'s `RtsTemplate` has no such component —
say if `RtsTemplate` should have one too).

## E18 — Stream the ground per map (2026-10-08, small)

`Visuals.streamGround(cells)` (E10) is one switch for the whole client: set once, every map the run lays streams its
ground. The dungeon wants its painted open world streamed (E16's chunks) while its descent floors — small, kit-drawn —
are built whole as today. Today the game can only set it when the map named at launch is painted, so a descent started
from the menu in the same launch streams too.

Asked: the switch can be given per map — e.g. `Visuals.streamGround(cells, Predicate<map>)`, or a map/look component
saying it streams, or streaming on by default for painted maps only — whichever fits; the client asks it each time a
map is laid. Done when a launch can lay a painted map streamed and then a kit floor built whole, with a test.
## E19 — A game's rows on the pause menu, and a word before the client stops (2026-10-08, small)

The game now saves (`docs/plan/2026-10-08-saving.md`): an autosave as each floor is entered, and "Davom ettirish" /
"Yuklash" on the way in, through `Shell.Question`. Two things it cannot do from its side:

1. **Saqlash from the game.** The pause menu is `DukeRtsApp.showPauseMenu` (~line 2412): its rows are fixed —
   Resume, Settings, Abandon the run. Asked: a game may add rows to it, e.g. `Shell.pauseRows(List<Shell.Option>)`
   (or `Duke3D.pauseRows`), each a label and a `Runnable` run on the client thread with the simulation paused; a row
   may open a `Shell.Question` (for "which slot") and come back to the pause menu. The game adds "Saqlash" → slot 1–3.
2. **Saving on quitting.** "Abandon the run" and Quit call jME's `stop()` and the game is told nothing. Asked: a hook
   the game sets (`Duke3D.onLeaving(Runnable)`), run once before the match is let go of — on Abandon, on Quit, and on
   the window closing — on the simulation thread or with it paused, so the game can write its save from a still world.

Generic on purpose: any game with saves wants both. Done when: a game's row shows on the pause menu and runs; the
hook runs once on each way out; a client3d test pins both. Tell the game the names they ended up as.

## E20 — A game adds its own right-click actions beside the engine's (2026-10-08, small)

The game now has people who talk (`docs/plan/2026-10-08-dialogue-and-quests.md`): a right-click on a person sends the
hero over to talk to them, as the game's order `Talk`. The right-click word is `DukeGame.contextOrder(ContextOrder)`,
which holds ONE rule, and `BagScreen.show` (rpg-client-3d) sets it to its own: a thing lying on the floor is
`PickUp`, the keep's gate is `ToTheGate`. To add `Talk` the game sets the rule again after `BagScreen.show` and
repeats the bag's two cases inside it (`Bag.show` in the game) — a copy that silently goes stale the day the bag's
rule changes, and a second game with the bag would have to copy it too.

Asked: a game may add its own context actions without replacing anyone else's — e.g. `DukeGame.contextOrder` keeps a
list and asks each rule in the order added until one names a word (or `contextOrder(rule)` plus an
`addContextOrder(rule)` that goes before or after the ones there); `BagScreen.show` adds its rule rather than setting
it. The word still goes out as `GameOrder(player, word, selection, place, target, 0)` and its cursor is still the
`Cursor` of that name. The same for the ground rule (`groundOrder`) if it is a single slot too.

Done when: the bag's pickup/gate rule and a game's own rule (a word for some other target) both answer on one
match, in a test; the game then deletes its copy of the bag's two cases from `Bag.show`.

## E21 — The move order's mark lies on the slope it is on (2026-10-08, small)

The owner, playtesting: the walk order's mark looks the same on any ground, and should tilt with the surface. Today
`Chevrons.place` (client3d) lifts each arrowhead to `floorAt` under it but keeps it level, turned only about the
vertical: on a hillside (E16's painted relief especially) the heads stick out of the slope on one side and sink into it
on the other. Asked: each arrowhead (and the mark as a whole) is turned to lie in the ground's plane under it — the
normal from `floorAt` sampled a little either side, or the relief's own normal where the client has it (E16's smooth
normals) — still turned about that normal as it spins inward; level ground draws exactly as today. The attack ring
(`AttackFlash`) and anything else drawn flat on the ground at a point (selection rings, the aura marks under a
creature, if the client draws them) the same way, if they share the placement.

Done when: on a slope of 30° the heads lie on it (a test reads their rotation against the normal), level ground
unchanged.

## E22 — The story half lifted into `rpg` (2026-10-08, big — E8's way, game first then lifted)

The game built the campaign's foundations in its own code, as E8 asks ("build in the game first, lift later"), and they
are now played and tested (master afe2ed9, 1212 tests). Each has its spec in this repo:

| What | Spec | Game code |
|---|---|---|
| Dialogue: a box with a portrait, lines in sequence, pause alone / show in a party | `docs/plan/2026-10-08-dialogue-and-quests.md` | `story/` (Conversation, Line, DialogueScreen) |
| People: neutral creatures who talk, wander within a radius, face the talker; the "!" over a giver | same | `story/` (Npc, people placement in maps) |
| Quests: MAIN/SIDE, steps REACH / KILL / COLLECT / TALK / GIVE, OnDone (conversation, reward, next quest), the log saved | same | `story/` (Quest, QuestLog), run/SaveGame |
| Zones: named rectangles/circles on a map | same | `map/StaticMap` zones |
| Triggers and cutscenes: WHEN (zone entered, death, quest/step done, conversation, timer, flag, map start) → DO (lines, quests, spawn, walk, open gate, items, flags, next map, cutscene); letterbox + camera flight + lines | `docs/plan/2026-10-08-story-scripting.md` | `story/` (Trigger, Script, Cutscene) |
| Gold and a merchant: a purse per hero, drops by level, a trade window, buy/sell as orders | `docs/plan/2026-10-08-people-and-trade.md` | `story/` (Purse, Merchant, TradeScreen) |
| The quest tracker: a list of missions, MAIN ◆ / SIDE ◇, J folds it | — | `run/Tracker`, `MissionScreen` |

Asked, in steps like E8's, each with an old→new map in the CHANGELOG:
1. **core.map:** a zone type a map can list (a named rectangle or circle in cells) — a marker interface or annotation the
   editor (duke-plugin) can recognise by type rather than by shape.
2. **rpg:** conversations, people, quests and their log, triggers and their script, the purse and trade — the
   mechanisms, generalised away from the dungeon's settings (the words stay the game's data), deterministic and
   saveable (each with a state record a game writes into its save).
3. **rpg-client-3d:** the dialogue box, the cutscene's letterbox and camera flight, the trade window and the tracker,
   with the game's words handed in as `BagScreen.Words` is.
4. Mark, for an editor, which record component names a zone, a person, a quest, a conversation (`@Link`-like), so a
   quest step's target can be checked by kind.

The game then deletes its copies, as it did for E8 step 3. Done when duke-generals builds without any of it and the
game's 1212 tests pass on the lifted classes.

## E23 — A long list is read in the time its length takes (2026-10-08, small)

The open world (`maps/world`, 512 cells a side) was first written with a piece of scenery a line: some 30,000 lines in
one `Scenery = [ … ]`. Reading it took 50 seconds, where the rest of the file — 1.5 MB of cells, relief and paint in
about 1,500 long lines — takes 0.16. The cause is `DukeText.Reader.items` (core): every line appended to an open list
is followed by `closingBracket(text, line)`, which scans the whole list read so far from its first character, so a list
of n lines costs n² of its length. The game packs its scenery a line a row of cells (`;` between pieces) and reads the
world in 0.65 s, so nothing is blocked — but the same scan is still paid by every long list, a 768-cell world's paint
and relief already cost 1.9 s, and a hand-made map with a thing a line will meet it again.

Asked: the scan for `]` resumes where the last line's stopped (carrying whether it was inside a quote), so a list is
read in time linear in its length; the same for any other place a value is read on over lines. Nothing in the format
changes.

Done when: a list of 30,000 one-line items reads in well under a second, in a core test; every data test unchanged.

## E24 — Haze chosen per map (2026-10-08, small)

E18 let the game stream the ground per map. The open world also wants the engine's haze (distance fog) and the
world picture E10 offers, but they are one global setting on `Visuals`: turned on, they would fade the small descent
floors too. Asked: the same shape as E18 — e.g. `Visuals.haze(look, Predicate<Object> forMap)` (and the world picture
likewise), asked of each map's record as it is laid; the old one-argument form unchanged. Done when a launch can lay
the hazy open world and then an unhazed floor, with a test.

## E25 — Worn slots and a backpack (2026-10-09, medium)

The owner, 2026-10-09: today a hero's bag is six slots and everything in it acts on him (Dota's hero inventory). An RPG
needs to carry things that do nothing until put on. Decided with him:

- **Worn slots, by kind** — Qurol (weapon), Zirh (armour), Dubulg'a (helmet), Etik (boots), Tumor (amulet), Uzuk (ring)
  — the slot list and its words the game's data. Only what is worn acts on the hero (its figures, extras, auras the
  item gives). An item says which slot it goes in (`Slot = …` on the item's record); one that names none cannot be worn.
- **A backpack** of a size the game gives (10): carried, acting on nothing. Keys, quest items and things to sell live
  here; what a use-item does (a potion, the key given to the gate) is used from the backpack.
- **Moves:** a backpack item clicked → worn in its slot, the worn one there swapped back to the backpack; a worn item
  clicked → to the backpack (refused with a word if it is full). A thing picked up is worn when its slot is empty and
  nothing else, otherwise goes to the backpack; a full backpack refuses with a word, as the full bag does today. Drop
  from either. Every move is an order through the simulation (party in step, as the bag's orders are now).
- **rpg-client-3d's `BagScreen`:** the worn slots laid out by kind and the backpack's grid beside them, the item card
  saying "Kiyiladi: Uzuk" / what it gives; the game's words handed in as `BagScreen.Words`.
- **State for saving:** what is worn and the backpack, in order, readable and restorable through public API (the game
  saves it, as it saves the bag today).
- A game that gives no slot list (generals, or the old way) keeps today's single list where everything acts.

The game then: slot words and each item's `Slot` in data, the merchant buying from and selling to the backpack, saving
both, its loot drops unchanged. Done when the rpg tests pin each move and the old single-list mode, and the game's suite
passes with its items given slots.
