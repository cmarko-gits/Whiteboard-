> Arhivirani online plan. Za aktivni lokalni obim koristiti [LOCAL-REBUILD-PLAN.md](LOCAL-REBUILD-PLAN.md), [LOCAL-STATUS.md](LOCAL-STATUS.md) i [LOCAL-QUALIFICATION.md](LOCAL-QUALIFICATION.md). Cloud/AI/Play stavke ispod nisu aktivni zadaci ove iteracije.

# Preostali zadaci — 31. oktobar 2026.

Ovo je celovit spisak trenutno otvorenih stavki iz ENGINEERING-EXECUTION.md i TASKS.md. Inženjerske stavke se preklapaju sa proizvodnim planom; zbir nije broj zasebnih funkcija. Otvorena stavka može značiti nedostajuću implementaciju, delimično gotov tok, test ili odluku izdavača. Postojeće funkcije nisu navedene kao da kod nedostaje.

## Redosled neposrednog rada

- [x] Ponoviti popravljene Compose/UI testove na malom ekranu i rešiti preostale greške.
- [x] Proveriti fill alat: oblici/grupe/beleške/pozadina, Undo/Redo, Save/reopen i otkazivanje gesta.
- [x] Proveriti cloud tok sa izolovanim lokalnim serverom i test nalogom, uključujući retry bez duplikata i stvarni WorkManager.
- [x] Proveriti recovery/rotaciju, reprezentativne rasporede i izolovano merenje renderovanja na emulatoru.
- [x] Proveriti UI na fontu 150%, popraviti prelamanje tema i prikaz stvarne verzije; puna TalkBack i fizička kvalifikacija ostaju otvorene.
- [x] Ponoviti pogođene lint/style/architecture/release provere i bezbednosnu proveru konačnog APK-a.
- [x] Izgraditi lokalni APK/AAB i proveriti razvojni potpis, 16 KB kompatibilnost, instalaciju/ažuriranje na emulatoru i rezultate.
- [ ] Nastaviti nedovršene cloud/collaboration/AI implementacije; produkcioni servisi, tajne i Play objava imaju spoljne zavisnosti.

Raspoloživih 3,4 GB je čvrst limit. Koriste se postojeći SDK/AVD i paketi; bez novih emulatora/system image preuzimanja, bez paralelnog builda i emulatora, bez nepotrebnih kopija velikih artefakata. Slobodan prostor se prati između faza.

## Inženjerske provere (2 otvorene, 6 zatvorenih u ovom prolazu)

- [x] E03 Add deterministic ViewModel/coroutine tests for success, failure, cancellation, edits during Save and state restoration; retain existing integration tests.
- [ ] E05 Use explicit UI actions/state and typed recoverable errors; verify configuration changes and accessible UI controls.
- [x] E06 Add durable, account-scoped background synchronization with bounded retries and cancellation on account changes; preserve queued edits.
- [x] E07 Qualify the fill tool through geometry, history, grouped objects, save/reopen and gesture cancellation tests.
- [x] E08 Add focused Compose tests, debug memory-leak detection and reproducible rendering/performance measurements.
- [x] E09 Complete runtime dependency/security checks, backend negative tests, secure logging and release network/storage checks. Record coverage and unresolved findings.
- [x] E11 Run the applicable JVM, backend H2/PostgreSQL, Android, lint, architecture and packaging checks; record fresh results.
- [ ] E12 Produce the tested APK/AAB and complete physical-device checks where a device is available.

E05: stanje/greške, rotacija, IME i font 150% imaju prolazne provere; preostaje puna pristupačnost interakcija i čitača ekrana. E12: APK/AAB i emulator su provereni; fizički telefon/tablet/stylus nisu dostupni. Izveštaji, snimci i SHA-256 paketa su u artifacts/, a obim provera u STATUS.md i SECURITY.md. Prolaz lokalnog backenda nije javna cloud objava.

## P — proizvod i odluke

- [ ] P-01 Zaključati V1 obim i povezati svaku funkciju sa kriterijumom iz REQUIREMENTS.md.
- [ ] P-07 Potvrditi referentne telefone/tablete/stylus i proveriti podršku od izabranog minSdk 26, uključujući orijentacije.
- [ ] P-10 Zaključati reprezentativne test dokumente i merljive granice FPS/frame time, latencije, memorije i Save/Load/Export.
- [ ] P-12 Odrediti vlasnika aplikacije, naziv za prodavnicu, applicationId i kontakt za podršku; proveriti dostupnost naziva pre izrade finalnog identiteta.

