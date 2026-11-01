# Whiteboard 0.4.1 — kandidat, build 5

Datum: 1. novembar 2026. Izvor je na grani [codex/local-engineering](https://github.com/cmarko-gits/Whiteboard-/tree/codex/local-engineering). Kandidat nije Play objava. Fizički telefon ostaje izvan ovog prolaza po izboru korisnika.

## Promene i regresioni dokazi

| Promena | Dokaz |
|---|---|
| Četiri imenovana Settings prekidača sa stanjem i klikom na red | SettingsAccessibilityTest 2/2, uključuje 200% font; stvarni TalkBack Settings/virtual note 2/2 u početnom ciljanom prolazu |
| Stanje omiljene table za accessibility servis | IconToggleButton umesto obične ikonice; prošireni stvarni TalkBack library tok je dodat, završni rezultat još se proverava |
| Tekst sticky beleške na tamnoj/srednjoj/svetloj boji | ExportInstrumentedTest proverava piksele stvarnog izvoza za pet kombinacija |
| Svetla/tamna tema i konture kontrola | ThemeContrastTest proverava stvarne ColorScheme parove; sa Settings/export testovima ukupno 5/5 u ciljanom prolazu |
| Vidljiv fokus tastature na različitim bojama table | Dvobojni okvir, stvarni View bitmap; CanvasAccessibilityTest 5/5, uključujući postojeće navigator/mutation/stale-node testove |
| ZIP ograničenja tokom raspakivanja i ograničeni nazivi unosa | Tri nova adversarial testa; format modul 36/36 |
| IME dijalog, fokus Done i stabilniji CI testovi | Popravke TextEditorDialog/NewBoardDialog i ciljane prethodne provere; novi objedinjeni OS run je u toku |
| Reproduktivniji CI | Zaključani action SHA, prenosivi source scan, automatski snimci greške; optimizovani release smoke dodat za API 26 i 36 |

Format dokumenta ostaje verzija 3, Room schema 4; migracije i stare compatibility tabele su sačuvane. Cloud, backend, AI i mrežna dozvola nisu dodati.

## Trenutni gates

Kotlin stil/arhitektura, 127 JVM testova (71 core + 36 format + 5 data + 15 ViewModel), debug/release lint, R8, APK i AAB build prolaze. Lint ima po 0 grešaka i 2 postojeća upozorenja. Dependency provera obuhvata 147 razrešenih runtime komponenti, bez prijavljenih OSV nalaza; licencni inventory i spakovani notices prolaze za 147/147. Source scan prolazi. Razvojni v2/v3 potpis, ZIP poravnanje i četiri 64-bit ELF LOAD/RELRO provere za 16 KB prolaze.

Na početnom 0.4.1 kandidatu, API 37.1 / 16 KB / 2 GB RAM: puna app regresija 52/52, data 13/13 i zasebna kvalifikacija 4/4 (Memory, Frame i dva RealTalkBack testa). Sa 127 JVM testova to je 196 jedinstvenih provera tog izvora. Prošireni TalkBack tok uključuje Settings, kreiranje/pretragu/favorite/Trash/restore/cancel/delete sopstvene table i virtuelni canvas objekat; nije slušna sertifikacija.

Završne ispravke dodaju dva HEX Done testa, eksplicitan fokus Apply/Cancel i isključuju platformsku fokusnu masku preko ručno iscrtanog platna. Pogođeni CanvasAccessibility/ColorPickerCompose/ColorPickerFlow testovi prolaze **14/14** na istom API 37.1 emulatoru. Završni optimizovani APK prolazi smoke test tastature, recovery-ja, Save/reopen i phone/tablet portrait/landscape rasporeda. Početna 0.4.0 → 0.4.1 nadogradnja sačuvala je prethodnu QA tablu i vidljiv potez; dokaz se odnosi na imenovanu testnu tablu, ne na poređenje svakog dokumenta bajt po bajt.

[CI 41308210320](https://github.com/cmarko-gits/Whiteboard-/actions/runs/41308210320) potvrdio je verify/API 30/API 33 i API 36, uključujući optimizovani release smoke na 36. Nalazi sa API 26 obrađeni su u commit-u 7d52bb2. Završni [CI 41310553861](https://github.com/cmarko-gits/Whiteboard-/actions/runs/41310553861), izvor 6823dd9, još se izvršava. Njegov broj redovnih app testova je 54; ne prikazivati sve kao prolaz pre rezultata i ne sabirati ciljna ponavljanja kao nove testove.

Memorijska kvalifikacija početnog kandidata posmatrala je 12 View instanci u šest ciklusa, LeakCanary assertion prolazi i završni heap ima 0 WhiteboardView instanci; privremeni dump od 38.964.749 bajtova obrisan je u testu. Frame workload od 1000 poteza / 100000 tačaka beleži 89 frejmova, p50/p95/p99 = 234,15/268,12/782,76 ms. To je debug emulator sa tracing-om, nije release/hardverska potvrda 60 Hz. Dokazi su task-09-profile-api37-app-tests.txt i task-09-qualification-*.json.

## Paketi i ograničenja

Razvojni APK: artifacts/whiteboard-local-0.4.1-preview.apk, 1.928.126 bajtova, SHA-256 `98B4F703ABF065299B05F9C17F7BA9FE7368BBED060F6E848E63C3A0C8BF7485`. Unsigned AAB: artifacts/whiteboard-local-0.4.1-unsigned.aab, SHA-256 `5E42531B2056896A22042E5B385E9B40879058E35256DE96A4DFCE1B7384A020`. Razvojni potpis nije vlasnički Play potpis. Postojanje paketa nije potvrda otvorenih OS/accessibility/hardware stavki.

Početni APK/AAB su sačuvani sa rc1 u nazivu. Njihov stari snapshot hash-eva je task-09-rc1-packages.json, a novi paket ima zaseban hash. Ne mešati stare i završne UI rezultate.

Stari [LOCAL-STATUS.md](LOCAL-STATUS.md) čuva 0.4.0 rezultate. Aktuelni otvoreni rad prati [EXECUTION-PROGRESS.md](EXECUTION-PROGRESS.md), a potpuni obim [LOCAL-REMAINING-TASKS.md](LOCAL-REMAINING-TASKS.md). Pregled i granice bezbednosti su u [LOCAL-ENGINEERING-REVIEW.md](LOCAL-ENGINEERING-REVIEW.md). Korisničko uputstvo: [LOCAL-USER-GUIDE.md](LOCAL-USER-GUIDE.md).

Ostaju puna TalkBack/govorna pokrivenost, fizički GPU/stylus/OEM/foldable i low-RAM provere prema odloženoj hardverskoj grupi. Emulator nije potvrda 60 Hz ili rada na svakom Android uređaju. Dodatne funkcije iz grupe F/O nisu predstavljene kao završene.
