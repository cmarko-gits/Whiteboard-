# Whiteboard 0.5.1 — lokalni razvojni paket

Datum: 1. novembar 2026. Version code 7; izvor `550bd93fdafe1503660ff2f0179b77af3e53667b`, grana codex/partial-eraser. [CI 41341043323](https://github.com/cmarko-gits/Whiteboard-/actions/runs/41341043323) prolazi verify i svih pet uređajskih poslova. Ovo je razvojni paket za lokalnu proveru; fizički uređaji i javna distribucija ostaju odloženi.

## Šta je promenjeno

Settings → Eraser bira Whole stroke ili Partial stroke. Parcijalna gumica seče potez celom putanjom gesta, zadržava stil preostalih delova i podržava jedan Undo/Redo po gestu. Podešavanje se pamti, a otkazivanje gesta ne menja dokument. Prevelik rez se odbija pre upisa. Detalji i kriterijumi: [PARTIAL-ERASER.md](PARTIAL-ERASER.md).

Biblioteka čuva čitljiv prostor za naziv table u kratkom prozoru i pri povećanom fontu. Dijalozi boja zadržavaju isti sastav kontrola pri pojavljivanju tastature; Done vraća fokus na Apply/Cancel. Dijalozi i biblioteka zatvaraju svoju aktivnu sesiju unosa pre navigacije ili potvrde. Nova tabla, HEX, uređivanje, Objects i recovery ostaju u regresionoj matrici.

JSON dokumenta ostaje verzija 3, Room šema 4. Rotacija 0.6.0 i format 4 nisu uključeni. AI, backend, cloud, nalozi i mrežne dozvole nisu dodati.

## Provereni rezultati istog izvora

| Provera | Rezultat |
|---|---|
| JVM | 151/151: core 95, format 36, data 5, ViewModel 15 |
| API 26 telefon | app 66/66 + data 13/13 |
| API 30 telefon | app 66/66 + data 13/13 |
| API 33 telefon | app 66/66 + data 13/13 |
| API 36 telefon | app 66/66 + data 13/13 |
| API 26 tablet | app 66/66 + data 13/13; profil pixel_c, 1280 × 900 dp |
| Minified release | Smoke prolazi na API 26 telefonu/tabletu i API 36 telefonu; na API 36 uključuje runtime promenu phone/tablet i portrait/landscape rasporeda |
| Build gates | Kotlin stil/arhitektura, debug/release lint, R8, APK i AAB build |
| Zavisnosti/licence | 147 runtime Maven komponenti, 0 prijavljenih OSV nalaza; metadata i notices 147/147 |
| Paket | Manifest/DEX politika i source secret scan prolaze; lokalni razvojni v2/v3 potpis, ZIP-P16 i četiri ELF LOAD/RELRO provere prolaze |

Svaki pregledani device XML ima 0 failures/errors/skipped. Ponavljanja na pet konfiguracija nisu dodatni jedinstveni testovi: JVM/app/data kohorta ima 230 jedinstvenih slučajeva. Smoke tokovi su odvojeni. Redovni testovi ne izvršavaju posebnu Memory/Frame/RealTalkBack kvalifikaciju.

## Paketi

| Datoteka | SHA-256 |
|---|---|
| artifacts/whiteboard-local-0.5.1-preview.apk | `62C64CCF35F0DE70DB76C2A3508A9BBEF231D2AFE7029EF3B239A56875B8DBDB` |
| artifacts/whiteboard-local-0.5.1-unsigned.aab | `0480CDA70EB51DE71720DEBFBE4B20B08A45A5D3D2E2B0D99584EF70F504496C` |

APK koristi postojeći Android Debug sertifikat `e010bd619292802caf896b112bf702b24a6727b17eac687f2c43a33b0c6d91da`, u kontinuitetu sa prethodnim lokalnim paketima. AAB nema produkcioni potpis. Potpuna evidencija, veličine paketa, XML putanje i trenutni prostor: `artifacts/task-11-family-packages.json`. Verify log: `artifacts/task-11-family-verify.log`. Native/paket izveštaji: `artifacts/security/task-11-family-*.json`.

## Otvorena ograničenja

Nisu potvrđeni svi OEM uređaji, stvarna olovka/dlan, foldable hardver, slušni izlaz TalkBack-a, in-place OS upgrade ili release 60 Hz na fizičkom uređaju. Raniji debug emulator profil i njegova velika vremena frejmova ostaju nalaz za buduće merenje; nisu performansna potvrda ovog paketa. Ranije lokalno prekoračenje limita prostora ostaje evidentirano u task-08-storage-incident.json.

Izvoz/backup ograničenja, recovery prozor i gubitak privatnih podataka pri deinstalaciji opisani su u [uputstvu](LOCAL-USER-GUIDE.md). Izvršene bezbednosne provere nisu nezavisan pentest. Kompletna preostala funkcionalnost nalazi se u [spisku zadataka](LOCAL-REMAINING-TASKS.md).
