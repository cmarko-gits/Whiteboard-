# F06 — lokalni mind-map: ugovor pre implementacije

> F06 je završen u lokalnom softverskom/emulatorskom opsegu: [0.8.0/source 36cd15a](LOCAL-0.8.0-CANDIDATE.md), svih šest CI 43008205011 poslova bez retry-ja, kvalifikovani preview APK i vlasnički potpisani AAB. [Editor i tokovi](MIND-MAP-EDITOR.md) opisuju konačnu implementaciju. Prva neuspešna matrica 7f0fe83 i kasniji nalazi ostaju sačuvani u [pregledu](MIND-MAP-EDITOR-CI-REVIEW.md) i RELEASE-READINESS.md.

Status: kvalifikovani lokalni kandidat 0.8.0/code 17. Konačni izvor prolazi 519 JVM, pet profila po 140 app + 13 data, tri R8/jezička/runtime toka, upgrade/adaptive/split-screen i dve odvojene dijagnostike. Ranije projekcija/clipboard 6b53d42 (638 slučajeva, sačuvani startup retry) i branch potvrde 23463e6 (645 slučajeva) ostaju istorijske kohorte. Ručni govorni TalkBack, kompletan kontrast i fizičko profilisanje ostaju zasebni završni rad; ne menjaju domenski ugovor ispod.

## Autoritativni podaci i granice

Čvor koristi postojeći top-level Note, uključujući Note unutar AffineElement-a za rotaciju, kao autoritativni naslov, boju, font i položaj. Veza roditelj–dete koristi postojeći Connector i njegovu zajedničku geometriju. Poseban immutable MindMap model čuva identitet mape, jedan koren, uređeni skup node ID-jeva, roditelja i ID upravljane veze svakog deteta, kao i collapsed stanje. Naslov, boja i koordinate se ne dupliraju u metapodacima stabla. Nema prepoznavanja modela iz teksta, rezervisanih ID prefiksa ili skrivenog JSON-a u belešci.

Jedan čvor pripada najviše jednoj mapi. Koren nema roditelja ili upravljanu ulaznu vezu; ostali imaju tačno po jednog roditelja i jednu jedinstvenu upravljanu vezu. Svi su dostižni iz korena, bez ciklusa, samoreferenci, duplih ID-jeva i stranih page referenci. Granice broja čvorova, teksta, ukupnih objekata, JSON resursa i istorije proveravaju se pre objavljivanja promene. Validacija, indeks roditelj/deca i obilazak su iterativni, sa checkpoint-ima; duboko stablo ne sme izazvati stack overflow.

Upravljana veza mora imati bound krajeve baš ka roditelju i detetu. Obična F05 veza ostaje slobodna korisnička veza i nije automatski tree relation. Generic Edit/Detach upravljane veze otvara mind-map akciju ili objašnjenje; ne sme tiho napraviti nekonzistentne metapodatke. Grupisanje map čvorova sa običnim objektima nije dozvoljeno dok posebna konverzija ne očuva semantiku. Cela mapa ostaje skup pojedinačnih objekata, sa posebnom akcijom Select map.

## Komande i korisničko ponašanje

