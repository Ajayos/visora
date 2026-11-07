# Getting started

Build with JDK 17, Android SDK platform 35, and NDK 27.2.12479018. Set `ANDROID_HOME` and `ANDROID_NDK_HOME`, run `./scripts/build-ssh.sh`, then `./gradlew assembleDebug testDebugUnitTest lintDebug`.

Install the APK with an explicitly selected, authorized ADB serial. Launch `dev.visora.tv/dev.visora.MainActivity`. Do not publish deployment addresses or authorization material.

Menu opens settings. Rewind replays the visual introduction without restarting services. Screen sizing offers independent width and height settings from 70% to 100%. Open **Adjust screen edges with remote** to show a measured edge guide: left/right adjusts width and up/down adjusts height in 1% steps; Select toggles aspect fitting, Menu resets, and Back saves. Aspect preservation fits the 16:9 composition inside the selected rectangle; disabling it stretches the composition. This covers different resolutions and TV overscan without hardcoding a physical display size.

Weather is initially unconfigured. Search for your location in settings. Open-Meteo supplies weather and geocoding data. Expired readings are hidden.

Install and configure supported casting and VPN applications separately. Their consent screens remain under their control.
