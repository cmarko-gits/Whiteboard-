# Izvršavanje preostalih zadataka

Datum: 1. novembar 2026. Brojevi odgovaraju korisniku prikazanom spisku od 47 stavki i ID-jevima iz LOCAL-REMAINING-TASKS.md. Na početku svake stavke navesti broj. Nastaviti na sledeću izvodljivu stavku kada zavisnost nedostaje; ne označavati neizvršeno kao završeno.

| Broj | ID | Stanje | Dokaz / zavisnost |
|---|---|---|---|
| 1 | Z01 | Završeno | Pronađen i uklonjen preostali QA RAM snapshot od 3,22 GB; QuickbootFileBacked isključen. Stvarni API 36 boot bez RAM fajla, završno posmatrano dodatno zauzeće najviše 1,65 GB. task-01-*.json. |
| 2 | Z02 | Završeno | Commit 0e9d1d0, grana codex/local-engineering. Provereni ignore obrasci, Git identitet i source-secret scan; gradlew executable bit. |
| 3 | Z03 | U toku | Korisnik je dao https://github.com/cmarko-gits/Whiteboard-.git; origin povezan. Remote main ima samo README i početni commit 1a9c5fe; istorija se čuva. |
| 4 | Z04 | U toku | API 36 data 13/13. Prvi app prolaz 45/47; ispravljena jednokratna provera onboarding-a u testu. Ciljani color flow ponovljen 1/1. Puna regresija se ponavlja i sa privremeno vraćenim first-run stanjem samo u novom QA emulatoru. |

Postojeći dokazi za 0.4 ostaju u LOCAL-STATUS.md i artifacts/local-verification.json. Izveštaj o ranijem prekoračenju prostora ostaje sačuvan; pronađeni QA RAM fajl daje konkretan novi uzrok zauzeća, umesto ranije nepotvrđene pretpostavke o pagefile-u. Opcije O, hardver H i javna distribucija P zadržavaju prethodno definisan status; nalog da se nastavi rad ne znači da je fizički telefon ponovo zahtevan ili da je odobrena Play objava.

Pokretanje emulatora bez RAM snapshot backing fajla oslanja se na [Android emulator CLI](https://developer.android.com/studio/run/emulator-commandline) i [AOSP QuickbootFileBacked podešavanje](https://android.googlesource.com/platform/prebuilts/android-emulator/+/master/linux-x86_64/lib/advancedFeatures.ini). Uspeh je dodatno proveren na instaliranom emulatoru.
