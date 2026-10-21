# NPC Brains and Quests on the Map — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The owner places an NPC on a map in the plugin, gives that one NPC a brain (stand, wander, patrol between
points, or guard and fight), and a quest (talk lines, title, text, steps, reward). The game plays it.

**Architecture:**
- A new game block, `Person`, describes one placed NPC by name: the creature it is (`Kind`), its brain and its talk.
- A map's `Npcs = [GateGuard 30 40]` may name a Person instead of a creature. The game spawns the Person's `Kind`
  there and drives it by its brain.
- A Person also stands in the story as an `Npc` of its own name. Talk, quest marks, quests and TALK/FOLLOW steps
  then work through the existing QuestLog with no engine change.
- Patrol points are zones: a drawn zone, or a radius-0 zone the plugin places as a marker pin.
- Blocks live in the map's own folder (`maps/<map>/<map>.duke`). The game reads every map folder's blocks at startup.
- The plugin gets a Point tool, a person inspector, a patrol path drawn on the map and a quest editor. All of them
  write into that `.duke`.

**Tech Stack:** Java 25 (game), the local duke-engine snapshot (read only), Kotlin and JS (duke-plugin), Gradle.

**Spec:** this document. The owner approved the design on 2026-10-21:
- Patrol points are both zones and quick marker points.
- Both peaceful brains and a fighting Guard brain.

## Global Constraints

- **Engine:** never edit or build in ../duke-engine. If something is needed, write it as an engine request in
  `docs/plan/2026-09-29-engine-requests.md` and message the duke-engine session. This plan needs none.
- **The world map:** never run `writeWorldMap`. `maps/world/world.map` is edited by hand from now on (e24f4aa).
- **Data:** the data files stay Uzbek-first. Any new player-facing string gets a row in `data/lang/en.duke`.
- **Fighting goes through the skill system and the existing brains:** a Guard fights with `MonsterBrain` and a
  `Leash`, never with a new ad-hoc combat module.
- **Testing:** the game runs headless only (no `gradlew run`). Run the suite with
  `bash <scratchpad>/suite.sh <scratchpad>/engine-e105 <log>`.
- **Commits:** one commit per task, never pushed. The MSI is built at the end; the old one is deleted first.
- **Old maps:** names in `Npcs` that are not Persons keep working exactly as today. No existing map or test changes
  behaviour.

## Review Focus

1. **A Person whose `Kind` is not a creature, or a patrol point that names no zone on its map.** The map refuses to
   load, with a message naming the Person and the missing word, as `StageCheck` does for other mistakes. → Task 2
   test.
2. **A patrol point the NPC cannot walk to** (behind a wall, across water). It walks as far as a path goes, waits, and
   goes on to the next point; it never freezes forever. → Task 3 test.
3. **A Guard drawn far off its patrol by a fight.** The leash brings it back, and the patrol resumes from the nearest
   point. → Task 4 test.
4. **A save and load while an NPC patrols, or after its quest is taken.** It loads at home and walks on. The quest
   stays taken or finished, as the QuestLog saves it. → Task 5 test.
5. **The same Person name in two maps' `.duke` files.** Startup fails, naming both files, rather than one Person
   silently replacing the other. → Task 1 test.

---

## Design

### The Person block (game data, in a map folder's `.duke`)

```
Person
  Name = GateGuard            ; how the map's Npcs and quests name it
  Kind = Guard                ; the Monster it is drawn and moves as
  Role = "Darvoza qo'riqchisi"  ; under its name; blank for none
  Talk = GateGuardHello       ; its Conversation; blank for none
  Radius = 30                 ; how near a hero comes before it talks
  Brain = PATROL              ; STAND | WANDER | PATROL | GUARD
  Wander = 40                 ; WANDER: how far from home it strolls (units)
  Points = [GateA, GateB]     ; PATROL/GUARD: zones of its map, walked in turn
  Mode = BACK_AND_FORTH       ; LOOP: A→B→C→A ; BACK_AND_FORTH: A→B→C→B→A
  Wait = 3                    ; seconds it stands at each point
  Leash = 12                  ; GUARD: how far, in cells, it leaves its point to fight
End
```

- **Brains:**
  - STAND keeps home and faces a hero who comes near.
  - WANDER is today's stroll.
  - PATROL walks its Points.
  - GUARD is PATROL that also fights. It stands on the hero's side with `MonsterBrain` and a `Leash`, so its `Kind`
    must be a fighter template (one with `MonsterBrain`).
