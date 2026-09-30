# Duke Dungeon

3D roguelike, [Duke Engine](https://github.com/Duke-Engine/duke-engine) ustida.
Qavatlari seed'dan chiziladi, yoki **stage** — bir marta chizilib matn fayliga
muzlatilgan va har safar bir xil o'ynaladigan qavat.

**Bu o'yin, engine emas.** U faqat engine'ning ommaviy API'siga bog'lanadi va
unga bironta narsa qo'shmaydi — aynan shu uning maqsadi: agar o'yin yashash
uchun yangi engine imkoniyatini talab qilsa, demak engine tugallanmagan.

```
./gradlew run                       # o'ynash
./gradlew run --args=--map=first    # muzlatilgan xaritada
./gradlew build                     # 779 test
```

## Engine qayerdan olinadi

O'yin engine **0.7.0** ga yozilgan (`build.gradle.kts` dagi
`uz.duke-engine:bom:0.7.0`). 0.7.0 hali Maven Central'da **yo'q**, shuning uchun
`gradle.properties` dagi `dukeEngineLocal=true` o'yinni yonidagi engine
checkout'idan quradi — 0.6.0 chiqquncha qanday bo'lgan bo'lsa, shunday. 0.7.0
chiqqach o'sha qatorni o'chirsangiz, o'yin yana Central'dan oladi va klon hamda
`./gradlew build` yetarli bo'ladi.

Engine va o'yin ustida birga ishlash uchun engine'ni **yonma-yon** qo'yib,
`-PdukeEngineLocal` bilan quring: har bir `uz.duke-engine:…` bog'liqlik o'sha
checkout'dan olinadi (`settings.gradle.kts` dagi `includeBuild`):

```
<papka>/
  duke-engine/
  duke-dungeon/
```

Engine'ning ishchi nusxasi yarim o'zgarishda turib kompilyatsiya bo'lmasa, boshqa
checkout ko'rsatiladi: `./gradlew run -PdukeEngineLocal -PdukeEngineDir=<yo'l>`
(masalan, oxirgi commit'ning nusxasi).

## Hozirgi qadam

Eng kichik **o'ynaladigan** holat, ataylab yalang'och: bitta xona, bitta
qahramon, uchta skelet. Model yo'q, tekstura yo'q, ovoz yo'q — hamma narsa
rangli shakl. Maqsad — engine'ni *o'ynasa bo'ladimi* degan savolga javob
olish, buning eng tez yo'li esa javobni yashira oladigan hamma narsani
olib tashlash.

- **Xona** — qo'lda chizilgan ASCII (`Dungeon.ROOM`), `#` = tosh. Ikkita
  ustun ataylab qo'yilgan: bitta xonada ham A* o'z o'rnini oqlashi kerak.
- **Yurish** — yerni bosasiz → `GameMessage.MoveTo`. Yo'lni engine topadi.
- **Hujum** — skeletni bosasiz → `MoveTo` **so'ng** `AttackObject`.
- **O'lim** — HP tugaydi, obyekt dunyodan chiqadi; klient buni `ObjectDied`
  hodisasidan biladi (yo'qolib qolganidan taxmin qilmaydi).

## Ikkita nozik joy

**Skeletlarda `Speed = 0` bo'lsa ham `MoveUpdate` bor.** Engine uchun
*shakli bor, lekin `Locomotor` moduli yo'q* obyekt — relyef, va u navigatsiya
gridiga bosiladi. Modulsiz skeletlar **devorga aylanardi** va qahramon
ularga umuman yeta olmasdi. Bu modul ularni mebel emas, jonzot qiladi.

**Hujumda buyruq tartibi muhim.** `MoveTo` joriy nishonni bekor qiladi
(engine uchun aniq yurish buyrug'i = "qilayotganingni unut"), shuning uchun
`AttackObject` **keyin** kelishi shart, aks holda qahramon yetib borgunicha
u yo'qolib ketardi.

## Klient — engine'ning 3D klienti

`Duke3D.launch(game, Visuals.create(), shell)`, tamom. Hech qanday `Visuals`
bog'lanmagan, shuning uchun har bir jonzot **rangli primitiv** bo'lib
chiziladi — bu bosqichda shakllar o'yinning o'zi, san'atning o'rinbosari emas.

Kamera yerdan ~55° burchakda turadi (`(0, d×0.82, d×0.57)`), ya'ni tepadan
vertikal emas. Boshqaruv — engine'niki: qahramonni **LMB** bilan tanlaysiz,
**RMB** bilan yerga (yurish) yoki skeletga (hujum) buyruq berasiz.

> Bosh menyu ham shu o'yinniki: "Enter the dungeon / Settings / Quit" — LAN
> bandisiz.
>
> Boshida bu modul o'z 2D Swing oynasi bilan yozilgan edi (bitta klik = bitta
> buyruq). Ekranda ko'rilgach ma'lum bo'ldiki, u o'yinga emas, o'yin
> diagrammasiga o'xshaydi — shuning uchun engine'ning 3D klientiga
> o'tkazildi. Kiritish endi RTS uslubida, chunki klient shunday.

**Ma'lum vizual cheklov:** devor bloklari past (balandligi 6 birlik, katak
eni 10) — bu `client3d` ning relyef chizishida qattiq yozilgan, o'yin uni
o'zgartira olmaydi. Baland devorlar kerak bo'lsa, bu engine ishi bo'ladi.

