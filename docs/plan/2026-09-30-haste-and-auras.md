# The mages' haste and auras

Piece 5 of `2026-09-30-roadmap.md`, built on pieces 2 and 4 (`stun-lifesteal-summons`, `monsters-grow`) and met in
piece 3's court, where two summoners and two healers stand at every keep's corners. The owner asked for four things:
the summoner (`SkeletonSummoner`) hastens the strongest of its own near it, or itself — attack speed ×1.75 for five
seconds; and three passives that hold for everyone round their bearer — the summoner's +50% attack, the healer's
(`SkeletonHealer`) 5 mana a second, the Revenant's 10% lifesteal. Each is a skill of the one skill system, as he asked:
one new cast effect, three new passives, and the `SkillBook` the one thing that receives them. No engine change:
`DamageModifier`, `RateOfFireModifier`, `world.effect` and a renewing AURA layer are all there.

## Where it starts

- Five kinds carry no `SkillBook`: `Skeleton`, `Stalker`, `Brute`, `Runner`, `Revenant`. `SkillBook.drink` returns for
  a striker without one, and reads only the striker's own `LIFESTEAL`.
- The book is a `DamageModifier` (EMPOWER's timer) and a `WeaponHold`; the game's one `RateOfFireModifier` is the
  hero's `AttackSpeed`, put on him by `HeroProgress`. A plain monster's blow is changed only by its `DepthBonus`
  (piece 4: `LevelBonus`).
- A passive may not carry a `Look` (nor `Damage`, `ManaCost`, `WindUpFrames`, `Projectile`). A monster's cast marks are
  drawn by nobody; what rides a creature is `world.effect` with an AURA layer, as the stun's stars do, renewed by
  `Main.layerOf` for the Combat block's `StunLook` alone.
- Blows are heard at `Swing.launch` (a striker with no `Bow`) and `ArrowUpdate.strike`/`splash`; piece 4's meteor lands
  in `FallingUpdate`, which tells nobody.
- After piece 4: every monster has a level (`LevelBonus`), the three casting mages have pools, and the brain casts the
  first open, ready, affordable skill in file order that is not a passive.

## The haste

- **A new cast effect, `HASTE(Aim.SELF)`:** the one it hastens fires `BoostPercent` faster — every wait of its weapon
  divided by 1 + `BoostPercent`/100 and cut to whole frames, the engine's own rule — for `DurationFrames`. `Range` is
  how far from the caster that one may stand; `ManaCost`, `CooldownFrames` and `Look` are any cast skill's.
- **Whom:** of the caster's own side, living, carrying a `SkillBook`, within `Range` (middle to middle) and in plain
  sight (`SightLine.clear`, as a mending's patient) — the caster always among them — the one of the highest level
  (`LevelBonus`; 1 without), then the most maximum health, then the nearer, then the smaller object id. The book picks,
  as a `STRIKE` cast at nothing picks its victim, so the brain needs no new branch; the caster hastens itself when
  nothing sturdier stands near — "or itself".
- **When:** its condition is that of anything the brain throws at him — he is inside its `SkillDistance` and in plain
  sight: a haste in a fight, never in an empty room. `Aim.SELF`, as the summoning is, so piece 4's rule that an aimed
  skill's `Range` reach the band's far end does not ask it. Never refused for want of a target.
- **Held in the hastened's book,** its frames and percent counted down as EMPOWER's are; a second haste while it burns
  starts it again at the newer figures — it refreshes, never stacks. The book becomes a `RateOfFireModifier`, the seam
  the heroes' boots ride through `AttackSpeed`, rather than a module attached for five seconds: attaching one mid-frame
  is what the engine's module list refuses (the book's own note on EMPOWER), and every monster has its book from birth.
- **Seen:** the caster turns to it and is seen casting (`WeaponFired`, as a mending posts), and the hastened wears the
  skill's `Look`, played on it by `world.effect` for as long as the haste — `Main.measureLooks` already gives a look its
  skill's `DurationFrames` — and carried on by a second haste.

## Three auras

- **Three new passives, not one `AURA` with a kind:** `DAMAGE_AURA`, `MANA_AURA`, `LIFESTEAL_AURA`, each
  `(Aim.SELF, true)` as `LIFESTEAL` is. A kind field would be a second switch inside one constant, a field every other
  skill leaves blank, and a rule tying the two; three constants are the enum's own rule — a new shape is a constant and
  a branch in `SkillBook` — with the kind the effect itself. The three share one rule of reach, below.
- **What each lends everyone it reaches:**
  - `DAMAGE_AURA`: `BoostPercent` more on every blow and every skill's damage — through the book's `damageMultiplier`,
    which the engine's weapon multiplies into each blow and `SkillBook.damageOf` into each skill, as EMPOWER's is. A
    mending's heal is not damage and is not raised.
  - `MANA_AURA`: `ManaRegen` — a new `Skill` field, the heroes' word and unit, tenths of a point a second — added to the
    pool's own trickle (a pool with none of its own still fills at the aura's), counted by the book's carry in whole
    points. A creature with no pool gets nothing: the aura fills pools, it makes none.
  - `LIFESTEAL_AURA`: `BoostPercent` of every blow landed, back as health, by `SkillBook.drink`.