## A — Android projekat i arhitektura

- [ ] A-08 Dovršiti proveru postojećih tipiziranih grešaka, retry akcija i bezbednih logova u svim tokovima.
- [ ] A-11 Izmeriti postojeći canvas/input na referentnom fizičkom uređaju; prototip i renderer već postoje.
- [ ] A-12 Potvrditi postojeći renderer/input merenjima i proveriti odgovarajuće stylus/Ink API-je.

## V1-UX — dizajn i tokovi

- [ ] V1-UX-02 Potvrditi popravljeni raspored biblioteke, editora i Settings-a na malom/velikom telefonu i tabletu, u oba položaja.
- [ ] V1-UX-06 Definisati pristupačnost kontrola, tekstualne opise, kontrast, skaliranje teksta i alternative ključnim gestovima.

## V1-CAN — canvas i gestovi

- [ ] V1-CAN-06 Obraditi dodir i stylus, režim ignorisanja prsta gde je podržan i palm rejection na dogovorenim uređajima.
- [ ] V1-CAN-08 Izmeriti postojeće culling/keširanje i uvoditi prostorni indeks samo ako merenje pokaže potrebu.
- [ ] V1-CAN-09 Testirati tačnost na velikom zoom-u, negativnim koordinatama i velikim udaljenostima od početka.

## V1-DRAW — olovke i gumica

- [ ] V1-DRAW-06 Testirati brze, kratke, ukrštene i duge poteze, dodir jedne tačke, otkazivanje i više pokazivača.

## V1-OBJ — oblici, tekst, beleške i selekcija

- [ ] V1-OBJ-10 Proveriti tekst latinice/ćirilice, duge beleške i preklopljene/tanke objekte.

## V1-HOME — biblioteka i podešavanja

- [ ] V1-HOME-07 Proveriti navigaciju nazad, ponovno otvaranje, nedostupnu tablu i očuvanje stanja liste.

## V1-EXP — PNG i deljenje

- [ ] V1-EXP-06 Proveriti praznu tablu, providnost, negativne koordinate, tekst i velike dokumente u izvezenoj slici.

## QA — stabilizacija i uslov za release

- [ ] QA-01 Održavati već postojeće unit testove i dopuniti stvarne nepokrivene rizike; aktuelnih 123 lokalna JVM testa prolaze.
- [ ] QA-02 Zadržati i proširiti postojeće integracione/migracione testove; aktuelnih 16 data-device testova prolazi.
- [ ] QA-03 Ponoviti i stabilizovati postojeće ključne UI tokove posle ispravki; prethodni app prolaz je imao 13 grešaka od 37 slučajeva.
- [ ] QA-04 Proveriti ceo V1 tok offline, bez naloga, bez nepotrebnih dozvola i bez skrivene mrežne zavisnosti.
- [ ] QA-05 Proveriti referentni mali/veliki telefon, tablet i stylus uređaj na minimalnoj i aktuelnoj podržanoj Android verziji.
- [ ] QA-06 Proveriti rotaciju, multi-window gde je podržan, tastaturu, background/foreground i gašenje procesa.
- [ ] QA-07 Izmeriti frame time, latenciju crtanja, memoriju, save/load/export na dogovorenim malim i velikim dokumentima.
- [ ] QA-08 Otkloniti dokazana uska grla, blokiranje glavne niti i curenje memorije; ponoviti relevantna merenja.
- [ ] QA-09 Proveriti TalkBack za dostupni UI, dodirne površine, kontrast, uvećan font i lokalizovane tekstove.
- [ ] QA-10 Proveriti produkcijski build sa optimizacijama, potpisom i svim korišćenim bibliotekama, uključujući native kompatibilnost ako postoji.
- [ ] QA-11 Pregledati dozvole, izložene Android komponente, backup pravila, privremene fajlove, logove i licence zavisnosti.
- [ ] QA-12 Napraviti izveštaj o pokrivenosti zahtevima, rezultatima na uređajima i poznatim ograničenjima; rešiti blokirajuće greške.

## GP — Google Play priprema, testiranje i objava

