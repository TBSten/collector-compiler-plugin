package edge.jvm

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor

// JavaLabel is a Java source of this module (src/jvmMain/java): no class file of it exists while Kotlin is compiled
@CollectorFor<JavaLabel>()
object JavaLabelCollector : Collector<String>

@JavaLabel("same package")
val labeledInItsPackage = "same package"
