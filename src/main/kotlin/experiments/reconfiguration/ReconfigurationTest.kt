package experiments.reconfiguration

class ReconfigurationTest(
	val name: String,
	val needsRatingOrdering: Boolean,
	val extraArguments: Array<String>
) {
	companion object {
		val ALL = run {
			val all = mutableListOf<ReconfigurationTest>()
			for (useRating in arrayOf(true, false)) {
				for ((cutEnforcement, rawEnforcement) in arrayOf(
					Pair("traditional", 0), Pair("slow-safe", 1), Pair("fast-safe", 2), Pair("total", 3)
				)) {
					for ((minimization, rawMinimization) in arrayOf(
						Pair("tail", emptyArray()),
						Pair("head", arrayOf("--reconfigure-reverse-tail-minimizer")),
						Pair("random", arrayOf("--reconfigure-random-trials"))
					)) {
						all.add(ReconfigurationTest(
							name = "${if (useRating) "rating" else "scratch"} $cutEnforcement $minimization",
							needsRatingOrdering = useRating,
							extraArguments = arrayOf("--reconfigure-cut-enforcement-strategy") +
									arrayOf(rawEnforcement.toString()) + rawMinimization
						))
					}
				}
			}
			all
		}
	}
}
