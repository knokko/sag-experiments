package experiments.reconfiguration

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.math.mean
import org.jetbrains.kotlinx.dataframe.math.median
import org.jetbrains.kotlinx.kandy.dsl.internal.dataframe.GroupByPlotBuilder
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.x
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

//	// Compare PathType and CutMethod success counts
//	run {
//		val sharedResults = results.filter { it.graphPath != null }
//
//		plot {
//			countPlot(sharedResults.flatMap { problem ->
//				val output = mutableListOf<String>()
//				for (pathType in PathType.entries) {
//					for (cutMethod in CutMethod.entries) {
//						if (problem.trials.any { it.pathType == pathType && it.cutMethod == cutMethod }) {
//							output.add("$pathType $cutMethod")
//						}
//					}
//				}
//				output
//			})
//		}.save("path-type and cut-method finish.png")
//	}
//
//	// Compare PathType and MinimizationMethod success counts
//	run {
//		val sharedResults = results.filter { it.graphPath != null }
//
//		plot {
//			countPlot(sharedResults.flatMap { problem ->
//				val output = mutableListOf<String>()
//				for (pathType in PathType.entries) {
//					for (method in MinimizationMethod.entries) {
//						if (problem.trials.any { it.pathType == pathType && it.minimizationMethod == method}) {
//							output.add("$pathType $method")
//						}
//					}
//				}
//				output
//			})
//		}.save("path-type and minimization-method finish.png")
//	}
//
//	// Compare PathType and CutMethod execution time/#constraints
//	run {
//		val sharedResults = results.filter { problem ->
//			PathType.entries.all { pathType -> CutMethod.entries.all { cutMethod ->
//				problem.trials.any { it.pathType == pathType && it.cutMethod == cutMethod }
//			} }
//		}.toMutableList()
//		sharedResults.removeIf { problem -> problem.trials.map { it.executionTime }.median() < 3.0 }
//		sharedResults.sortBy { problem -> problem.trials.map { it.executionTime }.median() }
//
//		fun row(pathType: PathType, cutMethod: CutMethod) = sharedResults.map { problem ->
//			problem.trials.filter {
//				it.pathType == pathType && it.cutMethod == cutMethod
//			}.map { it.executionTime }.median()
//		}
//		val dataSet = dataFrameOf(
//			"time" to row(PathType.Rating, CutMethod.Traditional) + row(PathType.Rating, CutMethod.SlowSafe) +
//					row(PathType.Rating, CutMethod.FastSafe) + row(PathType.Rating, CutMethod.Instant) +
//					row(PathType.Scratch, CutMethod.Traditional) + row(PathType.Scratch, CutMethod.SlowSafe) +
//					row(PathType.Scratch, CutMethod.FastSafe) + row(PathType.Scratch, CutMethod.Instant),
//			"problem" to List(8 * sharedResults.size) { index -> index % sharedResults.size },
//			"job ordering" to List(4 * sharedResults.size) { "graph" } + List(4 * sharedResults.size) { "scratch" },
//			"method" to List(sharedResults.size) { "old" } + List(sharedResults.size) { "slow" } +
//					List(sharedResults.size) { "fast" } + List(sharedResults.size) { "instant" } +
//					List(sharedResults.size) { "old" } + List(sharedResults.size) { "slow" } +
//					List(sharedResults.size) { "fast" } + List(sharedResults.size) { "instant" }
//		)
//		dataSet.groupBy("method").plot {
//			line {
//				x("problem")
//				y("time")
//				color("method")
//				type("job ordering")
//			}
//			y.axis.min = 0
//			y.axis.max = 80
//		}.save("path-type and cut-method time.png")
//	}
//
//	run {
//		val sharedResults = results.filter { problem ->
//			PathType.entries.all { pathType -> CutMethod.entries.all { cutMethod ->
//				problem.trials.any { it.pathType == pathType && it.cutMethod == cutMethod }
//			} }
//		}.toMutableList()
//
//		println("${sharedResults.size} problems of the ${results.size} problems could be solved using every cut method")
//		sharedResults.sortBy { problem -> problem.trials.map { it.numExtraConstraints }.median() }
//
//		fun row(pathType: PathType, cutMethod: CutMethod) = sharedResults.map { problem ->
//			problem.trials.filter {
//				it.pathType == pathType && it.cutMethod == cutMethod
//			}.map { it.numExtraConstraints }.median()
//		}
//		val dataSet = dataFrameOf(
//			"#constraints" to row(PathType.Rating, CutMethod.Traditional) + row(PathType.Rating, CutMethod.SlowSafe) +
//					row(PathType.Rating, CutMethod.FastSafe) + row(PathType.Rating, CutMethod.Instant) +
//					row(PathType.Scratch, CutMethod.Traditional) + row(PathType.Scratch, CutMethod.SlowSafe) +
//					row(PathType.Scratch, CutMethod.FastSafe) + row(PathType.Scratch, CutMethod.Instant),
//			"problem" to List(8 * sharedResults.size) { index -> index % sharedResults.size },
//			"job ordering" to List(4 * sharedResults.size) { "graph" } + List(4 * sharedResults.size) { "scratch" },
//			"method" to List(sharedResults.size) { "old" } + List(sharedResults.size) { "slow" } +
//					List(sharedResults.size) { "fast" } + List(sharedResults.size) { "instant" } +
//					List(sharedResults.size) { "old" } + List(sharedResults.size) { "slow" } +
//					List(sharedResults.size) { "fast" } + List(sharedResults.size) { "instant" }
//		)
//		dataSet.groupBy("method").plot {
//			line {
//				x("problem")
//				y("#constraints")
//				color("method")
//				type("job ordering")
//			}
//			y.axis.min = 0
//			y.axis.max = 50
//		}.save("path-type and cut-method constraints.png")
//	}
//
//	// Compare PathType and Minimization execution time/#constraints
//	run {
//		val sharedResults = results.filter { problem ->
//			PathType.entries.all { pathType -> MinimizationMethod.entries.all { method ->
//				problem.trials.any { it.pathType == pathType && it.minimizationMethod == method }
//			} }
//		}.toMutableList()
//		sharedResults.removeIf { problem -> problem.trials.map { it.executionTime }.median() < 3.0 }
//		sharedResults.sortBy { problem -> problem.trials.map { it.executionTime }.median() }
//
//		fun row(pathType: PathType, method: MinimizationMethod) = sharedResults.map { problem ->
//			problem.trials.filter {
//				it.pathType == pathType && it.minimizationMethod == method
//			}.map { it.executionTime }.median()
//		}
//		val dataSet = dataFrameOf(
//			"time" to row(PathType.Rating, MinimizationMethod.Random) + row(PathType.Rating, MinimizationMethod.Tail) +
//					row(PathType.Rating, MinimizationMethod.Head) +
//					row(PathType.Scratch, MinimizationMethod.Random) + row(PathType.Scratch, MinimizationMethod.Tail) +
//					row(PathType.Scratch, MinimizationMethod.Head),
//			"problem" to List(6 * sharedResults.size) { index -> index % sharedResults.size },
//			"job ordering" to List(3 * sharedResults.size) { "graph" } + List(3 * sharedResults.size) { "scratch" },
//			"method" to List(sharedResults.size) { "random" } + List(sharedResults.size) { "tail" } +
//					List(sharedResults.size) { "head" } +
//					List(sharedResults.size) { "random" } + List(sharedResults.size) { "tail" } +
//					List(sharedResults.size) { "head" }
//		)
//		dataSet.groupBy("method").plot {
//			line {
//				x("problem")
//				y("time")
//				color("method")
//				type("job ordering")
//			}
//			y.axis.min = 0
//		}.save("path-type and minimization-method time.png")
//	}
//
//	run {
//		val sharedResults = results.filter { problem ->
//			PathType.entries.all { pathType -> MinimizationMethod.entries.all { method ->
//				problem.trials.any { it.pathType == pathType && it.minimizationMethod == method }
//			} }
//		}.toMutableList()
//		sharedResults.removeIf { problem ->
//			problem.trials.map { it.numExtraConstraints }.median() < 50 ||
//					problem.trials.map { it.numExtraConstraints }.median() > 250
//		}
//		sharedResults.sortBy { problem -> problem.trials.map { it.numExtraConstraints }.median() }
//
//		fun row(pathType: PathType, method: MinimizationMethod) = sharedResults.map { problem ->
//			problem.trials.filter {
//				it.pathType == pathType && it.minimizationMethod == method
//			}.map { it.numExtraConstraints }.median()
//		}
//		val dataSet = dataFrameOf(
//			"#constraints" to row(PathType.Rating, MinimizationMethod.Random) + row(PathType.Rating, MinimizationMethod.Tail) +
//					row(PathType.Rating, MinimizationMethod.Head) +
//					row(PathType.Scratch, MinimizationMethod.Random) + row(PathType.Scratch, MinimizationMethod.Tail) +
//					row(PathType.Scratch, MinimizationMethod.Head),
//			"problem" to List(6 * sharedResults.size) { index -> index % sharedResults.size },
//			"job ordering" to List(3 * sharedResults.size) { "graph" } + List(3 * sharedResults.size) { "scratch" },
//			"method" to List(sharedResults.size) { "random" } + List(sharedResults.size) { "tail" } +
//					List(sharedResults.size) { "head" } +
//					List(sharedResults.size) { "random" } + List(sharedResults.size) { "tail" } +
//					List(sharedResults.size) { "head" }
//		)
//		dataSet.groupBy("method").plot {
//			line {
//				x("problem")
//				y("#constraints")
//				color("method")
//				type("job ordering")
//			}
//			y.axis.min = 40
//			y.axis.max = 200
//		}.save("path-type and minimization-method constraints.png")
//	}

	run {
		class Combination(
			val pathType: PathType,
			val cutMethod: CutMethod,
			val minMethod: MinimizationMethod,
			val displayName: String
		)

		fun constraintsPlot(
			combinations: List<Combination>, fileName: String,
			sortAndFilter: (MutableList<ReconfigurationResult>) -> Unit,
			plotBlock: GroupByPlotBuilder<Any?, Any?>.() -> Unit
		) {
			val sharedResults = results.filter { problem -> combinations.all { combination ->
				problem.trials.any { it.pathType == combination.pathType && it.cutMethod == combination.cutMethod && it.minimizationMethod == combination.minMethod }
			} }.toMutableList()
			sortAndFilter(sharedResults)
			sharedResults.sortBy { problem ->
				val relevantTrials = problem.trials.filter { trial -> combinations.any {
					it.pathType == trial.pathType && it.cutMethod == trial.cutMethod && it.minMethod == trial.minimizationMethod
				} }
				relevantTrials.map { it.numExtraConstraints }.median()
			}

			fun row(combination: Combination) = sharedResults.map { problem ->
				val numConstraints = problem.trials.filter {
					it.pathType == combination.pathType &&
							it.minimizationMethod == combination.minMethod &&
							it.cutMethod == combination.cutMethod
				}.map { it.numExtraConstraints }
				if (numConstraints.size != 1) {
					throw Error("uh ooh")
				}
				numConstraints[0]
			}
			val dataSet = dataFrameOf(
				"#constraints" to combinations.flatMap { row(it) },
				"problem" to List(combinations.size * sharedResults.size) { index -> index % sharedResults.size },
				"job ordering" to combinations.flatMap { combination -> List(sharedResults.size) { combination.pathType.toString() } },
				"method" to combinations.flatMap { combination -> List(sharedResults.size) { combination.displayName} }
			)
			dataSet.groupBy("method").plot {
				line {
					x("problem")
					y("#constraints")
					color("method")
					type("job ordering")
				}
				apply(plotBlock)
			}.save(fileName)
		}

		constraintsPlot(listOf(
//			Combination(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Random, "graph fast"),
			Combination(PathType.Rating, CutMethod.Instant, MinimizationMethod.Random, "graph"),
//			Combination(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Random, "scratch fast"),
			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random, "scratch")
		), "path-type instant constraints.png", { sharedResults ->
			sharedResults.sortBy { it.trials.map { it.numExtraConstraints }.median() }
		}, {
			y.axis.min = 0
			y.axis.max = 100
		})

		fun timePlot(
			combinations: List<Combination>, fileName: String,
			sortAndFilter: (MutableList<ReconfigurationResult>) -> Unit,
			plotBlock: GroupByPlotBuilder<Any?, Any?>.() -> Unit
		) {
			val sharedResults = results.filter { problem -> combinations.all { combination ->
				problem.trials.any { it.pathType == combination.pathType && it.cutMethod == combination.cutMethod && it.minimizationMethod == combination.minMethod }
			} }.toMutableList()

			sortAndFilter(sharedResults)
			sharedResults.sortBy { problem ->
				val relevantTrials = problem.trials.filter { trial -> combinations.any {
					it.pathType == trial.pathType && it.cutMethod == trial.cutMethod && it.minMethod == trial.minimizationMethod
				} }
				relevantTrials.map { it.executionTime }.median()
			}

			fun row(combination: Combination) = sharedResults.map { problem ->
				val numConstraints = problem.trials.filter {
					it.pathType == combination.pathType &&
							it.minimizationMethod == combination.minMethod &&
							it.cutMethod == combination.cutMethod
				}.map { it.executionTime }
				if (numConstraints.size != 1) {
					throw Error("uh ooh")
				}
				numConstraints[0]
			}
			val dataSet = dataFrameOf(
				"time" to combinations.flatMap { row(it) },
				"problem" to List(combinations.size * sharedResults.size) { index -> index % sharedResults.size },
				"job ordering" to combinations.flatMap { combination -> List(sharedResults.size) { combination.pathType.toString() } },
				"method" to combinations.flatMap { combination -> List(sharedResults.size) { combination.displayName} }
			)
			dataSet.groupBy("method").plot {
				line {
					x("problem")
					y("time")
					color("method")
					type("job ordering")
				}
				apply(plotBlock)
			}.save(fileName)
		}

		run {
			val combinations = listOf(
				Combination(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Random, "fast random"),
				Combination(PathType.Rating, CutMethod.Instant, MinimizationMethod.Random, "instant random"),
				Combination(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Random, "fast random"),
				Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random, "instant random"),
				Combination(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Tail, "fast tail"),
				Combination(PathType.Rating, CutMethod.Instant, MinimizationMethod.Tail, "instant tail"),
				Combination(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Tail, "fast tail"),
				Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Tail, "instant tail")
			)
			timePlot(combinations, "path-type and cut-method and minimization-method time short.png", { sharedResults ->
				sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
			}, {
				x.axis.min = 0
				y.axis.min = 0
				x.axis.max = 150
				y.axis.max = 2
			})
			timePlot(combinations, "path-type and cut-method and minimization-method time medium.png", { sharedResults ->
				sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
			}, {
				x.axis.min = 150
				y.axis.min = 0
				x.axis.max = 270
				y.axis.max = 60
			})
			timePlot(combinations, "path-type and cut-method and minimization-method time long.png", { sharedResults ->
				sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
			}, {
				x.axis.min = 270
				y.axis.min = 60
				x.axis.max = 300
				y.axis.max = 240
			})

			constraintsPlot(combinations, "path-type and cut-method and minimization-method constraints short.png", {}, {
				x.axis.max = 280
				y.axis.min = 1
				y.axis.max = 100
			})
			constraintsPlot(combinations, "path-type and cut-method and minimization-method constraints long.png", {}, {
				x.axis.min = 280
				y.axis.min = 1
			})
		}

