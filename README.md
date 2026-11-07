# VISORA

A lightweight native Android TV display for animated branding, promotional media, casting access, and optional remote management.

![Android verification](https://github.com/ajayos/visora/actions/workflows/android.yml/badge.svg)

## Experience

- Layered animated startup, galaxy background, clock, live diagnostic terminal, and configurable motion quality.
- Independent AOS and Deluper brand configurations and campaign assets. Displaying both brands does not imply a business relationship.
- Cached image and video playlists, schedules, themes, and offline playback.
- Fire TV remote navigation: **Menu** opens settings; **Rewind** replays the visual introduction.
- Centered 16:9 fitting, independent display width and height adjustment, and optional stretch mode for different screens and overscan.
- Idle black screen with paused rendering; background supervision continues separately.
- Optional key-only SSH bound to the observed tailnet interface, within the application's sandbox.

## Build

Install JDK 17, Android SDK platform 35, and Android NDK `27.2.12479018`. Export `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_NDK_HOME` for your environment.

```sh
./scripts/build-ssh.sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
```

Run `./scripts/format.sh` to apply the repository Kotlin style, or append `--dry-run --set-exit-if-changed` to verify it.

The native SSH executables are built from the vendored, patched Dropbear source. Build them before packaging an APK. The application uses Kotlin, Android APIs, and platform media playback without a large UI framework.

## Install and configure

Enable ADB on your own trusted network, authorize your development computer, and use your device's serial:

```sh
adb devices
adb -s "$VISORA_DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$VISORA_DEVICE" shell am start -n dev.visora.tv/dev.visora.MainActivity
```

Open **Menu → Screen sizing → Adjust screen edges with remote** for a full-screen calibration guide. Use left/right for width, up/down for height, Select for aspect mode, Menu to reset, and Back to save. Dimensions change in 1% steps. Weather starts unconfigured; choose a location in settings. VPN consent and external application setup must be completed interactively when required. Boot and background behavior depend on the host OS and installed applications; the app does not replace firmware boot graphics or require root.

## APK releases

Publishing a GitHub release runs verification, builds the native component and APKs, and attaches APK files plus SHA-256 checksums. A workflow can also be run manually from **Actions**.

For a stable, installable release APK, configure the repository Actions secrets `VISORA_KEYSTORE_BASE64`, `VISORA_STORE_PASSWORD`, `VISORA_KEY_ALIAS`, and `VISORA_KEY_PASSWORD`. Keep the keystore backed up securely and use the same signing identity for future updates. Without these secrets, automation publishes a clearly named **unsigned release APK** and a **debug preview APK**. Debug preview signing identities can change between builds and are unsuitable for upgrade continuity. Unsigned APKs require signing before installation.

Release version names and monotonically increasing version codes are maintained in `app/build.gradle.kts`; update them before tagging a release.

## Remote management and security

AOS Core enrollment requires a compatible implementation of the [device protocol](docs/wiki/Remote-Management.md). The client is available; a production server integration is not bundled or presumed verified. Unenrolled installations use cached content and do not contact a preset Core server.

Credentials use AndroidKeyStore-backed encryption. Commands require signatures, replay protection, and local authorization. Updates must match the installed signing identity and require the supported installer flow. SSH is disabled until explicitly enabled with an administrator public key. Tailnet ACLs must restrict management access to trusted administrators; local interface binding does not replace access control.

Deployment addresses, enrollment tokens, SSH keys, signing keys, device reports, and local toolchains are excluded from this repository. Never expose ADB or SSH publicly.

## Documentation

Read the [wiki](https://github.com/ajayos/visora/wiki), or its [versioned source](docs/wiki/Home.md), for architecture, operation, release signing, and integration limits.

## Third-party notices

Dropbear and its bundled cryptographic libraries retain their upstream licenses in `third_party/dropbear`. Android adaptations are recorded in `VISORA-ANDROID.patch`. Branding assets are supplied for the VISORA experience; no trademark license or affiliation is implied.
