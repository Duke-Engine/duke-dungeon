# Credits

Art and audio the engine and its games are built from, and what each of them asks
for in return. Where a licence requires attribution it is given here; where it
only invites it, it is given anyway.

The licence text as it shipped is kept beside the files themselves, in a
`License.txt` in each folder — this page is the index, not the terms.

The code itself is under the MIT License — see `LICENSE`. None of the assets below
are: each keeps the terms given here.

**Adding an asset means adding a row here.** See the rule in `CLAUDE.md`.

## The kit

The starter set every game may draw from — the effects, and the art they are drawn with. Everything
lives under `kit/src/main/resources/`, so the paths below are written from there.

| What | Who | Licence | Where |
|---|---|---|---|
| Particle Pack — every flame, smoke, spark, slash and ring the skills are drawn with | Kenney | CC0 | `kit/effects/particles/` |
| Two columns of light — the gradients a level and an arrival are drawn with | made for this game, by `dungeon/art/effects/pillar_textures.py` | CC0 | `kit/effects/particles/pillar_{core,halo}.png` |

## Duke Dungeon

Everything lives under `dungeon/src/main/resources/`, so the paths below are
written from there.

| What | Who | Licence | Where |
|---|---|---|---|
| Skill, command, figure and attribute icons — all 26, painted in the item icons' look by `art/icons/ItemIcons.java` | **Lorc, Delapouite, Skoll** ([game-icons.net](https://game-icons.net)); Zeromancer's heart-plus | **CC BY 3.0** — attribution required, given here; Zeromancer's CC0 | `icons/{skills,commands,stats}/` |
| The coins a kill throws out of the corpse — Lorc's crown coin, filled with gold by `art/icons/ItemIcons.java` | **Lorc** ([game-icons.net](https://game-icons.net)) | **CC BY 3.0** — attribution required, given here | `effects/coin.png` |
| The marks an aura's bearer wears on the floor — a ring of runes, ripples, and a sigil of eight points | made for this game, by `art/effects/AuraDecals.java` | CC0 | `effects/aura_{runes,ripples,sigil}.png` |
| The knight's sword wave — a flat half-moon of white steel, the body his Q flies as | made for this game, built and exported with Blender by `art/effects/knight_wave.py` | CC0 | `models/effects/knight_wave.glb` |
| The ground a painted map is drawn with — grass, dirt, rock, snow and sand, a picture of noise and one of variation | made for this game, painted from a seed by `src/main/java/uz/dukeengine/dungeon/tools/GroundPictures.java` (`./gradlew writeGroundPictures`) | CC0 | `ground/*.png` |
| Licensed models: the queen — "Dark Elf Shadow Enchantress", rigged and animated for the game by `art/licensed/export_licensed.py` | CGTrader seller of [the model](https://www.cgtrader.com/free-3d-models/character/woman/dark-elf-shadow-enchantress-character) | **CGTrader Royalty Free** — shipped in the game only, not in this repository (git-ignored `licensed-assets/`); a stand-in where it is absent | `models/licensed/queen.glb` |
| Licensed models: the Skeleton Summoner — "Dark Fantasy Female Ritualist", rigged and animated the same way | **artistofthemonth** ([CGTrader](https://www.cgtrader.com/free-3d-models/character/woman/dark-fantasy-female-ritualist-low-and-high-poly)) | **CGTrader Royalty Free** — shipped in the game only, not in this repository | `models/licensed/ritualist.glb` |
| Dungeon Pack — floors, walls, stairs | **KayKit** / Kay Lousberg ([kaylousberg.com](https://kaylousberg.com)) | CC0 | `models/tiles/dungeon/` |
| …and its pillars, barrel and chest | KayKit | CC0 | `models/props/dungeon/` |
| …and the dirt floors the forest is laid on | KayKit | CC0 | `models/tiles/forest/` |
| …and the wall that retains its terraces | KayKit | CC0 | `models/props/forest/wall.gltf` |
| Forest Nature Pack — the trees that stand where a wall would | KayKit | CC0 | `models/tiles/forest/` |
| …and its rocks and bare trees | KayKit | CC0 | `models/props/forest/` |
| …and the cliff rocks the caverns are bounded by, on its own atlas | KayKit | CC0 | `models/tiles/dungeon/cliff_*.gltf`, `models/tiles/dungeon/forest_texture.png` |
| …and the pines, the dead trees and the rock clusters of the pine wood, the dead land and the mine | KayKit | CC0 | `models/tiles/forest/{pine,pine_tall,tree_twisted,tree_stunted,rock_cluster,rock_cluster_low}.gltf` |
| …and the autumn wood's trees, bushes and grass, on a copy of its atlas with the foliage gradient repainted gold, orange and red | KayKit, repainted here | CC0 (a repaint of a CC0 atlas) | `models/tiles/autumn/` |
| …and more cliffs and spires among the caverns' rocks, bushes among the forest's trees, and the grass, pebbles and stones every biome's floor is strewn with | KayKit | CC0 | `models/tiles/dungeon/{cliff_c,spire_a,spire_b}.gltf`, `models/tiles/forest/{bush_*,grass_*,pebble,stone}.gltf` |
| Resource Bits — the mine's gold, silver and copper ore, broken stone and timber | KayKit | CC0 | `models/props/mine/` |
| The keep's gate — "Old Driveway Gate": two stone pillars with their lamps, and an iron gate | **animatedheaven** ([CGTrader](https://www.cgtrader.com/designers/animatedheaven)) | commercial use with credit | `models/props/gate/` |
| The keep's key — a crown key, lying where the last monster outside the keep fell (its picture in the bag is Delapouite's boss key, in the row below) | from the owner's own asset folder, maker not named | **unconfirmed** — to be asked of the owner before a release | `models/props/key/` |
| Item icons drawn from game-icons.net — the key, the medallion, the locket, the amulet, the ring, the whetstone and the purse's coins, and 86 pictures of the starter library; filled with a gradient, outlined, shadowed and framed by `art/icons/ItemIcons.java` | **Lorc, Delapouite, Skoll, Willdabeast, Faithtoken, Caro Asercion, DarkZaitzev, Seregacthtuf** ([game-icons.net](https://game-icons.net)) | **CC BY 3.0** — attribution required, given here; the maker of each library picture is in its `index.csv` | `icons/items/`, `icons/items/library/` |
| Ravenmore Fantasy Icon Pack 2.0 — the tome, and 29 painted weapons, shields, armour, potions, gems and papers in the starter library, framed by `art/icons/ItemIcons.java` | **Ravenmore** ([ravenmore.itch.io](https://ravenmore.itch.io), [OpenGameArt](https://opengameart.org/node/11073)) | **CC BY 3.0** — attribution required, given here | `icons/items/tome.png`, `icons/items/library/` |
| "Armor Icons by Equipment Slot" — the gauntlet and the boots, and 20 painted pieces of armour in the starter library, keyed and framed by `art/icons/ItemIcons.java` | **Clint Bellanger, Blarumyrran, crowline, Justin Nichol** ([OpenGameArt](https://opengameart.org/content/armor-icons-by-equipment-slot)) | CC0 | `icons/items/{gauntlet,boots}.png`, `icons/items/library/` |
| "OSARE weapon icons" — 13 painted weapons, shields and armour in the starter library, cut, keyed and framed by `art/icons/ItemIcons.java` | **Blarumyrran** ([OpenGameArt](https://opengameart.org/content/osare-weapon-icons)) | CC0 | `icons/items/library/` |
| Fountain — the one at every way in, lifted to stand on its own foot | **Poly by Google** (Google Poly asset `4KKY7CmNe_r`), supplied by the owner | **CC BY 3.0** — attribution required, given here | `models/props/fountain/` |
| "Baba Yaga's hut" — Yalmog'iz kampir's hut on its chicken legs, the old woman, the girl in the cage (and the cage empty, once she is out) and the imp, her cauldron and yard, and the forest round it | **Inuciian** (@Inuciian, [Sketchfab](https://sketchfab.com/3d-models/baba-yagas-hut-479b5f3c493349b18c59dbcbe354d98f)) | **CC BY 4.0** — attribution required, given here; modified: split into parts, recoloured, converted to glTF (`art/inuciian/export_models.py`) | `models/inuciian/` |
| "Huntress" — the Rogue, the campaign's heroine, and her quiver and arrows | **Wilson** (neilhancart, [Sketchfab](https://sketchfab.com/3d-models/huntress-19ceb83a896c4d668b0f30a6fc58d563)) | **CC BY 4.0** — attribution required, given here; modified: pedestal, banner and the arrow in her hand removed, decimated to about 24k triangles, bones cut from 438 to 62, pictures cut to their colour and downsized, a hand bone added for her bow, KayKit's and Quaternius' clips retargeted onto her with her fingers curled, a pale iris and pupil painted into her blank eyes, her skirt reweighted to follow her thighs, converted to glTF (`art/huntress/export_huntress.py`) | `models/heroes/huntress.glb`, `animations/characters/huntress.glb` |
| "Lange - Half-Elf Knight" — the knight, Lange, with his sword, shield, dagger, potion and book | **phucpham1076** ([Sketchfab](https://sketchfab.com/3d-models/lange-half-elf-knight-3c22b831b66c4de88043f57fabca406b)) | **CC BY 4.0** — attribution required, given here; modified: glasses removed and eyes painted in, sword attached, textures downsized, converted to glTF (`art/lange/export_lange.py`) | `models/heroes/lange/` |
| Skeletons — everything that walks the floors, and its blades, axes and staves | KayKit | CC0 | `models/monsters/` |
| …and its atlas repainted with a green robe and a purple one, for the Skeleton Healer and the Skeleton Summoner | repainted from the original KayKit atlas, supplied by the owner | CC0 (a repaint of a CC0 atlas) | `models/monsters/skeleton_texture_{green,purple}.png` |
| The wilds' monsters — wolf, spider, Mushnub Evolved, goblin, Cactoro, dino, demon, Dragon Evolved, Goleling, Tribal, giant, slime, frog, ghost, Glub Evolved: four lands' families, their elites and camp bosses (`data/units/wilds_*.duke`) | **Quaternius** ([quaternius.com](https://quaternius.com); each from its page on [poly.pizza](https://poly.pizza/u/Quaternius), listed in `art/monsters/export_monsters.py` and `models/wilds/License.txt`) | CC0 | `models/wilds/` — four clips each kept and renamed, flat colours baked to one palette picture, rigs flattened to scale 1, converted by `art/monsters/export_monsters.py` |
| The bestiary's creatures — orc and orc grunt, demon, blue demon, imp, Monkroose, mushroom king, ninja, yeti and yeti cub, Armabee and Armabee Evolved, Goleling Evolved (the crowned shade), ghost skull, Hywirl, wasp, rat, bat, dragon (the wyvern), skull, crab, zombie, Hooded Adventurer and Adventurers (the bandits), King (the cursed king), the pirates Anne, Mako and the captain, Sharky, the pirate skeleton, the tentacle, bull, fox, stag and wolf: eight more families with their elites and camp bosses (`data/units/bestiary_*.duke`) | **Quaternius** ([quaternius.com](https://quaternius.com); each from its page on [poly.pizza](https://poly.pizza/u/Quaternius), listed in `art/monsters/export_bestiary.py` and `models/bestiary/License.txt`) | CC0 | `models/bestiary/` — six clips kept and renamed, feet put on the ground, flat colours baked to one palette picture, converted by `art/monsters/export_bestiary.py` |
| …and the zombie ghoul, the big-armed zombie, the half zombie (the crawler), the witch and the Animated Wizard (the cult mage) | **Quaternius** ([poly.pizza](https://poly.pizza/u/Quaternius)) | **CC BY 3.0** — attribution required, given here | `models/bestiary/{zombie_ghoul,zombie_brute,zombie_crawler,witch,cult_mage}.glb` |
| Licensed models: the golems — "Ultimate Golem Elemental Pack", earth, ice, shadow, fire and light; their idle, walk and death Quaternius' Universal Animation Library's, by `art/monsters/export_licensed_monsters.py` | **KaidaStudios** ([CGTrader](https://www.cgtrader.com/free-3d-models/character/fantasy-character/ultimate-golem-elemental-pack)) | **CGTrader Royalty Free** — shipped in the game only, not in this repository (git-ignored `licensed-assets/`); a stand-in where it is absent | `models/licensed/golem.glb`, `golem_*.png` |
| Licensed models: the minotaur — "Animated Minotaur" | **vsify** ([CGTrader](https://www.cgtrader.com/free-3d-models/character/fantasy-character/minotaur-cda47223-1a94-4f7d-bc2b-82344547726d)) | **CGTrader Royalty Free** — shipped in the game only, not in this repository (git-ignored `licensed-assets/`); a stand-in where it is absent | `models/licensed/minotaur.glb` |
| Licensed models: the troll — "Earthborn - Troll" | **souchenki** ([CGTrader](https://www.cgtrader.com/free-3d-models/character/fantasy-character/earthborn-troll)) | **CGTrader Royalty Free** — shipped in the game only, not in this repository (git-ignored `licensed-assets/`); a stand-in where it is absent | `models/licensed/troll.glb` |
| Licensed models: the bandits — "Low poly Bandit (Rigged + Animated)" | **karolis-prokop** ([CGTrader](https://www.cgtrader.com/free-3d-models/character/man/low-poly-bandit)) | **CGTrader Royalty Free** — shipped in the game only, not in this repository (git-ignored `licensed-assets/`); a stand-in where it is absent | `models/licensed/bandit.glb` |
| Licensed models: the cave spiders — "Animated Spiders Pack" | **3d-rina** ([CGTrader](https://www.cgtrader.com/free-3d-models/animal/insect/animated-spiders-pack)) | **CGTrader Royalty Free** — shipped in the game only, not in this repository (git-ignored `licensed-assets/`); a stand-in where it is absent | `models/licensed/spider.glb`, `spider_*.png` |
| Licensed models: the zombie — "Zombie Monster" (rigged and animated); its death Quaternius' Universal Animation Library's | **AmeerHamza3d** ([CGTrader](https://www.cgtrader.com/free-3d-models/character/man/zombie-monster-3d-character-rigged-and-animations-enemy-creature)) | **CGTrader Royalty Free** — shipped in the game only, not in this repository (git-ignored `licensed-assets/`); a stand-in where it is absent | `models/licensed/zombie.glb` |
| Food and fire — Deer, Pig (the boar), Bush with Berries, Pumpkin: the open world's animals, bushes and pumpkin patches (`data/world/food.duke`) | **Quaternius** ([quaternius.com](https://quaternius.com); each from its page on [poly.pizza](https://poly.pizza/u/Quaternius), listed in `models/food/License.txt`) | CC0 | `models/food/` — the animals' four clips kept and renamed, flat colours baked to one palette picture, converted by `art/food/export_food.py` |
| Fireplace Sound loop — a fire's crackle, placed where it burns (`data/world/food.duke`) | **PagDev** ([OpenGameArt](https://opengameart.org/content/fireplace-sound-loop)) | CC0 | `audio/ambience/campfire_loop.ogg` — made mono, Vorbis |
| amb_rain_loop_1 — the rain and the storm over the open world (`data/world/daynight.duke`) | **Kresiek The Furry** ([OpenGameArt](https://opengameart.org/content/amb-rain-loop-1)) | CC0 | `audio/ambience/rain_loop.ogg` — made mono, Vorbis |
| Crickets Ambient Noise (loopable) — the open world at night | **Wolfgang_** ([OpenGameArt](https://opengameart.org/content/crickets-ambient-noise-loopable)) | CC0 | `audio/ambience/night_crickets.ogg` — made mono, Vorbis from MP3 |
| Mild wind background noise — under the highlands' snow | **Bashar3A** ([OpenGameArt](https://opengameart.org/content/mild-wind-background-noise)) | CC0 | `audio/ambience/wind_loop.ogg` — 30 seconds cut, its end crossfaded into its start to loop, made mono, Vorbis |
| 100 CC0 SFX #2 — a storm's thunder | **rubberduck** ([OpenGameArt](https://opengameart.org/content/100-cc0-sfx-2)) | CC0 | `audio/ambience/thunder.ogg` — made mono |
| Campfire — the fires that cook (`data/world/food.duke`) | **Kenney** ([kenney.nl](https://kenney.nl); [poly.pizza](https://poly.pizza/m/i6UFAevfcu)) | CC0 | `models/food/campfire.glb` (`art/food/export_food.py`) |
| Horse — the horse the hero rides on the open world, each hero riding it on its Saddle bone (`data/world/mounts.duke`) | **Quaternius** ([quaternius.com](https://quaternius.com), from his Ultimate Animated Animals; [poly.pizza](https://poly.pizza/m/qvTrSG9pZF)) | CC0 | `models/mounts/` — six clips kept and renamed, flat colours baked to one palette picture, a Saddle bone added, each hero's riding clips made on his own skeleton (`animations/characters/riding_*.glb`, `art/mounts/export_riders.py`); its head framed as the whistle's picture, `icons/items/loot/horse_*.png` |
| The open world's ground — meadow, road, steppe, sand, snow, rock, forest floor, marsh and cliff faces (`WorldMapWriter.palette`) | **Poly Haven** ([polyhaven.com](https://polyhaven.com)): dirt_floor, mud_forest (eye-candy.xyz), withered_grass (Charlotte Baglioni), sand_01, snow_02, rock_08, forest_leaves_02 (Rob Tuytel), rock_face_03 (Dario Barresi, Rico Cilliers); **LuminousDragonGames** ("Blended textures of dirt and grass", [OpenGameArt](https://opengameart.org/content/blended-textures-of-dirt-and-grass), grass18) | CC0 | `ground/world/` — downsized to 512, softened, their colours set beside the game's ground |
| "Horse gallop on different surfaces" — the hooves of the horse and of a hero riding: the CC0 strides (grass, ground, muffled) | **congusbongus** ([OpenGameArt](https://opengameart.org/content/horse-gallop-on-different-surfaces)), from StephenSaldanha, D4XX and MrFizzywig on freesound.org (each CC0) | CC0 | `audio/sfx/horse/gallop_*.ogg` — made mono, Vorbis |
| "Zebra Whinny" — the horse answering his whistle | **Peet J van Eeden**, prepared by antumdeluge ([OpenGameArt](https://opengameart.org/content/zebra-whinny)) | **CC BY 3.0** — attribution required, given here | `audio/sfx/horse/whinny.ogg` — Vorbis |
| Adventurers — the archer and the mage and everything they carry; the knight Lange replaced, kept for the kingdom's soldiers and the Cursed Hero | KayKit | CC0 | `models/heroes/` |
| Character Animations — everything anything down here does | KayKit | CC0 | `animations/characters/` |
| Universal Animation Library — Lange's slash, flinch, fall, the wave's slash and the charge's drive, retargeted onto his rig | **Quaternius** ([quaternius.com](https://quaternius.com)) | CC0 | inside `models/heroes/lange/lange.glb` (`art/lange/retarget.py`) |
| Universal Animation Library — the Huntress's flinch, idle, fall, roll and casting hand, retargeted onto her rig | **Quaternius** ([quaternius.com](https://quaternius.com)) | CC0 | `animations/characters/huntress.glb` |
| "FREE - Elven Assassin Weapon Set Bow & Dagger" — the Huntress's bow and the dagger on her hip | **nodgerty** ([Sketchfab](https://sketchfab.com/3d-models/b8e64cc0c7df472c9f0cb5d6e1acdd3f)) | **CC BY 4.0** — attribution required, given here; modified: split, laid out as the kit's weapons, decimated, pictures cut to their colour and downsized, converted to glTF (`art/weapons/export_weapons.py`) | `models/weapons/elven/` |
| "Low poly medieval weapons free pack" — the commander's and the Cursed Hero's sword, the brute's warhammer, the champion's axe, the runner's dagger, and shields, a bow, a quiver and an arrow not yet carried | **panarth** ([Sketchfab](https://sketchfab.com/3d-models/24db2937c7b947f7917674a617661219)) | **CC BY 4.0** — attribution required, given here; modified: split into a file a weapon, stood up and sized to the kit's sword, converted to glTF (`art/weapons/export_weapons.py`) | `models/weapons/medieval/` |
| "Medieval Fortress Kit" — the fort's walls, gables, gate and gatehouse, towers and keep, ramp and railing, and their dark copies for the queen's castle | **Aya Rady** (ayarady, [Sketchfab](https://sketchfab.com/3d-models/f246ce47e15f407b8399c1b270d782bd)) | **CC BY 4.0** — attribution required, given here; modified: split a file a piece, pivots set on the grid, scaled and stretched to whole cells, the keep and the ramp decimated, pictures cut to their colour and downsized, recoloured dark copies made, converted to glTF (`art/castles/export_castles.py`) | `models/castles/fort/` |
| "modular fortress, Free" — the rampart's towers, walls, arcade and gate | **FelikinRuslan** ([Sketchfab](https://sketchfab.com/3d-models/a229821e9f91413a95d939b2c8412db9)) | **CC BY 4.0** — attribution required, given here; modified: the one mesh cut into its pieces and duplicates dropped, pivots set on the grid, scaled to whole cells, pictures cut to their colour and downsized, converted to glTF (`art/castles/export_castles.py`) | `models/castles/rampart/` |
| "Medieval city wall assets - low poly Modular" — the city's walls, gatehouse and gate, towers, palace towers, house, bridge and stepping stones | **BUKKBEEK** (bukkbeek, [Sketchfab](https://sketchfab.com/3d-models/8aa26a2809194ef6b1414e7bff890e81)) | **CC BY 4.0** — attribution required, given here; modified: split a file a piece, trees and ground left out, pivots set on the grid, scaled to whole cells, converted to glTF (`art/castles/export_castles.py`) | `models/castles/town/` |
| "Medieval castle modular kits (interior)" — the hall's walls, floor, round corner, skeleton plaque and torch | **Pedram Ashoori** (pedramashoori, [Sketchfab](https://sketchfab.com/3d-models/bde29c804d6247c59d29a2ade11e69bc)) | **CC BY 4.0** — attribution required, given here; modified: split a file a piece, pivots set on the grid, scaled to whole cells, pictures cut to their colour, converted to glTF (`art/castles/export_castles.py`) | `models/castles/hall/` |
| "Desert Fortress Walls" — the desert fort's blocks, sloped and tall walls, pillar and buttresses | **Nicolai Kilstrup** (nkilstrup, [Sketchfab](https://sketchfab.com/3d-models/72deaff2bf1b49f295b43bfd7e4ef570)) | **CC BY 4.0** — attribution required, given here; modified: split a file a piece, pivots set on the grid, scaled and stretched to whole cells, decimated to about 5000 triangles a piece, pictures cut to their colour and downsized, converted to glTF (`art/castles/export_castles.py`) | `models/castles/desert/` |
| "Stylized Medieval Castle Room" — the throne room's walls, arch, window, cornice, columns and floor, and its basin, banners, sconce, shelf, barrel, crate, chest, tables, stool and swords; the column, table and basin also the halls' solid props | **SantyFrow** (santyfrow, [Sketchfab](https://sketchfab.com/3d-models/e3d1e0562a9740ed8b759558bcaf0a36)) | **CC BY 4.0** — attribution required, given here; modified: the room taken apart into its pieces, coins and table settings left out, pivots set on the grid, scaled to whole cells, pictures cut to their colour and downsized, converted to glTF (`art/castles/export_castles.py`) | `models/castles/room/` |
| Character Animations 1.1 — Lange's raised hand for the pull, retargeted onto his rig | KayKit | CC0 | inside `models/heroes/lange/lange.glb` (`art/lange/retarget.py`) |
| Standing 2H Magic Area Attack — the mage's meteor | **Mixamo** / Adobe | **not CC0, see below** | `animations/characters/magic.glb` |
| RPG Audio — cloth, coins, a door, a knife drawn | Kenney | CC0 | `audio/sfx/` |
| Impact Sounds — what an arrow and a fist land like, and footsteps | Kenney | CC0 | `audio/sfx/` |
| Interface Sounds — clicks and a gong | Kenney | CC0 | `audio/ui/` |
| Music Jingles — the level-up, a quest taken and a quest done | Kenney | CC0 | `audio/ui/level_up.ogg`, `audio/ui/quest_*.ogg` |
| Fantasy UI Borders — the carved frames the hero panel's edges are painted with | Kenney | CC0 | `ui/borders/` |
| Cursor Pack — the mouse pointers | Kenney | CC0 | `ui/cursors/` |
| Voiceover Pack — what Erika says | Kenney | CC0 | `audio/voice/` |
| Ten pieces of music, from OpenGameArt's "CC0 - Cinematic Music" collection — see below | CodeManu, RandomMind, TAD, cynicmusic, Juhani Junkala, Cethiel | CC0 | `audio/music/` |
| Loopable Dungeon Ambience — under the descent and the castles | JaggedStone | CC0 | `audio/ambience/` |
| 80 CC0 RPG SFX, 80 CC0 Creature SFX, 100 CC0 Metal and Wood SFX — blades, chains, spells, fire, roars, bone, locks, keys, a chest's creak, coins, pages | rubberduck | CC0 | `audio/sfx/`, `audio/ui/` |
| Fantasy Weapons and Apparel SFX Library — arrows drawn and loosed in a volley, boots leaping | Vehicle (Jan Schupke, vehiclemusic.eu) | CC0 | `audio/sfx/skill/` |
| RPG Sound Pack — swings, a sword's ring, magic, ogres, giants, beasts, shades, armour put on, a bottle, coins | artisticdude | CC0 | `audio/sfx/`, `audio/ui/` |
| Male Grunt/Yelling sounds — the knight's, the mage's and the soldiers' pain, shouts and deaths | HaelDB | CC0 (of its dual OGA-BY 3.0 / CC0) | `audio/sfx/voice/` |
| Footsteps (grass/sand and stone) | Fantozzi | CC0 | `audio/sfx/step/` |
| Cinzel — the lettering the menus are set in, baked, and its own Regular and Bold faces (from github.com/NDISCOVER/Cinzel) for the hero bar to bake at the size it is drawn | Natanael Gama / the Cinzel Project Authors | [SIL OFL 1.1](https://openfontlicense.org) | `fonts/` |

### The two that needed a decision, and no longer do

Both are settled, and both the same way. This section is kept rather than deleted
because the reasoning is worth having next time a free asset arrives with terms
attached.

**The hero** came from Mixamo, which is Adobe's and is neither CC0 nor any of the
Creative Commons licences, and no licence text shipped with the download to keep
beside her. The fix was the straight swap this page predicted — *"a CC0 rigged
character of the same shape, of which KayKit's Ranger is one"* — and it turned
out to be exactly that, plus a bow, an arrow and three libraries of movement.

**The monsters** were Quaternius's Bestiary, under the QAL: *"You may not
extract, repackage, sublicense, sell, or otherwise redistribute the Assets… as a
standalone asset, asset pack, stock file, template, or similar product."*
Building and releasing the game was plainly allowed; whether a public git tree
counts as handing the files out was a judgement call rather than a rule, and the
kind of question that does not get easier by being left. They are gone too.

Every model in the tree is CC0, out of one maker's packs, but three: the fountain,
which is CC BY 3.0, the keep's gate, which may be used commercially with credit,
and the keep's key, whose maker is not named and whose terms are not known — each
has its row in the table above. Every clip in `animations/` is CC0 but one.
**That one is a Mixamo animation and it went in with its eyes open** —
the mage's two-handed cast, added later and knowingly, which puts the question
this section settled back on the page. It is set out in full below rather than
folded away here, because a decision reversed deserves as plain a statement as
the decision did.

One thing is open and is of a different kind: the keep's key: it came out of the owner's own asset
folder with no maker named, and its row in the table says it is to be asked of the
owner before a release.

### The icons that came before

Four icons by **Lorc**, from [game-icons.net](https://game-icons.net), under
[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) — the archer's whole
set, and the only skill art the game had until the generated sheets arrived:

- [Arrowhead](https://game-icons.net/1x1/lorc/arrowhead.html) — Q, the aimed shot
- [Arrow cluster](https://game-icons.net/1x1/lorc/arrow-cluster.html) — W, the volley around him
- [Sprint](https://game-icons.net/1x1/lorc/sprint.html) — E, the dash
- [Hood](https://game-icons.net/1x1/lorc/hood.html) — R, the ultimate

Changed from the originals: the opaque black backing square was removed and the
shape was written out at 128×128 on transparency, so the panel could tint it. The
drawings themselves are untouched.

They are at `_unused/icons/` now and **nothing draws them**, which is why they
are no longer in the table above — CC BY asks for a line of credit for art that
is *used*. The panel draws game-icons.net's drawings again since 2026-10-14, the
arrowhead and the arrow cluster among them, painted in the item icons' look: see
the note at the end of this page.

### The three heroes, in full

All three come out of KayKit Adventurers 2.0 (CC0), which ships five characters
— Barbarian, Knight, Mage, Ranger and Rogue — on one shared rig. Three of them
are in this repository, along with the weapons each of them carries:

- **Rogue** (`rogue.glb`) — the archer. He wore the ranger's mesh while he was
  the only hero and there was nobody to be told apart from. Carries `bow.gltf`
  and `quiver.gltf`. Since 2026-10-10 the Rogue is drawn as neilhancart's "Huntress"
  (CC BY 4.0, row above) and carries nodgerty's elven bow and dagger instead; `rogue.glb` and
  `bow.gltf` are kept for the
  kingdom's soldiers.
- **Knight** (`knight.glb`) — `sword_1handed.gltf` and `shield_round.gltf`. No longer
  the hero since 2026-10-10 -- the knight is Lange (`lange/lange.glb`, phucpham1076,
  CC BY 4.0, in the table above) -- and kept for the kingdom's soldiers and the
  Cursed Hero.
- **Mage** (`mage.glb`) — `staff.gltf` and `spellbook_closed.gltf`. He briefly
  wore the ranger's mesh and the *skeletons'* staff, which was a placeholder and
  is not one any more.

`ranger.glb` is still here and nothing uses it. It is the obvious fourth hero,
along with `sword_2handed.gltf`, which the knight put down when he picked up a
shield.

**Every skill has an icon now**, painted from game-icons.net's drawings — which is
what the eight empty slots on this page were waiting for.

### The audio, in full

Four packs by **Kenney** ([kenney.nl](https://kenney.nl)), all CC0 — no
attribution required, given anyway. Roughly sixty of their four hundred files are
shipped: the ones a dungeon has a use for, renamed from `impactSoft_medium_000`
to what they are actually for, and sorted into `sfx/`, `ui/` and `voice/`.

The voiceover pack is a game-show and military set rather than a fantasy one.
The lines that carry over are used — *go*, *target engaged*, *fire in the hole*,
*medic*, *level up*, *game over* — and the numbers, the quiz answers and the
weapon calls with no weapon behind them were left where they were.

### The one animation that is not KayKit's

`magic.glb` is **Mixamo's**, which is Adobe's, and it is the only file in
`animations/` that is not CC0. It is the mage's ultimate: the kit has no
two-handed cast in it, and a man calling a meteor down by waving a staff
one-handed is not the gesture.

**This is the same question the hero and the monsters were removed over**, and it
is worth saying plainly rather than leaving for somebody to find. Mixamo's terms
are royalty-free and generous about *using* the animation in a game; what they do
not clearly cover is redistributing the animation **as a file**, which is what a
public git tree does. That is the reasoning that retired the Mixamo hero and the
Quaternius bestiary from this repository, and it applies here unchanged.

It is here anyway, knowingly, because this is a hobby project and the owner said
so. Two things make it cheap to undo if that ever changes:

- The source `.fbx` is at `dungeon/art/anim/`, outside the resources, so removing
  the animation is deleting two files rather than unpicking a folder.
- Nothing else depends on it. `CastAnim` in the mage's `R` skill
  (`data/units/mage.duke`) names the clip; delete the two lines and the mage casts
  his meteor standing still, exactly as he did before this arrived.

The bones are KayKit's own, which is luck rather than planning: it was animated
on the mage himself, so the 23 joints are named as the kit names them and the
client's retargeting matches them one for one. A clip straight off Mixamo's own
rig arrives as `mixamorig:Hips` and friends and would animate nothing at all —
`dungeon/art/anim/fbx_to_glb.py` fails loudly rather than exporting one.

### The keep's gate

The gate across the keep's doorway, `models/props/gate/gate.glb`, is **"Old
Driveway Gate"** by **animatedheaven**
([CGTrader](https://www.cgtrader.com/designers/animatedheaven)): two stone pillars
with their lamps, and an iron gate of two leaves between them. The owner got it
from CGTrader, and its notes say it "can be used commercially with credits" — so
it is not CC0, and the credit is its row in the table above.

The owner animated the two leaves opening, in Blender, a clip apiece; the game's
copy has the two joined into one, `open`, by `art/models/join_clips.pl`. The
`License.txt` beside the file records the same.

### The music and the rest of the sound

Every sound and every piece of music shipped is CC0. The three freesound.org
tracks whose licences nobody had looked up, and the level-up out of the owner's
folder whose maker was never named, were taken out on 2026-10-15 and replaced by
the tracks and packs below; `SoundscapeTest` holds that no audio row here is left
unconfirmed.

The music, all from OpenGameArt's "CC0 - Cinematic Music" collection, trimmed,
levelled to about -18 LUFS and made Ogg Vorbis:

| Track | Where it plays | Author | Page |
|---|---|---|---|
| A Legend Will Rise (orchestral) | the menu | CodeManu | [a-legend-will-rise-orchestral](https://opengameart.org/content/a-legend-will-rise-orchestral) |
| Medieval: Market Day | the capital | RandomMind | [medieval-market-day](https://opengameart.org/content/medieval-market-day) |
| The Hope | the open world | TAD | [the-hope](https://opengameart.org/content/the-hope) |
| Once Upon a Time (loop) | the fields and the old woman's hut | TAD | [once-upon-a-time-loop](https://opengameart.org/content/once-upon-a-time-loop) |
| Unexplored (short version) | the descent, the dark forest | TAD | [unexplored-short-ver-orchestra-music](https://opengameart.org/content/unexplored-short-ver-orchestra-music) |
| Sinister Boss Appears | the dark castle, the queen's hall | cynicmusic ([cynicmusic.com](http://cynicmusic.com), [pixelsphere.org](http://pixelsphere.org)) | [sinister-boss-appears](https://opengameart.org/content/sinister-boss-appears) |
| Battle Theme A | a fight, the opening battle | cynicmusic | [battle-theme-a](https://opengameart.org/content/battle-theme-a) |
| Boss Battle Music | a boss in sight | Juhani Junkala | [boss-battle-music](https://opengameart.org/content/boss-battle-music) |
| Laments of the War | the turning | Cethiel | [laments-of-the-war](https://opengameart.org/content/laments-of-the-war) |
| Victory Theme for RPG | a run won | cynicmusic | [victory-theme-for-rpg](https://opengameart.org/content/victory-theme-for-rpg) |

Two of them are the pages' own previews — *Boss Battle Music* (the page's
"Preview") and *Laments of the War* (its MP3 preview) — the full versions being
there to have; they are CC0 either way.

The effects: rubberduck's three CC0 packs
([RPG](https://opengameart.org/content/80-cc0-rpg-sfx),
[creature](https://opengameart.org/content/80-cc0-creature-sfx),
[metal and wood](https://opengameart.org/content/100-cc0-metal-and-wood-sfx)),
Vehicle's [Fantasy Weapons and Apparel SFX Library](https://opengameart.org/content/fantasy-weapons-and-apparel-sfx-library),
artisticdude's [RPG Sound Pack](https://opengameart.org/content/rpg-sound-pack),
HaelDB's [Male Grunt/Yelling sounds](https://opengameart.org/content/male-gruntyelling-sounds)
(dual-licensed OGA-BY 3.0 and CC0; taken as CC0),
Fantozzi's [footsteps](https://opengameart.org/content/fantozzis-footsteps-grasssand-stone),
JaggedStone's [Loopable Dungeon Ambience](https://opengameart.org/content/loopable-dungeon-ambience)
and Kenney's [Music Jingles](https://kenney.nl/assets/music-jingles). Each was
trimmed of silence, levelled to one peak and made mono, since OpenAL will not
place a stereo sound; some of the swings are the same recording played lower (an
axe) or higher (a dagger).

### The lettering

The menus are set in **Cinzel** — the Cinzel Project Authors, Natanael Gama —
under the [SIL Open Font License 1.1](https://openfontlicense.org). The licence
ships with it at `fonts/License-Cinzel.txt`, as the OFL requires.

jME cannot read a TrueType font, so what ships is a bitmap of it: a PNG of every
glyph and an AngelCode `.fnt` saying where each one sits, in three sizes. Those
were baked here rather than downloaded — `client3d`'s `BitmapFontBaker` turns a
`.ttf` into the pair — so the OFL's terms are met by the original, and anyone can
re-bake them from it.


### The frames, and why they keep Kenney's numbering

The hero panel's edges are painted from **Fantasy UI Borders** (Kenney, CC0),
which ships at `ui/borders/` with its own `License.txt` beside it. Two families —
`default/` at 48×48 and `double/` at 96×96 — each in six sets: `border/` (frame
only, transparent middle), `panel/` (frame plus an opaque middle),
`transparent_border/`, `transparent_center/`, `divider/` and `divider_fade/`.

`CLAUDE.md` says a pack's own file names are changed on the way in, and these
were not. The rule exists so that `character_medieval_2.glb` becomes a name that
says what the thing is; here the number **is** what the thing is. There are
thirty-two interchangeable frames with nothing to tell them apart but their
carving, `data/world/hud.duke` picks between them by name, and inventing
`frame_studded.png` for `panel-border-013.png` would replace a number anyone can
look up in the pack with an adjective only this repository knows. The folders
were renamed — lower case, underscores for the spaces — and the files were not.

All 280 were copied rather than the eight in use, at 388 KB the lot, so that
choosing a different frame is an edit to `data/world/hud.duke` rather than a trip back to
the pack.

### The particles, and why they are half the size they shipped at

**Particle Pack** (Kenney, CC0, version 1.1) ships in the kit, at `kit/effects/particles/`, with its
own `License.txt` beside it — all 96 of the transparent textures, `rotated/`
included, so choosing a different smoke for a skill is an edit to its effect in `kit/data/effects/`
rather than a trip back to the pack.

They shipped at 512×512 and are kept at **256×256**. A particle is a soft shape
seen for a fraction of a second, usually smaller on screen than the texture is
wide, and a 512 texture with its mipmaps is about 1.3 MB of video memory — thirty
of them is forty megabytes on the kind of laptop GPU that was already running hot.
The quarter-size set is 2.7 MB on disk and indistinguishable in motion.

They were shrunk in **premultiplied alpha**, by `art/effects/shrink_particles.py`.
Every one of them is a white shape on transparency, and resampling colour and
alpha separately averages the white of the shape with the black of the empty
pixels beside it — the edge comes out darker than the middle, which under ordinary
blending is a grey fringe round every puff of smoke. The script says how to run it
again against a newer pack.

The names are Kenney's, for the reason the frames' are: `smoke_07` cannot be
improved on when there are ten interchangeable smokes and the number is the only
thing that tells them apart. The folders were lower-cased on the way in.

### The pointers

The mouse pointers are Kenney's **Cursor Pack 1.1** (CC0), at `ui/cursors/`,
with its `License.txt` beside them. The *Outline* family in both sizes — 182
drawings at 32 pixels in `default/` and the same 182 at 64 in `double/` — so
swapping size is one word in `data/world/hud.duke`. Five are used.

Only *Outline* was copied, and that is a decision rather than a preference. The
pack's other family, *Basic*, is pure white with no keyline: measured, one colour
and one colour only. A white pointer is invisible over a lit floor, and a
dungeon has both a lit floor and a dark one. The outlined drawings are white
**inside a black line**, which is what lets the client tint them — see
the `Cursor` blocks of `data/world/hud.duke` — and what keeps them readable whatever they are over.

### The pointers that came before

Wenrexa's *Assets: Magic Cursors Pack* was shipped for one commit and removed in
the next. It was not liked, and no licence text shipped with it: the download
contained the pictures, a Discord link, a Twitter link and a thank-you card, with
the terms on the pack's page rather than in the zip. Either reason would have
been enough on its own. The note is kept because the rule it followed is worth
keeping: **an asset's licence goes in the table above before it is committed,
not after.**
### The icons, replaced

Until 2026-10-14 the panel's pictures -- the skills, the order buttons, the figures and the attributes --
were AI-generated (Microsoft Copilot and an unrecorded generator), cut out of sheets. The owner had all of
them replaced: each is now a game-icons.net drawing painted in the item icons' look by
`art/icons/ItemIcons.java`, with its row in the table above, and the AI sheets and their cutter are gone
from the tree.

## Duke Skirmish

Everything lives under `skirmish/src/main/resources/`, so the paths below are
written from there. Every file here is a copy of one the dungeon already ships,
renamed for what it is in *this* game — a rock is an ore node, a decorated
pillar is a barracks — because a game owns its own assets rather than reaching
into another's. Each `.gltf` was copied with its `.bin` and its texture, and the
`uri` line inside it rewritten to the new name.

| What | Who | Licence | Where |
|---|---|---|---|
| The three units — worker, soldier, archer (the dungeon's rogue, knight and ranger) | **KayKit** / Kay Lousberg ([kaylousberg.com](https://kaylousberg.com)) | CC0 | `models/units/` |
| Ore nodes and a bare tree (its rocks and trees) | KayKit | CC0 | `models/ground/` |
| The field it is all fought on (its dirt floor) | KayKit | CC0 | `models/ground/field.*` |
| A barracks and a depot (its decorated pillar and chest) | KayKit | CC0 | `models/base/` |

Each folder keeps the pack's own `License.txt` beside the files.