- **Who can give quests:** a GUARD is not talked to. Quest givers are STAND, WANDER or PATROL.
- **The Person is also an Npc:** at load it is registered in the story as
  `Npc(Name, Talk, Radius, Wander, Role, route = [])`. Every existing lookup keyed by the NPC's name then works:
  QuestLog.talk, markOf, and TALK/FOLLOW steps.

### Quests

Quests use what exists: a `Conversation` with `Starts = [Quest]` and `Offer = Yes`, and a `Quest` with its Steps.
The last step's `OnDone` gives the experience and an item.

There is no gold on a quest's `Done`. Gold is paid by a `Trigger` with `When = QUEST_DONE`,
`Target = <quest>` and an `Act = GOLD` action, which already works. The plugin's quest editor writes all three blocks.

---

## Tasks

### Task 1: Every map folder's blocks are read at startup

**Files:**
- Modify: `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` — `load()` (around line 533) and `of(MapPackage)` (around line 547)
- Create: `src/main/resources/maps/talk/talk.duke`, an empty file with one comment line. `talk` is an existing small test map.
- Test: `src/test/java/uz/dukeengine/dungeon/content/MapBlocksTest.java`

**Interfaces:**
- Produces: `DungeonSettings.load()` = game data + the `.duke` blocks of every folder under `maps/`. A block name
  declared in two map folders throws `IllegalArgumentException` naming both folders.
- Produces: `static String mapBlocksText()`, the concatenated text of all maps' `.duke` files, so tests can check it.

- [ ] **Step 1: Write the failing tests in `MapBlocksTest`**
  - `aMapFoldersBlocksAreInTheGamesSettings()`:
    - A temp copy of `talk.duke` gets `Speaker\n  Name = MapBlockProbe\nEnd`.
    - `DungeonSettings.parse(Content.data() + "\n" + <that text>)` has `story().speaker("MapBlockProbe") != null`.
    - `DungeonSettings.mapBlocksText()` contains the shipped talk.duke's first line.
  - `oneNameInTwoMapsIsRefused()`: two texts each declaring `Person GateGuard` → `assertThrows(IllegalArgumentException)`, with the message containing both source names.
- [ ] **Step 2:** Run `./gradlew test --tests '*MapBlocksTest' -PdukeEngineDir=...`. Expect a FAIL (no `mapBlocksText`).
- [ ] **Step 3:** Implement `mapBlocksText()`. Read every `maps/*/` folder through `MapPackage.in(path).extrasText()`, the same reader `of(MapPackage)` uses. Then make `load()` parse `Content.data() + "\n" + mapBlocksText()`, and check duplicate names per source.
- [ ] **Step 4:** Run the test (PASS), then the full suite. Every existing test still passes, since no map has blocks today.
- [ ] **Step 5:** Commit with "Every map folder's blocks are read with the game's".

### Task 2: The Person block, spawned from a map's Npcs

**Files:**
- Create: `src/main/java/uz/dukeengine/dungeon/story/Person.java`
- Modify:
  - `src/main/java/uz/dukeengine/dungeon/content/Content.java` (TYPES: `named(Person.class)`)
  - `src/main/java/uz/dukeengine/dungeon/content/DungeonSettings.java` (collect persons, register their Npc)
  - `src/main/java/uz/dukeengine/dungeon/map/StaticMap.java:61` (`Npcs` link accepts a Monster or a Person)
  - `src/main/java/uz/dukeengine/dungeon/stage/StageCheck.java:131-150` (a Person name is valid; its Kind must be a Monster; its Points must be zones of the map)
  - `src/main/java/uz/dukeengine/dungeon/run/DungeonRun.java` `peopleOn` (around line 1351): spawn `person.kind()`'s template for a Person name
- Create the test map `src/main/resources/maps/people/`:
  - `people.map`: 30×30, one hero entrance, zones `PostA 5 5 0`, `PostB 20 5 0`, `Yard 12 20 6`, Npcs `[Walker 5 5, Sentry 12 20, Smith 8 25]`
  - `people.duke`: the Persons `Walker`, `Sentry`, `Smith`, plus their Conversation and Quest
- Test: `src/test/java/uz/dukeengine/dungeon/story/PersonTest.java`

