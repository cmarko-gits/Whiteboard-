# F15 — jezik početnog sadržaja šablona

Kriterijum: nova tabla koristi jezik aplikacionih Android resursa u trenutku potvrde kreiranja. EN i sr-Latn pokrivaju sve početne naslove, beleške, dane i naziv prve stranice; ostali jezici koriste potpuni engleski fallback. Korisnikov naziv table i već sačuvan sadržaj ne prevode se naknadno.

TemplateLabel daje stabilne semantičke ključeve, a TemplateContent čuva potpunu, proverenu kopiju vrednosti bez Android zavisnosti. Presentation čita Android Resources i predaje snimak kroz WorkspaceViewModel/EditorPersistence u TemplateFactory. Nema globalnog Locale.setDefault niti čuvanja Context-a u domenu ili ViewModel-u. Promena jezika ne menja geometriju, boje, identitete postojećih dokumenata ili format 4.

Implementacija obuhvata 37 string resursa po jeziku. Tri nova core testa proveravaju sve šablone, očuvanje geometrije/stila i validaciju/izolaciju snimka. ViewModel test proverava kreiranje, sačuvani prevod, ručnu izmenu i reopen bez ponovnog prevođenja. Dva Android testa koriste stvarne en/sr-Latn/de resource konfiguracije, sve šablone, DocumentCodec roundtrip i bitmap izvoz.

Status: implementirano, čeka CI. F15 se ne zatvara pre prolaza JVM, Android matrice i postojećih release/security gates. Ovo nije F14 birač jezika, niti pretvara Mind Map/Flowchart šablon u budući F06/F07 editor.
