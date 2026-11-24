#!/usr/bin/env bash
# Checks that incremental compilation picks up changes in upstream modules.
# Run from integration-tests/: ./scripts/ic-check.sh
set -u
cd "$(dirname "$0")/.."
mkdir -p build
FAILED=0
ITEM_FILE=items2/src/commonMain/kotlin/Items2b.kt
COLLECTOR_FILE=defs/src/commonMain/kotlin/DefsExtra.kt
TEST_FILE=app/src/commonTest/kotlin/ExtraTest.kt

cleanup() { rm -f "$ITEM_FILE" "$COLLECTOR_FILE" "$TEST_FILE"; }
trap cleanup EXIT

expect_pass() { # <label> <gradle args...>
    local label=$1; shift
    if ./gradlew :app:jvmTest "$@" -q > build/ic-check.log 2>&1; then
        echo "PASS  $label"
    else
        echo "FAIL  $label"; grep -m3 -E "expected|FAILED" build/ic-check.log; FAILED=1
    fi
}

cleanup
./gradlew :app:jvmTest -q > /dev/null 2>&1
expect_pass "baseline"

cat > "$ITEM_FILE" <<'K'
package items2

import defs.Entry
import defs.Greeter

@Entry
object D : Greeter { override val name = "d" }
K
expect_pass "item added in an upstream module is collected" -PextraEntries=d

rm -f "$ITEM_FILE"
expect_pass "item removed from an upstream module disappears"

cat > "$COLLECTOR_FILE" <<'K'
package defs

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

@CollectorFor<Entry>()
object EntryAnyCollector : Collector<Any>
K
cat > "$TEST_FILE" <<'K'
import defs.EntryAnyCollector
import me.tbsten.collector.collectAsList
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtraTest {
    @Test
    fun newCollectorSeesExistingItems() {
        assertEquals(4, EntryAnyCollector.collectAsList().size)
    }
}
K
expect_pass "Collector added upstream sees items of already-compiled modules"

# tags: a call by tag must see items and Collectors added upstream
# (only ExtraTest runs here, because TagAndAnnotationTest lists the plugins of the baseline)
tag_test() { # <expected count>
    cat > "$TEST_FILE" <<K
import defs.Plugin
import me.tbsten.collector.collectAsList
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtraTest {
    @Test
    fun pluginsByTag() {
        assertEquals($1, collectAsList<Plugin>("plugins").size)
    }
}
K
}
cleanup
cat > "$ITEM_FILE" <<'K'
package items2

import defs.Plugin
import defs.PluginA

@PluginA
object Gamma : Plugin { override val id = "gamma" }
K
tag_test 4
expect_pass "item added upstream is collected by a tag call" --tests ExtraTest

cat > "$COLLECTOR_FILE" <<'K'
package defs

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

annotation class PluginC

@CollectorFor<PluginC>(tag = "plugins")
object PluginCCollector : Collector<Plugin>
K
cat > "$ITEM_FILE" <<'K'
package items2

import defs.Plugin
import defs.PluginC

@PluginC
object Gamma : Plugin { override val id = "gamma" }
K
expect_pass "Collector added upstream with an existing tag joins the tag call" --tests ExtraTest

cleanup
expect_pass "back to baseline"
exit $FAILED