**Interfaces:**
- Produces:
  ```java
  public record Person(String name, String kind, String role, String talk, float radius, Brain brain,
          float wander, List<String> points, Mode mode, float wait, float leash) {
      public enum Brain { STAND, WANDER, PATROL, GUARD }
      public enum Mode { LOOP, BACK_AND_FORTH }
      public static final Person DEFAULTS = new Person("", "", "", "", 30f, Brain.STAND, 0f, List.of(),
              Mode.LOOP, 2f, 12f);
      public Npc asNpc() { return new Npc(name, talk, radius, brain == Brain.WANDER ? wander : 0f, role, List.of()); }
  }
  ```
- Produces: `DungeonSettings.person(String name)` returns `Person` or `null`. `story().npc(personName)` returns its `asNpc()`.
- Produces: the private `DungeonRun.Person` record is renamed `Folk`, so that it does not clash. It gains a
  `String person` field: blank for an ordinary NPC, the Person's name otherwise.

- [ ] **Step 1: Write the failing tests in `PersonTest`.** Load with `Dungeon.newStageSession(Stages.load("people", SETTINGS), SETTINGS)`.
  - `aPersonIsDrawnAsItsKind()`: the object at Walker's cell has template `Villager` (the Kind in people.duke).
  - `aPersonIsAnNpcOfItsOwnName()`: `SETTINGS.story().npc("Smith").talk()` equals `"SmithHello"`.
  - `aPersonWhoseKindIsNoCreatureIsRefused()`: parsing a Person with `Kind = Nobody` fails, naming `Nobody`.
  - `aPatrolPointThatIsNoZoneIsRefused()`: a map with `Walker` whose `Points = [Nowhere]` fails `StageCheck`, naming `Nowhere`.
  - `ordinaryNpcsAreUnchanged()`: `Stages.load("talk", ...)` spawns `Commander` exactly as before.
- [ ] **Step 2:** Run `--tests '*PersonTest'`. Expect a FAIL.
- [ ] **Step 3:** Implement the record, TYPES, the settings collection and Npc registration, the StaticMap link, StageCheck and `peopleOn`.
- [ ] **Step 4:** Run `PersonTest`, then the full suite. Expect PASS.
- [ ] **Step 5:** Commit with "A Person: one placed NPC by name, drawn as its kind and known to the story by its name".

### Task 3: STAND, WANDER and PATROL

**Files:**
- Create: `src/main/java/uz/dukeengine/dungeon/ai/Patrol.java`, the pure next-point logic
- Modify: `DungeonRun.stroll` (around line 3030) to dispatch on the Person's brain
- Test: `src/test/java/uz/dukeengine/dungeon/ai/PatrolTest.java` (unit) and `PersonTest` (stage)

**Interfaces:**
- Produces:
  ```java
  public final class Patrol {
      public Patrol(int points, Person.Mode mode);
      /** The point to walk to after the one it stands at. */
      public int next();
      /** The point it is at now. */
      public int at();
      /** Picks up at the point nearest the given distances (one per point). */
      public void nearest(double[] distances);
  }
  ```
- Produces: each patrolling Folk keeps a `Patrol` and a `waitUntil` frame. At a point it waits
  `wait * LOGICFRAMES_PER_SECOND` frames, then `moveTo` the next point's middle.
  - If the path stops short (`MoveUpdate.stoppedShort()`, or no progress over 3 seconds), it counts as arrived and goes on.
  - It stands while a hero talks to it, as today.
  - Sleep is respected: an NPC 40 cells or more from the hero is not ticked (Dungeon.java:391). It picks up where it was when the hero comes back.

- [ ] **Step 1: Write the failing tests.**
  - `PatrolTest.loopGoesRoundAndRound()`: 3 points, LOOP, gives 1,2,0,1.
  - `PatrolTest.backAndForthTurnsAtTheEnds()`: 3 points gives 1,2,1,0,1.
  - `PatrolTest.twoPointsBackAndForthIsAToB()`: gives 1,0,1.
  - `PatrolTest.onePointStands()`: `next()` stays 0.
  - `PersonTest.aPatrollerWalksToTheNextPostWaitsAndComesBack()`:
    - Walker has `PATROL`, `[PostA, PostB]`, `BACK_AND_FORTH`, `Wait = 1`.
    - Turn the sleep off: `logic.setSleep(null)`.
    - Within 900 frames it comes within 1 cell of PostB, then back within 1 cell of PostA.
  - `PersonTest.aStanderStaysHome()`: Smith (STAND) is within 0.5 cell of home after 600 frames.
  - `PersonTest.aPatrollerWhosePointIsWalledOffGoesOn()`:
    - Point `PostC` is a zone inside a closed wall ring in `people.map`.
    - The Walker with `[PostA, PostC, PostB]` still reaches PostB within 1500 frames.
