package experiments.reconfiguration

import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
	val outerFolders = File("/home/knokko/thesis/reconfiguration-results").listFiles()!!
	val results = Collections.synchronizedList(ArrayList<ReconfigurationResult>())
	val threadPool = Executors.newFixedThreadPool(8)
	for (outerFolder in outerFolders) {
		if (!outerFolder.isDirectory) continue
		for (innerFolder in outerFolder.listFiles()!!) {
			if (!innerFolder.isDirectory) continue
			threadPool.submit {
				results.add(ReconfigurationResult.parse(innerFolder))
			}
		}
	}
	threadPool.shutdown()
	if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) throw Error("Timed out")

	println("${results.count { it.graphPath != null }} graph results and ${results.count { it.scratchPath != null }} scratch results out of ${results.size}")
}
