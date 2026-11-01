> Istorijski plan/prethodna faza. Aktuelni lokalni obim i kriterijumi su u [LOCAL-REBUILD-PLAN.md](LOCAL-REBUILD-PLAN.md), a provereni rezultati u [LOCAL-STATUS.md](LOCAL-STATUS.md). Online/AI zahtevi iz ranije faze trenutno nisu aktivni.

# Whiteboard — zahtevi proizvoda

Status: radna specifikacija za usaglašavanje, 19. oktobar 2026.

Razvoj je započet po naknadnom nalogu korisnika da se zadaci izvršavaju redom. Važeće radne odluke i granice implementacije su u [IMPLEMENTATION-DEFAULTS.md](IMPLEMENTATION-DEFAULTS.md), a provereni rezultati u [STATUS.md](STATUS.md). Faze u ovom dokumentu čuvaju izvorni plan i ne opisuju tačno trenutno stanje koda.

Izvor: [kompletna vizija](WHITEBOARD-VISION.md). Izvor ostaje sačuvan u celosti. Ovaj dokument precizira obim, kriterijume prihvatanja i otvorene odluke; predlozi nisu automatski potvrđeni zahtevi.

## 1. Namena i granice proizvoda

Whiteboard je mobilni prostor za crtanje, beleške i vizuelno organizovanje ideja, sa kasnijom podrškom za dokumente, saradnju i AI. Namenjen je studentima, nastavnicima, programerima, dizajnerima i poslovnim korisnicima.

- Početna implementacija: nativni Android za telefone i tablete, Kotlin i Android Studio, prema odluci korisnika.
- iPhone/iPad ostaju deo izvorne vizije, ali zahtevaju zasebnu odluku o implementaciji; Android aplikacija se ne izvršava direktno na iOS-u.
- Web i Windows/macOS: kasnije; rok i obim nisu definisani.
- Osnovna aplikacija radi lokalno, bez naloga i interneta.
- Sadržaj se čuva kao objekti koji ostaju izmenjivi, a ne samo kao slika.
- Tehnološka odluka: Kotlin, bez Dart-a i Flutter-a. Predložena arhitektura: moduli po funkcionalnostima, odvojeni UI/domain/data slojevi i zasebno canvas jezgro u čistom Kotlinu. Predloženi Android alati: Jetpack Compose, ViewModel, StateFlow, Coroutines, Hilt i Room. Izvorna vizija ostaje neizmenjena kao istorijski izvor.
- Kanal javne distribucije: Google Play Store, prema odluci korisnika. Produkciono objavljivanje je deo cilja proizvoda; aplikacija nije namenjena samo demonstraciji.
- Proizvod se zove Whiteboard; postojeći naziv projektnog foldera WhiteDashboard ne menja naziv proizvoda.

## 2. Pojmovi

- **Tabla / Board:** dokument koji korisnik otvara, imenuje i čuva.
- **Stranica / Page:** zasebna radna površina unutar table. V1 koristi jednu stranicu; model treba da omogući kasnije više stranica.
- **Objekat:** potez, tekst, oblik, linija, strelica ili beleška; kasnije i slika, PDF, čvor, konektor ili grupa.
- **Sačuvana verzija:** stanje koje je korisnik potvrdio komandom Save, odnosno autosave kada postoji i kada je uključen.
- **Recovery:** privremeno stanje za oporavak nesnimljenog rada; ne sme neprimetno zameniti sačuvanu verziju.
- **Deljenje izvoza:** slanje PNG/JPG/PDF datoteke kroz sistemski meni.
- **Deljenje table:** kasniji pristup zajedničkom dokumentu preko linka i dozvola.

## 3. V1 — funkcionalni zahtevi

