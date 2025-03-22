package experiments.feasibility

import java.io.File
import java.lang.Double.parseDouble
import java.lang.Integer.parseInt
import java.nio.file.Files

class FeasibilitySolverResult(
	val solver: FeasibilityTest,
	val config: FeasibilityConfig,
	val certainlyFeasible: Boolean,
	val certainlyInfeasible: Boolean,
	val spentSeconds: Double?,
	val timedOut: Boolean,
	val processTimedOut: Boolean,
) {
	companion object {
		fun parse(solver: FeasibilityTest, config: FeasibilityConfig, file: File): FeasibilitySolverResult {
			var certainlyFeasible = false
			var certainlyInfeasible = false
			var timedOut = false
			var processTimedOut = false
			var spentSeconds: Double? = null
			for (line in Files.lines(file.toPath())) {
				if (line.contains("is feasible.")) certainlyFeasible = true
				if (line.contains("is infeasible")) certainlyInfeasible = true
				if (line.contains("process timed out")) processTimedOut = true
				else if (line.contains("timed out")) timedOut = true
				val indexSeconds = line.indexOf(" seconds")
				if (indexSeconds != -1) {
					val startIndex = line.lastIndexOf(" ", indexSeconds - 1) + 1
					spentSeconds = parseDouble(line.substring(startIndex, indexSeconds))
				}
			}

			if (timedOut && spentSeconds != null) throw Error("Failed to parse $file")
			return FeasibilitySolverResult(
				solver,
				config = config,
				certainlyFeasible = certainlyFeasible,
				certainlyInfeasible = certainlyInfeasible,
				spentSeconds,
				timedOut = timedOut,
				processTimedOut = processTimedOut
			)
		}
	}
}

class FeasibilityConfig(
	val numJobs: Int,
	val utilization: Int,
	val numCores: Int,
	val precedence: Boolean,
	val jobLength: Int
) {
	companion object {
		fun parse(folder: File): FeasibilityConfig {
			val numJobs = run {
				val endIndex = folder.path.indexOf("jobs")
				val startIndex = folder.path.lastIndexOf(File.separator, endIndex) + 1
				parseInt(folder.path.substring(startIndex, endIndex))
			}
			val utilization = run {
				val endIndex = folder.path.indexOf("util")
				val startIndex = folder.path.lastIndexOf("_", endIndex) + 1
				parseInt(folder.path.substring(startIndex, endIndex))
			}
			val numCores = run {
				val endIndex = folder.path.indexOf("cores")
				parseInt(folder.path.substring(endIndex - 1, endIndex))
			}
			val precedence = !folder.path.contains("no-prec")
			val jobLength = run {
				val endIndex = folder.path.indexOf("durations")
				val startIndex = folder.path.lastIndexOf("_", endIndex) + 1
				parseInt(folder.path.substring(startIndex, endIndex))
			}
			return FeasibilityConfig(
				numJobs = numJobs, utilization = utilization, numCores = numCores,
				precedence = precedence, jobLength = jobLength
			)
		}
	}
}

class FeasibilityCaseResult(
	val folder: File,
	val config: FeasibilityConfig,
	val heuristicResult: FeasibilitySolverResult,
	val z3Result: FeasibilitySolverResult,
	val cplexResult: FeasibilitySolverResult,
	val minisatResult: FeasibilitySolverResult?
) {
	private val all = mutableMapOf<FeasibilityTest, FeasibilitySolverResult>()

	init {
		all[FeasibilityTest.HEURISTIC] = heuristicResult
		all[FeasibilityTest.Z3_MODEL1] = z3Result
		all[FeasibilityTest.CPLEX] = cplexResult
		if (!config.precedence) all[FeasibilityTest.MINISAT] = minisatResult!!
	}

	fun getAll() = all

	val certainlyFeasible = all.values.any { it.certainlyFeasible }
	val certainlyInfeasible = all.values.any { it.certainlyInfeasible }

	init {
		if (certainlyFeasible && certainlyInfeasible) throw Error("Invalid result $folder")
	}

	companion object {
		fun parse(folder: File): FeasibilityCaseResult {
			val config = FeasibilityConfig.parse(folder)
			return FeasibilityCaseResult(
				folder = folder,
				config = config,
				heuristicResult = FeasibilitySolverResult.parse(
					FeasibilityTest.HEURISTIC, config, File("$folder/results-heuristic.log")
				),
				z3Result = FeasibilitySolverResult.parse(
					FeasibilityTest.Z3_MODEL1, config,
					File("$folder/results-z3-model1.log")
				),
				cplexResult = FeasibilitySolverResult.parse(
					FeasibilityTest.CPLEX, config,
					File("$folder/results-cplex.log")
				),
				minisatResult = if (config.precedence) null else FeasibilitySolverResult.parse(
					FeasibilityTest.MINISAT, config,
					File("$folder/results-minisat.log")
				)
			)
		}
	}
}
