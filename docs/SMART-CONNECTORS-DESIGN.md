# F05 — pametni konektori: ugovor i zavisnosti

Nastavak 4. novembra posle kvalifikovane 0.6.5/source 5129627: PageContent deli validaciju, ID-jeve, bounds i indeks portova između metadata-only kopija stranice. Port/resolver tipovi su u model sloju; serija veza koristi već provereni broj objekata/ID skup i isti indeks. Osam novih JVM slučajeva uključuje 99.999 ciljeva, otkazivanje i konkurentne čitaoce. PAGE-GEOMETRY-SNAPSHOTS.md opisuje ugovor i preostale granice. Ovo nije Element/format/UI integracija, pa F05.3 i glavna F05 stavka ostaju otvoreni; detalji lokalnog/CI prolaza biće vezani za tačan novi izvor.

Status 3. novembra 2026: core jezgro na grani `codex/smart-connectors`. Portovi prolaze punu regresiju. Source 0b3f169 uvodi Connector/ConnectorEnd i PageConnectionGeometry; svih 23 nova testa prolazi u verify poslu CI 41703271737. Ukupno 267 JVM (194+43+5+25), bez neuspeha/preskakanja, uz style/architecture/lint/release/security/license provere. API 36 je stao pre boot-a zbog manjka runner diska, pa nova puna Android matrica nije potvrđena. Model nije Element niti aktivan UI alat; nema novog tipa dokumenta u aplikaciji i F05 nije završen. Aktivna app verzija ostaje 0.6.3, format 4. Zahtev iz WHITEBOARD-VISION.md: pomeranje povezanog oblika automatski prilagođava vezu. Postojeća Arrow ostaje nezavisna linija i stari dokumenti ne menjaju ponašanje.

`ConnectionPort` i `PagePortIndex` predstavljaju prvi izolovani deo. Indeks je nepromenljiv, vezan za ID stranice i identitet liste elemenata; viewport ne utiče na world geometriju. Indeksira top-level i child ID-jeve, validira render okvir jednom po podržanom cilju i čuva samo njegov okvir/transformaciju za dalja port računanja. Grupa se ne skenira ponovo za svaku vezu. Stroke/Line i nepostojeći ID nisu dozvoljeni ciljevi; nevažeća geometrija prekida izgradnju pre objavljivanja indeksa. Konstrukcija ima checkpoint po objektu.

Svih 14 novih JVM testova prolazi: stvarne konture šest oblika, lokalni trougao, tekst/beleška, affine rotation/scale/shear, legacy i affine medij, grupni/child portovi, odbijanje nepoznatih/nepodržanih ciljeva, degenerisani okviri, invalidacija snapshot-a, koordinatni limit, otkazivanje i grupa sa 10.000 članova. Testovi upoređuju portove i sa postojećim ShapeGeometry pravilima. CI 41518088012/source 8cb5da1 potvrđuje 244 JVM (171 core + 43 format + 5 data + 25 ViewModel), svih pet profila po 86 app + 13 data, bez neuspeha/preskakanja, i verify/release smoke gates. Dokaz: artifacts/task-08-8cb5da1-evidence.json. Android obim ostaje isti jer novi alat još nije povezan.

Novi ConnectorEnd razlikuje validnu slobodnu world tačku od ID/port reference. Connector odbija sopstveni ID kao cilj, petlje ka istom objektu i nevažeće ID-jeve. PageConnectionGeometry ima jedan indeks portova po stranici, kopiju liste definicija i nepromenljive izvedene Line vrednosti sa istim ID/stil/arrow pravilima. Odbija duplikate i kolizije sa top-level/child ID-jevima, odsutne/nepodržane ciljeve, veze ka drugim konektorima, prekoračen zbirni broj objekata i nevažeći render okvir. Nema rekurzivnog graf obilaska; ciklusi između različitih ciljnih objekata su dozvoljeni. matches proverava page ID, identitet liste objekata i sadržaj definicija, uz viewport nezavisnost. Prekid izgradnje ne objavljuje delimičnu mapu. Resolver je početni deo zajedničke geometrije: još nije povezan sa postojećim Page.contentBounds/render/export/picking/preview potrošačima.

Source 4fee1e8 potvrđuje celo jezgro u punoj CI 41704569468 matrici: 267 JVM i pet profila po 86 app + 13 data, sa R8 smoke. Sledeći izvor 0e1ea78 uvodi Page.geometry/preview most za postojeće elemente. Page.contentBounds, renderer culling, hit-test, selekcija/snap, deo transform planning-a i CanvasAccessibility sada prolaze kroz taj kontekst. Zamene preview-a čuvaju originalne slojeve i ne menjaju session; clear/cancel/external-update vraća izvor. Devet JVM i tri Android testa čine deo c201456 kandidata i njegovog zasebnog CI 41707278041 uzorka. Ovaj most još ne menja Connector u Element i ne zatvara F05.3/F05.7/F05.8/F05.9.

