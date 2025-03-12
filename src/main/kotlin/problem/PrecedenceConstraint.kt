package problem

import java.lang.Integer.parseInt
import java.lang.Long.parseLong

class PrecedenceConstraint(
	val fromTask: Int,
	val fromJob: Int,
	val toTask: Int,
	val toJob: Int,
	val minSuspension: Long,
	val maxSuspension: Long,
) {

	companion object {
		fun fromLine(line: String): PrecedenceConstraint? {
			val split = line.split(",")
			if (split.size < 4) return null

			return try {
				PrecedenceConstraint(
					fromTask = parseInt(split[0]),
					fromJob = parseInt(split[1]),
					toTask = parseInt(split[2]),
					toJob = parseInt(split[3]),
					minSuspension = if (split.size > 5) parseLong(split[4]) else 0,
					maxSuspension = if (split.size > 5) parseLong(split[5]) else 0,
					// TODO maybe support dispatch ordering constraints
				)
			} catch (isHeader: NumberFormatException) {
				null
			}
		}
	}
}