- [ ] GP-01 Sa vlasnikom proveriti/otvoriti developer nalog, tip naloga, identitet i trenutne uslove pristupa produkciji.
- [ ] GP-02 Potvrditi applicationId, ime, jezike, kategoriju i podatke vlasnika aplikacije.
- [ ] GP-03 Ponovo proveriti target API, tehničke uslove za AAB, biblioteke i aktuelne Play politike neposredno pre predaje.
- [ ] GP-04 Podesiti Play App Signing, generisanje release AAB-a i bezbedno čuvanje upload ključa uz vlasnika; tajne ne čuvati u izvorima.
- [ ] GP-05 Pripremiti politiku privatnosti sa javnim URL-om i pristupom iz aplikacije, prema stvarnom ponašanju i SDK-ovima.
- [ ] GP-06 Popuniti Data safety, ciljnu publiku, content rating, ads/app access i ostale primenljive deklaracije.
- [ ] GP-07 Pripremiti finalnu ikonu, feature graphic, snimke telefona/tableta, kratak i pun opis i kontakt za podršku.
- [ ] GP-08 Postaviti test izdanje na internal track uz odobren pristup nalogu; proveriti instalaciju i ažuriranje preko Play-a.
- [ ] GP-09 Pregledati Play pre-launch izveštaje, crashes/ANR i povratne informacije testera; rešiti nađene probleme.
- [ ] GP-10 Organizovati closed testing i angažovanje stvarnih testera prema pravilima konkretnog naloga; rok zavisi od njihovog učešća.
- [ ] GP-11 Pripremiti zahtev za production access kada je potreban i otkloniti eventualne primedbe; odobrenje daje Google.
- [ ] GP-12 Pripremiti finalni release kandidat, beleške verzije, zemlje distribucije, cenu/besplatnu distribuciju i plan objavljivanja.
- [ ] GP-13 Predati aplikaciju i pustiti u produkciju tek uz odobrenje vlasnika za konkretno izdanje.
- [ ] GP-14 Posle objave pratiti Android vitals, podršku i ponašanje izdanja; pripremiti ispravke sa novim versionCode-om i bez gubitka podataka.

## V2-ORG — organizacija i produktivnost

- [ ] V2-ORG-03 Završiti regresionu proveru postojećih favorites/filter funkcija; funkcija već postoji.
- [ ] V2-ORG-04 Potvrditi postojeći Trash/Restore/brisanje i čišćenje priloga, te definisati retention politiku.
- [ ] V2-ORG-07 Potvrditi postojeći prenosivi backup sa prilozima i korisničkim izborom destinacije; automatski backup je dodatni posao.
- [ ] V2-ORG-09 Definisati zaključavanje i šifrovanje, PIN recovery i skrivanje osetljivih pregleda; implementirati dogovorenu PIN/biometrijsku zaštitu.
- [ ] V2-ORG-10 Dovršiti custom pozadine i proveriti postojeće grid/dots/ruled pozadine i omiljene alate.
- [ ] V2-ORG-12 Testirati migraciju V1 dokumenata, organizaciju, pretragu, backup/restore i zaključavanje bez interneta.

## V2-MEDIA — slike i PDF

- [ ] V2-MEDIA-01 Dovršiti kvalifikaciju/licence/performanse već izabranih Android image/PDF adaptera na ciljanim uređajima.
- [ ] V2-MEDIA-02 Dovršiti UI i fizičke provere postojećeg image/file/camera importa, URI prava i minimalnih dozvola.
- [ ] V2-MEDIA-04 Dovršiti regresiju postojećeg move/resize/90° rotate/crop/duplicate/delete kroz istoriju i izvoz; proizvoljna rotacija ostaje dodatni obim.
- [ ] V2-MEDIA-05 Dovršiti izbor PDF stranica i kvalifikaciju velikih PDF-ova; import kao izmenjive anotirane stranice već postoji.
- [ ] V2-MEDIA-08 Potvrditi postojeći JPG i višestranični PDF izvoz; SVG ostaje zasebna implementacija/provera.
- [ ] V2-MEDIA-09 Testirati velike, oštećene i lozinkom zaštićene PDF-ove, nedostupne URI-je i čišćenje nekorišćenih priloga.

## V2-EDIT — napredno uređivanje i dijagrami

