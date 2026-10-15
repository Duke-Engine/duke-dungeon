# The seamless campaign — Act 1 folded into the one open world

The owner, 2026-10-15: the campaign is played in **one** open world (`maps/world`), with no loading screen between its
steps. The queen's palace, the training and the opening battle become places of the world; the menu's backdrop battle
is the opening battle; "New game" turns that battle into the intro; the hero lands in a meteor, the battle holds while
he is told who he is, and he follows the commander to the palace through a world that is closed but for the way there.

Built on Act 1 (`2026-10-15-act1.md`), the story scripting (`2026-10-08-story-scripting.md`), quests
(`2026-10-08-dialogue-and-quests.md`) and saving (`2026-10-08-saving.md`). Every word is in the data, in Uzbek. What the
engine cannot do yet is asked as E82–E85 (`2026-09-29-engine-requests.md`); the game does the closest version now.

## 1. The places, on the world

All written by `WorldMapWriter` (the world file stays exactly what its seed writes: `WorldMapTest`), found from the
city's middle `C` and the north road, so a reseeded world still has them. At the world's own seed:

| Zone | What | At (cells) |
|---|---|---|
| `Darvoza_oldi` | the field before the city's north gate, the road through it: the opening battle | circle, `F` ≈ C + (3, −34), r 13 |
| `Saf` | the kingdom's line, between the field and the gate | rect 9 × 2 at F + (−4, +6) |
| `Dushman_safi` | the enemy's line, the far side of the field | circle F + (0, −8), r 3 |
| `Qonish` | where he lands: the field's east flank, clear of both lines | circle F + (9, 0), r 1 |
| `Yo'lak` | the corridor from the field down the road into the north gate | rect 9 wide, F.y to the gate |
| `Eski_qabrlar` | the old graves west of the field: the training | circle, flat ground nearest F + (−16, +2), r 6 |
| `Saroy` | the palace: the royal castle's walled court inside the city (its own fort ring, its gate onto the plaza) | rect 8 × 8 inside the ring |
| `Taxt` | the queen's throne, in the court | circle r 1 |
| `Bozor` | the plaza, where the merchant stands | circle C, r 3 |
| `Yerto'la` | the blacksmith's cellar, the city's north-east corner | circle C + (8, −8), r 2 |

Dressing (scenery only, nothing new stands in the way): the throne on a dais of floor tiles with banners and two
columns behind it; at the field two banner posts and spears; at the graves nine grave mounds in three rows, a sword
leaning at the head of each, a dead tree, candles, two column stumps; in the cellar corner crates, barrels and a shelf.

