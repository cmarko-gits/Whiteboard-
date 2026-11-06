# F06 — domenske komande grana

Razvojni 0.8.0 podkorak, bez aktiviranog template-a ili novog korisničkog dijaloga. F06 ostaje otvoren, 13/47 završeno i 34 preostala glavna zadatka. Kvalifikovani paket ostaje 0.7.2.

MindMapEdits priprema čist, otkaziv plan nad nepromenljivom Page scenom. DocumentSession ponovo proverava geometrijski i semantički identitet pre objave jedne izmene; nezavisna promena kamere ostaje dozvoljena. Zastareo plan ne menja dokument. MindMapChange čuva odbrambene, nepromenljive before/after liste i ulazi u postojeći history budžet zajedno sa objektima. Prevelika izmena se odbija pre objave.

Komande obuhvataju novu mapu, dete, sledećeg sibling-a, naslov/boju, collapse/expand, izričito reveal, reparent i brisanje grana. Novi čvor dobija slobodno mesto bez pomeranja postojećih objekata; dete nasleđuje boju/font roditelja. Rotirani Note zadržava world placement pri uređivanju sadržaja. Dodavanje deteta otvara potrebne pretke. Uređivanje skrivenog naslova samo po sebi ne otvara granu.

Reparent istovremeno menja parent referencu i početak managed Connector-a; ne pomera čvor i ne menja identitete/stil veze. Odbijaju se root premeštanje, ciklusi i roditelj iz druge mape. Sibling korena nije dozvoljen. Collapse-only istorija deli istu kanonsku geometriju. No-op ne pravi Undo korak.

Brisanje uključuje skrivene potomke i njihove managed veze, normalizuje preklopljene selekcije jednim obilaskom zahvaćenih stabala i uklanja metapodatke cele mape kada je obrisan root. Izbor managed veze označava njenu child granu. Obične spoljne veze preživljavaju sa odvezanim krajem na poslednjoj world poziciji. Sve je jedan Undo/Redo korak, uključujući više mapa i obične objekte. Grupisanje managed objekata je odbijeno da semantičke reference ne postanu group children.

Lokalna regresija: 461 JVM (330 core, 77 format, 5 data, 49 app), bez grešaka/preskakanja; 16 novih command slučajeva proverava i budžet, otkazivanje, neispravan identity generator, stale scene, kameru i osam konkurentno pripremljenih planova. Izvor testova koristi postojeći updateViewport API; prvi compile neuspeh zbog pogrešnog naziva test poziva je sačuvan u artifacts/task-16-command-regression-local.log. Uspešan tok: task-16-command-regression-2-local.log.

Pre korisničkog izlaganja ostaju zajednička vidljivost renderer/input/AX/export/Fit, branch-aware clipboard, bezbedna potvrda branch delete, ViewModel lifecycle/cancellation, adaptivni lokalizovani dijalozi/template i native/R8 tokovi. Sam prolaz domenskih testova ne zatvara F06.
