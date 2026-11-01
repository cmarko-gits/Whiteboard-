# F02 — rotacija objekata: kriterijumi i projektovanje

Stanje: geometrijska osnova je implementirana na grani codex/object-rotation; korisnička funkcija još nije implementirana. Prethodna akcija za medije rotira za 90°. Ovaj dokument ne označava F02 kao završen.

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

## Nalazi u postojećem kodu

- `MediaElement` čuva ugao 0–359 i ima `rotatedBounds`; renderer rotira oko centra.
- Ostali objekti nemaju transformaciju. `Element.bounds` i `renderBounds` imaju različite namene; selekcioni kod još koristi bounds na više mesta.
- Trenutni resize nije ograničen na isti faktor po obe ose. Samo dodavanje angle polja ne predstavlja tačno kombinaciju rotacije i neuniformnog skaliranja: rezultat može sadržati smicanje.
- `leaves()`, Fill, stroke eraser, stilovi i uređivanje teksta prepoznaju konkretne tipove. Novi geometrijski sloj mora dosledno da prođe kroz sve ove tokove.
- JSON je verzija 3, Room šema 4. Promena sadržaja JSON-a nije automatski promena SQL šeme; migraciju uvoditi prema stvarnoj promeni skladišta.

## Arhitektonska odluka koju implementacija mora potvrditi

Koristiti jedan čist Kotlin geometrijski sloj sa afinnim transformacijama, inverznim mapiranjem za hit-test i jasnim granicama lokalnih i koordinata dokumenta. Geometrija prikaza i izvoza mora dolaziti iz istih podataka. Android Canvas matrica je adapter, ne domenski model.

Pre izbora konačnog modela dokazati testovima kombinaciju rotate → nonuniform resize → fill/edit → undo → save/reopen. Razmotreni su zajednička transformacija elementa i normalizacija pojedinačnih tipova; ne uvoditi wrapper koji postojeća gumica ili stilovi tiho preskaču. Za grupe sačuvati postojeće pravilo ravne strukture i semantiku ID-jeva.

## Obavezne provere

Domenski testovi: uglovi 0/45/90/180/270/360 i negativni, zajednički pivot, grupe, granične koordinate, inverse round-trip, stabilan Undo/Redo, promašaj u praznom AABB uglu, Fill i gumica. Format testovi: round-trip, legacy verzije/mediji i odbijanje oštećenih transformacija. Android: stvarni renderer/izvoz piksela, dodir/selektor/Fill, uređivanje teksta, čuvanje i recovery, modalni fokus i pristupačnost, phone/tablet portrait/landscape i minified paket.

Ovo je ulaz u implementaciju, bez tvrdnje da su navedene provere već izvršene.
