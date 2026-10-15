# The player screens

The big plan's Phase 1, item 3 (`2026-10-15-big-plan.md`): a character sheet, a quest log and a world map, all in
Uzbek. The owner had no time for questions, so every choice below is mine and marked **decided**; each is a line of
data or a few lines of code to change. Every word and key is in `data/world/screens.duke` (the `Screens` block) or
in a block that already owned it. Item tooltips that compare with the worn one are the items work's, not this.

## How they open and shut

- **Keys** (**decided**): **C** the sheet (*Qahramon*), **L** the quest log (*Topshiriqlar*), **M** the map
  (*Xarita*) — the `SheetKey`, `QuestsKey`, `MapKey` lines. The file is refused where one of them is already a
  skill's (Q W E R), an order's (A S D F), the client's halt or pause (H P) or the tracker's fold (J).
- **One at a time.** A screen's key opens it and shuts it again; another screen's key changes to that one; **Esc**
  shuts it. Opening one shuts the trade window. A conversation or a cutscene shuts it: they have the screen.
- **Opened through the game's hotkeys** (`Main.screensOn`), which the client answers only in play — never on a menu,
  never while a chat line is being typed. While one is open the screens take their keys and Esc themselves; every
  other key (the skills, the camera) still goes to the game.
- **The world waits** (**decided**): played alone, the world is paused while a screen is open — the engine's own
  pause, taken and let go of exactly as the dialogue box does it (`PlayerScreens.pause`); in a party it is not,
  since pausing one machine of a lock-step game stalls them all. Nothing of the screens goes down the wire: they are
  the window's alone and only read.
- **Key hints** (**decided**): a small row at the left over the panel — a key cap and a word each: `C Qahramon`,
  `L Topshiriqlar`, `M Xarita`, `J Missiyalar` (the tracker's `FoldKey` with the `TrackerWord`). Drawn whenever no
  screen is open in play.
- **The Settings key list** (E46): the engine's Settings has a read-only *Tugmalar* list. The game says its letters
  there in its own words — C, L, M by the screens' titles, J by `TrackerWord`, F A S D by the Hud's order words,
  each skill by its name — and `data/world/menu.duke` gives Uzbek for the heading and every client control
  (*Pauza*, *Hammasini tanlash*, *Kamera yuqoriga* ...).

## The character sheet (C)

Read from `DungeonRun.getSheet()` — a `run.Sheet` worked out on the simulation's thread each frame from the same
sums his panel and his body use (`HeroProgress.figuresOf`, `HeroStatus.armourOf`, and E47's
`HeroProgress.sheetOf` for his blows, health and mana a second and his lifesteal), as the target frame is.

- **Left:** his live portrait (`Canvas.drawPortrait`, E26's), over the first letter of his name; his name, his title,
  his level (`7-daraja`), an experience bar (`Tajriba 120 / 300`), his gold (`Oltin`), and his souls (`Jonlar`) only
  while he wears the medallion.
- **Middle — Atributlar:** each attribute with its picture, what he has, `+N` in green for what his things give, ★
  for his primary, and under it what all of it gives in the Hud's words: `+264 Jon`, `+1.8 Tezlik`, `+40 Mana`, and
  for the primary `+12 Zarba`.
- **Right — Ko'rsatkichlar:** `Zarba` (his blow, things counted), `Hujum tezligi` (blows a second, `1.25/s`, with
  `(+15%)` from his things), `Zirh` (%), `Jon` and `Mana` (now / most), `Jon/s` and `Mana/s` (his own regen plus his
  things'), `Tezlik`, and `Qon so'rish` (%) only when he has any. Every label is the word the panel or the bag
  already uses. **No crit:** the engine has none yet; a row is one line in `CharacterSheet.figures` when it does.
- **Foot — Kiyimlar:** the worn slots in the bag's order, each framed with its thing's picture and the slot's word
  under it, `bo'sh` in an empty one. Shown, not changed: the bag does the swapping.

## The quest log (L)

Read from `DungeonRun.questsNow()` — the `QuestLog.State` as the last frame left it — and the quests' data.

- **Left:** *Faol* — the quests going, MAIN (◆, gold) before SIDE (◇), each in the order begun, the step it is on and
  its count under it (`• Skeletlarni qiring 2/5`). Then *▸ Bajarilgan (n)*, folded; a click unfolds it (✓ each).
- **Right:** the quest picked (a click picks one; the first going by default): its title, every step reached — the
  done ticked and dimmed, the one to do with its count — and its **description**, a new `Description` line on every
  `Quest` block (written for the two quests there are).
- **Xaritada ko'rsatish:** shown when the step it is on has a place on this map — a REACH's zone, a TALK's or GIVE's
  person, a COLLECT's thing lying (a KILL has none) — and opens the map centred on it, four times closer than whole.

## The world map (M)

- **The picture** (**decided**): the engine's `Duke3D.worldPicture` — the whole world as the player knows it, in the
  minimap's colours, kept up to date — asked for **every** map rather than the wide ones only: the brief's fallback
  ("the minimap's picture, scaled") is exactly this picture on a small map, and the engine has no other way to hand
  the minimap's. At most `MostTexels` (512) a side: the open world's 1024 walked cells are a texel for two. A small
  floor's first painting reads its few cells; no cost worth a second path.
- **Over it:** the named places — the `Places` lines name a map's zones in Uzbek (`Qorong'i_o'rmon = Qorong'i
  o'rmon`); a zone not named (a trigger's corridor) is no place — a diamond and a name each, **gold once a hero has
  stood in it, dim before** (the "discovered" castles); the Dialogue block's `!` mark where each quest going wants
  him; and his arrow, the way he faces.
- **Moving it:** the wheel zooms about the pointer, from the whole map to 16 times closer; a left drag pans; the map
  never leaves the screen. Opened by its key it fits the window; from the log, on the target.
- **Places found are saved** with the run: a `Places = ["Poytaxt", ...]` line in the save (`SaveGame.places`,
  quoted, since a zone name may hold an apostrophe); an older save has none.

## Code

- `world/Screens.java` + `data/world/screens.duke` — the block; `DungeonSettings.validateScreens`.
- `run/Sheet.java` — the sheet's numbers; `DungeonRun.getSheet / questsNow / storyNow / visited`.
- `PlayerScreens.java` — which is open, the keys, the pause, the shared frame and the HUD hints; wraps
  `DialogueScreen`, which wraps the bag. `CharacterSheet.java`, `QuestLogScreen.java`, `WorldMapScreen.java` (its
  `View` is the world ↔ screen projection).
- Tests: `PlayerScreensTest` (each model for a known hero, quest and map; the keys; the projection's round trip; the
  labels from data) and `PlayerScreensMockRenderTest`, which draws the three screens and the hints with Java2D into
  `build/mock-renders/`.

## Left for later

- Crit, when the engine has it; then one row.
- Fast travel from the map between discovered towers (big plan, Phase 2 item 6): the map's places are where it hangs.
