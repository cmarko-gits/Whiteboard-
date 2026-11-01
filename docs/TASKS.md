> Istorijski plan/prethodna faza. Aktuelni lokalni obim i kriterijumi su u [LOCAL-REBUILD-PLAN.md](LOCAL-REBUILD-PLAN.md), a provereni rezultati u [LOCAL-STATUS.md](LOCAL-STATUS.md). Online/AI zahtevi iz ranije faze trenutno nisu aktivni.

# Whiteboard — kompletan plan zadataka

Datum: 19. oktobar 2026. Platforma prve implementacije: nativni Android, Kotlin, Android Studio. Kanal distribucije: Google Play.

Izvori: [zahtevi](REQUIREMENTS.md) i [izvorna vizija](WHITEBOARD-VISION.md). Kontrolna lista se ažurira tokom razvoja; završene implementacione stavke ne znače da su završene fizičke provere uređaja ili objavljivanje. Rezultati provera i ograničenja beleže se u [STATUS.md](STATUS.md).

## Pravila praćenja

- Oznake: P = odluke i priprema; A = arhitektura; V1 = prva verzija; QA = kvalitet; GP = Google Play; V2/V3/V4 = naredne faze; X = buduće platforme.
- Zadatak je završen kada postoji proverljiv rezultat, odgovarajuća provera i ažurirana dokumentacija. Korisničke funkcije proveravaju se i kroz interfejs.
- Testove kritične logike pisati uz implementaciju. Završna QA faza povezuje i proverava celinu; nije početak testiranja.
- Predlozi koji još nisu potvrđeni ostaju zadaci za odluku. Naknadne funkcije ne proširuju automatski V1.
- Procene trajanja dati tek posle tehničkih provera canvas-a i dostupnog Android okruženja.
- Poslovi koji zahtevaju vlasnika naloga, fizičke uređaje ili odobrenje objave imaju tu zavisnost eksplicitno navedenu. Plan zadataka sam po sebi nije nalog za javno objavljivanje.

## Redosled i kontrolne tačke

| Korak | Grupe | Potreban rezultat pre narednog koraka |
|---|---|---|
| M0 | P, A | Usaglašen V1, osnovni projekat i potvrđena izvodljivost crtanja |
| M1 | V1-DOC, V1-CAN, minimalni V1-DRAW/V1-HIST/V1-SAVE | Nacrtaj potez, poništi/vrati, sačuvaj i ponovo otvori bez gubitka podataka |
| M2 | Sve V1 grupe | Funkcionalno kompletna prva verzija sa recovery-jem prema usaglašenom obimu |
| M3 | QA, GP priprema | Proveren release kandidat i spremni materijali za prodavnicu |
| M4 | GP testiranje i objava | Ispunjeni uslovi naloga, odobrena predaja i objavljena V1 |
| M5 | V2 | Stabilne lokalne funkcije za produktivnost |
| M6 | V3 | Bezbedan cloud i pouzdana saradnja |
| M7 | V4 | AI funkcije sa izmenjivim rezultatima i kontrolisanom potrošnjom |

V1 Home i dizajn mogu napredovati uz canvas proveru. Model dokumenta, istorija i čuvanje moraju se povezati rano. Google Play nalog i materijali mogu se pripremati tokom razvoja; zatvoreno testiranje zahteva funkcionalan kandidat. V2 grupe mogu se objavljivati postepeno. AI eksperimenti mogu koristiti lokalne test dokumente pre V3; komercijalni cloud AI zahteva svoju serversku i privatnosnu pripremu.

## P — proizvod i odluke

- [ ] P-01 Zaključati V1 obim i povezati svaku funkciju sa kriterijumom iz REQUIREMENTS.md.
- [x] P-02 Potvrditi recovery, upozorenje pri izlasku i status čuvanja kao uslove V1.
- [x] P-03 Odlučiti V1 režim gumice: celo brisanje poteza ili i parcijalno brisanje.
- [x] P-04 Odrediti operacije selekcije u V1: move/resize/delete i obim duplicate/rotate/clipboard/style/z-order.
- [x] P-05 Potvrditi jednu stranicu u V1, V1 pozadine i vlasništvo podešavanja pozadine.
- [x] P-06 Odrediti PNG granice, rezoluciju, pozadinu, izvoz selekcije i sistemsko deljenje.
- [ ] P-07 Odrediti minSdk, podržane orijentacije i listu referentnih telefona/tableta/stylusa.
- [x] P-08 Izabrati jezike V1, pravila lokalizacije i podršku za latinicu/ćirilicu u unosu.
- [x] P-09 Potvrditi ograničenja lokalnih tabli i Free funkcije; ostaviti Pro cene i kvote kao zasebnu odluku.
- [ ] P-10 Definisati test dokumente, ciljani FPS, latenciju poteza, memorijski budžet i vremena save/load/export.
- [x] P-11 Definisati interval recovery-ja, granice istorije i prihvatljiv oporavak poslednjih izmena.
- [ ] P-12 Odrediti vlasnika aplikacije, naziv za prodavnicu, applicationId i kontakt za podršku; proveriti dostupnost naziva pre izrade finalnog identiteta.

