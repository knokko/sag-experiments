package generator

import kotlinx.serialization.json.*
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import kotlin.math.roundToInt

class Runnable(
	val id: Int,
	val runningTime: Int,
	val rawInputs: Set<Int>,
	val rawOutputs: Set<Int>,
) {
	val dependencies = mutableListOf<Runnable>()
	val jobs = mutableListOf<RawJob>()

	override fun toString() = "Runnable(id=$id, wcet=$runningTime, #inputs=${rawInputs.size}, #output=${rawOutputs.size})"
}

class Task(
	val id: Int,
	val offset: Int,
	val period: Int,
) {
	val runnables = mutableListOf<Runnable>()

	override fun toString() = "Task(id=$id, offset=$offset, period=$period, #runnables=${runnables.size})"
}

class RawJob(val taskID: Int, val jobID: Int, val periodOffset: Int)

fun main() {
	val rawRunnableToFrame = Files.readString(File("/home/knokko/thesis/mercedes/baseline.json").toPath())
	val rawModel = Files.readString(File("/home/knokko/thesis/mercedes/model.json").toPath())
	val runnableToFrame = Json.parseToJsonElement(rawRunnableToFrame)
	val model = Json.parseToJsonElement(rawModel)

	val runnableToFrameMap = runnableToFrame.jsonObject["result"]!!.jsonArray.map { it.jsonPrimitive.int }
	val tasks = (model.jsonObject["Frames"]!!.jsonArray).mapIndexed { index, element ->
		val properties = element.jsonObject
		Task(
			id = index,
			offset = 100 * properties["Offset"]!!.jsonPrimitive.int,
			period = 100 * properties["Period"]!!.jsonPrimitive.int
		)
	}

	for ((index, element) in model.jsonObject["Runnables"]!!.jsonArray.withIndex()) {
		val properties = element.jsonObject
		tasks[runnableToFrameMap[index]].runnables.add(Runnable(
			id=index,
			runningTime = (100 * properties["Length"]!!.jsonPrimitive.double).roundToInt(),
			rawInputs = properties["ReadSignals"]!!.jsonArray.map { it.jsonPrimitive.int }.toSet(),
			rawOutputs = properties["WriteSignals"]!!.jsonArray.map { it.jsonPrimitive.int }.toSet(),
		))
	}

	for (destinationTask in tasks) {
		for (destinationRunnable in destinationTask.runnables) {
			for (sourceTask in tasks.filter { it.offset < destinationTask.offset && it.period == destinationTask.period }) {
				for (sourceRunnable in sourceTask.runnables) {
					if (sourceRunnable != destinationRunnable && sourceRunnable.rawOutputs.any {
						destinationRunnable.rawInputs.contains(it) }
						) {
						destinationRunnable.dependencies.add(sourceRunnable)
					}
				}
			}
		}
	}

	var hyperPeriod = 0
	while (true) {
		hyperPeriod += 100_000
		var shouldExit = true
		for (task in tasks) {
			if (hyperPeriod % task.period != 0) {
				shouldExit = false
				break
			}
		}
		if (shouldExit) break
	}

	val scale = 4.0

	val jobsWriter = PrintWriter("mercedes$scale.csv")
	jobsWriter.println("Task ID,Job ID,Arrival min,Arrival max,Cost min,Cost max,Deadline,Priority")
	for (task in tasks) {
		var jobOffset = 0
		for (offset in 0 until hyperPeriod step task.period) {
			jobOffset += 1000
			val taskArrival = offset + task.offset
			for (job in task.runnables) {
				if (Math.random() < 0.337) continue
				job.jobs.add(RawJob(task.id, job.id + jobOffset, offset))
				val execTime = (job.runningTime * scale).roundToInt()
				jobsWriter.println("${task.id}, ${job.id + jobOffset}, $taskArrival, $taskArrival, " +
						"${execTime * 29 / 30}, $execTime, ${offset + task.period}, ${task.period}")
			}
		}
	}
	jobsWriter.flush()
	jobsWriter.close()

	val precedenceWriter = PrintWriter("mercedes$scale.prec.csv")
	precedenceWriter.println("From TID,From JID,To TID,To JID")
	for (destinationTask in tasks) {
		for (destinationRunnable in destinationTask.runnables) {
			for (destinationJob in destinationRunnable.jobs) {
				for (sourceRunnable in destinationRunnable.dependencies) {
					val sourceJob = sourceRunnable.jobs.find { it.periodOffset == destinationJob.periodOffset } ?: continue
					precedenceWriter.println("${sourceJob.taskID}, ${sourceJob.jobID}, ${destinationJob.taskID}, ${destinationJob.jobID}")
				}
			}
		}
	}
	precedenceWriter.flush()
	precedenceWriter.close()
}
