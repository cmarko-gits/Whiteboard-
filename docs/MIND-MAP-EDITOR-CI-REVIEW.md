# F06 — prvi puni editor: CI nalazi i ispravke

Izvor 7f0fe831c34f9760ed3de94bd4fd43db1a7ef959, CI 42330852735, **nije kvalifikovan**. Verify prolazi 518 JVM, stil/arhitekturu/lint, licence, OSV 155 i source scan 435. API 33 izvršava 134/134 app + 13/13 data. API 26 phone i API 36 imaju po 133/134 app; API 30 ima 132/134 app. API 26 tablet staje posle pada procesa (85 evidentiranih app slučajeva, jedan neuspeh); to nije potpuna app regresija. Data suite prolazi 13/13 na svih pet profila. Novi R8 mind-map tokovi nisu kvalifikovani. Finalni digest-verified dokazi ostaju u artifacts/task-16-7f0fe83-evidence.json; nema retry-ja ovog izvora ili novog distribuiranog paketa.

## Prelaz dijaloga u testu

API 26 phone i API 30 padaju na Add sibling, API 36 kasnije na Move whole branch; API 30 ima i New mind map pad. DialogTestDriver prijavljuje neuspelu fizičku swipe injekciju. Pregledani pre-cleanup API 26 PNG D870FACD2ACA687499D2C8154AC2FEB8304D33B0DF80131B315C9CB669C17B76 prikazuje vidljiv, omogućen Add sibling u otvorenom Native child dijalogu. API 30 PNG C0CA8AD6D716CBA44FA38BE1EE42A0BBC958F96BF6CF0950F24C1BD926C948E4 i XML 4FE28A5E697AC0552C7FA275C3BD1E23BD4E43EA2CE74CB13DD5A5BBB0EFE860 prikazuju New mind map labelu sa enabled/clickable android.view.View roditeljem.

Problem je saglasan sa preranim driver ulaskom tokom promene aktivnog prozora; snimak sam ne dokazuje tačan raspored događaja. Novi flow fixture zato prvo zahteva vidljivu ciljnu labelu, a posle More → Mind maps i završni Done marker novog dijaloga, pa tek onda koristi postojeći connected/enabled driver. Nema novih timeout vrednosti, Back komande, gašenja IME iz testa ili izbacivanja asercija. Generički driver nije globalno menjan. API 33 uspeh nije korišćen da se ostali padovi zanemare.

## Stvarni tablet pad pri automatskom fokusu

Tablet proces pada sa Expected BringIntoViewRequester to not be used before parents are placed. Stack ide kroz Compose ContentInViewNode/BringIntoViewResponder; novi title form je tražio fokus odmah u LaunchedEffect(Unit), pre dokaza da je scroll roditelj postavljen. Fokus sada čeka onGloballyPositioned sa pozitivnom veličinom i sledeći frame, uz cancellation životnog ciklusa. Nema catch/ignore oko Compose pada i nema fiksnog vremenskog sleep-a kao produkcionog rešenja. Postojeći ceo native tok već uključuje kreiranje/uređivanje i rotaciju naslova i mora ponovo proći na tabletu.

## Vizuelni nalaz na uspešnom API 33

Pregledani sačuvani node-editor PNG A853AA44E37DDF1FF2F5AAC21AFAFA4F264F14E0C458B397BA8DE74B02AF60EE prikazuje ispravne naslove/topologiju i 13 izabranih objekata, ali donja beleška delimično ulazi ispod floating zoom kontrola. Select map/reveal sada koristi stvarno izmerenu visinu tih kontrola kao donji inset pri fit-u. Ne koristi fiksnu pretpostavku o fontu/density-ju. Native tok dodatno zahteva da Edited sibling donjom ivicom bude iznad Zoom out kontrole posle layout-a. Obična kamera i istorija ne dobijaju novi sadržajni edit.

PNG obojenog korena 0861355841606E5AA3FF20A23375E918C85F2401BBCFD127B0809DB45D789712 vizuelno pokazuje izabranu plavu boju i čitljiv beli naslov. Isti test proverava tačan bitmap pixel, Undo/Redo i sačuvani model. To je jedan uspešan profil, a ne puna kvalifikacija boja na svim uređajima.