## Keyingi qadamlar (hozir YO'Q)

Procedural generatsiya · ko'p xona, boss, leveling · o'lim/qaytadan sikli ·
model, tekstura, ovoz, musiqa.

## Jamoa — LAN orqali birga o'ynash

"Enter the dungeon" → **Kim bilan kirasiz**:

- **Yolg'iz** — avvalgi yo'l: rejim → bosqich → qahramon.
- **Jamoa ochish** — nechta qahramon (2–4) → rejim → bosqich → qahramon → kutish
  xonasi. Unda boshqalar kiritadigan manzil (shu kompyuterning LAN IP'si)
  ko'rsatiladi. Hamma kirgach o'yin o'zi boshlanadi.
- **Jamoaga qo'shilish** — qahramon → host manzili (`IP` yoki `IP:port`, keyingi
  safar uchun eslab qolinadi) → host boshlashini kutish.

Port — `7777` (`data/world/party.duke`). Windows birinchi marta tarmoq
ruxsatini so'rasa, ruxsat bering. Ikkala kompyuterda o'yinning bir xil build'i
bo'lishi kerak — boshqa build'dagi host rad etiladi va sababi aytiladi.

**Qoidalar (co-op):** qahramonlar ittifoqdosh, zindon hammaga dushman. Har kim
o'z qahramonini tanlaydi. Daraja, mahorat va o'lja — har kimniki o'ziga.
Yiqilgan qahramon boshqalarni kutadi: boss o'lgach butun jamoa keyingi qavatga
tushadi, yiqilganlar ham turib chiqadi. Hamma yiqilsa — run tugaydi, yangisi
hamma uchun boshlanadi.

**Qanday ishlaydi:** engine'ning lock-step'i (`MultiplayerSession`): tarmoqdan
faqat buyruqlar o'tadi, har bir kompyuter bir xil dunyoni o'zi hisoblaydi. O'yinning
o'z buyruqlari (mahorat, himoya, hujum-yurish, qahramon tanlash) `GameOrder`
bo'lib yuboriladi (`party/PartyOrders`). Hostning tanlovi (rejim, seed yoki
bosqich) mehmonlarga bitta versiyali qatorda boradi (`party/PartyMatch`).
`LanPartyTest` ikki "kompyuter"ni localhost socket'lari orqali ulab, bir daqiqalik
jangda har bir kadr checksum'i bir xil qolishini tekshiradi.

**Internet orqali (keyinroq):** lobby manzil va portga ulanadi, shuning uchun host
routerda 7777-portni ochsa, mehmon tashqi IP bilan hozir ham ulana oladi. Port
ocholmaydiganlar uchun uchrashish joyi (relay yoki server) — engine sessiyasining
keyingi ishi. O'yin tomonida hamma buyruq allaqachon kerakli shaklda.

