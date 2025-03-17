package experiments.reconfiguration

import generator.pourya.PouryaGeneratorConfig
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

val reconfigurationPool = Executors.newFixedThreadPool(8)!!

fun main() {
	for (precedence in arrayOf(false, true)) {
		generate(precedence = precedence)
		for (utilization in arrayOf(0.4, 0.8, 0.9, 0.95, 0.98)) {
			generate(utilization = utilization, precedence = precedence)
		}
		for (numCores in arrayOf(1, 2, 4)) {
			generate(numCores = numCores, precedence = precedence)
		}
		for (numTasks in arrayOf(3, 5)) {
			generate(numTasks = numTasks, precedence = precedence)
		}
		for (numJobs in arrayOf(100, 300, 3000, 10_000)) {
			generate(numJobs = numJobs, precedence = precedence)
		}
		for (jitter in arrayOf(0.0, 0.1, 0.5)) {
			generate(jitter = jitter, precedence = precedence)
		}
	}
	reconfigurationPool.shutdown()
}

private fun generate(
	utilization: Double = 0.6,
	numCores: Int = 3,
	numTasks: Int = 4,
	numJobs: Int = 1000,
	jitter: Double = 0.2,
	precedence: Boolean,
) {
	val m = 100
	val baseConfig = File("reconfiguration-config.yaml")

	val minNumJobs = (0.9 * numJobs).roundToInt()
	val maxNumJobs = (1.1 * numJobs).roundToInt()

	reconfigurationPool.submit {
		PouryaGeneratorConfig(
			baseConfig = baseConfig,
			numCores = numCores,
			numTasks = numTasks,
			desiredNumJobs = numJobs,
			minNumJobs = minNumJobs,
			maxNumJobs = maxNumJobs,
			addPrecedenceConstraints = precedence,
			desiredJobLengths = null,
			desiredLastDeadline = null,
			minLastDeadline = 0L,
			maxLastDeadline = Long.MAX_VALUE / 10L,
			utilization = utilization,
			jitter = jitter,
		).generateReconfigurationProblems(m)
	}
}
