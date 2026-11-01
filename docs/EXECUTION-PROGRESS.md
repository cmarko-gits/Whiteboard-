# Izvršavanje preostalih zadataka

Datum: 1. novembar 2026. Brojevi odgovaraju korisniku prikazanom spisku od 47 stavki i ID-jevima iz LOCAL-REMAINING-TASKS.md. Na početku svake stavke navesti broj. Nastaviti na sledeću izvodljivu stavku kada zavisnost nedostaje; ne označavati neizvršeno kao završeno.

| Broj | ID | Stanje | Dokaz / zavisnost |
|---|---|---|---|
| 1 | Z01 | Završeno | Pronađen i uklonjen preostali QA RAM snapshot od 3,22 GB; QuickbootFileBacked isključen. Stvarni API 36 boot bez RAM fajla, završno posmatrano dodatno zauzeće najviše 1,65 GB. task-01-*.json. |
| 2 | Z02 | U toku | Provereni ignore obrasci i Git identitet; source-secret scan 275 fajlova bez prijavljenih tajni. Priprema lokalne početne verzije. |
| 3 | Z03 | Čeka cilj repozitorijuma | Nema konfigurisanog Git remote-a; pripremljeni CI još nije izvršen na udaljenom servisu. |
| 4 | Z04 | Omogućeno za postojeće slike | API 36 je uspešno pokrenut nakon ispravke zadatka 1; testovi ove matrice nisu time automatski prošli. API 26/30/33 slike nisu lokalno dostupne. |

Postojeći dokazi za 0.4 ostaju u LOCAL-STATUS.md i artifacts/local-verification.json. Izveštaj o ranijem prekoračenju prostora ostaje sačuvan; pronađeni QA RAM fajl daje konkretan novi uzrok zauzeća, umesto ranije nepotvrđene pretpostavke o pagefile-u. Opcije O, hardver H i javna distribucija P zadržavaju prethodno definisan status; nalog da se nastavi rad ne znači da je fizički telefon ponovo zahtevan ili da je odobrena Play objava.

Pokretanje emulatora bez RAM snapshot backing fajla oslanja se na [Android emulator CLI](https://developer.android.com/studio/run/emulator-commandline) i [AOSP QuickbootFileBacked podešavanje](https://android.googlesource.com/platform/prebuilts/android-emulator/+/master/linux-x86_64/lib/advancedFeatures.ini). Uspeh je dodatno proveren na instaliranom emulatoru.
