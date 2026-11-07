# Releases

The Android workflow runs on pushes to master, pull requests, manual dispatch, and published GitHub releases. It installs the specified SDK and NDK, builds native SSH components, runs JVM tests and Android lint, packages APKs, and uploads artifacts. Published releases receive APKs and checksums automatically.

To enable production signing, set these repository Actions secrets:

| Secret | Value |
| --- | --- |
| VISORA_KEYSTORE_BASE64 | Base64-encoded release keystore |
| VISORA_STORE_PASSWORD | Keystore password |
| VISORA_KEY_ALIAS | Signing alias |
| VISORA_KEY_PASSWORD | Key password |

A partial signing configuration fails the build. No signing material is committed. Without signing secrets, release output is explicitly unsigned, with a separate development preview APK. Keep a secure offline backup of the signing identity. Use the same signer and a higher version code for upgrades. Debug previews are not suitable for stable update continuity.

Before publishing, update versionCode and versionName in app/build.gradle.kts and verify on representative hardware. Unit tests and lint do not establish that every vendor-specific behavior is supported.