- **Everyone:** the living of the bearer's own side that carry a `SkillBook` — the bearer itself and what a summoner
  calls up included; heroes never (another side, and a passive is refused on a hero).
- **Every monster carries a book:** `SkillBook End` joins the modules of `Skeleton`, `Stalker`, `Brute`, `Runner` and
  `Revenant` — empty for the first four. A creature without one is not reached, as one without a `StatusUpdate` is not
  stunned: the game does not decide what a creature is made of behind its file's back.
- An aura holds while its bearer lives, stunned or not, from the level that opens it (piece 4's rule; the three shipped
  ones from the first), and does not grow with the level: its figures are the skill's.

## How an aura reaches

- **The receiver asks, when it uses the figure:** the aura of a kind on a creature is the largest worth among the
  bearers of that kind on its side, living, within whose `Radius` it stands (middle to middle, as an area blast
  measures) and in whose plain sight — 0 for none. One static helper, `SkillBook.auraOn(creature, kind)`, over
  `world.objectsInRange` out to the widest aura any file gives, asked in three places: the book's `damageMultiplier` at
  each blow and skill, `drink` at each blow landed, and the mana trickle each frame the pool is not full.
- So it holds exactly while the receiver stands in reach and ends the moment it steps out or its bearer falls — no state
  kept, no timer, nothing sent between the two — and a maximum comes out the same in any order.
- **Several of one kind: the strongest,** never the sum — two summoners make a skeleton hit ×1.5, not ×2.25. Kinds add,
  each on its own seam. **A creature's own `LIFESTEAL` and the aura add:** a boss beside a Revenant drinks 25% + 10%.
- **Cost:** a pass over the floor's objects and a sight line per bearer in reach, at each blow and each refilling frame;
  none for a hero's book (none of his side may bear one) or where no file gives an aura. A cache waits for a slow floor.

## Every blow a monster lands

`drink` adds the strongest `LIFESTEAL_AURA` on the striker to its own `LIFESTEAL` shares. The places that tell it —
`Swing.launch`, `ArrowUpdate.strike` and `splash` — gain the meteor's blast (`FallingUpdate`, for each it hurts) and
the blows the book lands itself (`SkillBook`: an area blow, a blast at a spot, a strike with no shot, a charge — for
each it hurts), so no blow a monster deals escapes the Revenant's aura. The Revenant keeps its `AutoHealUpdate`, and
drinks from its own fire.

## Drawn

- **The hastened** wear `Hasted` — the kit's `Focus` (a glow round the body, motes rising) in a fury's red — for as long
  as the haste.