**Hali yo'q:** chat (engine qo'llaydi, o'yinda yozish oynasi yo'q), qorong'ulikni
jamoa bilan birga ochish (klient faqat o'z qahramoning atrofini ochadi),
alohida server.

## Narsalar — sumka, olish va tashlash

Monstr o'lganda **15%** ehtimol bilan narsa qoldiradi (boss — har doim). Narsa
yerda **sandiq** bo'lib yotadi.

- **Olish:** sandiq ustidan yurish hech narsa bermaydi. Qahramon tanlangan
  holda sandiqni **o'ng tugma** bilan bosing (kursor ochiq qo'lga aylanadi,
  sandiq atrofida sariq doira ikki marta yonib o'chadi) —
  qahramon borib uni oladi. Sumka to'la bo'lsa, narsa joyida qoladi va panelda
  "Sumka to'la" chiqadi.
- **Sumka:** ekranning o'ng tomonida, 6 ta uya. Narsa sumkada turgan ekan,
  bonusi hisoblanadi.
- **Tashlash:** sumkadagi narsani **o'ng tugma** bilan qo'lga oling (kursor
  yopiq qo'lga aylanadi, narsa kursor yonida yuradi), keyin yerni **chap tugma**
  bilan bosing — qahramon o'sha joyga borib qo'yadi. O'ng tugma yoki Esc —
  fikrdan qaytish. Yetib bo'lmaydigan joy bosilsa, borishi mumkin bo'lgan eng
  yaqin joyga qo'yadi.
- **Ko'rsatma:** kursorni sumkadagi yoki yerdagi narsa ustiga olib borsangiz,
  uning nomi, darajasi va nima berishi chiqadi (masalan `+9 Kuch`, `+2 Jon/s`).
  Yerdagi narsa uchun ham qahramon tanlangan bo'lishi shart emas.

Narsalar hozircha uch xil, har biri qahramon atributini beradi: `Kuch qo'lqopi`
(+3 Kuch), `Chaqqon etik` (+3 Epchillik), `Donolik kitobi` (+3 Aql).

**Uchtasi birlashadi.** Sumkada bir xil darajadagi uchta bir xil narsa bo'lsa,
ular bitta keyingi darajali narsaga aylanadi (uyacha burchagida `II`, `III`).
Har daraja oldingi uchtasining yig'indisi, 2-darajadan esa qo'shimcha effekt
ham beradi:

| | 1-daraja | 2-daraja | 3-daraja |
|---|---|---|---|
| Kuch qo'lqopi | +3 Kuch | +9 Kuch, +2 jon/s | +27 Kuch, +6 jon/s |
| Chaqqon etik | +3 Epchillik | +9 Epchillik, +20% hujum tezligi | +27 Epchillik, +60% hujum tezligi |
| Donolik kitobi | +3 Aql | +9 Aql, +2 mana/s | +27 Aql, +6 mana/s |

3-daraja eng yuqorisi. Uchinchi narsa to'la sumkaga ham sig'adi: u kirishi
bilan birlashadi. Hujum tezligi foizda, chunki +2% hujumlar orasidagi 24–34
kadrdan ko'pi bilan bittasini qisqartirardi. Hammasi `data/world/world.duke` da
(`LootDrops`: `JoinCount`, `TopLevel`; `LootItem`: `Extra`, `ExtraValue`):
yangi narsa — yangi blok, Java kerak emas.

**Jon va mana — joy, to'ldirish emas.** Kuch maksimal jonni, Aql maksimal manani
oshiradi, lekin jon va mananing o'zini emas; narsa tashlansa, oshgan joy ham
ketadi. Aks holda uni tashlab qayta olish bepul davolanish bo'lardi. Daraja esa
avvalgidek jonni ham ko'taradi.

**Jamoada:** olish ham, tashlash ham `GameOrder` (`PickUp`, `DropItem`), har bir
kompyuterda bir kadrda bajariladi. Kim yuborilsa, narsa o'shaniki; tashlangan
narsani istalgan qahramon olishi mumkin.

## Fontan — kirish joyidagi shifo favvorasi

Har qavatda qahramonlar paydo bo'ladigan joyda **fontan** turadi, qahramonlar
uning bir tomonida yonma-yon paydo bo'ladi. Suv oqib turadi: uchidan otilib
qaytib tushadi, kosadan kosaga to'kiladi, havzadan tuman ko'tariladi, suv
ustida uchqunlar yonib-o'chadi va atrofini sovuq ko'k nur yoritadi.

Fontan har soniyada atrofidagi (36 birlik, ~3.5 katak) **hammaga** —
qahramonlarga ham, monstrlarga ham — maksimal jon va mananing **5%** ini
qaytaradi. Davolanganlar ustida yashil "+N" chiqadi. Foiz bo'lgani uchun har
qavatda va har qahramonga bir xil qadrli. Fontan ichidan yurib bo'lmaydi,
atrofidan aylanib o'tiladi.

Hammasi ma'lumotda:
- `data/props/fountain.duke` — radius, tezlik, foizlar va suv effekti (`FountainWater`);
- `Run` blokidagi `WayIn = Fountain` — bo'sh qoldirilsa, fontan qo'yilmaydi;
- mavzulardagi `ThemeMonster Fountain` — model va o'lcham.

Kirish joyi atrofida 2 katak ochiq joy bo'lmasa, yaqin atrofdan joy
qidiriladi. Topilmasa, o'sha qavat fontansiz qoladi. Model: Poly by Google,
CC BY 3.0 (`CREDITS.md`).

## Generatsiya — kameralar, tunnellar, relyef

Qavat endi to'rtburchak xonalar va zinali qavatlardan (storey) emas, balki
**organik kameralar** va **silliq relyefdan** iborat (`gen/Cave`, `gen/Relief`):

- **Kamera** — xona joylashadigan footprint ichida o'stiriladi: ellips, bir
  necha bo'rtiq, keyin qirg'oqlari "yeyiladi". O'rtasi doim pol: qahramon va
  boss shu yerda turadi.
- **Tunnel** — kameralarni avvalgidek eng qisqa daraxt (MST) bo'yicha ulaydi,
  lekin to'g'ri chiziq emas, egri-bugri. Qo'shimcha **halqalar** (loops) faqat
  kirishdan bir xil (±1) uzoqlikdagi kameralar orasida, boss zaliga hech qachon —
  shuning uchun boss avvalgidek uzoqda qoladi.
- **Kafolatlar** — tunnellar va kameralar o'rtasi hech qachon toshga
  aylantirilmaydi; `CorridorWidth` dan tor joy qolmaydi; kirishdan yetib
  bo'lmaydigan pol olib tashlanadi. Ya'ni qavat qurilishning o'zidan bog'langan.
- **Orolchalar** — kamera ichida qolgan tosh: g'orda ustun, o'rmonda daraxtzor.
  Faqat atrofi pol bo'lgan joyda, shuning uchun hech narsani to'smaydi.
- **Relyef** — qavat/zina o'rniga tepalik va pastliklar: kameralar turli
  balandlikda, qiyalik tunnellarda. Har bir katak `Slope` dan tik emas (16 — jar),
  demak hech qayerda jar yo'q.

Yer qanday bo'lishini **tema** aytadi — `data/world/themes/*.duke` dagi
`Terrain` bloki: `Ragged`, `Winding`, `Loops`, `IslandsPerRoom`, `Rise`,
`HillSize`, `Slope`, `Level`. O'rmon ochiq va to'lqinli, zindon yopiq va tekis
zalli. Temaning qolgan qismi (kit, tuman rangi, tonlar) faqat ko'rinish —
simulyatsiyaga ta'sir qilmaydi (`DungeonThemeTest`).

### Biomlar — bitta qavatda bir nechta joy

Minecraft'dagidek: qavat ustidan ikki keng "ob-havo" maydoni o'tadi — **qanchalik
yovvoyi** va **qanchalik tirik** (`gen/BiomeMap`). Har bir katak `Climate` nuqtasi
(tema faylida, `[wild, alive]`) shu ob-havoga eng yaqin biomni oladi. Kamera
butunlay bitta biom va o'sha biomning `Terrain`i bilan kesiladi; tunnel ikki
uchining o'rtachasi; relyef mintaqa bo'yicha, lekin chegarada jar yo'q. Mayda
dog'lar qolmaydi, chuqurlashgan sari ob-havo `ClimatePerDepth` bo'yicha siljiydi
(o'rmonlar kamayadi, kon va o'lik yerlar ko'payadi).

Biomlar: **Forest**, **Autumn** (kuzgi o'rmon — oltin, to'q sariq, qizil),
**PineWood** (qarag'ay), **DeadLand** (o'lik o'rmon), **Dungeon** (g'orlar),
**Mine** (kon). Har katak o'z biomining kit'i bilan chiziladi (`run/FloorLooks`,
engine 0.7.0 `Looked`); har biomning polida o'z **dekoratsiyasi** yotadi —
o't, butalar, toshchalar, konda oltin/kumush/mis rudasi (`gen/Scenery`, tema
faylidagi `Scenery`, engine `Dressed`) — faqat ko'rinish uchun, hech narsani
to'smaydi. Devor modellari ham bir nechta (`Walls`): g'orda qoyalar va
nayzasimon toshlar, o'rmonda daraxtlar orasida butalar. Kirish joyi (5×5) doim
ochiq — favvora shu yerda turadi. Turg'un narsalar (ustun, bochka, sandiq,
favvora) turgan katagining biomi ko'rinishida; yuruvchi maxluqlar — kirish
biomining ko'rinishida.

**Daraxtzorlar (grove)** — o'rmonli biomlarda kamera ichidagi orolcha endi tosh
blok emas, ochiq yerda turgan daraxtlar: har biri faqat tanasi (`Footprint`)
qadar yo'lni to'sadi. Biomli qavat 5 birlikli katakchalarda yuriladi
(`world.duke` dagi `NavigationCellsPerCell = 2`, engine `Subdivided`), shuning
uchun qahramon daraxtlar orasidan joy bor joyda o'tadi, boss esa aylanib o'tadi.
G'or va kondagi ustunlar tosh bo'lib qoladi. Qoidalardagi masofalar (yonida,
yetib borish) hamon 10 birlikli katakda sanaladi.

`generation.duke` dagi `Biomes` qatorini olib tashlasangiz, qavatlar avvalgidek
chuqurlik bo'yicha bitta temada bo'ladi. Stage'lar (`newMap`) doim bitta temada
kesiladi — stage fayli biom xaritasini saqlamaydi. `MapPicture` har mintaqani
biom rangiga bo'yaydi.

### Boss qal'asi

Har qavat oxirida boss **qal'ada** turadi (`gen/Keep`): toshning ichiga
qurilgan kvadrat hovli, yer sathida (`levelMap` da `0`), atrofi bir qavat
baland devor — qaysi biomda bo'lmasin, tosh plitadan
(`data/world/themes/keep.duke`). Darvoza yaqinlashish tomonidagi devorning
o'rtasida (3 katak), oldida qal'a toshidan ostona (3 katak), ostonadan
kameragacha to'g'ri yo'l. To'rt mag hovlining burchaklarida, har devordan bir
katak ichkarida turadi. Qal'a eng chuqur kamera yonidagi
butunlay tosh joyga quriladi, shuning uchun hech qanday yo'lni kesmaydi; tekis
turadi, atrofidagi yer unga qiyalik bilan tushadi.

O'lchami `generation.duke` dagi `Keep` blokida: `Sizes = [15, 13, 11, 9]` —
kattalari faqat eng chuqur kameralar yonida sinab ko'riladi. Darvoza —
`data/props/gate.duke`, modeli animatedheaven'ning "Old Driveway Gate"i,
tabaqalarining ochilishi Blender'da animatsiya qilingan (ikki klip o'yin
nusxasida `art/models/join_clips.pl` bilan bitta `open` klipiga
birlashtirilgan). Ochilganda darvoza o'rniga shaklsiz `OpenGate` turadi va
`open` klipini bir marta o'ynab, ochiq qoladi (`PlayOnce`, engine
`ClipMode.ONCE`). Darvoza o'zi ochilmaydi: uni faqat kalit ochadi. Yopiq
turganda u qal'a ichini tashqarisidan ajratib turadi (`run/Seal`): zarba, o'q,
portlash, meteor va davolash undan o'tmaydi (ostona tashqari hisoblanadi);
ochilgach hammasi avvalgidek yetadi. Stage'lar qal'asiz kesiladi — stage fayli
qal'aning ko'rinishini saqlamaydi.

