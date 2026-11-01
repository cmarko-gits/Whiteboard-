# Pregled aktivne lokalne aplikacije — 1. novembar 2026.

Ovaj dokument dopunjuje LOCAL-SECURITY.md i EXECUTION-PROGRESS.md. Pregled odnosi se na Kotlin/Android lokalni kod, bez vraćanja backend-a, cloud-a ili AI-a. Nije nezavisan pentest ili potvrda potpune pristupačnosti.

## Bezbednosni pregled, zadaci 7–8

| Granica | Pregled i nalaz |
|---|---|
| Spoljni Intent-i | MainActivity je launcher; ne dekodira spoljne URI-je ili proizvoljne dokumente iz Intent dodataka. FileProvider je privatan, sa privremenim read grant-om samo za izričito deljenje. |
| URI i privatne putanje | DocumentTransfers prihvata izabrane content URI-je sa authority i ograničenom dužinom. Media ID ima zatvoren obrazac naziva. Preview proverava canonical privatni direktorijum, format i dužinu pre i tokom čitanja. |
| ZIP i JSON | Nema ekstrakcije arhivskih putanja na disk ili reflektivne deserijalizacije. JSON preflight ograničava dubinu, numeričke literale i ukupnu dužinu. ZIP proverava dozvoljene unose, duplikate, broj unosa, pojedinačnu veličinu, ukupni sadržaj i reference priloga. |
| Novi nalaz u ZIP parseru | Ukupan limit ranije se proveravao posle alokacije sledećeg kompletnog unosa. Provera je pomerena u petlju čitanja. Numerički board nazivi ranije su prihvatali proizvoljno mnogo cifara; sada su ograničeni na četiri, uz postojeći maksimum 1000 tabli. Novi testovi koriste stvarno kompresovane prekoračene unose i naziv od 60.000 cifara. Sva tri prolaze, format modul ukupno 36/36; artifacts/task-08-archive-build.log i JUnit XML. |
| SQL i promene podataka | Aktivni Room DAO koristi vezane parametre; search escapuje LIKE metaznakove. Dinamični SQL u migracijama koristi interne konstantne tabele/kolone. Save/import su transakcioni; nema destructive migration fallback-a. Restore pravi nove ID-jeve i čisti sopstvene nepotvrđene priloge pri grešci. |
| Slike i PDF | Ulaz se kopira uz limit 16 MiB; slike se subsampluju, PDF ograničava broj stranica i veličinu renderovanja. Nativni dekoderi zavise od zakrpa Android OS-a. Kamera i SAF/OEM provider na fizičkom uređaju ostaju odložene provere. |
| Privatnost i dozvole | Manifest isključuje automatski backup/transfer i cleartext. Release policy prethodnog 0.4 APK-a i udaljenog CI paketa prolazi bez INTERNET dozvole. Za nove izmene ponoviti release gate pre isporuke; stari hash nije dokaz novog paketa. |
| Dijagnostika | LeakCanary/qualification kod je odvojen od release grafa. UI failure snimci sadrže generisane testne podatke; CI ih čuva kroz additionalTestOutputDir. Privatni heap i lokalni kredencijali se ne šalju. |
| Izvor i Git istorija | Tekući source scan prolazi i bez rg, uz Git fallback. Gitleaks istorijski prolaz prijavio je 6 skeniranih commit-a i nijednu tajnu; dokaz artifacts/task-07-history-secrets.log/json. To ne obuhvata ignorisane lokalne fajlove ili sve spoljne naloge. |
| Preostala ograničenja | Nema enkripcije pojedinačnih tabli/PIN-a. Otključan ili kompromitovan OS nije zaštićen aplikacionim sandbox-om. Ne tvrditi da su poznate i nepoznate ranjivosti u svim build alatima, nativnom OS-u i OEM softveru proverene. |

## Pristupačnost i kontrast, zadaci 5–6

Stvarni Settings pregled našao je četiri neimenovana clickable/checkable elementa. Oznaka i prekidač sada su jedna semantička kontrola sa stanjem i celom površinom reda kao touch target-om. Dva Compose testa proveravaju imenovani Switch, minimalno 48dp, dodir na oznaku i dugačku oznaku pri font skali 200%.

ThemeContrastTest proverava stvarne ColorScheme vrednosti svetle i tamne teme: tekstualne parove i konture kontrola na korišćenim površinama. Konture su pojačane. BoardRenderer bira čitljiv tekst sticky beleške, zadržavajući postojeću toplu tamnu boju kada ima dovoljan kontrast, inače crnu ili belu. Slobodno izabrane boje TextElement objekata se ne menjaju.

Referentni prag za automatski tekst je [WCAG 1.4.3, 4,5:1](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html). Za transparentnu belešku računanje koristi boju stranice; ne predstavlja garanciju kontrasta preko proizvoljne fotografije/drugog objekta ispod nje. Zasebni bitmap test proverava pet stvarno iscrtanih kombinacija u izvozu, uključujući transparentne beleške na jednobojnim stranicama. Ovo nije sertifikacija svake moguće korisničke boje ili proizvoljnog sadržaja.

Dokaz: artifacts/task-06-accessibility-contrast-api37-app-tests.txt — 5/5 ciljanih testova. Stari 0.4 release APK još ne sadrži ove izmene. Novi RealTalkBackTest proverava imenovanje, fokus i promenu stanja četiri prekidača uz uključeni pravi servis; rezultat je zaseban od običnih Compose testova. Slušni pregled izgovora i kompletni spoljni sistemski tokovi ostaju otvoreni.

Okvir fokusa tastature koristi crni spoljašnji i beli unutrašnji potez. Stvarni View bitmap na beloj, crnoj i zelenoj stranici ima oba dela okvira; fokus se dobija kroz povezani AccessibilityNodeInfo. CanvasAccessibilityTest, uključujući postojeće mutacije/navigator/stare čvorove, prolazi 5/5: artifacts/task-06-keyboard-api37-app-tests.txt. Ovo nije merenje odziva na fizičku tastaturu.
