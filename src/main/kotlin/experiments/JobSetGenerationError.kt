package experiments

sealed class JobSetGenerationError {
	abstract fun print()
}

data object JobGenerationTimeout : JobSetGenerationError() {
	override fun print() {
		println("Job generator timed out")
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
