#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
VISORA_DEVICE="${VISORA_DEVICE:?Set VISORA_DEVICE to an authorized ADB serial}"
VISORA_APK="${1:-artifacts/VISORA-debug.apk}"
test -f "$VISORA_APK"
adb connect "$VISORA_DEVICE"
adb -s "$VISORA_DEVICE" install -r "$VISORA_APK"
adb -s "$VISORA_DEVICE" shell am start -n dev.visora.tv/dev.visora.MainActivity
