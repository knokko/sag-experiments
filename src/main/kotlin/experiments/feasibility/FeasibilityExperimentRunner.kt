package experiments.feasibility

import experiments.FEASIBILITY_RESULTS_FOLDER
import experiments.NP_TEST
import java.io.File
import java.lang.Integer.parseInt
import java.lang.Thread.sleep
import java.nio.file.Files

fun main(args: Array<String>) {
	val myGroup = parseInt(args[0])
	val numGroups = parseInt(args[1])

	val outerFolders = FEASIBILITY_RESULTS_FOLDER.listFiles()!!
	outerFolders.sortBy { it.absolutePath }

	val innerFolders = FEASIBILITY_RESULTS_FOLDER.listFiles()!!.flatMap {
		if (it.isDirectory) it.listFiles()!!.toList()
		else emptyList<File>()
	}.sortedBy { it.absolutePath }

	for (index in myGroup until innerFolders.size step numGroups) {
		val folder = innerFolders[index]

		val tests = mutableListOf(
			FeasibilityTest.HEURISTIC,
			FeasibilityTest.Z3_MODEL1,
			FeasibilityTest.Z3_MODEL2,
			FeasibilityTest.CPLEX,
		)

		val hasPrecedenceConstraints = !folder.path.contains("no-prec")
		if (!hasPrecedenceConstraints) tests.add(FeasibilityTest.MINISAT)

		val numCores = run {
			val startIndex = folder.path.indexOf("util_") + 5
			parseInt(folder.path.substring(startIndex, startIndex + 1))
		}

		println("index is $index and folder is $folder and #cores=$numCores and minisat? ${tests.contains(FeasibilityTest.MINISAT)}")
		for (test in tests) {
			var testArguments = arrayOf(NP_TEST.absolutePath, File("$folder/jobs.csv").absolutePath)
			if (hasPrecedenceConstraints) testArguments += arrayOf("-p", File("$folder/precedence.csv").absolutePath)
			if (numCores > 1) testArguments += arrayOf("-m", numCores.toString())
			testArguments += arrayOf("-l", "60")
			testArguments += test.arguments

			val processOutput = mutableListOf<String>()
			val processErrors = mutableListOf<String>()
			val process = Runtime.getRuntime().exec(testArguments)
			println("Running command ${testArguments.joinToString(separator = " ") { it }}")

			val startTime = System.nanoTime()
			val stopAt = startTime + 150_000_000_000L // Hard timeout after 150 seconds, just in case
			val inputReader = process.inputReader()
			val errorReader = process.errorReader()

			while (process.isAlive && System.nanoTime() < stopAt) {
				sleep(100)
				while (inputReader.ready()) processOutput.add(inputReader.readLine())
				while (errorReader.ready()) processErrors.add(errorReader.readLine())
			}
			if (process.isAlive) {
				process.destroy()
				processOutput.add("The process timed out after 150 seconds")
				processErrors.add("The process timed out after 150 seconds, which shouldn't happen")
			}

			val outputFile = File("$folder/results-${test.name}.log")
			Files.write(outputFile.toPath(), processOutput)
			println("Wrote results to ${outputFile.absolutePath}")

			val errorsFile = File("$folder/errors-${test.name}.log")
			if (processErrors.isNotEmpty()) {
				Files.write(errorsFile.toPath(), processErrors)
				println("Wrote errors to ${errorsFile.absolutePath}")
			} else errorsFile.delete()
		}
	}
}
