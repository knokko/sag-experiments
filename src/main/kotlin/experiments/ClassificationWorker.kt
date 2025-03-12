package experiments

import java.io.File
import java.io.PrintWriter
import java.lang.Double.parseDouble
import java.lang.Integer.parseInt
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.BlockingQueue

class ClassificationWorker(private val evaluationQueue: BlockingQueue<JobSetEvaluationOutput>) {
	fun start(): Thread {
		val thread = Thread {
			try {
				while (true) {
					val evaluation = evaluationQueue.take()
					classify(evaluation)
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

@OptIn(ExperimentalStdlibApi::class)
private fun classify(evaluation: JobSetEvaluationOutput) {
	// Skip problems that are already schedulable
	if (evaluation.output.any { it.contains("is already schedulable") }) return

	val sha1 = MessageDigest.getInstance("SHA-1")
	for (file in arrayOf(evaluation.jobSet.jobFile, evaluation.jobSet.precedenceFile!!, evaluation.jobSet.configFile)) {
		sha1.update(Files.readAllBytes(file.toPath()))
	}

	val folderName = sha1.digest().toHexString()
	val folder = File("$RESULTS_FOLDER/$folderName")

	// Skip job sets we already processed
	if (folder.exists()) return

	val resultsFile = File("$folder/results.yaml")
	try {
		folder.mkdirs()

		Files.move(evaluation.jobSet.jobFile.toPath(), File("$folder/jobs.csv").toPath())
		Files.move(evaluation.jobSet.precedenceFile.toPath(), File("$folder/precedence.csv").toPath())
		Files.copy(evaluation.jobSet.configFile.toPath(), File("$folder/config.yaml").toPath())
		if (evaluation.output.isNotEmpty()) Files.write(File("$folder/out.log").toPath(), evaluation.output)
		if (evaluation.errors.isNotEmpty()) Files.write(File("$folder/err.log").toPath(), evaluation.errors)

		var rootRating: Double? = null
		var rootSafe: Boolean? = null
		var feasible: Boolean? = null
		var numExtraConstraints: Int? = null
		for (line in evaluation.output) {
			run {
				val rootPrefix = "seems to be unschedulable using our scheduler, and the rating of the root node is "
				val rootIndex = line.indexOf(rootPrefix)
				if (rootIndex != -1) {
					rootRating = parseDouble(line.substring(rootIndex + rootPrefix.length, line.length - 1))
					if (rootRating == 0.0) rootSafe = false
				}
			}
			if (line.contains("root node is unsafe")) rootSafe = false
			if (line.contains("given problem is infeasible")) feasible = false
			if (line.contains("found a safe job ordering")) feasible = true
			if (line.contains("Time to make cuts")) {
				feasible = true
				if (rootSafe == null) rootSafe = true
			}
			run {
				val endIndex = line.indexOf(" remain after trial & error")
				if (endIndex != -1) numExtraConstraints = parseInt(line.substring(0, endIndex))
			}
		}

		val resultWriter = PrintWriter(resultsFile)
		resultWriter.println("exit-code: ${evaluation.exitCode}")
		resultWriter.println("root-rating: ${rootRating ?: "unknown"}")
		resultWriter.println("root-safe: ${rootSafe ?: "unknown"}")
		resultWriter.println("feasible: ${feasible ?: "unknown"}")
		resultWriter.println("num-extra-constraints: ${numExtraConstraints ?: "unknown"}")
		resultWriter.println("spent-millis: ${evaluation.spentMilliseconds}")
		resultWriter.flush()
		resultWriter.close()
	} finally {
		if (!resultsFile.isFile || resultsFile.length() <= 0L) folder.deleteRecursively()
	}
}
