package experiments.feasibility

import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.continuous
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.feature.FeatureName
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.feature.position
import org.jetbrains.kotlinx.kandy.letsplot.layers.bars
import org.jetbrains.kotlinx.kandy.letsplot.x
import org.jetbrains.kotlinx.kandy.letsplot.y
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.jetbrains.kotlinx.statistics.kandy.layers.boxplot
import org.jetbrains.kotlinx.statistics.kandy.layers.countPlot
import org.jetbrains.kotlinx.statistics.kandy.layers.heatmap
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
//	plot {
//		heatmap(listOf(1, 2, 3, 4), listOf("a", "b", "a", "a"), listOf(0.1, 0.7, 1.0, 1.0))
//	}.save("test.png")
//	return

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
	println("${results.count { it.config.precedence }} problems have precedence constraints")
	println("${results.count { !it.config.precedence }} problems do not have precedence constraints")
	println("${results.count { it.certainlyFeasible }} are certainly feasible")
	println("${results.count { it.certainlyInfeasible }} are certainly infeasible, " +
			"of which ${results.count { it.heuristicResult.failedBoundsTest }} failed the bounds test")
	println("${results.count { !it.certainlyFeasible && !it.certainlyInfeasible }} are unsolved")
	println("${results.count { it.certainlyFeasible && it.config.precedence }} certainly feasible problems have precedence constraints")
	println("${results.count { it.certainlyInfeasible && it.config.precedence }} certainly infeasible problems have precedence constraints")
	println("${results.count { it.certainlyFeasible && !it.config.precedence }} certainly feasible problems do not have precedence constraints")
	println("${results.count { it.certainlyInfeasible && !it.config.precedence }} certainly infeasible problems do not have precedence constraints")
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
		return "unidentified"
	}

	println("interval test: ${results.count { it.heuristicResult.certainlyInfeasible && it.heuristicResult.passedLoadTest }} out of ${results.count { it.heuristicResult.certainlyInfeasible }}")
	println("Z3 generation timeouts: ${results.count { it.z3Result.generationTimedOut }} out of ${results.count { it.z3Result.timedOut }} out of ${results.size}")
	println("Minisat generation timeouts: ${results.count { it.minisatResult?.generationTimedOut == true }} out of ${results.count { it.minisatResult?.timedOut == true }} out of ${results.count { it.minisatResult != null }}")
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
//
//	run {
//		results.sortWith { a, b ->
//			if (a.config.numJobs == b.config.numJobs) {
//				classification(a.certainlyFeasible, a.certainlyInfeasible).compareTo(classification(b.certainlyFeasible, b.certainlyInfeasible))
//			} else a.config.numJobs.compareTo(b.config.numJobs)
//		}
//		val data = mapOf(
//			"#jobs" to results.map { it.config.numJobs.toString() },
//			"classification" to results.map { classification(it.certainlyFeasible, it.certainlyInfeasible) }
//		)
//		data.toDataFrame().groupBy("classification").plot {
//			countPlot("#jobs") {
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

