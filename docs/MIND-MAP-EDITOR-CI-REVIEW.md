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
