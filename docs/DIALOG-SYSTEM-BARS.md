# Prikaz dijaloga — kandidat 0.8.1

Radni kandidat je **0.8.1/code 18**; dokument 6/delta 2/Room 4 i offline opseg ostaju isti. Poslednji kompletno kvalifikovani i sačuvani paket i dalje je [0.8.0/source 36cd15a](LOCAL-0.8.0-CANDIDATE.md). Njegovi stari APK/AAB fajlovi nisu prepisani niti proglašeni paketima ove izmene.

## Konkretan nalaz

[CI 43688093688](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43688093688), tooling c5e52e130ce00ffba7fde9abe08bbc2bd8d1da62 i originalni aplikacioni source 36cd15a, prolazi samostalni theme-capture profil na API 36 Google Play sa stvarnim PAGE_SIZE 16384. Sve četiri scene u četiri theme/system-night kombinacije i dva font scale-a daju **32 originalna PNG-a**. Početni theme/font/night su vraćeni; keyboard smoke nije deo tog profila.

Artefakt 14150489795 ima SHA-256 **3C6B23F8589B831A791196D2A7AC92244619C3FF4338BAB1E9FD929B7D92979F**. Svih 32 snimka su digest-proverena i pregledana, a pregled je našao problem statusne trake u tamnom color picker-u pri fontu 200%:

- `dark-forced-font-20-color-picker.png`: SHA-256 **9F5DBCF5225D8090F328E3962B80DC8D4D7D7C8FB82C51C906BCC36859194C03**.
- `system-dark-font-20-color-picker.png`: SHA-256 **BA4C6B09F20EA9736127C86FC19DCC0077C5BED760A226D46533E54452AD29F4**.

Vreme i sistemske ikone su tamni na tamnoj pozadini. Kontrole samog portretnog dijaloga ostaju čitljive. Font 200% ima scroll viewport-e, prelomljene settings opcije i skraćene nazive tabli; pregled ne tvrdi da svaka kontrola staje odjednom. Ovo nije merenje svih kontrastnih odnosa ili odobrenje svih jezika i dijaloga.

## Ispravka i regresija

