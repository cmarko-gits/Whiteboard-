# Z08 — ograničavanje memorijske cene JSON unosa

Status 5. novembra 2026: otvoren konkretan nalaz, pre ispravke. Izvor b5dfebc ima postojeći JsonSafety limit dubine i numeričkih literala i DocumentCodec limit od 64 Mi karaktera. Preflight ne ograničava ukupan broj objekata, vrednosti ili cenu platformskog JSONObject/JSONArray stabla.

Izolovana JVM proba koristi isti kompajlirani format kod, JSON-Java 20250517 i child proces sa heap limitom 64 MiB. Ulaz od 3.000.025 karaktera ima milion praznih objekata u dodatnom root nizu. JsonSafety prolazi, a puna DocumentCodec.decode obrada završava sa uhvaćenim OutOfMemoryError u child procesu. Ulaz se generiše samo u memoriji; nema korisničkih podataka. Log i metadata: `artifacts/task-15-json-allocation-probe.log` i `.json`; prvi neuspešan classpath setup je sačuvan odvojeno. Ovo nije reprodukcija pada Android uređaja ili dokaz identične memorijske potrošnje njegovog org.json parsera.

Potrebno je ograničiti strukturnu/alokacionu cenu pre poziva JSONObject, uz postojeću proveru sintakse/dubine. Budžet treba da obuhvati ulaz, kontejnere, ključeve, vrednosti, decoded stringove i duge numeričke reprezentacije; dodatna kontrola samo veličine fajla ne rešava broj sitnih objekata. Brojači moraju biti saturating/bounded i bez pravljenja novog JSON stabla. Checkpoint za velike nizove treba razmotriti zajedno sa postojećim pozadinskim import tokom.

Isti ugovor mora da važi za encode rezultate pre upisa, da aplikacija ne bi snimila dokument koji sopstveni reader odbija. Prekoračenje treba da ima zaseban stabilan limit razlog, bez dokumenta ili exception poruke u korisničkom logu. Neuspešan save/import mora da ostavi prethodni dokument i recovery reviziju netaknutim.

Prihvatanje zahteva: odbijanje width/number/string napada pre platformskih alokacija, ponovljenu child-heap probu, validan 1–5 roundtrip i granice budžeta, 10.000-node/9.999-connection scenario, save-failure/recovery provere, punu regresiju i kvalifikaciju novog paketa. F05 funkcionalni dokazi ne zatvaraju ovaj Z08 nalaz, niti je postojeći OSV/source scan njegov test. Smanjenje limita bez encode/read pariteta nije prihvatljiva ispravka.
