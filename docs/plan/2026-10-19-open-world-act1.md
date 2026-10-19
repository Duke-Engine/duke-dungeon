# Act 1 in the open world: the graves, the caves and the Black Knight

The owner's scenario, 2026-10-19. It replaces Act 1's training at the old graves, the war's descent floors (`urush`)
and the dark castle (`qora_qasr`). Everything happens on the open world. Act 2, the heroine's, stays as it is.

## The story, in order

1. **The landing.** The battle is going when the summoners drop the hero. The commander tells him to fight.
2. **The first wave.** One wave of monsters comes out of the graves. When it is dead, only the commander and the hero
   are left standing. The hero cannot die here: his health stops at one.
3. **The retreat.** The commander says the two of them cannot hold alone and they fall back to the city.
4. **The queen.** In the city they talk to the queen. She gives them soldiers to destroy the graves.
5. **The graves.** They go back to the graves with the soldiers (the mechanic is below).
6. **The cave.** After the graves, the entrance of a cave can be seen.
   - Hero: "What is that?"
   - Commander: "A cave, full of monsters. Since the Black Knight's rule there are more and more of them. To destroy one,
     every monster in it has to be killed. Nobody who went in has come back yet."
7. **The Black Knight.** The hero asks where the Black Knight's camp is. The commander says it is in the north.
8. **The city is attacked.** The commander: "We must go back and defend the city."
   - Hero: "Go. I will find the Black Knight and put an end to all of this."
   - Commander: "Good luck. Before you reach him, destroy as many caves and graves as you can: they give him strength."
9. **The open world.** It opens, with towns, villages, graves and caves. The hero explores it on his way to the Black
   Knight's camp in the north.
10. **The Black Knight.** His health and strength depend on how many graves and caves are left (see below).

## A grave

- Its **raiser**, a mage, stands in front of it. He has an aura round the graves and plays a casting animation while
  monsters keep climbing out of them.
- While the raiser lives, the **graves cannot be harmed**.
- When the raiser is dead, each grave can be struck and **destroyed one by one**.
- The graves raise monsters on a beat, with a cap on how many stand at once. They stop when the graves are gone.
- **What a kill gives:** the first `Paying` monsters of a grave (9 by default) give experience, drops and gold. After
  that its monsters come out **changed**: tinted blue-black, harder hitting, and giving **no experience, no drop and no
  gold**. This makes farming a grave pointless.
- A destroyed grave is counted. It stays destroyed across saves.

## A cave

- An entrance on the open world. Entering it lays a generated floor, as the descent does today.
- It is **destroyed when every monster on its floor is dead**. It is then counted, and its entrance is shown as sealed.
- The cap on paying kills does not apply: a cave is finite.

## The Black Knight's strength

- `left = (graves + caves left) / (graves + caves in all)`
- His health and damage are `floor + (1 − floor) × left` of their full values, with the floor at 50%. A player who
  cleared everything still fights a real boss.
- The quest log shows it as a line, for example "Qora ritsar kuchi: 70%", so every grave cleared is felt.

## What is reused

| Part | What it reuses |
|------|----------------|
| Graves and caves as places | the world's camps and zones (`CLEARED`, zone holds) |
| The commander's walk | the FOLLOW step |
| The sayings | the Conversation, Trigger and Cutscene data |
| A cave's floor | the generated descent floor, entered from a point on the world |
| The changed monsters | an Effect tint and a status in the skill system (everything goes through skills) |
| The hero who cannot die in the first wave | a skill-system status |

## What is new

- **A grave spawner.** A placed thing that raises on a beat, has a cap, counts what it has raised and knows which ones
  are changed. It is shielded while its raiser lives.
- **A bounty off.** A monster with no experience, no drop and no gold: a flag on the kill, read where `pay`, the loot
  roll and experience are paid.
- **The Black Knight scaled** by the count left, read when he is placed.
- **World placement** of the graves and the cave entrances (WorldMapWriter sites), and their counts in the save.

## Order of work

1. The graves: spawner, raiser shield, destroy, the paying cap and changed monsters. Tests.
2. The opening rewritten: the landing, the first wave, the retreat, the queen, the soldiers, the graves.
3. The caves: entrance, floor, cleared, sealed. Tests.
4. The commander's farewell, the open world opened, the counts in the save.
5. The Black Knight's camp in the north and his scaled fight; the old urush and qora_qasr joints retired.
6. The campaign walk test rewritten end to end.
