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
| E26 | single-hero selection: the hero bar's portrait follows the hero the status names, not the pick; a named "no drag box" switch; (later) a live portrait a game's painter may hang in its own frame | landed, 3f606061 (on e26-single-hero); the game draws the pick's live portrait |
| E25 | a hero's worn slots by kind and a backpack that gives nothing | landed, 831641bd (on e25-worn-slots); the game wears six slots and carries ten |
| E27 | the rpg module's blows name their dealer; a passive that drinks its bearer's kills (`SOUL_DRINK`); a worn item lends its skills | landed, f369b940 (in 0da97768); the medallion lends the engine's SOUL_DRINK, and an arrow's kill pays its archer |
| E28 | a passive that cannot be killed, only beaten (`UNYIELDING`); and `GrowableBody` honouring the damage scale | landed, 0da97768; the cursed hero is beaten by UNYIELDING, and the stand-in is gone |
| E29 | the pause menu in the game's words, with a Quit to the main menu and a Quit to the desktop; the shell's rows laid at a side | landed, cf0062a2; the game says every word in Uzbek (data/world/menu.duke) and lays its menus at the left |
| E30 | the backdrop behind the client's own stone menus, not only behind a game-drawn front end; the questions' "Back" said by the game; the game's own front rows | landed, 0aeeec88; the scene runs behind the menu, Orqaga is said, and the front rows stand on the menu itself |
| E31 | four new skill shapes for the knight: a piercing wave, a shield charge that drags and stuns, armour and regeneration as a passive, a pull to one point that stuns | landed, dba15f52 + 1d0bcfd3 (the charge frame by frame); the knight is Qilich to'lqini, Qalqon bilan surish, Ajdar qoni and Teskari qutb |
| E32 | the knight's skills as played: a charge that carries two, their looks drawn, an unlearnt passive that is off | landed, 24965199; the charge carries three skeletons the whole way, the wave, charge and pull are drawn, and an unlearnt Ajdar qoni does nothing |
| E33 | the front menu hides itself the frame the backdrop arrives behind it (forgetTheWorld hides the menu, nothing shows it again) | landed, bf191479 (on e31-knight-skills) |
| E34 | a scenery piece drawn with another picture (the queen's dark castle) | landed, 5971fa0b (on e34-36-castle-scenery); the queen's castle is the fort pieces @ their dark pictures |
| E35 | a map's scenery line carries its tint | landed, 5971fa0b; the game reads and writes it (`GeneratedDungeon.Piece` over `SceneryLine`) |
| E36 | scenery that blocks along its length, turned with its facing (castle walls) | landed, 5971fa0b; every castle wall is a `WxD` box, the qasr courtyard is entered only by its gate |
| E37 | placing one piece on the grid in the editor — the plugin's, done in duke-plugin | plugin |
| E38 | a map that starts explored: its ground seen from the first frame, its creatures only in sight | landed, 7f17f626 (with E34–E36); the open world begins seen, the descent black |
| E39 | particles that bounce, fly home and turn over (the kill's coins) | landed, 946409ed |
| E40 | a creature moved by the story over frames (the landing, the knock-back) | landed, fa3e72a0 |
| E41 | a cutscene camera that turns, tilts and zooms, eased | landed, fa3e72a0 |
| E42 | a title card | landed, fa3e72a0 |
| E43 | a `Loot` carries affixes: several (figure, value) pairs that `LootBag.total` and `extra` add beside its own | landed, fc24cecd; the game rolls every affix as the engine's Affix: magic 1–2, rare 3–4, epic 4–6, any affix on any slot it names |
| E44 | crit chance and move speed as figures the hero reads; a worn thing that raises a skill's rank | landed, fc24cecd; the game rolls critical chance (with how much harder), critical damage, move speed and +1 to a Q/W/E skill, and the sheet says a critical blow |
| E45 | the bag's card in the game's colours: a colour for each line, the name's by rarity | landed, fc24cecd; the bag's card is coloured through `BagScreen.colouredBy`, the game's overlay gone; digits bind, so keys 1–4 are `Hotkeys.on` |
| E46 | the game's keys in Settings | landed, 9ce15e4e |
| E47 | a hero's whole figures in one place | landed, 9ce15e4e |
| E48 | a key's code and name for a game's canvas | landed, 9ce15e4e |
| E52 | a route that prefers the roads: a cost per cell a walker's path weighs (travellers keep to the roads) | landed, eedd090b on the engine's branch `e52-road-costs`: `PathGrid.setStepCosts`; the game lays its painted roads (`Roads`) |
| E55 | music the game chooses (`Duke3D.musicByGame`), and a weapon that keeps its name through `HeroBuild` | landed, 7b7f76c1 on the engine's branch `e55-57-sounds`; the game sets `musicByGame(true)` |
| E56 | a flat faded loop the game starts and stops (`Duke3D.loop` / `stopLoop`), and a held sound that fades | landed, 7b7f76c1 on the engine's branch `e55-57-sounds`; place ambience is looped by `Soundscape`, the held trick gone |
| E57 | the bag's sounds (`bag_open`, `bag_close`, `item_lift`, `item_drop`), and `walking.<Template>.<surface>` | landed, 7b7f76c1 on the engine's branch `e55-57-sounds`; the cues are in `ui.duke`, creatures' footsteps by ground in `sfx.duke`, the hero-only step code gone |
| E58 | feel: a number that is bigger and another colour for a heavy or critical blow, a short hit-stop, and a camera knock from an effect list that obeys `ShakeScale` | landed, 62e569ef on the engine's branch `e58-feel`; the numbers, `HitStop` and the effect-list shakes are in `hud.duke` and `feel.duke` |
| E59 | settings: an Ambience channel with its own volume, a Camera shake row (`ShakeScale` for the player), a Language row if there is more than one | landed (engine 01eb7029; game 2026-10-15) |
| E60 | closest-thing questions with no candidate list or sort; a weapon that does not copy its owner's module list every frame | landed (engine 01eb7029; game 2026-10-15) |
| E61 | where the hero is wanted: a `Mission` that drops its key with a few still standing, and the game's own marks on the minimap | landed, 6e789f32 on the engine's branch `e61-key-minimap`; the game passes `Run.keyLeeway` to `Mission.begin` (`run/EarlyKey` gone) and marks the key (gold, blinking) and each quest's target (QuestColour) on the minimap |
| E62 | settings kept as the player leaves them (Esc, closing the window), not only on the Save button | landed, 53923bd4 on the engine's branch `e62-65-settings-menu-looks`; Esc and closing the window keep the applied settings (`GameSettings.keep()`), Cancel is the one way back; the hint says `ESC chiqish` |
| E63 | no bars, medallions or names over the front menu's backdrop (as on `Screen.FRONT`) | landed, 53923bd4 on `e62-65-settings-menu-looks`; `DukeRtsApp.barsHidden` clears bars and marks over the menu backdrop |
| E64 | `dressModel` takes a glTF piece's base colour by name, not the first texture jME lists (a normal map) | landed, 53923bd4 on `e62-65-settings-menu-looks`; `dressModel` takes `BaseColorMap` first; the game keeps its stripped GLBs (smaller; `ModelPicturesTest` is now a size rule) |
| E65 | a passive skill drawn as a passive on the hero's bar: framed, no key letter, no press or sweep, takes no click | landed, 53923bd4 on `e62-65-settings-menu-looks`; `HeroCard.skills` writes `passive`, drawn framed, letterless, unclickable; the game keeps a passive's key bound to a no-op and the `Passiv` card line |
| E66 | scenery a hero can work: a scenery piece the game can point at, take away (a tree felled) and lay again (grown back) | landed, 7b8e8069 on the engine's branch `e66-68-food-fire`; the world's own trees are felled (`GameLogic.removeScenery`, the Food block's scenery Gathers and `SceneryMaps`), the save carries them (`Felled`), and the game's `ChoppableTree` is gone |
| E67 | a busy state with its own clip: a creature plays a named clip on a loop for N frames, cut short by an order or a blow | landed, 7b8e8069 on `e66-68-food-fire`; work loops `GameObject.busy` with the Food block's `Clips` (the heroes' `Chop`, `Gather`, `Cook`) |
| E68 | health over time as a buff the skill book reads (as `MANA_AURA` is for mana), with its picture on the HUD | landed, 7b8e8069 on `e66-68-food-fire`; `Belt.digest` is gone, the buff row shows a meal by the Food block's `Taom` skill and `Jon/s`; fires crackle by the placed loop `Duke3D.loopAt` |
| E69 | a melee blow struck at its swing's impact: the attack clip started a lead before the blow lands (an inline `PreAttackFrames`, the clip played from the wind-up) | landed, 7fda6418 on the engine's branch `e69-70-attack-clips`; the knight's whole slash is back, `PreAttackFrames = 9` (its blade's fastest frame, 0.3s) with `ReloadFrames` 34 -> 25 so blows stay 34 frames apart (`HeroClipFitTest`) |
| E70 | a blow struck on the move drawn on the upper body only, the legs keeping the paced walk | landed, 7fda6418 on `e69-70-attack-clips`; `UpperBody` on the three heroes (`spine`, `QuickRigCharacter_Spine_012`, `spine`); monsters not yet |
| E71 | a flinch that never cuts short a blow or a cast: the hurt clip played only when the unit is not mid-attack, mid-gesture or moving, at most once per a look-set interval, optionally only on a major/critical blow | landed, 8c9b234d on `e71-flinches`; the heroes name `Hurt` again, `Flinch` in world/feel.duke (1.0s, `HeavyOnly`), `DungeonMonsterArtTest.aHeroFlinchesOnlyAtAHeavyBlowAndNotEveryBlow`; monsters still name none |

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

## E26 — Single-hero selection: the portrait stays the hero's, and no drag box (2026-10-09, small)

The owner, 2026-10-09: in the game only the hero is commanded; picking anything else never takes him away, and what
was picked is shown in a smaller window of its own. The game now writes the hero's card whatever is picked, draws a
target frame of its own on the canvas for the pick (`TargetFrame`, fed by `DungeonRun.getTarget` and the engine's
`BuffScreen`), and E8 step 4 already sends every order to the hero the status line names. Two things it cannot do:

1. **The hero bar's live portrait follows the pick, not the card.** `DukeRtsApp.portraitSubject()` hands
   `RpgHud.show` the single selected unit, so a picked skeleton (or a party fellow) stands in the hero's own frame —
   beside the hero's name, health and skills — and with nothing picked the frame is empty. Asked: when the status
   line names a hero (`hero=`, as `RpgHud.ordered` already reads it), the portrait shows that unit whatever is
   selected; with no hero named, the pick, as now (generals, other games). Done when an rpg-client-3d test pins: a
   monster picked, the portrait's subject is the named hero; nothing picked, still him; no `hero=`, the pick.
2. **A named switch for no drag box.** A single-hero game has no group to gather. The game sets
   `Visuals.dragDistance(Float.MAX_VALUE)` meanwhile, so every press is a click where the mouse lets go — it works,
   but says "far" where it means "never". Asked: `Visuals.dragSelects(false)` (default true): no rectangle drawn and
   a release is a click. Done when a client test pins it; the game swaps its knob for it.
3. **(Later, optional)** a live portrait a game's painter may hang: a second `HeroPortrait` for a unit the game names,
   its texture drawable on the `Canvas` (e.g. `drawPortrait(unitId, box)`), so the target frame shows the creature
   breathing rather than the first letter of its name. Not needed for the rule above.

## E27 — The medallion's passive in the skill system, and blows that name their dealer (2026-10-09, medium)

The campaign's medallion (`docs/plan/2026-10-09-medallion.md`): worn in the Tumor slot, it drinks the soul of every
enemy its wearer kills and every so many souls make his blows harder. The owner's rule is that whatever a skill or a
passive does lives in the skill system, never in a module of a game's own. Today the game counts the souls itself
(`DungeonRun.drunk`) and lends the bonus through the hero's `SkillBook` as a `DAMAGE_AURA` buff of his own, renewed
each frame — it works, but the strongest damage aura counts, never the sum, and the buff row shows it as some
monster's aura. Three things are asked:

1. **rpg's blows name their dealer.** `ArrowUpdate.strike` and its splash, and `SkillBook`'s damage (strike, area,
   meteor, dash, burst) call `getBody().damage(amount[, type])`, so the `ObjectDied` of such a kill names no killer
   and no side (`killerPlayerIndex = -1`): only `WeaponUpdate`'s blows (the Knight's swing) say whose they were. The
   game's gold for a kill (`DungeonRun.pay`) is therefore never paid for the Rogue's arrows or any skill's kill, and
   the medallion has to guess the killer from whose experience rose. Asked: each of those passes a `Death` with the
   shooter/caster and his side (as `WeaponUpdate.blow` does). Done when an rpg test pins an arrow's and a skill's
   kill naming the archer and his side.
