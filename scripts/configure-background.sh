#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
adb_bin="${ADB:-adb}"
device="${1:?Pass an authorized ADB serial}"
# A single package addition only; does not replace system exemptions or VPN consent.
"$adb_bin" -s "$device" shell cmd deviceidle whitelist +com.tailscale.ipn
"$adb_bin" -s "$device" shell cmd deviceidle whitelist