- [ ] **Step 2:** Run. Expect a FAIL.
- [ ] **Step 3:** Implement `Patrol` and the brain dispatch in `stroll`.
- [ ] **Step 4:** Run PatrolTest, PersonTest, then the full suite. Expect PASS.
- [ ] **Step 5:** Commit with "Brains for a Person: stand, wander, patrol its points in a loop or back and forth".

### Task 4: GUARD, which patrols and fights within its leash

**Files:**
- Modify:
  - `src/main/java/uz/dukeengine/dungeon/ai/Leash.java`: the home becomes settable (`void home(Coord3D)`)
  - `DungeonRun.peopleOn`: a GUARD is spawned on the hero's side (`seats.getFirst().player.getIndex()`), scaled like an ALLY (`Spawner.scale`), with `new Leash(it, home, leash * cell)`. It is a Folk, but not talkable.
  - `DungeonRun.stroll`: a GUARD walks its patrol only while its weapon has no target (`WeaponUpdate.getTarget() == null`) and it is not chasing. Each leg moves the Leash home to the leg's point.
- Test: `PersonTest`

**Interfaces:**
- Consumes: `Patrol` (Task 3) and `Leash(GameObject, Coord3D, float)`.

- [ ] **Step 1: Write the failing tests.** Sentry has `GUARD`, `[PostA, PostB]`, `Kind = Act1CommanderFighter`, `Leash = 6`.
  - `aGuardFightsAFoeWithinItsLeash()`:
    - A `Skeleton` is spawned on the dungeon side 3 cells from Sentry.
    - Within 300 frames the Skeleton has lost health from Sentry's blows (the Death killer is Sentry, or its health is down while nothing else is near).
  - `aGuardIgnoresAFoeBeyondItsLeash()`: a Skeleton 20 cells off and held still (`Battles.daze` each frame) is untouched after 300 frames, and Sentry stays within its leash of a post.
  - `aGuardDrawnOffComesBackAndPatrolsOn()`:
    - After killing a Skeleton placed at the leash edge, Sentry is again within 1 cell of PostA or PostB within 600 frames.
    - It then reaches the other post.
  - `aGuardIsNotTalkedTo()`: `run.talkTo(game, player, sentry.getId())` starts no conversation (`quests.said()` unchanged).
- [ ] **Step 2:** Run. Expect a FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run PersonTest, then the full suite. Expect PASS.
- [ ] **Step 5:** Commit with "A Guard: a Person who patrols on the hero's side and fights within its leash".

### Task 5: A Person's quest, end to end, through a save

**Files:**
- Modify: `src/main/resources/maps/people/people.duke`. Add:
  - `SmithHello`: a Conversation with `Offer = Yes`, `Starts = [SmithsOre]`
  - `SmithsOre`: a Quest whose steps are REACH `Yard`, KILL `Skeleton` ×2, TALK `Smith`; the last step's `OnDone` gives Experience 50 and an item
  - `SmithPays`: a Trigger with `When = QUEST_DONE`, `Target = SmithsOre`, and a `GOLD 30` action
- Modify: only what the test shows is missing. Expected: nothing in Java, since the Person is an Npc by name.
- Test: `src/test/java/uz/dukeengine/dungeon/story/PersonQuestTest.java`

- [ ] **Step 1: Write the failing tests.**
  - `aPersonGivesItsQuestAndShowsTheMark()`:
    - `markOf("Smith") == GIVE`.
    - Talk and accept (as `OfferTest` does), then `active()` holds `SmithsOre`.
  - `theQuestIsDoneAndPays()`:
    - Stand in Yard, kill 2 Skeletons, talk to Smith.
    - Then `finished()` holds `SmithsOre`, experience is +50, gold is +30 and the item is in the bag.
  - `aSaveMidQuestLoadsWithTheQuestTakenAndThePatrolGoingOn()`:
    - Save after accepting, then load (as `GraveyardTest.aGraveyardGoneIsNeverLaidAgain` does).
    - `SmithsOre` is active, and Walker moves within 300 frames.
- [ ] **Step 2:** Run. If it fails, fix the gap the failure names. Expect PASS.
- [ ] **Step 3:** Run the full suite. Expect PASS.
- [ ] **Step 4:** Commit with "A Person's quest, given, done, paid and kept through a save".

### Task 6 (plugin): Person names on the map and the Point tool

