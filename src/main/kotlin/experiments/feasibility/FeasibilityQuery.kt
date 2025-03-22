package experiments.feasibility

import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.bars
import org.jetbrains.kotlinx.kandy.letsplot.x
import org.jetbrains.kotlinx.kandy.letsplot.y
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.statistics.kandy.layers.countPlot
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
	val outerFolders = File("feasibility-results").listFiles()!!
	val results = Collections.synchronizedList(ArrayList<FeasibilityCaseResult>())
	val threadPool = Executors.newFixedThreadPool(8)
	for (outerFolder in outerFolders) {
		if (!outerFolder.isDirectory) continue
		for (innerFolder in outerFolder.listFiles()!!) {
			if (!innerFolder.isDirectory) continue
			threadPool.submit {
				try {
					results.add(FeasibilityCaseResult.parse(innerFolder))
				} catch (failed: Throwable) {
					failed.printStackTrace()
				}
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

	fun classification(certainlyFeasible: Boolean, certainlyInfeasible: Boolean): String {
		if (certainlyFeasible) return "feasible"
		if (certainlyInfeasible) return "infeasible"
		return "unsolved"
	}

	println(results.count { it.certainlyInfeasible && !it.heuristicResult.certainlyInfeasible && it.config.precedence })
	println(results.count { it.certainlyFeasible && !it.heuristicResult.certainlyFeasible && !it.cplexResult.certainlyFeasible })
	println(results.count { it.certainlyInfeasible && !it.heuristicResult.certainlyInfeasible && !it.z3Result.certainlyInfeasible })
//	run {
//		results.sortBy { it.config.utilization }
//		val data = mapOf(
//			"utilization" to results.map { "${it.config.utilization}%" },
//			"classification" to results.map { classification(it.certainlyFeasible, it.certainlyInfeasible) }
//		)
//		data.toDataFrame().groupBy("classification").plot {
//			countPlot("utilization") {
//				fillColor("classification") {
//					scale = categorical(listOf(Color.GREEN, Color.BLUE, Color.RED))
//				}
//			}
//		}.save("total-utilization-vs-classification.png")
//	}

//	run {
//		results.sortWith { a, b ->
//			if (a.config.numJobs == b.config.numJobs) {
//				classification(a.certainlyFeasible, a.certainlyInfeasible).compareTo(classification(b.certainlyFeasible, b.certainlyInfeasible))
//			} else a.config.numJobs.compareTo(b.config.numJobs)
//		}
//		val data = mapOf(
//			"jobs" to results.map { it.config.numJobs.toString() },
//			"classification" to results.map { classification(it.certainlyFeasible, it.certainlyInfeasible) }
//		)
//		data.toDataFrame().groupBy("classification").plot {
//			countPlot("jobs") {
//				fillColor("classification") {
//					scale = categorical(listOf(Color.GREEN, Color.BLUE, Color.RED))
//				}
//			}
//		}.save("total-jobs-vs-classification.png")
//	}

	fun solverBars(totalCount: Int, solverCounts: List<Int>, label: String, file: String) {
		plot {
			x(listOf("heuristic", "Z3", "CPLEX", "Minisat"))
			layout {
				xAxisLabel = "feasibility test"
				yAxisLabel = label
			}
			bars {
				y.constant(totalCount)
				width = 0.5
				fillColor = Color.GREY
				alpha = 0.3
			}
			bars {
				y(solverCounts)
			}
		}.save(file)
	}

	solverBars(results.count { it.certainlyFeasible }, listOf(
		results.count { it.heuristicResult.certainlyFeasible },
		results.count { it.z3Result.certainlyFeasible },
		results.count { it.cplexResult.certainlyFeasible },
		results.count { it.minisatResult?.certainlyFeasible == true }
	), "number of problems solved", "feasible-problems-vs-solvers.png")
	solverBars(results.count { it.certainlyInfeasible }, listOf(
		results.count { it.heuristicResult.certainlyInfeasible },
		results.count { it.z3Result.certainlyInfeasible },
		results.count { it.cplexResult.certainlyInfeasible },
		results.count { it.minisatResult?.certainlyInfeasible == true }
	), "number of problems solved", "infeasible-problems-vs-solvers.png")
	solverBars(results.count { !it.config.precedence && it.certainlyFeasible }, listOf(
		results.count { !it.config.precedence && it.heuristicResult.certainlyFeasible },
		results.count { !it.config.precedence && it.z3Result.certainlyFeasible },
		results.count { !it.config.precedence && it.cplexResult.certainlyFeasible },
		results.count { !it.config.precedence && it.minisatResult?.certainlyFeasible == true }
	), "number of problems solved", "no-prec-feasible-problems-vs-solvers.png")
	solverBars(results.count { !it.config.precedence && it.certainlyInfeasible }, listOf(
		results.count { !it.config.precedence && it.heuristicResult.certainlyInfeasible },
		results.count { !it.config.precedence && it.z3Result.certainlyInfeasible },
		results.count { !it.config.precedence && it.cplexResult.certainlyInfeasible },
		results.count { !it.config.precedence && it.minisatResult?.certainlyInfeasible == true }
	), "number of problems solved", "no-prec-infeasible-problems-vs-solvers.png")
	solverBars(results.count { it.config.precedence && it.certainlyFeasible }, listOf(
		results.count { it.config.precedence && it.heuristicResult.certainlyFeasible },
		results.count { it.config.precedence && it.z3Result.certainlyFeasible },
		results.count { it.config.precedence && it.cplexResult.certainlyFeasible },
		results.count { it.config.precedence && it.minisatResult?.certainlyFeasible == true }
	), "number of problems solved", "only-prec-feasible-problems-vs-solvers.png")
	solverBars(results.count { it.config.precedence && it.certainlyInfeasible }, listOf(
		results.count { it.config.precedence && it.heuristicResult.certainlyInfeasible },
		results.count { it.config.precedence && it.z3Result.certainlyInfeasible },
		results.count { it.config.precedence && it.cplexResult.certainlyInfeasible },
		results.count { it.config.precedence && it.minisatResult?.certainlyInfeasible == true }
	), "number of problems solved", "only-prec-infeasible-problems-vs-solvers.png")

	fun isExclusiveFeasible(result: FeasibilityCaseResult, test: FeasibilityTest) = result.certainlyFeasible &&
			result.getAll()[test]!!.certainlyFeasible && result.getAll().values.count { it.certainlyFeasible } == 1
	fun isExclusiveInfeasible(result: FeasibilityCaseResult, test: FeasibilityTest) = result.certainlyInfeasible &&
			result.getAll()[test]!!.certainlyInfeasible && result.getAll().values.count { it.certainlyInfeasible } == 1

	solverBars(results.count { it.certainlyFeasible }, listOf(
		results.count { isExclusiveFeasible(it, FeasibilityTest.HEURISTIC) },
		results.count { isExclusiveFeasible(it, FeasibilityTest.Z3_MODEL1) },
		results.count { isExclusiveFeasible(it, FeasibilityTest.CPLEX) },
		0
	), "number of problems exclusively solved", "feasible-problems-exclusive-solvers.png")
	solverBars(results.count { it.certainlyInfeasible }, listOf(
		results.count { isExclusiveInfeasible(it, FeasibilityTest.HEURISTIC) },
		results.count { isExclusiveInfeasible(it, FeasibilityTest.Z3_MODEL1) },
		results.count { isExclusiveInfeasible(it, FeasibilityTest.CPLEX) },
		0
	), "number of problems exclusively solved", "infeasible-problems-exclusive-solvers.png")
}
