# The final fight

The campaign's "What exists and what is new" item 7 (`2026-10-01-campaign.md`, "The end"): in the queen's hall she
calls the cursed hero — act 1's hero — to her side; the heroine beats him but does not kill him; she kills the queen;
the medallion breaks, the souls rise, the ending. Built on the story scripting of
`2026-10-08-story-scripting.md` (triggers, cutscenes, the story-map win rule) and the saving of
`2026-10-08-saving.md`. Choices the owner left open are marked **default**.

## A boss that cannot be killed, only beaten

A Monster that cannot be killed, only beaten, says so in its own block, in the skill system (the owner's rule:
passives are skills): an **`UNYIELDING`** passive (the engine's, E28), its `HealBelowPercent` the share of its most
health it is held at (**default** 25 for the cursed hero). A `Boss` block names a Monster the story fights. Beaten —
its book's `SkillBook.isBeaten()` —

- the blow that would take it lower leaves it there (the engine's `BodyModule.setLeastHealth`): a killing blow never
  kills it, and no death is posted;
- it fights no more: it goes over to nobody's side (the engine's neutral player, as the city's people stand on), so
  nothing is its enemy or takes it for one; it is disabled for good (the stun's own status), so its legs, weapon and
  skills stand still; and no blow takes anything from it after;
- it wears `BeatenLook` (an Effect: the **default** `Beaten` is dust and a dim ring as it goes down);
- the story hears it: a trigger's `When = BEATEN`, `Target` the Monster;
- it stays in the world, standing where it was beaten (no kneeling animation yet — the KayKit set has none named;
  **default** it stands disabled).

## Phases

A Boss block's `Phases` are begun in order, each **once**, the next only after the one before it — one a frame. A phase
begins the first frame the boss's health is at or below `Below` percent of its most (**default** 100: at once) and its
`AfterFlag`, if it names one, is set. Begun, it sets:

| Field | Does |
|---|---|
| `DamageTaken` | the percent of every blow it takes from now on — its body's damage scale (**default** 100; 0 is unhurtable) |
| `Skills` | the keys of the skills of its own it casts from now on; empty is all of them. Gaining and losing skills is choosing among those its Monster block writes |
| `Actions` | a trigger's actions, done once, in order. In a phase a `CALL` or an `EFFECT` with no `Zone` is to the boss |

Each phase states its whole state, so a phase that says nothing takes every blow whole and casts everything.

New for triggers and phases:

| When | Target | Fires when |
|---|---|---|
| `BEATEN` | a Monster | one of it is beaten (above) |
| `SEEN` | a cutscene | it has played to its end, or was skipped |

| Act | Target | With | Does |
|---|---|---|---|
| `CALL` | a Monster | `Zone` | every one of it standing on the dungeon's side walks to the zone's middle; in a phase with no Zone, to the boss's side. It fights from there as it always does: it answers whoever comes near |
| `EFFECT` | an Effect | `Zone` | played at the zone's middle, on the ground; in a phase with no Zone, on the boss |
| `WIN` | the banner's words, or blank | | the run ends won, the banner saying them (blank: no banner — there is no generic "won", 2026-10-09) |

**A story map is won by its triggers, never by its boss falling** (2026-10-09): its boss's death is a story event
(`DIED`), and the map is won when the story says so. As every story map, it stays won.

Every name is checked when the files are read: a Boss that is no Monster, a `Below` outside
1–100, a CALL of no Monster, an EFFECT naming no Effect block, and — outside a phase — a CALL or EFFECT with no Zone.

## The queen's fight

- **On her throne** (phase 1, at once): no blow takes anything from her; she casts only her fire (Q).
- **The fight begins** when the intro is seen (`SEEN QueenIntro` → flag `FightBegins`; phase 2): she says her line
  and calls the cursed hero to her side; she fights from afar (her Monster block's `KeepDistance`), still unhurtable,
  while he fights for her.
- **He is beaten** (`BEATEN CursedHero`): the heroine says she will not kill him, flag `CursedBeaten`; phase 3: the
  queen fights herself — every blow hers to take, every skill hers (Q fire, W meteor) — with a violet flash.
- **She dies** (`DIED QueenBoss`): flag `MedallionBroken` (the story event the medallion's own work may hear), the souls
  rise (`SoulsRise`: three waves of pale wisps out of the throne into the sky, a column of light and its glow — all
  data), and the ending cutscene (`QueenEnd`: the camera to the throne, her last words, the heroine's, and his — himself
  again). Seen, `WIN` with the words *La'nat ko'tarildi*.

The souls rise at the throne zone, where she fights, rather than where she fell: a trigger knows the zone, not the
corpse (**default**; an EFFECT on a dead creature is a later step if wanted).

## The demo: `maps/hall` — "Malika zali"

A map of its own (`maps/first` untouched): an antechamber where the heroine comes in, a corridor, and the hall with
pillars and two statues by the throne. The queen stands on the throne (the map's `Boss`); the cursed hero waits in the
hall's far corner until she calls. Coming through the hall's door (zone `HallDoor`) plays the intro (`QueenIntro`:
the camera to the throne, she and the heroine speak).

Stand-in looks, until the owner's models: **the queen** (`QueenBoss`, beside the talking `Queen` of the medallion) is the KayKit mage (`models/heroes/mage.glb`, as the merchant
is) tinted violet, with the mage's staff; **the cursed hero** is the KayKit knight (`models/heroes/knight.glb`)
darkened by `Tint`, the merchant's way, with the knight's sword. Neither is ever drawn into a floor (`MinDepth = 99`,
`Weight = 0`).

Try it: `./gradlew run --args="--map=hall"`, or Play → stages → Malika zali.

## Saving mid-fight

**Saves are off during a boss fight** (**default**, the simplest correct): while a creature a Boss block names stands
unbeaten on the floor, Saqlash and leaving write nothing, and the save from the hall's entry stands. A save is a
checkpoint and a load lays the floor fresh (`2026-10-08-saving.md`), so a phase or a beaten foe could not be put back
on a fresh floor anyway; a save at the entry is exactly the fight from its start. The trigger log in it is as it was at
the door, so the intro, the call and the beating all play again. Once the queen is dead the run is won, and a won run
writes nothing either.

## Deferred

- A kneeling or fallen animation for the beaten.
- The cursed hero as the player's own act-1 hero — his level, skills and things from the act-1 save
  (`2026-10-08-saving.md`, "what act 1 left for act 2").
- The curse lifting from him (his look and side back) — the medallion's work.
- An EFFECT on where a creature died; SPAWN round a boss with no Zone.
