// Hints as another version of the Collector plugin would have written them, for scripts/error-check.sh.
// The Collector plugin is deliberately not applied here (see the root build), so these hand-written hints are kept as is.
kotlin { sourceSets.commonMain.dependencies { implementation("me.tbsten.collector:collector-runtime:0.1.0-SNAPSHOT") } }
