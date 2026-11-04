# F18 — meni objekta

Kriterijum: u Select režimu dugi pritisak na stvarno pogođen objekat otvara postojeći Selection actions meni. Najviši pogođeni sloj određuje cilj. Ako je cilj deo postojeće višestruke selekcije, ona ostaje cela; drugi cilj postaje nova jednostruka selekcija. Prazna površina i resize ručica ne otvaraju meni. Pritisak tokom crtanja ostaje unos poteza.

Meni ima isti stalni anchor kao Selection actions dugme, uz standardni Compose Popup fokus, ograničenje na prozor i skrolovanje. Edit postoji za pojedinačni tekst/belešku; Duplicate i Delete dostupni su uz postojeće komande za clipboard, slojeve, grupisanje, rotaciju, poravnanje i svojstva. Akcije koriste iste ViewModel/session komande i istoriju kao toolbar. Otvaranje ili zatvaranje menija ne menja dokument.

Native adapter zakazuje jednu platformsku long-press odgodu i pamti samo cilj/početnu screen tačku. Pomeranje preko touch-slop granice (uključujući istorijske MotionEvent uzorke), cancel/release, drugi pokazivač, promena objekata/stranice/alata/selekcije, gubitak window fokusa ili detach otkazuju odgodu. Pre otvaranja ponovo se proveravaju postojeći ID-jevi; transient preview se odbacuje. Naknadni UP ne potvrđuje pomeranje niti otvara editor beleške.

Tastatura: posle selekcije i sa fokusom na platnu, Menu ili Shift+F10 otvara iste akcije. Samo dugme Selection actions ostaje alternativa dostupna tastaturom. Svaki virtuelni objekat dobija imenovanu accessibility akciju Object actions / Radnje nad objektom kada je meni dostupan. Postojeća Long click/Edit akcija teksta ostaje očuvana. Zastareo virtuelni čvor ne može otvoriti meni objekta nove stranice sa istim uvezenim ID-jem.

Compose kontroliše jedan expanded state, resetovan po stranici i pri praznoj selekciji. AndroidView update dobija aktuelni callback; callback starog page state-a se ne zadržava. On release uklanja callback. Accessibility adapter dobija predikat dostupnosti i ostaje nezavisan od konkretne implementacije host View-a.

Nema promene formata 4, Room šeme 4, runtime zavisnosti, dozvola, privatnog clipboard-a ili mrežnog obima. Kandidat 0.6.5/code 13 je implementiran, ali F18 ostaje otvoren dok nova CI matrica, šest dodatnih native/UI slučajeva, R8 keyboard/Copy tok, bezbednosne i package provere ne prođu. Stabilni kvalifikovani paket ostaje 0.6.4/source 9589e12. Glavna lista: 11/47 završeno, 36 otvorenih.
