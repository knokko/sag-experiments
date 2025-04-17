package experiments.reconfiguration

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.math.median
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.letsplot.y
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.statistics.kandy.layers.boxplot
import org.jetbrains.kotlinx.statistics.kandy.layers.countPlot
import org.jetbrains.kotlinx.statistics.kandy.statplots.boxplot
import org.jetbrains.kotlinx.statistics.stats.mean
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.log10

fun main() {
	val outerFolders = File("/home/knokko/thesis/reconfiguration-results").listFiles()!!
	val results = Collections.synchronizedList(ArrayList<ReconfigurationResult>())
	val threadPool = Executors.newFixedThreadPool(8)
	val futures = mutableListOf<Future<*>>()
	for (outerFolder in outerFolders) {
		if (!outerFolder.isDirectory) continue
		for (innerFolder in outerFolder.listFiles()!!) {
			if (!innerFolder.isDirectory) continue
			futures.add(threadPool.submit {
				results.add(ReconfigurationResult.parse(innerFolder))
			})
		}
	}
	threadPool.shutdown()
	if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) throw Error("Timed out")
	for (future in futures) future.get()

	println("${results.count { it.graphPath != null }} graph results and ${results.count { it.scratchPath != null }} scratch results out of ${results.size}")
	println("only solved by rating: ${results.count { it.graphPath != null && it.scratchPath == null }}")
	println("${results.count { it.rootRating > 0.0 }} positive root ratings, ${results.count { it.rootVisiblySafe }} roots visibly safe")

	fun pathData(sortByTime: Boolean, applyCandidateFilter: Boolean = true, condition: (ReconfigurationTrialResult) -> Boolean): DataFrame<*> {
		val sharedResults = results.filter { problem ->
			if (!applyCandidateFilter) problem.trials.isNotEmpty()
			else PathType.entries.all { path -> problem.trials.any { it.pathType == path && condition(it) } }
		}.sortedBy { problem ->
			problem.trials.filter(if (applyCandidateFilter) condition else { _ -> true }).map {
				if (sortByTime) it.executionTime else it.numExtraConstraints.toDouble()
			}.median()
		}

		val paths = mutableListOf<String>()
		val cutMethods = mutableListOf<String>()
		val problems = mutableListOf<Int>()
		val ratings = mutableListOf<Double>()
		val numConstraints = mutableListOf<Int>()
		val time = mutableListOf<Double>()
		val numJobs = mutableListOf<String>()
		for ((index, problem) in sharedResults.withIndex()) {
			for (trial in problem.trials.filter(condition).shuffled()) {
				paths.add(trial.pathType.toString())
				problems.add(index)
				ratings.add(problem.rootRating)
				cutMethods.add(trial.cutMethod.toString())
				numConstraints.add(trial.numExtraConstraints)
				time.add(trial.executionTime)
				numJobs.add(trial.config.numJobs.toString())
			}
		}

		return dataFrameOf(
			"job ordering" to paths,
			"problem" to problems,
			"rating" to ratings,
			"cut method" to cutMethods,
			"#constraints" to numConstraints,
			"time" to time,
			"#jobs" to numJobs
		)
	}

	fun cutData(sortByTime: Boolean?, candidates: List<CutMethod> = CutMethod.entries, applyCandidateFilter: Boolean = true, condition: (ReconfigurationTrialResult) -> Boolean): DataFrame<*> {
		val sharedResults = results.filter { problem ->
			if (!applyCandidateFilter) problem.trials.isNotEmpty()
			else candidates.all { method -> problem.trials.any { it.cutMethod == method && condition(it) } }
		}.sortedBy { problem ->
			problem.trials.filter(if (applyCandidateFilter) condition else { _ -> true }).filter { candidates.contains(it.cutMethod) }.map {
				if (sortByTime == true) it.executionTime
				else if (sortByTime == false) it.numExtraConstraints.toDouble()
				else it.originalExtraConstraints.toDouble()
			}.median()
		}

		val methods = mutableListOf<String>()
		val paths = mutableListOf<String>()
		val problems = mutableListOf<Int>()
		val numConstraints = mutableListOf<Int>()
		val numOriginalConstraints = mutableListOf<Int>()
		val time = mutableListOf<Double>()
		val numJobs = mutableListOf<String>()
		val numTasks = mutableListOf<String>()
		val utilization = mutableListOf<String>()
		val numCores = mutableListOf<String>()
		val jitter = mutableListOf<String>()
		val precedence = mutableListOf<Boolean>()
		for ((index, problem) in sharedResults.withIndex()) {
			for (trial in problem.trials.filter(condition).filter { candidates.contains(it.cutMethod) }.shuffled()) {
				methods.add(trial.cutMethod.toString())
				paths.add(trial.pathType.toString())
				problems.add(index)
				numConstraints.add(trial.numExtraConstraints)
				numOriginalConstraints.add(trial.originalExtraConstraints)
				time.add(trial.executionTime)
				numJobs.add(trial.config.numJobs.toString())
				numTasks.add(trial.config.numTasks.toString())
				utilization.add(trial.config.utilization.toString())
				numCores.add(trial.config.numCores.toString())
				jitter.add(trial.config.jitter.toString())
				precedence.add(trial.config.precedence)
			}
		}

		return dataFrameOf(
			"method" to methods,
			"job ordering" to paths,
			"problem" to problems,
			"#constraints" to numConstraints,
			"#peak constraints" to numOriginalConstraints,
			"time" to time,
			"#jobs" to numJobs,
			"#tasks" to numTasks,
			"utilization" to utilization,
			"#cores" to numCores,
			"jitter" to jitter,
			"precedence?" to precedence
		)
	}

	fun minimizationData(sortByTime: Boolean, candidates: List<MinimizationMethod> = MinimizationMethod.entries, applyCandidateFilter: Boolean = true, condition: (ReconfigurationTrialResult) -> Boolean): DataFrame<*> {
		val sharedResults = results.filter { problem ->
			if (!applyCandidateFilter) problem.trials.isNotEmpty()
			else candidates.all { method -> problem.trials.any { it.minimizationMethod == method && condition(it) } }
		}.sortedBy { problem ->
			problem.trials.filter(if (applyCandidateFilter) condition else { _ -> true }).filter { candidates.contains(it.minimizationMethod) }.map {
				if (sortByTime) it.executionTime else it.numExtraConstraints.toDouble()
			}.median()
		}

		val methods = mutableListOf<String>()
		val paths = mutableListOf<String>()
		val problems = mutableListOf<Int>()
		val numConstraints = mutableListOf<Int>()
		val time = mutableListOf<Double>()
		val numJobs = mutableListOf<String>()
		for ((index, problem) in sharedResults.withIndex()) {
			for (trial in problem.trials.filter(condition).filter { candidates.contains(it.minimizationMethod) }.shuffled()) {
				methods.add(trial.minimizationMethod.toString())
				paths.add(trial.pathType.toString())
				problems.add(index)
				numConstraints.add(trial.numExtraConstraints)
				time.add(trial.executionTime)
				numJobs.add(trial.config.numJobs.toString())
			}
		}

		return dataFrameOf(
			"method" to methods,
			"job ordering" to paths,
			"problem" to problems,
			"#constraints" to numConstraints,
			"time" to time,
			"#jobs" to numJobs
		)
	}

