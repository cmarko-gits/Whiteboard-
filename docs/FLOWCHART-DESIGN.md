# F07 — flowchart paleta i objekti

Zadatak 17/47, otvoren. Ovo je projektni ugovor i pregled postojećih potrošača; flowchart objekti još nisu implementirani. F06 razvojna 0.8.0 ima zasebnu tekuću kvalifikaciju i njen format 6 se ovim ne menja.

## Model i korisnički tok

Paleta nudi Start/End (zaobljen terminator), Process (pravougaonik), Decision (romb), Database (cilindar), Document (talasasta donja ivica) i Input/Output (paralelogram). Start/End koristi isti simbol sa izmenjivim naslovom. To su lokalni objekti sa običnim F05 vezama, bez nametnutog stabla, skrivenih potomaka, automatskog izvršavanja toka ili mind-map metapodataka.

Planirani FlowchartBlock implementira Element: jedan ID, bounds, FlowchartKind, naslov, fontSize, textColor, InkStyle i opcioni fill. Naslov pripada objektu; zasebna slobodna TextElement preko običnog oblika ne bi pouzdano očuvala edit, kretanje, kopiranje i vezivanje. Vrsta simbola nije Tool vrednost: alat je interakcija, simbol je trajni sadržaj. Promena vrste zadržava ID, naslov, okvir i bound konekcije. Transform/duplicate i affine placement slede postojeće domenske ugovore.

Shapes → Flowchart otvara lokalizovanu paletu i formu sa naslovom/bojama. Precizno dodavanje bez gesta dostupno je iz istog dijaloga; mesto koristi trenutno vidljivi centar. Selection actions nudi Edit flowchart, uz Move/Resize/Rotate/Properties/Copy/Paste/Duplicate/Group/Delete. Svaka potvrda je jedan budžetiran Undo; Cancel, rotacija, stale snapshot i prelazak na drugu tablu ne objavljuju delimičan objekat. Naslov je ograničen pre kopiranja/publikovanja; postojeći tekstualni/JSON limiti ostaju obavezni.

## Geometrija

Čista domenska geometrija definiše obris, unutrašnju površinu, bezbedan okvir naslova i portove svakog simbola. Renderer i Fill/picking moraju koristiti isti oblik, uključujući prazne uglove terminatora/paralelograma, cilindar i talas dokumenta. Interna ukrasna linija Database-a nije zaseban objekat ili nova fill površina. Portovi LEFT/TOP/RIGHT/BOTTOM su na stvarnoj spoljašnjoj ivici pri sredini odgovarajuće ose; CENTER ostaje centar. Affine transform primenjuje se na lokalni obris/portove, bez ponovnog računanja preko world AABB-a. Nulte/tanke dimenzije, tolerancije i ekstremne koordinate imaju eksplicitne, konačne rezultate.

Sve putanje ostaju unutar deklarisanog bounds-a, uz postojeće širenje za debljinu konture. Tekst koristi postojeći offline font i kontrast, klipovan u bezbedan unutrašnji pravougaonik; ne sme prelaziti ivice simbola. Boja, providnost, izbor i fokus moraju ostati čitljivi i na proizvoljnoj pozadini. Ne obećavati sve portove/zakrivljene ivice samo dodavanjem enum-a: PagePortIndex trenutno poznaje pravougaonik i poseban triangle inset, pa zahteva stvarnu geometrijsku integraciju.

## Obavezne tačke integracije

| Oblast | Postojeći potrošači / zahtev |
|---|---|
| Domen | Document Element/renderBounds/estimatedBytes/hitTest, Page validacija i optimizovani preview, AffineElement, ElementGroup, ObjectTranslation/ObjectRotation/ObjectCopies, history budžet |
| Veze | PagePortIndex, PageGeometry/PageConnectionGeometry, target picker, copy/remap, detach na brisanju mete, stale/cancellation zaštita |
| Stil/tekst | FillEngine i DocumentSession.fill, Workspace properties i text editor, Search i Folder excerpt, ObjectNavigatorDialog |
| Android | BoardRenderer/thumbnail/PNG/JPEG/PDF, WhiteboardView input/selection/context/AX Edit, CanvasAccessibility opis i akcije, paleta i lokalizacija |
| Storage | DocumentCodec board/page/affine/group, delta/digest/ZIP/draft, Room save/reopen i FTS; stari fixture-i ostaju izvorno stari |

Zasebna nova verzija dokumenta planirana je pri implementaciji novog trajnog tipa. Čitač mora nastaviti da čita 1–6, odbijati flowchart payload pod starim header-om i odbijati nepoznatu vrstu pre upisa. Nema tihog pravougaoničkog fallback-a koji gubi značenje. Room šema se ne menja automatski ako i dalje čuva isti JSON ugovor. Stari Flowchart Note/Line šabloni ostaju otvorivi i ne konvertuju se heuristički. Novi šablon koristi tri prava bloka i dve bound F05 veze; lokalizacija menja samo tekst.

## Redosled i kriterijumi zatvaranja

1. Čisti model i zajednička geometrija: šest simbola, inside/outside/pogranica, degenerisani i affine slučajevi, portovi i budžet.
2. Integracija transform/copy/group/Fill/selection/connection/history bez novih paralelnih definicija geometrije. F06 Note-only vlasništvo ostaje provereno.
3. Verzijski codec, hostile input, old-reader/old-header odbijanje, delta/digest/ZIP/FTS/draft/Room round-trip i Undo/Redo.
4. Adaptivna EN/sr-Latn paleta i editor, accessibility alternativa gestovima, naslov/boje i pravi šablon.
5. Android raster/picking/fill/port/AX/export i ceo create → edit → connect → transform → copy → Undo → Save/reopen tok, font 200%, rotacija i uski/tablet prozor.
6. Nova petoprofilna Android matrica, tri optimizovana R8 toka i sigurnosni/paket gates vezani za tačan novi izvor; tek zatim zatvoriti F07 i ažurirati glavni broj zadataka.

Fizički stylus/GPU, govorni TalkBack i Play objava ostaju zasebno otvoreni ili odloženi. Trenutni Flowchart template nije dokaz završenog editora.
