# Lokalni kandidat 0.7.0 / code 14

Kvalifikovan F05 emulator obim, 5. novembar 2026: izvor `b5dfebc38980c03be9a273a3da16353d382a4e95`, grana codex/connector-page-integration, [CI 42075106983](https://github.com/cmarko-gits/Whiteboard-/actions/runs/42075106983). Svih šest poslova prolazi u ovom pokušaju. **13/47 glavnih zadataka završeno, 34 otvorena.** Ovo nije završetak cele aplikacije: [JSON resource nalaz](JSON-RESOURCE-BUDGET.md), [cena velikog grafa](CONNECTION-SCALE-BASELINE.md) i ostale Z/F/O/H/P stavke ostaju otvoreni.

Shapes → Connection kreira vezu gestom; More actions → Connect objects nudi pretragu ciljeva, pet portova i free X/Y krajeve. Edit/Detach, stil, atomic delete/cut/group/ungroup i Undo/Redo, dvoprolazni copy/paste/duplicate, preview/cancel, render/AX granice i PNG/JPG/PDF/thumbnail koriste isti PageGeometry snapshot. JSON format je 5, sa čitanjem 1–4; Room ostaje 4. Nema novih runtime zavisnosti, dozvola ili online funkcija. [Ugovor](SMART-CONNECTORS-INTEGRATION.md) i [uputstvo](LOCAL-USER-GUIDE.md) navode ponašanje i granice.

378 JVM (268 core + 57 format + 5 data + 48 app) i pet profila po 119 app + 13 data prolaze bez neuspeha, grešaka ili preskakanja: **510 jedinstvenih slučajeva**, OS ponavljanja nisu novi testovi. API 26 telefon/tablet, API 30/33/36 su obuhvaćeni. Tri minified release profila dokazuju praćenje pomerenog cilja, tačan Undo/Redo i novo pomeranje posle recovery/save/reopen-a. Oba jezička process-restart slučaja po release profilu prolaze uz vraćen izbor.

Stil/arhitektura, debug/release lint, runtime licence/notices 155, OSV bez prijavljenih poznatih nalaza, source scan 388 fajlova i manifest/DEX/offline-font politika prolaze. History scan obrađuje 103 od 107 dostupnih revizija bez prijavljenih tajni; obim i razlika su sačuvani. To nisu testovi svih mogućih napada: zasebna bounded-heap JVM proba već ima otvoren Z08 nalaz, koji nije predstavljen kao Android-device reprodukcija.

Originalni GitHub archive digest-i provereni su pre izdvajanja ograničenih izveštaja. VCS metapodatak odgovara izvoru; unsigned APK SHA-256 je `77892B308EC801F873DE22ED57727F6EDB36AE29F8F6DEDF1177082EC9293BD8`. Lokalni razvojni v2/v3 potpis, ZIP-P16 i četiri ELF64 LOAD/RELRO provere prolaze; uključeni su arm64-v8a, armeabi-v7a, x86 i x86_64. Svih 381 ZIP sadržaja i v1 MANIFEST.MF odgovara sva tri CI APK-a; razlikuju se samo četiri imenovana v1 signer unosa i očekivani v2/v3 signing blokovi.

| Paket | Bajtovi | SHA-256 |
|---|---:|---|
| `artifacts/whiteboard-local-0.7.0-preview.apk` | 2.500.739 | `45690FAE4D89C1D4E61D005FA37789FFF0F5109FA3F82D436B1092D2CB132139` |
| `artifacts/whiteboard-local-0.7.0-unsigned.aab` | 5.268.248 | `717F0B4DB284D15CEE0DEBA9C75CA640793BAF3095919C8F2F5679C01A804031` |

Pregledani su novi tablet dijalog i API 36 minified reopened-connection snimak sa potvrđenim entry hash-evima, uz ranije portrait/landscape i renderer poređenje. Algoritamski profil za 100/1.000/10.000 čvorova prolazi na svih pet profila i ostaje vezan za debug worker, bez tvrdnje o 60 Hz ili fizičkoj latenciji. Fizički uređaji, slušni TalkBack, kompletan pregled tema/boja, in-place OS upgrade i Play objava nisu završeni ovim kandidatom.

Dokazi su `artifacts/task-15-b5dfebc-evidence.json`, `-packages.json`, `-release-content-match.json`, `-visual-review.json` i imenovani security/signature/native izveštaji. Prethodni 5e0c5ba, 5e17897 i c177b71 pokušaji nisu kvalifikovani: fixture enabled/scroll/IME nalazi, stvarna pre-render accessibility ispravka i implicitni drawing tool u release skripti ostaju dokumentovani u EXECUTION-PROGRESS.md. Nijedan test nije isključen radi prolaza. Potpis je razvojni, AAB unsigned, paket nije instaliran na fizički telefon niti objavljen.