Qal'ali qavatning **vazifasi** bor (`run/Mission`), ekranning tepasida
yoziladi: qal'adan tashqaridagi barcha monstrlarni o'ldirish (hisobi bilan),
oxirgisi yiqilgan joyda qolgan kalitni olish, uni darvozaga berish, bossni
o'ldirish. Kalit (`world.duke` dagi `Kind = KEY`, `Use = UNLOCK` li
`LootItem`) hech narsa bermaydi, birlashmaydi va tasodifan tushmaydi. Sumkada
kalitga chap tugma, keyin darvozaga bosilsa, qahramon borib kalitni darvozaga
beradi. Darvozaga o'ng tugma bosilsa, qahramon borib, kaliti bor-yo'qligini
boshi ustidagi pufakchada aytadi. Kalitni ko'targan qahramon yiqilsa, kalit
o'sha joyda qoladi. Kalit o'zi topilgan qavatga tegishli: qavat tugagach u
keyingi qavatga o'tmaydi, shu yerda qoladi; fayl o'qilganda darvozani ocha
olmaydigan kalit (`Use = UNLOCK` siz `KEY`), `KEY` bo'lmagan narsadagi
`UNLOCK` va kalitsiz qal'a rad etiladi.

## Stage rejimi — o'zgarmaydigan xarita

