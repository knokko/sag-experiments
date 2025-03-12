package experiments

import generator.knokko.KnokkoGeneratorConfig
import generator.knokko.generate
import generator.pourya.YamlFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.Double.parseDouble
import java.lang.Long.parseLong
import java.lang.Thread.sleep

fun main() {
	// ac0bed9714f37a4331d2dd85608b7ab71f9dd0fb was reliable by using z3: 'only' 244 seconds
	// multithreaded analysis took 1 second, 21 seconds, 29 seconds, 34 seconds, and 129 seconds
	val commandReader = BufferedReader(InputStreamReader(System.`in`))
	val experiments = mutableListOf<Experiment>()
	while (true) {
		val userCommand = if (commandReader.ready()) commandReader.readLine() else null
		if (userCommand != null) {
			if (userCommand == "stop" || userCommand == "quit" || userCommand == "exit") break
			if (userCommand == "abort") {
				for (experiment in experiments) experiment.kill()
				experiments.clear()
				continue
			}

			if (userCommand.startsWith("feasibility")) {
				addFeasibilityExperiment(experiments, userCommand)
				continue
			}

			addDefaultExperiment(experiments, userCommand)
		}

		experiments.removeIf { it.update() }
		sleep(100)
	}

	for (experiment in experiments) experiment.kill()
}

private fun addFeasibilityExperiment(experiments: MutableCollection<Experiment>, userCommand: String) {
	var arguments = userCommand.split(" ")
	arguments = arguments.subList(1, arguments.size)

	var numCores = 1
	var numJobs = 1
	var numPrecedenceConstraints = 0
	var lastDeadline = 10_000L
	var maxPriority = 10
	var minUtilization = 1.0
	var maxUtilization = 1.0
	var numThreads = 1

	for (argument in arguments) {
		val pair = argument.split("=")
		if (pair.size != 2) {
			println("Unexpected argument $argument")
			return
		}

		val value: Long
		try {
			if (pair[0] == "minUtilization") {
				minUtilization = parseDouble(pair[1])
				continue
			}
			if (pair[0] == "maxUtilization") {
				maxUtilization = parseDouble(pair[1])
				continue
			}
			value = parseLong(pair[1])
		} catch (invalid: NumberFormatException) {
			println("Invalid argument $argument")
			return
		}

		when (pair[0]) {
			"#cores" -> numCores = value.toInt()
			"#jobs" -> numJobs = value.toInt()
			"#constraints" -> numPrecedenceConstraints = value.toInt()
			"deadline" -> lastDeadline = value
			"priority" -> maxPriority = value.toInt()
			"#threads" -> numThreads = value.toInt()
			else -> {
				println("Invalid argument $argument")
				return
			}
		}
	}

	if (minUtilization > maxUtilization) {
		println("Invalid utilization range")
		return
	}

	val config = KnokkoGeneratorConfig(
		numCores = numCores, numJobs = numJobs, numPrecedenceConstraints = numPrecedenceConstraints,
		desiredJobLengths = numCores * lastDeadline / numJobs, lastDeadline = lastDeadline,
		maxPriority = maxPriority, minUtilization = minUtilization, maxUtilization = maxUtilization
	)
	val jobsFile = File("jobs.csv")//Files.createTempFile("", ".csv")
	val precedenceFile = File("precedence.csv")
	generate(config, jobsFile, precedenceFile)

	for (solver in arrayOf("cplex", "minisat", "z31")) {
		val rawSolver = if (solver.contains("z3")) "z3" else solver
		val process = Runtime.getRuntime().exec(
			arrayOf(
				NP_TEST.absolutePath, jobsFile.absolutePath,
				"-p", precedenceFile.absolutePath,
				"-m", config.numCores.toString(),
				"--feasibility-threads", numThreads.toString(),
				"--feasibility-$rawSolver"
			) + (if (rawSolver == "z3") arrayOf(solver.substring(2)) else emptyArray<String>())
		)
		experiments.add(Experiment(process, "feasibility", solver))
	}
}

private fun addDefaultExperiment(experiments: MutableCollection<Experiment>, userCommand: String) {
	val arguments = userCommand.split(" ")
	if (arguments.size < 3) {
		println("Use <hash> <experiment name> <args...>")
		return
	}

	val hash = arguments[0]
	if (!File("$RESULTS_FOLDER/$hash").exists()) {
		println("Can't find problem with hash $hash")
		return
	}

	val numCores = YamlFile(File("$RESULTS_FOLDER/$hash/config.yaml")).get("number_of_cores")
	val experimentName = arguments[1]
	val process = Runtime.getRuntime().exec(
		arrayOf(
			NP_TEST.absolutePath, "$RESULTS_FOLDER/$hash/jobs.csv",
			"-p", "$RESULTS_FOLDER/$hash/precedence.csv",
			"-m", numCores,
			"--reconfigure"
		) + arguments.subList(2, arguments.size).toTypedArray()
	)
	experiments.add(Experiment(process, hash, experimentName))
}

private class Experiment(val process: Process, val hash: String, val name: String) {

	private val inputReader = process.inputReader()
	private val errorReader = process.errorReader()

	private fun print(message: String) {
		println("$hash-$name: $message")
	}

	fun update(): Boolean {
		while (inputReader.ready()) {
			print(inputReader.readLine())
		}
		while (errorReader.ready()) {
			print("err: ${errorReader.readLine()}")
		}

		if (!process.isAlive) {
			print("finished with exit code ${process.exitValue()}")
			inputReader.close()
			errorReader.close()
			return true
		}

		return false
	}

	fun kill() {
		update()

		if (process.isAlive) {
			process.destroy()
			print("aborted")
			inputReader.close()
			errorReader.close()
		}
	}
}