**Files (duke-plugin):**
- Modify:
  - `src/main/kotlin/.../map/MapScene.kt` (around line 41): an `Npcs` word that is a Person in the map's `.duke` is drawn as its `Kind`'s model, labelled with the Person's name
  - `src/main/resources/viewer/map.js`: zone drawing (around lines 1344-1460) draws a radius-0 zone as a pin; a **Point** tool beside **Zone** places one, asking its name
  - `DukeMapEditor.act` (around line 336): `"point-new"` goes to `MapEdits.addZone(name, x, y, 0)`
- Test: `src/test/kotlin/.../MapScenePersonTest.kt`, with testData map plus `.duke`
  - A Person word resolves to its Kind's model.
  - Point-new writes `Name x y 0` into `Zones`.
- [ ] **Steps:** write the failing tests, run them (FAIL), implement, run them (PASS), run `./gradlew test` in duke-plugin, then commit with "Persons drawn as their kind, and a Point tool for patrol stops".

### Task 7 (plugin): The person inspector

**Files (duke-plugin):**
- Modify:
  - `map/MapStoryPanel.kt` `showPerson` / `rebuild` (around lines 76-81): a form for the picked NPC with these fields:
    - Name
    - Kind (from the game's Monsters)
    - Role
    - Brain (STAND / WANDER / PATROL / GUARD)
    - Wander
    - Points: an ordered list; add by picking a zone from the map's zones, or "Add here", which places a Point and adds it
    - Mode
    - Wait
    - Leash
  - `DukeMapEditor.picked` (around line 616)
- Writes:
  - Into `maps/<map>/<map>.duke`, created if absent, through `DukeEdits` (undoable). The Person block is added or updated.
  - The first edit of an ordinary NPC turns it into a Person: its `Npcs` word becomes the new Name, and its Kind is the old word.
- Test: `PersonInspectorTest.kt`:
  - Picking an ordinary NPC and setting Brain = PATROL with two points writes a Person block and renames the Npcs word.
  - Undo restores both.
- [ ] **Steps:** test (FAIL), implement, test (PASS), commit with "The person inspector: brain, points, mode, wait, leash, written into the map's own blocks".

### Task 8 (plugin): The patrol path drawn

**Files (duke-plugin):**
- Modify: `viewer/map.js`. For each Person with Points, draw a line through the points' middles, closed for LOOP and open for BACK_AND_FORTH, numbered at each stop, in 3D and top-down. GUARD gets a red ring of its Leash round each point.
- Test: `MapScenePersonTest`: the scene's patrol list for a Person holds its points' cells in order.
- [ ] **Steps:** test (FAIL), implement, test (PASS), commit with "A patrol drawn on the map: its stops in order, a guard's leash round each".

### Task 9 (plugin): The quest editor

**Files (duke-plugin):**
- Modify: `map/MapStoryPanel.kt`, a **Quest** tab in the inspector for a non-GUARD Person:
  - Talk lines (who says each line: the Person or the hero, and the text)
  - Quest title and description
  - Steps: REACH zone / KILL kind ×N / COLLECT item ×N / TALK person / GIVE item, with dropdowns filled from the map's zones, the game's Monsters and LootItems, and the map's Persons
  - Each step's tracker text
  - Rewards: experience, item, gold
- Writes into the map's `.duke`:
  - a `Conversation <Name>Talk` (`Offer = Yes`, `Starts = [<Quest>]`)
  - the `Quest`, with the last step's `OnDone` holding experience and item
  - when gold > 0, a `Trigger <Quest>Pays`
  - the Person's `Talk`
- Test: `PersonQuestEditorTest.kt`:
  - Filling the form writes the three blocks, in the same shape as `maps/people/people.duke`'s Smith (compare the parsed records).
  - Editing a step rewrites only that step's item (`DukeEdits.setItem`).
- [ ] **Steps:** test (FAIL), implement, test (PASS), commit with "The quest editor: talk lines, title, steps and rewards for a Person".

### Task 10: Ship

- [ ] Add English rows to `data/lang/en.duke` for every new Uzbek string (the people map's lines, any new UI word).
- [ ] Run the full game suite: 0 failures. Run the full plugin suite: 0 failures.
- [ ] Delete the old MSI, then build `./gradlew packageInstaller -PinstallerVersion=1.0.66 -PdukeEngineDir=<engine-e105>`.
- [ ] Build the plugin zip (`./gradlew buildPlugin` in duke-plugin) for the owner to reinstall.
- [ ] Commit with "NPC brains and quests: shipped in 1.0.66".
