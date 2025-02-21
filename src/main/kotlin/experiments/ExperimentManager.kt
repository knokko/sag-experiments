package experiments

import java.lang.Thread.sleep
import java.util.concurrent.LinkedBlockingQueue

fun main() {
	val sourceQueue = LinkedBlockingQueue<JobSetSource>()
	val jobQueue = LinkedBlockingQueue<JobSet>()
	val generationErrorQueue = LinkedBlockingQueue<JobSetGenerationError>()

	// TODO Multiple workers?
	GenerationWorker(sourceQueue, jobQueue, generationErrorQueue).start()

	while (true) {
		val userInput = readlnOrNull() ?: break
		if (userInput == "stop" || userInput == "quit" || userInput == "exit") {
			println("Stopping gracefully... press Control + C to force")
			while (sourceQueue.isNotEmpty()) sleep(100)
			while (true) {
				val nextError = generationErrorQueue.poll() ?: break
				println()
				println("A job generation error occurred:")
				nextError.print()
			}
			while (jobQueue.isNotEmpty()) sleep(100)
			break
		}
	}
}
