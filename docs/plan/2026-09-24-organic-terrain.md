# Organic floors on smooth ground

The descent used to draw rectangles joined by L-shaped corridors, stood on storeys with a flight of stairs at
every doorway, and laid a few gentle hills (at most 15 steps) over the lot. The engine already walks and draws a
full relief — a height at every cell corner, a cell a cliff only from 16 steps across it — so the storeys were
the one part of the floor the engine no longer needed. This replaces them, and the rectangles with them.

## What a floor is now

- **Chambers, not rooms.** Each is grown inside the footprint a room used to be placed in (same draw, same
  spacing rules): an ellipse, a few lobes, then the edges roughened. The footprint stays the `Room` record, so
  stage files, `StageCheck` and every "is it in the boss room" question keep working. Its middle cell is always
  floor: the hero starts there, the boss waits there.
- **Tunnels, not corridors.** The same minimum spanning tree joins them (links stay a tree), but each tunnel is a
  midpoint-displaced line stamped with a `CorridorWidth` brush, with pockets along it. A few extra tunnels make
  loops, only between chambers at the same distance from the entrance (±1) and never into the boss's, so the
  boss is as many chambers deep as before.
- **Always walkable and wide enough.** Tunnels and chamber cores are protected; roughening never touches them.
  Afterwards every floor cell not inside a `max(2, CorridorWidth)` square of floor goes (no slivers a big body
  wedges in), and anything the entrance cannot reach goes. Connected by construction, not by luck.
- **Islands.** A pillar in a cavern, a grove in a glade: small blocks of rock inside a chamber, only where two
  cells of floor ring them, so they never cut anything off. None in the boss's arena.
- **Smooth ground instead of storeys.** Two octaves of value noise (`Rise`, `HillSize`), each chamber levelled
  toward the height at its middle (`Level`), blurred, then held to `Slope` steps across a cell by a Lipschitz
  clamp — so no cell anywhere is a cliff. Every floor cell is storey 0; there are no stairs.

## Who decides the shape: the theme

A forest and a cellar should not be the same place in different clothes, so each `Theme` gains a `Terrain`
block: `Ragged`, `Winding`, `Loops`, `Islands`, `Rise`, `HillSize`, `Slope`, `Level`. A theme without one gets
the defaults, as does a floor with no theme. The theme of a depth is already a pure function of the depth, so a
seed still names one floor, and a stage is generated with the terrain its depth wears.

What stays true: the **look** — tones, models, fog, re-skinned creatures — changes nothing the simulation reads.
`DungeonThemeTest` now holds that claim precisely (two themes that differ only in look play one run) and adds
the new one (a theme's terrain shapes its floors).

Map size, room counts and sizes, `CorridorWidth` and spacing stay in `ProceduralMap.Layout`; the storey and hill
fields (`MaxStorey`, `StoreyChangePercent`, `StairLength`, `EntranceStorey`, `BossStorey`, `Hills`,
`HillSize`) go.

## Not changed

- The engine. Nothing here needs it.
- The stage format. New maps write every floor cell as `0` and carry a `Relief`; old maps with storeys still load.
- `first.map`: its relief was sculpted by hand and is kept. `deep.map` was untouched generator output and is
  drawn again (`writeExampleMaps --args=deep`).

## Build order

1. `Theme.Terrain` + validation; `Themes.terrainAt(depth)`; drop the storey/hill settings.
2. `Cave` (chambers, tunnels, roughening, slivers, reachability, islands) and `Relief` in `gen/`; the generator
   wired to them; `Storeys`, `Hills`, `Corridor` deleted.
3. Monsters, guards and props placed on the carved floor rather than inside rectangles.
4. Tests: the generator's properties at the new shape, a relief test, the theme test's two promises.
5. `MapPicture` shades floor by its height; `deep` redrawn; README.
