# Food and fire (the owner's design, 2026-10-15)

Eternal Return-style food for Last Hero's open world. The owner decided the rules; the numbers below are stand-ins he
may change in data (`data/world/food.duke`, the `Potion` blocks of `data/world/loot.duke`), none needs Java.

## The owner's rules

- No hunger and no thirst meters.
- Food replaces the health potions: eating gives health back. Drinks (water and the rest) replace the mana potions:
  drinking gives mana back. Both over a few seconds, as in Eternal Return, through the skill book's buffs (the owner's
  rule: buffs belong to the skill system, never a module of their own). Better recipes give more, and faster.
- Food and drink sit on the potion belt (keys 1-4) and in the bag. Merchants and drops sell and drop food and drink
  now; the old potions drop and sell no more, but a save that holds them loads and they still work from the belt.
- Sources: animals hunted on the roads and fields (meat); crops and fruit in the empty villages (harvested, grow back
  after a while, deterministically); water from the wells (and the merchants).
- Campfires: some burn already (villages, towers, the lands to free); the hero builds one anywhere from wood.
- Wood: as in Warcraft III or Dota, he right-clicks a tree, chops it for a few seconds, gets wood; the tree is gone and
  grows back after a long time.
- Cooking: inside a fire's radius he cooks a recipe, 3-4 seconds busy, and the meal drops into his bag.
- Animals: a deer flees; a boar and the forest's wolves fight back.
- Everything is the lock-step simulation's: frames, never a clock; lists in the order data writes them.

## What is eaten and drunk

`Heal` and `Mana` are percent of the most he holds, given over `Frames` (30 a second). A stack of ten makes one place
in the bag; `Weight` is how often a monster's drop is it (0: never dropped).

| name | Uzbek | from | heal | mana | over | price | drop weight |
|---|---|---|---|---|---|---|---|
| xom_gosht | Xom go'sht | a deer, a boar, a wolf | 8% | | 5 s | 4 | 0 |
| rezavor | Yovvoyi rezavor | a berry bush | 10% | | 5 s | 5 | 0 |
| qovoq | Qovoq | a pumpkin patch | 10% | | 5 s | 5 | 0 |
| non | Non | merchants, drops | 20% | | 5 s | 12 | 30 |
| qovurilgan_gosht | Qovurilgan go'sht | cooked | 35% | | 4 s | 20 | 25 |
| shorva | Sho'rva | cooked | 60% | | 3 s | 40 | 8 |
| suv | Suv | a well, merchants, drops | | 25% | 5 s | 5 | 30 |
| choy | Choy | cooked | | 45% | 4 s | 15 | 12 |
| kompot | Kompot | cooked | 15% | 40% | 3 s | 25 | 5 |
| otin | O'tin (wood) | a tree | | | | 2 | 0 |

Wood stacks to 20 and is not on the belt: only what heals or fills is.

Eating puts a buff on his book: health in tenths of a point a second (the book's `HEAL` kind) until the meal is done,
and a drink a `MANA_AURA` buff, which the book's own trickle already reads. One meal and one drink at a time: a second
replaces the first (the book keeps one buff a kind and lender). The old potions keep `Frames = 0`, all at once.

## Recipes (best first: a click on a fire cooks the first he has everything for)

| makes | from |
|---|---|
| shorva | xom_gosht + qovoq |
| kompot | suv + rezavor |
| qovurilgan_gosht | xom_gosht |
| choy | suv |

## Work (one busy state for all of it)

| what | how long | gives | grows back |
|---|---|---|---|
| chop a tree (Daraxt) | 3 s | 3 wood | 10 min |
| pick a berry bush | 1.5 s | 2 berries | 3 min |
| dig a pumpkin patch | 1.5 s | 1 pumpkin | 4 min |
| fill the waterskin at a well | 1.5 s | 1 water | never used up |
| cook at a fire | 3.5 s | the recipe's meal | |

A right-click on the thing sends him to it (the cursor says which: an axe, an open hand, a watering can, a torch);
there he works. A move order, a cast or a blow taken stops the work and nothing is spent: the owner's
"moving or being hit cancels it". Each second of work plays a puff and a sound on him; the true animation is an
engine ask (below).

## Fire

- A fire cooks for whoever stands within 30 units (three cells) of it.
- `G` builds one at his feet from 4 wood; it burns 3 minutes and goes out. Fires the world places burn for good.
- Placed fires: one by every village, every watchtower, the capital, the hut and the three lands to free.

## Where things stand (the open world)

The game places them when the map is laid (`Forage` in `data/world/food.duke`): each line a kind, a zone and how many
between two distances (cells) of its middle, on open ground a hero can stand on, drawn from the map's seed. The map's
file is untouched. Trees: 30 in the dark forest, 4 by each village, 6 on the green field and a few at the old sites.
Bushes and pumpkin patches by the villages; deer on the field, the forest and two villages; boars in the forest and two
villages. About 150 things, all still but the animals (16).

What was cut or picked comes back after its time, where it stood (not while a hero stands on it). What grows back and
the fires he built are not saved: a loaded game lays the map fresh.

## Not done / asked of the engine

- E62: a scenery tree that can be pointed at, felled and grown again (today's trees are the client's scenery and the
  simulation only knows their footprints) -- until then the trees to chop are objects the game places.
- E63: a busy state with its own clip: the hero plays a named clip on a loop while he works, cut short by an order or a
  blow -- until then a puff and a sound each second.
- E64: health over time as a buff the book reads (as `MANA_AURA` is for mana), with a picture on the HUD -- until then
  the run heals by the `HEAL` buff each frame.
- No bear (no CC0 animated bear without an account) and no rabbit (the CC0 "rabbits" are characters); a stag's antlers
  came apart in conversion. Rivers do not fill the waterskin, only wells.
