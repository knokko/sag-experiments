package experiments

import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class EvaluationWorker(
	private val jobQueue: BlockingQueue<JobSet>,
	private val outputQueue: Queue<JobSetEvaluationOutput>
) {
	fun start(): Thread {
		val thread = Thread {
			while (true) {
				val jobSet = jobQueue.take()
				val evaluation = evaluate(jobSet)
				outputQueue.add(evaluation)
			}
		}
		thread.isDaemon = true
		thread.start()
		return thread
	}
}

private fun evaluate(jobSet: JobSet): JobSetEvaluationOutput {
	val process = Runtime.getRuntime().exec(arrayOf(
		NP_TEST.absolutePath, jobSet.jobFile.absolutePath, "-p", jobSet.precedenceFile.absolutePath, "--reconfigure"
	))
	val exitCode = if (process.waitFor(1, TimeUnit.MINUTES)) process.exitValue() else {
		process.destroy()
		null
	}
	val output = process.inputReader().readLines()
	val errors = process.errorReader().readLines()
	return JobSetEvaluationOutput(jobSet, exitCode, output, errors)
}
