package experiments

import java.io.File
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
		entries["num_sets"] = amount.toString()
		entries["generate_dags"] = "true"
		entries["generate_dot"] = "false"
		entries["generate_job_sets"] = "true"
	}

	fun write(destination: File) {
		Files.write(destination.toPath(), entries.entries.map { "${it.key}: ${it.value}" })
	}
}
