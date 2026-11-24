package me.tbsten.collector.gradle

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectorKotlinVersionGuardTest {
    @Test
    fun theVersionItIsBuiltWithIsSupported() {
        assertNull(CollectorKotlinVersionGuard.problem(BuildConfig.KOTLIN))
    }

    @Test
    fun otherVersionsAreRejectedWithAWayOut() {
        for (version in listOf("2.4.20", "2.4.30", "2.5.0", "2.3.21")) {
            val problem = assertNotNull(CollectorKotlinVersionGuard.problem(version), version)
            assertTrue(version in problem && CollectorKotlinVersionGuard.SKIP_PROPERTY in problem, problem)
        }
    }
}
