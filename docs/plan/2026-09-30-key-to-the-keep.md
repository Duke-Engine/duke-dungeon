# The key to the keep

Piece 3 of `2026-09-30-roadmap.md`, the missions the keep was built for (`2026-09-30-boss-keep.md`). The owner played
the keep and asked for four things: the keep on the ground, where its gate can be seen; a gate that never opens by
itself; a key that the floor's mission gives and the player hands to the gate himself; and four mages round the boss.

## The keep on the ground

- **Storey 0, not 1.** The court and the doorway stand on the ground; nothing of the keep is a storey up, and there is
  no stair. The three cells before the doorway are its **threshold**: floor, paved in the keep's stone (the Keep look
  still covers them), where the stair used to be. `Keep.isStair` becomes `Keep.isThreshold`; the level map writes the
  keep's floor as `0` like any other; `roomStoreys` is 0 for it.
- Everything else about it stays: the site in solid rock, the ring of wall (a storey high on both faces now, the
  masonry kit's own wall), the doorway in the middle of the side facing its road, level ground, the gate.
- **Four mages at its corners, on every floor:** the boss's guard stands at the court's four corners, a cell in from
  each wall — the guard list in file order, so with `BossGuards = [SkeletonHealer = 2, SkeletonSummoner = 2]` the
  healers stand at one diagonal and the summoners at the other. A guard beyond the fourth stands on the ring round the
  boss as before, as does every guard on a floor with no keep. `MinDepth` no longer keeps a guard away: the file's
  guard stands with every boss.

## The gate stays shut

- **It opens only when a key is given to it.** A hero walking up does nothing; `GateUpdate` loses its reach and its
  timer, and keeps `open()`.
- **A right click on the gate** is an order of the game's own, as a click on a chest is: the ring round it flashes in
  the order colour (`OrderMark.ContextColour`, yellow), the hero walks up to it, and when he is there he says:
  without the key, *"Boss xonasi uchun kalit topishim kerak"*; with it in his bag, *"Kalit menda — uni darvozaga
  berishim kerak"*. The words are data.

## The mission

A floor of the descent that has a keep has a mission, the same on every machine, kept by the run:

1. **Clear:** every monster the floor put outside the keep must die — the ones it spawned, not what a summoner calls
   up later. The tracker counts them: *"Qal'adan tashqaridagi barcha monstrlarni o'ldiring — 12/40"* (killed of all).
2. **Take the key:** the last of them to die leaves the key where it fell, lying as a chest lies; a floor that put
   none outside leaves it at the way in from the start. *"Kalitni oling"*.
3. **Give it to the gate:** a hero has it in his bag. *"Kalitni boss darvozasiga bering"*.
4. **Kill the boss:** the gate is open. *"Bossni o'ldiring"*.

A hero who falls with the key in his bag drops it where he fell, so a party never loses the way on. A floor with no
keep (a stage, or a floor where none fitted) has no mission: its tracker says only the last line.

## The key: the first usable item

- **An item may be used:** a `Use` on the `LootItem` block, `NONE` by default. `UNLOCK` is the only use now; a blink
  and illusions come later on the same field.
- **The key** is a `LootItem` of a new `Kind = KEY`: it gives no figure, never joins with another into a higher level,
  and is never drawn as a random drop (`Weight = 0`). Its `Use = UNLOCK`. It lies on the floor as its own template,
  `Key` (a `GroundItem`, like `Chest`), drawn with the owner's `key_crown.glb` in every biome — one look named once in
  data, not one per theme. Its bag icon is rendered from that model by Blender (`art/` keeps the script).
- **Using it:** a left click on an item with a use, in the bag, arms the aim for a thing (the engine's
  `CommandButton.Aim.UNIT`); the next click on a thing sends the hero to it with the item (an order carried to every
  machine, like a pick-up). When he is there: `UNLOCK` on a gate opens it (its `open()`, the owner's swing) and the
  key leaves the bag; on anything else he says *"Bu kalit faqat boss darvozasini ochadi"* and keeps it. A right click
  on an item still takes it in hand to put down, as now.

## On the screen

- **The tracker** stands at the top of the screen: the mission's step, in the words above, with the count while there
  is one. Drawn by the game (a `Painter`, as the bag is), from the run's mission state read on the window's thread.
- **The hero's lines** are said in a bubble over his head, above his bar (`Canvas.barOf`): whatever his bag's note
  says (`LootBag.say`) — the gate's lines, and the bag's own ("the bag is full") with them.

## Tests

- The keep: storey 0 everywhere, threshold cells floor and in the Keep look, still one way into the court (through
  the doorway); the guard at the court's corners a cell in from each wall, at every depth, the healers on one
  diagonal.
- The gate: a hero walking up leaves it shut; the gate order walks him there and puts the right line in his bag's note
  (without and with the key).
- The mission: the count of monsters outside the keep; the key laid where the last one died, and at the way in on a
  floor with none; the steps in order; a fallen holder's key laid where he fell; no mission on a stage.
- The key: a `KEY` item gives nothing, does not join, never drops at random; using it on the gate opens the gate and
  empties its slot; using it on anything else keeps it and says the line; the use order reaches every machine.
- The shipped data: the key item, its template, model and icon exist; the words are there.

## Not in this piece

Other missions on the chain, other usable items, and the monsters' levels and skills (piece 4).
