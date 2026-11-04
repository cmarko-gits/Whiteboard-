# Lokalna bezbednosna provera — istorijska osnova i 0.5.1

Najnoviji kvalifikovani izvor je [0.6.5/5129627](LOCAL-0.6.5-CANDIDATE.md), CI 41888835384: 155 runtime komponenti/licenci/notices, OSV bez prijavljenih poznatih nalaza, source scan i proširena manifest/DEX/offline-font politika prolaze. EmojiCompatInitializer nije automatski pokrenut; koriste se sistemski emoji/fontovi. Lokalni razvojni potpis, ZIP-P16, četiri ELF64 i sadržaj prema tri CI release paketa provereni su uz tačan hash. Ostatak ovog dokumenta opisuje istorijsku osnovu i trajne granice poverenja. Kandidat 0.6.5 je kvalifikovan za opisani emulator obim.

Datum: 1. novembar 2026. Obim je aktivna lokalna Android aplikacija i tekući izvorni fajlovi. Ovo je inženjerski pregled sa automatizovanim regresionim proverama; nije nezavisan pentest niti tvrdnja da nema svih ranjivosti. Raniji backend/cloud izveštaj nije dokaz za ovu verziju.

Za provereni paket 0.5.1 merodavni su tačan commit, SHA-256 i gates u [LOCAL-0.5.1-CANDIDATE.md](LOCAL-0.5.1-CANDIDATE.md), kao i `artifacts/task-11-family-packages.json`. Dalje navedeni promenljivi artifacts/security fajlovi mogu pripadati kasnijem kandidatu; ne pripisivati njihov sadržaj ranijem APK-u bez poređenja hash-a. Razvojna rotacija 0.6.0 uvodi validaciju afinih matrica, granice koordinata i budžeta, ID/source ograničenja i odbijanje zastarelog plana; završni CI i pregled njenog tačnog paketa ostaju zaseban uslov.

## Imovina i granice poverenja

Štite se lokalne table, tekst, prilozi, recovery, istorija i izbori korisnika. Nepouzdani su datoteke/ZIP/JSON iz sistema i rezultati spoljnih aplikacija. Dokument napušta sandbox samo korisničkim izvozom/deljenjem ili izabranom kamerom/picker-om; izabrani sistemski provider može biti drugi servis. Kompromitovan OS, root i otključan uređaj prevazilaze garancije aplikacionog sandbox-a.

## Proverene mere

- Finalni optimizovani APK: nema INTERNET/ACCESS_NETWORK_STATE dozvole. Dozvoljen je samo interni dynamic-receiver permission. Izuzeti su online moduli i mrežni SDK-ovi; DEX sanity kontrola odbija poznate online/WorkManager klase i LeakCanary/Shark. Permission/manifest i dependency kontrole važe zajedno; DEX string provera sama ne dokazuje odsustvo obfuskovanog koda.
- App-private Room i datoteke, allowBackup/fullBackupContent false, data-extraction isključenja za backup i transfer. Nema sopstvene enkripcije baze/per-board lozinke. Postojeće compatibility tabele i stare lokalne datoteke nisu destruktivno brisane; ova verzija ne briše eventualni raniji udaljeni nalog.
- Scoped FileProvider: samo cache exports/ i camera/, provider nije exported. Deljenje daje privremeni read grant i ClipData. Ostale komponente imaju eksplicitnu privatnost ili proverenu sistemsku zaštitu; launcher ne tumači spoljne board URI-je.
- DocumentTransfers prihvata samo content URI sa authority i do 8192 karaktera. HTTP/file putanje se odbijaju pre pozivanja adaptera. Nema široke storage/camera dozvole za lokalni tok.
- Prilozi imaju ograničene veličine, validirane identifikatore i proveru sadržaja. Import ne dobija poverenje samo iz MIME/ekstenzije. ZIP parser ograničava ukupan raspakovan sadržaj i pojedinačne unose, odbija traversal/duplikate/nepoznate verzije; restore pravi nove dokumente i ID-jeve.
- Preview IO proverava canonical privatni thumbnails direktorijum, PNG ekstenziju i maksimalno 2 MiB pri čitanju, uz cancellation. Dekoder ograničava širinu/visinu na 1024. Neispravan preview se preskače bez gubitka dokumenta.
- Room transakcije/migracije bez destructive fallback-a. Testovi čuvaju poslednji uspešni zapis pri grešci/otkazivanju. Save čuva uhvaćenu reviziju; naknadne izmene ostaju dirty/recovery. Neuspešan autosave ne pravi beskonačan retry loop.
- UI greške imaju sanitarne resurse i identitet; CancellationException nije poruka o grešci. Nema logging-a sadržaja dokumenta ili sesija u novom lokalnom toku.

