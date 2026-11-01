# Whiteboard — potpuni preostali rad posle 0.4.0

Datum: 1. novembar 2026. Spisak je izveden iz LOCAL-REBUILD-PLAN.md, LOCAL-STATUS.md, LOCAL-QUALIFICATION.md, izvorne WHITEBOARD-VISION.md i pregleda aktivnog koda. Ne predstavlja potvrdu da su sve stavke iz šire vizije uključene u obim 0.4.0.

Lokalni plan 0.4 ima otvoren L40. Njegov checkbox nije inventar svih budućih funkcija proizvoda. Postojeće lokalne funkcije, njihovi prolazni testovi i ograničenja ostaju opisani u LOCAL-STATUS.md. Fizički uređaji su odloženi po izboru korisnika. AI, backend, cloud, nalozi, saradnja i Play objava nisu vraćeni u aktivni obim.

## Z — završetak sadašnje lokalne verzije i inženjerskog procesa

- [x] Z01 Prostor i razvojno okruženje. Pronađen i pojedinačno uklonjen novi QA snapshots/default_boot/ram.img od 3.221.291.008 bajtova. Pokretanje koristi no-snapshot i isključuje QuickbootFileBacked. Stvarni API 36 boot prolazi bez novog RAM fajla; najveći zabeleženi porast tokom završne provere 1.654.480.896 bajtova, ispod limita 3.400.000.000. Dokazi: artifacts/task-01-storage-recovery.json i task-01-startup-verification.json. Virtuelna veličina Google Play userdata particije nije isto što i fizičko zauzeće; nastaviti praćenje. SDK, postojeći korisnički AVD i Windows pagefile podešavanja nisu menjani.
- [x] Z02 Git početna verzija sačuvana kao 0e9d1d0 na grani codex/local-engineering. Izvor, wrapper, dokumentacija i testovi su praćeni; kredencijali i generisani artefakti izuzeti. Source-secret scan prolazi; gradlew ima executable bit. Generisani notices zadržavaju provereni format svog generatora.
- [x] Z03 Origin je povezan sa korisnikovim repozitorijumom https://github.com/cmarko-gits/Whiteboard-.git bez prepisivanja main istorije. CI 41341043323 za commit 550bd93 prolazi verify i svih pet Android poslova; pregledani XML-ovi i paket evidentirani su u artifacts/task-11-family-packages.json. Play distribucija ostaje odložena.
- [ ] Z04 Zatvoriti L40 izvršavanjem matrice API 26/30/33/36, uz proveru dostupne 36.1 slike kada resursi dozvole. Lokalni API 36 i 36.1 sada prolaze po 47/47 app + 13/13 data testova, uz 4096-byte stranice. CI 41293334740 prolazi na API 33/36, dok 26/30 imaju konkretne UI test nalaze koji se ispravljaju. API 30 je prolazio u prethodnom run-u; svi prolazi još nisu objedinjeni za novog kandidata. Promena emulator system image 36 → 36.1 nije dokazala in-place OS upgrade. Detalji su u EXECUTION-PROGRESS.md.
- [ ] Z05 Proći cele tokove sa stvarnim TalkBack-om: onboarding, biblioteka/folderi/Trash, kreiranje i čuvanje table, svi dijalozi, boje, Objects, stranice, import/export i podešavanja. Proveriti izgovor, redosled i vraćanje fokusa, modalne dijaloge i greške. Trenutni stvarni TalkBack test dokazuje virtuelni fokus/aktiviranje beleške; ostale automatizovane semantičke provere već postoje.
- [ ] Z06 Dovršiti ručni pregled kontrasta, čitljivosti i fokusa kroz Light/Dark/System i korisnički izabrane boje, naročito tekst na obojenim beleškama. Font 150/200% i 320–1600 dp već imaju prolazne testove; proširiti pregled na preostalu matricu i ispraviti pronađene probleme.
- [x] Z07 Izvršen ručni pregled komponenti/URI grant-ova, parsera, skladišta, SQL-a, izvoza i dijagnostike: LOCAL-ENGINEERING-REVIEW.md. ZIP nalazi imaju ispravke i tri prolazna regresiona testa (format 36/36). Source i Git history secret scan bez prijavljenih tajni; istorijski log navodi šest commit-a. Granice pokrivenosti su navedene, novi finalni release gates ostaju Z09. Nezavisan pentest nije predstavljen kao izvršen.
- [ ] Z08 Otkloniti konkretne greške pronađene u Z04–Z07 i hardverskim proverama; dodati regresione testove gde štite stvarno ponašanje. Ne postoji unapred utvrđen broj novih grešaka i ne označavati već implementirane alate kao nenapisane.
- [ ] Z09 Posle eventualnih izmena ponoviti pogođene testove i finalne gates: stil/arhitektura, JVM/device, lint, minified release, licence, dependency/secret scan, manifest/DEX politika, potpis i 16 KB kompatibilnost. Aktuelna 0.4 verzija već ima prolazne rezultate; ovo je završni uslov za izmenjenog kandidata.
- [ ] Z10 Za konačnog kandidata uskladiti korisnička uputstva, specifikaciju, release notes, poznata ograničenja, mapu zahteva na dokaze i hash-eve paketa. Dokumentovati granice importa/izvoza, recovery/backup i ponašanje pri deinstalaciji. Javna politika privatnosti i vlasnički podaci su u grupi P.

