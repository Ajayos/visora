#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools
jar=.tools/ktfmt-0.65.jar
expected=aba9753b6ab387926179ab7318ba8e0c33dad483ebc72f633e79522265b15214
if [ ! -f "$jar" ]; then
  curl --fail --location --proto '=https' --tlsv1.2 https://github.com/Kotlin/ktfmt/releases/download/v0.65/ktfmt-0.65-with-dependencies.jar -o "$jar"
fi
actual="$(shasum -a 256 "$jar" | cut -d ' ' -f 1)"
[ "$actual" = "$expected" ] || { echo 'Formatter checksum mismatch'; exit 1; }
java -jar "$jar" --kotlinlang-style "$@" app/src app/build.gradle.kts build.gradle.kts settings.gradle.kts
