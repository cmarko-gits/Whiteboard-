# F06 — potvrda brisanja i isecanja grane

Razvojni UI podkorak posle 6b53d42. Novi mind-map template i puni node editor još nisu aktivirani; ova zaštita važi i za uvezene dokumente sa postojećom semantikom. F06 ostaje otvoren; 13/47 završeno i 34 preostala glavna zadatka.

BranchRemovalController odvojeno poseduje pripremu/životni vek potvrde. Na computation dispatcher-u priprema tačan MindMapEditPlan, broj uklonjenih kanonskih objekata/čvorova (sa skrivenim potomcima) i, za Cut, kompletan PageFragment. Pre objave potvrde proverava owner session, semantic/geometry snapshot i generation. UI potvrđuje ID te pripremljene operacije; ne čita ponovo trenutno izabrane ID-jeve.

Delete iz selection menija, toolbar-a, canvas/tastature i accessibility putanja za managed node/edge otvara isti EN/sr-Latn dijalog. Cut grane takođe traži istu konkretnu potvrdu. Obični nemapirani objekti zadržavaju postojeće ponašanje. Cancel/dismiss ili promena table otkazuju worker i ne menjaju dokument/history/clipboard. Stara potvrda nakon izmene scene ne može obrisati novo stanje. Nezavisna kamera ne menja značenje pripremljene grane.

Confirm objavljuje jedan atomski semantic/object Undo, zatim postavlja pripremljeni privatni clipboard ako je u pitanju Cut. Ponovljena potvrda istog ID-ja ne pravi drugi edit. Kasnija promena selekcije ne sme isecati tu novu selekciju. Zatvaranje/reotvaranje aplikacije bez potvrde ne objavljuje brisanje; pending operacija je memorijska, a originalni main/draft ostaje autoritativan.

Lokalno prolazi 500 JVM (350 core + 77 format + 5 data + 68 app), bez grešaka/preskakanja, uz arhitekturu i Android kompilaciju. Šest novih VM slučajeva proverava tačan count/confirmation/Undo/kameru, Cancel, zastarelu scenu, managed-edge branch izbor/novu tablu, cancellation i odbijanje generic Edit/Detach nad managed vezom. Raniji cut slučajevi sada izričito potvrđuju pripremljenu operaciju. Generic connection editor dobija jasan EN/sr-Latn razlog odbijanja; ne može prekinuti parent relation.

Novi MindMapBranchRemovalFlowTest je kompajliran (132 ukupna app native slučaja); stvarno izvršavanje tek sledi. Tok obuhvata Cancel i Delete/Cut potvrdu sa skrivenim detetom, 200% sistemski font, landscape/recreation, Undo/Redo, private Paste, Save i reopen sa istim pripremljenim identitetima. Font/rotation se vraćaju u cleanup-u; tekst dijaloga je skrolabilan, potvrda ostaje odvojena. Dokazi: task-16-branch-removal-native-local.log i task-16-branch-removal-responsive-local.log. Prolaz ranijeg storage/command izvora ili clipboard matrice ne potvrđuje ovaj dijalog.
