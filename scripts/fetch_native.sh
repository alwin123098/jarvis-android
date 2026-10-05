#!/usr/bin/env bash
# Vendors the native inference engines into app/src/main/cpp/third_party.
# Pinned to known-good tags so the JNI layer in this repo stays compatible.
#
#   llama.cpp   -> GGUF transformer inference (the "brain")
#   whisper.cpp -> speech-to-text
#
# Version note: whisper.cpp v1.7.4's whisper.h includes ggml-cpu.h, which only
# exists in newer ggml. llama.cpp b4211 provides it, so both share one ggml
# build (whisper.cpp reuses the ggml target when one already exists).
#
# Usage:  ./scripts/fetch_native.sh
set -euo pipefail

LLAMA_TAG="b4211"
WHISPER_TAG="v1.7.4"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/third_party"
mkdir -p "$DEST"

clone_pinned() {
  local url="$1" tag="$2" dir="$3"
  if [ -d "$DEST/$dir" ]; then
    echo "  -> $dir already present, skipping"
    return
  fi
  echo "  -> cloning $dir @ $tag"
  git clone --depth 1 --branch "$tag" --recurse-submodules --shallow-submodules \
    "$url" "$DEST/$dir"
  # Safety net: whisper.cpp vendors ggml as a submodule; make sure it is present.
  git -C "$DEST/$dir" submodule update --init --recursive --depth 1 || true
}

echo "Fetching native engines into $DEST"
clone_pinned "https://github.com/ggerganov/llama.cpp.git"   "$LLAMA_TAG"   "llama.cpp"
clone_pinned "https://github.com/ggerganov/whisper.cpp.git" "$WHISPER_TAG" "whisper.cpp"

echo
echo "Done. Now build with native enabled:"
echo "  ./gradlew assembleDebug -Pjarvis.buildNative=true"
