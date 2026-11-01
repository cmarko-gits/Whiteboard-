# F02 — rotacija objekata: kriterijumi i projektovanje

Stanje 1. novembra 2026: implementacija 0.6.0/code 8 pripremljena je na grani codex/object-rotation. Dijalog za relativni ugao, model, selekcija, Fill, čuvanje i renderer su povezani; integracioni testovi još nisu izvršeni. Ovaj dokument ne označava F02 kao završen.

`AffineTransform2D` u core modelu podržava rotaciju oko zajedničkog centra, translaciju, neuniformno skaliranje/refleksiju, kompoziciju i inverziju, mapiranje AABB granica i direktno inverzno mapiranje tačke bez nove matrice pri svakom hit-testu. Matrica zadržava Double preciznost do mapiranja u postojeći Point model. Neispravne, singularne i numerički prevelike matrice se odbijaju. Quarter-turn uglovi ostaju tačni.

12/12 novih testova prolazi, uključujući 500 slučajeva sa fiksiranim seed-om i kombinaciju rotation → nonuniform scale → inverse. Cela JVM regresija ove grane prolazi: core 107 + format 36 + data 5 + ViewModel 15 = 163. Logovi: artifacts/task-12-affine-tests.log i task-12-foundation-regression.log. Ovaj rezultat nije potvrda integracije sa rendererom, skladištem ili UI-jem; ti koraci ostaju otvoreni.

## Prihvatanje funkcije

1. Korisnik može da rotira jedan objekat ili više izabranih objekata/grupu za proizvoljan ugao. Dijalog navodi da li je vrednost relativna ili apsolutna; podržava otkazivanje, tastaturu, TalkBack i mali prozor sa 200% fontom.
2. Rotacija više objekata koristi zajednički centar selekcije. Slojevi, identifikatori, tekst, boje, prilozi i članstvo grupe ostaju očuvani. Jedna potvrda predstavlja jednu Undo komandu; Redo reprodukuje isti rezultat.
3. Izbor dodirom i Fill koriste stvarnu geometriju nakon rotacije. Prazan ugao pravougaonog bounding box-a rotirane slike ili beleške nije deo tog objekta. Ne pretpostavljati da je AABB isto što i obojena površina.
4. Move/resize, snap, selection handles, Objects navigator i virtuelne accessibility granice odgovaraju vidljivom objektu. Posebno proveriti rotaciju praćenu neuniformnim resize-om.
5. Whole/Partial eraser ostaju ispravni za rotirane poteze. Copy/cut/paste, group/ungroup i dupliciranje ne gube položaj ili ugao.
6. Uređivanje postojećeg rotiranog teksta/beleške zadržava transformaciju. Crop i rotacija medija se pravilno kombinuju.
7. Save/reopen, recovery, backup/restore, PNG/PDF i pretraga koriste isti model i zadržavaju rotaciju. Stare verzije dokumenata i postojeći mediji sa rotation poljem ostaju čitljivi; format koji stari čitač pogrešno prikazuje mora dobiti novu verziju.
8. Granice koordinata, singularne/neispravne matrice, overflow i trošak Undo provere se izvršavaju pre izmene dokumenta. Odbijena operacija ne ostavlja delimično rotiranu grupu.

## Implementirana odluka

Pravougaoni sadržaj koristi `AffineElement`: jedan lokalni okvir centriran na nulu i jednu `AffineTransform2D` matricu. Dozvoljeni su Shape, Note, TextElement i MediaElement; grupe i ugnježdene transformacije u sadržaju su zabranjene. Linije i potezi se rotiraju u koordinatama dokumenta, pa postojeća parcijalna gumica nastavlja da obrađuje iste polilinije. Ravne grupe zadržavaju svoje ID-jeve i članstvo.

Rotacija i naknadni neuniformni resize komponuju se bez gubitka smicanja. Kod postavljenog teksta/beleške resize skalira prikaz sadržaja; običan tekst bez transformacije zadržava postojeći reflow. Dijalog ovo objašnjava. Debljina okvira oblika ostaje u jedinicama dokumenta, uključujući neuniformni resize.

Planiranje rotacije radi na Default dispatcher-u uz kooperativno otkazivanje. Pre izmene proverava budžet istorije i koordinate; primena odbija plan čija se početna stranica u međuvremenu promenila. Potvrda je jedna Undo komanda. Dijalog zatvara sopstvenu tastaturu i prikazuje zauzetost do završetka planiranja.

