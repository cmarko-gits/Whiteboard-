# Whiteboard 0.8.0 — lokalni kvalifikovani kandidat

Presek 11. novembra 2026. Aplikacioni izvor **36cd15a6415d66eff7be10c97802ddc61f522f59**, grana `codex/release-candidate`, [CI 43008205011](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43008205011): svih šest poslova prolazi u prvom pokušaju, bez retry-ja. Kasniji dokumentacioni commit ne menja ovaj kvalifikovani aplikacioni izvor. Verzija 0.8.0/code 17, min API 26/target 36, document 6/delta 2/Room 4.

## Ponašanje i opseg

Offline Kotlin Android kandidat uključuje crtanje/bojenje pozadine i objekata, postojeće oblike/tekst/beleške/veze, stranice, biblioteku, recovery i import/export. F06 dodaje stvarni semantički Mind map šablon, pretraživu hijerarhiju, kreiranje i uređivanje korena/child/sibling čvorova i njihove boje, promenu roditelja, pomeranje cele grane, collapse/reveal, izbor vidljive mape, privatni branch clipboard i potvrde Cut/Delete. Komande ostaju atomski Undo/Redo koraci; save/reopen i format koriste kanonsku topologiju. [Uputstvo](LOCAL-USER-GUIDE.md) i [arhitektura editora](MIND-MAP-EDITOR.md) opisuju granice.

F07 ostaje na zasebnoj nespojenoj grani. AI, backend, cloud, nalozi i javna Play objava nisu uključeni. Ovo je kvalifikacija softvera u navedenom emulatorskom opsegu, ne potvrda svakog Android uređaja ili završetka svih budućih funkcija.

## Izvršene provere

| Provera | Rezultat |
|---|---|
| JVM | 519: core 358, format 77, data 5, app 79; bez grešaka/preskakanja |
| Android regresija | Na svakom od pet profila po 140 app + 13 data, bez grešaka/preskakanja |
| Profili | API 26 telefon, API 26 tablet, API 30, API 33, API 36 |
| Optimizovani release | Tri R8 toka: API 26 telefon/tablet i API 36; mape uma, unos/boje, rotacija, Undo/Redo, recovery i save/reopen |
| Jezici | Oba process-restart jezička slučaja i vraćanje podešavanja na svakom od tri R8 profila |
| Nadogradnja | Stvarni prethodni 0.7.2 APK → kandidat na API 36 sa `install -r`; tabla i okvir poteza očuvani |
| Prozor i font | API 36: tri stvarna split-screen slučaja i četiri adaptive slučaja, 320 dp/font 150%/200%/1600 dp |
| Dijagnostika | Dva odvojena API 36 memory/frame slučaja, source-bound debug APK sa LeakCanary/Shark/tracing |
| Build | Kotlin stil, granice arhitekture, debug/release lint, kompilacija i R8/bundle gates prolaze |
| Bezbednost | OSV/licence/notices 155 komponenti; source scan 453 fajla i history scan 149 skeniranih/157 dostupnih revizija bez prijavljenih tajni |
| Paket | VCS/SDK/verzija, manifest/DEX/offline politika, osam native biblioteka/četiri ABI-ja, četiri ELF64 LOAD/RELRO i ZIP-P16 |
| AAB | Native uncompressed/PAGE_ALIGNMENT_16K, zvanični bundletool 1.18.3 validate pre i posle potpisa |

Regularni inventar je 672 različita slučaja (519 + 140 + 13), odnosno 674 sa dve zasebne dijagnostike. Ponavljanja po OS-u nisu dodatni jedinstveni slučajevi. Opt-in `releaseClient` ima jedan pomoćni JUnit metod koji se pokreće po UI komandi; nije deo regularnih 140 niti dodatna proizvodna funkcija. Klijent koristi zaseban testni APK/standardni runner i stvarne accessibility klikove, a release politika odbija njegov DEX paket i instrumentation unose u produkcionom APK-u.

Sadržaj 381 payload/MANIFEST stavke lokalnog preview-a jednak je svim trima originalnim R8 CI APK-ovima: nula payload razlika, samo imenovane razlike potpisnih stavki. Nadogradnja je vezana za isti poređeni API 36 APK. Potpisivanje AAB-a čuva svih 390 originalnih ZIP stavki; DEX i osam native biblioteka jednaki su kvalifikovanom preview-u. Strict jarsigner verifikacija prolazi uz tačan javni upload sertifikat.

## Lokalni paketi

Paketi su u ignorisanom `artifacts/` direktorijumu radnog prostora; nisu objavljeni na Play-u niti automatski dostupni u Git checkout-u.

