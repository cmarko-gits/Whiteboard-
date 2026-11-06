# Whiteboard 0.4.0 — lokalni status i QA

Najnoviji lokalni kandidat je [0.7.2/source 1176e59](LOCAL-0.7.2-CANDIDATE.md): 399 JVM i pet profila po 123 app + 13 data, tri release/jezička toka i provereni paket; 535 jedinstvenih slučajeva. JSON resource nalaz je zatvoren, a warm preview velikog grafa ima novo poređenje na svih pet profila. **13/47 završeno, 34 otvoreno**; preostale cene velikih scena i Z/F/O/H/P stavke zahtevaju nastavak. Ostatak dokumenta je istorijska 0.4.0 kohorta. Tekući F06 je razvojna osnova, bez gotovog mind-map UI-ja. EXECUTION-PROGRESS.md i LOCAL-REMAINING-TASKS.md navode izvore i granice.

Istorijski izveštaj za 0.4.0. Novi izvor/kandidat 0.4.1 i kasniji OS/CI/storage nalazi opisani su u [LOCAL-0.4.1-CANDIDATE.md](LOCAL-0.4.1-CANDIDATE.md) i [EXECUTION-PROGRESS.md](EXECUTION-PROGRESS.md). Promenljivi build izlazi i noviji security report fajlovi mogu sada pripadati novom kandidatu; stare rezultate proveravati uz njihove zabeležene hash-eve i imenovane qualification artefakte.

Datum: 1. novembar 2026. Aktivni obim ostaje lokalna Kotlin Android aplikacija. AI, backend, nalozi, cloud i Play objava nisu uključeni. Korisnik je izabrao nastavak bez fizičkog telefona.

## Rezultat ove iteracije

Platno sada izlaže pristupačne pojedinačne objekte, vidljiv fokus i akcije za izbor, pomeranje, veličinu, tekst, brisanje i kameru. Objects dijalog pretražuje celu stranicu, uključujući objekte van ekrana i stranice sa više od 256 preklopljenih objekata. Birač boja ima pristupačne alternative prevlačenju, izabrano stanje i 48 dp ciljeve. Slider-i imaju razumljiva imena.

Ispravljeni su zastareli snapshot pri pomeranju/izmeni teksta i kasni bitmap decode posle detach/reattach. Relativne komande i potvrda teksta koriste trenutno domensko stanje. Postojeći lokalni format, Room migracije, recovery i osam modula ostaju očuvani; nije svaki postojeći izvorni red ponovo napisan.

## Izvršene provere

| Provera | Rezultat |
|---|---|
| JVM domen/format/data/ViewModel | **124/124**, 0 grešaka: 71 + 33 + 5 + 15 |
| Room/data device | **13/13**, bez mrežnog servisa |
| Redovni aplikacioni/device/UI testovi | **47/47**, 0 failed, 0 ignored |
| Memorija i Window/CPU kvalifikacija | **2/2**, zasebna dijagnostička konfiguracija |
| Stvarno uključen TalkBack servis | **1/1**: touch exploration, virtuelni fokus i aktiviranje objekta |
| Ciljani accessibility/cache/boje testovi | 11/11; predstavljaju podskup redovne regresije, ne dodatne jedinstvene testove |
| Kotlin stil i arhitektura | Prolaze |
| Debug i release lint | 0 errors, po 2 warnings: OldTargetApi i localeConfig/UnusedAttribute |
| Optimizovani APK | R8/shrinking, razvojni v2/v3 potpis, ZIP/ELF 16 KB, stroga APK politika prolazi |
| Runtime zavisnosti/licence | 147 komponenti; 0 prijavljenih OSV nalaza; 147/147 licenci |
| Release smoke i upgrade | Prolaze; 0.3/code 3 → 0.4/code 4 preko install -r, postojeća QA tabla očuvana |
| Adaptivnost/font | 4/4: 320 dp, 360 dp sa 150/200% fonta, 1600 dp prozor |
| Lokalizacija | 2/2: sr-Latn i stvarna RTL konfiguracija sa engleskim fallback-om |
| Stvarni split-screen | 3/3: portrait, zamena strana, landscape; povratak u fullscreen; Fit prikazuje sačuvani potez bez dirty izmene |

Ukupno je 187 jedinstvenih testova u izvršenim JVM/data/app/kvalifikacionim grupama; ponavljanja i ciljani podskupovi nisu sabirani kao novi testovi. Redovni build ne uključuje tri eksplicitne kvalifikacione klase. GitHub CI matrica je pripremljena, nije pokrenuta iz ovog okruženja.

## Pristupačnost, memorija i profil

