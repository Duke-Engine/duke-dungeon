# Biome generator — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One floor of the descent mixes several biomes — each region cut to its own biome's terrain — decided by
two climate fields, headless and tested, behind a `Biomes` list in `generation.duke`.

**Architecture:** A biome is a `Theme` with a `Climate` point. `BiomeMap` (gen) draws two value-noise fields
(wild, alive) from a stream of its own, gives every cell the nearest biome, gives every chamber's footprint the
biome of its middle, and smooths the rest. `Cave` and `Relief` take their numbers per chamber, per tunnel and per
cell instead of per floor; given one terrain they build the same arrays and draw exactly today's floors.

**Tech Stack:** Java 25, JUnit 5, `.duke` data read by the engine's record reader (a two-field record is written
`[a, b]`).

**Spec:** `docs/plan/2026-09-29-world-generation.md`, Stage 3.

## Global Constraints

- No `Biomes` in the file → every floor, relief and checksum exactly as today (the old tests are the proof).
- Lock-step: all of it a pure function of seed, depth and data; the climate's own stream moves no other draw.
- The floor still wears one look until engine E4 exists: the start chamber's biome, with a tone drawn as `Themes`
  draws one. `GeneratedDungeon` carries the whole map for when it does.
- Nothing here needs the engine; commits only on the owner's word.

## Tasks

1. **Data.** `Theme.Climate(int wild, int alive)` and `Theme.climate` (null: not a biome); `ProceduralMap.biomes`
   (`@Link(Theme.class)`), `biomeSize` (cells to a climate feature, default 40), `climatePerDepth` (a `Climate`,
   default `[0, 0]`); `DungeonSettings.biomes()` → `content.Biomes(all, size, perDepth)`; refused at load: a listed
   biome with no `Climate`, a size under 8, a climate point outside 0–100. *Tests:* `BiomeSettingsTest`.
2. **`gen/BiomeMap`.** Fields at `size` and `size/2`, stretched to 0–100 over the floor, shifted by depth; nearest
   point wins, ties to list order; footprints whole; three majority passes over the rest. *Tests:* `BiomeMapTest`
   — same seed same map; every chamber one biome; several biomes on most floors; no region under a dozen cells;
   deeper floors move toward the biomes the shift points at.
3. **Terrain by region.** `Cave.carve` takes a terrain per chamber and a raggedness per cell (tunnels: the mean
   of their two ends; loops: the mean of the chambers); `Relief.of` takes a rise per corner, a level per chamber,
   and the floor's hill size and slope (the smallest slope any biome allows). The one-terrain overloads build
   those arrays. *Tests:* the old gen tests unchanged; `BiomeTerrainTest` — chambers of a ragged biome come out
   more ragged than a close one's on the same floors.
4. **Wiring.** `DungeonGenerator` draws the map when `Biomes` is listed and hands it on; `GeneratedDungeon.biomes`
   (a second constructor keeps the old calls); `DungeonRun` dresses the floor in its start chamber's biome;
   `MapPicture` tints ground by biome. *Tests:* the generator's guarantees (reachability, room counts, no slits)
   rerun on a biome file; the look agrees with chamber 0.
5. **Biomes to choose from.** New themes from the owner's CC0 KayKit packs — a pine wood, an autumn wood (the
   forest atlas's foliage repainted gold, orange and red), dead land, a mine — each a file, a folder of models and
   a CREDITS row; no halls of worked stone, whose edges would be the masonry the owner ruled out. A thing kit's
   wall may stand narrower than its cell (pines three to a cell) but never wider. `Biomes` written into
   `generation.duke` commented out until E4. *Tests:* `DungeonTilesTest` covers every theme already.
6. **Look at it.** Pictures of seeds' biome maps, tuned until regions read as places. The weather is ranked
   rather than stretched (summed noise piles up mid-range, which starved the corner biomes), and the six points
   spread so the first floor is about a sixth each — forest 21%, autumn 18%, caves 17%, dead land 16%, pine 15%,
   mine 10% — and the fourth is mostly caves, dead land and mine. README section.
