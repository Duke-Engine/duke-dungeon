# Action controls: the mouse turns the camera (2026-10-15)

The owner, 2026-10-15: a **third** control scheme beside Zamonaviy and Klassik
(`2026-10-15-modern-controls.md`), the third-person action game's: no pointer in play, the mouse turns the camera
round the hero, the left button strikes ahead of him, the skills on the digits, E works what is in front of him.

Settings: **Boshqaruv: Zamonaviy (WASD) / Klassik (sichqoncha) / Jangovar**. The row's third choice (index 2; a kept
0 or 1 stays what it was), a scheme of its own since E90-E94 landed (phase 2). Data: the `Controls` block (`data/world/controls.duke`),
Java: `world/Controls` (`Scheme.ACTION`, `actionKeys`, `beltKey`, `promptOf`, `walksOnKeys`).

Frames are the simulation's: 30 a second.

## 1. The key map

| Key | Action (Jangovar) | Modern, for comparison |
|---|---|---|
| Mouse motion | turns the camera round him (yaw); no pointer | moves the pointer |
| W A S D | walk, relative to the camera (as modern: `MoveAlong`) | same |
| LMB | strike ahead: melee at the front arc, ranged at the soft lock (section 3); held = keep striking | lock on / swing at the pointer |
| RMB | cancels an armed skill; otherwise nothing (reserved: block/aim later) | interact |
| 1 2 3 4 | the hero's four (slots Q W E R) | belt |
| 5 6 7 8 | the belt (ItemRules `BeltSize = 4`, so the owner's 5 6 7 grows an 8) | none |
| E | interact with the nearest thing in front (section 4) | the W skill |
| Space | dodge (the `DODGE` skill, Key V as its name, as modern) | same |
| Q R F | free (nothing bound in action) | skills |
| G | build a fire | same |
| C L M K | sheet, quests, map, crafting (Hunar) -- the screens | same |
| J | tracker fold | same |
| B / I | bag: the bag is on screen all game, no window key | same |
| H, P | the client's stop, pause | same |
| Alt (held) | the pointer back for as long as it is held (click the HUD, the bar's badges) | none |
| Esc | cancels an armed skill; else the menu | the menu |
| T | calls his horse, gets him on and off it (`data/world/mounts.duke`) | same |

Internal, never pressed: X (STAGGER's name), V (DODGE's name), Z/U (lent skills).

Conflicts checked by `DungeonSettings.validateAction`: the four slots and the belt each on a digit of its own
(1-9), as many belt digits as `BeltSize`, and `InteractKey` a single letter that is not a step (W A S D), a screen's
(C L M K), the tracker's (J), the fire's (G), the horse's (T), H or P. `ActionControlsTest` holds the map: every hero's four on 1-4
asking for what their own keys asked for in classic, none of W A S D Q R F E bound, 5-8 the belt, T the horse.

### Migration of the skill keys

As modern: a skill keeps its own Key (Q W E R in the hero's file) as its **name** everywhere -- `CastSkill`,
`UpgradeSkill`, ranks, the save, an item's `SKILL_RANK:E`. Only the key pressed changes: `ActionSkillKeys = ["Q = 1",
"W = 2", "E = 3", "R = 4"]`. `DungeonSettings.letterOf` gives the digit, so the bar shows 1-4 (`HeroCard.skills(..,
letterOf)`, E79), the keys page names each digit by its skill, and an item's "+1" says "+1 2 mahorati" for the W
skill. The knight's E is a passive: 3 is bound to nothing (as modern's R). The belt's slots draw their key
(`ItemCard`: 5-8 in action). Rebinding (E79's keys page) rebinds the digit as any other key.

## 2. The camera and the pointer (engine: E90, E91)

- **Captured mouse.** In play the pointer is hidden and held in the window; the mouse gives relative motion only.
  Every screen that opens (bag drag, map M, quests L, sheet C, a crafting or trade window, a dialogue, the menus, the
  launch screen, a cutscene) **releases** it: the pointer shows where it was, the camera stops turning. Alt held
  releases it too, for the HUD; let go, captured again. Losing the window's focus releases it.
- **Yaw orbit.** Mouse x turns the camera round the followed hero (E77's `CameraFollow`): `yaw += dx * sensitivity`
  (default 0.25 degrees a pixel, a Settings row later), `invertX` an option. Pitch stays the scheme's fixed angle
  (the follow camera's), mouse y clamped to a tight band (plus or minus 8 degrees) or ignored (`pitchBand = 0`, the
  default). Zoom stays on the wheel. The arrows' look-away (E77) is off in action: the mouse is the look.
- **Client only.** The yaw is the camera's, never in the simulation or the checksum. What the simulation hears is the
  orders the yaw shapes (section 5).

## 3. Attacking ahead (engine: E93)

The hero faces the camera's forward when he strikes: LMB turns him to it (his facing is the order's, not the camera's).

- **Melee (knight).** LMB sends `AttackToward(point, held=true)` (E76) with point = his position + forward x his
  reach (11). E76's swing then strikes the one enemy nearest his front within reach plus its radius in the
  120-degree arc (`SwingArcDegrees`). Held: resent when the camera's forward moved 1/256 of a turn or more, at most
  every 3 frames (E78's resend rule on the angle instead of the ground point). Let go: `held=false`.
- **Ranged (rogue, mage): soft lock.** On press the client picks the **nearest enemy whose centre is within a 30-degree
  cone (15 each side) of the camera's forward and within his `AttackRange`**, visible (not under the fog) -- ties to
  the smaller angle. Found: `AttackObject` on it (the lock; E76's lock-on rules: leash 90, ends on WASD, a dodge,
  another LMB). Held: if the locked one dies the next press-tick picks again. None found: `AttackToward` along the
  forward at his full `AttackRange` (a missed shot is a miss). The soft-locked enemy wears the target ring while
  held, and the cone's candidate wears a faint one while LMB is up (so the player sees who he would shoot).
- **Which is which**: his weapon's range: `AttackRange` over 20 is ranged (rogue 60, mage), else melee.

## 4. Skills aimed by the mouse's motion (engine: E92)

- **SELF** skills (`Aim.SELF`, the passives excluded) cast at once on the digit.
- **Aimed** skills (UNIT, GROUND, OPEN_GROUND): the digit **arms** the skill: its indicator (the existing E-numbered
  aims: the lane for SKILLSHOT/WAVE/SKEWER, the circle at a spot for AREA_AT_SPOT/METEOR/DASH/BLINK, the circle on a
  creature for STRIKE/HEAL, the cone where one is) is drawn **from the hero along the camera's forward**. While armed,
  mouse x still turns the camera and the indicator with it; mouse y pushes the aim point nearer or further along the
  forward, clamped to the skill's reach (`SkillRange.reach`), starting at its reach (a lane) or at 2/3 of it (a spot).
  UNIT skills snap the circle to the enemy (a heal: the friend) nearest the aim point within 6 units, as quick-cast.
- **Confirm**: LMB, or the same digit pressed again. **Release-to-cast** (an option, off by default): letting go of
  the digit casts. **Cancel**: RMB or Esc, or another digit arms that skill instead. Nothing is spent until confirm.
- **Order**: `CastSkill(player, key, unit, point)` as today, the point quantized as the client already rounds.

## 5. Determinism

Nothing the simulation does may read the mouse or the camera. As modern (section 2 there):

- WASD: `MoveAlong(angle)` with the angle = the keys' screen direction turned by the camera's yaw, quantized to 1/256
  of a turn, sent only when the byte changes. In action the yaw moves with the mouse, so a held W while turning sends
  at most one order a 1.4-degree step, capped at one every 2 frames (a flick of the mouse is ~10 orders, not 100).
- LMB: `AttackToward` with a point the client computes from the quantized forward angle (byte) and his position
  rounded as orders are; or `AttackObject` on the soft lock's id.
- Skills: `CastSkill` with the confirmed point/unit. Interact: the context order the click would have sent (section
  6), with the thing's id. Dodge: as modern, the walk's angle or the forward.
- Two machines that read the mouse to different floats post the same orders, since the angle is a byte and the
  pick is made on the poster's machine only (the order carries its result).

## 6. Interact (engine: E94)

- **Who**: among the things a click would give a context order (E20 rules: `addContextOrder`, `groundOrder` for map
  scenery, a thing on the floor's `PickUp`, the gate's `ToTheGate`), the nearest within **reach 14** (his `Reach` 10
  from food.duke plus a margin) whose centre is within a **100-degree cone** (50 each side) of **his facing** while he
  stands, or of the camera's forward while he walks; ties to the smaller angle, then the nearer.
- **Prompt**: over it, `"[E] " + word`, the key as bound (E79), the word from `Controls.promptOf(order, key)`:
  Talk Gaplashish, Chop Kesish, Harvest Terish, Fill Suv olish, Mine Qazish, Cook Pishirish, PickUp Olish,
  ToTheGate Darvozaga, Ride Minish
  (English rows in `data/lang/en.duke`). An order with no word shows no prompt and is not offered. Not while a screen
  holds the keys, a story holds him, or he is DISABLED.
- **Press E**: the same order a right-click on it gives today (the engine's context order: walk up to it and do the
  word), with the thing's id.

## 6b. Riding (`2026-10-15-mounts.md`, `run/Stable`)

The hero on his horse is the same unit drawn as his riding look, his legs twice as fast, so the action scheme rides
as it walks: no new order.

| | Action | Modern / classic (unchanged) |
|---|---|---|
| Call / mount / dismount | T, as every scheme (`Mount` Key; validated against every scheme's keys) | T |
| Mount a horse standing by | E on it: the prompt "[E] Minish" (the `Ride` context order, E94) | RMB on it |
| Ride | W A S D relative to the camera, the mouse turns the camera (E91) | WASD / right-click |
| Space on horseback | **dismount** (no roll): the dodge is a `CastSkill`, and a skill pressed riding gets him off (`Dungeon`: `CastSkill when mounted`) | same in modern |
| LMB, 1-4 on horseback | dismount, as modern: an ordered attack (`Stable.tick`) or a cast unseats him | same |
| A blow, a town, indoors | dismount (`Stable`) | same |

Space is a dismount rather than a gallop burst: it costs nothing new and is what the code does already; a burst
would be a skill (the owner's rule) -- a `MOVE_SPEED` buff with a cooldown under E89's `MOUNT` -- if a playtest
asks for one. No engine request of its own: E94 finds the horse as any context-order target, E91 orbits the camera
round the riding hero as round the walking one; E88/E89 (the rider on the horse's bone, the `MOUNT` skill) change
nothing here.

## 7. Engine requests (in `2026-09-29-engine-requests.md`)

| | What | Why |
|---|---|---|
| E90 | captured mouse: hidden pointer, relative motion events, released by screens/menus/Alt/focus | no pointer in play |
| E91 | `CameraFollow` yaw orbit from mouse x, sensitivity, invert, pitch band | the camera turns round him |
| E92 | aiming by relative motion: arm, indicator from the hero along forward, confirm/cancel/release-to-cast | skills without a pointer |
| E93 | `MouseScheme.ACTION`: LMB along the camera's forward, soft-lock cone for ranged, resend by angle | strike ahead |
| E94 | interact-nearest: the context-order target in a front cone within reach, its word, a prompt hook, a key | E |
| E96 | E94 over the map's scenery too (the ground rule's trees) | E on a forest tree |

Not asked: digits on the bar (E79's `letterOf` draws any key), the belt on 5-8 (game-side), the dodge (E80).

## 8. Phases

**Phase 1 (done, branch `action`)**: this spec, E90-E94; `Scheme.ACTION` (row index 2), `ActionSkillKeys`,
`ActionBeltKeys`, `InteractKey`, `InteractOrders`/`InteractWords` and `promptOf`; `validateAction`; the third
Settings choice; in action the game plays as modern (`Controls.walksOnKeys`: WASD, follow camera, the dodge,
`MouseScheme.MODERN`, `FightOnlyWhenTold`, no order buttons) with the skills on 1-4 and the belt on 5-8 (the belt
slots draw their key); E is left unbound until E94. `ActionControlsTest`. File default stays MODERN.

**Phase 2 (done, branch `action2`, engine E90-E94 at db83232a)**: in action `Main` holds the mouse
(`captureMouse`), the follow camera orbits by it (`CameraFollow.orbiting(ORBIT_SENSITIVITY, false, 0)`),
`MouseScheme.ACTION` strikes ahead, `Hotkeys.aimBy(FORWARD)` aims 1-4 ahead of him, and
`onInteract(E, 14, 100, Controls::promptOf)` works the nearest thing in front (a person, a bush, a well, a fire, a thing
on the floor, the gate, his horse). The row's word is plain "Jangovar"; modern and classic are unchanged.

Not yet: E on a scenery tree. The open world's forest is map scenery, worked through the ground rule, which E94 does
not search, so in action (no pointer) a forest tree waits for **E96**. Also later: Settings rows for the sensitivity
and invert, and `DrinkHint`'s "1-4".
