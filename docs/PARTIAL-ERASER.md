# F01 — parcijalna gumica, proverena lokalna 0.5.1

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

24 nova JVM test prolazi: geometrija, kapsula, retke/ponovljene tačke, veliki koordinatni opseg, deterministički nasumični slučajevi, grupe i redosled, stari snimci, Undo/Redo i granice memorije/broja objekata. Sa postojećim testovima: 151 JVM provera. Logovi: `artifacts/task-11-domain-tests-repeat.log` i `artifacts/task-11-android-build.log`.

Konačni CI [41341043323](https://github.com/cmarko-gits/Whiteboard-/actions/runs/41341043323), source `550bd93`, prolazi u celosti. JVM: core 95 + format 36 + data 5 + ViewModel 15 = 151. Svaka od pet konfiguracija (API 26/30/33/36 pixel_2 i API 26 pixel_c) prolazi 66 app + 13 data testova, bez grešaka ili preskakanja. Uključeni su devet MotionEvent testova i ceo Settings/čuvanje/Undo/Redo/PNG tok. Optimizovani release smoke prolazi na API 26 i 36; tablet profil potvrđuje 1280 × 900 dp. F01 je završen u lokalnom emulator obimu.

Posle nalaza ispravljeni su: fokus i zadržana tastatura pri zatvaranju dijaloga, skriveni rezultati pretrage, raspored kartica u kratkom prozoru i pri 200% fontu. Svi navedeni rezultati odnose se na isti commit, bez objedinjavanja prolaza različitih verzija.

Paket `artifacts/whiteboard-local-0.5.1-preview.apk`, code 7, ima SHA-256 `62C64CCF35F0DE70DB76C2A3508A9BBEF231D2AFE7029EF3B239A56875B8DBDB`. Potpis je razvojni v2/v3 sa postojećim lokalnim sertifikatom. ZIP i četiri ELF biblioteke prolaze proveru 16 KB poravnanja; manifest/DEX politika, licence i source scan prolaze. OSV za 147 runtime Maven zavisnosti nema prijavljenih nalaza. Potpuna evidencija paketa i pregledanih XML-ova: `artifacts/task-11-family-packages.json`; verify log: `artifacts/task-11-family-verify.log`.

Ovo ne potvrđuje fizičke uređaje, govorni izlaz TalkBack-a ili release performanse na hardveru. Razvojna rotacija 0.6.0 nije uključena u ovaj APK. Stariji 0.5.0 paket nije konačni kandidat.

Ova funkcija ne dodaje mrežu, dozvole, uvoz novih tipova datoteka ili izvršavanje korisničkog koda. Granice opterećenja i atomicitet imaju regresione testove; nezavisan penetracioni test nije izvršen.
