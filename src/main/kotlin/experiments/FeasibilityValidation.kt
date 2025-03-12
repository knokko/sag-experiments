package experiments

import generator.knokko.KnokkoGeneratorConfig
import generator.knokko.generate
import java.io.File
import java.nio.file.Files

fun main() {
	val numThreads = 1
	val config = KnokkoGeneratorConfig(
		numCores = 5, numJobs = 20, numPrecedenceConstraints = 0, desiredJobLengths = 4, lastDeadline = 15L,
		maxPriority = 5, minUtilization = 0.9, maxUtilization = 1.1
	)

	val threads = Array(numThreads) { Thread {
		while (true) {
			val jobsFile = Files.createTempFile("", ".csv").toFile()
			jobsFile.deleteOnExit()
			val precedenceFile = Files.createTempFile("", ".csv").toFile()
			precedenceFile.deleteOnExit()

			generate(config, jobsFile, precedenceFile)

			class FeasibilityProcess(
				val exact: Boolean,
				val name: String,
				val suffix: String,
				val extraArguments: Array<String> = emptyArray()
			) {
				lateinit var process: Process
				var certainlyFeasible: Boolean = false
				var certainlyInfeasible: Boolean = false

				fun start() {
					this.process = Runtime.getRuntime().exec(arrayOf(
						NP_TEST.absolutePath, jobsFile.absolutePath, "-p", precedenceFile.absolutePath,
						"-m", config.numCores.toString(), "--feasibility-$suffix"
					) + extraArguments)
				}

				fun parseResult() {
					val exitCode = process.waitFor()
					if (exitCode != 0) {
						println("$name test failed with exit code $exitCode; aborting! Standard output:")
						process.inputReader().forEachLine { println(" - $it") }
						println("Standard error:")
						process.errorReader().forEachLine { println(" - $it") }
						throw Error("$name failed: jobs are at $jobsFile")
					}

					val lines = process.inputReader().readLines()
					lines.forEach { line ->
						if (line.contains("is feasible")) certainlyFeasible = true
						if (line.contains("is infeasible")) certainlyInfeasible = true
					}

					if (exact && !certainlyFeasible && !certainlyInfeasible) {
						for (line in lines) println(line)
						throw RuntimeException("Exact $name test was inconclusive: jobs are $jobsFile and constraints are $precedenceFile")
					}

					if (certainlyFeasible && certainlyInfeasible) {
						for (line in lines) println(line)
						throw RuntimeException("$name test was conflicting")
					}
				}

				fun displayResult() = "$name: " + if (certainlyFeasible) "feasible"
						else if (certainlyInfeasible) "infeasible" else "inconclusive"
			}

			val processes = arrayOf(
				//FeasibilityProcess(false, "necessary", "necessary"),
				FeasibilityProcess(true, "cplex", "cplex"),
				FeasibilityProcess(true, "minisat", "minisat"),
			)
			for (process in processes) process.start()
			for (process in processes) process.parseResult()

			val isFeasible = processes.first { it.exact }.certainlyFeasible
			val isInfeasible = processes.first { it.exact }.certainlyInfeasible
			if ((isFeasible && processes.any { it.certainlyInfeasible }) || (isInfeasible && processes.any { it.certainlyFeasible })) throw Error(
				"Conflict detected with jobs $jobsFile and constraints $precedenceFile: ${processes.map { it.displayResult() }}"
			)

			if (!processes.any { it.certainlyInfeasible != isInfeasible }) println("all tests agree")
			else {
				println("--------------necessary test doesn't agree with exact tests-------------")

				val outputParentFolder = File("necessary-${config.numCores}-suboptimal")

				var id = System.currentTimeMillis()
				var outputFolder: File
				while (true) {
					outputFolder = File("$outputParentFolder/$id")
					if (outputFolder.mkdirs()) break
					if (!outputFolder.isDirectory) throw RuntimeException("Should not happen")
					id += 1
				}

				Files.copy(jobsFile.toPath(), File("$outputFolder/jobs.csv").toPath())
				Files.copy(precedenceFile.toPath(), File("$outputFolder/precedence.csv").toPath())
			}
		}
	}}
	for (thread in threads) {
		thread.isDaemon = true
		thread.start()
	}

	println("type anything to quit")
	readlnOrNull()
}
