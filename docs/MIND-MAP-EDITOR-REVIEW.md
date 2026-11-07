# F06 — pregled editora i granica poverenja

Izvor pregleda: 7f0fe831c34f9760ed3de94bd4fd43db1a7ef959, razvojna 0.8.0/code 17. Ovo je pregled izmena i ciljanih regresija; nova CI 42330852735 još nije završena. Rezultati starijeg 23463e6 nisu pripisani novim UI/R8 tokovima.

| Rizik | Kontrola i dokaz |
|---|---|
| Kasna komanda menja drugu tablu ili novu reviziju | Immutable request/page, session identity, generation i sharesSceneWith provera pre publikovanja; VM regresije za novu tablu, stale scenu i dismiss |
| Ponovljeni Apply potiskuje cancellation pravog worker-a | Busy guard pre dodele active job-a; poseban test drugog ignorisanog Apply-a pa dismiss-a |
| Brisanje skrivenih podataka bez jasnog obima | Zaseban pripremljeni BranchRemovalRequest sa brojem kanonskih čvorova/objekata i potvrdom; source 23463e6 izvršava 200% font/landscape/Cancel/Delete/Cut/Paste/Undo/Save/reopen na svih pet profila |
| Ciklus, strani roditelj ili oštećene reference | MindMapTree i čisti MindMapEdits proveravaju identitete, vlasništvo, reachability i managed krajeve; UI ne menja kolekcije direktno |
| Nepotpun Undo ili prekoračen budžet | Jedan validiran object+semantic EditCommand, budget pre primene; branch move, reparent i create regresije proveravaju identitete i vraćanje |
| Predugačak naslov ili nevažeće koordinate | UI i coordinator odbijaju naslov preko 20.000 UTF-16 jedinica/prazan naslov; unos se ne skraćuje i ne objavljuje delimično; move zahteva konačne koordinate, domen proverava world bounds |
| Nekontrolisan broj redova / duboka rekurzija | Postojeći Page/object/tree limiti, iterativni DFS sa cancellation checkpoint-ima; LazyColumn i ograničeno uvlačenje. Ovo ne dokazuje ukupnu memoriju ili latenciju svih 50.000-node scena |
| Curenje privatnog sadržaja u sistemski clipboard ili mrežu | Koristi se postojeći privatni memorijski ObjectClipboard; nema nove clipboard/mrežne integracije, dependency-ja, Android komponente ili dozvole |
| Skriveni sadržaj se crta/izvozi ili ostaje interaktivan | Zajednička Page vidljiva projekcija; source 23463e6 uključuje izvršene render/PNG/JPEG/PDF/native-gesture/framework-AX regresije. Novi R8 smoke proverava odsustvo potomka uz iste screen okvire predaka |
| Promena roditelja prekida konekciju pri rotaciji | Novi start/end portovi koriste Page geometry/port resolver; affine i facing-port domenski testovi. Nova Android matrica mora potvrditi integraciju |
| Uvoz zloupotrebljava UI metapodatke | UI zahtev nije serijalizovan dokument; format 6/delta 2 ostaju autoritativni, prethodno kvalifikovani limiti/token parser, storage mapiranje i immutable model ostaju aktivni |

Lokalno je potvrđeno 518 JVM slučajeva bez grešaka/preskakanja, stil, arhitektura, debug/release lint bez grešaka (dva ranije poznata upozorenja) i kompilacija 134 native app slučaja. Gitleaks 8.30.1 pregled dostupne Git istorije na 7f0fe83 obrađuje 120 commit-a bez prijavljenih tajni (redact=100); zapis je artifacts/security/task-16-7f0fe83-history.log/json. Jedina ranija uska prose-fingerprint iznimka ostaje dokumentovana u MIND-MAP-STORAGE-REVIEW.md; nije dodato novo izuzeće.

Digest-verified android-reports artefakt CI 42330852735 potvrđuje istih 518 JVM, OSV proveru 155 runtime Maven paketa bez prijavljenih poznatih ranjivosti, licence/notices i current-source scan 435 fajlova bez prijavljenih tajni. Release politika prolazi za unsigned APK E53FE87FE2C2116CA89FEF26EB565890F89B2E9AAA44F385DCDD41320BD68C05: offline dozvole/komponente, backup exclusions, cleartext off, scoped FileProvider i odsustvo online klijenata/WorkManager-a/debug leak klasa/kredencijal fajlova. artifacts/task-16-7f0fe83-evidence.json je u ovom trenutku delimičan (samo verify artefakt); puna Android/R8 matrica i tačan razvojni potpis/ABI/paket još nisu kvalifikovani.

Ovaj pregled ne zatvara opštu Z08/H memorijsku i performance kvalifikaciju, govorni TalkBack ili fizički OEM/stylus obim i nije nezavisan pentest. Paket 0.8.0 se ne priprema dok ne prođu svih šest poslova i tri nova optimizovana mind-map toka.