- [ ] V2-EDIT-01 Dovršiti nedostajuće rotacije i pravila clipboard-a; style/z-order/privatni copy-paste/duplicate već postoje i treba ih kvalifikovati.
- [ ] V2-EDIT-04 Dovršiti višestruko poravnanje/raspoređivanje i proveriti postojeći snap/pomoćne linije sa isključivanjem.
- [ ] V2-EDIT-05 Dovršiti korisničko prihvatanje/poništavanje i UI kvalifikaciju postojećeg Smart Shapes engine-a.
- [ ] V2-EDIT-06 Implementirati flowchart oblike: Start/End, Process, Decision, Database, Document i Input/Output.
- [ ] V2-EDIT-07 Implementirati smart konektore sa vezivanjem krajeva, prilagođavanjem pri pomeranju i pravilima brisanja povezanog objekta.
- [ ] V2-EDIT-08 Implementirati ručne mind maps: roditelj/deca, brzo dodavanje, boje i collapse/expand.
- [ ] V2-EDIT-09 Dodati Pencil i dodatne fontove ako su prioritet; proveriti font licence i ugrađivanje pri izvozu.
- [ ] V2-EDIT-10 Unaprediti stylus: pressure, dugmad/eraser gde su podržani i podesive finger/stylus gestove.
- [ ] V2-EDIT-11 Procena i po potrebi implementacija mini-map-a, pomerljivog toolbar-a i checkbox/task beležaka.
- [ ] V2-EDIT-12 Testirati nove objekte kroz save/load, istoriju, backup i svaki podržani format izvoza.

## V3-CLOUD — nalog, serverska osnova i sinhronizacija

- [ ] V3-CLOUD-01 Definisati cloud obim, budžet, regije, kvote, retention i izbor managed platforme ili sopstvenog backend-a.
- [ ] V3-CLOUD-02 Dovršiti monitoring i odvojena produkciona/staging okruženja; tehnički predlog, verzionisani API i lokalno pakovanje postoje.
- [ ] V3-CLOUD-03 Dovršiti uređajske tokove registracije, potvrde, resetovanja i stvarne SMTP isporuke; prijava/odjava/obnova sesije već postoje.
- [ ] V3-CLOUD-05 Dodati privatne cloud priloge i kvalifikovati njihove dozvole/kvote; vlasništvo/autorizacija dokumenata već postoje na serveru.
- [ ] V3-CLOUD-07 Završiti kvalifikaciju postojećeg outbox-a i WorkManager slanja, reconnect/cancel/account-switch i idempotentne oporavke.
- [ ] V3-CLOUD-08 Povezati zajednički editor i privatne priloge sa delta slanjem i sync/error/offline stanjima; trenutno postoje objavljivanje i read-only pregled.
- [ ] V3-CLOUD-09 Implementirati cloud backup i restore odvojeno od sinhronizacije; proveriti oporavak greškom obrisanih podataka.
- [ ] V3-CLOUD-10 Dovršiti purge cloud keša posle brisanja naloga, izvoz naloga i retention; serversko brisanje već postoji. Ažurirati privatnost/Data safety.
- [ ] V3-CLOUD-11 Testirati dva uređaja, istovremene offline izmene, brisanje naspram izmene, promenu naloga i neovlašćen pristup.

## V3-COLLAB — deljenje i istovremeni rad

- [ ] V3-COLLAB-01 Povezati postojeće serverske pozive/pozivnice/opoziv sa klijentskim tokom i jasnim pristupom primaoca.
- [ ] V3-COLLAB-02 Povezati postojeće serverske uloge sa zajedničkim editorom i komentarima u klijentu.
- [ ] V3-COLLAB-03 Dovršiti integraciju postojećeg modela konkurentnih delta izmena i preciznu semantiku zajedničkog Undo/Redo.
- [ ] V3-COLLAB-04 Implementirati real-time izmene, reconnect, redosled, duplikate i lokalno optimističko uređivanje.
- [ ] V3-COLLAB-05 Implementirati live cursors, oznake korisnika i presence sa ograničenjem učestalosti slanja.
- [ ] V3-COLLAB-06 Implementirati komentare na tabli/objektu, odgovore, resolve i ponašanje kada se objekat obriše.
- [ ] V3-COLLAB-07 Implementirati pregled aktivnosti i dogovorena obaveštenja sa odgovarajućim korisničkim kontrolama.
- [ ] V3-COLLAB-08 Testirati promenu/opoziv uloga tokom rada, offline povratak, više učesnika i dogovoreni maksimalni teret.
- [ ] V3-COLLAB-09 Pripremiti serverske alarme, backup proveru, operativni plan i postepeno uključivanje servisa.

## V4-AI — AI i prepoznavanje

