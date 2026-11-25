import edge.jvm.JavaLabel
import edge.jvm.JavaLabelCollector
import edge.jvm.JavaLabels
import me.tbsten.collector.collectAsList
import me.tbsten.collector.collectWithAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

@JavaLabels
val labeledThroughJava = "meta-annotation"

class JavaAnnotationJvmTest {
    /**
     * An annotation declared in a Java source of the module of the items (:items1), and a meta-annotation written in
     * Java in a dependency.
     */
    @Test
    fun annotationsDeclaredInJava() {
        // edge.jvm.labeledInItsPackage, edge.labeledByFullName, labeledThroughJava
        assertEquals(listOf("same package", "full name", "meta-annotation"), JavaLabelCollector.collectAsList())
        val labels = JavaLabelCollector.collectWithAnnotation<JavaLabel, String>().map { it.first.value }
        assertEquals(listOf("same package", "full name", "through JavaLabels"), labels)
    }
}
