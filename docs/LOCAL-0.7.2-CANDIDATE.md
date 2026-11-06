# Lokalni kandidat 0.7.2 / code 16

Kvalifikovan emulator/paket obim, pregled 6. novembra 2026: izvor `1176e599e54d6d1c6e8ab7be1b59c684f3520b09`, [CI 42095770590](https://github.com/cmarko-gits/Whiteboard-/actions/runs/42095770590), svih šest poslova prolazi u prvom pokušaju tog izvora. **13/47 glavnih zadataka završeno, 34 otvorena.** F06 domenska osnova je zaseban kasniji izvor i nije u ovom APK-u. Z08 ostaje otvoren zbog preostalih cena velikih scena i drugih konkretnih nalaza.

Warm preview povezane stranice deli proverene identitete, nepromenjene portove/linije i render okvire. Preračunavaju se promenjeni ciljevi i incidentne veze; jeftina provera redosleda/definicija i tačan union ostaju linearni. Union koristi četiri primitive i jedan završni Bounds. Nepovezane stranice ne alociraju novi frame cache. Cold/strukturna scena i preview prethodnog preview-a koriste kanonski postupak; nema neograničenog lanca starih snapshot-a. Strani lokalni Element zadržava sopstvenu geometriju, zastarela Connector definicija se odbija, a otkazivanje ne objavljuje delimičan preview.

399 JVM (279 core + 66 format + 5 data + 49 app), pet profila po 123 app + 13 data i tri R8 connection/reopen/jezička toka prolaze bez neuspeha, grešaka ili preskakanja: **535 jedinstvenih slučajeva**, OS ponavljanja nisu novi testovi. Profili su API 26 telefon/tablet i API 30/33/36. Jedanaest novih core slučajeva proverava deljenje nepromenjene geometrije, tačan union koji se smanjuje, group/child/affine portove, stale definicije, popravku hladnog nevalidnog izvora, otkazivanje, konkurentne čitaoce i 2.000 uzastopnih preview-a. Native renderer fixture izričito priprema warm snapshot; render/input/cancel/AX/export i saved-reopen regresije prolaze na novom izvoru. Format/Room ostaju 5/4.

## Algoritamski profil

Isti deterministički grafovi i broj ponavljanja poređeni su sa kvalifikovanim 0.7.1/source d451e65/CI 42085746810. Za 10.000 čvorova i 9.999 veza:

| Debug emulator profil | Pre: median preview, ms | Posle: median preview, ms |
|---|---:|---:|
| API 26 telefon | 78,80 | 2,74 |
| API 26 tablet | 82,50 | 3,06 |
| API 30 telefon | 214,29 | 5,27 |
| API 33 telefon | 62,79 | 5,94 |
| API 36 telefon | 108,45 | 5,16 |

Sirovi podaci i svih 90 poređenja operacija/veličina/profila sa proverenim entry SHA-256 su u `artifacts/task-08-1176e59-algorithm-comparison.json`. To su debug worker algoritmi, bez Canvas/GPU/input-to-display ili fizičkog release dokaza. JIT i različiti deljeni hostovi utiču na rezultate. Neke druge operacije su sporije na API 33, a najveći graf i dalje ima oko 240–478 ms za move/Undo/Redo/Undo sekvencu, 237–475 ms za copy i 458–2.556 ms za codec roundtrip. Nije proglašeno ubrzanje svih putanja ili 60 Hz.

Allocation schema 2 čuva sirove pre/posle brojače, potpisanu razliku i GC status. Summaries uključuju samo observed uzorke; bez njih ostaju null. Na najvećem grafu neke velike operacije nemaju nijedan pouzdan allocation uzorak zbog GC overlap-a; to je sačuvano, ne pretvoreno u nulu. Svaka geometrijska provera i svaki vremenski uzorak ostaju aktivni. Ovo nije retained cache-memory dokaz. [Ugovor i neuspešan prvi pokušaj](CONNECTION-PREVIEW-OPTIMIZATION.md) objašnjavaju granice.

## Paket i bezbednosne provere

Style/architecture, debug/release lint, OSV/licence/notices 155, source scan 397, manifest/DEX/offline-font i VCS provera prolaze za ovaj izvor. Lokalni Git history scan obuhvata 111 od 117 dostupnih revizija bez prijavljenih tajni: svi lokalni refs, uključujući kandidat, kasniju F06 osnovu i zadržan stash; tadašnji uncommitted affine F06 rad nije deo skena. Obim nije nezavisan pentest.

Unsigned APK SHA-256 je `984848CF8B9587C70BCBDF78C42941A70C88E4CF550E5C23845E0669305BC0A5`. Originalni GitHub archive digest-i i izdvojeni entry hash-evi provereni su pre upotrebe. Lokalni v2/v3 razvojni potpis, ZIP-P16, četiri ELF64 LOAD/RELRO provere i osam biblioteka za četiri ABI-ja prolaze. Svih 381 ZIP sadržaja i v1 MANIFEST.MF odgovara sva tri CI release APK-a; samo četiri imenovana v1 signer unosa i očekivani v2/v3 signing blokovi se razlikuju.

| Paket | Bajtovi | SHA-256 |
|---|---:|---|
| `artifacts/whiteboard-local-0.7.2-preview.apk` | 2.517.123 | `11EBA4CC3B404A2BA336F388B27115E18D3D4857C0D1C1259A63ECA5B259F1A1` |
| `artifacts/whiteboard-local-0.7.2-unsigned.aab` | 5.275.723 | `C6DE0EBA16DC0B26F22D216E660FDA14C5322BEA9330A3FFC2B0F48490D616A7` |

Vizuelno su pregledani novi API 30 original/moved renderer par i API 36 minified saved/reopened connections snimak, oba sa potvrđenim entry hash-evima. Dijagnostička schema/guard proverava raw vrednosti, status i valid-only median/max; ne sakriva negativne ili nedostupne uzorke.

Dokazi: `artifacts/task-08-1176e59-{local,evidence,packages,release-content-match,algorithm-comparison,visual-review}.json` i `artifacts/security/task-08-1176e59-history-summary.json`. Prvi source c7bd2b0/CI 42091269841 nije distribuiran: API 26 je otkrio pogrešnu monotonost brojača, a API 36 startup problem prethodio je aplikacionim testovima. Svi ti logovi/izveštaji ostaju sačuvani. Novi izvor je prošao celu matricu; test nije isključen radi prolaza.

Razvojni potpis i unsigned AAB nisu vlasnički Play paket. Nema novih runtime biblioteka, mrežnih dozvola ili online funkcija. Fizički uređaji, spoken TalkBack, puna matrica tema, OS in-place upgrade i ukupni process/encoder/bitmap/retained-memory profil ostaju otvoreni. Nema fizičke instalacije ili objave.
