# Store page: shots, text, requirements, owner's checklist

"Last Hero" is a stand-in title; every name below (game, heroes, commander, kingdom) is the owner's call. Shots are 1920x1080, fullscreen, UI in Uzbek, taken with F12-less plain screenshots from a normal run, no debug overlay.

## Shots (9)

| # | Map | Camera | Moment | On screen |
|---|-----|--------|--------|-----------|
| 1 | `world` (open world) | game camera, wide, a little above the default zoom | the hero on a road at the edge of a land, a camp in the distance | painted ground, scenery, the hero, the HUD bars; the headline shot |
| 2 | `first` | default | a fight with a skeleton pack, a nova or meteor mid-air | yellow crit number, hit flash, skill effects, health bars |
| 3 | `world` | close | a camp boss fight, boss bar up | boss, the family's elite beside it, big numbers, camera mid-shake |
| 4 | `qasr` | default | the keep's shut gate, the hero before it | the castle, the seal, the target frame |
| 5 | `hall` | close | the bag open with a rare item's card | bag grid, item card in rarity colours, character sheet beside it |
| 6 | `talk` | close | a conversation with a quest choice | dialogue screen, portrait, the choices, the quest log tab |
| 7 | `world` | top-down, high | the world map screen | the four lands, roads, the hero's mark |
| 8 | `painted` | default | a trader town at dusk | people, stalls, the trade screen open |
| 9 | main menu | menu scene | the live scene behind the menu | title "Last Hero", the rows at the left, the fight across the rest |

Take 2 and 3 twice (a calm frame and a busy one); the store's first four slots decide most clicks, so order 1, 2, 3, 5.

## Store description (stand-in)

**English.** You are the last hero. A curse has run its circle again, and the kingdom's walls are only as strong as the one who walks out of them. Cross an open world of four lands, each with its own monsters, elites and camp bosses. Fight in real time with skills that grow, find loot that changes how you play, talk to people who remember what you did, and open the keep at the end of the road. Played alone; saved when you leave; runs on a normal laptop.

**Uzbek.** Siz oxirgi qahramonsiz. La'nat yana o'z doirasini aylandi, shohlikning devorlari esa ulardan chiqib ketgan yolg'iz odamdek mustahkam. To'rt yurtdan iborat ochiq dunyoni kezing: har birining o'z jonivorlari, elita jangchilari va lager boshliqlari bor. Rivojlanuvchi mahoratlar bilan jang qiling, o'ynashingizni o'zgartiradigan o'ljani toping, qilganingizni eslab qoladigan odamlar bilan gaplashing va yo'lning oxiridagi qasrni oching. Yakka o'yin; chiqsangiz saqlanadi; oddiy noutbukda ishlaydi.

## System requirements (rough, to measure on a clean machine before posting)

- OS: Windows 10/11 64-bit (the only installer built so far; macOS and Linux build in CI but are untested for Steam).
- Java: none to install; the installer bundles its own runtime. Installer is about 104 MB (1.0.37 MSI), JVM is started with `-Xmx2g`.
- Memory: 4 GB minimum, 8 GB recommended (2 GB heap plus textures and models).
- Graphics: OpenGL 3.2 core (jMonkeyEngine 3.7 with LWJGL 3 asks for it by default, engine sets no other renderer); any GPU of roughly 2012 or newer with current drivers; integrated Intel HD 4000 class or better. VSync is on.
- Storage: about 400 MB installed (measure), saves and settings in `~/.duke-engine`.
- Not measured yet: frame rate on the open world on a low-end GPU; the `perf` branch is working on it. Fill the numbers in after that lands.

## Only the owner can do

- [ ] Steamworks partner account and the one-time app fee (per app, paid in Steamworks); tax and bank forms.
- [ ] The final title, the heroes', commander's and kingdom's names, and the store name check (no clash on Steam).
- [ ] Capsule art: header 460x215, small 231x87, main 616x353, vertical 374x448, hero 3840x1240, library hero and logo, plus the page background. Needs an artist or a decision on style.
- [ ] Trailer (30-60 s, from shots 1-3 and the menu scene) and the 5+ screenshots chosen from the list above.
- [ ] Content survey for the age rating (violence: cartoon fantasy combat, no blood to decide) and the IARC-style questionnaire Steam shows.
- [ ] Price, launch discount, regions, release date (Steam wants the page live about two weeks before release for review).
- [ ] Sign the build: an unsigned MSI trips SmartScreen; a Steam depot build is not an MSI, so also decide how the game is uploaded (a zipped install folder with the bundled runtime via SteamPipe).
- [ ] Store text in final form, languages offered (Uzbek first, English for the page), support email, privacy line.
- [ ] Check licences of the assets on the credits screen allow a paid release (CREDITS.md; the one "with credits" model needs the credit shown).