//	pathData(false) { true }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("job ordering")
//			size = 2.5
//		}
//	}.save("v2/path-type constraints.png")
//
//	cutData(false) { true }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/cut-method constraints.png")
//	cutData(null) { true }.plot {
//		points {
//			x("problem")
//			y("#peak constraints")
//			color("method")
//			size = 2.5
//			y.axis.min = 1
//		}
//	}.save("v2/cut-method peak constraints.png")
//	cutData(null) { true }.plot {
//		points {
//			x("problem")
//			y("#peak constraints")
//			color("method")
//			size = 2.5
//			y.axis.min = 1
//			y.axis.max = 1000
//		}
//	}.save("v2/cut-method peak constraints zoomed.png")
//
//	minimizationData(false) { true }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/minimization-method constraints.png")
//
//	pathData(true) { true }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("job ordering")
//			size = 2.5
//			y.axis.name = "time (seconds)"
//		}
//	}.save("v2/path-type time.png")
//
//	cutData(true) { true }.plot {
//		points {
//			x("problem")
//			y("time")
//			y.axis.name = "time (seconds)"
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/cut-method time.png")
//
//	minimizationData(true) { true }.plot {
//		points {
//			x("problem")
//			y("time")
//			y.axis.name = "time (seconds)"
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/minimization-method time.png")
//
//	cutData(true, listOf(CutMethod.FastSafe, CutMethod.Instant)) { true }.plot {
//		points {
//			x("problem")
//			y("time")
//			y.axis.name = "time (seconds)"
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/cut-method final time.png")
//
//	cutData(false, listOf(CutMethod.FastSafe, CutMethod.Instant)) { true }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/cut-method final constraints.png")