//	solverBars(results.count { it.certainlyFeasible }, listOf(
//		results.count { it.heuristicResult.certainlyFeasible },
//		results.count { it.z3Result.certainlyFeasible },
//		results.count { it.cplexResult.certainlyFeasible },
//		results.count { it.minisatResult?.certainlyFeasible == true }
//	), "number of problems identified", "feasible-problems-vs-solvers.png")
//	solverBars(results.count { it.certainlyInfeasible }, listOf(
//		results.count { it.heuristicResult.certainlyInfeasible },
//		results.count { it.z3Result.certainlyInfeasible },
//		results.count { it.cplexResult.certainlyInfeasible },
//		results.count { it.minisatResult?.certainlyInfeasible == true }
//	), "number of problems identified", "infeasible-problems-vs-solvers.png")
//	solverBars(results.count { !it.config.precedence && it.certainlyFeasible }, listOf(
//		results.count { !it.config.precedence && it.heuristicResult.certainlyFeasible },
//		results.count { !it.config.precedence && it.z3Result.certainlyFeasible },
//		results.count { !it.config.precedence && it.cplexResult.certainlyFeasible },
//		results.count { !it.config.precedence && it.minisatResult?.certainlyFeasible == true }
//	), "number of problems identified", "no-prec-feasible-problems-vs-solvers.png")
//	solverBars(results.count { !it.config.precedence && it.certainlyInfeasible }, listOf(
//		results.count { !it.config.precedence && it.heuristicResult.certainlyInfeasible },
//		results.count { !it.config.precedence && it.z3Result.certainlyInfeasible },
//		results.count { !it.config.precedence && it.cplexResult.certainlyInfeasible },
//		results.count { !it.config.precedence && it.minisatResult?.certainlyInfeasible == true }
//	), "number of problems identified", "no-prec-infeasible-problems-vs-solvers.png")
//	solverBars(results.count { !it.config.precedence && it.certainlyInfeasible && it.config.jobLength <= 20 && it.config.utilization == 90 }, listOf(
//		results.count { !it.config.precedence && it.heuristicResult.certainlyInfeasible && it.config.jobLength <= 20 && it.config.utilization == 90 },
//		results.count { !it.config.precedence && it.z3Result.certainlyInfeasible && it.config.jobLength <= 20 && it.config.utilization == 90 },
//		results.count { !it.config.precedence && it.cplexResult.certainlyInfeasible && it.config.jobLength <= 20 && it.config.utilization == 90 },
//		results.count { !it.config.precedence && it.minisatResult?.certainlyInfeasible == true && it.config.jobLength <= 20 && it.config.utilization == 90 }
//	), "number of problems identified", "short-infeasible-problems-vs-solvers.png")
//	solverBars(results.count { !it.config.precedence && it.certainlyFeasible && it.config.jobLength <= 20 && it.config.utilization == 90 }, listOf(
//		results.count { !it.config.precedence && it.heuristicResult.certainlyFeasible && it.config.jobLength <= 20 && it.config.utilization == 90 },
//		results.count { !it.config.precedence && it.z3Result.certainlyFeasible && it.config.jobLength <= 20 && it.config.utilization == 90 },
//		results.count { !it.config.precedence && it.cplexResult.certainlyFeasible && it.config.jobLength <= 20 && it.config.utilization == 90 },
//		results.count { !it.config.precedence && it.minisatResult?.certainlyFeasible == true && it.config.jobLength <= 20 && it.config.utilization == 90 }
//	), "number of problems solved", "short-feasible-problems-vs-solvers.png")
//	solverBars(results.count { it.config.precedence && it.certainlyFeasible }, listOf(
//		results.count { it.config.precedence && it.heuristicResult.certainlyFeasible },
//		results.count { it.config.precedence && it.z3Result.certainlyFeasible },
//		results.count { it.config.precedence && it.cplexResult.certainlyFeasible },
//		results.count { it.config.precedence && it.minisatResult?.certainlyFeasible == true }
//	), "number of problems solved", "only-prec-feasible-problems-vs-solvers.png")
//	solverBars(results.count { it.config.precedence && it.certainlyInfeasible }, listOf(
//		results.count { it.config.precedence && it.heuristicResult.certainlyInfeasible },
//		results.count { it.config.precedence && it.z3Result.certainlyInfeasible },
//		results.count { it.config.precedence && it.cplexResult.certainlyInfeasible },
//		results.count { it.config.precedence && it.minisatResult?.certainlyInfeasible == true }
//	), "number of problems solved", "only-prec-infeasible-problems-vs-solvers.png")

	fun isExclusiveFeasible(result: FeasibilityCaseResult, test: FeasibilityTest) = result.certainlyFeasible &&
			result.getAll()[test]!!.certainlyFeasible && result.getAll().values.count { it.certainlyFeasible } == 1
	fun isExclusiveInfeasible(result: FeasibilityCaseResult, test: FeasibilityTest) = result.certainlyInfeasible &&
			result.getAll()[test]!!.certainlyInfeasible && result.getAll().values.count { it.certainlyInfeasible } == 1

