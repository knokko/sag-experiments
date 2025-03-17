package generator.pourya

import experiments.FEASIBILITY_RESULTS_FOLDER
import experiments.GENERATOR
import experiments.RECONFIGURATION_EXPERIMENTS_FOLDER
import problem.Problem
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class PouryaGeneratorConfig(
	val baseConfig: File,
	val numCores: Int,
	val numTasks: Int,
	val desiredNumJobs: Int,
	val minNumJobs: Int,
	val maxNumJobs: Int,
	val addPrecedenceConstraints: Boolean,
	val desiredJobLengths: Long?,
	val desiredLastDeadline: Long?,
	val minLastDeadline: Long,
	val maxLastDeadline: Long,
	val utilization: Double,
	val jitter: Double = 0.05,
) {

	fun generateFeasibilityProblems(amount: Int) {
		val config = YamlFile(baseConfig)
		val tempJobsFolder = Files.createTempDirectory("").toFile()
		val tempConfig = Files.createTempFile("", ".yaml").toFile()
		val extra = mutableMapOf(
			Pair("tasks", numTasks.toString()),
			Pair("number_of_cores", numCores.toString()),
			Pair("generate_dags", addPrecedenceConstraints.toString()),
			Pair("utilization", (numCores * utilization).toString()),
		)
		config.prepareJobGeneration(tempJobsFolder, 1, extra)
		config.write(tempConfig)
		tempConfig.deleteOnExit()

		val maxJobs = arrayOf(3000, 1000, 10_000, 300, 30_000)
		var maxJobIndex = 0
		var timeout = 1

		val niceUtilization = (100 * utilization).roundToInt()
		val precedenceDescription = if (addPrecedenceConstraints) "prec" else "no-prec"
		val outerFolder = File(
			"$FEASIBILITY_RESULTS_FOLDER/${desiredNumJobs}jobs_${niceUtilization}util_" +
					"${numCores}cores_${precedenceDescription}_${desiredJobLengths}durations"
		)

		val candidateProblems = mutableListOf<Problem?>()

		var remaining = amount
		while (remaining > 0) {
			extra["max_jobs"] = maxJobs[maxJobIndex].toString()
			config.prepareJobGeneration(tempJobsFolder, 1, extra)
			config.write(tempConfig)

			val process = Runtime.getRuntime().exec(arrayOf(GENERATOR.absolutePath, "-config", tempConfig.absolutePath))
			if (process.waitFor(3, TimeUnit.SECONDS)) {
				val jobSets = collect(tempConfig, tempJobsFolder, 1, numCores, addPrecedenceConstraints)
				if (jobSets.size != 1) throw Error("What? $jobSets")
				val jobSet = jobSets.iterator().next()
				val problem = Problem(jobSet.jobFile, jobSet.precedenceFile, jobSet.numCores)

				if (problem.jobs.size >= minNumJobs) {
					candidateProblems.add(problem)
					if (problem.jobs.size <= maxNumJobs || candidateProblems.size >= 5) {
						val bestProblem = candidateProblems.filterNotNull().minBy {
							abs(it.jobs.size - desiredNumJobs)
						}
						println("desired #jobs is $desiredNumJobs and best #jobs is ${bestProblem.jobs.size} and last #jobs is ${problem.jobs.size}")
						if (!bestProblem.setSize(
							desiredDuration = desiredJobLengths, desiredNumJobs = desiredNumJobs,
							minNumJobs = minNumJobs, maxNumJobs = maxNumJobs,
							desiredLastDeadline = desiredLastDeadline, minLastDeadline = minLastDeadline,
							maxLastDeadline = maxLastDeadline
						)) {
							throw Error()
						}
						remaining -= 1
						val innerFolder = File("$outerFolder/case$remaining")
						innerFolder.mkdirs()
						bestProblem.write(File("$innerFolder/jobs.csv"), File("$innerFolder/precedence.csv"))
						candidateProblems.clear()
					} else candidateProblems.add(null)
				} else {
					if (candidateProblems.isEmpty()) maxJobIndex += 1
					else candidateProblems.add(null)
				}
			} else {
				if (candidateProblems.isEmpty()) maxJobIndex += 1
				else candidateProblems.add(null)
				process.destroy()
			}

			if (maxJobIndex == maxJobs.size) {
				if (timeout == 1) {
					println("Increasing timeout for $outerFolder")
					maxJobIndex = 0
					timeout = 5
				} else {
					println("failed to generate job set for $outerFolder with config $tempConfig")
					return
				}
			}
			tempJobsFolder.deleteRecursively()
		}

		tempConfig.delete()
		tempJobsFolder.deleteRecursively()
		println("generated job set for $outerFolder")
	}

	fun generateReconfigurationProblems(amount: Int) {
		val config = YamlFile(baseConfig)
		val tempJobsFolder = Files.createTempDirectory("").toFile()
		val tempConfig = Files.createTempFile("", ".yaml").toFile()
		val extra = mutableMapOf(
			Pair("tasks", numTasks.toString()),
			Pair("number_of_cores", numCores.toString()),
			Pair("generate_dags", addPrecedenceConstraints.toString()),
			Pair("utilization", (numCores * utilization).toString()),
			Pair("jitter", jitter.toString()),
			Pair("exec_variation", jitter.toString()),
		)
		config.prepareJobGeneration(tempJobsFolder, 1, extra)
		config.write(tempConfig)
		tempConfig.deleteOnExit()

		val maxJobs = arrayOf(max(maxNumJobs, 3000), max(maxNumJobs, 1000), 10_000, max(maxNumJobs, 300), 30_000)
		var maxJobIndex = 0
		var timeout = 1

		val niceUtilization = (100 * utilization).roundToInt()
		val niceJitter = (100 * jitter).roundToInt()
		val precedenceDescription = if (addPrecedenceConstraints) "prec" else "no-prec"
		val outerFolder = File(
			"$RECONFIGURATION_EXPERIMENTS_FOLDER/${desiredNumJobs}jobs_${niceUtilization}util_" +
					"${numCores}cores_${precedenceDescription}_${numTasks}tasks_${niceJitter}jitter"
		)

		val candidateProblems = mutableListOf<Problem?>()

		var remaining = amount
		while (remaining > 0) {
			extra["max_jobs"] = maxJobs[maxJobIndex].toString()
			config.prepareJobGeneration(tempJobsFolder, 1, extra)
			config.write(tempConfig)

			val process = Runtime.getRuntime().exec(arrayOf(GENERATOR.absolutePath, "-config", tempConfig.absolutePath))
			if (process.waitFor(3, TimeUnit.SECONDS)) {
				val jobSets = collect(tempConfig, tempJobsFolder, 1, numCores, addPrecedenceConstraints)
				if (jobSets.size != 1) throw Error("What? $jobSets")
				val jobSet = jobSets.iterator().next()
				val problem = Problem(jobSet.jobFile, jobSet.precedenceFile, jobSet.numCores)

				if (problem.jobs.size >= minNumJobs) {
					candidateProblems.add(problem)
					if (problem.jobs.size <= maxNumJobs || candidateProblems.size >= 5) {
						val bestProblem = candidateProblems.filterNotNull().minBy {
							abs(it.jobs.size - desiredNumJobs)
						}
						println("desired #jobs is $desiredNumJobs and best #jobs is ${bestProblem.jobs.size} and last #jobs is ${problem.jobs.size}")
						if (!bestProblem.setSize(
								desiredDuration = desiredJobLengths, desiredNumJobs = desiredNumJobs,
								minNumJobs = minNumJobs, maxNumJobs = maxNumJobs,
								desiredLastDeadline = desiredLastDeadline, minLastDeadline = minLastDeadline,
								maxLastDeadline = maxLastDeadline
							)) {
							throw Error()
						}
						remaining -= 1
						val innerFolder = File("$outerFolder/case$remaining")
						innerFolder.mkdirs()
						bestProblem.write(File("$innerFolder/jobs.csv"), File("$innerFolder/precedence.csv"))
						candidateProblems.clear()
					} else candidateProblems.add(null)
				} else {
					if (candidateProblems.isEmpty()) maxJobIndex += 1
					else candidateProblems.add(null)
				}
			} else {
				if (candidateProblems.isEmpty()) maxJobIndex += 1
				else candidateProblems.add(null)
				process.destroy()
			}

			if (maxJobIndex == maxJobs.size) {
				if (timeout == 1) {
					println("Increasing timeout for $outerFolder")
					maxJobIndex = 0
					timeout = 5
				} else {
					println("failed to generate job set for $outerFolder with config $tempConfig")
					return
				}
			}
			tempJobsFolder.deleteRecursively()
		}

		tempConfig.delete()
		tempJobsFolder.deleteRecursively()
		println("generated job set for $outerFolder")
	}
}