- **An aura's bearer** wears its `Look` (`MightAura`, `ManaAura`, `BloodAura`), in the colour of the aura's *kind*, not
  of its bearer — might violet, mana blue, the thirst red — and of two layers (the aura-looks plan, Task 1): a thick,
  bright ring on the floor as wide as its `Radius` (a `MARK` measured in reach, which `Main.measureLooks` already sets
  from the skill; the kit's `circle_03`, sized so its brightest line lies on the `Radius`), and a small `AURA` layer
  riding the body that says which kind it bears — violet sparks, a few blue motes at the feet, red drops rising. Its
  book plays it on itself every `TickFrames` (30), staggered by its id as the brain's re-plans are, and
  `Main.measureLooks` makes an aura's look last two ticks: each ring crossfades into the next, `Main.layerOf` renews the
  rider at each beat, and the whole look rides the bearer while it lives and fades within two seconds of its fall.
  Nothing is drawn on each creature an aura reaches.
- **The passive-`Look` rule relaxes for an aura:** it may name one, with a `TickFrames` — a lasting skill's beat, as a
  whirlwind's is; a `LIFESTEAL` still may not. `Main.layerOf` renews every look worn as a state: the stun's, each
  `HASTE`'s and each aura's. The four looks are written in their mages' files.

## The three mages

| Mage | Key | Skill | Effect | Figures |
|---|---|---|---|---|
| `SkeletonSummoner` | Q | *Chaqiruv* | `SUMMON` | as piece 4 leaves it (`ManaCost = 40`) |
| | W | *Shiddat* | `HASTE` | `BoostPercent = 75`, `DurationFrames = 150`, `Range = 60`, `ManaCost = 25`, `CooldownFrames = 360`, `Look = Hasted` |
| | E | *Qudrat* | `DAMAGE_AURA` | `BoostPercent = 50`, `Radius = 60`, `Look = MightAura`, `TickFrames = 30` |
| `SkeletonHealer` | Q | *Muqaddas nur* | `HEAL` | as now |
| | W | *Sehr buloqi* | `MANA_AURA` | `ManaRegen = 50`, `Radius = 60`, `Look = ManaAura`, `TickFrames = 30` |
| `Revenant` | Q | *Qon aurasi* | `LIFESTEAL_AURA` | `BoostPercent = 10`, `Radius = 60`, `Look = BloodAura`, `TickFrames = 30` |

All `MaxRank = 1`, open from the first level. File order is the brain's priority: the summoner raises its four first and
hastens after (a summoning refused for want of room passes to the haste that frame); the passives, skipped wherever they
stand, come last. 60 reaches past a mage's band (`KeepDistance` 35–55) to the ones fighting him. Beside a healer the
summoner's pool (80, 2.5 a second) pays for both at their cooldowns; alone, as its trickle allows.

## When the file is read

- An aura needs a `Radius`; a `DAMAGE_AURA` a `BoostPercent` of at least 1, a `LIFESTEAL_AURA` one from 1 to 100 (as
  `LIFESTEAL`), a `MANA_AURA` a `ManaRegen` of at least 1. `ManaRegen` on any other skill is read by nothing, refused as
  `StunFrames` off a skillshot is; an aura's `Look` and `TickFrames` come together or not at all.
- An aura says no `DurationFrames`: it holds while its bearer lives, and its look is measured two of its beats.
- A `HASTE` needs a `BoostPercent` of at least 1 and `DurationFrames`; a `Range` of 0 hastens only its caster.
- A passive is still refused on a hero, and still may not carry `Damage`, `ManaCost`, `WindUpFrames` or a `Projectile`.
- The switches over every effect (the aim rings' in `Main.rangeOf`) learn the four: the auras where `LIFESTEAL` stands,
  the haste round him as far as its `Range`.

## Lock-step

On the simulation thread, from simulation state: an aura is a maximum of whole percents and tenths, the same in any
order, behind a sight line walked on integers; the haste's choice a total order ending in the object id; its frames
counted down; the mana by the book's carry in whole points; the waits by the engine's own cut to whole frames. The looks
are events, out of the checksum. Every name and number from data.

## Tests

- **Haste:** in a fight the summoner hastens a Brute over two Skeletons of its level, a Skeleton a level up over the
  Brute, the nearer of two alike, nobody behind stone or past `Range`, and itself when alone; a hastened Skeleton waits
  17 frames for its 30 through 150 frames, and 30 after; a second haste starts the 150 again; it spends 25 mana and is
  refused stunned or unpaid for; `Hasted` is played on the one hastened.
- **Might:** a Skeleton 60 from a summoner deals 10.5 for its 7, a fire mage's fireball ×1.5 alike; at 61, or behind
  stone, 7; two summoners, still 10.5; the summoner itself ×1.5; a hero beside it, his own.
- **Mana:** a healer alone refills 7.5 a second (its own 2.5, its aura's 5), a fire mage beside it 8, out of reach 3;
  two healers, still 5 more; a Skeleton beside it has no pool and is given none.
- **Lifesteal:** a Skeleton beside a Revenant striking for 7 gets back 0.7, a Stalker's bolt its tenth, the Revenant its
  own fire's; a boss beside it 35%; two Revenants, still 10%; out of reach, nothing; the meteor's blast drinks.
- **Reach:** its bearer killed, a Skeleton's next blow is its own again; a creature with no book is not reached; an aura
  its bearer's level has not opened lends nothing.
- **The file:** each refusal above, by its message; an aura's `Look` and `TickFrames` accepted, a `LIFESTEAL`'s not.
- **The shipped data:** the three mages' skills in that order with those figures; every `Monster` carries a `SkillBook`;
  the four looks exist and renew, `Hasted` measured 5 s and each aura's look two ticks, its ring bold and at its
  `Radius`, its rider fed, and both in the colour of its kind.
- **Lock-step:** two worlds fought through the same frames with the three mages read the same checksum.

## Decided by default (the owner may change)

1. **One cast effect and three passives,** an aura's kind being its effect; no generic `AURA`.
2. **"Strongest"** is the highest level, then the most maximum health, then the nearer, then the smaller id — in reach
   and in plain sight, the caster one of the candidates, so it hastens itself only when nothing sturdier is near.
   Sturdiest rather than hardest-hitting: health is on every body, a blow's worth is not.
3. **A haste only in a fight,** the brain's condition for what it throws at him; it refreshes, never stacks.
4. **The haste's cost, rhythm and reach:** 25 mana, the summoning's 12 s, a mending's 60; the owner gave the +75%, 5 s.
5. **Everyone** is the bearer's own side that carries a book, the bearer and what is summoned included; heroes never.
6. **Only in plain sight,** as a mending and the alarm are: stone does not carry an aura, so a room is a room and the
   court's mages lend nothing through the keep's wall. (Warcraft's auras ignore walls; a dungeon of rooms should not.)
7. **A `Radius` of 60** for all three: a room round the bearer, and the ones fighting him from a mage's band.
8. **The strongest of a kind, never the sum; kinds add; a creature's own lifesteal and the aura add.**
9. **+50% on every blow and every skill's damage,** as EMPOWER's; a mending is not raised.
10. **Mana as a rate,** 5.0 a second through the carry; nothing to a creature with no pool; no aura grows with a level.
11. **Asked where it is used, not pushed to the receivers:** nothing held, nothing to go stale.
12. **Every monster carries a book** — one line in each of five files.
13. **Drawn:** a thick ring in the aura's colour, and a rider of its own, on each bearer, the ring as wide as its reach;
    `Hasted` on the hastened; nothing on each it reaches.
14. **The meteor's blast drinks,** so the lifesteal aura misses no monster's blow.
15. **Priority and names:** the summoning before the haste; *Shiddat*, *Qudrat*, *Sehr buloqi*, *Qon aurasi* — all data.

## Not in this piece

A hero's haste or aura (a passive stays a monster's); auras that grow with a level, stack, or reach through stone; a
mark on every creature an aura reaches; speed of foot in the haste; sounds for either; the creature card showing what an
aura or a haste lends; an aura on any boss; the Revenant's self-mending as a skill (it stays `AutoHealUpdate`).