2. **A passive `SOUL_DRINK`.** Never cast (`isPassive`), on a hero's skill allowed: its book counts every kill its
   bearer makes (`souls`, readable and settable for a save), and every `SoulsPerStep` (a new Skill field, or reuse
   `TickFrames`) of them add `BoostPercent` to `damageMultiplier`, up to a cap (`MaxPercent`, or `Damage` as the cap).
   Summed with an aura, not maxed. Shown in `BuffScreen` with its own words (`+3% Zarba`, `Jonlar: 37`).
3. **A worn item lends its skills.** A `Loot`'s optional `Skills` (skill blocks headed by the item's id): while it is
   worn (E25), they are in its wearer's book; taken off, gone (the souls stay with the book's owner, not the item).
   A hero's file may name no passive, as today; an item worn by a hero may.

The game then: the medallion's `LootItem` carries a `Skill` block (`Effect = SOUL_DRINK`), its own soul counting and
buff go, and gold is paid for every kill. Done when the rpg tests pin each, and the game's `MedallionTest` and
`TradeTest` pass with the game's counting deleted.

## E28 — A passive that cannot be killed, only beaten (2026-10-09, small)

The campaign's final fight (`docs/plan/2026-10-09-final-fight.md`): the cursed hero cannot be killed, only beaten.
The owner's rule is that passives are skills, and `SkillEffect` is closed to a game, so the game carries a stand-in
(`run/Fighting`, a module heard through `DamageListener`) and asks for the passive:

1. **`SkillEffect.UNYIELDING(Aim.SELF, true)`** — a passive. Its bearer's body never falls below `HealBelowPercent`
   (the field a mending already reads as "below this share") of its most health: the blow that would take it lower
   leaves it there, inside `BodyModule.damage`, before any death is reaped — so no `ObjectDied`, no `onDie`, no kill
   credit. The bearer is then **beaten**: say so where a game can read it — `SkillBook.isBeaten()` (or a condition word
   the thing wears) — set on the frame it happened; what being beaten does (side, stun, story) stays the game's.
   Refused on a hero, as every passive is today.
