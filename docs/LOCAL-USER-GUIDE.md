# Whiteboard — lokalno korišćenje

Najnoviji lokalni razvojni kandidat je 0.7.2: [rezultati, otvoreni nalazi i ograničenja](LOCAL-0.7.2-CANDIDATE.md). Settings → Language bira sistemski jezik, English ili Srpski (latinica). Izbor se pamti posle ponovnog pokretanja; otvorena tabla i nesnimljene izmene ostaju dostupne pri promeni jezika. Nazivi komandi ispod odgovaraju engleskom interfejsu.

Dokument sa previše objekata ili predugim tekstom može prekoračiti bezbednosni limit obrade. Aplikacija tada traži manje objekata ili kraći tekst. Neuspešno čuvanje zadržava prethodno sačuvanu reviziju i nesnimljene izmene; smanji sadržaj i ponovi Save. Recovery nije nezavisan backup.

## Pametne veze

Shapes → Connection bira alat za povezivanje. Povuci od jednog oblika, beleške, teksta, medija ili grupe do drugog objekta. Strelica se vezuje za njihove ivice i prati pomeranje, promenu veličine i rotaciju. Prekid dodira, drugi prst ili promena alata otkazuju nedovršen gest. Za precizan izbor koristi More actions → Connect objects.

U dijalogu Start i End biraju objekte kroz pretragu. Attachment point bira levu, gornju, desnu, donju stranu ili centar u okviru objekta; port prati i rotaciju. Free end omogućava X/Y položaj, decimalnu tačku ili zarez i dugme ±. Arrow at end bira završnu strelicu. Izbor istog objekta za oba vezana kraja onemogućava Apply. Cancel zatvara dijalog bez izmene dokumenta.

Izabrana veza nudi Selection actions → Edit connection i Detach connection. Detach zadržava trenutni oblik linije i pretvara vezane krajeve u slobodne; Undo vraća vezivanje. Za pomeranje vezane veze izaberi i sve njene ciljeve ili koristi Detach. Properties uređuje boju, debljinu i providnost izabrane veze. Za Group prvo izaberi samo ciljne oblike.

Brisanje cilja ostavlja kraj veze na poslednjem položaju, a jedan Undo vraća i cilj i vezivanje. Copy/Paste i Duplicate prave nove identitete: kopirana veza prati kopirane ciljeve; veza ka cilju koji nije kopiran dobija slobodan kraj u kopiji. Save/reopen, recovery, editable ZIP i PNG/JPG/PDF koriste iste krajnje tačke. Dokumenti sa vezama koriste format 5 i zahtevaju verziju aplikacije koja ga podržava; postojeći dokumenti formata 1–4 ostaju čitljivi.

Ostaje otvoren nalaz memorijske cene veoma složenog JSON ulaza: [Z08 pregled](JSON-RESOURCE-BUDGET.md). Kandidat ne predstavlja završetak svih bezbednosnih, hardverskih i performance provera aplikacije.

## Table i biblioteka

New board otvara naziv i izbor šablona. Početni tekst i ime prve stranice nove table koriste izabrani jezik; kasnija promena jezika ne prevodi ranije upisan sadržaj. Folders organizuje lokalne table; Favorites prikazuje omiljene table. Pretraga obuhvata i tekst u dokumentima. Meni Actions for… sadrži raspoložive akcije za tu tablu.

Move to Trash zadržava tablu za kasniji Restore. Delete permanently? traži potvrdu i nepovratno briše izabranu tablu. Trash nema automatski vremenski rok brisanja u ovoj verziji.

## Crtanje, boje i objekti

U Select režimu drži prst na objektu da otvoriš Selection actions. Ako je objekat već izabran, ostaje cela višestruka selekcija. Edit uređuje pojedinačan tekst/belešku; Duplicate i Delete koriste istu Undo/Redo istoriju. Dugi meni možeš skrolovati. Sa fokusom na platnu koriste se Menu ili Shift+F10; accessibility čvor nudi Object actions. Pomeranje prsta, drugi dodir i promena stranice otkazuju nedovršen dugi pritisak.

Toolbar bira olovku, marker, oblike, tekst, sticky belešku, selekciju, pomeranje i gumicu. Settings → Eraser bira Whole stroke za brisanje celog poteza ili Partial stroke za brisanje dela preko kog pređeš. Jedan završen gest se vraća jednim Undo; otkazan gest ne briše sadržaj. Izbor režima se pamti. Pencil ima grafitno zrno uz izabranu boju, širinu i providnost; isti izgled koristi se u dokumentu i izvozu. Pressure/tilt nisu deo ove verzije.

Fill color menja boju pogođenog podržanog oblika/beleške ili pozadine kada se dodirne prazna površina. Započeti potez prevlačenjem ne potvrđuje fill tap. Undo/Redo vraćaju izmene. Canvas background u More actions bira izgled i boju stranice.

