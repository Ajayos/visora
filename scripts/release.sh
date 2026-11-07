#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
: "${VISORA_KEYSTORE:?Set a secure production keystore path}"
: "${VISORA_STORE_PASSWORD:?Set the keystore password}"
: "${VISORA_KEY_ALIAS:?Set the signing alias}"
: "${VISORA_KEY_PASSWORD:?Set the signing key password}"
./gradlew --no-daemon :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease --console=plain
mkdir -p artifacts
cp app/build/outputs/apk/release/app-release.apk artifacts/VISORA-release.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs artifacts/VISORA-release.apk
shasum -a 256 artifacts/VISORA-release.apk > artifacts/VISORA-release.apk.sha256