Završetak: otvorene odluke za V1 imaju zapisane odgovore; kasnije odluke imaju fazu u kojoj se rešavaju.

Radne odluke su zapisane u [IMPLEMENTATION-DEFAULTS.md](IMPLEMENTATION-DEFAULTS.md), na osnovu naloga da se razvoj nastavi. P-05: početni predlog jedne stranice proširen je već implementiranim višestraničnim režimom iz V2-ORG-01. Identitet izdavača, komercijalni uslovi, cloud i fizička kvalifikacija nisu time potvrđeni.

## A — Android projekat i arhitektura

- [x] A-01 Proveriti Android Studio, SDK, JDK, emulator i dostupnost fizičkih uređaja; dokumentovati reproducibilan početak rada.
- [x] A-02 Kreirati Kotlin Android projekat sa Gradle Kotlin DSL, kompatibilnim stabilnim verzijama alata i zaključanim zavisnostima.
- [x] A-03 Postaviti debug/release konfiguracije, applicationId, verzionisanje i resurse aplikacije.
- [x] A-04 Definisati module i smerove zavisnosti; izdvojiti čisti Kotlin model/engine i Android implementacije bez kružnih zavisnosti.
- [x] A-05 Zapisati ključne arhitektonske odluke: rendering, model dokumenta, istorija i persistence; ne praviti prazne buduće module.
- [x] A-06 Postaviti Compose navigaciju, ViewModel/StateFlow, Coroutines i Hilt, sa jasnim vlasništvom stanja.
- [x] A-07 Postaviti Room, eksport schema verzija, početnu migracionu strategiju i odvojene modele baze/dokumenta/interfejsa gde je potrebno.
- [ ] A-08 Definisati tipizirane greške, granice obrade grešaka i logove koji ne otkrivaju sadržaj tabli.
- [x] A-09 Postaviti formatiranje, Kotlin/Android statičke provere i proveru granica modula.
- [x] A-10 Postaviti lokalne i automatizovane build/test provere; dokumentovati izvršavanje bez tajni u repozitorijumu.
- [ ] A-11 Napraviti kratak canvas prototip: stylus/finger input, zoom/pan, više poteza i merenje na referentnom uređaju.
- [ ] A-12 Na osnovu merenja izabrati Android renderer i input adapter; proveriti Compose integraciju i relevantne stylus/Ink API-je pre konačnog izbora.
- [x] A-13 Dodati početna uputstva za razvoj, pravila pregledanja koda i politiku dodavanja zavisnosti/licenci.

Završetak: projekat se gradi, osnovne provere prolaze, a canvas pristup ima izmerene rezultate. Sam izbor biblioteka nije završen canvas engine.

## V1-UX — dizajn i tokovi

- [x] V1-UX-01 Definisati boje, tipografiju, razmake, ikone i stanja kontrola za Light/Dark teme.
- [ ] V1-UX-02 Napraviti raspored Home-a, editora i Settings-a za telefon i tablet, portrait i landscape.
- [x] V1-UX-03 Definisati tok kreiranja, imenovanja, otvaranja, čuvanja i napuštanja table.
- [x] V1-UX-04 Definisati kompaktan toolbar, aktivni alat i panel svojstava objekta/alata.
- [x] V1-UX-05 Definisati prazna stanja, učitavanje, greške, nesnimljen rad i recovery dijalog.
- [ ] V1-UX-06 Definisati pristupačnost kontrola, tekstualne opise, kontrast, skaliranje teksta i alternative ključnim gestovima.
- [x] V1-UX-07 Definisati kratak onboarding koji može da se preskoči i pokazuje samo dostupne funkcije.

Završetak: osnovni tokovi su pregledni na obe veličine ekrana i imaju rešena neuspešna stanja.

## V1-DOC — model dokumenta

- [x] V1-DOC-01 Implementirati Board i Page sa stabilnim ID-jevima, datumima, nazivom i podešavanjima.
- [x] V1-DOC-02 Implementirati tipove Stroke, Shape, Line, Arrow, Text i StickyNote.
- [x] V1-DOC-03 Definisati koordinate dokumenta, granice, transformacije, stil i redosled prikaza objekata.
- [x] V1-DOC-04 Definisati verzionisan format, serijalizaciju, validaciju i ponašanje pri nepoznatoj/novijoj verziji.
- [x] V1-DOC-05 Odvojiti sačuvani dokument, radno stanje editora, selekciju, pogled i privremeni input.
- [x] V1-DOC-06 Testirati serijalizaciju i ponovno učitavanje svih tipova bez promene sadržaja; odbacivanje neispravnih vrednosti mora biti kontrolisano.

