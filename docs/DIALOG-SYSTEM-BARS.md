# Prikaz dijaloga — kandidat 0.8.1

Radni kandidat je **0.8.1/code 18**; dokument 6/delta 2/Room 4 i offline opseg ostaju isti. Poslednji kompletno kvalifikovani i sačuvani paket i dalje je [0.8.0/source 36cd15a](LOCAL-0.8.0-CANDIDATE.md). Njegovi stari APK/AAB fajlovi nisu prepisani niti proglašeni paketima ove izmene.

## Konkretan nalaz

[CI 43688093688](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43688093688), tooling c5e52e130ce00ffba7fde9abe08bbc2bd8d1da62 i originalni aplikacioni source 36cd15a, prolazi samostalni theme-capture profil na API 36 Google Play sa stvarnim PAGE_SIZE 16384. Sve četiri scene u četiri theme/system-night kombinacije i dva font scale-a daju **32 originalna PNG-a**. Početni theme/font/night su vraćeni; keyboard smoke nije deo tog profila.

Artefakt 14150489795 ima SHA-256 **3C6B23F8589B831A791196D2A7AC92244619C3FF4338BAB1E9FD929B7D92979F**. Svih 32 snimka su digest-proverena i pregledana, a pregled je našao problem statusne trake u tamnom color picker-u pri fontu 200%:

- `dark-forced-font-20-color-picker.png`: SHA-256 **9F5DBCF5225D8090F328E3962B80DC8D4D7D7C8FB82C51C906BCC36859194C03**.
- `system-dark-font-20-color-picker.png`: SHA-256 **BA4C6B09F20EA9736127C86FC19DCC0077C5BED760A226D46533E54452AD29F4**.

Vreme i sistemske ikone su tamni na tamnoj pozadini. Kontrole samog portretnog dijaloga ostaju čitljive. Font 200% ima scroll viewport-e, prelomljene settings opcije i skraćene nazive tabli; pregled ne tvrdi da svaka kontrola staje odjednom. Ovo nije merenje svih kontrastnih odnosa ili odobrenje svih jezika i dijaloga.

## Ispravka i regresija

Prethodni WhiteboardTheme podešava samo Activity Window. Compose dijalog poseduje zaseban Window preko [DialogWindowProvider](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogWindowProvider); promena vlasničkog Activity-ja sama ne postavlja appearance zastavice tog prozora. Ispravka prenosi izabranu Light/Dark/System odluku kroz CompositionLocal i u InputDialog/InputAlertDialog primenjuje ikone na stvarni prozor njihovog sadržaja. Koristi postojeći [WindowInsetsControllerCompat](https://developer.android.com/reference/androidx/core/view/WindowInsetsControllerCompat), bez novog SDK-a, dozvole ili globalnog WindowManager pristupa.

Dva nova DialogAppearanceTest slučaja proveravaju plain i alert dijalog: promena same teme mora ažurirati zadržani Window, dok font 100/200% sme da ga ponovo kreira; oba puta njegove ikone moraju odgovarati temi. [Tačan Compose UI 1.10.1 izvor](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.10.1/ui-android-1.10.1-sources.jar) koristi `remember(view, density)` za DialogWrapper. Zato je prestroga prvobitna asercija iste instance i tokom promene fonta ispravljena, a nedovršeni CI 43689778057/source e8fb920 je superseded novim izvornim stanjem, ne kvalifikovan kao prolaz. Postojeći IME testovi zadržavaju svoja očekivanja.

## HEX polje pri maloj preostaloj visini

Uspešan R8 input kontrolni [CI 43689937504](https://github.com/cmarko-gits/Whiteboard-/actions/runs/43689937504), isti originalni source 36cd15a i 4 GB udaljeni VM, potvrđuje funkcionalan HEX/landscape Apply tok. Njegov originalni `release-color-keyboard-landscape.png`, SHA-256 **3C90A5EB2FA8882EF856983E1011D7C51B073926AF7300B5D7F063CE63B91C0E**, ipak pokazuje odsečen donji deo HEX teksta. Funkcionalni prolaz zato nije predstavljen kao vizuelni prolaz.

Kompaktni prikaz je trošio po 16 dp spoljnog i unutrašnjeg vertikalnog razmaka. AdaptiveDialogSurface sada u tom režimu ostavlja prirodnu visinu polju i akcijama, dok običan prikaz zadržava razmake. Input i action roditelji ostaju stabilni tokom promene IME insets-a; ne menjaju se tip polja, HEX validacija, čuvanje ili upravljanje tastaturom.

Novi ColorDialogLayoutTest ograničava stvarni zajednički surface/layout na 82 dp visine i zahteva da HEX polje zadrži najmanje 56 dp i tačan tekst, uz vidljive Cancel i Apply akcije. Sa dva nova testa prozora ukupan novi redovni inventar je **143 app + 13 data**, uz 519 JVM i dve odvojene memory/frame dijagnostike.

Lokalno prolaze Android test kompilacija, stil i arhitektura: 116 task-ova, završni prolaz 14 s u `artifacts/dialog-compact-20261115-compile-fixed.log`; raniji density-test prolaz traje 48 s. Prvi poziv bez JAVA_HOME nije pokrenuo build. Style prijave novih testova i indentacija refaktorisanog color-picker bloka ispravljene su postojećim formatter-om, ograničenim na promenjeni fajl. Ovo nije izvršavanje na uređaju. Puna nova matrica sa 143 app testa i novi R8 vizuelni dokaz potrebni su pre kvalifikacije ili novog distribucionog paketa. Prethodni CI 43690271484/source 2202a0d ima zaseban 142-testni opseg; njegov delimičan rezultat nije završna kvalifikacija ove dodatne ispravke.

Ostali otvoreni nalazi iz [dodatne kvalifikacije](ADDITIONAL-QUALIFICATION.md), govorni TalkBack, fizičko profilisanje i nezavisna kopija ključa ostaju u svom opsegu. Play predaja nije izvršena.