| ID | Zahtev | Kriterijum prihvatanja |
|---|---|---|
| V1-01 | Home i nova tabla | Korisnik bez registracije kreira praznu tablu, imenuje je i ulazi u editor. |
| V1-02 | Praktično beskonačan canvas | Pan i zoom ne menjaju položaje objekata u dokumentu; opseg koordinata i zoom-a biće definisan tehničkom proverom. |
| V1-03 | Navigacija | Pinch menja zoom, pomeranje sa dva prsta pomera pogled; korisnik može resetovati pogled i koristiti Fit to Content. |
| V1-04 | Crtanje | Pen, Marker i Highlighter prave zasebne poteze; korisnik bira boju, debljinu i providnost. Početna boja je crna. |
| V1-05 | Gumica | Potezi mogu da se brišu; brisanje može da se poništi. Režim za V1 je otvorena odluka. |
| V1-06 | Undo/Redo | Kreiranje, uređivanje i brisanje objekata može da se poništi i vrati; nova izmena posle Undo poništava prethodnu Redo granu. |
| V1-07 | Oblici | Dostupni su pravougaonik, kvadrat, krug, elipsa, trougao i romb, kao i linija i strelica. |
| V1-08 | Tekst | Korisnik unosi i menja tekst, veličinu, boju, bold, italic i poravnanje; tekst ostaje izmenjiv posle otvaranja table. |
| V1-09 | Sticky notes | Korisnik dodaje belešku, menja tekst i boju, pomera je, menja veličinu, duplicira i briše. Komentari dolaze sa saradnjom. |
| V1-10 | Selekcija | Korisnik bira objekat, pomera ga, menja veličinu i briše ga. Dodatne operacije selekcije imaju poseban obim u odeljku 7. |
| V1-11 | Save/Load | Save čuva objekte i svojstva; zatvaranje i ponovno pokretanje omogućava otvaranje iste sačuvane table. Neuspešno čuvanje ne prikazuje uspeh. |
| V1-12 | Recent i thumbnail | Home prikazuje poslednje korišćene table sa nazivom i pregledom sadržaja; otvaranje vodi na odgovarajuću tablu. |
| V1-13 | Tema | Light, Dark i System menjaju interfejs nezavisno od boje canvas-a. |
| V1-14 | PNG izvoz | Korisnik izvozi sačuvan ili trenutno izabran sadržaj u PNG; izvoz ne menja dokument. Granice, rezolucija i providnost su otvorene odluke. |
| V1-15 | Offline rad | Svi navedeni osnovni tokovi rade u airplane režimu, bez prijave i cloud servisa. |
| V1-16 | Dodir i stylus | Crtanje i navigacija rade na telefonu i tabletu; podržani uređaji i pravila palm rejection-a moraju biti provereni na stvarnom hardveru. Pressure sensitivity nije uslov za V1. |

## 4. Zaštita rada — predlog obaveznog uslova za objavljivanje V1

Izvor zahteva zaštitu od gubitka rada, ali je recovery izostavljen iz V1 tabele. Predlog je da sledeće bude uslov za objavljivanje V1:

| ID | Zahtev | Kriterijum prihvatanja |
|---|---|---|
| SAFE-01 | Vidljiv status izmene | Editor razlikuje nesnimljeno, čuvanje, sačuvano i neuspešno čuvanje. |
| SAFE-02 | Izlazak sa nesnimljenim radom | Nudi Save, Discard i Cancel; neuspešno čuvanje zadržava rad i omogućava ponovni pokušaj. |
| SAFE-03 | Oporavak | Posle prekida aplikacije korisnik može da vrati dostupno privremeno stanje ili da ga odbaci. Interval oporavka i dopušten gubitak poslednjih izmena još se definišu. |
| SAFE-04 | Bezbedno zapisivanje | Prekinuto ili neuspešno čuvanje ne uništava poslednju uspešno sačuvanu verziju. |
| SAFE-05 | Greške skladišta | Nedostatak prostora i nedostupna/oštećena datoteka daju razumljivu poruku i ne brišu postojeći dokument. |

Opcioni Autosave ostaje planiran za V2. Recovery, autosave i backup su tri različite funkcije.

## 5. Kasnije verzije — očuvan obim vizije

| Faza | Funkcije |
|---|---|
| V2 — produktivnost | Slike iz galerije i kamere; pomeranje, rotacija, resize i crop slike; PDF import, crtanje, highlight i beleške preko PDF-a i PDF izvoz sa anotacijama; više stranica sa preimenovanjem, dupliranjem, brisanjem i promenom redosleda; folderi i podfolderi; pretraga naziva tabli, foldera, stranica i tekstualnih objekata; favorites; Trash sa vraćanjem i trajnim brisanjem; zaključavanje PIN-om/biometrijom; autosave; lokalni backup; Smart Shapes; snap i poravnanje; omiljene olovke; šabloni; unapređenja stylusa. |
| V3 — cloud i saradnja | Opcioni nalog; cloud skladište, backup i sinhronizacija; deljenje table linkom; Viewer/Commenter/Editor dozvole; istovremeno uređivanje; kursori učesnika; komentari, odgovori i resolve; aktivnost; rešavanje konflikata. |
| V4 — AI | Rukopis u tekst; OCR fotografija i PDF-a; inteligentna pretraga; rezime ideja, zadataka, odluka i datuma; generisanje mind map-a i dijagrama; Beautify. Generisani dijagrami moraju ostati objekti koji mogu da se uređuju. |
| Naknadno rasporediti | Ručni mind maps sa granama i collapse/expand; flowchart oblici; smart konektori; grupe; multi-select; napredni clipboard; JPG/PDF/SVG izvoz van PDF anotacija; mini-map; pomerljiv toolbar; Pencil alat; dodatni fontovi; task/checkbox beleške; automatsko čišćenje Trash-a; web i desktop. |