Završetak: model se koristi i testira bez Android UI-ja; otvaranje ne menja originalni dokument tiho.

## V1-CAN — canvas i gestovi

- [x] V1-CAN-01 Napraviti renderer i odvojiti pozadinu, završene objekte, aktivni potez i selekcione oznake.
- [x] V1-CAN-02 Implementirati screen-to-document i document-to-screen transformacije i njihove testove.
- [x] V1-CAN-03 Implementirati pan, pinch zoom oko tačke gesta, granice zoom-a i praktične granice koordinata.
- [x] V1-CAN-04 Dodati reset pogleda i Fit to Content, uključujući praznu tablu.
- [x] V1-CAN-05 Definisati prioritet gestova, prelazak crtanje/navigacija i obradu pointer cancel događaja bez slučajnih poteza.
- [ ] V1-CAN-06 Obraditi dodir i stylus, režim ignorisanja prsta gde je podržan i palm rejection na dogovorenim uređajima.
- [x] V1-CAN-07 Očuvati dokument i smislen pogled pri rotaciji, promeni veličine prozora, background/foreground prelazu.
- [ ] V1-CAN-08 Uvesti iscrtavanje vidljivih objekata, keširanje i prostorni indeks u meri koju opravdavaju testovi performansi.
- [ ] V1-CAN-09 Testirati tačnost na velikom zoom-u, negativnim koordinatama i velikim udaljenostima od početka.

Završetak: navigacija ne menja geometriju dokumenta, a input i rendering zadovoljavaju dogovoreni benchmark.

## V1-DRAW — olovke i gumica

- [x] V1-DRAW-01 Implementirati Pen, Marker i Highlighter sa precizno definisanim ponašanjem providnosti i preklapanja.
- [x] V1-DRAW-02 Dodati izbor boje, debljine i opacity-ja; crna je početna boja za standardnu olovku.
- [x] V1-DRAW-02a Dodati vizuelnu paletu sa HEX/RGB unosom i do 12 sačuvanih boja; povezati olovku, tekst, beleške, ivice/ispune oblika i pozadinu.
- [x] V1-DRAW-03 Implementirati obradu tačaka, prikaz aktivnog poteza i završavanje poteza bez vidljivog skoka.
- [x] V1-DRAW-04 Implementirati dogovoreni režim gumice sa pogođenim objektima i ispravnim ponašanjem pri zoom-u.
- [x] V1-DRAW-05 Omogućiti pamćenje poslednjeg alata i podešavanja prema dogovorenom obimu.
- [ ] V1-DRAW-06 Testirati brze, kratke, ukrštene i duge poteze, dodir jedne tačke, otkazivanje i više pokazivača.

Završetak: potezi ostaju izmenjivi posle čuvanja i mogu se brisati/vratiti bez promene drugih objekata.

## V1-HIST — izmene i Undo/Redo

- [x] V1-HIST-01 Definisati komande dodavanja, brisanja, transformacije i promene svojstava.
- [x] V1-HIST-02 Implementirati Undo/Redo i grupisanje kontinuiranog gesta u jednu korisničku operaciju.
- [x] V1-HIST-03 Definisati početak/završetak tekstualne izmene kao istorijsku operaciju.
- [x] V1-HIST-04 Ograničiti istoriju prema dogovorenom budžetu, bez kopiranja cele table na svakoj tački.
- [x] V1-HIST-05 Povezati istoriju sa stanjem nesnimljenih izmena i poslednjim sačuvanim stanjem.
- [x] V1-HIST-06 Testirati mešane operacije, novu izmenu posle Undo, krajeve istorije i prekid operacije.

Završetak: Undo/Redo deterministički vraća sadržaj i svojstva; granice istorije su definisane.

## V1-OBJ — oblici, tekst, beleške i selekcija

- [x] V1-OBJ-01 Implementirati pravougaonik, kvadrat, krug, elipsu, trougao i romb.
- [x] V1-OBJ-02 Implementirati linije i strelice, prikaz tokom prevlačenja i završavanje kreiranja.
- [x] V1-OBJ-03 Implementirati unos i izmenu teksta: veličina, boja, bold, italic i poravnanje.
- [x] V1-OBJ-04 Rešiti tastaturu, fokus, višeredni tekst, promenu veličine i dovršavanje/otkazivanje unosa.
- [x] V1-OBJ-05 Implementirati StickyNote sa tekstom, bojom, promenom veličine, pomeranjem, dupliranjem i brisanjem.
- [x] V1-OBJ-06 Implementirati hit testing po tipu objekta, preklapanju i nivou zoom-a.
- [x] V1-OBJ-07 Dodati selekcione granice, ručke za resize, pomeranje i brisanje uz dogovoreno ponašanje svakog tipa.
- [x] V1-OBJ-08 Implementirati ostale V1 operacije selekcije tek nakon odluke P-04; preostale vezati za V2-EDIT.
- [x] V1-OBJ-09 Povezati sve izmene objekata sa istorijom i stanjem dokumenta.
- [ ] V1-OBJ-10 Proveriti tekst latinice/ćirilice, duge beleške i preklopljene/tanke objekte.

