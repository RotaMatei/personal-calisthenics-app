#!/usr/bin/env bash
# Render the skinned human mesh (PNG) for visual review. JVM only; no Gradle needed.
# Usage: tools/render-mesh.sh <outDir> big <id> <A|B|keyframe> <yaw> <pitch> [yaw pitch ...]
#        tools/render-mesh.sh <outDir> strip <id> [frames] [yaw pitch ...]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${KOTLIN_CACHE:-/tmp/kotlinc-cache}"
KOTLINC="$CACHE/kotlinc/bin/kotlinc"
STDLIB="$CACHE/kotlinc/lib/kotlin-stdlib.jar"
OUT="$ROOT/tools/out/mesh-preview"
[ -x "$KOTLINC" ] || { echo "Run tools/run-core-tests.sh once first to fetch kotlinc"; exit 2; }
rm -rf "$OUT"; mkdir -p "$OUT"
"$KOTLINC" -nowarn -jvm-target 17 -d "$OUT" \
  $(find "$ROOT/core/src/main" -name '*.kt') "$ROOT/tools/mesh-preview/MeshPreview.kt" 2>&1 \
  | grep -v "JAVA_TOOL_OPTIONS" || true
[ -d "$OUT/com" ] || { echo "compilation failed"; exit 2; }
java -Djava.awt.headless=true -cp "$OUT:$ROOT/core/src/main/resources:$STDLIB" com.personal.calisthenics.tools.MeshPreview "$@" 2>&1 | grep -v "JAVA_TOOL_OPTIONS"
