#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
build="$repo/out/launcher-tests"
mkdir -p "$build"
compiler=(javac)
if ! command -v javac >/dev/null 2>&1; then compiler=(java -m jdk.compiler/com.sun.tools.javac.Main); fi
"${compiler[@]}" -d "$build" "$repo/app/src/main/java/com/racas/black/LauncherSettings.java" "$repo/app/src/main/java/com/racas/black/ControlLayout.java" "$repo/tests/LauncherSettingsTest.java" "$repo/tests/ControlLayoutTest.java" "$repo/app/src/main/java/com/racas/black/ReleaseVersion.java" "$repo/tests/ReleaseVersionTest.java"
java -cp "$build" com.racas.black.LauncherSettingsTest
java -cp "$build" com.racas.black.ControlLayoutTest "$build/editor-layout.toml"
java -cp "$build" com.racas.black.ReleaseVersionTest
