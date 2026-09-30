# Monsters that grow like heroes

Piece 4 of `2026-09-30-roadmap.md`, built on pieces 2 and 3 (`stun-lifesteal-summons`, `key-to-the-keep`). The owner
asked for enemies that are units as the heroes are — a level, mana, skills that open with the level and are paid for,
an ultimate — with levels climbing exponentially with depth, so that already on the first floor the monsters before
the boss's room stand at level 7 or 8; and for the fire mage an ultimate that drops a falling fireball, open from level
6. Everything a monster casts stays a skill of the one skill system: the meteor is a `Skill` block, mana is the
`SkillBook`'s own pool, a skill opens by the heroes' own `LevelPerRank`. No engine change: E7 (a creature's own level
on its bar) has landed in the local 0.7.0.

## Where it starts

- A monster's level on the bar is its floor's depth (`UnitBarReading.levelOn`); there is no per-monster level.
- `Spawner.scale` grows health, attaches `DepthBonus` and swaps the experience module by the `Descent` block's
  per-depth percentages; `SkillBook` reads `DepthBonus` for a skill's damage and a mending, `SummoningUpdate` copies it
  onto what rises, and the creature card shows every creature's blow at the monsters' depth rate, the boss's too.
- Monsters cast free (`SkillBook.usesMana` off), and only the first skill they cast, at rank 1 (`Monster.skillKey`,
  `ITS_ONLY_RANK` in `MonsterBrain`).
- A stage plays at its `Difficulty`, which is a depth.

## A level for every monster

A place has a **tier**, how hard it is: today a floor's depth, which is what a stage's `Difficulty` already means;
later a region's (E8 names it, E10 brings regions). The level rule is handed the tier and knows nothing else of floors.

- **Two ends per tier,** from the `Descent` block: `BeforeBossLevel = 8` in the chamber before the boss's, ×(1 +
  `TierGrowthPercent`/100) a tier past the first, with `TierGrowthPercent = 60` — worked out in one step
  (`StrictMath.pow`), never compounded floor by floor; and the way in: `WayInLevel = 1` on the first tier, and on every
  later one the level the tier before closed at (its before-the-boss level), so a descent never steps back down — as
  an open world's regions run 1–8, 8–13, 13–20. No monster above `MaxMonsterLevel = 50`.
- **Along the way:** a monster's share of the way is the steps walked from the way in to its cell over the steps to
  the middle of the chamber before the boss's — the one the keep's road leaves from (`Keep.chamber`), or the boss's own
  where there is no keep (a stage, or a floor where none fitted) — never above 1. Its level is
  `WayIn + (BeforeBoss − WayIn) × share`, rounded once, at least 1. The steps come from one walk from the way in over
  the floor's terrain, four ways from a cell in a fixed order, by the engine's own rule for a step (`PathGrid.canStep`):
  the walk `StageCheck` already makes to find what is walled off, now counting its steps, one walk for both. Props are
  not in it, so the shut gate does not hide the court.
- **Past that chamber** — its far side, the road, the court and the four mages in it — everything is at its level.
  **The boss** stands `BossLevelsAbove = 2` above it. **What rises from a rift** has its caller's level, read when the
  rift opens, as the depth's bonus is read now. A level is fixed where a monster is placed: none levels up in a fight.

| Tier | Way in | Before the boss | Boss |
|---|---|---|---|
| 1 | 1 | 8 | 10 |
| 2 | 8 | 13 | 15 |
| 3 | 13 | 20 | 22 |
| 4 | 20 | 33 | 35 |

The shipped stages: `first` (Difficulty 1) plays 1 → 8, boss 10; `deep` (Difficulty 8) would play 134 → 215 and plays
50 throughout, at the cap.

## What a level gives

