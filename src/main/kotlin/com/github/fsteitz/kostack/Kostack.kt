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

import com.github.fsteitz.kostack.command.CommandExecutor
import com.github.fsteitz.kostack.finder.PIDFinder
import java.io.File
import java.io.IOException
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

  private const val DUMP_COUNT = 6                                // Dump files per process
  private const val DUMP_DELAY_S = 5                              // In seconds
  private const val DUMP_DELAY_MS = DUMP_DELAY_S * 1000L          // In millis
  private const val DUMP_FILE_PATTERN = "\\thread_%s_%s__%s.dump" // Example: thread_9672_1594488582405__1.dump

  private const val THREAD_EXECUTION_DELAY = 200L   // 200ms

  fun createThreadDumps(appParams: AppParams, pidFinder: PIDFinder) {
    val threadPool = Executors.newCachedThreadPool()
    println("Ort von jstack: ${appParams.jstackLocation}")

    for (processSearchText in appParams.processSearchTextList) {
      threadPool.execute { createThreadDumps(processSearchText, appParams, pidFinder) }
      Thread.sleep(THREAD_EXECUTION_DELAY)
    }

    threadPool.shutdown()
    threadPool.awaitTermination(5, TimeUnit.MINUTES)

    println("FERTIG: ThreadDumps wurden erzeugt!")
  }

  fun createThreadDumps(processSearchText: String, appParams: AppParams, pidFinder: PIDFinder) {
    try {
      val jstackPattern = "${appParams.jstackLocation}${File.separator}jstack -l %s"
      val pidList = findPIDs(pidFinder, processSearchText)

      if (pidList.isEmpty()) {
        println("FEHLER: Es konnte kein aktiver Prozess fuer '${processSearchText}' ermittelt werden. Es werden keine ThreadDumps dafuer erstellt!")
        return
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

    // Counter starts at 1 to make the console output more readable
    for (i in 1..DUMP_COUNT) {
      val dumpFilePath = createThreadDump(pid, i, jstackPattern, dumpFileBasePath) ?: exitProcess(-1)
      println("ThreadDump $i von $DUMP_COUNT fuer PID '$pid' erzeugt: ${dumpFilePath} - In $DUMP_DELAY_S Sek. wird der naechste erzeugt")
      Thread.sleep(DUMP_DELAY_MS)
    }
  }

  @Throws(IOException::class)
  private fun createThreadDump(pid: String, dumpIndex: Int, jstackPattern: String, dumpFileBasePath: String): Path? {
    var dumpFilePath: Path? = null
    CommandExecutor.execute(String.format(jstackPattern, pid)) exec@{ stdin ->
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
  private fun findPIDs(pidFinder: PIDFinder, processSearchText: String): List<String> {
    println("Ermittle PIDs fuer '$processSearchText'...")
    return pidFinder.find(processSearchText)
  }

}
