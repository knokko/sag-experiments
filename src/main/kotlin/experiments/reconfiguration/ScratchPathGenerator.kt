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
			val scratchJobOrderingFile = File("$folder/scratch-ordering.bin")
			val numCores = run {
				val endIndex = folder.path.indexOf("cores")
				parseInt(folder.path.substring(endIndex - 1, endIndex))
			}
			val process = Runtime.getRuntime().exec(arrayOf(
				NP_TEST.absolutePath, File("$folder/jobs.csv").absolutePath,
				"-p", File("$folder/precedence.csv").absolutePath, "--feasibility-exact",
				"-m", numCores.toString(),
				"--feasibility-save-job-ordering", scratchJobOrderingFile.absolutePath,
				"-l", "300"
			))

			val hardTimeOut = System.nanoTime() + 350_000_000_000L
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

			var succeeded = scratchJobOrderingFile.isFile
			if (process.isAlive) {
				process.destroy()
				println("WARNING: hit hard from-scratch timeout for $folder")
				succeeded = false
			}

			println("Found from-scratch path for $folder? $succeeded")
			if (!succeeded) scratchJobOrderingFile.delete()
			val outputFile = File("$folder/scratch-job-ordering.out")
			Files.write(outputFile.toPath(), processOutput)

			val errorsFile = File("$folder/scratch-job-ordering.err")
			if (processErrors.isNotEmpty()) {
				Files.write(errorsFile.toPath(), processErrors)
				println("Scratch job ordering for $folder has errors")
			} else errorsFile.delete()
		}
	}
	reconfigurationPool.shutdown()
}
