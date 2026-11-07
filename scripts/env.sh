#!/usr/bin/env bash
# Source from the repository root, or use an existing JDK 17 / Android SDK.
VISORA_ROOT="$(pwd)"
if [ -d "$VISORA_ROOT/.tools/jdk-17.0.21+9/Contents/Home" ]; then
  export JAVA_HOME="$VISORA_ROOT/.tools/jdk-17.0.21+9/Contents/Home"
fi
export ANDROID_HOME="${ANDROID_HOME:-$VISORA_ROOT/.tools/android-sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$VISORA_ROOT/.tools/gradle-home}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
