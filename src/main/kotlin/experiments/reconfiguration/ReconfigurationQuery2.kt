package experiments.reconfiguration

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.math.median
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
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

	fun pathData(condition: (ReconfigurationTrialResult) -> Boolean): DataFrame<*> {
		val sharedResults = results.filter { problem ->
			PathType.entries.all { path -> problem.trials.any { it.pathType == path && condition(it) } }
		}.sortedBy { problem ->
			problem.trials.filter(condition).map { it.executionTime }.median()
		}

		val paths = mutableListOf<String>()
		val cutMethods = mutableListOf<String>()
		val problems = mutableListOf<Int>()
		val ratings = mutableListOf<Double>()
		val numConstraints = mutableListOf<Int>()
		val time = mutableListOf<Double>()
		for ((index, problem) in sharedResults.withIndex()) {
			for (trial in problem.trials.filter(condition).shuffled()) {
				paths.add(trial.pathType.toString())
				problems.add(index)
				ratings.add(problem.rootRating)
				cutMethods.add(trial.cutMethod.toString())
				numConstraints.add(trial.numExtraConstraints)
				time.add(trial.executionTime)
			}
		}

		return dataFrameOf(
			"job ordering" to paths,
			"problem" to problems,
			"rating" to ratings,
			"cut method" to cutMethods,
			"#constraints" to numConstraints,
			"time" to time
		)
	}

	fun cutData(candidates: List<CutMethod> = CutMethod.entries, condition: (ReconfigurationTrialResult) -> Boolean): DataFrame<*> {
		val sharedResults = results.filter { problem ->
			candidates.all { method -> problem.trials.any { it.cutMethod == method && condition(it) } }
		}.sortedBy { problem ->
			problem.trials.filter(condition).filter { candidates.contains(it.cutMethod) }.map { it.executionTime }.median()
		}

		val methods = mutableListOf<String>()
		val paths = mutableListOf<String>()
		val problems = mutableListOf<Int>()
		val numConstraints = mutableListOf<Int>()
		val time = mutableListOf<Double>()
		for ((index, problem) in sharedResults.withIndex()) {
			for (trial in problem.trials.filter(condition).filter { candidates.contains(it.cutMethod) }.shuffled()) {
				methods.add(trial.cutMethod.toString())
				paths.add(trial.pathType.toString())
				problems.add(index)
				numConstraints.add(trial.numExtraConstraints)
				time.add(trial.executionTime)
			}
		}

		return dataFrameOf(
			"method" to methods,
			"job ordering" to paths,
			"problem" to problems,
			"#constraints" to numConstraints,
			"time" to time
		)
	}

	fun minimizationData(candidates: List<MinimizationMethod> = MinimizationMethod.entries, condition: (ReconfigurationTrialResult) -> Boolean): DataFrame<*> {
		val sharedResults = results.filter { problem ->
			candidates.all { method -> problem.trials.any { it.minimizationMethod == method && condition(it) } }
		}.sortedBy { problem ->
			problem.trials.filter(condition).filter { candidates.contains(it.minimizationMethod) }.map { it.executionTime }.median()
		}

		val methods = mutableListOf<String>()
		val paths = mutableListOf<String>()
		val problems = mutableListOf<Int>()
		val numConstraints = mutableListOf<Int>()
		val time = mutableListOf<Double>()
		for ((index, problem) in sharedResults.withIndex()) {
			for (trial in problem.trials.filter(condition).filter { candidates.contains(it.minimizationMethod) }.shuffled()) {
				methods.add(trial.minimizationMethod.toString())
				paths.add(trial.pathType.toString())
				problems.add(index)
				numConstraints.add(trial.numExtraConstraints)
				time.add(trial.executionTime)
			}
		}

		return dataFrameOf(
			"method" to methods,
			"job ordering" to paths,
			"problem" to problems,
			"#constraints" to numConstraints,
			"time" to time
		)
	}

//	pathData { true }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("job ordering")
//			size = 2.5
//		}
//	}.save("v2/path-type constraints.png")
//
//	cutData { true }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/cut-method constraints.png")
//
//	minimizationData { true }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/minimization-method constraints.png")
//
//	pathData { true }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("job ordering")
//			size = 2.5
//		}
//	}.save("v2/path-type time.png")
//
//	cutData { true }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/cut-method time.png")
//
//	minimizationData { true }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/minimization-method time.png")
//
//	cutData(listOf(CutMethod.FastSafe, CutMethod.Instant)) { true }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/cut-method final time.png")
//
//	minimizationData { it.cutMethod == CutMethod.Instant }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/minimization-method instant time.png")
//
//	pathData { it.cutMethod == CutMethod.Instant }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("job ordering")
//			size = 2.5
//		}
//	}.save("v2/path-type instant time.png")
//
//	minimizationData(listOf(MinimizationMethod.Random, MinimizationMethod.Tail)) { it.cutMethod == CutMethod.Instant }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/minimization-method instant final time.png")
//
//	pathData { it.cutMethod == CutMethod.Instant && it.minimizationMethod == MinimizationMethod.Random }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("job ordering")
//			size = 2.5
//		}
//	}.save("v2/path-type instant random time.png")

	pathData { true }.plot {
		points {
			x("rating")
			y("#constraints")
			color("cut method")
			symbol("job ordering")
			size = 2.5
			y.axis.min = 1
			y.axis.max = 50
		}
	}.save("v2/path-type rating vs constraints.png")

	pathData { true }.plot {
		points {
			x("rating")
			y("time")
			color("cut method")
			symbol("job ordering")
			size = 2.5
			y.axis.min = 0
		}
	}.save("v2/path-type rating vs time.png")
}
