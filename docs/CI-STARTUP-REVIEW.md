# CI startup — sprečiti unos u nedovršen launcher

Status 2. novembra 2026: promena pripremljena, izvršenje nove CI matrice još nije potvrđeno. Ne menja produkcijski APK kod, izbor OS profila ili test asercije. Kvalifikovani 0.6.3/source a230f11 ostaje zaseban dokaz.

Prvi pokušaj nove skripte, source 5afa4d3/CI 41514731400, prolazi verify/244 JVM, ali svih pet device poslova staje pre boot-a: moj poziv `emulator -version` bira windowed qemu-system-x86_64, koji na headless hostu nema libpulse.so.0. Log i ci-emulator-version.txt to potvrđuju. Ispravka čita SDK package metadata, dok se stvarni emulator i dalje pokreće sa -no-window i zapisuje svoju verziju u log. Ne menja se skup Linux biblioteka ili Android testova radi ovog upita. Taj neuspešan pokušaj ostaje u artifacts/task-03-5afa4d3-*.

## Nalaz i granice zaključka

CI 41511522030/source 49a5de9 prolazi verify sa 244 JVM, ali API 36 pokušaj prekida pre app testova zbog Quickstep startup ANR-a. Log pokazuje boot completed u 19:45:07.881 UTC, `input keyevent 82` u 19:45:07.890 i ANR sa vremenom 19:45:12, razlog: application does not have a focused window. Keyevent komanda završava tek oko 19:45:15.375. Dokazi: artifacts/task-15-49a5de9-api36.log, -api36-boot-anr.txt i originalni ZIP digest u evidence JSON-u.

Pinned [runner izvor](https://raw.githubusercontent.com/ReactiveCircus/android-emulator-runner/a421e43855164a8197daf9d8d40fe71c6996bb0d/src/emulator-manager.ts) šalje taj događaj odmah po sys.boot_completed, pre našeg 60s clean-boot preflight-a. Vremenska korelacija i razlog ANR-a ukazuju na trku prvog unosa sa nedovršenim fokusom/prozorom. To je obrazložena hipoteza, ne dokaz da je svaki raniji emulator ANR imao isti uzrok ili da je promena već otklonila problem.

## Novi lifecycle

`Run-CiEmulator.sh` radi samo na Linux GitHub Actions-u. Kreira sopstveni svež AVD u RUNNER_TEMP, sa istim pixel_2/pixel_c profilima, dva jezgra i prethodnim GPU opcijama (API 36 GLES/SwiftShader bez Vulkan-a). Ne pokreće lokalni SDK, emulator ili Gradle na korisnikovom računaru.

SDK install ima do tri jasno zabeležena, vremenski ograničena pokušaja zbog ranijeg nečitljivog ZIP preuzimanja. Emulator boot i app suite se ne ponavljaju unutar skripte. Svi neuspešni pokušaji instalacije ostaju u ci-sdk-install.log; tri neuspeha prekidaju posao.

Posle boot signala čeka se najmanje 60s guest uptime-a, bez događaja tastature/dodira. Nepoznat health izlaz ili zabeležen ANR prekidaju posao. Zatim se koristi `wm dismiss-keyguard`, podržan i u [Android 8 AOSP komandi](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r36/cmds/wm/src/com/android/commands/wm/Wm.java), umesto prerano ubrizganog Menu događaja.

AWK provera zahteva fokus launchera i HAS_DRAWN njegovog sopstvenog prozora u kompletnom `dumpsys window -a` izlazu. Nacrtan System UI nije dovoljan. `-a` je bitan jer je draw-state u [WindowStateAnimator dump-u](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/services/core/java/com/android/server/wm/WindowStateAnimator.java) detaljan podatak. Tek potom se konfigurišu animacije i poziva postojeći Run-DeviceCi, koji ponovo zahteva čist boot pre app testova.

EXIT trap čuva stvarni neuspešan izlazni status, hvata dijagnostiku i zaustavlja samo sopstveni emulator. Rani SDK/lifecycle logovi, potpuni window dump i readiness screenshot ulaze u CI artefakt. ANR dijalog se ne zatvara i ne resetuje se njegova evidencija radi prolaza.

## Provera

Lokalno prolaze Bash sintaksa, whitespace i Test-CiStartup: spreman launcher; odbijanje nacrtanog System UI-ja uz nedovršen launcher, null/drugi fokus, nepoznat format i zabrana lokalnog SDK/AVD pokretanja. Nisu izvršavani lokalni Android build/emulator poslovi.

Za prihvatanje su potrebni stvarni uspešni startup/fokus/HAS_DRAWN i clean-boot izveštaji na svih pet CI profila, zatim 244 JVM, po 86 app + 13 data i minified smoke. Jedan zelen pokušaj ne dokazuje trajno odsustvo svih infrastrukturnih grešaka. Fizički/Vulkan profil i prava OS nadogradnja ostaju izvan ovog CI nalaza.
