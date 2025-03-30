package experiments.reconfiguration

import java.io.File

enum class BoringReconfigurationResult {
	TimedOut,
	CertainlySchedulable,
	CertainlyInfeasible;

	companion object {
		fun parse(folder: File): BoringReconfigurationResult {
			val ratingFile = File("$folder/rating-job-ordering.out")
			var certainlySchedulable = false
			var timedOut = false
			var certainlyInfeasible = false
			ratingFile.forEachLine { line ->
				if (line == "The given problem is already schedulable using our scheduler.") certainlySchedulable = true
				if (line.contains("problem is infeasible.")) certainlyInfeasible = true
				if (line == "Rating graph construction timed out") timedOut = true
			}
			if (certainlySchedulable && !timedOut && !certainlyInfeasible) return CertainlySchedulable
			if (timedOut && !certainlySchedulable && !certainlyInfeasible) return TimedOut
			if (certainlyInfeasible && !certainlySchedulable && !timedOut) return CertainlyInfeasible
			throw IllegalArgumentException("Unexpected $folder")
		}
	}
}
