package experiments.jobs

import experiments.NP_TEST
import experiments.reconfiguration.reconfigurationPool
import java.io.File
import java.io.PrintWriter
import java.lang.Integer.parseInt
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun main() {
	val experimentsFolder = File("./job-experiments")
	val futures = ConcurrentLinkedQueue<Future<*>>()
	val files = experimentsFolder.listFiles()!!.filter { it.name.endsWith(".csv") }
	val submissionCounter = AtomicInteger()
	println("Expecting ${files.size} files")
	for (file in files) {
		submissionCounter.incrementAndGet()
		futures.add(reconfigurationPool.submit {
			try {
				val testNumber = run {
					val startIndex = file.name.lastIndexOf('_') + 1
					parseInt(file.name.substring(startIndex until file.name.length - 4))
				}
				val logger = PrintWriter("$experimentsFolder/output$testNumber-initial.log")

				val alreadySchedulableProcess = Runtime.getRuntime().exec(
					arrayOf(NP_TEST.absolutePath, file.absolutePath, "-m", "3")
				)

				if (alreadySchedulableProcess.waitFor(5L, TimeUnit.MINUTES)) {
					val firstLine = alreadySchedulableProcess.inputReader().readLine()
					alreadySchedulableProcess.destroy()

					val indexComma = firstLine.indexOf(",  ")
					val startIndex = indexComma + 3
					val endIndex = firstLine.indexOf(',', startIndex)
					val isSchedulable = parseInt(firstLine.substring(startIndex, endIndex)) == 1
					if (isSchedulable) {
						logger.println("The problem is already schedulable")
						println("Test $testNumber was already schedulable")
						return@submit
					}
				} else {
					alreadySchedulableProcess.destroy()
					logger.println("Initial exploration timed out after 5 minutes")
					println("Initial exploration of $testNumber timed out")
					return@submit
				}

				logger.println("Starting reconfiguration threads...")
				logger.flush()
				logger.close()

				println("Start reconfiguring test $testNumber...")

				for ((methodName, methodNumber) in arrayOf(Pair("fast", 2), Pair("instant", 3))) {
					submissionCounter.incrementAndGet()
					futures.add(reconfigurationPool.submit {
						try {
							val logger = PrintWriter("$experimentsFolder/output$testNumber-$methodName.log")
							val startTime = System.nanoTime()
							val deadline = startTime + 30L * 60L * 1_000_000_000L
							val reconfigureProcess = Runtime.getRuntime().exec(arrayOf(
								NP_TEST.absolutePath, file.absolutePath, "-m", "3", "--reconfigure",
								"--reconfigure-skip-rating-graph", "--reconfigure-random-trials",
								"--reconfigure-cut-enforcement", methodNumber.toString()
							))
							println("started case $testNumber $methodName")

							val input = reconfigureProcess.inputReader()
							val errors = reconfigureProcess.errorReader()
							while (reconfigureProcess.isAlive && System.nanoTime() < deadline) {
								sleep(100)
								while (input.ready()) logger.println(input.readLine())
								while (errors.ready()) logger.println("ERROR: ${errors.readLine()}")
							}
							while (input.ready()) logger.println(input.readLine())
							while (errors.ready()) logger.println("ERROR: ${errors.readLine()}")

							if (reconfigureProcess.isAlive) {
								reconfigureProcess.destroy()
								logger.println("<ERROR> TIMEOUT")
								println("Case $testNumber $methodName timed out")
							}

							val spentSeconds = (System.nanoTime() - startTime) / 1000_000_000L
							logger.println("Finished after $spentSeconds seconds")
							println("Finished case $testNumber $methodName after $spentSeconds seconds")
							logger.flush()
							logger.close()
						} catch (failed: Throwable) {
							failed.printStackTrace()
						} finally {
							submissionCounter.decrementAndGet()
						}
					})
				}
			} catch (failed: Throwable) {
				failed.printStackTrace()
			} finally {
				submissionCounter.decrementAndGet()
			}
		})
	}

	while (submissionCounter.get() != 0) {
		sleep(1000)
		println("submission counter is ${submissionCounter.get()}")
	}
	reconfigurationPool.shutdown()
	println("Shut down the thread pool")
}
