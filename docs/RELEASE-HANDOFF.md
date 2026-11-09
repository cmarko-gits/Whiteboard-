# Završni ručni rad za prvo lokalno izdanje

Presek 9. novembra 2026. Merodavan status svih devet celina je u [RELEASE-READINESS.md](RELEASE-READINESS.md). Ovaj spisak ne proglašava kandidata kvalifikovanim dok njegove završne provere nisu završene. AI, backend, cloud i F07 ostaju van prvog izdanja.

## Pristupačnost i vizuelni pregled — celina 5

- Sa stvarno uključenim TalkBack servisom poslušati onboarding, biblioteku/pretragu, foldere/Trash i kreiranje table. Potvrditi razumljive nazive, redosled i povratak pristupačnog fokusa.
- Proći izbor objekata i akcije bez preciznih poteza: tekst, beleške, oblici/bojenje, veze, mape uma, stranice, izbor/pomeranje, Undo/Redo i čuvanje.
- Proveriti sve modalne unose i birač boje: unos/greška/potvrda/odustajanje, rotacija, zatvaranje tastature i ponovni izričit unos. Keyboard fokus i TalkBack fokus nisu isto stanje.
- Proći import, editable ZIP backup/restore, raster/PDF izvoz i sistemsko deljenje, uključujući razumljivu poruku pri oštećenom fajlu ili izgubljenom pristupu odredištu.
- Vizuelno pregledati Light/Dark/System, srpski/engleski, font 150/200% i korisnički izabrane boje. Potvrditi čitljivost, kontrast, fokus i dostupnost kontrola, ne samo postojanje njihovih oznaka u hijerarhiji.

Automatizovani native/semantički testovi i screenshots imaju svoj source i dokaz. Ranija 0.4 TalkBack integracija i nova kompilacija RealTalkBackTest ne predstavljaju izvršen govorni pregled konačnog izdanja.

## Fizičko profilisanje — celina 6

- Prema [PERFORMANCE.md](PERFORMANCE.md), zabeležiti tačan release APK/potpis, model, API, RAM, refresh rate i termalno stanje reprezentativnog telefona/tableta.
- Izmeriti stvarne Window/GPU frejmove i p50/p95/p99, crtanje/pomeranje/zoom i odziv unosa na dogovorenim scenama. Emulator/tracing rezultat ne proglašavati fizičkim FPS prolazom.
- Ponoviti male i velike dokumente, slike/PDF, čuvanje/izvoz i dužu sesiju; posmatrati peak/retained memoriju, low-RAM ponašanje, zagrevanje i potrošnju baterije.
- Proveriti olovku/prst/dlan i stvarne OS providere, IME i promenu prozora. Za instalaciju/nadogradnju čuvati postojeće podatke i prethodno napraviti proverenu editable rezervnu kopiju.
- Zasebno proveriti podržane 16 KB runtime uređaje/okruženje i stvarnu in-place OS nadogradnju. ELF/ZIP/BundleConfig provere potvrđuju strukturu paketa; postojeća API 26/30/33/36 matrica sama ne dokazuje te runtime slučajeve. API 36.1 i dodatne OEM varijante nisu deo ove matrice; novi lokalni image-i nisu preuzimani u ograničenju prostora.

Korisnik je prethodno izabrao nastavak bez fizičkog telefona. Ne treba prikazivati nedostupan hardver kao testiran. CI dijagnostički paket sa LeakCanary/Shark ima zasebne hash-eve i svrhu od optimizovanog release paketa.

## Nezavisna kopija upload ključa — celina 9

- Lokalni vlasnički ključ je izvan Git-a, u privatnom Windows direktorijumu pod ACL/DPAPI zaštitom. Privatni ključ i lozinka ne pripadaju repozitorijumu, CI artefaktima ili razgovoru.
- Potrebno je konkretno bezbedno odredište izvan C: diska. Druga kopija na istom disku ne štiti od njegovog kvara; odredište je zatraženo, ali još nije navedeno.
- Kopija mora omogućiti oporavak i ključa i njegove lozinke nezavisno od ovog Windows profila. Sam DPAPI password fajl nije prenosiv način oporavka.
- Proveriti otvaranje rezervne kopije i isti javni SHA-256 sertifikata. Dok to nije urađeno, nezavisna kopija nije završena.

Potpisivanje lokalnog AAB-a nije Play predaja. Play App Signing, nalog, listing/politike i eventualni obavezni test track ostaju zaseban vlasnički P opseg iz [LOCAL-REMAINING-TASKS.md](LOCAL-REMAINING-TASKS.md); nisu izvršeni ovim spiskom. Postojeći razvojni preview koristi svoj razvojni identitet, a upload ključ ima drugu ulogu.
