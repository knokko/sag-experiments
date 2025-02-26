package generator

class GeneratorConfig(
	val numCores: Int,
	val numJobs: Int,
	val numPrecedenceConstraints: Int,
	val lastDeadline: Long,
	val maxPriority: Int,
	val minUtilization: Double,
	val maxUtilization: Double
) {
}