## F — lokalne funkcije iz šire vizije koje nisu završene u 0.4

Ovo je proširenje sadašnjeg lokalnog obima. Za svaku uvedenu funkciju podrazumevaju se domenski model/komande, UI, pristupačnost, čuvanje, format/migracije kada su potrebni i odgovarajući testovi.

- [x] F01 Parcijalna gumica završena za lokalni emulator obim, 0.5.1/code 7, source 550bd93. CI 41341043323: 151 JVM; pet konfiguracija po 66 app + 13 data, bez grešaka/preskakanja; minified release smoke API 26/36. ZIP/ELF 16 KB, manifest/DEX, OSV 147, licence i source scan prolaze. Razvojni APK i tačni hash-evi: artifacts/task-11-family-packages.json. Kriterijumi, dokazi i hardverska ograničenja: PARTIAL-ERASER.md.
- [ ] F02 Implementacija 0.6.0/code 8 na codex/object-rotation: proizvoljna relativna rotacija objekata/grupa, afinni okvir, tačan Fill/picking, Undo/Redo, čuvanje formata 4 i renderer/izvoz. Napisani novi domenski/format/Android testovi još zahtevaju CI izvršenje; funkcija nije kvalifikovana. Odluke i kriterijumi: OBJECT-ROTATION-DESIGN.md.
- [ ] F03 Pomeranje objekta jedan nivo napred/nazad. Bring to Front/Send to Back već postoje.
- [ ] F04 Eksplicitno poravnanje više izabranih objekata po ivicama i centrima. Automatski Snap/guide već postoji; ravnomerna raspodela razmaka može biti dodatni deo ove funkcije.
- [ ] F05 Pametni konektori vezani za ID-jeve i tačke objekata, koji prate move/resize, pravilno se kopiraju i reaguju na brisanje/Undo. Sadašnje strelice su nezavisne linije.
- [ ] F06 Pravi lokalni mind-map model: roditelj/deca, brzo dodavanje i uređivanje čvorova, boje, veze i collapse/expand. Postojeći Mind Map template sadrži obične beleške i strelice.
- [ ] F07 Flowchart objekti/paleta: Start/End, Process, Decision, Database, Document, Input/Output i povezivanje preko F05. Postojeći Flowchart template nije kompletan flowchart editor.
- [ ] F08 Zaključavanje pojedinačne table PIN-om i Android biometrijom. Definisati opseg zaštite, ponovno zaključavanje, ponašanje pri izgubljenom pristupu i pravila za preview/export/backup. Ako cilj uključuje zaštitu podataka na disku, posebno projektovati enkripciju i upravljanje ključevima; UI brava sama nije ta zaštita.
- [ ] F09 Automatski lokalni backup: izabrana destinacija, raspored, broj zadržanih kopija, status poslednjeg uspeha, greške, restart uređaja i gubitak pristupa destinaciji. Ručni editable ZIP backup/restore već postoji. Pri uvođenju pozadinskog rada uskladiti lokalnu arhitekturu i APK allowlist politiku; cloud se ne uvodi time.
- [ ] F10 Opcionalno automatsko čišćenje Trash-a posle izabranog perioda, sa jasnim pravilima i upozorenjem o nepovratnom brisanju. Ručni Restore/Delete Permanently već postoje.
- [ ] F11 Storage ekran: veličine tabli/priloga/cache-a/Trash-a, raspoloživ prostor, bezbedno čišćenje potrošnog cache-a i pristup backup akcijama. Aktivni dokument, recovery, istorija i clipboard ne smeju izgubiti potrebne priloge.
- [ ] F12 SVG izvoz uz definisano ponašanje za tekst, fontove, slike/PDF podloge i grupe. PNG/JPG/PDF izvoz već postoji.
- [ ] F13 Podešavanja mreže/pozadine, kao što su razmak i izgled linija/tačaka. Izbor grid/dots/ruled i boje pozadine već postoji.
- [ ] F14 Izbor jezika unutar aplikacije i očuvanje izbora na podržanim OS verzijama. EN/sr-Latn resursi i sistemska lokalizacija već postoje; nema zasebnog in-app language birača.
- [ ] F15 Lokalizacija početnog sadržaja šablona. Nazivi interfejsa su lokalizovani, dok TemplateFactory upisuje početne engleske tekstove u dokument.
- [ ] F16 Dodatne porodice fontova uz dosledan prikaz/izvoz i proverene licence. Veličina, bold, italic, boja i poravnanje već postoje.
- [ ] F17 Checklist/task beleške sa sačuvanim checkbox stanjem, uređivanjem i Undo/Redo. Obične sticky notes već postoje.
- [ ] F18 Kontekstni meni objekta na dugi pritisak, sa odgovarajućom keyboard/accessibility alternativom. Akcije već postoje kroz selekcione i accessibility kontrole; touch long-press meni nije implementiran.
- [ ] F19 Poseban vizuelni karakter Pencil alata. Alat PENCIL već postoji; renderer trenutno koristi zajedničko iscrtavanje poteza, bez posebne grafitne teksture.