JSON format je podignut na 4 jer stari čitači ne razumeju afinni sadržaj. Čitanje verzija 1–3 ostaje podržano; Room šema ostaje 4 jer se SQL struktura nije menjala. Dekoder zabranjuje singularne/prevelike matrice, ugnježdene okvire, necentrirani izvor i neslaganje ID-ja izvora i objekta. Prilozi, pretraga, stilovi i uređivanje sadržaja prolaze kroz isti geometrijski sloj.

Dodato je 16 testova rotacije, sedam format testova, pet ViewModel testova, šest Android renderer/native-input testova, jedan kompletan UI tok i jedan backup/restore/PDF tok. Dodatnih pet testova komandi štiti redosled pri velikim izmenama, uključujući rotaciju 5000 od 10000 objekata. Dispatcher računanja je injektovan; paralelna izmena i otkazivanje proveravaju se kontrolisanim testnim scheduler-ima.

Prvi integracioni CI 41341674388/source aafa34d prošao je verify. CI 41342367471/source 401168d takođe je prošao verify; njegovi Android poslovi su još u toku. Kasnija injekcija dispatcher-a, dva konkurentna ViewModel testa i optimizacija komandi još čekaju novu kompilaciju/regresiju. Prethodno potvrđenih 12 testova osnove i 163 JVM testa odnosi se isključivo na raniji commit 2a3763f; ne pripisivati taj broj integraciji.

Primena standardnih komandi sa različitim nenegativnim odredištima spaja zadržane i izmenjene objekte u jednom prolazu posle sortiranja. Time velika proređena selekcija ne pomera veliki niz pri svakom ubacivanju. Za ponovljena/negativna odredišta čuva se ranije ponašanje; zasebni testovi pokrivaju clamping i Undo. Ovo je algoritamska optimizacija, bez tvrdnje o izmerenih 60 Hz na uređaju.

## Nalazi koji su vodili implementaciju

- `MediaElement` čuva ugao 0–359 i ima `rotatedBounds`; renderer rotira oko centra.
- Ostali objekti ranije nisu imali transformaciju. `Element.bounds`, `selectionBounds` i `renderBounds` imaju različite namene; selekcija sada uključuje vidljive granice legacy medija.
- Trenutni resize nije ograničen na isti faktor po obe ose. Samo dodavanje angle polja ne predstavlja tačno kombinaciju rotacije i neuniformnog skaliranja: rezultat može sadržati smicanje.
- `leaves()`, Fill, stroke eraser, stilovi i uređivanje teksta prepoznaju konkretne tipove. Novi geometrijski sloj mora dosledno da prođe kroz sve ove tokove.
- Pre ove funkcije JSON je bio verzija 3, a Room šema 4. Promena sadržaja JSON-a nije automatski promena SQL šeme.

## Arhitektonska odluka koju implementacija mora potvrditi

Koristiti jedan čist Kotlin geometrijski sloj sa afinnim transformacijama, inverznim mapiranjem za hit-test i jasnim granicama lokalnih i koordinata dokumenta. Geometrija prikaza i izvoza mora dolaziti iz istih podataka. Android Canvas matrica je adapter, ne domenski model.

Pre izbora konačnog modela dokazati testovima kombinaciju rotate → nonuniform resize → fill/edit → undo → save/reopen. Razmotreni su zajednička transformacija elementa i normalizacija pojedinačnih tipova; ne uvoditi wrapper koji postojeća gumica ili stilovi tiho preskaču. Za grupe sačuvati postojeće pravilo ravne strukture i semantiku ID-jeva.

## Obavezne provere

Domenski testovi: uglovi 0/45/90/180/270/360 i negativni, zajednički pivot, grupe, granične koordinate, inverse round-trip, stabilan Undo/Redo, promašaj u praznom AABB uglu, Fill i gumica. Format testovi: round-trip, legacy verzije/mediji i odbijanje oštećenih transformacija. Android: stvarni renderer/izvoz piksela, dodir/selektor/Fill, uređivanje teksta, čuvanje i recovery, modalni fokus i pristupačnost, phone/tablet portrait/landscape i minified paket.

Funkcija ostaje otvorena dok ove provere i puna regresija konkretnog kandidata ne prođu. Govorni izlaz TalkBack-a, fizički uređaji i release performanse nisu potvrđeni ovim kodom.
