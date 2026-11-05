# Z08 — ograničavanje memorijske cene JSON unosa

Radna ispravka 0.7.1/code 15: lokalno prolaze core/format/data/ViewModel regresija, pre-render/current Android test izvori se kompajliraju, a isti million-object ulaz u 64 MiB child procesu sada daje DOCUMENT_LIMIT pre pune obrade. Nova Android/release matrica još nije potvrđena. Glavni Z08 ostaje otvoren; najnoviji kvalifikovani paket je zasebna 0.7.0/source b5dfebc.

Status 5. novembra 2026: otvoren konkretan nalaz, pre ispravke. Izvor b5dfebc ima postojeći JsonSafety limit dubine i numeričkih literala i DocumentCodec limit od 64 Mi karaktera. Preflight ne ograničava ukupan broj objekata, vrednosti ili cenu platformskog JSONObject/JSONArray stabla.

Izolovana JVM proba koristi isti kompajlirani format kod, JSON-Java 20250517 i child proces sa heap limitom 64 MiB. Ulaz od 3.000.025 karaktera ima milion praznih objekata u dodatnom root nizu. JsonSafety prolazi, a puna DocumentCodec.decode obrada završava sa uhvaćenim OutOfMemoryError u child procesu. Ulaz se generiše samo u memoriji; nema korisničkih podataka. Log i metadata: `artifacts/task-15-json-allocation-probe.log` i `.json`; prvi neuspešan classpath setup je sačuvan odvojeno. Ovo nije reprodukcija pada Android uređaja ili dokaz identične memorijske potrošnje njegovog org.json parsera.

Potrebno je ograničiti strukturnu/alokacionu cenu pre poziva JSONObject, uz postojeću proveru sintakse/dubine. Budžet treba da obuhvati ulaz, kontejnere, ključeve, vrednosti, decoded stringove i duge numeričke reprezentacije; dodatna kontrola samo veličine fajla ne rešava broj sitnih objekata. Brojači moraju biti saturating/bounded i bez pravljenja novog JSON stabla. Checkpoint za velike nizove treba razmotriti zajedno sa postojećim pozadinskim import tokom.

Isti ugovor mora da važi za encode rezultate pre upisa, da aplikacija ne bi snimila dokument koji sopstveni reader odbija. Prekoračenje treba da ima zaseban stabilan limit razlog, bez dokumenta ili exception poruke u korisničkom logu. Neuspešan save/import mora da ostavi prethodni dokument i recovery reviziju netaknutim.

Prihvatanje zahteva: odbijanje width/number/string napada pre platformskih alokacija, ponovljenu child-heap probu, validan 1–5 roundtrip i granice budžeta, 10.000-node/9.999-connection scenario, save-failure/recovery provere, punu regresiju i kvalifikaciju novog paketa. F05 funkcionalni dokazi ne zatvaraju ovaj Z08 nalaz, niti je postojeći OSV/source scan njegov test. Smanjenje limita bez encode/read pariteta nije prihvatljiva ispravka.

## Implementirani obračun i granice

JsonSafety pre platformskog parsiranja troši konačan budžet od 64 MiB procenjene cene: input UTF-16 jedinice × 2, vrednost 32, objekat 96, niz 64, objektni entry 64, string header 48 i decoded UTF-16 jedinica × 2, numerička reprezentacija dodatnih 48 + raw dužina × 2. Ovo je konzervativna accounting politika, ne tvrdnja o tačnom Android/JVM heap-u. Svaka naplata prvo poredi cenu sa ostatkom, pa oduzima, bez prelivanja akumuliranog zbira. Sintaksa/dubina/numerički limiti ostaju aktivni.

DocumentCodec board/page/element i DocumentDeltaCodec koriste isti budžet pri pisanju i čitanju. Format i Room verzije ostaju 5/4. Prekoračenje daje stabilan DocumentError.DOCUMENT_LIMIT; korisnički EN/sr-Latn tekst traži manje objekata ili kraći tekst, bez sadržaja dokumenta i bez automatskog retry loop-a. Postojeći repository storageCall ne menja taj razlog i SQLite transakcije zadržavaju prethodnu reviziju.

Osam novih format slučajeva obuhvata width/string/numeric cenu, tačnu granicu i Long.MAX_VALUE konfiguraciju, encode/read paritet sva tri dokumentna ulaza i delta envelope-a, editable ZIP propagaciju, isti million-object child JVM i produkcioni 10.000-node graf. Novi ViewModel test čuva dirty edit i prethodni save bez retry-a. Dva Android Room testa proveravaju odbijeni save/draft uz obe sačuvane revizije i odsustvo phantom metadata/draft chunk-ova; lokalno su samo kompajlirani, stvarna matrica sledi.

Prethodni baseline log je sačuvan; novi direktni child izlaz je artifacts/task-08-json-allocation-after.log. Format i Android regresije ne garantuju da ukupna memorija celog procesa, bitmap cache, SAF provider, broj istovremenih operacija ili fizički low-RAM uređaj nikada neće iscrpeti memoriju. Ti H03/H04 scenariji i izmerena velika-graph optimizacija ostaju zasebni rad.