O'yinning ikkinchi turi. Roguelike tushishi har run'da yangi qavat chizadi va
savol "qanchaga tushdim?" bo'ladi. **Stage** — qotirilgan qavat: o'sha xonalar,
o'sha burchaklarda o'sha maxluqlar, har safar. Savol "shuni yengaman-mi?" ga
aylanadi — Warcraft custom map uslubi.

```
./gradlew run --args="--map=first"
```

yoki `data/game.duke` da:

```
Game
  StartMap = first
  Files = [ … ]
End
```

Map uning `Name`i bilan yoki map faylining yo'li bilan so'raladi. **Bo'sh bo'lsa —
roguelike**, aynan avvalgidek. Argument fayldan ustun turadi. Yo'l avval diskdan,
topilmasa classpath'dan qidiriladi (shipping map installer ichida yuradi).

**Stage'da:** o'lsang — o'sha stage boshidan (yangi dungeon EMAS). Bossni
o'ldirsang — g'alaba, chunki ostida qavat yo'q.

### Format — `dungeon/src/main/resources/maps/first/first.map`

Matn: bitta `StaticMap` bloki, boshqa `.duke` fayllar kabi o'qiladi. Binar format —
hech kim ocholmaydigan daraja: g'alati ishlaydigan stage kimdir ochib, o'qib, xatoni
ko'ra oladigan fayl bo'lishi kerak. Koordinatalar **katakda** (dunyo birligida emas),
chunki ular bir xil fakt va faqat birini `Cells` rasmi bo'yicha ko'z bilan sanash mumkin.

