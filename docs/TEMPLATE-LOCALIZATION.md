# F15 — jezik početnog sadržaja šablona

Kriterijum: nova tabla koristi jezik aplikacionih Android resursa u trenutku potvrde kreiranja. EN i sr-Latn pokrivaju sve početne naslove, beleške, dane i naziv prve stranice; ostali jezici koriste potpuni engleski fallback. Korisnikov naziv table i već sačuvan sadržaj ne prevode se naknadno.

TemplateLabel daje stabilne semantičke ključeve, a TemplateContent čuva potpunu, proverenu kopiju vrednosti bez Android zavisnosti. Presentation čita Android Resources i predaje snimak kroz WorkspaceViewModel/EditorPersistence u TemplateFactory. Nema globalnog Locale.setDefault niti čuvanja Context-a u domenu ili ViewModel-u. Promena jezika ne menja geometriju, boje, identitete postojećih dokumenata ili format 4.

Implementacija obuhvata 37 string resursa po jeziku. Tri nova core testa proveravaju sve šablone, očuvanje geometrije/stila i validaciju/izolaciju snimka. ViewModel test proverava kreiranje, sačuvani prevod, ručnu izmenu i reopen bez ponovnog prevođenja. Dva Android testa koriste stvarne en/sr-Latn/de resource konfiguracije, sve šablone, DocumentCodec roundtrip i bitmap izvoz.

Status: F15 zatvoren za lokalni emulator obim u 0.6.4/source 9589e12/CI 41874486383: svih šest poslova, 283 JVM i pet puta 97 app + 13 data prolaze, uz release/security/package gates. Paket, hash-evi i granice: LOCAL-0.6.4-CANDIDATE.md. F14 birač jezika ima zaseban ugovor; Mind Map/Flowchart šabloni ostaju običan početni sadržaj dok budući F06/F07 editori ne budu implementirani.
