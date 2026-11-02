# Z08 — pregled završnog uzorka gesta

Stanje 2. novembra 2026: ispravka 0.6.1/code 9 izvršena je na CI uređajima. Run 41501328904, source 490b764, prolazi verify i četiri profila (API 26 telefon/tablet, 30 i 33). API 36 izvršava svih 84 app testova, sa jednim neuspešnim testom preciznosti; puna kvalifikacija zato ostaje otvorena. Rotacija je uključena u ovu regresiju.

Pre ispravke `WhiteboardView.handleTouch` obrađivao je završnu ACTION_UP koordinatu za gumicu i Fill, dok su četkice završavale putanju bez poslednje koordinate, a oblik potvrđivao prethodni MOVE preview. Pri retkim događajima ili promeni položaja između poslednjeg MOVE i UP završetak je mogao odstupati od mesta podizanja prsta/olovke. ADB smoke snimak linije imao je kraći završetak od zadate krajnje tačke; to je trag za proveru, a ne merenje fizičke olovke.

Potrebno je pre završne komande uključiti validan završni uzorak za alate crtanja i konačnu geometriju oblika, uz postojeća pravila minimalnog gesta, page/tool generation, pinch/cancel i ograničenja koordinata. ACTION_CANCEL ne sme potvrditi sadržaj; prekid stylus pokazivača takođe treba proveriti. Izmena ne sme dodati duplu ili slučajnu tačku posle odbijenog/otkazanog gesta.

Implementacija sada obrađuje ACTION_UP i završni ACTION_POINTER_UP aktivne olovke. Poslednja različita tačka se čuva i ispod praga za redukciju uzoraka; tap zadržava jednu tačku, a postojeći limit od dva miliona uzoraka se ne povećava. Oblik se računa iz konačne koordinate. Minimalna dužina linije proverava se preko njenih krajnjih tačaka, pa tanka horizontalna linija nije odbačena zbog male debljine. Render granice se proveravaju pre Add događaja; neispravan završetak odbija ceo gest.

Napisano je deset native MotionEvent regresionih testova: četiri vrste četkice, finalni pomeraj/subpixel, brz gest bez MOVE, oblici/linije, tap, tanka linija, cancel/page/tool promene, dva prsta, stylus pointer-up i odbijanje koordinata bez delimičnog upisa. Lokalni ktlint/diff provera prolaze; puna Android regresija ostaje uslov zatvaranja nalaza.

API 36 nalaz: očekivan unos (10.2, 20.1), sačuvan kraj (10.200195, 20.09961). Test sada čita stvarne event.x/event.y posle kreiranja MotionEvent-a, proverava odstupanje od nameravanog unosa do 0.001 i zahteva potpuno jednaku sačuvanu koordinatu dostavljenom događaju, kao i tačno dve tačke. Nema tolerancije koja bi dozvolila aplikaciji da odbaci završetak. Ovo je korekcija test orakla; produkcijski kod gesta nije menjan. Log prvog neuspeha ostaje u artifacts/task-08-490b764-api36.log.

Regresija treba da koristi stvarne MotionEvent događaje sa različitim poslednjim MOVE i UP, kao i DOWN/UP bez MOVE. Proveriti tačan kraj poteza/oblika, otkazivanje, promenu alata/stranice i odbijanje koordinata bez delimičnog upisa. Zatim izvršiti pogođene domenske/canvas testove i redovnu Android matricu. Ovo je zaseban nalaz od F02 rotacije.
