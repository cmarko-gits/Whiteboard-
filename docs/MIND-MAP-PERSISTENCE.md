# F06 — semantički snapshot i trajni format

Razvojni podkorak 0.8.0/code 17; kvalifikovani korisnički paket ostaje 0.7.2/source 1176e59. Novi kod još nije završena mind-map funkcija. Domenske komande opisuje MIND-MAP-COMMANDS.md; njihovi dijalozi i povezivanje vidljive projekcije sa canvas/input/AX/izvoz potrošačima ostaju sledeći podkoraci. Novi template još nije aktiviran.

Page sada poseduje listu MindMapTree vrednosti i opcioni MindMapPageIndex. Prazna semantika nema novi indeks. Kopije naziva, kamere i pozadine dele isti content i semantički indeks; promena topologije/collapse stanja menja scene identitet. sharesSceneWith proverava oba identiteta u O(1). ViewModel potvrde konektora i SelectionEditPlan koriste taj ugovor, pa jednaka lista objekata više ne može prikriti promenjeno semantičko stanje.

Warm preview deli topologiju/vlasništvo i hidden-ID skup samo ako PageContent dokaže istu pripadnost, vrste, redosled i definicije veza. Nova vidljiva lista koristi nove objekte. Strukturna promena ili novi kraj konektora radi punu proveru i ponovo računa vidljivost. Ne čuva se stara Page fasada ili lanac prethodnih semantičkih indeksa. Canonical contentBounds ostaje validacija svih objekata, uključujući skrivene.

## Šema i kompatibilnost

Board dokument je verzija **6**, sa čitanjem legitimnih writer formata **1–5**. Page.mindMaps sadrži ravne zapise mape (id, nodes); svaki node ima id, parentId, connectionId i collapsed. Naslov/boja/font/pozicija ostaju u Note-u ili affine Note-u, a geometrija veze u Connector-u. Ravna lista ne povećava JSON dubinu kada raste logička dubina stabla. Unknown/missing polja, pogrešni tipovi, duplikati, ciklusi i strane/nekonzistentne veze se odbijaju.

Standalone page zapis dobija eksplicitan version=6. Prethodne stranice bez verzije čitaju se kao pre-semantički format 5. Semantički podaci se ne prihvataju pod starom oznakom, a eventualna unutrašnja page oznaka mora odgovarati dokumentu/envelope-u. Oznaka verzije mora biti ceo JSON broj. Decimalni ili eksponentski zapisi, sitni razlomci i prelivanje se odbijaju; ne zaokružuju se u podržanu verziju.

Delta envelope je verzija **2**, fingerprint verzija ostaje 1 jer se kanonski Element sadržaj nije promenio. Novi reader čita i envelope 1 sa dokumentima do 5. Mind-map ValueChange ima expected/value liste, uključujući izričitu praznu listu za uklanjanje semantike. Null nije prećutno nepostojeća promena. Collapse-only izmena više ne daje prazan delta; metadata i objekti primenjuju se jednom validiranom Page kopijom. Konflikt ne menja originalni Board. Ovo održava postojeći lokalni model/format ugovor; online moduli nisu aktivirani.

JSON accounting, dubina, string/number i ZIP budžeti važe i za semantiku. Broj mapa/čvorova i njihove jedinstvene reference proveravaju se i pre pravljenja writer JSON stabla. Ovo nije potpuna preflight garancija svih encoder/process alokacija; H04 ostaje otvoren. Room ostaje **4**, jer repository već čuva ceo verzionisani Board JSON u dokument/draft chunk-ovima. Nema destruktivne DB migracije ili novih dozvola/biblioteka.

## Kopiranje i istorija

Duplicate page koristi isti F05 object identity mapping i rekey svih map/node/parent/edge referenci, sa novim map ID-jevima i sačuvanim collapse stanjem. Copy generator ne sme iskoristiti stari map ID kao novi object ID. Undo/Redo ponovo koristi jednom pripremljene identitete. Tree vrednosti ulaze u history accounting; prevelika promena map stranice se odbija pre mutacije umesto da odmah izbaci sopstveni Undo. Ovo još nije branch-aware privatni clipboard ili Delete branch UI.

Geometrijski target-removal planer privremeno koristi projekcije bez semantičkih metapodataka kada uklanja sve Connector definicije iz te pomoćne liste. Konačna stranica zadržava punu semantiku; brisanje običnog spoljnog cilja može da detach-uje njegovu vezu bez gubitka mape. To nije konverzija korisničkog dokumenta u običan crtež.

## Provera pre nove matrice

Dodate su core regresije za metadata/collapse identitet, warm/fallback preview, spoljni kraj koji ponovo postaje vidljiv, late cancellation, stale selection, rotaciju/Undo, odbijanje pogrešne vrste čvora, complete-page duplicate/Undo/Redo i budžet. Format slučajevi obuhvataju roundtrip, stare verzije, stroge tipove i reference, flat duboko stablo/ZIP, collapse-only i created-page delta, konflikt, invalid writer metadata i resursni limit.

Prva matrica source 85c410f/CI 42176438620 ostaje neuspešna. JVM 445, build/lint, OSV 155 i release policy prolaze. Na svih pet profila prolazi 125/126 app i 13/13 data; isti novi test otkriva da parser odbija oštećenu topologiju, ali IllegalArgumentException izlazi iz repository-ja bez INVALID_DOCUMENT mapiranja. Sačuvani izveštaji: artifacts/task-16-85c410f-*. Nema kvalifikovanog 0.8.0 paketa.

Ispravka prevodi greške dekodiranja nepoverljivog saved/draft sadržaja u DocumentFailure(INVALID_DOCUMENT), uz očuvanje eksplicitnog DocumentFailure, UnsupportedDocumentVersion i cancellation razloga. Isti put koristi retainedAssetIds: oštećeni recovery prekida popis umesto da dopusti čišćenje potencijalno potrebnih priloga. Četiri Room slučaja sada obuhvataju saved/draft, import, oštećenu topologiju/decimalni header i buduću verziju; proveravaju da metadata i originalni zapisi nisu prepisani. Native izvršavanje ispravke zahteva novu matricu.

Source secret scanner istog 85c410f izvora prijavio je jedan pregledan lažno pozitivan nalaz u srpskom opisu JSON header-a, bez kredencijala. Tekst je preformulisan; .gitleaksignore izuzima isključivo fingerprint tog istorijskog commit-a/fajla/pravila/reda, a ne putanju ili celo pravilo. Lokalni ponovljeni history scan svih refs (113 obrađenih commit-a) i direktni docs scan nemaju nalaze. Pun current-source scan ostaje CI provera; lokalna kopija izvora nije pravljena. Detalji: MIND-MAP-STORAGE-REVIEW.md.

Ove rezultate ne pripisivati 95e3804: taj raniji izvor ima svoju zelenu regresiju (421 JVM i pet profila po 123 app + 13 data), format 5 i još nema Page/codec integraciju.

**F06 ostaje otvoren; 13/47 glavnih zadataka završeno, 34 preostala.**
