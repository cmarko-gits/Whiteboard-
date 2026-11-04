# F05.3 — sadržaj stranice i deljena world geometrija

Radni nastavak posle kvalifikovane 0.6.5/source 5129627. Ovo je priprema pune integracije pametnih konektora, ne završen konektorski alat. JSON/Room ostaju 4; korisnički paket 0.6.5 nije zamenjen ovim izvorom bez svoje kvalifikacije.

`Page` zadržava šest javnih vrednosti i isto ponašanje konstruktora, imenovanog `copy`, destrukturiranja, jednakosti, hash-a i tekstualnog prikaza. Interni `PageContent` pripada paru page ID / identitet liste elemenata. Promena viewport-a, naziva ili pozadine deli isti provereni sadržaj. Nova lista, čak i jednakog sadržaja, ili novi page ID pravi novi snapshot i ponavlja validaciju. Geometrijski facade uvek pokazuje aktuelni Page, pa novi viewport/pozadina nisu zamenjeni starim metapodacima.

Jedan prolaz proverava top-level i child ID-jeve i postojeći ukupan limit listova. Taj privatni skup i broj objekata služe i proveri kolizija konektora; svaka nova serija veza više ne prolazi ponovo kroz sve ciljeve. Content bounds izračunava se lenjo nad istim sadržajem, bez privremene liste svih okvira.

`PagePortIndex` i `PageConnectionGeometry` pripadaju model paketu. Model ne zavisi od editor komandi da bi izračunao svoj položaj. Resolver dobija indeks iz `PageGeometry.connectionPorts`, a svi neizmenjeni snapshot-i dele isti gotov indeks. Prva priprema obilazi objekte sa checkpoint-ima; otkazivanje ili nevažeći okvir ne objavljuje delimičan rezultat. Svaki zahtev, uključujući pogodak u cache-u, prvo proverava otkazivanje. Kandidat se računa izvan monitora; kratka sinhronizacija objavljuje samo jednu potpunu vrednost. Dva istovremena prva čitaoca mogu pripremiti dva kandidata, ali vraćaju isti objavljeni indeks. Nema globalne promenljive mape ili reference na stari page facade u sadržaju.

Kao i pre ove promene, document modeli primaju `List`, a caller mora da prestane sa njenim mutiranjem po predaji modelu. Ovaj korak ne tvrdi da je duboko zamrzavanje svih postojećih Stroke/Group listi implementirano. Indeks i skup ID-jeva imaju O(n) memoriju; algoritamske provere nisu fizičko profilisanje ili potvrda 60 Hz.

Osam novih JVM slučajeva proverava metadata copy/current facade, preview invalidaciju i povratak izvora, page ID/novu listu, svih šest javnih vrednosti, otkazivanje i retry, nevažeće okvire/duplikate/limite, 99.999 ciljeva i konkurentno objavljivanje. Na velikoj stranici 100 metadata promena koristi isti indeks i samo jedan checkpoint po pristupu; jedna naknadna serija veze koristi četiri checkpoint-a, bez ponovnog obilaska ciljeva. Postojeće geometrijske, format, session, Undo i clipboard regresije ostaju obavezne.

F05.3 ostaje otvoren: Connector još nije Element. Zajednički resolver svih potrošača, format 5, atomsko odvezivanje pri brisanju, remap kopija, stvarni UI/accessibility i potpuna nova kvalifikacija ostaju koraci iz SMART-CONNECTORS-DESIGN.md. Ne postoji nova Play/cloud ili fizička kvalifikacija.
