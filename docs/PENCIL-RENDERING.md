# F19 — poseban Pencil prikaz

Kriterijum: Pencil ima determinističnu grafitnu teksturu, uz korisnikovu boju, širinu i providnost. Live preview i sačuvani potez koriste isti postupak; otvaranje dokumenta i PNG/JPG/PDF/thumbnail koriste zajednički BoardRenderer. Pen i drugi alati ne nasleđuju teksturu prethodnog poteza. Pressure/tilt/palm-rejection ostaju zaseban hardverski obim.

PencilTexture ima jednu lenjo kreiranu 32×32 bitmap teksturu i najviše 16 tint filtera po renderer-u. Nema novih fontova/slika, zavisnosti ili random state-a. Tekstura ne uvodi dodatnu operaciju po svakom uzorku tačke. Šum je funkcija koordinata i ostaje stabilan u world prostoru. Alpha teksture dodatno modulira postojeći InkStyle alpha/opacity; tint je namerno neproziran da se korisnikova alpha ne primeni dvaput. Redovno Paint.reset čišćenje izdvaja stil narednog alata.

Tool.PENCIL već postoji u formatu 4, pa nema promene JSON/Room šeme niti geometrije poteza. Tri Android testa proveravaju vidljivo zrno naspram Pen-a, jednak live/committed bitmap, kombinovanu alpha vrednost i reset stila, te codec roundtrip i determinističan bitmap izvoz. Postojeći gesture testovi nastavljaju da uključuju Pencil.

Status: F19 zatvoren za lokalni emulator obim u 0.6.4/source 9589e12/CI 41874486383: svih šest poslova uspešno, 283 JVM i pet profila po 97 app + 13 data, sva tri Pencil testa i release/security/package gates prolaze. API 30 stvarni renderer PNG pregledan je uz originalni artifact/entry SHA-256: grafitno zrno naspram ravnomernog Pen-a. Dokazi: artifacts/task-29-9589e12-visual-review.json i LOCAL-0.6.4-CANDIDATE.md. Fizička olovka i pressure/tilt ostaju zaseban obim.
