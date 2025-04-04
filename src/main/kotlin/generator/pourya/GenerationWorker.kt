package generator.pourya

import experiments.*
import java.io.File
import java.lang.Boolean.parseBoolean
import java.lang.Integer.parseInt
import java.nio.file.Files
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class GenerationWorker(
	private val sourceQueue: BlockingQueue<JobSetSource>,
	private val jobQueue: Queue<JobSet>,
	private val errorQueue: Queue<JobSetGenerationError>,
) {
	fun start(): Thread {
		val thread = Thread {
			try {
				while (true) {
					val source = sourceQueue.take()
					generate(source)
				}
			} catch (interrupted: InterruptedException) {
				// Ok, we are done
			}
		}
		thread.isDaemon = true
		thread.start()
		return thread
	}

	private fun generate(source: JobSetSource) {
		val tempJobsFolder = Files.createTempDirectory("").toFile()
		source.config.prepareJobGeneration(tempJobsFolder, source.amount, emptyMap())

		val tempConfig = Files.createTempFile("", ".yaml").toFile()
		tempConfig.deleteOnExit()
		source.config.write(tempConfig)

		val process = Runtime.getRuntime().exec(arrayOf(GENERATOR.absolutePath, "-config", tempConfig.absolutePath))
		if (process.waitFor(1, TimeUnit.MINUTES)) {
			val exitCode = process.exitValue()
			if (exitCode == 0) {
				for (jobSet in collect(
					tempConfig, tempJobsFolder, source.amount,
					parseInt(source.config.get("number_of_cores")), parseBoolean(source.config.get("generate_dags"))
				)) jobQueue.add(jobSet)
			} else {
				errorQueue.add(
					JobGenerationNonZero(
						exitCode,
						process.inputReader().readLines(),
						process.errorReader().readLines()
					)
				)
				tempConfig.delete()
				tempJobsFolder.deleteRecursively()
			}
		} else {
			process.destroy()
			errorQueue.add(JobGenerationTimeout)
			tempConfig.delete()
			tempJobsFolder.deleteRecursively()
		}
	}
}

fun collect(
	configFile: File, outputFolder: File, amount: Int,
	numCores: Int, hasPrecedenceConstraints: Boolean
): Collection<JobSet> {
	var jobsFolder = outputFolder
	fun reportInvalid(): Nothing {
		throw IllegalArgumentException("invalid jobs folder $outputFolder -> $jobsFolder")
	}

	while (true) {
		jobsFolder.deleteOnExit()
		val children = jobsFolder.listFiles() ?: reportInvalid()
		if (children.size == 1) {
			jobsFolder = children[0]
			continue
		}

		for (childFolder in children) if (childFolder.name != "jobsets") childFolder.deleteRecursively()
		jobsFolder = children.find { it.name == "jobsets" } ?: reportInvalid()
		jobsFolder.deleteOnExit()
		break
	}

	val result = mutableListOf<JobSet>()
	val files = jobsFolder.listFiles() ?: reportInvalid()
	for (counter in 0 until amount) {
		val jobsFile = files.find { it.name.endsWith("_$counter.csv") } ?: reportInvalid()
		jobsFile.deleteOnExit()
		val precedenceFile = if (hasPrecedenceConstraints) {
			files.find { it.name.endsWith("_$counter.prec.csv") } ?: reportInvalid()
		} else null
		precedenceFile?.deleteOnExit()
		result.add(JobSet(configFile, numCores, jobsFile, precedenceFile))
	}
	return result
}
