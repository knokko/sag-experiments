package problem

import java.lang.Integer.parseInt
import java.lang.Long.parseLong
import kotlin.math.roundToLong

class Job(
	val taskID: Int,
	val jobID: Int,
	var arrivalMin: Long,
	var arrivalMax: Long,
	var costMin: Long,
	var costMax: Long,
	var deadline: Long,
	val priority: Int,
) {

	fun scale(s: Double) {
		arrivalMin = (s * arrivalMin).roundToLong()
		arrivalMax = (s * arrivalMax).roundToLong()
		costMin = (s * costMin).roundToLong()
		costMax = (s * costMax).roundToLong()
		deadline = (s * deadline).roundToLong()
	}

	companion object {
		fun fromLine(line: String): Job? {
			val split = line.split(",")
			if (split.size != 8) return null

			return try {
				Job(
					taskID = parseInt(split[0]),
					jobID = parseInt(split[1]),
					arrivalMin = parseLong(split[2]),
					arrivalMax = parseLong(split[3]),
					costMin = parseLong(split[4]),
					costMax = parseLong(split[5]),
					deadline = parseLong(split[6]),
					priority = parseInt(split[7])
				)
			} catch (isHeader: NumberFormatException) {
				null
			}
		}
	}
}