Završetak: svaki V1 objekat može da se napravi, izmeni, sačuva, ponovo otvori i poništi kroz Undo/Redo.

## V1-SAVE — čuvanje i oporavak

- [x] V1-SAVE-01 Implementirati repository ugovor i lokalnu Room/datotečnu implementaciju prema zapisanom persistence dizajnu.
- [x] V1-SAVE-02 Implementirati transakcijsko čuvanje sačuvane verzije i pouzdano otvaranje dokumenta.
- [x] V1-SAVE-03 Rešiti promene nastale dok je čuvanje u toku; označiti sačuvanom samo odgovarajuću reviziju.
- [x] V1-SAVE-04 Prikazati stanja nesnimljeno/čuvanje/sačuvano/greška sa ponovnim pokušajem.
- [x] V1-SAVE-05 Implementirati Save/Discard/Cancel pri izlasku sa promenama.
- [x] V1-SAVE-06 Napraviti odvojen recovery zapis i proveru dostupnog oporavka pri ponovnom pokretanju.
- [x] V1-SAVE-07 Implementirati prihvatanje/odbacivanje recovery-ja i čišćenje zapisa bez prepisivanja pogrešne verzije.
- [x] V1-SAVE-08 Definisati i podesiti Android sistemski backup/transfer pravila u skladu sa obećanim lokalnim režimom.
- [x] V1-SAVE-09 Testirati process death, prekid tokom zapisa, nedostatak prostora i neispravne dokumente.
- [x] V1-SAVE-10 Sačuvati schema i document migracione fixture-e; testirati nadogradnju sa prethodne verzije formata.

Završetak: poslednja uspešna verzija ostaje čitljiva posle neuspešnog zapisa, a recovery odgovara usaglašenom intervalu. ViewModel nije zamena za trajno čuvanje.

## V1-HOME — biblioteka i podešavanja

- [x] V1-HOME-01 Implementirati listu/mrežu tabli, prazno stanje i New Board sa validacijom naziva.
- [x] V1-HOME-02 Omogućiti otvaranje i preimenovanje table; odvojiti datum izmene od poslednjeg otvaranja.
- [x] V1-HOME-03 Implementirati Recent redosled i asinhrono generisanje/osvežavanje thumbnail-a.
- [x] V1-HOME-04 Implementirati Light/Dark/System i trajno čuvanje malih podešavanja kroz DataStore ili odgovarajući namenski adapter.
- [x] V1-HOME-05 Implementirati dogovorene V1 pozadine odvojeno od teme aplikacije.
- [x] V1-HOME-06 Implementirati onboarding, Help gestova, osnovni Settings i informacije o verziji/privatnosti/podršci.
- [ ] V1-HOME-07 Proveriti navigaciju nazad, ponovno otvaranje, nedostupnu tablu i očuvanje stanja liste.

Završetak: korisnik bez interneta može samostalno da napravi, sačuva, pronađe i nastavi svoju tablu.

## V1-EXP — PNG i deljenje

- [x] V1-EXP-01 Definisati izvoz iz stabilnog snimka dokumenta, nezavisno od trenutnog zumiranja.
- [x] V1-EXP-02 Implementirati dogovorene granice, pozadinu i rezoluciju PNG-a; isključiti toolbar i selekcione oznake.
- [x] V1-EXP-03 Ograničiti memorijsku potrošnju i dimenzije; prikazati smislen izbor kada je traženi izvoz prevelik.
- [x] V1-EXP-04 Implementirati korisnički izbor destinacije, otkazivanje i poruku o uspehu/neuspehu.
- [x] V1-EXP-05 Implementirati sistemski Share ako je uključen u V1; koristiti kontrolisane URI dozvole i čistiti privremene datoteke.
- [ ] V1-EXP-06 Proveriti praznu tablu, providnost, negativne koordinate, tekst i velike dokumente u izvezenoj slici.

Završetak: PNG se otvara u drugoj aplikaciji i odgovara sadržaju i odabranim postavkama.

## QA — stabilizacija i uslov za release

