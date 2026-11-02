# F05 — pametni konektori: ugovor i zavisnosti

Status 2. novembra 2026: započeto core jezgro na grani `codex/smart-connectors`. Nema Connector modela/UI-ja niti novog tipa dokumenta u aplikaciji; F05 nije završen. Aktivna app verzija ostaje 0.6.3, format 4. Zahtev iz WHITEBOARD-VISION.md: pomeranje povezanog oblika automatski prilagođava vezu. Postojeća Arrow ostaje nezavisna linija i stari dokumenti ne menjaju ponašanje.

`ConnectionPort` i `PagePortIndex` predstavljaju prvi izolovani deo. Indeks je nepromenljiv, vezan za ID stranice i identitet liste elemenata; viewport ne utiče na world geometriju. Indeksira top-level i child ID-jeve, validira render okvir jednom po podržanom cilju i čuva samo njegov okvir/transformaciju za dalja port računanja. Grupa se ne skenira ponovo za svaku vezu. Stroke/Line i nepostojeći ID nisu dozvoljeni ciljevi; nevažeća geometrija prekida izgradnju pre objavljivanja indeksa. Konstrukcija ima checkpoint po objektu.

Napisano je 14 JVM testova: stvarne konture svih šest oblika, lokalni trougao, tekst/beleška, affine rotation/scale/shear, legacy i affine medij, grupni/child portovi, odbijanje nepoznatih/nepodržanih ciljeva, degenerisani okviri, invalidacija snapshot-a, koordinatni limit, otkazivanje i grupa sa 10.000 članova. Testovi upoređuju portove i sa postojećim ShapeGeometry pravilima. Lokalni stil prolazi; CI izvršenje ovog jezgra još nije potvrđeno. Očekivan zbir grane je 244 JVM, dok Android tokovi ostaju 86 app + 13 data jer novi alat još nije povezan.

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
