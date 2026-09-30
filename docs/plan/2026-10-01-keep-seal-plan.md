# The shut gate seals the keep — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. This plan states each task's
> requirements, tests and files; the implementer writes the code test-first (the code is not pre-written here).

**Goal:** While the keep's gate stands, nothing crosses between the court and the rest of the floor — no blow, shot,
burst, falling meteor, dash, blink or rift — so the mission (clear outside → key → gate → boss) cannot be skipped; a
key never outlives its floor; the loader refuses a keep whose key could not work; and a hero sent on a long walk
reaches where he was sent.

**Why:** the final reviews of pieces 2 and 3 (2026-10-01) found that (1) a ranged hero on the threshold can hurt the
boss through the shut gate (sight and shots see through it), (2) every hero can dash or blink from the threshold into
the court (30 of 30 probes: `SkillBook.dashEnd` and `somewhereHeCanStand` pass over walls by design), (3) a summoner
behind the gate can open a rift on the threshold (`Summoning.open` asks `SightLine.clear`, which sees through the
gate), (4) a key carried past its floor opens the next floor's gate early and confuses the tracker, and (5) on 13 of
40 real floors one long walk order from the way in does not bring a hero to the gate (a pre-existing movement stall).

**Spec:** the key to the keep, `docs/plan/2026-09-30-key-to-the-keep.md` ("The gate stays shut", "The mission"); this
plan is its follow-up.

## Global Constraints

- Lock-step: every rule is a pure function of simulation state — the keep's cells (`Keep.holds`), whether its gate
  still stands (the gate object, not opened) and positions — asked on the simulation thread, in a fixed order; no clock,
  no hash iteration.
- One rule, asked everywhere: a single helper answers "are these two points sealed from each other" (one inside the
  keep, one outside, the gate still standing); every crossing path asks it rather than repeating the test.
- Names and numbers from data; nothing new is compiled in. The owner's rule: everything a skill does stays in the
  skill system — the seal is a property of the keep, asked by the skills, not a new module.
- `../duke-engine` is never edited; an engine need goes into `docs/plan/2026-09-29-engine-requests.md`.
- Every gradle command passes `-PdukeEngineDir=<the worktree's engine snapshot>`.
- Each task committed on its own: subject, blank line, `Co-Authored-By: <the model that did the work>
  <noreply@anthropic.com>`.

---

### Task 1: A key never outlives its floor, and the loader refuses a keep whose key could not work

**Files:** `run/DungeonRun.java` (`onTheFloor`, before `Mission.of`), `content/DungeonSettings.java` (`validate`),
`run/GateUpdate.java` (`Data`), tests in `run/MissionTest.java`, `content/KeepSettingsTest.java`, `run/GateTest.java`.

- When a floor is laid, every `KEY`-kind item is taken out of every seat's bag (a key belongs to the floor it was
  found on). Test: a hero carrying a key into the next floor has none there; that floor's tracker counts its own
  outside, and its gate stays shut.
- Load rules: every `Kind = KEY` item has `Use = UNLOCK`; a keep (a `Keep` block) needs at least one `KEY` item. Tests:
  each refused with a message naming the rule; the shipped files load.
- The gate's two lines (`WithoutKeyWord`, `WithKeyWord`) may not contain `,` or `|` (they travel in the panel line as
  `FullWord` and `NoUseWord` do). Test: refused at load.

### Task 2: Nothing that hurts crosses the shut gate

**Files:** a seal helper beside the keep (e.g. in `run/` or `gen/Keep` with the world's gate state), asked from
`combat/Swing.java` (`launch`: a melee blow across the seal is swallowed — the launcher claims it and lands nothing),
`combat/ArrowUpdate.java` (a shot whose flight crosses the seal is spent on the gate; a burst's splash never reaches
across it), `skill/SkillBook.java` (every place a skill's damage or heal lands on a creature), `combat/FallingUpdate.java`
(a meteor's blast); tests in a new `run/SealTest.java`.

- While the gate stands, a creature inside the keep and one outside cannot hurt (or mend) each other by any path;
  after `open()`, everything reaches as before.
- Tests (real fights): a Rogue on the threshold shooting at the boss does no damage while the gate stands and does
  after it opens; the boss's and the corner mages' shots do not reach a hero outside; a hero's meteor aimed into the
  court hurts nobody inside while the gate stands; a burst outside the gate catches nobody inside; heals do not cross.

### Task 3: Neither a dash, a blink nor a rift crosses the shut gate

**Files:** `skill/SkillBook.java` (`dashEnd`, `somewhereHeCanStand` or their callers), `skill/Summoning.java`
(`open`/`spots`), tests in `run/SealTest.java`.

- While the gate stands, a dash or blink that would land on the other side of the seal is refused (cooldown untouched,
  as for a landing in stone) or cut short on its own side — pick one and say which; a rift's spot on the other side of
  the seal is not opened.
- Tests: from the threshold's middle cell toward the court, on seeds 21–30 of the descent, the Rogue's E dash, the
  Knight's W dash and the Mage's E blink never land in the court while the gate stands, and do after it opens; a
  summoner standing behind the gate opens no rift outside it.

### Task 4: A hero sent on a long walk gets there

**Files:** found by investigation (the probe points at `ai/HeroBrain.mindTheWayOnHisErrand` → `WayAhead.stillShut`,
and walks that end silently short); tests in the matching test class.

- Debug first (superpowers:systematic-debugging): reproduce the probe — a hero ordered at frame 2 from the way in to the
  gate on seeds 4, 9, 24, 26, 35, 36 stands idle for ever with the errand open and no goal; on 6, 11, 12, 17, 30, 31 the
  walk ends silently 178–387 away; on 10 it is still walking at frame 6000; seed 4 depends on the frame the order is
  given. Find the root cause before changing anything.
- Fix at the root; a hero held for an obstruction goes on when the way clears or gives up with a reason, never idles
  for ever with an errand open.
- Tests: the seeds above reach the gate with one order (or end with a stated reason), deterministically.
