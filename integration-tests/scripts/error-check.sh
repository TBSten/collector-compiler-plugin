#!/usr/bin/env bash
# Checks the compile errors reported by the plugin against the `// E: <NAME>` markers in errors/.
# Run from integration-tests/: ./scripts/error-check.sh
set -u
cd "$(dirname "$0")/.."
mkdir -p build
LOG=build/error-check.log
SRC=errors/src/commonMain/kotlin/Errors.kt

if ./gradlew :errors:compileKotlinJvm -Pcollector.errorCases=true -q > "$LOG" 2>&1; then
    echo "FAIL  errors/ compiled without errors"; exit 1
fi

expected=$(grep -nE "// E: [A-Z_]+$" "$SRC" | sed -E 's/^([0-9]+):.*\/\/ E: ([A-Z_]+).*/\1 \2/' | sort -u)
actual=$(grep -E "^e: .*Errors.kt:[0-9]+:[0-9]+ " "$LOG" \
    | sed -E 's/^e: .*Errors.kt:([0-9]+):[0-9]+ \[([A-Z_]+)\].*/\1 \2/' | sort -u)

if [ "$expected" == "$actual" ]; then
    echo "PASS  $(echo "$expected" | wc -l) expected errors reported"
else
    echo "FAIL  reported errors differ from the markers"
    diff <(echo "$expected") <(echo "$actual") | sed 's/^/      /'
    grep "^e: " "$LOG" | head -20
    exit 1
fi