- [ ] V4-AI-01 Napraviti evaluacione primere rukopisa/dijagrama, podržane jezike i merljive kriterijume kvaliteta.
- [ ] V4-AI-02 Proveriti lokalni OCR/rukopis naspram cloud servisa: kvalitet, uređaje, licencu, latenciju, privatnost i cenu.
- [ ] V4-AI-03 Definisati korisnikov izbor sadržaja za slanje, informisanje, retention i eksplicitno pokretanje cloud obrade.
- [ ] V4-AI-04 Implementirati serverski pristup plaćenim AI servisima, zaštitu ključeva, kvote, rate limiting i ograničenje troška gde su potrebni.
- [ ] V4-AI-05 Implementirati OCR slike/PDF-a sa pregledom, kopiranjem i ubacivanjem teksta na tablu.
- [ ] V4-AI-06 Implementirati izbor rukopisa → tekst, pregled rezultata i izbor zamene/dodavanja bez gubitka originala.
- [ ] V4-AI-07 Implementirati AI rezime ideja, odluka, zadataka i datuma uz mogućnost provere i uređivanja.
- [ ] V4-AI-08 Implementirati generisanje mind map-a i dijagrama u validiranom modelu izmenjivih objekata i konektora.
- [ ] V4-AI-09 Implementirati Beautify: prepoznavanje elemenata/teksta/veza, raspored i pregled pre prihvatanja.
- [ ] V4-AI-10 Omogućiti poništavanje prihvaćenog AI rezultata kao jedne logičke operacije; otkazivanje ne menja dokument.
- [ ] V4-AI-11 Implementirati Smart Search preko tekstualnog/OCR/rukopis sadržaja i kontrolisati privatnost indeksa.
- [ ] V4-AI-12 Obraditi timeout, offline stanje, prekoračenu kvotu, neispravan izlaz i ponovne zahteve bez duplih izmena.
- [ ] V4-AI-13 Proveriti kvalitet, troškove i činjenicu da sadržaj dokumenta ne može pokrenuti neodobrene radnje; ažurirati Play deklaracije.

## PRO — monetizacija, kada bude potvrđena

- [ ] PRO-01 Odrediti Free/Pro funkcije, cenu, pretplatu/jednokratnu kupovinu i održive cloud/AI kvote.
- [ ] PRO-02 Proveriti primenljiva Google Play Billing pravila i izabrati podržanu implementaciju pre prodaje digitalnih funkcija.
- [ ] PRO-03 Implementirati kupovinu, obnovu prava, pending/cancel/refund i verifikaciju kupovine gde je potrebna.
- [ ] PRO-04 Definisati offline prava i ponašanje nakon isteka Pro-a; postojeći lokalni rad ne sme neprimetno nestati ili se oštetiti.
- [ ] PRO-05 Testirati naplatu kroz Play test okruženje i uskladiti prikaz ponude, uslove i podršku.

## X — buduće platforme i održavanje

- [ ] X-01 Doneti zasebnu odluku o iOS-u: nativni Swift/SwiftUI i/ili deljenje čistog Kotlin jezgra kroz Kotlin Multiplatform nakon provere izvodljivosti.
- [ ] X-02 Definisati kompatibilan format dokumenta i API ugovore za iOS, web i desktop pre njihove implementacije.
- [ ] X-03 Napraviti zaseban backlog interfejsa, input-a, skladišta, izvoza i prodavnice za svaku odobrenu dodatnu platformu.
- [ ] X-04 Održavati podršku za Android/Play promene, zavisnosti, licence, bezbednosne ispravke i migracije podataka.
- [ ] X-05 Voditi povratne informacije, prioritete grešaka, release beleške i regresione scenarije nakon svake objave.

## Spoljne zavisnosti i dodatni obim

Javni cloud/SMTP/AI zahtevaju servisne naloge, HTTPS domen, region, budžet i credentials. Play zahteva identitet izdavača, vlasničke ključeve, pristup nalogu i odobrenje konkretnog izdanja. Fizička stylus/performance kvalifikacija zahteva odgovarajuće uređaje. PRO/monetizacija i dodatne platforme ostaju uslovne odluke; ne predstavljaju već završeni ili automatski odobreni novi proizvod.

Kontrola potpunosti: 105 otvorenih identifikatora šireg plana preneto je u ovaj pregled. Dokazi i ograničenja su u STATUS.md i SECURITY.md. Stavke se zatvaraju tek nakon odgovarajuće provere.
