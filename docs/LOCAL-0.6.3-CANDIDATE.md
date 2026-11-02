# Lokalni kandidat 0.6.3 / code 11

Proveren 2. novembra 2026. Izvor `a230f111014cb2f6ab79341eae083ff3149aa218`, grana `codex/selection-alignment`, [CI 41507215597](https://github.com/cmarko-gits/Whiteboard-/actions/runs/41507215597). Posle ponavljanja jednog infrastrukturno neuspešnog posla, svih šest poslova završeno je uspešno. Ovo je razvojni lokalni paket, bez Play objave.

## Ponašanje i arhitektura

Uključuje prethodno proverene [rotaciju, završni uzorak i slojeve](LOCAL-0.6.2-CANDIDATE.md). Selection actions → Alignment sada nudi levu/desnu/gornju/donju ivicu i horizontalni/vertikalni centar. Referenca je zajednički okvir najmanje dva izabrana objekta. Poravnanje je translacija; čuva veličinu, ugao, tekst, crop, stil, ID-jeve, grupe, priloge i z-redosled. Jedna akcija je jedna Undo komanda. Već poravnata/prazna/jednostruka selekcija ne pravi istoriju, dirty stanje ili gubitak Redo-a.

`SelectionAlignment` računa na injektovanom computation dispatcher-u. Zajednički `SelectionEditPlan` i atomska primena koriste se i za rotaciju. Otkazivanje i izmena/promena stranice tokom računanja ne prepisuju noviji dokument. Pre kopiranja geometrije proverava se budžet, a pre upisa sve nove render granice. Dijalog blokira ponovljenu potvrdu dok rezultat nije završen. Format dokumenta ostaje 4, Room 4.

## Pregledani rezultati

| Provera | Testovi | Neuspesi / greške / preskakanja |
|---|---:|---:|
| JVM core / format / data / ViewModel | 157 + 43 + 5 + 25 = 230 | 0 |
| API 26 telefon | 86 app + 13 data | 0 |
| API 26 tablet, zaseban veliki AVD profil | 86 app + 13 data | 0 |
| API 30 telefon | 86 app + 13 data | 0 |
| API 33 telefon, ponovljeni SDK posao | 86 app + 13 data | 0 |
| API 36 telefon, eksplicitni GLES renderer | 86 app + 13 data | 0 |

To je 329 jedinstvenih JVM/app/data slučajeva; OS ponavljanja nisu dodatni jedinstveni testovi. Novi testovi uključuju svih šest poravnanja, grupe/afinne i legacy medije, linije, veliki potez/10.000 objekata, no-op/Redo, budžet/koordinate, otkazivanje i konkurentne izmene. Android tok stvarno koristi svih šest UI akcija, Cancel, Undo/Redo, Save/reopen i PNG.

Minified release smoke prolazi na API 26 telefonu/tabletu i API 36: oba objekta imaju očekivane poravnate granice, Undo/Redo tačno vraća geometriju, zatim prolaze slojevi/rotacija, IME/boje, recovery i Save/reopen. API 36 uključuje adaptivne širine i orijentacije. API 26 runtime DPI override je izričito izostavljen; veliki ekran je zaseban tablet profil.

API 33 prvi pokušaj je imao uspešan build, a zatim je sdkmanager pre pokretanja emulatora prijavio `ZipFile unknown archive` za Android Emulator paket. Nije bilo app testa na tom pokušaju. Log ostaje `artifacts/task-14-a230f11-api33.log`. Zahtev za ponavljanje dok je run još radio odbijen je sa 403; po završetku je prihvaćen jedan zahtev za ponavljanje samo tog posla (201), sa istim source commit-om. Novi API 33 posao 125500605556 prolazi. Attempt metadata i originalni neuspeh ostaju u evidence/rerun JSON-ovima; nisu uklonjeni ili proglašeni prolaznim.

Pregledani snimci izvora a230f11 prikazuju poravnatu selekciju na telefonu i adaptivni tablet landscape editor sa vidljivim kontrolama. To nije pregled svih tema, velikih fontova ili govornog TalkBack-a.

## Bezbednost i poreklo paketa

Stil/arhitektura, debug/release lint, R8, OSV 147 runtime paketa bez prijavljenih poznatih nalaza, licence/notices, source secret scan i manifest/DEX politika prolaze. Lokalni preview ima postojeći Android Debug sertifikat, v2/v3 proveru, ZIP-P16 poravnanje i četiri prolazne ELF64 LOAD/RELRO provere. ABI-ji: arm64-v8a, armeabi-v7a, x86 i x86_64. Potpis je razvojni, AAB unsigned. Ovo nije nezavisan pentest ili garancija odsustva svih propusta.

Svaki preuzeti ZIP odgovara GitHub SHA-256 digest-u; unsigned APK odgovara security policy hash-u. Dodatno su upoređeni sadržaji svih ZIP unosa lokalnog APK-a sa sva tri APK-a korišćena u release smoke-u: DEX, AndroidManifest, resursi, assets, nativne biblioteke, VCS podatak i v1 MANIFEST.MF su identični. Razlikuju se samo četiri imenovana v1 signer unosa ANDROIDD/WHITEBOA .RSA/.SF i v2/v3 signing blokovi, zbog različitih lokalnih/ephemeral CI ključeva. Nije tvrđena jednakost celog potpisanog APK hash-a.

| Paket | Bajtovi | SHA-256 |
|---|---:|---|
| `artifacts/whiteboard-local-0.6.3-preview.apk` | 1.973.182 | `FB882E40C2C97F3765F9D87BD89BDEA1A2D6665D3E6C16F0C5A0831891E4A00A` |
| `artifacts/whiteboard-local-0.6.3-unsigned.aab` | 4.470.009 | `CD8E7DC44C43D63018EFE772F65249F256E02B092D36255B4FB2F6E5B795A9C1` |

Dokazi: `artifacts/task-14-a230f11-evidence.json`, `task-14-a230f11-packages.json`, `task-14-a230f11-release-content-match.json`, potpis/security JSON-ovi i izvorni/rerun logovi. Paket nije instaliran na fizički telefon.

Minimum API 26, target API 36, optional kamera/touchscreen i resizeable Activity omogućavaju deklarisani raspon telefona/tableta/prozora. Nije testiran svaki OEM ili Android uređaj. Fizička olovka/performanse, puna slušna TalkBack provera i prava OS nadogradnja ostaju otvoreni. Backend/cloud/AI i Play objava ostaju van aktivnog obima.

Grana `codex/smart-connectors` sadrži kasnije izolovano jezgro portova. Ono nije deo ovog source-a/paketa i ne znači da je korisnički alat za konektore gotov.
