# Biomes on larger floors, with rock for walls

Four things asked for together: the dungeon's walls should be natural rock rather than masonry slabs; what blocks
should match what is drawn (two trees on neighbouring cells stop a hero even where their models leave a gap);
one floor should hold several biomes, as a Minecraft world does, rather than wearing one theme; and the endless
descent should be much larger. The first is data and models. The other three lean on the engine, which draws
every piece of a floor with one kit and one Geometry per piece — what it is asked for is in
`2026-09-29-engine-requests.md` (E1–E6); this is the game's side.

## Stage 1 — Rock, not masonry (now)

- **The dungeon theme becomes a "thing" kit.** KayKit Forest's cliff rocks (`Rock_1` layered cliffs and spires,
  `Rock_2` clusters; CC0, same pack as the trees) stand in the rock the way the forest's trees do:
  `WallFillsRock`, clumped, spread and varied, on a lid laid at ground level (`WallHeight = 0`). One rock per
  tone until E2 lets a tone list several.
- **Slabs only where the ground is raised.** The masonry wall becomes the theme's `RockFace`, which the client
  draws only where rock stands above the foot of its face: the retaining wall of higher ground, and nowhere else.
- **The forest's edge reads as closed where it is.** Until E6, a rock cell blocks all of itself, and what looks
  walkable is the open ground under the canopies between trunks — which no clump setting closes. With E2 the
  tree clumps take bushes among the trees, so the undergrowth fills that ground on screen.
- `DungeonTilesTest` measures the new pieces as it measures every kit piece; CREDITS gains the rocks.

## Stage 2 — Blocking that matches the drawing (after E5, E6)

Boundary rocks and trees stop being kit clumps and become scenery the generator places itself, each with a
footprint (E5), on a navigation grid finer than the drawn tiles (E6): the gap you see is the gap you can walk,
and a boss that does not fit goes round. `CorridorWidth` and the sliver rule are restated in world units then.

## Stage 3 — Biomes (generator now, drawn when E4 and E5 land)

- **A biome is a theme placed per region** rather than per depth: the existing `Theme` block (terrain + kit +
  look). New ones beside Forest and Dungeon (the caves), each a file and a folder of models: a pine wood, an autumn
  wood (the forest atlas repainted), dead land and a mine. No halls of worked stone: their edges would be masonry,
  and the owner's rule is that a boundary is rock or wood, never slabs.
- **Climate picks the biome.** Two value-noise fields per floor — how wild (worked stone ↔ wilderness) and how
  alive (barren ↔ lush) — about forty cells to a feature, from their own seed stream. Each biome file states the
  point it sits at (`Climate = [wild, alive]`) and a cell takes the nearest; depth moves the whole floor toward
  worked and barren, so the woods thin out on the way down. A chamber takes the biome at its middle, whole;
  tunnels and rock take their own cell's; a majority pass removes one-cell specks.
- **The ground follows the biome.** Each chamber is cut with its own biome's `Terrain` (ragged, islands), a
  tunnel with the mean of its two ends', and the relief is blended across regions; the slope clamp stays global,
  so no border becomes a cliff.
- **So do the things in it.** Each biome names its props (templates, as `PropsPerRoom` draws today) and its
  scenery — grass, bushes, flowers, ore, banners — with a density; the generator places both, deterministically.
- **Drawing.** `GeneratedDungeon` gains the biome of every cell; the floor's map record names a look per cell (E4)
  and lists its scenery (E5). Until E4 exists a floor can only wear one look, so biome floors stay off unless
  `generation.duke` lists `Biomes` — leaving it out is today's descent.
- Monsters stay the same everywhere for now; the stages keep their single theme.

## Stage 4 — Larger floors

`generation.duke` goes from 50×36 to 96×72 now — inside the 100×76 the engine already carries (`deep.map`) — with
the room counts, spacing and attempts scaled to fill it, and to 128×96 once E3 lands. `DungeonGeneratorTest`
keeps holding `MinRooms` to its word at the new size.

## Not changed

- Lock-step: everything the simulation reads is still a function of the seed.
- A look still changes nothing the simulation reads; a biome's terrain does, as a theme's does today.
- The stages, their files, and `first.map`'s hand-sculpted relief.

## Build order

1. Stage 1: rocks in the dungeon theme, tests and credits (plan: `2026-09-29-rock-walls-and-larger-floors.md`);
   the forest's undergrowth and more rocks per tone when E2 lands.
2. Stage 4, first half: the descent at 96×72.
3. Stage 3's generator: climate, the biome grid, per-region terrain, per-biome props and scenery lists, the new
   biome files and models — tested headless, behind `Biomes`.
4. Biomes on screen as E4 and E5 land; 128×96 after E3; Stage 2 after E6.
