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

# JVM-only modules (ic-defs <- ic-items <- ic-app), recompiled file by file. Main.kt of ic-app is never touched: it has
# to be recompiled because of what changes around it. One change after another, without a clean build in between.
DEFS=ic-defs/src/main/kotlin/Defs.kt
ITEMS=ic-items/src/main/kotlin/Items.kt
LOCAL=ic-app/src/main/kotlin/Local.kt
rm -rf build/ic-sources && mkdir -p build/ic-sources
cp "$DEFS" "$ITEMS" "$LOCAL" build/ic-sources/
PLAIN_ITEMS=ic-items/src/main/kotlin/Plain.kt
PLAIN_APP=ic-app/src/main/kotlin/Plain.kt
restore_sources() {
    cp build/ic-sources/Defs.kt "$DEFS"; cp build/ic-sources/Items.kt "$ITEMS"; cp build/ic-sources/Local.kt "$LOCAL"
    rm -f "$PLAIN_ITEMS" "$PLAIN_APP"
}
trap 'cleanup; restore_sources' EXIT
edit() { # <sed expression> <file>
    sed "$1" "$2" > build/ic-sources/edit.tmp && cp build/ic-sources/edit.tmp "$2"
}

expect_output() { # <label> <expected output of :ic-app:run>
    local actual
    actual=$(./gradlew :ic-app:run -q 2> build/ic-check.log)
    if [ "$actual" == "$2" ]; then
        echo "PASS  $1"
    else
        echo "FAIL  $1"; echo "      expected: $2"; echo "      actual:   $actual"
        grep -m3 -E "^e: |Exception|Error" build/ic-check.log | sed 's/^/      /'; FAILED=1
    fi
}

expect_output "jvm: baseline" "[loc1, loc2, up1, up2]"

# A file without items or calls is edited: the items of the other files of its module stay. Kotlin deletes what a
# plugin generated in every incremental round, so the plugin has the files of items and of calls compiled again each
# time; without that, the items of the files that were not edited would be gone.
printf 'package ic.items\n\nfun plain() = 1\n' > "$PLAIN_ITEMS"
printf 'package ic.app\n\nfun plain() = 1\n' > "$PLAIN_APP"
expect_output "jvm: files without items added" "[loc1, loc2, up1, up2]"

edit 's/= 1/= 2/' "$PLAIN_ITEMS"
expect_output "jvm: file without items edited in the module of the items" "[loc1, loc2, up1, up2]"

edit 's/= 1/= 2/' "$PLAIN_APP"
expect_output "jvm: file without items edited in the module of the call" "[loc1, loc2, up1, up2]"

edit 's/^@Tracked val loc2/val loc2/' "$LOCAL"
expect_output "jvm: annotation removed in the module of the call" "[loc1, up1, up2]"

cp build/ic-sources/Local.kt "$LOCAL"
edit 's/val loc2 = "loc2"/val loc2 = 2/' "$LOCAL"
expect_output "jvm: item of the module of the call no longer converts" "[loc1, up1, up2]"

mv "$LOCAL" "${LOCAL%Local.kt}Moved.kt"
expect_output "jvm: file of items renamed" "[loc1, up1, up2]"

rm "${LOCAL%Local.kt}Moved.kt"
expect_output "jvm: file of items removed" "[up1, up2]"

cp build/ic-sources/Local.kt "$LOCAL"
expect_output "jvm: file of items back" "[loc1, loc2, up1, up2]"

edit 's/val up2 = "up2"/val up2 = 2/' "$ITEMS"
expect_output "jvm: upstream item no longer converts" "[loc1, loc2, up1]"

edit 's/val upInt = 1/val upInt = "one"/' "$ITEMS"
expect_output "jvm: upstream item starts to convert" "[loc1, loc2, up1, one]"

edit 's/Collector<String>/Collector<Any>/' "$DEFS"
expect_output "jvm: T of the Collector widened" "[loc1, loc2, 1, up1, 2, one]"

edit 's/Collector<Any>/Collector<Int>/' "$DEFS"
expect_output "jvm: T of the Collector changed" "[1, 2]"

restore_sources
expect_output "jvm: back to baseline" "[loc1, loc2, up1, up2]"
exit $FAILED
