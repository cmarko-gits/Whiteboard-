# Kvalifikacija lokalnog Whiteboard-a 0.4.0

Ovo je istorijska kohorta 0.4.0. Aktuelni [0.7.2 kandidat](LOCAL-0.7.2-CANDIDATE.md) ima zasebnih 399 JVM i pet profila po 123 app + 13 data, tri release toka i provereni paket; 535 jedinstvenih slučajeva. Stari TalkBack, heap i frame-time nalazi ispod nisu automatski ponovljeni za njega. Warm preview algoritam ima novo petoprofilno poređenje, a committed edit/copy/codec i process/encoder/retained-memory scope ostaju otvoreni. Ovo nije završna potvrda cele aplikacije.

Datum: 1. novembar 2026. Korisnik je izabrao nastavak bez fizičkog telefona. Ovaj pregled obuhvata dostupne emulatore i lokalne pakete; ne potvrđuje fizičku latenciju olovke, OEM ponašanje ili sve Android uređaje.

## Pristupačnost

Platno izlaže pojedinačne virtuelne objekte, njihove opise, izbor i akcije. Move/resize/delete, edit teksta, dodavanje beleške/teksta, pan/zoom i Fit dostupni su bez preciznog crtanja gestova. Objects dijalog pretražuje celu stranicu i otkriva objekte van ekrana, uključujući slučaj sa više od 256 preklopljenih objekata. Boje imaju izabrano stanje, ciljeve od najmanje 48 dp i akcije za saturation/brightness. Slider-i imaju imena; fokus tastature je vidljiv.

`CanvasAccessibilityTest` koristi povezanu sistemsku hijerarhiju, ne pretpostavljene ID-jeve. Proverava izbor/pomeranje/edit/delete/Undo/persistence, kameru/dodavanje bez gestova, potpun navigator i odbijanje starog čvora posle promene stranice. `ColorPickerComposeTest` proverava semantiku i tačnu potvrđenu boju.

Sa instaliranim i uključenim TalkBack servisom provereni su touch exploration, pristupačni fokus i aktiviranje virtuelne beleške. Dokazi: `qualification-talkback-api37-app-tests.txt`, `qualification-api37-talkback.json/png`, `qualification-talkback-service.txt`. Zeleni fokus je vizuelno pregledan. Ovo je integraciona provera stvarnog servisa; nije slušna/profesionalna usability sertifikacija svih ekrana. Originalna secure accessibility podešavanja vraćena su posle testa.

## Memorija i profili

`MemoryQualificationTest` otvara stvarni editor sa osam privatnih bitmap priloga, ponovo kreira Activity i vraća biblioteku u šest ciklusa. Posmatra 12 instanci editor View-a i njihove ViewModel-e. LeakCanary assertion prolazi; Shark analiza završnog heap-a nalazi **0** preostalih `WhiteboardView` instanci. Privremeni heap od 38.951.515 bajtova analiziran je lokalno i obrisan u finally bloku; izveštaj ne sadrži korisnički heap.

API 37 PSS po ciklusima: 215318, 203487, 205553, 202735, 204523, 205555 KB; posle dodatne GC/analize 236267 KB. PSS uključuje runtime i dijagnostiku i nije maksimalna potrošnja niti dokaz odsustva svakog mogućeg curenja. Izveštaj: `qualification-api37-memory.json`.

`FrameQualificationTest` pokreće stvarne Window/Choreographer frejmove za 1000 poteza / 100000 tačaka, uz sampled CPU trace. Na SwiftShader emulatoru i debug paketu sa dijagnostikom: 89 frejmova; TOTAL_DURATION p50/p95/p99 250,89/400,74/834,84 ms. Rezultat pokazuje visoku latenciju tog testnog okruženja i ne zadovoljava fizički 60 Hz cilj. Ne predstavlja se kao prolaz performance budžeta ili fizički GPU FPS. Datoteke: `qualification-api37-frames.json`, `qualification-api37-cpu.trace` (Android Studio CPU profiler import). Raniji software-render i Save/Load/PNG benchmark ostaje zasebna mera, a ne zamena za Window frame time.

