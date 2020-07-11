package com.github.fsteitz.kostack

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.ArrayList
import java.util.List
import java.util.function.Consumer

object Main {

  private const val DUMP_COUNT = 6                        // Iterationen
  private const val DUMP_DELAY_S = 5                      // In Sekunden
  private const val DUMP_DELAY_MS = DUMP_DELAY_S * 1000   // In Millisekunden

  private const val PROCESS_NAME_PART = "SHD ECORO"
  private const val DUMP_FILE_PATTERN = "\\thread_%s_%s__%s.dump"

  @JvmStatic
  fun main(args: Array<String>) {
    if (args.size == 0) {
      System.err.println("FEHLER: Es wurden keine Programmparameter uebergeben")
      System.exit(-1)
    } else if (args.size != 2) {
      System.err.println("FEHLER: Programmparameter sind ungültig")
      System.exit(-1)
    }

    createThreadDumps(args[0], args[1])
  }

  private fun createThreadDumps(jdkHome: String, dumpFileBasePath: String) {
    println("JDK-Home: $jdkHome")

    try {
      val jstackPattern = "$jdkHome\\jstack -l %s"
      val ecoroPIDs = getEcoroPIDs()

      if (ecoroPIDs.size == 0) {
        println("FEHLER: Es konnte kein aktiver Prozess von SHD ECORO ermittelt werden. Es wurden keine ThreadDumps erstellt!")
        System.exit(-1)
      }

      println("Alle ermittelten PIDs von SHD ECORO: $ecoroPIDs")

      for (pid in ecoroPIDs) {
        createThreadDumps(pid, jstackPattern, dumpFileBasePath)
      }
    } catch (e: Exception) {
      System.err.println("FEHLER: Die Datei konnte nicht generiert werden")
      e.printStackTrace()
    }
  }

  @Throws(IOException::class, InterruptedException::class)
  private fun createThreadDumps(pid: String, jstackPattern: String, dumpFileBasePath: String) {
    println("Erzeuge insgesamt $DUMP_COUNT ThreadDumps fuer PID '$pid'")

    // Zähler beginnt bei 1 um nutzerfreundlicher zu sein
    for (i in 1..DUMP_COUNT) {
      createThreadDump(pid, i, jstackPattern, dumpFileBasePath)
      println("ThreadDump $i von $DUMP_COUNT erzeugt. In $DUMP_DELAY_S Sek. wird der naechste erzeugt")
      Thread.sleep(DUMP_DELAY_MS.toLong())
    }

    println("FERTIG: Alle ThreadDumps wurden erzeugt!")
  }

  @Throws(IOException::class)
  private fun createThreadDump(pid: String, dumpIndex: Int, jstackPattern: String, dumpFileBasePath: String) {
    exec(String.format(jstackPattern, pid), Consumer exec@{ stdin: BufferedReader ->
      try {
        var line: String
        val dumpFile = Path.of(String.format(dumpFileBasePath + DUMP_FILE_PATTERN, pid, System.currentTimeMillis(), dumpIndex))
        println("ThreadDump wird erzeugt: $dumpFile")

        if (!dumpFile.toFile().createNewFile()) {
          println("ThreadDump fuer PID '$pid' konnte nicht erzeugt werden")
          return@exec
        }

        while (stdin.readLine().also { line = it } != null) {
          Files.writeString(dumpFile, line + '\n', StandardOpenOption.APPEND)
        }

        stdin.close()
      } catch (e: IOException) {
        System.err.println("FEHLER: ThreadDump konnte nicht erzeugt werden")
        e.printStackTrace()
      }
    })
  }

  @Throws(IOException::class)
  private fun getEcoroPIDs(): MutableList<String> {
    val pids: MutableList<String> = ArrayList()

    println("Ermittle PIDs von SHD ECORO")
    exec("tasklist /v /fo csv", Consumer { stdin: BufferedReader ->
      var line: String

      try {
        while (stdin.readLine().also { line = it } != null) {
          val columns: Array<String?> = line.split(",").toTypedArray()

          if (columns.size < 2) {
            System.err.println("FEHLER: Format von 'tasklist' ist ungueltig")
          } else if (columns[columns.size - 1] != null && columns[columns.size - 1]!!.contains(PROCESS_NAME_PART)) {
            val pid = columns[1]!!.replace("\"".toRegex(), "")

            println("PID von SHD ECORO ermittelt: $pid")
            pids.add(pid)
          }
        }
      } catch (e: IOException) {
        System.err.println("FEHLER: ThreadDump konnte nicht erzeugt werden")
        e.printStackTrace()
      }
    })

    return pids
  }

  @Throws(IOException::class)
  private fun exec(command: String, stdinConsumer: Consumer<BufferedReader>) {
    val process = Runtime.getRuntime().exec(command)
    val stdin = BufferedReader(InputStreamReader(process.inputStream))
    val stderr = BufferedReader(InputStreamReader(process.errorStream))
    var line: String?

    // Read the output from the command
    stdinConsumer.accept(stdin)
    stdin.close()

    // Read any errors from the attempted command
    while (stderr.readLine().also { line = it } != null) {
      println(line)
    }

    stderr.close()
  }
}
