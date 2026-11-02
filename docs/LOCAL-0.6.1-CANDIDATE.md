# Lokalni kandidat 0.6.1 / code 9

Proveren 2. novembra 2026. Izvor `a5fdeb741a918baa764d3efc37558d414411160d`, grana `codex/final-gesture-input`, [CI 41504499100](https://github.com/cmarko-gits/Whiteboard-/actions/runs/41504499100). Svih šest poslova je završeno uspešno. Ovo je razvojni lokalni paket, bez objave na Play-u.

## Funkcije i regresija

Uključuje raniju parcijalnu gumicu, proizvoljnu relativnu rotaciju objekata/grupa i ispravku završnog uzorka poteza/oblika. Rotacija koristi zajednički centar selekcije, format dokumenta 4, postojeću Room šemu 4 i jednu Undo/Redo komandu. Render, izbor, Fill, copy/group, čuvanje i izvoz razumeju afinni položaj. Finalni ACTION_UP/aktivni stylus POINTER_UP se čuva; cancel i nevalidne koordinate ne prave delimičan upis.

Pregledani su XML/HTML izveštaji i SHA-256 ZIP digest svakog CI artefakta:

| Provera | Testovi | Neuspesi / greške / preskakanja |
|---|---:|---:|
| JVM core / format / data / ViewModel | 128 + 43 + 5 + 20 = 196 | 0 |
| API 26 telefon | 84 app + 13 data | 0 |
| API 26 tablet, stvarni veliki AVD profil | 84 app + 13 data | 0 |
| API 30 telefon | 84 app + 13 data | 0 |
| API 33 telefon | 84 app + 13 data | 0 |
| API 36 telefon, eksplicitni GLES renderer | 84 app + 13 data | 0 |

Ukupno 293 jedinstvena JVM/app/data slučaja; ponavljanja na OS matrici nisu novi jedinstveni testovi. Posebni stari TalkBack/memory/frame-time kvalifikacioni testovi nisu ponovljeni u ovoj kohorti.

Minified release smoke prolazi na API 26 telefonu i tabletu, kao i API 36 telefonu: tastatura/boje, relativna rotacija 45°, Undo/Redo, recovery i Save/reopen. API 36 proverava i promene širine/orijentacije. API 26 runtime DPI override je izričito izostavljen zbog nepouzdanog sistemskog touch injection-a; tablet je zaseban AVD profil.

Raniji neuspeh 490b764/API 36 ostaje dokumentovan: 83/84 zbog tačnog poređenja subpixel vrednosti koje nativni događaj predstavlja malo drugačije. Novi test zahteva da aplikacija sačuva potpuno iste event.x/event.y koje je primila, uz zasebnu proveru nameravanog ulaza do 0.001 piksela. Produkcijska obrada gesta tim test popravkom nije menjana. Raniji Quickstep/SystemUI startup ANR pokušaji takođe ostaju u evidenciji.

## Bezbednost i pakovanje

CI prolazi stil/arhitekturu, JVM, debug/release lint, R8, OSV proveru 147 runtime Maven paketa bez prijavljenih poznatih nalaza, licence/notices, source secret scan i manifest/DEX politiku. Nema INTERNET dozvole, cleartext saobraćaja, aktivnih online klijenata ili debug analizatora curenja u release-u. To nije nezavisan pentest ili garancija odsustva svih propusta.

Unsigned APK odgovara hash-u CI security izveštaja. Lokalni preview prolazi v2/v3 potpis istim Android Debug sertifikatom kao ranije, ZIP `-P 16` poravnanje i LOAD/RELRO proveru četiri ELF64 biblioteke. Paket sadrži arm64-v8a, armeabi-v7a, x86 i x86_64 biblioteke. 32-bitne biblioteke nisu predstavljene kao ELF64 provera.

| Paket | Bajtovi | SHA-256 |
|---|---:|---|
| `artifacts/whiteboard-local-0.6.1-preview.apk` | 1.952.702 | `2102459798DCF9E4119B3732F8DD5B8F51894C20F836C7ADADF848C79DB718B2` |
| `artifacts/whiteboard-local-0.6.1-unsigned.aab` | 4.449.780 | `9BABF297DD82E50B19CF3C24CA093FB209318F8F17E436C99DC1AE32BA2336CC` |

Dokazi: `artifacts/task-08-a5fdeb7-evidence.json`, `artifacts/task-08-a5fdeb7-packages.json`, odgovarajući ZIP-ovi, potpis i security JSON-ovi. Izveštaji su generisani lokalno iz CI artefakata i nisu korisnički podaci. APK nije instaliran na fizički telefon. Potpis je razvojni; AAB je unsigned.

## Preostale granice

Minimum API 26 i cilj API 36 su deklarisani; nije testiran svaki OEM uređaj. Nisu potvrđeni slušni TalkBack pregled svih tokova, fizička olovka/palm rejection, fizički release frame-time/memorija/baterija ili stvarna in-place OS nadogradnja. Te stavke ostaju u Z/H planu. Backend/cloud/AI i Play objava ostaju van aktivnog obima.

0.6.2 dodaje slojeve na zasebnoj grani i zahteva sopstvenu kvalifikaciju. Dalji radni kod 0.6.3 nije ovaj paket. Istorijski paket 0.6.1 RC1 iz drugog izvora nije zamenjen niti naknadno proglašen prolaznim.
