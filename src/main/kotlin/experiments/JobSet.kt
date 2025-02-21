package experiments

import java.io.File

class JobSet(val configFile: File, val jobFile: File, val precedenceFile: File) {

	companion object {
		fun collect(configFile: File, outputFolder: File, amount: Int): Collection<JobSet> {
			var jobsFolder = outputFolder
			fun reportInvalid(): Nothing {
				throw IllegalArgumentException("invalid jobs folder $outputFolder -> $jobsFolder")
			}

			while (true) {
				val children = jobsFolder.listFiles() ?: reportInvalid()
				if (children.size == 1) {
					jobsFolder = children[0]
					continue
				}

				if (children.size != 2) reportInvalid()
				jobsFolder = children.find { it.name == "jobsets" } ?: reportInvalid()
				break
			}

			val result = mutableListOf<JobSet>()
			val files = jobsFolder.listFiles() ?: reportInvalid()
			for (counter in 0 until amount) {
				val jobsFile = files.find { it.name.endsWith("_$counter.csv") } ?: reportInvalid()
				val precedenceFile = files.find { it.name.endsWith("_$counter.prec.csv") } ?: reportInvalid()
				result.add(JobSet(configFile, jobsFile, precedenceFile))
			}
			return result
		}
	}
}