//		timePlot(listOf(
//			Combination(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Random, "fast random"),
//			Combination(PathType.Rating, CutMethod.Instant, MinimizationMethod.Random, "instant random"),
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random, "instant random"),
//			Combination(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Tail, "fast tail"),
//			Combination(PathType.Rating, CutMethod.Instant, MinimizationMethod.Tail, "instant tail"),
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Tail, "instant tail")
//		), "path-type and cut-method and minimization-method time 3.png", { sharedResults ->
//			sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
//		}, {
//			x.axis.min = 200
//			y.axis.min = 0
//			x.axis.max = 270
//			y.axis.max = 50
//		})
//
//		timePlot(listOf(
//			Combination(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Random, "graph fast random"),
//			Combination(PathType.Rating, CutMethod.Instant, MinimizationMethod.Random, "graph instant random"),
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random, "scratch instant random"),
//		), "path-type and cut-method random time.png", { sharedResults ->
//			sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
//		}, {
//			x.axis.min = 200
//			y.axis.min = 0
//			x.axis.max = 270
//			y.axis.max = 50
//		})
//
//		timePlot(listOf(
//			Combination(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Tail, "graph fast tail"),
//			Combination(PathType.Rating, CutMethod.Instant, MinimizationMethod.Tail, "graph instant tail"),
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Tail, "scratch instant tail")
//		), "path-type and cut-method tail time.png", { sharedResults ->
//			sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
//		}, {
//			x.axis.min = 200
//			y.axis.min = 0
//			x.axis.max = 270
//			y.axis.max = 50
//		})
//
//		timePlot(listOf(
//			Combination(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Random, "graph fast random"),
//			Combination(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Tail, "graph fast tail"),
//		), "rating fast minimization method time.png", { sharedResults ->
//			sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
//		}, {
//			x.axis.min = 200
//			y.axis.min = 0
//			x.axis.max = 270
//			y.axis.max = 80
//		})
//
//		timePlot(listOf(
//			Combination(PathType.Rating, CutMethod.Instant, MinimizationMethod.Random, "graph instant random"),
//			Combination(PathType.Rating, CutMethod.Instant, MinimizationMethod.Tail, "graph instant tail"),
//		), "rating instant minimization method time.png", { sharedResults ->
//			sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
//		}, {
//			x.axis.min = 200
//			y.axis.min = 0
//			x.axis.max = 270
//			y.axis.max = 80
//		})
//
//		timePlot(listOf(
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random, "scratch instant random"),
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Tail, "scratch instant tail"),
//		), "scratch instant minimization method time.png", { sharedResults ->
//			sharedResults.removeIf { it.graphPath == null }
//			sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
//		}, {
//			x.axis.min = 200
//			y.axis.min = 0
//			x.axis.max = 270
//			y.axis.max = 80
//		})
//
//		timePlot(listOf(
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random, "scratch instant random"),
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Tail, "scratch instant tail"),
//			Combination(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Random, "scratch fast random"),
//			Combination(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Tail, "scratch fast tail"),
//		), "scratch all cut-method minimization method time.png", { sharedResults ->
//			sharedResults.sortBy { it.trials.map { it.executionTime }.median() }
//		}, {
//			x.axis.min = 300
//			y.axis.min = 0
//			//x.axis.max = 600
//			y.axis.max = 150
//		})
//
//		constraintsPlot(listOf(
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random, "scratch instant random"),
//			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Tail, "scratch instant tail"),
//			Combination(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Random, "scratch fast random"),
//			Combination(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Tail, "scratch fast tail"),
//		), "scratch all cut-method minimization method constraints small.png", { sharedResults ->
//		}, {
//			y.axis.min = 1
//			y.axis.max = 200
//		})
		constraintsPlot(listOf(
			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random, "scratch instant random"),
			Combination(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Tail, "scratch instant tail"),
			Combination(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Random, "scratch fast random"),
			Combination(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Tail, "scratch fast tail"),
		), "scratch all cut-method minimization method constraints large.png", { sharedResults ->
		}, {
			x.axis.min = 640
			y.axis.min = 1
			x.axis.max = 700
		})
	}

	// Compare PathType, CutMethod, and MinimizationMethod at the same time