Fayl ichida: metama'lumot + seed, bitta `Cells` xaritasi (`#` tosh, raqam qavat,
`/` zina), xonalar, koridor ulanishlari, kirish, boss, maxluqlar, prop'lar. Har bir
map — o'z papkasi: `maps/<nom>/<nom>.map`, yonida `preview.png` va shu mapning o'z
`.duke` bloklari. Ro'yxat yo'q — menyu `maps/` ichida turganini topadi. Endless
tushishning sozlamalari esa `data/world/generation.duke` dagi `ProceduralMap`.

**Stage fayli — muzlatilgan `GeneratedDungeon`.** Ya'ni `Spawner` qotirilgan
qavatni bir daqiqa oldin chizilganidan ajrata olmaydi — "stage o'zi kesilgan
dungeon bilan aynan bir xil o'ynaladi" degani shundan kelib chiqadi, ehtiyotkorlikdan
emas. `StagePlayTest` buni checksum bilan qulflaydi.

**Yuklashda qayta tekshiriladi** (`StageCheck`): qo'lda tahrirlangan fayl
yetib bo'lmaydigan xona, toshdagi maxluq yoki belgilanmagan boss bilan kelsa,
o'yin **hammasini ro'yxat qilib to'xtaydi** — jimgina roguelike'ga qaytmaydi.
Yurish engine'ning `PathGrid.canStep` i bilan tekshiriladi, nusxasi bilan emas.

