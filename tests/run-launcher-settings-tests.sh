#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
build="$repo/out/launcher-tests"
mkdir -p "$build"
javac -d "$build" "$repo/app/src/main/java/com/racas/black/LauncherSettings.java" "$repo/tests/LauncherSettingsTest.java"
java -cp "$build" com.racas.black.LauncherSettingsTest