2. **`GrowableBody` honours `BodyModule.setDamageScale`.** `ActiveBody.damage` takes `scaled(...)`; `GrowableBody`
   does not, while `worthOf` (what `ObjectHurt` and the listeners are told) does — so on a GrowableBody a scale of 0
   reports a blow worth nothing and still takes it whole. The game sets `GrowableBody.setDamageTaken` on monsters
   meanwhile (only a hero's armour uses it).

Done when an rpg test pins: a bearer of `HealBelowPercent = 25` struck for ten times its health stands at a quarter,
beaten, and no death is posted; a second blow takes nothing below it; and a GrowableBody scaled to 0 takes nothing.
The game then: the Boss block's `Beaten` becomes a Skill in the Monster's own block, and `Fighting` keeps only the
phase's skill keys.

## E29 — The pause and main menus in the game's words, and laid at a side (2026-10-09, small)

The owner, 2026-10-09: the menus say "Abandon the run" in English; he wants a Quit, and the menu's buttons at one side
of the screen rather than in the middle (as Generals, WC3 and most Steam games lay them), over a live scene.

Asked:
1. **Words.** Every fixed word of the pause menu (`DukeRtsApp.showPauseMenu`: "PAUSED", "Resume", "Settings", "Abandon
   the run", "ESC resume", the abandon question) and of the Settings screen comes from the game (a `Shell.Words` record
   or `MenuStyle` words, English by default), so the dungeon can say them in Uzbek.
2. **Two ways out on the pause menu:** "Quit to the main menu" (the match let go, onLeaving run, the front end shown —
   what Abandon does now, renamed by the game) and "Quit to the desktop" (onLeaving, then the app closes). Both asked to
   confirm, as Abandon is.
3. **Placement.** `StoneMenu` lays its rows at a side the game chooses — left (default for a game that asks), right or
   middle (today's, the default) — with a margin from the edge, the title above the rows on that side, so a live
   backdrop (`Duke3D.backdrop`) is seen across the rest of the screen. The pause menu and the front end both.

Done when: a game can set the words and the side; both quit rows work (onLeaving runs once); a client3d test pins the
words handed in and the side's layout; generals unchanged by default.

## E30 — The live scene behind the client's own menus (2026-10-09, small)

E29 lays the stone menus at the left and dims only their strip "so a live backdrop is seen across the rest" — but the
backdrop (`Duke3D.backdrop`) is only ever made and drawn on the game-drawn front end (`Screen.FRONT`, under
`Shell.drawnByTheGame()`), which shows none of the client's menus, pause menu, settings or HUD. A game that keeps the
client's stone menus (the dungeon: its whole HUD, its pause menu with Saqlash, its settings) therefore never sees its
backdrop: in `DukeRtsApp`, `keepTheBackdropGoing()` and `backdropShown()` answer only `screen == Screen.FRONT`, and
`showMainMenu()` for a stone shell sets `Screen.MENU` and returns from `update` before the world is drawn.

The game is ready: `MenuScene` (a real fight of its own soldiers and skeletons in a forest clearing, the fallen coming
back, the camera drifting; 0.2 ms a frame headless) is handed to `duke.backdrop(...)` and waits for this.

Asked:
1. **The backdrop behind the stone front menu.** With a backdrop set and a shell that is not drawn by the game, the
   front menu, the questions it opens (`Shell.Question`) and the Settings screen opened from it run and draw the backdrop
   behind them — made when the front menu is first shown, kept while the player walks its questions, torn down when a
   match starts (`startGame`/`startMatch`) and made fresh when the front menu comes back (Quit to the main menu). The
   menu draws dimmed over it as E29 already does when `backdropShown()`.
2. **"Back".** The questions' and the skirmish menu's `Back` row (`DukeRtsApp.showQuestion`, `showSkirmishMenu`) said
   through `Shell.word` like the other fixed words.
3. *Optional, later:* top-level front rows of the game's own (`Shell.frontRows(List<Option>)`, like `pauseRows`), so
   Continue · New game · Load · Credits stand on the front menu itself rather than behind its Play row.

Done when: a stone-shell game with a backdrop shows it running behind its front menu and its questions; a played match
and Quit to the main menu bring a fresh one; generals unchanged; a client3d test pins that the backdrop is kept going on
`Screen.MENU`.

## E31 — The knight's new skills: wave, shield charge, dragon blood, pull (2026-10-09, medium)

The owner, 2026-10-09, reworking the Knight (data `units/knight.duke`; today Q Cleave = AREA_AT_SPOT, W Charge = DASH,
E Guard = GUARD, R …). Everything a skill does lives in the skill system (his rule). Four shapes the rpg module lacks:

1. **Q — Sword wave** (Warcraft/Dota "Shockwave"): aimed at GROUND, a wave leaves the caster along the line for
   `Distance`, `Width` wide, at `Speed`; it **passes through** and damages every enemy it crosses once (`Damage`,
   `DamagePerLevel`), stops at stone and at the keep's seal. Today's SKILLSHOT stops at the first thing hit — either a
   `Pierce = Yes` on SKILLSHOT or its own effect (WAVE); its look a moving effect along the line (data).
2. **W — Shield charge** (Magnataur's Skewer): aimed at OPEN_GROUND like DASH, the caster drives forward `Distance` at
   `Speed`; every enemy his front meets is **caught and carried** with him (held at his front, not walking, not
   attacking), damaged once (`Damage`), and when he stops they are dropped at his front and **stunned** for
   `StunFrames` (the existing stun). Stops at stone/seal as DASH does; the carried are never pushed into stone.
3. **E — Dragon blood** (Dragon Knight's passive): a passive, ranked like the others: `+Armour` and `+HP per second`
   per rank (`ArmourPerLevel`, `RegenPerLevel` or the usual fields), applied while he has it. Today GUARD is a timed
   cast; this is permanent.
4. **R — Pull** (Magnataur's Reverse Polarity): self-cast; every enemy within `Radius` is **pulled to one point** in
   front of the caster (moved, not walked; never into stone, spread so they don't overlap), damaged (`Damage`) and
   **stunned** for `StunFrames`. Through the seal never.

Deterministic as every skill (victims in id order, no clock), saved as the others, previewable in the editor by
their aims. Each with a test. The game then rewrites the knight's four Skill blocks in data (words, icons, numbers).

## E32 — The knight's skills as played: a charge that carries two, their looks drawn, an unlearnt passive off (2026-10-09, small)

Found taking E31 (1d0bcfd3) into the knight (`units/knight.duke`, `KnightTest`, `SkillLookTest`). Three gaps, each
pinned by the game's tests:

1. **A charge that has caught two ends itself on them.** `driveTheCharge` holds the caught at his front by
   `placeNear`, which spreads the second round the first — at 135° or 180° off his heading, i.e. beside or behind his
   shoulder — and the next step's `findBlocker(owner, next)` takes that carried body for one in his way, so the drive
   stops after 2–3 frames (two skeletons side by side at (100,146) and (100,154) before a knight at (80,150): he stops at
   89.6 of 125). One caught is carried the whole way. Asked: the caught pass his step (`findBlocker(owner, next,
   caught::contains)`), and the spread laid ahead of him — rings in front, never behind his front. A test: two and three
   caught are carried the whole `Distance`. The game then adds a second skeleton to `theChargeCarriesWhatItMeetsAndStunsIt`.
2. **WAVE, SKEWER and PULL draw no look.** `SKEWER` and `PULL` mark inside `apply`, and `cast` then calls
   `rememberTheCast`, which clears every mark and has no case for the three; `WAVE` is never marked at all. So the
   knight's `Look = CastFlash / Charge / Whirlwind` draw nothing. Asked: `rememberTheCast` keeps what `apply` marked (or
   marks the three itself): WAVE on him (0), SKEWER both ends as DASH, PULL on him at its Radius. The game un-disables
   `SkillLookTest.aChargeLeavesItsDustBehindAndBringsHimselfAlong`.
3. **An unlearnt passive is rank 1.** `SkillBook.rankOf` is `max(1, …)`, so `setRank('E', 0)` — a knight who has put
   no point in his blood — still gets rank 1 of `DRAGON_BLOOD` (8% and 3 a second). Asked: a rank the game has SAID is
   0 means not held (the scale let go, nothing mended); "the first where nothing says" stays for a book nobody ranks.

Done when: the three tests above, and the game's suite green with the second skeleton and the look test back.

## E33 — The front menu vanishes when the backdrop arrives (2026-10-10, small, a bug)

The owner played MSI 1.0.23 (engine 24965199): the battle runs behind the front menu as E30 meant, but the menu's
rows never show. He sits watching the simulation with nothing to click. Keys do nothing either, because the menu's
input is gated on `menu.isVisible()`.

**The cause, in `client3d/.../DukeRtsApp.java` at 24965199:**
1. `showMainMenu()` (about line 1960) shows the stone menu (`menu.show(...)` sets the root's cull hint to `Never`)
   on `Screen.MENU`.
2. On a later frame, the backdrop's match is ready. `frame()` → `keepTheBackdropGoing()` (line 1156) sees
   `behind != game`, parks the front match and calls **`forgetTheWorld()` (line 1165)**.
3. `forgetTheWorld()` (line 1239) ends with **`menu.hide()` (line 1260)**, which sets the cull hint to `Always`.
4. `keepTheBackdropGoing()` then calls `menu.liveBehind(true)` (line 1168). `StoneMenu.liveBehind` only calls
   `refresh()`, which redraws the rows but never touches the cull hint. So the menu stays hidden. Nothing shows it
   again until the screen changes (Settings and back, for instance).

The same thing happens each time a fresh backdrop is made: on quit to the menu, and when Settings is closed back
to the menu.

Asked:
- `keepTheBackdropGoing()` swaps the world without hiding what is on the screen. Either it keeps the menu visible
  across its own `forgetTheWorld()` (note `menu.isVisible()` before, show it again after), or `forgetTheWorld()`
  leaves the menu alone and its other callers (`startMatch`, `backToFrontEnd`, `takeTheFrontMatchBack`) hide or show
  the menu themselves, as each of them already does next.
- Done when a client3d test pins it: with a backdrop and a stone shell, the menu is visible after the frame in which
  the backdrop's match arrives (and after a quit to the menu, and after Settings → Back); and the menu takes keys.

The game cannot get round this. Its shell, front rows, `menuAt` and `backdrop` wiring are as E30 asks
(`FrontMenu.shell`, `Main.main`), and no `Duke3D` call shows the stone menu again without ending the match drawn
behind it. It is not headless-testable from the game either, because `DukeRtsApp` needs a jME context.

## E34 — A scenery piece drawn with another picture (2026-10-10, small)

The castle kits (`models/castles/`, CC BY 4.0, `art/castles/export_castles.py`) want the queen's dark castle drawn
with the same pieces as the kingdom's, in darker, colder pictures. A `ThemeMonster` can already be handed another
`Texture`; a map's scenery cannot. The game works round it by writing a `_dark.gltf` beside every fort piece —
the same `.bin`, its images pointed at `textures/*_dark.jpg` — which costs a file a piece and doubles the names
the editor offers.

Asked: a scenery line may name a picture after its model — `models/castles/fort/fort_wall.gltf@textures/fort_9_dark.jpg
x y facing scale footprint`, or a seventh word — that replaces the base colour of every material of that piece (or
of the material whose picture has that file name). `Tileset.piece` caches by model and picture. Done when a
`TerrainScene` test lays one model twice, once with another picture, and the two draw different textures.

## E35 — A map's scenery line carries its tint (2026-10-10, small)

`MapScenery.tint()` exists and the client multiplies it over a piece, but a map's `Scenery` line is `model x y
facing scale footprint` and is read back white (`GeneratedDungeon.Piece.of`, the editor's `Scenic.written`): a tint
is only ever set by a generator. A castle that is a little darker here, a ruin greyer there, a red-lit throne room
— all one model and one tint — cannot be written into a map.

Asked: an optional seventh word, `0xRRGGBB`, read and written by the engine's line format (and by the editor), white
when absent, so every map written today reads the same. Done when a written line with a tint reads back with it.

## E36 — Scenery stands in the way along its length, turned with its facing (2026-10-10, medium)

A piece's footprint is a circle round its origin. A wall of the castle kits runs three to six cells east of its
origin, so a circle cannot stand for it: the courtyard in `maps/qasr` keeps its walls out of nobody's way, and a
map that wants a castle the hero cannot walk through must also paint `#` under the walls — which on painted ground
the client draws as rock (the kit's wall standing in the cell), on top of the castle.

Asked: a footprint may be a box — `footprint` written as `WxD` (cells, in the model's own frame: W along +X from
the origin, D centred across it), turned by the piece's facing — closing the cells it covers as a circle's are; or,
as good, a footprint read off the model's own bounds when the line says `box`. And a `#` cell under scenery that
says so is blocked but draws no rock. Done when a three-cell wall at facing 90 closes exactly the three cells north
of its origin and a hero routes round it.

## E37 — Placing a piece on the grid in the editor (2026-10-10, medium, for duke-plugin)

The owner will build castles in the editor. The scatter brush lays random kinds at random places and turns; a
castle wants one chosen piece at a chosen grid point and a right angle. Asked (of the plugin, which is the owner's
and mine to build): a Place tool — pick one kind from the Castles theme, click a grid point (snapping to whole and
half cells), turn by 90° with a key, write one piece. The pieces' pivots are made for it: a wall starts at its
origin and runs east, a tower is centred, a floor's corner is at its origin (see `data/world/themes/castles.duke`).

## E38 — A map that starts explored (2026-10-10, small)

The owner, 2026-10-10: the open world's pitch-black fog is wrong for it. "There is fog of war, but not black: the
map's relief, mountains and buildings are seen, but units are not — like the fog that falls after you have walked
somewhere once. The pitch-black fog only for the endless descent and when you enter a dungeon: there the map is
small and opening dark places is interesting; in the open world opening a huge map is tiresome. A player who sees at a
glance how big the map is and what is on it gets more curious."

So on the wide worlds the client's own fog (`Discovery`) should start with every cell **explored** (REMEMBERED: the
ground, cliffs and scenery drawn at the remembered light, no creatures drawn), and only the eyes make cells VISIBLE.
Today the only whole-map entry is `openEverything()` (explored and visible — a watcher's), and black is the start.

Asked: `Visuals.fogStartsExplored(Predicate<Object> forMap)` (the E18/E24 shape, asked of each map's record as it is
laid): a map it names starts with all cells explored and none visible; the descent and dungeons, not named, start black
as today. The minimap the same (the whole map drawn, creatures only in sight). Done when a test lays a named map
explored-not-visible and an unnamed one black.

## E39 — Coins that bounce and fly into the hero (2026-10-14, small)

The owner, 2026-10-14: when a monster dies and pays, coins burst out of the corpse, fall, bounce, and fly into
the hero who killed it, and the gold floats up as "+20". The game does it today with what the engine has: a
GoldBurst plays a BURST effect of coin pictures at the corpse (up in an arc, gravity, spin, fading as they fall),
once for every few gold, and its own painter floats the number over the corpse through `Canvas.screenOf`. Two
things are missing for the rest of the look, both drawing only (out of the checksum, as every effect is):

- **A floor to bounce on.** A BURST particle falls through the ground. Asked: a layer's `Bounce` (0 to 1, the
  speed kept on hitting the floor at the effect's own height; 0 as today) so a coin lands and hops once or twice.
- **A thing to fly into.** Asked: an effect played at a point with a thing to end at — `World.effect(name, where,
  facing, toward)` — whose layers that say `Home = Yes` turn, after `HomeDelay` of their life, toward that thing
  (where it stands as drawn) and are gone on reaching it; a thing gone, they fade where they are.

And a nicety: a coin spins in the screen's plane today (`Spin`); a `Flip` (turns a second, the picture squeezed
across by its cosine) would make it read as a coin turning over. Done when a test plays a homing layer at a point
with a thing and its particles end at the thing, and a bouncing layer's particles never go under its floor.

**Landed** as 946409ed (branch e39-bouncing-homing-particles): `Layer` gains `Bounce`, `Home` with `HomeDelay`, and
`Flip`; `World.effect(name, where, facing, toward)` and `EffectPlayed.toward`. The game reads the four in
`Main.layerOf`, and the GoldBurst plays its CoinBurst toward the killer: the coins bounce, spin over and fly into him.

## E43 — A thing carries several figures: affixes (2026-10-15, medium)

The big plan's items (docs/plan/2026-10-15-loot.md) are Diablo's: a magic sword is +9 % damage and +6 % attack speed,
a rare ring +3 strength, +2 mana a second, +5 % lifesteal and +30 health. A `Loot` carries one figure (`Kind`, `Value`)
and one extra (`Extra`, `ExtraValue`), and lends passives (`Skills`). The game fits what it can into those three — an
affix of the base's own figure folded into `Value`, one extra-figure affix in `Extra`, lifesteal as a lent
`LIFESTEAL` — which caps an item at three affixes, one of each kind, and rules out a second flat figure on one thing
(health on a ring, armour on a helmet).

Asked: `Loot` gains `affixes`, a list of `Affix(String stat, int value)` — a `Stat` word or the game's own, as `Kind`
is — kept through `joined`, `worth` and every copy, and `LootBag.total(kind)` and `extra(kind)` add every acting
thing's affixes of that word beside its own figure (`attributes` too: an affix naming the attribute). A thing with
none is as today. Done when the rpg tests pin a worn thing with two affixes moving two figures, and one in the
backpack moving none. The game then writes every affix there, lifts the cap to four for a rare, and lets any affix
roll on any slot (the `Affix` blocks' `Slots`).

**Landed** as fc24cecd: `rpg.item.Affix(stat, value)`, `Loot.affixes`, added by `LootBag.total`, `extra` and`attributes`; the game's generated things carry every affix there (`loot/Items.loot`).

## E44 — Crit and move speed as figures; a worn thing that raises a skill (2026-10-15, medium)

Diablo's affixes the hero has no figure for:

1. **Critical strikes** — `Stat.CRIT_CHANCE` (percent) and `Stat.CRIT_DAMAGE` (percent more), read by the hero's
   weapon and skills from what he wears; drawn from `world.random()` on the blow, so a party crits alike.
2. **Move speed** — `Stat.MOVE_SPEED` (percent), added to his figures' speed by `HeroProgress` as agility's is.
3. **A skill's rank** — a worn thing that adds a rank to a key's skill while worn, counted by `SkillBook` above what
   he learnt, never past `MaxRank` and the bonus.

Done when the rpg tests pin each from a worn thing and its end when it is taken off.

**Landed** as fc24cecd: `Stat.CRIT_CHANCE`, `CRIT_DAMAGE` (a `CriticalStrike` from what he wears), `MOVE_SPEED` and`SKILL_RANK:<key>`; the game rolls each as an affix and its sheet says a critical blow.

## E45 — The bag's card in the game's colours (2026-10-15, small)

`BagScreen` draws a thing's card in three colours: the name in torch, the lines in bone, the hints dim. An RPG's card
is read by colour: the name by rarity (grey, blue, gold, purple), the base figure white, affixes blue, the lore gold,
and the comparison with what is worn green and red. The game paints its own card over the engine's for now
(`ItemCard`: the same lines and fonts at the same anchor, so it covers it whole), which means it repeats the bag's
layout sums.

Asked: the bag's `gives` may hand back coloured lines — `Function<Loot, List<BagScreen.Line>>` with
`Line(String text, int colour)`, beside today's strings — and the name's colour from the game; and a hook for lines
under the hints (the comparison). Done when the client test draws a card with a blue line. The game then drops
`ItemCard`'s overlay and keeps only its belt.

**Landed** as fc24cecd: `BagScreen.colouredBy(lines, nameColour, under)` with `BagScreen.Line`; the game colours the bag's cardthrough `Bag.coloured` and keeps `ItemCard` for the belt and the trade window's card.

## E46 — The game's own keys in the Settings key list (2026-10-15, small)

The player screens (`2026-10-15-player-screens.md`) open on C, L and M, the tracker folds on J, and the skills and
orders have their letters — all the game's, through `Hotkeys`. The client's Settings screen lists none of them, so
the only place a player learns them is the small row of hints the game draws over the panel.

Asked: Settings shows a read-only **Keys** page — the client's own controls (`KeyMap`) and every letter the game
claimed (`Hotkeys.claimedKeys()`), each with a word the game gives (`Hotkeys.on(key, word, action)`, or a
`Hotkeys.describe(key, word)` beside the existing calls), in the game's language through `Shell.word`. Rebinding can
come later. Done when a test opens Settings with a game that claimed C with a word and finds the row.

**Landed** as 9ce15e4e: `Hotkeys.on(key, word, action)` / `describe(key, word)` and a read-only `Keys` list in
Settings. The game says C, L, M (the screens' titles), J (`TrackerWord`), the orders F A S D (the Hud's order words)
and each hero's skills (their names), and `data/world/menu.duke` gives Uzbek for `Keys` and every client control.

## E47 — A hero's whole figures in one place (2026-10-15, small)

The character sheet shows what a hero's figures come to, and has to gather them from five places: `HeroFigures`
(attributes, health, speed, attack, mana), the weapon's `reloadFrames` read off the template, the bag's
`attackSpeedPercent`/`healthRegen`/`manaRegen`, the sheet's regen in tenths, and lifesteal summed by hand from the
book's `LIFESTEAL` skills (its `passives()` is private, so a worn thing's lent lifesteal is missed) plus
`SkillBook.auraOn`. There is no crit at all.

Asked: `HeroFigures` (or a `HeroProgress.sheetOf(body)`) carries the rest of what the body uses — blows a second,
health and mana a second, lifesteal percent (own, lent and aura), and crit chance and multiplier once the engine has
them — worked out by the same code that applies them, so the sheet can never disagree with the fight. Done when a test
gives a hero a worn lifesteal thing and a quicker weapon and reads both back from the one record.

**Landed** as 9ce15e4e: `HeroProgress.sheetOf(body)` → `Sheet(HeroFigures, HeroRates)`. The character sheet reads its
blows a second, health and mana a second and lifesteal (worn things' lent included) from `HeroRates`; no crit row,
the engine has none yet.

## E48 — The letter a key code is, for a game's canvas input (2026-10-15, small)

`CanvasInput.Key` carries jME's scan code, and a game that takes letters on its canvas — the player screens take C, L,
M while one is open — has to keep its own copy of the keyboard's rows (`PlayerScreens.codeOf`), as `DialogueScreen`
keeps Esc, Space and Enter. `Hotkeys.codeOf(char)` already exists and is package-private.

Asked: `Hotkeys.codeOf(char)` public (or `CanvasInput.Key.letter()`), and the few named keys a game's canvas answers
(`CanvasInput.ESCAPE`, `ENTER`, `SPACE`) as constants. Done when the game's copies are deleted.

**Landed** as 9ce15e4e: `Hotkeys.codeOf` public and `CanvasInput.ESCAPE`, `ENTER`, `SPACE`. The game's copies are
gone (`PlayerScreens.codeOf`, `DialogueScreen`'s three); only Enter on the number pad stays the game's own.

## E40 — A creature moved by the story over frames: a slide, a leap, a fall from the sky (2026-10-15, medium)

Act 1's opening (`docs/plan/2026-10-15-act1.md`) has three moves the engine cannot make: the hero **lands** in a beam
of light, the enemy commander is **knocked back** by it, and (the campaign's) the commander **leaps**. The game does
them today with what there is: its `PLACE` and `KNOCK` acts set a thing's position at once (`GameObject.setPosition`)
under an effect — a jump cut, not a move.

Asked: a scripted move a game may put on any thing for so many frames — `Locomotor`-free, in the simulation and
checksummed — from where it stands to a point, along a straight line or an arc of a given height (a leap, a
knock-back's hop), or straight down from a height (a fall from the sky), the thing taking no orders and no blows'
pushes meanwhile, and told when it lands (an event the story can hear). Done when a test moves a creature 60 units
over 20 frames along an arc 15 high, it stands where it was sent on frame 20, and the landing is heard once.

**Landed** as fa3e72a0: `ScriptedMove.along(thing, to, frames, arcHeight)` / `fall(thing, to, height, frames)`, `onLanded`, the event `ThingLanded`. The game's story acts `MOVE`, `LEAP` and `FALL` (data: Target, Zone, Frames, Height, Landed) use it: the hero falls out of the sky onto the landing and the enemy commander is thrown back in an arc (`docs/plan/2026-10-15-act1.md`).

## E41 — A cutscene camera that turns, tilts and zooms, eased (2026-10-15, small)

A story's shot flies the camera to a point in a straight line at an even speed (`GameCamera.moveTo`); turning,
pitch, zoom and easing are not there (the story-scripting spec's "Deferred"). Act 1's shots want a low, close look
at the landing and a slow pull back over the battle. Asked: `GameCamera.moveTo(x, y, frames, Ease)` and
`turnTo(angle, frames)`, `pitchTo(degrees, frames)`, `zoomTo(distance, frames)`, each eased (linear, in, out,
in-out), stepped with the world and given back at `release()` to the player's own. Done when a test moves, turns and
zooms the camera together over 30 frames, eased in-out, and it ends exactly where asked.

**Landed** as fa3e72a0: `GameCamera.moveTo/turnTo/pitchTo/zoomTo` with frames and an `Ease`, `isMoving()`, `DukeGame.setCameraSeen`. A story Shot says `Turn`, `Pitch` (degrees), `Zoom` and `Ease`; Act 1's landing, the queen's hall and the turning use them.

## E42 — A title card (2026-10-15, small)

Act 1 ends on "1-qism tugadi"; Act 2 will open on its own card. Today the game says it with the run's banner (the
`won|` line of a `WIN`), the same strip a floor's "next depth" uses. Asked: `DukeGame.titleCard(text, subtext,
fadeIn, hold, fadeOut)` — big centred lettering over a darkened screen in the shell's font, drawn only (out of the
checksum), the game's input taken while it shows unless the game says otherwise. Done when a test shows a card and
the client reports it gone after its frames.

**Landed** as fa3e72a0: `DukeGame.titleCard(text, subtext, fadeIn, hold, fadeOut[, takesInput])`, `titleCardShowing()`, `titleCardGone(card)`. The story's `TITLE` act shows a `Title` block: each Act 1 chapter's name as it opens, and "1-qism tugadi" at its end.

## E49 — A credits roll (2026-10-15, small)

Act 2 ends the game (`docs/plan/2026-10-15-act2.md`): after "Oxiri" the credits. Today they are five title cards in a
row (E42), timed by the story's `AFTER` triggers, each replacing the last. Asked: `DukeGame.creditsRoll(lines,
seconds)` — the lines (a heading, then names) scrolling up over a darkened screen in the shell's font, drawn only,
skippable with Esc or a click, and a `creditsGone()` the game can ask for (or a callback) so it goes back to the front
menu when the roll ends rather than after a fixed time. Done when a test rolls ten lines over two seconds and the
client reports it gone.

**Landed** as e81d3d9c (branch e49-credits): `DukeGame.creditsRoll(lines, seconds, ended)` with `CreditsRoll.Line.heading`
/ `name`, skipped by Esc or a click, `ended` told on the window's thread; `creditsShowing()`, `creditsGone(roll)`. The
game's `CREDITS` act rolls `data/story/credits.duke` (the makers from `CREDITS.md`, grouped by kind) with its music
cue, and its `ended` goes back to the front menu.

## E52 — A route that prefers the roads (2026-10-15, small)

The open world's travellers and its pedlar (data/story/villages.duke, `Npc.Route`) walk from village to village by
`Locomotor.moveTo`, and the engine's path is the shortest over walkable ground: across a meadow, over a hill, never
along the dirt road the world's writer laid for them. A traveller cutting across a field looks lost.

Asked: the path grid takes a cost per cell -- or a set of preferred cells and how much cheaper they are -- that the
pathfinder (and the sector router on a wide map) weighs, from the map (its painted roads, `d` in the world's paint,
or a game-handed mask); every map that hands none routes as today. Done when a test routes between two points joined
by a road with a shortcut over open ground and the path keeps to the road where the cost says it should.

**Landed** as eedd090b on the engine's branch `e52-road-costs`: `PathGrid.setStepCosts(int[])` (a share in percent for each walked cell, `cy * width + cx`; null clears), `hasStepCosts()` and `stepPercent(cx, cy)`. The game builds the array from a painted map's `d` road (`Roads`: road 100, anything else 250) wherever `DungeonRun` lays a map's terrain, and passes nothing for a map with no road.


## E55 — Music the game chooses, and a weapon that keeps its name (2026-10-15, small)

The game now picks its music by place and moment (`Soundscape`, the Audio block's Music list) through
`Duke3D.music(cue, fadeOut, fadeIn)`. Two things fight it. (1) `startGame` plays the settings screen's chosen track
(`playChosenMusic`), so every match opens with track 0 for a frame before the game's track fades in, and the Music row's
track picker on the settings screen changes nothing that lasts (the game asks for its own track again within a second).
Asked: `Duke3D.musicByGame(true)` — the client plays no track of its own at a start or a menu, and the settings screen
shows the volume but not the track picker. (2) `HeroBuild.weapon` rebuilds a hero's `WeaponUpdate.Data` with the six-field
constructor and drops its `name` (and `targets`, the clip and `targetScanFrames`); the game names every weapon after
its holder (`Dungeon.namedFor`, after HeroBuild) so `fired.<Template>` sounds per creature. Asked: HeroBuild keeps every
field but the damage. Done when a test builds a hero from a named, targeted weapon and gets both back.

**Landed** as 7b7f76c1 on the engine's branch `e55-57-sounds`: `Duke3D.musicByGame(true)` (set in `Main`) and `HeroBuild.weapon` keeps the weapon's name and targets. `Dungeon.namedFor` stays only to name weapons their block left unnamed after their holder.

## E56 — A flat loop the game starts and stops, faded (2026-10-15, small)

Ambience per place rides the hero today: the simulation plays `ambience.<place>` with `World.sound(cue, hero, hold)`
every check (`DungeonRun.ambience`), so it has to be mono and placed, and when he leaves the place it stops dead when
the hold runs out. Asked: `Duke3D.loop(String cue, float fadeIn)` / `Duke3D.stopLoop(String cue, float fadeOut)` — a flat
loop on the cue's channel, one per cue, started and faded by the game's client — and, for a held `SoundPlayed`, a fade
of a few tenths of a second when it is let go of rather than a cut. Done when a test starts a loop, stops it with a
fade, and the sink sees the gain fall over that time.

**Landed** as 7b7f76c1 on the engine's branch `e55-57-sounds`: `Duke3D.loop(cue, fadeIn)` / `stopLoop(cue, fadeOut)`. `Soundscape` starts and fades the Audio block's Ambience loop as the place changes; `DungeonRun.ambience` (the held sound riding the hero) is gone.

## E57 — The bag's sounds, and a footstep with its ground (2026-10-15, small)

(1) `BagScreen` (rpg-client-3d) says nothing when it opens, closes, or a thing is picked up and put down in it, and
has no `isOpen()` the game could watch. Asked: it raises `bag_open`, `bag_close`, `item_lift`, `item_drop` through the
game's bank (any it does not name stay silent). (2) A creature's footsteps are `walking.<Template>`, one sound wherever
it walks; the game plays its hero's own by the paint under him (`Soundscape.stepOn`), but monsters cannot. Asked:
`walking.<Template>.<surface>` where the map is painted — the surface the palette picture's file name without its
folder and extension — falling back as every name does. Done when a test walks a creature from grass onto rock and
hears `walking.X.grass` then `walking.X.rock`.

**Landed** as 7b7f76c1 on the engine's branch `e55-57-sounds`: `BagScreen` raises the four cues through the bank and has `isOpen()`; a walker on a painted map plays `walking.<Template>.<surface>`. The game's cues are in `ui.duke` and `sfx.duke` (heroes and walking creatures, six grounds and stone); `Soundscape.stepOn` is gone.

## E58 — Feel: heavy numbers, a hit-stop, and list shakes that obey ShakeScale (2026-10-15, medium)

The game now knocks the camera only on big moments, through `hurt.<Template>.normal.major` and `died.<Template>` moments that play an EffectList with a `Shake` (`data/world/feel.duke`). Three things the client cannot do yet. (1) **A number that says how hard.** `HitNumbers` draws every blow alike, off the snapshot (`HealthWatch`), and a critical strike is not in the snapshot or in `ObjectHurt`. Asked: `ObjectHurt.critical` (the `BlowRoll` that made the blow harder than 1.0 sets it), and `HitNumbers` gets `BigAt` (a damage), `BigScale`, `BigColour` and `CritColour` and `CritScale`: a number from `BigAt` is drawn bigger and a critical one in its own colour (yellow) and size, a heal and a blow taken keeping theirs. Done when a test feeds a critical `ObjectHurt` and the number is the crit colour and scale. (2) **Hit-stop.** There is nothing: a heavy hit and a crit land with no weight. Asked: `Visuals.hitStop(HitStop)` with `Seconds` (about 0.04 to 0.07), `Slow` (the share of normal speed the struck and striking creatures' animations and the effects around them run at, 0 a freeze), `MajorOnly` and `CritOnly`; the client dips its own animation and particle time only, never the simulation's frames, and a second one within the first's time does not extend it. Done when a test sees a major `ObjectHurt` slow the victim's and attacker's action speed for that long and restore it. (3) **List shakes obey `ShakeScale`.** `ListShow.shake` ignores `Visuals.shakeScale`, so the setting that turns the camera still (0) leaves a heavy hit's knock and a boss's death. Asked: `ListShow.shake` multiplies by the same scale `SkillEffects.knock` does. Done when a test with scale 0 sees no offset from a `Shake` entry.

**Landed** as 62e569ef on the engine's branch `e58-feel`: `ObjectHurt.critical` (weapon shots, direct skill casts, single-target skill arrows; not yet looseAlong, wave or trample), five more `HitNumbers` figures (`BigAt`, `BigScale`, `BigColour`, `CritColour`, `CritScale`), `Visuals.hitStop(HitStop)` and list shakes that obey `ShakeScale`. The game's are in `hud.duke` and the new `HitStop` block in `feel.duke`. Still open: a hurt moment named by critical (`hurt.<T>.<type>.crit`), so a true crit can knock the camera -- the heavy-blow bars stand in for it.

**Follow-up landed** as afa7af42 on the engine's branch `e58b-crit-moments`: a critical `ObjectHurt` plays `hurt.<Template>.<type>.crit` where the game names it (else `.major`), and the same name goes to the sound bank; WAVE, SKILLSHOT, SKEWER and trample set `critical`. The game's `CritBlow` blocks in `feel.duke` name it for every creature (a stronger knock on heroes).

## E59 — Settings: an Ambience volume, the camera-shake scale, and a language row (2026-10-15, small)

The settings screen (`DukeRtsApp.showSettingsMenu`) has Volume, Effects, Voice and Music and no more. (1) The dungeon's ambience loop is on the `Effects` channel (`ambience.dungeon` in `sfx.duke`), so a player who finds the loop tiring can only mute every blow and footstep with it. Asked: a `SoundBank.Channel.AMBIENCE` the game can name in a Sound block, with its own knob (`volAmbience`, default 70) between Effects and Music, worded through `say("Ambience")`. (2) `Visuals.shakeScale` is set by the `HitFeel` block (`ShakeScale = 1` in `scene.duke`) and no player can change it; E58 added knocks on big moments, so a player who dislikes a moving camera has no way out. Asked: a "Camera shake" row (Full / Half / Off, saved as `shake`, applied through `Visuals.shakeScale` over the block's value), worded through `say`. (3) Asked, lowest priority: a "Language" row only if the game lists more than one word set (`Words` in `menu.duke` is one); the game is Uzbek-first and has no second language yet. Done when a test sets `volAmbience` to 0 and the loop's gain is 0 while effects still sound, and a test with `shake` Off sees no knock offset.

## E60 — Closest-thing questions without a sort, and a weapon that does not copy its owner's modules (2026-10-15, small, performance)

Profiled headless (JFR) on the open world and on `maps/deep` with the hero idle: most of a frame is the
engine answering "the closest thing within R". Every `GameLogic.findClosest` / `findClosestInReach` goes through
`PartitionManager.candidates` → `SpatialIndex.near`, which copies every bucket the box touches into a new `ArrayList`
and **sorts it by `getEntered`** before the caller walks it. Each idle monster asks twice a frame (the game's
`MonsterBrain.nearestEnemy` and `somethingNearbyHasStarted`, and `WeaponUpdate.acquireTarget` once more), so on
`deep` (226 things) the top of the profile is `TimSort.binarySort`, `Comparator.comparingLong`, `ArrayList.addAll` and
`SpatialIndex.near` -- over half the samples of a run (0.46 ms a frame on `deep`, 0.09 on the world). (1) Asked: `closestObject` and `closestWithinReach` need no
order — the best is the least distance, ties to the lowest id, the same whatever order the candidates come in (the
filters the game passes are pure) — so let them walk the buckets in place, no list and no sort; keep the sorted
`near` for questions whose answer is a list. (2) `WeaponUpdate.heldByAModule` calls `GameObject.getModules()`, which
is `List.copyOf(modules)`, for every armed thing every frame; asked: walk the modules without the copy (or a
`hasModule(Class)` / `anyModule(Predicate)` on `GameObject`). Done when the closest-thing tests still pass thing for
thing and a frame of `deep` with every monster idle allocates no candidate lists.

**E59 landed** as 01eb7029 on the engine's branch `e59-60-settings-perf`: `SoundBank.Channel.AMBIENCE` with `volAmbience` (default 70, `say("Ambience")`), loops follow their channel's knob live, and a "Camera shake" row (Full / Half / Off). The game: `ambience.dungeon` in `sfx.duke` is `Channel = Ambience` (`Main.channelOf` already maps any enum word, so no parser change), the five new words are in `menu.duke`'s `Words` in Uzbek (Muhit, Kamera titrashi, To'liq, Yarim, O'chiq), and `HitFeel.ShakeScale` is set in `Main.looks` before `duke.launch()`. Tests: `DungeonSoundTest.theAmbienceLoopsAreOnTheAmbienceChannel`, `MainMenuTest.everyFixedWordOfTheClientsMenusIsSaidInUzbek`.

**E60 landed** in the same commit: closest-thing questions walk the buckets unsorted. The game's three filters (`MonsterBrain.nearestEnemy`, `somethingNearbyHasStarted`, `HeroBrain.closestHeCanShoot`) were audited and are pure (no RNG, no mutation). `deep` idle: 0.414 -> 0.208 ms a frame; the open world is unchanged at 0.087.

## E61 — Where the hero is wanted: a key that drops early, and the game's marks on the minimap (2026-10-15, small)

The owner played MSI 1.0.39: a floor's key lies only where the **last** of the floor's own outside the keep fell, so
one skeleton missed in a far corner keeps the boss's gate shut; and after taking a quest he did not know where to go.
(1) **A mission's leeway.** `rpg.mission.Mission` lays the key when its count of deaths reaches all it counted. Asked:
`Mission.begin(..., int leeway)` — the key is laid at the death that leaves no more than `leeway` of the counted
standing (a counted thing that has left the world, or is dead, does not stand), where that one fell; `killed()` stops
at `outside()`; 0 is today's. The game works it out (`Run.keyLeeway`: 15% or 3, whichever is more, never all) and
stands in today with `run/EarlyKey`, a `DieModule` beside each `Counted` that calls the dying one's `Counted.onDie()`
for the rest — it goes once this lands. Done when a test kills all but `leeway` of five counted and the key lies
where the last killed fell, and the rest dying lay no second one and leave `killed() == outside()`. (2) **The game's
marks on the minimap.** The minimap draws units and the camera's box (`DukeRtsApp.minimapDots`) and nothing a game
names. Asked: `Duke3D.minimapMarks(Supplier<List<MinimapMark>>)` — a world point, a colour and a picture (or a plain
diamond), read each frame on the window's thread, clamped to the minimap's edge when off it (pointing the way), so the
floor's key and each quest's target show there as they do on the game's world map (M). Done when a test hands one mark
inside and one outside the minimap's span and sees the first at its projected place and the second on the edge. The
game today draws its key and quest targets on its own world map, and an arrow round the hero on the screen
(`MissionScreen.pointers`, from `Canvas.screenOf`) — no engine hook is missing for that.

**Landed** as 6e789f32 on the engine's branch `e61-key-minimap`: `Mission.begin(..., int leeway)` (clamped to 0..outside-1, one key, `killed()` capped at `outside()`) and `Duke3D.minimapMarks(Supplier<List<MinimapMark>>)`. The game: `DungeonRun.missionOf` passes `Run.keyLeeway`, and `run/EarlyKey` is deleted; `MissionScreen.minimapMarks` feeds the floor's key (the menu's gold, blinking) and every quest going's target (Run `QuestColour`, a still diamond), and quest places now stand in their own blue `QuestBeam`, not the key's gold. Tests: `MissionTest` (the early drop and the 0/0 last-of-them rule through the engine), `QuestMarkerTest.theCellarIsMarkedOnTheMinimap`, `theKeyIsMarkedOnTheMinimapAndBlinks`, `aQuestsPlaceLooksUnlikeTheKey`.

## E62 — Settings kept as the player leaves them, not only on Save (2026-10-15, small)

The owner played MSI 1.0.41: he set the game to a window in Settings, and the next launch was fullscreen again;
`~/.duke-engine/settings.properties` was last written 2026-09-30. The file is the right one (`GameSettings`, beside
the user; the packaged app writes its saves to `~/.duke-engine/saves` beside it, so the folder is writable) and the
game does not move it. What loses the choice is the screen: every row is applied **live** (`fillTheScreen`,
`applyDisplaySettings`, the volumes) into a draft, and only the **Save** button at the foot writes it. Escape is
`undoSettings` (the hint says `ESC back`), and closing the window — its X appears the moment he picks a window, which is
exactly when he is done — throws the draft away with no question. **Repro:** with the file saying `fullscreen=true`,
open Settings from the front menu, set Fullscreen to NO (the window leaves the screen at once), then close the window
with its X, or press Esc; relaunch: fullscreen, and the file's time is unchanged. Asked: what a player has *seen
applied* is kept when he leaves the screen by Esc or closes the window (save the draft on leaving, or ask "save
changes?" once); Cancel stays the one way back. Done when a test sets a row, leaves by Esc and by a stopped app, and
finds the value in the file. The game, meanwhile: its Uzbek hint says `ESC bekor qilish` (Esc cancels), and with no
file yet it starts in a 1280 × 720 window (`Duke3D.window`), not filling the screen (`DisplayModes.firstWindow`).

## E63 — Nothing of play drawn over the menu's backdrop (2026-10-15, one line)

The playtest of 1.0.41: the live fight behind the front menu (`Duke3D.backdrop`) wears every fighter's bar, medallion
and name — "Knight", "Skeleton", a "0" in each medallion — over a picture that is meant to be a picture.
`DukeRtsApp.showUnitBars` already clears them on `Screen.FRONT` ("behind a front end: a picture, with nothing of play
laid over it") but not on `Screen.MENU` with a backdrop shown, which is the engine's own front menu over a backdrop.
Asked: the same `unitBars.clear()` there (and the marks a bar carries with it). Done when a test shows a backdrop under
the menu and finds no bar placed. The game, meanwhile, names each backdrop kind in Uzbek on its status line (`|who=`),
so what is drawn is at least the game's words; the medallion's "0" stays until this lands.

## E64 — A model dressed in its base colour, not in whichever picture comes first (2026-10-15, small)

The playtest of 1.0.41: Lange (the knight hero, `models/heroes/lange/lange.glb`, its pictures inside the file) and his
portrait were drawn pale lavender. `DukeRtsApp.dressModel` takes `skinOf(loaded)` — the **first** `Texture` among
`Material.getParams()` — and jME 3.7 does not hand them in the file's order: a glTF piece with a normal map lists
`NormalMap` before `BaseColorMap` (and `EmissiveMap` before both), so the piece was lit over its normal map, and his
sword over its near-black emissive map. The animatedheaven gate (`models/props/gate/gate.glb`, normal maps) had the
same. **Repro:** `new DesktopAssetManager(true).loadModel(".../lange.glb")` at 1.0.41's game commit and print each
geometry's params: `NormalMap` comes first. Asked: `skinOf` asks by name — `BaseColorMap`, `DiffuseMap`, `ColorMap` —
and only then any texture (`ListShow`'s colour lookup already orders its names). Done when a test dresses a glTF with a
normal map and finds its base colour on the dressed material. The game, meanwhile, ships both files with their base
colour only (`art/models/base_colour_only.js`, guarded by `ModelPicturesTest`); with this landed the maps can return.

## E65 — A passive skill drawn as a passive on the hero's bar (2026-10-15, small)

The owner, 2026-10-15: the knight's E (Dragon Blood, `SkillEffect.DRAGON_BLOOD`, a passive the book never casts) sits on
the bar as a button like his three actives: its key letter, the hover and press wash, a cooldown sweep's place. In
Warcraft III a passive is a framed picture you cannot press. `HeroCard.skills` writes every skill's state as `ready`,
`cool,…` or `lock`, and `HeroPanel` draws every `ready` slot as pressable — neither can tell a passive. **Repro:** pick
the knight, buy E: his E slot lights under the mouse and shows "E". Asked: `HeroCard.skills` writes `passive` for a
learnt skill whose `effect().isPassive()` (unbought, `lock` as now), and `HeroPanel` draws such a slot in a plain,
dimmer frame with no key letter, no hover or press wash and no sweep, takes no click on it (the raise badge stays
clickable while a point can go there), and its card still shows (`tipAt` as the game sends it). Done when a panel test
reads a `passive` slot and finds it unlit under a click and letterless. The game, meanwhile: a passive's key is bound
to nothing (`Main.aimsFor`, so neither the key nor a click on the slot aims, orders or refuses), and its card says
"1-daraja · Passiv" where an active one says its key (`HeroStatus.passives`, hud.duke `PassiveWord`).

**Landed** as 53923bd4 on the engine's branch `e62-65-settings-menu-looks`. E62: Esc and closing the window keep what was applied (`GameSettings.keep()`); the game's hint says `ESC chiqish`. E63: `DukeRtsApp.barsHidden` hides bars and marks over the menu backdrop. E64: `dressModel` dresses in `BaseColorMap` first; the game keeps its base-colour-only GLBs for size (`ModelPicturesTest` is now a size rule; `lange.glb` left to the `anim` branch). E65: `HeroCard.skills` writes `passive`; the game keeps a passive's key bound to a no-op (it also replaces another hero's binding on that letter) and the `Passiv` card line; the knight's E (Dragon Blood) reports `isPassive()`.

## E66 — Scenery a hero can work: a tree pointed at, felled and grown again (2026-10-15, medium)

The owner's food and fire (`docs/plan/2026-10-15-food-and-fire.md`): as in Warcraft III or Dota, the hero right-clicks
a tree, chops it for a few seconds, gets wood, and the tree is gone until it grows back. The world's trees are E10's
scenery (`StaticMap.Scenery`, drawn by the client per chunk, their footprints `GameLogic.setSceneryFootprints` in the
path grid) and nothing in a snapshot or under the pointer is a piece of scenery, so none can be targeted or taken
away. Asked: (1) the context-order rule (`DukeGame.addContextOrder`) and the click are told of a scenery piece under
the pointer (its index and model), as they are of a thing, so the game can answer `Chop` and the pointer shows it;
(2) `GameLogic.removeScenery(index)` and `restoreScenery(index)`: the piece stops (starts again) being drawn and its
footprint leaves (rejoins) the grid, the same on every machine, and the client hears it as an event; (3) what is
removed is part of the world's state a save can carry. Done when a test removes a piece, a body walks through where
its footprint was, the client draws it no more, and restoring it puts both back. Until then the game places its own
choppable trees as things (`ChoppableTree`, the Food block's Forage, about 60 on the open world) beside the scenery.

## E67 — A busy state with its own clip (2026-10-15, small)

Chopping, picking, filling a waterskin and cooking keep the hero busy for 1.5 to 3.5 seconds (`run/Camp`); he should
be seen doing it. Nothing lets a game play a clip on a creature but the engine's own states (idle, walk) and moments
(attack, hurt, death) and a skill's cast. Asked: `GameObject.busy(String clip, int frames)` — the client loops that
clip (from the creature's own model or its `AnimationSet`) until the frames run out or `busy(null, 0)`, a snapshot
field like a condition, out of the checksum; the clip names live in the hero's block (`Chop`, `Gather`, `Cook`, a
name not there plays nothing). Done when a test sets a creature busy for 30 frames and the snapshot says the clip for
those frames and not after. Until then the game plays a puff (`WorkChop`, `WorkHarvest`, `WorkFill`, `WorkCook`) and a
sound on him every second of the work.

## E68 — Health over time as a buff the book reads (2026-10-15, small)

A meal heals over its seconds (Eternal Return's food), and the owner's rule is that buffs are the skill system's. The
game gives the hero's `SkillBook` a buff of kind `HEAL` (its own id lending it) worth tenths of a point of health a
second until the meal's last frame, and pays it out itself each frame (`Belt.digest`); a drink is a `MANA_AURA` buff,
which the book's trickle already reads. Asked: the book's update heals its bearer by the strongest `HEAL` buff it
holds (tenths a second, the way `regenerate` counts mana), `healthPerSecond()` and the HUD's rates count it, and the
buff row (`BuffScreen`) shows a held `HEAL` and `MANA_AURA` on the hero with a picture the game names. Done when a
test gives a book a `HEAL` buff of 100 for 60 frames and its body is 20 points fuller two seconds later and no fuller
after. Then `Belt.digest` goes.

## E69 — A melee blow struck at its swing's impact (2026-10-15, small)

The owner, 2026-10-15: the heroes' blows "don't stick". The client starts a unit's attack clip on `WeaponFired`
(`DukeRtsApp`, `playOnce(node, attackAnim)`), and a melee blow lands that same frame (`Swing` declines the launch and
the weapon lands it). So the damage, its number and the hit-stop come on the clip's first frame and the blade arrives
later: the knight's Quaternius slash was fastest 0.29s in. Today a wind-up can only be kept by cutting it off, which
is what the game does (`art/lange/export_lange.py` starts Sword_Attack 0.24s in, so the blow lands at the top of the
swing and the blade is fastest 0.12s later). **Repro:** with master's lange.glb, the knight attacks a skeleton: the
number rises before the sword comes down. Asked: an inline `PreAttackFrames` on `WeaponUpdate.Data` (today only a
named `Weapon` has one), and the client playing the attack clip when a wind-up starts (the `PREATTACK` moment) rather
than when the shot fires, for a weapon that winds up; one that does not plays it on `WeaponFired` as now. Done when a
test winds a weapon up 9 frames and the client's attack clip begins 9 frames before the `ObjectHurt`. Then the
knight's slash gets its whole wind-up back with `PreAttackFrames` set to its impact (0.42s at its own pace, 13 frames),
and `HeroClipFitTest` checks the impact against that instead of against 0.

## E70 — A blow struck on the move drawn on the upper body (2026-10-15, small)

The knight swings as he closes (`AttackOnTheMove = Yes`), and `playOnce` plays his slash over the whole body on the
default layer: for its 1.0s his legs keep the slash's stance while he keeps moving, so he slides. Asked: for a unit
whose look names an upper-body joint (`UpperBody = spine`), an attack (and a hurt) played while it moves goes on a
second `AnimComposer` layer masked (`ArmatureMask`) to that joint and below, the paced walk staying on the legs;
standing, as now. Done when a test plays an attack on a moving unit and its leg joints follow the walk while its arm
joints follow the attack. The game then names `UpperBody = spine` for the knight (Lange's rig) and the Huntress's
`QuickRigCharacter_Spine1_013`; nothing until then.

**E66-E68 landed** as 7b8e8069 on the engine's branch `e66-68-food-fire`, with a placed sound loop beside them (`Duke3D.loopAt`/`stopLoopAt`). The game (branch `food2`): a click on the ground under a tree of the open world's scenery answers `Chop` (`DukeGame.groundOrder`, `GameLogic.sceneryNear` within 8 units, ten tree models in the Food block's Gathers, on `SceneryMaps = [world]` only, which is streamed in chunks), the tree is felled with `removeScenery`, a stump stands until `restoreScenery` grows it back, and `SaveGame.Felled` carries the felled ones (a load fells them again and starts their growing over); the game's own `ChoppableTree` is gone. Work loops `GameObject.busy` with the Food block's `Clips`; `Belt.digest` is gone and the book heals by the meal's `HEAL` buff, shown by the Food block's `Taom` skill; every fire seen crackles (`Soundscape.firesHeard`, the `fire.crackle` cue, PagDev's CC0 loop). Tests: `FoodTest.choppingATreeOfTheWorldGivesWoodFellsItAndTheSaveKeepsItFelled`, `aBlowTakenCallsTheWorkOffAndNothingIsSpent` (the clip ends), `BuffScreenTest.aMealIsShownByItsOwnPictureAndWord`.
**E69 and E70 landed** as 7fda6418 on the engine's branch `e69-70-attack-clips`. E69: `WeaponUpdate.Data.preAttackFrames` inline, `WeaponWoundUp` starts the attack clip; the knight's Attack is Sword_Attack whole at 1.45x (1.083s), his `PreAttackFrames = 9` and `ReloadFrames = 25` (34 between blows as before); the Huntress and the mage loose on their clips' first frames and wind up 0. E70: `Drawn.upperBody`; the knight, the Huntress and the mage name their spine joint (`UpperBody`), checked by `HeroClipFitTest.everyHerosUpperBodyIsAJointOfHis`.

## E71 — A flinch that never cuts short a blow or a cast (2026-10-15, small)

The owner's playtest of 1.0.43: "the flinch-back covers the attack: fighting many enemies, the enemy's health goes
down but the hero is stuck trembling from the incoming blows". `DukeRtsApp.flinch` calls `playOnce(hurtAnim)` on
every drop in health, and `playOnce` refuses only while a gesture (`gestureUntil`) runs, so a flinch replaces a
swing in progress (and, on the move, the E70 upper-body swing too) and sets `actionUntil` so the next blow starts
late. Asked: a unit's look may say when it flinches -- `FlinchWhen = IDLE` (never over its own attack clip, its
gesture or while it moves; `actionUntil` from an attack holds the flinch off rather than the other way round),
`FlinchEvery` seconds (no second flinch within it; 1.5s for the heroes) and `FlinchOn = MAJOR` (only an `ObjectHurt`
that is critical or at least the look's hit-feel `majorAt`). Defaults as today. Done when a test hurts a unit
mid-swing and its attack clip runs to its end, and hurts a standing one twice in 0.5s and it flinches once.
Then the game names `Hurt` again for the knight (`Hurt`), the Huntress (`Hit_Chest`) and the mage (`Hit_A`) with
`FlinchWhen = IDLE`, `FlinchEvery = 1.5`, `FlinchOn = MAJOR`, and drops `noHeroFlinchesOverHisOwnBlows`.

**E71 landed** as 8c9b234d on the engine's branch `e71-flinches`: `DukeRtsApp.flinch` plays the hurt clip only on an
idle unit (not over an attack or flinch clip, a gesture, busy work, or while it has a target), and
`Visuals.flinch(new Flinch(seconds, heavyOnly))` sets the least time between two flinches and whether only a crit or
a drop of `HitNumbers.bigAt` and over flinches. The game reads a `Flinch` block (world/feel.duke: `Seconds = 1.0`,
`HeavyOnly = true`) through `Feel.apply`, and the knight (`Hurt`), the Huntress (`Hit_Chest`) and the mage (`Hit_A`)
name their flinch again. The monsters stay without one: in a fight they always have a target, so it would show only
on one struck from out of sight.
