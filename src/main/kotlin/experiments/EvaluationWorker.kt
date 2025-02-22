package experiments

import java.io.InputStream
import java.util.Queue
import java.util.Scanner
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class EvaluationWorker(
	private val jobQueue: BlockingQueue<JobSet>,
	private val outputQueue: Queue<JobSetEvaluationOutput>
) {
	fun start(): Thread {
		val thread = Thread {
			try {
				while (true) {
					val jobSet = jobQueue.take()
					val evaluation = evaluate(jobSet)
					outputQueue.add(evaluation)
				}
			} catch (finished: InterruptedException) {
				// Ok, we are finished
			}
		}
		thread.isDaemon = true
		thread.start()
		return thread
	}
}

private fun readLines(input: InputStream): List<String> {
	val scanner = Scanner(input)
	val lines = mutableListOf<String>()
	while (scanner.hasNextLine()) lines.add(scanner.nextLine())
	return lines
}

private fun evaluate(jobSet: JobSet): JobSetEvaluationOutput {
	val process = Runtime.getRuntime().exec(arrayOf(
		NP_TEST.absolutePath, jobSet.jobFile.absolutePath, "-p", jobSet.precedenceFile.absolutePath,
		"-m", jobSet.numCores.toString(), "--reconfigure"
	))
	val startTime = System.nanoTime()
	val exitCode = if (process.waitFor(1, TimeUnit.MINUTES)) process.exitValue() else {
		process.destroy()
		null
	}
	val spentTime = System.nanoTime() - startTime
	val output = readLines(process.inputStream)
	val errors = readLines(process.errorStream)
	return JobSetEvaluationOutput(jobSet, spentTime / 1_000_000L, exitCode, output, errors)
}
