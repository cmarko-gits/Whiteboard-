# Whiteboard za Android — lokalni kandidat 0.7.2

Kvalifikovani lokalni paket 0.7.2 ubrzava pripremu preview-a povezanih objekata. [LOCAL-0.7.2-CANDIDATE.md](docs/LOCAL-0.7.2-CANDIDATE.md) vezuje 535 jedinstvenih testova, pet Android profila, tri release toka, poređenje algoritama i tačan paket za izvor. [Uputstvo](docs/LOCAL-USER-GUIDE.md) opisuje funkcije. **13/47 završeno, 34 otvoreno**; Z08 nastavlja rad na preostalim cenama velikih scena. Zasebna razvojna osnova 0.8.0/code 17 uvodi F06 domenski model: [MIND-MAP-DESIGN.md](docs/MIND-MAP-DESIGN.md). [Page/format 6 integracija](docs/MIND-MAP-PERSISTENCE.md) lokalno se proverava; pune komande grana, vidljiva scena i UI još nisu završeni. Razvojna 0.8.0 nije distribuirani kandidat. Prethodni paketi i dokazi ostaju sačuvani.

Native Android aplikacija u Kotlin-u za lokalne bele table. Trenutni obim su editor, biblioteka, oporavak, mediji i prenosivi backup. AI, backend, nalozi, cloud i objavljivanje na Play-u nisu deo ove iteracije. Lokalni paket nema INTERNET ni ACCESS_NETWORK_STATE dozvolu.

Rekonstrukcija uvodi novo aplikaciono jezgro, Hilt graf, lokalnu navigaciju, bibliotečki ViewModel i Use Case sloj. Postojeći renderer, komande, format i Room migracije se zadržavaju radi kontinuiteta dokumenata i regresione provere. Ovo nije tvrdnja da je svaki red koda ponovo napisan.

## Razvoj

Na ovom računaru, dok važi limit od 3,4 GB dodatnog prostora, teške build/device provere izvršavaju se na GitHub CI-ju. Komande ispod su uputstvo za reprodukciju na odgovarajuće opremljenom razvojnom okruženju; ne pokretati lokalni Gradle i emulator istovremeno niti preuzimati nove system image-e u aktuelnoj sesiji.

Otvoriti projekat u Android Studio-u, sa SDK 36 i JDK-om koji odgovara Gradle wrapper-u (ovde Android Studio JBR). Minimum je Android 8 / API 26, target API 36. SDK putanja ostaje u ignorisanom local.properties.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat checkKotlinStyle checkArchitecture :core:test :format:test :data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease exportRuntimeDependencies '-Pwhiteboard.leakDetection=false' '-Pkotlin.compiler.execution.strategy=in-process' --offline --no-daemon --max-workers=1 --no-parallel '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8'
.\tools\Test-Android.ps1 -Suite all
.\tools\Test-DependencySecurity.ps1
.\tools\Get-DependencyLicenses.ps1 -FetchMissing
.\tools\Build-LicenseNotices.ps1 -Verify
```

Device skripta podrazumeva emulator-5554. Za drugi ciljni uređaj eksplicitno zadati DeviceSerial. U okruženju sa ograničenim resursima build i emulator rade naizmenično. Ne preuzimati novi emulator image bez potrebe; ograničenje dodatnog prostora je 3,4 GB.

`tools/Smoke-Test.ps1` proverava optimizovani paket, recovery i rasporede. Zatim `tools/Test-WindowModes.ps1` proverava stvarni split-screen preko WindowManager-a, `tools/Test-Adaptive.ps1 -VersionName 0.4.1` font/prozore, a `tools/Test-Locales.ps1` sr-Latn i stvarnu RTL konfiguraciju. Te skripte koriste engleski početni interfejs, otvaraju sopstvenu Recovery_check test tablu i vraćaju izmenjenu konfiguraciju. Izabrani emulator nije fizička kvalifikacija svih uređaja.

Verzija 0.4 dodaje pristupačne objekte platna, kompletan Objects navigator i alternative gestovima. Za zasebne testove sa LeakCanary/Shark i stvarnim TalkBack servisom koristiti uputstvo u [kvalifikacionom izveštaju](docs/LOCAL-QUALIFICATION.md). Redovni CI testovi ostaju bez tih eksplicitnih dijagnostičkih klasa.

## Moduli

- `core`: čist Kotlin, modeli, istorija/komande, repository portovi i Use Case-ovi.
- `format`: verzionisani JSON i ZIP backup, validacija neprijateljskog ulaza.
- `data`: Room transakcije, oporavak, pretraga, folderi i lokalna podešavanja.
- `canvas`: Android unos, geometrija prikaza, renderer i izvoz.
- `core:ui`: tema, deljene kontrole, EN i sr-Latn resursi.
- `feature:workspace`: Compose biblioteka/editor i ViewModel-i koji koriste Use Case-ove.
- `platform`: privatne datoteke, sistemski URI-ji, slike/PDF i kamere.
- `app`: Application, Activity, lokalna navigacija i Hilt composition root.

Server, sync i feature/cloud nisu u aktivnom Gradle grafu. Istorijski online kod i odluke ostaju arhivirani; Room schema 4 i njegove stare tabele ostaju radi nedestruktivnog upgrade-a, bez mrežne funkcije. checkArchitecture proverava granice modula/importa i zabranu blokiranja produkcionih korutina.

Manual Save čuva uhvaćenu reviziju. Izmene tokom upisa ostaju dirty; recovery je odvojen i debounced 700 ms. Prekid procesa pre uspešnog recovery upisa može izgubiti najnovije izmene ili nedovršen gest. Recovery nije backup. Automatski Android backup/transfer je isključen; korisnik pravi ZIP backup sa prilozima. Deinstalacija briše privatne podatke.

## Plan i dokazi

- [Aktuelni plan i kriterijumi](docs/LOCAL-REBUILD-PLAN.md)
- [Preostali rad, lokalne funkcije iz vizije i odložene stavke](docs/LOCAL-REMAINING-TASKS.md)
- [Aktuelna arhitektura](docs/LOCAL-ARCHITECTURE.md)
- [Aktuelni lokalni QA status](docs/LOCAL-STATUS.md)
- [Aktuelne bezbednosne provere](docs/LOCAL-SECURITY.md)
- [Pristupačnost, memorija i profili](docs/LOCAL-QUALIFICATION.md)
- [Arhivirani online kod](legacy/README.md)

Optimizovani APK i AAB služe lokalnoj proveri i pripremi. Preview koristi razvojni potpis; vlasnički upload ključ i Play Console objava nisu uključeni. Podrška svim fizičkim telefonima, stylus uređajima i foldable konfiguracijama nije proglašena na osnovu emulatora.

Repozitorijum: [cmarko-gits/Whiteboard-](https://github.com/cmarko-gits/Whiteboard-).
