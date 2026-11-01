# Z08 — pregled završnog uzorka gesta

Stanje: nalaz iz pregleda koda, još nije ispravljen ili kvalifikovan.

`WhiteboardView.handleTouch` obrađuje završnu ACTION_UP koordinatu za gumicu i Fill. Pen/Pencil/Marker/Highlighter trenutno završavaju putanju bez dodavanja te poslednje koordinate; oblik potvrđuje prethodni MOVE preview. Pri retkim događajima ili promeni položaja između poslednjeg MOVE i UP završetak može odstupati od mesta podizanja prsta/olovke. ADB smoke snimak linije ima kraći završetak od zadate krajnje tačke; to je trag za proveru, a ne merenje fizičke olovke.

Potrebno je pre završne komande uključiti validan završni uzorak za alate crtanja i konačnu geometriju oblika, uz postojeća pravila minimalnog gesta, page/tool generation, pinch/cancel i ograničenja koordinata. ACTION_CANCEL ne sme potvrditi sadržaj; prekid stylus pokazivača takođe treba proveriti. Izmena ne sme dodati duplu ili slučajnu tačku posle odbijenog/otkazanog gesta.

Regresija treba da koristi stvarne MotionEvent događaje sa različitim poslednjim MOVE i UP, kao i DOWN/UP bez MOVE. Proveriti tačan kraj poteza/oblika, otkazivanje, promenu alata/stranice i odbijanje koordinata bez delimičnog upisa. Zatim izvršiti pogođene domenske/canvas testove i redovnu Android matricu. Ovo je zaseban nalaz od F02 rotacije.