//	solverBars(results.count { it.certainlyFeasible }, listOf(
//		results.count { isExclusiveFeasible(it, FeasibilityTest.HEURISTIC) },
//		results.count { isExclusiveFeasible(it, FeasibilityTest.Z3_MODEL1) },
//		results.count { isExclusiveFeasible(it, FeasibilityTest.CPLEX) },
//		0
//	), "number of problems exclusively identified", "feasible-problems-exclusive-solvers.png")
//	solverBars(results.count { it.certainlyInfeasible }, listOf(
//		results.count { isExclusiveInfeasible(it, FeasibilityTest.HEURISTIC) },
//		results.count { isExclusiveInfeasible(it, FeasibilityTest.Z3_MODEL1) },
//		results.count { isExclusiveInfeasible(it, FeasibilityTest.CPLEX) },
//		0
//	), "number of problems exclusively identified", "infeasible-problems-exclusive-solvers.png")

	fun utilizationBars(test: FeasibilityTest, label: String, file: String, condition: (FeasibilitySolverResult) -> Boolean) {
		val testResults = results.mapNotNull { it.getAll()[test] }
        plot {
			x(listOf("30%", "70%", "90%", "~100%"))
			layout {
				xAxisLabel = "utilization"
				yAxisLabel = label
			}
			bars {
				y(listOf(30, 70, 90, 100).map {
					utilization -> testResults.count { it.config.utilization == utilization && condition(it) }
				})
				width = 0.5
				fillColor = Color.GREY
				alpha = 0.3
			}
			bars {
				y(listOf(30, 70, 90, 100).map {
					utilization -> testResults.count {
						it.config.utilization == utilization && condition(it) && (it.certainlyFeasible || it.certainlyInfeasible)
					}
				})
			}
		}.save(file)
	}

	fun numJobsBars(test: FeasibilityTest, label: String, file: String, condition: (FeasibilitySolverResult) -> Boolean) {
		val testResults = results.mapNotNull { it.getAll()[test] }
		plot {
			x(listOf("40", "80", "300", "1000", "5000"))
			layout {
				xAxisLabel = "number of jobs"
				yAxisLabel = label
			}
			bars {
				y(listOf(40, 80, 300, 1000, 5000).map {
						numJobs -> testResults.count { it.config.numJobs == numJobs && condition(it) }
				})
				width = 0.5
				fillColor = Color.GREY
				alpha = 0.3
			}
			bars {
				y(listOf(40, 80, 300, 1000, 5000).map { numJobs-> testResults.count {
					it.config.numJobs == numJobs && condition(it) && (it.certainlyFeasible || it.certainlyInfeasible)
				} })
			}
		}.save(file)
	}

	fun numCoresBars(test: FeasibilityTest, label: String, file: String, condition: (FeasibilityCaseResult) -> Boolean) {
		plot {
			x(listOf("1", "2", "3", "4", "5"))
			layout {
				xAxisLabel = "number of cores"
				yAxisLabel = label
			}
			bars {
				y(listOf(1, 2, 3, 4, 5).map {
						numCores -> results.count { it.config.numCores == numCores && condition(it) }
				})
				width = 0.5
				fillColor = Color.GREY
				alpha = 0.3
			}
			bars {
				y(listOf(1, 2, 3, 4, 5).map { numCores -> results.count {
					it.config.numCores == numCores && condition(it) && (it.getAll()[test]!!.certainlyFeasible || it.getAll()[test]!!.certainlyInfeasible)
				} })
			}
		}.save(file)
	}

	fun durationBars(test: FeasibilityTest, label: String, file: String, condition: (FeasibilitySolverResult) -> Boolean) {
		val testResults = results.mapNotNull { it.getAll()[test] }
		plot {
			x(listOf("5", "20", "100", "5000"))
			layout {
				xAxisLabel = "average worst-case execution time"
				yAxisLabel = label
			}
			bars {
				y(listOf(5, 20, 100, 5000).map {
						duration -> testResults.count { it.config.jobLength == duration && condition(it) }
				})
				width = 0.5
				fillColor = Color.GREY
				alpha = 0.3
			}
			bars {
				y(listOf(5, 20, 100, 5000).map { duration -> testResults.count {
					it.config.jobLength == duration && condition(it) && (it.certainlyFeasible || it.certainlyInfeasible)
				} })
			}
		}.save(file)
	}

	fun createUtilizationJobsMap(file: String, goodCondition: (FeasibilityCaseResult) -> Boolean, totalCondition: (FeasibilityCaseResult) -> Boolean) {
		plot {
			results.sortBy { it.config.utilization }
			results.sortBy { it.config.numJobs }
			val countMap = mutableMapOf<Pair<Int, Int>, Int>()
			for (result in results.filter(totalCondition)) {
				val key = Pair(result.config.utilization, result.config.numJobs)
				countMap[key] = countMap.getOrDefault(key, 0) + 1
			}
			heatmap(
				results.map { "${it.config.utilization}%" },
				results.map { it.config.numJobs.toString() },
				results.map { (if (totalCondition(it) && goodCondition(it)) 100.0 / countMap[Pair(it.config.utilization, it.config.numJobs)]!! else 0.0)  }
			) {
				x.axis.name = "utilization"
				y.axis.name = "number of jobs"
				fillColor(Stat.countWeighted) {
					legend.name = "% of problems"
					scale = continuous(domainMin = 0, domainMax = 101)
				}
			}
		}.save(file)
	}

