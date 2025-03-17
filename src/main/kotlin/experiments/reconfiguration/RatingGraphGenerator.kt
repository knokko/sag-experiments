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
			val ratingGraph = File("$folder/rating-graph.bin")
			val numCores = run {
				val endIndex = folder.path.indexOf("cores")
				parseInt(folder.path.substring(endIndex - 1, endIndex))
			}
			val process = Runtime.getRuntime().exec(arrayOf(
				NP_TEST.absolutePath, File("$folder/jobs.csv").absolutePath,
				"-p", File("$folder/precedence.csv").absolutePath, "--reconfigure",
				"-m", numCores.toString(),
				"--reconfigure-save-rating-graph", ratingGraph.absolutePath,
				"--reconfigure-rating-timeout", "60"
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

			var succeeded = ratingGraph.isFile
			if (process.isAlive) {
				process.destroy()
				println("WARNING: hit hard timeout for $folder")
				succeeded = false
			}

			if (succeeded) {
				val exitCode = process.exitValue()
				println("Finished rating graph for $folder with exit code $exitCode")
				if (exitCode == 0 || exitCode == 2) {
					val outputFile = File("$folder/rating-graph.out")
					Files.write(outputFile.toPath(), processOutput)

					val errorsFile = File("$folder/rating-graph.err")
					if (processErrors.isNotEmpty()) {
						Files.write(errorsFile.toPath(), processErrors)
						println("Rating graph for $folder has errors")
					} else errorsFile.delete()
				} else folder.deleteRecursively()
			} else folder.deleteRecursively()
		}
	}
	reconfigurationPool.shutdown()
}
