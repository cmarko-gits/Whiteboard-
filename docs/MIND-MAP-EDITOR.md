# F06 — editor čvorova i semantički šablon

> F06 je završen u lokalnom softverskom/emulatorskom opsegu: source 36cd15a / CI 43008205011, svih šest poslova bez retry-ja. 519 JVM, pet profila po 140 app + 13 data, tri R8/jezička/runtime toka, upgrade, adaptive/split-screen i dve odvojene dijagnostike prolaze. [Kvalifikovani 0.8.0 paket](LOCAL-0.8.0-CANDIDATE.md) ima preview APK i provereni owner-upload AAB. Prvi neuspešni 7f0fe83 i kasniji IME/fixture/startup nalazi ostaju u [pregledu](MIND-MAP-EDITOR-CI-REVIEW.md) i RELEASE-READINESS.md.

Kvalifikovani lokalni kandidat 0.8.0/code 17 uključuje ovaj editor; govorni TalkBack, fizička latencija i kompletan ručni kontrast ostaju zasebni otvoreni rad. Ovaj kod ne uključuje cloud, AI, naloge ili mrežne dozvole.

## Korisnički tok

More actions → Mind maps otvara pretraživu hijerarhiju cele stranice, uključujući skrivene čvorove. Red prikazuje mapu, nivo, skrivenost i collapse stanje. Izbor mapiranog čvora ili njegove upravljane veze otvara iste akcije direktno; pregled svih mapa ostaje dostupan. Liste su virtualizovane, uvlačenje ograničeno, tekst i akcije skrolabilni, kontrole lokalizovane na engleski i srpsku latinicu.

New mind map kreira obojen koren. Add child / Add sibling dodaju čvor i upravljanu vezu zajedno. Edit node koristi postojeći birač boje i naslov; granica novog naslova je 20.000 UTF-16 jedinica. Predugačak unos se odbija bez tihog skraćivanja važećeg naslova. Postojeći duži naslov se može pregledati, ali se ne prepisuje skraćenom verzijom. Prazan naslov i nevažeća topologija ne menjaju istoriju. Polja, boja i podpanel koriste saved-instance state; dokument i Android View se ne stavljaju u Bundle.

Change parent bira čvor iste mape; domen odbija ciklus, samoreferencu i promenu roditelja korena. Nova veza bira granične portove okrenute novom roditelju/detetu, uz isti identitet, stil i položaje beleški. Move whole branch pomera ceo kanonski podskup, uključujući skrivene potomke i affine beleške. Vezane konekcije prate objekte; nevezani objekti i topologija ostaju očuvani. Svaki edit je jedan Undo/Redo korak.

Collapse/Expand menja vidljivu projekciju. Reveal and select otkriva potreban put; Select map bira i kadrira samo vidljive objekte mape bez editovanja dokumenta. Cut/Delete koriste zasebnu pripremljenu potvrdu iz MIND-MAP-BRANCH-REMOVAL.md i kanonski broj potomaka. Privatni clipboard prenosi celu granu.

Novi Mind map šablon ima stvarni petočvorni model i četiri upravljana Connector-a sa lokalizovanim naslovima. Ostali šabloni ostaju bez mind-map metapodataka. Stare sačuvane Note/Line table se ne prepoznaju niti automatski pretvaraju u stabla. Dokument ostaje format 6, delta 2, Room 4.

## Arhitektura i zaštita stanja

Svi paneli jednog editor zahteva dele jedan `InputDialog` i njegov window token: pregled, akcije čvora, naslov/boja, izbor roditelja i pomeranje. Promena panela ne uklanja stari i ne otvara novi Android prozor. `MapCommit` pripada request scope-u i sprečava paralelnu potvrdu; Back/otkazivanje formulara je blokirano dok traje potvrda. Panel zadržava postojeći adaptive raspored, a `paneTitle` izlaže aktuelni naslov pristupačnosti. Birač boje ostaje zaseban ugnježdeni dijalog. Puna konačna native i R8 matrica potvrđuje tokove i na API 26 tabletu.

MindMapEditorController odvojen je od glavnog ViewModel-a. MindMapEditorRequest sadrži immutable Page snapshot i iterativno pripremljene redove. Teška priprema komandi radi na injektovanom computation dispatcher-u; objavljivanje ponovo proverava sesiju, semantičku scenu, request ID i generation. Promena table, dismiss ili cancellation ne može naknadno editovati novu scenu. Promena kamere je dozvoljena. Ponovljeni Apply tokom rada ne prepisuje referencu aktivnog worker-a i ne može zaobići cancellation.

Čisti MindMapEdits planovi ostaju autoritet za reference, granice, budžet i atomski Undo. UI ne menja stablo direktno. Fokus se primenjuje posle layout frame-a uz proveru table/stranice; Select map koristi zbirni vidljivi okvir. Semantički čvorovi, touch i izvoz koriste istu postojeću Page projekciju. Govorni TalkBack pregled i stvarna hardverska latencija ovim nisu potvrđeni.

## Dokazi i granice

Prethodna projekcija/clipboard source 6b53d42 završila je CI 42183053409: 494 JVM i pet profila po 131 app + 13 data, ukupno 638 jedinstvenih slučajeva. API 36 je prošao jedan ciljani startup retry; originalni SystemUI ANR pokušaj i artefakti ostaju sačuvani.

Branch potvrde source 23463e6 prošle su CI 42269898794 bez retry-ja: 500 JVM (350 core + 77 format + 5 data + 68 app), pet profila po 132 app + 13 data, 645 jedinstvenih slučajeva. Tri R8 toka i procesno ponovno pokretanje EN/sr-Latn imaju zasebne izveštaje. Digest-verified dokazi: artifacts/task-16-23463e6-evidence.json. Raniji d2f1803/4f7c03b fixture neuspehi ostaju u MIND-MAP-DIALOG-TEST-REVIEW.md.

MindMapEditorViewModelTest proverava immutable hijerarhiju, create/child/sibling/edit/color/Undo, visible map selection, reveal, cycle rejection, hidden movement, stale scene, cancellation i granice naslova. Core dopune pokrivaju move/affine/Undo/budget, facing reparent portove i semantički lokalizovan šablon. Dva MindMapEditorFlowTest slučaja izvršena su na svih pet konačnih Android profila: template → child/sibling → rotacija → reparent → collapse/move/expand → save/reopen, i cancel/create/HEX color → raster pixel → Undo/Redo.

Optimizovani smoke izvršava create/child/sibling, konfiguracionu promenu tokom unosa, reparent, collapse, Undo/Redo i saved process reopen. Negativna provera skrivenog deteta koristi nepromenjene screen okvire korena/roditelja posle Redo, tako da odsustvo ne može biti objašnjeno novim Fit-om. Konačni source 36cd15a prolazi svih šest CI poslova, tri R8 toka, jezike sa vraćenim stanjem, security gates i proveru tačnog paketa. To zatvara F06; buduće promene zahtevaju novu source-bound kvalifikaciju.
