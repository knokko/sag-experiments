package experiments.reconfiguration

import experiments.BORING_RECONFIGURATION_FOLDER
import experiments.FAILED_RECONFIGURATION_FOLDER
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
			val numCores = run {
				val endIndex = folder.path.indexOf("cores")
				parseInt(folder.path.substring(endIndex - 1, endIndex))
			}
			val process = Runtime.getRuntime().exec(arrayOf(
				NP_TEST.absolutePath, File("$folder/jobs.csv").absolutePath,
				"-p", File("$folder/precedence.csv").absolutePath, "--reconfigure",
				"-m", numCores.toString(),
				"--reconfigure-save-job-ordering", ratingJobOrderingFile.absolutePath,
				"--reconfigure-rating-timeout", "60", "--reconfigure-feasibility-graph-timeout", "10"
			))

			val hardTimeOut = System.nanoTime() + 100_000_000_000L
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

			var succeeded = true
			if (process.isAlive) {
				process.destroy()
				println("WARNING: hit hard rating timeout for $folder")
				succeeded = false
			}

			val logsFolder: File
			if (succeeded) {
				val exitCode = process.exitValue()
				// exitCode = 0 -> problem is schedulable or certainly infeasible
				// exitCode = 1 -> problem is visibly safe and job ordering was saved
				// exitCode = 2 -> problem is not visibly safe, so job ordering was not saved
				println("Finished rating job ordering for $folder with exit code $exitCode")
				if (exitCode == 1 || exitCode == 2) {
					logsFolder = folder
				} else {
					folder.deleteRecursively()
					logsFolder = File("$BORING_RECONFIGURATION_FOLDER/${folder.path}")
				}
			} else {
				folder.deleteRecursively()
				logsFolder = File("$FAILED_RECONFIGURATION_FOLDER/${folder.path}")
				println("Hit hard timeout for $logsFolder")
			}

			if (!logsFolder.isDirectory) logsFolder.mkdirs()
			val outputFile = File("$logsFolder/rating-job-ordering.out")
			Files.write(outputFile.toPath(), processOutput)

			val errorsFile = File("$logsFolder/rating-job-ordering.err")
			if (processErrors.isNotEmpty()) {
				Files.write(errorsFile.toPath(), processErrors)
				println("Rating job ordering for $logsFolder has errors")
			} else errorsFile.delete()
		}
	}
	reconfigurationPool.shutdown()
}
