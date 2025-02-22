package experiments

import java.io.File

class JobSetSource(val config: YamlFile, val amount: Int)

sealed class JobSetGenerationError {
	abstract fun print()
}

data object JobGenerationTimeout : JobSetGenerationError() {
	override fun print() {
		println("Job generation timed out")
	}
}

class JobGenerationNonZero(
	private val exitCode: Int,
	private val output: List<String>,
	private val errors: List<String>
) : JobSetGenerationError() {
	override fun print() {
		println("Job generator process failed with exit code $exitCode")
		println("Standard output:")
		for (line in output) println("  $line")
		println("Standard error:")
		for (line in errors) println("  $line")
	}
}

class JobSet(val configFile: File, val numCores: Int, val jobFile: File, val precedenceFile: File)

class JobSetEvaluationOutput(
	val jobSet: JobSet,
	val spentMilliseconds: Long,
	val exitCode: Int?,
	val output: List<String>,
	val errors: List<String>
)
