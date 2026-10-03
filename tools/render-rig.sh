#!/usr/bin/env bash
# Render contact sheets (PNG) of the body rig for visual review. JVM only; no Gradle needed.
# Usage: tools/render-rig.sh [outDir] [exerciseId ...|all]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KVER="${KOTLIN_VERSION:-2.0.21}"
CACHE="${KOTLIN_CACHE:-/tmp/kotlinc-cache}"
KOTLINC="$CACHE/kotlinc/bin/kotlinc"
STDLIB="$CACHE/kotlinc/lib/kotlin-stdlib.jar"
OUT="$ROOT/tools/out/preview"
[ -x "$KOTLINC" ] || { echo "Run tools/run-core-tests.sh once first to fetch kotlinc"; exit 2; }
mkdir -p "$OUT"
DEST="${1:-$ROOT/tools/out/rig-png}"
shift || true
"$KOTLINC" -nowarn -jvm-target 17 -d "$OUT" \
  $(find "$ROOT/core/src/main" -name '*.kt') "$ROOT/tools/rig-preview/RigPreview.kt" 2>&1 \
  | grep -v "JAVA_TOOL_OPTIONS" || true
java -Djava.awt.headless=true -cp "$OUT:$STDLIB" com.personal.calisthenics.tools.RigPreviewKt "$DEST" "${@:-all}" 2>&1 | grep -v "JAVA_TOOL_OPTIONS"