Per level past the first, alike for every monster and boss, from the `Descent` block: `HealthPercentPerLevel = 10`,
`DamagePercentPerLevel = 5` (its blow, its skills' damage and a mending's heal, everything the depth's bonus reached),
`ExperiencePercentPerLevel = 5`, `ManaPercentPerLevel = 10`. A level-1 monster is its template exactly.

- Before the first floor's boss a monster has 1.7× its health and hits 1.35× as hard; before the fourth's, 4.2× and
  2.6× (today's fourth floor: 1.75× and 1.45×). The boss at 10: 1.9× and 1.45×; at 35: 4.4× and 2.7×.
- **Experience climbs slowly on purpose:** a level-8 monster is worth 1.35× its kind. The first floor is worth about
  what the per-depth rule made it; the later ones somewhat more, since each starts where the one before closed — so the
  hero levels a little faster than today while what he fights climbs faster still.
- **Replaced:** `MonsterHealthPercentPerDepth`, `MonsterDamagePercentPerDepth`, `BossHealthPercentPerDepth`,
  `BossDamagePercentPerDepth`, `ExperiencePercentPerDepth` and their accessors (`monsterHealthAt`, `monsterDamageAt`,
  `bossHealthAt`, `bossDamageAt`, `experienceAt`) go; a file still writing one is refused, as any unknown line is.
  `MonsterCountPercentPerDepth`, `MinDepth` and the bosses by depth stay: who lives in a place, and how many, is the
  place's, not a level's.
- **`DepthBonus` becomes `LevelBonus`:** the level and the two multipliers it gives, on every placed or risen monster,
  level 1 included; a creature without one (a test's bare skeleton) is level 1. Read wherever `DepthBonus` is read
  today, and by the brain for which skills are open.
- **One step makes a creature its level** — health grown, `LevelBonus` attached, its worth set, its pool sized and
  filled, its `level:N` word set: `Spawner.scale`, which the rift's `rise` now calls rather than copies.
- **The creature card's attack is its own:** its weapon's damage × its own `LevelBonus`. The unit bar's marks
  (`Segments`) are measured again over the new range of health, the fourth floor's boss and `deep`'s included.

## Mana

- A `Monster` block may name `MaxMana` and `ManaRegen`, the heroes' words and units (points; tenths of a point a
  second), each grown by `ManaPercentPerLevel` a level, in whole numbers. The pool is full when the monster is placed or
  rises (`SkillBook.poolOf`, `fillMana`); its skills' `ManaCost` is paid at rank 1; a cast it cannot pay for is refused
  with nothing spent, as a hero's is.
- A kind that names no pool casts free, as every monster does today; a `Monster` block that writes skills that cost
  mana must name a pool (checked when the file is read, per block: a block that re-tunes a kind and writes no skills
  says nothing of what they cost, so one that leaves out `MaxMana` leaves the kind's shipped skills casting free).
- The three casting mages get pools (`MaxMana`, `ManaRegen`): `SkeletonMage` 60, 30 (its meteor costs 50, *Olov
  shari* 20); `SkeletonHealer` 50, 25 (*Muqaddas nur* 25); `SkeletonSummoner` 80, 25 (*Chaqiruv* 40) — enough to
  cast at their cooldowns through a fight of a minute or more, the fire mage's meteor included, longer with the level.
- It is not drawn: the bar's mana is the hero's alone (the status line carries one pool). Piece 5's healer aura feeds
  these pools.

## Several skills, opened by the level

- **A monster's skills are its `Skills = [ … ]`,** as many as it has, each paid for and recharged as a hero's is.
- **A skill opens at the heroes' `LevelPerRank`:** the level its first rank waits for (0: from the first). A monster
  holds rank 1 of every skill its level has opened and rank 0 of the rest, which `SkillBook.cast` refuses as it
  refuses a hero's unlearnt skill. Chosen over a gate of the monsters' own because it is one number meaning one thing
  on both sides, it makes the meteor an ultimate by the rule that already defines one (`Skill.isUltimate`), and it adds
  no field. A monster's skill never ranks past 1: its level's figures are what make it hit harder.
- **The brain chooses** each frame it is neither mid-blow nor mid-cast: it walks its skills in the order its block
  writes them and casts the first that is open, ready, affordable (`canAfford`) and not a passive, and whose own
  condition holds — for a mending, one of its own hurt enough in reach and sight (on its cadence, as now); for anything
  aimed at him, him inside its `SkillDistance` and in plain sight. A cast the book declines (no room for a rift)
  passes to the next skill that frame. One cast per gesture: nothing else leaves it until `SwingFrames` after the last.
- **One band per monster:** `SkillDistance` is where it casts at him from, whichever skill; a skill aimed at him must
  reach the band's far end (`Range` at least its furthest, checked when the file is read), so none falls short.
- `Monster.skillKey`, `MonsterKind.skillKey` and `ITS_ONLY_RANK` give way to the book's skills in file order and the
  rank the level gives; the settings' checks ask of each skill.

## The fire mage's meteor

- `SkeletonMage` gets its ultimate: a `METEOR` skill — the heroes' effect, a mark on the floor and the blast a moment
  later (`FallingUpdate`) — written first in its `Skills`: `Key = R`, `LevelPerRank = 6`, `MaxRank = 1`, so it is open
  from level 6 and never cast below. `Damage = 90`, `Radius = 30`, `Range = 70`, `WindUpFrames = 45`,
  `CooldownFrames = 600`, `ManaCost = 50`, `Name = Meteor`. A hero at the mark's middle walks out of 30 in the second
  and a half it lies there.
- **Its own names,** because `Main.measureLooks` sets an effect's seconds and reach from the skill that throws it, and
  the hero's meteor is wider: the mark `SkullMeteorMark` (a `Projectile` with a `FallingUpdate`, as `MeteorMark` is),
  drawn as `SkullMeteor` — the kit's `Meteor` in its fireball's violet (0xB04AFF), written in the game's data beside
  the mark — and heard as the rift is, `spawned.SkullMeteorMark` (a shipped file). No `Look`: a monster's cast is drawn
  by what it throws; the status line's cast marks are the hero's alone.
- *Olov shari* stays second: while the meteor recharges or cannot be paid for, it throws the fireball, which still
  stuns.

## On the bar

`LevelWord = level:` in `hud.duke`'s `UnitBar`, handed to the client through E7's `UnitBarLook.withLevelWord` in
`Main.unitBars`; the one step above sets `level:N` on every placed monster, the boss and whatever rises. The hero keeps
his own; a creature with no word shows the depth, as now. `hud.duke` stops saying a monster's level is its depth.

## Lock-step

The walk, the levels and every pool are worked out on the simulation thread when a floor is placed, from the floor's
own data: whole steps in a fixed order, `StrictMath.pow`, one rounding; mana in whole points and tenths; the brain's
choice in file order, with no dice and no hash order; every name and number from data.

## Tests

- **The rule:** tier 1 gives 1 and 8, tier 2 gives 8 and 13, tier 4 gives 20 and 33, the boss two above; tier 4 asked
  directly is tier 4; `TierGrowthPercent = 0` keeps every tier after the first at 8 → 8; nothing above
  `MaxMonsterLevel`, the boss included (`deep` plays 50 throughout).
- **Along the way:** on a generated first floor a monster more steps from the way in is never below one with fewer;
  every monster in the chamber before the keep is at 7 or 8, every guard in the court at 8, the boss at 10; one seed
  gives every monster the same level twice. A stage's way ends at its boss, at its Difficulty.
- **What a level gives:** a level-1 monster is its template; a level-8 one has 1.7× the health, its blow, skill
  damage and mending 1.35×, and is worth 1.35×; what rises has its caller's level, figures and word, and is worth its
  share of its kind at that level; the card shows its own attack; the bar's marks hold over the new range.
- **The bar:** each placed monster, the boss and each risen creature carries exactly one `level:N`, its own; the look
  handed to the client reads `level:`.
- **Mana:** a pool grown by its level and full when placed, trickling back; a cast it cannot pay for refused with
  nothing spent, and the next skill cast; no pool, free; a cost with no pool refused when the file is read.
- **Skills:** the first open, ready, affordable skill in file order is cast, then nothing until the gesture ends; a
  passive never; an aimed skill whose `Range` falls short of the band refused when the file is read.
- **The meteor:** below level 6 never cast, whatever its mana and wherever he stands; from 6 cast at where he stands,
  its mark a `SkullMeteorMark`; while it recharges, the fireball follows.
- **The shipped data:** the level lines and their numbers, no per-depth health, damage or experience; the three pools
  and costs; the meteor first in the fire mage's block; `LevelWord` in the `UnitBar`.

## Decided by default (the owner may change)

1. **The way is walked, not measured straight,** and ends in the chamber before the boss's; its far side, the road and
   the court are all at that chamber's level.
2. **A tier starts where the one before closed** (the controller's ruling while the owner was away; the spec's first
   draft had every floor start low again — the second at 2 after the first closed at 8): the owner wanted the
   monsters harder with depth, and a descent — or a world's regions — that steps back down to level 2 undoes it.
   The before-the-boss end grows ×1.6 a tier.
3. **A cap, `MaxMonsterLevel = 50`,** so a deep stage stays finite: `deep` (Difficulty 8) plays 50 throughout and
   keeps its Difficulty; lowering it (to 4, say: 20 → 33) is the owner's call.
4. **The boss two levels above the chamber before it;** the guard at that chamber's level; what rises at its caller's.
5. **One rule for monsters and bosses:** a level is worth the same to both; the bosses' faster per-depth rates go.
6. **A level is worth** +10% health, +5% blow, skill and mending, +5% experience, +10% mana and regeneration; no
   armour, speed or attack speed.
7. **Experience at 5% a level,** so the hero levels at about today's pace.
8. **A level is fixed where a monster is placed,** and a monster's skill never ranks past 1.
9. **`LevelPerRank` as the gate; file order as the priority,** the meteor written first.
10. **One `SkillDistance` band** for every skill a monster aims at him.
11. **Pools for the three casting mages** at the figures above; everything else casts free; no monster's mana drawn.
12. **The meteor's figures** above, named *Meteor* as the hero's is. A stunning fireball under a falling meteor is a
    hard pair; its 20 s cooldown and its cost keep it rare.
13. **The level lines live in the `Descent` block,** where the per-depth lines they replace were.
14. **The hero's own cap is 15** (`Progression MaxLevel`), and the monsters' numbers pass it from the third floor (13
    → 20). The number is what the bar shows; what a level is worth is the per-level percents. Raise the hero's
    `MaxLevel`, lower `TierGrowthPercent`, or keep it — all data, for the owner at playtest.

## Not in this piece

Piece 5's haste and auras (the healer's mana aura feeds the pools made here); regions and their tiers (E10) and the
split's move from depth to tier (E8) — until then the tier is the depth; a monster ranking its skills past 1, levelling
in a fight, or its mana drawn on its bar (an engine request, if wanted); armour, speed or loot by level; new skills for
any monster but the fire mage; the Revenant's self-mending (`AutoHealUpdate`) as a skill.