TalkBack je zaista bio uključen; test ne potiskuje druge accessibility servise. Snimak zelenog fokusa je vizuelno pregledan. Ovo je integraciona provera i alternativa ključnim gestovima, a ne slušna/usability sertifikacija svih ekrana. Originalna podešavanja su vraćena.

Šest ciklusa stvarnog editora sa osam bitmap priloga i Activity recreation posmatra 12 editor View-a i ViewModel-e. LeakCanary assertion prolazi; završni Shark heap nalazi 0 preostalih WhiteboardView instanci. Privremeni heap je obrisan posle lokalne analize. To ne dokazuje odsustvo svih mogućih curenja.

Workload Save/Load/PNG: 617/807/809 ms; software visible draw p50/p95/p99 30,818/43,171/46,736 ms. Stvarni Window workload sa 1000 poteza/100000 tačaka na debug SwiftShader emulatoru beleži visoke TOTAL_DURATION vrednosti: 250,89/400,74/834,84 ms. CPU trag najveći wall interval nalazi u nSyncAndDrawFrame; to upućuje na render/synchronization čekanje u ovom okruženju, bez native GPU dokaza. **Fizički 60 Hz/stylus budžet nije kvalifikovan.** Detalji i postupak: [LOCAL-QUALIFICATION.md](LOCAL-QUALIFICATION.md).

## Uređaj, prostor i preostala ograničenja

Uspešni rezultati iz ove iteracije potiču sa emulator-5554, sdk_gphone16k_x86_64, Android 17 / API 37.1, page size 16384, SwiftShader, 3 GB emuliranog RAM-a. Build i testovi rađeni su odvojeno. Deklarisani minimum je API 26, target 36; izvršena regresija nije dokaz rada na svim deklarisanim OS/OEM uređajima.

Pokušaj izolovanog Android 16 / API 36 boot-a zaustavljen je **pre testova** zbog pada slobodnog prostora. Zabeleženi najveći porast zauzeća prema početnoj vrednosti iznosi 3.906.899.968 bajtova, iznad korisničkog limita 3.400.000.000. Nove QA disk-slike i odabrani ponovo generisivi međufajlovi uklonjeni su pojedinačno; zauzeće je vraćeno ispod limita. Host pagefile je posle pokušaja imao alokaciju 7168 MiB; njegovu početnu veličinu nemamo kao dokaz uzroka. Korisnički dokumenti, SDK i postojeći AVD podaci nisu brisani. Automatska kontrola odbila je rekurzivno uklanjanje QA direktorijuma; u njemu su ostale male konfiguracione datoteke.

Nov guard zahteva stvarni završetak emulator VM procesa, slobodan host RAM i dodatnu rezervu prostora pre pokretanja. API 36 rezultat nije prolaz; API 36.1 nije pokrenut. **L40 ostaje delimično otvoren:** izvršiti redovne testove na API 26/30/33/36 i širu OEM/hardware matricu kada resursi to dozvole. Fizički telefon je isključen iz ovog prolaza po korisničkom izboru. Foldable šarka, fizička olovka/palm rejection, desktop/freeform i stvarni GPU/latencija nisu sertifikovani.

## Paketi i dokazi

- [APK 0.4.0](../artifacts/whiteboard-local-0.4.0-preview.apk), razvojni potpis; 1.928.126 bajtova. SHA-256: `254F113F846ADF3F0419F4FC0F84C22ADE0B729E14FCCAA5B43A13891DA6CC3C`.
- AAB: app/build/outputs/bundle/release/app-release.aab; unsigned build artefakt, bez Play predaje. Hashes: artifacts/local-packages.json.
- Konačni build: artifacts/qualification-final-build.log; JVM XML i lint izveštaji u module/build/.
- Regresija: qualification-regression-api37-{app,data}-tests.txt; profili/TalkBack: qualification-profile-api37-app-tests.txt i qualification-talkback-api37-app-tests.txt.
- Release smoke/upgrade: qualification-release-smoke-api37.txt i qualification-upgrade.json.
- Adaptivnost/lokalizacija: qualification-api37-adaptive.json i qualification-api37-locales.json; snimci u qualification-api37-screens/.
- Split-screen: qualification-window-results.json, qualification-split-*-config.txt i snimci.
- Bezbednost: artifacts/security/; dokazi su vezani za hash finalnog APK-a. Obim nije nezavisan pentest.
- Storage: qualification-storage-baseline.json i qualification-storage-current.json; neuspešni OS pokušaj ostaje u qualification-os-matrix.log.

Aktuelni plan je [LOCAL-REBUILD-PLAN.md](LOCAL-REBUILD-PLAN.md). Istorijski 0.3 hashes i verifikacija sačuvani su kao local-0.3.0-packages.json i local-0.3.0-verification.json; stari cloud rezultati nisu dodavani lokalnim rezultatima.
