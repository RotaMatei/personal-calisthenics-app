#!/usr/bin/env bash
# Compile and run the pure-Kotlin :core module tests without Gradle or Maven.
#
# Why: some sandboxes cannot reach Maven Central / Google Maven / Gradle distributions. This script only needs
# a JDK plus a Kotlin compiler, which it downloads from GitHub Releases on first use. Tests are compiled against
# a tiny JUnit shim (tools/junit-shim); the real Gradle build uses the real JUnit 4.
#
# Usage:  tools/run-core-tests.sh [class-name-filter]
# Env:    KOTLIN_CACHE  directory holding (or receiving) kotlinc/  (default /tmp/kotlinc-cache)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KVER="${KOTLIN_VERSION:-2.0.21}"
CACHE="${KOTLIN_CACHE:-/tmp/kotlinc-cache}"
OUT="$ROOT/tools/out"
KOTLINC="$CACHE/kotlinc/bin/kotlinc"
STDLIB="$CACHE/kotlinc/lib/kotlin-stdlib.jar"

if [ ! -x "$KOTLINC" ]; then
  echo ">> Downloading Kotlin $KVER compiler from GitHub Releases"
  mkdir -p "$CACHE"
  curl -sSL -o "$CACHE/kotlin-compiler.zip" \
    "https://github.com/JetBrains/kotlin/releases/download/v$KVER/kotlin-compiler-$KVER.zip"
  unzip -q -o "$CACHE/kotlin-compiler.zip" -d "$CACHE"
fi

rm -rf "$OUT"
mkdir -p "$OUT/shim" "$OUT/main" "$OUT/test"

echo ">> Compiling JUnit shim"
javac -d "$OUT/shim" $(find "$ROOT/tools/junit-shim" -name '*.java')

echo ">> Compiling :core main"
"$KOTLINC" -nowarn -jvm-target 17 -d "$OUT/main" $(find "$ROOT/core/src/main" -name '*.kt') 2>&1 \
  | grep -v "JAVA_TOOL_OPTIONS" || true
[ -d "$OUT/main/com" ] || { echo "main compilation failed"; exit 2; }

echo ">> Compiling :core tests"
"$KOTLINC" -nowarn -jvm-target 17 -cp "$OUT/main:$OUT/shim:$STDLIB" -d "$OUT/test" \
  $(find "$ROOT/core/src/test" -name '*.kt') 2>&1 | grep -v "JAVA_TOOL_OPTIONS" || true
[ -d "$OUT/test/com" ] || { echo "test compilation failed"; exit 2; }

echo ">> Running tests"
java -cp "$OUT/main:$OUT/test:$OUT/shim:$STDLIB" ShimRunner "$OUT/test" "${1:-}" 2>&1 | grep -v "JAVA_TOOL_OPTIONS"
exit "${PIPESTATUS[0]}"
