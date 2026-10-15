# Modern controls: WASD, the mouse aims (2026-10-15)

The owner, 2026-10-15: the single-hero game moves from Dota/WC3 click controls to modern action-RPG controls (Diablo IV,
Lost Ark, Last Epoch with WASD). The old scheme stays as a settings option.

Settings: **Boshqaruv: Zamonaviy (WASD) / Klassik (sichqoncha)**. Zamonaviy is the default for a new player once the
engine can walk him on WASD (phase 2); until then the file says Klassik so a shipped build is never one you cannot
play. Data: `data/world/controls.duke` (`Controls` block), Java: `world/Controls`, `DungeonSettings.scheme()/letterOf`.

Frames below are the simulation's: 30 a second (`GameConstants.LOGICFRAMES_PER_SECOND`).

## 1. The two schemes

| | Zamonaviy (modern) | Klassik (classic, as today) |
|---|---|---|
| Walk | W A S D held, relative to the camera (W = up the screen); diagonals; letting go stops him | right-click ground; F + click |
| Aim | the cursor, always | the cursor after a skill key (press, then click) |
| Attack | LMB: on an enemy = lock on; on ground = swing toward the cursor; held = keep swinging | right-click an enemy; A + click |
| Interact | RMB: talk, chop, gather, open, pick up, cook (the E20 context orders) | right-click |
| Skills | Q E R F | Q W E R |
| Dodge | Space | none (Space stays the camera's "back to my hero") |
| Stop | let go of WASD; H (client STOP) | S |
| Guard (stand and fight) | none: in modern he never acts on his own but for the lock-on (below) | D |
| Camera | follows him (section 5), no edge scroll; arrows still pan for a look | free RTS camera, edge scroll, arrows |
| Belt, fire, screens | 1-4, G, C sheet, L quests, M map, J tracker, I/B bag: unchanged | same |

### Hotkey migration

A skill keeps its own `Key` (Q W E R in its hero's file) as its **name** everywhere: `CastSkill`, `UpgradeSkill`, his
ranks, the save, an item's `SKILL_RANK:E`. The scheme only says which letter on the keyboard presses it
(`Controls.ModernSkillKeys`), so no save, item or test changes and classic is exactly as it was.

| Slot (its name) | Modern letter | Knight | Rogue | Mage |
|---|---|---|---|---|
| Q | **Q** | Qilich to'lqini (WAVE) | Og'ir o'q (STRIKE) | Olov o'qi (SKILLSHOT) |
| W | **E** | Qalqon bilan surish (SKEWER) | O'q yomg'iri (AREA_DAMAGE) | Muz to'lqini (AREA_AT_SPOT) |
| E | **R** | Ajdar qoni (passive: bound to nothing, letterless on the bar since E65) | Chopish (DASH) | Siljish (BLINK) |
| R | **F** | Teskari qutb (PULL) | Nishonga ol (EMPOWER) | Meteor (METEOR) |
| X | none | his STAGGER: never pressed | same | same |

Conflicts checked: W A S D are walking; the classic orders' F A S D give way (F becomes R's skill, A S D his feet);
G (fire), C L M (screens), J (tracker), H P (client), 1-4 (belt), Z/U (the food's and items' lent skills, never pressed)
clash with nothing. `DungeonSettings.validateControls` refuses a modern letter on W A S D, a screen's, the tracker's,
the fire's, H or P, two slots on one letter, and (once the file says MODERN) a pressed slot left on a step's letter.

### Quick-cast (an option, modern only, default on)

Pressing a skill's letter casts it at the cursor at once, no second click: GROUND/OPEN_GROUND skills at the cursor's
ground point (pulled back to their reach as the ring does today), SELF skills at once, UNIT skills (the rogue's Q, a
heal) on the creature under the cursor, else the one nearest the cursor within 6 units of it, else refused as today
with no cooldown spent. Off: press shows the ring and the next LMB casts (RMB or Esc cancels), the classic flow.

## 2. Determinism: the intent travels as orders

Nothing the simulation does may read a key. The client turns held keys and the mouse into **orders on the lock-step
command stream**, the same stream every other order is on, applied by every peer on the same frame and in the replay.

- **Walking.** Each render frame the client reads W A S D, makes a direction in screen space (W = (0,1), D = (1,0),
  summed, so 8 ways), turns it by the camera's yaw onto the ground, and quantizes its angle to 1/256 of a turn (one
  byte; a gamepad stick later gives any of the 256). It posts `MoveAlong(player, units, angle)` (E74) **only when
  the quantized value changes**: a key pressed or let go, or the camera turned 1.4 degrees or more while a key is
  held. Let go of all four: `MoveAlong(player, units, NONE)`. So a held key costs one order, a run 2-6 a second.