//	createUtilizationJobsMap("total-feasible-matrix.png", { it.certainlyFeasible }, { true })
//	createUtilizationJobsMap("total-infeasible-matrix.png", { it.certainlyInfeasible }, { true })
//	createUtilizationJobsMap("total-matrix.png", { it.certainlyFeasible || it.certainlyInfeasible }, { true })
//	utilizationBars(FeasibilityTest.HEURISTIC, "number of identified problems", "heuristic-utilization-vs-solved.png") { true }
//	numJobsBars(FeasibilityTest.HEURISTIC, "number of identified problems", "heuristic-jobs-vs-solved.png") { true }
//	numCoresBars(FeasibilityTest.HEURISTIC, "number of identified problems", "heuristic-cores-vs-solved.png") { true }
//	durationBars(FeasibilityTest.HEURISTIC, "number of identified problems", "heuristic-duration-vs-solved.png") { true }
//	createUtilizationJobsMap("heuristic-feasible-matrix.png", { it.heuristicResult.certainlyFeasible }, { true })
//	createUtilizationJobsMap("heuristic-infeasible-matrix.png", { it.heuristicResult.certainlyInfeasible }, { true })
//	createUtilizationJobsMap("heuristic-matrix.png", {
//		it.heuristicResult.certainlyFeasible || it.heuristicResult.certainlyInfeasible }, { true }
//	)
//	utilizationBars(FeasibilityTest.Z3_MODEL1, "number of identified problems", "z3-utilization-vs-solved.png") { true }
//	numJobsBars(FeasibilityTest.Z3_MODEL1, "number of identified problems", "z3-jobs-vs-solved.png") { true }
//	numCoresBars(FeasibilityTest.Z3_MODEL1, "number of identified problems", "z3-cores-vs-solved.png") { true }
//	durationBars(FeasibilityTest.Z3_MODEL1, "number of identified problems", "z3-duration-vs-solved.png") { true }
//	utilizationBars(FeasibilityTest.Z3_MODEL1, "number of solved problems", "z3-no-prec-utilization-vs-solved.png") { !it.config.precedence }
//	numJobsBars(FeasibilityTest.Z3_MODEL1, "number of solved problems", "z3-no-prec-jobs-vs-solved.png") { !it.config.precedence }
//	numCoresBars(FeasibilityTest.Z3_MODEL1, "number of solved problems", "z3-no-prec-cores-vs-solved.png") { !it.config.precedence }
//	durationBars(FeasibilityTest.Z3_MODEL1, "number of solved problems", "z3-no-prec-duration-vs-solved.png") { !it.config.precedence }
//	createUtilizationJobsMap("z3-feasible-matrix.png", { it.z3Result.certainlyFeasible }, { true })
//	createUtilizationJobsMap("z3-infeasible-matrix.png", { it.z3Result.certainlyInfeasible }, { true })
//	createUtilizationJobsMap("z3-matrix.png", {
//		it.z3Result.certainlyFeasible || it.z3Result.certainlyInfeasible }, { true }
//	)
//	createUtilizationJobsMap("z3-no-prec-feasible-matrix.png", { it.z3Result.certainlyFeasible }, { !it.config.precedence })
//	createUtilizationJobsMap("z3-no-prec-infeasible-matrix.png", { it.z3Result.certainlyInfeasible }, { !it.config.precedence })
//	createUtilizationJobsMap("z3-no-prec-matrix.png", {
//		it.z3Result.certainlyFeasible || it.z3Result.certainlyInfeasible }, { !it.config.precedence }
//	)
//	utilizationBars(FeasibilityTest.CPLEX, "number of identified problems", "cplex-utilization-vs-solved.png") { true }
//	numJobsBars(FeasibilityTest.CPLEX, "number of identified problems", "cplex-jobs-vs-solved.png") { true }
//	numCoresBars(FeasibilityTest.CPLEX, "number of identified problems", "cplex-cores-vs-solved.png") { true }
//	numCoresBars(FeasibilityTest.CPLEX, "number of identified problems", "cplex-feasible-cores-vs-solved.png") { it.certainlyFeasible }
//	numCoresBars(FeasibilityTest.CPLEX, "number of identified problems", "cplex-infeasible-cores-vs-solved.png") { it.certainlyInfeasible }
//	durationBars(FeasibilityTest.CPLEX, "number of identified problems", "cplex-duration-vs-solved.png") { true }
//	createUtilizationJobsMap("cplex-feasible-matrix.png", { it.cplexResult.certainlyFeasible }, { true })
//	createUtilizationJobsMap("cplex-infeasible-matrix.png", { it.cplexResult.certainlyInfeasible }, { true })
//	createUtilizationJobsMap("cplex-matrix.png", {
//		it.cplexResult.certainlyFeasible || it.cplexResult.certainlyInfeasible }, { true }
//	)

	fun coresClassification(file: String, condition: (FeasibilitySolverResult) -> Boolean) {
		val groupResults = results.flatMap { it.getAll().values }.filter(condition).sortedBy { it.config.numCores }
		val data = mapOf(
			"cores" to groupResults.map { it.config.numCores.toString() },
			"solver" to groupResults.map { it.solver.name }
		)
		data.toDataFrame().groupBy("solver").plot {
			countPlot("cores") {
				fillColor("solver")
				x.axis.name = "number of cores"
				y.axis.name = "number of identified problems"
			}
		}.save(file)
	}

