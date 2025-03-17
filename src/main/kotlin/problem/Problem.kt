package problem

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files

class Problem(jobsFile: File, precedenceFile: File?, val numCores: Int) {

	val jobs = ArrayList<Job>()
	val precedenceConstraints = ArrayList<PrecedenceConstraint>()

	init {
		Files.readAllLines(jobsFile.toPath()).forEach {
			val job = Job.fromLine(it)
			if (job != null) jobs.add(job)
		}
		jobsFile.delete()

		if (precedenceFile != null) {
			Files.readAllLines(precedenceFile.toPath()).forEach {
				val constraint = PrecedenceConstraint.fromLine(it)
				if (constraint != null) precedenceConstraints.add(constraint)
			}
			precedenceFile.delete()
		}
	}

	fun setSize(
		desiredDuration: Long?, desiredNumJobs: Int, minNumJobs: Int, maxNumJobs: Int,
		desiredLastDeadline: Long?, minLastDeadline: Long, maxLastDeadline: Long
	): Boolean {
		jobs.sortBy { it.arrivalMax }
		if (jobs.size < minNumJobs) return false
		if (jobs.size > maxNumJobs) {
			while (jobs.size > desiredNumJobs) jobs.removeAt(jobs.size - 1)
			val jobSet = mutableSetOf<Pair<Int, Int>>()
			for (job in jobs) jobSet.add(Pair(job.taskID, job.jobID))
			precedenceConstraints.removeIf {
				!jobSet.contains(Pair(it.fromTask, it.fromJob)) || !jobSet.contains(Pair(it.toTask, it.toJob))
			}
		}

		val lastDeadline = jobs.maxOf { it.deadline }
		if (lastDeadline < minLastDeadline || lastDeadline > maxLastDeadline) {
			val scale = desiredLastDeadline!!.toDouble() / lastDeadline
			for (job in jobs) job.scaleTimeInstants(scale)
			val actualDuration = jobs.sumOf { it.costMax }.toDouble() / jobs.size
			val durationScale = desiredDuration!! / actualDuration
			for (job in jobs) job.scaleExecutionTimes(durationScale)
		}
		return true
	}

	fun write(jobsFile: File, precedenceFile: File) {
		val jobsWriter = PrintWriter(jobsFile)
		jobsWriter.println("Task ID,Job ID,Arrival min,Arrival max,Cost min,Cost max,Deadline,Priority")
		for (job in jobs) job.run {
			jobsWriter.println("$taskID, $jobID, $arrivalMin, $arrivalMax, $costMin, $costMax, $deadline, $priority")
		}
		jobsWriter.flush()
		jobsWriter.close()

		val precedenceWriter = PrintWriter(precedenceFile)
		precedenceWriter.println("From TID,From JID,To TID,To JID")
		for (precedenceConstraint in precedenceConstraints) precedenceConstraint.run {
			precedenceWriter.println("$fromTask, $fromJob, $toTask, $toJob")
		}
		precedenceWriter.flush()
		precedenceWriter.close()
	}
}
