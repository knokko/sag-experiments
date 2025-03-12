package generator.knokko

import experiments.FEASIBILITY_RESULTS_FOLDER
import java.io.File
import kotlin.math.roundToInt

class KnokkoGeneratorConfig(
	val numCores: Int,
	val numJobs: Int,
	val numPrecedenceConstraints: Int,
	val desiredJobLengths: Long,
	val lastDeadline: Long,
	val maxPriority: Int,
	val minUtilization: Double,
	val maxUtilization: Double
) {

	fun generateFeasibilityProblems(amount: Int) {
		val utilization = (50 * (minUtilization + maxUtilization)).roundToInt()
		val hasPrecedence = if (numPrecedenceConstraints > 0) "prec" else "no-prec"
		val outerFolder = File(
			"$FEASIBILITY_RESULTS_FOLDER/${numJobs}jobs_${utilization}util_" +
					"${numCores}cores_${hasPrecedence}_${desiredJobLengths}durations"
		)
		for (counter in 0 until amount) {
			val innerFolder = File("$outerFolder/case$counter")
			innerFolder.mkdirs()
			generate(this, File("$innerFolder/jobs.csv"), File("$innerFolder/precedence.csv"))
		}
	}
}
