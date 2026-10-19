#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
bash "$repo/tests/run-launcher-settings-tests.sh"
build=${BLACK_TEST_BUILD:-"$repo/out/control-tests"}
deps=${BLACK_TEST_DEPS:-"$build/deps"}
mkdir -p "$build" "$deps"

fetch() {
    if [[ ! -d "$deps/$1/.git" ]]; then
        git clone --quiet --depth 1 --branch "$3" "$2" "$deps/$1"
    fi
}
fetch SDL https://github.com/libsdl-org/SDL.git release-3.4.16
fetch imgui https://github.com/ocornut/imgui.git v1.91.0
fetch tomlplusplus https://github.com/marzer/tomlplusplus.git v3.4.0
sdk=${XBE_SDK_DIR:-"$repo/sdk"}
if [[ ! -f "$sdk/runtime/core/input.h" ]]; then
    fetch sdk https://github.com/TGP482/XboxXBERecomp.git main
    sdk="$deps/sdk"
fi

flags=(-std=c++20 -O1 -g -pthread -ffunction-sections -fdata-sections -Wl,--gc-sections)
if [[ ${BLACK_TEST_SANITIZE:-0} == 1 ]]; then
    flags+=(-fsanitize=address,undefined -fno-omit-frame-pointer)
fi
"${CXX:-g++}" "${flags[@]}" -I"$repo/src" -I"$repo/native" -I"$sdk/runtime" \
    -I"$deps/SDL/include" -I"$deps/imgui" -I"$deps/tomlplusplus/include" \
    "$repo/tests/touch_controls.cpp" -o "$build/touch-controls-tests"
"$build/touch-controls-tests" "$repo/out/launcher-tests/editor-layout.toml"
java -cp "$repo/out/launcher-tests" com.racas.black.ControlLayoutTest "$repo/out/launcher-tests/editor-layout.toml.native" native
