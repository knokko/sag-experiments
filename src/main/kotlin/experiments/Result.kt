package experiments

import generator.pourya.YamlFile
import java.io.File
import java.lang.Double.parseDouble
import java.lang.Integer.parseInt
import java.lang.Long.parseLong
import java.nio.file.Files

class Result(
	val hash: String,
	val exitCode: Int?,
	val rootRating: Double?,
	val rootSafe: Boolean?,
	val feasible: Boolean?,
	val numExtraConstraints: Int?,
	val spentMilliseconds: Long,

	val numCores: Int,
	val utilization: Double,
	val numJobs: Int,
	val numConstraints: Int,
) {
	override fun toString() = "Result(hash=$hash, exitCode=$exitCode, rootRating=$rootRating, rootSafe=$rootSafe, feasible=$feasible, #constraints=$numExtraConstraints, time=${spentMilliseconds}ms, #jobs=$numJobs, #prec=$numConstraints, utilization=$utilization, #cores=$numCores)"

	companion object {
		fun fromFolder(folder: File): Result {
			val config = YamlFile(File("$folder/config.yaml"))
			val results = YamlFile(File("$folder/results.yaml"))
			val numJobs = Files.lines(File("$folder/jobs.csv").toPath()).filter { it.trim().isNotEmpty() }.count() - 1
			val numConstraints = Files.lines(File("$folder/precedence.csv").toPath()).filter { it.trim().isNotEmpty() }.count() - 1
			val numCores = parseInt(config.get("number_of_cores"))

			return Result(
				hash = folder.name,
				exitCode = results.getOptionalInt("exit-code"),
				rootRating = results.getOptionalDouble("root-rating"),
				rootSafe = results.getOptionalBoolean("root-safe"),
				feasible = results.getOptionalBoolean("feasible"),
				numExtraConstraints = results.getOptionalInt("num-extra-constraints"),
				spentMilliseconds = parseLong(results.get("spent-millis")),
				numCores = numCores,
				utilization = parseDouble(config.get("utilization")) / numCores,
				numJobs = numJobs.toInt(),
				numConstraints = numConstraints.toInt()
			)
		}

		fun getAll(): List<Result> {
			val folders = RESULTS_FOLDER.listFiles() ?: return emptyList()
			return folders.map(::fromFolder)
		}
	}
}
