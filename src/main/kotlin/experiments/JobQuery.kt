package experiments

import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.statistics.binning.BinsOption
import org.jetbrains.kotlinx.statistics.kandy.layers.countPlot
import org.jetbrains.kotlinx.statistics.kandy.layers.histogram
import org.jetbrains.kotlinx.statistics.kandy.stattransform.statBin
import java.io.File
import java.lang.Integer.parseInt
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.sqrt

fun main() {
	reconfigurations()
}

fun reconfigurations() {
	val numJobsToTimeOutsFast = mutableListOf<Int>()
	val numJobsToTimeOutsInstant = mutableListOf<Int>()
	val data = run {
		val caseColumn = mutableListOf<Int>()
		val jobsColumn = mutableListOf<Int>()
		val methodColumn = mutableListOf<String>()
		val explorationsColumn = mutableListOf<Int>()
		val peakConstraintsColumn = mutableListOf<Int>()
		val remainingConstraintsColumn = mutableListOf<Int>()
		val timeColumn = mutableListOf<Int>()
		val timedOutColumn = mutableListOf<Boolean>()

		for (case in 0 until 500) {
			val jobsFile = File("job-experiments/jobset-log-uniform_$case.csv")
			val numJobs = jobsFile.readLines().size - 1
			val fastFile = File("job-experiments/output$case-fast.log")
			val instantFile = File("job-experiments/output$case-instant.log")
			if (fastFile.exists() != instantFile.exists()) {
				throw RuntimeException("Check case $case")
			}
			if (!fastFile.exists()) continue

			while (numJobs >= numJobsToTimeOutsInstant.size) {
				numJobsToTimeOutsFast.add(0)
				numJobsToTimeOutsInstant.add(0)
			}

			for ((resultFile, methodName) in arrayOf(Pair(fastFile, "fast"), Pair(instantFile, "instant"))) {

				var numExplorations = 0
				var peakConstraints = -1
				var remainingConstraints = -1
				var executionTime = 1800
				resultFile.forEachLine { line ->
					if (line.contains("intermediate exploration")) numExplorations += 1
					if (line.contains("remain after transitivity analysis")) {
						peakConstraints = parseInt(line.substring(0, line.indexOf(' ')))
					}
					if (line.contains("remain after trial")) {
						remainingConstraints = parseInt(line.substring(0, line.indexOf(' ')))
					}
					if (line.contains("Finished after")) {
						executionTime = parseInt(line.substring("Finished after ".length, line.indexOf(' ', "Finished after ".length)))
					}
				}

				val excludeIfOneFailed = true
				if (excludeIfOneFailed && remainingConstraints == -1) {
					if (methodName == "instant" && caseColumn.size % 2 == 1) {
						caseColumn.removeLast()
						jobsColumn.removeLast()
						methodColumn.removeLast()
						explorationsColumn.removeLast()
						peakConstraintsColumn.removeLast()
						remainingConstraintsColumn.removeLast()
						timeColumn.removeLast()
						timedOutColumn.removeLast()
					}
					break
				}

				caseColumn.add(case)
				jobsColumn.add(numJobs)
				methodColumn.add(methodName)
				explorationsColumn.add(numExplorations)
				peakConstraintsColumn.add(peakConstraints)
				remainingConstraintsColumn.add(remainingConstraints)
				timeColumn.add(executionTime)
				timedOutColumn.add(remainingConstraints == -1)
				if (remainingConstraints == -1) {
					if (methodName == "fast") numJobsToTimeOutsFast[numJobs] += 1
					else numJobsToTimeOutsInstant[numJobs] += 1
				}
			}
		}

		dataFrameOf(
			"case" to caseColumn,
			"#jobs" to jobsColumn,
			"method" to methodColumn,
			"#explorations" to explorationsColumn,
			"#peak constraints" to peakConstraintsColumn,
			"#constraints" to remainingConstraintsColumn,
			"time" to timeColumn,
			"sqrt(time)" to timeColumn.map { sqrt(it.toDouble()) },
			"timed out" to timedOutColumn
		)
	}

	println("smallest #jobs is ${data.min("#jobs")} and largest #jobs is ${data.max("#jobs")}")
//	data.groupBy("method").plot {
//		histogram("#jobs", binsOption = BinsOption.byWidth(2000.0)) {
//			x.axis.min = 0
//			x.axis.max = 20000
//			y.axis.min = 0
//			y.axis.max = 60
//		}
//	}.save("v3/jobs distribution.png")
	data.shuffle().groupBy("method").plot {
		points {
			x("#jobs")
			y("time")
			color("method")
			size = 2.0
		}
	}.save("v3/jobs vs time.png")
	data.shuffle().groupBy("method").plot {
		points {
			x("#jobs")
			y("#explorations")
			color("method")
			y.axis.min = 1
			size = 2.0
		}
	}.save("v3/jobs vs explorations.png")
	data.shuffle().groupBy("method").plot {
		points {
			x("#jobs")
			y("#constraints")
			color("method")
			size = 2.0
			y.axis.min = 1
		}
	}.save("v3/jobs vs constraints.png")
	data.shuffle().groupBy("method").plot {
		points {
			x("#jobs")
			y("sqrt(time)")
			color("method")
			size = 2.0
		}
	}.save("v3/jobs vs sqrt time.png")
//	data.filterBy("timed out").plot {
//		countPlot("method") {
//			y.axis.name = "#time outs"
//		}
//	}.save("v3/method timeout.png")
//	data.filterBy("timed out").groupBy("method").plot {
//		histogram("#jobs", binsOption = BinsOption.byWidth(2000.0)) {
//			x.axis.min = 0
//			x.axis.max = 20000
//			y.axis.min = 0
//			y.axis.max = 60
//			y.axis.name = "#time-outs"
//		}
//	}.save("v3/method jobs timeout.png")

//	val massJobsToTimeOutsFast = IntArray(numJobsToTimeOutsFast.size)
//	val massJobsToTimeOutsInstant = IntArray(numJobsToTimeOutsInstant.size)
//	for ((startIndex, count) in numJobsToTimeOutsFast.withIndex()) {
//		for (index in startIndex until massJobsToTimeOutsFast.size) massJobsToTimeOutsFast[index] += count
//	}
//	for ((startIndex, count) in numJobsToTimeOutsInstant.withIndex()) {
//		for (index in startIndex until massJobsToTimeOutsInstant.size) massJobsToTimeOutsInstant[index] += count
//	}
//	val massData = dataFrameOf(
//		"#jobs" to List(numJobsToTimeOutsInstant.size * 2) { index -> index % numJobsToTimeOutsInstant.size },
//		"method" to List(numJobsToTimeOutsInstant.size) { "instant" } + List(numJobsToTimeOutsFast.size) { "fast" },
//		"#timeouts" to (massJobsToTimeOutsInstant + massJobsToTimeOutsFast).toList()
//	)
//	massData.groupBy("method").plot {
//		line {
//			x("#jobs")
//			y("#timeouts")
//			color("method")
//			x.axis.name = "X: the number of jobs"
//			y.axis.name = "#time-outs for at most X jobs"
//		}
//	}.save("v3/jobs vs timeouts.png")
}

fun alreadySchedulable() {
	var alreadySchedulable = 0
	var unschedulable = 0
	var timedOut = 0
	File("./job-experiments.log").forEachLine { line ->
		if (line.contains("was already schedulable")) alreadySchedulable += 1
		if (line == "Initial exploration timed out after 5 minutes") timedOut += 1
		if (line.contains("Start reconfiguring test")) unschedulable += 1
	}

	println("Total is ${alreadySchedulable + unschedulable + timedOut}")
	println("$alreadySchedulable were already schedulable")
	println("$unschedulable were unschedulable")
	println("$timedOut timed out after 5 minutes")
}