//	cutData(false) { it.config.isBase || it.config.numJobs != 1000 }.groupBy("method").plot {
//		boxplot("#jobs", "#constraints")
//	}.save("v2/cut-method jobs constraints.png")
//	cutData(true) { it.config.isBase || it.config.numJobs != 1000 }.groupBy("method").plot {
//		boxplot("#jobs", "time")
//	}.save("v2/cut-method jobs time.png")
//	cutData(true, applyCandidateFilter = false) { it.config.isBase || it.config.numJobs != 1000 }.groupBy("precedence?").plot {
//		countPlot("#jobs")
//	}.save("v2/cut-method jobs precedence finish.png")

//	pathData(false) { it.config.isBase || it.config.numJobs != 1000 }.groupBy("job ordering").plot {
//		boxplot("#jobs", "#constraints")
//	}.save("v2/path-type jobs constraints.png")
//	pathData(true) { it.config.isBase || it.config.numJobs != 1000 }.groupBy("job ordering").plot {
//		boxplot("#jobs", "time")
//	}.save("v2/path-type jobs time.png")
//	pathData(true, applyCandidateFilter = false) { it.config.isBase || it.config.numJobs != 1000 }.groupBy("job ordering").plot {
//		countPlot("#jobs")
//	}.save("v2/path-type jobs finish.png")

//	minimizationData(false) { it.config.isBase || it.config.numJobs != 1000 }.groupBy("method").plot {
//		boxplot("#jobs", "#constraints")
//	}.save("v2/minimization-method jobs constraints.png")
//	minimizationData(true) { it.config.isBase || it.config.numJobs != 1000 }.groupBy("method").plot {
//		boxplot("#jobs", "time")
//	}.save("v2/minimization-method jobs time.png")
//	minimizationData(true, applyCandidateFilter = false) { it.config.isBase || it.config.numJobs != 1000 }.groupBy("method").plot {
//		countPlot("#jobs")
//	}.save("v2/minimization-method jobs finish.png")