Ispravke traže novu punu matricu za novi source. F06 i glavni broj ostaju **13/47 završeno, 34 otvoreno** dok ne prođu Android/R8 i paket gates. F07 arhitekturni podkorak je u FLOWCHART-DESIGN.md.

## Sledeći izvor da3908e: landscape IME nalaz

CI 42332393903 potvrđuje verify/518 JVM, licence, OSV 155, source scan 438 i APK politiku. API 26 phone, 30 i 33 izvršavaju po 133/134 app + 13/13 data; njihov neuspeh je Confirm posle landscape rotacije naslovnog formulara. Raniji Add sibling readiness pad više nije mesto zaustavljanja ovih tokova. Ostali profili još rade u trenutku ovog zapisa; puna matrica nije kvalifikovana.

Pregledani API 33 pre-cleanup PNG 3C7A1B8BF69D122254A66616BB73BC79C14F48951B85AF55A89FAAD9E364EB43 i XML 71B6323706F482A9A199FC957C22326A85B2F038842342CD82BECFA2D8A65B9D pokazuju očuvan Edited sibling naslov i otvorenu landscape tastaturu, dok su Confirm/Cancel prekriveni. Ovo je stvarni adaptivni UI nalaz, ne pogrešna test labela. Test ne zatvara tastaturu da bi ga sakrio.

Title i signed-move formulari sada koriste postojeći InputDialog + AdaptiveDialogContent, system-bars/IME inset-e i stabilne roditelje input/action elemenata. Pri maloj visini unos i akcije su jedan pored drugog. Birač boje je uz naslov i ostaje dostupan u kompaktnoj formi; polja zadržavaju vrednosti pri rotaciji. Prethodni onGloballyPositioned/frame focus guard ostaje. Native ceo tok dodatno otvara tastaturu i rotira formular sa oba signed pomeraja, proverava njihove očuvane vrednosti, potvrdu i zatvaranje IME bez test Back-a. Nova puna matrica mora potvrditi ove izmene.

Konačan status da3908e pokušaja je cancelled/superseded: tri phone profila imaju gore navedeni native neuspeh; API 36 i API 26 tablet imaju 134/134 app + 13/13 data, ali su njihovi kasniji R8 poslovi otkazani objavljivanjem ispravljenog izvora. Sačuvani su finalni task-16-da3908e-evidence.json, pre-superseding snapshot i oba cancelled job loga. To potvrđuje da je tablet prošao raniji focus crash, ali nije kompletna kvalifikacija ova dva posla. Ne prikazivati cancelled kao success.

Adaptivni formulari su source e0d35a25b204cb26427f2937578aedab13ebb408, CI 42333984840 u toku. Lokalno prolaze pogođeni JVM, arhitektura i kompilacija 134 app native slučaja (task-16-node-editor-adaptive-final-local.log). Poseban host package guard za 0.8+ sada zahteva eksplicitni očekivani JVM/app/data inventar; tri izvršene negativne kontrole odbijaju pogrešan JVM broj, pogrešan device broj i nedostajući novi mind-map R8 marker pre pisanja paketa. Za ovaj source očekivanje je 518 JVM, 134 app i 13 data po profilu. To nisu završni rezultati nove matrice.

## E0d35a2 — završna matrica i sledeće ispravke

CI 42333984840 je završen neuspešno, bez retry-ja. Verify prolazi 518 JVM; API 33 i API 26 tablet prolaze svih 134 app + 13 data. Tablet prolazi i novi optimizovani mind-map smoke i EN/sr-Latn proveru uz vraćen jezik. API 36 prolazi 134/134 app + 13/13 data, ali pada u R8 resize scenariju. API 26 phone i API 30 imaju po 133/134 app + 13/13 data. Svi originalni rezultati ostaju u finalnom task-16-e0d35a2-evidence.json; nema distribuiranog 0.8.0 paketa.

