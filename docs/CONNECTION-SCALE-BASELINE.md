# Veze: izmerena cena velikog grafa

Ovo je istorijski početni baseline. Novi kvalifikovani [0.7.2 profil](LOCAL-0.7.2-CANDIDATE.md) poredi svih pet profila sa svežim 0.7.1 izvorom; preview optimizacija je implementirana. Opisi punog preview rebuild-a ispod važe za početni izvor, a cene drugih operacija ostaju predmet rada.

Početni izvor `5e0c5ba35f470238df87e2e3ce3f4ae1e38e7a48`, CI `41995668364`, 5. novembar 2026. ConnectionScaleTest prolazi na svih pet profila, ali cela matrica ima zasebne UI neuspehe i nije kvalifikovan kandidat. Merodavni originalni archive digest-i i pojedinačni entry SHA-256 su u `artifacts/task-15-5e0c5ba-evidence.json`; objedinjeni neizmenjeni podaci su u `artifacts/task-15-5e0c5ba-algorithms.json`.

Ovo su debug algoritmi na instrumentation worker-u emulatora. Ne meri se Compose, Canvas iscrtavanje, GPU, raspored frejmova, input-to-display, baterija ili fizički uređaj. Razlike Android runtime-a, JIT-a i deljenih CI hostova utiču na rezultate; tabela ne predstavlja rangiranje telefona.

Svaki graf ima 100, 1.000 ili 10.000 pravougaonih čvorova i vezu između svakog para suseda. Najveći ima 19.999 ukupnih objekata. Cold resolve ima jedan uzorak; metadata100, preview i move/Undo/Redo imaju po pet; kopiranje i codec po tri. Izveštaji čuvaju sve sirove milisekunde, median i maksimum. Sa tako malim uzorcima ne izračunava se pouzdan p95/p99.

| Profil | Čvorovi | Veze | Median pripreme preview-a, ms | Median codec roundtrip-a, ms |
|---|---:|---:|---:|---:|
| API 26 telefon | 10.000 | 9.999 | 87,097 | 713,965 |
| API 26 tablet | 10.000 | 9.999 | 82,049 | 693,662 |
| API 30 telefon | 10.000 | 9.999 | 107,441 | 2.550,721 |
| API 33 telefon | 10.000 | 9.999 | 74,594 | 664,009 |
| API 36 telefon | 10.000 | 9.999 | 83,468 | 747,198 |

API 30 primer za 1.000 čvorova ima median preview 14,433 ms. Za 10.000 čvorova isti profil ima median 446,847 ms za celu sekvencu move + Undo + Redo + Undo, 369,473 ms za kompletno kopiranje i 0,123 ms za sto metadata-only kopija. Poslednji slučaj proverava i identitet ponovo upotrebljene Line geometrije. Vrednost za četiri history operacije ne predstavlja trajanje jedne operacije.

Svaki merljivi uzorak ujedno proverava tačne world krajeve, nepromenjen original, Undo/Redo, prevezane kopirane ID-jeve ili jednak roundtrip dokumenta. Izveštaj nije zamena za te testove. Room, native preview, gesture cancel, render/export i stvarni korisnički tokovi imaju zasebne testove.

## Posledice za optimizaciju

Metadata-only put deli PageContent/port/Line cache i nema ponovno skeniranje grafa. Promena sadržaja i preview trenutno grade novi immutable snapshot: validaciju, indeks portova i razrešene veze. To ima linearnu cenu prema veličini stranice čak i kada se pomera samo jedan čvor. Tabela pokazuje da najveći graf nema dokazanih 60 Hz; granica nije sakrivena većim timeout-om testa.

Sledeći Z08/H03 korak treba da uporedi potpuno isti scenario sa optimizacijom: indeks incidentnih veza i ograničeno ponovno računanje promenjenih ciljeva; isti ID/order, konture, stale-definition i cancellation ugovori; fallback za strukturne izmene grupa. Kopiranje i potvrda novog snapshot-a zahtevaju poseban pregled main-thread rada, memorijskog budžeta i odbijanja zastarelog plana. Nije dovoljno samo preseliti merenje na drugu nit ili isključiti validaciju.

Pre prihvatanja optimizacije ponoviti correctness, render/AX/preview i OS/tablet matricu, sačuvati sirove pre/posle podatke i navesti da li je meren debug ili optimizovani release. Konačan frame-time i input latency cilj ostaje u H03/H04, uz prethodno odložen fizički hardver. Ova dokumentacija ne zatvara glavne Z08, H03 ili F05 stavke.
