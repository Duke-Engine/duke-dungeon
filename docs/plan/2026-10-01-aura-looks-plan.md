# What an aura looks like — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. This plan states each task's
> requirements, tests and files; the implementer writes the code test-first (the code is not pre-written here).

**Goal:** A player tells at a glance which monster bears which aura and who stands in one, as in Dota 1: each aura's
bearer wears its kind's own look (the Revenant's thirst red things round it, the healer's mana a thick blue ring, the
summoner's might a violet one), every creature an aura reaches wears a small light-blue circle underfoot that burns
brighter the more kinds reach it, and the HUD shows the auras on a creature the player picks as small icons whose
tooltip says what each adds.

**Why:** the owner, 2026-10-01, after piece 5 (the mages' haste and auras) landed: its rings are faint, in the
bearer's colour, and nothing shows who is lent what. His words: "Aura lar beradigan unit atrofida auraga bog'liq
nimadir chiqib tursin … Skeleton king ni 2 chi skilli … atrofida qizil narsalar turadi … crystal maidenni 3 skilli …
katta qalin ko'k halqa … aura tasirida bo'lgan unitlarning tagida ham belgi … och ko'k rangdagi kichkina doira …
auralarning ko'pligiga qarab shu ko'k doira yorqinroq yonadi", and "Hud da qanday auralar tasirida ekanligini ham
ko'rsatadigan qilish kerak … kichkina skill ning ikonchalari … hover qilganda tooltip … nima qo'shayotganini". He
approved the design in chat (colours by aura kind: lifesteal red, mana blue, might violet; the circle light blue, 1, 2
or 3 kinds dim to bright; heroes never wear it — no aura reaches a hero).

**Spec:** this file and the owner's words above; piece 5's spec `docs/plan/2026-09-30-haste-and-auras.md` for how
auras work (its "Drawn" section changes as Task 1 says).

## Global Constraints

- Lock-step: a look is an event (`world.effect`), out of the checksum; what the HUD shows is handed from the
  simulation's thread to the client's whole and read there, never a live object read across threads; nothing asks the
  clock or walks a hash.
- Names, colours, sizes, beats and words come from data (the mages' files, `world.duke`); nothing new is compiled in.
- Everything an aura does stays in the skill system: the mark under a receiver is its own `SkillBook`'s doing, asked
  through `SkillBook.auraOn` as the figures are — so when the keep's seal makes an aura stop at the shut gate, the mark
  stops with it, with nothing more to change.
- `../duke-engine` is never edited; an engine need goes into `docs/plan/2026-09-29-engine-requests.md`. Every gradle
  command passes `-PdukeEngineDir=<the scratchpad's engine-080>` (engine 0.8.0, 1b82edf3).
- Each task committed on its own: subject, blank line, `Co-Authored-By: <the model that did the work>
  <noreply@anthropic.com>`.

---

### Task 1: Each aura's bearer wears its kind's own look

**Files:** `src/main/resources/data/units/revenant.duke` (`BloodAura`), `skeleton_healer.duke` (`ManaAura`),
`skeleton_summoner.duke` (`MightAura`); the piece-5 spec's "Drawn" section; tests in `AuraTest` and
`DungeonEffectLayerTest` (and whichever test pins a ring's colour today).

- The ring a bearer wears stays where its reach ends (as wide as its `Radius`, played every `TickFrames`, lasting two),
  but is bold enough to read at a glance: a thick ring (the kit's `circle_04.png`, or another of the kit's rings if it
  reads better — its `Size` set so the brightest line still lies at the reach, as the existing test measures) and
  brighter (`Alpha` about 0.45–0.6 at the ring's peak).
- Coloured by the aura's kind, not by its bearer: `LIFESTEAL_AURA` red (about `0xD01C28`), `MANA_AURA` blue (about
  `0x3F8CFF`), `DAMAGE_AURA` violet (the summoner's `0xB06AE8`).
- Each look gains a layer of its own riding the bearer (an `AURA` layer, `Rate`-fed, renewed at each beat as every
  aura's look already is — `Main.layerOf`): for the thirst, red drops or embers rising and turning round the body (the
  Skeleton King's vampiric aura); for the mana, a few blue motes at the feet; for the might, violet sparks. Small: the
  ring carries the reach, these say "this one bears it".
- Tests: the ring still lies where the reach ends for each of the three; each look is measured two beats and its
  rider layer renews; the three rings' colours are red-, blue- and violet-led as above; the real-floor checksum is
  the same with the looks and without (the existing `SkillLookTest` run).
- The words in the three files' notes and the spec's "Drawn" section say what is now true (rings by kind, the rider).

### Task 2: A light-blue circle under every creature an aura reaches, brighter the more kinds

**Files:** `world/Combat.java` (two new fields), `world.duke`'s Combat block and three new `Effect` blocks beside
`Stunned`, `skill/SkillBook.java` (a beat that counts the kinds reaching its owner), `Main.java` (`measureLooks`),
`content/DungeonSettings.java` (load rules); tests in `AuraTest` (or a new `AuraMarkTest`), `DungeonSettingsTest`.

- The Combat block names `AuraMarkLooks` — a list of `Effect` names: the first worn by a creature one aura kind
  reaches, the second by one two kinds reach, the third by three (a count past the list's end wears its last) — and
  `AuraMarkTickFrames`, the beat (30). An empty list draws nothing. Load rules: every name an `Effect`; a beat of at
  least 1 when the list is not empty.
- One rule says which auras are on a creature: `SkillBook.aurasOn(creature)` — the aura kinds, in `SkillEffect`
  order, whose `auraOn(creature, kind)` is above 0, a `MANA_AURA` counting only for a creature with a mana pool (it
  changes nothing on one without). Pure, asked when wanted, nothing stored; Task 3's HUD asks the same rule.
- Every `SkillBook`, on its beat (the frames its owner's id falls on, as the rings are staggered), asks that rule of its
  owner and, if any, plays the look for that count on it. Counted by kind, not by bearer: two summoners lend one
  might, so they make one, not two. The bearer itself is among those its aura reaches. A hero's book counts nothing
  (its `auraReach` is 0); a dead creature wears nothing.
- `Main.measureLooks`: each mark lasts two beats, so it rides the creature while the aura reaches it and is gone within
  two beats of leaving it.
- The three shipped marks: `MARK`, `Follows`, `Measure = Units`, a small soft ring underfoot (about 14 units across,
  the kit's `circle_05.png` or `circle_04.png`), light blue (about `0x8FD8FF`), `Alpha` rising with the count (about
  0.3, 0.55, 0.85), fading in and out over half its life each (the rings' crossfade), lying just above the floor.
- Tests: a skeleton beside a summoner wears the first mark at its beat; beside a summoner and a healer (it given a
  pool) the second; with a Revenant too the third; two summoners still the first; a pool-less skeleton beside a healer
  alone nothing; alone, behind stone or past the `Radius`, nothing; the summoner itself wears one; a hero beside a
  summoner never; a creature killed wears none from then. `aurasOn` answers the same kinds in the same order. The marks
  are measured two beats; their `Alpha` rises with the count; the load rules refuse by message; the real-floor
  checksum is the same with the marks and without.

### Task 3: The HUD shows the auras on the creature the player picks

**Where it can go (found 2026-10-01):** a left click on a monster selects it alone and the engine's `HeroPanel` then
shows its card (portrait, name, health) from the status channel built in `run/DungeonRun.card` / `HeroStatus.creature`;
the panel has no place for buffs, and its parser hides the whole panel on a field it does not know — so a buff row
there would be an engine change. The game draws its own pieces on the canvas through `BagScreen` (the one `Painter`
and `CanvasInput` the client takes; `MissionScreen` is painted from `BagScreen.paint`), reads simulation values handed
over whole from `game.onTick` (`BagScreen.lying`), hangs things over a unit's bar with `canvas.barOf(id)`
(`MissionScreen`), and draws hover tooltips with `BagScreen.tip`. Live `SkillBook` state may not be read from paint
(`run/Watching`). So the row is the game's, over the creature's own bar.

**Files:** a new `BuffScreen` beside `BagScreen` (owned and painted by it, right after `MissionScreen`), `BagScreen`
(the call, and its tooltip drawing shared rather than copied), `skill/SkillBook.java` (a getter for a haste's percent),
the four skills' `Icon` and `Blurb` in `skeleton_summoner.duke`, `skeleton_healer.duke`, `revenant.duke`, the
tooltip's words in data (one new: what a lifesteal share is called); tests in a new `BuffScreenTest` (fake canvas, as
`MissionScreenTest`) and `AuraTest`.

- For the creature the player has selected alone and the one the pointer rests on (`getSelection`, `getPointedAt`,
  read on the simulation's thread), each tick the screen reads what is on it — `SkillBook.aurasOn` (Task 2), and a
  haste while one burns on it — with each one's skill (its name, icon, blurb) and figure, and hands the client an
  immutable map from id to that list, whole.
- Painted over the creature's bar (`canvas.barOf`), left-aligned with it: one small icon a buff (about 20 px at the
  design size), in `SkillEffect` order — the skill's `Icon`, framed in its kind's colour (Task 1's ring colour); with no
  icon, a stone square with the first letter of its name. The selected creature's row always; the pointed one's while
  the pointer is on it or on its row; none behind the bottom panel (a pointer there names nothing).
- The pointer on an icon shows a tooltip in the bag's style: the skill's name; what it adds, from the figures, never
  written into the words — "+50% zarba", "+5 mana/s", "10% hayot so'rish", a haste's "+75% hujum tezligi" and the
  seconds it has left; then its blurb. A left press on an icon is taken (it neither selects nor orders what is behind).
- Data: `Icon` for HASTE, DAMAGE_AURA, MANA_AURA and LIFESTEAL_AURA — the stat pictures already cut until aura art is
  painted (`icons/stats/stat_speed.png`, `stat_attack.png`, `stat_intelligence.png`, `stat_lifesteal.png`) — and a
  one-line `Blurb` each, in Uzbek.
- Tests: the published map for a skeleton in a summoner's reach holds the might, then the might and the mana with a
  healer, the haste while hastened; a hero, a pool-less skeleton beside a healer alone, and a dead creature hold none;
  the fake canvas sees one icon per buff over the bar in order, the letter square where no icon is, nothing where
  `barOf` is null; a pointer on an icon draws the tooltip's lines with the figures; a press on an icon is taken and one
  beside it is not; the data's icons exist on disk.
- Later, if the owner wants the row inside the panel beside the portrait (as Dota's), that is an engine request (a
  buff field in `HeroPanel`'s status reading); this task does not need it.
