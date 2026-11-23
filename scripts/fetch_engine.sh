#!/usr/bin/env bash
# Downloads the pinned Cactus engine build for Android into ./engine/<platform>/.
# The engine ships as a static library that app/src/main/cpp links into libnimble.so.
set -euo pipefail

CACTUS_VERSION="${CACTUS_VERSION:-4.2.6}"
PLATFORMS="${PLATFORMS:-android-arm64}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENV="$ROOT/.engine-venv"

python3 -m venv "$VENV"
"$VENV/bin/pip" install --quiet --upgrade pip
"$VENV/bin/pip" install --quiet "cactus-compute==${CACTUS_VERSION}"

for platform in $PLATFORMS; do
  CACTUS_TELEMETRY=0 "$VENV/bin/cactus" download "$platform" --out "$ROOT/engine"
  echo "== $platform"
  ls -la "$ROOT/engine/$platform"
done
