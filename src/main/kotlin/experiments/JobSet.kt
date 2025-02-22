package experiments

import java.io.File

class JobSet(val configFile: File, val jobFile: File, val precedenceFile: File)

class JobSetEvaluationOutput(
	val jobSet: JobSet,
	val exitCode: Int?,
	val output: List<String>,
	val errors: List<String>
)