//	coresClassification("cores-vs-classification.png") { it.isSolved }
//	coresClassification("only-prec-cores-vs-classification.png") { it.isSolved && it.config.precedence }
//	coresClassification("no-prec-cores-vs-classification.png") { it.isSolved && !it.config.precedence }
//	utilizationBars(FeasibilityTest.MINISAT, "number of identified problems", "minisat-utilization-vs-solved.png") { !it.config.precedence }
//	numJobsBars(FeasibilityTest.MINISAT, "number of identified problems", "minisat-jobs-vs-solved.png") { !it.config.precedence }
//	numCoresBars(FeasibilityTest.MINISAT, "number of identified problems", "minisat-cores-vs-solved.png") { !it.config.precedence }
//	numCoresBars(FeasibilityTest.MINISAT, "number of identified problems", "minisat-feasible-cores-vs-solved.png") { it.certainlyFeasible && !it.config.precedence }
//	numCoresBars(FeasibilityTest.MINISAT, "number of identified problems", "minisat-infeasible-cores-vs-solved.png") { it.certainlyInfeasible  && !it.config.precedence }
//	durationBars(FeasibilityTest.MINISAT, "number of identified problems", "minisat-duration-vs-solved.png") { true }
//	createUtilizationJobsMap("minisat-feasible-matrix.png", { it.minisatResult!!.certainlyFeasible }, { !it.config.precedence })
//	createUtilizationJobsMap("minisat-infeasible-matrix.png", { it.minisatResult!!.certainlyInfeasible }, { !it.config.precedence })
//	createUtilizationJobsMap("minisat-matrix.png", {
//		it.minisatResult!!.certainlyFeasible || it.minisatResult!!.certainlyInfeasible }, { !it.config.precedence }
//	)

