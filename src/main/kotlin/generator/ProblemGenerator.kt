package generator

import java.io.File
import java.io.PrintWriter
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.random.nextLong

fun generate(config: GeneratorConfig, jobsDestination: File, precedenceDestination: File) {
	class RawJob(var startTime: Long, var endTime: Long)

	class Core(val lastDeadline: Long) {

		val splits = mutableSetOf<Long>()

		fun numJobs() = splits.size + 1

		fun canSplit() = numJobs() < lastDeadline

		fun split(rng: Random) {
			if (!canSplit()) throw IllegalStateException()

			while (true) {
				val next = rng.nextLong(1 until lastDeadline)
				if (splits.add(next)) break
			}
		}

		fun put(destination: MutableList<RawJob>) {
			var startTime = 0L
			for (split in splits.sorted()) {
				destination.add(RawJob(startTime = startTime, endTime = split))
				startTime = split
			}
			destination.add(RawJob(startTime = startTime, endTime = lastDeadline))
		}
	}

	val rng = Random.Default
	val cores = Array(config.numCores) { Core(config.lastDeadline) }
	while (cores.sumOf { it.numJobs() } < config.numJobs) {
		val candidates = cores.filter { it.canSplit() }
		if (candidates.isEmpty()) throw IllegalArgumentException(
			"Impossible config: ${config.numJobs} jobs on ${config.numCores} with last deadline at ${config.lastDeadline}"
		)
		candidates.random(rng).split(rng)
	}

	val jobs = ArrayList<RawJob>(config.numJobs)
	for (core in cores) core.put(jobs)

	jobs.shuffle(rng)

	run {
		val writer = PrintWriter(jobsDestination)
		writer.println("Task ID,Job ID,Arrival min,Arrival max,Cost min,Cost max,Deadline,Priority")

		for ((index, job) in jobs.withIndex()) {
			val arrival = rng.nextLong(0 .. job.startTime)
			var executionTime = job.endTime - job.startTime

			val currentUtilization = if (config.minUtilization == config.maxUtilization) config.maxUtilization
			else rng.nextDouble(config.minUtilization, config.maxUtilization)
			executionTime = (executionTime * currentUtilization).roundToLong()
			executionTime = max(executionTime, 1)

			val deadline = rng.nextLong(job.endTime .. config.lastDeadline)
			val priority = rng.nextInt(config.maxPriority + 1)
			writer.println("$index,1,$arrival,$arrival,$executionTime,$executionTime,$deadline,$priority")
		}
		writer.flush()
		writer.close()
	}

	run {
		val writer = PrintWriter(precedenceDestination)
		writer.println("From TID,From JID,To TID,To JID")
		for (counter in 0 until config.numPrecedenceConstraints) {
			while (true) {
				val index1 = rng.nextInt(jobs.size)
				val index2 = rng.nextInt(jobs.size)
				if (jobs[index1].endTime <= jobs[index2].startTime) {
					writer.println("$index1,1,$index2,1")
					break
				}
			}
		}
		writer.flush()
		writer.close()
	}
}
