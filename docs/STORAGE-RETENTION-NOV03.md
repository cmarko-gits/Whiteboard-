# Retencija dokaza i prostora — 3. novembar 2026.

Ograničenje je 3.400.000.000 dodatnih bajtova, uz početnu slobodnu veličinu C: volumena 11.423.408.128 bajtova. To je konzervativno merenje celog volumena, uključujući druge Windows procese; uzrok svake promene nije moguće pripisati ovom projektu.

Pri nastavku, pre novog CI preuzimanja, zatečeno je 3.661.246.464 dodatnih bajtova. Uzrok promene tokom pauze nije utvrđen. Download guard je zaustavio preuzimanje. Raniji incident iz novembra ostaje sačuvan; ne tvrditi da ograničenje nikada nije prekoračeno.

Automatska provera odobrenja odbila je rekurzivno uklanjanje source-scan privremenih direktorijuma. Ta operacija nije izvršena i direktorijumi su zadržani. Nije ponavljana drugačijom komandom. Primena lossless kompresije drugih generisanih fajlova vratila je zauzeće ispod ograničenja.

## Šta je zadržano i kako proveriti

- Izvor, dokumenti korisnika i finalni preview APK/AAB paketi nisu menjani kompresijom. APK 0.6.3 ostaje source a230f11 sa ranije objavljenim SHA-256.
- Stariji CI verify ZIP-ovi sada imaju naziv `*-reports.zip` ili `*-retained-reports.zip`. Uklonjeni su samo dupli generisani APK/AAB unosi; preostali report/log/security/PNG/HTML unosi provereni su pojedinačnim SHA-256. Originalni GitHub container digest ostaje u retencionom zapisu; **izvedeni ZIP nije originalni GitHub artefakt**.
- Jedanaest starijih device ZIP-ova ima naziv `*-retained-reports.zip`, sa istim pravilom očuvanja izveštaja. Aktuelni a230f11 device dokazi ostali su originalni. Kasnije je i 8cb5da1 verify ZIP kompaktiran u `task-08-8cb5da1-verify-retained-reports.zip`; svi izveštaji su očuvani, originalni digest i uklonjeni dupli binarni unosi su u zasebnom manifestu. Pokretanje starog download helper-a nad preimenovanim starijim arhivama može ponovo tražiti originalni ZIP; ne preuzimati ga bez disk provere.
- Veliki root `artifacts/task-*`, `qualification-*`, `ci-*` i `qa-*` tekstualni izveštaji imaju nastavak `.log.gz`/`.txt.gz`. Stari dokumenti mogu navoditi nekadašnji naziv bez `.gz`. Gzip roundtrip čuva tačne izvorne bajtove; indeks sadrži originalnu veličinu i SHA-256.
- Odabrani stari generisani mapping/DEX/intermediary AAB/KSP/project-local Gradle history fajlovi imaju `.gz` nastavak. Njihove izvorne putanje ne treba tretirati kao gotov lokalni build; eventualni budući lokalni build mora ih regenerisati. Globalni Gradle cache, SDK i AVD podešavanja nisu menjani. Teški build/test poslovi ostaju u CI-ju.
- Stara nekorišćena server build distribucija zadržava skripte u `server-retained-metadata.zip`; 58 duplih JAR unosa prethodno je SHA-256 poređeno sa postojećim `server/build/install/server/lib` kopijama. Ovo je retencija generisanih fajlova; backend nije vraćen u razvojni obim.

## Lokalni manifesti

Svi navedeni putevi su relativni korenu projekta i ignorisani u Git-u:

- `artifacts/task-01-nov03-retention.json`: prvobitna mera, odbijena operacija, originalni/izvedeni ZIP hash-evi, entry manifesti i prva build kompresija.
- `artifacts/task-01-nov03-device-retention.json`: device originalni digest-i i sačuvani report unosi.
- `artifacts/task-01-nov03-log-retention.json`: gzip fajlovi, originalni bajtovi i SHA-256.
- `artifacts/task-01-nov03-additional-compression.json`: dodatna lossless kompresija generisanih fajlova.
- `artifacts/task-01-nov03-distribution-retention.json`: poređenje duplih distribucionih JAR kopija.
- `artifacts/task-01-nov03-baseline-retention.json`: naknadna 8cb5da1 verify retencija, 25.729.151 → 158.624 bajta, uz originalni/izvedeni hash i iste izveštaje.
- `artifacts/task-08-8cb5da1-evidence.json`: najnoviji preuzeti i digest-provereni CI rezultati.

Posle preuzimanja novog CI kompleta izmereno je 3.256.176.640 dodatnih bajtova, ispod ograničenja. Ovo je vremenski presek, ne rezervacija prostora. Guard zadržava 134.217.728 bajtova rezerve pre preuzimanja. Novi CI verify upload više ne uključuje veliki debug APK; release APK/AAB i svi izveštaji ostaju uključeni. Nema lokalnog SDK/image preuzimanja ili Gradle/emulator pokretanja.

## Dalji nastavak istog dana

Originalni source-scan direktorijumi su i dalje zadržani. NTFS `compact /C` primenjen je samo na tih 18 snapshot direktorijuma: svih 4.904 fajla zadržalo je istu putanju, veličinu sadržaja i SHA-256 pre/posle, uz oko 12,8 MB više slobodnog volumena. Ovo je filesystem kompresija koja transparentno čuva iste fajlove; nema uklanjanja foldera ili ponavljanja ranije odbijene delete operacije. Manifest: artifacts/task-01-nov03-ntfs-retention.json.

Dalja retencija superseded CI binarnih unosa, uz očuvane report unose i originalne/izvedene hash-eve: task-01-nov03-model-retention.json (0b3f169 verify), task-01-nov03-historical-device-retention.json (11 istorijskih arhiva) i task-01-nov03-ci-startup-retention.json (4fee1e8 verify i tri device arhive). Za osam starijih istorijskih arhiva originalni lokalni container hash je sačuvan, ali GitHub digest nije ponovo preuzet; ta razlika je eksplicitna u manifestu. Za novije osnove digest je upoređen sa sačuvanim digest-proverenim dokazima. Izvedene arhive nisu originalni GitHub ZIP-ovi. Aktuelni 0.6.3 a230f11 paket i njegovi raw device payload dokazi ostaju nepromenjeni.