//	run {
//		val sharedResults = results.filter {
//			it.heuristicResult.isSolved && it.z3Result.isSolved && it.cplexResult.isSolved && it.minisatResult?.isSolved == true && it.certainlyFeasible
//		}
//		dataFrameOf(
//			"heuristic" to sharedResults.map { it.heuristicResult.spentSeconds ?: 0.0 },
//			"Z3" to sharedResults.map { it.z3Result.spentSeconds ?: 0.0 },
//			"CPLEX" to sharedResults.map { it.cplexResult.spentSeconds ?: 0.0 },
//			"Minisat" to sharedResults.map { it.minisatResult!!.spentSeconds ?: 0.0 }
//		).gather("heuristic", "Z3", "CPLEX", "Minisat").into("solver", "execution time").plot {
//			boxplot("solver", "execution time") {
//				y.axis.name = "runtime (seconds)"
//			}
//		}.save("spent-seconds-feasible.png")
//	}
//
//	run {
//		val sharedResults = results.filter {
//			it.heuristicResult.isSolved && it.z3Result.isSolved && it.cplexResult.isSolved &&
//					it.minisatResult?.isSolved == true && it.certainlyInfeasible && !it.heuristicResult.failedBoundsTest
//		}
//		dataFrameOf(
//			"Z3" to sharedResults.map { it.z3Result.spentSeconds ?: 0.0 },
//			"CPLEX" to sharedResults.map { it.cplexResult.spentSeconds ?: 0.0 },
//			"Minisat" to sharedResults.map { it.minisatResult!!.spentSeconds ?: 0.0 }
//		).gather("Z3", "CPLEX", "Minisat").into("solver", "execution time").plot {
//			boxplot("solver", "execution time") {
//				y.axis.name = "runtime (seconds)"
//			}
//		}.save("spent-seconds-infeasible.png")
//	}
//
//	run {
//		for (solver in arrayOf(FeasibilityTest.HEURISTIC, FeasibilityTest.Z3_MODEL1, FeasibilityTest.CPLEX, FeasibilityTest.MINISAT)) {
//			dataFrameOf(
//				solver.name to results.mapNotNull { it.getAll()[solver] }.mapNotNull { it.spentSeconds }
//            ).gather(solver.name).into("solver", "execution time").plot {
//				boxplot("solver", "execution time")
//				layout.size = Pair(200, 300)
//				y.axis.max = 60.0
//				y.axis.name = "runtime (seconds)"
//			}.save("spent-seconds-${solver.name}.png")
//		}
//	}
}
