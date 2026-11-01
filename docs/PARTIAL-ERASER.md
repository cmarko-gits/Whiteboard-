# F01 — parcijalna gumica, razvoj 0.5.0

## Kriterijumi prihvatanja

- Podrazumevani režim ostaje brisanje celog poteza. Korisnik bira Whole stroke ili Partial stroke u podešavanjima; izbor se čuva lokalno.
- Parcijalni režim uklanja deo poteza duž cele putanje gumice, uključujući razmak između retko pristiglih touch događaja i završni ACTION_UP.
- Preostali fragmenti zadržavaju alat, boju, širinu, providnost i redosled slojeva. Ostali objekti ostaju isti; grupe zadržavaju identitet dok imaju najmanje dva člana.
- Jedan završen gest daje jednu Undo komandu. Redo vraća iste fragmente i identifikatore. Otkazan gest, promena dokumenta/alata/režima i početak pinch navigacije ne menjaju dokument.
- Sačuvana tabla i PNG izvoz prikazuju isti rez. Nije potrebna promena Room šeme ili JSON formata jer fragmenti ostaju obični potezi.
- Prevelika izmena se odbija bez delimičnog upisa: najviše 4096 fragmenata po potezu, postojeći limit od 100000 objekata po stranici i postojeći memorijski budžet Undo istorije.

## Implementacija

`PartialStrokeEraser` u čistom Kotlin core modulu seče poliliniju kapsulom između dve tačke gumice. Proračun koristi Double, uključuje poluširinu poteza i brzo odbacuje udaljene granice. Promašen potez zadržava originalni objekat. `DocumentSession.eraseParts` proverava originalne snimke pre atomske komande i čuva Undo/Redo.

Native canvas čuva samo privremeni pregled tokom gesta. ViewModel prima završene rezove, a podešavanje prolazi kroz postojeći repository/use-case sloj. Novi UI redovi imaju radio semantiku i najmanje 48 dp dodirnu površinu. Engleski i srpski latinični tekstovi su dodati.

## Dokazi i trenutno stanje

21 novi JVM test prolazi: geometrija, kapsula, retke/ponovljene tačke, veliki koordinatni opseg, deterministički nasumični slučajevi, grupe i redosled, stari snimci, Undo/Redo i granice memorije/broja objekata. Sa postojećim testovima: 148 JVM provera. Logovi: `artifacts/task-11-domain-tests-repeat.log` i `artifacts/task-11-android-build.log`.

Android build, test APK i debug lint prolaze. Devet testova MotionEvent unosa i jedan ceo UI/čuvanje/PNG tok su napisani; izvršavanje na Android matrici još nije potvrđeno. F01 ostaje otvoren dok se ti rezultati ne pregledaju. Broj prolaznih JVM testova nije dokaz izvršenih Android testova.

Ova funkcija ne dodaje mrežu, dozvole, uvoz novih tipova datoteka ili izvršavanje korisničkog koda. Granice opterećenja i atomicitet imaju regresione testove; nezavisan penetracioni test nije izvršen.
