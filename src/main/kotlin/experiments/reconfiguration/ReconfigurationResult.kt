package experiments.reconfiguration

import java.io.File
import java.lang.Double.parseDouble
import java.lang.Integer.parseInt
import java.nio.file.Files
import java.util.*

class ReconfigurationResult(
	val folder: File,
	val config: ReconfigurationConfig,
	val rootRating: Double,
	val rootVisiblySafe: Boolean,
	val graphPath: ReconfigurationPathResult?,
	val scratchPath: ReconfigurationPathResult?,
) {
	val paths = arrayOf(graphPath, scratchPath).filterNotNull()
	val trials = paths.flatMap { it.trials }

	companion object {
		private fun addTrial(
			config: ReconfigurationConfig, files: Array<File>,
			target: MutableList<ReconfigurationTrialResult>,
			pathType: PathType, cutMethod: CutMethod, minimizationMethod: MinimizationMethod
		) {
			val fileName = "results-${pathType.name.lowercase(Locale.ROOT)} " +
					"${cutMethod.filePart} ${minimizationMethod.name.lowercase(Locale.ROOT)}.out"
			val file = files.find { it.name == fileName } ?: return

			var numExplorations = 0
			var numSchedulableExplorations = 0
			var executionTime: Double? = null
			var peakMemory: Int? = null
			var numExtraConstraints: Int? = null
			Files.lines(file.toPath()).forEach { line ->
				if (line.contains("intermediate exploration")) {
					numExplorations += 1
					if (line.contains("schedulable? 1")) numSchedulableExplorations += 1
				}
				if (line.contains("remain after trial & error")) {
					numExtraConstraints = parseInt(line.substring(0, line.indexOf(" remain")).trim())
				}
				if (line.startsWith("Reconfiguration took")) {
					executionTime = run {
						val startIndex = "Reconfiguration took ".length
						val endIndex = line.indexOf(" ", startIndex)
						parseDouble(line.substring(startIndex, endIndex))
					}
					peakMemory = run {
						val endIndex = line.indexOf("MB")
						val startIndex = 1 + line.lastIndexOf(" ", endIndex)
						parseInt(line.substring(startIndex, endIndex))
					}
				}
			}

			if (executionTime == null) return
			target.add(ReconfigurationTrialResult(
				executionTime = executionTime!!,
				numExtraConstraints = numExtraConstraints!!,
				peakMemory = peakMemory!!,
				numExplorations = numExplorations,
				numSchedulableExplorations = numSchedulableExplorations,
				config = config,
				pathType = pathType,
				cutMethod = cutMethod,
				minimizationMethod = minimizationMethod
			))
		}

		fun parse(folder: File): ReconfigurationResult {
			val config = ReconfigurationConfig.parse(folder)
			val files = folder.listFiles()!!

			val hasGraphPath = files.any { it.name == "rating-ordering.bin" }
			val hasScratchPath = files.any { it.name == "scratch-ordering.bin" }

			var rootRating: Double? = null
			var rootVisiblySafe = true
			files.find { it.name == "rating-job-ordering.out" }!!.forEachLine { line ->
				val prefix = "and the rating of the root node is "
				val ratingPrefix = line.indexOf(prefix)
				if (ratingPrefix != -1) {
					rootRating = parseDouble(line.substring(ratingPrefix + prefix.length, line.length - 1))
					rootVisiblySafe = rootRating!! > 0.0
				}
				if (line == "Feasibility graph search timed out") rootVisiblySafe = false
			}

			val trials = mutableListOf<ReconfigurationTrialResult>()
			for (pathType in PathType.entries) {
				for (cutMethod in CutMethod.entries) {
					for (minimization in MinimizationMethod.entries) {
						addTrial(config, files, trials, pathType, cutMethod, minimization)
					}
				}
			}
			val graphResult = if (hasGraphPath) {
				ReconfigurationPathResult(PathType.Rating, config, trials.filter { it.pathType == PathType.Rating })
			} else null
			val scratchResult = if (hasScratchPath) {
				ReconfigurationPathResult(PathType.Scratch, config, trials.filter { it.pathType == PathType.Scratch })
			} else null

			return ReconfigurationResult(folder, config, rootRating!!, rootVisiblySafe, graphResult, scratchResult)
		}
	}
}

class ReconfigurationPathResult(
	val type: PathType,
	val config: ReconfigurationConfig,
	val trials: List<ReconfigurationTrialResult>
)

class ReconfigurationTrialResult(
	/**
	 * In seconds
	 */
	val executionTime: Double,
	val numExtraConstraints: Int,
	/**
	 * In megabytes
	 */
	val peakMemory: Int,
	val numExplorations: Int,
	val numSchedulableExplorations: Int,

	val config: ReconfigurationConfig,
	val pathType: PathType,
	val cutMethod: CutMethod,
	val minimizationMethod: MinimizationMethod
)

enum class PathType(private val displayName: String) {
	Rating("graph"),
	Scratch("scratch");

	override fun toString() = displayName
}

enum class CutMethod(val filePart: String, private val displayName: String) {
	Traditional("traditional", "old"),
	SlowSafe("slow-safe", "slow"),
	FastSafe("fast-safe", "fast"),
	Instant("total", "instant");

	override fun toString() = displayName
}

enum class MinimizationMethod {
	Random,
	Tail,
	Head;

	override fun toString() = name.lowercase(Locale.ROOT)
}

class ReconfigurationConfig(
	val numJobs: Int,
	val numTasks: Int,
	val numCores: Int,
	val utilization: Int,
	val jitter: Int,
	val precedence: Boolean,
) {
	companion object {
		fun parse(folder: File): ReconfigurationConfig {
			val numJobs = run {
				val endIndex = folder.path.indexOf("jobs")
				val startIndex = folder.path.lastIndexOf("/", endIndex) + 1
				parseInt(folder.path.substring(startIndex, endIndex))
			}
			val utilization = run {
				val endIndex = folder.path.indexOf("util")
				val startIndex = folder.path.lastIndexOf("_", endIndex) + 1
				parseInt(folder.path.substring(startIndex, endIndex))
			}
			val jitter = run {
				val endIndex = folder.path.indexOf("jitter")
				val startIndex = folder.path.lastIndexOf("_", endIndex) + 1
				parseInt(folder.path.substring(startIndex, endIndex))
			}
			val numCores = run {
				val endIndex = folder.path.indexOf("cores")
				parseInt(folder.path.substring(endIndex - 1, endIndex))
			}
			val numTasks = run {
				val endIndex = folder.path.indexOf("tasks")
				parseInt(folder.path.substring(endIndex - 1, endIndex))
			}
			val precedence = !folder.path.contains("no-prec")
			return ReconfigurationConfig(
				numJobs = numJobs, numTasks = numTasks, numCores = numCores,
				utilization = utilization, jitter = jitter, precedence = precedence
			)
		}
	}
}
