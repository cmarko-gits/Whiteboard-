# F03 — pomeranje za jedan sloj

Stanje: kriterijumi su pripremljeni; implementacija još nije započeta. F02 ostaje na svojoj CI kvalifikaciji. Ova funkcija ne dodaje format dokumenta ili SQL migraciju.

## Ugovor ponašanja

Bring forward pomera svaki uzastopni blok izabranih objekata preko jednog narednog neizabranog objekta u z-redosledu stranice. Send backward radi obrnuto. Relativni redosled izabranih, kao i neizabranih objekata, ostaje isti. Postojeći Bring to front i Send to back ostaju krajnje pozicije.

Primer, gde su B/C izabrani: `A B C D E` → Bring forward → `A D B C E`. Za odvojenu selekciju B/D: `A B C D E` → `A C B E D`. Grupa je jedan objekat na nivou stranice; njena deca se ne preraspoređuju.

Jedna akcija je jedna Undo komanda i Redo vraća isti redosled. Granica, prazna selekcija i nepostojeći ID-jevi ne prave istoriju i ne brišu postojeći Redo. Geometrija, ID-jevi, transformacije, stilovi i prilozi ostaju isti objekti. Save/reopen i export moraju poštovati novi redosled.

## Projektovana primena

Čist Kotlin sloj računa redosled; UI samo prosleđuje izabrane ID-jeve i smer. Čuvati promenu redosleda kao liste ID-jeva pre/posle, a primenjivati je na trenutne objekte. Pre upisa proveriti jedinstvenost, potpun skup ID-jeva i početni redosled, kako promena ne bi obrisala ili oživela objekat.

Istorija ne treba da računa kopije pune geometrije samo zbog promene sloja. Operacija ne menja priložene datoteke; trenutni dokument i postojeće komande brisanja/izmena ostaju vlasnici retention-a. Budžet istorije mora ostati eksplicitan. Obrada velikih stranica mora biti linearna, bez ponovljenog ubacivanja u sredinu velike liste.

Komande će biti u selekcionom meniju, imenovane u EN/sr-Latn resursima i dostupne tastaturi/accessibility navigaciji. Akcija koja ne može da pomeri selekciju treba da bude onemogućena.

## Prihvatanje

Domenski testovi pokrivaju jedan objekat, uzastopne i odvojene selekcije, granice, grupe/rotirane medije, stabilne ID-jeve, Undo/Redo i očuvanje Redo pri no-op-u. Proveriti asset retention kroz reorder → delete → undo → redo i veliku stranicu.

Android tok bira objekte i izvršava obe nove akcije kroz meni, čuva i ponovo otvara dokument. Test renderera/PNG-a sa preklopljenim obojenim oblicima proverava vidljiv redosled. Potrebni su pogođeni JVM/device testovi, release gates i pregled CI matrice konkretnog izvora pre zatvaranja F03.
