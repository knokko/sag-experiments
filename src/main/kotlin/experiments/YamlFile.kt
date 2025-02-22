package experiments

import java.io.File
import java.lang.Double.parseDouble
import java.lang.Integer.parseInt
import java.nio.file.Files

class YamlFile(source: File) {

	private val entries = mutableMapOf<String, String>()

	init {
		Files.lines(source.toPath()).forEach { rawLine ->
			val properLine = rawLine.trim()
			if (!properLine.startsWith("#") && properLine.isNotEmpty()) {
				val pair = properLine.split(": ")
				if (pair.size != 2) throw IllegalArgumentException("Unexpected line $rawLine")
				entries[pair[0]] = pair[1]
			}
		}
	}

	fun prepareJobGeneration(outputFolder: File, amount: Int) {
		entries["path"] = "\"${outputFolder.absolutePath}\""
		entries["output_format"] = "csv"
		entries["num_sets"] = amount.toString()
		entries["generate_dags"] = "true"
		entries["generate_dot"] = "false"
		entries["generate_job_sets"] = "true"
		entries["verbose"] = "0"
	}

	fun write(destination: File) {
		Files.write(destination.toPath(), entries.entries.map { "${it.key}: ${it.value}" })
	}

	fun get(key: String) = entries[key]

	private fun getOptional(key: String): String? {
		val raw = entries[key] ?: return null
		return if (raw == "unknown" || raw == "null") null else raw
	}

	fun getOptionalBoolean(key: String): Boolean? {
		val raw = getOptional(key) ?: return null
		return if (raw == "true") true else if (raw == "false") false else throw IllegalArgumentException("Invalid boolean $raw")
	}

	fun getOptionalInt(key: String): Int? {
		val raw = getOptional(key) ?: return null
		return parseInt(raw)
	}

	fun getOptionalDouble(key: String): Double? {
		val raw = getOptional(key) ?: return null
		return parseDouble(raw)
	}
}
