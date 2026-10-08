# Dialogue, talking people and quests

The campaign's "What exists and what is new" items 3 (dialogue) and 4 (people who talk), and the quest system the
whole campaign hangs on — the first of the foundations in its "Order" 1. Everything here is the game's own today;
the dialogue box, a person who talks and the quest state machine are mechanisms every RPG needs, so they lift to the
engine's `rpg` module (E8's split) once the game has played them — game first, lift later. Nothing here needed the
engine: the neutral side, pausing and painting over the world are all there already.

Choices the owner left open are marked **default**; they hold until he says otherwise.

## What the player sees

- **People.** A person stands where the map puts them: not an enemy, nobody's target, on a side of their own. Over
  their head, a gold **!** when they have a quest to give, a gold **?** when a quest of the hero's waits on them.
- **Talking.** The hero walks up to them (within the person's `Radius`) and the conversation plays; or the player
  right-clicks them (the pointer becomes a speech bubble) and the hero walks over and talks. Walking up plays only
  what is new — a first greeting, a quest handed in; a right-click plays the greeting again whenever he likes.
- **The box.** At the bottom of the screen: the speaker's picture in a frame (or the first letter of their name
  where they have none), their name in gold, the line, and under it `Davom etish uchun bosing / Space`. A click,
  Space or Enter is the next line; Esc skips the rest of the conversation. No choices.