- **Attacking.** LMB sends `AttackObject` (exists) on a creature, `AttackToward(point, held)` (E76) on ground; while
  held the client re-sends the point when it moved 2 units or more, at most every 3 frames; let go sends
  `AttackToward(.., held=false)`, which ends the hold after the swing in progress.
- **Dodge.** Space sends `CastSkill(player, <the dodge's key>, null, point)`, where point is his position plus the
  current WASD direction, or the cursor's ground point when no key is held; the book turns it into a direction.
- **Skills, interact, belt, fire, screens**: as today (orders, or window-only for the screens).
- Every order carries quantized numbers (angle byte, coordinates the client already rounds) so two machines that read
  the same mouse to different floats still post the same thing.

**Multiplayer and latency.** Lock-step adds the command delay to every order: `MultiplayerSession`'s 3 frames
(100 ms at 30 Hz) in a party, the local loop's own in single play. For WASD that is felt as a start/stop lag. Phase 2
ships without prediction; if a party playtest finds 100 ms too soft, the follow-up is client-side prediction of his
own drawn position only (draw him ahead along the intent, snap smoothly to the simulation), never a change to what
the simulation reads. Sending on change keeps bandwidth flat: a party of four running and swinging is ~20 small
orders a second.

## 3. The numbers

| What | Value | Why |
|---|---|---|
| WASD walk speed | his `Speed` (knight 21, rogue 27.2, mage 23.65) | the same walk as a click |
| Direction resolution | 256 a turn | a byte; gamepad-ready |
| Lock-on: LMB pick | the creature under the cursor (the client's pick), an enemy only | a friend or person is RMB's |
| Lock-on: furthest start | 60 from him; past it the click is a ground swing | the rogue's reach; the knight walks up to it |
| Lock-on: leash | ends when the target is 90 away, dead, or he is given any WASD direction, a dodge or another LMB | "until it dies or he moves away" |
| Melee swing at ground (knight) | his own blow (`PreAttackFrames` 9, 34 frames apart), facing the point; strikes the ONE enemy nearest his front within his reach 11 plus its radius and inside a 120-degree arc (60 each side) | the same damage a second as a targeted attack, no free cleave (cleave is the wave's) |
| Ranged shot at ground (rogue, mage) | his own missile toward the point, flying his `AttackRange`, striking the first body | a missed shot is a miss |
| Hold LMB | a swing every time his weapon is ready, at the latest point | |
| Attacking on the move | allowed, at full speed (`MoveWhileAttackingPercent = 100` to tune) | the E70 upper-body layer draws it |
| Dodge distance | 16 (1.45 of his reach, 0.75 s of a knight's walk) | out of a skeleton's swing, not out of the room |
| Dodge time | 9 frames (0.3 s): 53 a second | quick, visible |
| Dodge i-frames | frames 1-7 (0.23 s): no blow, projectile or effect takes health or staggers | the last 2 frames are the landing |
| Dodge cooldown | 75 frames (2.5 s), no mana | a button pressed every fight, not spammed |
| Dodge rules | not while DISABLED/HELD (a stagger, a stun); cuts his own wind-up (no blow lands), not a skill's gesture; passes through creatures, never ends inside one (shortened to the nearest free spot); stops at stone and the shut gate; refused over a seal (as DASH) | |
| Dodge's clip | a roll (`Roll` / `Dodge_Forward` from the heroes' packs), hurried into 0.3 s | |
| Camera follow | centre on him plus a look-ahead of 8 units along his walk; eased with a 0.15 s time constant (exponential); the look-ahead itself eased 0.4 s so a turn does not swing the view | |
| Camera in modern | zoom (wheel) and turn as now; arrows pan away for a look, eased back 1.0 s after the last arrow is let go | |

The dodge is a skill (the owner's rule: abilities, buffs, stuns live in the skill system): a `DODGE` skill in each
hero's file with `Key = V` as its name (pressed on Space, like STAGGER no slot of the four: `skillsFor` leaves it out,
`bookFor` gives it to him, no ranks, not on the bar but a small cooldown pip by the portrait).

## 4. What changes, where

**Engine** (`docs/plan/2026-09-29-engine-requests.md`): E74 directional move, E75 held keys to orders, E76 attack
toward a point and the hold, E77 camera follow, E78 the modern mouse, E79 the game's rows on Settings and the
letters pressed shown on the bar and the keys page, E80 the dodge's shape; E81 traversal links (after WASD).

**Game, phase 1 (done, branch `wasd`)**: the `Controls` block and record, `DungeonSettings.scheme/letterOf/
useScheme` and its validation; `Main.aimsFor`/`ringsFor` bind and ring each skill on the scheme's letter;
`Main.controls` binds the classic orders only in classic, and in modern hands the client a `KeyMap` with W A S D off
panning (`modernKeyMap`); no edge scroll in modern; the choice read at launch from the user's preferences
(`Controls.remembered`, key `controls`) or `-Dlasthero.controls=modern`. `ModernControlsTest`. File default Klassik.

**Game, phase 2a (done, branch `wasd2`, engine E74/E75/E77 at c5f942f2)**: modern posts `MoveAlong` from
`Hotkeys.onDirection` (`Main.walk`) and follows the hero (`CameraFollow.of(null)`, arrows look away); classic is
unchanged. The launch screen, the player screens and a conversation or cutscene hold the keys (`holdsKeys`); a story
that holds the hero is covered the way a click is today, on the client: the keys give `NONE` once and nothing more
is sent. The bag is no opening window (it is on the screen all game), so it holds nothing. The file stays Klassik:
without E78 there is no left-click attack (A, the classic attack key, is a step now) and without E79 no row to
switch back, so modern is reachable only by `-Dlasthero.controls=modern` for playtests.

**Game, phase 2b (done, branch `wasd3`, engine E76/E78/E79/E80 at f4422800)**: modern is the default
(`Scheme = MODERN`). The mouse is `MouseScheme.MODERN`; the knight's `SwingArcDegrees = 120`; every hero carries a
`DODGE` (Key V as its name, pressed on Space through `Hotkeys.on(' ')`, cast by `Main.roll` the way he walks or
faces, refused while a screen holds the keys) at the numbers above -- the rogue's clip UAL Roll, the mage's KayKit
Jump_Full_Short, the knight's own Cast_Charge until a roll is retargeted onto his rig; it is off his four and his bar
(as his stagger), no icon of its own yet. The bar draws and presses each slot on the scheme's letter
(`HeroCard.skills(.., letterOf)`), and modern shows no order buttons. Settings has the row "Boshqaruv (keyingi
ochilishda)" (`Controls.ROW`): it takes hold at the next launch, since the keys, the mouse, the camera and the bar
change together; `-Dlasthero.controls` is for tests. Not yet: the HeroBrain's own auto-fighting is unchanged in
modern, and the item card's `+1` to a skill still names its own key. E81 (traversal links) waits for section 5.

**Game, phase 2c (done, branch `wasd4`)**: in modern the hero picks no fight of his own. The client sends
`FightOnlyWhenTold` once his hero is chosen (an order, so every machine of a party agrees); `HeroBrain` then never
auto-acquires or chases, and its `HeroBrain.Module` holds his weapon's fire while it was given nothing, so the
engine's idle weapon picks nothing either (E87 asks the engine for a per-unit flag). A lock-on fights its target to its
death and he stands; classic is as ever. An item's "+1" to a skill names the letter pressed (`Items` through
`letterOf`): "+1 E mahorati" for the W skill, modern.

