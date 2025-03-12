package experiments.feasibility

import generator.knokko.KnokkoGeneratorConfig
import generator.pourya.PouryaGeneratorConfig
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun main() {
	val threads = mutableListOf<Thread>()
	val m = 1
	val baseConfig = File("feasibility-config.yaml")
	for (numJobs in arrayOf(40, 80, 300, /*1000, 5000*/)) {
		val minNumJobs = (0.9 * numJobs).roundToInt()
		val maxNumJobs = (1.1 * numJobs).roundToInt()
		threads.add(Thread {
			for (precedence in arrayOf(true, false)) {
				for (jobLength in arrayOf(5L, 20L, 100L, 5000L)) {
					for (numCores in arrayOf(1, 2, 3, 4, 5)) {
						for (utilization in arrayOf(0.3, 0.7, 0.9)) {

							// These two combinations are too difficult for the generator of Pourya
							if (utilization == 0.9 && numCores >= 4) continue
							if (utilization == 0.7 && numCores >= 5) continue

							val lastDeadline = (jobLength * numJobs / numCores / utilization).roundToLong()
							val minLastDeadline = (0.9 * lastDeadline).roundToLong()
							val maxLastDeadline = (1.1 * lastDeadline).roundToLong()
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

						val knokkoUtilization = mutableListOf(Pair(0.95, 1.05))
						if (numCores >= 4) knokkoUtilization.add(Pair(0.85, 0.95))
						if (numCores >= 5) knokkoUtilization.add(Pair(0.65, 0.75))
						for ((minUtilization, maxUtilization) in knokkoUtilization) {
							KnokkoGeneratorConfig(
								numCores = numCores,
								numJobs = numJobs,
								numPrecedenceConstraints = if (precedence) numJobs / 4 else 0,
								desiredJobLengths = jobLength,
								lastDeadline = jobLength * numJobs / numCores,
								maxPriority = numJobs / 2,
								minUtilization = minUtilization,
								maxUtilization = maxUtilization
							).generateFeasibilityProblems(m)
						}
					}
				}
			}
		})
	}

	for (thread in threads) thread.start()
	for (thread in threads) thread.join()
}