## Korisnički ugovor

Korisnik povezuje dva postojeća objekta aktivne stranice izborom početnog i završnog objekta. Dostupna je i Objects/tastatura/accessibility alternativa preciznom prevlačenju. Prva verzija ima pravu liniju i strelicu sa fiksnim portovima; ortogonalno rutiranje/izbegavanje prepreka i petlje ka istom objektu su zasebna proširenja.

Portovi LEFT/TOP/RIGHT/BOTTOM/CENTER pripadaju lokalnoj geometriji oblika, beleške, teksta ili medija. Kod pravougaonog sadržaja koriste se sredine ivica; kod trougla/romba/elipse smer od lokalnog centra mora preseći stvarnu konturu, ne prazni AABB ugao. Podeliti tu geometriju sa postojećim ShapeGeometry pravilima. Rezultat se kroz afinni okvir mapira u dokument, pa port prati rotaciju. Grupa može imati portove zajedničkog world okvira; njena pravila su drugačija od portova pojedinačne rotirane dece. Potez, drugi konektor i objekat druge stranice nisu cilj porta prve verzije.

Move, resize, rotacija i poravnanje pomeraju vezani kraj. Samo pomeranje vezanog konektora ne pomera ciljeve: korisnik prvo odvezuje kraj ili bira i njegove ciljeve. Promena stila/debljine ne menja identitet veze. Fill ne boji konektor. Cela konektorska linija je jedan objekat u slojevima i Undo istoriji.

Brisanje cilja odvezuje njegov kraj na poslednjem važećem world položaju; ostatak veze ostaje vidljiv. Undo iste komande vraća i cilj i vezivanje. Brisanje samog konektora ne briše ciljeve. Cut prati ista pravila kao brisanje. Promena stranice ne stvara vezu preko stranica.

Copy/paste i dupliciranje cele stranice prvo prave kompletnu mapu starih/novih ID-jeva, uključujući decu grupa. Ako je cilj kopiran, vezivanje prelazi na njegov novi ID. Ako nije kopiran, kopirani kraj postaje slobodan na svom pomerenom world položaju. Nikada se ne povezuje slučajno sa starim objektom druge table. Clipboard mora zadržati geometriju i potrebne priloge.

Grupisanje koje zadržava ID deteta zadržava i port tog deteta. Kada flatten/group/ungroup ukloni ID postojeće grupe, vezu ka tom grupnom okviru treba atomski odvezati na poslednjem položaju; Undo vraća grupu i vezu. Nije dozvoljeno ostaviti viseći ID. Struktura grupa ostaje ravna.

U prvoj verziji konektor je objekat stranice, ne dete ElementGroup-a. Group akciju onemogućiti kada selekcija uključuje konektor; korisnik može grupisati njegove ciljeve, a spoljašnja veza i dalje prati stabilne child ID-jeve. Ne preskakati izabrani konektor prećutno i ne obećavati da slobodan kraj putuje sa grupom. Parser takođe odbija konektor unutar grupe. Šire grupisanje kompletnog grafa zahteva posebno definisano ponašanje i testove.

## Model i jedinstvena geometrija

Planiran je poseban `Connector` sa sopstvenim ID-jem, dva kraja, stilom i arrow zastavicom. Kraj je ili slobodna validna world tačka ili veza ka ID-ju i definisanom portu. Nema proizvoljnog URI-ja, skripte, mrežnog resursa ili rekurzivne veze na konektor.

Završne tačke vezanog konektora su izvedene iz trenutne stranice. Ne uvoditi više nezavisnih računanja u renderer, hit-test, izvoz i accessibility. Potreban je core `PageGeometry`/resolved snapshot koji indeksira top-level i child ID-jeve, razrešava sve veze i daje iste krajnje tačke i granice svim potrošačima. Izvorni Connector ostaje model koji se uređuje; razrešena linija nije njegova zamena u dokumentu.

Sadašnji `Element.bounds`/`selectionBounds`/`renderBounds` nemaju kontekst stranice. Zato novi tip nije bezbedno dodati samo u renderer. Pre implementacije preneti sledeće potrošače na zajednički kontekst: Page.contentBounds, whiteboard hit-test i transient preview, selekcija/snap/move, Objects i accessibility granice, PNG/JPG/PDF, thumbnail, clipboard i komande koje brišu ili preimenuju ID-jeve. Za stranice bez konektora sačuvati postojeći jednostavan put.

Indeks praviti jednom po immutable snapshot-u, u linearnom prolazu. Keš vezati za stvarnu listu elemenata/reviziju; ne za promenljivu globalnu mapu. Tokom prevlačenja prikaz veze mora koristiti istu preview geometriju kao cilj. Posle cancel-a prikazuje se original. Broj konektora ulazi u postojeći ukupni limit stranice; izračunavanje mora poštovati otkazivanje i izmereni budžet, bez tvrdnje o 60 Hz dok nema merenja.

