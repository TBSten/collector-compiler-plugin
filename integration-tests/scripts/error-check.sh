#!/usr/bin/env bash
# Checks the errors and warnings reported by the plugin against the `// E: <NAME>` / `// W: <NAME>` markers in errors/.
# Warnings are not printed when compilation fails, so the warning cases (errors/warnings) are compiled separately.
# Run from integration-tests/: ./scripts/error-check.sh
set -u
cd "$(dirname "$0")/.."
mkdir -p build
FAILED=0

# check <label> <source dir> <errorCases value> <expect compile success: true|false>
check() {
    local label=$1 dir=$2 cases=$3 succeed=$4 log=build/error-check-$3.log
    if ./gradlew :errors:compileKotlinJvm -Pcollector.errorCases="$cases" > "$log" 2>&1; then
        [ "$succeed" == true ] || { echo "FAIL  $label: compiled without errors"; FAILED=1; return; }
    else
        [ "$succeed" == false ] || { echo "FAIL  $label: compilation failed"; grep -E "^e: " "$log" | head; FAILED=1; return; }
    fi
    # "<file>:<line> <e|w> <NAME>"
    local expected actual
    expected=$(cd "$dir" && grep -rnHE "// [EW]: [A-Z_]+$" --include='*.kt' . | sed -E 's#^[^:]*/##' \
        | sed -E 's/^([^:]+):([0-9]+):.*\/\/ ([EW]): ([A-Z_]+)$/\1:\2 \3 \4/' | sed 's/ E / e /; s/ W / w /' | sort -u)
    actual=$(grep -E "^[ew]: .*/$dir/[^:]+:[0-9]+:[0-9]+ \[[A-Z_]+\]" "$log" \
        | sed -E 's/^([ew]): .*\/([^/:]+):([0-9]+):[0-9]+ \[([A-Z_]+)\].*/\2:\3 \1 \4/' | sort -u)
    if [ "$expected" == "$actual" ]; then
        echo "PASS  $label: $(echo "$expected" | wc -l) expected diagnostics reported"
    else
        echo "FAIL  $label: reported diagnostics differ from the markers (< expected, > actual)"
        diff <(echo "$expected") <(echo "$actual") | sed 's/^/      /'
        grep -E "^[ew]: " "$log" | head -30
        FAILED=1
    fi
}

check "errors" errors/src/commonMain/kotlin true false
check "warnings" errors/warnings warnings true
exit $FAILED
