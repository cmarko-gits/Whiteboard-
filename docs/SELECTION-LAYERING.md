# F03 — pomeranje za jedan sloj

Stanje 2. novembra 2026: F03 je kvalifikovan za lokalni emulator obim, 0.6.2/code 10, source `481ab64`, CI 41504676373. Prolazi 209 JVM i pet profila po 85 app + 13 data bez grešaka/preskakanja, uz minified release smoke API 26 telefon/tablet i API 36. Paket, hash-evi, pregled snimaka i ograničenja: LOCAL-0.6.2-CANDIDATE.md. Format dokumenta ostaje 4, Room 4.

## Ponašanje

Bring forward one layer pomera svaki uzastopni blok izabranih objekata preko jednog narednog neizabranog objekta u z-redosledu stranice. Send backward one layer radi obrnuto. Relativni redosled izabranih i neizabranih objekata ostaje isti. Postojeći Bring to front i Send to back ostaju krajnje pozicije.

Primer, gde su B/C izabrani: `A B C D E` → napred → `A D B C E`. Za odvojenu selekciju B/D: `A B C D E` → `A C B E D`. Grupa je jedan objekat na nivou stranice; njena deca se ne preraspoređuju.

Jedna akcija je jedna Undo komanda; Redo vraća isti redosled. Granica, prazna selekcija i nepostojeći ID-jevi ne prave istoriju, ne menjaju saved/dirty stanje i ne brišu postojeći Redo. Geometrija, ID-jevi, transformacije, stilovi i prilozi ostaju isti objekti. Save/reopen i export poštuju novi redosled.

## Arhitektura i zaštite

`LayerOrdering` je čist Kotlin algoritam. Skeniranje u odgovarajućem smeru pomera svaki izabrani blok jednom. Nova lista se alocira tek kada postoji pomeranje; obrada je linearna u broju objekata.

`LayerOrderChange` čuva samo liste ID-jeva pre/posle, a pri primeni koristi trenutne objekte. Proverava limit broja objekata, jedinstvenost, isti skup ID-jeva i očekivani početni redosled. Neispravna ili zastarela komanda ne može odbaciti ili oživeti sadržaj. Viewport i ostala svojstva stranice ostaju očuvani.

Istorija računa `128 + 16 * (before.size + after.size)` procenjenih bajtova. Prekoračenje budžeta odbija operaciju pre promene dokumenta. Oba postojeća krajnja pomeranja koriste istu komandu, pa veliko telo poteza ne troši budžet kao kopirana geometrija. Trenutni dokument i komande brisanja/izmena zadržavaju potrebne priloge; promena redosleda ne uvodi nove vlasnike datoteka.

UI prosleđuje izabrane ID-jeve i smer preko ViewModel-a; postojeći mehanizam obrade greške i busy stanja ostaje zajednički. Stavke menija imaju EN/sr-Latn resurse i semantiku standardnog Material menija. Akcija na granici je onemogućena.

## Pripremljene provere

- 12 domenskih testova: pojedinačni objekti, uzastopne/odvojene selekcije, granice, no-op i Redo/saved stanje, grupe/rotirani mediji, istorijski budžet, asset retention kroz brisanje/Undo/Redo, neispravne/zastarele komande i 10.000 objekata.
- Jedan ViewModel test: selekcija, komanda, save/read, no-op, dirty stanje i Undo/Redo.
- Jedan Android tok: obe akcije kroz meni, onemogućene granice, Save/reopen, Undo/Redo i stvarni PNG sa preklopljenim crvenim/plavim oblicima.
- Minified release smoke na API 26 telefon/tablet i API 36: izbor pravougaonika, promena sloja i provera graničnih stanja kroz Undo/Redo. Postojeće provere rotacije, recovery-ja i adaptivnosti ostaju uključene.

Izvršeni obim ove verzije je 209 JVM, 85 redovnih app i 13 data testova po profilu: 307 jedinstvenih slučajeva. CI matrica API 26/30/33/36 i zasebni API 26 tablet, release smoke i sigurnosne/packaging provere konkretnog izvora prolaze. Dokazi: artifacts/task-13-481ab64-evidence.json i task-13-481ab64-packages.json. Testovi na fizičkom uređaju i slušanje TalkBack-a nisu ovim potvrđeni.