Stage yasash — `./gradlew newMap --args="nom seed [chuqurlik [eni bo'yi xonalar]]"` qavatni seed'dan chizadi; nima qayerda turishini IDE'dagi **Map** tabida qo'lda qo'yasiz.

Har bir map papkasida `preview.png` — mapning ustidan ko'rinishi, va map tanlanadigan
ekran shuni ko'rsatadi. `newMap` uni o'zi yozadi; allaqachon chizilgan maplar uchun
`./gradlew writeMapPreviews` (mapning o'ziga tegmaydi), IDE'da esa **Map**
tabidagi **Save Preview**.

### Qiyinchilik = chuqurlik

`Stage.difficulty` — **o'sha bosqich qaysi chuqurlikda o'ynalishi**, yorliq emas.
Yangi mexanizm yasalmadi: qavatni xavfli qiladigan hamma narsa allaqachon
chuqurlik bo'yicha yozilgan va sozlangan. Maxluqning **darajasi** chuqurlikdan
(bugun u joyning *tier*'i) daraja qoidasi bilan olinadi — `wayInLevel`,
`beforeBossLevel`, `bossLevel`, `levelAlong`, `generation.duke` dagi
`Descent` qatorlaridan; sog'lig'i, zarbasi va qiymati esa har daraja uchun
foizlardan (`HealthPercentPerLevel`, `DamagePercentPerLevel`,
`ExperiencePercentPerLevel` — `healthAtLevel`, `damageAtLevel`,
`experienceAtLevel`). Ular avvalgi `monsterHealthAt` va `monsterDamageAt`
o'rnini egalladi. Chuqurlikning o'zi esa hamon `monsterCountAt`, qaysi turlar
umuman paydo bo'lgani (`MinDepth`) va `bossKindAt` ni belgilaydi. Shuning
uchun "qiyinchilik 7" ning ma'nosi bor: tushishning 7-qavati qanday bo'lsa,
shunday — borib tekshirsa bo'ladi.

Tushish `Bosses` ro'yxati tugaganda tugaydi (hozir 4 qavat). Bosqich esa
**undan chuqurroq** qurilishi mumkin — bu bosqich yasashning asosiy
sabablaridan biri.

O'lmoq → **o'sha chuqurlikda** qayta boshlanadi (1 ga tushmaydi, aks holda
qiyin bosqichning ikkinchi urinishi oson bo'lib qolardi). Bossni o'ldirmoq →
g'alaba, qaysi chuqurlikda qurilgan bo'lsa ham.

Chuqurlik qayerdan boshlanishini `Floors.firstDepth()` aytadi: `GeneratedFloors`
1 qaytaradi (roguelike o'zgarmadi), `StageFloors` — bosqichning qiyinchiligini.

### O'lcham

Generatsiya o'lchami endi `Layout` record'i orqali override qilinadi
(`gen/Layout.java`). `Layout.of(settings)` — bugungi dungeon, **bit-baravar**;
`Layout.sized(settings, w, h, rooms)` — muallif so'ragani.

Shipping bosqichlar: `maps/first/first.map` (50×36, 9 xona, chuqurlik 1) va
`maps/deep/deep.map` (100×76, 28 xona, **chuqurlik 8** — tushishdan chuqurroq).
Ikkalasini `./gradlew writeExampleMaps` qayta yozadi.

**Ulanish kafolati kattalikda ham tekshirilgan:** `LayoutTest` 180×140 / 60 xona
o'lchamda 12 ta seed'ni `StageCheck` bilan yurib chiqadi.
