package experiments

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Queue
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

private fun readNonBlockingLines(input: InputStream): List<String> {
	val bytes = mutableListOf<Byte>()
	while (input.available() > 0) {
		val nextByte = input.read()
		if (nextByte == -1) break
		bytes.add(nextByte.toByte())
	}

	return InputStreamReader(ByteArrayInputStream(bytes.toByteArray())).readLines()
}

private fun evaluate(jobSet: JobSet): JobSetEvaluationOutput {
	val process = Runtime.getRuntime().exec(arrayOf(
		NP_TEST.absolutePath, jobSet.jobFile.absolutePath, "-p", jobSet.precedenceFile.absolutePath,
		"-m", jobSet.numCores.toString(), "--reconfigure"
	))
	val startTime = System.nanoTime()
	val timedOut = !process.waitFor(5, TimeUnit.MINUTES)
	val spentTime = System.nanoTime() - startTime

	val output: List<String>
	val errors: List<String>
	val exitCode = if (timedOut) {
		output = readNonBlockingLines(process.inputStream)
		errors = readNonBlockingLines(process.errorStream)
		process.destroy()
		null
	} else {
		output = process.inputReader().readLines()
		errors = process.errorReader().readLines()
		process.exitValue()
	}

	return JobSetEvaluationOutput(jobSet, spentTime / 1_000_000L, exitCode, output, errors)
}
