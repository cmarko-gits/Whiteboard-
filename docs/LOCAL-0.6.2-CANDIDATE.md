# Lokalni kandidat 0.6.2 / code 10

Proveren 2. novembra 2026. Izvor `481ab6412bc4f9c855b3091dde2ed03a63ef3551`, grana `codex/selection-layering`, [CI 41504676373](https://github.com/cmarko-gits/Whiteboard-/actions/runs/41504676373). Svih šest poslova završeno je uspešno. Ovo je razvojni lokalni paket, bez Play objave.

Uključuje prethodno proverene [rotaciju i završni uzorak gesta](LOCAL-0.6.1-CANDIDATE.md), kao i Bring forward one layer / Send backward one layer. Svaki izabrani blok prelazi jednog suseda, uz očuvanje relativnog redosleda, identiteta, geometrije, grupe i priloga. Jedna akcija daje jednu Undo komandu. Na granici je akcija onemogućena; no-op ne prlja dokument i ne briše Redo. Postojeće krajnje slojne akcije koriste istu budžetiranu ID istoriju. Format dokumenta ostaje 4, Room 4.

## Pregledani rezultati

| Provera | Testovi | Neuspesi / greške / preskakanja |
|---|---:|---:|
| JVM core / format / data / ViewModel | 140 + 43 + 5 + 21 = 209 | 0 |
| API 26 telefon | 85 app + 13 data | 0 |
| API 26 tablet, zaseban veliki AVD profil | 85 app + 13 data | 0 |
| API 30 telefon | 85 app + 13 data | 0 |
| API 33 telefon | 85 app + 13 data | 0 |
| API 36 telefon, eksplicitni GLES renderer | 85 app + 13 data | 0 |

To je 307 jedinstvenih JVM/app/data slučajeva; OS ponavljanja nisu dodatni jedinstveni testovi. Novi testovi pokrivaju blokove/disjunktnu selekciju, budžet, 10.000 objekata, asset retention, stale/malformed komande i UI meni/Undo/Redo/Save/reopen/stvarni PNG redosled.

Minified release smoke prolazi na API 26 telefonu/tabletu i API 36: stvaran izbor objekta, obe granične dostupnosti menija posle pomeranja, Undo/Redo, rotacija, IME/boje, recovery i Save/reopen. API 36 uključuje adaptivne širine i orijentacije; API 26 runtime DPI override je izričito izostavljen, a veliki ekran proverava stvarni tablet profil.

Pregledana su tri snimka konkretnog izvora: API 36 kompaktni dijalog boje u landscape-u, adaptivni tablet landscape editor i API 26 tablet selekcioni meni. Kontrole aplikacije su vidljive i nove granične stavke imaju očekivano stanje. Na prvom snimku sistemska IME oblast je crna: tasteri sistemske tastature ne mogu se vizuelno oceniti iz tog snimka. IME stanje i završetak toka proveravaju odvojene automatizovane provere. Ovo nije pregled svih tema, velikih fontova ili TalkBack govora.

## Bezbednost i artefakti

Stil/arhitektura, debug/release lint, R8, OSV 147 runtime paketa bez prijavljenih poznatih nalaza, licence/notices, source secret scan i manifest/DEX politika prolaze. Lokalni preview prolazi v2/v3 potpis postojećim Android Debug sertifikatom, ZIP-P16 i četiri ELF64 LOAD/RELRO provere. Deklarisane biblioteke pokrivaju arm64-v8a, armeabi-v7a, x86 i x86_64. Ovaj obim nije nezavisan pentest.

| Paket | Bajtovi | SHA-256 |
|---|---:|---|
| `artifacts/whiteboard-local-0.6.2-preview.apk` | 1.952.702 | `52DB9C3573D4CE3CD1C9A3814750C86E4D36253ED43C3BBA25DC8DA7C6B455D8` |
| `artifacts/whiteboard-local-0.6.2-unsigned.aab` | 4.453.752 | `0BB2AB5E5D5D24B5CD8821348C73BB8732EC07978E66662E8A18F6F7EB1575D3` |

Svaki preuzeti ZIP odgovara GitHub SHA-256 digest-u; unsigned APK odgovara CI security policy hash-u pre lokalnog potpisivanja. Dokazi su u `artifacts/task-13-481ab64-evidence.json`, `artifacts/task-13-481ab64-packages.json`, potpisu i security JSON-ovima istog prefiksa. APK je razvojni preview, AAB unsigned; fizička instalacija nije izvršena.

Hardverski/OEM/stylus rad, fizičko release profilisanje, slušni TalkBack pregled i prava OS nadogradnja ostaju otvoreni. Backend/cloud/AI i Play objava ostaju van aktivnog obima. Radni 0.6.3 sa poravnanjem je zaseban kandidat, bez pripisivanja ovih rezultata njegovom izvoru.