- Kreiranje dodaje koren i metapodatke jednom Undo komandom. Novi Mind Map template kreira pravi model sa lokalizovanim početnim naslovima; stari dokumenti se ne zaključuju heuristikom ili preimenuju pri učitavanju.
- Add child i Add sibling nude odmah fokusirano uređivanje naslova. Novi čvor i bound veza nastaju zajedno, sa stabilnim novim ID-jevima i determinističkim početnim položajem u granicama geometrije. Postojeći raspored se ne pomera bez izričite komande.
- Pomeranje, resize, tekst i boja koriste postojeće komande i portove. Reparent menja parent relation i upravljanu vezu atomarno; pokušaj vezivanja pod sopstvenog potomka ili u drugu mapu se odbija. Pomeranje grane je posebna akcija, bez skrivenog pomeranja potomaka pri običnom pomeranju čvora.
- Collapse skriva potomke i njihove upravljane veze, čuva njihov sadržaj i položaj i predstavlja Undo promenu. Expand ih vraća bez gubitka sadržaja. Fokus i selekcija skrivenih potomaka prelaze na vidljivog pretka. Dodavanje deteta proširuje potrebnu granu.
- Delete branch jasno imenuje i potvrđuje uklanjanje grane sa potomcima. Brisanje korena uklanja mapu. Obične spoljne veze ka uklonjenim čvorovima koriste postojeći atomic detach u poslednjoj world tački. Undo vraća celo stablo, veze, položaje, collapse stanje i redosled slojeva.
- Copy/cut čvora eksplicitno obrađuje celu izabranu granu. Kopirana grana dobija sopstveni koren i nove node/map/edge ID-jeve kroz dva prolaza; spoljne reference prate F05 pravila. Više izabranih preklopljenih grana se deduplikuje. Duplicate page i editable backup čuvaju sve skrivene čvorove i metapodatke.

Dijalog stabla daje pretraživu hijerarhiju, naslov, boju, Add child/sibling, Reparent i Expand/Collapse bez obaveznog gesta. Telefon koristi skrolabilan raspored sa dostupnom potvrdom pri IME-u; tablet koristi raspoloživu širinu. EN/sr-Latn tekstovi, 48 dp ciljevi, imenovane akcije, logičan fokus i konfiguraciona rekreacija deo su iste funkcije. Izbor čvora iz pretrage ima izričitu Reveal akciju za skrivenu granu. Nema online zavisnosti.

## Projekcija scene i čuvanje

Canonical Page.elements ostaje puna lista: collapse nikada ne briše podatke. Page.contentBounds i potpuna domenska validacija i dalje obuhvataju i skriveni sadržaj. Posebna vidljiva projekcija daje visibleObjects/visibleContentBounds za canvas, hit test, marquee, Objects navigator, accessibility, Fit, thumbnail i PNG/JPG/PDF izvoz. Time izvozni kadar odgovara onome što korisnik vidi. Editable JSON/ZIP i recovery uvek sadrže celo stablo. Spoljna obična veza sa skrivenim krajem skriva se u toj projekciji, umesto da izgleda odsečeno; vraća se proširivanjem grane.

Semantička projekcija mora biti vezana za isti content i mind-map snapshot. Metadata-only promena naziva ili viewport-a ne skenira ponovo stablo. Collapse može ponovo koristiti kanonsku F05 geometriju, uz novu vidljivu projekciju. Geometrijski edit ili promena grafa invalidira odgovarajući indeks. Nema globalnog mutable stabla, nekontrolisanog lanca starih Page fasada ili posebne geometrije izvoza.

Stale-plan provera uključuje i identitet semantičkog snapshot-a, uz postojeće board/page/element-list provere. Jednaka lista objekata ne dokazuje jednak collapse/topology state. Promena viewport-a ili naziva može ostati dozvoljena, ali collapse/reparent/delete koji menja značenje izbora mora odbiti zastarelu potvrdu. Page-level vlasništvo i vidljivost pripremaju se jednim zajedničkim indeksom; ne graditi ceo object lookup zasebno za svaku mapu na istoj stranici.

Dodavanje semantičkih page metapodataka zahteva novu eksplicitnu verziju dokumenta, čitanje starih 1–5 i testirane page/board/delta/digest/ZIP/recovery putanje. Konačna šema i numerička verzija određuju se pri integraciji, ne menjaju kvalifikovani format 5 unapred. Room menja šemu samo ako se zaista menja njegov storage ugovor; postojeći dokument JSON ne zahteva automatski destruktivnu Room migraciju. Nepoznata verzija se odbija pre upisa. Greška ili cancellation ostavlja raniju reviziju, draft, selekciju i istoriju netaknutim.

## Redosled i kriterijumi prihvatanja

