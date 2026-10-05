#!/usr/bin/env bash
# One command from a fresh clone to Jarvis running on your device.
#
#   bash scripts/bootstrap.sh
#
# It finds a JDK, fetches the native engines, builds the APK, and installs it
# to a connected phone if one is attached. Every step prints what it is doing
# and tells you exactly what to fix if something is missing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

bold() { printf "\n\033[1m==> %s\033[0m\n" "$1"; }
info() { printf "    %s\n" "$1"; }
die()  { printf "\n\033[31m✖ %s\033[0m\n\n" "$1"; exit 1; }

bold "Jarvis bootstrap"

# --- 1. Java 17 -------------------------------------------------------------
if ! command -v java >/dev/null 2>&1; then
  for cand in \
    "$HOME/android-studio/jbr" \
    "/opt/android-studio/jbr" \
    "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
    if [ -x "$cand/bin/java" ]; then
      export JAVA_HOME="$cand"
      export PATH="$JAVA_HOME/bin:$PATH"
      info "Using Android Studio's bundled JDK: $cand"
      break
    fi
  done
fi
command -v java >/dev/null 2>&1 \
  || die "Java 17 not found. Install a JDK 17 (see docs/TROUBLESHOOTING.md), then re-run."
info "Java: $(java -version 2>&1 | head -1)"

# --- 2. Android SDK ---------------------------------------------------------
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
  for cand in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    if [ -d "$cand" ]; then export ANDROID_HOME="$cand"; break; fi
  done
fi
if [ -n "${ANDROID_HOME:-}" ]; then
  info "Android SDK: $ANDROID_HOME"
elif [ -f local.properties ]; then
  info "Android SDK: from local.properties"
else
  info "Android SDK: not set — Gradle may ask for it (see docs/TROUBLESHOOTING.md)"
fi

# --- 3. executable bits -----------------------------------------------------
chmod +x gradlew scripts/fetch_native.sh 2>/dev/null || true

# --- 4. native engines ------------------------------------------------------
if [ ! -d app/src/main/cpp/third_party/llama.cpp ]; then
  bold "Fetching native engines (llama.cpp + whisper.cpp)…"
  bash scripts/fetch_native.sh
else
  info "Native engines already present."
fi

# --- 5. build ---------------------------------------------------------------
bold "Building APK with the native engine…"
./gradlew assembleDebug -Pjarvis.buildNative=true

APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || die "Build finished but $APK was not produced."
bold "Built: $APK"

# --- 6. install -------------------------------------------------------------
if command -v adb >/dev/null 2>&1 && [ "$(adb devices | grep -c 'device$')" -gt 0 ]; then
  bold "Installing to the connected device…"
  adb install -r "$APK"
  bold "Done. Open 'Jarvis' on your phone — it downloads its model on first run."
else
  bold "No device connected yet. When your phone is ready (USB debugging on):"
  info "adb install -r $APK"
  info "…or copy $APK to the phone and tap it."
fi
