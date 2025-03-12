package experiments.feasibility

import generator.knokko.KnokkoGeneratorConfig
import generator.pourya.PouryaGeneratorConfig
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun main() {
	val m = 1
	val baseConfig = File("feasibility-config.yaml")
	for (numJobs in arrayOf(5, 10, 20, 40, 80, 300, 1000, 5000)) {
		val minNumJobs = (0.9 * numJobs).roundToInt()
		val maxNumJobs = (1.1 * numJobs).roundToInt()
		for (numCores in arrayOf(1, 3, 5)) {
			for (precedence in arrayOf(true, false)) {
				for (jobLength in arrayOf(5L, 20L, 100L, 5000L)) {
					val lastDeadline = jobLength * numJobs / numCores
					val minLastDeadline = (0.9 * lastDeadline).roundToLong()
					val maxLastDeadline = (1.1 * lastDeadline).roundToLong()
					for (utilization in arrayOf(0.3, 0.7, 0.9)) {
						PouryaGeneratorConfig(
							baseConfig = baseConfig,
							numCores = numCores,
							numTasks = 4,
							desiredNumJobs = numJobs,
							minNumJobs = minNumJobs,
							maxNumJobs = maxNumJobs,
							addPrecedenceConstraints = precedence,
							desiredJobLengths = jobLength,
							desiredLastDeadline = lastDeadline,
							minLastDeadline = minLastDeadline,
							maxLastDeadline = maxLastDeadline,
							utilization = utilization
						).generateFeasibilityProblems(m)
					}

					KnokkoGeneratorConfig(
						numCores = numCores,
						numJobs = numJobs,
						numPrecedenceConstraints = if (precedence) numJobs / 4 else 0,
						desiredJobLengths = jobLength,
						lastDeadline = lastDeadline,
						maxPriority = numJobs / 2,
						minUtilization = 0.95,
						maxUtilization = 1.05
					).generateFeasibilityProblems(m)
				}
			}
		}
	}
}