1. Čist Kotlin graph model/indeks i iterativne, bounded/cancellable validacije; malformed i duboka stabla, poredak braće, collapse/reveal, ciklus, dupli čvor/veza i strani ID-jevi. Još bez tvrdnje o funkciji u aplikaciji.
2. Semantički page ugovor i commands/planner: create/add/edit/reparent/collapse/delete/copy, atomski Undo/Redo, budget i stale snapshot zaštita, uključujući spoljne veze.
3. Eksplicitni codec/delta/digest/ZIP i Room save/draft/reopen, sa starim dokumentima i hostile-input regresijama. Originalni fixture-i se ne prepisuju novim formatom.
4. Jedinstvena vidljiva projekcija kroz renderer/input/AX/Fit/izvoz i skrivene-grane scenariji; bez curenja skrivenog sadržaja u raster/PDF izvoz ili virtuelni fokus.
5. Pristupačan adaptivan UI, lokalizacija, pravi novi template i kompletan create → child/sibling → move/reparent → collapse → Undo → save/reopen → export tok.
6. Isti dokument na API 26 telefonu/tabletu i API 30/33/36; R8 tokovi, style/architecture/lint, dependency/source/history/security i ABI/16 KB/potpis/payload gates za tačan novi kandidat. Sačuvati neuspešne pokušaje. Fizički i govorni TalkBack ostaju u jasno odloženom obimu.

F06 je završen tek kada svih šest koraka ima kod i dokaze. Broj glavnih preostalih zadataka ostaje 34; ovo su podkoraci, ne nove glavne stavke.

## Početni lokalni dokaz

MindMapTree/MindMapNode trenutno imaju defensive copy i nemutabilne javne kolekcije, identitet/vlasništvo node/edge referenci, jedan koren, iterativnu ciklus/reachability proveru, stabilan poredak braće, subtree/path/visible/collapse/reveal i proveru Note/Connector vezivanja za stranicu. MindMapPageIndex zajedničkim lookup-om proverava više mapa, zabranjuje zajednički čvor/vezu ili map ID, čuva originalni redosled vidljivih objekata i skriva i obične spoljne veze sa skrivenim krajem. Prazne mape ne alociraju novi ceo object/visibility indeks. Granica je 1.024 mape i ukupni Page object budžet; veličina se proverava pre pripreme.

Prva osnova source 84f9de9 prošla je CI 42096432309: svih šest poslova, 420 JVM (300 core + 66 format + 5 data + 49 app), pet profila po 123 app + 13 data i tri R8/jezička toka. To je 556 jedinstvenih slučajeva, regresija osnove bez gotove F06 funkcije. Source scan 402 i OSV/notices 155 prolaze; nema pripremljenog distribuiranog 0.8.0 paketa. Tekuća affine-note dopuna ima 22 nova domenska slučaja i lokalnih 421 JVM (301/66/5/49), bez grešaka/preskakanja, uz novu CI proveru koja sledi. Posebne probe imaju 50.000-node duboko i široko stablo, late cancellation, strane reference, nested-note odbijanje, više mapa, obične spoljne veze, različit geometry/semantic identitet i konkurentne nezavisne čitaoce. To je lokalni domenski dokaz; vidljiva projekcija još nije povezana sa stvarnim renderer/input/AX/export potrošačima.

Granica 50.000 je samo gornja granica topologije izvedena iz Page.MAX_ELEMENTS i potrebnih veza, ne obećanje da aplikacija može obraditi/sačuvati toliko sadržaja u svakom heap-u. JSON, istorija, potpuna stranica i kasniji UI imaju sopstvene stvarne budžete. Dokaz je artifacts/task-16-mind-map-foundation-local.json sa base source-om, hash-evima radnih fajlova i logovima. Ne pripisivati ove nove testove c7bd2b0 APK-u ili njegovom CI-ju. Page semantika, scene projection, komande, format i UI još nisu integrisani.
