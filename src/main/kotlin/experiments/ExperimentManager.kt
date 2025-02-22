package experiments

import java.io.File
import java.lang.IllegalArgumentException
import java.lang.Integer.parseInt
import java.lang.Thread.sleep
import java.util.concurrent.LinkedBlockingQueue

fun main() {
	ExperimentManager().run()
}

private class ExperimentManager {

	private val sourceQueue = LinkedBlockingQueue<JobSetSource>()
	private val jobQueue = LinkedBlockingQueue<JobSet>()
	private val generationErrorQueue = LinkedBlockingQueue<JobSetGenerationError>()
	private val evaluationQueue = LinkedBlockingQueue<JobSetEvaluationOutput>()

	private val generationThreads = mutableListOf<Thread>()
	private val evaluationThreads = mutableListOf<Thread>()

	fun run() {
		// TODO Multiple workers?
		generationThreads.add(GenerationWorker(sourceQueue, jobQueue, generationErrorQueue).start())
		evaluationThreads.add(EvaluationWorker(jobQueue, evaluationQueue).start())

		while (true) {
			val userInput = readlnOrNull() ?: break
			if (userInput == "stop" || userInput == "quit" || userInput == "exit") {
				stopGracefully()
				break
			} else if (userInput.startsWith("generate ")) {
				generate(userInput)
			} else if (userInput == "status") {
				printStatus()
			} else if (userInput == "error") {
				printNextError()
			} else {
				println("unknown command")
			}
		}
	}

	private fun stopGracefully() {
		println("Stopping gracefully... press Control + C to force")
		while (sourceQueue.isNotEmpty()) sleep(100)
		for (thread in generationThreads) thread.interrupt()
		for (thread in generationThreads) thread.join()

		while (true) {
			val nextError = generationErrorQueue.poll() ?: break
			println()
			nextError.print()
		}

		while (jobQueue.isNotEmpty()) sleep(100)
		for (thread in evaluationThreads) thread.interrupt()
		for (thread in evaluationThreads) thread.join()

		// TODO Classification threads
	}

	private fun generate(userInput: String) {
		val arguments = userInput.split(" ")
		if (arguments.size != 3) println("use: generate <config name> <amount>")

		val configFile = File("$GENERATOR_EXAMPLES/${arguments[1]}")
		if (!configFile.isFile) {
			println("Invalid/missing config file ${configFile.absolutePath}")
			return
		}

		val amount: Int
		try {
			amount = parseInt(arguments[2])
		} catch (invalidAmount: NumberFormatException) {
			println("Invalid <amount>: ${arguments[2]}")
			return
		}

		try {
			val yamlFile = YamlFile(configFile)
			sourceQueue.add(JobSetSource(yamlFile, amount))
		} catch (invalid: IllegalArgumentException) {
			println("Config file ${configFile.absolutePath} is invalid")
		}
	}

	private fun printStatus() {
		println(" - there are ${sourceQueue.size} entries in the source queue")
		println(" - there are ${generationErrorQueue.size} unread errors")
		println(" - there are ${jobQueue.size} entries in the job queue")
		println(" - there are ${evaluationQueue.size} entries in the evaluation queue")
	}

	private fun printNextError() {
		val error = generationErrorQueue.poll()
		if (error == null) println("There are no unread errors")
		else error.print()
	}
}