Prethodni WhiteboardTheme podešava samo Activity Window. Compose dijalog poseduje zaseban Window preko [DialogWindowProvider](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogWindowProvider); promena vlasničkog Activity-ja sama ne postavlja appearance zastavice tog prozora. Ispravka prenosi izabranu Light/Dark/System odluku kroz CompositionLocal i u InputDialog/InputAlertDialog primenjuje ikone na stvarni prozor njihovog sadržaja. Na API 30+ koristi postojeći [WindowInsetsControllerCompat](https://developer.android.com/reference/androidx/core/view/WindowInsetsControllerCompat), bez novog SDK-a, dozvole ili globalnog WindowManager pristupa. Legacy dijalozi zadržavaju svoje platform/Compose window zastavice, kao što je objašnjeno ispod.

Dva nova DialogAppearanceTest slučaja proveravaju plain i alert dijalog: promena same teme mora ažurirati zadržani Window, dok font 100/200% sme da ga ponovo kreira; oba puta njegove ikone moraju odgovarati temi. [Tačan Compose UI 1.10.1 izvor](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.10.1/ui-android-1.10.1-sources.jar) koristi `remember(view, density)` za DialogWrapper. Zato je prestroga prvobitna asercija iste instance i tokom promene fonta ispravljena, a nedovršeni CI 43689778057/source e8fb920 je superseded novim izvornim stanjem, ne kvalifikovan kao prolaz. Postojeći IME testovi zadržavaju svoja očekivanja.

## HEX polje pri maloj preostaloj visini

Uspešan R8 input kontrolni [CI 43689937504](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43689937504), isti originalni source 36cd15a i 4 GB udaljeni VM, potvrđuje funkcionalan HEX/landscape Apply tok. Njegov originalni `release-color-keyboard-landscape.png`, SHA-256 **3C90A5EB2FA8882EF856983E1011D7C51B073926AF7300B5D7F063CE63B91C0E**, ipak pokazuje odsečen donji deo HEX teksta. Funkcionalni prolaz zato nije predstavljen kao vizuelni prolaz.

Kompaktni prikaz je trošio po 16 dp spoljnog i unutrašnjeg vertikalnog razmaka. AdaptiveDialogSurface sada u tom režimu ostavlja prirodnu visinu polju i akcijama, dok običan prikaz zadržava razmake. Input i action roditelji ostaju stabilni tokom promene IME insets-a; ne menjaju se tip polja, HEX validacija, čuvanje ili upravljanje tastaturom.

Novi ColorDialogLayoutTest ograničava stvarni zajednički surface/layout na 82 dp visine i zahteva da HEX polje zadrži najmanje 56 dp i tačan tekst, uz vidljive Cancel i Apply akcije. Sa dva nova testa prozora ukupan novi redovni inventar je **143 app + 13 data**, uz 519 JVM i dve odvojene memory/frame dijagnostike.

Lokalno prolaze Android test kompilacija, stil i arhitektura: 116 task-ova, završni prolaz 14 s u `artifacts/dialog-compact-20261115-compile-fixed.log`; raniji density-test prolaz traje 48 s. Prvi poziv bez JAVA_HOME nije pokrenuo build. Style prijave novih testova i indentacija refaktorisanog color-picker bloka ispravljene su postojećim formatter-om, ograničenim na promenjeni fajl. Ovo nije izvršavanje na uređaju. Puna nova matrica sa 143 app testa i novi R8 vizuelni dokaz potrebni su pre kvalifikacije ili novog distribucionog paketa. Prethodni CI 43690271484/source 2202a0d ima zaseban 142-testni opseg; njegov delimičan rezultat nije završna kvalifikacija ove dodatne ispravke.

Ostali otvoreni nalazi iz [dodatne kvalifikacije](ADDITIONAL-QUALIFICATION.md), govorni TalkBack, fizičko profilisanje i nezavisna kopija ključa ostaju u svom opsegu. Play predaja nije izvršena.

## Android 8 regresija i ograničena ispravka

[CI 43691241431](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43691241431), source 966fb03, ima neuspešnu API 26 phone i tablet kvalifikaciju: svaki izveštaj sadrži svih 143 app testa sa po pet neuspeha, bez preskakanja i grešaka izvršavanja, dok svih 13 data testova prolazi. API 30 i 33 poslovi prolaze. To nije kvalifikovan kandidat, i njegovi unsigned inspection fajlovi nisu distribucija.

Tablet snimci Objects pretrage, New folder-a i InputDialogWindowTest-a pokazuju da aktivna tastatura zaklanja rezultat/akcije dijaloga. Originalni tablet arhiv 14152943187 ima SHA-256 **BEDB86D4A10A9768132E87B00A9311F6A5C56538164E34DE58BCA8110141AF15**, a phone arhiv 14152948306 **C4438A0AF6B18EA43FB7F697220B4E3CA32C095836BBA319DEEBB95A660EE643**. Oba izveštaja ostaju sačuvana. Četiri neuspeha su zajednička (Objects search, folder i dva input-window testa); peti je tablet LayerOrderingFlowTest, odnosno phone ColorPickerComposeTest cancellation. Ne tvrdi se da je svaki neuspeh već zasebno objašnjen.

[Tačna AndroidX Core 1.16.0 legacy implementacija](https://dl.google.com/dl/android/maven2/androidx/core/core/1.16.0/core-1.16.0-sources.jar) za svetle ikonice dodatno uklanja translucent window zastavice i dodaje DRAWS_SYSTEM_BAR_BACKGROUNDS. Za razliku od same boje ikonica, to utiče na layout legacy floating prozora. Nova ograničena ispravka na API 26–29 dijalozima menja samo podržane appearance bitove decor view-a, čuvajući window zastavice koje postavlja Compose. Activity i API 30+ zadržavaju compat putanju. Kada platforma ne podržava primenu appearance bita uz postojeći stil prozora, zadržava se njeno ponašanje; vizuelna kvalifikacija legacy sistemskih traka ostaje zasebna.

Postojeći testovi nisu oslabljeni. Dva DialogAppearanceTest slučaja sada dodatno čuvaju početne system-bar window zastavice i zahtevaju da ih promene teme/fonta ne promene, uz prethodne zahteve za ikonicama i stvarnim vlasničkim prozorom. Inventar ostaje 143/13/519. Lokalna kompilacija, stil i arhitektura prolaze kroz 116 task-ova za 29 s (`artifacts/dialog-legacy-20261115-compile-fixed.log`); prvi poziv je prijavio stil, koji je ispravljen formatter-om samo u dva promenjena fajla. Potrebna je puna nova kvalifikacija pre tvrdnje da su uređajski neuspehi rešeni.

## Rezultat ispravke i čekanje stvarnog IME-a

[Source 7137675 / CI 43693506262](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43693506262) potvrđuje tablet API 26 **143 app + 13 data bez neuspeha**, uz ceo optimizovani smoke/jezički/runtime posao. Android 11 i 13 poslovi takođe prolaze. Phone API 26 ima **142/143 app i 13/13 data**: ranijih pet neuspeha svedeno je na jedan drugačiji neuspeh unutar InputDialogWindowTest, pri prvom Plain → Alert replacement-u. To nije puna kvalifikacija.

Originalni tablet artefakt 14153514971 ima SHA-256 **AD3824B9DD39BC84AF0FDD8A8146FF93B0A62A419AACA39649FFF9B7D51A64C4**. Phone `input-window-plain.png` ima SHA-256 **4E4543C12C0ED77761406E28470E46ABF28A7606CED85D09CD4985F9E94AD7DE**, njegov XML **18E56582FACC39860A5DB8C3E87C7B9FDC7B9A11E443E736EDB46020CA456018**, a istovremeni IME dump **0A31A65DFDA752DB48934BBC3F4FC13D0BA4CAABC6BB5532B86B403723FD8D47**. Sadržaj dijaloga i obe akcije su vidljivi; Child je fokusiran, ali IME je skriven, a servis još pokazuje Owner input connection i null root. Uzrok tog zasebnog prelaza nije potvrđen samim snimkom.

Test je posle fizičkog UiAutomator klika čekao IME preko SystemClock petlje, koja ne pokreće kontrolisani Compose sat. Input callback može zakazati Compose rad nakon prethodnog waitForIdle. Čekanje sada koristi [Compose waitUntil](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/ComposeUiTest#waitUntil(kotlin.String,kotlin.Long,kotlin.Function0)), koji pokreće taj sat i predaje vreme platformskom IME-u. Uslov ostaje stvarni mInputShown=true, rok ostaje 10 s, nema drugog klika, dodatnog show zahteva ili izostavljenog replacement-a. Ovo je opravdana sinhronizaciona kontrola; naredni uređajski rezultat treba da pokaže da li je dovoljna. Ako i dalje padne, test sada uz postojeće dokaze čuva ograničen WindowManager dump pre cleanup-a. Produkcioni kod ove kontrole ostaje identičan source-u 7137675; menja se samo test/diagnostic izvorni presek.

Lokalno prolaze kompilacija testova, stil i arhitektura: 116 task-ova / 45 s (`artifacts/dialog-clock-20261115-compile.log`). Ovaj rezultat nije izvršavanje novog čekanja na uređaju.

## Konačan rezultat clock kontrole i verzija QA ulaza

[CI 43695542760 / source dc7ddc8](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43695542760) sada ima završne izveštaje: svih pet profila sadrži **143 app + 13 data bez neuspeha ili preskakanja**. API 26 phone/tablet i API 30/33 poslovi prolaze; oba input-window slučaja rade uz stvarni IME i prethodne zahteve. To podržava sinhronizacionu ispravku testa, bez tvrdnje o svakom mogućem platformskom focus problemu.

API 36 posao nije zelen: posle native testova, binarne nadogradnje, optimizovanog smoke toka i runtime merenja staje u prvom adaptive slučaju jer pozivalac traži `Whiteboard 0.8.0`, dok pregledani APK ima verziju 0.8.1. Originalni neuspeh `Version/license section could not be reached in compact-320` ostaje sačuvan. Nije izmenjen UI da bi prikazivao staru oznaku.

Run-DeviceCi sada čita očekivanu verziju iz AAPT badging izlaza stvarnog izgrađenog APK-a preko ograničenog Read-ApkVersionName parser-a. Test-Adaptive zahteva izričitu očekivanu verziju i i dalje mora da pronađe tu oznaku i licence u stvarnom interfejsu. Host kontrole prihvataju 0.8.0/0.8.1/1.0.0 i odbijaju nedostajući, neispravan ili višestruk podatak. Provereno je i na digest-proverenom APK-u dc7ddc8, SHA-256 **188F7EE07EC1B61657BAB38353E3946629AD2276F09F1FFD2D081ADF6C2CDAD8**, koji vraća 0.8.1. Izveštaj je `artifacts/release-dc7ddc8-apk-version-host-check.json`; kontrola je dodata u redovni CI.

Ova QA izmena zahteva novu punu matricu pre distribucije. Poslednji kvalifikovani distribucioni fajlovi i dalje su 0.8.0; nova adaptive kontrola, zasebne dijagnostike i izvorno vezani R8 vizuelni/TalkBack/16 KB dokazi ne proglašavaju se izvršenim unapred.

## Završena matrica 06f9dc8 i dodatni kontrastni nalaz

[CI 44892361765](https://github.com/cmarko-gits/Whiteboard-/actions/runs/44892361765) prolazi svih šest poslova: 519 JVM i svih pet grupa po 143 app + 13 data. Svih šest arhiva je digest-provereno u `artifacts/release-rc-06f9dc8-evidence.json`. Nova adaptive provera čita stvarnu 0.8.1 verziju. Pregledani unsigned AAB ima SHA-256 **4B99E140DF0E467B16F61A3EAB7AEB8D4A9555015C0841BFC27457F19C61C858**, prolazi zvanični bundletool validate i PAGE_ALIGNMENT_16K konfiguraciju. Distribucija tog međupreseka nije kreirana.

Vizuelni pregled njegovog API 26 optimizovanog unosa boje potvrđuje ceo HEX tekst i obe akcije uz vidljivu tastaturu. Originalni PNG ima SHA-256 **847431A129B84752C0CD272EF12DC4DE9FD0E79D0204E72B39C10213B69EC322**. Istovremeno otkriva tamne statusne ikonice/vreme preko zatamnjene pozadine. U oblasti sata stabilne grupe piksela su RGB 40/40/40 i 100/100/99, sa približnim raster kontrastom **2.489:1**. To je ograničeno očitavanje originalnog snimka, ne globalna potvrda usaglašenosti; [W3C objašnjava značaj kontrasta i ograničenja antialias merenja](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html). Metadata i oblast merenja su u `artifacts/dialog-contrast-20261121-before.json`.

Raniji API 36 portretni Light/font 200% snimak iz 32-image grupe pokazuje isti obrazac. Nasuprot tome, tekući API 36 landscape PNG, SHA-256 **D70C0A376FE6F62BE8E29226865B5B44D26282FDEC3532446E08033FE0B5ADA9**, već ima svetle statusne ikonice i čitljiv HEX. U njemu tastatura nije vidljivo iscrtana; njegovo ime i funkcionalni prolaz ne predstavljaju zasebnu vizuelnu potvrdu tastature. Nalaz se zato ne generalizuje na svaki prozor ili uređaj.

Pravilo sistemskih traka sada prati stvarnu pozadinu: Activity prati Light/Dark/System temu, a providan modal sa zatamnjenim vlasnikom traži svetle ikonice. Za modal se obe compat appearance vrednosti postavljaju na false. Prema tačnom AndroidX Core 1.16.0 izvoru, ova legacy grana samo uklanja appearance bitove i ne menja translucent/DRAWS window zastavice. Zbog toga više nisu potrebni prethodni ručni legacy bitovi ili nasleđivanje teme kroz CompositionLocal. Postojeće očuvanje window zastavica i IME testovi ostaju aktivni.

Ista politika važi za sve aktivne obične potvrde, foldere, stranice, podešavanja i licence preko zajedničkog WhiteboardAlertDialog. Ponašanje dugmadi, tekst, Material izgled i properties ostaju isti; appearance hook se nalazi u obaveznom confirm slot-u i ne dodaje vidljiv element. InputDialog/InputAlertDialog zadržavaju svoj zasebni keyboard lifecycle. Neaktivni cloud moduli nisu uključeni u ovu izmenu.

Treći DialogAppearanceTest proverava stvarni Window običnog alerta, uz postojeća dva input slučaja, theme/font promene i nepromenjene window zastavice. Novi inventar je **144 app + 13 data + 519 JVM**, uz dve zasebne dijagnostike. Lokalna kompilacija, stil i arhitektura prolaze kroz 116 task-ova za 71 s (`artifacts/dialog-all-modals-20261121-compile.log`), bez preuzimanja zavisnosti; Gradle daemon je zaustavljen. Ranija pojedinačna style prijava razmaka je ispravljena. Nova puna matrica i novi R8 snimci potrebni su pre tvrdnje da je i ova dodatna vizuelna izmena kvalifikovana.

## Prekid Android 11 emulatora u prvom pokušaju

[CI 44895366623 / source e3a2c1e](https://github.com/cmarko-gits/Whiteboard-/actions/runs/44895366623), API 30 job 136819750531: data 13/13 prolazi, a app izvršavanje je prekinuto posle 24 završena slučaja, tokom rotacije u 25. slučaju ColorPickerFlowTest. XML ima 25 unosa i jedan sintetički neuspeh; to nije izvršena grupa od 144 slučaja. Runner javlja `INSTRUMENTATION_ABORTED: System has crashed`.

Originalni artefakt 14703657644 ima SHA-256 **10EE680CE3E5A65055D519AF597D9F2818DAAC0D209243783DE1E82E71AC7964**. Digest-proveren log pokazuje SIGSEGV u **/system/bin/surfaceflinger**, PID 344, ImageManager thread, prvi okvir `/vendor/lib64/egl/libEGL_emulation.so (eglCreateImageKHR+754)` i zatim sistemski restart. U zadržanom AndroidRuntime izvodu nema aplikacionog Java izuzetka. Ovo utvrđuje koji proces i drajver su pali; ne utvrđuje univerzalni uzrok ili da se problem ne može ponoviti. Nije proglašeno ispravkom aplikacionog koda ili bezbednosne ranjivosti.

Planiran je jedan svež API 30 emulator sa istim source-om i nepromenjenim testovima, tek po završetku ostalih poslova. Prvi neuspeh ostaje sačuvan; ponavljanje istog pada zahteva novu dijagnozu umesto daljih slepih pokušaja. Dok punih 144/13 ne bude izvršeno u zdravom okruženju i cela matrica ne prođe, paket se ne priprema. Lokalni zapis je `artifacts/release-e3a2c1e-api30-infrastructure-failure.json`.