- **The world waits.** Played alone, the world is paused while a conversation is on the screen (the engine's own
  pause, the one Esc's menu uses, set from the window). In a party it is **not** paused — pausing one machine of a
  lock-step game stalls them all, and the others would wait on a box they cannot close — so the box is only shown,
  on every machine of the party, and the fight goes on under it. A party shares its quests and its story.
- **The tracker.** The compact list at the right edge lists every quest the run has going, under the floor's own
  mission: the MAIN ones (◆ gold) first — the floor's mission (key → gate → boss) the first of them — then the SIDE
  ones (◇), each in the order it was started, each with its current step (`Skeletlarni qiring 3/5`).

## The data

All of it is `.duke` blocks in the game's files (`data/story/*.duke`, listed in `data/game.duke`). Every word the
player reads is in data.

```
; Who may speak: a name the lines use, what the box calls them, and their picture.
Speaker
  Name = Commander
  DisplayName = Qo'mondon Laylo
  ; A picture from the resource root, framed; none draws the first letter of the name.
  Picture =
End

Conversation
  Name = CommanderGreets
  Lines = [
    Line
      Speaker = Commander
      Text = "Nihoyat keldingiz, qahramon."
    End,
    Line
      Speaker = Hero
      Text = "Men qayerdaman?"
    End
  ]
  ; Quests this conversation hands out, started as it plays.
  Starts = [WallSkeletons, LostLocket]
End

; A person: the Monster template they are (a body and a look, Weight 0 so no floor ever draws one), what they say
; when talked to, and how near the hero comes before they speak.
Npc
  Name = Commander
  Talk = CommanderGreets
  Radius = 30
End

Quest
  Name = WallSkeletons
  Kind = MAIN                      ; MAIN or SIDE
  Title = "Devordagi skeletlar"
  Steps = [
    Step
      Goal = KILL                  ; REACH, KILL, COLLECT, TALK or GIVE
      Target = Skeleton            ; a Monster template
      Count = 5
      Text = "Skeletlarni qiring %d/%d"
    End,
    Step
      Goal = TALK
      Target = Commander           ; an Npc
      Text = "Qo'mondonga qayting"
      ; What finishing this step does: a conversation, a reward, the next quest.
      OnDone = Done
        Says = CommanderThanks
        Experience = 120
        Item = HealthCharm         ; a LootItem, into the bag of the hero who finished it
        Starts =                   ; another quest
      End
    End
  ]
End
```

The goals, and the one event each advances on — and only on it:

| Goal | Target | Done when |
|---|---|---|
| `REACH` | a zone of the map (below) | a hero stands in it — positions are looked at every `CheckFrames` |
| `KILL` | a Monster template, `Count` of them | that many of it die after the step began (a unique boss's template is "a named one") |
| `COLLECT` | a LootItem, `Count` of them | the heroes' bags hold that many between them (a joined one counts as what joined into it) |
| `TALK` | an Npc | a hero talks to them |
| `GIVE` | an Npc, with `Item` (×`Count`, default 1) | a hero who holds it talks to them; it leaves his bag |

`Text` is the tracker's line; a `%d/%d` in it is the count so far and the count asked (KILL and COLLECT).

A quest starts from a conversation's `Starts`, a map's `Quests` list, or a step's `OnDone.Starts`. Starting one that
is already going or already finished does nothing. A quest's last step done, it is finished and leaves the tracker.

**Load rules** — refused when the files are read, with a message naming the block:
- a line's `Speaker` that no `Speaker` block names (`Hero` is always known: it is whoever the player plays, by his
  `DisplayName`);
- a conversation named by an `Npc`, a `Says` or a map that no `Conversation` block is; an empty conversation;
- a quest named by `Starts` that no `Quest` is; a quest with no steps;
- a step whose `Goal` is not one of the five (the reader's own message: "'Goal' is one of [REACH, …], not 'FLY'");
- a `KILL` target that is no Monster, a `TALK`/`GIVE` target that is no `Npc`, a `COLLECT`/`GIVE`/reward `Item`
  that is no `LootItem`, a `Count` below one;
- an `Npc` whose `Name` is no Monster template.

A quest item is a `LootItem` of `Kind = QUEST`: it gives no figure and is never dropped (`Weight = 0`); it is laid
on a map, given as a reward, or handed in.

The window's words and looks are the `Dialogue` block (`data/story/story.duke`): the hint under the line, the two
marks' pictures and colour, the context-order cursor, and `CheckFrames`.

### On a map

A map lists, beside its monsters and props, as plain `kind x y` lines and `name x y …` zones so the plugin editor
can show them later:

```
  ; People: an Npc's name, then the cell they stand in.
  Npcs = [Commander 9 27]
  ; Named places, in cells: name x y width height (a rectangle), or name x y radius (a circle round a cell).
  Zones = [Gate 20 5 4 3, Well 30 12 3]
  ; Things lying on the floor: a LootItem, then the cell.
  Items = [LostLocket 33 20]
  ; The quests this map starts with.
  Quests = []
```

A map file writes them only when it has some, so every map drawn before reads and writes back unchanged.

## How it runs

- **One quest log a run**, the run's own, in the simulation: plain records (`QuestLog.Active(quest, step, count)` in
  the order the quests were started; the finished ones' names; the conversations heard), iterated only as lists —
  no hash order — so it is the same on every machine, and a save writes it as it is. A run that ends
  (lost or won) starts its log again, as it starts the hero again.
- **Saved** with the run (autosave, slots, on leaving: docs/plan/2026-10-08-saving.md) and restored on load before the
  floor is laid, so the map's starting quests are not started again and nothing heard is said again; a save from
  before quests were saved loads with the map's starting quests.
- **Events:** a death is heard from the engine's own `GameLogic.onDied` (KILL); bags, zones and the people's radii are
  looked at every `CheckFrames` (COLLECT, REACH, walking up); a right-click on a person is this game's order `Talk`
  (`TalkTo`), heard as every order is, on the same frame on every machine.
- **Talking to a person** first hands in: the first quest, in start order, whose step is `TALK` to them, or `GIVE` to
  them with the item in the talker's bag. Failing that, their `Talk` — if it has not been heard yet, or the player
  asked by a right-click.
- **A conversation played** is added to the run's list of what was said; the window shows that list one
  conversation at a time. The simulation never waits for the box.
- **People** are spawned on the engine's neutral side (player 0), which no hero and no monster is the enemy of: no
  body, so nothing targets them and they never die.

## The demo

`maps/talk` — **"Qo'mondon"**, a small map of its own (`maps/first/first.map` is hand-sculpted and untouched): the hero
comes in beside the commander (**default** stand-in model: the KayKit ranger, `models/heroes/ranger.glb`, which no
hero wears any more). Walking up, she greets him in four lines and hands him a MAIN quest, *Devordagi skeletlar* (kill
5 skeletons, then come back) and a SIDE one, *Yo'qolgan tumor* (find her lost locket in the far room and give it to
her). Handing each in plays her thanks and pays experience (and, for the locket, a charm). Its boss is at the far
end, as every map's is: killing it still wins the map.

Try it: `./gradlew run --args="--map=talk"`, or Play → stages → Qo'mondon.

## Deferred

- **Choices** in a conversation, and conversations that differ by what the hero has done.
- **Cutscenes**: the camera on a path, letterbox, scripted moves (the hero's landing, the commander's leap), the
  world stopped for a beat by the story rather than by the box.
- **Story triggers** beyond a REACH step: a line said on entering a zone, a chapter turned by a boss falling.
- KILL of one *placed* creature by a name of its own, and REACH of a point not written as a zone.
- Gold: the game has no money yet; a reward is experience and an item.
- The tracker keeps no finished quest step on screen for a moment, as the floor's mission does.
- A merchant and the city.
- The right-click rule: `Bag.show` repeats the bag's pickup and gate cases to add `Talk`, until the engine lets a game
  add a rule of its own (E20 in docs/plan/2026-09-29-engine-requests.md).
- Lifting the box, the person and the log into the engine's `rpg` module.
