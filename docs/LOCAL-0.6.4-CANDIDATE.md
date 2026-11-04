# Lokalni kandidat 0.6.4 / code 12

Kvalifikovan 4. novembra 2026: izvor `9589e12601e772a1355029566c0b7a0b958285ca`, grana `codex/smart-connectors`, [CI 41874486383](https://github.com/cmarko-gits/Whiteboard-/actions/runs/41874486383). Svih šest poslova uspešno je u prvom pokušaju tog izvora. Razvojni lokalni paket završava F14, F15 i F19 za emulator obim. Prethodni [0.6.3/source a230f11](LOCAL-0.6.3-CANDIDATE.md) ostaje sačuvan sa svojim dokazima.

## Ponašanje

- Settings → Language bira sistemski, engleski ili srpski latinični jezik. AppCompat održava izbor od API 26, uz sistemsko skladištenje od API 33. Promena jezika ponovo kreira interfejs uz očuvan otvoreni dokument i nesnimljene izmene. Već upisan tekst zadržava svoj sadržaj.
- Nova tabla preuzima svih 37 početnih template tekstova i naziv prve stranice iz resursa koji važe pri potvrdi kreiranja. EN i sr-Latn su potpuni; nepodržani jezici koriste engleski fallback. Korisnikovo ime table ostaje njegov unos.
- Pencil koristi determinističnu grafitnu teksturu, korisnikovu boju/širinu/providnost i isti renderer za live potez, dokument i izvoz. Pen i ostali alati imaju svoj postojeći stil.
- Preview pomeranja/skaliranja čuva originalni redosled slojeva, uz povratak izvora pri otkazivanju ili spoljašnjoj promeni. Page.geometry objedinjuje postojeće geometrijske potrošače.

JSON format i Room ostaju 4. Izolovani portovi i Connector jezgro još nisu korisnički alat; F05 ostaje otvoren. Dodatni pressure/tilt, backend, cloud, AI i Play objava ne ulaze u ovu verziju.

## Potvrđeni rezultati

| Provera | Testovi | Neuspesi / greške / preskakanja |
|---|---:|---:|
| JVM core / format / data / ViewModel | 209 + 43 + 5 + 26 = 283 | 0 |
| API 26 telefon | 97 app + 13 data | 0 |
| API 26 tablet, zaseban veliki AVD profil | 97 app + 13 data | 0 |
| API 30 telefon | 97 app + 13 data | 0 |
| API 33 telefon | 97 app + 13 data | 0 |
| API 36 telefon | 97 app + 13 data | 0 |

To je 393 jedinstvena JVM/app/data slučaja; ponavljanje na OS matrici nije novi jedinstveni slučaj. R8 smoke prolazi na API 26 telefonu/tabletu i API 36, uključujući prethodne boje, recovery, Save/reopen, rotaciju, slojeve i poravnanje. Na sva tri release profila oba jezička slučaja prolaze posle force-stop/relaunch-a, uz ponovno otvaranje sačuvane table i vraćen početni jezik. API 36 zadržava adaptivne resize provere; API 26 veliki ekran ima zaseban tablet AVD.

Stil/arhitektura, debug/release lint, 155 runtime licence/notices, OSV 155 bez prijavljenih poznatih nalaza, source scan i manifest/DEX politika prolaze. Offline font politika odbija EmojiCompatInitializer; nema INTERNET/ACCESS_NETWORK_STATE dozvole ili novog online modula. Ovo nije nezavisan pentest ili garancija odsustva nepoznatih propusta.

Svi originalni GitHub artifact SHA-256 provereni su pri čitanju arhiva u memoriji. Sačuvane `*-bounded-reports.zip` arhive su izvedene, uz pojedinačne entry hash-eve; nisu originalni GitHub ZIP-ovi. Unsigned APK SHA-256 `7FB27D4B5D44B6BE1DAF0F25DEFF4BE2A21554B3F922E2AA4F3DD8A84A348A43` odgovara CI security politici.

Lokalni APK prolazi postojeći Android Debug v2/v3 potpis, ZIP-P16 i četiri ELF64 LOAD/RELRO provere. Podržava arm64-v8a, armeabi-v7a, x86 i x86_64. Svih 381 ZIP unosa upoređeno je sa sva tri originalna CI release APK-a: payload i v1 MANIFEST.MF su identični; izuzeta su samo četiri imenovana v1 .RSA/.SF signer unosa, dok v2/v3 signing blokovi takođe očekivano razlikuju ephemeral CI i lokalni razvojni ključ. Jednakost celog potpisanog APK hash-a nije tvrđena.

| Paket | Bajtovi | SHA-256 |
|---|---:|---|
| `artifacts/whiteboard-local-0.6.4-preview.apk` | 2.455.682 | `5E9C94560BAFFE1C1BD90FB4A653F3BCA8AFBBFB7D1E4C7D3DBAC73074C1E615` |
| `artifacts/whiteboard-local-0.6.4-unsigned.aab` | 5.169.039 | `1D7585455F629FA8CD8558D2E3F511E4F49B20CC8DC53459E96D33C42EF49B6F` |

API 30 PNG stvarnog BoardRenderer-a pokazuje vidljivo Pencil zrno i ravnomeran Pen. Njegov SHA-256 je `D5725A4CB417235140BCD0C89426432726C7A2C336465250711C60C6B783A6F5`, jednak prethodno pregledanom prikazu, uz provereno poreklo iz ovog izvora. Ovo je ograničen vizuelni pregled, ne pregled svih korisničkih boja/širina.

Dokazi: `artifacts/task-24-25-29-9589e12-evidence.json`, `task-24-25-29-9589e12-packages.json`, `task-24-25-29-9589e12-release-content-match.json`, signature/security/native JSON-ovi i `task-29-9589e12-visual-review.json`. Potpis je razvojni, AAB unsigned; nema fizičke instalacije ili Play objave. Minimum je API 26, target 36. Poznate granice su fizički uređaji, slušni TalkBack, fizičko profilisanje i in-place OS upgrade; prolaz emulatora ih ne zatvara.

## Sačuvani neuspešni pokušaji

c201456/CI 41707278041 prolazi celu prethodnu matricu, ali lokalna proširena offline font politika odbija EmojiCompatInitializer. Njegovi odbijeni APK/AAB imaju posebna imena. Sledeće izmene uklanjaju samo taj initializer, uz očuvan Startup provider i ostale inicijalizatore; koristi se sistemski emoji/font. Nema nove INTERNET dozvole.

40088a5/CI 41713013132 otkriva zastareo notices inventar i UI fixture probleme. febd8e0/CI 41716444192 prolazi verify sa 155 runtime notices/OSV komponenti i sva tri Pencil testa na pet profila, ali pada na scroll/stale-reference proverama i pogrešnom PowerShell vezivanju `-W`. b8fefa5/CI 41724232978 popravlja scroll i ADB argumente, ali nova provera pogrešno traži clickable umesto checkable pretka već izabrane radio opcije. XML beleži stvarno označeno stanje. 9589e12 ispravlja izbor semantičkog pretka i dodaje povezanu native regresiju u postojeći accessibility test. Nijedan neuspešan pokušaj nije predstavljen kao kvalifikovana distribucija.
