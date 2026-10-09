# The medallion and the turning

The campaign's "What exists and what is new" item 6 (`2026-10-01-campaign.md`): the queen's medallion, which drinks
the soul of every enemy its wearer kills and pours that strength into him, and the turning, where at the castle's boss
it drinks him whole. Built on the worn slots (E25), the story's triggers and cutscenes
(`2026-10-08-story-scripting.md`), people (`2026-10-08-dialogue-and-quests.md`) and the save (`2026-10-08-saving.md`).
Every number and word is in `data/story/medallion.duke`; choices the owner left open are marked **default**.

## The medallion

- A `LootItem` (`Medallion`, "Malikaning medalyoni") worn in the **Tumor** slot. `Kind = QUEST`, so it is never
  dropped (Weight 0) and never sold. Only a hero carries a bag, so only a hero can wear it; and only a kill by a hero
  (a `Hero` template) who **wears** it — not one carrying it in the backpack — feeds it. A monster, or anything else
  of his side, never does.
- Its passive, **"Jon yutish"**: every enemy (the dungeon's side) its wearer kills is one soul, counted on the hero
  (his seat in the run, beside his gold) and saved with the run. Every `SoulsPerStep` souls (**default** 10) make his
  blows and skills `PercentPerStep` harder (**default** 1 %), up to `MaxPercent` (**default** 20 %, so 200 souls).
- **In the skill system:** the bonus is lent to the hero through his own `SkillBook` — a `DAMAGE_AURA` buff of his
  own, renewed each frame while he wears it — so the engine's `damageMultiplier` multiplies it into every blow and
  skill as it does an aura's. The buff row over his bar shows it as `+3% Zarba`. What the engine cannot hold yet — a
  passive that counts its bearer's kills, carried by a worn item — is **E27**; once it lands, "Jon yutish" becomes a
  `Skill` block on the item and the game's counting goes.
- **Its card** (the bag's pointer card): `Jon yutish`, `Jonlar: 37`, `+3% Zarba`.
- **Who killed:** a blow that names its dealer (the Knight's swing) is his; one that names nobody — the rpg module's
  arrows and skills say no killer yet (E27) — is the hero's whose experience rose for it that frame.

## A soul, seen

When its wearer kills, a `SoulWisp` rises from the corpse and flies into him (`WispSpeed`, **default** 4 world units a
frame) — a `Projectile` template with no model whose `Effect` (`SoulWisp`: a pale cold core, a trail of motes, a
little light) is its body, as the fireball's is. Moved by the run on the simulation's thread, gone when it reaches
him or he is gone.

## The turning

A story action any trigger may use: `Act = CURSE`, `Target = <an Npc>`.

```
Trigger
  Name = Turning
  Map = medalyon
  When = ZONE
  Target = Altar
  Actions = [
    Action
      Act = CURSE
      Target = Queen
    End,
    Action
      Act = CUTSCENE
      Target = Turned
    End
  ]
End
```

It does, in order:

1. **The medallion leaves his bag** (whichever hero holds it; the first hero if none does) and is **given** to the
   person named: the run keeps who holds it, and her `GivenLook` (**default** the same `SoulWisp` glow, for
   `GivenLookFrames` 90) plays on her as it appears in her hand.
2. **His look darkens:** the hero holds the condition word `CursedWord` (**default** `CURSED`), and every hero's look
   carries the engine's word tint for it (`Visuals.UnitVisual.tint(words, r, g, b, ease)`): `CursedRed/Green/Blue`
   (**default** −0.30, −0.40, −0.20, added to his colour) eased in over `CursedEaseFrames` (60). No engine request was
   needed: `GameObject.setCondition` and word tints were already there.
3. **His side changes** to the monsters' (`GameObject.setPlayerIndex`, the dungeon's player): he is hostile, and his
   player commands no hero any more — the panel loses him, orders go nowhere.
4. **The act ends:** the run is won and stays won (a story map), the banner says `TurnedWord` ("Qahramon
   la'natlandi"), no checkpoint is written from it, and the story goes on — a `MAP` in a later trigger takes the
   party on to the next chapter (for Act 2, **default**: whoever builds it decides what the cursed hero is there).

## Saved

`Souls = 37` in the save, and `GivenTo = Queen` once the turning gave it away. Whether he holds it is the bag's own
(the item in its place). A hero loaded with `GivenTo` set is cursed: his look is dark again. His side is not turned
again on load (the turning's run is not saved as a checkpoint; only a save written later, on another map, carries it).
A save without these lines loads with no souls and nothing given.

## The demo: `maps/medalyon`

The queen (a stand-in look: the KayKit mage tinted violet) on her throne by the way in; walking up to her plays her
greeting, whose `HEARD` trigger gives the medallion (worn at once: the Tumor slot is empty). The hall beyond the
corridor holds four skeletons whose souls it drinks. At the hall's far end, the zone `Altar` fires the turning and the
cutscene `Turned`: the camera on him as the dark comes over him, then to the throne, where she says it is back in her
hand. A skeleton in the room below the hall is the map's boss. `maps/first` and the talk map are untouched.

Try it: `./gradlew run --args="--map=medalyon"`.

## Deferred

- The turning at the castle's boss itself (a `DIED` trigger on the boss calling `CURSE`) — the demo uses a zone.
- A condition on a trigger (the altar before the medallion is given turns a hero who never had it).
- What the cursed hero does on his new side (Act 2: the final fight's).
- The medallion's own model at his neck and in her hand.