## Automatizovani dokazi

- `artifacts/security/release-policy.json`: rezultat manifest/resource/DEX politike i SHA-256 pregledanog finalnog APK-a.
- `artifacts/security/runtime-dependencies.tsv` i `dependency-audit.json`: 147 razrešenih Maven komponenti aktivnog Android release grafa; OSV nije prijavio poznate ranjivosti pri ovom pokretanju. Build alati/JDK/OS i neobjavljene ranjivosti nisu obuhvaćeni. [Izvor advisories](https://osv.dev/).
- `artifacts/security/source-secrets-summary.json` i `source-secrets.json`: Gitleaks 8.30.1, tekući neignorisani fajlovi uključujući untracked izvore i arhivu. Generisani artefakti, ignorisani lokalni kredencijali i Git istorija nisu skenirani.
- `artifacts/dependency-licenses.json`: 147 komponenti, bez nedostajućih metapodataka; aplikacioni open-source notices obnovljen iz aktivnog grafa i upstream tekstova.
- `artifacts/security/qualification-native-alignment.json`: četiri spakovane 64-bit biblioteke, ELF LOAD i RELRO kompatibilni sa 16 KB; zasebno je prošao zipalign -P 16. APK potpis proverava v2/v3; preview koristi razvojni ključ.
- JVM/device izveštaji, broj testova i granice dostupnog uređaja navedeni su u LOCAL-STATUS.md. No-INTERNET test proverava stvarni instalirani paket, a preview test proverava privatnu putanju i ograničenje veličine.

## Ponoviti

```powershell
.\gradlew.bat checkArchitecture exportRuntimeDependencies
.\tools\Test-DependencySecurity.ps1
.\tools\Get-DependencyLicenses.ps1 -FetchMissing
.\tools\Test-ReleaseSecurity.ps1 -ApkPath artifacts/whiteboard-local-0.4.0-preview.apk -Aapt2Path "$env:LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/aapt2.exe"
.\tools\Test-SourceSecrets.ps1 -GitleaksPath artifacts/security/gitleaks/gitleaks.exe
```

Pred javnu distribuciju ostaju vlasnički potpis, stvarni uređaji/min OS, accessibility/profiler kvalifikacija i nezavisan pregled prema procenjenom riziku. Backend, cloud, nalog i AI nemaju aktivnu implementaciju u ovom paketu i ne predstavljaju odložene serverske testove lokalne aplikacije.


## Dopuna 0.4: dijagnostički i pristupačni tokovi

LeakCanary/Shark i testni heap dump pripadaju isključivo debug/androidTest grafu. Release APK policy ponovo prolazi bez njihovih klasa, bez mrežne dozvole i bez dodatnih exported komponenti. Privremeni heap ostaje u privatnom cache-u dijagnostičkog paketa, analizira se lokalno i briše u finally bloku; korisnički heap nije kopiran u izveštaje. Sačuvani CPU trag sadrži metode/timing, a testni dokumenti su generisani fixture-i.

Virtuelni čvorovi ne čuvaju paralelne kopije dokumenta: akcija čita trenutno stanje, promena stranice poništava stare ID-jeve, a izmena teksta i relativna transformacija rešavaju aktuelni objekat po ID-ju. Test dokazuje da zastareli čvor ne menja novu stranicu sa istim uvezenim ID-jem. Bitmap lifecycle test dokazuje da kasni stari decode ne prepisuje nov rezultat. Bezbednosni i profiler dokazi vezani su za hash paketa u artifacts/local-packages.json i qualification-profile-packages.json.
