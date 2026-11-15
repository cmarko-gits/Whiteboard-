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

[AndroidX legacy implementacija](https://raw.githubusercontent.com/androidx/androidx/androidx-main/core/core/src/main/java/androidx/core/view/WindowInsetsControllerCompat.java) za svetle ikonice dodatno uklanja translucent window zastavice i dodaje DRAWS_SYSTEM_BAR_BACKGROUNDS. Za razliku od same boje ikonica, to utiče na layout legacy floating prozora. Nova ograničena ispravka na API 26–29 dijalozima menja samo podržane appearance bitove decor view-a, čuvajući window zastavice koje postavlja Compose. Activity i API 30+ zadržavaju compat putanju. Kada platforma ne podržava primenu appearance bita uz postojeći stil prozora, zadržava se njeno ponašanje; vizuelna kvalifikacija legacy sistemskih traka ostaje zasebna.

Postojeći testovi nisu oslabljeni. Dva DialogAppearanceTest slučaja sada dodatno čuvaju početne system-bar window zastavice i zahtevaju da ih promene teme/fonta ne promene, uz prethodne zahteve za ikonicama i stvarnim vlasničkim prozorom. Inventar ostaje 143/13/519. Lokalna kompilacija, stil i arhitektura prolaze kroz 116 task-ova za 29 s (`artifacts/dialog-legacy-20261115-compile-fixed.log`); prvi poziv je prijavio stil, koji je ispravljen formatter-om samo u dva promenjena fajla. Potrebna je puna nova kvalifikacija pre tvrdnje da su uređajski neuspehi rešeni.

## Rezultat ispravke i čekanje stvarnog IME-a

[Source 7137675 / CI 43693506262](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43693506262) potvrđuje tablet API 26 **143 app + 13 data bez neuspeha**, uz ceo optimizovani smoke/jezički/runtime posao. Android 11 i 13 poslovi takođe prolaze. Phone API 26 ima **142/143 app i 13/13 data**: ranijih pet neuspeha svedeno je na jedan drugačiji neuspeh unutar InputDialogWindowTest, pri prvom Plain → Alert replacement-u. To nije puna kvalifikacija.

Originalni tablet artefakt 14153514971 ima SHA-256 **AD3824B9DD39BC84AF0FDD8A8146FF93B0A62A419AACA39649FFF9B7D51A64C4**. Phone `input-window-plain.png` ima SHA-256 **4E4543C12C0ED77761406E28470E46ABF28A7606CED85D09CD4985F9E94AD7DE**, njegov XML **18E56582FACC39860A5DB8C3E87C7B9FDC7B9A11E443E736EDB46020CA456018**, a istovremeni IME dump **0A31A65DFDA752DB48934BBC3F4FC13D0BA4CAABC6BB5532B86B403723FD8D47**. Sadržaj dijaloga i obe akcije su vidljivi; Child je fokusiran, ali IME je skriven, a servis još pokazuje Owner input connection i null root. Uzrok tog zasebnog prelaza nije potvrđen samim snimkom.

Test je posle fizičkog UiAutomator klika čekao IME preko SystemClock petlje, koja ne pokreće kontrolisani Compose sat. Input callback može zakazati Compose rad nakon prethodnog waitForIdle. Čekanje sada koristi [Compose waitUntil](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/ComposeUiTest#waitUntil(kotlin.String,kotlin.Long,kotlin.Function0)), koji pokreće taj sat i predaje vreme platformskom IME-u. Uslov ostaje stvarni mInputShown=true, rok ostaje 10 s, nema drugog klika, dodatnog show zahteva ili izostavljenog replacement-a. Ovo je opravdana sinhronizaciona kontrola; naredni uređajski rezultat treba da pokaže da li je dovoljna. Ako i dalje padne, test sada uz postojeće dokaze čuva ograničen WindowManager dump pre cleanup-a. Produkcioni kod ove kontrole ostaje identičan source-u 7137675; menja se samo test/diagnostic izvorni presek.

Lokalno prolaze kompilacija testova, stil i arhitektura: 116 task-ova / 45 s (`artifacts/dialog-clock-20261115-compile.log`). Ovaj rezultat nije izvršavanje novog čekanja na uređaju.
