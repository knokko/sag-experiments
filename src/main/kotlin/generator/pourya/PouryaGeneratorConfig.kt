package generator.pourya

import experiments.FEASIBILITY_RESULTS_FOLDER
import experiments.GENERATOR
import problem.Problem
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class PouryaGeneratorConfig(
	val baseConfig: File,
	val numCores: Int,
	val numTasks: Int,
	val desiredNumJobs: Int,
	val minNumJobs: Int,
	val maxNumJobs: Int,
	val addPrecedenceConstraints: Boolean,
	val desiredJobLengths: Long,
	val desiredLastDeadline: Long,
	val minLastDeadline: Long,
	val maxLastDeadline: Long,
	val utilization: Double,
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
		val outerFolder = File(
			"$FEASIBILITY_RESULTS_FOLDER/${desiredNumJobs}jobs_${niceUtilization}util_" +
					"${numCores}cores_${addPrecedenceConstraints}_${desiredJobLengths}durations"
		)

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

				if (problem.setSize(
					desiredNumJobs = desiredNumJobs, minNumJobs = minNumJobs, maxNumJobs = maxNumJobs,
					desiredLastDeadline = desiredLastDeadline, minLastDeadline = minLastDeadline, maxLastDeadline = maxLastDeadline
				)) {
					remaining -= 1
					val innerFolder = File("$outerFolder/case$remaining")
					innerFolder.mkdirs()
					problem.write(File("$innerFolder/jobs.csv"), File("$innerFolder/precedence.csv"))
				} else {
					maxJobIndex += 1
					println("not enough jobs with: ${maxJobs[maxJobIndex - 1]} max #jobs")
				}
			} else {
				maxJobIndex += 1
				println("timed out with: ${maxJobs[maxJobIndex - 1]} max #jobs")
				process.destroy()
			}

			if (maxJobIndex == maxJobs.size) {
				if (timeout == 1) {
					println("Increasing timeout...")
					maxJobIndex = 0
					timeout = 5
				} else {
					println("failed to generate job set for numJobs=$desiredNumJobs numCores=$numCores " +
							"precedence=$addPrecedenceConstraints jobLength=$desiredJobLengths utilization=$utilization")
					println("the config is at $tempConfig")
					break
				}
			}
			tempJobsFolder.deleteRecursively()
		}

		tempConfig.delete()
		tempJobsFolder.deleteRecursively()
	}
}