## Istorija, format i zaštite

Dodavanje, odvezivanje/rebinding, promena stila i brisanje su regularne budžetirane komande. Promena koja uklanja cilj i odvezuje njegove konektore mora biti jedna atomska komanda, uključujući tačan stari redosled. Istorija ne kopira celu stranicu samo zbog jedne veze. Stale plan ne prepisuje noviju listu elemenata.

Novi tip zahteva JSON format 5 sa čitanjem postojećih 1–4. Room migracija nije automatski potrebna ako postojeće kolone ostaju iste. Posebno pregledati DocumentCodec, DocumentDeltaCodec, ElementDigest, ZIP backup/restore i oporavak. Stariji kod mora jasno odbiti noviji format. Čuvanje nove table ne sme prepisati original tokom neuspešne konverzije.

Pri uvozu odbiti nepostojeći ID, vezu na konektor/drugu stranicu, nepoznat port, isti cilj na oba kraja u ovoj prvoj verziji, duplikate, NaN/Infinity i prevelike koordinate. Ne popravljati napadački dokument tihim prevezivanjem na prvi sličan objekat. UI akcija namernog brisanja/ungroup-a proizvodi eksplicitno odvezivanje pre validacije. Render i parser dele isti limit broja objekata i geometrije.

## Prihvatanje pre zatvaranja

- Dva objekta: oba kraja prate move/resize/rotation/alignment i transient gest; cancel vraća prikaz. Portovi rotiranog pravougaonika, trougla, romba i elipse pripadaju stvarnoj konturi.
- Delete/cut, grupisanje/ungroup i Undo/Redo ne ostavljaju viseće ID-jeve i čuvaju slojeve.
- Copy/paste delimične/potpune selekcije, kopiranje između tabli i duplicate-page imaju tačno remapirane ili odvezane krajeve.
- Save/reopen, draft recovery, ZIP restore, PNG/JPG/PDF, thumbnail, Objects i accessibility koriste istu geometriju.
- Format 1–4 ostaje čitljiv; format 5 ima roundtrip/delta/digest provere. Malformed/adversarial reference i limiti se odbijaju bez delimičnog upisa.
- Domenski/ViewModel/Android testovi i minified release smoke dokazuju stvarno praćenje cilja, ne samo postojanje dugmeta.
- Prolaz aktuelne OS/tablet matrice, release/security provera i izmereno ponašanje velikog grafa vezuju se za tačan kandidat. Fizička olovka i fizički frame-time ostaju u H obimu.

F06 mind-map roditelj/deca i F07 flowchart koriste ovaj lokalni model tek kada je kvalifikovan. AI, backend, cloud i nalozi ne uvode se ovim projektovanjem.

## Preostala implementacija F05

- [x] F05.1 Izolovano jezgro portova i 14 JVM testova, source 49a5de9. Ovo nije aktivan Connector alat.
- [x] F05.2 Tipovi Connector/ConnectorEnd i invarianti, source 0b3f169; sedam model testova i provera nepodržanih referenci u resolver testovima prolaze u CI 41703271737/verify. Nema Element/format integracije.
- [ ] F05.3 Početni PageConnectionGeometry batch resolver i 16 testova prolaze, source 0b3f169/CI 41703271737/verify. Ostaje puna PageGeometry integracija sa svim potrošačima i transient preview tokovima.
- [ ] F05.4 Atomske session komande: dodavanje/rebinding/odvezivanje, delete/cut/group/ungroup, budžet i tačan Undo/Redo.
- [ ] F05.5 Dvoprolazni remap ID-jeva u ObjectClipboard i duplicate-page, uz pravilno odvezivanje nekopiranog cilja i očuvanje priloga.
- [ ] F05.6 Format 5: DocumentCodec, DocumentDeltaCodec, ElementDigest, stari 1–4 dokumenti, recovery i ZIP backup/restore; Room menjati samo ako šema to zahteva.
- [ ] F05.7 BoardRenderer, PageExporter i platform DocumentFiles (PNG/JPG/PDF, thumbnail/share) koriste isti resolved snapshot.
- [ ] F05.8 WhiteboardView picking/snap/preview i konektorski gest, bez delimičnog upisa pri cancel-u ili odbijenom kraju.
- [ ] F05.9 Editor, izbor portova/stila, Objects, keyboard/TalkBack alternativa i CanvasAccessibility granice/opisi koriste istu geometriju.
- [ ] F05.10 Adversarial reference/koordinate/veličine i lifecycle/concurrency testovi; geometrijski i vizuelni testovi stvarnog praćenja cilja.
- [ ] F05.11 Cela aktivna CI matrica i minified release smoke kompletne funkcije, uz proveru velikog grafa i očuvanje ranijih tokova.
- [ ] F05.12 Verzija, paket, security/ABI/16 KB/signature provere, korisničko uputstvo i source/hash mapa kompletnog kandidata.
