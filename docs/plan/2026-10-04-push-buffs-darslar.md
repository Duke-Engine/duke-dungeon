# Auralar va tezlik "push" bufflarga — kunlik darslar rejasi

Egasi o'zi yozadi, kuniga ~1 soat. Har kun: bitta tushuncha, bitta kichik o'zgarish, testlar yashil, commit.
Har kuni men ishni ko'rib chiqaman (review), xatolarni tushuntiraman.

**Nima quriladi:** hozir aura "pull": ta'sir oluvchi monstr har zarbada atrofidan aura egasini qidiradi
(`SkillBook.auraOn`). Yangisi "push": aura egasi har 10 kadrda atrofidagi ittifoqchilarga **buff** qo'yadi
(15 kadr yashaydi), ta'sir oluvchi esa faqat o'z ro'yxatiga qaraydi. Tezlik (haste) ham shu ro'yxatga o'tadi.

**Usul:** yangisi eskisining yonida quriladi, o'quvchi joylar birma-bir yangisiga o'tadi, eskisi oxirida o'chadi.
Har kuni o'yin ishlaydi va hamma testlar yashil.

**Branch:** `push-buffs`. Milestone'larda (4, 8, 12-kunlar) master'ga qo'shiladi.

| Kun | Ish | Nimani o'rganasiz |
|---|---|---|
| 1 ✅ | Aura devordan o'tadi | metod, shartlar (`&&`), test, commit |
| 2 | `world.duke` ning `Combat` blokiga `AuraPulseFrames = 10` va `BuffLifeFrames = 15`; `Combat.java` record'iga ikki maydon; yuklanishini tekshiruvchi test | **record**, data fayl qanday Java'ga aylanadi |
| 3 | Yuklash qoidasi: ikkalasi ham ≥ 1, `BuffLifeFrames` > `AuraPulseFrames` (aks holda buff yangilanish orasida o'chib qoladi); qoida xabari kalit nomini aytadi; rad etish testi | **validatsiya**, xatoni aniq xabar bilan aytish |
| 4 | Yangi fayl `skill/Buff.java`: `record Buff(SkillEffect kind, int value, int until)` va `boolean activeAt(int frame)`; kichik test | **yangi sinf yaratish**, o'zgarmas (immutable) qiymat |
| 5 | `SkillBook` ichida bufflar ro'yxati: `give(kind, value, until)`. Bir turdan bittasi qoladi: kuchlirog'i yoki uzoqroq yashaydigani | **List**, sikl (`for`), "eng kuchlisi" qoidasi |
| 6 | `buffOf(kind, frame)` — shu turdagi faol buff qiymati (yo'q bo'lsa 0); `update()` da muddati o'tgan bufflarni olib tashlash (`removeIf`) | **kadrlar** va vaqt, ro'yxatdan o'chirish |
| 7 | **Puls:** aura egasi har `AuraPulseFrames` kadrda (id bo'yicha surilgan) radiusidagi tirik ittifoqchilarga (yopiq darvozadan emas) buff beradi | **fazoviy so'rov** (`objectsInRange`), lambda filtrlar |
| 8 | Puls testlari: 10 kadrdan keyin summoner yonidagi skeletda 50 lik buff bor; 61 da yo'q; darvoza ortida yo'q; egasi o'lgach 15 kadrda yo'qoladi | **test yozish** o'zingiz, "kechikish" tushunchasi |
| 9 | Birinchi o'qiydigan joy: `damageMultiplier` endi bufflarga qaraydi. Eski testlar puls kelishini kutadigan qilib to'g'rilanadi | **refaktor**, testlarni yangi qoidaga moslash |
| 10 | Mana tezligi (`regenerate`) va jon qaytarish (`drink`) ham bufflarga o'tadi | bir xil o'zgarishni takrorlash, ishonch |
| 11 | `aurasOn` bufflardan o'qiydi, shu bilan oyoq ostidagi doiralar va HUD ham | o'zgarish butun tizim bo'ylab qanday tarqalishi |
| 12 | **Tezlik (haste)** ham buff: cast `give(HASTE, foiz, kadr + davom)`; `rateOfFireMultiplier` va HUD (qolgan soniya) bufflardan o'qiydi; `hasteFrames`/`hastePercent` o'chadi | eski kodni xavfsiz o'chirish |
| 13 | Eski pull kodini (`auraOn` ichidagi atrofni qidirish) o'chirish, izohlarni yangilash, butun testlar | tozalash, hujjat |
| 14 | Yakuniy review: men butun branch'ni ko'rib chiqaman, siz tuzatasiz, master'ga | **code review**ni qabul qilish |

Taxminan **13 dars** (1-kun bajarilgan). Kuniga bitta bo'lsa, ~2–3 hafta.
Qiyin kunlar (7, 9, 12) ikki kunga cho'zilishi mumkin — bu normal.

Keyin (bu rejadan tashqari): bufflar saqlash tizimiga qo'shiladi (saqlash qurilganda); stun, sekinlashtirish va
iksirlar ham shu ro'yxatga o'tishi mumkin; buffni olib tashlaydigan sehr (dispel) mumkin bo'ladi.
