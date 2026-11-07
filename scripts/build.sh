#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
mkdir -p artifacts
cp app/build/outputs/apk/debug/app-debug.apk artifacts/VISORA-debug.apk
shasum -a 256 artifacts/VISORA-debug.apk > artifacts/VISORA-debug.apk.sha256
