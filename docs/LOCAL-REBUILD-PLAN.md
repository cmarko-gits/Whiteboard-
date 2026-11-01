# Novi lokalni Whiteboard — plan i kriterijumi prihvatanja

Datum: 1. novembar 2026. Ovaj plan zamenjuje raniji cloud/AI plan za trenutni rad. Izvor: poslednja korisnička instrukcija i prilog o Clean Architecture, MVVM/MVI, Hilt-u, korutinama i testiranju. AI, backend, nalozi, sinhronizacija, saradnja i objavljivanje na Play-u trenutno su van obima.

## Pravila izvedbe

- Na početku zadatka navesti deo rada; po završetku odmah preći na sledeći.
- Novo lokalno aplikaciono jezgro, navigacija i orkestracija ne zavise od online modula. Zadržavanje postojećeg formata i migracija štiti dokumente korisnika. Postojeći provereni renderer/codec nisu predstavljeni kao novonapisani kod; njihove eventualne zamene zahtevaju iste regresione provere.
- Ne brisati korisničke dokumente, aplikacione podatke, ključeve ili SDK. Nema destruktivnog Room fallback-a.
- Ograničenje dodatnog prostora ostaje 3,4 GB; postojeći SDK/AVD, offline Gradle, build i emulator naizmenično. Nema novih system image preuzimanja.
- Prolazan build, prolazan emulator i stvarni telefon jesu različiti dokazi. Ne tvrditi „svi uređaji”, „bez ranjivosti”, „60 FPS” ili „bez curenja” bez odgovarajućeg merenja.

## 1. Obim i kompatibilnost

- [x] L01 Pregledati postojeći kod, zahteve, prilog, verzije, podatke i prethodne izveštaje.
- [x] L02 Napisati novi plan i kriterijume bez online funkcija.
- [x] L03 Definisati lokalnu verziju, kompatibilnost sa starim dokumentima i upgrade bez gubitka podataka.
- [x] L04 Matrica: Android 8/API 26 do aktuelnog OS-a, telefon/tablet/foldable i promenljivi Android prozori; portrait/landscape, split-screen, 320–1600+ dp širine, font 100/150/200%, tastatura/insets/RTL.
- [x] L05 Evidentirati realno dostupne uređaje. TV, Wear, Auto i XR nisu isti proizvod i nemaju implicitno garantovan interfejs. Sklopivi ekran/šarka, stylus i palm rejection moraju se proveriti na odgovarajućem hardveru.

## 2. Nova lokalna arhitektura

- [x] L06 Nov lokalni Application, Hilt composition root i Activity bez mrežnog pokretanja i pozadinskog cloud rada.
- [x] L07 Nova lokalna Compose navigacija: biblioteka → editor → podešavanja, dijalozi i sistemski picker-i sa očuvanjem stanja.
- [x] L08 Odvojiti aktivne lokalne module od sačuvanog, neaktivnog online koda; online biblioteke i permissions ne smeju u lokalni APK.
- [x] L09 Čist Kotlin domen, modeli, komande, istorija i Use Case-ovi. Presentation nema direktan pristup Room-u, adapterima ni repository interfejsima.
- [x] L10 Hilt konstruktor injection/scopes; constructor injection ostaje normalan način deklarisanja zavisnosti, a Hilt gradi graf. Testovi koriste eksplicitne fake implementacije.
- [x] L11 Immutable UI snapshots, jednosmerne akcije, StateFlow i lifecycle-aware collect. Trajne greške/acknowledgment imaju identitet; otkazivanje nije greška.
- [x] L12 Automatizovati granice modula/importa, zabraniti GlobalScope i blokiranje produkcionog UI-ja. Ne dodavati prazne module ili biblioteke samo radi naziva u prilogu.

## 3. Lokalni dokumenti i bezbednost rada