- [ ] QA-01 Održavati unit testove geometrije, komandi, istorije, dokumenta i validacije.
- [ ] QA-02 Održavati integracione testove Room-a, migracija, save/load, recovery-ja i neuspešnih zapisa.
- [ ] QA-03 Dodati UI testove ključnih tokova: Home → nova tabla → uređivanje → Save → ponovno otvaranje → izvoz.
- [ ] QA-04 Proveriti ceo V1 tok offline, bez naloga, bez nepotrebnih dozvola i bez skrivene mrežne zavisnosti.
- [ ] QA-05 Proveriti referentni mali/veliki telefon, tablet i stylus uređaj na minimalnoj i aktuelnoj podržanoj Android verziji.
- [ ] QA-06 Proveriti rotaciju, multi-window gde je podržan, tastaturu, background/foreground i gašenje procesa.
- [ ] QA-07 Izmeriti frame time, latenciju crtanja, memoriju, save/load/export na dogovorenim malim i velikim dokumentima.
- [ ] QA-08 Otkloniti dokazana uska grla, blokiranje glavne niti i curenje memorije; ponoviti relevantna merenja.
- [ ] QA-09 Proveriti TalkBack za dostupni UI, dodirne površine, kontrast, uvećan font i lokalizovane tekstove.
- [ ] QA-10 Proveriti produkcijski build sa optimizacijama, potpisom i svim korišćenim bibliotekama, uključujući native kompatibilnost ako postoji.
- [ ] QA-11 Pregledati dozvole, izložene Android komponente, backup pravila, privremene fajlove, logove i licence zavisnosti.
- [ ] QA-12 Napraviti izveštaj o pokrivenosti zahtevima, rezultatima na uređajima i poznatim ograničenjima; rešiti blokirajuće greške.

Završetak: svaki usaglašeni V1/SAFE kriterijum ima dokaz provere; nema otvorenih blokirajućih grešaka ni poznate korupcije dokumenata.

## GP — Google Play priprema, testiranje i objava

- [ ] GP-01 Sa vlasnikom proveriti/otvoriti developer nalog, tip naloga, identitet i trenutne uslove pristupa produkciji.
- [ ] GP-02 Potvrditi applicationId, ime, jezike, kategoriju i podatke vlasnika aplikacije.
- [ ] GP-03 Ponovo proveriti target API, tehničke uslove za AAB, biblioteke i aktuelne Play politike neposredno pre predaje.
- [ ] GP-04 Podesiti Play App Signing, generisanje release AAB-a i bezbedno čuvanje upload ključa uz vlasnika; tajne ne čuvati u izvorima.
- [ ] GP-05 Pripremiti politiku privatnosti sa javnim URL-om i pristupom iz aplikacije, prema stvarnom ponašanju i SDK-ovima.
- [ ] GP-06 Popuniti Data safety, ciljnu publiku, content rating, ads/app access i ostale primenljive deklaracije.
- [ ] GP-07 Pripremiti finalnu ikonu, feature graphic, snimke telefona/tableta, kratak i pun opis i kontakt za podršku.
- [ ] GP-08 Postaviti test izdanje na internal track uz odobren pristup nalogu; proveriti instalaciju i ažuriranje preko Play-a.
- [ ] GP-09 Pregledati Play pre-launch izveštaje, crashes/ANR i povratne informacije testera; rešiti nađene probleme.
- [ ] GP-10 Organizovati closed testing i angažovanje stvarnih testera prema pravilima konkretnog naloga; rok zavisi od njihovog učešća.
- [ ] GP-11 Pripremiti zahtev za production access kada je potreban i otkloniti eventualne primedbe; odobrenje daje Google.
- [ ] GP-12 Pripremiti finalni release kandidat, beleške verzije, zemlje distribucije, cenu/besplatnu distribuciju i plan objavljivanja.
- [ ] GP-13 Predati aplikaciju i pustiti u produkciju tek uz odobrenje vlasnika za konkretno izdanje.
- [ ] GP-14 Posle objave pratiti Android vitals, podršku i ponašanje izdanja; pripremiti ispravke sa novim versionCode-om i bez gubitka podataka.

Završetak: izdanje je odobreno i dostupno u izabranim zemljama, a instalacija iz prodavnice je proverena. Priprema paketa nije isto što i objava.