//	run {
//		val cutMethods = arrayOf(CutMethod.FastSafe, CutMethod.Instant)
//		val minimizationMethods = arrayOf(MinimizationMethod.Random, MinimizationMethod.Tail)
//		val sharedResults = results.filter { problem ->
//			PathType.entries.all { pathType -> cutMethods.all { method ->
//				minimizationMethods.all { minimizationMethod ->
//					problem.trials.any { it.pathType == pathType && it.cutMethod == method && it.minimizationMethod == minimizationMethod }
//				}
//			} }
//		}.toMutableList()
//		sharedResults.removeIf { problem ->
//			problem.trials.map { it.numExtraConstraints }.median() < 50 ||
//					problem.trials.map { it.numExtraConstraints }.median() > 250
//		}
//		sharedResults.sortBy { problem -> problem.trials.map { it.numExtraConstraints }.median() }
//
//		fun row(pathType: PathType, cutMethod: CutMethod, method: MinimizationMethod) = sharedResults.map { problem ->
//			val numConstraints = problem.trials.filter {
//				it.pathType == pathType && it.minimizationMethod == method && it.cutMethod == cutMethod
//			}.map { it.numExtraConstraints }
//			if (numConstraints.size != 1) {
//				throw Error("uh ooh")
//			}
//			numConstraints[0]
//		}
//		val dataSet = dataFrameOf(
//			"#constraints" to row(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Random) +
//					row(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Tail) +
//					row(PathType.Rating, CutMethod.Instant, MinimizationMethod.Random) +
//					row(PathType.Rating, CutMethod.Instant, MinimizationMethod.Tail) +
//					row(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Random) +
//					row(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Tail) +
//					row(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random) +
//					row(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Tail),
//			"problem" to List(8 * sharedResults.size) { index -> index % sharedResults.size },
//			"job ordering" to List(4 * sharedResults.size) { "graph" } + List(4 * sharedResults.size) { "scratch" },
//			"method" to List(sharedResults.size) { "fast random" } + List(sharedResults.size) { "fast tail" } +
//					List(sharedResults.size) { "instant random" } + List(sharedResults.size) { "instant tail" } +
//					List(sharedResults.size) { "fast random" } + List(sharedResults.size) { "fast tail" } +
//					List(sharedResults.size) { "instant random" } + List(sharedResults.size) { "instant tail" }
//		)
//		dataSet.groupBy("method").plot {
//			line {
//				x("problem")
//				y("#constraints")
//				color("method")
//				type("job ordering")
//			}
//			y.axis.min = 40
//			y.axis.max = 200
//		}.save("path-type and cut-method and minimization-method constraints.png")
//	}
//
//	// Compare PathType, CutMethod, and MinimizationMethod at the same time
//	run {
//		val cutMethods = arrayOf(CutMethod.FastSafe, CutMethod.Instant)
//		val minimizationMethods = arrayOf(MinimizationMethod.Random, MinimizationMethod.Tail)
//		val sharedResults = results.filter { problem ->
//			PathType.entries.all { pathType -> cutMethods.all { method ->
//				minimizationMethods.all { minimizationMethod ->
//					problem.trials.any { it.pathType == pathType && it.cutMethod == method && it.minimizationMethod == minimizationMethod }
//				}
//			} }
//		}.toMutableList()
//		sharedResults.removeIf { problem ->
//			problem.trials.map { it.executionTime }.median() < 3.0
//		}
//		sharedResults.sortBy { problem -> problem.trials.map { it.executionTime }.median() }
//
//		fun row(pathType: PathType, cutMethod: CutMethod, method: MinimizationMethod) = sharedResults.map { problem ->
//			val numConstraints = problem.trials.filter {
//				it.pathType == pathType && it.minimizationMethod == method && it.cutMethod == cutMethod
//			}.map { it.executionTime }
//			if (numConstraints.size != 1) {
//				throw Error("uh ooh")
//			}
//			numConstraints[0]
//		}
//		val dataSet = dataFrameOf(
//			"time" to row(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Random) +
//					row(PathType.Rating, CutMethod.FastSafe, MinimizationMethod.Tail) +
//					row(PathType.Rating, CutMethod.Instant, MinimizationMethod.Random) +
//					row(PathType.Rating, CutMethod.Instant, MinimizationMethod.Tail) +
//					row(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Random) +
//					row(PathType.Scratch, CutMethod.FastSafe, MinimizationMethod.Tail) +
//					row(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Random) +
//					row(PathType.Scratch, CutMethod.Instant, MinimizationMethod.Tail),
//			"problem" to List(8 * sharedResults.size) { index -> index % sharedResults.size },
//			"job ordering" to List(4 * sharedResults.size) { "graph" } + List(4 * sharedResults.size) { "scratch" },
//			"method" to List(sharedResults.size) { "fast random" } + List(sharedResults.size) { "fast tail" } +
//					List(sharedResults.size) { "instant random" } + List(sharedResults.size) { "instant tail" } +
//					List(sharedResults.size) { "fast random" } + List(sharedResults.size) { "fast tail" } +
//					List(sharedResults.size) { "instant random" } + List(sharedResults.size) { "instant tail" }
//		)
//		dataSet.groupBy("method").plot {
//			line {
//				x("problem")
//				y("time")
//				color("method")
//				type("job ordering")
//			}
//			y.axis.min = 0
//		}.save("path-type and cut-method and minimization-method time.png")
//	}
}