Osnovne boje i pozadine obuhvataju white/black/gray/custom, grid/dots/ruled. Tačan raspored pozadina po verzijama još nije potvrđen. Podešavanja se uvode kada pripadajuća funkcija postoji.

## 6. Podaci i tehnička ograničenja

- Board: ID, naziv, datumi kreiranja/izmene, thumbnail, stranice i podešavanja; kasnije folder i favorite status.
- Page: ID, naziv, pozadina, objekti i položaj pogleda.
- Svaki objekat ima stabilan ID, tip, geometriju/transformaciju, stil i redosled prikaza.
- Stroke čuva tačke, boju, debljinu, opacity i tip alata. Predvideti opcione podatke stylusa.
- Dodati verziju formata dokumenta i plan migracija pre nego što se format koristi za korisničke podatke.
- Slike i PDF treba da imaju definisan životni ciklus priloga i pravila backup-a; ne vezivati dokument samo za privremene putanje.
- Canvas, istorija promena, lokalno skladište i buduća sinhronizacija treba da imaju odvojene odgovornosti.
- Početni Android stack je Kotlin, Compose, ViewModel/StateFlow, Coroutines, Hilt i Room, prema usvojenom smeru arhitekture. Backend platforma, sync protokol, renderer i AI dobavljač biraju se kroz tehničke provere odgovarajuće faze.
- Napredna saradnja mora posebno definisati offline konflikte, konkurentno uređivanje/brisanje, opoziv dozvola i ponašanje posle ponovnog povezivanja.

## 7. Otvorene odluke i neusklađenosti izvora

| ID | Tema | Šta treba odlučiti / predlog |
|---|---|---|
| D-01 | Zaštita rada | Potvrditi SAFE zahteve za V1; predlog: uključiti ih pre objavljivanja. |
| D-02 | Gumica | Izvor opisuje Pixel i Stroke režim, ali V1 samo Eraser. Predlog: Stroke u V1, parcijalno brisanje poteza u V2. |
| D-03 | Selekcija | Razvojni plan navodi move/resize, a opšti opis dodaje rotate, copy/paste, stil i z-order. Potvrditi koje operacije ulaze u V1 i za koje tipove objekata. |
| D-04 | Stranice i pozadine | V1 jedna stranica je predložena interpretacija faznog plana. Odlučiti V1 pozadine i da li su one svojstvo table ili stranice; predlog: svojstvo stranice uz početnu vrednost table. |
| D-05 | Broj lokalnih tabli | Izvor istovremeno pominje neograničene projekte i ograničenja Free/Pro. Predlog: neograničene lokalne table u okviru kapaciteta uređaja. |
| D-06 | Izvoz i deljenje | Definisati granice i kvalitet PNG izvoza, sistemsko deljenje u V1 i raspored JPG/PDF/SVG izvoza. |
| D-07 | Jezici | Odrediti jezike interfejsa, latinicu/ćirilicu i kasnije podržane OCR/rukopis jezike. |
| D-08 | Platforme | Odrediti minimalne verzije sistema, podržane uređaje, orijentacije i redosled Android/iOS objavljivanja. |
| D-09 | Performanse | Cilj izvora je 60 FPS, ali nedostaju uređaj, broj objekata/tačaka i scenario. Pre razvoja utvrditi merljiv benchmark, latenciju crtanja, memoriju i vreme otvaranja/čuvanja/izvoza. |
| D-10 | Zaključavanje | Razlikovati UI zaključavanje od šifrovanja podataka. Definisati PIN recovery, lokalne kopije, izvoze i zaštitu backup-a. |
| D-11 | AI privatnost | Definisati koji sadržaj napušta uređaj, potvrdu korisnika, čuvanje/brisanje kod dobavljača i lokalne funkcije. Uključivanje cloud-a samo po sebi ne odobrava slanje AI servisu. |
| D-12 | Monetizacija | Free je upotrebljiv offline i uključuje osnovno crtanje, čuvanje i izvoz. Pro, cene, kvote, pretplata/jednokratna kupovina i ponašanje nakon isteka još nisu odlučeni. Bez agresivnih reklama je smernica izvora. |
| D-13 | Onboarding | Definisati preskakanje i prvi ulazak; prikazivati mogućnosti koje postoje u isporučenoj verziji. |
| D-14 | Backup i prenos | Definisati format za prenos izmenjivih tabli, sadržaj kopije, obnovu na drugom uređaju, migraciju i neuspešan restore. PNG/PDF izvoz nije puna kopija dokumenta. |
| D-15 | Pristupačnost | Definisati veličinu kontrola, čitače ekrana za dostupni UI, skaliranje teksta, kontrast i alternativu gestovima. |
| D-16 | Istorija | Definisati memorijski limit i granularnost Undo/Redo, kao i da li se istorija zadržava nakon zatvaranja. |

