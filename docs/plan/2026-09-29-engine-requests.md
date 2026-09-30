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

---

**Order the game can use them in:** E1 and E2 at once (small). E3 before the descent grows past ~100×76. E4
and E5 unlock biomes on screen (the game's biome generator is written and tested headless meanwhile). E6 last.
Please tell the duke-dungeon session as each one lands, with the API it ended up as and the engine version it is
in — this repo is the game's, so the engine side is best not written into it.
