# Bezbednosni pregled kandidata 0.8.0

Presek 11. novembra 2026, pregledani aplikacioni izvor **36cd15a6415d66eff7be10c97802ddc61f522f59**, code 17. Svih šest poslova pune matrice [43008205011](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43008205011) prolazi iz prvog pokušaja: 519 JVM, pet profila po 140 app + 13 data, lint/build i security gates. Strict package gate, poređenje tri R8 APK-a i vlasnički potpisani AAB su završeni. Opt-in eksterni UI klijent koristi odvojeni testni APK i standardni AndroidJUnitRunner. Release politika zabranjuje instrumentation unose i njegov DEX paket u produkcionom APK-u. Ovo nije nezavisan pentest ili potvrda konačne spremnosti za Play.

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
- 453 neignorisana source fajla prolazi Gitleaks 8.30.1. Odvojeni lokalni pregled svih refs prijavljuje 149 skeniranih revizija među 157 dostupnih, bez prijavljenih tajni. Izveštaj: artifacts/security/release-36cd15a-history-summary.json.
- Unsigned APK SHA-256: **1C9EAB9A97E727AD7B67C12E95C7BCE000DEF34C002795D11C40E412B23AF780**. VCS/source, 0.8.0/code 17, min 26/target 36, osam native biblioteka/četiri ABI-ja, četiri ELF64 LOAD/RELRO i ZIP-P16 prolaze.
- Unsigned AAB SHA-256: **91CCD7C558578893F15B262879235E84E868E0AA33A5F734ADFC537969FAA8E4**. BundleConfig.pb potvrđuje uncompressed native libraries / PAGE_ALIGNMENT_16K.
- Zvanični bundletool 1.18.3 validate proverava strukturu istog AAB-a: exit 0, modul base. Korišćen je postojeći resolved AGP classpath, bez novih preuzimanja. Njegov JAR SHA-256 je CCAD18514FD97DB010856B2BBED40F481F8BA9349368C97AE54D72E3567D0171. Sopstveni neispravan 415-byte config-only ZIP je odbijen; originalni AAB nije menjan.

Dokazi: artifacts/release-rc-36cd15a-{evidence,build-inspection,bundle-delivery-inspection,bundle-structural-inspection,packages,release-content-match}.json. Konačni evidence sadrži svih šest originalnih digest-proverenih CI arhiva. Prethodni stvarni zeleni ciljani izveštaj sa šest poslova odbijen je na 140/13 paket gate-u, bez kreiranja preview-a: release-focus-9c52b9b-package-boundary.json. Lokalni ACL/javni sertifikat pregled je artifacts/security/release-127a072-key-storage.json; privatni sadržaj nije čitan ili kopiran tim pregledom. Svi prethodni neuspešni izvori ostaju u RELEASE-READINESS.md i nisu distribuirani.

## Završni uslovi i granice

Distribucija i vlasnički potpis zahtevaju svih šest uspešnih poslova istog izvora, tačan inventar 519/140/13 + dve dijagnostike, tri R8/jezička/runtime toka, binarnu nadogradnju, adaptive/split-screen dokaze i poređenje sadržaja tri originalna CI release APK-a. Potpisivanje zahteva i strukturnu/16 KB proveru tačnog AAB-a. Jarsigner zatim proverava owner sertifikat i potpis; svaki originalni ZIP payload ostaje byte-identičan, a potpisani AAB ponovo prolazi bundletool validate. Negativne host kontrole odbijaju nedostajući comparison, drugi source, različit payload i drugi upgrade APK pre pristupa ključu. Sintetički JAR strict/tamper test je zaseban kriptografski dokaz, ne potpis aplikacije.

Aplikacioni AAB je vlasnički potpisan: SHA-256 **77054F7DC2B0AC14CA8D75631D1445DE7ECE2AC59CC43447A317505D79FDAD97**, upload sertifikat **67158377779CBECE20CBED2E46F0D44EB337128E547F2B980FE648D720A3A522**. Strict jarsigner proverava taj sertifikat; svih 390 originalnih ZIP stavki ostaje nepromenjeno, DEX/native sadržaj jednak je kvalifikovanom preview-u i potpisani bundle prolazi zvanični bundletool validate. Dokazi: artifacts/whiteboard-0.8.0-{owner-signing,owner-bundle-validation}.json i whiteboard-0.8.0-bundle-verification.log. Privatni ključ pristupljen je samo lokalnim potpisnim postupkom; lozinka se oslobađa iz zaštićene memorije i environment-a po završetku. Upload sertifikat nije Google Play App Signing sertifikat, a Play predaja nije izvršena.

Fizički GPU/peak-memory/OEM/stylus, potpuni govorni TalkBack i nezavisna kopija ključa ostaju u [RELEASE-HANDOFF.md](RELEASE-HANDOFF.md). Strukturna/ELF/ZIP provera ne predstavlja runtime potvrdu svakog uređaja ili Play odobrenje. Ne tvrdi se zaštita kompromitovanog sistema ili odsustvo svih ranjivosti.

Prethodni neuspešni app/fixture/startup pokušaji, ispravke i originalni archive digest-i ostaju u [RELEASE-READINESS.md](RELEASE-READINESS.md). Stariji prolaz ne zamenjuje konačan source ili paket.