## 8. Provera pre objavljivanja V1

1. Na podržanom telefonu i tabletu, bez interneta i naloga, kreirati tablu i koristiti sve V1 alate.
2. Proveriti da pan, zoom i promena orijentacije ne menjaju sadržaj ni tačnost poteza/selekcije.
3. Proveriti Undo/Redo nad mešavinom poteza, oblika, teksta i beležaka.
4. Sačuvati, zatvoriti aplikaciju, ponovo otvoriti i uporediti sadržaj i svojstva objekata.
5. Proveriti izlazak sa nesnimljenim promenama, prekinuto čuvanje, nedostatak prostora i recovery prema usaglašenim SAFE zahtevima.
6. Otvoriti izvezeni PNG i proveriti sadržaj, granice i kvalitet prema odluci D-06.
7. Proveriti Light/Dark/System i odvojenost teme od boje canvas-a.
8. Izmeriti rad na dogovorenim uređajima i test tablama prema D-09; zabeležiti stvarne rezultate.

Završetak V1 znači da su kriterijumi za usaglašeni obim prošli, da su greške koje uzrokuju gubitak podataka otklonjene i da poznata ograničenja imaju jasan opis. Ovaj dokument ne potvrđuje da je bilo koja funkcija implementirana ili testirana.

## 9. Priprema za Google Play

Zahtevi provereni 19. oktobra 2026; ponovo ih proveriti pre predaje jer se pravila menjaju.

- Release isporuka: potpisan Android App Bundle (AAB), Play App Signing i bezbedno čuvanje upload ključa van repozitorijuma.
- Izabrati trajan applicationId pre prvog objavljivanja, voditi versionCode/versionName i odvojiti debug od release konfiguracije.
- Prema trenutnom pravilu, nove standardne Android aplikacije i ažuriranja moraju ciljati Android 16 / API 36 ili noviji. To nije minimalna verzija Androida za instaliranje; minSdk se odlučuje posebno.
- Predvideti testove migracija korisničkih podataka kroz ažuriranja aplikacije, kao i proveru stvarnog release izdanja na telefonima i tabletima.
- Koristiti minimalne dozvole i sistemski izbor datoteka/fotografija gde odgovara funkciji. Pravila za backup podataka aplikacije moraju biti usklađena sa obećanjima o lokalnom čuvanju i privatnosti.
- Pripremiti javnu politiku privatnosti i tačnu Data safety deklaraciju prema stvarnom ponašanju aplikacije i svih dodatih SDK-ova.
- Pripremiti ikonu, snimke ekrana, opis, kontakt za podršku, ciljnu publiku, ocenu sadržaja i druge primenljive Play Console deklaracije.
- Plan distribucije: interno testiranje, zatvoreno testiranje prema pravilima naloga, pa produkciono izdanje. Za lične developer naloge kreirane posle 13. novembra 2023. trenutno se traži najmanje 12 testera neprekidno prijavljenih tokom 14 dana pre zahteva za pristup produkciji. Ispunjenje tog uslova nije automatsko odobrenje.
- Tip i status developer naloga još nisu poznati. Pro model i naplata nisu potvrđeni; pravila za naplatu proveriti pri definisanju monetizacije.

Zvanični izvori:

- [Target API zahtevi](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Potpisivanje aplikacije](https://developer.android.com/studio/publish/app-signing)
- [Objavljivanje i AAB](https://developer.android.com/studio/publish)
- [Korisnički podaci i privatnost](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Testiranje novih ličnih naloga](https://support.google.com/googleplay/android-developer/answer/14151465)
