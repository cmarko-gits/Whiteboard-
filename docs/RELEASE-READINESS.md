# Prva javna lokalna verzija — završni rad

Presek 8. novembra 2026. Završene su 2 od 9 celina (1 — početni neuspešni testovi; 3 — opseg); 7 ostaje otvoreno ili u proveri. Ovo je aktivni plan od devet celina za pripremu izdanja, uz širi istorijski backlog od 47 stavki. Brojevi ta dva plana nisu isti inventar. Objavljivanje, pristup Play nalogu i ocena Google review-a nisu izvršeni ovim dokumentom.

## Opseg

Prvo izdanje ostaje offline Kotlin Android: postojeće crtanje/bojenje, objekti, veze, mape uma, stranice, organizacija, recovery, privatni clipboard i postojeći import/export. Dijagrami toka iz lokalne grane `codex/flowchart-foundation` / b78b435 ostaju zasebno sačuvani i nisu spojeni u kandidata. Kandidat polazi od `codex/mind-map-editor` / 1b83faa, app 0.8.0/code 17, dokument 6/delta 2/Room 4. AI, cloud, backend, naplata, PIN, novi fontovi, SVG i druge dodatne funkcije nisu uslov ovog izdanja.

## Devet celina i kriterijumi

| Broj | Rad | Status i dokaz potreban za završetak |
|---|---|---|
| 1 | Android 13/16 neuspešni testovi | Završeno: 699288b / CI 42518487169, sva tri posla prolaze. Po četiri ciljana native slučaja na API 33/36, plus API 36 R8/jezički tok i clean health checkpoint-i. To ne zamenjuje završnu punu matricu. |
| 2 | Mape uma | Otvoreno. Native i optimizovani create/edit/color/search/rotate/reparent/move/collapse/Undo/save/reopen, svih pet konačnih profila. |
| 3 | Zamrznut opseg prve verzije | Završeno za kandidat 699288b: izdvojena grana bez F07 koda, 0.8.0/code 17 i format 6. Budući paketi moraju ostati na ovom opsegu; konkretne korisničke funkcije opisuje postojeće MIND-MAP-EDITOR.md/uputstvo. |
| 4 | Pouzdanost podataka | Otvoreno za tačan kandidat. Room migracije, recovery, save race/cancellation, stari dokumenti, backup/restore, oštećeni ulazi i bezbedna greška pri nedostatku prostora. |
| 5 | Prilagodljivost i pristupačnost | Automatizovani dokazi postoje za prethodne izvore; ponoviti pogođene tokove i završiti ručni vizuelni/govorni pregled. Fizički uređaji i slušni TalkBack nisu zamenjeni testnom semantikom. |
| 6 | Performanse i memorija | Otvoreno. Proveriti konačni release i reprezentativne scene, čuvanje/izvoz, velike priloge i granice; popraviti merljive probleme. Ne tvrditi univerzalnih 60 Hz ili fizički GPU profil iz emulatora. |
| 7 | Bezbednost konačnog kandidata | Otvoreno. Review promenjenih granica, hostile input/regresije, source/history tajne, zavisnosti/licence i manifest/DEX/permissions. Prethodni čist izvor nije dokaz novog. |
| 8 | Završna regresija | Otvoreno. Isti tačan source: svih pet Android profila, JVM, style/architecture/lint, tri R8 toka i jezičko vraćanje; bez neobjašnjenih neuspeha/preskakanja. Stvarni telefon/tablet ostaju odloženi po ranijoj odluci korisnika. |
| 9 | Distribucioni paket | Otvoreno. Konačan AAB, vlasnički upload potpis i zaštićena rezervna kopija ključa, provera paketa/provenance/ABI/16 KB i instalacije. Razvojni potpis nije vlasnički Play paket; account/listing/policy/testeri su zaseban spoljni rad. |

## Postupak provere

Popravke prvo koriste lokalne pogođene testove i dva pogođena udaljena profila (trenutno 26/30) na grani `codex/release-readiness`. Taj prolaz ima četiri app testa (dva mind-map i dva dialog-driver), bez R8/full data regresije; ne predstavlja punih 138 app i 13 data. Ne sme biti korišćen kao konačna kvalifikacija ili paket dokaz. Isti commit na kandidatskoj grani pokreće punu petoprofilnu matricu. Neuspešna testiranja ostaju sačuvana, bez tihog uklanjanja asercija ili ponavljanja dok slučajno ne prođu.

