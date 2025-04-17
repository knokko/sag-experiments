package experiments.feasibility

class FeasibilityTest(
	val name: String,
	val arguments: Array<String>,
	val ordinal: Int
) {
	companion object {
		val HEURISTIC = FeasibilityTest("heuristic", arrayOf("--feasibility-exact"), 0)
		val Z3_MODEL1 = FeasibilityTest("z3", arrayOf("--feasibility-z3", "1"), 1)
		val Z3_MODEL2 = FeasibilityTest("z3-model2", arrayOf("--feasibility-z3", "2"), 2)
		val CPLEX = FeasibilityTest("cplex", arrayOf("--feasibility-cplex"), 3)
		val MINISAT = FeasibilityTest("minisat", arrayOf("--feasibility-minisat"), 4)
	}
}
