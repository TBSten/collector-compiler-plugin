package me.tbsten.collector.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollectFunctionTest {
    /** The placeholders declared in collector-runtime (Collect.kt, CollectByTag.kt, CollectWithAnnotation.kt). */
    @Test
    fun namesMatchTheRuntime() {
        assertEquals(
            setOf(
                "collectAsList", "collectAsSet", "collectAsSequence", "collectAsArray", "collectSingle", "collectSingleOrNull",
                "lazyCollectAsList", "lazyCollectAsSet", "lazyCollectAsSequence", "lazyCollectAsArray", "lazyCollectSingle", "lazyCollectSingleOrNull",
                "collectWithAnnotation", "collectAsMap", "lazyCollectWithAnnotation", "lazyCollectAsMap",
            ),
            CollectFunction.ALL.mapTo(mutableSetOf()) { it.name },
        )
    }

    @Test
    fun recognisesOnlyTopLevelFunctionsOfTheRuntimePackage() {
        val name = Name.identifier("lazyCollectAsSet")
        assertEquals(CollectFunction(CollectKind.AS_SET, lazy = true), CollectFunction.of(CallableId(CollectorNames.RUNTIME_PACKAGE, name)))
        assertNull(CollectFunction.of(CallableId(FqName("other"), name)))
        assertNull(CollectFunction.of(CallableId(ClassId(CollectorNames.RUNTIME_PACKAGE, Name.identifier("Some")), name)))
        assertNull(CollectFunction.of(CallableId(CollectorNames.RUNTIME_PACKAGE, Name.identifier("collectAll"))))
        assertNull(CollectFunction.of(null))
    }
}
