package experiments

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
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

			val arguments = userCommand.split(" ")
			if (arguments.size < 3) {
				println("Use <hash> <experiment name> <args...>")
				continue
			}

			val hash = arguments[0]
			if (!File("$RESULTS_FOLDER/$hash").exists()) {
				println("Can't find problem with hash $hash")
				continue
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

		experiments.removeIf { it.update() }
		sleep(100)
	}

	for (experiment in experiments) experiment.kill()
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
