/*
 * Copyright 2020 Florian Steitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.fsteitz.kostack

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * @author Florian Steitz (florian@fsteitz.com)
 */
object Kostack {

  private const val DUMP_COUNT = 6                                // Iterationen
  private const val DUMP_DELAY_S = 5                              // In Sekunden
  private const val DUMP_DELAY_MS = DUMP_DELAY_S * 1000           // In Millisekunden
  private const val DUMP_FILE_PATTERN = "\\thread_%s_%s__%s.dump" // Example: thread_9672_1594488582405__1.dump
  private const val THREAD_EXECUTION_DELAY = 200L   // 200ms

  fun createThreadDumps(appParams: AppParams) {
    val threadPool = Executors.newCachedThreadPool()
    println("JDK-Home: ${appParams.jdkBin}")

    for (processSearchText in appParams.processSearchTextList) {
      threadPool.execute { createThreadDumps(processSearchText, appParams) }
      Thread.sleep(THREAD_EXECUTION_DELAY)
    }

    threadPool.shutdown()
    threadPool.awaitTermination(5, TimeUnit.MINUTES)

    println("FERTIG: ThreadDumps wurden erzeugt!")
  }

  fun createThreadDumps(processSearchText: String, appParams: AppParams) {
    try {
      val jstackPattern = "${appParams.jdkBin}${File.separator}jstack -l %s"
      val pidList = findPIDs(processSearchText)

      if (pidList.isEmpty()) {
        println("FEHLER: Es konnte kein aktiver Prozess fuer '${processSearchText}' ermittelt werden. Es wurden keine ThreadDumps erstellt!")
        exitProcess(-1)
      }

      println("Alle fuer '${processSearchText}' ermittelten PIDs: $pidList")

      for (pid in pidList) {
        createThreadDumps(pid, jstackPattern, appParams.dumpFileDir)
      }
    } catch (e: Exception) {
      System.err.println("FEHLER: Die Datei konnte nicht generiert werden")
      e.printStackTrace()
    }
  }

  @Throws(IOException::class, InterruptedException::class)
  private fun createThreadDumps(pid: String, jstackPattern: String, dumpFileBasePath: String) {
    println("Erzeuge insgesamt $DUMP_COUNT ThreadDumps fuer PID '$pid'...")

    // Zähler beginnt bei 1 um nutzerfreundlicher zu sein
    for (i in 1..DUMP_COUNT) {
      val dumpFilePath = createThreadDump(pid, i, jstackPattern, dumpFileBasePath) ?: exitProcess(-1)
      println("ThreadDump $i von $DUMP_COUNT fuer PID '$pid' erzeugt: ${dumpFilePath} - In $DUMP_DELAY_S Sek. wird der naechste erzeugt")
      Thread.sleep(DUMP_DELAY_MS.toLong())
    }
  }

  @Throws(IOException::class)
  private fun createThreadDump(pid: String, dumpIndex: Int, jstackPattern: String, dumpFileBasePath: String): Path? {
    var dumpFilePath: Path? = null
    exec(String.format(jstackPattern, pid)) exec@{ stdin ->
      try {
        var line: String?
        val path = Path.of(String.format(dumpFileBasePath + DUMP_FILE_PATTERN, pid, System.currentTimeMillis(), dumpIndex))

        if (!path.toFile().createNewFile()) {
          println("ThreadDump fuer PID '$pid' konnte nicht erzeugt werden")
          return@exec
        }

        while (stdin.readLine().also { line = it } != null) {
          Files.writeString(path, line + '\n', StandardOpenOption.APPEND)
        }

        stdin.close()
        dumpFilePath = path
      } catch (e: IOException) {
        System.err.println("FEHLER: ThreadDump konnte nicht erzeugt werden")
        e.printStackTrace()
      }
    }

    return dumpFilePath
  }

  @Throws(IOException::class)
  private fun findPIDs(processSearchText: String): List<String> {
    val pids = mutableListOf<String>()

    println("Ermittle PIDs fuer '$processSearchText'...")
    exec("tasklist /v /fo csv") { stdin ->
      var line: String?

      try {
        while (stdin.readLine().also { line = it } != null) {
          val columns = line?.split(",") ?: emptyList()

          if (columns.size < 2) {
            System.err.println("FEHLER: Format von 'tasklist' ist ungueltig")
          } else if (columns[columns.size - 1].contains(processSearchText)) {
            val pid = columns[1].replace("\"".toRegex(), "")

            println("PID fuer '$processSearchText' ermittelt: $pid")
            pids.add(pid)
          }
        }
      } catch (e: IOException) {
        System.err.println("FEHLER: ThreadDump konnte nicht erzeugt werden")
        e.printStackTrace()
      }
    }

    return pids
  }

  @Throws(IOException::class)
  private fun exec(command: String, stdinConsumer: (BufferedReader) -> Unit) {
    val process = Runtime.getRuntime().exec(command)
    val stdin = BufferedReader(InputStreamReader(process.inputStream))
    val stderr = BufferedReader(InputStreamReader(process.errorStream))
    var line: String?

    // Read the output from the command
    stdinConsumer(stdin)
    stdin.close()

    // Read any errors from the attempted command
    while (stderr.readLine().also { line = it } != null) {
      println(line)
    }

    stderr.close()
  }
}
