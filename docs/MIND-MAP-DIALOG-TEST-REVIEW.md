# F06 — pregled neuspeha native selektora potvrde

d2f18034e98a4dba9cbc5527e64a29a8871f7834 / CI 42185382992: verify prolazi 500 JVM i prateće provere. Novi MindMapBranchRemovalFlowTest pada pre potvrde na pronalaženju Delete branch kontrole. Ovo nije isti nalaz kao 6b53d42/API 36 startup ANR; ovde je emulator podignut i aplikacija izvršena. Izvor nije kvalifikovan niti retry-ovan kao da nema test neuspeha.

Pregledan je stvarni API 36 PNG pre cleanup-a, sa originalnim artifact/entry SHA proverama: mind-map-branch-removal-before-cleanup.png, entry SHA-256 B97786E577A9C0BD32C6793ABF56F667D0C10A2ACB7B5D52A05C13A771B6D6CE; XML 3B6B4E63C504B5BAF5419705727E6CBA048DBFBA3BAC397D1D3AD11F59C16F90. Na njemu su vidljivi modalni naslov Delete branch, Nodes: 2 / Objects: 3, Cancel i Delete branch potvrda. Dokument iza dijaloga još je Saved, sa jednom vidljivom root beleškom. Podaci nisu obrisani.

Native test je pogrešno zahtevao android.widget.Button sa direktnim text atributom. Actual Compose hijerarhija ima dva TextView-a sa Delete branch tekstom (naslov i labelu), a samo labela ima enabled/clickable android.view.View roditelja. Cancel je isti tip. Selektor sada obilazi sve podudarne labele i ograničeni lanac predaka, zahteva klikabilnost, enabled stanje i neprazan vidljivi okvir. Zastareli handle nakon rekompozicije se odbacuje i čita nova hijerarhija. Nema klika po naslovu, koordinatnog zaobilaženja, većeg test timeout-a ili preskakanja potvrde.

Na sačuvanom connected XML-u taj izbor nalazi tačno jednu omogućenu klikabilnu Delete branch akciju (dve tekstualne labele) i jednu Cancel akciju. To je pregled selektora nad stvarnim dokazom, ne izvršen Android test novog izvora. Nova puna matrica ostaje obavezna.

TestWatcher snimak failure-hiddenBranch… nastaje posle @After cleanup-a i prikazuje biblioteku. On nije korišćen kao dokaz stanja pada. Dodati su tačni mind-map pre-cleanup PNG/XML ulazi u lokalni bounded collector, sa istim ograničenjem po unosu/ukupnom ZIP-u i SHA proverom. Oba snimka ostaju sačuvana, kao i neuspešni XML/log.

## Zaseban API 26 phone slučaj

Na svih pet profila novi branch slučaj pada na istom class-selector uslovu. API 26 phone ima još jedan pad: AppLanguageFlowTest čeka zatvaranje tastature posle srpskog New board Confirm-a. Pregledani language-before-cleanup PNG (SHA-256 5BE84AE539736E081FAF836F025332364692FC7E32E8232A10C2A1E1EDD0BBFB) i XML (281ED663DD7E7868F37CFB73F3045B57F38A97BBB1FAA3210EEEBC9E13AADEA5) pokazuju da je New board dijalog i dalje otvoren sa unetim nazivom. Ovo nije dokaz da je tastatura ostala preko već otvorenog editora. Nalaz je saglasan sa ranim fizičkim tap-om po labeli pre enabled rekompozicije, ali screenshot ne dokazuje tačan vremenski red.

Taj fixture sada koristi postojeći clickTestDialogText: svežu connected hijerarhiju i enabled klikabilnog roditelja. Najpre zahteva da se stvarni editor pojavi, pa zatim zadržava awaitTestKeyboardHidden proveru. Nema Back komande, skrivanja tastature iz testa, proširenja timeout-a ili uklanjanja IME asercije. Novi source mora izvršiti oba toka ponovo. D2f1803 neuspeh na svih pet profila ostaje sačuvan.