QA je otkrio i otklonio dve stvarne greške: zastareli editor snapshot mogao je vratiti geometriju pri izmeni teksta posle pomeranja; staro blokirajuće dekodiranje moglo je upisati rezultat u novi lifecycle bitmap keša. Obe imaju regresione testove. Prekinuti proces pri pokretanju sa 2 GB RAM-a nije uračunat kao prolaz; ponovljeni emulator koristi 3 GB, bez istovremenog Gradle-a.

## Ponovljivost

Build dijagnostičkog para, dok je emulator zaustavljen:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :data:assembleDebugAndroidTest '-Pwhiteboard.qualificationTests=true' '-Pwhiteboard.leakDetection=true' '-Pkotlin.compiler.execution.strategy=in-process' --offline --no-daemon --max-workers=1 --no-parallel '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8'
```

Zatim pokrenuti jedan emulator i koristiti `tools/Test-Android.ps1`. Redovna regresija isključuje tri eksplicitne kvalifikacione klase:

```powershell
.\tools\Test-Android.ps1 -Suite all -ExcludeClasses dev.whiteboard.app.MemoryQualificationTest,dev.whiteboard.app.FrameQualificationTest,dev.whiteboard.app.RealTalkBackTest -ReportPrefix qualification-regression
.\tools\Test-Android.ps1 -Suite app -SkipInstall -Classes dev.whiteboard.app.MemoryQualificationTest,dev.whiteboard.app.FrameQualificationTest -ReportPrefix qualification-profile
```

TalkBack test pokrenuti zasebno dok je instalirani TalkBack servis zaista uključen; test koristi `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`. Vratiti originalna podešavanja u finally bloku. Redovni UIAutomation testovi i TalkBack provera nisu istovremeni.

Dokazi se nalaze u `artifacts/`; SHA-256 vezuje tačan app/test par uz rezultate. Redovni CI bez qualification flag-a ne kompajlira ove tri klase. CI matrica API 26/30/33/36 pripremljena je lokalno; nije predstavljena kao izvršen GitHub run.

## Primarni izvori

- [Android custom view pristupačnost](https://developer.android.com/guide/topics/ui/accessibility/views/custom-views)
- [ExploreByTouchHelper](https://developer.android.com/reference/androidx/customview/widget/ExploreByTouchHelper)
- [AGP built-in Kotlin source sets](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [LeakCanary instrumentacioni testovi](https://square.github.io/leakcanary/ui-tests/)
- [Shark analiza heap-a](https://square.github.io/leakcanary/shark/)

### Analiza CPU traga

Iz ART v3 dual-clock zapisa rekonstruisano je 6235 događaja glavne niti bez nemonotonih timestamp-ova. Približna self atribucija obuhvata 856,486 ms CPU-a i 8284,881 ms wall vremena. `HardwareRenderer.nSyncAndDrawFrame` nosi 6928,389 ms wall vremena (oko 84% obuhvaćenog intervala), uz 92,277 ms CPU-a glavne niti. Najveći Java/Kotlin self uzorci su Point getter, Binder, EmptySet lookup i geometrija; nema dokaza da novi lookup/index sam rešava najveći wall zastoj.

To **upućuje** na čekanje/sinhronizaciju render pipeline-a u tom emulatoru. Native RenderThread/GPU stekovi nisu obuhvaćeni sampled Java tragom, pa se ne tvrdi dokazan GPU uzrok ili fizički performance rezultat. `qualification-api37-cpu-analysis.json` i `Analyze-QualificationTrace.ps1` čuvaju ulazni hash, postupak i granice aproksimacije. Format je proveren prema [ART trace implementaciji](https://android.googlesource.com/platform/art/+/master/runtime/trace.cc). Za konkretan fizički performance budžet potreban je zaseban release/hardware profil, izvan korisnikovog izbora da se sada radi bez telefona.

## Ograničenje dodatne OS matrice

Izolovani API 36 boot prekinut je pre pokretanja testova nakon prekoračenja limita slobodnog prostora. API 36.1 nije pokrenut. Ovi pokušaji nisu prolazna OS regresija. Posle uklanjanja novih QA disk-slika i regenerisivih međufajlova zauzeće je vraćeno ispod 3,4 GB; tačne vrednosti i preostali L40 kriterijumi su u LOCAL-STATUS.md. Startup helper sada proverava stvarni kraj VM procesa, RAM i veću rezervu prostora. Nove sistemske slike nisu preuzete.
