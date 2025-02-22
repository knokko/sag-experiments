package experiments

private val allResults = Result.getAll()

fun main() {
	val results = allResults.filter { it.utilization > 0.7 }
	println("Selected ${results.size} / ${allResults.size}")
	for (result in results) println(result)
}
