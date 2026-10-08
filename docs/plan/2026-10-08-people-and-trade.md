# People who walk, gold, and a merchant

The campaign's "What exists and what is new" item 4 (people and the city, a merchant, money), on top of the people,
quests and story of `2026-10-08-dialogue-and-quests.md` and `2026-10-08-story-scripting.md`. Game first, lift later:
a purse, a merchant and the trade window are mechanisms every RPG needs and lift to the engine's `rpg` module once
played. Nothing here needed the engine.

Choices the owner left open are marked **default**.

## People who walk

- A person has legs when their Monster block carries a `MoveUpdate` (the commander does now, Speed 15), so the
  story's `WALK` takes her to a zone.
- **Wandering:** an Npc's `Wander` (world units, **default** 0 = stands still) lets them stroll round where they stand
  — their home: where the map put them, or the zone a `WALK` last took them to. Every `WanderFrames` (the `Trade`
  block, **default** 150) each wandering person who has no hero near picks a point within `Wander` of home and walks
  to it. The point is worked out from the frame and the person's id, never from the world's dice, so a person's walk
  moves no other draw and is the same on every machine.
- **Facing:** a person who talks turns to face the hero who talked to them, and stands while a hero is within their
  radius.

## Gold ("oltin")

- Every monster the floor placed, or a story spawned, pays the purse of the hero who kills it, at once (**default**:
  one a summoner raised pays nothing — a summoner is not a well of gold): `GoldPerKill + GoldPerLevel × level`
  (its level as the floor scaled it). **Default** 4 + 2 × level. Straight into the purse — no coins lie on the floor —
  and the hero's bubble says `+12 oltin`.
- Each hero has his own purse (a party's heroes are paid for their own kills). It is shown at the bottom right, above
  the hero's bar: `Oltin: 120`.
- Saved with the run (`Gold = 120` in the save; a save without it has none). A run that ends starts with none.

## The merchant

```
; A person who trades: the Npc of that name, what he sells at what price, and how many of each a map starts him with.
Merchant
  Name = Merchant
  Stock = [Gauntlet 80 2, Boots 80 2, Amulet 50 3]   ; item price count
End
```

- **Opening:** a right-click on a merchant (the `Talk` order) opens the trade window once the hero is near — walking
  up only greets, as with every person. Esc, or walking away, closes it.
- **The window** (drawn by the game, like the bag; every word in the `Trade` block): the merchant's name and the
  hero's gold at the top; on the left his stock — each thing's picture, name, price and how many are left, a click
  buys one; on the right the hero's bag — a click sells that thing for `SellPercent` (**default** 40) of its price,
  times its level (a joined thing is worth what joined into it). A thing no merchant stocks sells at `BasePrice`.
  Quest things and keys cannot be sold.
- **Refused, and said:** buying with too little gold (`Oltin yetmaydi`), with no room in the bag (`Xalta to'la`), or
  what is sold out. Nothing changes hands.
- **Deterministic:** buying and selling are this game's orders (`buy`, `sell`), heard on every machine on the same
  frame as every order, checked there — the hero still near the merchant, the gold, the room, the stock — so a party
  stays in step. The window only asks.
- **Restocked** whenever a map's floor is laid (a new run, a load, or the next chapter): the stock is the data's
  again. The stock left is not saved.

## The demo, on `maps/talk`

A merchant (**default** stand-in model: the KayKit mage, tinted) stands near the way in, strolling a little, and
sells six things: the three attribute charms of the descent and three of his own (an amulet of health, a ring of
mana, a whetstone of attack) that are never found, only bought. The commander walks too: when her quest is handed in
and the ambush springs, she steps back to the wall.

## Deferred

- A city: streets, houses, many people — the open world's (another branch).
- Haggling, a merchant's own gold, buying back what was sold, prices by depth.
- Gold lying on the floor to be picked up; gold in the hero card.
- People who follow the hero, or fight.