//
//	cutData(false) { it.config.isBase || it.config.numTasks != 4 }.sortBy("#tasks").groupBy("method").plot {
//		boxplot("#tasks", "#constraints")
//	}.save("v2/cut-method tasks constraints.png")
//	cutData(true) { it.config.isBase || it.config.numTasks != 4 }.sortBy("#tasks").groupBy("method").plot {
//		boxplot("#tasks", "time")
//	}.save("v2/cut-method tasks time.png")
//	cutData(true, applyCandidateFilter = false) { it.config.isBase || it.config.numTasks != 4 }.sortBy("#tasks").groupBy("method").plot {
//		countPlot("#tasks")
//	}.save("v2/cut-method tasks finish.png")
//
//	cutData(false) { it.config.isBase || it.config.utilization != 60 }.sortBy("utilization").groupBy("method").plot {
//		boxplot("utilization", "#constraints")
//	}.save("v2/cut-method utilization constraints.png")
//	cutData(true) { it.config.isBase || it.config.utilization != 60 }.sortBy("utilization").groupBy("method").plot {
//		boxplot("utilization", "time") {
//			y.axis.min = 0
//			y.axis.max = 10
//		}
//	}.save("v2/cut-method utilization time.png")
//	cutData(true, applyCandidateFilter = false) { it.config.isBase || it.config.utilization != 60 }.sortBy("utilization").groupBy("method").plot {
//		countPlot("utilization")
//	}.save("v2/cut-method utilization finish.png")
//
//	cutData(false) { it.config.isBase || it.config.numCores != 3 }.sortBy("#cores").groupBy("method").plot {
//		boxplot("#cores", "#constraints")
//	}.save("v2/cut-method cores constraints.png")
//	cutData(true) { it.config.isBase || it.config.numCores != 3 }.sortBy("#cores").groupBy("method").plot {
//		boxplot("#cores", "time")
//	}.save("v2/cut-method cores time.png")
//	cutData(true, applyCandidateFilter = false) { it.config.isBase || it.config.numCores != 3 }.sortBy("#cores").groupBy("method").plot {
//		countPlot("#cores")
//	}.save("v2/cut-method cores finish.png")
//
//	cutData(false) { it.config.isBase || it.config.jitter != 20 }.sortBy("jitter").groupBy("method").plot {
//		boxplot("jitter", "#constraints")
//	}.save("v2/cut-method jitter constraints.png")
//	cutData(true) { it.config.isBase || it.config.jitter != 20 }.sortBy("jitter").groupBy("method").plot {
//		boxplot("jitter", "time")
//	}.save("v2/cut-method jitter time.png")
//	cutData(true, applyCandidateFilter = false) { it.config.isBase || it.config.jitter != 20 }.sortBy("jitter").groupBy("method").plot {
//		countPlot("jitter")
//	}.save("v2/cut-method jitter finish.png")
//
//	cutData(false) { true }.groupBy("method").plot {
//		boxplot("precedence?", "#constraints")
//	}.save("v2/cut-method precedence constraints.png")
//	cutData(true) { true }.groupBy("method").plot {
//		boxplot("precedence?", "time")
//	}.save("v2/cut-method precedence time.png")
//	cutData(true, applyCandidateFilter = false) { true }.groupBy("method").plot {
//		countPlot("precedence?")
//	}.save("v2/cut-method precedence finish.png")

//	minimizationData(true) { it.cutMethod == CutMethod.Instant }.plot {
//		points {
//			x("problem")
//			y("time")
//			y.axis.name = "time (seconds)"
//			color("method") {
//				scale = categorical(
//					"random" to Color.rgb(228, 26, 28),
//					"late" to Color.rgb(55, 126, 184),
//					"early" to Color.rgb(77, 175, 74)
//				)
//			}
//			size = 2.5
//		}
//	}.save("v2/minimization-method instant time.png")

//	minimizationData(false) { it.cutMethod == CutMethod.Instant }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/minimization-method instant constraints.png")
//
//	pathData(true) { it.cutMethod == CutMethod.Instant }.plot {
//		points {
//			x("problem")
//			y("time")
//			color("job ordering")
//			size = 2.5
//		}
//	}.save("v2/path-type instant time.png")
//
//	minimizationData(true, listOf(MinimizationMethod.Random, MinimizationMethod.Tail)) { it.cutMethod == CutMethod.Instant }.plot {
//		points {
//			x("problem")
//			y("time")
//			y.axis.name = "time (seconds)"
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/minimization-method instant final time.png")
//
//	minimizationData(false, listOf(MinimizationMethod.Random, MinimizationMethod.Tail)) { it.cutMethod == CutMethod.Instant }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("method")
//			size = 2.5
//		}
//	}.save("v2/minimization-method instant final constraints.png")
//
//	pathData(true) { it.cutMethod == CutMethod.Instant && it.minimizationMethod == MinimizationMethod.Random }.plot {
//		points {
//			x("problem")
//			y("time")
//			y.axis.name = "time (seconds)"
//			color("job ordering")
//			size = 2.5
//		}
//	}.save("v2/path-type instant random time.png")