- [x] L13 Room transakcije, nedestruktivne migracije i verzionisani format; legacy cloud tabele mogu ostati samo radi kompatibilnosti baze, bez aktivne mrežne funkcije.
- [x] L14 Save/Load, vidljiv dirty/saving/saved/error status; izmena tokom Save-a ostaje nesnimljena i povratljiva.
- [x] L15 Nezavisan recovery, prekid procesa, rotacija, discard/cancel/save-and-close; opcioni autosave i ograničene istorije.
- [x] L16 Nedostatak prostora, neispravan dokument i otkazani zapis ne uništavaju poslednju uspešnu verziju.
- [x] L17 Prenosivi ZIP backup sa prilozima, import kao kopija, bez prepisivanja; path traversal, duplicate entries, veličine, dubina i nepoznata verzija se odbijaju.
- [x] L18 Bezbedan životni ciklus priloga: aktivni dokument, Undo/Redo i clipboard štite potrebne datoteke od čišćenja.

## 4. Editor i lokalne funkcije

- [x] L19 Pen/marker/highlighter, debljina/opacity, tačke i brzi potezi, stroke eraser; precizni pan/zoom i prekid gestova.
- [x] L20 Linije/strelice/oblici, tekst i beleške, Unicode, svojstva i z-order.
- [x] L21 Selektovanje, transformacije, grupe, copy/cut/paste/duplicate sa novim identitetima; logičke Undo/Redo operacije.
- [x] L22 Fill za vektorske oblike, beleške i pozadinu, odvojene boje ivice/ispune/pozadine. Raster flood fill nije isto i ne tvrdi se da postoji.
- [x] L23 Birač boje: HEX/HSV/RGB, omiljene boje, validacija, Apply/Cancel i tastatura na niskom ekranu.
- [x] L24 Više stranica, preimenovanje/dupliranje/redosled/brisanje, grid/dots/ruled/custom pozadine i šabloni.
- [x] L25 Biblioteka: recent/thumbnail, folderi, pretraga, favorites, Trash/restore/trajno brisanje sa potvrdama.
- [x] L26 Import slike/kamere/PDF-a, ograničenja i čišćenje; pomeranje, crop, 90° rotacija, resize i izvoz uz postojeći podržani obim.
- [x] L27 PNG/JPG/PDF izvoz, negativne koordinate, prazan/veliki dokument i deljenje preko privremenog FileProvider URI-ja.
- [x] L28 Podešavanja, System/Light/Dark, finger/stylus režim, snap/smart shapes, brush presets, tačna verzija i lokalne licence; EN/sr-Latn.

## 5. Adaptivnost i performanse

- [x] L29 Koristiti veličinu raspoloživog prozora; bez zaključavanja orijentacije i zabrane resize-a. Kompaktni telefon/niska visina, tablet i veliki prozor imaju proverljive rasporede.
- [x] L30 Kontrole od najmanje 48 dp gde je primenljivo, semantički opisi/stanja, vidljiv fokus, font scaling, čitač ekrana i alternative ključnim gestovima. 0.4: virtuelni objekti, kompletan Objects navigator, alternative gestovima i stvarni TalkBack fokus/aktiviranje; obim i ograničenja u LOCAL-QUALIFICATION.md.
- [x] L31 Tastatura i system bars ne prekrivaju primarne akcije; promene konfiguracije ne gube dijaloge/unsent tekst.
- [x] L32 Tastaturne prečice i mouse/trackpad unos uz touch/stylus; ne preuzimati sistemske prečice ako akcija nije dostupna.
- [x] L33 Culling/keš, inkrementalni path, ograničen bitmap cache, CPU/IO van glavne niti, otkazivanje uvoza/izvoza i bez dugih animacija u petlji.
- [x] L34 Izmeriti Save/Load/Export i renderer na ponovljivim dokumentima. Emulator software draw nije merenje fizičkog FPS-a ili stylus latencije.
- [x] L35 Debug LeakCanary, stvarni editor lifecycle/memory ciklusi, Shark heap i sampled CPU/Window profili; release bez dijagnostičkih biblioteka. 0.4: šest ciklusa, 12 editor View-a, završni heap bez preostalih canvas instanci. Fizički profil isključen je iz ovog prolaza po izboru korisnika; emulator ne potvrđuje fizički performance budžet.

## 6. QA i cyber security

