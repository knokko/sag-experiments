package experiments.feasibility

import org.jetbrains.kotlinx.dataframe.api.DataFrameBuilder
import org.jetbrains.kotlinx.dataframe.api.column
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.layers.bars
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
	val data = mapOf(
		"solver" to listOf("heuristic", "z3"),
		"feasible" to listOf(30, 15),
		"infeasible" to listOf(5, 3),
		"unsolved" to listOf(2, 10)
	)
	plot(data) {
		points {
			x(column<String>("solver"))
			y(column<Int>("feasible"))
			size = 4.5
		}
	}.save("plot.png")
	return

	val outerFolders = File("feasibility-results").listFiles()!!
	val results = Collections.synchronizedList(ArrayList<FeasibilityCaseResult>())
	val threadPool = Executors.newFixedThreadPool(8)
	for (outerFolder in outerFolders) {
		if (!outerFolder.isDirectory) continue
		for (innerFolder in outerFolder.listFiles()!!) {
			if (!innerFolder.isDirectory) continue
			threadPool.submit {
				results.add(FeasibilityCaseResult.parse(innerFolder))
			}
		}
	}
	threadPool.shutdown()
	if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) throw Error("Timed out")

	println("base stats: out of the ${results.size} problems:")
	println("${results.count { it.certainlyFeasible }} are certainly feasible")
	println("${results.count { it.certainlyInfeasible }} are certainly infeasible")
	println("${results.count { !it.certainlyFeasible && !it.certainlyInfeasible }} are unsolved")
	println()
	for (numCores in 1 .. 5) {
		val numProblems = results.count { !it.certainlyInfeasible && !it.certainlyFeasible && it.config.numCores == numCores }
		println("  of which $numProblems have $numCores cores")
	}
	for (utilization in arrayOf(30, 70, 90, 100)) {
		val numProblems = results.count { !it.certainlyInfeasible && !it.certainlyFeasible && it.config.utilization == utilization }
		println("  of which $numProblems have $utilization% utilization")
	}
	for (numJobs in arrayOf(40, 80, 300, 1000, 5000)) {
		val numProblems = results.count { !it.certainlyInfeasible && !it.certainlyFeasible && it.config.numJobs == numJobs }
		println("  of which $numProblems have $numJobs jobs")
	}
	for (length in arrayOf(5, 20, 100, 5000)) {
		val numProblems = results.count { !it.certainlyInfeasible && !it.certainlyFeasible && it.config.jobLength == length }
		println("  of which $numProblems have an average job length of $length")
	}
	run {
		val numProblems = results.count { !it.certainlyInfeasible && !it.certainlyFeasible && it.config.precedence }
		println("  of which $numProblems have precedence constraints")
	}

	for (solver in arrayOf(
		FeasibilityTest.HEURISTIC, FeasibilityTest.Z3_MODEL1, FeasibilityTest.CPLEX, FeasibilityTest.MINISAT
	)) {
		println()
		if (solver == FeasibilityTest.MINISAT) {
			println("out of the ${results.count { !it.config.precedence }} problems without precedence constraints:")
		}
		var relevantResults = results.map { it.getAll()[solver] }
		if (solver == FeasibilityTest.MINISAT) relevantResults = relevantResults.filterNotNull()
		println("The ${solver.name} test detected that ${relevantResults.count { it!!.certainlyFeasible }} problems are feasible")
		println("The ${solver.name} test detected that ${relevantResults.count { it!!.certainlyInfeasible }} problems are infeasible")
		val exclusiveFeasible = results.count {
			val own = it.getAll()[solver] ?: return@count false
			own.certainlyFeasible && !it.getAll().entries.any { inner -> inner.key != solver && inner.value.certainlyFeasible }
		}
		println("$exclusiveFeasible problems were only detected to be feasible by the ${solver.name} test")
		val exclusiveInfeasible = results.count {
			val own = it.getAll()[solver] ?: return@count false
			own.certainlyInfeasible && !it.getAll().entries.any { inner -> inner.key != solver && inner.value.certainlyInfeasible }
		}
		println("$exclusiveInfeasible problems were only detected to be infeasible by the ${solver.name} test")
		run {
			val unsolved = relevantResults.count { !it!!.certainlyFeasible && !it.certainlyInfeasible }
			println("$unsolved / ${relevantResults.size} problems were unsolved by the ${solver.name} test")
		}
		for (numCores in 1 .. 5) {
			val unsolved = relevantResults.count { !it!!.certainlyInfeasible && !it.certainlyFeasible && it.config.numCores == numCores }
			val total = relevantResults.count { it!!.config.numCores == numCores }
			println("  $unsolved / $total problems with $numCores cores were unsolved")
		}
		for (utilization in arrayOf(30, 70, 90, 100)) {
			val unsolved = relevantResults.count { !it!!.certainlyInfeasible && !it.certainlyFeasible && it.config.utilization == utilization }
			val total = relevantResults.count { it!!.config.utilization == utilization }
			println("  $unsolved / $total problems with $utilization% utilization were unsolved")
			val unsolvedManyJobs = relevantResults.count { !it!!.certainlyInfeasible && !it.certainlyFeasible && it.config.utilization == utilization && it.config.numJobs == 5000 }
			val totalManyJobs = relevantResults.count { it!!.config.utilization == utilization && it.config.numJobs == 5000 }
			println("  $unsolvedManyJobs / $totalManyJobs problems with $utilization% utilization and 5000 jobs were unsolved")
		}
		for (numJobs in arrayOf(40, 80, 300, 1000, 5000)) {
			val unsolved = relevantResults.count { !it!!.certainlyInfeasible && !it.certainlyFeasible && it.config.numJobs == numJobs }
			val total = relevantResults.count { it!!.config.numJobs == numJobs }
			println("  $unsolved / $total problems with $numJobs jobs were unsolved")
		}
		for (length in arrayOf(5, 20, 100, 5000)) {
			val unsolved = relevantResults.count { !it!!.certainlyInfeasible && !it.certainlyFeasible && it.config.jobLength == length }
			val total = relevantResults.count { it!!.config.jobLength == length }
			println("  $unsolved / $total problems with an average job length of $length were unsolved")
		}
		run {
			val unsolved = relevantResults.count { !it!!.certainlyInfeasible && !it.certainlyFeasible && it.config.precedence }
			val total = relevantResults.count { it!!.config.precedence }
			println("  $unsolved / $total problems with precedence constraints were unsolved")
		}
	}


}
