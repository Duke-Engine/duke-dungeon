# Auras and the haste as pushed buffs — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. This plan states each task's
> requirements, tests and files; the implementer writes the code test-first (the code is not pre-written here).

**Goal:** An aura's bearer lends its aura: every `AuraPulseFrames` it gives everyone of its own side within its
`Radius` (through walls, never through the keep's shut gate) a **buff** that lasts `BuffLifeFrames`; a creature's
figures — its might, its mana's trickle, its thirst — read the buffs it holds, not a search of its neighbours at every
asking. The summoner's haste is a buff on the same list. One list of what is on a creature, for the figures, the
light-blue marks and the HUD.

**Why:** the owner, 2026-10-04/05, after learning how auras work today ("pull": the receiver searches for bearers at
every blow): every passive aura and every lasting skill should be pushed — "aurasi bor unitda shu skill bo'ladi
atrofidagi unitlarni har 10 ta kadrda olib aylanib chiqsak bo'ladi" — cheaper with many creatures (a bearer looks round
once a pulse, a receiver reads its own list), and the ground for timed buffs, their remaining time on the HUD, potions
and dispels later. He first meant to write it himself as lessons (`docs/plan/2026-10-04-push-buffs-darslar.md`), then
asked for it to be done: "o'zing o'tkazib qo'ya qol".

**Spec:** this file; piece 5's spec `docs/plan/2026-09-30-haste-and-auras.md` for what each aura and the haste do (their
figures, who they reach, the strongest-not-sum rule) — unchanged, except *when* a change shows (see "Latency").

## Global Constraints

- Lock-step: bearers pulse on the frames their object id falls on (`frame % pulse == floorMod(id, pulse)`, as the
  marks are staggered); a receiver's figure is the largest value among its live buffs of that kind (order-free);
  buffs live in the receiver's `SkillBook` as plain state, iterated in insertion order; nothing asks a clock or walks a
  hash. (A book's state is not in the world's checksum today — the haste's frames are not — and stays so; when the
  game gains saving, the buffs must be saved.)
- Names and numbers in data: `AuraPulseFrames` (10) and `BuffLifeFrames` (15) in `world.duke`'s Combat block, with
  load rules (each ≥ 1; the life longer than the pulse, so a buff never lapses between two pulses).
- Everything a skill does stays in the skill system (the owner's rule): the buff list is the `SkillBook`'s; no new
  module.
- The rules auras keep: same side, living, the bearer's level has opened the skill, within the skill's `Radius`
  middle to middle, **through walls** (the owner, 2026-10-03), never across the keep's shut gate (`Seal.parts`), the
  bearer itself among them; the strongest of a kind, never the sum; kinds add; a creature's own `LIFESTEAL` and the
  lifesteal aura add; a `MANA_AURA` changes nothing on a creature without a pool. Heroes are never given an aura.
- **Latency** (accepted by the owner's design): a creature stepping into reach, or a bearer appearing, is lent within
  one pulse; stepping out, or its bearer falling, it keeps the buff at most `BuffLifeFrames`.
- `../duke-engine` is never edited. Every gradle command passes
  `-PdukeEngineDir=<scratchpad>/engine-e15` (engine 668045f0, 0.8.0).
- Each task committed on its own: subject, blank line, `Co-Authored-By: <the model that did the work>
  <noreply@anthropic.com>`.

---

### Task 1: The settings, the `Buff` record and the book's list — no behaviour change

**Files:** `world/Combat.java` (+ `auraPulseFrames`, `buffLifeFrames`, `DEFAULTS` 10 and 15), `data/world/world.duke`
(the two lines with a note), `content/DungeonSettings.java` (load rules), a new `skill/Buff.java`, `skill/SkillBook.java`
(the list and its methods), tests in `content/DungeonSettingsTest` and a new `skill/BuffTest` (or in `AuraTest`).

- `record Buff(SkillEffect kind, int source, int value, int until)` — `source` the giver's object id, `until` the last
  frame it holds.
- The book: `give(SkillEffect kind, int source, int value, int until)` keeps **one buff per (kind, source)** — a give
  from the same source replaces its own; `buffOf(SkillEffect kind, int frame)` answers the largest `value` among the
  buffs of that kind still holding at `frame` (0 for none) — so a stronger lender leaving hands over to a weaker one
  still in reach with no gap; at each `update`, buffs past `until` are dropped.
- Tests: the settings read from a block and the shipped values; the load rules refuse by message; give/buffOf: two
  sources keep the larger, one source replaces itself, a buff past its frame counts for nothing and is dropped. The
  whole suite unchanged otherwise (nothing reads the list yet).

### Task 2: Bearers pulse, and the figures read the buffs

**Files:** `skill/SkillBook.java` (the pulse in `update`; `damageMultiplier`, `regenerate`, `drink`, `auraOn`,
`aurasOn` read the list), the tests that pinned the immediate pull (`AuraTest`, `SealTest`'s aura tests,
`BuffScreenTest`, `SkillLookTest` if it counts marks), and the notes (SkillBook, SkillEffect, the piece-5 spec's
"how it is asked", the mages' data notes).

- On its pulse frames a living bearer, for each aura skill its level has opened, gives everyone of its side within the
  skill's `Radius` (itself included; through walls; not across `Seal.parts`; living) a buff of that kind and worth
  (`BoostPercent`, or `ManaRegen` for the mana) lasting `BuffLifeFrames`.
- `auraOn(creature, kind)` becomes the creature's `buffOf(kind, now)` (same name and meaning, no search); `aurasOn`
  answers the kinds it holds (a `MANA_AURA` only with a pool, as now); the might, the mana's trickle and the thirst ask
  it as now. The search over neighbours (`objectsInRange` per asking) and `auraReach` go.
- Tests: within one pulse of meeting a bearer a skeleton hits half as hard again; through a wall too; never across the
  shut gate (SealTest); 61 away nothing; two summoners still half as hard again; its bearer killed, the buff is gone
  within `BuffLifeFrames`; a healer's mana and a Revenant's thirst likewise; the marks and the HUD follow. Existing
  tests that asked a figure on the frame a room was made run a pulse first — a helper, said once.

### Task 3: The haste is a buff, and the old reach goes

**Files:** `skill/SkillBook.java` (the haste's cast gives a `HASTE` buff; `rateOfFireMultiplier` reads it;
`hasteFrames`/`hastePercent` and their getters go), `BuffScreen.java` (the haste's figure and seconds left from the
buff), `HasteTest`, `BuffScreenTest`; notes.

- A haste cast on one gives it a `HASTE` buff from the caster, worth the skill's percent, holding for its
  `DurationFrames`; a second haste refreshes it (same source replaces; another summoner's stronger or longer one wins
  by the list's rule). The weapon's waits read `buffOf(HASTE)` as they read `hastePercent` today.
- The HUD's row reads the list: an aura's picture while its buff holds, the haste's with its seconds left.
- Tests: the existing haste tests (17 frames between blows while hastened, 30 after; refresh; whom it picks) hold on
  the buff; the HUD shows the haste's seconds counting down.

---

Final review of the whole branch, then merge.