System UI startup nalaz izvora 1b83faa je potvrđen: prvi API 36 failure PNG BC6852DE0814FB463480BC0227EE935653F295C34FBE801F3FB6113820001F3D i XML 73C4BA2DC7A6AFFEFDEF4CBBEB3BC366172A8534414E95FDE112BBF557A2A815 prikazuju sistemski ANR dijalog. Digest-verified originalni log beleži `ANR in com.android.systemui` / `failed to complete startup` u 04:34:44 UTC, pre launcher-ready 04:34:57 i prvog app testa 04:35:30. I početni i finalni lastanr izlaz tvrde da ANR nije bilo. Novi checkpoint čita i filtrirane logcat ANR/fatal zapise; ne zatvara dijalog niti briše logove. Build daemon se zaustavlja pre emulator boot-a da oslobodi memoriju. To je infrastrukturna dijagnoza, a ne prolaz API 36 aplikacije.

Dokazi: `artifacts/task-16-1b83faa-evidence.json`, `task-16-1b83faa-36-pixel_2-diagnostics.json`, `release-step-01-fixture-compile.log` i novi imenovani CI rezultati. Dodatni disk limit ostaje 3.400.000.000 bajtova; nema novih lokalnih SDK/system-image preuzimanja. Automatske provere ne predstavljaju nezavisan pentest ili potvrdu odsustva svih nepoznatih grešaka.


## Izvršeno u ovom prolazu

- 699288b / CI 42518487169: verify potvrđuje 519 JVM (358 core + 77 format + 5 data + 79 app), stil/arhitekturu/lint i build. OSV 155 nema prijavljenih poznatih nalaza; 441 neignorisan source fajl prolazi secret scan. APK policy je vezan za B02DCF6DA35ED12D658A61BB63693998F93AF58CB2F06DD4ADDF3DE1D15FBA68. Oba API 33/36 profila prolaze četiri ciljana slučaja; API 36 prolazi i ceo R8 mind-map/resize/save/reopen i oba jezička process-restart toka uz vraćen jezik. Svi checkpoint-i su čisti. Konačni digest-verified dokaz je release-focus-699288b-evidence.json; nema retry-ja.
- Novi FailedWriteTest proverava stvarni SQLite abort drugog uvoza, rollback prvog dokumenta/chunk-ova/indeksa, očuvan saved/draft i uspešan kasniji retry. Lokalni 519 JVM, kompilacija 136 native slučajeva i arhitektura prolaze u release-step-04-local-validation.log; novi native slučaj još treba izvršiti na uređaju.
- Novi release runtime harness zadržava sirove startup/meminfo/framestats zapise i ne pretvara nedostajuće vrednosti u nulu. Host negativne kontrole parsera prolaze. Merenje na CI uređaju tek sledi i nije dokaz fizičkih performansi.
- Lokalni Gitleaks pregled svih refs obuhvata 128 revizija bez prijavljenih tajni; zasebno prolaze novi tools fajlovi. Nije nezavisan pentest.
- Zaseban RSA-3072 upload ključ je napravljen van repozitorijuma, u privatnom direktorijumu pod Windows ACL/DPAPI zaštitom. Ponovno otvaranje ključa i poklapanje javnog sertifikata provereni su dva puta. Prvobitni public-only PEM reader/newline proceduralni neuspehi su ispravljeni uz isti ključ, bez regenerisanja ili izlaganja lozinke. Prenosiva nezavisna rezervna kopija i potpisani konačni AAB još nisu urađeni. AAB upload potpis i Play app-signing identitet su različite uloge; razvojni APK ne zamenjuje taj postupak.


Konačni API 36 full prolaz dodatno proverava binarnu nadogradnju 0.7.2 → kandidat: prethodni originalni APK potiče iz immutable artefakta 13446823155, sa zasebno proverenim archive/APK hash-om, i oba paketa koriste isti ephemeral CI potpis. Stara verzija pravi i snima stvarni crtež, nova se instalira isključivo sa install -r, a postojeća tabla i okvir poteza moraju biti očuvani. Nema uninstall/clear data. Ovo ne zamenjuje fizičku nadogradnju ili sve OEM slučajeve. Prethodni artefakt je vremenski ograničena CI baseline zavisnost; za buduća izdanja mora se izabrati i proveriti tada odgovarajuća prethodna verzija.


