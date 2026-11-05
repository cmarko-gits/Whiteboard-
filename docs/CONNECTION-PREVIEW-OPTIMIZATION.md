# Z08 — sledeći korak: preview velikog grafa

Status: projektovanje, bez tvrdnje da je optimizacija već implementirana. Prvo se kvalifikuje JSON-budget 0.7.1; sledeći izvor dobija zaseban profil i punu regresiju. Polazni rezultati i ograničenja su u CONNECTION-SCALE-BASELINE.md.

Trošak jednog pomerenog cilja sada uključuje ponovnu proveru cele liste, indeksiranje svih portova, razrešavanje svih veza i ponavljanje računanja ink/arrowhead okvira. Cilj je ograničiti novi proračun na promenjene ciljeve i incidentne veze, uz jeftin tačan union svih okvira kada je potreban.

Predloženi snapshot ugovor:

- Kanonski content po potrebi jednom priprema validne render okvire. Cache koristi referencu definicije, ne samo ID; strani lokalni Element zadržava context-free geometriju, a zastarela Connector definicija se odbija pre cache lookup-a.
- Stabilni preview cilja može deliti proverene ID-jeve, broj objekata, nepromenjene definicije veza i izvorne okvire. Promenjeni portovi, incidentne linije i njihovi ink okviri računaju se ponovo pre objavljivanja preview-a.
- Indeks incidentnih veza pripada content-u, sa otkazivim kompletnim publish-om. Port lookup ima privatnu overlay strukturu; ne koristi Map implementaciju čiji entries/values daju pogrešne stare vrednosti.
- Brzi put zahteva isti identitet/kind i stabilne group-child ID-jeve. Izmena reference/stila/free kraja konektora, promena strukture grupe ili nepodržana zamena koristi pun kanonski postupak. Originalni model i istorija ostaju netaknuti.
- Preview ne sme praviti neograničen lanac prethodnih stranica. Metadata kopije mogu deliti content, a strukturni edit stvara novi snapshot. Nema globalnog mutabilnog geometrijskog indeksa ili zadržavanja stare Page fasade.

Prihvatanje zahteva cold/warm i metadata slučajeve, strani local Element, stale Connector, rotirane/group-child portove, tačne slojeve/ink/selection okvire, otkazivanje i konkurentne čitaoce, chained preview, native cancel/render/AX/export i ceo gesture/release tok. Ponoviti isti 100/1.000/10.000-node algoritamski profil i navesti sirove pre/posle podatke i dodatnu memoriju cache-a. Pozitivan debug algoritamski rezultat ne predstavlja fizičku ili 60 Hz kvalifikaciju.
