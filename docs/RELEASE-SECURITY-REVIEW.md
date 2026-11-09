# Bezbednosni pregled kandidata 0.8.0

Presek 9. novembra 2026, pregledani aplikacioni izvor **8a4b8e41f3ba0cba96a2fb42beba2cd1baa74a5b**, code 17. Pregled koda i automatizovane zaštite su završeni u opisanom opsegu. Puna matrica [42669070894](https://github.com/cmarko-gits/Whiteboard-/actions/runs/42669070894) završava neuspešno: verify i četiri telefonska profila prolaze sve svoje tokove, API 26 tablet ima 139/140 app uz 13/13 data. Fokusirano polje pomeraja na tabletu nema stvarnu IME sesiju; novi single-window editor zahteva ponavljanje svih gates za novi source. Ciljani [42667755595](https://github.com/cmarko-gits/Whiteboard-/actions/runs/42667755595) prolazi verify, po sedam native slučajeva na API 26/30/33 i odvojene 2/2 memorijske/frame dijagnostike na API 36. To nije nezavisan pentest ili potvrda konačne spremnosti za Play.

## Proverene granice

| Granica | Ponašanje i dokaz |
|---|---|
| Dokument iz nepouzdanog fajla | Document 6 / delta 2 proveravaju tip i integer verziju, identitete, reference i semantiku; nepoznat format i nova semantika pod starim header-om se odbijaju. JSON preflight ograničava dubinu, veličinu i numeričke literale. |
| Mape i velike scene | Iterativno validiranje/obilazak i eksplicitni budžeti sprečavaju rekurziju po korisničkoj dubini. Topologija zahteva validan koren, roditelje, veze i odsustvo ciklusa; važe limiti stranice/objekata/istorije i najviše 50000 čvorova po mapi. To nije obećanje interaktivnih performansi na maksimalnom dokumentu. |
| Odložene komande | Worker pripada vlasniku, sesiji, sceni, generation i request ID-ju. Otkazani ili zastareli rezultat ne menja novu tablu. Pending stanje sprečava višestruki Apply; domen ponovo validira atomski plan. |
| Unos i prozori | Novi naslov je do 20000 UTF-16 jedinica, bez praznog unosa i tihog skraćivanja postojećeg predugačkog teksta; pomeraji moraju biti konačni i unutar granica. IME cleanup pripada prozoru: owner close je jednokratan, listener/callback se uklanjaju kroz View lifecycle, a insets listener se ne uklanja unutar platformskog obilaska. Novo izričito korisničko fokusiranje prekida odloženi cleanup. |
| Skriveni sadržaj | Collapse čuva kanonski dokument, recovery i editable backup; raster/PDF prikazuje vidljivu projekciju. Branch Cut/Delete prikazuje stvarni broj zahvaćenih čvorova/objekata i pravi jednu budžetiranu Undo promenu. |
| Upis i uvoz | Save/recovery su serijalizovani i vezani za reviziju; kasnija izmena ostaje dirty. Room migracije su nedestruktivne. Stvarni SQLite-full, cancellation i abort drugog uvoza proveravaju očuvanje saved/draft i rollback dokumenta/chunk-ova/indeksa. Izvršeni 13-data/18-app storage dokazi i nova regresija imaju source u RELEASE-READINESS.md. |
| URI i prilozi | Aktivni prenos prihvata korisnički odabran content URI. Privatni asset ID-jevi, ZIP limiti/putanje/referencirani prilozi, ograničeni dekoderi i privremeni read-only FileProvider grant ostaju aktivni. Nema mrežnog preuzimanja proizvoljnog URL-a ili novog izvršivog formata. |
| Dozvole i privatnost | Konačna merged manifest/DEX politika potvrđuje lokalni release bez INTERNET/storage/camera dozvole, bez debug SDK-a, sa isključenim Android backup/transfer i cleartext-om. Nema naloga, backend-a, cloud-a ili AI-a. Podaci su u app sandbox-u; zasebna per-board enkripcija/PIN nisu uvedeni. |
| CI i dijagnostika | Zakucani action commit-i i read-only repository permissions. Actions-read preuzima samo pinned 0.7.2 upgrade baseline sa tačnim archive/APK hash-om; nema latest fallback-a. ANR/fatal zapisi nisu obrisani ili zatvoreni radi prolaza. Dijagnostički debug APK ima zasebne hash-eve i LeakCanary/Shark; release ih nema. |
| Ključ za upload | Van Git-a, privatni direktorijum pod Windows korisnik/SYSTEM ACL i DPAPI zaštitom. JDK dobija kratkotrajnu environment vrednost; privatni ključ/lozinka nisu u komandnoj liniji, CI-ju ili izveštajima. Javni sertifikat nije tajna. Nezavisna prenosiva kopija još zahteva bezbedno odredište izvan C: diska. |

## Automatizovani rezultati za ovaj izvor

- 519 JVM, stil/arhitektura, debug/release lint i build prolaze.
- OSV proverava 155 resolved release runtime Maven komponenti bez prijavljenih poznatih nalaza; licence/notices svih 155 prolaze. Inventar ne obuhvata OS/JDK/build-tool ili neobjavljene ranjivosti.
- 451 neignorisan source fajl prolazi Gitleaks 8.30.1. Odvojeni lokalni pregled svih refs prijavljuje 144 skenirane revizije među 152 dostupne, bez prijavljenih tajni. Izveštaj: artifacts/security/release-8a4b8e4-history-summary.json.
- Unsigned APK SHA-256: **80D03385E9D62C16C7C951210F2D05C0A63BBC44A10D17BAED76C8D16D6FC7C2**. VCS/source, 0.8.0/code 17, min 26/target 36, osam native biblioteka/četiri ABI-ja, četiri ELF64 LOAD/RELRO i ZIP-P16 prolaze.
- Unsigned AAB SHA-256: **DC745E72A9EF9DCBAC3971A21837DC316EBF70B9B750C276F4F4CC2ACC97944E**. BundleConfig.pb potvrđuje uncompressed native libraries / PAGE_ALIGNMENT_16K.
- Zvanični bundletool 1.18.3 validate proverava strukturu istog AAB-a: exit 0, modul base. Korišćen je postojeći resolved AGP classpath, bez novih preuzimanja. Njegov JAR SHA-256 je CCAD18514FD97DB010856B2BBED40F481F8BA9349368C97AE54D72E3567D0171. Sopstveni neispravan 415-byte config-only ZIP je odbijen; originalni AAB nije menjan.

Dokazi: artifacts/release-rc-8a4b8e4-{evidence,build-inspection,bundle-config-inspection,bundle-structural-inspection}.json, artifacts/bundle-structural-negative-result.json i ciljani release-focus-8a4b8e4-evidence.json. Puni evidence sadrži konačan status svih šest poslova, uključujući tablet neuspeh; 8a4b8e4 nije kvalifikovan za distribuciju ili potpisivanje.

## Završni uslovi i granice

Distribucija i vlasnički potpis zahtevaju svih šest uspešnih poslova istog izvora, tačan inventar 519/140/13 + dve dijagnostike, tri R8/jezička/runtime toka, binarnu nadogradnju, adaptive/split-screen dokaze i poređenje sadržaja tri originalna CI release APK-a. Potpisivanje zahteva i strukturnu/16 KB proveru tačnog AAB-a. Jarsigner zatim proverava owner sertifikat i potpis; svaki originalni ZIP payload ostaje byte-identičan, a potpisani AAB ponovo prolazi bundletool validate. Negativne host kontrole odbijaju nedostajući comparison, drugi source, različit payload i drugi upgrade APK pre pristupa ključu. Sintetički JAR strict/tamper test je zaseban kriptografski dokaz, ne potpis aplikacije.

Aplikacioni AAB još nije vlasnički potpisan. Fizički GPU/peak-memory/OEM/stylus, potpuni govorni TalkBack i nezavisna kopija ključa ostaju u [RELEASE-HANDOFF.md](RELEASE-HANDOFF.md). Strukturna/ELF/ZIP provera ne predstavlja runtime potvrdu svakog uređaja ili Play odobrenje. Ne tvrdi se zaštita kompromitovanog sistema ili odsustvo svih ranjivosti.

Prethodni neuspešni app/fixture/startup pokušaji, ispravke i originalni archive digest-i ostaju u [RELEASE-READINESS.md](RELEASE-READINESS.md). Stariji prolaz ne zamenjuje konačan source ili paket.
