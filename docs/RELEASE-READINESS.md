# Prva javna lokalna verzija — završni rad

Presek 8. novembra 2026. Ovo je aktivni plan od devet celina za pripremu izdanja, uz širi istorijski backlog od 47 stavki. Brojevi ta dva plana nisu isti inventar. Objavljivanje, pristup Play nalogu i ocena Google review-a nisu izvršeni ovim dokumentom.

## Opseg

Prvo izdanje ostaje offline Kotlin Android: postojeće crtanje/bojenje, objekti, veze, mape uma, stranice, organizacija, recovery, privatni clipboard i postojeći import/export. Dijagrami toka iz lokalne grane `codex/flowchart-foundation` / b78b435 ostaju zasebno sačuvani i nisu spojeni u kandidata. Kandidat polazi od `codex/mind-map-editor` / 1b83faa, app 0.8.0/code 17, dokument 6/delta 2/Room 4. AI, cloud, backend, naplata, PIN, novi fontovi, SVG i druge dodatne funkcije nisu uslov ovog izdanja.

## Devet celina i kriterijumi

| Broj | Rad | Status i dokaz potreban za završetak |
|---|---|---|
| 1 | Android 13/16 neuspešni testovi | U toku. Razdvojiti fixture/layout problem i dokazani System UI startup ANR; ciljano izvršavanje mora proći bez ignorisanja grešaka. |
| 2 | Mape uma | Otvoreno. Native i optimizovani create/edit/color/search/rotate/reparent/move/collapse/Undo/save/reopen, svih pet konačnih profila. |
| 3 | Zamrznut opseg prve verzije | Kandidat izdvojen od F07 razvoja; potvrditi build/source i korisničko uputstvo bez nedovršenih novih opcija. |
| 4 | Pouzdanost podataka | Otvoreno za tačan kandidat. Room migracije, recovery, save race/cancellation, stari dokumenti, backup/restore, oštećeni ulazi i bezbedna greška pri nedostatku prostora. |
| 5 | Prilagodljivost i pristupačnost | Automatizovani dokazi postoje za prethodne izvore; ponoviti pogođene tokove i završiti ručni vizuelni/govorni pregled. Fizički uređaji i slušni TalkBack nisu zamenjeni testnom semantikom. |
| 6 | Performanse i memorija | Otvoreno. Proveriti konačni release i reprezentativne scene, čuvanje/izvoz, velike priloge i granice; popraviti merljive probleme. Ne tvrditi univerzalnih 60 Hz ili fizički GPU profil iz emulatora. |
| 7 | Bezbednost konačnog kandidata | Otvoreno. Review promenjenih granica, hostile input/regresije, source/history tajne, zavisnosti/licence i manifest/DEX/permissions. Prethodni čist izvor nije dokaz novog. |
| 8 | Završna regresija | Otvoreno. Isti tačan source: svih pet Android profila, JVM, style/architecture/lint, tri R8 toka i jezičko vraćanje; bez neobjašnjenih neuspeha/preskakanja. Stvarni telefon/tablet ostaju odloženi po ranijoj odluci korisnika. |
| 9 | Distribucioni paket | Otvoreno. Konačan AAB, vlasnički upload potpis i zaštićena rezervna kopija ključa, provera paketa/provenance/ABI/16 KB i instalacije. Razvojni potpis nije vlasnički Play paket; account/listing/policy/testeri su zaseban spoljni rad. |

## Postupak provere

Popravke prvo koriste lokalne pogođene testove i dva udaljena profila (33/36) na grani `codex/release-readiness`. Taj prolaz ima četiri app testa (dva mind-map i dva dialog-driver), ne punih 135 app i 13 data. Ne sme biti korišćen kao konačna kvalifikacija ili paket dokaz. Isti commit na kandidatskoj grani pokreće punu petoprofilnu matricu. Neuspešna testiranja ostaju sačuvana, bez tihog uklanjanja asercija ili ponavljanja dok slučajno ne prođu.

System UI startup nalaz izvora 1b83faa je potvrđen: prvi API 36 failure PNG BC6852DE0814FB463480BC0227EE935653F295C34FBE801F3FB6113820001F3D i XML 73C4BA2DC7A6AFFEFDEF4CBBEB3BC366172A8534414E95FDE112BBF557A2A815 prikazuju sistemski ANR dijalog. Digest-verified originalni log beleži `ANR in com.android.systemui` / `failed to complete startup` u 04:34:44 UTC, pre launcher-ready 04:34:57 i prvog app testa 04:35:30. I početni i finalni lastanr izlaz tvrde da ANR nije bilo. Novi checkpoint čita i filtrirane logcat ANR/fatal zapise; ne zatvara dijalog niti briše logove. Build daemon se zaustavlja pre emulator boot-a da oslobodi memoriju. To je infrastrukturna dijagnoza, a ne prolaz API 36 aplikacije.

Dokazi: `artifacts/task-16-1b83faa-evidence.json`, `task-16-1b83faa-36-pixel_2-diagnostics.json`, `release-step-01-fixture-compile.log` i novi imenovani CI rezultati. Dodatni disk limit ostaje 3.400.000.000 bajtova; nema novih lokalnih SDK/system-image preuzimanja. Automatske provere ne predstavljaju nezavisan pentest ili potvrdu odsustva svih nepoznatih grešaka.