Ciljani API 33 vizuelni pregled: obojeni koren PNG 4091BF2631D18A91142BE3440252599E453C6F0419494CD0B2C263F0CDCBFA46 pokazuje plavu belešku sa belim naslovom i zatvorenu tastaturu; saved-map PNG 7584444E45E80CD973FC5F5F5506A0870F547EFBEDEFDC7707A57658E2E2795C pokazuje svih 13 izabranih vidljivih objekata i donji čvor iznad zoom kontrola. Tačan bitmap pixel, model i Undo/Redo/save/reopen asercije prolaze. Ovo su source-specific snimci, ne opšta ručna TalkBack/tema kvalifikacija.


## Nalaz vizuelnog pregleda — granica native platna

Pregledan API 36 R8 child-input PNG izvora 699288b, SHA-256 6FA9E0C8D7497AB2D31B416CEC293405FE10EB2F2C3DF4033F1EC3CDD19F685D, pokazuje plavi crtež izvan lokalnog platna, preko zaglavlja/statusne oblasti posle resize-a. WhiteboardView je ograničavao izbor objekata prema vidljivom okviru, ali nije eksplicitno klipovao native Canvas; veliki/presečeni objekti i privremeni potezi mogli su iscrtavati van View-a kada roditelj ostavi širi clip.

onDraw sada čuva roditeljsko stanje, postavlja lokalni 0..width/height clip pre kamere i zatvara ga tek posle accessibility focus prikaza. finally vraća roditeljski clip/matricu/save count. drawViewport zadržava postojeću geometriju, history i eksport renderer. Nova dva native slučaja proveravaju stvarne bitmap margine izvan malog prevedenog View-a, velike objekte, selekciju, dve kamere, live potez koji izlazi iz View-a i očuvan pun dokumentni krajnji položaj. Testovi nisu bili izvršeni na starom source-u; snimak i pregled koda su dokaz početnog nalaza, a novi device prolaz mora potvrditi ispravku.

Lokalna kompilacija i arhitektura prolaze u release-step-05-viewport-compile.log. Očekivani konačni inventar sada je **519 JVM + 138 app + 13 data po profilu**; ukupno 670 jedinstvenih slučajeva, bez sabiranja OS ponavljanja. C89c596/CI 42520415145 je superseded ovim konkretnim vizuelnim ispravkama; njegov delimičan prolaz nije konačna kvalifikacija. Pre-superseding stanje je sačuvano. Nova matrica mora obuhvatiti rollback/import, binary upgrade i release runtime dijagnostiku uz nove clipping regresije.


## 20dc050 — nova matrica, dva razdvojena nalaza

Verify prolazi 519 JVM i paket gates; API 33 i API 26 tablet prolaze 138 app + 13 data. API 26 telefon i API 30 imaju po jedan neuspeh, u istom celom mind-map toku; nova dva viewport i import-rollback slučaja prolaze i na tim profilima. API 36 u vreme dijagnoze još radi. Nema retry-ja ili pripreme distribucije neuspešnog izvora.

API 30, digest-verified artefakt 13634166773 / SHA-256 42304A519DAF6220519AD2CA8ECC5749F035B630B11E6121DBFBFCE80326EB23, potvrđuje Native child izmenu u editoru sa zaostalom tastaturom. MapCommit više ne vraća pending=false posle uspeha pre nego što Compose ukloni dijalog: proverava živi request ID, drži potvrđen unos onemogućenim do disposal-a, a re-enable dozvoljava samo kada isti zahtev ostaje otvoren za retry. Pozni autofocus proverava pending posle frame-a. Title/move forme imaju key vezan za request ID, da nova priprema ne nasledi pending stanje stare.

API 26 telefon, artefakt 13633588886 / SHA-256 E51A4EE662C69F85A3E42A2211314D89FA118EC9F823CAE923A1A88D74FB4785, staje posle rotacije na pronalaženju Node title. Pregledani PNG i povezani XML zaista imaju vidljiv Edited sibling EditText i Node title kao njegov potomak. Fixture sada osvežava ograničeno povezano accessibility stablo pre istog tačnog labeled-input selektora. Timeout ostaje 10 s; nema biranja prvog proizvoljnog EditText-a, Back-a ili uklanjanja asercija.

Zbog smanjenog ukupnog prostora na C: dodatni izveštaji pregledani su u memoriji, uz proveru originalnog archive digest-a, bez snimanja ZIP/PNG na disk. U projektu je izmereno oko 58 MB poslednje izmenjenih datoteka i oko 5,5 MB novog/izmenjenog Gradle cache-a; ukupni pad slobodnog prostora nije time objašnjen. Nema novih lokalnih SDK/system-image preuzimanja, lokalnog emulatora ili novih teških buildova u ovom delu. Spoljna potrošnja prostora se razjašnjava sa korisnikom; globalni package-write guard nije isključen.
