package experiments.reconfiguration

import experiments.NP_TEST
import experiments.RECONFIGURATION_EXPERIMENTS_FOLDER
import java.io.File
import java.lang.Integer.parseInt
import java.lang.Thread.sleep
import java.nio.file.Files

fun main() {
	val problemFolders = mutableListOf<File>()
	for (outerFolder in RECONFIGURATION_EXPERIMENTS_FOLDER.listFiles()!!) {
		if (!outerFolder.isDirectory) continue
		for (innerFolder in outerFolder.listFiles()!!) {
			if (innerFolder.isDirectory) problemFolders.add(innerFolder)
		}
	}

	for (folder in problemFolders) {
		reconfigurationPool.submit {
			val ratingJobOrderingFile = File("$folder/rating-ordering.bin")
			val scratchJobOrderingFile = File("$folder/scratch-ordering.bin")
			val numCores = run {
				val endIndex = folder.path.indexOf("cores")
				parseInt(folder.path.substring(endIndex - 1, endIndex))
			}

			for (reconfigurationTest in ReconfigurationTest.ALL) {
				val jobOrderingFile = if (reconfigurationTest.needsRatingOrdering) ratingJobOrderingFile
				else scratchJobOrderingFile
				if (!jobOrderingFile.exists()) continue

				val baseArguments = arrayOf(
					NP_TEST.absolutePath, File("$folder/jobs.csv").absolutePath,
					"-p", File("$folder/precedence.csv").absolutePath, "-m", numCores.toString(),
					"--reconfigure", "--reconfigure-load-job-ordering", jobOrderingFile.absolutePath,
					"--reconfigure-enforce-timeout", "120", "--reconfigure-minimize-timeout", "120",
				)
				val process = Runtime.getRuntime().exec(baseArguments + reconfigurationTest.extraArguments)

				val startTime = System.nanoTime()
				val hardTimeOut = System.nanoTime() + 300_000_000_000L
				val processOutput = ArrayList<String>()
				val outputReader = process.inputReader()
				val processErrors = ArrayList<String>()
				val errorReader = process.errorReader()

				while (process.isAlive && System.nanoTime() < hardTimeOut) {
					sleep(100)
					while (outputReader.ready()) processOutput.add(outputReader.readLine())
					while (errorReader.ready()) processErrors.add(errorReader.readLine())
				}
				while (outputReader.ready()) processOutput.add(outputReader.readLine())
				while (errorReader.ready()) processErrors.add(errorReader.readLine())

				if (process.isAlive) {
					process.destroy()
					println("WARNING: hit hard from-scratch timeout for $folder")
				}

				println("Finished ${reconfigurationTest.name} for $folder? after ${(System.nanoTime() - startTime) / 1000_000_000L} seconds")
				val outputFile = File("$folder/results-${reconfigurationTest.name}.out")
				Files.write(outputFile.toPath(), processOutput)

				val errorsFile = File("$folder/results-${reconfigurationTest.name}.err")
				if (processErrors.isNotEmpty()) {
					Files.write(errorsFile.toPath(), processErrors)
					println("${reconfigurationTest.name} for $folder has errors")
				} else errorsFile.delete()
			}
		}
	}
	reconfigurationPool.shutdown()
}