## O — dodatne opcije, nisu uslovi završetka lokalne 0.4

- [ ] O01 Mini-map za pregled velike table i brzo pomeranje pogleda.
- [ ] O02 Pomerljiv/prilagodljiv toolbar sa očuvanjem položaja i bez zaklanjanja važnih kontrola na malom prozoru.
- [ ] O03 Sistemski clipboard kao izričita opcija, uz pravila formata, priloga i privatnosti. Postojeći privatni copy/cut/paste je nameran i već funkcioniše.
- [ ] O04 Naprednije export opcije: selekcija ili region, providna PNG pozadina, kvalitet/dimenzije u bezbednim granicama. Trenutni izvoz koristi postojeća definisana ograničenja i uključuje pozadinu.
- [ ] O05 Pressure/tilt podrška za kompatibilnu olovku: unos/model/renderer, kompatibilnost formata i kalibracija na stvarnom hardveru. Nije uslov izvorne V1 verzije.

## H — hardverski rad odložen po korisnikovom izboru

- [ ] H01 Instalacija i upgrade bez gubitka podataka na stvarnom minimalno podržanom uređaju, reprezentativnom telefonu i velikom tabletu; konačan izbor uređaja i prihvatljivih granica.
- [ ] H02 Provera stylus/prst/dlan ponašanja, palm rejection-a i prekida poteza na odgovarajućim uređajima; implementirati popravke prema nalazima. Osnovni stylus/finger režimi postoje.
- [ ] H03 Profilisati optimizovani release: CPU/GPU, p50/p95/p99 frame time, spore frejmove i input-to-display latenciju. Potvrditi ili korigovati cilj od 60 Hz na dogovorenim scenarijima; sadašnji emulator rezultat ga ne potvrđuje.
- [ ] H04 Duži rad, low-RAM uređaj, velike slike/PDF dokumenti, ponavljani import/export, memorija, baterija i zagrevanje; optimizovati konkretno izmerena uska grla i ponovo izmeriti.
- [ ] H05 OEM sistemske integracije: stvarna kamera, SAF/file provideri, IME, fizička tastatura, mouse/trackpad i dodatni accessibility unos. Emulator i simulirani input nisu isti dokaz.
- [ ] H06 Stvarni tablet/foldable prelazi, šarka i desktop/freeform režim sa promenom veličine prozora. Pravi split-screen i emulirane širine već imaju prolazne rezultate.

## P — kasnija javna distribucija, trenutno van aktivnog obima

- [ ] P01 Vlasnik/izdavač, konačan applicationId, naziv, kontakt za podršku i privatnost.
- [ ] P02 Vlasnički upload/signing ključ, bezbedna rezervna kopija i potpisivanje konačnog AAB-a. APK 0.4 koristi razvojni potpis, AAB je unsigned build artefakt.
- [ ] P03 Završna politika privatnosti, javni URL i deklaracije koje odgovaraju stvarnom ponašanju tadašnjeg paketa.
- [ ] P04 Finalna ikona/identitet, opisi, screenshot-i telefona/tableta i ostali materijali za distribuciju.
- [ ] P05 Ponovna provera tada važećih target SDK/ABI/16 KB i ostalih uslova prodavnice, verzionisanja i konačnih release artefakata. Ranija provera nije trajno odobrenje prodavnice.
- [ ] P06 Odgovarajuće interno/zatvoreno testiranje i produkciona predaja kada Play obim bude vraćen u rad. Objavljivanje nije izvršeno ovim planom.
- [ ] P07 Odluka da li uopšte postoji Free/Pro model; tek zatim pravila cena/ograničenja/kupovina. Naplata se ne uvodi automatski.

## Šta se ovde ne vraća u rad

Backend, cloud/sinhronizacija, nalozi, saradnja/komentari uživo, AI/OCR/rukopis i njihove produkcione infrastrukture ostaju van trenutnog lokalnog obima. iOS/iPadOS, web, Windows/macOS kao zasebne aplikacije takođe nisu završni zadaci Kotlin Android verzije. Android tablet i podržani Android prozori pripadaju ovom proizvodu; TV/Wear/Auto/XR nemaju implicitno obećanu podršku.

Redosled rada: Z01–Z03 omogućavaju ponovljivo izvršenje Z04–Z10. Funkcije F i opcije O proširuju obim i svaka mora dobiti kriterijume prihvatanja pre implementacije. H ostaje odložen dok se ne promeni odluka o fizičkim uređajima. P ostaje odložen dok se ne vrati javna distribucija. Zatvaranje stavke zahteva konkretan kod/dokument i odgovarajući dokaz, ne samo checkbox.