- [x] L36 JVM testovi geometrije, komandi, istorije, Fill-a, Use Case-ova, formata, migracionih fixture-a i bezbednosnih granica.
- [x] L37 Deterministički coroutine/ViewModel testovi: uspeh, neuspeh, cancellation, debounce, konkurentni Save i recovery; bez stvarnog sleep-a za logiku.
- [x] L38 Compose testovi dijaloga/validacije/IME/state restoration; UI tokovi biblioteka/editor/stranice/boje/fill/podešavanja.
- [x] L39 Device testovi Room-a, neuspešnog upisa, medija, PDF-a, izvoza i velikog dokumenta; bez brisanja ličnih podataka telefona.
- [ ] L40 Delimično: API 37.1 regresija, 320–1600 dp, font 150/200%, obe orijentacije, stvarni split-screen, IME/RTL, upgrade i process recovery prolaze; reprezentativni snimci pregledani. API 36 pokušaj zaustavljen je pre testova radi storage limita. API 26/30/33/36 CI matrica pripremljena, ali neizvršena; šira OS/OEM kvalifikacija ostaje otvorena.
- [x] L41 Lokalni APK bez INTERNET/ACCESS_NETWORK_STATE i mrežnih SDK-ova; bez naloga, tokena, reklama i analytics-a. Pregledati stvarni manifest/DEX/resources posle R8.
- [x] L42 Privatni storage, backup/transfer pravila, minimalna FileProvider površina, validacija URI/priloga/ZIP/JSON i sanitarne greške bez osetljivih logova.
- [x] L43 Runtime dependency audit, source-secret scan, licence i neaktivni debug alati; navesti tačan obim, ne tvrditi da je ovo nezavisan pentest.

## 7. Release i završna evidencija

- [x] L44 Kotlin style, arhitektura, unit/device/UI testovi i debug/release lint u ponovljivoj CI konfiguraciji bez backend/cloud koraka.
- [x] L45 Minify/resource shrinking, APK/AAB, verzionisanje i 16 KB ZIP/ELF kompatibilnost; debug i release imaju odvojene applicationId vrednosti.
- [x] L46 Razvojni potpis samo za proveru. Vlasnički upload ključ, Play nalog, produkciona potpisivanja i sama objava su izvan trenutnog obima.
- [x] L47 Instalacija/ažuriranje 0.3 → 0.4 uz očuvanje lokalnih dokumenata i smoke na optimizovanom APK-u. Korisnik je izabrao nastavak bez fizičkog telefona; emulator dokaz ne predstavlja instalaciju na telefonu.
- [x] L48 SHA-256 paketa, uređaj/API/page size, izveštaji, poznata ograničenja i preostale provere; raniji online rezultati nisu novi lokalni rezultati.

## Primena priloga

Detaljan preostali rad i razlika između završetka 0.4, još neimplementiranih lokalnih funkcija iz šire vizije, dodatnih opcija i odloženog hardverskog/izdavačkog rada: [LOCAL-REMAINING-TASKS.md](LOCAL-REMAINING-TASKS.md).

MVVM i MVI nisu dve paralelne aplikacije: ViewModel prima akcije i izlaže StateFlow stanja. Hilt pripada Android composition root-u; domen ostaje čist Kotlin. IO/CPU dispatcheri su injektovani, cancellation se propagira, a SharedFlow nije zamena za stanje greške koje ne sme da se izgubi. @Stable/@Immutable se ne dodaju na tipove koji ne ispunjavaju ugovor. Room migracije i testovi su obavezni. WorkManager, Retrofit/AuthInterceptor, JWT i encrypted session storage iz primera ne uvode se u aplikaciju bez mreže. Git istorija se ne prepisuje i distribucija trećoj strani se ne pokreće bez konkretnog cilja; lokalni CI recept i testovi mogu se pripremiti.

Zvanične smernice proverene za ovaj plan: [adaptivnost](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability), [16 KB](https://developer.android.com/guide/practices/page-sizes), [target SDK](https://developer.android.com/google/play/requirements/target-sdk). Konfigurisan je API 36 target; zahteve prodavnice ponovo proveriti pred stvarnu predaju. Podrška svim fizičkim konfiguracijama se ne proglašava na osnovu jednog emulatora.
