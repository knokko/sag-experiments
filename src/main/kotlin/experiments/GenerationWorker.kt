package experiments

import java.util.Queue
import java.util.concurrent.BlockingQueue

class GenerationWorker(
	private val sourceQueue: BlockingQueue<JobSetSource>,
	private val jobQueue: Queue<JobSet>,
	private val errorQueue: Queue<JobSetGenerationError>,
) {
	fun start() {
		val thread = Thread {
			while (true) {
				val source = sourceQueue.take()
				source.generate(jobQueue, errorQueue)
			}
		}
		thread.isDaemon = true
		thread.start()
	}
}