- API 26 phone: pre-cleanup PNG 8186E7FCA836EBE794D1FBBBE23B8361B9B8C2012CE15CD45B4883428AF58DA5 i XML 85FAFBF22440654B29CFBB9A1CE18B2FC52E2AA91D6871638FF170BE822E222C pokazuju Find a node sa otvorenom tastaturom i prekrivenim Done akcijama. Browser sada koristi isti inset-aware adaptivni okvir, sa LazyColumn koja dobija samo preostalu konačnu visinu; nema LazyColumn unutar unbounded verticalScroll-a. Početni fokus je na pregledu, ne na automatskom otvaranju pretrage. Search query/editing stanje se čuva pri rotaciji, a Done tokom pretrage prvo zatvara sopstvenu IME sesiju i zadržava rezultate.
- API 30: pregledani PNG DF952A5828E4A78EDC199DAFE7B4465562C665A96A7E6253C75C9EFCF2351464 prikazuje obojeni koren u editoru i zaostalu tastaturu posle potvrde. Privatni MapCommit zadržava dijalog kroz sledeći frame, ponovo zatvara njegovu input sesiju, pa tek pokreće i čeka ViewModel Job. Pending stanje blokira ponovljenu potvrdu i izmenu polja; cancellation pri uklanjanju UI-ja ne sme kasno objaviti akciju. Ne koristi se test Back da prikrije zaostalu IME.
- API 36 R8: poslednji smoke-ui.xml (SHA-256 3403003DD8D76412AF3CB1ACF75E3E44E1C0B04A8F8894D665AAAF8E8E6E9E6E) prikazuje launcher. Bounded error-level AndroidRuntime iz digest-verified originalnog logcat-a potvrđuje fatalni Mismatching scroller orientation; single-line, non-wrap text fields can only scroll horizontally tokom resize-a. Title polje više ne menja singleLine režim pri promeni constraint-a; stalno je jednolinijsko i zadržava celu vrednost. Nema catch/ignore oko ovog pada. Novi CI artefakti zadržavaju mapping.txt za dalju R8 dijagnostiku.

Nova native regresija proširuje isti ceo tok pretragom sa otvorenom tastaturom/rotacijom/Done/prikazom rezultata; postojeći title/move/color/Undo/save/reopen i R8 resize zahtevi ostaju. UI prosleđuje request ID pri primeni, a coordinator odbija odloženu akciju starog dijaloga čak i kada je scena ista. Poseban novi VM slučaj proverava odbijanje stare potvrde i uspeh nove uz tačan Undo; očekivani novi inventar je **519 JVM (358 + 77 + 5 + 79), 134 app + 13 data**. Nova matrica mora potvrditi ispravke; F06 i dalje nije završen.

## 5bf60ca — fokus posle pretrage i fixture izbor reda

CI 42336461002 je završen neuspešno, bez retry-ja: verify potvrđuje 519 JVM, a svi profili po 133/134 app + 13/13 data. R8 tokovi ovog izvora nisu izvršeni jer native regresija nije prošla. Dva problema su razdvojena pregledom pre-cleanup snimaka.

API 26 phone/tablet staju na awaitTestKeyboardHidden posle Search Done-a u landscape-u. Pregledani phone PNG A4443ADFEC7C7C4491474B4FAE0DB9FE2BA662A11109A73813502AF7E6D2E097 prikazuje očuvan query i vidljiv Done, ali fokus/IME ostaju na pretrazi. Done sada privremeno onemogućava unos, zatvara sopstvenu sesiju pre i posle frame-a i prenosi fokus na eksplicitno focusable dugme (canFocus=true i u touch režimu). Isti handler koristi tastaturni IME Done; korisnički upit i lista ostaju očuvani. Cancel tokom prikazane pripreme odmah otkazuje worker.

API 30/33/36 stižu do Add child očekivanja. Pregledani API 33 PNG AA4469FA0A4540DC209014FFBDCCCF7A5D62393272711036B274EA7D173653D4 i XML F79C49C76FB89859D1902C95343E8571C791EF61296C6AFD5E28B9ACC49B82C0 pokazuju ponovo fokusiran Search sa istim Central idea tekstom i zaseban red Central idea. Fixture node(text) je birao prvi textContains, uključujući EditText query. Sada bira android.widget.TextView u target app paketu, tako da aktivira naslov reda. Nema uklanjanja pretrage/rotacije/IME asercija ili povećanja timeout-a.

Zatvaranje dijaloga i zahtevi za branch Cut/Delete vezani su za isti request ID kao Apply. Postojeći VM slučaj je proširen starim branch-removal zahtevom: novi dijalog ostaje aktivan i ne pojavljuje se destruktivna potvrda. Broj slučajeva ostaje 519 JVM / 134 app / 13 data; nova puna matrica je obavezna pre kvalifikacije.