//	pathData(false) { it.cutMethod == CutMethod.Instant && it.minimizationMethod == MinimizationMethod.Random }.plot {
//		points {
//			x("problem")
//			y("#constraints")
//			color("job ordering")
//			size = 2.5
//		}
//	}.save("v2/path-type instant random constraints.png")
//
//	pathData(false) { true }.plot {
//		points {
//			x("rating")
//			y("#constraints")
//			color("cut method")
//			symbol("job ordering")
//			size = 2.5
//			y.axis.min = 1
//			y.axis.max = 50
//		}
//	}.save("v2/path-type rating vs constraints.png")
//
//	pathData(true) { true }.plot {
//		points {
//			x("rating")
//			y("time")
//			color("cut method")
//			symbol("job ordering")
//			size = 2.5
//			y.axis.min = 0
//		}
//	}.save("v2/path-type rating vs time.png")

	run {
//		val sharedResults = results.filter { it.graphPath != null && it.scratchPath != null }
//		val data = dataFrameOf(
//			"graph" to sharedResults.map { it.graphPath!!.pathConstructionTime },
//			"scratch" to sharedResults.map { it.scratchPath!!.pathConstructionTime }
//		).gather("graph", "scratch").into("job ordering", "construction time")
//		data.plot {
//			boxplot("job ordering", "construction time") {
//				y.axis.min = 0
//				y.axis.name = "construction time (seconds)"
//			}
//		}.save("v2/path-type construction time.png")
//		data.plot {
//			boxplot("job ordering", "construction time") {
//				y.axis.min = 0
//				y.axis.max = 5
//			}
//		}.save("v2/path-type construction time zoomed.png")
	}
	run {
//		val trials = results.flatMap { problem -> problem.trials.filter {
//			it.cutMethod == CutMethod.Instant &&
//					it.minimizationMethod == MinimizationMethod.Random &&
//					it.config.isBase || it.config.numJobs != 1000
//		} }.sortedBy { it.config.numJobs }
//		val data = dataFrameOf(
//			"#jobs" to trials.map { it.config.numJobs.toString() },
//			"#explorations" to trials.map { it.numExplorations },
//			"avg exploration time" to trials.map { it.explorationTimes.mean() },
//			"log10(avg exploration time)" to trials.map { log10(it.explorationTimes.mean()) }
//		)
//		data.plot {
//			boxplot("#jobs", "#explorations")
//		}.save("v2/jobs explorations.png")
//		data.plot {
//			boxplot("#jobs", "avg exploration time") {
//				y.axis.name = "average exploration time (seconds)"
//			}
//		}.save("v2/jobs exploration time.png")
//		data.plot {
//			boxplot("#jobs", "log10(avg exploration time)") {
//				y.axis.name = "log10(avg exploration time) in seconds"
//			}
//		}.save("v2/jobs log exploration time.png")
	}
//
//	dataFrameOf(
//		"construction time" to results.mapNotNull { it.scratchPath }.map { it.pathConstructionTime }
//	).plot {
//		boxplot("construction time") {
//			y.axis.min = 0
//		}
//	}.save("v2/path-type construction time scratch.png")
//
//	run {
//		val sortedResults = results.filter { it.scratchPath != null }.sortedBy {
//			it.graphPath?.pathConstructionTime ?: (70.0 + it.scratchPath!!.pathConstructionTime)
//		}
//
//		val problems = mutableListOf<Int>()
//		val paths = mutableListOf<String>()
//		val time = mutableListOf<Double>()
//		for ((index, problem) in sortedResults.withIndex()) {
//			for (path in problem.paths) {
//				problems.add(index)
//				paths.add(path.type.toString())
//				time.add(path.pathConstructionTime)
//			}
//		}
//
//		dataFrameOf(
//			"problem" to problems,
//			"job ordering" to paths,
//			"construction time" to time
//		).plot {
//			points {
//				x("problem")
//				y("construction time")
//				y.axis.name = "construction time (seconds)"
//				color("job ordering")
//			}
//		}.save("v2/path-type construction time.png")
//
//		dataFrameOf(
//			"problem" to problems,
//			"job ordering" to paths,
//			"log10(construction time)" to time.map { log10(it) }
//		).plot {
//			points {
//				x("problem")
//				y("log10(construction time)")
//				y.axis.name = "log10(construction time) in seconds"
//				color("job ordering")
//			}
//		}.save("v2/path-type construction log time.png")
//	}

	val boringResults = boringResults()
	fun classificationPlot(
		sort: (MutableList<Pair<String, ReconfigurationConfig>>) -> Unit,
		condition: (ReconfigurationConfig) -> Boolean
	): DataFrame<*> {
		val classifications = (results.filter { condition(it.config) }.map { problem ->
			val classification = if (problem.scratchPath == null && problem.graphPath == null) "possibly infeasible"
			else if (problem.trials.isEmpty()) "late timeout" else "reconfigured"
			Pair(classification, problem.config)
		} + boringResults.filter { condition(it.second) }.map { Pair(it.first.displayName, it.second) }).toMutableList()
		sort(classifications)
		return dataFrameOf(
			"classification" to classifications.map { it.first },
			"#jobs" to classifications.map { it.second.numJobs.toString() },
			"#cores" to classifications.map { it.second.numCores.toString() },
			"#tasks" to classifications.map { it.second.numTasks.toString() },
			"utilization" to classifications.map { it.second.utilization.toString() },
			"precedence?" to classifications.map { it.second.precedence },
			"jitter" to classifications.map { it.second.jitter.toString() }
		)
	}