Izvori za proveru promenljivih pravila: [target API](https://support.google.com/googleplay/android-developer/answer/11926878), [testing](https://support.google.com/googleplay/android-developer/answer/14151465), [privatnost](https://support.google.com/googleplay/android-developer/answer/10144311), [signing](https://developer.android.com/studio/publish/app-signing).

## V2-ORG — organizacija i produktivnost

- [x] V2-ORG-01 Implementirati više stranica: dodavanje, preimenovanje, dupliranje, brisanje, redosled i izbor aktivne stranice.
- [x] V2-ORG-02 Implementirati foldere/podfoldere, pomeranje tabli i zaštitu od ciklusa foldera; definisati brisanje foldera sa sadržajem.
- [ ] V2-ORG-03 Implementirati favorites i filter omiljenih tabli.
- [ ] V2-ORG-04 Implementirati Trash, Restore i trajno brisanje sa potvrdama i čišćenjem vezanih priloga; definisati retention politiku.
- [x] V2-ORG-05 Implementirati lokalnu pretragu tabli, foldera, stranica i tekstualnih objekata sa prelaskom na rezultat.
- [x] V2-ORG-06 Implementirati opcioni autosave, interval/status i jasno ponašanje prema ručnom Save-u i recovery-ju.
- [ ] V2-ORG-07 Implementirati lokalni prenosivi backup svih dokumenata i priloga, opcioni automatski backup i korisnički izbor destinacije.
- [x] V2-ORG-08 Implementirati restore sa validacijom, migracijom, pravilima spajanja/zamene, duplikatima i bezbednim neuspehom.
- [ ] V2-ORG-09 Definisati zaključavanje i šifrovanje, PIN recovery i skrivanje osetljivih pregleda; implementirati dogovorenu PIN/biometrijsku zaštitu.
- [ ] V2-ORG-10 Implementirati grid/dots/ruled/custom pozadine, omiljene kombinacije alata i podešavanja koja nisu ušla u V1.
- [x] V2-ORG-11 Napraviti izmenjive šablone: Blank/Grid/Dotted, Brainstorming, Kanban, Weekly Planner, Study Notes i kasnije dijagramske šablone.
- [ ] V2-ORG-12 Testirati migraciju V1 dokumenata, organizaciju, pretragu, backup/restore i zaključavanje bez interneta.

## V2-MEDIA — slike i PDF

- [ ] V2-MEDIA-01 Odabrati PDF/image biblioteke posle provere licenci, offline rada, performansi i Android kompatibilnosti.
- [ ] V2-MEDIA-02 Implementirati import iz sistemskog izbora fotografija/datoteka i kamere sa minimalnim dozvolama.
- [x] V2-MEDIA-03 Kopirati uvezene priloge u kontrolisano skladište, obraditi orijentaciju i velike slike, povezati priloge sa dokumentom.
- [ ] V2-MEDIA-04 Implementirati image move/resize/rotate/crop/duplicate/delete i ponašanje tih operacija u istoriji i izvozu.
- [ ] V2-MEDIA-05 Definisati mapiranje PDF stranica u dokument; implementirati izbor stranica, prikaz i ograničenja za velike PDF-ove.
- [x] V2-MEDIA-06 Implementirati pisanje, crtanje, highlighter i sticky notes preko PDF-a; komentare povezati sa V3 kada budu dostupni.
- [x] V2-MEDIA-07 Implementirati PDF izvoz sa anotacijama i odrediti šta ostaje izmenjivo u whiteboard dokumentu i izvoznom PDF-u.
- [ ] V2-MEDIA-08 Implementirati JPG i opšti višestranični PDF izvoz po dogovorenom obimu; SVG odvojeno proveriti za raster/PDF sadržaj.
- [ ] V2-MEDIA-09 Testirati velike, oštećene i lozinkom zaštićene PDF-ove, nedostupne URI-je i čišćenje nekorišćenih priloga.

## V2-EDIT — napredno uređivanje i dijagrami

- [ ] V2-EDIT-01 Implementirati preostale rotate/style/z-order/copy/paste/duplicate operacije i pravila sistemskog clipboard-a.
- [x] V2-EDIT-02 Implementirati multi-select, pravougaonu/lasso selekciju prema odluci i zajedničko pomeranje/brisanje/dupliranje.
- [x] V2-EDIT-03 Implementirati grupisanje/razgrupisavanje sa stabilnim transformacijama i Undo/Redo.
- [ ] V2-EDIT-04 Implementirati snap, pomoćne linije, poravnanje i raspoređivanje više objekata uz mogućnost isključivanja.
- [ ] V2-EDIT-05 Implementirati opcioni Smart Shapes sa jasnim prihvatanjem/poništavanjem prepoznavanja.
- [ ] V2-EDIT-06 Implementirati flowchart oblike: Start/End, Process, Decision, Database, Document i Input/Output.
- [ ] V2-EDIT-07 Implementirati smart konektore sa vezivanjem krajeva, prilagođavanjem pri pomeranju i pravilima brisanja povezanog objekta.
- [ ] V2-EDIT-08 Implementirati ručne mind maps: roditelj/deca, brzo dodavanje, boje i collapse/expand.
- [ ] V2-EDIT-09 Dodati Pencil i dodatne fontove ako su prioritet; proveriti font licence i ugrađivanje pri izvozu.
- [ ] V2-EDIT-10 Unaprediti stylus: pressure, dugmad/eraser gde su podržani i podesive finger/stylus gestove.
- [ ] V2-EDIT-11 Procena i po potrebi implementacija mini-map-a, pomerljivog toolbar-a i checkbox/task beležaka.
- [ ] V2-EDIT-12 Testirati nove objekte kroz save/load, istoriju, backup i svaki podržani format izvoza.

Završetak V2 grupa: stare table se otvaraju bez gubitka sadržaja; nove lokalne funkcije rade bez naloga i interneta. Ne moraju sve grupe izaći u istom izdanju.

## V3-CLOUD — nalog, serverska osnova i sinhronizacija

Odluka korisnika od 20.10.2026: cloud, saradnja i AI ulaze u prvo Play izdanje. Oznake V3/V4 označavaju grupe rada, ne odlaganje za sledeće javno izdanje. Serverski deo naloga i autorizacije je započet i testiran; zadaci ostaju otvoreni dok ne budu završeni klijent, sinhronizacija i produkciona provera. Detalji: BACKEND.md.

Napredak: nalog je povezan sa Android interfejsom i Keystore čuvanjem sesije. Povezani su izričito slanje sačuvane table, trajni zahtev za prvo slanje, cloud lista/pregled i pravljenje nezavisne lokalne kopije. Provereni su izgubljen odgovor servera, ponovni pokušaj bez duplikata, nadogradnja baze sa neslatim izmenama, konflikti i zaštita pri promeni naloga. Ista 32 serverska scenarija proverena su na H2 i PostgreSQL-u: revizije, delta operacije, trajna evidencija brisanja, nastavak istorije, dozvole, kvote i nadogradnja stare baze. Zajednički editor, pozadinsko slanje, prilozi, zajednički Undo i saradnja uživo ostaju u radu; detalji su u SYNCHRONIZATION.md. Produkciono slanje emaila, hosting i AI još nisu povezani. Stavke ostaju otvorene do završetka celih tokova.

- [ ] V3-CLOUD-01 Definisati cloud obim, budžet, regije, kvote, retention i izbor managed platforme ili sopstvenog backend-a.
- [ ] V3-CLOUD-02 Napraviti serverski tehnički predlog, API ugovore, verzionisanje, monitoring i odvojena okruženja.
- [ ] V3-CLOUD-03 Implementirati opcioni nalog, prijavu/odjavu, obnovu sesije i tokove oporavka identiteta.
- [x] V3-CLOUD-04 Definisati preuzimanje lokalnih tabli u nalog, promenu naloga i ponašanje lokalnih kopija pri odjavi.
- [ ] V3-CLOUD-05 Implementirati serversko vlasništvo dokumenata/priloga, autorizaciju svake operacije i upravljanje kvotama.
- [x] V3-CLOUD-06 Definisati revizije, identitet promena, tombstones i strategiju konflikata pre pisanja sync engine-a.
- [ ] V3-CLOUD-07 Implementirati lokalni red slanja, ponovne pokušaje, idempotentnost i nastavak posle prekida mreže.
- [ ] V3-CLOUD-08 Implementirati upload/download dokumenata i priloga, delta promene i vidljiva sync/error/offline stanja.
- [ ] V3-CLOUD-09 Implementirati cloud backup i restore odvojeno od sinhronizacije; proveriti oporavak greškom obrisanih podataka.
- [ ] V3-CLOUD-10 Implementirati brisanje naloga/podataka, izvoz podataka i ažurirati privatnost/Data safety pre uključivanja servisa.
- [ ] V3-CLOUD-11 Testirati dva uređaja, istovremene offline izmene, brisanje naspram izmene, promenu naloga i neovlašćen pristup.

## V3-COLLAB — deljenje i istovremeni rad

- [ ] V3-COLLAB-01 Definisati invitation/link model, pristup primaoca i potrebu za nalogom; implementirati opoziv deljenja.
- [ ] V3-COLLAB-02 Implementirati Viewer/Commenter/Editor dozvole na serveru i u klijentu.
- [ ] V3-COLLAB-03 Izabrati protokol i model konkurentnih izmena na osnovu provere; definisati semantiku zajedničkog Undo/Redo.
- [ ] V3-COLLAB-04 Implementirati real-time izmene, reconnect, redosled, duplikate i lokalno optimističko uređivanje.
- [ ] V3-COLLAB-05 Implementirati live cursors, oznake korisnika i presence sa ograničenjem učestalosti slanja.
- [ ] V3-COLLAB-06 Implementirati komentare na tabli/objektu, odgovore, resolve i ponašanje kada se objekat obriše.
- [ ] V3-COLLAB-07 Implementirati pregled aktivnosti i dogovorena obaveštenja sa odgovarajućim korisničkim kontrolama.
- [ ] V3-COLLAB-08 Testirati promenu/opoziv uloga tokom rada, offline povratak, više učesnika i dogovoreni maksimalni teret.
- [ ] V3-COLLAB-09 Pripremiti serverske alarme, backup proveru, operativni plan i postepeno uključivanje servisa.

Završetak V3: promene konvergiraju u proveravanim scenarijima, ovlašćenja važe i na serveru, a lokalni rad ostaje moguć kada cloud nije dostupan.

## V4-AI — AI i prepoznavanje

- [ ] V4-AI-01 Napraviti evaluacione primere rukopisa/dijagrama, podržane jezike i merljive kriterijume kvaliteta.
- [ ] V4-AI-02 Proveriti lokalni OCR/rukopis naspram cloud servisa: kvalitet, uređaje, licencu, latenciju, privatnost i cenu.
- [ ] V4-AI-03 Definisati korisnikov izbor sadržaja za slanje, informisanje, retention i eksplicitno pokretanje cloud obrade.
- [ ] V4-AI-04 Implementirati serverski pristup plaćenim AI servisima, zaštitu ključeva, kvote, rate limiting i ograničenje troška gde su potrebni.
- [ ] V4-AI-05 Implementirati OCR slike/PDF-a sa pregledom, kopiranjem i ubacivanjem teksta na tablu.
- [ ] V4-AI-06 Implementirati izbor rukopisa → tekst, pregled rezultata i izbor zamene/dodavanja bez gubitka originala.
- [ ] V4-AI-07 Implementirati AI rezime ideja, odluka, zadataka i datuma uz mogućnost provere i uređivanja.
- [ ] V4-AI-08 Implementirati generisanje mind map-a i dijagrama u validiranom modelu izmenjivih objekata i konektora.
- [ ] V4-AI-09 Implementirati Beautify: prepoznavanje elemenata/teksta/veza, raspored i pregled pre prihvatanja.
- [ ] V4-AI-10 Omogućiti poništavanje prihvaćenog AI rezultata kao jedne logičke operacije; otkazivanje ne menja dokument.
- [ ] V4-AI-11 Implementirati Smart Search preko tekstualnog/OCR/rukopis sadržaja i kontrolisati privatnost indeksa.
- [ ] V4-AI-12 Obraditi timeout, offline stanje, prekoračenu kvotu, neispravan izlaz i ponovne zahteve bez duplih izmena.
- [ ] V4-AI-13 Proveriti kvalitet, troškove i činjenicu da sadržaj dokumenta ne može pokrenuti neodobrene radnje; ažurirati Play deklaracije.

Završetak: AI rezultat je pregledljiv, izmenjiv i poništiv; slanje sadržaja i potrošnja su kontrolisani, a original može da se sačuva.

## PRO — monetizacija, kada bude potvrđena

- [ ] PRO-01 Odrediti Free/Pro funkcije, cenu, pretplatu/jednokratnu kupovinu i održive cloud/AI kvote.
- [ ] PRO-02 Proveriti primenljiva Google Play Billing pravila i izabrati podržanu implementaciju pre prodaje digitalnih funkcija.
- [ ] PRO-03 Implementirati kupovinu, obnovu prava, pending/cancel/refund i verifikaciju kupovine gde je potrebna.
- [ ] PRO-04 Definisati offline prava i ponašanje nakon isteka Pro-a; postojeći lokalni rad ne sme neprimetno nestati ili se oštetiti.
- [ ] PRO-05 Testirati naplatu kroz Play test okruženje i uskladiti prikaz ponude, uslove i podršku.

## X — buduće platforme i održavanje

- [ ] X-01 Doneti zasebnu odluku o iOS-u: nativni Swift/SwiftUI i/ili deljenje čistog Kotlin jezgra kroz Kotlin Multiplatform nakon provere izvodljivosti.
- [ ] X-02 Definisati kompatibilan format dokumenta i API ugovore za iOS, web i desktop pre njihove implementacije.
- [ ] X-03 Napraviti zaseban backlog interfejsa, input-a, skladišta, izvoza i prodavnice za svaku odobrenu dodatnu platformu.
- [ ] X-04 Održavati podršku za Android/Play promene, zavisnosti, licence, bezbednosne ispravke i migracije podataka.
- [ ] X-05 Voditi povratne informacije, prioritete grešaka, release beleške i regresione scenarije nakon svake objave.

## Obavezne zavisnosti van samog koda

| Zavisnost | Za koje zadatke je potrebna |
|---|---|
| Vlasnik i Play Console nalog | GP-01/02/04/08/10/11/12/13, PRO testiranje i naplata |
| Stvarni telefoni/tableti/stylus | A-11/12, V1-CAN, QA-05/07, V2 stylus |
| Testeri i protek propisanog vremena | GP-10/11, zavisno od naloga |
| Javni hosting politike privatnosti i kontakt | GP-05/07 |
| Budžet, cloud i AI nalozi | V3/V4 cloud funkcije |
| Odobrenje konkretnog javnog izdanja | GP-13 i buduće produkcione objave |

## Zajednički uslov završetka funkcije

1. Ponašanje odgovara usaglašenom zahtevu i uključuje prazna, neuspešna i otkazana stanja.
2. Kod poštuje module i granice odgovornosti; nema tajni i osetljivog sadržaja u logovima.
3. Relevantne statičke provere i smisleni testovi prolaze; testovi ne proveravaju samo preslikanu implementaciju.
4. Promene dokumenta imaju odgovarajuće ponašanje čuvanja, istorije, migracije i izvoza.
5. Korisnički tok je proveren na odgovarajućem ekranu/uređaju; performanse su izmerene kada promena na njih utiče.
6. Dokumentacija, kriterijumi i poznata ograničenja su ažurirani; release uslovi se proveravaju za celo izdanje.
