# Stun, lifesteal, and a summoner's four

Piece 2 of `2026-09-30-roadmap.md`. Three changes to how the dungeon's creatures fight, each small, none needing the
engine: the fire mage's fireball stuns, every boss heals itself by its blows, and the summoner raises two kinds at
once.

## Stun

- **A skill may stun what it hurts:** a new `StunFrames` on the `Skill` block (0, the default, stuns nothing). The
  fire mage's fireball — its `Olov shari` skill, a skillshot — stuns for a second, `StunFrames = 30`, whoever the shot
  hurts: the one it struck and everyone its burst caught. Its ordinary shots do not stun: a stun on every blow of a
  creature that shoots every two seconds would hold a hero still for most of a fight.
- **A stun is the engine's `DISABLED` status** for the frames, set through the victim's `StatusUpdate`, which refreshes
  and does not stack: the engine's own movement and weapon already stand still under it. The heroes (`rogue.duke`,
  `knight.duke`, `mage.duke`) gain the `StatusUpdate` every monster already carries.
- **A stunned creature casts nothing:** `SkillBook.cast` refuses while its owner is `DISABLED`, its cooldown untouched,
  as it refuses for want of mana. The brains keep thinking; they only cannot act.
- **Stars over the stunned head for as long as the stun:** an `Effect` of its own (`Stunned`) with an AURA layer,
  played on the victim by `world.effect`, its seconds set from the longest `StunFrames` any skill has (as
  `Main.measureLooks` sets a frost's from `SlowFrames`).

## Lifesteal on every boss

- **A module, `Lifesteal`,** with one number, `Percent`: its bearer gets back that share of the damage its blows deal,
  as health, never above its maximum.
- **Heard where the blows land that a boss can deal:** the melee blow (`Swing.launch` sees the blow's damage before the
  engine lands it) and the shot (`ArrowUpdate.strike`, and the burst's splash, where the shooter is known). A kill is
  no special case: the blow's damage counts, whether or not the victim had that much left.
- **Every boss carries it at 25%:** `warden.duke`, `reaper.duke`, `necromancer.duke`, `champion.duke`. The heal is a
  body's heal, so the client shows it as it shows every other.

## Summons of several kinds

- **`Summons` becomes a list of kinds and counts,** written `[Skeleton = 2, Stalker = 2]` as `BossGuards` is, and
  `SummonCount` goes: the count is the list's sum. One cast raises all of them, each kind as many as it says.
- **The summoner raises two swordsmen and two archers a cast:** `Summons = [Skeleton = 2, Stalker = 2]`,
  `MaxSummoned = 4` — one cast's worth standing at a time.
- **Where they rise:** `Summoning.spots` finds as many open spots as the cast asks for, fanned round the aim as now;
  where there are fewer, as many rise as fit, the kinds taken in list order (a swordsman before an archer).
- Everything else a summons is stays: the depth's bonuses, no loot, the share of experience the skill names.

## Tests

- A fireball skill with `StunFrames` sets its victim `DISABLED` for exactly that long, and the burst's too; a skill
  without leaves it free; a stunned hero cannot cast, and casts again once the stun is over; the `Stunned` effect is
  played on the victim.
- A boss with `Lifesteal = 25` hitting a hero for N gets back N/4, capped at its maximum; its shot does the same; a
  creature without the module gets nothing.
- One summoner cast raises two Skeletons and two Stalkers where there is room; `MaxSummoned` still caps them; a
  settings file with the old single `Summons` is refused with a message naming the new form.
- The shipped data: the fire mage's skill stuns 30 frames; every boss has 25% lifesteal; the heroes carry
  `StatusUpdate`.

## Not in this piece

Stuns from anything but a skill, a stun's resistance or immunity, lifesteal on anything but the bosses (the
Revenant's lifesteal aura is piece 5), and the summoner's haste (piece 5).