//	classificationPlot({}) { true }.plot {
//		countPlot("classification")
//	}.save("v2/classification.png")

	val colorList = listOf(Color.GREEN, Color.GREY, Color.RED, Color.ORANGE, Color.BLACK, Color.PURPLE)
	val colorClassifications = listOf("reconfigured", "already schedulable", "certainly infeasible", "possibly infeasible", "early timeout", "late timeout")

//	classificationPlot({ problems -> problems.sortBy { it.second.numJobs } } ) { config ->
//		config.isBase || config.numJobs != 1000
//	}.groupBy("classification").plot {
//		countPlot("#jobs") {
//			fillColor("classification") {
//				scale = categorical(colorList, colorClassifications)
//			}
//		}
//	}.save("v2/classification jobs.png")
//
//	classificationPlot({ problems -> problems.sortBy { it.second.numTasks } } ) { config ->
//		config.isBase || config.numTasks != 4
//	}.groupBy("classification").plot {
//		countPlot("#tasks") {
//			fillColor("classification") {
//				scale = categorical(colorList, colorClassifications)
//			}
//		}
//	}.save("v2/classification tasks.png")
//
//	classificationPlot({ problems -> problems.sortBy { it.second.utilization } } ) { config ->
//		config.isBase || config.utilization != 60
//	}.groupBy("classification").plot {
//		countPlot("utilization") {
//			fillColor("classification") {
//				scale = categorical(colorList, colorClassifications)
//			}
//		}
//	}.save("v2/classification utilization.png")
//
//	classificationPlot({ problems -> problems.sortBy { it.second.numCores } } ) { config ->
//		config.isBase || config.numCores != 3
//	}.groupBy("classification").plot {
//		countPlot("#cores") {
//			fillColor("classification") {
//				scale = categorical(colorList, colorClassifications)
//			}
//		}
//	}.save("v2/classification cores.png")
//
//	classificationPlot({ problems -> problems.sortBy { it.second.jitter } } ) { config ->
//		config.isBase || config.jitter != 20
//	}.groupBy("classification").plot {
//		countPlot("jitter") {
//			fillColor("classification") {
//				scale = categorical(colorList, colorClassifications)
//			}
//		}
//	}.save("v2/classification jitter.png")

//	classificationPlot({ problems -> problems.sortBy { if (it.second.precedence) 1 else 0 } } ) { config ->
//		true
//	}.groupBy("classification").plot {
//		countPlot("precedence?") {
//			fillColor("classification") {
//				scale = categorical(colorList, colorClassifications)
//			}
//		}
//	}.save("v2/classification precedence.png")
}

fun boringResults(): List<Pair<BoringReconfigurationResult, ReconfigurationConfig>> {
	val outerFolders = File("/home/knokko/thesis/boring-reconfiguration-experiments").listFiles()!!
	val boringResults = mutableListOf<Pair<BoringReconfigurationResult, ReconfigurationConfig>>()
	for (outerFolder in outerFolders) {
		for (innerFolder in outerFolder.listFiles()!!) {
			boringResults.add(Pair(BoringReconfigurationResult.parse(innerFolder), ReconfigurationConfig.parse(innerFolder)))
		}
	}

	return boringResults
}