Color picker omogućava vizuelni izbor, šestocifreni HEX i RGB kontrole. Save color dodaje boju u pripremljenu paletu. Apply potvrđuje boju i izmenu palete; Cancel odbacuje pripremljene izmene. Netačan HEX onemogućava potvrdu. Kada tastatura zauzme prostor, Done/Back zatvara tastaturu i oslobađa prikaz ostalih kontrola.

Sticky beleška automatski bira čitljiv tamni, crni ili beli tekst prema svojoj boji i boji stranice. Kod providne beleške preko slike ili drugog objekta čitljivost zavisi i od sadržaja ispod nje. Samostalni tekstualni objekat zadržava korisnikov izbor boje.

More actions → Objects pretražuje celu stranicu, uključujući objekte van ekrana. Izbor otkriva objekat i selektuje ga. Pages upravlja stranicama. Selekcione komande i dostupne akcije objekta omogućavaju izmenu bez preciznog prevlačenja.

Od 0.6.1 Rotate selection otvara relativni ugao; prihvata decimalnu tačku ili zarez, kao i negativan ugao. Više objekata/grupa rotira se oko zajedničkog centra. Apply pravi jednu Undo komandu, a Cancel ne menja tablu. Promena veličine rotiranog teksta skalira njegov postojeći prikaz. Sačuvaj tablu da zadržiš promenu.

Od 0.6.2 Selection actions → Bring forward one layer / Send backward one layer pomera izbor preko jednog susednog neizabranog objekta. Uzastopni izabrani objekti ostaju blok; njihov međusobni redosled se ne menja. Na granici je stavka isključena. Bring to front / Send to back pomera na kraj/početak slojeva. Undo/Redo vraća jednu celu akciju.

U 0.6.3 izaberi najmanje dva objekta, zatim Selection actions → Alignment. Šest dugmadi poravnava ivice ili horizontalne/vertikalne centre prema zajedničkom okviru. Veličina i rotacija se čuvaju; grupa se pomera kao celina. Cancel ne menja tablu. Jedna akcija se vraća jednim Undo; zatim možeš Redo i Save. Pametni konektori još nisu gotov alat; postojeće Arrow linije su nezavisne.

## Čuvanje, oporavak i backup

Save čuva potvrđenu reviziju. Izmene nastale dok upis traje mogu ostati označene kao Unsaved changes. U Settings može se uključiti automatsko čuvanje završenih izmena.

Recovery čuva odvojen nacrt sa odlaganjem približno 700 ms. Pri ponovnom otvaranju table ponuđeni Recover vraća raspoloživi nacrt. Nagli prekid pre završenog upisa ili usred gesta može izgubiti najnoviji deo rada. Recovery nije zamena za backup.

Settings sadrži izvoz i uvoz ZIP backup-a sa prilozima. Restore pravi nove lokalne kopije dokumenata. Napraviti backup izvan privatnog skladišta aplikacije pre deinstalacije, čišćenja app podataka ili promene uređaja: te radnje brišu privatne table. Automatski Android backup i transfer podataka aplikacije su isključeni.

## Slike, PDF i izvoz

Uvoz prihvata datoteku izabranu sistemskim picker-om; ne preuzima proizvoljni URL. Pojedinačna ulazna slika/PDF ima limit 16 MiB. Slike se subsampluju radi memorije. PDF uvoz podržava do 100 stranica. ZIP backup ima limit 1000 tabli, 16 MiB po unosu i ukupno 64 MiB raspakovanog sadržaja; nedostajući ili nepovezani prilozi se odbijaju.

Postoje PNG, JPG i PDF izvoz; SVG je preostala funkcija. Raster izvoz ograničava dimenzije i potrošnju memorije. Deljenje otvara Android chooser sa privremenim pristupom izvezenom PNG-u. Izbor spoljnog/cloud provider-a u sistemskom dijalogu zavisi od instaliranih aplikacija i korisnikovog izbora.

## Uređaji i pristupačnost

Deklarisani minimum je Android 8 / API 26. Aplikacija prilagođava raspored prozoru i podržava tablete i split-screen. Izvršena matrica i ograničenja stvarnog hardvera navedeni su uz kandidata; deklarisana podrška ne znači da je testiran svaki OEM uređaj.

Settings prekidači imaju naziv, stanje i aktiviranje preko celog reda. Omiljena tabla izlaže uključeno/isključeno stanje. Platno izlaže pristupačne objekte, dodatne akcije i fokus; Objects daje pregled svih objekata. Stvarni TalkBack servis ima odvojene integracione provere. Slušna provera izgovora, specifični OEM provider-i i olovka na fizičkom uređaju ostaju otvoreni.

## Privatnost i trenutno izostavljene funkcije

Lokalni release nema INTERNET dozvolu. Nema aktivnih naloga, sinhronizacije, saradnje ili AI-a. Table nemaju zaseban PIN/biometrijsko zaključavanje niti sopstvenu enkripciju. Podaci su u Android sandbox-u aplikacije; zaštita zaključavanja i ažurnost uređaja zavise i od Android sistema.
