# Story scripting and simple cutscenes

The campaign's "What exists and what is new" items 1 (story scripting) and 2 (cutscenes), built on the dialogue,
people and quests of `docs/plan/2026-10-08-dialogue-and-quests.md`. Game first, lift later: the trigger runner and the
cutscene runner are mechanisms every story game needs and lift to the engine's `rpg` module once played. Nothing
here needed the engine: it already lets a game drive the camera from the simulation (`DukeGame.camera()`, a
`GameCamera` moved over so many logic frames, stepped with the world, not checksummed) and paint over the world.

Choices the owner left open are marked **default**.

## Triggers

A trigger is a block in the game's data: **when** something happens, do its **actions**, in order. Each fires
once, unless it says `Repeat = Yes`. They run in the simulation, on the frame the event is heard, in file order, so a
party sees the same story on every machine; what has fired is saved with the run, as the quest log is.

```
Trigger
  Name = Ambush
  Map = talk                 ; only on this map; blank is every floor
  When = QUEST_DONE          ; what it waits for
  Target = WallSkeletons     ; what of
  Delay = 0                  ; frames after the event before it acts
  Repeat = No
  Actions = [
    Action
      Act = SAY
      Target = AmbushLine
    End,
    Action
      Act = SPAWN
      Target = Skeleton
      Count = 3
      Zone = Ambush
    End
  ]
End
```

| When | Target | Fires when |
|---|---|---|
| `MAP_START` | — | the map's floor is laid (a load lays it again, but a trigger that fired stays fired) |
| `ZONE` | a zone of the map | a hero comes into it — from outside it: a Repeat fires again only after every hero has left it |
| `DIED` | a Monster template | one of it dies — a unique boss's template is "a named creature" |
| `QUEST_STARTED` | a quest | it is started (by a conversation, a map, a step or a trigger; not by a load) |
| `QUEST_DONE` | a quest | its last step is done |
| `STEP_DONE` | a quest, with `Step` (1 = its first) | that step is done |
| `HEARD` | a conversation | it is played (**default**: "a conversation ends" is when the simulation plays it; alone the world is paused until the box is closed, so what follows waits for it) |
| `AFTER` | another trigger | that one fires — with `Delay`, the timer |
| `FLAG` | a flag | the flag is set |

| Act | Target | With | Does |
|---|---|---|---|
| `SAY` | a conversation | | plays it (its `Starts` start) |
| `START_QUEST` | a quest | | starts it |
| `FINISH_QUEST` | a quest | | finishes it at once, without its rewards |
| `SPAWN` | a Monster template | `Count`, `Zone` | that many at the zone, on the dungeon's side at the floor's depth; a person (an Npc) on the neutral side, and they talk |
| `WALK` | an Npc | `Zone` | the person walks to the zone's middle |
| `OPEN_GATE` | — | | opens the floor's keep gate |
| `GIVE` | a LootItem | | into the first hero's bag |
| `TAKE` | a LootItem | | out of whichever hero's bag holds it |
| `FLAG` | a flag name | | sets it |
| `MAP` | a map | | the next chapter: the heroes go on to that map with all they have, as down a floor |
| `CUTSCENE` | a cutscene | | plays it |

Every name is checked when the files are read (an unknown When or Act is the reader's own message; a conversation,
quest, Monster, Npc, LootItem, cutscene or trigger that does not exist is refused, naming the trigger). A zone and a
map are checked when that map is loaded.

## Cutscenes

```
Cutscene
  Name = ChestReveal
  Shots = [
    Shot
      Zone = Chest       ; the camera flies to the zone's middle...
      Frames = 60        ; ...over this many frames (2 s)
      Hold = 30          ; and stays this long once there
    End,
    Shot
      Says = CommanderSeesChest   ; a conversation in the dialogue box
      Hold = 15
    End
  ]
End
```

- While one plays: black bars across the top and bottom of the screen (the letterbox), the camera the cutscene's, and
  the player's input taken — no orders, no keys — but Space and Esc. The world goes on (**default**: monsters do not
  freeze for a camera pan; a line said pauses it alone, as every conversation does).
- **Esc** skips the rest of the cutscene, its lines with it; **Space** (or a click) is the next line while a line is
  shown, and skips the cutscene when none is. A skip is this game's order `skip`, heard on every machine: in a party
  any player's skip skips it for all (**default**), and nobody is paused.
- At its end the camera is given back and put over the first hero.
- **A cutscene never starts while a line is on a screen.** It queues behind every conversation said before it was
  asked for, and begins once every player's machine has shown those to the end (or had them skipped) — each window
  says so as this game's order `read` — or, at the latest, after the `Dialogue` block's `ReadWaitFrames` (**default**
  300) of the world going on: for a machine with no window, or a player away from his keys. Alone the world is
  paused while a line is shown, so that wait does not run then. (The owner's playtest: the locket's cutscene,
  begun by her greeting, put its black bars over the greeting.)
- A shot's camera flight is linear (the engine's `GameCamera.moveTo`); turning, pitch and zoom are not used yet.
- A cutscene is not saved: a load mid-cutscene comes back without it.

## On a map

Zones are the map's `Zones` (the dialogue spec). Triggers name their map with `Map`; the demo's live in
`data/story/story.duke`.

## The demo, on `maps/talk`

- Coming through the corridor into the far hall (zone `HallDoor`), a cutscene: the camera flies to the chest in the
  far corner (zone `Chest`) and the commander says one line about her locket.
- Her MAIN quest handed in, an ambush: she says a line, and three skeletons rise at zone `Ambush` in the first room.

## Winning a story map

A map with a story — people, zones, things laid or quests of its own, or a trigger naming it (the talk map, the open
world) — is won when its boss falls and **stays won** (the owner's playtest, 2026-10-09: it used to start again by
itself after `VictoryFrames`, as the descent and a plain stage still do). The banner holds; the story goes on, so a
trigger on the boss's death (`DIED`, its template) may say what follows — `MAP` takes the heroes to the next chapter
and the run plays on there. With no such trigger the player leaves by the menu.

## Saved

The trigger log's state goes into the save beside the quests: the triggers fired, the flags set, the timers waiting
(each with its frames left), and which zones had a hero in them. A save from before it was saved loads with none
fired, as a new run.

## Deferred

- Scripted moves of the heroes and knock-backs (the hero's landing, the commander's leap); WALK moves people only.
- Camera turns, zoom and pitch in a shot; easing.
- Conditions beyond one event (AND/OR of flags), and a trigger's actions waiting on each other inside one trigger
  (use `AFTER` with a `Delay`).
- Saving a cutscene in progress.
