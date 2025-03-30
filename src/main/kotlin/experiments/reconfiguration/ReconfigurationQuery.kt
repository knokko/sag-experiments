package experiments.reconfiguration

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.math.median
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.y
import org.jetbrains.kotlinx.statistics.kandy.layers.countPlot
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
	mainResults()
}

fun mainBoring() {
	val outerFolders = File("/home/knokko/thesis/boring-reconfiguration-experiments").listFiles()!!
	val boringResults = mutableListOf<BoringReconfigurationResult>()
	for (outerFolder in outerFolders) {
		for (innerFolder in outerFolder.listFiles()!!) {
			boringResults.add(BoringReconfigurationResult.parse(innerFolder))
		}
	}

	println("${boringResults.count { it == BoringReconfigurationResult.TimedOut }} time-outs & " +
			"${boringResults.count { it == BoringReconfigurationResult.CertainlySchedulable }} schedulable & " +
			"${boringResults.count { it == BoringReconfigurationResult.CertainlyInfeasible }} infeasible out of ${boringResults.size}")
}

fun mainResults() {
	val outerFolders = File("/home/knokko/thesis/reconfiguration-results").listFiles()!!
	val results = Collections.synchronizedList(ArrayList<ReconfigurationResult>())
	val threadPool = Executors.newFixedThreadPool(8)
	for (outerFolder in outerFolders) {
		if (!outerFolder.isDirectory) continue
		for (innerFolder in outerFolder.listFiles()!!) {
			if (!innerFolder.isDirectory) continue
			threadPool.submit {
				results.add(ReconfigurationResult.parse(innerFolder))
			}
		}
	}
	threadPool.shutdown()
	if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) throw Error("Timed out")

	println("${results.count { it.graphPath != null }} graph results and ${results.count { it.scratchPath != null }} scratch results out of ${results.size}")
	println("only solved by rating: ${results.count { it.graphPath != null && it.scratchPath == null }}")
	println("${results.count { it.rootRating > 0.0 }} positive root ratings, ${results.count { it.rootVisiblySafe }} roots visibly safe")

	// Compare PathType and CutMethod success counts
	run {
		val sharedResults = results.filter { it.graphPath != null }

		plot {
			countPlot(sharedResults.flatMap { problem ->
				val output = mutableListOf<String>()
				for (pathType in PathType.entries) {
					for (cutMethod in CutMethod.entries) {
						if (problem.trials.any { it.pathType == pathType && it.cutMethod == cutMethod }) {
							output.add("$pathType $cutMethod")
						}
					}
				}
				output
			})
		}.save("path-type and cut-method finish.png")
	}

	// Compare PathType and MinimizationMethod success counts
	run {
		val sharedResults = results.filter { it.graphPath != null }

		plot {
			countPlot(sharedResults.flatMap { problem ->
				val output = mutableListOf<String>()
				for (pathType in PathType.entries) {
					for (method in MinimizationMethod.entries) {
						if (problem.trials.any { it.pathType == pathType && it.minimizationMethod == method}) {
							output.add("$pathType $method")
						}
					}
				}
				output
			})
		}.save("path-type and minimization-method finish.png")
	}

	// Compare PathType and CutMethod execution time/#constraints
	run {
		val sharedResults = results.filter { problem ->
			PathType.entries.all { pathType -> CutMethod.entries.all { cutMethod ->
				problem.trials.any { it.pathType == pathType && it.cutMethod == cutMethod }
			} }
		}.toMutableList()
		sharedResults.removeIf { problem -> problem.trials.map { it.executionTime }.median() < 3.0 }
		sharedResults.sortBy { problem -> problem.trials.map { it.executionTime }.median() }

		fun row(pathType: PathType, cutMethod: CutMethod) = sharedResults.map { problem ->
			problem.trials.filter {
				it.pathType == pathType && it.cutMethod == cutMethod
			}.map { it.executionTime }.median()
		}
		val dataSet = dataFrameOf(
			"time" to row(PathType.Rating, CutMethod.Traditional) + row(PathType.Rating, CutMethod.SlowSafe) +
					row(PathType.Rating, CutMethod.FastSafe) + row(PathType.Rating, CutMethod.Instant) +
					row(PathType.Scratch, CutMethod.Traditional) + row(PathType.Scratch, CutMethod.SlowSafe) +
					row(PathType.Scratch, CutMethod.FastSafe) + row(PathType.Scratch, CutMethod.Instant),
			"problem" to List(8 * sharedResults.size) { index -> index % sharedResults.size },
			"job ordering" to List(4 * sharedResults.size) { "graph" } + List(4 * sharedResults.size) { "scratch" },
			"method" to List(sharedResults.size) { "old" } + List(sharedResults.size) { "slow" } +
					List(sharedResults.size) { "fast" } + List(sharedResults.size) { "instant" } +
					List(sharedResults.size) { "old" } + List(sharedResults.size) { "slow" } +
					List(sharedResults.size) { "fast" } + List(sharedResults.size) { "instant" }
		)
		dataSet.groupBy("method").plot {
			line {
				x("problem")
				y("time")
				color("method")
				type("job ordering")
			}
			y.axis.min = 0
			y.axis.max = 80
		}.save("path-type and cut-method time.png")
	}

	run {
		val sharedResults = results.filter { problem ->
			PathType.entries.all { pathType -> CutMethod.entries.all { cutMethod ->
				problem.trials.any { it.pathType == pathType && it.cutMethod == cutMethod }
			} }
		}.toMutableList()
		sharedResults.sortBy { problem -> problem.trials.map { it.numExtraConstraints }.median() }

		fun row(pathType: PathType, cutMethod: CutMethod) = sharedResults.map { problem ->
			problem.trials.filter {
				it.pathType == pathType && it.cutMethod == cutMethod
			}.map { it.numExtraConstraints }.median()
		}
		val dataSet = dataFrameOf(
			"#constraints" to row(PathType.Rating, CutMethod.Traditional) + row(PathType.Rating, CutMethod.SlowSafe) +
					row(PathType.Rating, CutMethod.FastSafe) + row(PathType.Rating, CutMethod.Instant) +
					row(PathType.Scratch, CutMethod.Traditional) + row(PathType.Scratch, CutMethod.SlowSafe) +
					row(PathType.Scratch, CutMethod.FastSafe) + row(PathType.Scratch, CutMethod.Instant),
			"problem" to List(8 * sharedResults.size) { index -> index % sharedResults.size },
			"job ordering" to List(4 * sharedResults.size) { "graph" } + List(4 * sharedResults.size) { "scratch" },
			"method" to List(sharedResults.size) { "old" } + List(sharedResults.size) { "slow" } +
					List(sharedResults.size) { "fast" } + List(sharedResults.size) { "instant" } +
					List(sharedResults.size) { "old" } + List(sharedResults.size) { "slow" } +
					List(sharedResults.size) { "fast" } + List(sharedResults.size) { "instant" }
		)
		dataSet.groupBy("method").plot {
			line {
				x("problem")
				y("#constraints")
				color("method")
				type("job ordering")
			}
			y.axis.min = 0
			y.axis.max = 50
		}.save("path-type and cut-method constraints.png")
	}

	// Compare PathType and Minimization execution time/#constraints
	run {
		val sharedResults = results.filter { problem ->
			PathType.entries.all { pathType -> MinimizationMethod.entries.all { method ->
				problem.trials.any { it.pathType == pathType && it.minimizationMethod == method }
			} }
		}.toMutableList()
		sharedResults.removeIf { problem -> problem.trials.map { it.executionTime }.median() < 3.0 }
		sharedResults.sortBy { problem -> problem.trials.map { it.executionTime }.median() }

		fun row(pathType: PathType, method: MinimizationMethod) = sharedResults.map { problem ->
			problem.trials.filter {
				it.pathType == pathType && it.minimizationMethod == method
			}.map { it.executionTime }.median()
		}
		val dataSet = dataFrameOf(
			"time" to row(PathType.Rating, MinimizationMethod.Random) + row(PathType.Rating, MinimizationMethod.Tail) +
					row(PathType.Rating, MinimizationMethod.Head) +
					row(PathType.Scratch, MinimizationMethod.Random) + row(PathType.Scratch, MinimizationMethod.Tail) +
					row(PathType.Scratch, MinimizationMethod.Head),
			"problem" to List(6 * sharedResults.size) { index -> index % sharedResults.size },
			"job ordering" to List(3 * sharedResults.size) { "graph" } + List(3 * sharedResults.size) { "scratch" },
			"method" to List(sharedResults.size) { "random" } + List(sharedResults.size) { "tail" } +
					List(sharedResults.size) { "head" } +
					List(sharedResults.size) { "random" } + List(sharedResults.size) { "tail" } +
					List(sharedResults.size) { "head" }
		)
		dataSet.groupBy("method").plot {
			line {
				x("problem")
				y("time")
				color("method")
				type("job ordering")
			}
			y.axis.min = 0
		}.save("path-type and minimization-method time.png")
	}

	run {
		val sharedResults = results.filter { problem ->
			PathType.entries.all { pathType -> MinimizationMethod.entries.all { method ->
				problem.trials.any { it.pathType == pathType && it.minimizationMethod == method }
			} }
		}.toMutableList()
		sharedResults.removeIf { problem ->
			problem.trials.map { it.numExtraConstraints }.median() < 50 ||
					problem.trials.map { it.numExtraConstraints }.median() > 250
		}
		sharedResults.sortBy { problem -> problem.trials.map { it.numExtraConstraints }.median() }

		fun row(pathType: PathType, method: MinimizationMethod) = sharedResults.map { problem ->
			problem.trials.filter {
				it.pathType == pathType && it.minimizationMethod == method
			}.map { it.numExtraConstraints }.median()
		}
		val dataSet = dataFrameOf(
			"#constraints" to row(PathType.Rating, MinimizationMethod.Random) + row(PathType.Rating, MinimizationMethod.Tail) +
					row(PathType.Rating, MinimizationMethod.Head) +
					row(PathType.Scratch, MinimizationMethod.Random) + row(PathType.Scratch, MinimizationMethod.Tail) +
					row(PathType.Scratch, MinimizationMethod.Head),
			"problem" to List(6 * sharedResults.size) { index -> index % sharedResults.size },
			"job ordering" to List(3 * sharedResults.size) { "graph" } + List(3 * sharedResults.size) { "scratch" },
			"method" to List(sharedResults.size) { "random" } + List(sharedResults.size) { "tail" } +
					List(sharedResults.size) { "head" } +
					List(sharedResults.size) { "random" } + List(sharedResults.size) { "tail" } +
					List(sharedResults.size) { "head" }
		)
		dataSet.groupBy("method").plot {
			line {
				x("problem")
				y("#constraints")
				color("method")
				type("job ordering")
			}
			y.axis.min = 40
			y.axis.max = 200
		}.save("path-type and minimization-method constraints.png")
	}
}