| Fajl | Bajtova | SHA-256 |
|---|---:|---|
| `whiteboard-local-0.8.0-preview.apk` | 2566275 | CF5F6D5E58E7EB5D09887EE0CA09B296A84F6FE8326ED91E70994906D32C61D6 |
| `whiteboard-local-0.8.0-unsigned.aab` | 5407071 | 91CCD7C558578893F15B262879235E84E868E0AA33A5F734ADFC537969FAA8E4 |
| `whiteboard-0.8.0-play-upload.aab` | 5442726 | 77054F7DC2B0AC14CA8D75631D1445DE7ECE2AC59CC43447A317505D79FDAD97 |

Preview je R8 release APK sa razvojnim Android Debug v2/v3 potpisom i kontinuitetom prethodnog lokalnog 0.7.2 preview-a. Vlasnički potpisani AAB koristi zaseban upload sertifikat SHA-256 **67158377779CBECE20CBED2E46F0D44EB337128E547F2B980FE648D720A3A522**. Upload identitet nije Google Play App Signing identitet; AAB se ne instalira neposredno kao APK. Privatni ključ i lozinka ostaju van Git-a i CI-ja, pod Windows ACL/DPAPI zaštitom.

## Merenja i preostale granice

Tri force-stop library starta sa toplim OS cache-om daju medijane 908/1104/671 ms za API 26 telefon/tablet i API 36; PSS snapshot-i su 37071/36803/38911 KB. Nisu merenje peak memorije, cold boot-a ili fizičke input latencije.

Debug dijagnostika izvršava šest stvarnih media/editor/recreate/close ciklusa, prati 12 editor Views, prolazi LeakCanary i nalazi 0 preostalih WhiteboardView instanci. Finalni heap ima 51346978 bajtova/479501 objekat; fixture ga briše. PSS ciklusa je 215017/206739/205852/204796/207780/202100 KB, post-GC 254497 KB. Workload 1000 poteza/100000 tačaka daje 89 frejmova, p50/p95/p99 299.733/316.896/584.089 ms. To je visoka latencija softverskog GPU emulatora pod debug/tracing-om i nije prolaz fizičkog cilja od 60 Hz. [PERFORMANCE.md](PERFORMANCE.md) čuva kontekst i hardverski postupak.

Četiri odabrana screenshot-a konačnog izvora pregledana su: tablet R8 panel, sačuvana native mapa, API 36 R8 formular i proširena mapa. Kontrole/tekst su dostupni u tim scenama; pregled ne predstavlja kompletan Light/Dark/System/kontrast ili govorni TalkBack prolaz.

Glavni plan: **6/9 završeno, 3 otvoreno** — ručni govorni TalkBack i kompletan kontrast (5), fizičko release profilisanje/OEM/stylus i širi runtime (6), nezavisna prenosiva rezervna kopija upload ključa (9). AAB i potpisni deo celine 9 su završeni. Korisnik je izabrao nastavak bez fizičkog telefona; bezbedno nezavisno odredište ključa još nije određeno. Širi backlog: **14/47 završeno, 33 otvoreno**, uključujući buduće opcije koje nisu uslov ove verzije. Nisu izvršeni nezavisan pentest, univerzalna hardverska kvalifikacija, 16 KB runtime svih uređaja, Play nalog/listing/predaja ili objava. [Ručni završetak](RELEASE-HANDOFF.md).

## Dokazi

- `artifacts/release-rc-36cd15a-evidence.json`: svih šest digest-proverenih originalnih CI arhiva i inventar.
- `artifacts/release-rc-36cd15a-packages.json`: strict package readiness, SHA-256 51CD2E6729B8770BD0E1369751E575F822B2815E7ADECE3B08B4A38FE3122D77.
- `artifacts/release-rc-36cd15a-release-content-match.json`: tri R8 poređenja, SHA-256 CB05463695018998D7ECFCF42E06D6C7247FBB080BB1775AEFB67FCFE9A48C37.
- `artifacts/whiteboard-0.8.0-owner-signing.json`, `whiteboard-0.8.0-owner-bundle-validation.json` i `whiteboard-0.8.0-bundle-verification.log`: owner sertifikat, nepromenjen sadržaj i zvanična validacija.
- `artifacts/release-rc-36cd15a-visual-review.json`: ograničen vizuelni pregled sa provenance/hash-evima.
- [Bezbednosni pregled](RELEASE-SECURITY-REVIEW.md), [plan sa prethodnim neuspesima](RELEASE-READINESS.md) i [izvršavanje](EXECUTION-PROGRESS.md) čuvaju nalaze i granice. Prethodni neuspešni pokušaji nisu prepisani ili distribuirani.
