# F04 — eksplicitno poravnanje selekcije

Status 2. novembra 2026: ugovor je definisan; implementacija i testovi nisu izvršeni. F03 ostaje na zasebnoj CI kvalifikaciji. Ovo nije potvrda završetka F04.

## Ugovor

Šest akcija: leva ivica, horizontalni centar, desna ivica, gornja ivica, vertikalni centar i donja ivica. Potrebna su najmanje dva postojeća izabrana objekta na aktivnoj stranici. Referenca je zajednički okvir selekcije pre operacije, bez posebno izabranog ključnog objekta. Centar znači centar tog okvira, ne prosek centara objekata.

Koristiti postojeći `selectionBounds` u koordinatama dokumenta. Okvir rotiranog/afinnog objekta je njegov world okvir; grupa je jedna jedinica. Poravnanje pomera objekat, bez promene veličine, ugla, debljine poteza, lokalnog teksta, crop-a, asset ID-ja ili z-redosleda. Ne poravnavati lokalne ivice rotiranog objekta prema osama ekrana.

Primer: okviri A=(10,20,50,60), B=(80,100,140,180). Leva ivica pomera samo B za -70 po x; horizontalni centar je x=75, pa se centri oba okvira pomeraju na 75. Ostala koordinata ostaje ista. Pri grupi se isti vektor primenjuje na svu decu.

Prazna/jednostruka selekcija, nepoznati ID-jevi i već poravnata selekcija ne menjaju istoriju, dirty stanje ili Redo. Jedna akcija sa više objekata je jedna Undo komanda; Redo vraća identičan rezultat. Save/reopen, recovery, clipboard i izvoz moraju koristiti novu geometriju.

## Arhitektura i granice

Čist Kotlin planer pravi nepromenljiv plan sa originalnom stranicom i zamenama. Računanje velikih poteza ide kroz postojeći computation dispatcher, uz proveru otkazivanja i budžeta pre kopiranja geometrije. Pre primene proveriti identitet sesije, aktivnu stranicu i originalnu listu objekata. Zastareli ili otkazani plan ne sme prepisati noviju izmenu.

Pre upisa proveriti sve nove render granice i postojeće limite koordinata. Ne menjati dokument delimično kada jedan objekat prekoračuje limit. Komanda čuva originalne objekte/priloge za Undo prema postojećem budžetu; prevelika izmena se odbija jasnom porukom. Za afinni objekat komponuje se world translacija; za potez pomeraju se sve tačke; za grupu isti vektor čuva unutrašnji raspored.

UI: posebna akcija Alignment u selekcionom meniju otvara adaptivan dijalog sa šest imenovanih dugmadi. Nema dodatnog numeričkog unosa ili tastature. Koristiti EN/sr-Latn resurse, najmanje 48 dp dodirne površine i standardnu fokus/accessibility semantiku. Za manje od dva postojeća objekta akcija je onemogućena. Tokom računanja zaključati ponovljenu potvrdu i zatvoriti dijalog tek po rezultatu; neuspeh ne sme izgledati kao uspeh.

Ravnomerna raspodela razmaka je opciona zasebna funkcija i nije uključena u ovih šest operacija. Format dokumenta i Room šema ne menjaju se translacijom postojećih tipova.

## Uslovi prihvatanja

- Svih šest operacija sa objektima različitih veličina; očuvanje druge ose, dimenzija, identiteta i z-redosleda.
- Grupa, rotirani/afinni tekst i medij, nulta širina/visina linije, stroke sa mnogo tačaka i neizabrani objekti.
- Prazna/jednostruka/već poravnata selekcija zadržava Redo i saved stanje.
- Jedna Undo/Redo komanda za celu selekciju; asset retention kroz naknadno brisanje i Undo.
- Prekoračenje koordinata/budžeta, otkazivanje i promena dokumenta tokom planiranja daju atomsku zaštitu.
- ViewModel testovi sa kontrolisanim dispatcher-ima proveravaju stvarni životni ciklus računanja i čuvanja.
- Android tok kroz UI proverava komandu, granice, Undo/Redo, Save/reopen i PNG rezultat; R8 smoke potvrđuje dostupnost optimizovane implementacije.
- Puna aktivna CI matrica i sigurnosne/packaging provere tačnog izvora pre zatvaranja stavke. Hardversko profilisanje i govorni TalkBack pregled ostaju zasebni.