**Game, phase 2 (the rest)**:
- `Main`: post `MoveAlong` from E75's hook; LMB/RMB through E78; Space to the dodge; the camera into follow (E77);
  the Settings row (E79), its change saved to the preferences and applied at once (rebind the hotkeys, KeyMap,
  edge scroll, camera).
- `HeroBrain`: in modern no auto-acquire and no chase of his own; the lock-on is the engine's `AttackObject`.
- Hero files: a `DODGE` skill each (Key V, the numbers above, the roll clip); `skillsFor` leaves it out as STAGGER.
- HUD: the bar's letters and the order buttons follow the scheme (classic shows F A S D buttons; modern shows none,
  or LMB/RMB/Space hints in the key-hints row); the item card's `+1 to the W skill` names the letter pressed.
- Input owners: the `LaunchScreen` (splash and loading) swallows all input while it is up, as the dialogue and the
  player screens take their keys; WASD, LMB/RMB and Space must give `MoveAlong(NONE)` / nothing while any of them
  holds the input (E75's "a game screen that takes the keys"), and a key held as the splash ends starts no walk until
  it is pressed again.
- `controls.duke`: `Scheme = MODERN`.
- Tests: an `AttackToward` swing strikes the one enemy in the arc and none behind him; a `MoveAlong` hero walks,
  slides along a wall, and stops on `NONE`, the same on two peers (checksum); a dodge passes through a skeleton
  untouched inside its i-frames and is hit after; the lock-on ends on a WASD order; the Settings row flips the keys.

**Gamepad (later, not now)**: the left stick is `MoveAlong`'s angle as it stands; the right stick an aim vector that
replaces the cursor's point (`AttackToward` and quick-cast at his position plus aim times reach); buttons map onto
the same letters. Nothing above assumes a mouse in the simulation.

## 5. Later: traversal points (after WASD; the owner approved 2026-10-15)

Diablo IV / Lost Ark style: links placed on the map between two points: **jump** a gap or broken bridge, **vault** a
low wall, fence or fallen tree, **climb** up or **drop** down a ledge. No free jumping, no gravity, no platforms.

- **Use**: Space near a point (within 6 units, the point's prompt shown; Space is the dodge everywhere else) or RMB
  on it. He plays its clip (`Jump`, `Vault`, `Climb`, `Drop`) and is moved from A to B along a scripted path; no
  orders and no pushes meanwhile, blows still land (no i-frames).
- **Dodge over low obstacles**: a dodge whose line crosses a `Low` link (a fence, a fallen tree) clears it as part of
  the dodge.
- **Engine**: the move itself exists: E40's `ScriptedMove.along(thing, to, frames, arcHeight)` (straight or arced,
  Locomotor-free, keeps busy, `onLanded`) with E67's `GameObject.busy(clip, frames)` for the clip. So the game can
  build a point-and-press link today. What needs the engine is **E81**: a traversal link in the path graph, so a click
  or an AI route through the link uses it (walks to A, traverses, walks on), and a link the editor can place.
- **Content**: 15-20 points on the open world, some leading to hidden chests or shortcuts; placed in duke-plugin's
  campaign editor once it can (the plugin is ours: a `Traversal` piece with its two ends and kind).
