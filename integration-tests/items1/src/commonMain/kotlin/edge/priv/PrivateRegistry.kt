package edge.priv

import me.tbsten.collector.Collector
import me.tbsten.collector.CollectorFor
import me.tbsten.collector.collectAsList
import me.tbsten.collector.collectWithAnnotation

// A registry that stays inside its file: the annotations, the type of the items and the Collectors are all private.
// What the plugin generates for them is in files of its own, which can name none of them.
private annotation class Step(val order: Int)

private annotation class Extra

private interface Task {
    val name: String
}

@CollectorFor<Step>("private tasks") private object Tasks : Collector<Task>

@CollectorFor<Step> private object TaskFactories : Collector<() -> Task>

// two private annotations on one Collector
@CollectorFor<Step> @CollectorFor<Extra> private object StepNames : Collector<String>

@Step(1) private object First : Task { override val name = "first" }

@Step(2) private class Second : Task { override val name = "second" }

@Step(3) private fun third(): Task = First

@Step(4) @Extra val stepName = "step"

@Extra internal val extraName = "extra"

fun privateTasks(): List<String> = Tasks.collectAsList().map { it.name }

fun privateTasksByOrder(): List<String> =
    Tasks.collectWithAnnotation<Step, Task>().map { (step, task) -> "${step.order} ${task.name}" }

fun privateTaskFactories(): List<String> = TaskFactories.collectAsList().map { it().name }

fun privateStepNames(): List<String> = StepNames.collectAsList()