The palace is the **enclosed walled court** (the royal castle's ring), not a roofed interior: the court is entered by
its gate and the walls stand in the way, which the world already draws. A roof hidden on entry is not asked for now.

The old Act 1 maps `jang`, `poytaxt` (and the `mashq` descent) leave the chain. Their files stay (the stages list and
tests use them); `maps/first` is untouched.

## 2. The chain now

| # | Joint (`--act1=N`) | Where | What |
|---|---|---|---|
| 1 | the battle | `world`, `Darvoza_oldi` | the intro (section 4), the follow (section 5) to the palace |
| 2 | the palace | `world`, `Saroy` | the queen, her gold and the medallion; *Mashq* sends him to the old graves |
| 3 | the training | `world`, `Eski_qabrlar` | skeletons and the graves' keeper (a Reaper) rise; back to the palace: the war council |
| 4 | the war | `urush` (the descent's keeps) | unchanged: still a floor change — the keeps are the descent's generated floors |
| 5 | the dark castle | `qora_qasr` | unchanged |

Joints 1–3 are one map: no loading screen between them. The war and the castle stay their own maps until the world has
keeps of its own (Act 2's forts are the world's; folding the war into them is the next step, not this one).

The act's world story runs only with the flag `Act1` (every act-1 joint sets it), as Act 2's runs with `Act2`: the
open world played off the stages list, and Act 2, stay as they were.

## 3. The backdrop is the opening battle

The front menu's scene (`MenuScene`) is the world itself, at `Darvoza_oldi`, built from the same `Battle` block the
story lays (`data/story/act1.duke`): the kingdom's soldiers at `Saf`, the enemy army at `Dushman_safi`, the enemy
commander behind it, and the three ritualists round `Qonish` — the same spot, the same armies. The fallen come back at
their line after a while, so it never ends. The camera stands where play's intro first frame stands.

The launch screen loads the world once for the backdrop. **"New game"** starts the match, which is built on the same
world and opens on the same framing with the same armies, so what the player sees goes on. The match still loads under
the bar (about 0.6 s for the world from warm): the engine tears the backdrop down when a match starts, and keeping it
as the match is **E83**.

## 4. The intro, beat by beat

Frames at 30 a second. Every number is the data's (`data/story/act1.duke`: the triggers' Delays, the `Ritual`,
`Shockwave` and cutscene blocks), not code.

| t (frames) | Beat |
|---|---|
| 0 | `MAP_START` (Act1, not landed): the card *Darvoza oldidagi jang*; `BATTLE Opening` lays both armies, the commander, the three ritualists (neutral: section 4.1); the hero is held high in the sky over `Qonish`, out of sight. Cutscene `Summoning`: the menu's framing over the field (45), the line *BattleHolds*, then the camera eases (75, IN_OUT) to the ritualists, drawn in and lower. |
| seen +0 | `POSE Ritualist Ritual` (150): arms spread wide to the sides, faces up to the sky (0–100), then hands snap together forward (100–115) and held out (115–150). Their motes rise (`RitualGlow`). A cutscene holds the camera on `Qonish` through to the landing. |
| +105 | `BEAM`: a bright beam from each ritualist's hands to a point 40 above `Qonish` (laser `RitualBeam`, 100 frames): three beams from three sides meeting in the middle. |
| +120 | the pillar of light: `SkyPillar` (a PILLAR of light, motes, a light) and the laser `SkyPillarBeam` from the ground at `Qonish` up 900 (90 frames): the beams feed it. |
| +150 | `STREAK Hero`: the meteor. He is carried from 700 above and 400 off to the south-west down onto `Qonish` in 24 frames, burning (`MeteorTrail` on him every frame of it). |
| +174 | **Impact** (`MeteorImpact`): a white flash, a shockwave ring, dust, a scorched crater lying 20 s, a light, the biggest knock of the camera any look may have (0.8 s, 2.0: `SkillLookTest`), a low boom (`landing.impact`). `SHOCKWAVE`: every enemy within 160 thrown 50 outward in an arc 18 high over 20 frames and stunned (the Combat StunLook over them). `HOLD Darvoza_oldi`: every fighter on the field held — the battle stops. The hero kneels in the smoke (`HeroLanding`, his kneel-and-rise clip, 70). The ritualists vanish in a flash (`RitualistVanish`, then `REMOVE`). Flag `HeroLanded`. |
| +249 | Cutscene `Landing`: low and close; *HeroLands* (he was training with Erika a moment ago; the commander: the light chose you), then *AfterLanding* (they are beaten; you are the last hero summoned; the queen knows the way home; follow me). |
| seen | `RELEASE`; the soldiers finish the stunned (`KILL` each enemy kind); the quest *Qo'mondon izidan* (section 5); flag `BattleWon`; `CHECKPOINT`. |

**The hero does not fight before the dialogue ends**: from the impact to `RELEASE` he is held as every fighter on the
field is, and the enemies are stunned and thrown clear of him. Played headless with every line read at once, the order
is the same (`SeamlessIntroTest`).

### 4.1 The ritualists

Three, round `Qonish` on an equilateral triangle: 30 out (data: `Battle.Ring`), 120° apart from the north, each facing
the middle, so the three beams come from three sides. They are a `Ritualist` Monster drawn as the licensed Ritualist
(`SkeletonSummoner`'s look: the stand-in skeleton mage where licensed-assets/ is not shipped), with no weapon and no
skills, and stand on the **neutral side** (player 0), which nobody is the enemy of: nothing targets or hurts them, an
area blow does not take them in, and they attack nobody. The armies' lines are 8 or more cells from the triangle. After
the landing they **vanish in a flash**: nobody on the field saw where they went (the queen's hidden hand; the turning
says the rest).

Their clip: `Ritual`, made on the licensed rig by `art/licensed/export_licensed.py` (posed, not borrowed). The stand-in
has no rig of ours to pose cheaply, so it plays its summoning gesture (`Ranged_Magic_Summon`): `POSE` names both
(`Clip`, `Else`), and the look drawn picks.

### 4.2 The hero's landing clip

`HeroLanding` on the Knight (Lange) and the Rogue (the Huntress): the superhero landing — one knee down, a fist to the
ground, the head bowed — held, then rising to the idle stance. Posed from each rig's own Cook (`Fixing_Kneeling`) as the
kneel and its Idle as the stand, blended (`art/anim/hero_landing.py`, run over the shipped `.glb`s). Played as his busy
clip (E67) for its 70 frames.

### 4.3 The knock-back and the hold (game-side, E82 asked)

`SHOCKWAVE` reads a `Shockwave` block (Radius, Distance, Height, Frames, StunFrames). Each enemy (the dungeon's side,
alive, with a body) within Radius is carried by the engine's `ScriptedMove` (E40) along an arc outward from the middle,
stopping short of stone, and stunned by the engine's own stun (`StatusUpdate` DISABLED, as `SkillBook.daze`), wearing
the Combat block's StunLook. The skill system has no outward push (`PULL` draws in): **E82** asks `SkillEffect.PUSH`;
when it lands, the story casts it as a skill of the hero's and this act goes.

`HOLD` (a zone) keeps every fighter standing in it DISABLED, renewed each frame, until `RELEASE`: no blows, no steps.

## 5. The follow mission: *Qo'mondon izidan*

A quest step `Goal = FOLLOW`, `Target` an Npc, `Where` a zone:

- the person walks to `Where` (the route prefers the road, E52) while a hero is within the Dialogue block's
  `FollowNear` (90); farther than `FollowFar` (150), they stop, turn to him and wait;
- over their head the `FollowLook` (`FollowMark`: a gold-blue diamond and a soft ring, the quest beam's colour) is
  played every `GlowFrames`, as a quest's glow is: on whoever is to be followed, for as long as the step is;
- the minimap and the world map mark them (E61 `minimapMarks`, the quest's colour);
- done when the person stands in `Where` and a hero is within `FollowNear` of them.

Reusable by any follow mission: nothing in it is the commander's.

At the palace: the queen greets him (her `QueenWelcome`, as before: gold, the medallion), and *Mashq* sends him to the
old graves: reaching them raises six skeletons and the graves' keeper (a Reaper); its death sets `Trained`, he says he
is ready, and *Saroyga qayting* brings him back to the war council (`WarCouncil`), then *Urushga*: out by the field
→ `MAP urush`. The cellar and the ring side quests are the capital's again, in `Yerto'la`.

## 6. The world gated by the story

A `Gate` block: on its map, while its `If` holds (the trigger's conditions), a hero must stand in one of its `Open`
zones. Each frame a hero found outside them is put back where he last stood inside, his walk given up, and he says the
gate's `Word` (*Hozir u yerga bormaymiz*) at most once every three seconds. A loaded save (or a death's checkpoint)
under a gate starts at its `Start` zone. The first gate of the file that holds is the one kept. Visible barriers: the
kingdom's guards (`Soqchi`) stand at the corridor's ends and say the same word when talked to; the fog stays.

| Gate | Holds while | Open |
|---|---|---|
| `Act1Battle` | Act1, not `Welcomed` | `Darvoza_oldi`, `Yo'lak`, `Poytaxt` |
| `Act1Capital` | Act1, `Welcomed`, not `WarBegun` | the same and `Eski_qabrlar` |
| `Act1War` | Act1, `WarBegun` | the same (the war leaves by the field) |

Act 2 has no gate: its world is open, as it was. A later act opens the world step by step by adding gates with wider
`Open` lists under its own flags. The barrier is the game's leash, not the path: a route the player clicks still plans
through the closed land and stops at its edge. **E84** asks for regions of the path grid shut by the game (walls by
flag), so a route never plans through one.

## 7. Old saves

A save on a retired map loads onto the world at the joint that took its place (the Campaign block's `Retired`):

| Saved on | Loads as |
|---|---|
| `jang` | joint 1: the field, the intro again (the battle is where it was) |
| `poytaxt` | joint 2: the palace; with `Trained` set, the war council's |
| `mashq` (the training descent) | joint 3: the old graves |

Its hero, experience, ranks, bag, gold, souls, quests and flags are kept; the joint's flags (`Act1` among them) are
added; the joint's quests started unless going or finished. The save's next autosave is on the world. A save on
`urush` or `qora_qasr` loads as before.

## 8. Engine asks (2026-09-29-engine-requests.md)

- **E82** — `SkillEffect.PUSH`: a radial knock-back with a stun, in the skill system.
- **E83** — the backdrop kept as the match ("New game" with no load: the client hands the running backdrop game over).
- **E84** — path regions shut and opened by the game (story gating that routes respect).
- **E85** — a scripted camera path (a list of points with eases) for the intro's single long move; the cutscene's shots
  stand in.

The follow marker needs no ask: the engine's riding effects (`World.effect(name, thing, bone, offset)`) can carry one;
the game plays its Effect on the person as the quest glow is played, which reads the same.

## 9. Frame cost

The world was 0.088 ms a frame idle (perf report, E60 era). Added each frame: the gate's check (a few zones, one hero),
the hold's renew (only while held), the follow (every CheckFrames). Measured in section 10's report.

## 10. Tests

`WorldMapTest` (the world is what its seed writes), `SeamlessWorldTest` (the regions are there, on walked ground, the
throne inside the palace's ring, the graves and the field outside the city), `MenuSceneTest` (the backdrop is the world
at `Darvoza_oldi` with the Battle's armies; the ritualists 120° apart round `Qonish`, never hurt), `SeamlessIntroTest`
(the order of the beats, the hero held until the dialogue is over, the knock-back throwing and stunning the enemies),
`FollowAndGateTest` (the marker on the commander and on the minimap, she waits, the step done at the palace; a hero
put back at the edge until the flag opens it; a save under a gate loads at its Start), `Act1Test` (the chain, the
joints, old saves migrated).
