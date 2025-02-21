package experiments

import experiments.JobSet.Companion.collect
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit

class JobSetSource(private val config: YamlFile, private val amount: Int) {

	fun generate(jobQueue: Queue<JobSet>, errorQueue: Queue<JobSetGenerationError>) {
		val tempJobsFolder = Files.createTempDirectory("").toFile()
		config.prepareJobGeneration(tempJobsFolder, amount)

		val tempConfig = Files.createTempFile("", ".yaml").toFile()
		tempConfig.deleteOnExit()
		config.write(tempConfig)

		val process = Runtime.getRuntime().exec(arrayOf(GENERATOR.absolutePath, "-config", tempConfig.absolutePath))
		if (process.waitFor(10, TimeUnit.SECONDS)) {
			val exitCode = process.exitValue()
			if (exitCode == 0) {
				for (jobSet in collect(tempConfig, tempJobsFolder, amount)) jobQueue.add(jobSet)
			} else {
				// TODO report failure
			}
		} else {
			// TODO report timeout
		}
	}
}
