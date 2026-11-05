# Lokalni kandidat 0.7.1 / code 15

Kvalifikovan emulator obim, 5. novembar 2026: izvor `d451e65a0bfaf7a857dd81a738eba12355929ef1`, grana codex/json-resource-budget, [CI 42085746810](https://github.com/cmarko-gits/Whiteboard-/actions/runs/42085746810). Svih šest poslova prolazi u prvom pokušaju. **13/47 glavnih zadataka završeno, 34 otvorena.** Z08 ostaje otvoren zbog velikog grafa i narednih konkretnih nalaza; ova verzija zatvara određeni [JSON allocation-budget nalaz](JSON-RESOURCE-BUDGET.md).

Pre platformskog JSONObject parsiranja ograničava se procenjena strukturna cena ulaza. Politika je najviše 80 MiB i pola maksimalnog heap-a, snimljena pri konstrukciji codec-a. Board/page/element/delta reader i writer koriste isti budžet; izlaz se proverava posle serijalizacije, pre vraćanja/upisa. Prekoračenje daje DOCUMENT_LIMIT i lokalizovanu poruku, bez automatskog retry loop-a. Format ostaje 5, Room 4; nema novih runtime biblioteka, dozvola ili online funkcija.

388 JVM (268 core + 66 format + 5 data + 49 app), pet profila po 122 app + 13 data i tri R8 connection/reopen/jezička toka prolaze bez neuspeha, grešaka ili preskakanja: **523 jedinstvena slučaja**. Profili su API 26 telefon/tablet i API 30/33/36. Novi slučajevi uključuju million-object ulaz u 64 MiB child JVM procesu, granice cene, encode/read paritet, dirty ViewModel stanje, Room atomarnost odbijenog save/draft-a, odsustvo phantom zapisa i produkcioni save/load grafa sa 10.000 čvorova, 9.999 veza i punim 36-character ID-jevima. Child proba je JVM dokaz, dok Room slučajevi imaju stvarno Android izvršavanje na svih pet emulator profila.

Stil/arhitektura, debug/release lint, OSV i licence/notices za 155 runtime paketa, source scan 394 fajla, manifest/DEX/offline-font politika i VCS provera prolaze. History scan obuhvata 106 od 110 dostupnih revizija bez prijavljenih tajni; razlika u scanner/Git brojanju je sačuvana. Ovo nije nezavisan pentest. Prvobitni source 53df3e2 nije distribuiran: lokalni UUID test otkrio je pretesnu prvu politiku, uprkos zelenom CI-ju. Taj neuspeh i njegovi dokazi ostaju sačuvani.

Originalni GitHub archive digest-i provereni su pre izdvajanja ograničenih izveštaja. Unsigned APK SHA-256: `F529C026BD741FA1AAB583F82B1B41A944BFCF17C9F5104B2F548A82C37F98DE`. Lokalni razvojni v2/v3 potpis, ZIP-P16, četiri ELF64 LOAD/RELRO i osam biblioteka za četiri ABI-ja prolaze. Svih 381 ZIP sadržaja i v1 MANIFEST.MF odgovara sva tri CI release APK-a; razlikuju se samo četiri imenovana v1 signer unosa i očekivani v2/v3 signing blokovi.

| Paket | Bajtovi | SHA-256 |
|---|---:|---|
| `artifacts/whiteboard-local-0.7.1-preview.apk` | 2.500.739 | `5E98D82D301DA754125505B443DBD7A7ADF9EA4997E87B11CE9AC4BFE896FD44` |
| `artifacts/whiteboard-local-0.7.1-unsigned.aab` | 5.268.206 | `A222727F69A64541F972BCFEB7272BC34B2DC7A4B699D5775FB92F0EC51435E9` |

Dokazi: `artifacts/task-08-d451e65-{local,evidence,packages,release-content-match}.json` i `artifacts/security/task-08-d451e65-history-summary.json`. Razvojna preview optimizacija 0.7.2 ima zaseban izvor i nije sadržana u ovom APK-u. Ukupni process/encoder/bitmap/buffer peak, fizički low-RAM, govorni TalkBack, kompletna matrica tema, in-place OS nadogradnja i hardver ostaju otvoreni. Potpis je razvojni, AAB unsigned; paket nije instaliran na fizički telefon niti objavljen.
